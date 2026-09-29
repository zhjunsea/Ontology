package com.ocean.ontologyframework;

import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.OpenlletTuning;
import com.ocean.openlletresolver.QueryService;
import com.ocean.ontologyframework.tmsd.AccessoryConnectionType;
import com.ocean.ontologyframework.tmsd.ElevatorType;
import com.ocean.ontologyframework.tmsd.LayoutSpec;
import com.ocean.ontologyframework.tmsd.TmsdDesignPipeline;
import com.ocean.ontologyframework.tmsd.TmsdOntologyService;
import com.ocean.ontologyframework.tmsd.TmsdOutputWriter;
import com.ocean.ontologyframework.tmsd.TmsdVocabulary;
import com.ocean.ontologyframework.tmsd.TowerDesignEngine;
import com.ocean.ontologyframework.tmsd.TowerDesignRequest;
import com.ocean.ontologyframework.tmsd.TowerExcelReader;
import com.ocean.ontologyframework.tmsd.TowerGeometry;
import com.ocean.ontopobdahandler.OBDAHandler;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 塔架中段设计（TMSD）JobWorker。
 *
 * <p>对照 {@code TCMOntologyJobWorker} 编写：同为 {@code @Component} + {@code @Profile}
 * （本类为 {@code TMSDBPMN}），在 {@link #init()} 中按相同顺序初始化
 * {@link OpenlletTuning} → {@link OBDAHandler} → {@link BackendService} → {@link QueryService}，
 * 每个 {@link JobWorker} 以 {@code autoComplete = false} 手动完成/抛错。
 *
 * <p><b>覆盖的 9 个 jobType</b>（对应 {@code TowerMidDesign.bpmn} 的 9 个 serviceTask，完全正向 S0→S8，
 * 无匹配 / 无回退）：
 * <ul>
 *   <li>{@code tmsd-step0-parse-input} —— S0 输入解析（读取塔架主体信息表 + 项目布局表，建立设计会话）；</li>
 *   <li>{@code tmsd-step1-tube-shape} —— S1 塔筒外形与分段几何（在 S0 会话上求解全部设计变型）；</li>
 *   <li>{@code tmsd-step9-elevator} —— 步骤9 升降机设计；</li>
 *   <li>{@code tmsd-step8-accessory} —— 步骤8 附件类型替换；</li>
 *   <li>{@code tmsd-step7-diameter} —— 步骤7 直径设计；</li>
 *   <li>{@code tmsd-step6-height} —— 步骤6 高度设计；</li>
 *   <li>{@code tmsd-step5-tubesection} —— 步骤5 筒节设计（附件 + 灯）；</li>
 *   <li>{@code tmsd-step4-platform} —— 步骤4 平台设计；</li>
 *   <li>{@code tmsd-output-parameters} —— 输出「需修改的模型参数及值」txt（含多方案比较）。</li>
 * </ul>
 *
 * <p><b>状态传递</b>：设计结果较重（含各段 SectionDesign），不放入流程变量，而是按
 * {@code processInstanceKey} 缓存在 {@link #sessions}；步骤间只传递轻型标量/摘要。
 */
@Component
@Profile("TMSDBPMN")
public class TMSDOntologyJobWorker {

    private static final Logger log = LoggerFactory.getLogger(TMSDOntologyJobWorker.class);

    // ---- 本体与 OBDA（与 TCMOntologyJobWorker 同源配置项） ----
    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    @Value("${ontology.obda-path:}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path:}")
    private String obdaPropertiesPath;

    @Value("${openllet.tuning.use-cd-classification:}")
    private String openlletUseCdClassification;

    @Value("${openllet.tuning.use-advanced-caching:}")
    private String openlletUseAdvancedCaching;

    // ---- 塔架中段设计专有配置 ----
    @Value("${tmsd.historical-geo-path}")
    private String historicalGeoPath;

    @Value("${tmsd.historical-layout-path}")
    private String historicalLayoutPath;

    @Value("${tmsd.output-dir}")
    private String outputDir;

    private BackendService backendService;
    private QueryService queryService;
    private TmsdOntologyService ontologyService;

    /** 按流程实例缓存设计会话（步骤间共享）。 */
    private final Map<Long, DesignSession> sessions = new ConcurrentHashMap<>();

    /** 一次设计会话：输入 + 完整求解结果（{@code result} 在 S1 求解后回填）。 */
    private static final class DesignSession {
        final TowerDesignRequest request;
        TmsdDesignPipeline.CaseResult result;

        DesignSession(TowerDesignRequest request) {
            this(request, null);
        }

        DesignSession(TowerDesignRequest request, TmsdDesignPipeline.CaseResult result) {
            this.request = request;
            this.result = result;
        }
    }

    // ==================== 初始化 ====================

    @PostConstruct
    public void init() {
        try {
            log.info("==================== TMSD 初始化开始 ====================");

            // 顺序与 TCMOntologyJobWorker 保持一致：先调 Openllet 库级选项，再建 BackendService。
            Properties openlletOverrides = new Properties();
            if (openlletUseCdClassification != null && !openlletUseCdClassification.isBlank()) {
                openlletOverrides.setProperty("USE_CD_CLASSIFICATION", openlletUseCdClassification.trim());
            }
            if (openlletUseAdvancedCaching != null && !openlletUseAdvancedCaching.isBlank()) {
                openlletOverrides.setProperty("USE_ADVANCED_CACHING", openlletUseAdvancedCaching.trim());
            }
            OpenlletTuning.apply(openlletOverrides);

            // 塔架中段本体（TowerMidSection.owl）是纯 TBox，无 ABox 数据库映射，
            // 故仅当配置了 OBDA 映射时才初始化 OBDAHandler；否则直接构建 BackendService
            // （其无库构造函数仍会取 OBDAHandler 单例，但本应用从不发起 OBDA 查询）。
            if (obdaPath != null && !obdaPath.isBlank()
                    && obdaPropertiesPath != null && !obdaPropertiesPath.isBlank()) {
                OBDAHandler.init(obdaPropertiesPath, obdaPath);
                backendService = BackendService.getInstance(mainOntologyPath, OBDAHandler.getInstance());
                log.info("[init] 已按配置初始化 OBDA 映射：{}", obdaPath);
            } else {
                backendService = BackendService.getInstance(mainOntologyPath);
                log.info("[init] 未配置 OBDA 映射，按纯 TBox 模式加载本体");
            }
            queryService = new QueryService(backendService);
            ontologyService = new TmsdOntologyService(backendService);

            // 零硬编码：启动时解析本体一次并缓存，供引擎/校验/输出统一取值。
            TmsdVocabulary.init(Paths.get(mainOntologyPath));
            log.info("[init] 已解析本体约束：version={} 数值约束={} 条 基数约束={} 条",
                    TmsdVocabulary.ontologyVersion(),
                    TmsdVocabulary.numericConstraints().size(),
                    TmsdVocabulary.cardinalityConstraints().size());

            log.info("==================== TMSD 初始化完成 ====================");
        } catch (Exception e) {
            log.error("TMSD 初始化失败（本体/OBDA 环境不可用）：{}", e.getMessage(), e);
        }
    }

    // ==================== 步骤0 ====================

    /**
     * S0 输入解析：读取「塔架主体信息表 + 项目布局表」，建立设计会话（供后续 S1~S8 共享）。
     *
     * <p>输入变量（均可选，缺省用历史设计几何）：
     * {@code caseName} / {@code caseIndex} / {@code geometryPath} / {@code layoutPath} /
     * {@code model} / {@code elevatorType} / {@code region} / {@code connectionType}。
     *
     * <p>输出变量：{@code caseName} / {@code model} / {@code elevatorType} / {@code region} /
     * {@code connectionType} / {@code middleSectionNumbers}。
     */
    @JobWorker(type = "tmsd-step0-parse-input", autoComplete = false)
    public void handleStep0ParseInput(final ActivatedJob job, final JobClient client) {
        try {
            long key = job.getProcessInstanceKey();
            Map<String, Object> vars = job.getVariablesAsMap();
            TowerDesignRequest req = resolveRequest(vars);
            sessions.put(key, new DesignSession(req));

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("caseName", req.caseName());
            out.put("model", req.model());
            out.put("elevatorType", req.elevatorType().label());
            out.put("region", req.region());
            out.put("connectionType", req.connectionType().label());
            out.put("middleSectionNumbers", new ArrayList<>(req.middleSectionNumbers()));

            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("[S0] {} 输入解析完成：机型={} 升降机={} 区域={} 连接={} 中段={}",
                    req.caseName(), req.model(), req.elevatorType().label(), req.region(),
                    req.connectionType().label(), req.middleSectionNumbers());
        } catch (Exception e) {
            log.error("tmsd-step0-parse-input 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TMSD_STEP0_FAILED").errorMessage(String.valueOf(e.getMessage())).send().join();
        }
    }

    // ==================== 步骤1 ====================

    /**
     * S1 塔筒外形与分段几何：基于 S0 建立的设计会话求解全部设计变型，回填并缓存结果（供 S2~S8 共享）。
     *
     * <p>输出变量：{@code caseName} / {@code middleSectionNumbers} / {@code variantCount} /
     * {@code satisfiedCount}。
     */
    @JobWorker(type = "tmsd-step1-tube-shape", autoComplete = false)
    public void handleStep1TubeShape(final ActivatedJob job, final JobClient client) {
        try {
            long key = job.getProcessInstanceKey();
            DesignSession session = sessions.get(key);
            if (session == null) {
                throw new IllegalStateException("设计会话丢失（S0 输入解析未执行，key=" + key + "）");
            }
            TowerDesignRequest req = session.request;
            TmsdDesignPipeline.CaseResult result = TmsdDesignPipeline.design(req);
            session.result = result;

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("caseName", req.caseName());
            out.put("middleSectionNumbers", new ArrayList<>(req.middleSectionNumbers()));
            out.put("variantCount", result.variants().size());
            out.put("satisfiedCount", result.satisfied().size());

            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("[S1] {} 塔筒外形与分段几何完成：满足方案 {}/{}", req.caseName(),
                    result.satisfied().size(), result.variants().size());
        } catch (Exception e) {
            log.error("tmsd-step1-tube-shape 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TMSD_STEP1_FAILED").errorMessage(String.valueOf(e.getMessage())).send().join();
        }
    }

    // ==================== 步骤9 ====================

    /** 步骤9 升降机设计：升降机固定钢绳导向 ⇒ 必有扶持；H_SUPPORT 直读布局表 (12,n)。 */
    @JobWorker(type = "tmsd-step9-elevator", autoComplete = false)
    public void handleStep9Elevator(final ActivatedJob job, final JobClient client) {
        completeStep(job, client, "tmsd-step9-elevator", "步骤9 升降机设计", (sd, req) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", sd.sectionNo());
            m.put("elevatorType", req.elevatorType().label());
            m.put("elevatorClass", req.elevatorType().classIri());
            m.put("hasSupport", req.elevatorType().hasSupport());
            m.put("supportHeight", sd.supportHeight());
            m.put("supportToWeldDistance", sd.supportToWeldDistance());
            return m;
        });
    }

    // ==================== 步骤8 ====================

    /** 步骤8 附件类型替换：按连接方式选型。 */
    @JobWorker(type = "tmsd-step8-accessory", autoComplete = false)
    public void handleStep8Accessory(final ActivatedJob job, final JobClient client) {
        completeStep(job, client, "tmsd-step8-accessory", "步骤8 附件类型替换", (sd, req) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", sd.sectionNo());
            m.put("accessoryType", sd.accessoryType());
            m.put("connectionType", sd.connectionType());
            return m;
        });
    }

    // ==================== 步骤7 ====================

    /** 步骤7 直径设计：电缆托架按机型映射。 */
    @JobWorker(type = "tmsd-step7-diameter", autoComplete = false)
    public void handleStep7Diameter(final ActivatedJob job, final JobClient client) {
        completeStep(job, client, "tmsd-step7-diameter", "步骤7 直径设计", (sd, req) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", sd.sectionNo());
            m.put("bracketLength", sd.bracketLength());
            m.put("bracketRightChord", sd.bracketRightChord());
            m.put("bracketLeftChord", sd.bracketLeftChord());
            return m;
        });
    }

    // ==================== 步骤6 ====================

    /** 步骤6 高度设计：爬梯 / 线槽长度 = 筒段总高。 */
    @JobWorker(type = "tmsd-step6-height", autoComplete = false)
    public void handleStep6Height(final ActivatedJob job, final JobClient client) {
        completeStep(job, client, "tmsd-step6-height", "步骤6 高度设计", (sd, req) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", sd.sectionNo());
            m.put("ladderLength", sd.ladderLength());
            m.put("trayLength", sd.trayLength());
            return m;
        });
    }

    // ==================== 步骤5 ====================

    /** 步骤5 筒节设计：附件排布 + 灯布置。 */
    @JobWorker(type = "tmsd-step5-tubesection", autoComplete = false)
    public void handleStep5TubeSection(final ActivatedJob job, final JobClient client) {
        completeStep(job, client, "tmsd-step5-tubesection", "步骤5 筒节设计", (sd, req) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", sd.sectionNo());
            m.put("accessoryHeights", new ArrayList<>(sd.accessoryHeights()));
            m.put("accessoryCount", sd.accessoryCount());
            m.put("accessorySpacing", sd.accessorySpacing());
            m.put("firstAccessoryToBottom", sd.firstAccessoryToBottom());
            m.put("secondLastToPlatform", sd.secondLastToPlatform());
            m.put("accessoryToWeldDistance", sd.minAccessoryToWeldDistance());
            m.put("cableClampHeights", new ArrayList<>(sd.cableClampHeights()));
            m.put("lastBracketToPlatform", sd.lastBracketToPlatform());
            m.put("lightHeights", new ArrayList<>(sd.lightHeights()));
            m.put("lightType", sd.lightType());
            m.put("firstLightHeight", sd.firstLightHeight());
            m.put("lightStudSpacing", sd.lightStudSpacing());
            m.put("minLightStudToWeldDistance", sd.minLightStudToWeldDistance());
            return m;
        });
    }

    // ==================== 步骤4 ====================

    /** 步骤4 平台设计：平台到筒顶 1250mm。 */
    @JobWorker(type = "tmsd-step4-platform", autoComplete = false)
    public void handleStep4Platform(final ActivatedJob job, final JobClient client) {
        completeStep(job, client, "tmsd-step4-platform", "步骤4 平台设计", (sd, req) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("section", sd.sectionNo());
            m.put("platformDistance", sd.platformDistance());
            m.put("platformHeight", sd.platformHeight());
            m.put("platformInnerDiameter", sd.platformInnerDiameter());
            return m;
        });
    }

    // ==================== 输出 ====================

    /**
     * 输出「需修改的模型参数及值」：对全部满足约束的方案写 txt（GBK）+ 方案对比报告，
     * 并对推荐方案做本体一致性校验。
     */
    @JobWorker(type = "tmsd-output-parameters", autoComplete = false)
    public void handleOutputParameters(final ActivatedJob job, final JobClient client) {
        try {
            long key = job.getProcessInstanceKey();
            DesignSession session = sessions.get(key);
            if (session == null) {
                throw new IllegalStateException("设计会话丢失（key=" + key + "）");
            }
            TmsdDesignPipeline.CaseResult result = session.result;

            List<Path> files = TmsdOutputWriter.write(result, Paths.get(outputDir));
            List<String> fileNames = new ArrayList<>();
            for (Path p : files) {
                fileNames.add(p.toString());
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("caseName", result.caseName());
            out.put("satisfiedCount", result.satisfied().size());
            out.put("rejectedCount", result.rejected().size());
            TmsdDesignPipeline.VariantResult rec = result.recommended();
            out.put("recommendedVariant", rec == null ? null : rec.variantName());
            out.put("outputFiles", fileNames);
            out.put("outputDir", outputDir);

            // 本体一致性校验（推荐方案，逐段；环境不可用则跳过并记录）
            if (ontologyService != null && rec != null) {
                List<String> reports = new ArrayList<>();
                boolean allConsistent = true;
                for (Map.Entry<Integer, TowerDesignEngine.SectionDesign> e : rec.sections().entrySet()) {
                    TmsdOntologyService.OntologyReport r = ontologyService.validate(
                            "TMSD_" + key + "_S" + e.getKey(), session.request, e.getValue());
                    allConsistent &= r.consistent();
                    reports.add(r.summary());
                }
                out.put("ontologyConsistent", allConsistent);
                out.put("ontologyReports", reports);
            }

            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("[输出] {} 写出 {} 个文件（满足方案 {} / {}），目录={}",
                    result.caseName(), files.size(), result.satisfied().size(),
                    result.variants().size(), outputDir);
        } catch (Exception e) {
            log.error("tmsd-output-parameters 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TMSD_OUTPUT_FAILED").errorMessage(String.valueOf(e.getMessage())).send().join();
        }
    }

    // ==================== 通用步骤处理 ====================

    /** 段设计 + 设计输入 → 输出映射。 */
    private interface SectionMapper {
        Map<String, Object> map(TowerDesignEngine.SectionDesign sd, TowerDesignRequest req);
    }

    /**
     * 通用步骤：从会话取推荐方案，对每个已执行中段产出该步骤的输出，写入流程变量。
     */
    private void completeStep(final ActivatedJob job, final JobClient client,
                              String jobType, String stepLabel, SectionMapper mapper) {
        try {
            long key = job.getProcessInstanceKey();
            DesignSession session = sessions.get(key);
            if (session == null) {
                throw new IllegalStateException("设计会话丢失（key=" + key + "）");
            }
            TmsdDesignPipeline.VariantResult rec = session.result.recommended();
            List<Map<String, Object>> sections = new ArrayList<>();
            if (rec != null) {
                for (Map.Entry<Integer, TowerDesignEngine.SectionDesign> e : rec.sections().entrySet()) {
                    sections.add(mapper.map(e.getValue(), session.request));
                }
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("step", stepLabel);
            out.put("variantName", rec == null ? null : rec.variantName());
            out.put("sections", sections);

            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("[{}] {} 段（方案={}）", stepLabel, sections.size(),
                    rec == null ? "无" : rec.variantName());
        } catch (Exception e) {
            log.error("{} 失败", jobType, e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TMSD_STEP_FAILED").errorMessage(String.valueOf(e.getMessage())).send().join();
        }
    }

    // ==================== 输入解析 ====================

    /** 固定项（设计流程 §1.3）：升降机 = 钢绳导向；区域 = 中国；附件连接方式 = 焊接。 */
    private static final ElevatorType FIXED_ELEVATOR = ElevatorType.ROPE_GUIDED;
    private static final String FIXED_REGION = "中国";
    private static final AccessoryConnectionType FIXED_CONNECTION = AccessoryConnectionType.WELDED;

    /** caseIndex 缺省样例的机型轮换（覆盖 V12/V15/V17/V19）。 */
    private static final String[] SAMPLE_MODELS = {"V12", "V15", "V17", "V19"};

    /**
     * 由流程变量解析设计输入（完全正向，不依赖历史设计）。
     *
     * <p>输入变量（均可选）：{@code caseName} / {@code caseIndex} / {@code geometryPath} /
     * {@code layoutPath} / {@code model} / {@code elevatorType} / {@code region} / {@code connectionType}。
     * 升降机 / 区域 / 连接方式缺省取固定项；机型缺省取布局表或主体信息表。
     */
    private TowerDesignRequest resolveRequest(Map<String, Object> vars) {
        String caseName = strOf(vars.get("caseName"));
        int caseIndex = intOf(vars.get("caseIndex"), -1);

        if (caseIndex >= 0) {
            TowerGeometry geometry = readGeometry(Paths.get(historicalGeoPath));
            LayoutSpec layout = readLayout(Paths.get(historicalLayoutPath));
            String model = SAMPLE_MODELS[Math.floorMod(caseIndex, SAMPLE_MODELS.length)];
            return new TowerDesignRequest(orDefault(caseName, "样例" + caseIndex), geometry,
                    model, FIXED_ELEVATOR, FIXED_REGION, FIXED_CONNECTION, layout);
        }

        String geoPath = strOf(vars.get("geometryPath"));
        String layPath = strOf(vars.get("layoutPath"));
        if (geoPath == null) {
            throw new IllegalArgumentException("缺少输入：geometryPath（或 caseIndex）");
        }
        TowerGeometry geometry = readGeometry(Paths.get(geoPath));
        LayoutSpec layout = readLayout(Paths.get(layPath != null ? layPath : historicalLayoutPath));
        String model = orDefault(strOf(vars.get("model")),
                layout.model() != null ? layout.model() : geometry.description().productCode());
        ElevatorType elevator = ElevatorType.of(orDefault(strOf(vars.get("elevatorType")),
                FIXED_ELEVATOR.name()));
        String region = orDefault(strOf(vars.get("region")), FIXED_REGION);
        AccessoryConnectionType conn = AccessoryConnectionType.of(
                orDefault(strOf(vars.get("connectionType")), FIXED_CONNECTION.name()));
        return new TowerDesignRequest(orDefault(caseName, "自定义输入"), geometry,
                model, elevator, region, conn, layout);
    }

    private static TowerGeometry readGeometry(Path path) {
        try {
            return TowerExcelReader.readGeometry(path);
        } catch (Exception e) {
            throw new RuntimeException("读取主体信息表失败: " + path + "：" + e.getMessage(), e);
        }
    }

    private static LayoutSpec readLayout(Path path) {
        try {
            return TowerExcelReader.readLayout(path);
        } catch (Exception e) {
            throw new RuntimeException("读取项目布局表失败: " + path + "：" + e.getMessage(), e);
        }
    }

    // ==================== 工具 ====================

    private static String strOf(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static int intOf(Object v, int def) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v != null) {
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException ignored) {
                // fallthrough
            }
        }
        return def;
    }

    private static String orDefault(String v, String def) {
        return v != null ? v : def;
    }
}
