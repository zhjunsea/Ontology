package com.ocean.env;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ServiceLauncher {

    private static final Map<String, String> CONFIG = new LinkedHashMap<>();
    private static final List<Process> PROCESSES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path configPath = Paths.get("startup-config.properties");
        loadConfig(configPath);
        validatePaths();
        ensureDotEnv();

        // 构建独立环境变量，仅注入 JAVA_HOME，不污染系统全局
        Map<String, String> serviceEnv = new HashMap<>(System.getenv());
        serviceEnv.put("JAVA_HOME", CONFIG.get("JAVA_HOME"));

        System.out.println("==========================================");
        System.out.println("  Semantic Stack Startup (Java Launcher)");
        System.out.println("==========================================\n");

        // 1. 启动 Ontop Endpoint
        List<String> ontopCmd = Arrays.asList(
                resolveExe("ONTOP_HOME", "ontop.bat"),
                "endpoint",
                "--properties=myPizza.properties",
                "-m", "myPizza.obda",
                "-t", CONFIG.get("ONTOLOGY_FILE")
        );
        startService("Ontop-Endpoint", ontopCmd, CONFIG.get("MAPPING_DIR"), serviceEnv);

        // 2. 启动 Camunda
        List<String> camundaCmd = Arrays.asList(
                resolveExe("CAMUNDA_HOME", "c8run.exe"),
                "start", "--port", "9080"
        );
        startService("Camunda-C8Run", camundaCmd, null, serviceEnv);

        // 3. 启动 RabbitMQ
        List<String> rabbitmqCmd = Collections.singletonList(
                Paths.get(CONFIG.get("RABBITMQ_HOME"), "sbin", "rabbitmq-server.bat").toString()
        );
        startService("RabbitMQ-Server", rabbitmqCmd, null, serviceEnv);

        System.out.println("\n==========================================");
        System.out.println("  All services launched successfully!");
        System.out.println("==========================================");
        System.out.println("提示: 按 Ctrl+C 可优雅停止所有服务\n");

        // 注册关闭钩子，实现优雅停止
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[STOP] 正在停止所有服务...");
            for (Process p : PROCESSES) {
                if (p.isAlive()) {
                    p.destroy();
                    System.out.println("       Terminated PID=" + p.pid());
                }
            }
            System.out.println("[DONE] 所有服务已停止");
        }));

        // 主线程保持运行，监控子进程状态
        while (true) {
            for (Process p : PROCESSES) {
                if (!p.isAlive()) {
                    System.out.printf("[WARN] 服务已退出 (PID=%d, ExitCode=%d)%n", p.pid(), p.exitValue());
                }
            }
            TimeUnit.SECONDS.sleep(5);
        }
    }

    private static void loadConfig(Path path) throws IOException {
        if (!Files.exists(path)) {
            System.err.println("[ERROR] 配置文件不存在: " + path.toAbsolutePath());
            System.exit(1);
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
        props.forEach((k, v) -> CONFIG.put(k.toString().trim(), v.toString().trim()));
    }

    private static void validatePaths() {
        String[][] checks = {
                {"JAVA_HOME", "dir"}, {"ONTOLOGY_FILE", "file"}, {"MAPPING_DIR", "dir"},
                {"ONTOP_HOME", "dir"}, {"CAMUNDA_HOME", "dir"}, {"RABBITMQ_HOME", "dir"}
        };
        for (String[] check : checks) {
            Path p = Paths.get(CONFIG.get(check[0]));
            boolean valid = "dir".equals(check[1]) ? Files.isDirectory(p) : Files.isRegularFile(p);
            if (!valid) {
                System.err.printf("[ERROR] %s 路径无效: %s%n", check[0], p);
                System.exit(1);
            }
        }
        // 校验关键可执行文件
        String[] exeChecks = {
                resolveExe("ONTOP_HOME", "ontop.bat"),
                resolveExe("CAMUNDA_HOME", "c8run.exe"),
                Paths.get(CONFIG.get("RABBITMQ_HOME"), "sbin", "rabbitmq-server.bat").toString()
        };
        for (String exe : exeChecks) {
            if (!Files.isExecutable(Paths.get(exe))) {
                System.err.println("[ERROR] 可执行文件未找到或无权限: " + exe);
                System.exit(1);
            }
        }
        System.out.println("[OK] 所有路径校验通过\n");
    }

    private static void ensureDotEnv() throws IOException {
        Path envFile = Paths.get(CONFIG.get("MAPPING_DIR"), ".env");
        if (!Files.exists(envFile)) {
            Files.writeString(envFile, "# Auto-generated empty .env for Ontop\n");
            System.out.println("[INFO] 已自动生成空 .env 文件: " + envFile);
        }
    }

    private static String resolveExe(String homeKey, String exeName) {
        return Paths.get(CONFIG.get(homeKey), exeName).toString();
    }

    private static void startService(String name, List<String> cmd, String cwd, Map<String, String> env) throws IOException {
        System.out.printf("[START] %s...%n", name);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().putAll(env);
        if (cwd != null) pb.directory(new File(cwd));

        // 日志重定向到工作目录或当前目录
        Path logDir = cwd != null ? Paths.get(cwd) : Paths.get(".");
        Path logFile = logDir.resolve(name.toLowerCase().replace(" ", "_") + ".log");
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        PROCESSES.add(process);
        System.out.printf("        PID=%d, Log=%s%n", process.pid(), logFile.toAbsolutePath());
    }
}