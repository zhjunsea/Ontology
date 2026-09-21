package com.ocean.openlletresolver;

import openllet.core.OpenlletOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Openllet 推理器全局调优<b>配置点</b>（通用，与具体业务本体无关）。
 *
 * <p>{@link OpenlletOptions} 是 Openllet 库（{@code openllet-core}）的<b>全局静态配置</b>：
 * 一旦调用 {@link OpenlletOptions#setOptions(Properties)}，同一 JVM 内<b>之后创建的所有</b>
 * Openllet 推理器都会继承这些选项。因此本类只需在「第一个推理器创建之前」调用一次即可。
 *
 * <p><b>为什么需要它</b>：Openllet 对「含大量 {@code owl:equivalentClass} 充要定义」的本体，
 * 其 {@code CDOptimizedTaxonomyBuilder} 的 CD 优化分类路径
 * （{@code _useCD = USE_CD_CLASSIFICATION && !TOP可展开 && !hasNominal()}，
 * 见该类 {@code reset()}）会呈现<b>强非确定性耗时</b> —— 同一本体、同一输入，
 * 分类耗时可在秒级到小时级之间随机波动，偶发彻底挂起。
 * 关闭 CD 分类器即回退到通用分类算法，耗时稳定。
 *
 * <p><b>三级配置源</b>（后者覆盖前者）：
 * <ol>
 *   <li><b>内置默认</b>：本类的 {@code DEFAULT_*} 常量；</li>
 *   <li><b>配置文件</b>：classpath 资源 {@value #RESOURCE}（可选，随使用方发布）；</li>
 *   <li><b>应用参数</b>：调用方通过 {@link #apply(Properties)} 传入，
 *       并可由系统属性 {@code -Dopenllet.tuning.*} 临时覆盖（最高优先级，运维排障用）。</li>
 * </ol>
 *
 * <p><b>语义安全性</b>：这些开关都是<b>优化开关</b>，不改变推理语义 ——
 * 关闭 CD 分类只是回退到通用分类算法；关闭高级缓存只是不做模型缓存。
 * 上线前应以使用方自己的全量回归验证结果不变。
 *
 * <p><b>用法</b>：
 * <pre>{@code
 * // 在创建任何 OntologyService / ReasonerService 之前调用（幂等）
 * OpenlletTuning.apply();
 * // 或带应用参数（例如从 Spring 配置注入）
 * Properties overrides = new Properties();
 * overrides.setProperty("USE_CD_CLASSIFICATION", "false");
 * OpenlletTuning.apply(overrides);
 * }</pre>
 */
public final class OpenlletTuning {

    private static final Logger log = LoggerFactory.getLogger(OpenlletTuning.class);

    /** 第 2 级配置源：classpath 资源名。 */
    private static final String RESOURCE = "openllet-tuning.properties";
    /** 第 3 级配置源：系统属性前缀。 */
    private static final String SYS_PREFIX = "openllet.tuning.";

    /** 参与调优的 Openllet 选项键（与 {@code OpenlletOptions} 字段名一致）。 */
    private static final String KEY_USE_CD_CLASSIFICATION = "USE_CD_CLASSIFICATION";
    private static final String KEY_USE_ADVANCED_CACHING = "USE_ADVANCED_CACHING";
    private static final String[] KEYS = {KEY_USE_CD_CLASSIFICATION, KEY_USE_ADVANCED_CACHING};

    /** 第 1 级：内置默认 —— 关 CD 优化分类器（非确定性耗时的直接来源）。 */
    private static final String DEFAULT_USE_CD_CLASSIFICATION = "false";
    /** 第 1 级：内置默认 —— 关高级缓存（与关 CD 叠加后耗时收敛、方差最小）。 */
    private static final String DEFAULT_USE_ADVANCED_CACHING = "false";

    private static volatile boolean applied = false;

    private OpenlletTuning() {
    }

    /**
     * 幂等应用调优。必须在任何推理器创建之前调用。
     *
     * @param appOverrides 第 3 级应用参数（键为 Openllet 选项名，值为字符串）；
     *                     为 {@code null} 或空则跳过该级。
     */
    public static synchronized void apply(Properties appOverrides) {
        if (applied) {
            return;
        }

        Properties p = new Properties();
        // ---- 第 1 级：内置默认 ----
        p.setProperty(KEY_USE_CD_CLASSIFICATION, DEFAULT_USE_CD_CLASSIFICATION);
        p.setProperty(KEY_USE_ADVANCED_CACHING, DEFAULT_USE_ADVANCED_CACHING);

        // ---- 第 2 级：classpath 配置文件 ----
        try (InputStream in = OpenlletTuning.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                Properties fileProps = new Properties();
                fileProps.load(in);
                copyNonBlank(fileProps, p, "配置文件 " + RESOURCE);
            } else {
                log.debug("[OpenlletTuning] 未找到 {}，跳过第 2 级配置", RESOURCE);
            }
        } catch (IOException e) {
            log.warn("[OpenlletTuning] 读取 {} 失败，跳过第 2 级配置: {}", RESOURCE, e.getMessage());
        }

        // ---- 第 3 级：应用参数 ----
        if (appOverrides != null) {
            copyNonBlank(appOverrides, p, "应用参数");
        }

        // ---- 第 3 级（最高）：系统属性临时覆盖 ----
        for (String key : KEYS) {
            String v = System.getProperty(SYS_PREFIX + key);
            if (v != null && !v.isBlank()) {
                p.setProperty(key, v.trim());
                log.info("[OpenlletTuning] 系统属性覆盖 {}={}", key, v.trim());
            }
        }

        OpenlletOptions.setOptions(p);
        applied = true;

        log.info("[OpenlletTuning] 已应用 Openllet 选项: USE_CD_CLASSIFICATION={} USE_ADVANCED_CACHING={} "
                        + "(USE_CACHING={} USE_SMART_RESTORE={} ORDERED_CLASSIFICATION={})",
                OpenlletOptions.USE_CD_CLASSIFICATION,
                OpenlletOptions.USE_ADVANCED_CACHING,
                OpenlletOptions.USE_CACHING,
                OpenlletOptions.USE_SMART_RESTORE,
                OpenlletOptions.ORDERED_CLASSIFICATION);
    }

    /** 便捷重载：仅用内置默认 + 配置文件。 */
    public static void apply() {
        apply(null);
    }

    /** 是否已应用（便于测试与自检）。 */
    public static boolean isApplied() {
        return applied;
    }

    private static void copyNonBlank(Properties from, Properties to, String source) {
        for (String key : KEYS) {
            String v = from.getProperty(key);
            if (v != null && !v.isBlank()) {
                to.setProperty(key, v.trim());
                log.info("[OpenlletTuning] {} 覆盖 {}={}", source, key, v.trim());
            }
        }
    }
}
