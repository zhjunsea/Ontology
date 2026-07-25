package com.ocean.ontopobdahandler;

import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class OBDAHandler {
    private static final Logger log = LoggerFactory.getLogger(OBDAHandler.class);

    // ==================== 配置路径 ====================
    private static String PROPERTIES_PATH = "D:\\work\\Ontology\\pizza-ontology\\ontology\\database\\myPizza.properties";
    private static String OBDA_PATH = "D:\\work\\Ontology\\pizza-ontology\\ontology\\database\\myPizza.obda";
    private static GenericDbWriter DB_WRITER = null;

    public static void setPropertiesPath(String p) { PROPERTIES_PATH = p; }
    public static void setObdaPath(String p) { OBDA_PATH = p; }
    public static GenericDbWriter getDbWriter() { return DB_WRITER; }
    public static void setDbWriter(GenericDbWriter w) { DB_WRITER = w; }
    public static String getObdaPath() { return OBDA_PATH; }
    private static final Map<String, Set<String>> tableColumnsCache = new ConcurrentHashMap<>();


    // ==================== SPARQL 查询 ====================
    public static Model queryConstruct(String constructSparql) {
        try {
            return Holder.SPARQL_CONN.queryConstruct(constructSparql);
        } catch (Exception e) {
            log.error("CONSTRUCT 查询失败 | Query: {}", constructSparql, e);
            throw new RuntimeException("VKG CONSTRUCT 查询异常", e);
        }
    }

    public static String escapeSparqlUri(String uri) {
        if (uri == null) return "";
        return uri.replace("\\", "\\\\").replace(">", "\\>").replace("<", "\\<").replace("\"", "\\\"");
    }

    public static String escapeSparqlLiteral(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    // ==================== Holder 懒加载单例 ====================
    public static final class Holder {
        static final Properties DB_PROPS = loadProperties();
        static final String SPARQL_ENDPOINT = parseSparqlEndpointFromObda(OBDA_PATH);


        // ✅ 替换：使用 OntopMappingResolver 预加载映射（纯文本解析，无DB依赖）
        public static final Map<String, OntopMappingResolver.ColumnMapping> MAPPING_CACHE;

        /** 预计算的 JOIN 键列表（基于 OBDA Subject Variable） */
        public static final List<OntopMappingResolver.JoinKeyInfo> JOIN_KEYS;
        static {
            try {
                MAPPING_CACHE = OntopMappingResolver.resolvePropertyToColumnMappings(OBDA_PATH,DB_PROPS);
                JOIN_KEYS = OntopMappingResolver.resolveJoinKeys(OBDA_PATH, DB_PROPS);
                log.info("✅ OntopMappingResolver 已随 Holder 自动加载 | path={} | 属性数={}",
                        OBDA_PATH, MAPPING_CACHE.size());
            } catch (Exception e) {
                log.error("❌ OntopMappingResolver 加载失败 | path={}", OBDA_PATH, e);
                throw new RuntimeException("OBDA 映射文件解析失败: " + OBDA_PATH, e);
            }
        }

        static final RDFConnection SPARQL_CONN = RDFConnectionRemote.create()
                .destination(SPARQL_ENDPOINT).build();

        static final ConnectionPoolManager POOL_MANAGER = initPoolManager();

        static {
            DB_WRITER = new GenericDbWriter(POOL_MANAGER.getDataSource());
            log.info("✅ GenericDbWriter 已通过 ConnectionPoolManager 初始化");
        }

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

        private static String parseSparqlEndpointFromObda(String obdaPath) {
            try {
                String content = Files.readString(Path.of(obdaPath));
                Matcher matcher = Pattern.compile("(?:sparql\\.)?endpoint\\s*=\\s*(.+)", Pattern.CASE_INSENSITIVE)
                        .matcher(content);
                if (matcher.find()) {
                    String endpoint = matcher.group(1).trim();
                    log.info("✅ 从 .obda 文件解析到 SPARQL Endpoint: {}", endpoint);
                    return endpoint;
                }
            } catch (IOException e) {
                log.warn("⚠️ 读取 .obda 文件失败: {}, 将尝试从 .properties 回退", e.getMessage());
            }
            String fallback = DB_PROPS.getProperty("sparql.endpoint",
                    DB_PROPS.getProperty("ontop.sparql.endpoint", "http://localhost:8080/sparql"));
            log.info("ℹ️ 使用 SPARQL Endpoint (from properties/default): {}", fallback);
            return fallback;
        }

        private static ConnectionPoolManager initPoolManager() {
            String url = DB_PROPS.getProperty("jdbc.url",
                    DB_PROPS.getProperty("db.url", "jdbc:mysql://localhost:3306/pizza_db"));
            String user = DB_PROPS.getProperty("jdbc.user",
                    DB_PROPS.getProperty("db.user", "root"));
            String pass = DB_PROPS.getProperty("jdbc.password",
                    DB_PROPS.getProperty("db.password", ""));
            int maxPool = Integer.parseInt(DB_PROPS.getProperty("pool.maxSize", "10"));
            int minIdle = Integer.parseInt(DB_PROPS.getProperty("pool.minIdle", "2"));
            return new ConnectionPoolManager(url, user, pass, maxPool, minIdle);
        }
    }

    public void persistToDatabase(List<Triple> triples) { /* TODO */ }

    // ==================== 读取路径 ====================
    public List<Map<String, String>> executeAboxQuery(String sparql) {
        return executeSelect(sparql);
    }

    private OBDAHandler() {}
    private OBDAHandler(String propsPath, String obdaPath) {
        PROPERTIES_PATH = propsPath;
        OBDA_PATH = obdaPath;
    }
    public static OBDAHandler getInstance() { return Singleton.INSTANCE; }
    public static OBDAHandler getInstance(String p, String o) { return Singleton.INSTANCE; }
    private static final class Singleton { private static final OBDAHandler INSTANCE = new OBDAHandler(); }

    public List<Map<String, String>> getInstanceProperties(String prefix, String instanceUri) {
        String sparql = String.format(
                "PREFIX : <%s>%nSELECT ?property ?value WHERE { <%s> ?property ?value } ORDER BY ?property",
                prefix, instanceUri);
        return executeSelect(sparql);
    }

    public List<Map<String, String>> queryWithInference(String prefix, String className, int limit) {
        String sparql = String.format("""
            PREFIX : <%s>
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT DISTINCT ?individual ?type WHERE {
                ?individual a <%s> ; rdf:type ?type .
            } LIMIT %d
            """, prefix, className, limit);
        return executeSelect(sparql);
    }

    public List<Map<String, Object>> queryAggregation(String prefix, String className,
                                                      String groupByProp, String aggProp, int limit) {
        if (!className.matches("^[A-Za-z_][A-Za-z0-9_]*$") ||
                !groupByProp.matches("^[A-Za-z_][A-Za-z0-9_]*$") ||
                !aggProp.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException("类名/属性名仅允许字母、数字和下划线");
        }
        String limitClause = limit > 0 ? "LIMIT " + limit : "";
        String sparql = String.format("""
            PREFIX : <%s>
            SELECT ?%s (COUNT(?item) AS ?count) (AVG(?%s) AS ?avgValue) WHERE {
                ?item a :%s ; :%s ?%s ; :%s ?%s .
            } GROUP BY ?%s ORDER BY DESC(?count) %s
            """, prefix, groupByProp, aggProp, className,
                groupByProp, groupByProp, aggProp, aggProp, groupByProp, limitClause);

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

    // ==================== 增删改（Write via JDBC）====================
    public int addComponent(String table, List<String> columns, List<Object> values) {
        if (columns.size() != values.size() || columns.isEmpty())
            throw new IllegalArgumentException("columns 与 values 数量必须一致且非空");
        String cols = String.join(", ", columns);
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));
        String sql = String.format("INSERT INTO %s (%s) VALUES (%s)", sanitize(table), cols, placeholders);
        return executeUpdate(sql, values.toArray());
    }

    public int updateComponent(String table, List<String> setColumns, List<Object> setValues,
                               String whereCol, Object whereVal) {
        if (setColumns.size() != setValues.size() || setColumns.isEmpty())
            throw new IllegalArgumentException("setColumns 与 setValues 数量必须一致且非空");
        String setClause = IntStream.range(0, setColumns.size())
                .mapToObj(i -> setColumns.get(i) + " = ?").collect(Collectors.joining(", "));
        String sql = String.format("UPDATE %s SET %s WHERE %s = ?", sanitize(table), setClause, sanitize(whereCol));
        List<Object> params = new ArrayList<>(setValues);
        params.add(whereVal);
        return executeUpdate(sql, params.toArray());
    }

    public int deleteComponent(String table, String whereCol, Object whereVal) {
        if (whereCol == null || whereCol.isBlank()) throw new IllegalArgumentException("whereCol 不能为空");
        if (whereVal == null) throw new IllegalArgumentException("whereVal 不能为 null");
        String sql = String.format("DELETE FROM %s WHERE %s = ?", sanitize(table), sanitize(whereCol));
        return executeUpdate(sql, whereVal);
    }

    private String sanitize(String identifier) {
        if (identifier == null || !identifier.matches("^[A-Za-z_][A-Za-z0-9_]*$"))
            throw new IllegalArgumentException("非法标识符: " + identifier);
        return identifier;
    }

    // ==================== 生命周期管理 ====================
    public static void shutdown() {
        log.info("🛑 正在关闭 VKG 单例资源...");
        try { Holder.SPARQL_CONN.close(); } catch (Exception e) { log.warn("SPARQL 连接关闭异常", e); }
        Holder.POOL_MANAGER.shutdown();
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
                    String val = node.isLiteral() ? node.asLiteral().getString()
                            : node.isResource() ? node.asResource().getLocalName() : node.toString();
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
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL 语句不能为空");
        @SuppressWarnings("unused") var ensureInit = Holder.POOL_MANAGER;

        String trimmedSql = sql.trim();
        String operation = trimmedSql.split("\\s+")[0].toUpperCase();
        try {
            switch (operation) {
                case "INSERT": {
                    Matcher m = Pattern.compile("(?i)INSERT\\s+INTO\\s+(`?\\w+(?:\\.\\w+)?`?)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)")
                            .matcher(trimmedSql);
                    if (!m.find()) throw new IllegalArgumentException("无法解析 INSERT SQL: " + trimmedSql);
                    String tableName = m.group(1).replace("`", "");
                    String[] columns = m.group(2).split("\\s*,\\s*");
                    String primaryKey = columns[0].trim().replace("`", "");
                    if (params.length != columns.length)
                        throw new IllegalArgumentException(String.format("INSERT 参数个数(%d)与列数(%d)不匹配", params.length, columns.length));
                    Map<String, Object> data = new LinkedHashMap<>();
                    for (int i = 0; i < columns.length; i++)
                        data.put(columns[i].trim().replace("`", ""), params[i]);
                    WriteResult result = DB_WRITER.insert(tableName, primaryKey, data);
                    if (!result.isAccepted()) throw new IllegalArgumentException(result.getMessage());
                    return 1;
                }
                case "UPDATE": {
                    Matcher m = Pattern.compile("(?i)UPDATE\\s+([\"`\\[]?\\w+(?:\\.\\w+)?[\"`\\]]?)\\s+SET\\s+(.+?)\\s+WHERE\\s+([\"`\\[]?\\w+[\"`\\]]?)\\s*=\\s*\\?")
                            .matcher(trimmedSql);;
                if (!m.find()) throw new IllegalArgumentException("无法解析 UPDATE SQL: " + trimmedSql);
                    String tableName = unquoteIdentifier(m.group(1));
                    String setClause = m.group(2);
                    String primaryKey = unquoteIdentifier(m.group(3).trim());
                    Matcher colMatcher = Pattern.compile("(?i)(`?\\w+`?)\\s*=\\s*\\?").matcher(setClause);
                    Map<String, Object> data = new LinkedHashMap<>();
                    int paramIdx = 0;
                    while (colMatcher.find()) {
                        if (paramIdx >= params.length - 1) throw new IllegalArgumentException("UPDATE SET 参数不足");
                        data.put(unquoteIdentifier(colMatcher.group(1)), params[paramIdx++]);
                    }
                    if (data.isEmpty()) throw new IllegalArgumentException("UPDATE SET 子句未找到有效赋值");
                    WriteResult result = DB_WRITER.update(tableName, primaryKey, params[params.length - 1], data);
                    if (!result.isAccepted()) throw new IllegalArgumentException(result.getMessage());
                    return 1;
                }
                case "DELETE": {
                    Matcher m = Pattern.compile("(?i)DELETE\\s+FROM\\s+(`?\\w+(?:\\.\\w+)?`?)\\s+WHERE\\s+(`?\\w+`?)\\s*=\\s*\\?")
                            .matcher(trimmedSql);
                    if (!m.find()) throw new IllegalArgumentException("无法解析 DELETE SQL: " + trimmedSql);
                    String tableName = m.group(1).replace("`", "");
                    String primaryKey = m.group(2).trim().replace("`", "");
                    if (params.length != 1) throw new IllegalArgumentException(String.format("DELETE 应恰好1个参数，实际传入%d个", params.length));
                    WriteResult result = DB_WRITER.delete(tableName, primaryKey, params[0]);
                    if (!result.isAccepted()) throw new IllegalArgumentException(result.getMessage());
                    return 1;
                }
                default: throw new IllegalArgumentException("不支持的 DML 操作类型: " + operation);
            }
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) {
            log.error("SQL 解析或执行异常 | OP: {} | SQL: {}", operation, trimmedSql, e);
            throw new RuntimeException("数据库写入异常", e);
        }
    }

    public int executeUpdate(Connection conn, String sql, Object... params) {
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL 语句不能为空");
        String trimmedSql = sql.trim();
        String operation = trimmedSql.split("\\s+")[0].toUpperCase();
        try {
            switch (operation) {
                case "INSERT": {
                    Matcher m = Pattern.compile("(?i)INSERT\\s+INTO\\s+(`?\\w+(?:\\.\\w+)?`?)\\s*\\(([^)]+)\\)\\s*VALUES\\s*\\(([^)]+)\\)")
                            .matcher(trimmedSql);
                    if (!m.find()) throw new IllegalArgumentException("无法解析 INSERT SQL: " + trimmedSql);
                    String tableName = m.group(1).replace("`", "");
                    String[] columns = m.group(2).split("\\s*,\\s*");
                    String primaryKey = columns[0].trim().replace("`", "");
                    if (params.length != columns.length)
                        throw new IllegalArgumentException(String.format("INSERT 参数个数(%d)与列数(%d)不匹配", params.length, columns.length));
                    Map<String, Object> data = new LinkedHashMap<>();
                    for (int i = 0; i < columns.length; i++)
                        data.put(columns[i].trim().replace("`", ""), params[i]);
                    WriteResult result = DB_WRITER.insert(conn, tableName, primaryKey, data);
                    log.info("DB_WRITER.insert(tx) 返回: accepted={}, message={}", result.isAccepted(), result.getMessage());
                    if (!result.isAccepted()) throw new IllegalArgumentException(result.getMessage());
                    return 1;
                }
                // ✅ 新增：事务模式下的 UPDATE 支持
                case "UPDATE": {
                    Matcher m = Pattern.compile("(?i)UPDATE\\s+(`?\\w+(?:\\.\\w+)?`?)\\s+SET\\s+(.+?)\\s+WHERE\\s+(`?\\w+`?)\\s*=\\s*\\?")
                            .matcher(trimmedSql);
                    if (!m.find()) throw new IllegalArgumentException("无法解析 UPDATE SQL: " + trimmedSql);
                    String tableName = unquoteIdentifier(m.group(1));
                    String setClause = m.group(2);
                    String primaryKey = unquoteIdentifier(m.group(3).trim());
                    Matcher colMatcher = Pattern.compile("(?i)(`?\\w+`?)\\s*=\\s*\\?").matcher(setClause);
                    Map<String, Object> data = new LinkedHashMap<>();
                    int paramIdx = 0;
                    while (colMatcher.find()) {
                        if (paramIdx >= params.length - 1) throw new IllegalArgumentException("UPDATE SET 参数不足");
                        data.put(unquoteIdentifier(colMatcher.group(1)), params[paramIdx++]);
                    }
                    if (data.isEmpty()) throw new IllegalArgumentException("UPDATE SET 子句未找到有效赋值");
                    // ✅ 关键：使用带 Connection 的 update 重载，确保在同一事务内执行
                    WriteResult result = DB_WRITER.update(conn, tableName, primaryKey, params[params.length - 1], data);
                    log.info("DB_WRITER.update(tx) 返回: accepted={}, message={}", result.isAccepted(), result.getMessage());
                    if (!result.isAccepted()) throw new IllegalArgumentException(result.getMessage());
                    return 1;
                }
                // ✅ 可选：同步放开 DELETE
                case "DELETE": {
                    Matcher m = Pattern.compile("(?i)DELETE\\s+FROM\\s+(`?\\w+(?:\\.\\w+)?`?)\\s+WHERE\\s+(`?\\w+`?)\\s*=\\s*\\?")
                            .matcher(trimmedSql);
                    if (!m.find()) throw new IllegalArgumentException("无法解析 DELETE SQL: " + trimmedSql);
                    String tableName = unquoteIdentifier(m.group(1));
                    String primaryKey = unquoteIdentifier(m.group(3).trim());
                    if (params.length != 1) throw new IllegalArgumentException(String.format("DELETE 应恰好1个参数，实际传入%d个", params.length));
                    WriteResult result = DB_WRITER.delete(conn, tableName, primaryKey, params[0]);
                    log.info("DB_WRITER.delete(tx) 返回: accepted={}, message={}", result.isAccepted(), result.getMessage());
                    if (!result.isAccepted()) throw new IllegalArgumentException(result.getMessage());
                    return 1;
                }
                default:
                    throw new IllegalArgumentException("事务模式下暂不支持的操作类型: " + operation);
            }
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) {
            log.error("事务SQL解析或执行异常 | OP: {} | SQL: {}", operation, trimmedSql, e);
            throw new RuntimeException("数据库写入异常", e);
        }
    }

    /**
     * 将映射中的原始列引用解析为目标表的物理列名。
     * JOIN 场景下，不同表的同名字段视为同一逻辑字段，自动翻译到目标表。
     *
     * @param rawColumnRef 映射缓存中的列标识，如 `pizza_components`.name
     * @param targetTable  目标写入表名，如 crust_component
     * @return 目标表的物理列名；若该列在目标表中不存在则返回 null
     */
    private String resolveTargetColumnName(String rawColumnRef, String targetTable) {
        if (rawColumnRef == null || rawColumnRef.isBlank()) {
            return null;
        }

        // 1. 提取纯列名（剥离所有表前缀和引号）
        String pureColumnName = extractPureColumnName(rawColumnRef);
        if (pureColumnName == null) {
            return null;
        }

        // 2. 【关键】校验该列是否真实存在于目标表中
        //    这既保证了 JOIN 字段的正确翻译，又防止了非法列名注入
        if (!columnExistsInTable(targetTable, pureColumnName)) {
            log.debug("列 {} 不存在于目标表 {} 中，跳过", pureColumnName, targetTable);
            return null;
        }

        return pureColumnName;
    }

    /**
     * 从可能带表前缀和引号的列引用中提取纯列名
     * 支持: `pizza_components`.name / "schema"."table"."col" / name / [dbo].[col]
     */
    private static String extractPureColumnName(String rawColumnRef) {
        String ref = rawColumnRef.trim();

        // 取最后一个点之后的部分作为列名（兼容 schema.table.column 三段式）
        int lastDot = ref.lastIndexOf('.');
        String columnPart = (lastDot >= 0) ? ref.substring(lastDot + 1) : ref;

        return unquoteIdentifier(columnPart.trim());
    }

    // unquoteIdentifier 保持不变（支持 `, ", [] 三种引号）
    private static String unquoteIdentifier(String identifier) {
        if (identifier == null || identifier.length() < 2) return identifier;
        char first = identifier.charAt(0);
        char last = identifier.charAt(identifier.length() - 1);
        if ((first == '`' && last == '`')

                || (first == '"' && last == '"')
                || (first == '[' && last == ']')) {
            return identifier.substring(1, identifier.length() - 1);
        }
        return identifier;
    }

    public void executeInTransaction(Consumer<Connection> action) {
        Connection conn = null;
        boolean originalAutoCommit = true;
        try {
            conn = Holder.POOL_MANAGER.getDataSource().getConnection();
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            action.accept(conn);
            conn.commit();
            log.debug("✅ 事务提交成功");
        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); log.warn("⚠️ 事务已回滚"); }
                catch (SQLException ex) { log.error("❌ 事务回滚失败", ex); }
            }
            throw new RuntimeException("❌ 跨表写入事务失败，已回滚", e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(originalAutoCommit); } catch (SQLException ignored) {}
                try { conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    public <T> List<T> executeAndMap(String sparql, Function<Map<String, String>, T> mapper) {
        try {
            return executeAboxQuery(sparql).stream().map(mapper).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("ABox query execution failed: " + e.getMessage(), e);
        }
    }

    public static Model fetchAboxSubgraph(String constructSparql) { return queryConstruct(constructSparql); }

    private static OWLOntology loadTbox(OWLOntologyManager m, String path) throws OWLOntologyCreationException {
        return m.loadOntologyFromOntologyDocument(new File(path));
    }

    private static OWLOntology loadTbox(String path) throws OWLOntologyCreationException {
        return OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(new File(path));
    }

    public static OWLOntology loadAboxFromOntop(String constructSparql, OWLOntology tboxOntology)
            throws OWLOntologyCreationException {
        Model aboxData = queryConstruct(constructSparql);
        if (aboxData.isEmpty())
            throw new IllegalStateException("CONSTRUCT 查询返回空结果，请检查 SPARQL 或数据源");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        aboxData.write(baos, "N-TRIPLES");
        byte[] turtleBytes = baos.toByteArray();
        String rawTurtle = new String(turtleBytes, StandardCharsets.UTF_8);

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology aboxOntology = manager.loadOntologyFromOntologyDocument(new ByteArrayInputStream(turtleBytes));
        ABoxTypeFixer.fixDataPropertyTypes(tboxOntology, aboxOntology, rawTurtle);

        rawTurtle = baos.toString(StandardCharsets.UTF_8);
        log.debug("========== Ontop CONSTRUCT 原始输出 ==========");
        log.debug(rawTurtle);
        log.debug("===============================================");

        manager.addAxioms(tboxOntology, aboxOntology.getAxioms());
        manager.removeOntology(aboxOntology);
        return tboxOntology;
    }

    /**
     * 校验指定列是否存在于目标表中（带本地缓存）。
     * 用于 JOIN 场景下的列名翻译安全校验。
     */
    private boolean columnExistsInTable(String tableName, String columnName) {
        String normalizedTable = unquoteIdentifier(tableName.trim()).toLowerCase();
        String normalizedColumn = columnName.toLowerCase();

        Set<String> columns = tableColumnsCache.computeIfAbsent(normalizedTable, t -> {
            try (Connection conn = Holder.POOL_MANAGER.getDataSource().getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                ResultSet rs = meta.getColumns(null, null, t, "%");
                Set<String> cols = new HashSet<>();
                while (rs.next()) {
                    cols.add(rs.getString("COLUMN_NAME").toLowerCase());
                }
                return Collections.unmodifiableSet(cols);
            } catch (SQLException e) {
                log.error("❌ 获取表 {} 元数据失败，返回空集", t, e);
                return Collections.emptySet();
            }
        });

        return columns.contains(normalizedColumn);
    }

    /**
     * 获取所有属性IRI到SQL变量集合的映射（供外部服务读取）
     * <p>
     * ✅ 修复：直接访问 Holder.MAPPING_CACHE，
     * 因为映射已在 Holder 静态初始化块中通过 OntopMappingResolver 预加载，
     * 无需额外的 ensureMappingLoaded() 检查。
     */
    public Map<String, Set<String>> getAllMappedPropertiesWithVariables() {
        Map<String, Set<String>> legacyMap = new LinkedHashMap<>();
        Holder.MAPPING_CACHE.forEach((iri, cm) ->
                legacyMap.put(iri, Collections.singleton(cm.tableName() + "." + cm.columnName()))
        );
        return Collections.unmodifiableMap(legacyMap);
    }
}