package dev.clipboard.sync.ide;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;
public final class SettingsAction extends DumbAwareAction {
    @Override public void actionPerformed(@NotNull AnActionEvent e){if(e.getProject()!=null)new SettingsDialog(e.getProject()).show();}
}
