package com.ocean.utilities;

import com.openai.client.OpenAIClient;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 大模型单轮对话通用封装（基于 Spring AI {@link ChatClient}，OpenAI 兼容后端）。
 *
 * <p>与业务无关：任何应用配置 {@code spring.ai.openai.*} 后即可复用，用于语义分析 / 信息抽取 / 文本生成等。
 *
 * <p><b>为什么自建模型</b>：Spring AI 的 OpenAI starter 默认自动配置会在 {@code api-key} 为空时
 * 让应用启动失败（{@code At least one credential source must be specified}）。为保持「未配置 key
 * 也能启动、LLM 自动降级」的通用行为，各宿主应用须以 {@code spring.ai.model.*=none} 关闭 Spring AI
 * 默认自动配置，由本类按需（懒加载）自建 {@link OpenAiChatModel}。
 *
 * <p><b>api-key 双来源</b>：优先取配置 {@code spring.ai.openai.api-key}；为空时回落外部文本文件
 * {@code spring.ai.openai.api-key-file}（首行即 key），见 {@link ApiKeyResolver}。
 *
 * <p><b>可用性与降级</b>：未配置 {@code api-key} 且无可用外部 key 文件（或 {@code spring.ai.openai.enabled=false}）
 * 时 {@link #isAvailable()} 返回 {@code false}；{@link #chat(String, String)} 在不可用或调用失败时
 * 一律返回 {@code null}（不抛异常），由调用方决定确定性降级策略。
 *
 * <p><b>测试</b>：可通过 {@link #setOverride(BiFunction)} 或 {@link #ChatAgent(BiFunction)}
 * 注入桩函数，离线验证调用方逻辑。
 */
@Component
public class ChatAgent {

    private static final Logger log = LoggerFactory.getLogger(ChatAgent.class);

    @Value("${spring.ai.openai.enabled:true}")
    private boolean enabled = true;

    @Value("${spring.ai.openai.base-url:}")
    private String baseUrl = "";

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey = "";

    /** 外部 key 文件路径（首行即 key）；仅当 {@code spring.ai.openai.api-key} 为空时启用。 */
    @Value("${spring.ai.openai.api-key-file:}")
    private String apiKeyFile = "";

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    private String model = "gpt-4o-mini";

    @Value("${spring.ai.openai.chat.options.temperature:0.0}")
    private double temperature = 0.0;

    @Value("${spring.ai.openai.timeout-seconds:60}")
    private int timeoutSeconds = 60;

    @Value("${spring.ai.openai.max-retries:3}")
    private int maxRetries = 3;

    /** 底层 Spring AI 对话客户端（按配置懒加载缓存）。 */
    private volatile ChatClient client;
    private volatile String clientKey;

    /** 测试桩：入参 (system, user)，返回模型文本；非 null 时优先于真实调用。 */
    private volatile BiFunction<String, String, String> override;

    public ChatAgent() {
    }

    /** 供单元测试构造（默认可用）。 */
    public ChatAgent(BiFunction<String, String, String> override) {
        this.override = override;
        this.enabled = true;
    }

    public void setOverride(BiFunction<String, String, String> override) {
        this.override = override;
    }

    public void configure(boolean enabled, String baseUrl, String apiKey, String model, int timeoutSeconds) {
        configure(enabled, baseUrl, apiKey, this.apiKeyFile, model, timeoutSeconds);
    }

    public void configure(boolean enabled, String baseUrl, String apiKey, String apiKeyFile,
                          String model, int timeoutSeconds) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.apiKeyFile = apiKeyFile;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
    }

    /** 解析实际使用的 api-key：{@code spring.ai.openai.api-key} 优先，为空则回落外部文件首行（见 {@link ApiKeyResolver}）。 */
    private String resolvedApiKey() {
        return ApiKeyResolver.resolve(apiKey, apiKeyFile);
    }

    public boolean isAvailable() {
        if (override != null) return true;
        return enabled
                && !resolvedApiKey().isBlank()
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
                log.warn("[ChatAgent] 测试桩异常: {}", e.toString());
                return null;
            }
        }
        if (!isAvailable()) {
            return null;
        }
        try {
            String content = client().prompt()
                    .system(systemPrompt == null ? "" : systemPrompt)
                    .user(userPrompt == null ? "" : userPrompt)
                    .call()
                    .content();
            if (content == null || content.isBlank()) return null;
            return content;
        } catch (Exception e) {
            log.warn("[ChatAgent] 调用失败，降级为确定性策略: {}", e.toString());
            return null;
        }
    }

    /** 按当前配置懒加载/缓存 ChatClient；配置变化时重建。 */
    private ChatClient client() {
        String key = baseUrl + "|" + resolvedApiKey() + "|" + model + "|" + temperature + "|" + timeoutSeconds;
        ChatClient c = this.client;
        if (c != null && key.equals(clientKey)) {
            return c;
        }
        synchronized (this) {
            if (this.client == null || !key.equals(clientKey)) {
                this.client = buildClient();
                this.clientKey = key;
            }
            return this.client;
        }
    }

    /** 按当前配置自建 Spring AI {@link ChatClient}（仅在有 key 时调用）。 */
    private ChatClient buildClient() {
        OpenAIClient sdk = OpenAiSetup.setupSyncClient(
                baseUrl,
                resolvedApiKey(),
                null,                                   // credential
                null,                                   // microsoftDeploymentName
                null,                                   // microsoftFoundryServiceVersion
                null,                                   // organizationId
                false,                                  // microsoftFoundry
                false,                                  // gitHubModels
                model,                                  // 默认 user-agent 槽位（与自动配置同口径）
                Duration.ofSeconds(Math.max(1, timeoutSeconds)),
                Math.max(0, maxRetries),
                null,                                   // proxy
                Map.of(),                               // customHeaders
                ObservationRegistry.NOOP,
                null,                                   // meterRegistry
                List.of());                             // httpClientBuilderCustomizers
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                // 必须显式设置单次请求超时：OpenAiChatOptions 的 timeout 默认为
                // AbstractOpenAiOptions.DEFAULT_TIMEOUT（60 秒），且非 null。OpenAiChatModel
                // 在 buildRequestOptions() 中会把该值写入 RequestOptions，进而在
                // SpringAiOpenAiHttpClient.newCall() 覆盖客户端侧的 connect/read/write/call
                // 超时。若不设置，则上面 setupSyncClient 传入的 timeoutSeconds 会被 60 秒覆盖，
                // 长耗时推理请求（如推理模型生成上万个 token，需数分钟）将在 60 秒即超时失败。
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                // 必须回填 base-url/api-key：OpenAiChatModel.Builder#build() 在未显式提供异步 client 时，
                // 会用本 options 自建异步 client；options 缺失 key 会导致 setupAsyncClient 抛
                // "At least one credential source must be specified"。
                .baseUrl(baseUrl)
                .apiKey(resolvedApiKey())
                .build();
        return ChatClient.builder(OpenAiChatModel.builder()
                .openAiClient(sdk)
                .options(options)
                .build()).build();
    }
}
