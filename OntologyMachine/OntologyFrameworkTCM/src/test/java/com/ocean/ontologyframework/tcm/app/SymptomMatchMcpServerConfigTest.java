package com.ocean.ontologyframework.tcm.app;

import com.ocean.utilities.LlmClient;
import com.ocean.utilities.McpServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内嵌薄 MCP server 测试（离线）：
 * 校验 chat 工具定义、工具处理逻辑（复用 {@link LlmClient}），以及 server bean 的工具注册。
 */
class SymptomMatchMcpServerConfigTest {

    private static final SymptomMatchMcpServerConfig CONFIG = new SymptomMatchMcpServerConfig();

    @Test
    @DisplayName("chat 工具：name=chat，schema 声明 system/user")
    void chatToolSchema() {
        McpSchema.Tool tool = SymptomMatchMcpServerConfig.chatTool();
        assertThat(tool.name()).isEqualTo("chat");
        assertThat(tool.description()).isNotBlank();
        assertThat(tool.inputSchema().toString()).contains("system").contains("user");
    }

    @Test
    @DisplayName("invokeChat：有模型文本 → 返回文本且非 error")
    void invokeChatReturnsText() {
        LlmClient llm = new LlmClient((sys, user) -> "OK:" + user);
        McpSchema.CallToolResult res = SymptomMatchMcpServerConfig.invokeChat(llm,
                McpSchema.CallToolRequest.builder("chat")
                        .arguments(Map.of("system", "s", "user", "u")).build());
        assertThat(Boolean.TRUE.equals(res.isError())).isFalse();
        assertThat(textOf(res)).contains("OK:u");
    }

    @Test
    @DisplayName("invokeChat：模型不可用 → 标记 error")
    void invokeChatErrorWhenUnavailable() {
        LlmClient llm = new LlmClient();
        llm.configure(false, "", "", "stub", 5);
        McpSchema.CallToolResult res = SymptomMatchMcpServerConfig.invokeChat(llm,
                McpSchema.CallToolRequest.builder("chat").arguments(Map.of("user", "u")).build());
        assertThat(res.isError()).isTrue();
    }

    @Test
    @DisplayName("server bean：注册了 chat 工具，且暴露 HTTP servlet 端点")
    void serverRegistersTool() {
        LlmClient llm = new LlmClient((sys, user) -> "x");
        try (McpServerHandler server = CONFIG.symptomMatchMcpServer(llm)) {
            assertThat(server.listTools()).extracting(McpSchema.Tool::name).contains("chat");
            assertThat(server.getHttpServlet()).isNotNull();
        }
    }

    private static String textOf(McpSchema.CallToolResult res) {
        StringBuilder sb = new StringBuilder();
        if (res.content() != null) {
            for (McpSchema.Content c : res.content()) {
                if (c instanceof McpSchema.TextContent t) sb.append(t.text());
            }
        }
        return sb.toString();
    }
}
