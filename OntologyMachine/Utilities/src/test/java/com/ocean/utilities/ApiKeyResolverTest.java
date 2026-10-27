package com.ocean.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ApiKeyResolver} 单测（全离线，临时文件验证，不发起任何 HTTP）。
 */
@DisplayName("ApiKeyResolver · yaml 优先 / 外部文件兜底")
class ApiKeyResolverTest {

    @Test
    @DisplayName("内联 key 非空时优先，且去除首尾空白")
    void inlineKeyWins() {
        assertEquals("inline-key", ApiKeyResolver.resolve("  inline-key  ", "any/path"));
        assertEquals("inline-key", ApiKeyResolver.resolve("  inline-key  ", ""));
    }

    @Test
    @DisplayName("内联为空时回落外部文件首行")
    void fallsBackToFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("maas.key");
        Files.writeString(f, "file-key\n", StandardCharsets.UTF_8);
        assertEquals("file-key", ApiKeyResolver.resolve("", f.toString()));
        assertEquals("file-key", ApiKeyResolver.resolve(null, f.toString()));
        assertEquals("file-key", ApiKeyResolver.resolve("   ", f.toString()));
    }

    @Test
    @DisplayName("文件取首个非空行并去除 BOM 与空白")
    void skipsBlankLinesAndBom(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("maas.key");
        Files.writeString(f, "\n  \n\uFEFF  real-key  \nsecond\n", StandardCharsets.UTF_8);
        assertEquals("real-key", ApiKeyResolver.resolve("", f.toString()));
    }

    @Test
    @DisplayName("文件缺失 / 为空 / 路径为空时返回空串（降级）")
    void degradesToEmpty(@TempDir Path dir) throws Exception {
        assertEquals("", ApiKeyResolver.resolve("", dir.resolve("nope.key").toString()));
        Path empty = dir.resolve("empty.key");
        Files.writeString(empty, "   \n\n", StandardCharsets.UTF_8);
        assertEquals("", ApiKeyResolver.resolve("", empty.toString()));
        assertEquals("", ApiKeyResolver.resolve("", ""));
        assertEquals("", ApiKeyResolver.resolve(null, null));
    }
}
