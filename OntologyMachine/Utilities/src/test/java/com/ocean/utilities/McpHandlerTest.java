package com.ocean.utilities;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class McpHandlerTest {

    @Test
    void serverRegistersAndListsTool() throws Exception {
        PipedOutputStream clientToServer = new PipedOutputStream();
        PipedInputStream serverIn = new PipedInputStream(clientToServer);
        try (McpServerHandler server =
                     McpServerHandler.stdio("test-server", "1.0.0", serverIn, new ByteArrayOutputStream())) {
            server.addTool(
                    McpSchema.Tool.builder("echo", Map.of("type", "object")).description("回显").build(),
                    (exchange, request) -> McpSchema.CallToolResult.builder()
                            .addTextContent("ok")
                            .build());

            List<McpSchema.Tool> tools = server.listTools();
            assertEquals(1, tools.size());
            assertEquals("echo", tools.get(0).name());
            assertNotNull(server.getCapabilities().tools());
        } finally {
            clientToServer.close();
            serverIn.close();
        }
    }

    @Test
    void httpServerExposesServlet() {
        try (McpServerHandler server = McpServerHandler.http("test-http", "1.0.0", "/mcp")) {
            assertNotNull(server.getHttpServlet());
            assertEquals("test-http", server.getServer().getServerInfo().name());
        }
    }

    @Test
    void clientHandlersCanBeConstructed() {
        try (McpClientHandler http = McpClientHandler.http("http://localhost:8080", "/mcp")) {
            assertFalse(http.isInitialized());
            assertNotNull(http.getClient());
        }
        try (McpClientHandler sse = McpClientHandler.sse("http://localhost:8080")) {
            assertNotNull(sse.getClient());
        }
        try (McpClientHandler stdio =
                     McpClientHandler.stdio("npx", "-y", "@modelcontextprotocol/server-everything")) {
            assertNotNull(stdio.getClient());
        }
    }
}
