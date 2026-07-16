package com.ocean.ontopobdahandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class ObdaMappingParser {

    private static volatile boolean initialized = false;
    private static final Map<String, ColumnMapping> PREDICATE_TO_COLUMN = new LinkedHashMap<>();
    private static final Map<String, String> PREFIX_MAP = new LinkedHashMap<>();
    private static String loadedFilePath = null;
    private static final Logger log = LoggerFactory.getLogger(ObdaMappingParser.class);


    // target 模板中谓词-列占位符的正则
    // 匹配: :localName {col} 或 <fullIRI> {col}，忽略 ^^xsd:type 和 @lang 后缀
    private static final Pattern TARGET_PATTERN = Pattern.compile(
            "(?::([\\w-]+)|<([^>]+)>)\\s*\"\\{([^}]+)\\}\"(?:\\^\\^|@)?"
    );

    public static class ColumnMapping {
        private final String predicateIri;
        private final String columnName;
        private final String mappingId;

        public ColumnMapping(String predicateIri, String columnName, String mappingId) {
            this.predicateIri = predicateIri;
            this.columnName = columnName;
            this.mappingId = mappingId;
        }

        public String getPredicateIri() { return predicateIri; }
        public String getColumnName()   { return columnName; }
        public String getMappingId()    { return mappingId; }

        @Override
        public String toString() {
            return String.format("ColumnMapping{predicate='%s', column='%s', mapping='%s'}",
                    predicateIri, columnName, mappingId);
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
            log.info("✅ OBDA 映射已从磁盘加载: %s (%d 条谓词映射)%n",
                    loadedFilePath, PREDICATE_TO_COLUMN.size());
        } catch (IOException e) {
            throw new IllegalStateException("❌ 读取 OBDA 映射文件失败: " + path, e);
        }
    }

    // ==================== 解析核心（OBDA 简化语法） ====================

    private static void parse(BufferedReader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }

        String text = content.toString();

        // 1. 解析前缀声明
        parsePrefixes(text);

        // 2. 按 mappingId 分块解析 target
        parseMappings(text);
    }

    private static void parsePrefixes(String text) {
        // 提取 [PrefixDeclaration] 到下一个 [ 之间的内容
        int start = text.indexOf("[PrefixDeclaration]");
        if (start == -1) return;

        int end = text.indexOf('[', start + 1);
        String block = end != -1 ? text.substring(start, end) : text.substring(start);

        for (String line : block.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") || trimmed.isEmpty()) continue;

            // 格式: prefix:   namespace
            Matcher m = Pattern.compile("^([\\w-]*)\\s*:\\s*(.+)$").matcher(trimmed);
            if (m.matches()) {
                String prefix = m.group(1);       // 空字符串表示默认前缀 ":"
                String ns = m.group(2).trim();
                PREFIX_MAP.put(prefix, ns);
            }
        }
        log.info("📖 已加载 %d 个前缀声明%n", PREFIX_MAP.size());
    }

    private static void parseMappings(String text) {
        // 按 mappingId 分割，每个块包含 target/source
        Pattern blockPattern = Pattern.compile(
                "mappingId\\s+(\\S+)(.*?)(?=mappingId\\s+\\S+|\\]\\]|$)",
                Pattern.DOTALL
        );

        Matcher blockMatcher = blockPattern.matcher(text);
        while (blockMatcher.find()) {
            String mappingId = blockMatcher.group(1).trim();
            String blockContent = blockMatcher.group(2);

            // 提取 target 行（可能跨多行，以 source 或下一个关键字为界）
            int targetStart = blockContent.indexOf("target");
            if (targetStart == -1) continue;

            int sourceStart = blockContent.indexOf("source", targetStart);
            String targetBlock = sourceStart != -1
                    ? blockContent.substring(targetStart + "target".length(), sourceStart)
                    : blockContent.substring(targetStart + "target".length());

            // 将 target 模板中的换行合并为单行以便正则匹配
            String targetTemplate = targetBlock.replaceAll("\\s+", " ").trim();

            extractFromTarget(targetTemplate, mappingId);
        }
    }

    private static void extractFromTarget(String template, String mappingId) {
        Matcher m = TARGET_PATTERN.matcher(template);
        while (m.find()) {
            String localName = m.group(1);   // :name → "name"
            String fullIri     = m.group(2); // <http://...> → 完整IRI
            String columnName  = m.group(3).trim();

            String predicateKey;
            if (fullIri != null) {
                predicateKey = fullIri;
            } else if (localName != null) {
                // 使用默认前缀（空字符串key）展开
                String defaultNs = PREFIX_MAP.getOrDefault("", "");
                predicateKey = defaultNs + localName;
            } else {
                continue;
            }

            if (!columnName.isEmpty()) {
                PREDICATE_TO_COLUMN.put(predicateKey,
                        new ColumnMapping(predicateKey, columnName, mappingId));
            }
        }
    }

    // ==================== 内置映射注入 ====================

    private static void injectBuiltinMappings() {
        String rdfTypeIri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
        if (!PREDICATE_TO_COLUMN.containsKey(rdfTypeIri)) {
            PREDICATE_TO_COLUMN.put(rdfTypeIri,
                    new ColumnMapping(rdfTypeIri, "type", "__builtin_rdf_type__"));
            log.info("✅ 已注入内置映射: rdf:type → column 'type'");
        }
    }

    // ==================== 对外查询接口 ====================

    public static ColumnMapping resolve(String predicate) {
        ensureLoaded();

        // ① 精确匹配（完整 IRI 直接命中）
        ColumnMapping exact = PREDICATE_TO_COLUMN.get(predicate);
        if (exact != null) return exact;

        // ② 前缀展开匹配（rdf:type → http://www.w3.org/1999/02/22-rdf-syntax-ns#type）
        String expanded = expandPrefixedName(predicate);
        if (!expanded.equals(predicate)) {
            ColumnMapping prefixed = PREDICATE_TO_COLUMN.get(expanded);
            if (prefixed != null) {
                log.debug("⚠️ 谓词 '%s' 通过前缀展开匹配到 IRI: %s%n", predicate, expanded);
                return prefixed;
            }
        }

        // ③ 裸名回退匹配（type → 遍历所有 IRI 的本地名）
        for (Map.Entry<String, ColumnMapping> entry : PREDICATE_TO_COLUMN.entrySet()) {
            String localName = extractLocalName(entry.getKey());
            if (localName.equals(predicate)) {
                log.debug("⚠️ 谓词 '%s' 通过本地名回退匹配到 IRI: %s%n", predicate, entry.getKey());
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

    private static void ensureLoaded() {
        if (!initialized) {
            throw new IllegalStateException(
                    "❌ ObdaMappingParser 尚未初始化，请先调用 load(path) 加载 OBDA 映射文件");
        }
    }
    /**
     * 将带前缀的谓词（如 rdf:type）展开为完整 IRI
     * 如果无法展开则返回原始字符串
     */
    private static String expandPrefixedName(String prefixedName) {
        int colonIdx = prefixedName.indexOf(':');
        if (colonIdx <= 0) return prefixedName; // 无前缀或非法格式

        String prefix = prefixedName.substring(0, colonIdx);
        String localPart = prefixedName.substring(colonIdx + 1);

        String namespace = PREFIX_MAP.get(prefix);
        if (namespace == null) return prefixedName; // 未知前缀，原样返回

        return namespace + localPart;
    }
}