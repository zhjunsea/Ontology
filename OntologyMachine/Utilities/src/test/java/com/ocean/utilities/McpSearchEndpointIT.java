package com.ocean.utilities;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class McpSearchEndpointIT {

    private static final String QUERY = "华为云 CodeArts 代码智能体";

    @Test
    void parallelSearch() {
        run("PARALLEL", "https://search.parallel.ai",
                "web_search", Map.of("objective", QUERY, "search_queries", List.of(QUERY)));
    }

    @Test
    void exaSearch() {
        run("EXA", "https://mcp.exa.ai",
                "web_search_exa", Map.of("query", QUERY));
    }

    private void run(String tag, String baseUrl, String tool, Map<String, Object> args) {
        try (McpClientHandler client = McpClientHandler.http(baseUrl, "/mcp")) {
            McpSchema.InitializeResult init = client.initialize();
            System.out.println(tag + " serverInfo=" + init.serverInfo());
            System.out.println(tag + " protocolVersion=" + init.protocolVersion());
            List<McpSchema.Tool> tools = client.listTools();
            System.out.println(tag + " tools=" + tools.stream().map(McpSchema.Tool::name).toList());
            McpSchema.CallToolResult result = client.callTool(tool, args);
            System.out.println(tag + " isError=" + result.isError());
            if (result.content() != null) {
                for (McpSchema.Content c : result.content()) {
                    if (c instanceof McpSchema.TextContent text) {
                        String body = text.text();
                        System.out.println(tag + " textLen=" + (body == null ? 0 : body.length()));
                        System.out.println(tag + " textHead=" +
                                (body == null ? "" : body.substring(0, Math.min(600, body.length()))));
                    }
                }
            }
        }
    }
}
