package dev.clipboard.sync.ide;

import com.intellij.util.ui.ThreeStateCheckBox;
import dev.clipboard.sync.core.DirectorySelection;
import dev.clipboard.sync.core.SyncModel.FileEntry;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/** Hierarchical checkbox selection shared by sending and receiving. */
final class FileTree extends JPanel {
    private final String rootName;
    private final JTree tree=new JTree();
    private final JLabel summary=new JLabel();
    private final JButton selectAll=new JButton("全选"),selectNone=new JButton("取消全选");
    private DirectorySelection selection=new DirectorySelection(List.of());
    private Map<String,String> descriptions=Map.of();
    private final Renderer renderer=new Renderer();
    FileTree(String rootName){
        super(new BorderLayout(4,4));this.rootName=rootName;
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));toolbar.add(selectAll);toolbar.add(selectNone);
        JButton expand=new JButton("展开全部"),collapse=new JButton("折叠目录");toolbar.add(expand);toolbar.add(collapse);toolbar.add(summary);add(toolbar,BorderLayout.NORTH);
        tree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode(selection.root())));
        tree.setRootVisible(true);tree.setShowsRootHandles(true);tree.setLargeModel(true);tree.setRowHeight(26);tree.setCellRenderer(renderer);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);tree.getAccessibleContext().setAccessibleName("同步目录树，空格切换勾选，左右方向键展开或折叠");
        ToolTipManager.sharedInstance().registerComponent(tree);add(new JScrollPane(tree),BorderLayout.CENTER);
        selectAll.addActionListener(e->selectRoot(true));selectNone.addActionListener(e->selectRoot(false));
        expand.addActionListener(e->{for(int row=0;row<tree.getRowCount();row++)tree.expandRow(row);});
        collapse.addActionListener(e->{for(int row=tree.getRowCount()-1;row>=0;row--)tree.collapseRow(row);tree.expandRow(0);});
        tree.addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){
            if(!SwingUtilities.isLeftMouseButton(e)||e.getClickCount()!=1)return;
            TreePath path=tree.getPathForLocation(e.getX(),e.getY());if(path==null)return;Rectangle bounds=tree.getPathBounds(path);
            if(bounds!=null&&e.getX()>=bounds.x&&e.getX()<=bounds.x+renderer.checkWidth()){tree.setSelectionPath(path);toggle(path);}
        }});
        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"toggle-directory-check");
        tree.getActionMap().put("toggle-directory-check",new AbstractAction(){@Override public void actionPerformed(ActionEvent e){toggle(tree.getSelectionPath());}});
        clear();
    }
    void setFiles(List<FileEntry> files){setFiles(files,files.stream().map(FileEntry::relativePath).collect(java.util.stream.Collectors.toSet()),Map.of());}
    void setFiles(List<FileEntry> files,Set<String> selectable,Map<String,String> descriptions){
        this.descriptions=Map.copyOf(descriptions);selection=new DirectorySelection(files,selectable);
        var root=new DefaultMutableTreeNode(selection.root());var pending=new ArrayDeque<DefaultMutableTreeNode>();pending.add(root);
        while(!pending.isEmpty()){
            var parent=pending.remove();var node=(DirectorySelection.Node)parent.getUserObject();
            for(var child:node.children()){var item=new DefaultMutableTreeNode(child);parent.add(item);if(child.directory())pending.add(item);}
        }
        tree.setModel(new DefaultTreeModel(root));tree.expandRow(0);tree.setSelectionRow(0);updateSummary();
    }
    void clear(){setFiles(List.of(),Set.of(),Map.of());}
    Set<String> selectedPaths(){return selection.selectedPaths();}
    String selectedFilePath(){var path=tree.getSelectionPath();if(path==null)return null;var node=node(path);return node.directory()?null:node.relativePath();}
    void setLocked(boolean locked){selection.setLocked(locked);updateSummary();}
    private DirectorySelection.Node node(TreePath path){return (DirectorySelection.Node)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();}
    private void toggle(TreePath path){if(path!=null&&!selection.locked()&&node(path).selectable()){selection.toggle(node(path));updateSummary();}}
    private void selectRoot(boolean value){if(!selection.locked()){selection.setSelected(selection.root(),value);updateSummary();}}
    private void updateSummary(){
        var root=selection.root();summary.setText("已选 "+root.selectedCount()+" / "+root.selectableCount()+" 个可选文件 · "+root.selectedBytes()+" 字节");
        selectAll.setEnabled(!selection.locked()&&root.selectable());selectNone.setEnabled(!selection.locked()&&root.selectable());tree.repaint();
    }
    private final class Renderer extends JPanel implements TreeCellRenderer {
        private final ThreeStateCheckBox checkbox=new ThreeStateCheckBox();
        private final DefaultTreeCellRenderer label=new DefaultTreeCellRenderer();
        Renderer(){super(new BorderLayout(4,0));checkbox.setOpaque(false);checkbox.setFocusable(false);label.putClientProperty("html.disable",Boolean.TRUE);add(checkbox,BorderLayout.WEST);add(label,BorderLayout.CENTER);}
        // Tree renderers are reused as rubber stamps; their children are not laid out like normal panels.
        @Override public void paint(Graphics graphics){doLayout();super.paint(graphics);}
        int checkWidth(){return checkbox.getPreferredSize().width+4;}
        @Override public Component getTreeCellRendererComponent(JTree tree,Object value,boolean selected,boolean expanded,boolean leaf,int row,boolean focused){
            var node=(DirectorySelection.Node)((DefaultMutableTreeNode)value).getUserObject();
            label.getTreeCellRendererComponent(tree,value,selected,expanded,!node.directory(),row,focused);
            String text=node==selection.root()?rootName:node.name();
            if(node.directory())text+="  （已选 "+node.selectedCount()+"/"+node.selectableCount()+"）";
            else text+="  "+descriptions.getOrDefault(node.relativePath(),node.size()+" 字节");
            label.setText(text);label.setFont(tree.getFont());
            checkbox.setState(switch(node.state()){case ALL->ThreeStateCheckBox.State.SELECTED;case PARTIAL->ThreeStateCheckBox.State.DONT_CARE;case NONE->ThreeStateCheckBox.State.NOT_SELECTED;});
            checkbox.setEnabled(node.selectable()&&!selection.locked());setOpaque(true);setBackground(selected?label.getBackgroundSelectionColor():tree.getBackground());
            String tip=(node==selection.root()?rootName:node.relativePath())+"  "+descriptions.getOrDefault(node.relativePath(),"");setToolTipText(tip);getAccessibleContext().setAccessibleName(text+" "+switch(node.state()){case ALL->"已全选";case PARTIAL->"部分选中";case NONE->"未选中";});return this;
        }
    }
}
