package com.ocean.envprepare;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 启动环境：先停止旧的 RabbitMQ / Ontop / Camunda 进程，再依次启动
 * RabbitMQ、MySQL（启动 Ontop 前确保已就绪：已启动则跳过）、Ontop endpoint（注入 JAVA_HOME）、
 * Camunda，并常驻等待 Ctrl+C 退出；退出时停止 MySQL 并清理已启动进程。
 */
public final class EnvironmentStarter {

    private final EnvConfig cfg;
    private final List<Process> started = new ArrayList<>();
    private MysqlService mysql;

    public EnvironmentStarter(EnvConfig cfg) {
        this.cfg = cfg;
    }

    public int run() {
        System.out.println("=".repeat(50));
        System.out.println(" [START] 开始初始化本体环境服务（按 application.yaml 配置启停 RabbitMQ/Ontop/Camunda） ");
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
        if (cfg.mysqlDefined() && isBlank(cfg.mysqlServiceName())) {
            System.out.println("[ERROR] 已定义 env-prepare.mysql，但缺少 env-prepare.mysql.service-name");
            return 1;
        }

        if (!cfg.ontopConfigured() && !cfg.rabbitmqDefined() && !cfg.bpmnPathDefined()
                && !cfg.mysqlDefined()) {
            System.out.println("[SKIP] application.yaml 未配置 Ontop / RabbitMQ / Camunda / MySQL 任一服务，无需启动。");
            return 0;
        }

        stopCamunda();
        stopOntop();
        stopRabbitmq();
        ProcessUtil.sleep(2000);

        try {
            Files.createDirectories(cfg.logDir());
        } catch (IOException e) {
            System.out.println("[ERROR] 创建日志目录失败: " + e.getMessage());
            return 1;
        }

        registerShutdownHook();

        try {
            startRabbitmq();
            if (!startMysql()) {
                System.out.println("[FAIL] MySQL 未就绪，已中止 Ontop / Camunda 启动。");
                return 1;
            }
            startOntop();
            startCamunda();
        } catch (IOException e) {
            System.out.println("[FAIL] 启动服务失败: " + e.getMessage());
            return 1;
        }

        System.out.println("\n" + "=".repeat(50));
        System.out.println(" [OK] 环境服务处理完成（按配置启动的服务已就绪）。");
        System.out.println(" [INFO] MySQL 已确保在 Ontop 之前就绪（未配置 env-prepare.mysql 时跳过）；");
        System.out.println("        首次建库建表 + 灌数请另行运行 EnvPrepare createdb。");
        System.out.println(" [INFO] 统一日志目录: " + cfg.logDir());
        System.out.println(" [INFO] 按 Ctrl+C 停止所有服务并退出。");
        System.out.println("=".repeat(50) + "\n");

        while (true) {
            ProcessUtil.sleep(1000);
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[EXIT] 收到退出信号，正在清理所有进程...");
            for (Process p : started) {
                if (p.isAlive()) {
                    ProcessUtil.killTree(p.pid());
                }
            }
            stopCamunda();
            stopMysql();
            System.out.println("[OK] 清理完成，已安全退出。");
        }));
    }

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
    }

    private void stopOntop() {
        if (!cfg.ontopConfigured()) {
            System.out.println("[SKIP] Ontop: application.yaml 未配置齐全的 Ontop 参数"
                    + "（env-prepare.java-home/ontop-home 与 ontology.obda-path/obda-properties-path/main-path），跳过停止。");
            return;
        }
        System.out.println("[STOP] Ontop: 查找并终止进程树 ...");
        Set<Long> pids = new LinkedHashSet<>();
        for (ProcessUtil.WinProcess wp : ProcessUtil.snapshot()) {
            String name = wp.name().toLowerCase(Locale.ROOT);
            String cl = wp.commandLine().toLowerCase(Locale.ROOT);
            if (name.contains("ontop") || (name.equals("java.exe") && cl.contains("ontop"))) {
                pids.add(wp.pid());
            }
        }
        if (pids.isEmpty()) {
            System.out.println("       [INFO] 未发现运行中的 Ontop 进程");
            return;
        }
        for (long pid : pids) {
            System.out.println("       正在终止 Ontop 进程树 PID=" + pid + " ...");
            ProcessUtil.killTree(pid);
        }
        System.out.println("       [OK] Ontop 已完全停止");
    }

    private void stopRabbitmq() {
        if (!cfg.rabbitmqDefined()) {
            System.out.println("[SKIP] RabbitMQ: 未定义 env-prepare.rabbitmq，跳过停止。");
            return;
        }
        System.out.println("[STOP] RabbitMQ: 查找并终止 Erlang 节点 ...");
        Set<Long> pids = new LinkedHashSet<>();
        for (ProcessUtil.WinProcess wp : ProcessUtil.snapshot()) {
            String name = wp.name().toLowerCase(Locale.ROOT);
            String cl = wp.commandLine().toLowerCase(Locale.ROOT);
            if (name.equals("erl.exe") && (cl.contains("rabbit") || cl.contains("25672"))) {
                pids.add(wp.pid());
            }
            if (name.contains("rabbitmq")) {
                pids.add(wp.pid());
            }
        }
        if (pids.isEmpty()) {
            System.out.println("       [INFO] 未发现运行中的 RabbitMQ/Erlang 进程");
            return;
        }
        for (long pid : pids) {
            System.out.println("       正在终止 PID=" + pid + " ...");
            ProcessUtil.killTree(pid);
        }
        ProcessUtil.sleep(2000);
        if (ProcessUtil.portFree(25672)) {
            System.out.println("       [OK] RabbitMQ 已完全停止，端口 25672 已释放");
        } else {
            System.out.println("       [WARN] 端口 25672 仍被占用");
        }
    }

    /** 启动 Ontop 前确保 MySQL 已就绪：未配置则跳过；未运行则启动、已运行则跳过；启动失败返回 false。 */
    private boolean startMysql() {
        if (!cfg.mysqlDefined()) {
            System.out.println("[SKIP] MySQL: 未定义 env-prepare.mysql，跳过启动。");
            return true;
        }
        return mysqlService().ensureStarted(null) != MysqlService.StartOutcome.FAILED;
    }

    /** 退出时停止 MySQL（已配置 env-prepare.mysql 时）。 */
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

    private void startRabbitmq() throws IOException {
        if (!cfg.rabbitmqDefined()) {
            System.out.println("[SKIP] RabbitMQ: 未定义 env-prepare.rabbitmq，跳过启动。");
            return;
        }
        Path bat = Path.of(cfg.rabbitmqHome(), "sbin", "rabbitmq-server.bat");
        startService("RabbitMQ", List.of(bat.toString()),
                Path.of(cfg.rabbitmqHome()), cfg.logDir().resolve("rabbitmq.log"), null);
    }

    private void startOntop() throws IOException {
        if (!cfg.ontopConfigured()) {
            System.out.println("[SKIP] Ontop: application.yaml 未配置齐全的 Ontop 参数"
                    + "（env-prepare.java-home/ontop-home 与 ontology.obda-path/obda-properties-path/main-path），跳过启动。");
            return;
        }
        String properties = cfg.toFileUri(cfg.resolvePath(cfg.ontopProperties()).toString());
        String mapping = cfg.toFileUri(cfg.resolvePath(cfg.ontopMapping()).toString());
        String ontology = cfg.toFileUri(cfg.resolvePath(cfg.ontologyFile()).toString());

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
        cmd.add("-m");
        cmd.add(mapping);
        cmd.add("-t");
        cmd.add(ontology);
        startService("Ontop", cmd, Path.of(cfg.ontopHome()),
                cfg.logDir().resolve("ontop.log"), Map.of("JAVA_HOME", cfg.javaHome()));
    }

    private void startCamunda() throws IOException {
        if (!cfg.bpmnPathDefined()) {
            System.out.println("[SKIP] Camunda: 未定义 ontology.bpmn-path，跳过启动。");
            return;
        }
        Path exe = Path.of(cfg.camundaHome(), "c8run.exe");
        List<String> cmd = List.of(exe.toString(), "start", "--port", "9080");
        startService("Camunda", cmd, Path.of(cfg.camundaHome()),
                cfg.logDir().resolve("camunda.log"), null);
    }

    private Process startService(String name, List<String> cmd, Path cwd, Path logFile,
                                 Map<String, String> extraEnv) throws IOException {
        System.out.println("[START] " + name + ": " + String.join(" ", cmd));

        if (cwd != null) {
            if (!Files.exists(cwd)) {
                throw new IOException("[" + name + "] cwd 路径不存在: " + cwd);
            }
            if (!Files.isDirectory(cwd)) {
                throw new IOException("[" + name + "] cwd 不是有效目录: " + cwd);
            }
        }

        List<String> real = new ArrayList<>();
        String exe = cmd.get(0).toLowerCase(Locale.ROOT);
        if (exe.endsWith(".bat") || exe.endsWith(".cmd")) {
            real.add("cmd.exe");
            real.add("/c");
        }
        real.addAll(cmd);

        ProcessBuilder pb = new ProcessBuilder(real);
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        if (extraEnv != null) {
            pb.environment().putAll(extraEnv);
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

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
