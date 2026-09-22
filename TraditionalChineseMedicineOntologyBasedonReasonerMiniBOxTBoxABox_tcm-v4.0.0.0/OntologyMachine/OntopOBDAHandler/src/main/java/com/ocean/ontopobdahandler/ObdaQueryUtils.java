package com.ocean.ontopobdahandler;

import com.ocean.ontopobdahandler.OBDAHandler;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * OBDA / IRI / Label 通用工具方法。与任何领域无关。
 */
public final class ObdaQueryUtils {

    private ObdaQueryUtils() {}

    /** 提取 IRI 的 fragment（# 或最后一个 / 之后的部分）。 */
    public static String fragmentOf(String iri) {
        if (iri == null) return null;
        String s = iri.trim();
        if (s.isEmpty()) return null;
        int idx = s.lastIndexOf('#');
        if (idx >= 0) return s.substring(idx + 1);
        idx = s.lastIndexOf('/');
        return idx >= 0 ? s.substring(idx + 1) : s;
    }

    /** 若 iri 非绝对 IRI，则用 baseNs 补全。 */
    public static String toFullIri(String iri, String baseNs) {
        if (iri == null || iri.isBlank()) return iri;
        if (iri.startsWith("http://") || iri.startsWith("https://")) return iri;
        return baseNs + iri;
    }

    /**
     * 提取 fragment，并去掉可选的实例后缀（例如 "_instance"）。
     * 若 iri 不以 baseNs 开头，返回完整 iri。
     */
    public static String frag(String iri, String baseNs, String instanceSuffix) {
        String full = toFullIri(iri, baseNs);
        if (full == null) return null;
        if (!full.startsWith(baseNs)) return full;
        String f = full.substring(baseNs.length());
        if (instanceSuffix != null && !instanceSuffix.isEmpty() && f.endsWith(instanceSuffix)) {
            f = f.substring(0, f.length() - instanceSuffix.length());
        }
        return f;
    }

    /**
     * 从 OBDA 执行 SPARQL（SELECT ?a ?b）并构建无向关系表。
     * 结果为 Map<fragmentA, Set<fragmentB>>，双向填充。
     */
    public static Map<String, Set<String>> loadUndirectedRelationFromObda(
            OBDAHandler handler, String sparql) {
        Map<String, Set<String>> map = new HashMap<>();
        for (Map<String, String> row : handler.executeAboxQuery(sparql)) {
            String a = fragmentOf(row.get("a"));
            String b = fragmentOf(row.get("b"));
            if (a == null || b == null) continue;
            map.computeIfAbsent(a, k -> new HashSet<>()).add(b);
            map.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        }
        return map;
    }

    /** 查询某 IRI 的 rdfs:label，失败或不存在则回退 fragment。 */
    public static String queryLabel(OBDAHandler handler, String iri,
                                    String baseNs, Logger log) {
        if (iri == null || iri.isBlank()) return "";
        String full = toFullIri(iri, baseNs);
        String frag = fragmentOf(full);
        try {
            String sparql = """
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?label WHERE { <%s> rdfs:label ?label . }
                """.formatted(full);
            for (Map<String, String> row : handler.executeAboxQuery(sparql)) {
                String label = row.get("label");
                if (label != null && !label.isBlank()) return label.trim();
            }
        } catch (Exception e) {
            if (log != null) log.warn("queryLabel 失败: {}", full, e);
        }
        return frag != null ? frag : full;
    }

    /** 从 Camunda 变量 Map 里安全提取 List<String>。 */
    public static List<String> getList(Map<String, Object> vars, String key) {
        Object v = vars.get(key);
        if (v instanceof List<?> l)
            return l.stream().map(Object::toString).collect(Collectors.toList());
        return Collections.emptyList();
    }
}