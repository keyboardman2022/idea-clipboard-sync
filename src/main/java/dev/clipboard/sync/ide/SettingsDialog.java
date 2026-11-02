package dev.clipboard.sync.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;

final class SettingsDialog extends DialogWrapper {
    private final SyncSettings settings;
    private final JSpinner chars;
    private final JTextArea ignores;
    SettingsDialog(Project project){super(project);settings=project.getService(SyncSettings.class);chars=new JSpinner(new SpinnerNumberModel(settings.chunkChars,4096,1<<20,4096));ignores=new JTextArea(String.join("\n",settings.ignores),8,48);setTitle("剪贴板同步设置");setOKButtonText("保存");setCancelButtonText("取消");init();}
    @Override protected JComponent createCenterPanel(){JPanel panel=new JPanel(new BorderLayout(8,8));JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT));JLabel label=new JLabel("每段最大字符数：");label.setLabelFor(chars);top.add(label);top.add(chars);panel.add(top,BorderLayout.NORTH);JPanel center=new JPanel(new BorderLayout(4,4));JLabel help=new JLabel("附加忽略规则（每行一个，例如 *.log、temp/**；/ 分隔路径）");help.setLabelFor(ignores);center.add(help,BorderLayout.NORTH);center.add(new JScrollPane(ignores),BorderLayout.CENTER);panel.add(center,BorderLayout.CENTER);return panel;}
    @Override protected void doOKAction(){settings.chunkChars=(Integer)chars.getValue();settings.ignores=new ArrayList<>(Arrays.stream(ignores.getText().split("\\R")).map(String::trim).filter(s->!s.isEmpty()).toList());super.doOKAction();}
}
