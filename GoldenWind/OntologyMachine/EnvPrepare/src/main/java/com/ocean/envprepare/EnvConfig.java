package com.ocean.envprepare;

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
 * 再与 EnvPrepare 内置的通用环境配置（classpath:/env.yaml，含软件安装位置与 MySQL/RabbitMQ 连接默认值）合并，
 * 同名项以应用 application.yaml 为准（应用覆盖通用）。
 * 是否启用 MySQL / RabbitMQ 由应用 application.yaml 是否声明 env-prepare.mysql / env-prepare.rabbitmq 节点决定。
 * 相对路径统一以模块根目录（包含 src/main/resources/application.yaml 的目录）为基准解析。
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
        String normalized = pathStr.replace('\\', '/');
        if (normalized.toLowerCase().startsWith("file:")) {
            return normalized;
        }
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return "file://" + normalized;
    }

    public String javaHome() {
        return str(env, "java-home");
    }

    public String ontopHome() {
        return str(env, "ontop-home");
    }

    public String camundaHome() {
        return str(env, "camunda-home");
    }

    public String rabbitmqHome() {
        return str(env, "rabbitmq-home");
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

    public String mysqlBin() {
        return str(mysql, "bin", "");
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
