package com.ocean.ontopobdahandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/**
 * OBDA 映射文件全量解析器（v2.0）
 * 支持 target + source 双段解析，提供正向/反向索引，为多表动态查询和更新提供元数据基础。
 */
public class ObdaMappingParser {

    private static final Logger log = LoggerFactory.getLogger(ObdaMappingParser.class);

    // ==================== 不可变状态快照 ====================
    private static volatile MappingSnapshot snapshot = null;
    private static volatile String loadedFilePath = null;

    /**
     * ✅ 新增：检查 OBDA 映射是否已成功加载
     * 供外部（如 OBDAHandler.Holder）在初始化前进行守卫判断
     */
    public static boolean isLoaded() {
        return snapshot != null;
    }

    /**
     * 获取当前已加载的文件路径（用于诊断）
     */
    public static String getLoadedFilePath() {
        return loadedFilePath;
    }

    /**
     * 映射快照：所有数据结构在加载完成后即为不可变，天然线程安全。
     */
    public static class MappingSnapshot {
        final Map<String, ObdaMappingEntry> predicateToEntry;   // 谓词IRI → 完整映射条目
        final Map<String, Set<String>> tableToPredicates;       // 表名 → 该表承载的所有谓词IRI集合
        final Map<String, String> prefixMap;                    // 前缀 → 命名空间
        final int totalMappings;

        MappingSnapshot(Map<String, ObdaMappingEntry> predicateToEntry,
                        Map<String, Set<String>> tableToPredicates,
                        Map<String, String> prefixMap) {
            this.predicateToEntry = Collections.unmodifiableMap(predicateToEntry);
            this.tableToPredicates = Collections.unmodifiableMap(tableToPredicates);
            this.prefixMap = Collections.unmodifiableMap(prefixMap);
            this.totalMappings = predicateToEntry.size();
        }
    }

    /**
     * 完整的 OBDA 映射条目：同时包含 target 语义信息和 source SQL 物理信息。
     */
    public static class ObdaMappingEntry {
        private final String predicateIri;
        private final String columnName;
        private final String mappingId;
        private final String targetDatatype;
        private final String sourceTable;          // source SQL 中的主表名
        private final String sourceSqlTemplate;    // 原始 source SQL 模板
        private final List<String> joinTables;     // source SQL 中涉及的关联表

        public ObdaMappingEntry(String predicateIri, String columnName, String mappingId,
                                String targetDatatype, String sourceTable,
                                String sourceSqlTemplate, List<String> joinTables) {
            this.predicateIri = predicateIri;
            this.columnName = columnName;
            this.mappingId = mappingId;
            this.targetDatatype = targetDatatype != null ? targetDatatype : "xsd:string";
            this.sourceTable = sourceTable;
            this.sourceSqlTemplate = sourceSqlTemplate;
            this.joinTables = joinTables != null
                    ? Collections.unmodifiableList(joinTables)
                    : Collections.emptyList();
        }

        public String getPredicateIri()      { return predicateIri; }
        public String getColumnName()        { return columnName; }
        public String getMappingId()         { return mappingId; }
        public String getTargetDatatype()    { return targetDatatype; }
        public String getSourceTable()       { return sourceTable; }
        public String getSourceSqlTemplate() { return sourceSqlTemplate; }
        public List<String> getJoinTables()  { return joinTables; }

        @Override
        public String toString() {
            return String.format("ObdaMappingEntry{pred='%s', col='%s', table='%s', type='%s', joins=%s}",
                    predicateIri, columnName, sourceTable, targetDatatype, joinTables);
        }
    }

    // 兼容旧接口：保留 ColumnMapping 作为轻量视图
    public static class ColumnMapping {
        private final ObdaMappingEntry entry;
        public ColumnMapping(ObdaMappingEntry entry) { this.entry = entry; }
        public String getPredicateIri()   { return entry.getPredicateIri(); }
        public String getColumnName()     { return entry.getColumnName(); }
        public String getMappingId()      { return entry.getMappingId(); }
        public String getTargetDatatype() { return entry.getTargetDatatype(); }
    }

    // ==================== 正则模式 ====================

    private static final Pattern TARGET_PATTERN = Pattern.compile(
            "(?::([\\w-]+)|<([^>]+)>)\\s*\"\\{([^}]+)\\}\"(?:\\^\\^([\\w:-]+)|@[\\w-]+)?"
    );

    // ==================== 加载入口 ====================

    /**
     * 加载 OBDA 映射文件（幂等：已加载则跳过）
     */
    public static synchronized void load(String defaultPath) {
        if (snapshot != null) {
            log.debug("ℹ️ ObdaMappingParser 已加载 [{}]，跳过重复加载", loadedFilePath);
            return;
        }

        long startTime = System.currentTimeMillis();
        String path = System.getenv("OBDA_MAPPING_PATH");
        if (path == null || path.isBlank()) path = defaultPath;

        File file = new File(path);
        if (!file.exists()) {
            throw new IllegalStateException(String.format(
                    "❌ OBDA 映射文件不存在: %s%n   (可通过环境变量 OBDA_MAPPING_PATH 覆盖)",
                    file.getAbsolutePath()));
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            MappingSnapshot newSnapshot = parse(reader);
            snapshot = newSnapshot;
            loadedFilePath = file.getAbsolutePath();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("✅ OBDA 映射已加载: {} | {} 条谓词 | {} 张物理表 | 耗时 {}ms",
                    loadedFilePath, newSnapshot.totalMappings,
                    newSnapshot.tableToPredicates.size(), elapsed);
        } catch (IOException e) {
            throw new IllegalStateException("❌ 读取 OBDA 映射文件失败: " + path, e);
        }
    }

    /**
     * 强制重新加载（用于热更新或测试）。
     */
    public static synchronized void reload(String path) {
        log.info("🔄 强制重新加载 OBDA 映射: {}", path);
        snapshot = null;
        loadedFilePath = null;
        load(path);
    }

    // ==================== 解析核心 ====================

    private static MappingSnapshot parse(BufferedReader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        String text = content.toString();

        Map<String, String> prefixMap = parsePrefixes(text);
        Map<String, ObdaMappingEntry> predicateToEntry = parseMappings(text, prefixMap);

        // 注入内置映射
        injectBuiltinMappings(predicateToEntry);

        // 构建反向索引：表名 → 谓词集合
        Map<String, Set<String>> tableToPredicates = new LinkedHashMap<>();
        for (ObdaMappingEntry entry : predicateToEntry.values()) {
            if (entry.getSourceTable() != null) {
                tableToPredicates
                        .computeIfAbsent(entry.getSourceTable().toLowerCase(), k -> new LinkedHashSet<>())
                        .add(entry.getPredicateIri());
            }
        }

        return new MappingSnapshot(predicateToEntry, tableToPredicates, prefixMap);
    }

    private static Map<String, String> parsePrefixes(String text) {
        Map<String, String> prefixes = new LinkedHashMap<>();
        int start = text.indexOf("[PrefixDeclaration]");
        if (start == -1) return prefixes;

        int end = text.indexOf('[', start + 1);
        String block = end != -1 ? text.substring(start, end) : text.substring(start);

        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") || trimmed.isEmpty()) continue;
            Matcher m = Pattern.compile("^([\\w-]*)\\s*:\\s*(.+)$").matcher(trimmed);
            if (m.matches()) {
                prefixes.put(m.group(1), m.group(2).trim());
            }
        }
        log.debug("📖 已加载 {} 个前缀声明", prefixes.size());
        return prefixes;
    }

    private static Map<String, ObdaMappingEntry> parseMappings(String text, Map<String, String> prefixMap) {
        Map<String, ObdaMappingEntry> entries = new LinkedHashMap<>();

        Pattern blockPattern = Pattern.compile(
                "mappingId\\s+(\\S+)(.*?)(?=mappingId\\s+\\S+|\\]\\]|$)", Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(text);

        int skippedCount = 0;
        while (blockMatcher.find()) {
            String mappingId = blockMatcher.group(1).trim();
            String blockContent = blockMatcher.group(2);

            // --- 解析 target ---
            int targetStart = blockContent.indexOf("target");
            if (targetStart == -1) { skippedCount++; continue; }

            int sourceStart = blockContent.indexOf("source", targetStart);
            String targetBlock = sourceStart != -1
                    ? blockContent.substring(targetStart + "target".length(), sourceStart)
                    : blockContent.substring(targetStart + "target".length());
            String targetTemplate = targetBlock.replaceAll("\\s+", " ").trim();

            // --- 解析 source SQL ---
            String sourceSql = "";
            if (sourceStart != -1) {
                int nextMapping = blockContent.indexOf("mappingId", sourceStart);
                sourceSql = nextMapping != -1
                        ? blockContent.substring(sourceStart + "source".length(), nextMapping)
                        : blockContent.substring(sourceStart + "source".length());
                sourceSql = sourceSql.replaceAll("\\s+", " ").trim();
            }

            // 提取主表和关联表
            String primaryTable = extractPrimaryTable(sourceSql);
            List<String> joinTables = extractJoinTables(sourceSql);

            // 从 target 提取谓词→列映射
            Matcher tm = TARGET_PATTERN.matcher(targetTemplate);
            while (tm.find()) {
                String localName   = tm.group(1);
                String fullIri     = tm.group(2);
                String columnName  = tm.group(3).trim();
                String rawDatatype = tm.group(4);

                String predicateKey;
                if (fullIri != null) {
                    predicateKey = fullIri;
                } else if (localName != null) {
                    String defaultNs = prefixMap.getOrDefault("", "");
                    predicateKey = defaultNs + localName;
                } else {
                    continue;
                }

                String resolvedDatatype = resolveDatatype(rawDatatype, prefixMap);

                if (!columnName.isEmpty()) {
                    entries.put(predicateKey, new ObdaMappingEntry(
                            predicateKey, columnName, mappingId, resolvedDatatype,
                            primaryTable, sourceSql, joinTables));
                }
            }
        }

        if (skippedCount > 0) {
            log.warn("⚠️ 跳过了 {} 个缺少 target 段的 mappingId 块", skippedCount);
        }
        return entries;
    }

    private static String extractPrimaryTable(String sourceSql) {
        if (sourceSql == null || sourceSql.isBlank()) return null;
        Matcher m = Pattern.compile("FROM\\s+([\\w.`\"]+)", Pattern.CASE_INSENSITIVE).matcher(sourceSql);
        return m.find() ? normalizeTableName(m.group(1)) : null;
    }

    private static List<String> extractJoinTables(String sourceSql) {
        if (sourceSql == null || sourceSql.isBlank()) return Collections.emptyList();
        List<String> tables = new ArrayList<>();
        Matcher m = Pattern.compile("JOIN\\s+([\\w.`\"]+)", Pattern.CASE_INSENSITIVE).matcher(sourceSql);
        while (m.find()) {
            tables.add(normalizeTableName(m.group(1)));
        }
        return tables;
    }

    private static String normalizeTableName(String raw) {
        return raw.replace("`", "").replace("\"", "").trim().toLowerCase();
    }

    private static String resolveDatatype(String rawDatatype, Map<String, String> prefixMap) {
        if (rawDatatype == null || rawDatatype.isBlank()) return null;
        int colonIdx = rawDatatype.indexOf(':');
        if (colonIdx <= 0) return rawDatatype;

        String prefix = rawDatatype.substring(0, colonIdx);
        String localPart = rawDatatype.substring(colonIdx + 1);

        if ("xsd".equals(prefix) || "rdf".equals(prefix)

                || "rdfs".equals(prefix) || "owl".equals(prefix)) {
            return rawDatatype;
        }

        String namespace = prefixMap.get(prefix);
        if (namespace != null) return namespace + localPart;

        log.warn("⚠️ 数据类型前缀 '{}' 未在前缀声明中找到，保持原始值: {}", prefix, rawDatatype);
        return rawDatatype;
    }

    private static void injectBuiltinMappings(Map<String, ObdaMappingEntry> entries) {
        String rdfTypeIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
        if (!entries.containsKey(rdfTypeIri)) {
            entries.put(rdfTypeIri, new ObdaMappingEntry(
                    rdfTypeIri, "type", "__builtin_rdf_type__", "xsd:anyURI",
                    null, null, Collections.emptyList()));
            log.debug("✅ 已注入内置映射: rdf:type");
        }
    }

    // ==================== 对外查询接口 ====================

    /** 兼容旧版 API */
    public static ColumnMapping resolve(String predicate) {
        return new ColumnMapping(resolveEntry(predicate));
    }

    /** 新版：返回完整映射条目 */
    public static ObdaMappingEntry resolveEntry(String predicate) {
        MappingSnapshot snap = ensureLoaded();

        // ① 精确匹配
        ObdaMappingEntry exact = snap.predicateToEntry.get(predicate);
        if (exact != null) return exact;

        // ② 前缀展开匹配
        String expanded = expandPrefixedName(predicate, snap.prefixMap);
        if (!expanded.equals(predicate)) {
            ObdaMappingEntry prefixed = snap.predicateToEntry.get(expanded);
            if (prefixed != null) {
                log.debug("⚠️ 谓词 '{}' 通过前缀展开匹配到 IRI: {}", predicate, expanded);
                return prefixed;
            }
        }

        // ③ 裸名回退匹配
        for (Map.Entry<String, ObdaMappingEntry> entry : snap.predicateToEntry.entrySet()) {
            if (extractLocalName(entry.getKey()).equals(predicate)) {
                log.debug("⚠️ 谓词 '{}' 通过本地名回退匹配到 IRI: {}", predicate, entry.getKey());
                return entry.getValue();
            }
        }

        throw new IllegalStateException(String.format(
                "❌ 谓词 [%s] 在 OBDA 映射文件中未找到对应列%n" +
                        "📋 已加载的 %d 个谓词:%n%s%n" +
                        "💡 提示: 请检查谓词大小写、命名空间前缀是否与 OBDA 文件一致",
                predicate, snap.totalMappings,
                snap.predicateToEntry.keySet().stream().sorted()
                        .map(k -> "   - " + k).collect(Collectors.joining("\n"))));
    }

    /** 根据表名获取该表承载的所有谓词 */
    public static Set<String> getPredicatesByTable(String tableName) {
        MappingSnapshot snap = ensureLoaded();
        return snap.tableToPredicates.getOrDefault(tableName.toLowerCase(), Collections.emptySet());
    }

    /** 获取所有已知物理表名 */
    public static Set<String> getAllTables() {
        return ensureLoaded().tableToPredicates.keySet();
    }

    /** 批量安全解析，不抛异常，返回成功/失败分离结果 */
    public static BatchResolveResult resolveBatch(List<String> predicates) {
        MappingSnapshot snap = ensureLoaded();
        Map<String, ObdaMappingEntry> resolved = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();

        for (String pred : predicates) {
            try {
                resolved.put(pred, resolveEntry(pred));
            } catch (IllegalStateException e) {
                unresolved.add(pred);
            }
        }
        return new BatchResolveResult(resolved, unresolved);
    }

    public record BatchResolveResult(
            Map<String, ObdaMappingEntry> resolved,
            List<String> unresolved
    ) {}

    public static boolean containsPredicate(String predicate) {
        MappingSnapshot snap = ensureLoaded();
        if (snap.predicateToEntry.containsKey(predicate)) return true;
        for (String iri : snap.predicateToEntry.keySet()) {
            if (extractLocalName(iri).equals(predicate)) return true;
        }
        return false;
    }

    public static Set<String> getAllPredicates() {
        return ensureLoaded().predicateToEntry.keySet();
    }

    // ==================== 类型转换 ====================

    public static Object convertObjectValue(String objectValue, ColumnMapping mapping) {
        return convertObjectValue(objectValue, mapping.getTargetDatatype(), mapping.getColumnName());
    }

    /** 重载：直接使用 ObdaMappingEntry */
    public static Object convertObjectValue(String objectValue, ObdaMappingEntry entry) {
        return convertObjectValue(objectValue, entry.getTargetDatatype(), entry.getColumnName());
    }

    private static Object convertObjectValue(String objectValue, String targetXsdType, String columnName) {
        if (objectValue == null || objectValue.isBlank()) return null;
        String trimmedValue = objectValue.trim();

        try {
            return switch (targetXsdType) {
                case "xsd:integer", "xsd:int", "xsd:long", "xsd:short", "xsd:nonNegativeInteger" ->
                        new BigInteger(trimmedValue);
                case "xsd:decimal", "xsd:float", "xsd:double" ->
                        new BigDecimal(trimmedValue);
                case "xsd:boolean" -> parseFlexibleBoolean(trimmedValue);
                case "xsd:dateTime" ->
                        Timestamp.valueOf(LocalDateTime.parse(trimmedValue.replace("T", " ")));
                case "xsd:date" -> Date.valueOf(LocalDate.parse(trimmedValue));
                case "xsd:string", "xsd:anyURI", "rdfs:Literal" -> trimmedValue;
                default -> {
                    log.warn("⚠️ 未识别的 OBDA 目标类型 {} | column={} | 降级为字符串", targetXsdType, columnName);
                    yield trimmedValue;
                }
            };
        } catch (DateTimeParseException | IllegalArgumentException e) {
            log.warn("⚠️ OBDA 类型转换失败 | column={} | targetType={} | rawValue='{}' | error={}",
                    columnName, targetXsdType, trimmedValue, e.getMessage());
            return trimmedValue;
        }
    }

    private static Boolean parseFlexibleBoolean(String value) {
        return switch (value.toUpperCase()) {
            case "TRUE", "1", "Y", "YES" -> Boolean.TRUE;
            case "FALSE", "0", "N", "NO" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("无法解析为布尔值: '" + value + "'");
        };
    }

    // ==================== 工具方法 ====================

    private static String extractLocalName(String iri) {
        int slashIdx = iri.lastIndexOf('/');
        int hashIdx = iri.lastIndexOf('#');
        String localName = slashIdx > hashIdx ? iri.substring(slashIdx + 1)
                : hashIdx != -1 ? iri.substring(hashIdx + 1) : iri;
        while (localName.endsWith("/") || localName.endsWith("#")) {
            localName = localName.substring(0, localName.length() - 1);
        }
        return localName;
    }

    private static String expandPrefixedName(String prefixedName, Map<String, String> prefixMap) {
        int colonIdx = prefixedName.indexOf(':');
        if (colonIdx <= 0) return prefixedName;
        String prefix = prefixedName.substring(0, colonIdx);
        String namespace = prefixMap.get(prefix);
        return namespace != null ? namespace + prefixedName.substring(colonIdx + 1) : prefixedName;
    }

    private static MappingSnapshot ensureLoaded() {
        MappingSnapshot snap = snapshot;
        if (snap == null) {
            throw new IllegalStateException(
                    "❌ ObdaMappingParser 尚未初始化，请先调用 load(path) 加载 OBDA 映射文件");
        }
        return snap;
    }
}