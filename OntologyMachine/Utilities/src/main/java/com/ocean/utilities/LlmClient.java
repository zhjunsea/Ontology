package com.ocean.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.BiFunction;

/**
 * 大模型客户端（OpenAI 兼容 {@code /chat/completions} 接口）通用封装。
 *
 * <p>与业务无关：任何应用配置 {@code llm.*} 后即可复用，用于语义分析 / 信息抽取 / 文本生成等。
 *
 * <p><b>可用性与降级</b>：未配置 {@code api-key}（或 {@code llm.enabled=false}）时
 * {@link #isAvailable()} 返回 {@code false}；{@link #chat(String, String)} 在不可用或调用失败时
 * 一律返回 {@code null}（不抛异常），由调用方决定确定性降级策略。
 *
 * <p><b>测试</b>：可通过 {@link #setOverride(BiFunction)} 或 {@link #LlmClient(BiFunction)}
 * 注入桩函数，离线验证调用方逻辑。
 */
@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    @Value("${llm.enabled:true}")
    private boolean enabled = true;

    @Value("${llm.base-url:}")
    private String baseUrl = "";

    @Value("${llm.api-key:}")
    private String apiKey = "";

    @Value("${llm.model:gpt-4o-mini}")
    private String model = "gpt-4o-mini";

    @Value("${llm.timeout-seconds:20}")
    private int timeoutSeconds = 20;

    @Value("${llm.temperature:0.0}")
    private double temperature = 0.0;

    private final ObjectMapper mapper = new ObjectMapper();

    /** 底层通用 REST 客户端（按配置懒加载缓存）；实际 HTTP 调用统一复用 {@link RestClientHandler}。 */
    private volatile RestClientHandler rest;
    private volatile String restKey;

    /** 测试桩：入参 (system, user)，返回模型文本；非 null 时优先于真实 HTTP 调用。 */
    private volatile BiFunction<String, String, String> override;

    public LlmClient() {
    }

    /** 供单元测试构造（默认不可用）。 */
    public LlmClient(BiFunction<String, String, String> override) {
        this.override = override;
        this.enabled = true;
    }

    public void setOverride(BiFunction<String, String, String> override) {
        this.override = override;
    }

    public void configure(boolean enabled, String baseUrl, String apiKey, String model, int timeoutSeconds) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isAvailable() {
        if (override != null) return true;
        return enabled
                && apiKey != null && !apiKey.isBlank()
                && baseUrl != null && !baseUrl.isBlank();
    }

    public String getModel() {
        return model;
    }

    /**
     * 单轮对话。失败返回 {@code null}（不抛异常）。
     */
    public String chat(String systemPrompt, String userPrompt) {
        BiFunction<String, String, String> ov = this.override;
        if (ov != null) {
            try {
                return ov.apply(systemPrompt, userPrompt);
            } catch (Exception e) {
                log.warn("[LlmClient] 测试桩异常: {}", e.toString());
                return null;
            }
        }
        if (!isAvailable()) {
            return null;
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature);
            ArrayNode messages = body.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            ObjectNode usr = messages.addObject();
            usr.put("role", "user");
            usr.put("content", userPrompt);

            RestClientHandler.Response resp =
                    restClient().postJson("/chat/completions", mapper.writeValueAsString(body));
            if (!resp.isSuccessful()) {
                log.warn("[LlmClient] HTTP {} : {}", resp.statusCode(),
                        abbreviate(resp.body()));
                return null;
            }
            JsonNode root = mapper.readTree(resp.body());
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("[LlmClient] 响应无 choices: {}", abbreviate(resp.body()));
                return null;
            }
            String content = choices.get(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) return null;
            return content;
        } catch (Exception e) {
            log.warn("[LlmClient] 调用失败，降级为确定性策略: {}", e.toString());
            return null;
        }
    }

    /** 按当前配置懒加载/缓存通用 REST 客户端；配置变化时重建并关闭旧实例。 */
    private RestClientHandler restClient() {
        String key = baseUrl + "|" + apiKey + "|" + timeoutSeconds;
        RestClientHandler r = this.rest;
        if (r != null && key.equals(restKey)) {
            return r;
        }
        synchronized (this) {
            if (this.rest == null || !key.equals(restKey)) {
                RestClientHandler old = this.rest;
                this.rest = RestClientHandler.builder(baseUrl)
                        .bearerToken(apiKey)
                        .requestTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                        .build();
                this.restKey = key;
                if (old != null) {
                    old.close();
                }
            }
            return this.rest;
        }
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}
