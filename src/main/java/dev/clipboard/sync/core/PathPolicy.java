package dev.clipboard.sync.core;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public final class PathPolicy {
    private PathPolicy() {}
    public static boolean windows() { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"); }
    public static void validateWindows(String path) throws IOException {
        for (String part : path.split("/", -1)) {
            String stem = part.split("\\.", 2)[0].toUpperCase(Locale.ROOT);
            if (part.endsWith(".") || part.endsWith(" ") || part.matches(".*[<>:\"|?*].*") || stem.matches("CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³]")) throw new IOException("Windows 不支持的文件名：" + part);
        }
    }
    public static Path resolve(Path root, String relative) throws IOException {
        SnapshotCodec.validatePath(relative); if (windows()) validateWindows(relative);
        for (String part : relative.split("/")) if (part.equalsIgnoreCase(".clipboard-sync")) throw new IOException("禁止写入插件状态目录");
        Path base = root.toAbsolutePath().normalize(); Path path;
        try { path = base.resolve(relative).normalize(); } catch (InvalidPathException e) { throw new IOException("目标文件名无效", e); }
        if (!path.startsWith(base) || path.equals(base)) throw new IOException("目标路径越界");
        checkAncestors(path);
        return path;
    }
    public static void checkAncestors(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize(); Path cursor = absolute.getRoot();
        for (Path part : absolute) {
            cursor = cursor.resolve(part);
            if (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) continue;
            var attrs = Files.readAttributes(cursor, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attrs.isSymbolicLink() || attrs.isOther()) throw new IOException("路径包含链接或特殊文件：" + cursor);
            if (!cursor.equals(absolute) && !attrs.isDirectory()) throw new IOException("父路径不是目录：" + cursor);
        }
    }
}
