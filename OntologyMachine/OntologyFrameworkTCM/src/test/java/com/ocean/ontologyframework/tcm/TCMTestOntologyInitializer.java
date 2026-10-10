package com.ocean.ontologyframework.tcm;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.OpenlletTuning;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * TCM 测试专用轻量本体初始化组件（{@code TCMJunitTest} Profile）。
 * <p>
 * 复用 JobWorker 的本体初始化逻辑（OBDAHandler + BackendService + Openllet 调优），
 * 但<b>不连接 Camunda、不做 ABox 查表加载、症状索引、复合映射等额外工作</b>。
 * <p>
 * Spring 启动时自动执行 {@link #init()}，{@link OntologyFrameworkTCMTests} 等
 * 测试类无需手动 {@code setUp()}，直接通过 {@code OBDAHandler.getInstance()} /
 * {@code BackendService.getInstance()} 使用即可。
 */
@Component
@Profile("TCMJunitTest")
public class TCMTestOntologyInitializer {

    private static final Logger log = LoggerFactory.getLogger(TCMTestOntologyInitializer.class);

    @Value("${ontology.main-path}")
    private String owlPath;

    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private String obdaPropertiesPath;

    @Value("${openllet.tuning.use-cd-classification:}")
    private String openlletUseCdClassification;

    @Value("${openllet.tuning.use-advanced-caching:}")
    private String openlletUseAdvancedCaching;

    @PostConstruct
    public void init() throws Exception {
        log.info("=== TCMTestOntologyInitializer 初始化开始 ===");

        // 1. 应用 Openllet 库级调优（与 JobWorker.createBackendService 前的调优一致）
        Properties openlletOverrides = new Properties();
        if (openlletUseCdClassification != null && !openlletUseCdClassification.isBlank()) {
            openlletOverrides.setProperty("USE_CD_CLASSIFICATION", openlletUseCdClassification.trim());
        }
        if (openlletUseAdvancedCaching != null && !openlletUseAdvancedCaching.isBlank()) {
            openlletOverrides.setProperty("USE_ADVANCED_CACHING", openlletUseAdvancedCaching.trim());
        }
        OpenlletTuning.apply(openlletOverrides);

        // 2. 初始化 OBDAHandler（与 JobWorker.createBackendService 一致）
        OBDAHandler.init(obdaPropertiesPath, obdaPath);
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        log.info("✅ OBDAHandler 初始化成功: obdaPath={}", obdaPath);

        // 3. 初始化 BackendService（SkosSynonymReader / ReasonerService 依赖它）
        try {
            BackendService.getInstance();
            log.debug("BackendService 已存在，跳过重复初始化");
        } catch (IllegalStateException e) {
            BackendService.getInstance(owlPath, obdaHandler);
            log.info("✅ BackendService 初始化成功: owlPath={}", owlPath);
        }

        log.info("=== TCMTestOntologyInitializer 初始化完成 ===");
    }
}
