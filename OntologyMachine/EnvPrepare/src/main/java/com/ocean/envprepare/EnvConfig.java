package com.ocean.envprepare;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 环境准备配置读取器：从应用模块的 application.yaml 读取 ontology 节点与应用特有的 env-prepare 项，
 * 再与 EnvPrepare 内置的通用环境配置（classpath:/env.yaml，含 MySQL/RabbitMQ 连接默认值）合并，
 * 同名项以应用 application.yaml 为准（应用覆盖通用）。
 * 是否启用 MySQL / RabbitMQ 由应用 application.yaml 是否声明 env-prepare.mysql / env-prepare.rabbitmq 节点决定。
 * 相对路径统一以模块根目录（包含 src/main/resources/application.yaml 的目录）为基准解析。
 *
 * <p><b>软件安装位置不写死</b>：java-home / ontop-home / camunda-home / rabbitmq-home / erlang-home
 * 按「应用 application.yaml（分发时由安装器按实际安装目录注入）→ 环境变量
 * （JAVA_HOME / ONTOP_HOME / CAMUNDA_HOME / RABBITMQ_HOME / ERLANG_HOME）→ 自动探测
 * （JDK 取当前 JVM 的 java.home；Erlang 从 PATH 中的 bin\erl.exe 反推）」依次解析，避免在不同安装环境间写死路径。
 */
public final class EnvConfig {

    private static final String CONFIG_RELATIVE = "src/main/resources/application.yaml";
    private static final String COMMON_ENV_RESOURCE = "/env.yaml";

    private final Path moduleRoot;
    private final Path configFile;
    private final Map<String, Object> env;
    private final Map<String, Object> ontology;
    private final Map<String, Object> mysql;
    private final Map<String, Object> rabbit;
    private final boolean rabbitmqDefined;
    private final boolean mysqlDefined;

    private EnvConfig(Path moduleRoot, Path configFile, Map<String, Object> root) {
        this.moduleRoot = moduleRoot;
        this.configFile = configFile;
        Map<String, Object> appEnv = node(root, "env-prepare");
        this.ontology = node(root, "ontology");
        this.env = merge(loadCommonEnv(), appEnv);
        this.mysql = node(env, "mysql");
        this.rabbit = node(env, "rabbitmq");
        this.rabbitmqDefined = appEnv.get("rabbitmq") instanceof Map;
        this.mysqlDefined = appEnv.get("mysql") instanceof Map;
    }

    /** 读取 EnvPrepare 内置的通用环境配置（classpath:/env.yaml）的 env-prepare 节点；缺失时返回空表。 */
    private static Map<String, Object> loadCommonEnv() {
        InputStream in = EnvConfig.class.getResourceAsStream(COMMON_ENV_RESOURCE);
        if (in == null) {
            return new LinkedHashMap<>();
        }
        try (in) {
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            return node(parseYaml(lines), "env-prepare");
        } catch (IOException e) {
            throw new IllegalStateException("读取通用环境配置失败: " + COMMON_ENV_RESOURCE, e);
        }
    }

    /** 递归合并：override 覆盖 base，叶子级以 override 为准（null 值不覆盖）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> result = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> e : override.entrySet()) {
            Object ov = e.getValue();
            if (ov == null) {
                continue;
            }
            Object bv = result.get(e.getKey());
            if (bv instanceof Map && ov instanceof Map) {
                result.put(e.getKey(), merge((Map<String, Object>) bv, (Map<String, Object>) ov));
            } else {
                result.put(e.getKey(), ov);
            }
        }
        return result;
    }

    public static EnvConfig load() {
        Path moduleRoot = findModuleRoot();
        Path candidate = moduleRoot.resolve(CONFIG_RELATIVE);
        if (Files.isRegularFile(candidate)) {
            List<String> lines;
            try {
                lines = Files.readAllLines(candidate, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("读取配置文件失败: " + candidate, e);
            }
            return new EnvConfig(moduleRoot, candidate, parseYaml(lines));
        }
        InputStream in = EnvConfig.class.getResourceAsStream("/application.yaml");
        if (in == null) {
            throw new IllegalStateException(
                    "未找到 application.yaml，已尝试: " + candidate + " 与 classpath:/application.yaml");
        }
        List<String> lines;
        try (in) {
            lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new IllegalStateException("读取 classpath 配置失败", e);
        }
        return new EnvConfig(moduleRoot, Path.of("classpath:/application.yaml"), parseYaml(lines));
    }

    /**
     * 按显式指定的 application.yaml 路径加载配置。模块根目录由该文件路径推导
     * （形如 &lt;模块&gt;/src/main/resources/application.yaml 时取模块目录），
     * 以保证配置内的相对路径（obda/bpmn/main-path、ddl/data-file）以对应模块根为基准解析。
     */
    public static EnvConfig load(Path configFile) {
        Path actual = configFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(actual)) {
            throw new IllegalStateException("指定的配置文件不存在: " + actual);
        }
        Path moduleRoot = deriveModuleRoot(actual);
        List<String> lines;
        try {
            lines = Files.readAllLines(actual, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取配置文件失败: " + actual, e);
        }
        return new EnvConfig(moduleRoot, actual, parseYaml(lines));
    }

    private static Path deriveModuleRoot(Path configFile) {
        Path dir = configFile.getParent();
        if (dir != null && "resources".equals(fileName(dir))) {
            Path main = dir.getParent();
            if (main != null && "main".equals(fileName(main))) {
                Path src = main.getParent();
                if (src != null && "src".equals(fileName(src)) && src.getParent() != null) {
                    return src.getParent();
                }
            }
        }
        return dir != null ? dir : configFile;
    }

    private static String fileName(Path p) {
        Path n = p.getFileName();
        return n == null ? "" : n.toString();
    }

    public static Path findModuleRoot() {
        String override = System.getProperty("env.moduleRoot");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path p = cwd;
        for (int i = 0; i < 8 && p != null; i++) {
            if (Files.isRegularFile(p.resolve(CONFIG_RELATIVE))) {
                return p;
            }
            p = p.getParent();
        }
        return cwd;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseYaml(List<String> lines) {
        Map<String, Object> root = new LinkedHashMap<>();
        Deque<Object[]> stack = new ArrayDeque<>();
        stack.push(new Object[]{-1, root});
        for (String raw : lines) {
            String line = raw.stripTrailing();
            String stripped = line.strip();
            if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("- ") || !stripped.contains(":")) {
                continue;
            }
            int indent = line.length() - line.stripLeading().length();
            int colon = stripped.indexOf(':');
            String key = stripped.substring(0, colon).strip();
            String value = stripped.substring(colon + 1).strip();
            while (stack.size() > 1 && indent <= (int) stack.peek()[0]) {
                stack.pop();
            }
            Map<String, Object> parent = (Map<String, Object>) stack.peek()[1];
            if (value.isEmpty()) {
                Map<String, Object> child = new LinkedHashMap<>();
                parent.put(key, child);
                stack.push(new Object[]{indent, child});
            } else {
                parent.put(key, stripQuotes(value));
            }
        }
        return root;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if (first == last && (first == '"' || first == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> node(Map<String, Object> parent, String key) {
        Object v = parent == null ? null : parent.get(key);
        return v instanceof Map ? (Map<String, Object>) v : new LinkedHashMap<>();
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static String str(Map<String, Object> m, String key, String def) {
        String v = str(m, key);
        return (v == null || v.isEmpty()) ? def : v;
    }

    private static boolean hasText(Map<String, Object> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof String && !((String) v).isBlank();
    }

    public Path moduleRoot() {
        return moduleRoot;
    }

    public Path configFile() {
        return configFile;
    }

    public Path logDir() {
        return moduleRoot.resolve("logs");
    }

    public Path resolvePath(String pathStr) {
        Path p = Path.of(pathStr);
        return p.isAbsolute() ? p.normalize() : moduleRoot.resolve(pathStr).normalize();
    }

    public String toFileUri(String pathStr) {
        String trimmed = pathStr == null ? "" : pathStr.trim();
        if (trimmed.toLowerCase().startsWith("file:")) {
            return trimmed;
        }
        // 使用 Path.toUri() 生成符合 RFC 3986 的 file URI：会把空格等特殊字符编码为 %20，
        // 避免含空格路径（如 "D:\01 test\..."）被 Ontop 解析为非法 URI 而启动失败。
        return Path.of(trimmed).toAbsolutePath().normalize().toUri().toString();
    }

    /**
     * 解析软件安装位置，优先级：应用 application.yaml（合并后 env）→ 环境变量 → 自动探测；均未取到返回 null。
     * 使各软件 home「按实际安装环境」确定，不在 env.yaml 中写死机器绝对路径。
     */
    private String home(String key, String envVar, java.util.function.Supplier<String> probe) {
        String configured = str(env, key);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String fromEnv = System.getenv(envVar);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return probe == null ? null : probe.get();
    }

    public String javaHome() {
        return home("java-home", "JAVA_HOME", EnvConfig::probeJavaHome);
    }

    public String ontopHome() {
        return home("ontop-home", "ONTOP_HOME", null);
    }

    public String camundaHome() {
        return home("camunda-home", "CAMUNDA_HOME", null);
    }

    /**
     * Ontop SPARQL endpoint 端口（默认 8080）。多应用并行部署时按应用分别配置以避免冲突；
     * 该值既用于启动 Ontop 时注入 {@code --port}，也会回写应用 .properties 的 port/sparql.endpoint 保持一致。
     */
    public String ontopPort() {
        return str(env, "ontop-port", "8080");
    }

    /** Camunda（c8run）主 REST 端口（Operate/Tasklist，默认 9080），以 {@code --port} 传给 c8run。 */
    public String camundaPort() {
        return str(env, "camunda-port", "9080");
    }

    /** Camunda Zeebe 网关 gRPC 端口（默认 26500），以环境变量注入 c8run 启动进程。 */
    public String camundaGrpcPort() {
        return str(env, "camunda-grpc-port", "26500");
    }

    /** 运行时生成配置目录（如 RabbitMQ 监听端口配置 rabbitmq.conf）。 */
    public Path generatedDir() {
        return moduleRoot.resolve(".envprepare");
    }

    public String rabbitmqHome() {
        return home("rabbitmq-home", "RABBITMQ_HOME", null);
    }

    /** Erlang/OTP 安装位置（RabbitMQ 运行依赖 Erlang，启动时注入 ERLANG_HOME）。 */
    public String erlangHome() {
        return home("erlang-home", "ERLANG_HOME", EnvConfig::probeErlangHome);
    }

    public String ontologyCatalog() {
        return str(env, "ontology-catalog");
    }

    public String ddlFile() {
        return str(env, "ddl-file");
    }

    public String dataFile() {
        return str(env, "data-file");
    }

    public String ontopProperties() {
        return str(ontology, "obda-properties-path");
    }

    public String ontopMapping() {
        return str(ontology, "obda-path");
    }

    public boolean obdaPathDefined() {
        return hasText(ontology, "obda-path");
    }

    public String bpmnPath() {
        return str(ontology, "bpmn-path");
    }

    public boolean bpmnPathDefined() {
        return hasText(ontology, "bpmn-path");
    }

    public boolean rabbitmqDefined() {
        return rabbitmqDefined;
    }

    public boolean mysqlDefined() {
        return mysqlDefined;
    }

    /**
     * Ontop 是否具备完整运行配置：需 env-prepare 的 java-home/ontop-home
     * 与 ontology 的 obda-properties-path/obda-path/main-path 全部齐全。
     * 任一缺失即视为未配置 Ontop，启动/停止时跳过。
     */
    public boolean ontopConfigured() {
        return hasText(env, "java-home") && hasText(env, "ontop-home")
                && hasText(ontology, "obda-properties-path")
                && hasText(ontology, "obda-path")
                && hasText(ontology, "main-path");
    }

    public String ontologyFile() {
        return str(ontology, "main-path");
    }

    public String mysqlServiceName() {
        return str(mysql, "service-name");
    }

    /** MySQL 管理方式：service（默认，Windows 服务）或 portable（独立进程 mysqld --console）。 */
    public String mysqlMode() {
        return str(mysql, "mode", "service");
    }

    public boolean mysqlPortable() {
        return "portable".equalsIgnoreCase(mysqlMode());
    }

    /** 便携模式下 MySQL 安装根目录（其下含 bin/mysqld.exe 与 data）。 */
    public String mysqlHome() {
        return str(mysql, "home", "");
    }

    /** 便携模式下 MySQL 端口（用于探测服务是否已就绪）。 */
    public String mysqlPort() {
        return str(mysql, "port", "3306");
    }

    /** 便携模式下 mysqld.exe 路径：优先显式 mysqld-bin，其次 &lt;home&gt;/bin/mysqld.exe。 */
    public String mysqldBin() {
        String explicit = str(mysql, "mysqld-bin", "");
        if (!explicit.isBlank()) {
            return explicit;
        }
        String home = mysqlHome();
        if (!home.isBlank()) {
            return Path.of(home, "bin", "mysqld.exe").toString();
        }
        return "";
    }

    /** 便携模式下数据目录：优先显式 datadir，其次 &lt;home&gt;/data。 */
    public String mysqlDatadir() {
        String explicit = str(mysql, "datadir", "");
        if (!explicit.isBlank()) {
            return explicit;
        }
        String home = mysqlHome();
        if (!home.isBlank()) {
            return Path.of(home, "data").toString();
        }
        return "";
    }

    /** mysql 客户端路径：优先显式 bin，其次 &lt;home&gt;/bin/mysql.exe；再取 MYSQL_HOME 环境变量，最后 PATH 探测。 */
    public String mysqlBin() {
        String explicit = str(mysql, "bin", "");
        if (!explicit.isBlank()) {
            return explicit;
        }
        String home = mysqlHome();
        if (!home.isBlank()) {
            return Path.of(home, "bin", "mysql.exe").toString();
        }
        String envHome = System.getenv("MYSQL_HOME");
        if (envHome != null && !envHome.isBlank()) {
            return Path.of(envHome, "bin", "mysql.exe").toString();
        }
        String probed = probeExeInPath("mysql.exe");
        return probed == null ? "" : probed;
    }

    public String mysqlUser() {
        return str(mysql, "user");
    }

    public String mysqlPassword() {
        return str(mysql, "password", "");
    }

    public String dbName() {
        return str(mysql, "db-name");
    }

    public String rabbitHost() {
        return str(rabbit, "host", "localhost");
    }

    public String rabbitPort() {
        return str(rabbit, "port", "5672");
    }

    public String rabbitManagementPort() {
        return str(rabbit, "management-port", "15672");
    }

    public String rabbitUser() {
        return str(rabbit, "user", "guest");
    }

    public String rabbitPassword() {
        return str(rabbit, "password", "guest");
    }

    public String rabbitVhost() {
        return str(rabbit, "vhost", "/");
    }

    // ==================== 软件位置自动探测（无应用配置 / 环境变量时的兜底） ====================

    /** 自动探测 JDK 根目录：优先当前 JVM 的 java.home（其下含 bin/java.exe），否则回退其父目录。 */
    private static String probeJavaHome() {
        String jh = System.getProperty("java.home");
        if (jh == null || jh.isBlank()) {
            return null;
        }
        Path p = Path.of(jh).toAbsolutePath().normalize();
        if (Files.isRegularFile(p.resolve("bin").resolve("java.exe"))) {
            return p.toString();
        }
        Path parent = p.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("bin").resolve("java.exe"))) {
            return parent.toString();
        }
        return p.toString();
    }

    /** 自动探测 Erlang/OTP 根目录：在 PATH 中查找 bin\erl.exe，返回其父目录（ERLANG_HOME 口径）。 */
    private static String probeErlangHome() {
        String exe = probeExeInPath("erl.exe");
        if (exe == null) {
            return null;
        }
        Path parent = Path.of(exe).getParent();
        return parent == null ? exe : parent.toString();
    }

    /** 在 PATH 各段查找指定可执行文件，返回其规范路径；未找到返回 null。 */
    private static String probeExeInPath(String exeName) {
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return null;
        }
        for (String seg : path.split(File.pathSeparator)) {
            String s = seg.trim();
            if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
                s = s.substring(1, s.length() - 1);
            }
            if (s.isBlank()) {
                continue;
            }
            Path bin;
            try {
                bin = Path.of(s).toAbsolutePath().normalize();
            } catch (RuntimeException ex) {
                continue;
            }
            Path exe = bin.resolve(exeName);
            if (Files.isRegularFile(exe)) {
                return exe.toString();
            }
        }
        return null;
    }

    public String exchange() {
        return str(rabbit, "exchange");
    }

    public String exchangeType() {
        return str(rabbit, "exchange-type", "direct");
    }

    public String queue() {
        return str(rabbit, "queue");
    }

    public String routingKey() {
        return str(rabbit, "routing-key");
    }
}
