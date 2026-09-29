package com.ocean.ontologyframework.tcm.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.BiFunction;

/**
 * 轻量 LLM 客户端（OpenAI 兼容 {@code /chat/completions} 接口）。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>可降级</b>：未配置 api-key 或 {@code llm.enabled=false} 时 {@link #isAvailable()} 返回 false，
 *       调用方应自动退回确定性策略，应用整体仍可用。</li>
 *   <li><b>不抛异常</b>：{@link #chat(String, String)} 在任何失败（网络、超时、非 200、解析失败）时返回 {@code null}，
 *       由调用方决定降级行为。</li>
 *   <li><b>可测试</b>：{@link #setOverride(BiFunction)} 允许单元测试注入桩函数，无需真实网络。</li>
 * </ul>
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

    private volatile HttpClient http;

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

            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = http().send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
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

    private HttpClient http() {
        HttpClient h = this.http;
        if (h == null) {
            synchronized (this) {
                if (this.http == null) {
                    this.http = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                }
                h = this.http;
            }
        }
        return h;
    }

    private static String abbreviate(String s) {
        if (s == null) return "";
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}
