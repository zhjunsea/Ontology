package com.ocean.utilities;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用 REST 客户端封装（与业务无关）：供各应用对接任意 HTTP/REST 服务时复用，
 * 与 {@link McpClientHandler} 并列，构成 Utilities 对外「REST 通道 / MCP 通道」两类通用外部调用能力。
 *
 * <p>集中沉淀各处重复的 REST 调用样板：懒加载 {@link HttpClient}、连接/请求超时、
 * 默认请求头与 Bearer 鉴权、URL 拼接、状态码与响应体读取。
 *
 * <p>典型用法：
 * <pre>{@code
 * try (RestClientHandler rest = RestClientHandler.builder("https://api.example.com/v1")
 *         .bearerToken(apiKey)
 *         .requestTimeout(Duration.ofSeconds(15))
 *         .build()) {
 *     RestClientHandler.Response resp = rest.postJson("/search", "{\"query\":\"hi\"}");
 *     if (resp.isSuccessful()) {
 *         String body = resp.body();
 *     }
 * }
 * }</pre>
 *
 * <p><b>失败语义</b>：传输层异常（连接失败、超时、中断等）统一包装为 {@link RestException} 抛出；
 * HTTP 非 2xx <b>不属于</b>异常，原样体现在 {@link Response#statusCode()}，由调用方自行决定降级策略。
 */
public class RestClientHandler implements AutoCloseable {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final String baseUrl;
    private final Map<String, String> defaultHeaders;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final HttpClient.Redirect redirectPolicy;

    private volatile HttpClient http;

    private RestClientHandler(Builder builder) {
        this.baseUrl = builder.baseUrl == null ? "" : builder.baseUrl.trim();
        this.defaultHeaders = Map.copyOf(builder.defaultHeaders);
        this.connectTimeout = builder.connectTimeout;
        this.requestTimeout = builder.requestTimeout;
        this.redirectPolicy = builder.redirectPolicy;
    }

    /** 创建构建器；{@code baseUrl} 为接口基地址（可含路径前缀，如 {@code https://api.openai.com/v1}）。 */
    public static Builder builder(String baseUrl) {
        return new Builder(baseUrl);
    }

    // ---------------------------------------------------------------------
    // 便捷方法
    // ---------------------------------------------------------------------

    public Response get(String path) {
        return exchange("GET", path, null, null);
    }

    public Response get(String path, Map<String, String> headers) {
        return exchange("GET", path, headers, null);
    }

    public Response postJson(String path, String jsonBody) {
        return postJson(path, jsonBody, null);
    }

    /**
     * POST 一个 JSON 请求体（自动补 {@code Content-Type: application/json}）。
     *
     * @param path    相对基地址的路径，或以 {@code http(s)://} 开头的绝对 URL
     * @param jsonBody 请求体（JSON 文本，可为 {@code null} 表示空体）
     * @param headers 单次调用附加请求头（可覆盖默认头；可为 {@code null}）
     */
    public Response postJson(String path, String jsonBody, Map<String, String> headers) {
        Map<String, String> merged = new LinkedHashMap<>();
        merged.put("Content-Type", "application/json");
        if (headers != null) {
            merged.putAll(headers);
        }
        return exchange("POST", path, merged, jsonBody);
    }

    // ---------------------------------------------------------------------
    // 通用方法
    // ---------------------------------------------------------------------

    /**
     * 发起任意 HTTP 方法调用，返回原始 {@link Response}（非 2xx 不抛异常）。
     *
     * @param method  HTTP 方法（如 GET / POST / PUT / DELETE / PATCH）
     * @param path    相对基地址的路径，或以 {@code http(s)://} 开头的绝对 URL
     * @param headers 单次调用附加请求头（可覆盖默认头；可为 {@code null}）
     * @param body    请求体（文本，可为 {@code null} 表示无体）
     * @throws RestException 传输层失败（连接失败 / 超时 / 中断等）
     */
    public Response exchange(String method, String path, Map<String, String> headers, String body) {
        String url = resolveUrl(path);
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(requestTimeout)
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        applyHeaders(rb, headers);
        try {
            HttpResponse<String> resp = http().send(rb.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(resp.statusCode(), resp.body(), resp.headers().map());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RestException("REST 调用被中断: " + method + " " + url, -1, e);
        } catch (Exception e) {
            throw new RestException("REST 调用失败: " + method + " " + url + " : " + e, -1, e);
        }
    }

    private void applyHeaders(HttpRequest.Builder rb, Map<String, String> headers) {
        for (Map.Entry<String, String> e : defaultHeaders.entrySet()) {
            rb.header(e.getKey(), e.getValue());
        }
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    rb.header(e.getKey(), e.getValue());
                }
            }
        }
    }

    /** 拼接 URL：绝对 URL 原样返回；否则按需补一个 {@code /} 后与基地址拼接。 */
    private String resolveUrl(String path) {
        String p = path == null ? "" : path.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return p;
        }
        if (p.isEmpty()) {
            return baseUrl;
        }
        boolean baseSlash = baseUrl.endsWith("/");
        boolean pathSlash = p.startsWith("/");
        if (baseSlash && pathSlash) {
            return baseUrl + p.substring(1);
        }
        if (!baseSlash && !pathSlash) {
            return baseUrl + "/" + p;
        }
        return baseUrl + p;
    }

    private HttpClient http() {
        HttpClient h = this.http;
        if (h == null) {
            synchronized (this) {
                if (this.http == null) {
                    this.http = HttpClient.newBuilder()
                            .connectTimeout(connectTimeout)
                            .followRedirects(redirectPolicy)
                            .build();
                }
                h = this.http;
            }
        }
        return h;
    }

    @Override
    public void close() {
        HttpClient h = this.http;
        if (h != null) {
            try {
                h.close();
            } catch (Exception ignored) {
                // 关闭底层 HTTP 客户端失败无需处理
            }
        }
    }

    // ---------------------------------------------------------------------
    // 数据结构
    // ---------------------------------------------------------------------

    /** HTTP 响应：状态码 + 响应体（UTF-8 文本）+ 响应头。 */
    public record Response(int statusCode, String body, Map<String, List<String>> headers) {
        /** 是否为成功响应（2xx）。 */
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    /** REST 调用异常（传输层失败的统一包装；{@code statusCode=-1} 表示未拿到 HTTP 响应）。 */
    public static final class RestException extends RuntimeException {
        private final int statusCode;

        public RestException(String message, int statusCode, Throwable cause) {
            super(message, cause);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    // ---------------------------------------------------------------------
    // Builder
    // ---------------------------------------------------------------------

    public static final class Builder {

        private final String baseUrl;
        private final Map<String, String> defaultHeaders = new LinkedHashMap<>();
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private HttpClient.Redirect redirectPolicy = HttpClient.Redirect.NORMAL;

        private Builder(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /** 追加一个默认请求头（对所有请求生效）。 */
        public Builder header(String name, String value) {
            if (name != null && value != null) {
                defaultHeaders.put(name, value);
            }
            return this;
        }

        /** 批量追加默认请求头。 */
        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                headers.forEach(this::header);
            }
            return this;
        }

        /** 设置 {@code Authorization: Bearer <token>} 默认头（token 空白则忽略）。 */
        public Builder bearerToken(String token) {
            if (token != null && !token.isBlank()) {
                defaultHeaders.put("Authorization", "Bearer " + token.trim());
            }
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            if (timeout != null) {
                this.connectTimeout = timeout;
            }
            return this;
        }

        public Builder requestTimeout(Duration timeout) {
            if (timeout != null) {
                this.requestTimeout = timeout;
            }
            return this;
        }

        public Builder followRedirects(HttpClient.Redirect policy) {
            if (policy != null) {
                this.redirectPolicy = policy;
            }
            return this;
        }

        public RestClientHandler build() {
            return new RestClientHandler(this);
        }
    }
}
