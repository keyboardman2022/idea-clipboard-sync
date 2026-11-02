package dev.clipboard.sync.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;
import static dev.clipboard.sync.core.SyncModel.*;

public final class SnapshotScanner {
    private static final Set<String> EXCLUDED = Set.of(".git", ".idea", "target", "build", "node_modules", ".clipboard-sync", ".vmsync");
    private SnapshotScanner() {}
    public static ScanResult scan(Path root, List<String> ignores, Limits limits, BooleanSupplier cancelled) throws IOException {
        root = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) throw new IOException("请选择普通目录");
        final Path base = root;
        List<Pattern> patterns = ignores.stream().filter(s -> !s.isBlank()).map(SnapshotScanner::glob).toList();
        List<FileEntry> files = new ArrayList<>(); List<String> skipped = new ArrayList<>(); long[] total = {0};
        Files.walkFileTree(base, new SimpleFileVisitor<>() {
            private String rel(Path p) { return base.relativize(p).toString().replace('\\', '/'); }
            private boolean ignored(Path p, boolean dir) {
                String r = rel(p);
                return EXCLUDED.contains(p.getFileName().toString()) || patterns.stream().anyMatch(m -> m.matcher(r).matches() || (dir && m.matcher(r + "/").matches()));
            }
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                CoreIo.checkCancelled(cancelled);
                if (!dir.equals(base) && ignored(dir, true)) { skipped.add(rel(dir) + "/（已排除）"); return FileVisitResult.SKIP_SUBTREE; }
                if (attrs.isSymbolicLink() || attrs.isOther()) { skipped.add(rel(dir) + "（链接）"); return FileVisitResult.SKIP_SUBTREE; }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes before) throws IOException {
                CoreIo.checkCancelled(cancelled);
                if (ignored(path, false) || !before.isRegularFile()) { skipped.add(rel(path) + "（已排除或链接）"); return FileVisitResult.CONTINUE; }
                if (before.size() > limits.maxFileBytes()) throw new IOException("单文件超限：" + rel(path));
                if (files.size() >= limits.maxFiles() || total[0] + before.size() > limits.maxTotalBytes()) throw new IOException("文件数量或总大小超限：" + rel(path));
                byte[] content = CoreIo.readFile(path, limits.maxFileBytes());
                var after = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!after.isRegularFile() || before.size() != content.length || after.size() != content.length || !before.lastModifiedTime().equals(after.lastModifiedTime()) || !Objects.equals(before.fileKey(), after.fileKey())) throw new IOException("扫描期间文件改变，请重试：" + rel(path));
                if (total[0] + content.length > limits.maxTotalBytes()) throw new IOException("目录总大小超限");
                total[0] += content.length;
                files.add(new FileEntry(rel(path), content, CoreIo.sha256(content)));
                return FileVisitResult.CONTINUE;
            }
        });
        CoreIo.checkCancelled(cancelled);
        files.sort(Comparator.comparing(FileEntry::relativePath));
        return new ScanResult(new Snapshot(UUID.randomUUID(), base.getFileName() == null ? base.toString() : base.getFileName().toString(), Instant.now(), files), skipped);
    }
    private static Pattern glob(String pattern) {
        pattern = pattern.replace('\\', '/');
        StringBuilder regex = new StringBuilder(pattern.contains("/") ? "^" : "^(?:.*/)?");
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '*') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '*') { regex.append(".*"); i++; }
                else regex.append("[^/]*");
            } else if (c == '?') regex.append("[^/]");
            else regex.append(Pattern.quote(String.valueOf(c)));
        }
        return Pattern.compile(regex.append('$').toString());
    }
}
