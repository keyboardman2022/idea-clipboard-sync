package dev.clipboard.sync.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import static dev.clipboard.sync.core.SyncModel.*;

public final class FileApplier {
    private FileApplier() {}
    @FunctionalInterface public interface DocumentGuard { boolean canApply(Set<Path> paths); }
    @FunctionalInterface public interface IoOperation { void run() throws IOException; }
    @FunctionalInterface public interface MutationRunner { void run(Path path, IoOperation operation) throws IOException; }
    public static ApplyResult applyGuarded(Preview preview, Set<String> selectedPaths, BooleanSupplier cancelled, DocumentGuard guard) throws IOException {
        return applyGuarded(preview, selectedPaths, cancelled, guard, (path, operation) -> operation.run());
    }
    public static ApplyResult applyGuarded(Preview preview, Set<String> selectedPaths, BooleanSupplier cancelled, DocumentGuard guard, MutationRunner mutations) throws IOException {
        Set<Path> paths = new HashSet<>();
        for (var change : preview.changes()) if (selectedPaths.contains(change.relativePath()) && (change.kind() == ChangeKind.ADD || change.kind() == ChangeKind.MODIFY)) paths.add(PathPolicy.resolve(preview.root(), change.relativePath()));
        if (!guard.canApply(Set.copyOf(paths))) throw new IOException("目标存在未保存的编辑内容，请先保存或处理");
        return applyInternal(preview, selectedPaths, cancelled, guard, mutations);
    }
    public static synchronized ApplyResult apply(Preview preview, Set<String> selectedPaths, BooleanSupplier cancelled) throws IOException {
        return applyInternal(preview, selectedPaths, cancelled, paths -> true, (path, operation) -> operation.run());
    }
    private static synchronized ApplyResult applyInternal(Preview preview, Set<String> selectedPaths, BooleanSupplier cancelled, DocumentGuard guard, MutationRunner mutations) throws IOException {
        List<Change> eligible = preview.changes().stream().filter(c -> selectedPaths.contains(c.relativePath()) && (c.kind() == ChangeKind.ADD || c.kind() == ChangeKind.MODIFY)).toList();
        List<Change> valid = new ArrayList<>(); Map<String,String> failures = new LinkedHashMap<>(); List<String> done = new ArrayList<>(), pending = new ArrayList<>();
        for (var change : eligible) { CoreIo.checkCancelled(cancelled); try { DirectorySync.verifyUnchanged(preview.root(), change); valid.add(change); } catch (IOException e) { failures.put(change.relativePath(), e.getMessage()); } }
        if (valid.isEmpty()) return new ApplyResult(done, failures, pending);
        Map<String,FileEntry> files = new HashMap<>(); for (var file : preview.snapshot().files()) files.put(file.relativePath(), file);
        boolean stopped = false;
        for (var change : valid) {
            String rel = change.relativePath();
            if (stopped || cancelled.getAsBoolean()) { stopped = true; pending.add(rel); continue; }
            try {
                DirectorySync.verifyUnchanged(preview.root(), change); Path path = PathPolicy.resolve(preview.root(), rel);
                byte[] content = files.get(rel).content(); if (!CoreIo.sha256(content).equals(change.afterSha256())) throw new IOException("收到的内容摘要发生变化");
                mutations.run(path, () -> { if (!guard.canApply(Set.of(path))) throw new IOException("目标产生了未保存编辑，请重新预览"); DirectorySync.verifyUnchanged(preview.root(), change); Files.createDirectories(path.getParent()); PathPolicy.resolve(preview.root(), rel); });
                Path temp = Files.createTempFile(path.getParent(), ".sync-", ".tmp");
                try {
                    Files.write(temp, content);
                    mutations.run(path, () -> { if (!guard.canApply(Set.of(path))) throw new IOException("目标产生了未保存编辑，请重新预览"); DirectorySync.verifyUnchanged(preview.root(), change); CoreIo.replace(temp, path); });
                }
                finally { Files.deleteIfExists(temp); }
                done.add(rel);
            } catch (IOException e) { failures.put(rel, e.getMessage()); }
        }
        return new ApplyResult(done, failures, pending);
    }
}
