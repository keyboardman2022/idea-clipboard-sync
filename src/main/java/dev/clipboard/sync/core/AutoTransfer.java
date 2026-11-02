package dev.clipboard.sync.core;

import java.io.IOException;
import java.util.*;
import static dev.clipboard.sync.core.SyncModel.*;

/** Stop-and-wait transport over a single bidirectional text clipboard. No file application here. */
public final class AutoTransfer {
    public static final String PREFIX = "IDEA-DIRSYNC-AUTO/1";
    private AutoTransfer() {}
    public record Progress(int confirmed, int total, boolean paused, boolean complete, String message) {}
    public interface Endpoint {
        String tick(String clipboard, long nowMillis) throws IOException;
        Progress progress();
        void pause(String reason);
        void resume();
    }
    public static final class Sender implements Endpoint {
        private final List<String> chunks;
        private final List<ClipboardChunks.Chunk> headers;
        private final UUID run = UUID.randomUUID();
        private final long timeout;
        private final int maxAttempts;
        private int index, attempts;
        private long lastSent;
        private boolean sent, paused, ready;
        private String message = "等待自动发送";
        public Sender(List<String> chunks, long timeoutMillis, int maxAttempts) throws IOException {
            if (chunks.isEmpty() || timeoutMillis < 1 || maxAttempts < 1) throw new IllegalArgumentException("Invalid sender settings");
            this.chunks = List.copyOf(chunks); this.timeout = timeoutMillis; this.maxAttempts = maxAttempts;
            List<ClipboardChunks.Chunk> parsed = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                var c = ClipboardChunks.parse(chunks.get(i));
                if (c.index() != i + 1 || c.total() != chunks.size() || (!parsed.isEmpty() && (!c.id().equals(parsed.getFirst().id()) || !c.bundleSha256().equals(parsed.getFirst().bundleSha256())))) throw new IOException("自动发送数据不是同一份完整快照");
                parsed.add(c);
            }
            headers = List.copyOf(parsed);
        }
        public UUID runId() { return run; }
        private String frame() { return PREFIX + "|DATA|" + run + "\n" + chunks.get(index); }
        private String greeting(String type) { var c=headers.getFirst();return PREFIX+"|"+type+"|"+run+"|"+c.id()+"|"+chunks.size()+"|"+c.bundleSha256(); }
        @Override public synchronized String tick(String clipboard, long now) {
            if (paused || index == chunks.size()) return null;
            if (!ready) {
                if (sent && clipboard != null && clipboard.strip().equals(greeting("READY"))) { ready=true;sent=false;attempts=0; }
                else {
                    if(sent && (clipboard==null || !clipboard.startsWith(PREFIX+"|"))){pause("剪贴板被其他内容占用，已暂停；点击继续可恢复发送");return null;}
                    if(!sent || now-lastSent>=timeout){
                        if(attempts>=maxAttempts){pause("对端尚未完成握手，已暂停。请在另一边启动自动接收，再点击继续");return null;}
                        attempts++;lastSent=now;sent=true;message="正在连接接收端"+(attempts>1?"（重试 "+(attempts-1)+" 次）":"");return greeting("HELLO");
                    }
                    return null;
                }
            }
            if (sent && clipboard != null && clipboard.startsWith(PREFIX + "|ACK|")) {
                String[] f = clipboard.strip().split("\\|", -1);
                var c = headers.get(index);
                if (f.length == 10 && f[2].equals(run.toString()) && f[3].equals(c.id().toString()) && f[4].equals(String.valueOf(c.index())) && f[5].equals(String.valueOf(c.total())) && f[6].equals(c.bundleSha256()) && f[7].equals(c.partSha256()) && f[8].equals("OK")) {
                    if (f[9].equals("COMPLETE")) { index = chunks.size(); message = "全部分段已由对端保存，整包校验通过"; return null; }
                    if (f[9].equals("RECEIVED") && index + 1 < chunks.size()) { index++; sent = false; attempts = 0; }
                }
            } else if (sent && (clipboard == null || (!clipboard.startsWith(PREFIX + "|DATA|" + run + "\n") && !clipboard.strip().equals(greeting("READY")) && !clipboard.strip().equals(greeting("HELLO"))))) {
                pause("剪贴板被其他内容占用，已暂停；点击继续可恢复发送"); return null;
            }
            if (!sent || now - lastSent >= timeout) {
                if (attempts >= maxAttempts) { pause("对端长时间未确认，已暂停。检查两边自动模式与 RDP 剪贴板，再点击继续"); return null; }
                attempts++; lastSent = now; sent = true;
                message = "等待第 " + (index + 1) + " / " + chunks.size() + " 段确认" + (attempts > 1 ? "（重发 " + (attempts - 1) + " 次）" : "");
                return frame();
            }
            return null;
        }
        @Override public synchronized Progress progress() { return new Progress(index, chunks.size(), paused, index == chunks.size(), message); }
        @Override public synchronized void pause(String reason) { paused = true; message = reason; }
        @Override public synchronized void resume() { if (index != chunks.size()) { paused = false; sent = false; attempts = 0; message = "继续发送"; } }
    }
    public static final class Receiver implements Endpoint {
        private final ReceiveStore store;
        private UUID run, transfer;
        private String bundle;
        private int confirmed, total;
        private boolean paused, armed;
        private Snapshot snapshot;
        private String message = "自动接收已就绪，等待发送端";
        public Receiver(ReceiveStore store) { this.store = store; }
        public synchronized Snapshot snapshot() { return snapshot; }
        @Override public synchronized String tick(String clipboard, long now) throws IOException {
            if (paused) return null;
            if(clipboard!=null && clipboard.startsWith(PREFIX+"|HELLO|")){
                if(clipboard.length()>512){message="握手数据无效，等待重试";return null;}
                String[] h=clipboard.strip().split("\\|",-1);
                try{
                    if(h.length!=6)throw new IllegalArgumentException();UUID proposedRun=UUID.fromString(h[2]),proposedTransfer=UUID.fromString(h[3]);int proposedTotal=Integer.parseInt(h[4]);
                    if(!proposedRun.toString().equals(h[2])||!proposedTransfer.toString().equals(h[3])||proposedTotal<1||proposedTotal>4096||!h[5].matches("[0-9a-f]{64}"))throw new IllegalArgumentException();
                    if(armed && (!proposedRun.equals(run)||!proposedTransfer.equals(transfer)||!h[5].equals(bundle))){pause("检测到另一份自动传输，已暂停，未混入当前会话");return null;}
                    run=proposedRun;transfer=proposedTransfer;bundle=h[5];total=proposedTotal;
                    message="已连接发送端，等待数据分段";
                    return PREFIX+"|READY|"+run+"|"+transfer+"|"+total+"|"+bundle;
                }catch(IllegalArgumentException e){message="握手数据无效，等待重试";return null;}
            }
            if (clipboard == null || !clipboard.startsWith(PREFIX + "|DATA|")) {
                if (armed && snapshot == null && (clipboard == null || !clipboard.startsWith(PREFIX + "|ACK|"))) pause("剪贴板被其他内容占用，已暂停接收");
                return null;
            }
            if (clipboard.length() > (1 << 20) + 128) { message = "收到的分段过大，等待重发"; return null; }
            int newline = clipboard.indexOf('\n');
            if (newline < 0 || newline > 128) { message = "分段头损坏，等待重发"; return null; }
            String[] head = clipboard.substring(0, newline).strip().split("\\|", -1);
            UUID incomingRun; ClipboardChunks.Chunk chunk;
            try {
                if (head.length != 3) throw new IOException("自动分段头无效");
                incomingRun = UUID.fromString(head[2]); if (!incomingRun.toString().equals(head[2])) throw new IOException("传输编号无效");
                chunk = ClipboardChunks.parse(clipboard.substring(newline + 1));
            } catch (IOException | IllegalArgumentException e) { message = "分段损坏或被截断，等待发送端重发"; return null; }
            if(run==null){message="忽略旧分段，等待发送端建立新连接";return null;}
            if (!run.equals(incomingRun) || !transfer.equals(chunk.id()) || !bundle.equals(chunk.bundleSha256()) || total!=chunk.total()) {
                if(!armed){message="忽略旧传输的分段，等待当前发送端";return null;}
                pause("检测到另一份自动传输，已暂停，未混入当前会话"); return null;
            }
            var state = store.accept(clipboard.substring(newline + 1));
            run = incomingRun; transfer = chunk.id(); bundle = chunk.bundleSha256(); armed = true;
            confirmed = state.received().size(); total = state.total();
            if (state.complete() && snapshot == null) snapshot = store.assemble(transfer);
            message = snapshot != null ? "全部分段已保存并校验，正在准备文件预览" : "已接收 " + confirmed + " / " + total + " 段";
            return PREFIX + "|ACK|" + run + "|" + transfer + "|" + chunk.index() + "|" + total + "|" + bundle + "|" + chunk.partSha256() + "|OK|" + (snapshot == null ? "RECEIVED" : "COMPLETE");
        }
        @Override public synchronized Progress progress() { return new Progress(confirmed, total, paused, snapshot != null, message); }
        @Override public synchronized void pause(String reason) { paused = true; message = reason; }
        @Override public synchronized void resume() { paused = false; armed = false; message = "继续接收，等待下一段"; }
    }
}
