package com.ocean.ontologyframework.neinsight;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontologyframework.neinsight.analyze.InputAnalysis;
import com.ocean.ontologyframework.neinsight.retrieval.InsightWebRetriever;
import com.ocean.utilities.LlmClient;
import com.ocean.utilities.OntologyWorkerSupport;
import com.ocean.utilities.RabbitMqHandler;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * NEInsight「五看三定」洞察流程的 JobWorker。
 *
 * <p>12 个 {@code @JobWorker} 与 {@code ontology/bpmn/neInsightProcess.bpmn} 的 12 个
 * {@code serviceTask} 一一对应（入口 {@code analyze-input} 输入分析 + 五看三定 + 汇总 + 生成洞察报告；契约见《NEInsight应用-应用架构设计》§4）。
 *
 * <p><b>应用口径</b>：NEInsight <b>不内置本体业务实例数据（内部知识基线）</b>——这些基线不通用。
 * 「五看三定」8 个分析节点保留流程拓扑，但<b>不再检索/输出本体实例数据</b>；其知识基线为
 * <b>本体逐节点方法论</b>（{@code core:InsightMethod}，给出检索关键词 / 信息源 / 需采集字段 / 加工步骤 / 产出 / 来源），
 * 由 {@code generate-report} 按 SKILL 各节点「🔎 联网检索指令」经 {@link InsightWebRetriever} 联网采集外部公开数据后，
 * 连同 SKILL 一并交大模型加工分析成文。全部业务逻辑经现存 API（{@link BackendService} 等）实现，不硬编码业务数据（★铁律21）。
 */
@Component
@Profile("NEInsightBPMN")
public class NEInsightOntologyJobWorker extends OntologyWorkerSupport {

    private static final Logger log = LoggerFactory.getLogger(NEInsightOntologyJobWorker.class);

    /**
     * 入口「输入分析」调用的大模型客户端（通用能力，见 {@link com.ocean.utilities.LlmClient}）。
     * 未配置 {@code llm.*} 时为不可用，节点自动降级为确定性提取（仅取场景原文、其余字段留空）。
     */
    @Autowired(required = false)
    private LlmClient llm;

    /**
     * 洞察报告「联网检索」通道（见 {@link InsightWebRetriever}）。
     * 由 {@code generate-report} 按 SKILL 各节点检索指令联网采集外部公开数据，连同 SKILL 交大模型成文；
     * 未装配/未启用/检索失败时报告退化为「仅据 SKILL 方法论」生成，流程不阻断。
     */
    @Autowired(required = false)
    private InsightWebRetriever webRetriever;

    // ==================== 命名空间（与 ontology/neinsight-all.owl 一致） ====================
    private static final String CORE_NS = "http://example.org/neinsight/core#";
    private static final String ENV_NS = "http://example.org/neinsight/env#";
    private static final String ABOX_NS = "http://example.org/neinsight/abox#";

    // ==================== 对象属性 ====================
    private static final String P_HAS_ELEMENT = CORE_NS + "hasElement";

    // ==================== 数据属性 ====================
    private static final String D_OPPORTUNITY_PRIORITY = ENV_NS + "opportunityPriority";
    private static final String D_MARKET_ATTRACTIVENESS = ENV_NS + "marketAttractiveness";
    private static final String D_COMPETITIVE_POSITION = ENV_NS + "competitivePosition";
    private static final String D_FLAGGED_FOR_ALERT = ENV_NS + "flaggedForAlert";

    // ==================== 类 ====================
    private static final String C_OPPORTUNITY = ENV_NS + "Opportunity";

    // ==================== 洞察方法（逐节点方法论，存于本体 core:InsightMethod） ====================
    private static final String C_INSIGHT_METHOD = CORE_NS + "InsightMethod";
    private static final String D_APPLIES_TO_NODE = CORE_NS + "appliesToNode";
    private static final String D_RETRIEVAL_KEYWORD = CORE_NS + "retrievalKeyword";
    private static final String D_DATA_SOURCE = CORE_NS + "dataSource";
    private static final String D_DATA_FIELD = CORE_NS + "dataField";
    private static final String D_ANALYSIS_STEP = CORE_NS + "analysisStep";
    private static final String D_METHOD_OUTPUT = CORE_NS + "methodOutput";
    private static final String D_SOURCE_REF = CORE_NS + "sourceRef";

    /** BPMN 流程定义 id（与 ontology/bpmn/neInsightProcess.bpmn 一致）。 */
    private static final String INSIGHT_PROCESS_ID = "NewEnergyInsightProcess";

    /** 「五看三定」各节点的 jobType，按 BPMN serviceTask 执行顺序（用于按节点联网检索）。 */
    private static final List<String> INSIGHT_NODE_JOB_TYPES = List.of(
            "scan-industry-trend", "scan-market-customer", "scan-competition", "scan-self",
            "scan-opportunity", "define-control-point", "define-objective", "define-strategy");

    /**
     * 联合域编码。
     *
     * <p>域概念已并入输入场景表达（前端不再选择洞察域、BPMN 亦无域名关），
     * 故检索根域固定为「联合域」；域仅用于标注流程实例归属，业务数据不再从 ABox 检索。
     */
    private static final String DEFAULT_DOMAIN = "COMBINED";

    /** 节点占位说明：本节点不再内嵌本体实例数据，检索与加工口径见 SKILL。 */
    private static final String METHODOLOGY_NODE_NOTE =
            "本节点不再检索/内嵌本体实例数据；检索与加工口径见 SKILL 对应节点的"
                    + "「🔎 联网检索指令」与「🧮 数据加工与分析要求」，由 generate-report 联网采集后交大模型加工分析。";

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    /** 洞察 SKILL 落盘目录（相对模块工作目录；默认 skill/）。 */
    @Value("${neinsight.skill.dir:skill}")
    private String skillDir;

    /** 洞察报告落盘目录（相对模块工作目录；默认 report/）。 */
    @Value("${neinsight.report.dir:report}")
    private String reportDir;

    @Value("${env-prepare.rabbitmq.exchange}")
    private String alertExchange;

    @Value("${env-prepare.rabbitmq.routing-key}")
    private String alertRoutingKey;

    @PostConstruct
    public void init() throws Exception {
        log.info("🔧 初始化 NEInsightOntologyJobWorker 依赖链...");
        initOntologyPipeline();
        log.info("✅ NEInsightOntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);
    }

    @Override
    protected BackendService createBackendService() throws Exception {
        // 第 7 步：单文件本体（TBox + 典型 ABox）；OBDA/数据库接入见第 11 步
        return BackendService.getInstance(mainOntologyPath);
    }

    // ==========================================================================
    // 入口「输入分析」（大模型解析客户输入：身份/场景/行业/客户/目标等，业务无关）
    // ==========================================================================
    @JobWorker(type = "analyze-input", autoComplete = false)
    public void handleAnalyzeInput(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String raw = str(vars.get("insightScenario"));
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("insightScenario", raw == null ? "" : raw);

            InputAnalysis.Result r = InputAnalysis.analyze(llm, input);
            Map<String, Object> analysis = r.analysis();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("inputAnalysisResult", analysis);
            out.put("refinedScenario", str(analysis.get("scenario")));
            out.put("inputAnalysisSource", r.source());
            out.put("inputIdentity", str(analysis.get("identity")));
            out.put("inputMissingFields", r.missingFields());
            complete(client, job, out);
            log.info("✅ analyze-input 完成 | source={} | identity={} | missing={} | scenario=【{}】",
                    r.source(), out.get("inputIdentity"), r.missingFields(), out.get("refinedScenario"));
        } catch (Exception e) {
            fail(job, client, "ANALYZE_INPUT_FAILED", e);
        }
    }

    // ==========================================================================
    // 五看（看行业/趋势）
    //
    // 应用口径：本节点保留流程拓扑，但不再检索/输出本体实例数据——其知识基线为
    // 本体「看行业/趋势」方法论（core:InsightMethod / scan-industry-trend），
    // 数据由 generate-report 按 SKILL 检索指令联网采集后交大模型加工分析。
    // ==========================================================================
    @JobWorker(type = "scan-industry-trend", autoComplete = false)
    public void handleScanIndustryTrend(final ActivatedJob job, final JobClient client) {
        try {
            emitSceneNode(job, client, "scan-industry-trend", "industryTrendResult", null);
        } catch (Exception e) {
            fail(job, client, "SCAN_INDUSTRY_TREND_FAILED", e);
        }
    }

    // ==========================================================================
    // 五看（看市场/客户）
    // ==========================================================================
    @JobWorker(type = "scan-market-customer", autoComplete = false)
    public void handleScanMarketCustomer(final ActivatedJob job, final JobClient client) {
        try {
            emitSceneNode(job, client, "scan-market-customer", "marketCustomerResult", null);
        } catch (Exception e) {
            fail(job, client, "SCAN_MARKET_CUSTOMER_FAILED", e);
        }
    }

    // ==========================================================================
    // 五看（看竞争）
    // ==========================================================================
    @JobWorker(type = "scan-competition", autoComplete = false)
    public void handleScanCompetition(final ActivatedJob job, final JobClient client) {
        try {
            emitSceneNode(job, client, "scan-competition", "competitionResult", null);
        } catch (Exception e) {
            fail(job, client, "SCAN_COMPETITION_FAILED", e);
        }
    }

    // ==========================================================================
    // 五看（看自己）
    // ==========================================================================
    @JobWorker(type = "scan-self", autoComplete = false)
    public void handleScanSelf(final ActivatedJob job, final JobClient client) {
        try {
            emitSceneNode(job, client, "scan-self", "selfAssessmentResult", null);
        } catch (Exception e) {
            fail(job, client, "SCAN_SELF_FAILED", e);
        }
    }

    // ==========================================================================
    // 五看（看机会 · SPAN 定位）
    //
    // 应用口径：不再检索/输出本体实例数据（机会点/SPAN 象限不再内嵌），
    // opportunityPriority 恒为 NONE——高优先级分支与 mark-high-priority 外发不再触发（定义保留）。
    // ==========================================================================
    @JobWorker(type = "scan-opportunity", autoComplete = false)
    public void handleScanOpportunity(final ActivatedJob job, final JobClient client) {
        try {
            emitSceneNode(job, client, "scan-opportunity", "opportunityResult",
                    Map.of("opportunityPriority", "NONE"));
        } catch (Exception e) {
            fail(job, client, "SCAN_OPPORTUNITY_FAILED", e);
        }
    }

    // ==========================================================================
    // 高优先级机会点标记与外发（定义保留；因不再内嵌实例数据，当前不再触发）
    // ==========================================================================
    @JobWorker(type = "mark-high-priority", autoComplete = false)
    public void handleMarkHighPriority(final ActivatedJob job, final JobClient client) {
        boolean dispatched = false;
        try {
            String domain = currentDomain(job);
            List<OWLNamedIndividual> opps = filterByType(domainElements(domain), C_OPPORTUNITY);

            OWLNamedIndividual high = null;
            for (OWLNamedIndividual o : opps) {
                if ("HIGH".equals(resolvePriority(o))) {
                    high = o;
                    break;
                }
            }

            if (high != null) {
                Double attractiveness = dataNumber(high, D_MARKET_ATTRACTIVENESS);
                Double position = dataNumber(high, D_COMPETITIVE_POSITION);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("event", "NEINSIGHT_HIGH_PRIORITY_OPPORTUNITY");
                payload.put("domain", domain);
                payload.put("opportunityIri", high.getIRI().toString());
                payload.put("opportunityLabel", label(high.getIRI().toString()));
                payload.put("marketAttractiveness", attractiveness);
                payload.put("competitivePosition", position);
                payload.put("priority", "HIGH");
                payload.put("flaggedForAlert", dataBoolean(high, D_FLAGGED_FOR_ALERT));

                dispatched = dispatchAlert(payload);
            } else {
                log.info("ℹ️ mark-high-priority：当前未提供机会点实例数据（本应用不内置），跳过外发 | domain={}", domain);
            }

            complete(client, job, Map.of("alertDispatched", dispatched));
            log.info("✅ mark-high-priority 完成 | domain={} | alertDispatched={}", domain, dispatched);
        } catch (Exception e) {
            fail(job, client, "MARK_HIGH_PRIORITY_FAILED", e);
        }
    }

    private boolean dispatchAlert(Map<String, Object> payload) {
        RabbitMqHandler mq = new RabbitMqHandler();
        try {
            mq.send(alertExchange, alertRoutingKey, payload);
            log.info("📨 高优先级机会点已外发 | exchange={} | routingKey={} | opportunity={}",
                    alertExchange, alertRoutingKey, payload.get("opportunityLabel"));
            return true;
        } catch (Exception e) {
            log.error("❌ 高优先级机会点外发失败（不影响流程继续）| exchange={} | routingKey={}",
                    alertExchange, alertRoutingKey, e);
            return false;
        } finally {
            try {
                mq.destroy();
            } catch (Exception ignore) {
                // 释放失败无需处理
            }
        }
    }

    // ==========================================================================
    // 三定（定战略控制点）
    // ==========================================================================
    @JobWorker(type = "define-control-point", autoComplete = false)
    public void handleDefineControlPoint(final ActivatedJob job, final JobClient client) {
        try {
            emitSceneNode(job, client, "define-control-point", "controlPointResult", null);
        } catch (Exception e) {
            fail(job, client, "DEFINE_CONTROL_POINT_FAILED", e);
        }
    }

    // ==========================================================================
    // 三定（定目标 · SMART）
    // ==========================================================================
    @JobWorker(type = "define-objective", autoComplete = false)
    public void handleDefineObjective(final ActivatedJob job, final JobClient client) {
        try {
            emitSceneNode(job, client, "define-objective", "objectiveResult",
                    Map.of("smartCompliant", false));
        } catch (Exception e) {
            fail(job, client, "DEFINE_OBJECTIVE_FAILED", e);
        }
    }

    // ==========================================================================
    // 三定（定策略）
    // ==========================================================================
    @JobWorker(type = "define-strategy", autoComplete = false)
    public void handleDefineStrategy(final ActivatedJob job, final JobClient client) {
        try {
            emitSceneNode(job, client, "define-strategy", "strategyResult", null);
        } catch (Exception e) {
            fail(job, client, "DEFINE_STRATEGY_FAILED", e);
        }
    }

    /**
     * 「五看三定」8 个分析节点的统一出口：保留流程变量与拓扑，但不再检索/输出本体实例数据，
     * 仅回写洞察域、场景与本节点的方法论驱动说明（数据采集与加工口径见 SKILL）。
     */
    private void emitSceneNode(ActivatedJob job, JobClient client, String jobType, String resultVar,
                               Map<String, Object> extra) {
        String domain = currentDomain(job);
        String scenario = currentScenario(job);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("domain", domain);
        result.put("scenario", scenario);
        result.put("note", METHODOLOGY_NODE_NOTE);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put(resultVar, result);
        if (extra != null) {
            out.putAll(extra);
        }
        complete(client, job, out);
        log.info("✅ {} 完成 | domain={} | scenario=【{}】", jobType, domain, scenario);
    }

    // ==========================================================================
    // 汇总输出（一致性校验 + 生成给大模型的 SKILL）
    // ==========================================================================
    @JobWorker(type = "summarize-insight", autoComplete = false)
    public void handleSummarizeInsight(final ActivatedJob job, final JobClient client) {
        try {
            String scenario = currentScenario(job);
            String domain = currentDomain(job);

            boolean consistent;
            try {
                consistent = backendService.getReasonerService().getReasoner().isConsistent();
            } catch (Exception e) {
                consistent = false;
                log.error("❌ 一致性校验异常", e);
            }
            String status = consistent ? "CONSISTENT" : "INCONSISTENT";

            String summary = composeSummary(domain, scenario, status);

            String skillPath = null;
            try {
                skillPath = writeInsightSkill(job, domain, scenario, summary, status);
            } catch (Exception e) {
                log.error("⚠️ 洞察 SKILL 落盘失败（不影响流程完成）| domain={} | scenario={}", domain, scenario, e);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("insightSummary", summary);
            out.put("instanceIri", domainIndividualIri(domain));
            out.put("ontologyValidationStatus", status);
            if (skillPath != null) {
                out.put("insightSkillPath", skillPath);
            }
            complete(client, job, out);
            log.info("✅ summarize-insight 完成 | domain={} | scenario={} | validation={} | skill={} | summary={}",
                    domain, scenario, status, skillPath, summary);
        } catch (Exception e) {
            fail(job, client, "SUMMARIZE_INSIGHT_FAILED", e);
        }
    }

    // ==========================================================================
    // 汇总后「生成洞察报告」：把 SKILL 导入大模型生成洞察报告并落盘供下载
    // ==========================================================================

    /**
     * 生成洞察报告节点（{@code generate-report}）。
     *
     * <p>读取汇总节点产出的「洞察报告生成 SKILL」，先按 SKILL 各节点「🔎 联网检索指令」经
     * {@link InsightWebRetriever} 联网采集外部公开数据，再连同 SKILL 一并导入大模型加工分析生成《洞察报告》
     * 并落盘，回写 {@code insightReportPath} 供前端下载。检索通道不可用/大模型不可用/失败时逐级降级
     * （退回「仅据 SKILL 方法论生成」，或直接落盘 SKILL 内容），保证流程不阻断；报告落盘失败仅记日志、不阻断流程。
     */
    @JobWorker(type = "generate-report", autoComplete = false)
    public void handleGenerateReport(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String scenario = currentScenario(job);
            String skillPathVar = str(vars.get("insightSkillPath"));

            String skillContent = readSkillContent(job, skillPathVar);
            if (skillContent == null || skillContent.isBlank()) {
                String summary = str(vars.get("insightSummary"));
                skillContent = (summary == null || summary.isBlank()) ? "" : summary;
            }

            String report = generateReport(scenario, skillContent, job.getProcessInstanceKey());

            String reportPath = null;
            try {
                reportPath = writeInsightReport(job, report);
            } catch (Exception e) {
                log.error("⚠️ 洞察报告落盘失败（不影响流程完成）| scenario={}", scenario, e);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            if (reportPath != null) {
                out.put("insightReportPath", reportPath);
            }
            complete(client, job, out);
            log.info("✅ generate-report 完成 | scenario={} | report={}", scenario, reportPath);
        } catch (Exception e) {
            fail(job, client, "GENERATE_REPORT_FAILED", e);
        }
    }

    /**
     * 读取本次流程生成的洞察 SKILL 全文：优先流程变量 {@code insightSkillPath}（summarize-insight 回写），
     * 其次按命名规则回退 {@code <neinsight.skill.dir>/neinsight-<instanceKey>.md}；两者均须落在 SKILL 目录之内（防路径穿越）。
     */
    private String readSkillContent(ActivatedJob job, String pathVar) {
        Path base = Path.of(skillDir).toAbsolutePath().normalize();
        if (pathVar != null && !pathVar.isBlank() && !"null".equals(pathVar)) {
            try {
                Path p = Path.of(pathVar).toAbsolutePath().normalize();
                if (p.startsWith(base) && Files.isRegularFile(p)) {
                    return Files.readString(p, StandardCharsets.UTF_8);
                }
            } catch (Exception ignore) {
                // 非法路径，回退命名规则
            }
        }
        try {
            Path fallback = base.resolve("neinsight-" + job.getProcessInstanceKey() + ".md").normalize();
            if (Files.isRegularFile(fallback)) {
                return Files.readString(fallback, StandardCharsets.UTF_8);
            }
        } catch (Exception ignore) {
            // 忽略，交由调用方兜底
        }
        return null;
    }

    /**
     * 生成洞察报告：先按 SKILL 各节点「🔎 联网检索指令」联网采集外部公开数据，再连同 SKILL 一并导入
     * 大模型加工分析成文；大模型不可用/失败时降级为直接落盘 SKILL 内容（保证流程不阻断）。
     */
    private String generateReport(String scenario, String skillContent, long instanceKey) {
        String scene = (scenario == null || scenario.isBlank()) ? "给定洞察场景" : scenario;
        if (llm != null && llm.isAvailable() && skillContent != null && !skillContent.isBlank()) {
            String retrieval = collectRetrievalData(scene, instanceKey);
            String system = "你是一名资深的战略洞察分析师，精通华为「五看三定」方法论。"
                    + "请严格依据用户提供的「洞察报告生成 SKILL」，并结合「联网检索结果」中由检索通道实际采集的"
                    + "外部公开数据，围绕洞察场景逐节点生成一份结构完整、结论有据可依、来源可溯（标注 URL）、"
                    + "缺失项标注「待补充」的《洞察报告》，使用规范 Markdown 输出。"
                    + "外部数据须优先采用并注明来源；不同来源数据冲突时并列说明；不得编造数据。";
            String user = buildReportUserPrompt(scene, skillContent, retrieval);
            String r = llm.chat(system, user);
            if (r != null && !r.isBlank()) {
                return r;
            }
            log.warn("⚠️ 大模型生成洞察报告不可用/失败，降级为直接落盘 SKILL 内容 | scenario={}", scene);
        }
        return skillContent == null ? "" : skillContent;
    }

    /**
     * 按「三、洞察内容」各节点的「🔎 联网检索指令」（取自本体 {@code core:InsightMethod} 的
     * {@code retrievalKeyword}，★铁律21 不硬编码业务词表）逐节点联网采集外部公开数据，汇总为 Markdown 片段。
     *
     * <p>检索通道未装配/未启用/检索失败时返回空串——报告退化为「仅据 SKILL 方法论」生成，流程不阻断。
     */
    private String collectRetrievalData(String sceneLabel, long instanceKey) {
        if (webRetriever == null || !webRetriever.isEnabled()) {
            return "";
        }
        try {
            List<InsightWebRetriever.NodeQuery> nodes = new ArrayList<>();
            for (String jobType : INSIGHT_NODE_JOB_TYPES) {
                OWLNamedIndividual m = insightMethod(jobType);
                if (m == null) {
                    continue;
                }
                List<String> keywords = dataStrings(m, D_RETRIEVAL_KEYWORD);
                if (keywords.isEmpty()) {
                    continue;
                }
                List<String> queries = new ArrayList<>();
                for (String k : keywords) {
                    queries.add("「" + sceneLabel + "」+ " + k);
                }
                nodes.add(new InsightWebRetriever.NodeQuery(jobType,
                        "洞察场景「" + sceneLabel + "」· 节点 " + jobType + " 外部公开数据检索", queries));
            }
            if (nodes.isEmpty()) {
                return "";
            }
            Map<String, String> res = webRetriever.retrieve("neinsight-" + instanceKey, nodes);
            if (res == null || res.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : res.entrySet()) {
                if (e.getValue() == null || e.getValue().isBlank()) {
                    continue;
                }
                sb.append("### 节点 `").append(e.getKey()).append("` 联网检索结果\n\n").append(e.getValue()).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("⚠️ 联网检索数据采集失败，报告将仅据 SKILL 方法论生成 | scenario={}: {}",
                    sceneLabel, e.toString());
            return "";
        }
    }

    /** 组装报告生成的 user 提示词：洞察场景 + SKILL 全文（+ 联网检索结果）。 */
    private String buildReportUserPrompt(String scene, String skillContent, String retrieval) {
        StringBuilder sb = new StringBuilder();
        sb.append("洞察场景：").append(scene).append("\n\n");
        sb.append("以下是「洞察报告生成 SKILL」，请据此生成洞察报告：\n\n").append(skillContent).append("\n\n");
        if (retrieval != null && !retrieval.isBlank()) {
            sb.append("----\n\n## 联网检索结果（由程序按 SKILL 各节点检索指令实际联网采集的外部公开数据）\n\n");
            sb.append("> 以下外部数据已由检索通道实时采集；撰写各节点结论时请**优先采用并标注来源 URL**，")
                    .append("并据该节点方法论加工分析；不同来源冲突时并列说明，缺失项标注「待补充」。\n\n");
            sb.append(retrieval);
        } else {
            sb.append("> 注：本次未能获取联网检索结果，请仅依据 SKILL 方法论生成，")
                    .append("涉及外部数据处标注「待补充」，不得编造。\n\n");
        }
        return sb.toString();
    }

    /** 落盘洞察报告至 {@code <neinsight.report.dir>/neinsight-<instanceKey>-洞察报告.md}，返回绝对路径。 */
    private String writeInsightReport(ActivatedJob job, String report) throws Exception {
        long instanceKey = job.getProcessInstanceKey();
        Path dir = Path.of(reportDir);
        Files.createDirectories(dir);
        Path target = dir.resolve("neinsight-" + instanceKey + "-洞察报告.md");
        Files.writeString(target, report == null ? "" : report, StandardCharsets.UTF_8);
        log.info("📄 洞察报告已落盘 | path={}", target.toAbsolutePath());
        return target.toAbsolutePath().toString();
    }

    // ==========================================================================
    // 私有辅助
    // ==========================================================================

    /** 内部 ABox 检索根域：域概念并入输入场景后不再由用户选择，默认联合域。 */
    private String currentDomain(ActivatedJob job) {
        String d = str(job.getVariablesAsMap().get("insightDomain"));
        return (d == null || d.isBlank()) ? DEFAULT_DOMAIN : d;
    }

    /**
     * 输入洞察场景（流程唯一业务输入）。
     *
     * <p>优先使用入口「输入分析」节点（analyze-input）由大模型精炼后的 {@code refinedScenario}；
     * 该变量缺失时回退到用户原始输入 {@code insightScenario}。
     */
    private String currentScenario(ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        String refined = str(vars.get("refinedScenario"));
        if (refined != null && !refined.isBlank()) {
            return refined;
        }
        String s = str(vars.get("insightScenario"));
        return (s == null || s.isBlank()) ? "" : s;
    }

    private String domainIndividualIri(String insightDomain) {
        return switch (insightDomain) {
            case "IT_SPACE" -> ABOX_NS + "IT_SPACE";
            case "CLOUD_SPACE" -> ABOX_NS + "CLOUD_SPACE";
            case "COMBINED" -> ABOX_NS + "COMBINED_SPACE";
            default -> throw new IllegalArgumentException("未知洞察域: " + insightDomain);
        };
    }

    /** 收集检索根域下的全部要素（沿 hasElement 递归展开；联合域含 IT/云子域要素）。 */
    private List<OWLNamedIndividual> domainElements(String insightDomain) {
        String domainIri = domainIndividualIri(insightDomain);
        OWLNamedIndividual root = backendService.getIndividual(domainIri);
        OWLObjectProperty hasElement = backendService.getObjectProperty(P_HAS_ELEMENT);

        LinkedHashSet<OWLNamedIndividual> acc = new LinkedHashSet<>();
        Deque<OWLNamedIndividual> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            OWLNamedIndividual cur = stack.pop();
            if (!acc.add(cur)) {
                continue;
            }
            for (OWLNamedIndividual child : backendService.getObjectPropertyAllValueOfIndividual(cur, hasElement)) {
                stack.push(child);
            }
        }
        acc.remove(root);
        List<OWLNamedIndividual> out = new ArrayList<>(acc);
        out.sort(Comparator.comparing(i -> i.getIRI().toString()));
        return out;
    }

    private List<OWLNamedIndividual> filterByType(List<OWLNamedIndividual> inds, String classIri) {
        List<OWLNamedIndividual> out = new ArrayList<>();
        for (OWLNamedIndividual i : inds) {
            if (isInstance(i, classIri)) {
                out.add(i);
            }
        }
        return out;
    }

    private List<OWLNamedIndividual> globalInstances(String classIri) {
        List<OWLNamedIndividual> out = new ArrayList<>(backendService.getIndividuals(classIri));
        out.sort(Comparator.comparing(i -> i.getIRI().toString()));
        return out;
    }

    private boolean isInstance(OWLNamedIndividual ind, String classIri) {
        try {
            return backendService.isInstanceOf(ind.getIRI().toString(), classIri);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- 属性/字面量读取 ----------------

    private String dataString(OWLNamedIndividual ind, String propIri) {
        Set<OWLLiteral> vs = backendService.getDataPropertyValueOfIndividual(ind, propIri);
        return vs.isEmpty() ? null : vs.iterator().next().getLiteral();
    }

    private Double dataNumber(OWLNamedIndividual ind, String propIri) {
        Set<OWLLiteral> vs = backendService.getDataPropertyValueOfIndividual(ind, propIri);
        if (vs.isEmpty()) {
            return null;
        }
        Number n = backendService.parseNumeric(vs.iterator().next());
        return n == null ? null : n.doubleValue();
    }

    private Boolean dataBoolean(OWLNamedIndividual ind, String propIri) {
        Set<OWLLiteral> vs = backendService.getDataPropertyValueOfIndividual(ind, propIri);
        return vs.isEmpty() ? null : Boolean.parseBoolean(vs.iterator().next().getLiteral());
    }

    private String label(String iri) {
        try {
            String l = backendService.resolveLabel(iri);
            return (l == null || l.isBlank()) ? localName(iri) : l;
        } catch (Exception e) {
            return localName(iri);
        }
    }

    private static String localName(String iri) {
        if (iri == null) {
            return "";
        }
        int i = Math.max(iri.lastIndexOf('#'), iri.lastIndexOf('/'));
        return i < 0 ? iri : iri.substring(i + 1);
    }

    private String resolvePriority(OWLNamedIndividual opp) {
        String inferred = dataString(opp, D_OPPORTUNITY_PRIORITY);
        if (inferred != null) {
            return inferred;
        }
        Double a = dataNumber(opp, D_MARKET_ATTRACTIVENESS);
        Double c = dataNumber(opp, D_COMPETITIVE_POSITION);
        if (a == null || c == null) {
            return null;
        }
        log.warn("⚠️ SWRL 未返回 opportunityPriority，按占位阈值镜像 R1/R1b 计算 | opp={}",
                localName(opp.getIRI().toString()));
        return placeholderPriority(a, c);
    }

    private static String placeholderPriority(double attractiveness, double position) {
        if (attractiveness > SPAN_THRESHOLD && position > SPAN_THRESHOLD) {
            return "HIGH";
        }
        if (attractiveness < SPAN_THRESHOLD && position < SPAN_THRESHOLD) {
            return "LOW";
        }
        return "MEDIUM";
    }

    // ---------------- 汇总 ----------------

    private String composeSummary(String domain, String scenario, String status) {
        String scene = (scenario == null || scenario.isBlank()) ? domainLabel(domain) + "全景" : scenario;
        return "【洞察场景「" + scene + "」的「五看三定」洞察结论】"
                + "本流程以华为「五看三定」方法论为基线，逐节点给出联网检索口径与数据加工分析要求；"
                + "各节点不再内嵌本体实例数据，业务数据由 generate-report 按 SKILL 各节点「🔎 联网检索指令」"
                + "联网采集后交大模型加工分析。本体一致性校验=" + status + "。";
    }

    private String domainLabel(String domain) {
        return switch (domain) {
            case "IT_SPACE" -> "IT 空间";
            case "CLOUD_SPACE" -> "云空间";
            case "COMBINED" -> "联合空间";
            default -> domain;
        };
    }

    // ---------------- 洞察 SKILL（md）落盘 ----------------

    /**
     * 生成并落盘「洞察报告生成 SKILL」（Markdown）。
     *
     * <p>该 SKILL 是一份可**直接输入大模型**的提示词文件：以「角色设定 + 任务指令」为骨架，
     * 按「五看三定」逐节点给出①🔎 联网检索指令（依据场景生成的检索关键词/信息源/需采集字段）、
     * ②🧮 数据加工与分析要求，并附📚 方法论来源；大模型据此**真正联网检索、加工、分析**后生成洞察报告，
     * 而非仅按维度骨架作答。**不内嵌本体业务实例数据**——逐节点方法论基线取自本体 {@code core:InsightMethod}。
     * 落盘至 {@code <neinsight.skill.dir>/neinsight-<instanceKey>.md}。
     * 全程经现存 API（{@link BackendService}）读取本体，不硬编码业务数据、不硬编码绝对路径（★铁律21）。
     */
    private String writeInsightSkill(ActivatedJob job, String domain, String scenario,
                                     String summary, String status) throws Exception {
        long instanceKey = job.getProcessInstanceKey();
        String processId = job.getBpmnProcessId();
        if (processId == null || processId.isBlank()) {
            processId = INSIGHT_PROCESS_ID;
        }
        String markdown = composeSkill(instanceKey, processId, domain, scenario, summary, status);

        Path dir = Path.of(skillDir);
        Files.createDirectories(dir);
        Path target = dir.resolve("neinsight-" + instanceKey + ".md");
        Files.writeString(target, markdown, StandardCharsets.UTF_8);
        log.info("📄 洞察 SKILL 已落盘 | path={}", target.toAbsolutePath());
        return target.toAbsolutePath().toString();
    }

    private String composeSkill(long instanceKey, String processId, String domain, String scenario,
                                String summary, String status) {
        StringBuilder sb = new StringBuilder();
        String retrievalRoot = domainLabel(domain);
        String sceneLabel = scenario.isBlank() ? retrievalRoot + "全景" : scenario;
        String instanceIri = domainIndividualIri(domain);

        sb.append("# 洞察报告生成 SKILL（场景：").append(sceneLabel).append("）\n\n");
        sb.append("> 本文件由 NEInsight 洞察流程自动生成，是一份可**直接输入大模型**的提示词 SKILL。")
                .append("将本文件全文作为提示词输入大模型，即可据此生成《关于「")
                .append(sceneLabel).append("」的洞察报告》。\n\n");

        sb.append("## 一、角色设定\n\n");
        sb.append("你是一名资深的**战略洞察分析师**，精通华为「五看三定」方法论，")
                .append("善于从行业趋势、市场与客户、竞争格局、自身能力、机会点等维度提炼洞察，")
                .append("并给出可执行的战略控制点、目标与策略建议。\n\n");
        sb.append("你具备**联网检索能力**：对每个节点，须按该节点的「🔎 联网检索指令」主动联网采集外部公开数据，")
                .append("再按「🧮 数据加工与分析要求」加工分析，最终形成该节点的洞察结论——**不是仅按维度骨架作答**。")
                .append("各节点的知识基线为该节点的方法论（检索关键词 / 信息源 / 需采集字段 / 加工步骤 / 产出 / 来源，取自本体 core:InsightMethod），")
                .append("本次流程不再内嵌任何业务实例数据。\n\n");
        sb.append("本次洞察场景为 **").append(sceneLabel).append("**，")
                .append("业务方法论为华为「五看三定」——「五看」「三定」各维度均围绕该场景展开。\n\n");

        sb.append("## 二、任务指令\n\n");
        sb.append("请以「角色设定」中的身份，**围绕「").append(sceneLabel)
                .append("」这一洞察场景，按下方「三、洞察内容」所列 BPMN 流程节点的先后顺序，逐节点逐步构建**《关于「")
                .append(sceneLabel).append("」的洞察报告》——即依次完成「五看」各节点（看行业/趋势 → 看市场/客户 → 看竞争 → 看自己 → 看机会）与「三定」各节点（定战略控制点 → 定目标 → 定策略），最后汇总形成结论。要求：\n\n");
        sb.append("1. 所有内容严格围绕输入洞察场景组织，按 BPMN 流程节点顺序（「五看三定」）逐节构建，不跳节、不打乱次序；\n");
        sb.append("2. **每个节点都必须先联网采集数据、再做加工分析**：按该节点「🔎 联网检索指令」给出的检索关键词、信息源与需采集字段**真正联网检索**，")
                .append("再按「🧮 数据加工与分析要求」完成加工与分析后形成结论；不得跳过检索仅按维度骨架作答；\n");
        sb.append("3. **以方法论为基线**：各节点的分析方法、检索口径与产出要求均来自本体「洞察方法」（core:InsightMethod）；")
                .append("本次流程不再内嵌业务实例数据，结论须完全基于联网采集的外部公开数据；\n");
        sb.append("4. **有据可依、来源可溯**：外部数据须标注来源（机构/报告/链接）与时间；不同来源数据冲突时须并列说明；不得编造数据；\n");
        sb.append("5. 对数据缺失或标注为【待考】的内容，须明确注明「待补充」，不得臆测；\n");
        sb.append("6. 使用规范的 Markdown 输出（标题、摘要、分节点小节、结论），语言专业、简洁。\n\n");

        sb.append("## 三、洞察内容（按 BPMN 流程逐步构建）\n\n");
        sb.append("> 以下各节严格按 BPMN 流程 `").append(processId).append("` 的 serviceTask 执行顺序排列；每节标注对应节点（`节点 id` / `jobType`）。")
                .append("每节包含两部分：**①🔎 联网检索指令**（该节点依据场景需联网采集的检索关键词 / 信息源 / 数据字段）、")
                .append("**②🧮 数据加工与分析要求**（该节点数据应如何加工与分析、产出什么结论），并附 **📚 方法论来源**。")
                .append("本次流程不内嵌本体实例数据，大模型须据此真正联网检索、加工、分析后产出该节结论。\n\n");

        sb.append("### 3.1 流程元信息\n\n");
        sb.append("| 项 | 值 |\n| --- | --- |\n");
        sb.append("| 流程实例 Key | `").append(instanceKey).append("` |\n");
        sb.append("| 流程定义 | `").append(processId).append("` |\n");
        sb.append("| 洞察场景 | ").append(sceneLabel).append(" |\n");
        sb.append("| 检索根域 | ").append(domain).append("（").append(retrievalRoot).append("） |\n");
        sb.append("| 实例 IRI | `").append(instanceIri).append("` |\n");
        sb.append("| 本体一致性校验 | **").append(status).append("**（Openllet） |\n");
        sb.append("| SKILL 生成时间 | ").append(Instant.now()).append(" |\n\n");

        appendScenarioSection(sb, scenario, retrievalRoot, domain);

        sb.append("### 3.3 洞察数据摘要\n\n> ").append(summary).append("\n\n");

        sb.append("### 3.4 【五看】按 BPMN 节点逐步构建（场景：").append(sceneLabel).append("）\n\n");
        appendTrendSection(sb, sceneLabel);
        appendMarketCustomerSection(sb, sceneLabel);
        appendCompetitionSection(sb, sceneLabel);
        appendSelfSection(sb, sceneLabel);
        appendOpportunitySection(sb, sceneLabel);

        sb.append("### 3.5 【三定】按 BPMN 节点逐步构建（场景：").append(sceneLabel).append("）\n\n");
        appendControlPointSection(sb, sceneLabel);
        appendObjectiveSection(sb, sceneLabel);
        appendStrategySection(sb, sceneLabel);

        sb.append("### 3.6 数据校验与提示（`Task_Summarize` → `summarize-insight`）\n\n");
        appendDataNotes(sb, status);

        sb.append("---\n\n");
        sb.append("*数据来源：NEInsight 洞察流程（Camunda 实例 `").append(instanceKey).append("`）；")
                .append("洞察维度骨架与逐节点方法论基线取自本体（core:InsightMethod）；")
                .append("各节点「🔎 联网检索指令」所示外部数据由 generate-report 联网采集并标注来源。*\n");
        return sb.toString();
    }

    /**
     * 输出某节点的「🔎 联网检索指令」与「🧮 数据加工与分析要求」。
     *
     * <p>节点小结不再是维度骨架，也不再在 Java 硬编码方法：内容**取自本体**的「洞察方法」
     * （{@code core:InsightMethod}，经数据属性 {@code core:appliesToNode} 与流程节点 jobType 对应），
     * 检索关键词/信息源/需采集字段/加工步骤/产出/来源备注均由本体表达（★铁律21）。
     */
    private void appendMethodFromOntology(StringBuilder sb, String sceneLabel, String jobType) {
        OWLNamedIndividual m = insightMethod(jobType);
        if (m == null) {
            sb.append("> ⚠️ 未在本体中找到节点 `").append(jobType).append("` 的洞察方法（core:InsightMethod）。\n\n");
            return;
        }
        List<String> keywords = dataStrings(m, D_RETRIEVAL_KEYWORD);
        List<String> sources = dataStrings(m, D_DATA_SOURCE);
        List<String> fields = dataStrings(m, D_DATA_FIELD);
        List<String> steps = dataStrings(m, D_ANALYSIS_STEP);

        sb.append("**🔎 联网检索指令**（围绕场景「").append(sceneLabel).append("」联网采集外部公开数据）：\n\n");
        List<String> ks = new ArrayList<>();
        for (String k : keywords) {
            ks.add("「" + sceneLabel + "」+ " + k);
        }
        sb.append("- **检索关键词**：").append(joinOrDash(ks)).append("\n");
        sb.append("- **建议信息源**：").append(joinOrDash(sources)).append("\n");
        sb.append("- **需采集的数据字段**：").append(joinOrDash(fields)).append("\n\n");

        sb.append("**🧮 数据加工与分析要求**：\n\n");
        for (String step : steps) {
            sb.append(step).append("\n");
        }
        sb.append("\n> **产出**：").append(orDash(dataString(m, D_METHOD_OUTPUT))).append("\n\n");
        sb.append("> **📚 方法论来源**：").append(orDash(dataString(m, D_SOURCE_REF))).append("\n\n");
    }

    /** 按流程节点 jobType 从本体检索对应的「洞察方法」个体（core:InsightMethod，经 appliesToNode 匹配）。 */
    private OWLNamedIndividual insightMethod(String jobType) {
        for (OWLNamedIndividual m : globalInstances(C_INSIGHT_METHOD)) {
            if (jobType.equals(dataString(m, D_APPLIES_TO_NODE))) {
                return m;
            }
        }
        return null;
    }

    /** 读取某个体某数据属性的全部取值（排序后返回以保证输出稳定；加工步骤值已含序号前缀）。 */
    private List<String> dataStrings(OWLNamedIndividual ind, String propIri) {
        Set<OWLLiteral> vs = backendService.getDataPropertyValueOfIndividual(ind, propIri);
        List<String> out = new ArrayList<>();
        for (OWLLiteral l : vs) {
            out.add(l.getLiteral());
        }
        Collections.sort(out);
        return out;
    }

    /** 流程起点节点：输入洞察场景（用户唯一业务输入，取代原「洞察域选择 + 域扫描」）。 */
    private void appendScenarioSection(StringBuilder sb, String scenario, String retrievalRoot, String domain) {
        sb.append("### 3.2 输入洞察场景（流程起点）\n\n");
        sb.append("- **洞察场景**：")
                .append(scenario.isBlank() ? "（未提供，按联合域全景展开）" : scenario).append("\n");
        sb.append("- **检索根域**：").append(retrievalRoot).append("（").append(domain)
                .append("）——域概念已并入场景内容表达；本流程不再检索本体业务实例，域仅用于标注流程实例归属\n\n");
    }

    private void appendTrendSection(StringBuilder sb, String sceneLabel) {
        sb.append("#### 节点 1 · 看行业/趋势（`scan-industry-trend` → `industryTrendResult`）\n\n");
        appendMethodFromOntology(sb, sceneLabel, "scan-industry-trend");
    }

    private void appendMarketCustomerSection(StringBuilder sb, String sceneLabel) {
        sb.append("#### 节点 2 · 看市场/客户（`scan-market-customer` → `marketCustomerResult`）\n\n");
        appendMethodFromOntology(sb, sceneLabel, "scan-market-customer");
    }

    private void appendCompetitionSection(StringBuilder sb, String sceneLabel) {
        sb.append("#### 节点 3 · 看竞争（`scan-competition` → `competitionResult`）\n\n");
        appendMethodFromOntology(sb, sceneLabel, "scan-competition");
    }

    private void appendSelfSection(StringBuilder sb, String sceneLabel) {
        sb.append("#### 节点 4 · 看自己（`scan-self` → `selfAssessmentResult`）\n\n");
        appendMethodFromOntology(sb, sceneLabel, "scan-self");
    }

    private void appendOpportunitySection(StringBuilder sb, String sceneLabel) {
        sb.append("#### 节点 5 · 看机会 SPAN（`scan-opportunity` → `opportunityResult`）\n\n");
        appendMethodFromOntology(sb, sceneLabel, "scan-opportunity");
    }

    private void appendControlPointSection(StringBuilder sb, String sceneLabel) {
        sb.append("#### 节点 6 · 定战略控制点（`define-control-point` → `controlPointResult`）\n\n");
        appendMethodFromOntology(sb, sceneLabel, "define-control-point");
    }

    private void appendObjectiveSection(StringBuilder sb, String sceneLabel) {
        sb.append("#### 节点 7 · 定目标（`define-objective` → `objectiveResult`）\n\n");
        appendMethodFromOntology(sb, sceneLabel, "define-objective");
    }

    private void appendStrategySection(StringBuilder sb, String sceneLabel) {
        sb.append("#### 节点 8 · 定策略（`define-strategy` → `strategyResult`）\n\n");
        appendMethodFromOntology(sb, sceneLabel, "define-strategy");
    }

    private void appendDataNotes(StringBuilder sb, String status) {
        sb.append("- 本体一致性校验：**").append(status).append("**")
                .append("CONSISTENT".equals(status) ? "（通过）。\n" : "（未通过，需人工核查）。\n");
        sb.append("- 本流程不再内嵌本体业务实例数据：各节点的结论须由「🔎 联网检索指令」联网采集的外部公开数据支撑，并标注来源。\n");
        sb.append("- 对数据缺失或标注为【待考】的内容（如竞品不指名项），须在报告中注明「待补充」。\n\n");
    }

    private static String orDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private static String joinOrDash(List<String> values) {
        return (values == null || values.isEmpty()) ? "—" : String.join("、", values);
    }

    /** SPAN 分档占位阈值（【待考】）；与本体 SWRL R1/R1b 的 3.0 占位保持一致。 */
    private static final double SPAN_THRESHOLD = 3.0;

    // ---------------- Camunda 作业完成/失败 ----------------

    private void complete(JobClient client, ActivatedJob job, Map<String, Object> variables) {
        client.newCompleteCommand(job.getKey()).variables(variables).send().join();
    }

    private void fail(ActivatedJob job, JobClient client, String errorCode, Exception e) {
        log.error("❌ JobWorker 失败 | type={} | jobKey={}", errorCode, job.getKey(), e);
        client.newThrowErrorCommand(job.getKey())
                .errorCode(errorCode)
                .errorMessage(e.getMessage())
                .send().join();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
