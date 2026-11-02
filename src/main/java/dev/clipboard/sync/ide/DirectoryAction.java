package dev.clipboard.sync.ide;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

abstract class DirectoryAction extends DumbAwareAction {
    @Override public @NotNull ActionUpdateThread getActionUpdateThread() { return ActionUpdateThread.BGT; }
    @Override public void update(@NotNull AnActionEvent e) { e.getPresentation().setEnabled(e.getProject() != null); }
    @Override public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject(); if (project == null) return;
        VirtualFile selected = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (selected != null && selected.isDirectory() && selected.isInLocalFileSystem()) use(project, selected);
        else {
            var descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor(); descriptor.setTitle("选择同步目录");
            FileChooser.chooseFile(descriptor, project, null, folder -> use(project, folder));
        }
    }
    protected abstract void use(Project project, VirtualFile folder);
}
