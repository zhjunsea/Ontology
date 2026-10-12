package com.ocean.utilities;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.servlet.http.HttpServlet;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.function.BiFunction;

/**
 * MCP（Model Context Protocol）服务器封装：用于把本机能力（工具/资源/提示）暴露给 MCP 客户端。
 *
 * <p>支持 STDIO 与 Streamable HTTP 两种传输。典型用法：
 * <pre>{@code
 * McpServerHandler server = McpServerHandler.stdio("my-server", "1.0.0");
 * server.addTool(
 *         McpSchema.Tool.builder("echo", Map.of("type", "object")).description("回显").build(),
 *         (exchange, request) -> McpSchema.CallToolResult.builder()
 *                 .addTextContent(String.valueOf(request.arguments()))
 *                 .build());
 * // stdio 模式：进程 stdin/stdout 即为协议通道
 * }</pre>
 *
 * <p>HTTP 模式返回的服务器需由宿主 Servlet 容器注册 {@link #getHttpServlet()} 后对外提供 /mcp 端点。
 */
public class McpServerHandler implements AutoCloseable {

    private final McpSyncServer server;
    private final HttpServletStreamableServerTransportProvider httpTransportProvider;

    private McpServerHandler(McpSyncServer server, HttpServletStreamableServerTransportProvider httpTransportProvider) {
        this.server = server;
        this.httpTransportProvider = httpTransportProvider;
    }

    /**
     * 以 STDIO 传输创建服务器，使用进程标准输入/输出作为协议通道（适用于被宿主进程按 MCP 服务器方式拉起）。
     */
    public static McpServerHandler stdio(String name, String version) {
        StdioServerTransportProvider provider = new StdioServerTransportProvider(McpJsonDefaults.getMapper());
        McpSyncServer server = McpServer.sync(provider)
                .serverInfo(name, version)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();
        return new McpServerHandler(server, null);
    }

    /**
     * 以 STDIO 传输创建服务器，使用指定的输入/输出流（便于测试或自定义通道）。
     */
    public static McpServerHandler stdio(String name, String version, InputStream in, OutputStream out) {
        StdioServerTransportProvider provider =
                new StdioServerTransportProvider(McpJsonDefaults.getMapper(), in, out);
        McpSyncServer server = McpServer.sync(provider)
                .serverInfo(name, version)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();
        return new McpServerHandler(server, null);
    }

    /**
     * 以 Streamable HTTP 传输创建服务器。
     *
     * <p>返回后需由宿主容器把 {@link #getHttpServlet()} 注册到 {@code endpoint} 对应的 Servlet 映射上。
     *
     * @param endpoint MCP 端点路径（如 /mcp）
     */
    public static McpServerHandler http(String name, String version, String endpoint) {
        HttpServletStreamableServerTransportProvider provider = HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint(endpoint)
                .build();
        McpSyncServer server = McpServer.sync(provider)
                .serverInfo(name, version)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .build();
        return new McpServerHandler(server, provider);
    }

    /**
     * 使用已构建的同步服务器（供高级场景或测试使用）。
     */
    public static McpServerHandler of(McpSyncServer server) {
        return new McpServerHandler(server, null);
    }

    /**
     * 注册一个 MCP 工具及其调用处理逻辑。
     *
     * @param tool   工具定义（名称、描述、输入 schema）
     * @param handler 调用处理函数，入参为会话交换对象与调用请求，返回调用结果
     */
    public void addTool(McpSchema.Tool tool,
                        BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> handler) {
        server.addTool(McpServerFeatures.SyncToolSpecification.builder()
                .tool(tool)
                .callHandler(handler)
                .build());
    }

    public void removeTool(String name) {
        server.removeTool(name);
    }

    public List<McpSchema.Tool> listTools() {
        return server.listTools();
    }

    public McpSchema.ServerCapabilities getCapabilities() {
        return server.getServerCapabilities();
    }

    public McpSyncServer getServer() {
        return server;
    }

    /**
     * HTTP 模式下返回作为 MCP 端点的 Servlet（供宿主容器注册）；STDIO 模式返回 null。
     */
    public HttpServlet getHttpServlet() {
        return httpTransportProvider;
    }

    @Override
    public void close() {
        server.closeGracefully();
    }
}
