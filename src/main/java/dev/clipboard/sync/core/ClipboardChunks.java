package dev.clipboard.sync.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class ClipboardChunks {
    public static final String PREFIX = "IDEA-DIRSYNC/1";
    private ClipboardChunks() {}
    public record Chunk(UUID id, int index, int total, String bundleSha256, String partSha256, String payload) {}
    public static List<String> split(UUID id, byte[] compressed, int maxTextChars) throws IOException {
        if (maxTextChars < 4096 || maxTextChars > (1 << 20) || compressed.length > (110 << 20)) throw new IOException("分段大小或快照大小超限");
        String payload = Base64.getEncoder().encodeToString(compressed); int capacity = maxTextChars - 256;
        int total = Math.max(1, (payload.length() + capacity - 1) / capacity);
        if (total > 4096) throw new IOException("分段超过 4096 段，请增大分段大小或缩小目录");
        List<String> result = new ArrayList<>(); String bundleHash = CoreIo.sha256(compressed);
        for (int i = 0; i < total; i++) {
            String part = payload.substring(i * capacity, Math.min(payload.length(), (i + 1) * capacity));
            result.add(PREFIX + "|" + id + "|" + (i + 1) + "|" + total + "|" + bundleHash + "|" + CoreIo.sha256(part.getBytes(StandardCharsets.US_ASCII)) + "\n" + part);
        }
        return List.copyOf(result);
    }
    public static Chunk parse(String text) throws IOException {
        if (text == null || text.length() > (1 << 20)) throw new IOException("剪贴板不是同步数据或分段过大");
        text = text.strip(); int newline = text.indexOf('\n');
        if (newline < 0 || newline > 256) throw new IOException("剪贴板不是同步数据");
        String[] header = text.substring(0, newline).strip().split("\\|", -1); String payload = text.substring(newline + 1);
        if (header.length != 6 || !PREFIX.equals(header[0])) throw new IOException("未知同步格式或版本");
        try {
            UUID id = UUID.fromString(header[1]); int index = Integer.parseInt(header[2]), total = Integer.parseInt(header[3]);
            if (!id.toString().equals(header[1]) || total < 1 || total > 4096 || index < 1 || index > total || !header[4].matches("[0-9a-f]{64}") || !header[5].matches("[0-9a-f]{64}") || payload.isEmpty() || !payload.matches("[A-Za-z0-9+/]*={0,2}")) throw new IOException("分段字段无效");
            if (!CoreIo.sha256(payload.getBytes(StandardCharsets.US_ASCII)).equals(header[5])) throw new IOException("分段损坏或剪贴板被截断，请重发当前段");
            return new Chunk(id, index, total, header[4], header[5], payload);
        } catch (IllegalArgumentException e) { throw new IOException("分段字段无效", e); }
    }
}
