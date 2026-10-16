package com.ocean.ontologyframework.tcm.app;

import com.ocean.utilities.LlmClient;
import com.ocean.utilities.McpServerHandler;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * 内嵌薄 MCP server：把「通用大模型单轮对话」以 MCP 工具形式暴露给本应用（或任何 MCP 客户端）。
 *
 * <p>挂在应用自身 Servlet 容器（端口同 {@code server.port}）的 {@value #ENDPOINT} 路径上，
 * 暴露一个 {@value #TOOL_NAME} 工具：入参 {@code {system,user}}，内部复用 {@link LlmClient}
 * （OpenAI 兼容 REST）返回模型文本。这样症状映射的 L4 通道无需依赖任何外部付费 MCP 服务即可开箱可用，
 * 同时端点仍可经 {@code symptom-mapping.mcp.*} 配置改为外部 server。
 */
@Configuration
public class SymptomMatchMcpServerConfig {

    /** MCP 端点路径（与 {@code symptom-mapping.mcp.endpoint} 默认值一致）。 */
    public static final String ENDPOINT = "/mcp";

    /** 工具名（与 {@code symptom-mapping.mcp.tool} 默认值一致）。 */
    public static final String TOOL_NAME = "chat";

    @Bean(destroyMethod = "close")
    public McpServerHandler symptomMatchMcpServer(LlmClient llmClient) {
        McpServerHandler server = McpServerHandler.http("tcm-symptom-match", "1.0.0", ENDPOINT);
        server.addTool(chatTool(), (exchange, request) -> invokeChat(llmClient, request));
        return server;
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> symptomMatchMcpServlet(McpServerHandler symptomMatchMcpServer) {
        ServletRegistrationBean<HttpServlet> registration =
                new ServletRegistrationBean<>(symptomMatchMcpServer.getHttpServlet(), ENDPOINT);
        registration.setName("symptomMatchMcpServlet");
        registration.setLoadOnStartup(1);
        return registration;
    }

    static McpSchema.Tool chatTool() {
        Map<String, Object> schema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "system", Map.of("type", "string", "description", "系统提示词"),
                        "user", Map.of("type", "string", "description", "用户提示词")),
                "required", List.of("user"));
        return McpSchema.Tool.builder(TOOL_NAME, schema)
                .description("通用大模型单轮对话（OpenAI 兼容）：入参 {system,user}，返回模型文本。")
                .build();
    }

    static McpSchema.CallToolResult invokeChat(LlmClient llmClient, McpSchema.CallToolRequest request) {
        Map<String, Object> args = request.arguments() == null ? Map.of() : request.arguments();
        Object sysArg = args.get("system");
        Object userArg = args.get("user");
        String system = sysArg == null ? null : String.valueOf(sysArg);
        String user = userArg == null ? "" : String.valueOf(userArg);
        String out = llmClient.chat(system, user);
        if (out == null) {
            return McpSchema.CallToolResult.builder()
                    .isError(true)
                    .addTextContent("大模型不可用或调用失败")
                    .build();
        }
        return McpSchema.CallToolResult.builder().addTextContent(out).build();
    }
}
