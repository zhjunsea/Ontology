package com.ocean.ontopobdahandler;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class OBDAHandler {

    private static final Logger log = LoggerFactory.getLogger(OBDAHandler.class);

    public static void setPropertiesPath(String propertiesPath) {
        PROPERTIES_PATH = propertiesPath;
    }

    // ==================== 外部配置文件路径 ====================
    private static String PROPERTIES_PATH = "D:\\work\\Ontology\\pizza-ontology\\ontology\\database\\myPizza.properties";

    public static void setObdaPath(String obdaPath) {
        OBDA_PATH = obdaPath;
    }

    public static GenericDbWriter getDbWriter() {
        return DB_WRITER;
    }

    public static void setDbWriter(GenericDbWriter dbWriter) {
        DB_WRITER = dbWriter;
    }

    public static String getObdaPath() {
        return OBDA_PATH;
    }

    private static String OBDA_PATH       = "D:\\work\\Ontology\\pizza-ontology\\ontology\\database\\myPizza.obda";

    private static GenericDbWriter DB_WRITER  = null;

    public static Model queryConstruct(String constructSparql) {
        try {
            // queryConstruct 直接返回 Model，内部自动处理 RDF 解析
            return Holder.SPARQL_CONN.queryConstruct(constructSparql);
        } catch (Exception e) {
            log.error("CONSTRUCT 查询失败 | Query: {}", constructSparql, e);
            throw new RuntimeException("VKG CONSTRUCT 查询异常", e);
        }
    }

    /**
     * 安全转义 SPARQL URI 中的特殊字符，防止注入攻击
     */
    public static String escapeSparqlUri(String uri) {
        if (uri == null) return "";
        return uri.replace("\\", "\\\\")
                .replace(">", "\\>")
                .replace("<", "\\<")
                .replace("\"", "\\\"");
    }

    /**
     * 转义 SPARQL 字符串字面量（用于 "..." 包裹的值）
     */
    public static String escapeSparqlLiteral(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    // ==================== Holder 懒加载单例 ====================
    private static final class Holder {
        static final Properties DB_PROPS = loadProperties();
        static final String SPARQL_ENDPOINT = parseSparqlEndpointFromObda(OBDA_PATH);

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
        private static String parseSparqlEndpointFromObda(String OBDA_PATH) {
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

            config.setJdbcUrl(DB_PROPS.getProperty("jdbc.url",
                    DB_PROPS.getProperty("db.url", "jdbc:mysql://localhost:3306/pizza_db")));
            config.setUsername(DB_PROPS.getProperty("jdbc.user",
                    DB_PROPS.getProperty("db.user", "root")));
            config.setPassword(DB_PROPS.getProperty("jdbc.password",
                    DB_PROPS.getProperty("db.password", "")));

            config.setMaximumPoolSize(Integer.parseInt(DB_PROPS.getProperty("pool.maxSize", "10")));
            config.setMinimumIdle(Integer.parseInt(DB_PROPS.getProperty("pool.minIdle", "2")));
            config.setConnectionTimeout(Long.parseLong(DB_PROPS.getProperty("pool.connectionTimeout", "5000")));
            config.setIdleTimeout(Long.parseLong(DB_PROPS.getProperty("pool.idleTimeout", "300000")));
            config.setMaxLifetime(Long.parseLong(DB_PROPS.getProperty("pool.maxLifetime", "600000")));

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            HikariDataSource ds = new HikariDataSource(config);

            // ✅ 用同一个连接池初始化 GenericDbWriter
            DB_WRITER = new GenericDbWriter(ds);

            log.info("✅ HikariCP [{}] 初始化完成 | URL: {}", config.getPoolName(), config.getJdbcUrl());
            return ds;
        }
    }

    public void persistToDatabase(List<Triple> triples) {
        // TODO: 通过JDBC或Ontop UPDATE接口写入MySQL
    }

    // ==================== 读取路径 ====================

    /**
     * 组合查询：TBox缓存 ∪ Ontop ABox
     */
    public List<Map<String, String>>  executeAboxQuery(String sparql) {
        // 简化示例：实际需解析SPARQL判断是否涉及TBox推导
        // 这里演示直接委托Ontop查询ABox数据
        List<Map<String, String>>  qe = executeSelect(sparql);
        return qe;
    }

    private OBDAHandler() {}

    private OBDAHandler(String PROPERTIES_PATH, String OBDA_PATH) {
        this.PROPERTIES_PATH = PROPERTIES_PATH;
        this.OBDA_PATH = OBDA_PATH;
    }

    public static OBDAHandler getInstance() {
        return Singleton.INSTANCE;
    }
    public static OBDAHandler getInstance(String PROPERTIES_PATH, String OBDA_PATH) {
        return Singleton.INSTANCE;
    }

    private static final class Singleton {
        private static final OBDAHandler INSTANCE = new OBDAHandler();
    }

    // ==================== 查（Read via SPARQL）====================

    public List<Map<String, String>> getInstanceProperties(String prefix, String instanceUri) {
        String sparql = String.format(
                "PREFIX : <%s>%nSELECT ?property ?value WHERE { <%s> ?property ?value } ORDER BY ?property",
                prefix,
                instanceUri
        );
        return executeSelect(sparql);
    }

    public List<Map<String, String>> queryWithInference(String prefix, String className, int limit) {
        String sparql = String.format("""
            PREFIX : <%s>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT DISTINCT ?individual ?type WHERE {
                ?individual a <%s> ;
                           rdf:type ?type .
            } LIMIT %d
            """, prefix, className, limit);
        return executeSelect(sparql);
    }

    /**
     * 通用 SPARQL 聚合查询
     * @param prefix      命名空间前缀 IRI
     * @param className   本体类本地名（如 PizzaComponent）
     * @param groupByProp 分组属性本地名（如 supplier）
     * @param aggProp     被聚合的属性本地名（如 price）
     * @param limit       返回最大行数，<=0 表示不限制
     */
    public List<Map<String, Object>> queryAggregation(
            String prefix,
            String className,
            String groupByProp,
            String aggProp,
            int limit) {

        // 1. 参数校验（防注入 + 防空值）
        if (!className.matches("^[A-Za-z_][A-Za-z0-9_]*$") ||
                !groupByProp.matches("^[A-Za-z_][A-Za-z0-9_]*$") ||
                !aggProp.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException("类名/属性名仅允许字母、数字和下划线");
        }

        // 2. 动态构建 SPARQL
        String limitClause = limit > 0 ? "LIMIT " + limit : "";
        String sparql = String.format("""
            PREFIX : <%s>
            SELECT ?%s (COUNT(?item) AS ?count) (AVG(?%s) AS ?avgValue) WHERE {
                ?item a :%s ;
                      :%s ?%s ;
                      :%s ?%s .
            } GROUP BY ?%s ORDER BY DESC(?count) %s
            """,
                prefix,
                groupByProp, aggProp,          // SELECT 子句
                className,                      // rdf:type 过滤
                groupByProp, groupByProp,       // 分组属性绑定
                aggProp, aggProp,               // 聚合属性绑定
                groupByProp,                    // GROUP BY
                limitClause                     // LIMIT
        );

        // 3. 执行并映射结果（字段名跟随输入参数动态变化）
        List<Map<String, Object>> results = new ArrayList<>();
        Holder.SPARQL_CONN.querySelect(sparql, qs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(groupByProp, qs.getLiteral(groupByProp).getString());
            row.put("count", qs.getLiteral("count").getInt());
            row.put("avg_" + aggProp, qs.getLiteral("avgValue").getDouble());
            results.add(row);
        });
        return results;
    }

    // ==================== 增删改（Write via JDBC + HikariCP）====================

    /**
     * 通用 INSERT
     * @param table   目标表名
     * @param columns 要插入的列名列表（有序）
     * @param values  对应的值列表（与 columns 一一对应）
     */
    public int addComponent(String table, List<String> columns, List<Object> values) {
        if (columns.size() != values.size() || columns.isEmpty()) {
            throw new IllegalArgumentException("columns 与 values 数量必须一致且非空");
        }
        String cols = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", sanitize(table), cols, placeholders);
        return executeUpdate(sql, values.toArray());
    }

    /**
     * 通用 UPDATE（单条件等值更新）
     * @param table      目标表名
     * @param setColumns 要更新的列名列表
     * @param setValues  对应的新值列表
     * @param whereCol   WHERE 条件列名
     * @param whereVal   WHERE 条件值
     */
    public int updateComponent(String table, List<String> setColumns, List<Object> setValues,
                      String whereCol, Object whereVal) {
        if (setColumns.size() != setValues.size() || setColumns.isEmpty()) {
            throw new IllegalArgumentException("setColumns 与 setValues 数量必须一致且非空");
        }
        String setClause = IntStream.range(0, setColumns.size())
                .mapToObj(i -> setColumns.get(i) + " = ?")
                .collect(Collectors.joining(", "));
        String sql = String.format("UPDATE %s SET %s WHERE %s = ?",
                sanitize(table), setClause, sanitize(whereCol));
        // SET 的值在前，WHERE 的值在后
        List<Object> params = new ArrayList<>(setValues);
        params.add(whereVal);
        return executeUpdate(sql, params.toArray());
    }

    /**
     * 按唯一键删除指定表的一行记录。
     * 与 updateComponent 对称设计：参数校验 → sanitize 防注入 → executeUpdate 委托执行。
     *
     * @param table    目标表名
     * @param whereCol WHERE 条件列名
     * @param whereVal WHERE 条件值
     * @return 受影响的行数（0 表示未匹配到任何行，不抛异常，保证幂等性）
     */
    public int deleteComponent(String table, String whereCol, Object whereVal) {
        if (whereCol == null || whereCol.isBlank()) {
            throw new IllegalArgumentException("whereCol 不能为空");
        }
        if (whereVal == null) {
            throw new IllegalArgumentException("whereVal 不能为 null");
        }

        String sql = String.format("DELETE FROM %s WHERE %s = ?",
                sanitize(table), sanitize(whereCol));

        return executeUpdate(sql, whereVal);
    }

    /**
     * 标识符白名单校验：仅允许字母、数字、下划线，防止 SQL 注入
     * 注意：此方法仅用于表名/列名，不用于值（值通过 PreparedStatement 参数绑定）
     */
    private String sanitize(String identifier) {
        if (identifier == null || !identifier.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException("非法标识符: " + identifier);
        }
        return identifier;
    }

    // ==================== 生命周期管理 ====================

    public static void shutdown() {
        log.info("🛑 正在关闭 VKG 单例资源...");
        try { Holder.SPARQL_CONN.close(); } catch (Exception e) { log.warn("SPARQL 连接关闭异常", e); }
        try { Holder.DATA_SOURCE.close(); } catch (Exception e) { log.warn("HikariCP 关闭异常", e); }
        log.info("✅ VKG 资源已全部释放");
    }

    // ==================== 内部工具方法 ====================

    public List<Map<String, String>> executeSelect(String sparql) {
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
    /* 简化版
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
    }*/

    /**
     * 接口签名不变，内部解析 SQL 后委托 GenericDbWriter 执行
     * ✅ 已适配 WriteResult：业务拒绝转为 IllegalArgumentException，系统异常保持 RuntimeException
     */
    private int executeUpdate(String sql, Object... params) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 语句不能为空");
        }
        // ✅ 强制触发 Holder 懒加载，确保 DB_WRITER 已初始化
        // 这行代码本身无实际业务作用，仅用于触发 JVM 类加载
        @SuppressWarnings("unused")
        var ensureInit = Holder.DATA_SOURCE;

        String trimmedSql = sql.trim();
        String operation = trimmedSql.split("\\s+")[0].toUpperCase();

        try {
            switch (operation) {
                case "INSERT": {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("(?i)INSERT\\s+INTO\\s+(`?\\w+(?:\\.\\w+)?`?)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)")
                            .matcher(trimmedSql);
                    if (!m.find()) {
                        throw new IllegalArgumentException("无法解析 INSERT SQL: " + trimmedSql);
                    }
                    String tableName = m.group(1).replace("`", "");
                    String[] columns = m.group(2).split("\\s*,\\s*");
                    String primaryKey = columns[0].trim().replace("`", "");

                    if (params.length != columns.length) {
                        throw new IllegalArgumentException(
                                String.format("INSERT 参数个数(%d)与列数(%d)不匹配", params.length, columns.length));
                    }

                    Map<String, Object> data = new java.util.LinkedHashMap<>();
                    for (int i = 0; i < columns.length; i++) {
                        data.put(columns[i].trim().replace("`", ""), params[i]);
                    }

                    // ✅ 适配 WriteResult：业务拒绝 → IllegalArgumentException
                    WriteResult result = DB_WRITER.insert(tableName, primaryKey, data);
                    // ✅ 新增诊断日志
                    log.info("DB_WRITER.insert 返回: accepted={}, message={}",
                            result.isAccepted(), result.getMessage());
                    if (!result.isAccepted()) {
                        throw new IllegalArgumentException(result.getMessage());
                    }
                    return 1;
                }

                case "UPDATE": {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("(?i)UPDATE\\s+(`?\\w+(?:\\.\\w+)?`?)\\s+SET\\s+(.+?)\\s+WHERE\\s+(`?\\w+`?)\\s*=\\s*\\?")
                            .matcher(trimmedSql);
                    if (!m.find()) {
                        throw new IllegalArgumentException("无法解析 UPDATE SQL: " + trimmedSql);
                    }
                    String tableName = m.group(1).replace("`", "");
                    String setClause = m.group(2);
                    String primaryKey = m.group(3).trim().replace("`", "");

                    java.util.regex.Matcher colMatcher = java.util.regex.Pattern
                            .compile("(?i)(`?\\w+`?)\\s*=\\s*\\?")
                            .matcher(setClause);
                    Map<String, Object> data = new java.util.LinkedHashMap<>();
                    int paramIdx = 0;
                    while (colMatcher.find()) {
                        if (paramIdx >= params.length - 1) {
                            throw new IllegalArgumentException("UPDATE SET 参数不足");
                        }
                        String col = colMatcher.group(1).replace("`", "");
                        data.put(col, params[paramIdx++]);
                    }
                    if (data.isEmpty()) {
                        throw new IllegalArgumentException("UPDATE SET 子句未找到有效赋值");
                    }
                    Object whereValue = params[params.length - 1];

                    // ✅ 适配 WriteResult
                    WriteResult result = DB_WRITER.update(tableName, primaryKey, whereValue, data);
                    if (!result.isAccepted()) {
                        throw new IllegalArgumentException(result.getMessage());
                    }
                    return 1;
                }

                case "DELETE": {
                    java.util.regex.Matcher m = java.util.regex.Pattern
                            .compile("(?i)DELETE\\s+FROM\\s+(`?\\w+(?:\\.\\w+)?`?)\\s+WHERE\\s+(`?\\w+`?)\\s*=\\s*\\?")
                            .matcher(trimmedSql);
                    if (!m.find()) {
                        throw new IllegalArgumentException("无法解析 DELETE SQL: " + trimmedSql);
                    }
                    String tableName = m.group(1).replace("`", "");
                    String primaryKey = m.group(2).trim().replace("`", "");

                    if (params.length != 1) {
                        throw new IllegalArgumentException(
                                String.format("DELETE 应恰好1个参数，实际传入%d个", params.length));
                    }

                    // ✅ 适配 WriteResult
                    WriteResult result = DB_WRITER.delete(tableName, primaryKey, params[0]);
                    if (!result.isAccepted()) {
                        throw new IllegalArgumentException(result.getMessage());
                    }
                    return 1;
                }

                default:
                    throw new IllegalArgumentException("不支持的 DML 操作类型: " + operation);
            }
        } catch (IllegalArgumentException e) {
            // 业务校验失败（含 WriteResult.rejected 转换而来）直接透传
            throw e;
        } catch (java.sql.SQLException e) {
            // ✅ 系统级数据库异常单独捕获，保留原始 SQLException 链
            log.error("JDBC 执行失败 | OP: {} | SQL: {} | Error: {}", operation, trimmedSql, e.getMessage(), e);
            throw new RuntimeException("数据库写入异常", e);
        } catch (Exception e) {
            // 其他未预期异常（如正则解析NPE等）
            log.error("SQL 解析或执行异常 | OP: {} | SQL: {} | Error: {}", operation, trimmedSql, e.getMessage(), e);
            throw new RuntimeException("数据库写入异常", e);
        }
    }

    /* ABox 查询执行：执行 SPARQL 并将结果映射为指定类型的列表
     * @param sparql 完整的 SPARQL SELECT 查询字符串
     * @param mapper 将单行查询结果 Map 转换为目标对象的函数
     * @return 映射后的对象列表
     */
    public <T> List<T> executeAndMap(String sparql, Function<Map<String, String>, T> mapper) {
        try {
            List<Map<String, String>> rawResults = executeAboxQuery(sparql);
            return rawResults.stream()
                    .map(mapper)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("ABox query execution failed: " + e.getMessage(), e);
        }
    }


    /**
     * 从 Ontop 虚拟端点按需拉取 ABox 子图
     * @param constructSparql CONSTRUCT 查询字符串
     * @return 包含实例数据的 Jena Model
     */
    public static Model fetchAboxSubgraph(String constructSparql) {
        return queryConstruct(constructSparql);
    }

    private static OWLOntology loadTbox(OWLOntologyManager m, String path) throws OWLOntologyCreationException {
        return m.loadOntologyFromOntologyDocument(new java.io.File(path));
    }

    private static OWLOntology loadTbox(String path) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        return manager.loadOntologyFromOntologyDocument(
                new java.io.File(path));
    }

    /**
     * 通用 Ontop ABox 拉取并转换为 OWLOntology
     * @param constructSparql CONSTRUCT 查询语句（必须包含 PREFIX 和 LIMIT）
     * 所有字面量都缺少显式数据类型标签。这就是 OWL API 将其降级为 AnnotationAssertion 的直接原因。
     * 即使共享了 TBox Manager，OWL API 的 Turtle 解析器在处理无类型字面量（Plain Literal） 时，
     * 如果当前文档片段内没有该属性的局部声明，仍然会保守地将其解析为 Annotation。
     */
    public static OWLOntology loadAboxFromOntop(String constructSparql,
                                                OWLOntology tboxOntology) throws OWLOntologyCreationException {
        // Step 1: 从 Ontop 拉取纯 ABox 数据
        Model aboxData = queryConstruct(constructSparql);
        if (aboxData.isEmpty()) {
            throw new IllegalStateException("CONSTRUCT 查询返回空结果，请检查 SPARQL 或数据源");
        }

        // Step 2: 转换为 TTL 字节流（强制保留所有 XSD 类型标签）
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        aboxData.write(baos, "N-TRIPLES"); // ⚠️ 强烈建议用 N-TRIPLES，避免 Turtle 缩写导致正则解析失败
        byte[] turtleBytes = baos.toByteArray();
        String rawTurtle = new String(turtleBytes, StandardCharsets.UTF_8);

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology aboxOntology = manager.loadOntologyFromOntologyDocument(
                new ByteArrayInputStream(turtleBytes)
        );
        // ✅ 新增：一行调用完成类型修复
        ABoxTypeFixer.fixDataPropertyTypes(tboxOntology,aboxOntology, rawTurtle);

        //测试
        // ⭐ 诊断：打印 Ontop 生成的原始 Turtle
        rawTurtle = baos.toString(StandardCharsets.UTF_8);
        log.debug("========== Ontop CONSTRUCT 原始输出 ==========");
        log.debug(rawTurtle);
        log.debug("===============================================");

        // ⭐ Step 3: 将 ABox 公理合并到 TBox 本体中（或保持独立但共享 Manager）
        // 如果后续推理需要统一本体，执行合并：
        manager.addAxioms(tboxOntology, aboxOntology.getAxioms());
        manager.removeOntology(aboxOntology); // 清理临时本体，释放内存

        return tboxOntology; // 返回包含完整 TBox + ABox 的本体
    }

    // 在 OBDAHandler.java 中添加

}