package dev.clipboard.sync.core;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class CoreIo {
    private CoreIo() {}
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException("操作已取消");
    }
    public static byte[] readLimited(InputStream in, long max) throws IOException {
        var out = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count;
        while ((count = in.read(buffer)) != -1) {
            if ((long)out.size() + count > max) throw new IOException("数据超过允许大小");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
    public static byte[] readFile(Path path, long max) throws IOException {
        try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) { return readLimited(in, max); }
    }
    public static String hashFile(Path path) throws IOException {
        try (var in = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] block = new byte[8192]; int count;
            while ((count = in.read(block)) != -1) digest.update(block, 0, count);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static void replace(Path temp, Path destination) throws IOException {
        if (Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) && Files.getFileAttributeView(destination, java.nio.file.attribute.PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setPosixFilePermissions(temp, Files.getPosixFilePermissions(destination, LinkOption.NOFOLLOW_LINKS));
        }
        try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
    }
    public static void atomicWrite(Path target, byte[] content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".sync-", ".tmp");
        try { Files.write(temp, content); replace(temp, target); }
        finally { Files.deleteIfExists(temp); }
    }
}
