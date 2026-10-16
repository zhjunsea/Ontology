package com.ocean.utilities;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * MCP（Model Context Protocol）客户端封装：用于连接外部 MCP 服务器并调用其能力。
 *
 * <p>支持三种传输：STDIO（子进程）、Streamable HTTP、SSE（Legacy）。典型用法：
 * <pre>{@code
 * try (McpClientHandler client = McpClientHandler.http("http://localhost:8080", "/mcp")) {
 *     client.initialize();
 *     for (McpSchema.Tool tool : client.listTools()) {
 *         System.out.println(tool.name());
 *     }
 *     McpSchema.CallToolResult result = client.callTool("echo", Map.of("text", "hi"));
 * }
 * }</pre>
 */
public class McpClientHandler implements AutoCloseable {

    private static final String CLIENT_NAME = "utilities-mcp-client";
    private static final String CLIENT_VERSION = "1.0.0";
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final McpSyncClient client;

    private McpClientHandler(McpSyncClient client) {
        this.client = client;
    }

    /**
     * 以 STDIO 传输连接外部 MCP 服务器（以子进程方式启动）。
     *
     * @param command 可执行命令（如 npx、python、java）
     * @param args    命令参数
     * @param env     附加环境变量，可为 null
     */
    public static McpClientHandler stdio(String command, List<String> args, Map<String, String> env) {
        ServerParameters.Builder builder = ServerParameters.builder(command);
        if (args != null && !args.isEmpty()) {
            builder.args(args);
        }
        if (env != null && !env.isEmpty()) {
            builder.env(env);
        }
        McpClientTransport transport = new StdioClientTransport(builder.build(), McpJsonDefaults.getMapper());
        return of(transport);
    }

    /**
     * 以 STDIO 传输连接外部 MCP 服务器（便捷变参重载）。
     */
    public static McpClientHandler stdio(String command, String... args) {
        return stdio(command, args == null ? List.of() : List.of(args), null);
    }

    /**
     * 以 Streamable HTTP 传输连接外部 MCP 服务器。
     *
     * @param baseUrl  服务器基地址（如 http://localhost:8080）
     * @param endpoint MCP 端点路径（如 /mcp）
     */
    public static McpClientHandler http(String baseUrl, String endpoint) {
        return http(baseUrl, endpoint, null);
    }

    /**
     * 以 Streamable HTTP 传输连接外部 MCP 服务器，并对外发请求附加自定义请求头。
     *
     * <p>用于需要鉴权的 MCP 服务器，例如：
     * <pre>{@code
     * McpClientHandler.http("https://mcp.example.com", "/mcp",
     *         Map.of("Authorization", "Bearer <token>"));
     * }</pre>
     *
     * @param baseUrl  服务器基地址（如 http://localhost:8080）
     * @param endpoint MCP 端点路径（如 /mcp）
     * @param headers  附加到每个 HTTP 请求的请求头（如 {@code Authorization}）；可为 {@code null}
     */
    public static McpClientHandler http(String baseUrl, String endpoint, Map<String, String> headers) {
        HttpClientStreamableHttpTransport.Builder builder = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint(endpoint)
                .jsonMapper(McpJsonDefaults.getMapper());
        if (headers != null && !headers.isEmpty()) {
            Map<String, String> hs = Map.copyOf(headers);
            builder.httpRequestCustomizer((rb, method, uri, body, context) -> hs.forEach(rb::header));
        }
        return of(builder.build());
    }

    /**
     * 以 SSE（Legacy）传输连接外部 MCP 服务器。
     *
     * <p>SSE 传输已被 MCP 协议标记为 Legacy，优先使用 {@link #http(String, String)}；
     * 仅当对端服务器不支持 Streamable HTTP 时才使用本方法。
     *
     * @param baseUrl 服务器基地址（如 http://localhost:8080）
     */
    @SuppressWarnings("deprecation")
    public static McpClientHandler sse(String baseUrl) {
        McpClientTransport transport = HttpClientSseClientTransport.builder(baseUrl)
                .jsonMapper(McpJsonDefaults.getMapper())
                .build();
        return of(transport);
    }

    /**
     * 使用自定义传输构建客户端（供高级场景或测试使用）。
     */
    public static McpClientHandler of(McpClientTransport transport) {
        McpSyncClient syncClient = McpClient.sync(transport)
                .requestTimeout(DEFAULT_REQUEST_TIMEOUT)
                .clientInfo(McpSchema.Implementation.builder(CLIENT_NAME, CLIENT_VERSION).build())
                .capabilities(McpSchema.ClientCapabilities.builder().roots(true).build())
                .build();
        return new McpClientHandler(syncClient);
    }

    /**
     * 执行 MCP 初始化握手；调用其余能力前必须先调用此方法。
     */
    public McpSchema.InitializeResult initialize() {
        return client.initialize();
    }

    public boolean isInitialized() {
        return client.isInitialized();
    }

    public List<McpSchema.Tool> listTools() {
        return client.listTools().tools();
    }

    public McpSchema.CallToolResult callTool(String name, Map<String, Object> arguments) {
        return client.callTool(McpSchema.CallToolRequest.builder(name)
                .arguments(arguments == null ? Map.of() : arguments)
                .build());
    }

    public List<McpSchema.Resource> listResources() {
        return client.listResources().resources();
    }

    public McpSchema.ReadResourceResult readResource(String uri) {
        return client.readResource(McpSchema.ReadResourceRequest.builder(uri).build());
    }

    public List<McpSchema.Prompt> listPrompts() {
        return client.listPrompts().prompts();
    }

    public McpSchema.GetPromptResult getPrompt(String name, Map<String, Object> arguments) {
        return client.getPrompt(McpSchema.GetPromptRequest.builder(name)
                .arguments(arguments == null ? Map.of() : arguments)
                .build());
    }

    public McpSyncClient getClient() {
        return client;
    }

    @Override
    public void close() {
        client.closeGracefully();
    }
}
