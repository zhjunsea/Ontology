package com.ocean.ontologyframework.sizhubazi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本体/映射/流程等「文件型」资源的路径解析工具：把配置中的（可能相对的）路径解析为
 * 「与进程工作目录（CWD）无关」的绝对路径。
 *
 * <p><b>背景</b>：{@code application.yaml} 中的 {@code ontology.main-path / abox-path / obda-path /
 * obda-properties-path / bpmn-path} 均为相对路径（约定以模块根 {@code OntologyFrameworkSiZhuBaZi/}
 * 为基准）。但 {@code OntologyService}（{@code new File(mainPath)}）、{@code OBDAHandler}
 * （{@code new File(obdaPath)} 交由 Ontop 读取）、{@code ProcessOrchestrator}（{@code Path.of(bpmnPath)}
 * + {@code Files.readString}）均按**进程 CWD** 解析相对路径——一旦应用以非模块根目录启动即失败
 * （如「OBDA 映射文件解析失败」「本体文件不存在」「BPMN 找不到」）。本工具统一消除该 CWD 依赖。
 *
 * <p><b>解析顺序</b>：①原样（含绝对路径、或相对当前目录已存在）→ 返回绝对化结果；
 * ②自当前工作目录逐级向上最多 {@link #MAX_UP} 层查找 {@code p}；③仍找不到则原样返回
 * （保留原始路径与既有报错信息，便于定位问题）。全过程**不含任何硬编码路径/表名**。
 */
public final class OntologyPathResolver {

    private static final Logger log = LoggerFactory.getLogger(OntologyPathResolver.class);

    /** 自 CWD 向上查找的最大层级（覆盖「以子目录为 CWD 启动」的常见场景）。 */
    private static final int MAX_UP = 6;

    private OntologyPathResolver() {
    }

    /**
     * 将（可能相对的）资源路径解析为绝对路径；失败则原样返回（不抛异常、不改变既有行为）。
     */
    public static String resolve(String p) {
        if (p == null || p.isBlank()) return p;
        try {
            Path direct = Path.of(p);
            if (Files.exists(direct)) {
                return direct.toAbsolutePath().normalize().toString();
            }
            if (direct.isAbsolute()) return p;
            Path cur = Path.of("").toAbsolutePath();
            for (int i = 0; i < MAX_UP && cur != null; i++) {
                Path cand = cur.resolve(p);
                if (Files.exists(cand)) {
                    String resolved = cand.normalize().toString();
                    log.info("📎 资源路径已按 CWD 无关方式解析 | 原始={} → 解析={}", p, resolved);
                    return resolved;
                }
                cur = cur.getParent();
            }
        } catch (Exception ignore) {
            // 解析异常时降级为原样返回，不改变既有行为
        }
        return p;
    }
}
