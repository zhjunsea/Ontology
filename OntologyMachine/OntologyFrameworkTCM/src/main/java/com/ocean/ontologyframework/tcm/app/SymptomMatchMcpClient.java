package com.ocean.ontologyframework.tcm.app;

import com.ocean.utilities.McpClientHandler;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 症状映射的 L4 兜底通道：经 MCP（Streamable HTTP）调用大模型。
 *
 * <p>与 {@code LlmClient} 的 REST 通道<b>并列</b>，语义完全一致（入参 {@code system,user}，
 * 返回模型文本），因此二者可被同一段提示词/解析/防幻觉逻辑复用。默认端点指向本应用内嵌的
 * 薄 MCP server（见 {@link SymptomMatchMcpServerConfig}），可经配置改为任意符合「chat 工具
 * （入参 {system,user}，返回文本）」契约的外部 MCP server。
 *
 * <p><b>降级</b>：未启用 / 未配置地址 / 调用失败 / 返回非法，一律返回 {@code null}（不抛异常），
 * 由调用方回落为「未匹配」，绝不中断诊断流程。
 *
 * <p><b>测试</b>：可经 {@link #setOverride(BiFunction)} 注入桩函数，离线验证调用方逻辑。
 */
@Component
@ConfigurationProperties(prefix = "symptom-mapping.mcp")
public class SymptomMatchMcpClient {

    private static final Logger log = LoggerFactory.getLogger(SymptomMatchMcpClient.class);

    private boolean enabled = false;
    private String baseUrl = "";
    private String endpoint = "/mcp";
    private String tool = "chat";
    private Map<String, String> headers = new LinkedHashMap<>();

    private volatile BiFunction<String, String, String> override;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers == null ? new LinkedHashMap<>() : headers;
    }

    /** 供单元测试注入桩函数（入参 system,user，返回模型文本）。 */
    public void setOverride(BiFunction<String, String, String> override) {
        this.override = override;
    }

    public boolean isAvailable() {
        if (override != null) return true;
        return enabled && baseUrl != null && !baseUrl.isBlank();
    }

    /**
     * 单轮对话（经 MCP chat 工具）。失败返回 {@code null}（不抛异常）。
     */
    public String chat(String systemPrompt, String userPrompt) {
        BiFunction<String, String, String> ov = this.override;
        if (ov != null) {
            try {
                return ov.apply(systemPrompt, userPrompt);
            } catch (Exception e) {
                log.warn("[SymptomMatchMcp] 测试桩异常: {}", e.toString());
                return null;
            }
        }
        if (!isAvailable()) {
            return null;
        }
        try (McpClientHandler client = McpClientHandler.http(baseUrl, endpoint, headers)) {
            client.initialize();
            Map<String, Object> args = new LinkedHashMap<>();
            if (systemPrompt != null) args.put("system", systemPrompt);
            args.put("user", userPrompt == null ? "" : userPrompt);
            McpSchema.CallToolResult result = client.callTool(tool, args);
            if (result == null) {
                log.warn("[SymptomMatchMcp] MCP 无返回，L4 降级");
                return null;
            }
            if (Boolean.TRUE.equals(result.isError())) {
                log.warn("[SymptomMatchMcp] MCP 工具返回错误，L4 降级");
                return null;
            }
            if (result.content() != null) {
                for (McpSchema.Content c : result.content()) {
                    if (c instanceof McpSchema.TextContent text) {
                        String body = text.text();
                        if (body != null && !body.isBlank()) return body;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("[SymptomMatchMcp] MCP 调用失败，L4 降级: {}", e.toString());
            return null;
        }
    }
}
