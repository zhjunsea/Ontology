package com.ocean.envprepare;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动环境（按 application.yaml 启停 RabbitMQ / MySQL / Ontop / Camunda，并常驻等待 Ctrl+C）。
 * <p>对每个服务先「探测端口占用 + 判定进程归属」，再决定复用还是重启：
 * <ul>
 *   <li><b>ABSENT</b>（端口空闲）：启动服务；</li>
 *   <li><b>MINE</b>（本环境实例在运行）：start 复用、init 停止后重建再启动；</li>
 *   <li><b>FOREIGN</b>（端口被非本环境进程占用）：报冲突（打印占用端口与占用者）并逐服务三选一
 *       ——复用原有环境 / 杀掉原进程启动新进程 / 提示空闲端口让用户改 application.yaml 后重启；
 *       非交互环境（右键双击、stdin 重定向）直接失败退出，不擅自杀进程。</li>
 * </ul>
 * <p>启动动作发出后，对每个服务再「等待端口进入监听」做<b>就绪确认</b>（进程提前退出或超时即判失败并给出原因）；
 * 全部应启动的服务都就绪才报告成功并进入常驻，否则逐项列出「哪个失败 + 失败原因」并以非 0 退出。
 * 退出时仅停止「本次由本进程启动的」服务，避免误停复用来的外部进程。
 */
public final class EnvironmentStarter {

    /** 冲突处理后回调启动的动作，返回启动的进程用于就绪确认（可能抛 IO 异常）。 */
    @FunctionalInterface
    private interface Starter {
        Process start() throws IOException;
    }

    /** 单个环境服务的检查结果：{@code configured=false} 表示该服务未配置（跳过，不计失败）。 */
    private record SvcResult(String name, boolean configured, boolean ok, String status, String cause) {
        static SvcResult skip(String name) {
            return new SvcResult(name, false, true, "未配置（跳过）", null);
        }

        static SvcResult ok(String name, String status) {
            return new SvcResult(name, true, true, status, null);
        }

        static SvcResult fail(String name, String cause) {
            return new SvcResult(name, true, false, "未就绪", cause);
        }
    }

    private final EnvConfig cfg;
    private final boolean resetData;
    private final List<Process> started = new ArrayList<>();
    private final Set<String> startedServices = new LinkedHashSet<>();
    private MysqlService mysql;

    /** 启动后等待「端口进入监听」的就绪确认超时（秒）。Ontop/Camunda 首次启动可能较慢。 */
    private static final int READY_TIMEOUT_SEC = 120;

    /** 交棒型启动器（c8run）退出后、判定子进程缺失为失败前的宽限期（秒）。 */
    private static final int LAUNCHER_HANDOFF_GRACE_SEC = 15;

    /**
     * 会被 Spring Boot「松散绑定」误当作端口配置的环境变量名（一律大写比较）。
     *
     * <p>c8run 启动 Camunda 与 Connectors 两种 Spring Boot 应用子进程：Camunda 侧由 camunda.bat 显式传
     * {@code -Dserver.port=<主端口>}（命令行优先，不受影响）；而 <b>Connectors 侧不加端口参数</b>，
     * 其端口取自 {@code connectors-application.properties}（{@code server.port=8086}）——一旦环境里存在
     * {@code SERVER_PORT} / {@code SERVER__PORT} 之类变量，Spring 松散绑定会让其覆盖 8086，使 Connectors
     * 去绑定一个已被占用（或非预期）的端口，报 {@code Port … was already in use} 后子进程退出。
     *
     * <p>典型来源：把本启动器放在会注入 {@code SERVER__PORT} 的宿主环境（如某些 IDE/Agent 外壳）里运行，
     * 该变量会随父进程环境一路继承到 c8run 及其 Java 子进程。故启动 Camunda 前主动剔除这几个键，
     * 让 Camunda/Connectors 严格按各自配置的端口启动（命令行与 application.* 决定），不受宿主环境干扰。
     */
    private static final List<String> PORT_HIJACK_ENV_KEYS = List.of(
            "SERVER_PORT", "SERVER__PORT",
            "MANAGEMENT_SERVER_PORT", "MANAGEMENT_SERVER__PORT");

    /**
     * @param resetData true=init：重建持久化系统（删库重建 + 重建 MQ 资源）；MINE 服务先停再起；
     *                  false=start：仅启动环境，缺则建、已存在则复用（MINE 不重启）。
     */
    public EnvironmentStarter(EnvConfig cfg, boolean resetData) {
        this.cfg = cfg;
        this.resetData = resetData;
    }

    public int run() {
        if (!cfg.ontopConfigured() && !cfg.rabbitmqDefined() && !cfg.bpmnPathDefined()
                && !cfg.mysqlDefined()) {
            System.out.println("[SKIP] application.yaml 未配置 Ontop / RabbitMQ / Camunda / MySQL 任一服务，无需启动。");
            return 0;
        }

        System.out.println("=".repeat(50));
        System.out.println(resetData
                ? " [INIT] 重建本体环境服务（" + rebuildSummary() + "，再常驻启动） "
                : " [START] 启动本体环境服务（按 application.yaml 启停 " + declaredServices() + "） ");
        System.out.println("=".repeat(50));
        System.out.println("[INFO] 已加载配置: " + cfg.configFile());
        System.out.println("[INFO] 模块根目录: " + cfg.moduleRoot());

        if (cfg.rabbitmqDefined() && isBlank(cfg.rabbitmqHome())) {
            System.out.println("[ERROR] 已定义 env-prepare.rabbitmq，但缺少 env-prepare.rabbitmq-home");
            return 1;
        }
        if (cfg.bpmnPathDefined() && isBlank(cfg.camundaHome())) {
            System.out.println("[ERROR] 已定义 ontology.bpmn-path，但缺少 env-prepare.camunda-home");
            return 1;
        }
        if (cfg.mysqlDefined() && !cfg.mysqlPortable() && isBlank(cfg.mysqlServiceName())) {
            System.out.println("[ERROR] 已定义 env-prepare.mysql（service 模式），但缺少 env-prepare.mysql.service-name");
            return 1;
        }
        if (cfg.mysqlDefined() && cfg.mysqlPortable() && isBlank(cfg.mysqldBin())) {
            System.out.println("[ERROR] 已定义 env-prepare.mysql（portable 模式），但缺少 env-prepare.mysql.home（mysqld 路径未知）");
            return 1;
        }

        try {
            Files.createDirectories(cfg.logDir());
        } catch (IOException e) {
            System.out.println("[ERROR] 创建日志目录失败: " + e.getMessage());
            return 1;
        }

        registerShutdownHook();
        reportAppJavaEnv();

        List<SvcResult> results = new ArrayList<>();
        try {
            results.add(ensureRabbitmq());
            SvcResult mysqlResult = ensureMysql();
            results.add(mysqlResult);
            if (mysqlResult.ok()) {
                initializeData();
                AppConfigSync.sync(cfg, System.out::println);
                results.add(ensureOntop());
                results.add(ensureCamunda());
            } else {
                System.out.println("[WARN] MySQL 未就绪，跳过数据初始化 / 配置同步 / Ontop / Camunda 的启动检查。");
            }
        } catch (IOException e) {
            System.out.println("[FAIL] 启动服务失败: " + e.getMessage());
            results.add(SvcResult.fail("环境服务", "启动过程异常: " + e.getMessage()));
        }

        List<SvcResult> failures = results.stream()
                .filter(r -> r.configured() && !r.ok())
                .collect(Collectors.toList());

        System.out.println("\n" + "=".repeat(50));
        System.out.println(" [CHECK] 环境服务就绪检查结果：");
        for (SvcResult r : results) {
            if (!r.configured()) {
                System.out.println(" [SKIP] " + r.name() + "：" + r.status());
            } else if (r.ok()) {
                System.out.println(" [OK]   " + r.name() + "：" + r.status());
            } else {
                System.out.println(" [FAIL] " + r.name() + "：未就绪");
                System.out.println("         原因: " + r.cause());
            }
        }
        System.out.println("=".repeat(50));

        if (!failures.isEmpty()) {
            System.out.println(" [FAIL] 环境启动失败：以下应启动的服务未就绪——");
            for (SvcResult r : failures) {
                System.out.println("        - " + r.name() + "：" + r.cause());
            }
            System.out.println("=".repeat(50) + "\n");
            return 1;
        }

        System.out.println(" [OK] 环境服务全部就绪（按配置应启动的服务均已确认监听端口）。");
        if (!persistentResources().isEmpty()) {
            System.out.println(resetData
                    ? " [INFO] init：" + persistentResources() + "已删旧重建。"
                    : " [INFO] start：" + persistentResources() + "缺则建、有则留（未删任何已有数据）。");
        }
        List<String> portLines = new ArrayList<>();
        if (cfg.ontopConfigured()) {
            portLines.add("Ontop 端口 " + cfg.ontopPort());
        }
        if (cfg.bpmnPathDefined()) {
            portLines.add("Camunda 端口 " + cfg.camundaPort() + "（gRPC " + cfg.camundaGrpcPort() + "）");
        }
        if (!portLines.isEmpty()) {
            System.out.println(" [INFO] " + String.join("、", portLines) + "。");
        }
        System.out.println(" [INFO] 统一日志目录: " + cfg.logDir());
        System.out.println(" [INFO] 按 Ctrl+C 停止所有服务并退出。");
        System.out.println("=".repeat(50) + "\n");

        while (true) {
            ProcessUtil.sleep(1000);
        }
    }

    /** 按 application.yaml 实际声明/启用的服务拼「启停清单」（如仅 Camunda 时不再出现 MySQL/RabbitMQ）。 */
    private String declaredServices() {
        List<String> names = new ArrayList<>();
        if (cfg.rabbitmqDefined()) {
            names.add("RabbitMQ");
        }
        if (cfg.mysqlDefined()) {
            names.add("MySQL");
        }
        if (cfg.ontopConfigured()) {
            names.add("Ontop");
        }
        if (cfg.bpmnPathDefined()) {
            names.add("Camunda");
        }
        return String.join("/", names);
    }

    /** init 横幅：仅列出实际声明/启用服务的重建动作（无持久化服务时不出现「清空并重建」字样）。 */
    private String rebuildSummary() {
        List<String> parts = new ArrayList<>();
        if (cfg.mysqlDefined()) {
            parts.add("清空并重建 MySQL 库");
        }
        if (cfg.rabbitmqDefined()) {
            parts.add("重建 RabbitMQ 资源");
        }
        if (cfg.ontopConfigured()) {
            parts.add("停止后重启 Ontop");
        }
        if (cfg.bpmnPathDefined()) {
            parts.add("停止后重启 Camunda");
        }
        return String.join(" + ", parts);
    }

    /** 具备持久化状态的服务名：MySQL 库 / RabbitMQ 资源（按声明组合）；均未声明时返回空串。 */
    private String persistentResources() {
        boolean mysql = cfg.mysqlDefined();
        boolean rabbit = cfg.rabbitmqDefined();
        if (mysql && rabbit) {
            return "MySQL 库与 RabbitMQ 资源";
        }
        if (mysql) {
            return "MySQL 库";
        }
        if (rabbit) {
            return "RabbitMQ 资源";
        }
        return "";
    }

    /**
     * 启动前探测并回显「本应用 Java 环境」（应用级）——让随包 JDK 的真实版本一目了然
     * （Camunda/Connectors 等子进程实际运行的就是这个 java），并据此排查
     * 「压缩包目录名叫 jdk-27、里面其实是 26」之类的误判。
     *
     * <p>本方法只读探测、只打印，<b>绝不</b>写系统/用户环境变量；随包脚本各自在本窗口内设置
     * JAVA_HOME/PATH（应用级）。可用 {@code env-prepare.java-env-auto: false} 关闭探测。
     */
    private void reportAppJavaEnv() {
        if (!cfg.javaEnvAuto()) {
            System.out.println("[ENV] 已关闭启动时的 Java 环境探测（env-prepare.java-env-auto=false）。");
            return;
        }
        String jh = cfg.javaHome();
        if (isBlank(jh)) {
            System.out.println("[WARN] 未解析到随包 java-home，Camunda 子进程可能因 PATH 中无 java 而启动失败"
                    + "（请配置 env-prepare.java-home）。");
            return;
        }
        JavaEnv.report(JavaEnv.probe(jh));
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[EXIT] 收到退出信号，正在清理本次启动的进程...");
            for (Process p : started) {
                if (p.isAlive()) {
                    ProcessUtil.killTree(p.pid());
                }
            }
            if (startedServices.contains("Camunda")) {
                stopCamunda();
            }
            if (startedServices.contains("MySQL")) {
                stopMysql();
            }
            System.out.println("[OK] 清理完成，已安全退出（复用来的进程未改动）。");
        }));
    }

    // ==================== 各服务：探测 + 复用/重启/冲突处理 ====================

    /** 确保 RabbitMQ：端口空闲则启动；本环境实例在运行则 start 复用 / init 重建；被非本环境占用则冲突三选一。 */
    private SvcResult ensureRabbitmq() throws IOException {
        if (!cfg.rabbitmqDefined()) {
            System.out.println("[SKIP] RabbitMQ: 未定义 env-prepare.rabbitmq，跳过启动。");
            return SvcResult.skip("RabbitMQ");
        }
        int amqp = parseIntPort(cfg.rabbitPort(), 5672);
        int mgmt = parseIntPort(cfg.rabbitManagementPort(), 15672);
        Path log = cfg.logDir().resolve("rabbitmq.log");
        if (ProcessUtil.portFree(amqp) && ProcessUtil.portFree(mgmt)) {
            Process p = startRabbitmq();
            startedServices.add("RabbitMQ");
            return confirmReady("RabbitMQ", p, amqp, READY_TIMEOUT_SEC, log);
        }
        if (!matchInstances("rabbit").isEmpty()) {
            if (!resetData) {
                System.out.println("[INFO] RabbitMQ 已在运行（本环境实例），start 模式复用，跳过启动。");
                return SvcResult.ok("RabbitMQ", "复用（本环境实例已在运行）");
            }
            System.out.println("[INIT] RabbitMQ: 停止本环境实例后重建 ...");
            stopRabbitmq();
            waitPortFree(amqp, 15);
            Process p = startRabbitmq();
            startedServices.add("RabbitMQ");
            return confirmReady("RabbitMQ", p, amqp, READY_TIMEOUT_SEC, log);
        }
        return resolveForeign("RabbitMQ", "env-prepare.rabbitmq.port / management-port",
                amqp, this::startRabbitmq, log);
    }

    /** 确保 MySQL：service 模式按服务状态；portable 模式由 {@link MysqlService} 探测端口与归属（含冲突三选一）。 */
    private SvcResult ensureMysql() {
        if (!cfg.mysqlDefined()) {
            System.out.println("[SKIP] MySQL: 未定义 env-prepare.mysql，跳过启动。");
            return SvcResult.skip("MySQL");
        }
        MysqlService.StartOutcome outcome = mysqlService().ensureStarted(null);
        return switch (outcome) {
            case STARTED -> {
                startedServices.add("MySQL");
                yield SvcResult.ok("MySQL", "已启动并就绪");
            }
            case ALREADY_RUNNING -> SvcResult.ok("MySQL", "复用（已在运行）");
            case FAILED -> SvcResult.fail("MySQL",
                    "启动失败或端口未就绪，请查看日志: " + cfg.logDir().resolve("mysql.log"));
        };
    }

    /** 确保 Ontop：端口空闲则启动；本环境实例在运行则 start 复用 / init 重建；被非本环境占用则冲突三选一。 */
    private SvcResult ensureOntop() throws IOException {
        if (!cfg.ontopConfigured()) {
            System.out.println("[SKIP] Ontop: application.yaml 未配置齐全的 Ontop 参数"
                    + "（env-prepare.java-home/ontop-home 与 ontology.obda-path/obda-properties-path/main-path），跳过启动。");
            return SvcResult.skip("Ontop");
        }
        int port = parseIntPort(cfg.ontopPort(), 8080);
        Path log = cfg.logDir().resolve("ontop.log");
        if (ProcessUtil.portFree(port)) {
            Process p = startOntop();
            startedServices.add("Ontop");
            return confirmReady("Ontop", p, port, READY_TIMEOUT_SEC, log);
        }
        if (!matchInstances("ontop").isEmpty()) {
            if (!resetData) {
                System.out.println("[INFO] Ontop 已在运行（本环境实例，端口 " + port + "），start 模式复用，跳过启动。");
                return SvcResult.ok("Ontop", "复用（本环境实例已在运行，端口 " + port + "）");
            }
            System.out.println("[INIT] Ontop: 停止本环境实例后重建 ...");
            stopOntop();
            waitPortFree(port, 15);
            Process p = startOntop();
            startedServices.add("Ontop");
            return confirmReady("Ontop", p, port, READY_TIMEOUT_SEC, log);
        }
        return resolveForeign("Ontop", "env-prepare.ontop-port", port, this::startOntop, log);
    }

    /**
     * 确保 Camunda：按「一整组端口」探测（主端口 + Zeebe gRPC/内部 + Connectors/metrics）。
     * 全组空闲则启动；本环境实例在运行则 start 复用 / init 重建；被非本环境进程占用则冲突三选一
     * （复用原有环境 / 杀掉原进程重启 / 改用其它端口）。
     */
    private SvcResult ensureCamunda() throws IOException {
        if (!cfg.bpmnPathDefined()) {
            System.out.println("[SKIP] Camunda: 未定义 ontology.bpmn-path，跳过启动。");
            return SvcResult.skip("Camunda");
        }
        int port = parseIntPort(cfg.camundaPort(), 9080);
        Path log = cfg.logDir().resolve("camunda.log");

        // 端口检测：c8run 会占用一整组端口（含内部 26501/26502、Connectors 8086、metrics 9600）。
        // 任一被"非本环境进程"占用，其 Java 子进程都会 BindException 后不监听，而 c8run 只轮询健康地址，
        // 表现为「进程已启动但一直 Waiting、端口起不来」——故先做整组端口检测，再决定复用/冲突处理。
        List<PortSpec> specs = camundaPortSpecs();
        List<PortSpec> occupied = specs.stream()
                .filter(s -> !ProcessUtil.portFree(s.port()))
                .collect(Collectors.toList());

        if (occupied.isEmpty()) {
            Process p = startCamunda();
            startedServices.add("Camunda");
            return confirmCamundaReady(p, port, log);
        }
        if (!matchInstances("camunda").isEmpty()) {
            if (!resetData) {
                System.out.println("[INFO] Camunda 已在运行（本环境实例，端口 " + port + "），start 模式复用，跳过启动。");
                return SvcResult.ok("Camunda", "复用（本环境实例已在运行，端口 " + port + "）");
            }
            System.out.println("[INIT] Camunda: 停止本环境实例后重建 ...");
            stopCamunda();
            waitPortsFree(specs, 30);
            Process p = startCamunda();
            startedServices.add("Camunda");
            return confirmCamundaReady(p, port, log);
        }
        return resolveForeignPorts("Camunda", occupied, this::startCamunda, log, port);
    }

    /**
     * FOREIGN：端口被非本环境进程占用——报冲突并逐服务三选一；非交互环境直接失败退出。
     *
     * @return 复用/重启成功返回 ok 结果；非交互或用户选择「改端口退出」返回 fail 结果。
     */
    private SvcResult resolveForeign(String service, String cfgKey, int port, Starter starter, Path logFile)
            throws IOException {
        long pid = ProcessUtil.pidOnPort(port);
        String holder = describeProcess(pid);
        System.out.println("[CONFLICT] " + service + ": 端口 " + port + " 被非本环境进程占用");
        if (!holder.isBlank()) {
            System.out.println("           占用者: " + holder);
        }
        if (!ConflictResolver.interactive()) {
            System.out.println("[FAIL] 非交互环境无法处理端口冲突，已中止。");
            System.out.println("       请手动释放端口，或修改 application.yaml 的 " + cfgKey + " 后重试。");
            return SvcResult.fail(service,
                    "端口 " + port + " 被非本环境进程占用，且非交互环境无法处理（可改 " + cfgKey + " 后重试）");
        }
        switch (ConflictResolver.ask(service, "端口 " + port, holder)) {
            case REUSE:
                System.out.println("[INFO] " + service + ": 复用原有环境（沿用占用端口的进程，不启动新进程）。");
                return SvcResult.ok(service, "复用（沿用占用端口 " + port + " 的原有进程）");
            case KILL_RESTART:
                if (pid > 0) {
                    System.out.println("[STOP] " + service + ": 终止原进程 PID=" + pid + " ...");
                    ProcessUtil.killTree(pid);
                }
                waitPortFree(port, 15);
                System.out.println("[START] " + service + ": 原进程已终止，启动本环境新进程 ...");
                Process p = starter.start();
                startedServices.add(service);
                return confirmReady(service, p, port, READY_TIMEOUT_SEC, logFile);
            default:
                int free = ConflictResolver.suggestFreePort(port);
                System.out.println("[HINT] " + service + ": 建议将 application.yaml 的 " + cfgKey + " 改为 " + free
                        + "，保存后重新启动环境（本次不做任何改动，已退出）。");
                return SvcResult.fail(service, "端口 " + port + " 被占用，建议改 " + cfgKey + " 后重新启动环境");
        }
    }

    /**
     * FOREIGN（多端口）：Camunda 一组端口中若干被非本环境进程占用——汇总报告后三选一：
     * 复用原有环境 / 杀掉占用进程并启动新进程 / 改用其它端口（提示空闲端口，改 application.yaml 后重启）。
     * 非交互环境（右键双击/输入重定向）直接失败退出，不擅自杀进程。
     */
    private SvcResult resolveForeignPorts(String service, List<PortSpec> occupied, Starter starter, Path logFile,
                                          int readyPort) throws IOException {
        StringBuilder desc = new StringBuilder();
        StringBuilder holders = new StringBuilder();
        for (PortSpec s : occupied) {
            if (desc.length() > 0) {
                desc.append("、");
            }
            desc.append(s.port());
            if (s.desc() != null && !s.desc().isBlank()) {
                desc.append("（").append(s.desc()).append("）");
            }
            String holder = describeProcess(ProcessUtil.pidOnPort(s.port()));
            if (!holder.isBlank()) {
                if (holders.length() > 0) {
                    holders.append(" | ");
                }
                holders.append("端口 ").append(s.port()).append(" -> ").append(holder);
            }
        }
        System.out.println("[CONFLICT] " + service + ": 端口 " + desc + " 被非本环境进程占用");
        if (holders.length() > 0) {
            System.out.println("           占用者: " + holders);
        }
        if (!ConflictResolver.interactive()) {
            System.out.println("[FAIL] 非交互环境无法处理端口冲突，已中止。");
            System.out.println("       请手动释放端口，或修改 application.yaml 的 env-prepare.camunda-port /"
                    + " env-prepare.camunda-grpc-port 后重试。");
            return SvcResult.fail(service, "端口 " + desc + " 被非本环境进程占用，且非交互环境无法处理");
        }
        switch (ConflictResolver.ask(service, "端口 " + desc, holders.toString())) {
            case REUSE:
                System.out.println("[INFO] " + service + ": 复用原有环境（沿用占用端口的进程，不启动新进程）。");
                return SvcResult.ok(service, "复用（沿用占用端口的原有进程）");
            case KILL_RESTART:
                for (PortSpec s : occupied) {
                    long pid = ProcessUtil.pidOnPort(s.port());
                    if (pid > 0) {
                        System.out.println("[STOP] " + service + ": 终止占用端口 " + s.port() + " 的进程 PID=" + pid + " ...");
                        ProcessUtil.killTree(pid);
                    }
                }
                waitPortsFree(occupied, 15);
                System.out.println("[START] " + service + ": 原进程已终止，启动本环境新进程 ...");
                Process p = starter.start();
                startedServices.add(service);
                return confirmReady(service, p, readyPort, READY_TIMEOUT_SEC, logFile, true, "camunda");
            default:
                int newMain = ConflictResolver.suggestFreePort(parseIntPort(cfg.camundaPort(), 9080));
                int newGrpc = ConflictResolver.suggestFreePort(parseIntPort(cfg.camundaGrpcPort(), 26500));
                System.out.println("[HINT] " + service + ": 建议将 application.yaml 的 env-prepare.camunda-port 改为 "
                        + newMain + "、env-prepare.camunda-grpc-port 改为 " + newGrpc
                        + "，保存后重新启动环境（本次不做任何改动，已退出）。");
                return SvcResult.fail(service,
                        "端口被占用，建议改 env-prepare.camunda-port / camunda-grpc-port 后重新启动环境");
        }
    }

    /** 等待一组端口全部释放（最多 maxSec 秒）。 */
    private void waitPortsFree(List<PortSpec> specs, int maxSec) {
        for (int i = 0; i < maxSec; i++) {
            boolean allFree = true;
            for (PortSpec s : specs) {
                if (!ProcessUtil.portFree(s.port())) {
                    allFree = false;
                    break;
                }
            }
            if (allFree) {
                return;
            }
            ProcessUtil.sleep(1000);
        }
    }

    // ==================== 归属判定与辅助 ====================

    /**
     * 按本环境软件目录匹配正在运行的实例进程（用于判定 MINE 归属）。
     * kind：ontop / camunda / rabbit。归属判据以「命令行含本环境 home 路径」为主，尽量不误伤其它应用实例。
     */
    private List<ProcessUtil.WinProcess> matchInstances(String kind) {
        String home;
        switch (kind) {
            case "ontop" -> home = cfg.ontopHome();
            case "camunda" -> home = cfg.camundaHome();
            case "rabbit" -> home = cfg.rabbitmqHome();
            default -> home = null;
        }
        String homeLc = home == null ? "" : home.toLowerCase(Locale.ROOT);
        String nodeLc = "rabbit".equals(kind) ? rabbitInstanceKey().toLowerCase(Locale.ROOT) : "";
        List<ProcessUtil.WinProcess> hits = new ArrayList<>();
        for (ProcessUtil.WinProcess wp : ProcessUtil.snapshot()) {
            String name = wp.name().toLowerCase(Locale.ROOT);
            String cl = wp.commandLine() == null ? "" : wp.commandLine().toLowerCase(Locale.ROOT);
            boolean candidate;
            switch (kind) {
                case "ontop" -> candidate = name.contains("ontop")
                        || (name.equals("java.exe") && cl.contains("ontop"));
                // Camunda 由 c8run 启动器 + Java 子进程（StandaloneCamunda / camunda-zeebe）两部分组成：
                // 启动器交棒后会自行退出，真正占端口的是 Java 子进程，故须同时把 Java 子进程识别为本环境实例。
                case "camunda" -> candidate = name.contains("c8run")
                        || cl.contains("c8run")
                        || (name.equals("java.exe")
                            && (cl.contains("standalonecamunda") || cl.contains("c8run") || cl.contains("camunda-zeebe")));
                case "rabbit" -> candidate = name.equals("erl.exe") || name.contains("rabbitmq");
                default -> candidate = false;
            }
            if (!candidate) {
                continue;
            }
            boolean mine;
            if (homeLc.isEmpty()) {
                mine = true;
            } else if ("rabbit".equals(kind)) {
                mine = cl.contains(homeLc) || (!nodeLc.isEmpty() && cl.contains(nodeLc));
            } else {
                mine = cl.contains(homeLc);
            }
            if (mine) {
                hits.add(wp);
            }
        }
        return hits;
    }

    private String describeProcess(long pid) {
        if (pid <= 0) {
            return "";
        }
        for (ProcessUtil.WinProcess wp : ProcessUtil.snapshot()) {
            if (wp.pid() == pid) {
                return "PID=" + pid + " " + wp.name() + " " + wp.commandLine();
            }
        }
        return "PID=" + pid;
    }

    private void waitPortFree(int port, int maxSec) {
        for (int i = 0; i < maxSec; i++) {
            if (ProcessUtil.portFree(port)) {
                return;
            }
            ProcessUtil.sleep(1000);
        }
    }

    private static int parseIntPort(String s, int def) {
        try {
            return Integer.parseInt(s.strip());
        } catch (Exception e) {
            return def;
        }
    }

    // ==================== 停止（仅针对本环境实例） ====================

    private void stopCamunda() {
        if (!cfg.bpmnPathDefined()) {
            System.out.println("[SKIP] Camunda: 未定义 ontology.bpmn-path，跳过停止。");
            return;
        }
        System.out.println("[STOP] Camunda: 执行 c8run.exe stop ...");
        Path exe = Path.of(cfg.camundaHome(), "c8run.exe");
        ProcessUtil.ExecResult r = ProcessUtil.run(
                List.of(exe.toString(), "stop"), Path.of(cfg.camundaHome()), null, 30);
        if (r.code() >= 0) {
            System.out.println("       [OK] Camunda 停止指令已发送");
        } else {
            System.out.println("       [WARN] Camunda 停止异常: " + r.stderr());
        }

        // 兜底清理：c8run stop 只终止其自身记录的进程；若上次为异常退出/交棒后启动器先行退出，
        // 其 Java 子进程（StandaloneCamunda）可能残留并持续占用 26500/26502 等端口，导致下次启动 BindException。
        // 这里再按「本环境 home 归属」查找并终止残留的 Camunda 进程树，最后确认整组端口已释放。
        ProcessUtil.sleep(2000);
        List<PortSpec> specs = camundaPortSpecs();
        boolean stillOccupied = specs.stream().anyMatch(s -> !ProcessUtil.portFree(s.port()));
        List<ProcessUtil.WinProcess> hits = matchInstances("camunda");
        if (!hits.isEmpty() || stillOccupied) {
            System.out.println("       [WARN] 检测到残留的 Camunda 进程 / 端口占用，执行兜底清理 ...");
            for (ProcessUtil.WinProcess wp : hits) {
                System.out.println("       正在终止 Camunda 残留进程树 PID=" + wp.pid() + " ...");
                ProcessUtil.killTree(wp.pid());
            }
            waitPortsFree(specs, 30);
        }
        if (specs.stream().allMatch(s -> ProcessUtil.portFree(s.port()))) {
            System.out.println("       [OK] Camunda 已停止，相关端口均已释放");
        } else {
            StringBuilder busy = new StringBuilder();
            for (PortSpec s : specs) {
                if (!ProcessUtil.portFree(s.port())) {
                    busy.append(busy.length() == 0 ? "" : "、").append(s.port());
                }
            }
            System.out.println("       [WARN] 以下端口仍被占用: " + busy + "，下次启动可能失败，请检查占用进程");
        }
    }

    private void stopOntop() {
        if (!cfg.ontopConfigured()) {
            System.out.println("[SKIP] Ontop: application.yaml 未配置齐全的 Ontop 参数"
                    + "（env-prepare.java-home/ontop-home 与 ontology.obda-path/obda-properties-path/main-path），跳过停止。");
            return;
        }
        System.out.println("[STOP] Ontop: 查找并终止本环境进程树 ...");
        List<ProcessUtil.WinProcess> hits = matchInstances("ontop");
        if (hits.isEmpty()) {
            System.out.println("       [INFO] 未发现运行中的本环境 Ontop 进程");
            return;
        }
        for (ProcessUtil.WinProcess wp : hits) {
            System.out.println("       正在终止 Ontop 进程树 PID=" + wp.pid() + " ...");
            ProcessUtil.killTree(wp.pid());
        }
        System.out.println("       [OK] Ontop 已停止");
    }

    private void stopRabbitmq() {
        if (!cfg.rabbitmqDefined()) {
            System.out.println("[SKIP] RabbitMQ: 未定义 env-prepare.rabbitmq，跳过停止。");
            return;
        }
        System.out.println("[STOP] RabbitMQ: 查找并终止本环境 Erlang 节点 ...");
        List<ProcessUtil.WinProcess> hits = matchInstances("rabbit");
        if (hits.isEmpty()) {
            System.out.println("       [INFO] 未发现运行中的本环境 RabbitMQ/Erlang 进程");
        } else {
            for (ProcessUtil.WinProcess wp : hits) {
                System.out.println("       正在终止 PID=" + wp.pid() + " ...");
                ProcessUtil.killTree(wp.pid());
            }
        }
        ProcessUtil.sleep(2000);
        maybeStopEpmd();
        if (ProcessUtil.portFree(25672)) {
            System.out.println("       [OK] RabbitMQ 已停止，端口 25672 已释放");
        } else {
            System.out.println("       [WARN] 端口 25672 仍被占用");
        }
    }

    /**
     * 清理 Erlang 共享端口映射守护进程 epmd.exe（端口 4369）。
     * epmd 由多个 Erlang/RabbitMQ 实例共享，故仅当本机已无其它 erl/rabbitmq 进程存活、
     * 且 4369 占用者确为 epmd 时才终止，避免误伤其它 Erlang 程序。
     */
    private void maybeStopEpmd() {
        if (hasOtherRabbitOrErlang()) {
            System.out.println("       [INFO] 检测到其它 Erlang/RabbitMQ 进程仍在运行，保留共享的 epmd.exe");
            return;
        }
        long epmdPid = ProcessUtil.pidOnPort(4369);
        if (epmdPid > 0 && ProcessUtil.processNameContains(epmdPid, "epmd")) {
            System.out.println("       终止 epmd.exe PID=" + epmdPid + " (Erlang 端口映射守护进程，端口 4369)");
            ProcessUtil.killTree(epmdPid);
        }
    }

    /** 本机是否还有其它 Erlang/RabbitMQ 进程（epmd 不算）存活——用于决定能否安全清理共享的 epmd。 */
    private boolean hasOtherRabbitOrErlang() {
        for (ProcessUtil.WinProcess wp : ProcessUtil.snapshot()) {
            String name = wp.name() == null ? "" : wp.name().toLowerCase(Locale.ROOT);
            if (name.equals("erl.exe") || name.contains("rabbitmq")) {
                return true;
            }
        }
        return false;
    }

    /** 退出时停止 MySQL（仅当本次由本进程启动时调用）。 */
    private void stopMysql() {
        if (!cfg.mysqlDefined()) {
            System.out.println("[SKIP] MySQL: 未定义 env-prepare.mysql，跳过停止。");
            return;
        }
        mysqlService().stop(null);
    }

    private MysqlService mysqlService() {
        if (mysql == null) {
            mysql = new MysqlService(cfg);
        }
        return mysql;
    }

    /** 初始化持久化数据：init(reset)=删旧重建；start=缺则建、有则留（不删任何已有数据）。 */
    private void initializeData() {
        if (cfg.mysqlDefined()) {
            int rc = new DatabaseInitializer(cfg).run(resetData);
            if (rc != 0) {
                System.out.println("[WARN] MySQL 数据初始化返回码 " + rc + "，请查看日志: "
                        + cfg.logDir().resolve("mysql-init.log"));
            }
        } else {
            System.out.println("[SKIP] MySQL 数据初始化：未定义 env-prepare.mysql。");
        }
        if (cfg.rabbitmqDefined()) {
            int rc = new RabbitMqInitializer(cfg).run(resetData);
            if (rc != 0) {
                System.out.println("[WARN] RabbitMQ 资源初始化返回码 " + rc + "，请查看日志: "
                        + cfg.logDir().resolve("rabbitmq-init.log"));
            }
        } else {
            System.out.println("[SKIP] RabbitMQ 资源初始化：未定义 env-prepare.rabbitmq。");
        }
    }

    // ==================== 启动 ====================

    private Process startRabbitmq() throws IOException {
        if (!cfg.rabbitmqDefined()) {
            System.out.println("[SKIP] RabbitMQ: 未定义 env-prepare.rabbitmq，跳过启动。");
            return null;
        }
        Map<String, String> env = rabbitEnv();
        applyErlangEnv(env);
        Path bat = Path.of(cfg.rabbitmqHome(), "sbin", "rabbitmq-server.bat");
        return startService("RabbitMQ", List.of(bat.toString()),
                Path.of(cfg.rabbitmqHome()), cfg.logDir().resolve("rabbitmq.log"),
                env.isEmpty() ? null : env);
    }

    /**
     * RabbitMQ 运行依赖 Erlang：rabbitmq-server.bat 直接使用 {@code %ERLANG_HOME%\bin\erl.exe}
     * （校验失败即报 "ERLANG_HOME not set correctly" 退出）。安装环境未把 Erlang 加入系统 PATH，
     * 故显式注入 ERLANG_HOME，并把其 bin 前置到 PATH（兼容 rabbitmq-env.bat 用
     * {@code Get-Command erl.exe} 反查 PATH 的场景）。
     */
    private void applyErlangEnv(Map<String, String> env) {
        String erlangHome = cfg.erlangHome();
        if (erlangHome == null || erlangHome.isBlank()) {
            System.out.println("[WARN] 未配置 env-prepare.erlang-home，RabbitMQ 可能因找不到 Erlang(erl.exe) 而启动失败。");
            return;
        }
        Path bin = Path.of(erlangHome, "bin");
        env.put("ERLANG_HOME", erlangHome);
        String path = System.getenv("PATH");
        env.put("PATH", (path == null || path.isBlank())
                ? bin.toString() : bin + ";" + path);
        System.out.println("       [INFO] 注入 ERLANG_HOME=" + erlangHome);
    }

    /**
     * RabbitMQ 监听端口环境变量：默认端口(5672/15672)沿用安装默认、行为不变；
     * 自定义端口则生成 rabbitmq.conf 并隔离节点名/数据目录/分发端口，使多实例并行不冲突。
     */
    private Map<String, String> rabbitEnv() throws IOException {
        Map<String, String> env = new LinkedHashMap<>();
        boolean defaultPorts = "5672".equals(cfg.rabbitPort())
                && "15672".equals(cfg.rabbitManagementPort());
        if (defaultPorts) {
            return env;
        }
        Path dir = cfg.generatedDir();
        Files.createDirectories(dir);
        Path conf = dir.resolve("rabbitmq.conf");
        String content = "# 由 EnvPrepare 生成：RabbitMQ 监听端口（多应用并行时按应用区分）\n"
                + "listeners.tcp.default = " + cfg.rabbitPort() + "\n"
                + "management.tcp.port = " + cfg.rabbitManagementPort() + "\n";
        Files.writeString(conf, content, StandardCharsets.UTF_8);
        env.put("RABBITMQ_CONFIG_FILE", conf.toString());
        String key = rabbitInstanceKey();
        env.put("RABBITMQ_NODENAME", key + "@localhost");
        env.put("RABBITMQ_MNESIA_BASE", dir.resolve("rabbitmq").resolve(key).toString());
        env.put("RABBITMQ_LOG_BASE", cfg.logDir().resolve("rabbitmq-" + key).toString());
        env.put("RABBITMQ_DIST_PORT", String.valueOf(25672 + Math.floorMod(key.hashCode(), 10000)));
        System.out.println("[INFO] RabbitMQ 自定义端口: amqp=" + cfg.rabbitPort()
                + ", management=" + cfg.rabbitManagementPort() + "，节点=" + key + "，配置: " + conf);
        return env;
    }

    private String rabbitInstanceKey() {
        Path name = cfg.moduleRoot().getFileName();
        String base = name == null ? "app" : name.toString();
        String key = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        return key.isBlank() ? "app" : key;
    }

    private Process startOntop() throws IOException {
        if (!cfg.ontopConfigured()) {
            System.out.println("[SKIP] Ontop: application.yaml 未配置齐全的 Ontop 参数"
                    + "（env-prepare.java-home/ontop-home 与 ontology.obda-path/obda-properties-path/main-path），跳过启动。");
            return null;
        }
        String properties = cfg.toFileUri(cfg.resolvePath(cfg.ontopProperties()).toString());
        String mapping = cfg.toFileUri(cfg.resolvePath(cfg.ontopMapping()).toString());
        // Ontop 的 ontologyFile(String) 内部用 new URL(s).getPath() 取路径且不做 %20 解码，
        // 故本体文件必须传「原始文件路径」而非 file URI，否则含空格路径会被解析成带 %20 的非法路径。
        String ontology = cfg.resolvePath(cfg.ontologyFile()).toString();

        System.out.println("       Properties: " + properties);
        System.out.println("       Mapping:    " + mapping);
        System.out.println("       Ontology:   " + ontology);

        Path ontopBat = Path.of(cfg.ontopHome(), "ontop.bat");
        List<String> cmd = new ArrayList<>(List.of(ontopBat.toString(), "endpoint",
                "--properties", properties));
        String catalog = cfg.ontologyCatalog();
        if (!isBlank(catalog)) {
            String catalogPath = cfg.resolvePath(catalog).toString();
            System.out.println("       Catalog:    " + catalogPath);
            cmd.add("--xml-catalog");
            cmd.add(catalogPath);
        }
        cmd.add("--port");
        cmd.add(cfg.ontopPort());
        cmd.add("-m");
        cmd.add(mapping);
        cmd.add("-t");
        cmd.add(ontology);
        return startService("Ontop", cmd, Path.of(cfg.ontopHome()),
                cfg.logDir().resolve("ontop.log"), Map.of("JAVA_HOME", cfg.javaHome()));
    }

    /** Camunda 就绪确认：c8run 为交棒型启动器（拉起 Java 子进程后自行退出），故容忍其退出、以端口监听 + 子进程存活为准。 */
    private SvcResult confirmCamundaReady(Process p, int port, Path log) {
        return confirmReady("Camunda", p, port, READY_TIMEOUT_SEC, log, true, "camunda");
    }

    private Process startCamunda() throws IOException {
        if (!cfg.bpmnPathDefined()) {
            System.out.println("[SKIP] Camunda: 未定义 ontology.bpmn-path，跳过启动。");
            return null;
        }
        Path home = Path.of(cfg.camundaHome());
        preflightCamunda(home);
        Path exe = home.resolve("c8run.exe");
        List<String> cmd = List.of(exe.toString(), "start", "--port", cfg.camundaPort());

        Map<String, String> env = new LinkedHashMap<>();
        // c8run 的 Camunda/Connectors 子进程由 camunda.bat 启动，bat 内 `if "%JAVACMD%"=="" set JAVACMD=java`
        // ——即默认从 PATH 取 java。若本机 PATH 未包含 %JAVA_HOME%\bin，子进程会立即以
        // "'java' is not recognized as an internal or external command" 退出；而 c8run 只轮询健康地址、
        // 不检测子进程，表现为「进程已启动但一直 Waiting、端口起不来」。故显式注入 JAVA_HOME，
        // 并把 %JAVA_HOME%\bin 前置到 PATH（与 Ontop 启动注入 JAVA_HOME 的处理保持一致）；
        // JAVACMD 仅在 java.exe 路径不含空格时注入——因 camunda.bat 对 %JAVACMD% 未加引号，
        // 含空格路径会被 cmd 截断报 “'C:\Program' is not recognized”（详见 JavaEnv.patch）。
        applyJavaEnv(env);
        env.put("CAMUNDA_API_GRPC_PORT", cfg.camundaGrpcPort());
        env.put("ZEEBE_GATEWAY_NETWORK_PORT", cfg.camundaGrpcPort());
        // 关键：c8run 严格按「当前工作目录」查找 .env 与 configuration/application.yaml，
        // 故必须以 c8run 安装目录为 cwd 启动；否则会报
        // "open .env: ..." / "missing default config at <cwd>\configuration\application.yaml"。
        // 同时剔除会劫持端口的宿主环境变量（SERVER_PORT / SERVER__PORT 等），使 Connectors 不被
        // 覆盖为错误端口（详见 PORT_HIJACK_ENV_KEYS 说明）。
        return startService("Camunda", cmd, home,
                cfg.logDir().resolve("camunda.log"), env, PORT_HIJACK_ENV_KEYS);
    }

    /**
     * c8run 启动前预检：校验启动器与随包文件是否齐全，补救可自动修复项，避免 c8run 直接以
     * “open .env”/“missing default config”退出而被误判为端口问题。
     * <ul>
     *   <li>c8run.exe 缺失 → 直接失败并给出 camunda-home 提示；</li>
     *   <li>.env 缺失 → 警告（c8run 会报错，通常说明运行包不完整）；</li>
     *   <li>configuration/application.yaml 缺失 → 写入官方默认（H2 secondary storage）后继续。</li>
     * </ul>
     */
    private void preflightCamunda(Path home) throws IOException {
        Path exe = home.resolve("c8run.exe");
        if (!Files.isRegularFile(exe)) {
            throw new IOException("[Camunda] 未找到 c8run 启动器: " + exe
                    + "（请检查 application.yaml 的 env-prepare.camunda-home 是否指向 c8run 解压目录）");
        }
        Path dotEnv = home.resolve(".env");
        if (!Files.isRegularFile(dotEnv)) {
            System.out.println("       [WARN] 未找到 " + dotEnv
                    + "，c8run 可能报 \"open .env: The system cannot find the file specified.\"，请确认 Camunda 运行包完整。");
        }
        Path conf = home.resolve("configuration").resolve("application.yaml");
        if (!Files.isRegularFile(conf)) {
            Files.createDirectories(conf.getParent());
            Files.writeString(conf, C8RUN_DEFAULT_APP_YAML, StandardCharsets.UTF_8);
            System.out.println("       [INFO] 缺少 c8run 配置，已生成官方默认: " + conf);
        }
        System.out.println("       [INFO] c8run 工作目录: " + home + "（c8run 按 cwd 查找 .env / configuration）");
    }

    /** c8run 随包自带的默认 Zeebe 配置（configuration/application.yaml）内容；缺失时据此补全。 */
    private static final String C8RUN_DEFAULT_APP_YAML = """
            camunda:
              data:
                secondary-storage:
                  type: rdbms
                  rdbms:
                    url: jdbc:h2:file:./camunda-data/h2db
                    username: sa
                    password:
                    flushInterval: PT0.5S
                    queueSize: 1000
              security:
                initialization:
                  users:
                    - username: demo
                      password: demo
                      name: Demo
                      email: demo@example.com
                  defaultRoles:
                    admin:
                      users:
                        - demo
                # By default authorization and api protection is disabled
                authentication:
                  method: BASIC
                  unprotected-api: true
                authorizations:
                  enabled: false
              mcp:
                enabled: true

            zeebe:
              broker:
                network:
                  host: localhost
                  advertisedHost: localhost
              gateway:
                cluster:
                  initialContactPoints: zeebe:26502
                  memberId: identity
            """;

    /**
     * 向「本应用派生的子进程」注入 JDK 运行环境（应用级）：
     * JAVA_HOME / JAVACMD（java.exe 存在时）+ PATH 前置 {@code <java-home>\bin}。
     * 仅注入子进程环境，不修改任何系统/用户环境变量。
     */
    private void applyJavaEnv(Map<String, String> env) {
        if (!JavaEnv.patch(env, cfg.javaHome())) {
            System.out.println("       [WARN] 未解析到随包 java-home，Camunda 子进程可能因 PATH 中无 java 而启动失败。");
            return;
        }
        String jcmd = env.get("JAVACMD");
        System.out.println("       [INFO] 注入 JAVA_HOME=" + env.get("JAVA_HOME")
                + "，JAVACMD=" + (jcmd == null ? "(未注入，交由 PATH 解析 java)" : jcmd));
    }

    /** Camunda（c8run）实际占用的端口集合：主端口 + Zeebe gRPC + Zeebe 内部（Command API/集群通信）+ Connectors/metrics。 */
    private List<PortSpec> camundaPortSpecs() {
        List<PortSpec> specs = new ArrayList<>();
        specs.add(new PortSpec(parseIntPort(cfg.camundaPort(), 9080),
                "env-prepare.camunda-port", "Camunda 主端口(Operate/Tasklist/API)"));
        specs.add(new PortSpec(parseIntPort(cfg.camundaGrpcPort(), 26500),
                "env-prepare.camunda-grpc-port", "Zeebe gRPC 网关"));
        specs.add(new PortSpec(26501, null, "Zeebe Command API(内部)"));
        specs.add(new PortSpec(26502, null, "Zeebe 集群通信(内部)"));
        specs.add(new PortSpec(8086, null, "Connectors API"));
        specs.add(new PortSpec(9600, null, "Camunda metrics"));
        return specs;
    }

    /** 单个受管端口：port 为端口号；cfgKey 为可配置该项的 application.yaml 键（内部固定端口为 null）；desc 为说明。 */
    private record PortSpec(int port, String cfgKey, String desc) {
    }

    private Process startService(String name, List<String> cmd, Path cwd, Path logFile,
                                 Map<String, String> extraEnv) throws IOException {
        return startService(name, cmd, cwd, logFile, extraEnv, null);
    }

    /**
     * 启动单个服务子进程。
     *
     * @param extraEnv  额外注入的环境变量（在继承父进程环境之后叠加）
     * @param envRemove 需从「继承来的父进程环境」中剔除的变量名（不区分大小写）；null/空表示不剔除。
     *                  用于防止宿主环境注入的变量（如 SERVER__PORT）劫持子进程（Spring Connectors）的端口。
     */
    private Process startService(String name, List<String> cmd, Path cwd, Path logFile,
                                 Map<String, String> extraEnv, List<String> envRemove) throws IOException {
        System.out.println("[START] " + name + ": " + String.join(" ", cmd));

        if (cwd != null) {
            if (!Files.exists(cwd)) {
                throw new IOException("[" + name + "] cwd 路径不存在: " + cwd);
            }
            if (!Files.isDirectory(cwd)) {
                throw new IOException("[" + name + "] cwd 不是有效目录: " + cwd);
            }
        }

        List<String> real = ProcessUtil.wrapForWindows(cmd);

        ProcessBuilder pb = new ProcessBuilder(real);
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        if (extraEnv != null) {
            pb.environment().putAll(extraEnv);
        }
        if (envRemove != null && !envRemove.isEmpty()) {
            // Windows 环境变量名不区分大小写，但 Java 的 environment() 键集会保留原始大小写，
            // 故逐个（忽略大小写）比对后按实际键名删除；命中时打印被剔除的原值，便于排查。
            List<String> stripped = new ArrayList<>();
            for (String want : envRemove) {
                for (String actual : new ArrayList<>(pb.environment().keySet())) {
                    if (actual.equalsIgnoreCase(want)) {
                        String val = pb.environment().remove(actual);
                        stripped.add(actual + "=" + val);
                    }
                }
            }
            if (!stripped.isEmpty()) {
                System.out.println("       [INFO] 已剔除会劫持子进程端口的环境变量: " + String.join(", ", stripped));
            }
        }
        if (logFile != null) {
            Files.createDirectories(logFile.getParent());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        }

        Process p = pb.start();
        started.add(p);
        System.out.println("       [OK] " + name + " 已启动 (PID=" + p.pid() + ")");
        if (logFile != null) {
            System.out.println("       日志输出: " + logFile);
        }
        return p;
    }

    /**
     * 就绪确认：对「本次新启动的进程」等待其监听端口真正进入监听状态，避免进程一闪而过/启动失败仍报成功。
     * <p>轮询期间若进程提前退出，或超过 {@code timeoutSec} 仍未监听端口，即判失败并给出原因（含日志路径）。
     *
     * @param p 本次新启动的进程；{@code null} 表示复用已有进程（视为已就绪）。
     */
    private SvcResult confirmReady(String name, Process p, int port, int timeoutSec, Path logFile) {
        return confirmReady(name, p, port, timeoutSec, logFile, false, null);
    }

    /**
     * 就绪确认（带「启动器交棒」容忍）。
     *
     * @param launcherHandoff true=本次启动的进程是「交棒型启动器」（如 c8run.exe start）：它会拉起真正占端口的
     *                        子进程后自行退出，故其退出（exitCode=0）属预期，不能据此判失败；此时改为
     *                        「只要端口进入监听即就绪」，并在启动器退出后借助 {@code childKind} 检查子进程是否存在，
     *                        避免真失败时一直空等。
     * @param childKind       launcherHandoff 时用于探测子进程归属的 kind（如 "camunda"）；其余情况可为 null。
     */
    private SvcResult confirmReady(String name, Process p, int port, int timeoutSec, Path logFile,
                                   boolean launcherHandoff, String childKind) {
        if (p == null) {
            return SvcResult.ok(name, "复用（已在运行）");
        }
        for (int i = 0; i < timeoutSec; i++) {
            if (!ProcessUtil.portFree(port)) {
                return SvcResult.ok(name, "端口 " + port + " 已进入监听");
            }
            if (!p.isAlive()) {
                if (!launcherHandoff) {
                    return SvcResult.fail(name,
                            "进程启动后提前退出（exitCode=" + exitCodeOf(p) + "），请查看日志: " + logFile);
                }
                // 交棒型启动器：正常交棒后退出属预期。若退出后仍能探测到子进程，则继续等端口；
                // 否则给一小段宽限期（子进程可能刚拉起），仍无子进程才判失败。
                boolean childAlive = childKind != null && !matchInstances(childKind).isEmpty();
                if (!childAlive && i >= LAUNCHER_HANDOFF_GRACE_SEC) {
                    return SvcResult.fail(name,
                            "启动器已退出（exitCode=" + exitCodeOf(p) + "）且未检测到运行中的服务子进程，请查看日志: " + logFile);
                }
            }
            ProcessUtil.sleep(1000);
        }
        return SvcResult.fail(name,
                "等待端口 " + port + " 进入监听超时（" + timeoutSec + "s），进程可能启动异常，请查看日志: " + logFile);
    }

    private static String exitCodeOf(Process p) {
        try {
            return String.valueOf(p.exitValue());
        } catch (IllegalThreadStateException e) {
            return "unknown";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
