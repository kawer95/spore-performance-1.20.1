package com.arxyt.sporeperformance.diagnostics;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RotatingTraceWriterTest {
    @Test
    void rotatesBeforeNormalTraceCanGrowPastConfiguredLimit() throws Exception {
        Path directory = Files.createTempDirectory("spore-trace-rotation");
        Path file = directory.resolve("trace.jsonl");
        try (RotatingTraceWriter writer = new RotatingTraceWriter(file, 32, 2)) {
            writer.writeLine("012345678901234567890123");
            writer.writeLine("abcdefghijklmnopqrstuvwxyz");
            writer.flush();
        }

        assertTrue(Files.exists(file));
        assertTrue(Files.exists(directory.resolve("trace.jsonl.1")));
        assertTrue(Files.size(file) <= 32);
        assertTrue(Files.size(directory.resolve("trace.jsonl.1")) <= 32);
    }
}
