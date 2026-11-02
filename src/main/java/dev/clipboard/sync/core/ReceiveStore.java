package dev.clipboard.sync.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static dev.clipboard.sync.core.SyncModel.*;

public final class ReceiveStore {
    private final Path stateRoot; private final Limits limits;
    private final Map<UUID, TreeMap<Integer, ClipboardChunks.Chunk>> activeParts = new HashMap<>();
    public record ReceiveProgress(UUID id, int total, Set<Integer> received, boolean complete) {
        public ReceiveProgress { received = Collections.unmodifiableSet(new TreeSet<>(received)); }
    }
    public ReceiveStore(Path stateRoot, Limits limits) { this.stateRoot = stateRoot.toAbsolutePath().normalize(); this.limits = limits; }
    private Path folder(UUID id) { return stateRoot.resolve(id.toString()); }
    public synchronized List<UUID> sessions() throws IOException {
        PathPolicy.checkAncestors(stateRoot);
        if (!Files.exists(stateRoot)) return List.of();
        try (var stream = Files.list(stateRoot)) { return stream.filter(p -> Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)).map(p -> p.getFileName().toString()).filter(s -> s.matches("[0-9a-f-]{36}")).map(UUID::fromString).sorted().toList(); }
    }
    public synchronized ReceiveProgress accept(String text) throws IOException {
        var chunk = ClipboardChunks.parse(text); Path directory = folder(chunk.id());
        PathPolicy.checkAncestors(directory);
        if (Files.exists(directory)) {
            var existing = activeParts.get(chunk.id());
            if (existing == null) { existing = parts(chunk.id()); activeParts.put(chunk.id(), existing); }
            if (!existing.isEmpty()) {
                var first = existing.firstEntry().getValue();
                if (first.total() != chunk.total() || !first.bundleSha256().equals(chunk.bundleSha256())) throw new IOException("该传输 ID 的数据与已收片段冲突");
                var same = existing.get(chunk.index());
                if (same != null) {
                    if (!same.equals(chunk)) throw new IOException("相同段号出现不同内容");
                    var persisted = ClipboardChunks.parse(new String(CoreIo.readFile(directory.resolve(chunk.index() + ".part"), 1 << 20), java.nio.charset.StandardCharsets.UTF_8));
                    if (!persisted.equals(chunk)) throw new IOException("已保存分段被修改，请清理会话后重试");
                    return progress(chunk.id(), existing);
                }
            }
        } else if (sessions().size() >= 10) throw new IOException("接收会话已达 10 个，请清理旧会话");
        var currentParts = activeParts.get(chunk.id());
        long sessionChars = currentParts == null ? 0 : currentParts.values().stream().mapToLong(c -> c.payload().length() + 256L).sum();
        if (sessionChars + text.length() > (150L << 20)) throw new IOException("单个接收会话超过 150 MiB");
        long stored = 0;
        if (Files.exists(stateRoot)) try (var paths = Files.walk(stateRoot)) {
            for (Path p : paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) stored += Files.size(p);
        }
        if (stored + text.length() > (300L << 20)) throw new IOException("接收缓存已达上限，请清理旧会话");
        CoreIo.atomicWrite(directory.resolve(chunk.index() + ".part"), text.strip().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var cached = activeParts.computeIfAbsent(chunk.id(), id -> new TreeMap<>());
        cached.put(chunk.index(), chunk);
        return progress(chunk.id(), cached);
    }
    private TreeMap<Integer, ClipboardChunks.Chunk> parts(UUID id) throws IOException {
        Path directory = folder(id);
        PathPolicy.checkAncestors(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("接收会话不存在");
        var map = new TreeMap<Integer, ClipboardChunks.Chunk>(); long totalChars = 0;
        try (var stream = Files.list(directory)) {
            for (Path path : stream.filter(p -> p.getFileName().toString().endsWith(".part")).toList()) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || map.size() >= 4096) throw new IOException("接收缓存无效");
                byte[] bytes = CoreIo.readFile(path, 1 << 20); totalChars += bytes.length;
                if (totalChars > (150L << 20)) throw new IOException("接收数据超限");
                var chunk = ClipboardChunks.parse(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
                if (!chunk.id().equals(id) || !path.getFileName().toString().equals(chunk.index() + ".part") || map.putIfAbsent(chunk.index(), chunk) != null) throw new IOException("接收缓存编号无效");
                var first = map.firstEntry().getValue();
                if (first.total() != chunk.total() || !first.bundleSha256().equals(chunk.bundleSha256())) throw new IOException("接收缓存混入其他传输");
            }
        }
        return map;
    }
    private ReceiveProgress progress(UUID id, TreeMap<Integer, ClipboardChunks.Chunk> parts) throws IOException {
        if (parts.isEmpty()) throw new IOException("接收会话为空");
        int total = parts.firstEntry().getValue().total(); return new ReceiveProgress(id, total, parts.keySet(), parts.size() == total);
    }
    public synchronized ReceiveProgress load(UUID id) throws IOException { var verified = parts(id); activeParts.put(id, verified); return progress(id, verified); }
    public synchronized Snapshot assemble(UUID id) throws IOException {
        var parts = parts(id); if (!progress(id, parts).complete()) throw new IOException("尚未收齐全部分段");
        StringBuilder data = new StringBuilder(); for (var part : parts.values()) data.append(part.payload());
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(data.toString()); } catch (IllegalArgumentException e) { throw new IOException("Base64 数据损坏", e); }
        if (!CoreIo.sha256(bytes).equals(parts.firstEntry().getValue().bundleSha256())) throw new IOException("整包摘要校验失败");
        var snapshot = SnapshotCodec.decode(bytes, limits); if (!snapshot.transferId().equals(id)) throw new IOException("快照 ID 不符");
        return snapshot;
    }
    public synchronized void cancel(UUID id) throws IOException {
        activeParts.remove(id);
        Path dir = folder(id); if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) return;
        PathPolicy.checkAncestors(dir);
        try (var paths = Files.walk(dir)) { for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); }
    }
}
