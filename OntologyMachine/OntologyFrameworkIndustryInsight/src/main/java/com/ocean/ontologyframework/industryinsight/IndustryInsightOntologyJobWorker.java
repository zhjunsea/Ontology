package com.ocean.ontologyframework.industryinsight;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.utilities.ChatAgent;
import com.ocean.utilities.OntologyWorkerSupport;
import com.ocean.utilities.RabbitMqHandler;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行业洞察（五看三定）流程 {@code IndustryInsightProcess} 的 JobWorker。
 *
 * <p>15 个 {@link JobWorker} 与 {@code ontology/bpmn/industryInsightProcess.bpmn} 的 15 个
 * {@code serviceTask} 一一对应（契约见《IndustryInsight应用-应用架构设计》§4/§5，闸门④已确认）：
 * parse-insight-scenario / return-scenario-clarification / build-initial-hypothesis-tree /
 * analyze-industry-trend / analyze-market-customer / analyze-competition / analyze-self /
 * identify-opportunities / locate-opportunity-span / send-key-insight-message /
 * cross-validate-hypothesis-plan / define-strategic-control-point / define-objective /
 * define-strategy / generate-insight-skill。
 *
 * <p><b>落地口径</b>：全部业务逻辑一律经现存 API（{@link OntologyWorkerSupport}、
 * {@link ChatAgent}、{@link RabbitMqHandler}）实现，不重写平台 API（★铁律 21）。
 * 各分析节点统一采用「经 MaaS 分析（一次调用内依次产出各工具结论并归纳共识/分歧）→ 再次经 MaaS 总结 → 兜底」模式；
 * LLM 未装配/不可用/超时一律降级兜底（确定性框架/模板，结论置【待考】），**不阻断流程**、
 * **不内置任何行业/身份词表**（★26）。
 *
 * <p><b>输出变量</b>：直接写入流程（顶层）变量，与 BPMN 网关条件
 * （{@code validationResult}/{@code priorityLevel}）及
 * {@code IndustryInsightController} 读取口径一致。
 */
@Component
@Profile("IndustryInsightBPMN")
public class IndustryInsightOntologyJobWorker extends OntologyWorkerSupport {

    private static final Logger log = LoggerFactory.getLogger(IndustryInsightOntologyJobWorker.class);

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    /** OBDA（Ontop 虚拟知识图谱）映射与属性文件；第 11 步（ABox 入库）后方配置。 */
    @Value("${ontology.obda-path:}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path:}")
    private String obdaPropertiesPath;

    /** 洞察 SKILL 落盘目录（节点 14 产出「可执行 SKILL」指令文件）。 */
    @Value("${industryinsight.skill.dir:skill}")
    private String skillDir;

    @Value("${env-prepare.rabbitmq.exchange:industryinsight.insight.exchange}")
    private String outboundExchange;

    @Value("${env-prepare.rabbitmq.routing-key:key.insight.alert}")
    private String outboundRoutingKey;

    /**
     * 大模型客户端（通用能力，见 {@link ChatAgent}）。未配置 {@code spring.ai.openai.*}
     * （未装配或不可用）时各节点降级：以确定性框架/模板完成，流程不阻断。
     */
    @Autowired(required = false)
    private ChatAgent llm;

    /**
     * 联网检索取证通道（通用能力，见 {@link IndustryInsightWebSearch}）。默认关闭
     * （{@code industryinsight.websearch.enabled=false}）；未开启时各分析节点跳过取证，行为不变。
     */
    @Autowired(required = false)
    private IndustryInsightWebSearch webSearch;

    /** 消息外发处理器（第 9 节点：关键机会/风险消息外发）。惰性创建。 */
    private volatile RabbitMqHandler rabbitMqHandler;

    /** ★铁律 26：调用 MaaS 时系统提示须显式告知「不允许编造、所有内容必须有依据」。 */
    private static final String NO_FABRICATION =
            "严格遵守：不允许编造。所有结论、数据与出处必须来自用户提供的材料、下方「联网检索资料」"
            + "或可核实的公开常识；凡无依据者一律标注【待考】，严禁虚构数字、来源、企业或事实。"
            + "引用联网检索资料处以 [S#] 标注来源编号；对结构性/方向性/机制性等公开常识级判断可直接给结论，"
            + "但须标注依据等级（检索资料 [S#] / 公开常识）与置信度（高/中/低）；"
            + "对预测性数值、专有数据、企业具体份额/金额/增速须有明确来源，否则标注【待考】。"
            + "使用规范 Markdown 输出。";

    /**
     * ★输出收敛：抑制「通篇【待考】的空壳框架」，要求先给结论、对公开常识级判断直接表态，
     * 仅对依赖用户材料/专有数据的具体数值标注【待考】。用于提升报告的信息密度与可用性。
     */
    private static final String CONCISE =
            "输出务求精炼：先给结论要点，避免冗长铺陈与逐条罗列空模板；"
            + "对行业通用的公开常识级（结构性、方向性、机制性）判断应直接给出明确结论；"
            + "凡有「联网检索资料」可依处，须据此给出结论并以 [S#] 标注来源；"
            + "仅对依赖用户材料、专有数据或需核实来源的具体数值/企业/份额/金额标注【待考】。";

    /** 联网检索取证在各节点的检索主题与查询词面（均为方法论通用词，非行业词表——守铁律 26）。 */
    private static final String[] REF_TOPIC_INDUSTRY_TREND = {"行业趋势与宏观环境", "行业趋势 政策", "市场规模 增速", "技术演进 颠覆性变量"};
    private static final String[] REF_TOPIC_MARKET_CUSTOMER = {"市场与客户需求", "目标客户 需求 痛点", "市场规模 增速", "细分市场 结构"};
    private static final String[] REF_TOPIC_COMPETITION = {"竞争格局", "竞争格局 主要厂商", "市场份额 集中度", "替代品 新进入者"};
    private static final String[] REF_TOPIC_SELF = {"自身资源与能力", "能力 资源 短板", "行业标杆 对标", "交付能力"};
    private static final String[] REF_TOPIC_OPPORTUNITY = {"市场机会点", "市场机会 增长空间", "新兴 蓝海 机会", "投资机会"};
    private static final String[] REF_TOPIC_CONTROL_POINT = {"战略控制点与护城河", "战略控制点 护城河", "标准 专利 生态 壁垒", "行业准入"};
    private static final String[] REF_TOPIC_OBJECTIVE = {"经营目标与 KPI 基准", "经营目标 增长", "KPI 基准 对标", "行业份额 目标"};
    private static final String[] REF_TOPIC_STRATEGY = {"实施策略与路线图", "实施策略 最佳实践", "实施路线图 资源配置", "风险 应对"};

    private static final String[] TOOLS_INDUSTRY_TREND = {
            "PESTEL（宏观环境）", "波特五力", "价值链与利润池", "技术成熟度曲线 Gartner", "颠覆性变量与变化率"};

    private static final String[] TOOLS_MARKET_CUSTOMER = {
            "STP（细分-目标-定位）", "$APPEALS（客户需求）", "JTBD（用户任务）", "KANO（需求属性）", "客户旅程"};

    private static final String[] TOOLS_COMPETITION = {
            "竞争矩阵与竞品拆解", "蓝军推演", "对手财报与战略假设"};

    private static final String[] TOOLS_SELF = {
            "资源-能力盘点与能力雷达", "商业模式画布 BMC", "VRIO", "差距分析"};

    private static final String[] TOOLS_OPPORTUNITY = {
            "机会地图", "安索夫增长矩阵", "机会-能力匹配", "SPAN 预筛", "FAN 预筛", "统一格式机会点清单"};

    @PostConstruct
    public void init() throws Exception {
        log.info("🔧 初始化 IndustryInsightOntologyJobWorker 依赖链...");
        initOntologyPipeline();
        log.info("✅ IndustryInsightOntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);
    }

    @Override
    protected BackendService createBackendService() throws Exception {
        // 第 7~10 步：仅加载 TBox 本体（ABox 分离/入库在第 9~11 步完成后方接入 OBDA）。
        if (obdaPath == null || obdaPath.isBlank()) {
            log.info("[OBDA] 未配置 ontology.obda-path（第 11 步前）—— 仅加载 TBox 本体");
            return BackendService.getInstance(mainOntologyPath);
        }
        boolean reusedObdaHandler = false;
        try {
            OBDAHandler.init(obdaPropertiesPath, obdaPath);
        } catch (IllegalStateException alreadyInitialized) {
            reusedObdaHandler = true;
            log.warn("⚠️ OBDAHandler 已初始化，复用现有实例（同一 JVM 内多 Spring 上下文场景）");
        }
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        if (reusedObdaHandler) {
            BackendService.setInstance(null);
        }
        return BackendService.getInstance(mainOntologyPath, obdaHandler);
    }

    // ==========================================================================
    // 节点 1：洞察场景解析与校验
    // ==========================================================================
    @JobWorker(type = "parse-insight-scenario", autoComplete = false)
    public void handleParseInsightScenario(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String scenario = str(vars.get("scenario"));
            String industry = str(vars.get("industry"));
            String region = str(vars.get("region"));
            String timeWindow = str(vars.get("timeWindow"));

            // 确定性兜底判定（不内置行业词表，仅用通用业务词判断「行业」与「目标」两要素）
            boolean industryOk = (industry != null && !industry.isBlank()) || containsAny(scenario, INDUSTRY_TOKENS);
            boolean goalOk = containsAny(scenario, GOAL_TOKENS);
            boolean tooShort = scenario == null || scenario.trim().length() < 6;

            String parsed = null;
            String source = "兜底";
            if (llm != null && llm.isAvailable()) {
                String sys = "你是一名行业洞察场景解析助手。请从用户提供的「洞察场景」文本中抽取：行业/领域、地域范围、"
                        + "时间窗、洞察目标，并判断场景是否可执行。校验规则：同时含「针对什么行业/领域」与"
                        + "「想洞察什么（目标）」两要素才判「通过」，否则判「不通过」并写明缺失项。"
                        + "请用简洁 Markdown 输出：场景要素抽取 + 校验结论（以「校验结论：通过/不通过」开头一行）。"
                        + NO_FABRICATION;
                String user = "洞察场景：" + safe(scenario)
                        + "\n行业(可选)：" + safe(industry)
                        + "\n地域(可选)：" + safe(region)
                        + "\n时间窗(可选)：" + safe(timeWindow);
                String r = llm.chat(sys, user);
                if (r != null && !r.isBlank()) {
                    parsed = r;
                    source = "MaaS";
                    // LLM 判定优先：显式出现「不通过」即不通过，否则通过
                    if (r.contains("不通过")) {
                        industryOk = false;
                        goalOk = false;
                    }
                } else {
                    log.warn("⚠️ parse-insight-scenario | 大模型无有效返回，降级为确定性解析");
                }
            } else {
                log.info("⏭️ parse-insight-scenario | 大模型未装配/不可用，降级为确定性解析");
            }

            String validationResult;
            String missingElement;
            if (industryOk && goalOk && !tooShort) {
                validationResult = "通过";
                missingElement = "";
            } else {
                validationResult = "不通过";
                List<String> miss = new ArrayList<>();
                if (!industryOk) {
                    miss.add("行业/领域");
                }
                if (!goalOk) {
                    miss.add("洞察目标");
                }
                if (tooShort) {
                    miss.add("文本过简");
                }
                if (miss.isEmpty()) {
                    miss.add("需求要素");
                }
                missingElement = String.join("、", miss);
            }

            if (parsed == null) {
                parsed = "### 场景要素抽取（降级）\n"
                        + "- 行业/领域：" + safe(industry) + "\n"
                        + "- 地域范围：" + safe(region) + "\n"
                        + "- 时间窗：" + safe(timeWindow) + "\n"
                        + "- 洞察目标：【待考】\n"
                        + "- 原始场景：" + safe(scenario) + "\n";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("scenarioParsed", parsed);
            result.put("validationResult", validationResult);
            result.put("missingElement", missingElement);
            result.put("parseSource", source);
            complete(client, job, result);
            log.info("✅ parse-insight-scenario | validationResult={} | missing={} | source={}",
                    validationResult, missingElement, source);
        } catch (Exception e) {
            fail(job, client, "PARSE_SCENARIO_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 1'：返回场景澄清提示
    // ==========================================================================
    @JobWorker(type = "return-scenario-clarification", autoComplete = false)
    public void handleScenarioClarification(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String missing = str(vars.get("missingElement"));

            String text;
            if (missing != null && missing.contains("行业")) {
                text = "未能识别「针对什么行业/领域」。请补充：本次洞察聚焦的行业或领域（如：新能源汽车、云计算、医疗器械…）。";
            } else if (missing != null && missing.contains("目标")) {
                text = "未能识别「想洞察什么」。请补充：本次洞察的目标或待答问题（如：判断未来三年增长机会、评估进入某细分市场的可行性…）。";
            } else if (missing != null && missing.contains("过简")) {
                text = "洞察场景描述过于简略。请补充行业/领域、洞察目标、可选的观察地域与时间窗，以便开展五看三定分析。";
            } else {
                text = "洞察场景信息不足（缺少：" + safe(missing) + "）。请补充行业/领域与洞察目标后重试。";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clarificationText", text);
            complete(client, job, result);
            log.info("✅ return-scenario-clarification | missing={} ", missing);
        } catch (Exception e) {
            fail(job, client, "SCENARIO_CLARIFICATION_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 2：初始假设树
    // ==========================================================================
    @JobWorker(type = "build-initial-hypothesis-tree", autoComplete = false)
    public void handleHypothesisTree(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = scenarioContext(vars);

            String fallback = "### 初始假设树（降级框架）\n"
                    + "大模型不可用，以下为待填充假设模板（结论一律【待考】）：\n"
                    + "- 假设1（行业）：【待考】 待验证点：… 预期证据：…\n"
                    + "- 假设2（市场/客户）：【待考】 待验证点：… 预期证据：…\n"
                    + "- 假设3（竞争）：【待考】 待验证点：… 预期证据：…\n"
                    + "- 假设4（自身）：【待考】 待验证点：… 预期证据：…\n"
                    + "- 假设5（机会）：【待考】 待验证点：… 预期证据：…\n\n"
                    + "### 《假设验证日志》框架\n"
                    + "| 假设 | 验证方法 | 证据 | 结论 | 状态 |\n|---|---|---|---|---|\n"
                    + "|  |  |  |  | 待验证 |\n";

            String tree = runGenerative("初始假设树",
                    "你是一名战略洞察专家。请基于给定的行业洞察场景，先提出 3–5 个核心假设（按 行业/市场/竞争/自身/机会 "
                    + "分支结构化），并为每个假设标注「待验证点」与「预期证据来源」；随后给出《假设验证日志》框架"
                    + "（含 假设/验证方法/证据/结论/状态 列）。假设数量严格控制在 3–5 条。"
                    + CONCISE + "总长控制在 800 字以内。",
                    context, fallback);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hypothesisTree", tree);
            complete(client, job, result);
            log.info("✅ build-initial-hypothesis-tree | 假设树长度={}", tree.length());
        } catch (Exception e) {
            fail(job, client, "HYPOTHESIS_TREE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 3：看行业/趋势
    // ==========================================================================
    @JobWorker(type = "analyze-industry-trend", autoComplete = false)
    public void handleIndustryTrend(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = withEvidence(job, vars, REF_TOPIC_INDUSTRY_TREND, scenarioContext(vars));
            String out = runToolAnalysis("看行业/趋势", TOOLS_INDUSTRY_TREND, context);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("industryTrend", out);
            putRefs(vars, result);
            complete(client, job, result);
            log.info("✅ analyze-industry-trend | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "ANALYZE_INDUSTRY_TREND_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 4：看市场/客户
    // ==========================================================================
    @JobWorker(type = "analyze-market-customer", autoComplete = false)
    public void handleMarketCustomer(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = withEvidence(job, vars, REF_TOPIC_MARKET_CUSTOMER, scenarioContext(vars));
            String out = runToolAnalysis("看市场/客户", TOOLS_MARKET_CUSTOMER, context);
            out += "\n### 客户分层与决策链（DMU）\n"
                    + "客户分层与痛点〔验证状态：待验证/已验证〕、决策链 DMU（决策者/使用者/影响者/采购者）"
                    + "分析要点见各工具结论；无依据者已标【待考】。\n";
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("marketCustomer", out);
            putRefs(vars, result);
            complete(client, job, result);
            log.info("✅ analyze-market-customer | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "ANALYZE_MARKET_CUSTOMER_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 5：看竞争
    // ==========================================================================
    @JobWorker(type = "analyze-competition", autoComplete = false)
    public void handleCompetition(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = withEvidence(job, vars, REF_TOPIC_COMPETITION, scenarioContext(vars));
            String out = runToolAnalysis("看竞争", TOOLS_COMPETITION, context);
            out += "\n### 生态位与博弈\n结合竞争矩阵与蓝军推演，梳理自身在产业生态中的生态位与博弈要点；"
                    + "无依据者已标【待考】。\n";
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("competition", out);
            putRefs(vars, result);
            complete(client, job, result);
            log.info("✅ analyze-competition | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "ANALYZE_COMPETITION_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 6：看自己
    // ==========================================================================
    @JobWorker(type = "analyze-self", autoComplete = false)
    public void handleSelf(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = withEvidence(job, vars, REF_TOPIC_SELF, scenarioContext(vars));
            String out = runToolAnalysis("看自己", TOOLS_SELF, context);
            out += "\n### 战略控制点候选\n基于资源-能力与 VRIO，列出潜在战略控制点候选（类型集合见「定战略控制点」）；"
                    + "无依据者已标【待考】。\n";
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("selfInsight", out);
            putRefs(vars, result);
            complete(client, job, result);
            log.info("✅ analyze-self | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "ANALYZE_SELF_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 7：看机会（机会点识别）
    // ==========================================================================
    @JobWorker(type = "identify-opportunities", autoComplete = false)
    public void handleOpportunity(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = scenarioContext(vars)
                    + "\n\n【前序五看洞察】\n"
                    + "看行业/趋势：\n" + safe(str(vars.get("industryTrend"))) + "\n\n"
                    + "看市场/客户：\n" + safe(str(vars.get("marketCustomer"))) + "\n\n"
                    + "看竞争：\n" + safe(str(vars.get("competition"))) + "\n\n"
                    + "看自己：\n" + safe(str(vars.get("selfInsight"))) + "\n";
            context = withEvidence(job, vars, REF_TOPIC_OPPORTUNITY, context);
            String out = runToolAnalysis("看机会", TOOLS_OPPORTUNITY, context);
            out += "\n> 说明：每个机会点均须可追溯至前序五看洞察（标注来源）；无依据者已标【待考】。\n";
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("opportunityList", out);
            putRefs(vars, result);
            complete(client, job, result);
            log.info("✅ identify-opportunities | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "IDENTIFY_OPPORTUNITIES_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 8：机会点 SPAN 定位与优先级判定
    // ==========================================================================
    @JobWorker(type = "locate-opportunity-span", autoComplete = false)
    public void handleSpanLocate(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = "【机会点清单】\n" + safe(str(vars.get("opportunityList")))
                    + "\n\n【市场吸引力依据（节点4）】\n" + safe(str(vars.get("marketCustomer")))
                    + "\n\n【竞争实力依据（节点5）】\n" + safe(str(vars.get("competition")));

            String priorityText = null;
            String level = null;
            if (llm != null && llm.isAvailable()) {
                String r = llm.chat(
                        "你是一名战略洞察专家。请对机会点清单做正式 SPAN 定位：以「市场吸引力」（节点4）×"
                        + "「竞争实力/地位」（节点5）两轴定位象限，映射为「高/中/低」优先级。"
                        + "请以「优先级：高/中/低」的格式明确给出一行结论，并简述分档依据。" + NO_FABRICATION,
                        context);
                if (r != null && !r.isBlank()) {
                    priorityText = r;
                    level = extractPriorityLevel(r);
                } else {
                    log.warn("⚠️ locate-opportunity-span | 大模型无有效返回，降级为确定性分档");
                }
            } else {
                log.info("⏭️ locate-opportunity-span | 大模型未装配/不可用，降级为确定性分档");
            }

            if (level == null) {
                level = "中";
                priorityText = "### SPAN 定位与优先级（降级：确定性分档）\n"
                        + "- 两轴：市场吸引力（节点4）× 竞争实力/地位（节点5）\n"
                        + "- 分档阈值【待考】；未获有效分档输入，默认档位=中\n"
                        + "- 机会点清单见流程变量 opportunityList\n";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("opportunityPriority", priorityText);
            result.put("priorityLevel", level);
            complete(client, job, result);
            log.info("✅ locate-opportunity-span | priorityLevel={}", level);
        } catch (Exception e) {
            fail(job, client, "SPAN_LOCATE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 9：关键机会/风险消息外发
    // ==========================================================================
    @JobWorker(type = "send-key-insight-message", autoComplete = false)
    public void handleSendMessage(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            List<String> related = new ArrayList<>();
            if (str(vars.get("opportunityList")) != null) {
                related.add(str(vars.get("opportunityList")));
            }
            if (str(vars.get("industryTrend")) != null) {
                related.add("看行业/趋势要点见流程变量 industryTrend");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("类型", "关键机会/风险外发");
            payload.put("优先级", str(vars.get("priorityLevel")) == null ? "高" : str(vars.get("priorityLevel")));
            payload.put("描述", safe(str(vars.get("opportunityPriority"))));
            payload.put("关联洞察", related);
            payload.put("scenarioIri", "http://example.org/industryinsight#Scenario_" + job.getProcessInstanceKey());

            String outbound;
            try {
                RabbitMqHandler mq = mq();
                mq.send(outboundExchange, outboundRoutingKey, payload);
                outbound = "已投递 | exchange=" + outboundExchange + " | routingKey=" + outboundRoutingKey;
                log.info("✅ send-key-insight-message | {}", outbound);
            } catch (Exception sendEx) {
                outbound = "投递失败（降级，不阻断）：" + sendEx.getMessage();
                log.warn("⚠️ send-key-insight-message | {}", outbound);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("outboundMessage", outbound);
            complete(client, job, result);
        } catch (Exception e) {
            fail(job, client, "SEND_KEY_INSIGHT_MESSAGE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 10：洞察交叉验证・冲突消解与关键假设/验证计划
    // ==========================================================================
    @JobWorker(type = "cross-validate-hypothesis-plan", autoComplete = false)
    public void handleCrossValidate(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = "【行业/趋势】\n" + safe(str(vars.get("industryTrend")))
                    + "\n\n【市场/客户】\n" + safe(str(vars.get("marketCustomer")))
                    + "\n\n【竞争】\n" + safe(str(vars.get("competition")))
                    + "\n\n【自身】\n" + safe(str(vars.get("selfInsight")))
                    + "\n\n【机会点】\n" + safe(str(vars.get("opportunityList")))
                    + "\n\n【假设树/日志】\n" + safe(str(vars.get("hypothesisTree")));

            String fallback = "### 关键洞察一致性矩阵（降级框架）\n"
                    + "| 洞察 | 行业 | 市场 | 竞争 | 自身 | 机会 | 一致性 |\n|---|---|---|---|---|---|---|\n"
                    + "|  |  |  |  |  |  | 【待考】 |\n\n"
                    + "### 冲突消解\n【待考】\n\n### 关键假设清单\n【待考】\n\n### 验证计划\n【待考】\n\n"
                    + "> 只做显式化，不实做验证。\n";

            String out = runGenerative("交叉验证与假设计划",
                    "你是一名战略洞察负责人。请对五看结论做交叉验证与冲突消解，并显式化假设，输出："
                    + "①《关键洞察一致性矩阵》；②冲突及消解结论；③待验证假设标记；④关键假设清单；"
                    + "⑤验证计划（如何验证/所需证据）；⑥假设-洞察映射。只做显式化、不实做验证。"
                    + CONCISE + "总长控制在 800 字以内。",
                    context, fallback);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("crossValidation", out);
            result.put("consistencyMatrix", out);
            result.put("keyAssumptions", extractSection(out, "关键假设清单", out));
            result.put("validationPlans", extractSection(out, "验证计划", out));
            complete(client, job, result);
            log.info("✅ cross-validate-hypothesis-plan | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "CROSS_VALIDATE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 11：定战略控制点
    // ==========================================================================
    @JobWorker(type = "define-strategic-control-point", autoComplete = false)
    public void handleControlPoint(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = "【机会点优先级】\n" + safe(str(vars.get("opportunityPriority")))
                    + "\n\n【看自己】\n" + safe(str(vars.get("selfInsight")))
                    + "\n\n【看竞争】\n" + safe(str(vars.get("competition")))
                    + "\n\n【关键假设】\n" + safe(str(vars.get("keyAssumptions")));

            String fallback = "### 定战略控制点（降级框架）\n"
                    + "候选控制点须落在「战略控制点类型集合」内（如：标准/专利/品牌/渠道/客户关系/数据/生态等），"
                    + "并给出筛选矩阵与契合矩阵；聚焦 2–3 个。\n"
                    + "- 控制点1：【待考】 类型：… 护城河（含领先指标）：…\n"
                    + "- 控制点2：【待考】 类型：… 护城河（含领先指标）：…\n"
                    + "- （可选）控制点3：【待考】\n";

            context = withEvidence(job, vars, REF_TOPIC_CONTROL_POINT, context);
            String out = runGenerative("定战略控制点",
                    "你是一名战略顾问。请依「定战略控制点」明确「凭什么持续赢」，从战略控制点类型集合出发，"
                    + "给出候选清单与筛选矩阵、控制点-机会点契合矩阵、护城河可持续性评估（含领先指标）、"
                    + "业务设计（BLM）要点；最终聚焦 2–3 个关键控制点。"
                    + CONCISE + "总长控制在 900 字以内。",
                    context, fallback);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("controlPoints", out);
            putRefs(vars, result);
            complete(client, job, result);
            log.info("✅ define-strategic-control-point | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "DEFINE_CONTROL_POINT_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 12：定目标（SMART 校验）
    // ==========================================================================
    @JobWorker(type = "define-objective", autoComplete = false)
    public void handleObjective(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = "【战略控制点】\n" + safe(str(vars.get("controlPoints")))
                    + "\n\n【机会点优先级】\n" + safe(str(vars.get("opportunityPriority")))
                    + "\n\n【关键假设】\n" + safe(str(vars.get("keyAssumptions")));

            String fallback = "### 定目标（降级框架）\n"
                    + "对每项目标做 SMART 五要素齐备性校验（具体/可衡量/可达成/相关/有时限）；"
                    + "量化数值与时限由业务填入，缺失一律【待考】。\n"
                    + "| 目标 | S | M | A | R | T | 齐备性 |\n|---|---|---|---|---|---|---|\n"
                    + "| 【待考】 |  |  |  |  |  | 待填 |\n\n"
                    + "控制点-目标对齐矩阵、目标分解与里程碑、BSC 与战略地图、OGSM 见正文。\n";

            context = withEvidence(job, vars, REF_TOPIC_OBJECTIVE, context);
            String out = runGenerative("定目标（SMART）",
                    "你是一名战略规划专家。请依「定目标」把方向转化为可衡量里程碑：给出目标清单并逐项做 SMART"
                    + "五要素校验（具体/可衡量/可达成/相关/有时限），补控制点-目标对齐矩阵、目标分解与里程碑、"
                    + "BSC 与战略地图、OGSM。仅校验量化指标/时限/相关性齐备性；数值须由业务填入或来自检索资料，"
                    + "无据一律【待考】。"
                    + CONCISE + "总长控制在 900 字以内。",
                    context, fallback);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("objectives", out);
            putRefs(vars, result);
            complete(client, job, result);
            log.info("✅ define-objective | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "DEFINE_OBJECTIVE_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 13：定策略（含定节奏/定路径）
    // ==========================================================================
    @JobWorker(type = "define-strategy", autoComplete = false)
    public void handleStrategy(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = "【战略控制点】\n" + safe(str(vars.get("controlPoints")))
                    + "\n\n【目标】\n" + safe(str(vars.get("objectives")))
                    + "\n\n【看竞争】\n" + safe(str(vars.get("competition")))
                    + "\n\n【看自己】\n" + safe(str(vars.get("selfInsight")))
                    + "\n\n【关键假设】\n" + safe(str(vars.get("keyAssumptions")));

            String fallback = "### 定策略（降级框架）\n"
                    + "关键举措分解与策略-目标映射、资源投入排序、风险应对矩阵与里程碑、安索夫矩阵、4P、"
                    + "战略解码表/一页纸战略、定节奏/定路径（速赢/攻坚/布局）、洞察-策略映射（关键洞察转化表，"
                    + "每条策略须≥1 洞察）、资源匹配度。\n"
                    + "- 策略1：【待考】 关联洞察：… 资源匹配度：…\n"
                    + "- 策略2：【待考】 关联洞察：… 资源匹配度：…\n";

            context = withEvidence(job, vars, REF_TOPIC_STRATEGY, context);
            String out = runGenerative("定策略",
                    "你是一名战略顾问。请依「定策略」做出「做什么、不做什么」的取舍：给出关键举措分解与策略-目标映射、"
                    + "资源投入排序、风险应对矩阵与里程碑、安索夫矩阵、4P、战略解码表/一页纸战略、"
                    + "定节奏/定路径（速赢/攻坚/布局）、洞察-策略映射（关键洞察转化表，每条策略须≥1 洞察）、资源匹配度。"
                    + CONCISE + "总长控制在 900 字以内。",
                    context, fallback);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("strategies", out);
            result.put("onePageStrategy", extractSection(out, "一页纸战略", out));
            result.put("keyInsightConversionTable", extractSection(out, "洞察-策略映射", out));
            putRefs(vars, result);
            complete(client, job, result);
            log.info("✅ define-strategy | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "DEFINE_STRATEGY_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 14：生成洞察 SKILL（可执行指令文件）
    // ==========================================================================
    @JobWorker(type = "generate-insight-skill", autoComplete = false)
    public void handleGenerateSkill(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            StringBuilder md = new StringBuilder();
            md.append("# 行业洞察 SKILL（五看三定）— 可执行指令\n\n");
            md.append("> 本文件是「可执行 SKILL」：把本文件完整内容作为提示词输入任一具备推理能力的大模型，\n");
            md.append("> 模型将依据下方「一、角色设定」与「二、任务指令」，结合已内嵌的本次洞察素材，\n");
            md.append("> 直接产出《行业洞察报告（五看三定）》。\n\n");

            md.append("## 一、角色设定\n");
            md.append("你是一名资深战略与行业洞察分析师，精通「五看三定」方法论"
                    + "（看行业/看市场/看竞争/看自己/看机会；定战略控制点/定目标/定策略）。"
                    + "你的任务是依据用户提供的洞察素材，产出一份结构完整、结论明确的行业洞察报告。\n\n");

            md.append("## 二、任务指令\n");
            md.append("1. 依据下方「三、洞察素材」逐节产出《行业洞察报告（五看三定）》，章节顺序与素材一致：\n");
            md.append("   洞察场景 → 初始假设树 → 看行业/趋势 → 看市场/客户 → 看竞争 → 看自己 → 看机会 →\n");
            md.append("   机会点优先级（SPAN）→ 交叉验证与关键假设/验证计划 → 定战略控制点 → 定目标 → 定策略。\n");
            md.append("2. 不得引入素材之外的任何新事实、数字、企业或来源。\n");
            md.append("3. 素材中标注 [S#] 之处须在报告中保留来源标注，并可回溯到「四、参考资料」清单。\n");
            md.append("4. 素材中标注【待考】之处不得臆造；应保留【待考】并给出需补充的信息。\n");
            md.append("5. 对结构性/方向性/机制性判断可直接给结论；对预测性数值须有来源否则标【待考】。\n");
            md.append("6. 输出规范 Markdown；可补充「执行摘要」「So What（价值提炼）」「有效期与刷新机制」等章节。\n\n");

            md.append("## 三、洞察素材（本次分析，已内嵌）\n");
            appendSection(md, "3.1 洞察场景", str(vars.get("scenarioParsed")));
            appendSection(md, "3.2 初始假设树", str(vars.get("hypothesisTree")));
            appendSection(md, "3.3 看行业/趋势", str(vars.get("industryTrend")));
            appendSection(md, "3.4 看市场/客户", str(vars.get("marketCustomer")));
            appendSection(md, "3.5 看竞争", str(vars.get("competition")));
            appendSection(md, "3.6 看自己", str(vars.get("selfInsight")));
            appendSection(md, "3.7 看机会", str(vars.get("opportunityList")));
            appendSection(md, "3.8 机会点优先级（SPAN）", str(vars.get("opportunityPriority")));
            appendSection(md, "3.9 交叉验证与关键假设/验证计划", str(vars.get("crossValidation")));
            appendSection(md, "3.10 定战略控制点", str(vars.get("controlPoints")));
            appendSection(md, "3.11 定目标", str(vars.get("objectives")));
            appendSection(md, "3.12 定策略", str(vars.get("strategies")));
            appendReferences(md, vars);

            String skill = md.toString().trim();

            String skillPath = "";
            try {
                Path dir = Paths.get(skillDir);
                Files.createDirectories(dir);
                Path file = dir.resolve("industryinsight-" + job.getProcessInstanceKey() + ".md");
                Files.writeString(file, skill, StandardCharsets.UTF_8);
                skillPath = file.toAbsolutePath().toString();
            } catch (Exception ioEx) {
                log.warn("⚠️ generate-insight-skill | SKILL 落盘失败（降级，不阻断）：{}", ioEx.getMessage());
            }

            // 结构校验：五看三定各环节齐备性（对标本体 8 大域）。校验对象为已内嵌素材的 SKILL 正文。
            List<String> required = List.of("看行业", "看市场", "看竞争", "看自己", "看机会",
                    "定战略控制点", "定目标", "定策略");
            List<String> missing = new ArrayList<>();
            for (String r : required) {
                if (!skill.contains(r)) {
                    missing.add(r);
                }
            }
            String ontologyValidationStatus = missing.isEmpty()
                    ? "结构校验：通过（五看三定各环节齐备）"
                    : "结构校验：缺失环节=" + String.join("/", missing);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("insightSummary", skill);
            result.put("insightSkillPath", skillPath);
            result.put("deliverables", "行业洞察 SKILL（Markdown）：" + skillPath
                    + "；含角色设定 + 任务指令 + 五看三定洞察素材 + 参考资料，可输入大模型直接生成洞察报告");
            result.put("ontologyValidationStatus", ontologyValidationStatus);
            complete(client, job, result);
            log.info("✅ generate-insight-skill | insightSkillPath={} | {}", skillPath, ontologyValidationStatus);
        } catch (Exception e) {
            fail(job, client, "GENERATE_SKILL_FAILED", e);
        }
    }

    // ==========================================================================
    // 通用：LLM 通道（一次调用合并「各工具分析 + 求同存异」→ 总结 → 兜底）
    // ==========================================================================

    /** 分析节点统一模式：一次调用合并「逐工具分析 + 求同存异」→ 再次经 MaaS 总结；LLM 不可用则兜底。 */
    private String runToolAnalysis(String nodeLabel, String[] tools, String context) {
        if (llm != null && llm.isAvailable()) {
            String toolList = String.join("、", tools);
            String merged = llm.chat(
                    "你是一名资深战略与行业洞察分析专家。请针对给定的行业洞察场景做「" + nodeLabel + "」分析。"
                    + "请依次运用下列分析工具：[" + toolList + "]，对每个工具分别给出结构化要点；"
                    + "随后归纳各工具一致的观点（共识）与彼此不一致或互补的观点（分歧）。"
                    + CONCISE + "总输出控制在 1200 字以内。" + NO_FABRICATION,
                    context);
            if (merged != null && !merged.isBlank()) {
                String summary = llm.chat(
                        "你是一名战略洞察负责人。请基于下列「" + nodeLabel + "」的各工具分析与共识/分歧，输出总结要点。"
                        + CONCISE + "总结控制在 600 字以内；若本环节因输入信息不足而无法给出具体数值，"
                        + "应给出方向性/结构性判断结论，而非通篇【待考】。" + NO_FABRICATION,
                        "【各工具分析与共识/分歧】\n" + merged);
                StringBuilder sb = new StringBuilder();
                sb.append("### 各工具分析结论与共识/分歧\n").append(merged);
                sb.append("\n### 总结\n").append(safe(summary)).append('\n');
                return sb.toString();
            }
            log.warn("⚠️ {} | 大模型无有效返回，降级为模板化框架", nodeLabel);
        } else {
            log.info("⏭️ {} | 大模型未装配/不可用，降级为模板化框架", nodeLabel);
        }
        return fallbackFramework(nodeLabel, tools);
    }

    /** 单次生成型节点（假设树/交叉验证/控制点/目标/策略/报告润色）的 LLM 调用 + 兜底。 */
    private String runGenerative(String nodeLabel, String systemPrompt, String userPrompt, String fallbackText) {
        if (llm != null && llm.isAvailable()) {
            String r = llm.chat(systemPrompt + " " + NO_FABRICATION, userPrompt);
            if (r != null && !r.isBlank()) {
                return r;
            }
            log.warn("⚠️ {} | 大模型无有效返回，降级为模板化框架", nodeLabel);
        } else {
            log.info("⏭️ {} | 大模型未装配/不可用，降级为模板化框架", nodeLabel);
        }
        return fallbackText;
    }

    // ==========================================================================
    // 通用：联网检索取证（可溯源公开资料 → 注入提示词 + 汇总来源清单）
    // ==========================================================================

    /**
     * 为某分析节点联网检索公开资料并渲染为证据块，同时把来源追加进流程变量 {@code searchReferences}。
     *
     * <p>仅在「联网检索已开启 <b>且</b> 大模型可用」时执行（未开启 / LLM 不可用时证据无处可用，
     * 不发起外部调用）；未开启 / 失败 / 超时 / 无来源一律返回空串，由调用方沿用既有降级，**不阻断**。
     * 铁律 26：检索资料作为「依据」注入，要求模型对其引用并标注 [S#]，杜绝无据编造。
     *
     * @param topicAndFacets {@code [0]} 为检索主题，其余为查询词面（均为方法论通用词）
     */
    private String gatherEvidence(ActivatedJob job, Map<String, Object> vars, String[] topicAndFacets) {
        if (webSearch == null || !webSearch.isEnabled() || llm == null || !llm.isAvailable()) {
            return "";
        }
        List<String> queries = searchQueries(vars, topicAndFacets);
        if (queries.isEmpty()) {
            return "";
        }
        String objective = searchObjective(vars, topicAndFacets[0]);
        IndustryInsightWebSearch.Result result =
                webSearch.search(objective, queries, "industryinsight-" + job.getProcessInstanceKey());
        if (result == null || result.sources().isEmpty()) {
            return "";
        }
        List<Map<String, Object>> refs = readRefs(vars);
        String block = webSearch.renderEvidence(result, refs.size());
        int n = refs.size();
        for (IndustryInsightWebSearch.Source s : result.sources()) {
            n++;
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("id", "S" + n);
            ref.put("title", s.title() == null ? "" : s.title());
            ref.put("url", s.url() == null ? "" : s.url());
            refs.add(ref);
        }
        vars.put("searchReferences", refs);
        log.info("🔎 {} | 联网检索取证 {} 条来源", topicAndFacets[0], result.sources().size());
        return block;
    }

    /** 在既有上下文后追加本次检索证据块；无证据（未开启 / 失败 / 无来源）时原样返回。 */
    private String withEvidence(ActivatedJob job, Map<String, Object> vars, String[] topicAndFacets, String context) {
        String evidence = gatherEvidence(job, vars, topicAndFacets);
        if (evidence.isBlank()) {
            return context;
        }
        return context + "\n\n【联网检索资料（公开来源，可据此引用并标注 [S#]）】\n" + evidence;
    }

    /** 读取流程变量中已累计的检索来源（拷贝为可写列表）。 */
    private static List<Map<String, Object>> readRefs(Map<String, Object> vars) {
        List<Map<String, Object>> refs = new ArrayList<>();
        Object existing = vars.get("searchReferences");
        if (existing instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    refs.add(copy);
                }
            }
        }
        return refs;
    }

    /** 把已累计的检索来源写入结果变量，令其持久化到流程作用域（供报告节点汇总）。 */
    private static void putRefs(Map<String, Object> vars, Map<String, Object> result) {
        Object refs = vars.get("searchReferences");
        if (refs instanceof List<?> l && !l.isEmpty()) {
            result.put("searchReferences", refs);
        }
    }

    private static String searchObjective(Map<String, Object> vars, String topic) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, str(vars.get("industry")));
        appendPart(sb, str(vars.get("region")));
        appendPart(sb, str(vars.get("timeWindow")));
        appendPart(sb, truncate(str(vars.get("scenario")), 60));
        appendPart(sb, topic);
        return sb.toString();
    }

    private static List<String> searchQueries(Map<String, Object> vars, String[] topicAndFacets) {
        String industry = str(vars.get("industry"));
        String base = (industry != null && !industry.isBlank()) ? industry : truncate(str(vars.get("scenario")), 40);
        String region = str(vars.get("region"));
        String timeWindow = str(vars.get("timeWindow"));
        List<String> queries = new ArrayList<>();
        for (int i = 1; i < topicAndFacets.length; i++) {
            StringBuilder sb = new StringBuilder();
            appendPart(sb, base);
            appendPart(sb, region);
            appendPart(sb, topicAndFacets[i]);
            appendPart(sb, timeWindow);
            if (sb.length() > 0) {
                queries.add(sb.toString());
            }
        }
        return queries;
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.trim());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private static String joinTools(Map<String, String> perTool) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : perTool.entrySet()) {
            sb.append("- **").append(e.getKey()).append("**：").append(safe(e.getValue())).append('\n');
        }
        return sb.toString();
    }

    private static String fallbackFramework(String nodeLabel, String[] tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(nodeLabel).append("（降级框架）\n");
        sb.append("当前大模型通道不可用，以下为待填充的分析框架（结论一律【待考】）。\n\n");
        for (String t : tools) {
            sb.append("- **").append(t).append("**：【待考】待补充结论与出处\n");
        }
        return sb.toString();
    }

    /** 从文本中抽取以 {@code marker} 起始的段落；未命中则返回默认值。 */
    private static String extractSection(String text, String marker, String defaultText) {
        if (text == null) {
            return defaultText;
        }
        int idx = text.indexOf(marker);
        if (idx < 0) {
            return defaultText;
        }
        int end = text.length();
        for (String next : new String[]{"\n###", "\n##"}) {
            int n = text.indexOf(next, idx + marker.length());
            if (n >= 0 && n < end) {
                end = n;
            }
        }
        String section = text.substring(idx, end).trim();
        return section.isEmpty() ? defaultText : section;
    }

    /** 从文本中判定优先级档位（高/中/低）；无法判定返回 {@code null}（由调用方兜底）。 */
    private static String extractPriorityLevel(String text) {
        if (text == null) {
            return null;
        }
        if (text.contains("高优先级") || text.contains("优先级：高") || text.contains("优先级:高")) {
            return "高";
        }
        if (text.contains("低优先级") || text.contains("优先级：低") || text.contains("优先级:低")) {
            return "低";
        }
        if (text.contains("中优先级") || text.contains("优先级：中") || text.contains("优先级:中")) {
            return "中";
        }
        return null;
    }

    /** 在报告末尾追加「参考资料（联网检索）」来源清单（仅当存在检索来源时）。 */
    private static void appendReferences(StringBuilder md, Map<String, Object> vars) {
        List<Map<String, Object>> refs = readRefs(vars);
        if (refs.isEmpty()) {
            return;
        }
        md.append("\n## 参考资料（联网检索）\n");
        for (Map<String, Object> r : refs) {
            md.append("- [").append(String.valueOf(r.get("id"))).append("] ")
                    .append(String.valueOf(r.get("title"))).append(" — ")
                    .append(String.valueOf(r.get("url"))).append('\n');
        }
    }

    private static void appendSection(StringBuilder sb, String title, String body) {
        sb.append("\n## ").append(title).append('\n');
        sb.append(body == null || body.isBlank() ? "【待考】" : body).append('\n');
    }

    private static String scenarioContext(Map<String, Object> vars) {
        StringBuilder sb = new StringBuilder();
        sb.append("【洞察场景】\n").append(safe(str(vars.get("scenario")))).append('\n');
        Object parsed = vars.get("scenarioParsed");
        if (parsed != null) {
            sb.append("\n【场景解析】\n").append(safe(str(parsed))).append('\n');
        }
        Object tree = vars.get("hypothesisTree");
        if (tree != null) {
            sb.append("\n【初始假设树】\n").append(safe(str(tree))).append('\n');
        }
        return sb.toString();
    }

    private RabbitMqHandler mq() {
        RabbitMqHandler h = rabbitMqHandler;
        if (h == null) {
            synchronized (this) {
                if (rabbitMqHandler == null) {
                    rabbitMqHandler = new RabbitMqHandler();
                }
                h = rabbitMqHandler;
            }
        }
        return h;
    }

    private void complete(JobClient client, ActivatedJob job, Map<String, Object> result) {
        client.newCompleteCommand(job.getKey()).variables(result).send().join();
    }

    private void fail(ActivatedJob job, JobClient client, String errorCode, Exception e) {
        log.error("❌ JobWorker 失败 | type={} | jobKey={}", errorCode, job.getKey(), e);
        client.newThrowErrorCommand(job.getKey())
                .errorCode(errorCode)
                .errorMessage(e.getMessage())
                .send()
                .join();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "【待考】" : s;
    }

    // 通用业务词（非行业词表），用于确定性兜底的「行业」与「目标」要素判定
    private static final String[] INDUSTRY_TOKENS = {"行业", "领域", "赛道", "产业"};
    private static final String[] GOAL_TOKENS = {"洞察", "分析", "研究", "趋势", "机会", "前景", "判断", "评估", "策略", "增长"};

    private static boolean containsAny(String text, String[] tokens) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String t : tokens) {
            if (text.contains(t)) {
                return true;
            }
        }
        return false;
    }
}
