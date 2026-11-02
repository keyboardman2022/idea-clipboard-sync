package dev.clipboard.sync.core;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import static dev.clipboard.sync.core.SyncModel.*;

public final class SnapshotCodec {
    private static final int MAGIC = 0x44534331, META_MAX = 10 << 20;
    private SnapshotCodec() {}
    public static byte[] encode(Snapshot snapshot, Limits limits) throws IOException {
        if (snapshot.files().size() > limits.maxFiles()) throw new IOException("文件数量超限");
        var raw = new ByteArrayOutputStream(); long total = 0; Set<String> paths = new HashSet<>();
        try (var data = new DataOutputStream(raw)) {
            data.writeInt(1); writeString(data, snapshot.transferId().toString(), 64); writeString(data, snapshot.rootName(), 1024);
            data.writeLong(snapshot.createdAt().toEpochMilli()); data.writeInt(snapshot.files().size());
            for (var entry : snapshot.files()) {
                validatePath(entry.relativePath());
                if (!paths.add(entry.relativePath())) throw new IOException("清单路径重复");
                byte[] content = entry.content(); total += content.length;
                if (content.length > limits.maxFileBytes() || total > limits.maxTotalBytes()) throw new IOException("文件大小超限");
                if (!CoreIo.sha256(content).equals(entry.sha256())) throw new IOException("文件摘要不符");
                writeString(data, entry.relativePath(), 4096); writeString(data, entry.sha256(), 64); data.writeInt(content.length); data.write(content);
                if ((long)raw.size() > limits.maxTotalBytes() + META_MAX) throw new IOException("快照元数据超限");
            }
        }
        var zipped = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(zipped)) { raw.writeTo(gzip); }
        byte[] payload = zipped.toByteArray(); var result = new ByteArrayOutputStream();
        try (var data = new DataOutputStream(result)) { data.writeInt(MAGIC); data.writeInt(payload.length); data.write(payload); }
        return result.toByteArray();
    }
    public static Snapshot decode(byte[] compressed, Limits limits) throws IOException {
        try {
            if (compressed.length > (150 << 20)) throw new IOException("压缩数据超限");
            var framed = new DataInputStream(new ByteArrayInputStream(compressed));
            if (framed.readInt() != MAGIC) throw new IOException("无效快照格式");
            int length = framed.readInt();
            if (length < 0 || length != compressed.length - 8) throw new IOException("压缩长度不符或存在多余数据");
            byte[] raw;
            try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed, 8, length))) { raw = CoreIo.readLimited(gzip, limits.maxTotalBytes() + META_MAX); }
            var data = new DataInputStream(new ByteArrayInputStream(raw));
            if (data.readInt() != 1) throw new IOException("不支持的快照版本");
            UUID id = UUID.fromString(readString(data, 64)); String name = readString(data, 1024);
            Instant instant = Instant.ofEpochMilli(data.readLong()); int count = data.readInt();
            if (count < 0 || count > limits.maxFiles()) throw new IOException("文件数量超限");
            List<FileEntry> files = new ArrayList<>(); Set<String> paths = new HashSet<>(); long total = 0;
            for (int i = 0; i < count; i++) {
                String path = readString(data, 4096); validatePath(path);
                if (!paths.add(path)) throw new IOException("清单路径重复");
                String hash = readString(data, 64); int size = data.readInt();
                if (size < 0 || size > limits.maxFileBytes() || size > data.available() || (total += size) > limits.maxTotalBytes()) throw new IOException("文件大小超限或数据截断");
                byte[] bytes = data.readNBytes(size);
                if (!CoreIo.sha256(bytes).equals(hash)) throw new IOException("文件摘要不符：" + path);
                files.add(new FileEntry(path, bytes, hash));
            }
            if (data.available() != 0) throw new IOException("快照包含多余数据");
            return new Snapshot(id, name, instant, files);
        } catch (IllegalArgumentException e) { throw new IOException("快照字段无效", e); }
    }
    static void validatePath(String path) throws IOException {
        if (path.isBlank() || path.startsWith("/") || path.contains("\\") || path.contains(":")) throw new IOException("无效相对路径：" + path);
        for (String part : path.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..") || part.chars().anyMatch(c -> c < 32)) throw new IOException("无效路径段");
    }
    static void writeString(DataOutputStream out, String value, int max) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8); if (bytes.length > max) throw new IOException("文本字段过长");
        out.writeInt(bytes.length); out.write(bytes);
    }
    static String readString(DataInputStream in, int max) throws IOException {
        int length = in.readInt(); if (length < 0 || length > max || length > in.available()) throw new IOException("文本字段长度无效");
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(in.readNBytes(length))).toString();
    }
}
