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
 * parse-insight-scenario / return-scenario-clarification /
 * analyze-industry-trend / analyze-market-customer / analyze-competition / analyze-self /
 * identify-opportunities / locate-opportunity-span / send-key-insight-message /
 * cross-validate-insight-consensus / distill-insight-conclusion /
 * define-strategic-control-point / define-objective / define-strategy / generate-insight-skill。
 *
 * <p><b>方法论顺序</b>：严格遵循「先五看、后依五看三定」——场景解析后直接进入五看
 * （看行业/趋势→看市场/客户→看竞争→看自己→看机会）→ 机会点 SPAN → 五看结论交叉验证与机会共识
 * → 洞察结论与关键判断 → 三定（定战略控制点→定目标→定策略）。不再采用「先提假设、再验证假设」。
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

    /** 消息外发处理器（第 8 节点：关键机会/风险消息外发）。惰性创建。 */
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
            "竞争矩阵与竞品拆解", "蓝军推演", "对手财报与战略推演"};

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
    // 节点 10：洞察结论与关键判断（结论驱动，基于五看 + 机会共识）
    // ==========================================================================
    @JobWorker(type = "distill-insight-conclusion", autoComplete = false)
    public void handleInsightConclusion(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = "【洞察场景】\n" + safe(str(vars.get("scenario")))
                    + "\n\n【看行业/趋势】\n" + safe(str(vars.get("industryTrend")))
                    + "\n\n【看市场/客户】\n" + safe(str(vars.get("marketCustomer")))
                    + "\n\n【看竞争】\n" + safe(str(vars.get("competition")))
                    + "\n\n【看自己】\n" + safe(str(vars.get("selfInsight")))
                    + "\n\n【看机会】\n" + safe(str(vars.get("opportunityList")))
                    + "\n\n【机会点优先级（SPAN）】\n" + safe(str(vars.get("opportunityPriority")))
                    + "\n\n【五看结论交叉验证与机会共识】\n" + safe(str(vars.get("crossValidation")));

            String fallback = "### 洞察结论与关键判断（降级框架）\n"
                    + "大模型不可用，以下为待填充框架（结论一律【待考】）：\n"
                    + "- 结论1（行业/趋势）：【待考】 关键判断：… 对三定的指向：…\n"
                    + "- 结论2（市场/客户）：【待考】 关键判断：… 对三定的指向：…\n"
                    + "- 结论3（竞争）：【待考】 关键判断：… 对三定的指向：…\n"
                    + "- 结论4（自身）：【待考】 关键判断：… 对三定的指向：…\n"
                    + "- 结论5（机会）：【待考】 关键判断：… 对三定的指向：…\n\n"
                    + "### 三定输入摘要\n"
                    + "| 结论 | 支撑证据 | 对「定战略控制点/定目标/定策略」的指向 |\n|---|---|---|\n"
                    + "|  |  | 【待考】 |\n";

            String conclusion = runGenerative("洞察结论与关键判断",
                    "你是一名战略洞察负责人。请基于给定的「五看」结论与「五看结论交叉验证与机会共识」，"
                    + "提炼「洞察结论与关键判断」：按 行业/市场/竞争/自身/机会 分支给出结论要点，"
                    + "每条标注支撑证据（无依据者标【待考】）与其对「定战略控制点/定目标/定策略」的指向，"
                    + "并给出「三定输入摘要」表。结论数量严格控制在 3–5 条，"
                    + "不得引入素材之外的事实或数字。" + CONCISE + "总长控制在 800 字以内。",
                    context, fallback);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("insightConclusion", conclusion);
            complete(client, job, result);
            log.info("✅ distill-insight-conclusion | 结论长度={}", conclusion.length());
        } catch (Exception e) {
            fail(job, client, "INSIGHT_CONCLUSION_FAILED", e);
        }
    }

    // ==========================================================================
    // 节点 2：看行业/趋势
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
    // 节点 3：看市场/客户
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
    // 节点 4：看竞争
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
    // 节点 5：看自己
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
    // 节点 6：看机会（机会点识别）
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
    // 节点 7：机会点 SPAN 定位与优先级判定
    // ==========================================================================
    @JobWorker(type = "locate-opportunity-span", autoComplete = false)
    public void handleSpanLocate(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = "【机会点清单】\n" + safe(str(vars.get("opportunityList")))
                    + "\n\n【市场吸引力依据（节点 3）】\n" + safe(str(vars.get("marketCustomer")))
                    + "\n\n【竞争实力依据（节点 4）】\n" + safe(str(vars.get("competition")));

            String priorityText = null;
            String level = null;
            if (llm != null && llm.isAvailable()) {
                String r = llm.chat(
                        "你是一名战略洞察专家。请对机会点清单做正式 SPAN 定位：以「市场吸引力」（节点 3）×"
                        + "「竞争实力/地位」（节点 4）两轴定位象限，映射为「高/中/低」优先级。"
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
                        + "- 两轴：市场吸引力（节点 3）× 竞争实力/地位（节点 4）\n"
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
    // 节点 8：关键机会/风险消息外发
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
    // 节点 9：五看结论交叉验证与机会共识
    // ==========================================================================
    @JobWorker(type = "cross-validate-insight-consensus", autoComplete = false)
    public void handleCrossConsensus(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String context = "【行业/趋势】\n" + safe(str(vars.get("industryTrend")))
                    + "\n\n【市场/客户】\n" + safe(str(vars.get("marketCustomer")))
                    + "\n\n【竞争】\n" + safe(str(vars.get("competition")))
                    + "\n\n【自身】\n" + safe(str(vars.get("selfInsight")))
                    + "\n\n【机会点】\n" + safe(str(vars.get("opportunityList")))
                    + "\n\n【机会点优先级（SPAN）】\n" + safe(str(vars.get("opportunityPriority")));

            String fallback = "### 关键洞察一致性矩阵（降级框架）\n"
                    + "| 洞察 | 行业 | 市场 | 竞争 | 自身 | 机会 | 一致性 |\n|---|---|---|---|---|---|---|\n"
                    + "|  |  |  |  |  |  | 【待考】 |\n\n"
                    + "### 冲突及消解结论\n【待考】\n\n### 机会共识清单\n【待考】\n\n"
                    + "> 仅做研判与共识收敛，不给预测性数值。\n";

            String out = runGenerative("五看结论交叉验证与机会共识",
                    "你是一名战略洞察负责人。请对「五看」结论做交叉验证与冲突消解，并收敛机会共识，输出："
                    + "①《关键洞察一致性矩阵》；②冲突及消解结论；③机会共识清单（去伪存真后的共识机会）。"
                    + "仅做研判与共识收敛、不实做验证、不给预测性数值。" + CONCISE + "总长控制在 800 字以内。",
                    context, fallback);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("crossValidation", out);
            complete(client, job, result);
            log.info("✅ cross-validate-insight-consensus | 长度={}", out.length());
        } catch (Exception e) {
            fail(job, client, "CROSS_VALIDATE_CONSENSUS_FAILED", e);
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
                    + "\n\n【洞察结论与关键判断】\n" + safe(str(vars.get("insightConclusion")));

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
                    + "\n\n【洞察结论与关键判断】\n" + safe(str(vars.get("insightConclusion")));

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
                    + "\n\n【洞察结论与关键判断】\n" + safe(str(vars.get("insightConclusion")));

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
            md.append("> 并对素材中标注【待考】的**待定数据**按「四、待定数据获取指引」补数，\n");
            md.append("> 直接产出《行业洞察报告（五看三定）》。\n\n");

            md.append("## 一、角色设定\n");
            md.append("你是一名资深战略与行业洞察分析师，精通「五看三定」方法论"
                    + "（看行业/看市场/看竞争/看自己/看机会；定战略控制点/定目标/定策略）。"
                    + "你的任务是依据用户提供的洞察素材，产出一份结构完整、结论明确的行业洞察报告；"
                    + "对素材中尚无依据的**待定数据**，须结合本次行业与洞察内容给出可执行的补数方案。\n\n");

            md.append("## 二、任务指令\n");
            md.append("1. 依据下方「三、洞察素材」逐节产出《行业洞察报告（五看三定）》，章节顺序与素材一致：\n");
            md.append("   洞察场景 → 看行业/趋势 → 看市场/客户 → 看竞争 → 看自己 → 看机会 →\n");
            md.append("   机会点优先级（SPAN）→ 五看结论交叉验证与机会共识 → 洞察结论与关键判断 →\n");
            md.append("   定战略控制点 → 定目标 → 定策略。\n");
            md.append("2. 不得引入素材之外的任何新事实、数字、企业或来源。\n");
            md.append("3. 素材中标注 [S#] 之处须在报告中保留来源标注，并可回溯到「五、参考资料」清单。\n");
            md.append("4. 素材中标注【待考】之处不得臆造；应保留【待考】，并**依据「四、待定数据获取指引」**给出需补充的信息。\n");
            md.append("5. 对结构性/方向性/机制性判断可直接给结论；对预测性数值须有来源否则标【待考】。\n");
            md.append("6. 输出规范 Markdown；可补充「执行摘要」「So What（价值提炼）」「有效期与刷新机制」等章节。\n");
            md.append("7. 对本次行业与洞察内容中的**待定数据**，须按「四、待定数据获取指引」逐条给出可执行补数方案：\n");
            md.append("   ①先定义统计口径；②按数据源分层指定首选/备选来源；③给出测算方法（自上而下 / 自下而上 bottom-up）；\n");
            md.append("   ④标注校验与避坑要点；⑤标注数据来源等级（一手访谈 > 权威第三方报告 > 公开新闻 > 内部经验）与置信度（高/中/低）。\n\n");

            // 洞察素材（按「五看三定」顺序内嵌；同时作为「四、待定数据获取指引」【待考】清单的归集来源）
            List<String[]> material = new ArrayList<>();
            material.add(new String[]{"3.1 洞察场景", str(vars.get("scenarioParsed"))});
            material.add(new String[]{"3.2 看行业/趋势", str(vars.get("industryTrend"))});
            material.add(new String[]{"3.3 看市场/客户", str(vars.get("marketCustomer"))});
            material.add(new String[]{"3.4 看竞争", str(vars.get("competition"))});
            material.add(new String[]{"3.5 看自己", str(vars.get("selfInsight"))});
            material.add(new String[]{"3.6 看机会", str(vars.get("opportunityList"))});
            material.add(new String[]{"3.7 机会点优先级（SPAN）", str(vars.get("opportunityPriority"))});
            material.add(new String[]{"3.8 五看结论交叉验证与机会共识", str(vars.get("crossValidation"))});
            material.add(new String[]{"3.9 洞察结论与关键判断", str(vars.get("insightConclusion"))});
            material.add(new String[]{"3.10 定战略控制点", str(vars.get("controlPoints"))});
            material.add(new String[]{"3.11 定目标", str(vars.get("objectives"))});
            material.add(new String[]{"3.12 定策略", str(vars.get("strategies"))});

            md.append("## 三、洞察素材（本次分析，已内嵌）\n");
            for (String[] sec : material) {
                appendSection(md, sec[0], sec[1]);
            }

            appendPendingDataGuide(md, vars, material);
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

    /** 单次生成型节点（五看结论交叉验证与机会共识 / 洞察结论与关键判断 / 控制点 / 目标 / 策略 / 报告润色）的 LLM 调用 + 兜底。 */
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
        md.append("\n## 五、参考资料（联网检索）\n");
        for (Map<String, Object> r : refs) {
            md.append("- [").append(String.valueOf(r.get("id"))).append("] ")
                    .append(String.valueOf(r.get("title"))).append(" — ")
                    .append(String.valueOf(r.get("url"))).append('\n');
        }
    }

    /**
     * 节点 14：追加「待定数据获取指引」——告诉下游大模型如何**依据本次行业与洞察内容**，
     * 对素材中标注【待考】的**待定数据**补数：先定统计口径 → 数据源分层 → 测算方法 → 校验避坑 → 交付优先级，
     * 并自动归集本次素材中的【待考】项形成逐条补数清单。
     *
     * <p><b>确定性拼装</b>（不额外经 MaaS 重写，对齐设计 Q23）；正文为**方法论通用词**
     * （口径/数据源/测算/校验等），**不内置任何行业词表**，行业与目标仅从流程变量/场景解析中读取（守★铁律 26）。
     */
    private static void appendPendingDataGuide(StringBuilder md, Map<String, Object> vars, List<String[]> material) {
        String industry = resolveScenarioField(vars, "industry", "行业/领域");
        String goal = resolveScenarioField(vars, "goal", "洞察目标");

        md.append("\n## 四、待定数据获取指引（如何获取【待考】数据）\n");
        md.append("> 用途：告诉大模型在生成《行业洞察报告》时，针对「三、洞察素材」中标注【待考】的**待定数据**，"
                + "如何**依据本次行业与洞察内容**去**定义口径 → 锁定数据源 → 测算 → 校验**，"
                + "从而补全或给出可执行的补数方案；**不得臆造**。\n");
        md.append("> 本次行业/领域：").append(industry)
                .append("；洞察目标：").append(goal).append("（详见 3.1）。\n\n");

        md.append("### 4.0 前置结论（先定口径，再多源拼数）\n");
        md.append("- 对**垂直细分、无官方统一口径的专有数据**（份额/金额/规模/增速/算力等），通常不存在现成标准化统计；"
                + "须**先书面定义口径**，再**多源拼数 + 自下而上（bottom-up）测算**。\n");
        md.append("- 区分两类口径：①「通用/大盘」市场（多有第三方报告）；②「本次场景的垂直细分/专属」"
                + "（仅用于本行业特定用途；通用口径不计入）。\n");
        md.append("- 仅当有**可溯源来源**时方可给出具体数值；否则**保留【待考】**，并按本节给出补数方案与置信度。\n\n");

        md.append("### 4.1 第一步：先锁定统计口径（最关键，否则数据不可比）\n");
        md.append("先定义口径再取数，交付给数据源/调研：\n");
        md.append("1. **规模口径**：单位与量纲（如 FP32/FP64 与 FP16/BF16；台/卡/容量）；是否含租赁/自建/分时；"
                + "仅训练或含推理；**剔除通用、办公类**部分。\n");
        md.append("2. **投资口径**：硬件（算力/存储/网络）+ 软件（平台/模型/框架/数据治理）+ 服务；"
                + "明确**仅含本场景专项**，还是含通用 IT/工控。\n");
        md.append("3. **份额口径**：按**营收/数量/容量/项目中标额**；并约定**玩家范围**"
                + "（厂商/用户自研/第三方服务商/云厂商/科研团队）。\n");
        md.append("4. **边界口径**：逐项声明「本场景专属」与「通用/临近场景」的纳入或剔除。\n\n");

        md.append("### 4.2 数据源分层（按可信度与获取难度排序）\n");
        md.append("1. **行业官方/协会（免费、基准）**：主管部门、行业协会、标准化机构 —— 用作大盘/分母。\n");
        md.append("2. **第三方咨询（付费，优先可定制专项）**：研究机构、券商深度研报 —— 多为大盘口径，须人工剥离垂直细分。\n");
        md.append("3. **招投标/政府采购/企业公告（最适合 bottom-up，免费可检索）**：公共资源交易平台、央企采购平台、"
                + "企业招标/中标公告 —— 按「关键词 + 项目用途」筛选并剔除无关项目，抓取金额/规格/甲方/厂商。\n");
        md.append("4. **企业端披露（年报/ESG/白皮书/投资者交流）**：披露的投入预算、平台与案例，可估算份额"
                + "（常合并多口径，须拆解）。\n");
        md.append("5. **学术与开源（技术参数参考，市场份额弱）**：论文/数据集/开源仓库 —— 用于典型负载与参数，**不用于份额**。\n");
        md.append("6. **行业访谈 + 德尔菲调研（补缺；垂直细分的可行途径）**：面向负责人/供应商/客户经理收集，"
                + "交叉校验后估算。\n\n");

        md.append("### 4.3 测算方法（两条路线）\n");
        md.append("- **路线 A 自上而下**：大盘 × 细分占比 × 场景折算系数 → 估算值（快、误差大，仅早期研判）。\n");
        md.append("- **路线 B 自下而上（推荐）**：全量采集专项项目 → 清洗对齐口径、剔除无关 → 逐项统计金额/规模/厂商 → "
                + "汇总与份额 → 缺失小项用访谈样本补全 → 标注「已落地/在建/规划」。\n\n");

        md.append("### 4.4 数据校验与避坑\n");
        md.append("1. **单位/量纲陷阱**：不同口径（如 FP64 与 FP16）不可直接对比。\n");
        md.append("2. **重复统计**：共用资源须做配额拆分，不能整份计入。\n");
        md.append("3. **边界混淆**：通用工具/通用算力不计入垂直细分。\n");
        md.append("4. **份额口径说明**：必须标注按何种口径，不同口径份额差异巨大。\n");
        md.append("5. **来源分级**：一手访谈 > 权威第三方报告 > 公开新闻 > 内部经验；无法溯源标【待考】。\n\n");

        md.append("### 4.5 交付优先级（补数动作顺序）\n");
        md.append("1. 先写**口径定义**（发数据源/调研）→ 2. 优先**招投标 + 企业公告**做 bottom-up 样本库 → "
                + "3. 采购**定制专项**调研 → 4. **头部玩家访谈**交叉验证 → "
                + "5. 输出须标注：**估算值、统计边界、样本覆盖度、误差区间**，以及来源等级与置信度。\n\n");

        md.append("### 4.6 本次待定数据清单（据素材自动归集，须逐条给出补数方案）\n");
        List<String> pending = collectPendingItems(material);
        if (pending.isEmpty()) {
            md.append("- （本次素材未出现【待考】；如报告生成过程中新增待定数据，同样按 4.1–4.5 处理。）\n");
        } else {
            for (String item : pending) {
                md.append(item).append('\n');
            }
        }
        md.append("\n> 逐条补数模板：`待定数据 | 口径定义 | 首选来源 | 备选来源 | 测算方法 | 校验要点 | 来源等级/置信度`\n");
    }

    /** 读取行业/目标：优先流程变量，其次从「3.1 场景解析」文本中抽取；均缺省时给出指向说明。 */
    private static String resolveScenarioField(Map<String, Object> vars, String varKey, String label) {
        String v = str(vars.get(varKey));
        if (v != null && !v.isBlank() && !v.contains("待考")) {
            return v.trim();
        }
        String fromParsed = extractField(str(vars.get("scenarioParsed")), label);
        if (!fromParsed.isBlank() && !fromParsed.contains("待考")) {
            return fromParsed;
        }
        return "（见 3.1 场景解析）";
    }

    /** 抽取形如「label：value」的字段值（取到行尾，去掉前导分隔符）；未命中返回空串。 */
    private static String extractField(String text, String label) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int i = text.indexOf(label);
        if (i < 0) {
            return "";
        }
        int start = i + label.length();
        int end = text.indexOf('\n', start);
        if (end < 0) {
            end = text.length();
        }
        return text.substring(start, end).replaceAll("^[\\s：:｜|•·*>-]+", "").trim();
    }

    /** 自动归集素材中所有含【待考】的行，形成「章节 → 待考项」清单（去空行、限长、限条数）。 */
    private static List<String> collectPendingItems(List<String[]> material) {
        List<String> items = new ArrayList<>();
        for (String[] sec : material) {
            String body = sec[1];
            if (body == null || body.isBlank()) {
                continue;
            }
            for (String raw : body.split("\n")) {
                String line = raw.strip();
                if (!line.contains("待考")) {
                    continue;
                }
                String item = line.replaceAll("^[\\-*>\\s]+", "").strip();
                if (item.isEmpty()) {
                    continue;
                }
                // 过滤「降级框架」样板行，以及去掉标记后无实质内容的孤立【待考】行
                if (item.contains("降级框架") || item.contains("待填充") || item.contains("大模型")
                        || item.replace("【待考】", "").replaceAll("[\\s|\\-:：]", "").isEmpty()) {
                    continue;
                }
                items.add("- **" + sec[0] + "**：" + truncate(item, 160));
                if (items.size() >= 40) {
                    return items;
                }
            }
        }
        return items;
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
