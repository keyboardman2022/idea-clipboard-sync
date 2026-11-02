package dev.clipboard.sync.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static dev.clipboard.sync.core.SyncModel.*;

class DirectorySyncTest {
    @TempDir Path temp;
    Snapshot snap(String... pairs) {
        List<FileEntry> entries = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) { byte[] b = pairs[i+1].getBytes(java.nio.charset.StandardCharsets.UTF_8); entries.add(new FileEntry(pairs[i], b, CoreIo.sha256(b))); }
        return new Snapshot(UUID.randomUUID(), "source", Instant.now(), entries);
    }
    Path root() throws IOException { return Files.createDirectories(temp.resolve("target")); }
    @Test void addsNestedFilesUpdatesExistingAndPreservesDestinationOnlyFiles() throws Exception {
        Path target = root(); Files.writeString(target.resolve("same"), "same"); Files.writeString(target.resolve("change"), "old"); Files.writeString(target.resolve("keep"), "keep");
        var preview = DirectorySync.preview(target, snap("same","same","change","new","a/b/new.java","中文\r\n"), () -> false);
        assertEquals(List.of(ChangeKind.SAME, ChangeKind.MODIFY, ChangeKind.ADD), preview.changes().stream().map(Change::kind).toList());
        var result = FileApplier.apply(preview, Set.of("same","change","a/b/new.java"), () -> false);
        assertTrue(result.failed().isEmpty()); assertEquals(2, result.succeeded().size());
        assertEquals("new", Files.readString(target.resolve("change"))); assertEquals("中文\r\n", Files.readString(target.resolve("a/b/new.java")));
        assertEquals("keep", Files.readString(target.resolve("keep")));
        assertFalse(Files.exists(target.resolve(".clipboard-sync")));
    }
    @Test void stalePreviewCannotOverwriteNewChanges() throws Exception {
        Path target = root(); Files.writeString(target.resolve("a"), "old");
        var preview = DirectorySync.preview(target, snap("a","incoming","new","incoming"), () -> false);
        Files.writeString(target.resolve("a"), "edited after preview"); Files.writeString(target.resolve("new"), "created after preview");
        var result = FileApplier.apply(preview, Set.of("a","new"), () -> false);
        assertEquals(2, result.failed().size()); assertEquals("edited after preview", Files.readString(target.resolve("a")));
        assertEquals("created after preview", Files.readString(target.resolve("new")));
    }
    @Test void blocksTypeAndCaseConflicts() throws Exception {
        Path target = root(); Files.createDirectories(target.resolve("dir")); Files.writeString(target.resolve("file"), "old");
        var preview = DirectorySync.preview(target, snap("dir","data","file/child","data","A/x","1","a/y","2","UP","1","up","2"), () -> false);
        assertTrue(preview.changes().stream().allMatch(c -> c.kind() == ChangeKind.BLOCKED), preview.changes().toString());
    }
    @Test void rejectsPathTraversalAndWindowsInvalidNames() throws Exception {
        Path target = root();
        for (String path : List.of("../x", "/x", "C:/x", "a/../../x", "a\\x", "a//b", "a/./b")) assertThrows(IOException.class, () -> PathPolicy.resolve(target, path), path);
        for (String path : List.of("CON", "con.txt", "a/NUL.java", "x.", "x ", "a:b", "x?")) assertThrows(IOException.class, () -> PathPolicy.validateWindows(path), path);
        assertEquals(target.resolve("a/b.txt"), PathPolicy.resolve(target, "a/b.txt"));
    }
    @Test void guardsUnsavedDocumentsBeforeAnyWrite() throws Exception {
        Path target = root(); Files.writeString(target.resolve("a"), "old");
        var preview = DirectorySync.preview(target, snap("a","new"), () -> false);
        assertThrows(IOException.class, () -> FileApplier.applyGuarded(preview, Set.of("a"), () -> false, paths -> false));
        assertEquals("old", Files.readString(target.resolve("a"))); assertFalse(Files.exists(target.resolve(".clipboard-sync")));
    }
    @Test void entireDirectoryRoundTripViaClipboardBothDirections() throws Exception {
        Path a = Files.createDirectories(temp.resolve("a")), b = Files.createDirectories(temp.resolve("b")); Files.writeString(a.resolve("code.java"), "v1");
        transfer(a,b); assertEquals("v1", Files.readString(b.resolve("code.java")));
        Files.writeString(b.resolve("code.java"), "v2"); Files.createDirectory(b.resolve("sub")); Files.writeString(b.resolve("sub/new"), "added");
        transfer(b,a); assertEquals("v2", Files.readString(a.resolve("code.java"))); assertEquals("added", Files.readString(a.resolve("sub/new")));
    }
    @Test void guardRunsAgainAtCommitAndPreventsLateUnsavedChanges() throws Exception {
        Path target=root();Files.writeString(target.resolve("a"),"old");
        var preview=DirectorySync.preview(target,snap("a","new"),()->false);
        var checks=new java.util.concurrent.atomic.AtomicInteger();
        var result=FileApplier.applyGuarded(preview,Set.of("a"),()->false,paths->checks.incrementAndGet()==1,(path,operation)->operation.run());
        assertEquals(1,result.failed().size());assertEquals("old",Files.readString(target.resolve("a")));
    }
    @Test void moveFailureReportsCompletedAndUntouchedFiles() throws Exception {
        Path target=root();Files.writeString(target.resolve("a"),"old");Files.writeString(target.resolve("b"),"old");
        var preview=DirectorySync.preview(target,snap("a","new","b","new"),()->false);
        var result=FileApplier.applyGuarded(preview,Set.of("a","b"),()->false,paths->true,(path,operation)->{if(path.getFileName().toString().equals("b"))throw new IOException("simulated move failure");operation.run();});
        assertEquals(List.of("a"),result.succeeded());assertTrue(result.failed().containsKey("b"));assertEquals("old",Files.readString(target.resolve("b")));
        assertEquals("new",Files.readString(target.resolve("a")));assertFalse(Files.exists(target.resolve(".clipboard-sync")));
    }
    @Test void cancellationAfterOneFileLeavesTheRestPending() throws Exception {
        Path target=root();var preview=DirectorySync.preview(target,snap("a","new","b","new"),()->false);
        var cancelled=new java.util.concurrent.atomic.AtomicBoolean();
        var result=FileApplier.applyGuarded(preview,Set.of("a","b"),cancelled::get,paths->true,(path,operation)->{operation.run();cancelled.set(true);});
        assertEquals(List.of("a"),result.succeeded());assertEquals(List.of("b"),result.pending());assertFalse(Files.exists(target.resolve("b")));
    }
    @Test void updatingExecutablePreservesExistingPermissions() throws Exception {
        Path target=root();Path script=target.resolve("gradlew");Files.writeString(script,"old");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.getFileAttributeView(script,java.nio.file.attribute.PosixFileAttributeView.class)!=null,"POSIX permissions unavailable");
        var permissions=java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x");Files.setPosixFilePermissions(script,permissions);
        var result=FileApplier.apply(DirectorySync.preview(target,snap("gradlew","new"),()->false),Set.of("gradlew"),()->false);
        assertTrue(result.failed().isEmpty());assertEquals(permissions,Files.getPosixFilePermissions(script));assertEquals("new",Files.readString(script));
    }
    void transfer(Path source, Path target) throws Exception {
        var scan = SnapshotScanner.scan(source, List.of(), Limits.defaults(), () -> false);
        var store = new ReceiveStore(temp.resolve("receive"), Limits.defaults());
        for (String part : ClipboardChunks.split(scan.snapshot().transferId(), SnapshotCodec.encode(scan.snapshot(), Limits.defaults()), 4096)) store.accept(part);
        var preview = DirectorySync.preview(target, store.assemble(scan.snapshot().transferId()), () -> false);
        var selected = new HashSet<String>(); preview.changes().forEach(c -> selected.add(c.relativePath()));
        assertTrue(FileApplier.apply(preview, selected, () -> false).failed().isEmpty());
    }
}
