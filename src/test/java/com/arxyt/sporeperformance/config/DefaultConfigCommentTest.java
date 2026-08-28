package com.arxyt.sporeperformance.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultConfigCommentTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "/defaultconfigs/spore_performance-common.toml",
            "/defaultconfigs/spore_performance-client.toml"
    })
    void everyConfigurationEntryHasAnAdjacentChineseComment(String resource) throws IOException {
        try (InputStream input = DefaultConfigCommentTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            List<String> lines = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index).trim();
                if (!line.matches("[A-Za-z][A-Za-z0-9_.-]*\\s*=.*")) continue;
                assertTrue(index > 0 && lines.get(index - 1).trim().startsWith("#"),
                        resource + ":" + (index + 1) + " 配置项前缺少紧邻中文注释: " + line);
                assertTrue(lines.get(index - 1).codePoints().anyMatch(value -> value > 127),
                        resource + ":" + index + " 注释不含中文: " + lines.get(index - 1));
            }
        }
    }
}
