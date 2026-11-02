package dev.clipboard.sync.core;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

public final class SyncModel {
    private SyncModel() {}
    public record Limits(long maxFileBytes, long maxTotalBytes, int maxFiles) {
        public Limits {
            if (maxFileBytes < 0 || maxTotalBytes < 0 || maxFiles < 0) throw new IllegalArgumentException("Invalid limits");
        }
        public static Limits defaults() { return new Limits(10L << 20, 100L << 20, 10_000); }
    }
    public record FileEntry(String relativePath, byte[] content, String sha256) {
        public FileEntry { Objects.requireNonNull(relativePath); content = content.clone(); Objects.requireNonNull(sha256); }
        @Override public byte[] content() { return content.clone(); }
        public int size() { return content.length; }
    }
    public record Snapshot(UUID transferId, String rootName, Instant createdAt, List<FileEntry> files) {
        public Snapshot { Objects.requireNonNull(transferId); Objects.requireNonNull(rootName); Objects.requireNonNull(createdAt); files = List.copyOf(files); }
        public long totalBytes() { return files.stream().mapToLong(FileEntry::size).sum(); }
    }
    public record ScanResult(Snapshot snapshot, List<String> skipped) {
        public ScanResult { skipped = List.copyOf(skipped); }
    }
    public enum ChangeKind { ADD, MODIFY, SAME, BLOCKED }
    public record Change(String relativePath, ChangeKind kind, String beforeSha256, String afterSha256, String reason) {}
    public record Preview(Path root, Snapshot snapshot, List<Change> changes) {
        public Preview { root = root.toAbsolutePath().normalize(); changes = List.copyOf(changes); }
    }
    public record ApplyResult(List<String> succeeded, Map<String, String> failed, List<String> pending) {
        public ApplyResult { succeeded = List.copyOf(succeeded); failed = Collections.unmodifiableMap(new LinkedHashMap<>(failed)); pending = List.copyOf(pending); }
    }
}
