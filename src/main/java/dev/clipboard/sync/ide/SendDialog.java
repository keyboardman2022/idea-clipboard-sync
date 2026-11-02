package dev.clipboard.sync.ide;

import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import dev.clipboard.sync.core.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import static dev.clipboard.sync.core.SyncModel.*;

final class SendDialog extends DialogWrapper {
    private final Project project; private final SyncController controller; private final ScanResult scan;
    private final FileTree files;
    private final JLabel progress = new JLabel("勾选文件后生成同步数据。");
    private List<String> chunks = List.of(); private int index;
    private final JButton generate = new JButton("生成同步数据并复制第一段");
    private final JButton copy = new JButton("复制当前段"), previous = new JButton("上一段"), next = new JButton("下一段并复制");
    private final JButton automatic = new JButton("自动发送全部"), pause = new JButton("暂停"), stop = new JButton("停止自动");
    private AutoClipboardLoop autoLoop;
    private long autoEpoch;
    SendDialog(Project project, SyncController controller, ScanResult scan) {
        super(project, false, IdeModalityType.MODELESS); this.project=project; this.controller=controller; this.scan=scan;
        files=new FileTree(scan.snapshot().rootName());files.setFiles(scan.snapshot().files());
        setTitle("发送目录 · " + scan.snapshot().rootName()); setCancelButtonText("关闭");
        init();
    }
    @Override protected Action[] createActions() { return new Action[]{getCancelAction()}; }
    @Override protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(10,10)); panel.setPreferredSize(new Dimension(800,500));
        JPanel header = new JPanel(new GridLayout(0,1,0,5));
        header.add(new JLabel("发送 " + scan.snapshot().files().size() + " 个文件，共 " + scan.snapshot().totalBytes() + " 字节；跳过 " + scan.skipped().size() + " 项。"));
        header.add(new JLabel("勾选文件夹即可选择全部子文件；半选表示部分文件已选。接收端按相对路径新增或更新。")); header.add(progress); panel.add(header,BorderLayout.NORTH);
        panel.add(files,BorderLayout.CENTER);
        JPanel controls = new JPanel(new GridLayout(0,1,0,5));
        JPanel manual = new JPanel(new FlowLayout(FlowLayout.LEFT)); manual.add(generate); manual.add(previous); manual.add(copy); manual.add(next); controls.add(manual);
        JPanel autos = new JPanel(new FlowLayout(FlowLayout.LEFT)); autos.add(automatic); autos.add(pause); autos.add(stop); controls.add(autos);
        automatic.setEnabled(false); pause.setEnabled(false); stop.setEnabled(false);
        JButton skipped = new JButton("查看跳过项"); manual.add(skipped);
        skipped.addActionListener(e -> {
            JTextArea text = new JTextArea(scan.skipped().isEmpty() ? "没有跳过项。" : String.join("\n", scan.skipped()),16,70); text.setEditable(false);text.setCaretPosition(0);
            JOptionPane.showMessageDialog(panel,new JScrollPane(text),"已跳过的文件和目录",JOptionPane.INFORMATION_MESSAGE);
        });
        copy.setEnabled(false); previous.setEnabled(false); next.setEnabled(false);
        generate.addActionListener(e -> {
            var selected = files.selectedPaths(); if (selected.isEmpty()) { progress.setText("请至少选择一个文件或文件夹。"); return; }
            Snapshot snapshot = new Snapshot(scan.snapshot().transferId(),scan.snapshot().rootName(),scan.snapshot().createdAt(),scan.snapshot().files().stream().filter(f -> selected.contains(f.relativePath())).toList());
            int chars = project.getService(SyncSettings.class).chunkChars;
            files.setLocked(true);generate.setEnabled(false);
            controller.background("生成剪贴板同步数据", i -> ClipboardChunks.split(snapshot.transferId(),SnapshotCodec.encode(snapshot,Limits.defaults()),chars), parts -> {
                if (isDisposed()) return; chunks=parts; index=0; files.setLocked(true); generate.setEnabled(false); copy.setEnabled(true); previous.setEnabled(true); next.setEnabled(true); automatic.setEnabled(true); copy();
            },()->{if(!isDisposed()&&chunks.isEmpty()){files.setLocked(false);generate.setEnabled(true);}});
        });
        copy.addActionListener(e -> copy()); previous.addActionListener(e -> { if(index>0)index--; updateProgress(); });
        next.addActionListener(e -> { if(index+1<chunks.size())index++; copy(); });
        automatic.addActionListener(e -> {
            try {
                long epoch = ++autoEpoch;
                if (autoLoop != null) autoLoop.close();
                var sender = new AutoTransfer.Sender(chunks, 3000, 10);
                AutoClipboardLoop loop = new AutoClipboardLoop(controller, sender, state -> {
                    if(isDisposed() || autoEpoch != epoch)return;
                    index = Math.min(state.confirmed(), chunks.size()-1); progress.setText(state.message()); pause.setText(state.paused()?"继续":"暂停");
                    if(state.complete()){ automatic.setEnabled(true); pause.setEnabled(false); stop.setEnabled(false);copy.setEnabled(true);previous.setEnabled(index>0);next.setEnabled(index+1<chunks.size()); }
                }, () -> {});
                if(!controller.startAuto(loop)){controller.error(new IllegalStateException("当前 IDEA 已有自动收发任务，请先停止另一窗口的任务。"));return;}
                autoLoop=loop;automatic.setEnabled(false);pause.setEnabled(true);pause.setText("暂停");stop.setEnabled(true);copy.setEnabled(false);previous.setEnabled(false);next.setEnabled(false);
                progress.setText("自动发送已启动，请确保另一边已经点击“自动接收”。");
            }catch(Exception ex){controller.error(ex);}
        });
        pause.addActionListener(e -> { if(autoLoop==null||autoLoop.closed())return;if(autoLoop.paused()){autoLoop.resume();pause.setText("暂停");}else{autoLoop.pause("已暂停自动发送，点击继续恢复");pause.setText("继续");} });
        stop.addActionListener(e -> { autoEpoch++;if(autoLoop!=null)autoLoop.close();autoLoop=null;automatic.setEnabled(true);pause.setEnabled(false);stop.setEnabled(false);copy.setEnabled(true);previous.setEnabled(index>0);next.setEnabled(index+1<chunks.size());progress.setText("已停止自动发送。重新开始时，两边均需重新启动自动模式。"); });
        panel.add(controls,BorderLayout.SOUTH); return panel;
    }
    private void copy() { if (chunks.isEmpty()) return; CopyPasteManager.getInstance().setContents(new StringSelection(chunks.get(index))); updateProgress(); }
    private void updateProgress() {
        progress.setText("第 " + (index+1) + " / " + chunks.size() + " 段（" + chunks.get(index).length() + " 字符）。切换另一边点击“读取剪贴板并接收”。");
        previous.setEnabled(index>0); next.setEnabled(index+1<chunks.size());
    }
    @Override protected void dispose(){autoEpoch++;if(autoLoop!=null)autoLoop.close();super.dispose();}
}
