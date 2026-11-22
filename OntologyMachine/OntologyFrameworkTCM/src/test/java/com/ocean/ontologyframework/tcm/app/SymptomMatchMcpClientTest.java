package com.ocean.ontologyframework.tcm.app;

import com.ocean.utilities.McpClientHandler;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SymptomMatchMcpClient}（L4 兜底 MCP 客户端）离线单元测试。
 *
 * <p>覆盖：可用性判定（override / enabled+url / 禁用 / 空地址）、override 测试桩（成功/异常）、
 * 未启用直接降级、HTTP 通道异常降级、MCP 无返回降级、getter/setter。
 * 不依赖真实 MCP server（用 {@code setOverride} 与 {@code mockStatic(McpClientHandler)}）。
 */
@DisplayName("SymptomMatchMcpClient L4 MCP：可用性/override/HTTP降级")
class SymptomMatchMcpClientTest {

    @Test
    @DisplayName("isAvailable：默认 false；enabled+url true；enabled+空url false；override true")
    void isAvailable() {
        SymptomMatchMcpClient c = new SymptomMatchMcpClient();
        assertFalse(c.isAvailable());                 // 默认禁用
        c.setEnabled(true);
        c.setBaseUrl("http://h");
        assertTrue(c.isAvailable());                  // enabled + url
        c.setBaseUrl("   ");
        assertFalse(c.isAvailable());                 // 空白 url
        c.setOverride((s, u) -> "x");
        assertTrue(c.isAvailable());                  // 有桩即视为可用
    }

    @Test
    @DisplayName("chat(override)：返回桩结果；桩抛异常 → null")
    void chatOverride() {
        SymptomMatchMcpClient c = new SymptomMatchMcpClient();
        c.setOverride((s, u) -> "REPLY:" + u);
        assertEquals("REPLY:u1", c.chat("sys", "u1"));

        c.setOverride((s, u) -> { throw new RuntimeException("boom"); });
        assertNull(c.chat("sys", "u2"));
    }

    @Test
    @DisplayName("chat：未启用 → 直接 null（不触网）")
    void chatNotAvailable() {
        SymptomMatchMcpClient c = new SymptomMatchMcpClient();
        assertNull(c.chat("sys", "u"));
    }

    @Test
    @DisplayName("chat(HTTP)：http 构造抛异常 → 降级 null")
    void chatHttpThrows() {
        SymptomMatchMcpClient c = new SymptomMatchMcpClient();
        c.setEnabled(true);
        c.setBaseUrl("http://h");
        try (MockedStatic<McpClientHandler> ms = mockStatic(McpClientHandler.class)) {
            ms.when(() -> McpClientHandler.http(anyString(), anyString(), anyMap()))
                    .thenThrow(new RuntimeException("net down"));
            assertNull(c.chat("sys", "u"));
        }
    }

    @Test
    @DisplayName("chat(HTTP)：callTool 无返回 → 降级 null（覆盖 try 内空返回分支）")
    void chatHttpNoResult() {
        SymptomMatchMcpClient c = new SymptomMatchMcpClient();
        c.setEnabled(true);
        c.setBaseUrl("http://h");
        McpClientHandler handler = mock(McpClientHandler.class);
        when(handler.callTool(anyString(), anyMap())).thenReturn(null);
        try (MockedStatic<McpClientHandler> ms = mockStatic(McpClientHandler.class)) {
            ms.when(() -> McpClientHandler.http(anyString(), anyString(), anyMap()))
                    .thenReturn(handler);
            assertNull(c.chat("sys", "u"));
        }
        verify(handler).initialize();
    }

    @Test
    @DisplayName("getter/setter：tool/endpoint/baseUrl/headers（含 headers=null 兜底）")
    void gettersSetters() {
        SymptomMatchMcpClient c = new SymptomMatchMcpClient();
        c.setTool("t");
        assertEquals("t", c.getTool());
        c.setEndpoint("/e");
        assertEquals("/e", c.getEndpoint());
        c.setBaseUrl("http://b");
        assertEquals("http://b", c.getBaseUrl());
        c.setHeaders(Map.of("A", "b"));
        assertEquals("b", c.getHeaders().get("A"));
        c.setHeaders(null);
        assertNotNull(c.getHeaders());
        assertTrue(c.getHeaders().isEmpty());
        c.setEnabled(true);
        assertTrue(c.isEnabled());
    }

    @Test
    @DisplayName("chat(HTTP)：callTool 返回 isError=true → 降级 null")
    void chatHttpToolError() {
        SymptomMatchMcpClient c = new SymptomMatchMcpClient();
        c.setEnabled(true);
        c.setBaseUrl("http://h");
        McpClientHandler handler = mock(McpClientHandler.class);
        McpSchema.CallToolResult res = mock(McpSchema.CallToolResult.class);
        when(res.isError()).thenReturn(Boolean.TRUE);
        when(handler.callTool(anyString(), anyMap())).thenReturn(res);
        try (MockedStatic<McpClientHandler> ms = mockStatic(McpClientHandler.class)) {
            ms.when(() -> McpClientHandler.http(anyString(), anyString(), anyMap())).thenReturn(handler);
            assertNull(c.chat("s", "u"));
        }
    }

    @Test
    @DisplayName("chat(HTTP)：callTool 返回 TextContent → 取文本")
    void chatHttpTextContent() {
        SymptomMatchMcpClient c = new SymptomMatchMcpClient();
        c.setEnabled(true);
        c.setBaseUrl("http://h");
        McpClientHandler handler = mock(McpClientHandler.class);
        McpSchema.CallToolResult res = mock(McpSchema.CallToolResult.class);
        when(res.isError()).thenReturn(Boolean.FALSE);
        McpSchema.TextContent tc = mock(McpSchema.TextContent.class);
        when(tc.text()).thenReturn("你好");
        List<McpSchema.Content> content = new ArrayList<>();
        content.add(tc);
        when(res.content()).thenReturn(content);
        when(handler.callTool(anyString(), anyMap())).thenReturn(res);
        try (MockedStatic<McpClientHandler> ms = mockStatic(McpClientHandler.class)) {
            ms.when(() -> McpClientHandler.http(anyString(), anyString(), anyMap())).thenReturn(handler);
            assertEquals("你好", c.chat("s", "u"));
        }
    }
}
