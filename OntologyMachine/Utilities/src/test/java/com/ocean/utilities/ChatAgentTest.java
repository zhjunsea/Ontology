package com.ocean.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChatAgent} 通用大模型对话封装单测（全离线，通过桩函数验证，不发起真实调用）。
 */
@DisplayName("ChatAgent · 可用性 / 桩调用 / 降级")
class ChatAgentTest {

    @Test
    @DisplayName("未配置 api-key 或 enabled=false 时不可用，chat 返回 null（降级）")
    void unavailableWithoutConfig() {
        ChatAgent c = new ChatAgent();
        c.configure(false, "", "", "stub", 5);
        assertFalse(c.isAvailable());
        assertNull(c.chat("sys", "user"));

        ChatAgent c2 = new ChatAgent();
        c2.configure(true, "https://api.example.com/v1", "", "stub", 5);
        assertFalse(c2.isAvailable(), "缺 api-key 应不可用");
        assertNull(c2.chat("sys", "user"));
    }

    @Test
    @DisplayName("注入桩函数后可用，chat 返回桩文本")
    void overrideStubReturnsText() {
        ChatAgent c = new ChatAgent((sys, user) -> "OK:" + user);
        assertTrue(c.isAvailable());
        assertEquals("OK:hi", c.chat("sys", "hi"));
        assertEquals("gpt-4o-mini", c.getModel(), "默认模型名");
    }

    @Test
    @DisplayName("桩函数抛异常时 chat 返回 null，不向外传播异常")
    void overrideExceptionReturnsNull() {
        ChatAgent c = new ChatAgent((sys, user) -> {
            throw new RuntimeException("boom");
        });
        assertNull(c.chat("s", "u"));
    }

    @Test
    @DisplayName("setOverride 可在构造后切换为可用")
    void setOverrideAfterConstruction() {
        ChatAgent c = new ChatAgent();
        c.configure(false, "", "", "stub", 5);
        assertNull(c.chat("s", "u"));
        c.setOverride((s, u) -> "X");
        assertTrue(c.isAvailable());
        assertEquals("X", c.chat("s", "u"));
    }

    @Test
    @DisplayName("内联 api-key 为空时回落外部 key 文件，可用性随之打开")
    void fallsBackToKeyFile(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("maas.key");
        Files.writeString(f, "file-key\n", StandardCharsets.UTF_8);

        ChatAgent fromFile = new ChatAgent();
        fromFile.configure(true, "https://api.example.com/v1", "", f.toString(), "stub", 5);
        assertTrue(fromFile.isAvailable(), "内联为空、文件有值时可用");

        ChatAgent inlineWins = new ChatAgent();
        inlineWins.configure(true, "https://api.example.com/v1", "inline", "bad/path", "stub", 5);
        assertTrue(inlineWins.isAvailable(), "内联优先：即使文件路径无效也可用");
    }
}
