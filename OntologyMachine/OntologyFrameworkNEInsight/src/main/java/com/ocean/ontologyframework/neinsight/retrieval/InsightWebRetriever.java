package com.ocean.ontologyframework.neinsight.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ocean.utilities.RestClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 洞察报告「联网检索」通道：按 SKILL 各节点的「🔎 联网检索指令」调用外部 Web 检索服务
 * （REST，Bearer 鉴权），采集外部公开数据，供 {@code generate-report} 节点连同 SKILL 一并交大模型成文。
 *
 * <p>支持的服务商（{@code neinsight.websearch.provider}）：
 * <ul>
 *   <li>{@code bocha}（默认）：{@code POST {base-url}{path}}，Header {@code Authorization: Bearer <key>}，
 *       响应 {@code webPages.value[]}（{@code name}/{@code url}/{@code summary}|{@code snippet}）。</li>
 *   <li>{@code tavily}：{@code POST {base-url}{path}}，Header {@code Authorization: Bearer <key>}，
 *       响应 {@code results[]}（{@code title}/{@code url}/{@code content}）。</li>
 * </ul>
 *
 * <p>与业务无关：检索关键词/信息源由调用方（基于本体 {@code core:InsightMethod}）传入，本类不硬编码任何业务词表。
 *
 * <p><b>降级</b>：未配置 {@code api-key}、检索通道不可用或某节点检索失败时不抛异常——跳过该节点（记日志），
 * 返回已完成部分，由调用方决定是否以「无外部数据」继续生成报告，保证流程不阻断。
 */
@Component
public class InsightWebRetriever {

    private static final Logger log = LoggerFactory.getLogger(InsightWebRetriever.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 检索通道开关；关闭时 {@link #retrieve} 直接返回空。 */
    @Value("${neinsight.websearch.enabled:true}")
    private boolean enabled = true;

    /** 检索服务商：{@code bocha}（默认）或 {@code tavily}。 */
    @Value("${neinsight.websearch.provider:bocha}")
    private String provider = "bocha";

    /** 检索服务基址（如 {@code https://api.bochaai.com} / {@code https://api.tavily.com}）。 */
    @Value("${neinsight.websearch.base-url:https://api.bochaai.com}")
    private String baseUrl = "https://api.bochaai.com";

    /** 检索端点路径；留空则按服务商取默认（bocha={@code /v1/web-search}，tavily={@code /search}）。 */
    @Value("${neinsight.websearch.path:}")
    private String path = "";

    /** 检索服务 API Key（Bearer 令牌）；未配置时跳过联网检索（报告退化生成）。 */
    @Value("${neinsight.websearch.api-key:}")
    private String apiKey = "";

    /** 每个节点最多保留的结果条数。 */
    @Value("${neinsight.websearch.max-results-per-node:4}")
    private int maxResultsPerNode = 4;

    /** 每条结果摘要的最大字符数（超出截断，控制送入大模型的体积）。 */
    @Value("${neinsight.websearch.excerpt-chars:600}")
    private int excerptChars = 600;

    /** 结果时效过滤（仅 bocha 适用；如 {@code noLimit}/{@code oneDay}/{@code oneWeek}/{@code oneMonth}/{@code oneYear}）。 */
    @Value("${neinsight.websearch.freshness:noLimit}")
    private String freshness = "noLimit";

    /** 单次检索请求超时（秒）。 */
    @Value("${neinsight.websearch.timeout-seconds:15}")
    private int timeoutSeconds = 15;

    /** 底层通用 REST 客户端（懒加载，统一复用 Utilities.RestClientHandler，携带 Bearer 鉴权与超时）。 */
    private volatile RestClientHandler rest;

    public boolean isEnabled() {
        return enabled;
    }

    /** 是否具备调用条件（需配置 api-key）。 */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 一个流程节点的检索请求：{@code jobType} 标识节点，{@code objective}/{@code searchQueries} 来自该节点检索指令。 */
    public record NodeQuery(String jobType, String objective, List<String> searchQueries) {
    }

    /**
     * 按节点联网检索，返回 {@code jobType → 紧凑检索结果（Markdown 片段）}。
     *
     * <p>未配置 api-key 或检索失败一律吞掉异常（记日志）并返回已完成部分，绝不中断调用方流程。
     *
     * @param sessionId 检索会话标识（便于服务侧串联同一流程实例的多次检索）
     * @param nodes     各节点检索请求（可为空）
     */
    public Map<String, String> retrieve(String sessionId, List<NodeQuery> nodes) {
        Map<String, String> out = new LinkedHashMap<>();
        if (!enabled || nodes == null || nodes.isEmpty()) {
            return out;
        }
        if (!isConfigured()) {
            log.warn("[WebSearch] 未配置 neinsight.websearch.api-key，本次跳过联网检索");
            return out;
        }
        for (NodeQuery q : nodes) {
            try {
                String body = search(sessionId, q);
                String md = renderResults(body, provider, maxResultsPerNode, excerptChars);
                if (!md.isBlank()) {
                    out.put(q.jobType(), md);
                }
            } catch (Exception e) {
                log.warn("[WebSearch] 节点 {} 检索失败（跳过）: {}", q.jobType(), e.toString());
            }
        }
        return out;
    }

    /** 发起单次 REST 检索请求，返回原始响应体。 */
    private String search(String sessionId, NodeQuery q) throws Exception {
        boolean tavily = isTavily();
        ObjectNode body = MAPPER.createObjectNode();
        body.put("query", buildQuery(q));
        if (tavily) {
            body.put("max_results", maxResultsPerNode);
            body.put("search_depth", "basic");
        } else {
            body.put("freshness", freshness);
            body.put("summary", true);
            body.put("count", maxResultsPerNode);
        }
        RestClientHandler.Response resp = restClient().postJson(
                resolvePath(), MAPPER.writeValueAsString(body), Map.of("Accept", "application/json"));
        if (!resp.isSuccessful()) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " : " + abbreviate(resp.body()));
        }
        log.debug("[WebSearch] 节点 {} 检索成功 | session={} | provider={} | bytes={}",
                q.jobType(), sessionId, provider, resp.body() == null ? 0 : resp.body().length());
        return resp.body();
    }

    /** 合并节点检索指令（objective + searchQueries）为单个查询串（REST 服务仅接受单 query）。 */
    private static String buildQuery(NodeQuery q) {
        StringBuilder sb = new StringBuilder();
        if (q.objective() != null && !q.objective().isBlank()) {
            sb.append(q.objective().trim());
        }
        if (q.searchQueries() != null) {
            for (String s : q.searchQueries()) {
                if (s == null || s.isBlank()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(s.trim());
            }
        }
        return sb.toString();
    }

    private boolean isTavily() {
        return "tavily".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    /** 解析检索端点路径（相对基地址）；{@code path} 留空时按服务商取默认。 */
    private String resolvePath() {
        return (path == null || path.isBlank())
                ? (isTavily() ? "/search" : "/v1/web-search")
                : path.trim();
    }

    /**
     * 解析单个检索响应体（JSON 字符串）为 Markdown 列表：每条 {@code - 标题 —— URL：摘要}，
     * 最多 {@code maxResultsPerNode} 条，摘要超 {@code excerptChars} 截断。
     *
     * <p>支持两种响应结构（据 {@code provider}）：{@code bocha} 取 {@code webPages.value[]}
     * （{@code name}/{@code summary}|{@code snippet}），{@code tavily} 取 {@code results[]}
     * （{@code title}/{@code content}）。包级可见以便离线单测。
     */
    static String renderResults(String body, String provider, int maxResultsPerNode, int excerptChars) {
        if (body == null || body.isBlank()) {
            return "";
        }
        boolean tavily = "tavily".equalsIgnoreCase(provider == null ? "" : provider.trim());
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode results = tavily ? root.path("results") : root.path("webPages").path("value");
            if (!results.isArray()) {
                return "";
            }
            int count = 0;
            for (JsonNode r : results) {
                if (count >= maxResultsPerNode) {
                    break;
                }
                String url = r.path("url").asText("");
                String title = tavily ? r.path("title").asText("") : r.path("name").asText("");
                String excerpt = (tavily ? r.path("content").asText("") : firstNonBlankText(r, "summary", "snippet"))
                        .replaceAll("\\s+", " ").trim();
                sb.append("- ").append(title.isBlank() ? url : title).append(" —— ").append(url);
                if (!excerpt.isBlank()) {
                    sb.append("：").append(truncate(excerpt, excerptChars));
                }
                sb.append('\n');
                count++;
            }
        } catch (Exception e) {
            log.debug("[WebSearch] 解析检索结果失败: {}", e.toString());
        }
        return sb.toString();
    }

    /** 取首个非空文本字段。 */
    private static String firstNonBlankText(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = node.path(f).asText("");
            if (!v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }

    /** 懒加载通用 REST 客户端（Bearer 鉴权 + 请求超时）。 */
    private RestClientHandler restClient() {
        RestClientHandler r = this.rest;
        if (r == null) {
            synchronized (this) {
                if (this.rest == null) {
                    this.rest = RestClientHandler.builder(baseUrl)
                            .bearerToken(apiKey)
                            .requestTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                            .build();
                }
                r = this.rest;
            }
        }
        return r;
    }
}
