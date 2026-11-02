package dev.clipboard.sync.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static dev.clipboard.sync.core.SyncModel.*;
import static org.junit.jupiter.api.Assertions.*;

class NoBackupApplyTest {
    @TempDir Path root;
    @Test void overwritesAndAddsWithoutCreatingAnyBackupOrTouchingLegacyState() throws Exception {
        Files.writeString(root.resolve("existing.java"),"old");Files.createDirectories(root.resolve(".clipboard-sync"));
        Path legacy=root.resolve(".clipboard-sync/backups");Files.writeString(legacy,"legacy backup path deliberately not writable as directory");
        var files=List.of(entry("existing.java","new"),entry("nested/new.java","added"));
        var snapshot=new Snapshot(UUID.randomUUID(),"source",Instant.now(),files);
        var result=FileApplier.apply(DirectorySync.preview(root,snapshot,()->false),Set.of("existing.java","nested/new.java"),()->false);
        assertTrue(result.failed().isEmpty());assertEquals(2,result.succeeded().size());assertEquals("new",Files.readString(root.resolve("existing.java")));
        try(var paths=Files.walk(root)){assertEquals(Set.of("existing.java","nested/new.java",".clipboard-sync/backups"),paths.filter(Files::isRegularFile).map(p->root.relativize(p).toString().replace('\\','/')).collect(java.util.stream.Collectors.toSet()));}
        assertTrue(Files.readString(legacy).startsWith("legacy backup path"));
    }
    @Test void chosenFolderOnlyIsTransferredAndOtherTargetFilesArePreserved() throws Exception {
        var files=List.of(entry("chosen/a.java","a"),entry("chosen/sub/b.java","b"),entry("excluded/c.java","c"));
        var selection=new DirectorySelection(files);selection.setSelected(selection.root(),false);selection.setSelected(selection.node("chosen"),true);
        var snapshot=new Snapshot(UUID.randomUUID(),"source",Instant.now(),files.stream().filter(f->selection.selectedPaths().contains(f.relativePath())).toList());
        snapshot=SnapshotCodec.decode(SnapshotCodec.encode(snapshot,Limits.defaults()),Limits.defaults());
        Files.writeString(root.resolve("target-only.java"),"keep");
        var result=FileApplier.apply(DirectorySync.preview(root,snapshot,()->false),selection.selectedPaths(),()->false);
        assertTrue(result.failed().isEmpty());assertEquals("a",Files.readString(root.resolve("chosen/a.java")));assertEquals("b",Files.readString(root.resolve("chosen/sub/b.java")));
        assertFalse(Files.exists(root.resolve("excluded")));assertEquals("keep",Files.readString(root.resolve("target-only.java")));
    }
    private FileEntry entry(String path,String content){byte[] bytes=content.getBytes(java.nio.charset.StandardCharsets.UTF_8);return new FileEntry(path,bytes,CoreIo.sha256(bytes));}
}
