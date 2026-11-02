package dev.clipboard.sync.core;

import org.junit.jupiter.api.Test;
import java.util.*;
import static dev.clipboard.sync.core.SyncModel.*;
import static org.junit.jupiter.api.Assertions.*;

class DirectorySelectionTest {
    static List<FileEntry> files(String... paths) {
        return Arrays.stream(paths).map(p -> new FileEntry(p,new byte[]{1,2},CoreIo.sha256(new byte[]{1,2}))).toList();
    }
    @Test void buildsNestedStructureWithFoldersBeforeFiles() {
        var selection=new DirectorySelection(files("readme.txt","z/a.java","z/sub/b.java","a/c.java"));
        assertEquals(List.of("a","z","readme.txt"),selection.root().children().stream().map(DirectorySelection.Node::name).toList());
        assertEquals(List.of("sub","a.java"),selection.node("z").children().stream().map(DirectorySelection.Node::name).toList());
        assertEquals(4,selection.root().selectedCount());assertEquals(8,selection.root().selectedBytes());
    }
    @Test void folderSelectionOnlyChangesItsOwnDescendants() {
        var selection=new DirectorySelection(files("a/one.java","a/sub/two.java","ab/three.java","root.txt"));
        selection.setSelected(selection.node("a"),false);
        assertEquals(Set.of("ab/three.java","root.txt"),selection.selectedPaths());
        assertEquals(DirectorySelection.State.NONE,selection.node("a").state());
        assertEquals(DirectorySelection.State.PARTIAL,selection.root().state());
        selection.setSelected(selection.node("a"),true);assertEquals(4,selection.selectedPaths().size());
    }
    @Test void childChangesUpdateAllAncestorStatesAndPartialFolderSelectsAll() {
        var selection=new DirectorySelection(files("src/a.java","src/sub/b.java","src/sub/c.java"));
        selection.setSelected(selection.node("src/sub/b.java"),false);
        assertEquals(DirectorySelection.State.PARTIAL,selection.node("src/sub").state());assertEquals(DirectorySelection.State.PARTIAL,selection.root().state());
        selection.toggle(selection.node("src/sub"));assertEquals(DirectorySelection.State.ALL,selection.root().state());
        selection.toggle(selection.node("src"));assertTrue(selection.selectedPaths().isEmpty());assertEquals(0,selection.root().selectedBytes());
    }
    @Test void sameNamedFilesInDifferentFoldersAreIndependent() {
        var selection=new DirectorySelection(files("a/Config.java","b/Config.java"));
        selection.setSelected(selection.node("a"),false);assertEquals(Set.of("b/Config.java"),selection.selectedPaths());
    }
    @Test void receiverFolderSelectionCannotIncludeUnchangedOrBlockedFiles() {
        var selection=new DirectorySelection(files("src/new.java","src/same.java","src/blocked.java"),Set.of("src/new.java"));
        selection.setSelected(selection.root(),false);selection.setSelected(selection.node("src"),true);selection.setSelected(selection.node("src/blocked.java"),true);
        assertEquals(Set.of("src/new.java"),selection.selectedPaths());assertEquals(1,selection.root().selectableCount());assertEquals(3,selection.root().fileCount());
        assertFalse(selection.node("src/same.java").selectable());assertEquals(DirectorySelection.State.ALL,selection.node("src").state());
    }
    @Test void generatedTransferSelectionCanBeLocked() {
        var selection=new DirectorySelection(files("a.java"));selection.setLocked(true);
        assertThrows(IllegalStateException.class,()->selection.toggle(selection.root()));assertEquals(Set.of("a.java"),selection.selectedPaths());
    }
    @Test void emptyAndLargeTreesMaintainCorrectCounts() {
        var empty=new DirectorySelection(List.of());assertEquals(DirectorySelection.State.NONE,empty.root().state());assertFalse(empty.root().selectable());
        var paths=java.util.stream.IntStream.range(0,10_000).mapToObj(n->"folder"+(n%100)+"/file"+n).toArray(String[]::new);
        var selection=new DirectorySelection(files(paths));selection.setSelected(selection.root(),false);selection.setSelected(selection.node("folder7"),true);
        assertEquals(100,selection.root().selectedCount());assertEquals(200,selection.root().selectedBytes());
    }
}
