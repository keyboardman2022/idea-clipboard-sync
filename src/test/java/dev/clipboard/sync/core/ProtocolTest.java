package dev.clipboard.sync.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static dev.clipboard.sync.core.SyncModel.*;

class ProtocolTest {
    @TempDir Path temp;
    static Snapshot snapshot(String path, byte[] content) {
        return new Snapshot(UUID.randomUUID(), "目录", Instant.parse("2026-10-23T00:00:00Z"), List.of(new FileEntry(path, content, CoreIo.sha256(content))));
    }
    @Test void roundTripPreservesLongBinaryFile() throws Exception {
        byte[] content = new byte[100_000]; new Random(17).nextBytes(content);
        var original = snapshot("子目录/file.bin", content);
        var decoded = SnapshotCodec.decode(SnapshotCodec.encode(original, Limits.defaults()), Limits.defaults());
        assertEquals(original.transferId(), decoded.transferId()); assertEquals(original.rootName(), decoded.rootName());
        assertArrayEquals(content, decoded.files().getFirst().content());
    }
    @Test void emptySnapshotRoundTrip() throws Exception {
        var s = new Snapshot(UUID.randomUUID(), "empty", Instant.now(), List.of());
        assertTrue(SnapshotCodec.decode(SnapshotCodec.encode(s, Limits.defaults()), Limits.defaults()).files().isEmpty());
    }
    @Test void rejectsWrongHashAndDuplicatePaths() {
        var e = new FileEntry("a", new byte[]{1}, "0".repeat(64));
        assertThrows(IOException.class, () -> SnapshotCodec.encode(new Snapshot(UUID.randomUUID(), "x", Instant.now(), List.of(e)), Limits.defaults()));
        var s = snapshot("a", new byte[]{1});
        assertThrows(IOException.class, () -> SnapshotCodec.encode(new Snapshot(s.transferId(), "x", s.createdAt(), List.of(s.files().getFirst(), s.files().getFirst())), Limits.defaults()));
    }
    @Test void rejectsTruncationCorruptionAndOversize() throws Exception {
        byte[] encoded = SnapshotCodec.encode(snapshot("a", new byte[2000]), Limits.defaults());
        assertThrows(IOException.class, () -> SnapshotCodec.decode(Arrays.copyOf(encoded, encoded.length / 2), Limits.defaults()));
        assertThrows(IOException.class, () -> SnapshotCodec.decode(encoded, new Limits(100, 100, 1)));
        byte[] garbage = Arrays.copyOf(encoded, encoded.length + 1); garbage[garbage.length - 1] = 3;
        assertThrows(IOException.class, () -> SnapshotCodec.decode(garbage, Limits.defaults()));
    }
    @Test void splitsAndReassemblesOutOfOrderAcrossRestart() throws Exception {
        byte[] bytes = new byte[25_000]; new Random(19).nextBytes(bytes);
        var snapshot = snapshot("a", bytes);
        var chunks = ClipboardChunks.split(snapshot.transferId(), SnapshotCodec.encode(snapshot, Limits.defaults()), 4096);
        assertTrue(chunks.size() > 1); assertTrue(chunks.stream().allMatch(c -> c.length() <= 4096));
        var store = new ReceiveStore(temp, Limits.defaults());
        var first = store.accept(chunks.getLast()); assertFalse(first.complete());
        assertThrows(IOException.class, () -> store.assemble(snapshot.transferId()));
        var restarted = new ReceiveStore(temp, Limits.defaults());
        for (String chunk : chunks) restarted.accept(chunk);
        assertTrue(restarted.load(snapshot.transferId()).complete());
        assertArrayEquals(bytes, restarted.assemble(snapshot.transferId()).files().getFirst().content());
        int count = restarted.accept(chunks.getFirst()).received().size(); assertEquals(chunks.size(), count);
    }
    @Test void corruptionAndConflictingSameIndexDoNotReplaceReceivedData() throws Exception {
        var s = snapshot("a", new byte[]{1});
        var chunks = ClipboardChunks.split(s.transferId(), SnapshotCodec.encode(s, Limits.defaults()), 4096);
        var store = new ReceiveStore(temp, Limits.defaults()); store.accept(chunks.getFirst());
        String bad = chunks.getFirst().substring(0, chunks.getFirst().length() - 1) + "!";
        assertThrows(IOException.class, () -> store.accept(bad));
        var another = new Snapshot(s.transferId(), "changed", s.createdAt(), s.files());
        String conflict = ClipboardChunks.split(s.transferId(), SnapshotCodec.encode(another, Limits.defaults()), 4096).getFirst();
        assertThrows(IOException.class, () -> store.accept(conflict));
        assertEquals("目录", store.assemble(s.transferId()).rootName());
    }
    @Test void sessionsAreIsolatedAndCancelIsScoped() throws Exception {
        var a = snapshot("a", new byte[]{1}); var b = snapshot("b", new byte[]{2});
        var store = new ReceiveStore(temp, Limits.defaults());
        store.accept(ClipboardChunks.split(a.transferId(), SnapshotCodec.encode(a, Limits.defaults()), 4096).getFirst());
        store.accept(ClipboardChunks.split(b.transferId(), SnapshotCodec.encode(b, Limits.defaults()), 4096).getFirst());
        store.cancel(a.transferId());
        assertThrows(IOException.class, () -> store.load(a.transferId())); assertTrue(store.load(b.transferId()).complete());
    }
    @Test void rejectsOrdinaryClipboardAndUnknownVersion() {
        assertThrows(IOException.class, () -> ClipboardChunks.parse("hello code"));
        assertThrows(IOException.class, () -> ClipboardChunks.parse("IDEA-DIRSYNC/99\nwhatever"));
    }
}
