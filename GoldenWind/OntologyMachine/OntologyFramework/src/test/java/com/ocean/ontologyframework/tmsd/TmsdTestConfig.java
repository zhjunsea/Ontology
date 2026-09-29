package com.ocean.ontologyframework.tmsd;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TMSD 测试配置加载器。
 *
 * <p>与运行时保持一致：先读 {@code application.yml}（其中 {@code ontology.*} 为中医经方
 * 应用的默认值），再叠加 {@code application-TMSDBPMN.yml}（TMSD profile 覆盖：塔架本体 /
 * 可执行 BPMN / {@code tmsd.*}）。
 *
 * <p>这样 TMSD 测试既能取到塔架 {@code ontology.main-path} / {@code ontology.bpmn-path} 与
 * {@code tmsd.*}，又不影响中医经方测试对 {@code application.yml} 中 TCM 默认值的依赖。
 */
public final class TmsdTestConfig {

    private static final String BASE = "application.yml";
    private static final String PROFILE = "application-TMSDBPMN.yml";

    private static Map<String, Object> merged;

    private TmsdTestConfig() {
    }

    /** 合并后的配置（base 叠加 profile）。 */
    public static synchronized Map<String, Object> merged() {
        if (merged == null) {
            merged = merge(read(BASE), read(PROFILE));
        }
        return merged;
    }

    /** 取 {@code tmsd.*} 配置项。 */
    public static String tmsd(String key) {
        return section("tmsd", key);
    }

    /** 取 {@code ontology.*} 配置项。 */
    public static String ontology(String key) {
        return section("ontology", key);
    }

    /** 零硬编码：测试用与运行时同一本体路径初始化 {@link TmsdVocabulary}。 */
    public static void initTmsdVocabulary() {
        TmsdVocabulary.init(Paths.get(ontology("main-path")));
    }

    /** 取 {@code camunda.client.*} 配置项。 */
    public static String camundaClient(String key) {
        Object camunda = merged().get("camunda");
        if (camunda instanceof Map<?, ?> c) {
            Object client = c.get("client");
            if (client instanceof Map<?, ?> cl) {
                Object v = cl.get(key);
                return v == null ? null : String.valueOf(v);
            }
        }
        return null;
    }

    private static String section(String section, String key) {
        Object s = merged().get(section);
        if (s instanceof Map<?, ?> m) {
            Object v = m.get(key);
            return v == null ? null : String.valueOf(v);
        }
        return null;
    }

    private static Map<String, Object> read(String resource) {
        try (InputStream is = TmsdTestConfig.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                throw new IllegalStateException(resource + " 必须在 classpath 上");
            }
            Map<String, Object> m = new Yaml().load(is);
            return m == null ? new LinkedHashMap<>() : m;
        } catch (Exception e) {
            throw new RuntimeException("读取 " + resource + " 失败", e);
        }
    }

    /** 深合并：profile 覆盖 base（Map 递归合并，其余直接替换）。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> out = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> e : override.entrySet()) {
            Object bv = out.get(e.getKey());
            Object ov = e.getValue();
            if (bv instanceof Map && ov instanceof Map) {
                out.put(e.getKey(), merge((Map<String, Object>) bv, (Map<String, Object>) ov));
            } else {
                out.put(e.getKey(), ov);
            }
        }
        return out;
    }
}
