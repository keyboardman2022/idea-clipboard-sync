package dev.clipboard.sync.ide;

import com.intellij.openapi.application.ApplicationManager;
import dev.clipboard.sync.core.AutoTransfer;
import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Owns one system clipboard per IDE process. Slow RDP clipboard reads never run on the EDT. */
final class AutoClipboardLoop implements AutoCloseable {
    private static final AtomicReference<AutoClipboardLoop> OWNER = new AtomicReference<>();
    private final AutoTransfer.Endpoint endpoint;
    private final Consumer<AutoTransfer.Progress> changed;
    private final Runnable completed;
    private final SyncController controller;
    private final Object writeLock = new Object();
    private final ScheduledExecutorService worker;
    private volatile boolean closed;
    private AutoTransfer.Progress lastProgress;
    private volatile AutoTransfer.Progress published;
    private volatile boolean requestedPause;
    private long completeAt = -1;
    private int busyReads;
    AutoClipboardLoop(SyncController controller, AutoTransfer.Endpoint endpoint, Consumer<AutoTransfer.Progress> changed, Runnable completed) {
        this.controller = controller; this.endpoint = endpoint; this.changed = changed; this.completed = completed;
        published = endpoint.progress();
        worker = Executors.newSingleThreadScheduledExecutor(r -> { Thread thread = new Thread(r, "clipboard-directory-sync-auto"); thread.setDaemon(true); return thread; });
    }
    boolean start() {
        if (!OWNER.compareAndSet(null, this)) { worker.shutdownNow(); return false; }
        worker.scheduleWithFixedDelay(this::tick, 0, 150, TimeUnit.MILLISECONDS); return true;
    }
    private String read(Clipboard clipboard) throws Exception {
        if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return null;
        Object value = clipboard.getData(DataFlavor.stringFlavor); return value instanceof String text ? text : null;
    }
    private void tick() {
        if (closed || requestedPause || endpoint.progress().paused()) return;
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            String before = read(clipboard); long now = System.nanoTime() / 1_000_000;
            String output = endpoint.tick(before, now);
            if (output != null && !closed) {
                if (!Objects.equals(before, read(clipboard))) endpoint.pause("处理期间剪贴板被其他内容替换，已暂停；点击继续可恢复");
                else synchronized (writeLock) { if (!closed && !requestedPause) clipboard.setContents(new StringSelection(output), null); }
            }
            busyReads = 0;
            AutoTransfer.Progress progress = endpoint.progress();
            published = progress; if (progress.paused()) requestedPause = true;
            if (!progress.equals(lastProgress)) { lastProgress = progress; dispatch(() -> changed.accept(progress)); }
            if (progress.complete()) {
                if (completeAt < 0) { completeAt = now; dispatch(completed); }
                if (endpoint instanceof AutoTransfer.Sender || now - completeAt >= 35_000) close();
            }
        } catch (IllegalStateException e) {
            if (++busyReads >= 100) pause("系统剪贴板长时间被占用，已暂停；检查远程桌面连接后继续");
        } catch (Exception e) { pause("自动传输已暂停：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())); }
    }
    private void dispatch(Runnable task) { ApplicationManager.getApplication().invokeLater(() -> { if (!closed || published.complete()) task.run(); }); }
    boolean paused() { return requestedPause; }
    boolean complete() { return published.complete(); }
    boolean closed() { return closed; }
    void pause(String message) {
        requestedPause=true;
        command(()->{endpoint.pause(message);published=endpoint.progress();dispatch(()->changed.accept(published));});
    }
    void resume() { requestedPause=false; command(()->{endpoint.resume();published=endpoint.progress();dispatch(()->changed.accept(published));}); }
    private void command(Runnable task) { if(!closed)try{worker.execute(task);}catch(RejectedExecutionException ignored){} }
    @Override public void close() {
        synchronized (writeLock) { if (closed) return; closed = true; }
        OWNER.compareAndSet(this, null); worker.shutdownNow(); controller.autoClosed(this);
    }
}
