package com.ocean.utilities;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RestClientHandler} 通用 REST 客户端单测：以 JDK 内置 {@link HttpServer} 提供本地桩服务，
 * 验证请求头/请求体透传、非 2xx 语义、传输层异常包装与 URL 拼接（全离线，不访问外网）。
 */
@DisplayName("RestClientHandler · 通用 REST 客户端")
class RestClientHandlerTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", ex -> respond(ex, 200, echo(ex)));
        server.createContext("/ping", ex -> respond(ex, 200, "pong"));
        server.createContext("/error", ex -> respond(ex, 500, "boom"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("postJson 透传 JSON 体、自动 Content-Type，并携带 Bearer 与默认头")
    void postJsonSendsBodyAndHeaders() {
        try (RestClientHandler rest = RestClientHandler.builder(baseUrl)
                .bearerToken("secret-token")
                .header("X-App", "utilities")
                .requestTimeout(Duration.ofSeconds(5))
                .build()) {
            RestClientHandler.Response resp = rest.postJson("/echo", "{\"a\":1}");
            assertTrue(resp.isSuccessful());
            assertEquals(200, resp.statusCode());
            assertTrue(resp.body().contains("ct=application/json"), resp.body());
            assertTrue(resp.body().contains("auth=Bearer secret-token"), resp.body());
            assertTrue(resp.body().contains("app=utilities"), resp.body());
            assertTrue(resp.body().contains("body={\"a\":1}"), resp.body());
        }
    }

    @Test
    @DisplayName("GET 返回响应体")
    void getReturnsBody() {
        try (RestClientHandler rest = RestClientHandler.builder(baseUrl).build()) {
            RestClientHandler.Response resp = rest.get("/ping");
            assertTrue(resp.isSuccessful());
            assertEquals("pong", resp.body());
        }
    }

    @Test
    @DisplayName("非 2xx 不抛异常，原样返回状态码与响应体")
    void nonSuccessIsReturnedNotThrown() {
        try (RestClientHandler rest = RestClientHandler.builder(baseUrl).build()) {
            RestClientHandler.Response resp = rest.get("/error");
            assertFalse(resp.isSuccessful());
            assertEquals(500, resp.statusCode());
            assertEquals("boom", resp.body());
        }
    }

    @Test
    @DisplayName("基地址末尾带斜杠、路径不带斜杠时正确拼接")
    void resolvesUrlWithTrailingSlashBase() {
        try (RestClientHandler rest = RestClientHandler.builder(baseUrl + "/").build()) {
            assertEquals("pong", rest.get("ping").body());
        }
    }

    @Test
    @DisplayName("传输层失败（端口不可达）包装为 RestException")
    void transportFailureWrapsRestException() throws IOException {
        int closedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            closedPort = s.getLocalPort();
        }
        try (RestClientHandler rest = RestClientHandler.builder("http://127.0.0.1:" + closedPort)
                .connectTimeout(Duration.ofSeconds(2))
                .requestTimeout(Duration.ofSeconds(3))
                .build()) {
            assertThrows(RestClientHandler.RestException.class, () -> rest.get("/whatever"));
        }
    }

    private static String echo(HttpExchange ex) throws IOException {
        String ct = ex.getRequestHeaders().getFirst("Content-Type");
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String app = ex.getRequestHeaders().getFirst("X-App");
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return "ct=" + ct + ";auth=" + auth + ";app=" + app + ";body=" + body;
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
