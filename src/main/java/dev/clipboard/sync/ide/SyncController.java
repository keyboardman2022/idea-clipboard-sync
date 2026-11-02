package dev.clipboard.sync.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.*;
import dev.clipboard.sync.core.*;
import org.jetbrains.annotations.NotNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import static dev.clipboard.sync.core.SyncModel.*;

@Service(Service.Level.PROJECT)
public final class SyncController implements Disposable {
    private final Project project;
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile ProgressIndicator current;
    private volatile boolean disposed;
    private final Set<AutoClipboardLoop> automatic = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<javax.swing.Timer> deferred = java.util.concurrent.ConcurrentHashMap.newKeySet();
    boolean startAuto(AutoClipboardLoop loop) {
        if (disposed || project.isDisposed()) throw new IllegalStateException("项目已关闭");
        if (busy.get()) throw new IllegalStateException("后台同步任务正在运行，请稍后启动自动模式。");
        automatic.add(loop);
        if (loop.start()) return true;
        automatic.remove(loop); return false;
    }
    void autoClosed(AutoClipboardLoop loop) { automatic.remove(loop); }
    public SyncController(Project project) { this.project = project; }
    public Path stateRoot() throws IOException {
        if (project.getBasePath() == null) throw new IOException("项目没有本地目录");
        Path root = Path.of(project.getBasePath()).resolve(".clipboard-sync"); PathPolicy.checkAncestors(root); return root;
    }
    public ReceiveStore receiveStore() throws IOException { return new ReceiveStore(stateRoot().resolve("receive"), Limits.defaults()); }
    @FunctionalInterface public interface Work<T> { T run(ProgressIndicator indicator) throws Exception; }
    public <T> void background(String title, Work<T> work, Consumer<T> done) {
        background(title,work,done,()->{});
    }
    public <T> void background(String title, Work<T> work, Consumer<T> done, Runnable finished) {
        if (!busy.compareAndSet(false, true)) { Messages.showInfoMessage(project, "同步任务正在运行，请等待或取消当前任务。", "剪贴板同步"); finished.run(); return; }
        var relay = new CompletionRelay<>(done);
        new Task.Backgroundable(project, title, true) {
            private T value; private Exception failure;
            private ProgressIndicator owned;
            @Override public void run(@NotNull ProgressIndicator indicator) {
                current = indicator; owned = indicator; indicator.setIndeterminate(true);
                try { if (disposed) throw new CancellationException(); value = work.run(indicator); }
                catch (CancellationException e) { indicator.cancel(); }
                catch (Exception e) { failure = e; }
            }
            @Override public void onSuccess() {
                release();
                if (disposed || project.isDisposed()) return;
                if (failure != null) error(failure); else relay.complete(value, false);
            }
            @Override public void onCancel() {
                release();
                if (!disposed && !project.isDisposed()) relay.complete(value, true);
            }
            private void release() { if (current == owned) { current = null; busy.set(false); } }
            @Override public void onFinished() { release(); finished.run(); }
        }.queue();
    }
    public <T> void backgroundWhenIdle(String title, Work<T> work, Consumer<T> done, java.util.function.BooleanSupplier relevant) {
        var job=new DeferredCompletion(()->!busy.get(),()->!disposed&&!project.isDisposed()&&relevant.getAsBoolean(),()->background(title,work,done));
        if(job.tick())return;
        javax.swing.Timer timer=new javax.swing.Timer(250,e->{if(job.tick()){javax.swing.Timer source=(javax.swing.Timer)e.getSource();source.stop();deferred.remove(source);}});
        deferred.add(timer);timer.start();
    }
    public void send(VirtualFile source) {
        try {
            Path root = source.toNioPath().toAbsolutePath().normalize(); var manager = FileDocumentManager.getInstance();
            for (var document : manager.getUnsavedDocuments()) {
                var file = manager.getFile(document);
                if (file != null && file.isInLocalFileSystem() && file.toNioPath().startsWith(root)) {
                    manager.saveDocument(document); if (manager.isDocumentUnsaved(document)) throw new IOException("文件未能保存：" + file.getPath());
                }
            }
            var settings = project.getService(SyncSettings.class); var ignores = java.util.List.copyOf(settings.ignores);
            background("扫描待发送目录", i -> SnapshotScanner.scan(root, ignores, Limits.defaults(), i::isCanceled), scan -> new SendDialog(project, this, scan).show());
        } catch (Exception e) { error(e); }
    }
    public void receive(VirtualFile target) { new ReceiveDialog(project, this, target).show(); }
    public boolean documentsSaved(Set<Path> paths) {
        var allowed = new AtomicBoolean(true);
        Runnable inspect = () -> {
            var manager = FileDocumentManager.getInstance();
            for (var document : manager.getUnsavedDocuments()) {
                var file = manager.getFile(document);
                if (file != null && file.isInLocalFileSystem() && paths.contains(file.toNioPath().toAbsolutePath().normalize())) allowed.set(false);
            }
        };
        if (ApplicationManager.getApplication().isDispatchThread()) inspect.run();
        else ApplicationManager.getApplication().invokeAndWait(inspect, ModalityState.any());
        return allowed.get();
    }
    public void refresh(VirtualFile root) { root.refresh(true, true); }
    public void mutate(Path path, FileApplier.IoOperation operation) throws IOException {
        IOException[] failure = {null};
        ProgressManager.getInstance().executeNonCancelableSection(() -> ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(() -> {
            try {
                if (disposed || project.isDisposed()) throw new IOException("项目已经关闭");
                operation.run();
                var file = LocalFileSystem.getInstance().findFileByNioFile(path);
                if (file != null) {
                    file.refresh(false, false);
                    if (file.isValid()) {
                        var manager = FileDocumentManager.getInstance(); var document = manager.getCachedDocument(file);
                        if (document != null && !manager.isDocumentUnsaved(document)) manager.reloadFromDisk(document);
                    }
                }
            } catch (IOException e) { failure[0] = e; }
        }), ModalityState.nonModal()));
        if (failure[0] != null) throw failure[0];
    }
    public void error(Throwable e) { Messages.showErrorDialog(project, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), "剪贴板同步失败"); }
    public void result(ApplyResult result) {
        StringBuilder message = new StringBuilder("成功：" + result.succeeded().size() + "，失败：" + result.failed().size() + "，未处理：" + result.pending().size());
        result.failed().entrySet().stream().limit(20).forEach(e -> message.append("\n").append(e.getKey()).append("：").append(e.getValue()));
        Messages.showInfoMessage(project, message.toString(), "剪贴板同步结果");
    }
    @Override public void dispose() { disposed = true; var indicator = current; if (indicator != null) indicator.cancel(); for (var loop : automatic.toArray(AutoClipboardLoop[]::new)) loop.close(); for(var timer:deferred)timer.stop();deferred.clear(); }
}
