package dev.clipboard.sync.ide;

import com.intellij.diff.*;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.vfs.VirtualFile;
import dev.clipboard.sync.core.*;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import static dev.clipboard.sync.core.SyncModel.*;

final class ReceiveDialog extends DialogWrapper {
    private final Project project; private final SyncController controller; private final VirtualFile target;
    private final FileTree files;
    private final JLabel progress = new JLabel("等待接收。不会自动读取或覆盖普通剪贴板内容。");
    private final JComboBox<UUID> sessions = new JComboBox<>();
    private final JButton apply = new JButton("应用选中文件");
    private Preview preview; private UUID current;
    private AutoClipboardLoop autoLoop;
    private long autoEpoch;
    private final JButton automatic = new JButton("自动接收"), pause = new JButton("暂停"), stop = new JButton("停止自动");
    private record Received(ReceiveStore.ReceiveProgress progress, Preview preview) {}
    ReceiveDialog(Project project, SyncController controller, VirtualFile target) {
        super(project,false,IdeModalityType.MODELESS); this.project=project; this.controller=controller; this.target=target;
        files=new FileTree(target.getName());
        setTitle("接收目录同步"); setCancelButtonText("关闭"); init();
        refreshSessions();
    }
    @Override protected Action[] createActions() { return new Action[]{getCancelAction()}; }
    @Override protected JComponent createCenterPanel() {
        JPanel panel=new JPanel(new BorderLayout(10,10)); panel.setPreferredSize(new Dimension(900,540));
        JPanel top = new JPanel(new GridLayout(0,1,0,5)); top.add(new JLabel("目标目录："+target.getPath())); top.add(progress);
        JPanel transfer = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton receive = new JButton("读取剪贴板并接收"), reload=new JButton("重新预览 / 继续会话"), clear=new JButton("清理选中会话");
        transfer.add(receive); transfer.add(sessions); transfer.add(reload); transfer.add(clear); top.add(transfer); panel.add(top,BorderLayout.NORTH);
        JPanel autos = new JPanel(new FlowLayout(FlowLayout.LEFT)); autos.add(automatic); autos.add(pause); autos.add(stop); top.add(autos); pause.setEnabled(false);stop.setEnabled(false);
        panel.add(files,BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT)); JButton diff=new JButton("查看选中文件差异"); buttons.add(diff); buttons.add(apply); panel.add(buttons,BorderLayout.SOUTH); apply.setEnabled(false);
        receive.addActionListener(e -> {
            long epoch=beginManualOperation();
            String text=CopyPasteManager.getInstance().getContents(DataFlavor.stringFlavor);
            controller.background("接收并校验剪贴板数据", i -> {
                var store=controller.receiveStore(); var state=store.accept(text);
                var nextPreview=state.complete()?DirectorySync.preview(target.toNioPath(),store.assemble(state.id()),i::isCanceled):null;
                return new Received(state,nextPreview);
            }, value->{if(!isDisposed()&&autoEpoch==epoch)received(value);});
        });
        automatic.addActionListener(e -> {
            try{
                long epoch=++autoEpoch;
                if(autoLoop!=null)autoLoop.close();
                var store=controller.receiveStore();var receiver=new AutoTransfer.Receiver(store);
                AutoClipboardLoop loop=new AutoClipboardLoop(controller,receiver,state->{
                    if(isDisposed() || autoEpoch!=epoch)return;progress.setText(state.message());pause.setText(state.paused()?"继续":"暂停");
                    if(state.complete()){pause.setEnabled(false);receive.setEnabled(true);reload.setEnabled(true);clear.setEnabled(true);automatic.setEnabled(true);}
                },()->{
                    if(isDisposed() || autoEpoch!=epoch)return;
                    Snapshot snapshot=receiver.snapshot();if(snapshot==null)return;
                    controller.backgroundWhenIdle("自动接收完成，比较目标目录",i->new Received(store.load(snapshot.transferId()),DirectorySync.preview(target.toNioPath(),snapshot,i::isCanceled)),value->{if(!isDisposed()&&autoEpoch==epoch)received(value);},()->!isDisposed()&&autoEpoch==epoch);
                });
                if(!controller.startAuto(loop)){controller.error(new IllegalStateException("当前 IDEA 已有自动收发任务，请先停止另一窗口的任务。"));return;}
                autoLoop=loop;preview=null;files.clear();apply.setEnabled(false);automatic.setEnabled(false);pause.setEnabled(true);pause.setText("暂停");stop.setEnabled(true);receive.setEnabled(false);reload.setEnabled(false);clear.setEnabled(false);
                progress.setText("自动接收已就绪。切换发送端点击“自动发送全部”，之后无需逐段点击。");
            }catch(Exception ex){controller.error(ex);}
        });
        pause.addActionListener(e->{if(autoLoop==null||autoLoop.closed())return;if(autoLoop.paused()){autoLoop.resume();pause.setText("暂停");}else{autoLoop.pause("已暂停自动接收，点击继续恢复");pause.setText("继续");}});
        stop.addActionListener(e->{autoEpoch++;if(autoLoop!=null)autoLoop.close();autoLoop=null;automatic.setEnabled(true);pause.setEnabled(false);stop.setEnabled(false);receive.setEnabled(true);reload.setEnabled(true);clear.setEnabled(true);progress.setText("已停止自动接收，已收到的分段仍保留。");});
        reload.addActionListener(e -> {
            long epoch=beginManualOperation();
            UUID id=(UUID)sessions.getSelectedItem(); if(id==null)id=current; if(id==null)return; UUID chosen=id;
            controller.background("重新比较目录", i -> {
                var store=controller.receiveStore(); var state=store.load(chosen);
                return new Received(state,state.complete()?DirectorySync.preview(target.toNioPath(),store.assemble(chosen),i::isCanceled):null);
            },value->{if(!isDisposed()&&autoEpoch==epoch)received(value);});
        });
        clear.addActionListener(e -> {
            UUID id=(UUID)sessions.getSelectedItem(); if(id==null)return;
            if(Messages.showYesNoDialog(project,"删除该会话已接收的片段？不会修改代码文件。","清理会话",null)!=Messages.YES)return;
            beginManualOperation();
            controller.background("清理接收会话",i->{controller.receiveStore().cancel(id);return id;}, cleared->{ if(isDisposed())return; if(cleared.equals(current)){preview=null;current=null;files.clear();apply.setEnabled(false);progress.setText("已清理，可重新接收。");} refreshSessions(); });
        });
        diff.addActionListener(e -> {
            String path=files.selectedFilePath();if(path==null||preview==null)return;
            var file=preview.snapshot().files().stream().filter(f->f.relativePath().equals(path)).findFirst().orElse(null); if(file==null)return;
            controller.background("准备文件差异", i -> {
                Path local=PathPolicy.resolve(preview.root(),path); byte[] before=Files.exists(local)?CoreIo.readFile(local,10L<<20):new byte[0];
                return new byte[][]{before,file.content()};
            }, bytes -> {
                if(isDisposed())return;
                try {
                    String left=decodeText(bytes[0]),right=decodeText(bytes[1]); var factory=DiffContentFactory.getInstance();
                    DiffManager.getInstance().showDiff(project,new SimpleDiffRequest(path,factory.create(project,left),factory.create(project,right),"目标现有内容","收到的内容"));
                } catch(Exception ex){Messages.showInfoMessage(project,"该文件为二进制或非 UTF-8 文本，无法显示文本差异。内容仍按原始字节同步。\n收到文件："+file.size()+" 字节\nSHA-256："+file.sha256(),"文件摘要");}
            });
        });
        apply.addActionListener(e -> {
            if(preview==null)return; var selected=files.selectedPaths(); if(selected.isEmpty()){progress.setText("请勾选需要新增或更新的文件或文件夹。");return;}
            if(Messages.showYesNoDialog(project,"将新增或更新 "+selected.size()+" 个文件。是否继续？","应用目录同步",null)!=Messages.YES)return;
            Preview planned=preview;
            files.setLocked(true);apply.setEnabled(false);
            controller.background("应用目录同步", i -> FileApplier.applyGuarded(planned,selected,i::isCanceled,controller::documentsSaved,controller::mutate), result->{controller.refresh(target);controller.result(result);if(isDisposed())return;preview=null;files.clear();apply.setEnabled(false);progress.setText("应用结束。可重新预览以确认当前目录状态。");},()->{if(!isDisposed()){files.setLocked(false);apply.setEnabled(preview!=null&&!files.selectedPaths().isEmpty());}});
        });
        return panel;
    }
    private static String decodeText(byte[] bytes) throws CharacterCodingException {
        for(byte b:bytes)if(b==0)throw new CharacterCodingException();
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    private void received(Received value) {
        if(isDisposed())return; current=value.progress().id();preview=value.preview();files.clear();
        var state=value.progress(); String missing=java.util.stream.IntStream.rangeClosed(1,state.total()).filter(n->!state.received().contains(n)).limit(30).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
        progress.setText("已收到 "+state.received().size()+" / "+state.total()+" 段"+(state.complete()?"，校验通过。勾选要应用的文件。":"；缺少："+missing));
        if(preview!=null){
            Set<String> selectable=new HashSet<>();Map<String,String> descriptions=new HashMap<>();
            for(var c:preview.changes()){if(c.kind()==ChangeKind.SAME)continue;if(c.kind()==ChangeKind.ADD||c.kind()==ChangeKind.MODIFY)selectable.add(c.relativePath());descriptions.put(c.relativePath(),kind(c.kind())+(c.reason().isEmpty()?"":" · "+c.reason()));}
            var visible=DirectorySync.changedFiles(preview);files.setFiles(visible,selectable,descriptions);
            if(visible.isEmpty())progress.setText("接收的文件均相同，无需同步。");
        }
        apply.setEnabled(preview!=null&&!files.selectedPaths().isEmpty()); if(((DefaultComboBoxModel<UUID>)sessions.getModel()).getIndexOf(current)<0)sessions.addItem(current); sessions.setSelectedItem(current);
    }
    private static String kind(ChangeKind k){return switch(k){case ADD->"新增";case MODIFY->"修改";case SAME->"相同（跳过）";case BLOCKED->"无法应用";};}
    private long beginManualOperation(){autoEpoch++;if(autoLoop!=null)autoLoop.close();autoLoop=null;automatic.setEnabled(true);pause.setEnabled(false);stop.setEnabled(false);return autoEpoch;}
    private void refreshSessions(){ controller.background("读取接收会话",i->controller.receiveStore().sessions(),ids->{if(isDisposed())return;sessions.removeAllItems();ids.forEach(sessions::addItem);if(current!=null)sessions.setSelectedItem(current);}); }
    @Override protected void dispose(){autoEpoch++;if(autoLoop!=null)autoLoop.close();super.dispose();}
}
