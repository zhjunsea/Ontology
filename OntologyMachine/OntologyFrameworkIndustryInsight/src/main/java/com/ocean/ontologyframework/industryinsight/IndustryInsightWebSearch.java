package com.ocean.ontologyframework.industryinsight;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.utilities.McpClientHandler;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 行业洞察「联网检索取证」通道：经 {@link McpClientHandler}（MCP · Streamable HTTP）调用
 * 外部联网检索工具，为各分析节点提供**可溯源的公开资料**（标题 + URL + 摘录）。
 *
 * <p><b>为什么需要</b>：本应用各分析节点此前只把「场景 + 前序结论」喂给大模型，无任何外部事实依据；
 * 叠加铁律 26「不允许编造、无依据标【待考】」后，模型只能输出方向性定性判断与【待考】占位，
 * 报告「无数据支撑」。本通道在分析前先检索公开资料，作为**合规依据**注入提示词，
 * 使模型可在有据可依处给出结论并标注来源（呼应铁律 10：喂给模型的依据本身必须真实、可溯源）。
 *
 * <p><b>默认关闭</b>：{@code industryinsight.websearch.enabled} 默认 {@code false}，
 * 未显式开启时不发起任何外部调用，保证离线 / 无外网 / 单元测试行为不变。
 *
 * <p><b>默认后端</b>：Parallel Web Search MCP（{@code https://search.parallel.ai/mcp}，工具
 * {@code web_search}，匿名可用、无需 key）。端点 / 工具 / key 均可经配置改为其它兼容 MCP server。
 *
 * <p><b>降级</b>：未开启 / 调用失败 / 超时 / 返回非法，一律返回 {@code null}（不抛异常），
 * 由调用方跳过取证、沿用既有确定性降级，**绝不阻断流程**、**绝不编造**（铁律 26 第 3 条）。
 *
 * <p><b>测试</b>：可经 {@link #setOverride(BiFunction)} 注入桩函数（入参 objective、search_queries，
 * 返回检索工具原始文本），离线验证解析与渲染逻辑。
 */
@Component
public class IndustryInsightWebSearch {

    private static final Logger log = LoggerFactory.getLogger(IndustryInsightWebSearch.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 是否启用联网检索（默认关闭）。 */
    @Value("${industryinsight.websearch.enabled:false}")
    private boolean enabled = false;

    /** MCP 服务器基地址。 */
    @Value("${industryinsight.websearch.base-url:https://search.parallel.ai}")
    private String baseUrl = "https://search.parallel.ai";

    /** MCP 端点路径。 */
    @Value("${industryinsight.websearch.endpoint:/mcp}")
    private String endpoint = "/mcp";

    /** 检索工具名。 */
    @Value("${industryinsight.websearch.tool:web_search}")
    private String tool = "web_search";

    /** 检索服务 api-key（可选；Parallel 匿名可用，留空即不带头）。 */
    @Value("${industryinsight.websearch.api-key:}")
    private String apiKey = "";

    /** 单节点最多采纳的检索结果条数。 */
    @Value("${industryinsight.websearch.max-results:5}")
    private int maxResults = 5;

    /** 单条摘录注入提示词的最大字符数（控制上下文体量）。 */
    @Value("${industryinsight.websearch.max-excerpt-chars:400}")
    private int maxExcerptChars = 400;

    /** 测试桩：入参 (objective, search_queries)，返回检索工具原始文本；非 null 时优先于真实调用。 */
    private volatile BiFunction<String, List<String>, String> override;

    /** 单条来源：标题 + URL + 摘录片段。 */
    public record Source(String title, String url, List<String> excerpts) {
    }

    /** 一次检索结果：检索目标 + 来源清单。 */
    public record Result(String objective, List<Source> sources) {
    }

    /** 供单元测试注入桩函数。 */
    public void setOverride(BiFunction<String, List<String>, String> override) {
        this.override = override;
    }

    /** 是否可用（已开启或已注入测试桩）。 */
    public boolean isEnabled() {
        return override != null || enabled;
    }

    /**
     * 执行一次联网检索。失败 / 未开启 / 无有效来源一律返回 {@code null}（不抛异常）。
     *
     * @param objective 检索目标（自然语言，描述本次要取证的问题）
     * @param queries   检索查询词列表
     * @param sessionId 会话标识（可选，便于对端归并同一流程的检索；可为空）
     */
    public Result search(String objective, List<String> queries, String sessionId) {
        BiFunction<String, List<String>, String> ov = this.override;
        if (ov != null) {
            try {
                return parse(objective, ov.apply(objective, queries));
            } catch (Exception e) {
                log.warn("[WebSearch] 测试桩异常: {}", e.toString());
                return null;
            }
        }
        if (!enabled || baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try (McpClientHandler client = McpClientHandler.http(baseUrl, endpoint, headers())) {
            client.initialize();
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("objective", objective == null ? "" : objective);
            args.put("search_queries", queries == null ? List.of() : queries);
            if (sessionId != null && !sessionId.isBlank()) {
                args.put("session_id", sessionId);
            }
            McpSchema.CallToolResult result = client.callTool(tool, args);
            if (result == null || Boolean.TRUE.equals(result.isError())) {
                log.warn("[WebSearch] MCP 无返回或返回错误，跳过取证");
                return null;
            }
            String raw = firstText(result);
            Result parsed = parse(objective, raw);
            if (parsed == null) {
                log.warn("[WebSearch] 检索结果解析失败，跳过取证");
            }
            return parsed;
        } catch (Exception e) {
            log.warn("[WebSearch] 联网检索失败，跳过取证（不阻断）: {}", e.toString());
            return null;
        }
    }

    /** 把检索结果渲染为可注入提示词的证据块；来源编号从 {@code baseIndex+1} 起（全局连续）。 */
    public String renderEvidence(Result result, int baseIndex) {
        if (result == null || result.sources() == null || result.sources().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int n = baseIndex;
        for (Source s : result.sources()) {
            n++;
            sb.append("- [S").append(n).append("] ").append(nz(s.title()))
                    .append(" — ").append(nz(s.url())).append('\n');
            for (String ex : s.excerpts()) {
                sb.append("  · ").append(truncate(ex, maxExcerptChars)).append('\n');
            }
        }
        return sb.toString();
    }

    private Map<String, String> headers() {
        if (apiKey == null || apiKey.isBlank()) {
            return Map.of();
        }
        return Map.of("x-api-key", apiKey.trim());
    }

    private static String firstText(McpSchema.CallToolResult result) {
        if (result.content() != null) {
            for (McpSchema.Content c : result.content()) {
                if (c instanceof McpSchema.TextContent text) {
                    String body = text.text();
                    if (body != null && !body.isBlank()) {
                        return body;
                    }
                }
            }
        }
        return null;
    }

    /** 解析检索工具返回文本（JSON：{@code {"results":[{"title","url","excerpts":[]}]}}）。 */
    private Result parse(String objective, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(raw);
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                return null;
            }
            List<Source> sources = new ArrayList<>();
            for (JsonNode node : results) {
                if (sources.size() >= Math.max(1, maxResults)) {
                    break;
                }
                String url = node.path("url").asText("");
                String title = node.path("title").asText("");
                List<String> excerpts = new ArrayList<>();
                JsonNode ex = node.path("excerpts");
                if (ex.isArray()) {
                    for (JsonNode e : ex) {
                        String t = e.asText("");
                        if (!t.isBlank()) {
                            excerpts.add(t);
                        }
                    }
                }
                if (!url.isBlank() || !title.isBlank()) {
                    sources.add(new Source(title, url, excerpts));
                }
            }
            if (sources.isEmpty()) {
                return null;
            }
            return new Result(objective, sources);
        } catch (Exception e) {
            log.warn("[WebSearch] 解析检索结果失败: {}", e.toString());
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        int m = Math.max(1, max);
        return s.length() <= m ? s : s.substring(0, m) + "…";
    }
}
