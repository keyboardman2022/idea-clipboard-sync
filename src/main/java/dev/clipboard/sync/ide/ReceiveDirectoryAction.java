package dev.clipboard.sync.ide;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
public final class ReceiveDirectoryAction extends DirectoryAction {
    @Override protected void use(Project project, VirtualFile folder) { project.getService(SyncController.class).receive(folder); }
}
