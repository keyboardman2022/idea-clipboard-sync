package dev.clipboard.sync.core;

import java.util.*;
import static dev.clipboard.sync.core.SyncModel.*;

/** File selection with directory aggregation; paths always remain relative to the chosen root. */
public final class DirectorySelection {
    public enum State { NONE, PARTIAL, ALL }
    public static final class Node {
        private final DirectorySelection owner;
        private final String path, name;
        private final boolean directory;
        private final Node parent;
        private final long size;
        private List<Node> children=new ArrayList<>();
        private int fileCount, selectableCount, selectedCount;
        private long selectedBytes;
        private Node(DirectorySelection owner,String path,String name,boolean directory,Node parent,long size) {
            this.owner=owner;this.path=path;this.name=name;this.directory=directory;this.parent=parent;this.size=size;
        }
        public String relativePath(){return path;}
        public String name(){return name;}
        public boolean directory(){return directory;}
        public List<Node> children(){return children;}
        public int fileCount(){return fileCount;}
        public int selectableCount(){return selectableCount;}
        public int selectedCount(){return selectedCount;}
        public long selectedBytes(){return selectedBytes;}
        public long size(){return size;}
        public boolean selectable(){return selectableCount>0;}
        public State state(){return selectedCount==0?State.NONE:selectedCount==selectableCount?State.ALL:State.PARTIAL;}
        @Override public String toString(){return name;}
    }
    private final Node root=new Node(this,"","",true,null,0);
    private final Map<String,Node> directories=new HashMap<>(), files=new HashMap<>();
    private final Set<String> selected=new LinkedHashSet<>();
    private boolean locked;
    public DirectorySelection(List<FileEntry> entries){this(entries,entries.stream().map(FileEntry::relativePath).collect(java.util.stream.Collectors.toSet()));}
    public DirectorySelection(List<FileEntry> entries,Set<String> selectablePaths){
        directories.put("",root);List<Node> created=new ArrayList<>();created.add(root);
        for(var entry:entries){
            try{SnapshotCodec.validatePath(entry.relativePath());}catch(java.io.IOException e){throw new IllegalArgumentException(e.getMessage(),e);}
            String[] parts=entry.relativePath().split("/");Node parent=root;String prefix="";
            for(int i=0;i<parts.length-1;i++){
                prefix=prefix.isEmpty()?parts[i]:prefix+"/"+parts[i];Node directory=directories.get(prefix);
                if(directory==null){directory=new Node(this,prefix,parts[i],true,parent,0);directories.put(prefix,directory);parent.children.add(directory);created.add(directory);}parent=directory;
            }
            Node leaf=new Node(this,entry.relativePath(),parts[parts.length-1],false,parent,entry.size());
            if(files.putIfAbsent(entry.relativePath(),leaf)!=null)throw new IllegalArgumentException("重复文件路径："+entry.relativePath());
            leaf.fileCount=1;
            if(selectablePaths.contains(entry.relativePath())){leaf.selectableCount=1;leaf.selectedCount=1;leaf.selectedBytes=entry.size();selected.add(entry.relativePath());}
            parent.children.add(leaf);created.add(leaf);
        }
        Comparator<Node> order=Comparator.comparing(Node::directory).reversed().thenComparing(Node::name,String.CASE_INSENSITIVE_ORDER).thenComparing(Node::name);
        for(int i=created.size()-1;i>=0;i--){Node node=created.get(i);if(node.directory){node.children.sort(order);node.children=List.copyOf(node.children);}else node.children=List.of();
            if(node.parent!=null){node.parent.fileCount+=node.fileCount;node.parent.selectableCount+=node.selectableCount;node.parent.selectedCount+=node.selectedCount;node.parent.selectedBytes+=node.selectedBytes;}
        }
    }
    public Node root(){return root;}
    public Node node(String path){Node node=directories.get(path);if(node==null)node=files.get(path);if(node==null)throw new IllegalArgumentException("未知路径："+path);return node;}
    public Set<String> selectedPaths(){return Collections.unmodifiableSet(new LinkedHashSet<>(selected));}
    public boolean locked(){return locked;}
    public void setLocked(boolean locked){this.locked=locked;}
    public void toggle(Node node){setSelected(node,node.state()!=State.ALL);}
    public void setSelected(Node node,boolean value){
        if(locked)throw new IllegalStateException("同步数据已生成，选择已锁定");
        if(node==null||node.owner!=this)throw new IllegalArgumentException("节点不属于当前目录");
        Deque<Node> pending=new ArrayDeque<>();pending.push(node);
        while(!pending.isEmpty()){
            Node current=pending.pop();
            if(current.directory){for(Node child:current.children)pending.push(child);continue;}
            if(!current.selectable())continue;
            boolean changed=value?selected.add(current.path):selected.remove(current.path);
            if(changed)for(Node ancestor=current;ancestor!=null;ancestor=ancestor.parent){ancestor.selectedCount+=value?1:-1;ancestor.selectedBytes+=value?current.size:-current.size;}
        }
    }
}
