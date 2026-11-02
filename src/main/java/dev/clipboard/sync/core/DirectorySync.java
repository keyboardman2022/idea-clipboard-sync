package dev.clipboard.sync.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static dev.clipboard.sync.core.SyncModel.*;

public final class DirectorySync {
    private DirectorySync() {}
    /** Receiver display policy; keep the original snapshot and comparisons intact for applying and refreshing. */
    public static List<FileEntry> changedFiles(Preview preview) {
        Set<String> visible=new HashSet<>();
        for(var change:preview.changes())if(change.kind()!=ChangeKind.SAME)visible.add(change.relativePath());
        return preview.snapshot().files().stream().filter(file->visible.contains(file.relativePath())).toList();
    }
    public static Preview preview(Path targetRoot, Snapshot snapshot, BooleanSupplier cancelled) throws IOException {
        Path root = targetRoot.toAbsolutePath().normalize(); PathPolicy.checkAncestors(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("目标目录不存在");
        List<Change> changes = new ArrayList<>(); Set<String> blocked = conflictingPaths(snapshot);
        for (var entry : snapshot.files()) {
            CoreIo.checkCancelled(cancelled); String before = null; ChangeKind kind; String reason = "";
            try {
                if (blocked.contains(entry.relativePath())) throw new IOException("清单存在大小写、重复路径或文件/目录冲突");
                Path path = PathPolicy.resolve(root, entry.relativePath());
                if (!CoreIo.sha256(entry.content()).equals(entry.sha256())) throw new IOException("收到文件的摘要不符");
                if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("目标不是普通文件");
                    before = CoreIo.hashFile(path); kind = before.equals(entry.sha256()) ? ChangeKind.SAME : ChangeKind.MODIFY;
                } else kind = ChangeKind.ADD;
            } catch (IOException | InvalidPathException e) { kind = ChangeKind.BLOCKED; reason = e.getMessage(); }
            changes.add(new Change(entry.relativePath(), kind, before, entry.sha256(), reason));
        }
        return new Preview(root, snapshot, changes);
    }
    private static Set<String> conflictingPaths(Snapshot snapshot) {
        Map<String, String> prefixCase = new HashMap<>(); Set<String> conflicts = new HashSet<>(); Set<String> files = new HashSet<>();
        Set<String> seen = new HashSet<>();
        for (var entry : snapshot.files()) {
            String path = entry.relativePath(), prefix = "";
            if (!seen.add(path)) conflicts.add(path.toLowerCase(Locale.ROOT));
            for (String part : path.split("/")) {
                prefix = prefix.isEmpty() ? part : prefix + "/" + part;
                String key = prefix.toLowerCase(Locale.ROOT), previous = prefixCase.putIfAbsent(key, prefix);
                if (previous != null && !previous.equals(prefix)) conflicts.add(key);
            }
            files.add(path.toLowerCase(Locale.ROOT));
        }
        for (String path : files) { int slash = path.indexOf('/'); while (slash >= 0) { if (files.contains(path.substring(0, slash))) conflicts.add(path.substring(0, slash)); slash = path.indexOf('/', slash + 1); } }
        Set<String> result = new HashSet<>();
        for (var entry : snapshot.files()) {
            String folded = entry.relativePath().toLowerCase(Locale.ROOT);
            if (conflicts.stream().anyMatch(p -> folded.equals(p) || folded.startsWith(p + "/"))) result.add(entry.relativePath());
        }
        return result;
    }
    public static void verifyUnchanged(Path root, Change change) throws IOException {
        Path path = PathPolicy.resolve(root, change.relativePath()); boolean exists = Files.exists(path, LinkOption.NOFOLLOW_LINKS);
        if (change.kind() == ChangeKind.ADD) {
            if (exists) throw new IOException("预览后文件已被创建，请重新预览");
        } else if (!exists || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !CoreIo.hashFile(path).equals(change.beforeSha256())) throw new IOException("预览后文件已改变，请重新预览");
    }
}
