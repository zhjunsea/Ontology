package com.ocean.ontologyframework.tmsd;

import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.OpenlletTuning;
import com.ocean.openlletresolver.QueryService;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.utilities.OntologyWorkerSupport;

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
 * <p>{@code @Component} + {@code @Profile}（本类为 {@code TMSDBPMN}），
 * 在 {@link #init()} 中按顺序初始化
 * {@link OpenlletTuning} → {@link OBDAHandler} → {@link BackendService} → {@link QueryService}，
 * 每个 {@link JobWorker} 以 {@code autoComplete = false} 手动完成/抛错。
 *
 * <p><b>覆盖的 9 个 jobType</b>（对应 {@code TowerMidDesign.bpmn} 的 9 个 serviceTask，完全正向 S0→S8，
 * 无匹配 / 无回退）：
 * <ul>
 *   <li>{@code tmsd-step0-parse-input} —— S0 输入解析（读取塔架主体信息表 + 项目布局表，建立设计会话）；</li>
 *   <li>{@code tmsd-step1-tube-shape} —— S1 塔筒外形与分段几何（建立各段设计对象并计算几何量，不判定约束）；</li>
 *   <li>{@code tmsd-step9-elevator} —— 步骤9 升降机设计；</li>
 *   <li>{@code tmsd-step8-accessory} —— 步骤8 附件类型替换；</li>
 *   <li>{@code tmsd-step7-diameter} —— 步骤7 直径设计；</li>
 *   <li>{@code tmsd-step6-height} —— 步骤6 高度设计；</li>
 *   <li>{@code tmsd-step5-tubesection} —— 步骤5 筒节设计（附件 + 灯）；</li>
 *   <li>{@code tmsd-step4-platform} —— 步骤4 平台设计；</li>
 *   <li>{@code tmsd-output-parameters} —— 输出「需修改的模型参数及值」txt（含多方案比较）。</li>
 * </ul>
 *
 * <p><b>状态传递</b>：设计结果较重（含各段 {@link TowerDesignEngine.SectionDesign}），不放入流程变量，
 * 而是按 {@code processInstanceKey} 缓存在 {@link #sessions}；S1 建立各段设计对象后，S2~S7
 * <b>逐步</b>写入本环节字段（逐步累积），步骤间只传递轻型标量/摘要；S8 输出步汇总参数并做本体校验。
 */
@Component
@Profile("TMSDBPMN")
public class TMSDOntologyJobWorker extends OntologyWorkerSupport {

    private static final Logger log = LoggerFactory.getLogger(TMSDOntologyJobWorker.class);

    // ---- 本体与 OBDA ----
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

    private TmsdOntologyService ontologyService;

    /** 按流程实例缓存设计会话（步骤间共享）。 */
    private final Map<Long, DesignSession> sessions = new ConcurrentHashMap<>();

    /**
     * 一次设计会话：输入 + <b>逐步累积</b>的段设计。
     *
     * <p>S0 建立会话；S1 建立各段 {@link TowerDesignEngine.SectionDesign} 并填几何；
     * S2~S7 逐段写入本环节字段；S8 汇总参数并做本体校验。
     */
    private static final class DesignSession {
        final TowerDesignRequest request;
        final Map<Integer, TowerDesignEngine.SectionDesign> sections = new LinkedHashMap<>();

        /** 每段当前附件候选序号（S8a 判定不合格则前进一档；回退外置到 BPMN 循环）。 */
        final Map<Integer, Integer> candidateIndex = new LinkedHashMap<>();

        /** 已通过 S8a 判定的段（冻结，后续 S6 不再重算）。 */
        final java.util.Set<Integer> frozen = new java.util.LinkedHashSet<>();

        /** 候选档位已用尽仍未合格的段。 */
        final java.util.Set<Integer> exhausted = new java.util.LinkedHashSet<>();

        DesignSession(TowerDesignRequest request) {
            this.request = request;
        }
    }

    // ==================== 初始化 ====================

    @PostConstruct
    public void init() {
        try {
            log.info("==================== TMSD 初始化开始 ====================");
            initOntologyPipeline();
            log.info("==================== TMSD 初始化完成 ====================");
        } catch (Exception e) {
            log.error("TMSD 初始化失败（本体/OBDA 环境不可用）：{}", e.getMessage(), e);
        }
    }

    /** 先调 Openllet 库级选项，再建 BackendService。 */
    @Override
    protected void applyOpenlletTuning() {
        Properties openlletOverrides = new Properties();
        if (openlletUseCdClassification != null && !openlletUseCdClassification.isBlank()) {
            openlletOverrides.setProperty("USE_CD_CLASSIFICATION", openlletUseCdClassification.trim());
        }
        if (openlletUseAdvancedCaching != null && !openlletUseAdvancedCaching.isBlank()) {
            openlletOverrides.setProperty("USE_ADVANCED_CACHING", openlletUseAdvancedCaching.trim());
        }
        OpenlletTuning.apply(openlletOverrides);
    }

    @Override
    protected BackendService createBackendService() throws Exception {
        // 塔架中段本体（TowerMidSection.owl）是纯 TBox，无 ABox 数据库映射，
        // 故仅当配置了 OBDA 映射时才初始化 OBDAHandler；否则直接构建 BackendService
        // （其无库构造函数仍会取 OBDAHandler 单例，但本应用从不发起 OBDA 查询）。
        if (obdaPath != null && !obdaPath.isBlank()
                && obdaPropertiesPath != null && !obdaPropertiesPath.isBlank()) {
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
            BackendService service = BackendService.getInstance(mainOntologyPath, OBDAHandler.getInstance());
            log.info("[init] 已按配置初始化 OBDA 映射：{}", obdaPath);
            return service;
        }
        BackendService service = BackendService.getInstance(mainOntologyPath);
        log.info("[init] 未配置 OBDA 映射，按纯 TBox 模式加载本体");
        return service;
    }

    @Override
    protected void afterBackendServiceReady() {
        ontologyService = new TmsdOntologyService(backendService);

        // 零硬编码：启动时解析本体一次并缓存，供引擎/校验/输出统一取值。
        TmsdVocabulary.init(Paths.get(mainOntologyPath));
        log.info("[init] 已解析本体约束：version={} 数值约束={} 条 基数约束={} 条",
                TmsdVocabulary.ontologyVersion(),
                TmsdVocabulary.numericConstraints().size(),
                TmsdVocabulary.cardinalityConstraints().size());
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
     * S1 塔筒外形与分段几何：建立各段设计对象并只求本环节几何量（段总高 / 锥角 / 上下法兰），
     * 不做任何约束判定（判定统一在 S8 输出步）。
     *
     * <p>输出变量：{@code caseName} / {@code middleSectionNumbers} / {@code sectionCount}。
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
            TowerGeometry geo = req.geometry();
            LayoutSpec lay = req.layoutReference();
            for (int s : req.middleSectionNumbers()) {
                TowerDesignEngine.SectionDesign sd = TowerDesignEngine.newSectionDesign(s);
                TowerDesignEngine.step1TubeShape(sd, geo, s - 1, lay);
                session.sections.put(s, sd);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("caseName", req.caseName());
            out.put("middleSectionNumbers", new ArrayList<>(req.middleSectionNumbers()));
            out.put("sectionCount", session.sections.size());

            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("[S1] {} 塔筒外形与分段几何完成：{} 段", req.caseName(), session.sections.size());
        } catch (Exception e) {
            log.error("tmsd-step1-tube-shape 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TMSD_STEP1_FAILED").errorMessage(String.valueOf(e.getMessage())).send().join();
        }
    }

    /**
     * 构造本体逐项约束判定器：把一段设计的各项实测值物化进本体（净距两两复核个体 + 逐项校验个体），
     * 回读 OWL 等价类（{@code :约束违规} / {@code :无附件段} / 各「合规类」）结论；
     * 某段回读失败自动退回 Java 兜底（{@link TmsdDesignPipeline#javaVerdicts}），
     * 保证生产与离线路径输出一致。
     */
    private TmsdDesignPipeline.ConstraintJudge constraintJudge(long processKey) {
        return (req, sd) -> {
            TmsdDesignPipeline.ConstraintVerdicts v = ontologyService.assessAll(
                    "TMSD_" + processKey + "_S" + sd.sectionNo(), req, sd);
            return v != null ? v : TmsdDesignPipeline.javaVerdicts(req, sd);
        };
    }

    // ==================== S2~S7 设计步骤 ====================

    /** S2 升降机设计：升降机固定钢绳导向 ⇒ 必有扶持；H_SUPPORT 直读布局表 (13,n)。 */
    @JobWorker(type = "tmsd-step9-elevator", autoComplete = false)
    public void handleStep9Elevator(final ActivatedJob job, final JobClient client) {
        runStep(job, client, "tmsd-step9-elevator", "S2 升降机设计",
                (sd, session) -> TowerDesignEngine.step2Elevator(sd, session.request.geometry(),
                        sd.sectionNo() - 1, session.request.layoutReference()),
                (sd, req) -> {
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

    /** S3 配件类型替换：按连接方式选型。 */
    @JobWorker(type = "tmsd-step8-accessory", autoComplete = false)
    public void handleStep8Accessory(final ActivatedJob job, final JobClient client) {
        runStep(job, client, "tmsd-step8-accessory", "S3 配件类型替换",
                (sd, session) -> TowerDesignEngine.step3Accessory(sd, session.request),
                (sd, req) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("section", sd.sectionNo());
                    m.put("accessoryType", sd.accessoryType());
                    m.put("connectionType", sd.connectionType());
                    return m;
                });
    }

    /** S4 直径设计：电缆托架按机型映射。 */
    @JobWorker(type = "tmsd-step7-diameter", autoComplete = false)
    public void handleStep7Diameter(final ActivatedJob job, final JobClient client) {
        runStep(job, client, "tmsd-step7-diameter", "S4 直径设计",
                (sd, session) -> TowerDesignEngine.step4Diameter(sd, session.request),
                (sd, req) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("section", sd.sectionNo());
                    m.put("bracketLength", sd.bracketLength());
                    m.put("bracketRightChord", sd.bracketRightChord());
                    m.put("bracketLeftChord", sd.bracketLeftChord());
                    return m;
                });
    }

    /** S5 高度设计：爬梯 / 线槽长度 = 筒段总高。 */
    @JobWorker(type = "tmsd-step6-height", autoComplete = false)
    public void handleStep6Height(final ActivatedJob job, final JobClient client) {
        runStep(job, client, "tmsd-step6-height", "S5 高度设计",
                (sd, session) -> TowerDesignEngine.step5Height(sd, session.request.layoutReference()),
                (sd, req) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("section", sd.sectionNo());
                    m.put("ladderLength", sd.ladderLength());
                    m.put("trayLength", sd.trayLength());
                    return m;
                });
    }

    /** S7 平台设计：平台到筒顶 1250mm（BPMN 中先于筒节执行）。 */
    @JobWorker(type = "tmsd-step4-platform", autoComplete = false)
    public void handleStep4Platform(final ActivatedJob job, final JobClient client) {
        runStep(job, client, "tmsd-step4-platform", "S7 平台设计",
                (sd, session) -> TowerDesignEngine.step7Platform(sd, session.request.geometry(),
                        sd.sectionNo() - 1, session.request.layoutReference()),
                (sd, req) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("section", sd.sectionNo());
                    m.put("platformDistance", sd.platformDistance());
                    m.put("platformHeight", sd.platformHeight());
                    m.put("platformInnerDiameter", sd.platformInnerDiameter());
                    return m;
                });
    }

    /** S6 筒节设计：附件排布 + 电缆线夹 + 灯布置（依赖 S7 平台高度）。 */
    @JobWorker(type = "tmsd-step5-tubesection", autoComplete = false)
    public void handleStep5TubeSection(final ActivatedJob job, final JobClient client) {
        runStep(job, client, "tmsd-step5-tubesection", "S6 筒节设计",
                (sd, session) -> {
                    if (session.frozen.contains(sd.sectionNo())) {
                        return;
                    }
                    int idx = session.candidateIndex.getOrDefault(sd.sectionNo(), 0);
                    TowerDesignEngine.step6TubeSection(sd, session.request.geometry(),
                            sd.sectionNo() - 1, session.request.layoutReference(), idx);
                },
                (sd, req) -> {
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

    // ==================== 输出 ====================

    /**
     * S8 输出参数表：先汇总各段 Creo 参数，再对「全部段设计」做本体约束校验（净距回读）并组装
     * 求解结果，写出 txt（GBK）+ 方案对比报告，并对推荐方案逐段做本体一致性校验。
     *
     * <p>“跑完整条流程、最后再验证”：净距判定 / 满足度 / 推荐方案在本步一次算出。
     */
    @JobWorker(type = "tmsd-output-parameters", autoComplete = false)
    public void handleOutputParameters(final ActivatedJob job, final JobClient client) {
        try {
            long key = job.getProcessInstanceKey();
            DesignSession session = sessions.get(key);
            if (session == null) {
                throw new IllegalStateException("设计会话丢失（key=" + key + "）");
            }
            TowerDesignRequest req = session.request;
            TowerGeometry geo = req.geometry();
            LayoutSpec lay = req.layoutReference();
            for (TowerDesignEngine.SectionDesign sd : session.sections.values()) {
                TowerDesignEngine.assembleParameters(sd, geo, sd.sectionNo() - 1, req, lay);
            }

            TowerDesignEngine.DesignVariant variant = TowerDesignEngine.variants().get(0);
            TmsdDesignPipeline.VariantResult vr = TmsdDesignPipeline.evaluate(
                    req, variant, session.sections, constraintJudge(key));
            TmsdDesignPipeline.CaseResult result = TmsdDesignPipeline.assemble(req, List.of(vr));

            List<Path> files = TmsdOutputWriter.write(result, Paths.get(outputDir));
            List<String> fileNames = new ArrayList<>();
            for (Path p : files) {
                fileNames.add(p.toString());
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("caseName", result.caseName());
            out.put("variantCount", result.variants().size());
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
                            "TMSD_" + key + "_S" + e.getKey(), req, e.getValue());
                    allConsistent &= r.consistent();
                    reports.add(r.summary());
                }
                out.put("ontologyConsistent", allConsistent);
                out.put("ontologyReports", reports);
            }

            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("[S8] {} 写出 {} 个文件（满足方案 {} / {}），目录={}",
                    result.caseName(), files.size(), result.satisfied().size(),
                    result.variants().size(), outputDir);
        } catch (Exception e) {
            log.error("tmsd-output-parameters 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TMSD_OUTPUT_FAILED").errorMessage(String.valueOf(e.getMessage())).send().join();
        }
    }

    // ==================== S8a 本体判定（回退驱动） ====================

    /**
     * S8a 本体判定轮：对未冻结段用 Openllet 判定（净距回读 + 逐条约束）；合格段冻结，
     * 不合格段候选前进一档（用尽则标记未定）。当全部段「合格冻结或候选用尽」时置
     * {@code judgeDone=true}，驱动 BPMN 网关退出循环到 S8b；否则由网关回到 S6 重新设计
     * （回退外置——S6 内部不再回溯）。
     */
    @JobWorker(type = "tmsd-step8a-judge", autoComplete = false)
    public void handleJudgeRound(final ActivatedJob job, final JobClient client) {
        try {
            long key = job.getProcessInstanceKey();
            DesignSession session = sessions.get(key);
            if (session == null) {
                throw new IllegalStateException("设计会话丢失（key=" + key + "）");
            }
            TowerDesignRequest req = session.request;
            TmsdDesignPipeline.ConstraintJudge judge = constraintJudge(key);

            List<Map<String, Object>> verdicts = new ArrayList<>();
            for (Map.Entry<Integer, TowerDesignEngine.SectionDesign> e : session.sections.entrySet()) {
                int s = e.getKey();
                TowerDesignEngine.SectionDesign sd = e.getValue();
                if (session.frozen.contains(s)) {
                    verdicts.add(verdict(s, "合格（已冻结）", session));
                    continue;
                }
                if (session.exhausted.contains(s)) {
                    verdicts.add(verdict(s, "候选已用尽（未定）", session));
                    continue;
                }
                TmsdDesignPipeline.ConstraintVerdicts v = judge.assess(req, sd);
                List<TmsdDesignPipeline.ConstraintCheck> checks = TmsdDesignPipeline.check(sd, req, v);
                boolean pass = checks.stream().allMatch(TmsdDesignPipeline.ConstraintCheck::pass);
                if (pass) {
                    session.frozen.add(s);
                    verdicts.add(verdict(s, "合格（冻结）", session));
                } else {
                    int next = session.candidateIndex.merge(s, 1, Integer::sum);
                    if (next >= candidateCount(req, sd)) {
                        session.exhausted.add(s);
                        verdicts.add(verdict(s, "不合格且候选已用尽（未定）", session));
                    } else {
                        verdicts.add(verdict(s, "不合格，候选前进", session));
                    }
                }
            }

            boolean done = session.frozen.size() + session.exhausted.size() == session.sections.size();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("judgeDone", done);
            out.put("frozenSections", new ArrayList<>(session.frozen));
            out.put("exhaustedSections", new ArrayList<>(session.exhausted));
            out.put("sectionCandidate", new LinkedHashMap<>(session.candidateIndex));
            out.put("verdicts", verdicts);
            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("[S8a] 判定轮：冻结 {} / 未定 {} / 共 {}（done={}）",
                    session.frozen.size(), session.exhausted.size(), session.sections.size(), done);
        } catch (Exception e) {
            log.error("tmsd-step8a-judge 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("TMSD_JUDGE_FAILED").errorMessage(String.valueOf(e.getMessage())).send().join();
        }
    }

    /** 该段候选方案总数（附件可行布局枚举数）。 */
    private static int candidateCount(TowerDesignRequest req, TowerDesignEngine.SectionDesign sd) {
        LayoutSpec.MiddleSection lay = req.layoutReference().middleSection(sd.sectionNo());
        double edgeOffset = lay.rungWidth() / 2.0;
        List<Integer> welds = req.geometry().weldPositions(sd.sectionNo() - 1);
        return TowerDesignEngine.accessoryLayoutAll(sd.platformHeight(), welds, edgeOffset).size();
    }

    /** 单段判定摘要。 */
    private static Map<String, Object> verdict(int sectionNo, String status, DesignSession session) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("section", sectionNo);
        m.put("status", status);
        m.put("candidate", session.candidateIndex.getOrDefault(sectionNo, 0));
        return m;
    }

    // ==================== 通用步骤处理 ====================

    /** 段设计 + 设计输入 → 输出映射。 */
    private interface SectionMapper {
        Map<String, Object> map(TowerDesignEngine.SectionDesign sd, TowerDesignRequest req);
    }

    /** 逐段设计动作：每个 BPMN 步骤 invoke 对应引擎方法，回写会话中的段设计。 */
    private interface SectionStep {
        void apply(TowerDesignEngine.SectionDesign sd, DesignSession session);
    }

    /**
     * 通用步骤：先对本步的每段执行设计计算（{@code step}），再产出该步骤的输出写入流程变量。
     *
     * <p>逐步设计的核心：{@code step} 只计算本环节负责的字段，其余字段由后续步骤（或 S8）填充，
     * 从而与 BPMN 各环节「各设计一个内容」的意图一致。
     */
    private void runStep(final ActivatedJob job, final JobClient client,
                         String jobType, String stepLabel, SectionStep step, SectionMapper mapper) {
        try {
            long key = job.getProcessInstanceKey();
            DesignSession session = sessions.get(key);
            if (session == null) {
                throw new IllegalStateException("设计会话丢失（key=" + key + "）");
            }
            for (TowerDesignEngine.SectionDesign sd : session.sections.values()) {
                step.apply(sd, session);
            }
            List<Map<String, Object>> sections = new ArrayList<>();
            for (TowerDesignEngine.SectionDesign sd : session.sections.values()) {
                sections.add(mapper.map(sd, session.request));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("step", stepLabel);
            out.put("variantName", TowerDesignEngine.variants().get(0).name());
            out.put("sections", sections);

            client.newCompleteCommand(job.getKey()).variables(out).send().join();
            log.info("[{}] {} 段", stepLabel, sections.size());
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
