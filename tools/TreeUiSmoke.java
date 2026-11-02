package dev.clipboard.sync.ide;

import dev.clipboard.sync.core.*;
import dev.clipboard.sync.core.SyncModel.FileEntry;
import static dev.clipboard.sync.core.SyncModel.*;
import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Runs real checkbox-tree keyboard/mouse/lock behavior and renders it without touching an IDE session. */
public final class TreeUiSmoke {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try {
                Font font=new Font("Microsoft YaHei UI",Font.PLAIN,14);
                for(String key:List.of("Label.font","Button.font","Tree.font","CheckBox.font"))UIManager.put(key,font);
                var paths=List.of("src/main/java/App.java","src/main/java/UserService.java","src/test/java/AppTest.java","docs/README.md","pom.xml");
                var entries=paths.stream().map(p->new FileEntry(p,new byte[]{1,2},CoreIo.sha256(new byte[]{1,2}))).toList();
                FileTree send=new FileTree("示例项目");send.setFiles(entries);JTree tree=findTree(send);
                if(send.selectedPaths().size()!=5)throw new AssertionError("Initial selection");
                key(tree,path(tree,"",true));if(!send.selectedPaths().isEmpty())throw new AssertionError("Space deselect root");
                key(tree,path(tree,"src",true));if(!send.selectedPaths().equals(Set.of(paths.get(0),paths.get(1),paths.get(2))))throw new AssertionError("Space selects folder descendants");
                key(tree,path(tree,paths.get(1),false));
                if(send.selectedPaths().size()!=2)throw new AssertionError("Space toggles single child");
                send.setLocked(true);key(tree,path(tree,"",true));if(send.selectedPaths().size()!=2)throw new AssertionError("Locked tree changed");send.setLocked(false);
                for(int row=0;row<tree.getRowCount();row++)tree.expandRow(row);
                JPanel panel=new JPanel(new GridLayout(1,2,14,0));panel.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
                JPanel left=new JPanel(new BorderLayout(0,10));left.add(new JLabel("发送选择：按文件夹勾选，支持半选"),BorderLayout.NORTH);left.add(send,BorderLayout.CENTER);panel.add(left);
                var preview=new Preview(Path.of("target"),new Snapshot(UUID.randomUUID(),"source",java.time.Instant.now(),entries),List.of(new Change(paths.get(0),ChangeKind.MODIFY,null,null,""),new Change(paths.get(1),ChangeKind.ADD,null,null,""),new Change(paths.get(2),ChangeKind.SAME,null,null,""),new Change(paths.get(3),ChangeKind.SAME,null,null,""),new Change(paths.get(4),ChangeKind.BLOCKED,null,null,"类型冲突")));
                FileTree receive=new FileTree("目标目录");receive.setFiles(DirectorySync.changedFiles(preview),Set.of(paths.get(0),paths.get(1)),Map.of(paths.get(0),"修改",paths.get(1),"新增",paths.get(4),"无法应用 · 类型冲突"));
                JTree targetTree=findTree(receive);for(int row=0;row<targetTree.getRowCount();row++)targetTree.expandRow(row);
                JPanel right=new JPanel(new BorderLayout(0,10));right.add(new JLabel("接收预览：文件夹批量选择，仅同步可选项"),BorderLayout.NORTH);right.add(receive,BorderLayout.CENTER);panel.add(right);
                panel.setSize(1240,560);layout(panel);
                TreePath docs=path(tree,"docs",true);Rectangle bounds=tree.getPathBounds(docs);if(bounds==null)throw new AssertionError("Folder row not visible");
                MouseEvent click=new MouseEvent(tree,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,bounds.x+8,bounds.y+bounds.height/2,1,false,MouseEvent.BUTTON1);
                for(MouseListener listener:tree.getMouseListeners())if(listener.getClass().getName().startsWith(FileTree.class.getName()))listener.mousePressed(click);
                if(!send.selectedPaths().contains("docs/README.md"))throw new AssertionError("Checkbox mouse hit target did not select folder");
                if(!receive.selectedPaths().equals(Set.of(paths.get(0),paths.get(1))))throw new AssertionError("Blocked/unchanged items selected");
                assertHidden(targetTree,paths.get(2),false);assertHidden(targetTree,paths.get(3),false);assertHidden(targetTree,"docs",true);assertHidden(targetTree,"src/test",true);
                BufferedImage image=new BufferedImage(1240,560,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();panel.paint(graphics);graphics.dispose();
                Component cell=tree.getCellRenderer().getTreeCellRendererComponent(tree,tree.getModel().getRoot(),false,true,false,0,false);
                cell.setSize(400,26);BufferedImage rowImage=new BufferedImage(400,26,BufferedImage.TYPE_INT_RGB);Graphics2D rowGraphics=rowImage.createGraphics();cell.paint(rowGraphics);rowGraphics.dispose();
                for(Component child:((Container)cell).getComponents())if(child.getWidth()<=0||child.getHeight()<=0)throw new AssertionError("Tree renderer children are invisible after painting");
                Path output=Path.of("build/tree-selection-preview.png");Files.createDirectories(output.getParent());ImageIO.write(image,"png",output.toFile());
                System.out.println("PASS: actual FileTree construction, keyboard, folder/child selection, locking, checkbox click and receiver eligibility; preview "+output.toAbsolutePath());
            } catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static void key(JTree tree,TreePath path){tree.setSelectionPath(path);tree.getActionMap().get("toggle-directory-check").actionPerformed(new ActionEvent(tree,0,"space"));}
    private static void assertHidden(JTree tree,String relative,boolean directory){try{path(tree,relative,directory);}catch(AssertionError expected){return;}throw new AssertionError("Identical file or empty folder is visible: "+relative);}
    private static TreePath path(JTree tree,String relative,boolean directory){var root=(DefaultMutableTreeNode)tree.getModel().getRoot();var nodes=root.breadthFirstEnumeration();while(nodes.hasMoreElements()){var node=(DefaultMutableTreeNode)nodes.nextElement();var data=(DirectorySelection.Node)node.getUserObject();if(data.relativePath().equals(relative)&&data.directory()==directory)return new TreePath(node.getPath());}throw new AssertionError("Missing tree path "+relative);}
    private static JTree findTree(Container component){for(Component child:component.getComponents()){if(child instanceof JTree tree)return tree;if(child instanceof Container container){var found=findTree(container);if(found!=null)return found;}}return null;}
    private static void layout(Container container){container.doLayout();for(Component child:container.getComponents())if(child instanceof Container nested)layout(nested);}
}
