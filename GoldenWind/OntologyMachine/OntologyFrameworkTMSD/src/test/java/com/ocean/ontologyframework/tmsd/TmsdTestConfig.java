package com.ocean.ontologyframework.tmsd;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TMSD 测试配置加载器。
 *
 * <p>与运行时保持一致：TMSD 的全部配置（塔架 {@code ontology.main-path} /
 * {@code ontology.bpmn-path} 与 {@code tmsd.*}）均来自唯一的 {@code application.yaml}。
 */
public final class TmsdTestConfig {

    private static final String BASE = "application.yaml";

    private static Map<String, Object> merged;

    private TmsdTestConfig() {
    }

    /** 配置（来自 application.yaml）。 */
    public static synchronized Map<String, Object> merged() {
        if (merged == null) {
            merged = read(BASE);
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
}
