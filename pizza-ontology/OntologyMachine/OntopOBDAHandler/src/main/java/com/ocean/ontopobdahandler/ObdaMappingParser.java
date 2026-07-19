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

public class ObdaMappingParser {

    private static volatile boolean initialized = false;
    private static final Map<String, ColumnMapping> PREDICATE_TO_COLUMN = new LinkedHashMap<>();
    private static final Map<String, String> PREFIX_MAP = new LinkedHashMap<>();
    private static String loadedFilePath = null;
    private static final Logger log = LoggerFactory.getLogger(ObdaMappingParser.class);

    /**
     * 匹配 target 模板中的谓词-列占位符及可选的 XSD 类型声明。
     * 支持格式:
     *   :localName "{col}"
     *   :localName "{col}"^^xsd:integer
     *   :localName "{col}"^^:customType
     *   <fullIRI> "{col}"^^xsd:string
     *   :name "{col}"@en  (语言标签会被忽略，仅捕获列名)
     */
    private static final Pattern TARGET_PATTERN = Pattern.compile(
            "(?::([\\w-]+)|<([^>]+)>)\\s*\"\\{([^}]+)\\}\"(?:\\^\\^([\\w:-]+)|@[\\w-]+)?"
    );

    // ==================== 内部数据类 ====================

    public static class ColumnMapping {
        private final String predicateIri;
        private final String columnName;
        private final String mappingId;
        private final String targetDatatype; // ✅ 新增：OBDA 显式声明的 XSD 类型，如 "xsd:integer"

        public ColumnMapping(String predicateIri, String columnName, String mappingId, String targetDatatype) {
            this.predicateIri = predicateIri;
            this.columnName = columnName;
            this.mappingId = mappingId;
            this.targetDatatype = targetDatatype != null ? targetDatatype : "xsd:string"; // 默认兜底
        }

        public String getPredicateIri()   { return predicateIri; }
        public String getColumnName()     { return columnName; }
        public String getMappingId()      { return mappingId; }
        public String getTargetDatatype() { return targetDatatype; }

        @Override
        public String toString() {
            return String.format("ColumnMapping{predicate='%s', column='%s', type='%s', mapping='%s'}",
                    predicateIri, columnName, targetDatatype, mappingId);
        }
    }

    // ==================== 加载入口 ====================

    public static synchronized void load(String defaultPath) {
        if (initialized) return;

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
            parse(reader);
            injectBuiltinMappings();
            loadedFilePath = file.getAbsolutePath();
            initialized = true;
            log.info("✅ OBDA 映射已从磁盘加载: {} ({} 条谓词映射)",
                    loadedFilePath, PREDICATE_TO_COLUMN.size());
        } catch (IOException e) {
            throw new IllegalStateException("❌ 读取 OBDA 映射文件失败: " + path, e);
        }
    }

    // ==================== 解析核心 ====================

    private static void parse(BufferedReader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        String text = content.toString();

        parsePrefixes(text);
        parseMappings(text);
    }

    private static void parsePrefixes(String text) {
        int start = text.indexOf("[PrefixDeclaration]");
        if (start == -1) return;

        int end = text.indexOf('[', start + 1);
        String block = end != -1 ? text.substring(start, end) : text.substring(start);

        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") || trimmed.isEmpty()) continue;

            Matcher m = Pattern.compile("^([\\w-]*)\\s*:\\s*(.+)$").matcher(trimmed);
            if (m.matches()) {
                String prefix = m.group(1);
                String ns = m.group(2).trim();
                PREFIX_MAP.put(prefix, ns);
            }
        }
        log.info("📖 已加载 {} 个前缀声明", PREFIX_MAP.size());
    }

    private static void parseMappings(String text) {
        Pattern blockPattern = Pattern.compile(
                "mappingId\\s+(\\S+)(.*?)(?=mappingId\\s+\\S+|\\]\\]|$)",
                Pattern.DOTALL
        );

        Matcher blockMatcher = blockPattern.matcher(text);
        while (blockMatcher.find()) {
            String mappingId = blockMatcher.group(1).trim();
            String blockContent = blockMatcher.group(2);

            int targetStart = blockContent.indexOf("target");
            if (targetStart == -1) continue;

            int sourceStart = blockContent.indexOf("source", targetStart);
            String targetBlock = sourceStart != -1
                    ? blockContent.substring(targetStart + "target".length(), sourceStart)
                    : blockContent.substring(targetStart + "target".length());

            String targetTemplate = targetBlock.replaceAll("\\s+", " ").trim();
            extractFromTarget(targetTemplate, mappingId);
        }
    }

    /**
     * 从 target 模板中提取谓词→列映射及 XSD 数据类型。
     * 数据类型中的前缀会自动展开（如 :int → http://example.org/int），
     * 标准 xsd: 前缀保持原样以便 convertObjectValue 直接匹配。
     */
    private static void extractFromTarget(String template, String mappingId) {
        Matcher m = TARGET_PATTERN.matcher(template);
        while (m.find()) {
            String localName    = m.group(1);
            String fullIri      = m.group(2);
            String columnName   = m.group(3).trim();
            String rawDatatype  = m.group(4); // 可能为 null

            String predicateKey;
            if (fullIri != null) {
                predicateKey = fullIri;
            } else if (localName != null) {
                String defaultNs = PREFIX_MAP.getOrDefault("", "");
                predicateKey = defaultNs + localName;
            } else {
                continue;
            }

            // ✅ 展开数据类型前缀
            String resolvedDatatype = resolveDatatype(rawDatatype);

            if (!columnName.isEmpty()) {
                PREDICATE_TO_COLUMN.put(predicateKey,
                        new ColumnMapping(predicateKey, columnName, mappingId, resolvedDatatype));
            }
        }
    }

    /**
     * 将 OBDA 中声明的数据类型前缀展开为标准形式。
     * 例如: ":integer" → "http://example.org/integer"
     *       "xsd:integer" → "xsd:integer" (保持不变)
     *       null → null (由 ColumnMapping 构造函数兜底为 xsd:string)
     */
    private static String resolveDatatype(String rawDatatype) {
        if (rawDatatype == null || rawDatatype.isBlank()) return null;

        int colonIdx = rawDatatype.indexOf(':');
        if (colonIdx <= 0) return rawDatatype; // 无前缀，原样返回

        String prefix = rawDatatype.substring(0, colonIdx);
        String localPart = rawDatatype.substring(colonIdx + 1);

        // xsd / rdf / rdfs / owl 等标准前缀不展开，保持短形式供 switch 匹配
        if ("xsd".equals(prefix) || "rdf".equals(prefix)

                || "rdfs".equals(prefix) || "owl".equals(prefix)) {
            return rawDatatype;
        }

        // 自定义前缀展开为完整 IRI
        String namespace = PREFIX_MAP.get(prefix);
        if (namespace != null) {
            return namespace + localPart;
        }

        // 未知前缀，记录警告并原样返回
        log.warn("⚠️ 数据类型前缀 '{}' 未在前缀声明中找到，保持原始值: {}", prefix, rawDatatype);
        return rawDatatype;
    }

    // ==================== 内置映射注入 ====================

    private static void injectBuiltinMappings() {
        String rdfTypeIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
        if (!PREDICATE_TO_COLUMN.containsKey(rdfTypeIri)) {
            PREDICATE_TO_COLUMN.put(rdfTypeIri,
                    new ColumnMapping(rdfTypeIri, "type", "__builtin_rdf_type__", "xsd:anyURI"));
            log.info("✅ 已注入内置映射: rdf:type → column 'type' (xsd:anyURI)");
        }
    }

    // ==================== 对外查询接口 ====================

    public static ColumnMapping resolve(String predicate) {
        ensureLoaded();

        // ① 精确匹配
        ColumnMapping exact = PREDICATE_TO_COLUMN.get(predicate);
        if (exact != null) return exact;

        // ② 前缀展开匹配
        String expanded = expandPrefixedName(predicate);
        if (!expanded.equals(predicate)) {
            ColumnMapping prefixed = PREDICATE_TO_COLUMN.get(expanded);
            if (prefixed != null) {
                log.debug("⚠️ 谓词 '{}' 通过前缀展开匹配到 IRI: {}", predicate, expanded);
                return prefixed;
            }
        }

        // ③ 裸名回退匹配
        for (Map.Entry<String, ColumnMapping> entry : PREDICATE_TO_COLUMN.entrySet()) {
            String localName = extractLocalName(entry.getKey());
            if (localName.equals(predicate)) {
                log.debug("⚠️ 谓词 '{}' 通过本地名回退匹配到 IRI: {}", predicate, entry.getKey());
                return entry.getValue();
            }
        }

        throw new IllegalStateException(String.format(
                "❌ 谓词 [%s] 在 OBDA 映射文件中未找到对应列，拒绝写入以防止数据丢失%n" +
                        "📋 已加载的 %d 个谓词:%n%s%n" +
                        "💡 提示: 请检查谓词大小写、命名空间前缀是否与 OBDA 文件一致",
                predicate,
                PREDICATE_TO_COLUMN.size(),
                PREDICATE_TO_COLUMN.keySet().stream()
                        .sorted()
                        .map(k -> "   - " + k)
                        .collect(Collectors.joining("\n"))
        ));
    }

    public static boolean containsPredicate(String predicate) {
        ensureLoaded();
        if (PREDICATE_TO_COLUMN.containsKey(predicate)) return true;
        for (String iri : PREDICATE_TO_COLUMN.keySet()) {
            if (extractLocalName(iri).equals(predicate)) return true;
        }
        return false;
    }

    public static Set<String> getAllPredicates() {
        ensureLoaded();
        return Collections.unmodifiableSet(PREDICATE_TO_COLUMN.keySet());
    }

    // ==================== 工具方法 ====================

    private static String extractLocalName(String iri) {
        int slashIdx = iri.lastIndexOf('/');
        int hashIdx = iri.lastIndexOf('#');
        String localName;
        if (slashIdx > hashIdx) {
            localName = iri.substring(slashIdx + 1);
        } else if (hashIdx != -1) {
            localName = iri.substring(hashIdx + 1);
        } else {
            localName = iri;
        }
        while (localName.endsWith("/") || localName.endsWith("#")) {
            localName = localName.substring(0, localName.length() - 1);
        }
        return localName;
    }

    private static String expandPrefixedName(String prefixedName) {
        int colonIdx = prefixedName.indexOf(':');
        if (colonIdx <= 0) return prefixedName;

        String prefix = prefixedName.substring(0, colonIdx);
        String localPart = prefixedName.substring(colonIdx + 1);

        String namespace = PREFIX_MAP.get(prefix);
        if (namespace == null) return prefixedName;

        return namespace + localPart;
    }

    private static void ensureLoaded() {
        if (!initialized) {
            throw new IllegalStateException(
                    "❌ ObdaMappingParser 尚未初始化，请先调用 load(path) 加载 OBDA 映射文件");
        }
    }

    // ==================== 类型转换（OBDA 驱动） ====================

    /**
     * 根据 OBDA 映射中定义的 XSD 类型进行精确转换，彻底移除列名启发式猜测。
     * 转换失败时降级返回原始字符串并记录警告，绝不中断写入流程。
     *
     * @param objectValue JDBC 读取的原始字符串值
     * @param mapping     当前列的 OBDA 映射对象（包含解析出的目标 XSD 类型）
     * @return 转换后的 Java 强类型对象，或降级返回原始字符串
     */
    public static Object convertObjectValue(String objectValue, ColumnMapping mapping) {
        if (objectValue == null || objectValue.isBlank()) {
            return null;
        }

        String trimmedValue = objectValue.trim();
        String targetXsdType = mapping.getTargetDatatype();

        try {
            return switch (targetXsdType) {
                case "xsd:integer", "xsd:int", "xsd:long", "xsd:short", "xsd:nonNegativeInteger" ->
                        new BigInteger(trimmedValue);
                case "xsd:decimal", "xsd:float", "xsd:double" ->
                        new BigDecimal(trimmedValue);
                case "xsd:boolean" ->
                        parseFlexibleBoolean(trimmedValue);
                case "xsd:dateTime" ->
                        Timestamp.valueOf(LocalDateTime.parse(trimmedValue.replace("T", " ")));
                case "xsd:date" ->
                        Date.valueOf(LocalDate.parse(trimmedValue));
                case "xsd:string", "xsd:anyURI", "rdfs:Literal" ->
                        trimmedValue;
                default -> {
                    log.warn("⚠️ 未识别的 OBDA 目标类型 {} | column={} | 降级为字符串",
                            targetXsdType, mapping.getColumnName());
                    yield trimmedValue;
                }
            };
        } catch (DateTimeParseException | IllegalArgumentException e) {
            log.warn("⚠️ OBDA 类型转换失败 | column={} | targetType={} | rawValue='{}' | error={}",
                    mapping.getColumnName(), targetXsdType, trimmedValue, e.getMessage());
            return trimmedValue;
        }
    }

    /**
     * 灵活的布尔解析：支持 true/false, 1/0, Y/N, YES/NO
     */
    private static Boolean parseFlexibleBoolean(String value) {
        String upper = value.toUpperCase();
        return switch (upper) {
            case "TRUE", "1", "Y", "YES" -> Boolean.TRUE;
            case "FALSE", "0", "N", "NO" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException("无法解析为布尔值: '" + value + "'");
        };
    }
}