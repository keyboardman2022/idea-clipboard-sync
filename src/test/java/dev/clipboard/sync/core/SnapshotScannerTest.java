package dev.clipboard.sync.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;
import static dev.clipboard.sync.core.SyncModel.*;

class SnapshotScannerTest {
    @TempDir Path root;
    @Test void scansNestedFilesAndPreservesBytes() throws Exception {
        Files.createDirectories(root.resolve("a/b"));
        byte[] text = "中文\r\nlast\n".getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve("a/b/file.txt"), text);
        Files.write(root.resolve("binary.bin"), new byte[]{0, -1, 3});
        var files = SnapshotScanner.scan(root, List.of(), Limits.defaults(), () -> false).snapshot().files();
        assertEquals(List.of("a/b/file.txt", "binary.bin"), files.stream().map(FileEntry::relativePath).toList());
        assertArrayEquals(text, files.getFirst().content());
        assertArrayEquals(new byte[]{0, -1, 3}, files.getLast().content());
    }
    @Test void prunesIgnoredSubtrees() throws Exception {
        for (String dir : List.of(".git", ".idea", "target", "nested/build", "node_modules", ".clipboard-sync", "custom/tmp")) {
            Files.createDirectories(root.resolve(dir)); Files.writeString(root.resolve(dir + "/x"), "ignore");
        }
        Files.writeString(root.resolve("keep.java"), "yes");
        Files.writeString(root.resolve("skip.log"), "no");
        var result = SnapshotScanner.scan(root, List.of("*.log", "custom/**"), Limits.defaults(), () -> false);
        assertEquals(List.of("keep.java"), result.snapshot().files().stream().map(FileEntry::relativePath).toList());
        assertFalse(result.skipped().isEmpty());
    }
    @Test void enforcesLimits() throws Exception {
        Files.write(root.resolve("a"), new byte[]{1, 2, 3});
        assertEquals(1, SnapshotScanner.scan(root, List.of(), new Limits(3, 3, 1), () -> false).snapshot().files().size());
        assertThrows(java.io.IOException.class, () -> SnapshotScanner.scan(root, List.of(), new Limits(2, 5, 2), () -> false));
        Files.write(root.resolve("b"), new byte[]{4});
        assertThrows(java.io.IOException.class, () -> SnapshotScanner.scan(root, List.of(), new Limits(3, 3, 2), () -> false));
        assertThrows(java.io.IOException.class, () -> SnapshotScanner.scan(root, List.of(), new Limits(3, 5, 1), () -> false));
    }
    @Test void cancellationDoesNotReturnPartialSnapshot() throws Exception {
        Files.writeString(root.resolve("a"), "a");
        assertThrows(CancellationException.class, () -> SnapshotScanner.scan(root, List.of(), Limits.defaults(), () -> true));
    }
    @Test void doesNotFollowSymbolicLinks() throws Exception {
        Files.writeString(root.resolve("real"), "a");
        try { Files.createSymbolicLink(root.resolve("link"), root.resolve("real")); }
        catch (Exception e) { org.junit.jupiter.api.Assumptions.abort("No link privilege: " + e.getMessage()); }
        var scan = SnapshotScanner.scan(root, List.of(), Limits.defaults(), () -> false);
        assertEquals(1, scan.snapshot().files().size());
        assertTrue(scan.skipped().stream().anyMatch(s -> s.contains("link")));
    }
}
