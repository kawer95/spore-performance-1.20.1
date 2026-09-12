package com.arxyt.sporeperformance.diagnostics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Append-only JSONL writer with a hard per-file size bound and numbered backups.
 *
 * <p>Only the diagnostics writer thread owns an instance.  Rotation happens before a line
 * would exceed the limit, so the game thread never performs filesystem work and a malformed
 * or unexpectedly dense trace cannot grow without bound.  A single line larger than the limit
 * is still written intact; this preserves diagnostic records while keeping the normal case
 * bounded.</p>
 */
final class RotatingTraceWriter implements AutoCloseable {
    private final Path file;
    private final long maxBytes;
    private final int backupFiles;
    private BufferedWriter writer;
    private long bytes;

    RotatingTraceWriter(Path file, long maxBytes, int backupFiles) throws IOException {
        this.file = file;
        this.maxBytes = Math.max(1L, maxBytes);
        this.backupFiles = Math.max(1, backupFiles);
        Files.createDirectories(file.toAbsolutePath().getParent());
        if (Files.exists(file) && Files.size(file) > this.maxBytes) rotate();
        open();
    }

    void writeLine(String line) throws IOException {
        String value = line == null ? "" : line;
        byte[] encoded = (value + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes > 0L && bytes + encoded.length > maxBytes) {
            writer.flush();
            writer.close();
            rotate();
            open();
        }
        writer.write(value);
        writer.newLine();
        bytes += encoded.length;
    }

    void flush() throws IOException {
        writer.flush();
    }

    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }

    private void open() throws IOException {
        writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        bytes = Files.size(file);
    }

    private void rotate() throws IOException {
        // Keep the newest backup at .1 and discard only the oldest configured backup.
        Path oldest = backupPath(backupFiles);
        Files.deleteIfExists(oldest);
        for (int index = backupFiles - 1; index >= 1; index--) {
            Path source = backupPath(index);
            if (Files.exists(source)) Files.move(source, backupPath(index + 1), StandardCopyOption.REPLACE_EXISTING);
        }
        if (Files.exists(file)) Files.move(file, backupPath(1), StandardCopyOption.REPLACE_EXISTING);
    }

    private Path backupPath(int index) {
        return file.resolveSibling(file.getFileName() + "." + index);
    }
}
