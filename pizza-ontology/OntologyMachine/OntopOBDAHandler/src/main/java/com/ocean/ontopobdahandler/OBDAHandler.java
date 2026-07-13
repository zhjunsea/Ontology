package com.ocean.ontopobdahandler;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OBDAHandler {

    private static final Logger log = LoggerFactory.getLogger(OBDAHandler.class);

    // ==================== 外部配置文件路径 ====================
    private static final String PROPERTIES_PATH = "D:\\work\\Ontology\\pizza-ontology\\ontology\\database\\myPizza.properties";
    private static final String OBDA_PATH       = "D:\\work\\Ontology\\pizza-ontology\\ontology\\database\\myPizza.obda";

    // ==================== Holder 懒加载单例 ====================
    private static final class Holder {
        static final Properties DB_PROPS = loadProperties();
        static final String SPARQL_ENDPOINT = parseSparqlEndpointFromObda();

        static final RDFConnection SPARQL_CONN = RDFConnectionRemote.create()
                .destination(SPARQL_ENDPOINT)
                .build();

        static final HikariDataSource DATA_SOURCE = initDataSource();

        private static Properties loadProperties() {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(PROPERTIES_PATH)) {
                props.load(fis);
                log.info("✅ 已加载数据库配置: {}", PROPERTIES_PATH);
            } catch (IOException e) {
                throw new RuntimeException("无法读取配置文件: " + PROPERTIES_PATH, e);
            }
            return props;
        }

        /**
         * 从 .obda 文件中提取 SPARQL endpoint
         * Ontop .obda 文件通常包含 [PrefixDeclaration] 或 [Mapping] 段，
         * 但 SPARQL endpoint 一般在对应的 .properties 或启动参数中。
         * 若你的 .obda 文件中确实定义了 endpoint，请根据实际格式调整正则。
         * 这里提供一个通用解析 + 回退机制。
         */
        private static String parseSparqlEndpointFromObda() {
            // 优先尝试从 obda 文件解析
            try {
                String content = Files.readString(Path.of(OBDA_PATH));
                // 匹配常见模式: sparql.endpoint=http://... 或 endpoint = http://...
                Pattern pattern = Pattern.compile("(?:sparql\\.)?endpoint\\s*=\\s*(.+)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    String endpoint = matcher.group(1).trim();
                    log.info("✅ 从 .obda 文件解析到 SPARQL Endpoint: {}", endpoint);
                    return endpoint;
                }
            } catch (IOException e) {
                log.warn("⚠️ 读取 .obda 文件失败: {}, 将尝试从 .properties 回退", e.getMessage());
            }

            // 回退：从 myPizza.properties 中读取
            String fallback = DB_PROPS.getProperty("sparql.endpoint",
                    DB_PROPS.getProperty("ontop.sparql.endpoint", "http://localhost:8080/sparql"));
            log.info("ℹ️ 使用 SPARQL Endpoint (from properties/default): {}", fallback);
            return fallback;
        }

        private static HikariDataSource initDataSource() {
            HikariConfig config = new HikariConfig();
            config.setPoolName("vkg-mysql-pool");

            // 从 myPizza.properties 读取数据库连接信息
            config.setJdbcUrl(DB_PROPS.getProperty("jdbc.url",
                    DB_PROPS.getProperty("db.url", "jdbc:mysql://localhost:3306/pizza_db")));
            config.setUsername(DB_PROPS.getProperty("jdbc.user",
                    DB_PROPS.getProperty("db.user", "root")));
            config.setPassword(DB_PROPS.getProperty("jdbc.password",
                    DB_PROPS.getProperty("db.password", "")));

            // 连接池参数（也支持从配置文件覆盖）
            config.setMaximumPoolSize(Integer.parseInt(DB_PROPS.getProperty("pool.maxSize", "10")));
            config.setMinimumIdle(Integer.parseInt(DB_PROPS.getProperty("pool.minIdle", "2")));
            config.setConnectionTimeout(Long.parseLong(DB_PROPS.getProperty("pool.connectionTimeout", "5000")));
            config.setIdleTimeout(Long.parseLong(DB_PROPS.getProperty("pool.idleTimeout", "300000")));
            config.setMaxLifetime(Long.parseLong(DB_PROPS.getProperty("pool.maxLifetime", "600000")));

            // MySQL 性能优化
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            log.info("✅ HikariCP [{}] 初始化完成 | URL: {}", config.getPoolName(), config.getJdbcUrl());
            return new HikariDataSource(config);
        }
    }

    private OBDAHandler() {}

    public static OBDAHandler getInstance() {
        return Singleton.INSTANCE;
    }

    private static final class Singleton {
        private static final OBDAHandler INSTANCE = new OBDAHandler();
    }

    // ==================== 查（Read via SPARQL）====================

    public List<Map<String, String>> getInstanceProperties(String instanceUri) {
        String sparql = String.format("""
                PREFIX : <http://example.org/pizza/>
                SELECT ?property ?value WHERE { <%s> ?property ?value } ORDER BY ?property
                """, instanceUri);
        return executeSelect(sparql);
    }

    public List<Map<String, String>> queryWithInference(String className, int limit) {
        String sparql = String.format("""
                PREFIX : <http://example.org/pizza/>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                SELECT ?individual ?type WHERE {
                    ?individual a %s ; rdf:type ?type .
                } LIMIT %d
                """, className, limit);
        return executeSelect(sparql);
    }

    public List<Map<String, Object>> queryAggregation() {
        String sparql = """
                PREFIX : <http://example.org/pizza/>
                SELECT ?supplier (COUNT(?item) AS ?count) (AVG(?price) AS ?avgPrice) WHERE {
                    ?item a :PizzaComponent ; :supplier ?supplier ; :price ?price .
                } GROUP BY ?supplier ORDER BY DESC(?count)
                """;
        List<Map<String, Object>> results = new ArrayList<>();
        Holder.SPARQL_CONN.querySelect(sparql, qs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("supplier", qs.getLiteral("supplier").getString());
            row.put("count", qs.getLiteral("count").getInt());
            row.put("avgPrice", qs.getLiteral("avgPrice").getDouble());
            results.add(row);
        });
        return results;
    }

    // ==================== 增删改（Write via JDBC + HikariCP）====================

    public int addComponent(String name, String supplier, double price, String type) {
        String sql = "INSERT INTO pizza_components (name, supplier, price, type) VALUES (?, ?, ?, ?)";
        return executeUpdate(sql, name, supplier, price, type);
    }

    public int updatePrice(String componentName, double newPrice) {
        String sql = "UPDATE pizza_components SET price = ? WHERE name = ?";
        return executeUpdate(sql, newPrice, componentName);
    }

    public int deleteComponent(String componentName) {
        String sql = "DELETE FROM pizza_components WHERE name = ?";
        return executeUpdate(sql, componentName);
    }

    // ==================== 生命周期管理 ====================

    public static void shutdown() {
        log.info("🛑 正在关闭 VKG 单例资源...");
        try { Holder.SPARQL_CONN.close(); } catch (Exception e) { log.warn("SPARQL 连接关闭异常", e); }
        try { Holder.DATA_SOURCE.close(); } catch (Exception e) { log.warn("HikariCP 关闭异常", e); }
        log.info("✅ VKG 资源已全部释放");
    }

    // ==================== 内部工具方法 ====================

    private List<Map<String, String>> executeSelect(String sparql) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            Holder.SPARQL_CONN.querySelect(sparql, qs -> {
                Map<String, String> row = new LinkedHashMap<>();
                qs.varNames().forEachRemaining(var -> {
                    var node = qs.get(var);
                    String val = node.isLiteral()   ? node.asLiteral().getString()
                            : node.isResource()  ? node.asResource().getLocalName()
                              : node.toString();
                    row.put(var, val);
                });
                results.add(row);
            });
        } catch (Exception e) {
            log.error("SPARQL 查询失败 | Query: {}", sparql, e);
            throw new RuntimeException("VKG 查询异常", e);
        }
        return results;
    }

    private int executeUpdate(String sql, Object... params) {
        try (Connection conn = Holder.DATA_SOURCE.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.error("JDBC 执行失败 | SQL: {} | Error: {}", sql, e.getMessage());
            throw new RuntimeException("数据库写入异常", e);
        }
    }
}