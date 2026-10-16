package com.ocean.utilities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LlmClient} 通用大模型客户端单测（全离线，通过桩函数验证，不发起真实 HTTP）。
 */
@DisplayName("LlmClient · 可用性 / 桩调用 / 降级")
class LlmClientTest {

    @Test
    @DisplayName("未配置 api-key 或 enabled=false 时不可用，chat 返回 null（降级）")
    void unavailableWithoutConfig() {
        LlmClient c = new LlmClient();
        c.configure(false, "", "", "stub", 5);
        assertFalse(c.isAvailable());
        assertNull(c.chat("sys", "user"));

        LlmClient c2 = new LlmClient();
        c2.configure(true, "https://api.example.com/v1", "", "stub", 5);
        assertFalse(c2.isAvailable(), "缺 api-key 应不可用");
        assertNull(c2.chat("sys", "user"));
    }

    @Test
    @DisplayName("注入桩函数后可用，chat 返回桩文本")
    void overrideStubReturnsText() {
        LlmClient c = new LlmClient((sys, user) -> "OK:" + user);
        assertTrue(c.isAvailable());
        assertEquals("OK:hi", c.chat("sys", "hi"));
        assertEquals("gpt-4o-mini", c.getModel(), "默认模型名");
    }

    @Test
    @DisplayName("桩函数抛异常时 chat 返回 null，不向外传播异常")
    void overrideExceptionReturnsNull() {
        LlmClient c = new LlmClient((sys, user) -> {
            throw new RuntimeException("boom");
        });
        assertNull(c.chat("s", "u"));
    }

    @Test
    @DisplayName("setOverride 可在构造后切换为可用")
    void setOverrideAfterConstruction() {
        LlmClient c = new LlmClient();
        c.configure(false, "", "", "stub", 5);
        assertNull(c.chat("s", "u"));
        c.setOverride((s, u) -> "X");
        assertTrue(c.isAvailable());
        assertEquals("X", c.chat("s", "u"));
    }
}
