package com.ocean.envprepare;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * 创建 RabbitMQ 资源：通过 Management HTTP API 先删除旧队列/交换机，再声明
 * vhost / permissions / exchange / queue / binding（先删旧建新）。
 */
public final class RabbitMqInitializer {

    private final EnvConfig cfg;
    private final HttpClient http;
    private final String apiBase;
    private final String authHeader;

    public RabbitMqInitializer(EnvConfig cfg) {
        this.cfg = cfg;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.apiBase = "http://" + cfg.rabbitHost() + ":" + cfg.rabbitManagementPort() + "/api";
        String token = Base64.getEncoder().encodeToString(
                (cfg.rabbitUser() + ":" + cfg.rabbitPassword()).getBytes(StandardCharsets.UTF_8));
        this.authHeader = "Basic " + token;
    }

    public int run() {
        System.out.println("=".repeat(50));
        System.out.println(" [START] 初始化 RabbitMQ 资源（先删旧，再建 vhost/exchange/queue/binding） ");
        System.out.println("=".repeat(50));
        System.out.println("[INFO] 已加载配置: " + cfg.configFile());
        System.out.println("       broker     = " + cfg.rabbitHost() + ":" + cfg.rabbitPort());
        System.out.println("       management = " + cfg.rabbitHost() + ":" + cfg.rabbitManagementPort());
        System.out.println("       vhost      = " + cfg.rabbitVhost());
        System.out.println("       exchange   = " + cfg.exchange() + " (" + cfg.exchangeType() + ")");
        System.out.println("       queue      = " + cfg.queue());
        System.out.println("       routingKey = " + cfg.routingKey());

        if (!cfg.rabbitmqDefined()) {
            System.out.println("[SKIP] application.yaml 未配置 env-prepare.rabbitmq，跳过 RabbitMQ 资源创建。");
            return 0;
        }
        if (isBlank(cfg.rabbitHost()) || isBlank(cfg.rabbitManagementPort()) || isBlank(cfg.rabbitUser())
                || isBlank(cfg.rabbitPassword()) || isBlank(cfg.rabbitVhost())
                || isBlank(cfg.exchange()) || isBlank(cfg.queue()) || isBlank(cfg.routingKey())) {
            System.out.println("[ERROR] application.yaml 的 env-prepare.rabbitmq 节点缺少必要配置项"
                    + "（host/management-port/user/password/vhost/exchange/queue/routing-key）");
            return 1;
        }

        Path logFile = cfg.logDir().resolve("rabbitmq-init.log");
        try {
            Files.createDirectories(cfg.logDir());
        } catch (IOException e) {
            System.out.println("[ERROR] 创建日志目录失败: " + e.getMessage());
            return 1;
        }

        try (PrintWriter log = new PrintWriter(Files.newBufferedWriter(logFile,
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            log.println("\n" + "=".repeat(50));

            if (!waitManagement(log)) {
                enableManagementPlugin(log);
                System.out.println("\n[FAIL] Management API 不可用。请启用插件并重启 RabbitMQ 后重试：");
                System.out.println("   rabbitmq-plugins enable rabbitmq_management");
                return 1;
            }

            String vhost = enc(cfg.rabbitVhost());
            String exchange = enc(cfg.exchange());
            String queue = enc(cfg.queue());
            String user = enc(cfg.rabbitUser());
            boolean ok = true;

            int rc = api("DELETE", "/queues/" + vhost + "/" + queue, null, log);
            System.out.println("       " + okOrFail(deleted(rc)) + " 删除旧队列 '" + cfg.queue() + "' (rc=" + rc + ")");

            rc = api("DELETE", "/exchanges/" + vhost + "/" + exchange, null, log);
            System.out.println("       " + okOrFail(deleted(rc)) + " 删除旧交换机 '" + cfg.exchange() + "' (rc=" + rc + ")");

            rc = api("PUT", "/vhosts/" + vhost, "{}", log);
            System.out.println("       " + okOrFail(accepted(rc)) + " vhost '" + cfg.rabbitVhost() + "' (rc=" + rc + ")");
            ok = ok && accepted(rc);

            rc = api("PUT", "/permissions/" + vhost + "/" + user,
                    "{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}", log);
            System.out.println("       " + okOrFail(accepted(rc)) + " 权限 " + cfg.rabbitUser()
                    + "@" + cfg.rabbitVhost() + " (rc=" + rc + ")");

            rc = api("PUT", "/exchanges/" + vhost + "/" + exchange,
                    "{\"type\":\"" + cfg.exchangeType()
                            + "\",\"durable\":true,\"auto_delete\":false,\"internal\":false,\"arguments\":{}}", log);
            System.out.println("       " + okOrFail(accepted(rc)) + " exchange '" + cfg.exchange() + "' (rc=" + rc + ")");
            ok = ok && accepted(rc);

            rc = api("PUT", "/queues/" + vhost + "/" + queue,
                    "{\"durable\":true,\"auto_delete\":false,\"arguments\":{}}", log);
            System.out.println("       " + okOrFail(accepted(rc)) + " queue '" + cfg.queue() + "' (rc=" + rc + ")");
            ok = ok && accepted(rc);

            rc = api("POST", "/bindings/" + vhost + "/e/" + exchange + "/q/" + queue,
                    "{\"routing_key\":\"" + cfg.routingKey() + "\",\"arguments\":{}}", log);
            System.out.println("       " + okOrFail(accepted(rc)) + " binding " + cfg.queue() + " <- "
                    + cfg.exchange() + " ['" + cfg.routingKey() + "'] (rc=" + rc + ")");
            ok = ok && accepted(rc);

            System.out.println("\n" + "=".repeat(50));
            if (ok) {
                System.out.println(" [OK] RabbitMQ 资源初始化完成（已先删旧再建）。");
                return 0;
            }
            System.out.println(" [WARN] 存在失败项，请查看日志: " + logFile);
            return 1;
        } catch (IOException e) {
            System.out.println("[ERROR] 打开日志文件失败: " + e.getMessage());
            return 1;
        }
    }

    private int api(String method, String path, String jsonBody, PrintWriter log) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(apiBase + path))
                    .header("Authorization", authHeader)
                    .timeout(Duration.ofSeconds(10));
            if (jsonBody != null) {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (log != null) {
                log.println(method + " " + apiBase + path + " -> " + resp.statusCode());
                if (!resp.body().isEmpty()) {
                    log.println(resp.body());
                }
                log.flush();
            }
            return resp.statusCode();
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.toString() : e.getMessage();
            if (log != null) {
                log.println(method + " " + apiBase + path + " -> ERROR " + msg);
                log.flush();
            }
            return -1;
        }
    }

    private boolean waitManagement(PrintWriter log) {
        System.out.println("[CHECK] 探测 RabbitMQ Management API: " + apiBase + "/overview");
        for (int i = 0; i < 30; i++) {
            int rc = api("GET", "/overview", null, log);
            if (rc == 200) {
                System.out.println("       [OK] Management API 可用 (耗时 " + i + "s)");
                return true;
            }
            if (rc == 401) {
                System.out.println("       [FAIL] 认证失败：请检查 env-prepare.rabbitmq.user / password");
                return false;
            }
            ProcessUtil.sleep(1000);
        }
        System.out.println("       [FAIL] 无法访问 Management API（超时）");
        return false;
    }

    private void enableManagementPlugin(PrintWriter log) {
        String home = cfg.rabbitmqHome();
        if (home == null || home.isBlank()) {
            return;
        }
        Path bat = Path.of(home).resolve("sbin").resolve("rabbitmq-plugins.bat");
        if (!Files.isRegularFile(bat)) {
            return;
        }
        System.out.println("[FIX] 尝试启用 rabbitmq_management 插件: " + bat);
        ProcessUtil.ExecResult r = ProcessUtil.run(
                List.of(bat.toString(), "enable", "rabbitmq_management"), null, null, 120);
        if (log != null) {
            log.println("enable plugin rc=" + r.code());
            if (!r.stdout().isEmpty()) {
                log.println(r.stdout());
            }
            if (!r.stderr().isEmpty()) {
                log.println(r.stderr());
            }
            log.flush();
        }
        String out = r.stdout().isEmpty() ? r.stderr() : r.stdout();
        if (!out.isEmpty()) {
            System.out.println(out);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean accepted(int rc) {
        return rc == 200 || rc == 201 || rc == 204;
    }

    private static boolean deleted(int rc) {
        return accepted(rc) || rc == 404;
    }

    private static String okOrFail(boolean ok) {
        return ok ? "[OK]" : "[FAIL]";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
