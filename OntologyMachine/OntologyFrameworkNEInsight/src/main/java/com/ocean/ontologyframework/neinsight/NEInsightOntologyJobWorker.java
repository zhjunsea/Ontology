package com.ocean.ontologyframework.neinsight;

import com.ocean.openlletresolver.BackendService;
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
 * <p>13 个 {@code @JobWorker} 与 {@code ontology/bpmn/neInsightProcess.bpmn} 的 13 个
 * {@code serviceTask} 一一对应（契约见《NEInsight应用-应用架构设计》§4）。
 * 全部业务逻辑经现存 API（{@link BackendService} 等）实现，不硬编码业务数据（★铁律21）。
 */
@Component
@Profile("NEInsightBPMN")
public class NEInsightOntologyJobWorker extends OntologyWorkerSupport {

    private static final Logger log = LoggerFactory.getLogger(NEInsightOntologyJobWorker.class);

    // ==================== 命名空间（与 ontology/neinsight-all.owl 一致） ====================
    private static final String CORE_NS = "http://example.org/neinsight/core#";
    private static final String ENV_NS = "http://example.org/neinsight/env#";
    private static final String DEC_NS = "http://example.org/neinsight/decision#";
    private static final String ABOX_NS = "http://example.org/neinsight/abox#";

    // ==================== 对象属性 ====================
    private static final String P_HAS_ELEMENT = CORE_NS + "hasElement";
    private static final String P_HAS_MACRO_FACTOR = ENV_NS + "hasMacroFactor";
    private static final String P_INDICATES_VALUE_TRANSFER = ENV_NS + "indicatesValueTransfer";
    private static final String P_HAS_PAIN_POINT = ENV_NS + "hasPainPoint";
    private static final String P_HAS_NEED = ENV_NS + "hasNeed";
    private static final String P_INVOLVES_COMPETITOR = ENV_NS + "involvesCompetitor";
    private static final String P_HAS_CAPABILITY = ENV_NS + "hasCapability";
    private static final String P_HAS_SHORT_BOARD = ENV_NS + "hasShortBoard";
    private static final String P_HAS_SPAN_QUADRANT = ENV_NS + "hasSpanQuadrant";
    private static final String P_ADDRESSES_OPPORTUNITY = DEC_NS + "addressesOpportunity";
    private static final String P_HAS_CONTROL_POINT_TYPE = DEC_NS + "hasControlPointType";
    private static final String P_STRATEGY_OF = DEC_NS + "strategyOf";

    // ==================== 数据属性 ====================
    private static final String D_MARKET_ATTRACTIVENESS = ENV_NS + "marketAttractiveness";
    private static final String D_COMPETITIVE_POSITION = ENV_NS + "competitivePosition";
    private static final String D_OPPORTUNITY_PRIORITY = ENV_NS + "opportunityPriority";
    private static final String D_TREND_DIRECTION = ENV_NS + "trendDirection";
    private static final String D_PAIN_SEVERITY = ENV_NS + "painSeverity";
    private static final String D_CAPABILITY_SCORE = ENV_NS + "capabilityScore";
    private static final String D_FLAGGED_FOR_ALERT = ENV_NS + "flaggedForAlert";
    private static final String D_CP_TYPE_CODE = DEC_NS + "cpTypeCode";
    private static final String D_HAS_METRIC = DEC_NS + "hasMetric";
    private static final String D_TARGET_VALUE = DEC_NS + "targetValue";
    private static final String D_HAS_TIMEFRAME = DEC_NS + "hasTimeframe";
    private static final String D_IS_MEASURABLE = DEC_NS + "isMeasurable";
    private static final String D_IS_RELEVANT = DEC_NS + "isRelevant";
    private static final String D_SMART_COMPLIANT = DEC_NS + "smartCompliant";

    // ==================== 类 ====================
    private static final String C_INDUSTRY_TREND = ENV_NS + "IndustryTrend";
    private static final String C_MARKET = ENV_NS + "Market";
    private static final String C_CUSTOMER = ENV_NS + "Customer";
    private static final String C_COMPETITIVE_LANDSCAPE = ENV_NS + "CompetitiveLandscape";
    private static final String C_SELF_ASSESSMENT = ENV_NS + "SelfAssessment";
    private static final String C_OPPORTUNITY = ENV_NS + "Opportunity";
    private static final String C_CONTROL_POINT = DEC_NS + "StrategicControlPoint";
    private static final String C_CONTROL_POINT_TYPE = DEC_NS + "ControlPointType";
    private static final String C_OBJECTIVE = DEC_NS + "Objective";
    private static final String C_STRATEGY = DEC_NS + "Strategy";

    /** BPMN 流程定义 id（与 ontology/bpmn/neInsightProcess.bpmn 一致）。 */
    private static final String INSIGHT_PROCESS_ID = "NewEnergyInsightProcess";

    /**
     * 内部 ABox 检索根域。
     *
     * <p>域概念已并入输入场景表达（前端不再选择洞察域、BPMN 亦无域名关），
     * 故检索根域固定为「联合域」——覆盖 IT 空间与云空间全部要素，沿 {@code hasElement}
     * 展开的检索口径保持不变；场景差异体现在 SKILL 内容的组织与表述上。
     */
    private static final String DEFAULT_DOMAIN = "COMBINED";

    /** SPAN 分档占位阈值（【待考】）；与本体 SWRL R1/R1b 的 3.0 占位保持一致。 */
    private static final double SPAN_THRESHOLD = 3.0;

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    /** 洞察 SKILL 落盘目录（相对模块工作目录；默认 skill/）。 */
    @Value("${neinsight.skill.dir:skill}")
    private String skillDir;

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
    // 五看（看行业/趋势）
    // ==========================================================================
    @JobWorker(type = "scan-industry-trend", autoComplete = false)
    public void handleScanIndustryTrend(final ActivatedJob job, final JobClient client) {
        try {
            String domain = currentDomain(job);
            List<OWLNamedIndividual> trends = filterByType(domainElements(domain), C_INDUSTRY_TREND);
            List<Map<String, Object>> items = new ArrayList<>();
            for (OWLNamedIndividual t : trends) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("iri", t.getIRI().toString());
                item.put("label", label(t.getIRI().toString()));
                item.put("direction", dataString(t, D_TREND_DIRECTION));
                item.put("valueTransferLabels", targetLabels(t, P_INDICATES_VALUE_TRANSFER));
                item.put("macroFactorLabels", targetLabels(t, P_HAS_MACRO_FACTOR));
                items.add(item);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("domain", domain);
            result.put("trendCount", trends.size());
            result.put("trends", items);
            complete(client, job, Map.of("industryTrendResult", result));
            log.info("✅ scan-industry-trend 完成 | domain={} | trends={}", domain, trends.size());
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
            String domain = currentDomain(job);
            List<OWLNamedIndividual> markets = globalInstances(C_MARKET);
            List<OWLNamedIndividual> customers = globalInstances(C_CUSTOMER);

            List<String> marketLabels = new ArrayList<>();
            for (OWLNamedIndividual m : markets) {
                marketLabels.add(label(m.getIRI().toString()));
            }

            List<Map<String, Object>> customerItems = new ArrayList<>();
            for (OWLNamedIndividual c : customers) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("iri", c.getIRI().toString());
                item.put("label", label(c.getIRI().toString()));

                List<Map<String, Object>> pains = new ArrayList<>();
                for (OWLNamedIndividual p : targets(c, P_HAS_PAIN_POINT)) {
                    Map<String, Object> pi = new LinkedHashMap<>();
                    pi.put("label", label(p.getIRI().toString()));
                    pi.put("severity", dataNumber(p, D_PAIN_SEVERITY));
                    pains.add(pi);
                }
                item.put("painPoints", pains);
                item.put("needs", targetLabels(c, P_HAS_NEED));
                customerItems.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("domain", domain);
            result.put("marketCount", markets.size());
            result.put("customerCount", customers.size());
            result.put("markets", marketLabels);
            result.put("customers", customerItems);
            complete(client, job, Map.of("marketCustomerResult", result));
            log.info("✅ scan-market-customer 完成 | domain={} | markets={} | customers={}",
                    domain, markets.size(), customers.size());
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
            String domain = currentDomain(job);
            List<OWLNamedIndividual> landscapes = globalInstances(C_COMPETITIVE_LANDSCAPE);
            List<Map<String, Object>> items = new ArrayList<>();
            for (OWLNamedIndividual l : landscapes) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("iri", l.getIRI().toString());
                item.put("label", label(l.getIRI().toString()));
                List<Map<String, Object>> comps = new ArrayList<>();
                for (OWLNamedIndividual comp : targets(l, P_INVOLVES_COMPETITOR)) {
                    Map<String, Object> ci = new LinkedHashMap<>();
                    ci.put("iri", comp.getIRI().toString());
                    ci.put("label", label(comp.getIRI().toString()));
                    comps.add(ci);
                }
                item.put("competitors", comps);
                items.add(item);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("domain", domain);
            result.put("landscapeCount", landscapes.size());
            result.put("landscapes", items);
            result.put("note", "竞品【待考】不指名，仅取本体 label");
            complete(client, job, Map.of("competitionResult", result));
            log.info("✅ scan-competition 完成 | domain={} | landscapes={}", domain, landscapes.size());
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
            String domain = currentDomain(job);
            List<OWLNamedIndividual> selfs = globalInstances(C_SELF_ASSESSMENT);

            List<Map<String, Object>> capabilities = new ArrayList<>();
            List<String> shortBoards = new ArrayList<>();
            for (OWLNamedIndividual s : selfs) {
                for (OWLNamedIndividual cap : targets(s, P_HAS_CAPABILITY)) {
                    Map<String, Object> ci = new LinkedHashMap<>();
                    ci.put("iri", cap.getIRI().toString());
                    ci.put("label", label(cap.getIRI().toString()));
                    ci.put("score", dataNumber(cap, D_CAPABILITY_SCORE));
                    capabilities.add(ci);
                }
                shortBoards.addAll(targetLabels(s, P_HAS_SHORT_BOARD));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("domain", domain);
            result.put("selfCount", selfs.size());
            result.put("capabilities", capabilities);
            result.put("shortBoards", shortBoards);
            complete(client, job, Map.of("selfAssessmentResult", result));
            log.info("✅ scan-self 完成 | domain={} | capabilities={} | shortBoards={}",
                    domain, capabilities.size(), shortBoards.size());
        } catch (Exception e) {
            fail(job, client, "SCAN_SELF_FAILED", e);
        }
    }

    // ==========================================================================
    // 五看（看机会 · SPAN 定位）
    // ==========================================================================
    @JobWorker(type = "scan-opportunity", autoComplete = false)
    public void handleScanOpportunity(final ActivatedJob job, final JobClient client) {
        try {
            String domain = currentDomain(job);
            List<OWLNamedIndividual> opps = filterByType(domainElements(domain), C_OPPORTUNITY);

            List<Map<String, Object>> items = new ArrayList<>();
            String topPriority = null;
            for (OWLNamedIndividual o : opps) {
                Double attractiveness = dataNumber(o, D_MARKET_ATTRACTIVENESS);
                Double position = dataNumber(o, D_COMPETITIVE_POSITION);
                String priority = dataString(o, D_OPPORTUNITY_PRIORITY);
                if (priority == null && attractiveness != null && position != null) {
                    priority = placeholderPriority(attractiveness, position);
                    log.warn("⚠️ SWRL 未返回 opportunityPriority，按占位阈值({})镜像 R1/R1b 计算 | opp={} | a={} | c={}",
                            SPAN_THRESHOLD, localName(o.getIRI().toString()), attractiveness, position);
                }

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("iri", o.getIRI().toString());
                item.put("label", label(o.getIRI().toString()));
                item.put("marketAttractiveness", attractiveness);
                item.put("competitivePosition", position);
                item.put("quadrantLabel", singleTargetLabel(o, P_HAS_SPAN_QUADRANT));
                item.put("priority", priority);
                items.add(item);

                topPriority = higherPriority(topPriority, priority);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("domain", domain);
            result.put("opportunityCount", opps.size());
            result.put("opportunities", items);
            result.put("topPriority", topPriority);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("opportunityResult", result);
            out.put("opportunityPriority", topPriority == null ? "NONE" : topPriority);
            complete(client, job, out);
            log.info("✅ scan-opportunity 完成 | domain={} | opportunities={} | topPriority={}",
                    domain, opps.size(), out.get("opportunityPriority"));
        } catch (Exception e) {
            fail(job, client, "SCAN_OPPORTUNITY_FAILED", e);
        }
    }

    // ==========================================================================
    // 高优先级机会点标记与外发
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
                log.info("ℹ️ mark-high-priority：当前域无高优先级机会点，跳过外发 | domain={}", domain);
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
            String domain = currentDomain(job);
            List<OWLNamedIndividual> cps = globalInstances(C_CONTROL_POINT);
            // ControlPointType 为独立分类值类（非 StrategicControlPoint 子类），此处防御性剔除
            cps.removeIf(i -> isInstance(i, C_CONTROL_POINT_TYPE));

            List<Map<String, Object>> items = new ArrayList<>();
            for (OWLNamedIndividual cp : cps) {
                OWLNamedIndividual type = singleTarget(cp, P_HAS_CONTROL_POINT_TYPE);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("iri", cp.getIRI().toString());
                item.put("label", label(cp.getIRI().toString()));
                item.put("typeLabel", type == null ? null : label(type.getIRI().toString()));
                item.put("typeCode", type == null ? null : dataString(type, D_CP_TYPE_CODE));
                item.put("opportunityLabel", singleTargetLabel(cp, P_ADDRESSES_OPPORTUNITY));
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("domain", domain);
            result.put("controlPointCount", cps.size());
            result.put("controlPoints", items);
            complete(client, job, Map.of("controlPointResult", result));
            log.info("✅ define-control-point 完成 | domain={} | controlPoints={}", domain, cps.size());
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
            String domain = currentDomain(job);
            List<OWLNamedIndividual> objs = globalInstances(C_OBJECTIVE);

            List<Map<String, Object>> items = new ArrayList<>();
            boolean allCompliant = !objs.isEmpty();
            for (OWLNamedIndividual ob : objs) {
                Boolean measurable = dataBoolean(ob, D_IS_MEASURABLE);
                Boolean relevant = dataBoolean(ob, D_IS_RELEVANT);
                String timeframe = dataString(ob, D_HAS_TIMEFRAME);

                Boolean compliant = dataBoolean(ob, D_SMART_COMPLIANT);
                if (compliant == null) {
                    compliant = Boolean.TRUE.equals(measurable)
                            && Boolean.TRUE.equals(relevant)
                            && timeframe != null && !timeframe.isBlank();
                    log.warn("⚠️ SWRL 未返回 smartCompliant，按占位判据镜像 R3 计算 | obj={}",
                            localName(ob.getIRI().toString()));
                }
                allCompliant = allCompliant && Boolean.TRUE.equals(compliant);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("iri", ob.getIRI().toString());
                item.put("label", label(ob.getIRI().toString()));
                item.put("metric", dataString(ob, D_HAS_METRIC));
                item.put("targetValue", dataNumber(ob, D_TARGET_VALUE));
                item.put("timeframe", timeframe);
                item.put("measurable", measurable);
                item.put("relevant", relevant);
                item.put("smartCompliant", compliant);
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("domain", domain);
            result.put("objectiveCount", objs.size());
            result.put("objectives", items);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("objectiveResult", result);
            out.put("smartCompliant", allCompliant);
            complete(client, job, out);
            log.info("✅ define-objective 完成 | domain={} | objectives={} | smartCompliant={}",
                    domain, objs.size(), allCompliant);
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
            String domain = currentDomain(job);
            List<OWLNamedIndividual> strategies = globalInstances(C_STRATEGY);

            List<Map<String, Object>> items = new ArrayList<>();
            for (OWLNamedIndividual st : strategies) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("iri", st.getIRI().toString());
                item.put("label", label(st.getIRI().toString()));
                item.put("type", mostSpecificType(st));
                item.put("objectiveLabel", singleTargetLabel(st, P_STRATEGY_OF));
                items.add(item);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("domain", domain);
            result.put("strategyCount", strategies.size());
            result.put("strategies", items);
            complete(client, job, Map.of("strategyResult", result));
            log.info("✅ define-strategy 完成 | domain={} | strategies={}", domain, strategies.size());
        } catch (Exception e) {
            fail(job, client, "DEFINE_STRATEGY_FAILED", e);
        }
    }

    // ==========================================================================
    // 汇总输出（一致性校验 + 生成给大模型的 SKILL）
    // ==========================================================================
    @JobWorker(type = "summarize-insight", autoComplete = false)
    public void handleSummarizeInsight(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
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

            String summary = composeSummary(vars, domain, scenario);

            String skillPath = null;
            try {
                skillPath = writeInsightSkill(job, domain, scenario, vars, summary, status);
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
    // 私有辅助
    // ==========================================================================

    /** 内部 ABox 检索根域：域概念并入输入场景后不再由用户选择，默认联合域（检索口径不变）。 */
    private String currentDomain(ActivatedJob job) {
        String d = str(job.getVariablesAsMap().get("insightDomain"));
        return (d == null || d.isBlank()) ? DEFAULT_DOMAIN : d;
    }

    /** 输入洞察场景（流程唯一业务输入）。 */
    private String currentScenario(ActivatedJob job) {
        String s = str(job.getVariablesAsMap().get("insightScenario"));
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

    private List<OWLNamedIndividual> targets(OWLNamedIndividual ind, String propIri) {
        OWLObjectProperty p = backendService.getObjectProperty(propIri);
        List<OWLNamedIndividual> out = new ArrayList<>(backendService.getObjectPropertyAllValueOfIndividual(ind, p));
        out.sort(Comparator.comparing(i -> i.getIRI().toString()));
        return out;
    }

    private List<String> targetLabels(OWLNamedIndividual ind, String propIri) {
        List<String> out = new ArrayList<>();
        for (OWLNamedIndividual v : targets(ind, propIri)) {
            out.add(label(v.getIRI().toString()));
        }
        return out;
    }

    private OWLNamedIndividual singleTarget(OWLNamedIndividual ind, String propIri) {
        List<OWLNamedIndividual> vs = targets(ind, propIri);
        return vs.isEmpty() ? null : vs.get(0);
    }

    private String singleTargetLabel(OWLNamedIndividual ind, String propIri) {
        OWLNamedIndividual v = singleTarget(ind, propIri);
        return v == null ? null : label(v.getIRI().toString());
    }

    private String mostSpecificType(OWLNamedIndividual ind) {
        try {
            Set<String> direct = backendService.readTypeFragments(ind, true);
            if (direct != null && !direct.isEmpty()) {
                return String.join(",", new TreeSet<>(direct));
            }
            Set<String> all = backendService.readTypeFragments(ind, false);
            return (all == null || all.isEmpty()) ? null : String.join(",", new TreeSet<>(all));
        } catch (Exception e) {
            return null;
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

    private static int rank(String priority) {
        if (priority == null) {
            return 0;
        }
        return switch (priority) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private static String higherPriority(String a, String b) {
        return rank(b) > rank(a) ? b : a;
    }

    // ---------------- 汇总 ----------------

    private String composeSummary(Map<String, Object> vars, String domain, String scenario) {
        StringBuilder sb = new StringBuilder();
        sb.append("【洞察场景「").append(scenario.isBlank() ? domainLabel(domain) + "全景" : scenario)
                .append("」的「五看三定」洞察结论】");
        sb.append(" 趋势=").append(nested(vars, "industryTrendResult", "trendCount"));
        sb.append("；市场=").append(nested(vars, "marketCustomerResult", "marketCount"));
        sb.append("/客户=").append(nested(vars, "marketCustomerResult", "customerCount"));
        sb.append("；竞争格局=").append(nested(vars, "competitionResult", "landscapeCount"));
        sb.append("；自身能力=").append(sizeOfNested(vars, "selfAssessmentResult", "capabilities"));
        sb.append("；机会点=").append(nested(vars, "opportunityResult", "opportunityCount"));
        sb.append("（优先级=").append(vars.getOrDefault("opportunityPriority", "NONE")).append("）");
        sb.append("；控制点=").append(nested(vars, "controlPointResult", "controlPointCount"));
        sb.append("；目标=").append(nested(vars, "objectiveResult", "objectiveCount"));
        sb.append("（SMART=").append(vars.getOrDefault("smartCompliant", Boolean.FALSE)).append("）");
        sb.append("；策略=").append(nested(vars, "strategyResult", "strategyCount"));
        sb.append("；高优先级外发=").append(vars.getOrDefault("alertDispatched", Boolean.FALSE));
        sb.append("。");
        return sb.toString();
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
     * 按「五看三定」逐节点给出①本体实例数据（内部知识基线）、②联网检索指令（依据场景生成的
     * 检索关键词/信息源/需采集字段）、③数据加工与分析要求；大模型据此**真正联网检索、加工、分析**
     * 后生成洞察报告，而非仅按维度骨架作答。
     * 落盘至 {@code <neinsight.skill.dir>/neinsight-<instanceKey>.md}。
     * 全程经现存 API（{@link BackendService}）读取本体，不硬编码业务数据、不硬编码绝对路径（★铁律21）。
     */
    private String writeInsightSkill(ActivatedJob job, String domain, String scenario,
                                     Map<String, Object> vars, String summary, String status) throws Exception {
        long instanceKey = job.getProcessInstanceKey();
        String processId = job.getBpmnProcessId();
        if (processId == null || processId.isBlank()) {
            processId = INSIGHT_PROCESS_ID;
        }
        String markdown = composeSkill(instanceKey, processId, domain, scenario, vars, summary, status);

        Path dir = Path.of(skillDir);
        Files.createDirectories(dir);
        Path target = dir.resolve("neinsight-" + instanceKey + ".md");
        Files.writeString(target, markdown, StandardCharsets.UTF_8);
        log.info("📄 洞察 SKILL 已落盘 | path={}", target.toAbsolutePath());
        return target.toAbsolutePath().toString();
    }

    private String composeSkill(long instanceKey, String processId, String domain, String scenario,
                                Map<String, Object> vars, String summary, String status) {
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
                .append("再按「🧮 数据加工与分析要求」加工分析，并与该节点内嵌的「本体实例数据」相互印证，")
                .append("最终形成该节点的洞察结论——**不是仅按维度骨架作答**。\n\n");
        sb.append("本次洞察场景为 **").append(sceneLabel).append("**，")
                .append("业务方法论为华为「五看三定」——「五看」「三定」各维度均围绕该场景展开。\n\n");

        sb.append("## 二、任务指令\n\n");
        sb.append("请以「角色设定」中的身份，**围绕「").append(sceneLabel)
                .append("」这一洞察场景，按下方「三、洞察内容」所列 BPMN 流程节点的先后顺序，逐节点逐步构建**《关于「")
                .append(sceneLabel).append("」的洞察报告》——即依次完成「五看」各节点（看行业/趋势 → 看市场/客户 → 看竞争 → 看自己 → 看机会）与「三定」各节点（定战略控制点 → 定目标 → 定策略），最后汇总形成结论。要求：\n\n");
        sb.append("1. 所有内容严格围绕输入洞察场景组织，按 BPMN 流程节点顺序（「五看三定」）逐节构建，不跳节、不打乱次序；\n");
        sb.append("2. **每个节点都必须先联网采集数据、再做加工分析**：按该节点「🔎 联网检索指令」给出的检索关键词、信息源与需采集字段**真正联网检索**，")
                .append("再按「🧮 数据加工与分析要求」完成加工与分析，最后与该节点内嵌的「本体实例数据」相互印证后形成结论；不得跳过检索仅按维度骨架作答；\n");
        sb.append("3. **内嵌数据为内部知识基线**：节点内嵌的本体实例数据是本次流程从本体检索所得的基线；当其为空或不足时，须以联网检索到的外部数据补齐，并注明数据来源与时间；\n");
        sb.append("4. **有据可依、来源可溯**：外部数据须标注来源（机构/报告/链接）与时间；本体数据与外部数据冲突时须并列说明；不得编造数据；\n");
        sb.append("5. 对数据缺失或标注为【待考】的内容，须明确注明「待补充」，不得臆测；\n");
        sb.append("6. 使用规范的 Markdown 输出（标题、摘要、分节点小节、结论），语言专业、简洁。\n\n");

        sb.append("## 三、洞察内容（按 BPMN 流程逐步构建）\n\n");
        sb.append("> 以下各节严格按 BPMN 流程 `").append(processId).append("` 的 serviceTask 执行顺序排列；每节标注对应节点（`节点 id` / `jobType`）与其产出的流程变量。")
                .append("每节包含三部分：**①本体实例数据**（本次流程从本体 ABox 检索所得的内部知识基线）、")
                .append("**②🔎 联网检索指令**（该节点依据场景需联网采集的检索关键词 / 信息源 / 数据字段）、")
                .append("**③🧮 数据加工与分析要求**（该节点数据应如何加工与分析、产出什么结论）。")
                .append("大模型须据此真正联网检索、加工、分析后产出该节结论。\n\n");

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
        appendTrendSection(sb, domain, sceneLabel);
        appendMarketCustomerSection(sb, sceneLabel);
        appendCompetitionSection(sb, sceneLabel);
        appendSelfSection(sb, sceneLabel);
        appendOpportunitySection(sb, domain, sceneLabel);

        sb.append("### 3.5 【三定】按 BPMN 节点逐步构建（场景：").append(sceneLabel).append("）\n\n");
        appendControlPointSection(sb, sceneLabel);
        boolean smartCompliant = appendObjectiveSection(sb, sceneLabel);
        appendStrategySection(sb, sceneLabel);

        sb.append("### 3.6 数据校验与提示（`Task_Summarize` → `summarize-insight`）\n\n");
        appendDataNotes(sb, domain, vars, status, smartCompliant);

        sb.append("---\n\n");
        sb.append("*数据来源：NEInsight 洞察流程（Camunda 实例 `").append(instanceKey).append("`）；")
                .append("洞察维度骨架取自本体 TBox，实例数据检索自 ABox；")
                .append("各节点「🔎 联网检索指令」所示外部数据由大模型在生成报告时联网采集并标注来源。*\n");
        return sb.toString();
    }

    /**
     * 输出某节点的「🔎 联网检索指令」与「🧮 数据加工与分析要求」。
     *
     * <p>该节点小结不再仅是维度骨架：大模型须据此**真正联网检索、加工、分析**后产出该节结论。
     * 检索关键词按输入场景动态生成（「场景」+ 维度词），信息源与需采集字段为方法论默认集合。
     */
    private void appendRetrievalAndAnalysis(StringBuilder sb, String sceneLabel,
                                            List<String> keywords, List<String> sources, List<String> fields,
                                            List<String> steps, String output) {
        sb.append("**🔎 联网检索指令**（围绕场景「").append(sceneLabel).append("」联网采集外部公开数据）：\n\n");
        List<String> ks = new ArrayList<>();
        for (String k : keywords) {
            ks.add("「" + sceneLabel + "」+ " + k);
        }
        sb.append("- **检索关键词**：").append(joinOrDash(ks)).append("\n");
        sb.append("- **建议信息源**：").append(joinOrDash(sources)).append("\n");
        sb.append("- **需采集的数据字段**：").append(joinOrDash(fields)).append("\n\n");

        sb.append("**🧮 数据加工与分析要求**：\n\n");
        int i = 1;
        for (String step : steps) {
            sb.append(i++).append(". ").append(step).append("\n");
        }
        sb.append("\n> **产出**：").append(output).append("\n\n");
    }

    /** 流程起点节点：输入洞察场景（用户唯一业务输入，取代原「洞察域选择 + 域扫描」）。 */
    private void appendScenarioSection(StringBuilder sb, String scenario, String retrievalRoot, String domain) {
        sb.append("### 3.2 输入洞察场景（流程起点）\n\n");
        sb.append("- **洞察场景**：")
                .append(scenario.isBlank() ? "（未提供，按联合域全景展开）" : scenario).append("\n");
        sb.append("- **检索根域**：").append(retrievalRoot).append("（").append(domain)
                .append("）——域概念已并入场景内容表达，检索口径（沿 `hasElement` 展开）保持不变\n\n");
    }

    private void appendTrendSection(StringBuilder sb, String domain, String sceneLabel) {
        List<OWLNamedIndividual> trends = filterByType(domainElements(domain), C_INDUSTRY_TREND);
        sb.append("#### 节点 1 · 看行业/趋势（`scan-industry-trend` → `industryTrendResult`）—— 共 ").append(trends.size()).append(" 条\n\n");
        sb.append("**📊 本体实例数据（内部知识基线）**\n\n");
        if (trends.isEmpty()) {
            sb.append("> 本域无趋势数据，须以联网检索结果补齐。\n\n");
        } else {
            sb.append("| 趋势 | 方向 | 价值转移 | 宏观因子 |\n| --- | --- | --- | --- |\n");
            for (OWLNamedIndividual t : trends) {
                sb.append("| ").append(label(t.getIRI().toString()))
                        .append(" | ").append(orDash(dataString(t, D_TREND_DIRECTION)))
                        .append(" | ").append(joinOrDash(targetLabels(t, P_INDICATES_VALUE_TRANSFER)))
                        .append(" | ").append(joinOrDash(targetLabels(t, P_HAS_MACRO_FACTOR)))
                        .append(" |\n");
            }
            sb.append('\n');
        }
        appendRetrievalAndAnalysis(sb, sceneLabel,
                List.of("行业趋势", "市场规模 增速", "政策 双碳 新能源", "技术演进路线",
                        "宏观因子 GDP 人口 政策 法规"),
                List.of("国家统计局、工信部/国家能源局等政府与监管机构", "行业协会（如中电联）与行业白皮书",
                        "券商/咨询研报（Gartner、IDC、艾瑞、赛迪等）", "权威财经媒体与头部企业年报"),
                List.of("趋势名称与方向（trendDirection）", "价值转移路径（indicatesValueTransfer）", "宏观因子（hasMacroFactor）",
                        "时间窗与量化幅度", "来源机构与发布时间、来源链接"),
                List.of("统一各来源的口径、单位与时间窗，形成可比数据表",
                        "对关键趋势做**多源交叉验证**（≥2 个独立来源），标注一致/冲突项",
                        "判定每条趋势的方向与其引发的价值转移路径，剔除无关噪音",
                        "按对本次场景的影响度与确定性排序，识别 TOP-N 关键趋势",
                        "对不确定/证据不足项标注「待补充」并说明缺口"),
                "该场景的行业趋势结论：TOP-N 关键趋势、方向与价值转移路径、宏观因子影响，及数据来源与不确定性说明。");
    }

    private void appendMarketCustomerSection(StringBuilder sb, String sceneLabel) {
        List<OWLNamedIndividual> markets = globalInstances(C_MARKET);
        List<OWLNamedIndividual> customers = globalInstances(C_CUSTOMER);
        sb.append("#### 节点 2 · 看市场/客户（`scan-market-customer` → `marketCustomerResult`）—— 市场 ").append(markets.size())
                .append(" / 客户 ").append(customers.size()).append("\n\n");
        sb.append("**📊 本体实例数据（内部知识基线）**\n\n");

        List<String> marketLabels = new ArrayList<>();
        for (OWLNamedIndividual m : markets) {
            marketLabels.add(label(m.getIRI().toString()));
        }
        sb.append("**市场**：").append(joinOrDash(marketLabels)).append("\n\n");

        if (customers.isEmpty()) {
            sb.append("> 无客户数据，须以联网检索结果补齐。\n\n");
        } else {
            sb.append("| 客户 | 痛点（严重度） | 需求 |\n| --- | --- | --- |\n");
            for (OWLNamedIndividual c : customers) {
                List<String> pains = new ArrayList<>();
                for (OWLNamedIndividual p : targets(c, P_HAS_PAIN_POINT)) {
                    Double severity = dataNumber(p, D_PAIN_SEVERITY);
                    pains.add(label(p.getIRI().toString()) + (severity == null ? "" : "（" + severity + "）"));
                }
                sb.append("| ").append(label(c.getIRI().toString()))
                        .append(" | ").append(joinOrDash(pains))
                        .append(" | ").append(joinOrDash(targetLabels(c, P_HAS_NEED)))
                        .append(" |\n");
            }
            sb.append('\n');
        }
        appendRetrievalAndAnalysis(sb, sceneLabel,
                List.of("市场规模 增速", "目标客户 画像", "客户痛点 需求", "细分市场 结构",
                        "客户采购行为 决策链"),
                List.of("第三方市场研究机构（IDC、艾瑞、赛迪、Gartner）", "行业招投标与政府采购平台",
                        "客户访谈/问卷与客服工单", "上市公司财报与投资者关系材料"),
                List.of("市场名称、规模与增速", "目标客户/细分客群", "痛点（hasPainPoint）及其严重度（painSeverity）",
                        "需求（hasNeed）", "来源机构与时间、来源链接"),
                List.of("对齐各来源的规模/增速口径、币种与统计年份，形成可比数据表",
                        "对客户痛点做**聚类并按严重度加权**，识别高价值痛点",
                        "将需求逐条映射到可被满足的能力/方案",
                        "测算各细分市场的吸引力（规模×增速），圈定目标细分市场",
                        "对样本不足或口径存疑项标注「待补充」"),
                "该场景的目标细分市场与客户画像：目标市场及规模增速、高优先级客户痛点与需求，及数据来源说明。");
    }

    private void appendCompetitionSection(StringBuilder sb, String sceneLabel) {
        List<OWLNamedIndividual> landscapes = globalInstances(C_COMPETITIVE_LANDSCAPE);
        sb.append("#### 节点 3 · 看竞争（`scan-competition` → `competitionResult`）—— 共 ").append(landscapes.size()).append(" 组\n\n");
        sb.append("**📊 本体实例数据（内部知识基线）**\n\n");
        if (landscapes.isEmpty()) {
            sb.append("> 无竞争格局数据，须以联网检索结果补齐。\n\n");
        } else {
            sb.append("| 竞争格局 | 竞争对手 |\n| --- | --- |\n");
            for (OWLNamedIndividual l : landscapes) {
                List<String> competitors = new ArrayList<>();
                for (OWLNamedIndividual comp : targets(l, P_INVOLVES_COMPETITOR)) {
                    competitors.add(label(comp.getIRI().toString()));
                }
                sb.append("| ").append(label(l.getIRI().toString()))
                        .append(" | ").append(joinOrDash(competitors)).append(" |\n");
            }
            sb.append("\n> 注：竞品【待考】不指名，仅取本体 label。\n\n");
        }
        appendRetrievalAndAnalysis(sb, sceneLabel,
                List.of("主要竞争对手", "市场份额 集中度", "竞品产品 技术 价格", "竞品渠道与商业模式",
                        "竞争格局 演变"),
                List.of("竞品官网与产品文档", "招投标记录与中标公告", "专利与标准数据库",
                        "分析师竞争格局报告（Gartner、IDC、Forrester）", "行业协会统计"),
                List.of("竞争格局描述", "竞争对手（involvesCompetitor）", "市场份额/集中度", "产品、技术与价格定位",
                        "渠道与商业模式", "来源与时间"),
                List.of("**合规脱敏优先**：竞品按【待考】不指名处理，仅保留可公开检索的格局性描述",
                        "对齐份额口径与统计年份，构建竞争矩阵（份额/技术/价格/渠道）",
                        "识别自身与主要竞争者的差异点与相对位次",
                        "判断竞争格局的演变方向与对场景的影响",
                        "对不可公开核实的信息标注「待补充」"),
                "该场景的竞争态势结论：竞争格局、相对位次与关键差异点（竞品按【待考】脱敏处理）。");
    }

    private void appendSelfSection(StringBuilder sb, String sceneLabel) {
        List<OWLNamedIndividual> selfs = globalInstances(C_SELF_ASSESSMENT);
        List<OWLNamedIndividual> capabilities = new ArrayList<>();
        List<String> shortBoards = new ArrayList<>();
        for (OWLNamedIndividual s : selfs) {
            capabilities.addAll(targets(s, P_HAS_CAPABILITY));
            shortBoards.addAll(targetLabels(s, P_HAS_SHORT_BOARD));
        }
        sb.append("#### 节点 4 · 看自己（`scan-self` → `selfAssessmentResult`）—— 能力 ").append(capabilities.size())
                .append(" / 短板 ").append(shortBoards.size()).append("\n\n");
        sb.append("**📊 本体实例数据（内部知识基线）**\n\n");
        if (!capabilities.isEmpty()) {
            sb.append("| 能力 | 评分 |\n| --- | --- |\n");
            for (OWLNamedIndividual cap : capabilities) {
                Double score = dataNumber(cap, D_CAPABILITY_SCORE);
                sb.append("| ").append(label(cap.getIRI().toString()))
                        .append(" | ").append(score == null ? "—" : String.valueOf(score)).append(" |\n");
            }
            sb.append('\n');
        }
        sb.append("**短板**：").append(joinOrDash(shortBoards)).append("\n\n");
        appendRetrievalAndAnalysis(sb, sceneLabel,
                List.of("自身能力 资源 禀赋", "行业标杆 最佳实践", "能力短板 差距", "研发投入 专利 人才",
                        "组织与运营效率"),
                List.of("内部资料（财报、审计、经营复盘、能力盘点）", "行业标杆企业公开资料与年报",
                        "第三方能力评估/成熟度模型", "专利、资质与人才公开数据"),
                List.of("能力名称与评分（capabilityScore）", "短板（hasShortBoard）", "标杆对照值",
                        "资源投入与产出指标", "来源与时间"),
                List.of("校准内部能力评分量表，确保与行业口径可比",
                        "以行业标杆为参照做**对标分析**，测算能力差距",
                        "对短板做归因（资源/机制/技术）并按影响度排序",
                        "识别可支撑本次场景的强项与必须补齐的短板",
                        "内部数据不可外采的，以公开信息近似替代并标注"),
                "该场景的自身能力结论：能力清单与评分、关键短板及优先补齐项（含对标依据）。");
    }

    private void appendOpportunitySection(StringBuilder sb, String domain, String sceneLabel) {
        List<OWLNamedIndividual> opps = filterByType(domainElements(domain), C_OPPORTUNITY);
        String topPriority = null;
        for (OWLNamedIndividual o : opps) {
            topPriority = higherPriority(topPriority, resolvePriority(o));
        }
        sb.append("#### 节点 5 · 看机会 SPAN（`scan-opportunity` → `opportunityResult`）—— 共 ").append(opps.size()).append(" 个");
        if (topPriority != null) {
            sb.append("（顶部优先级 = ").append(topPriority).append("）");
        }
        sb.append("\n\n");
        sb.append("**📊 本体实例数据（内部知识基线）**\n\n");
        if (opps.isEmpty()) {
            sb.append("> 本域无机会点数据，须以联网检索结果补齐。\n\n");
        } else {
            sb.append("| 机会点 | 市场吸引力 | 竞争地位 | SPAN 象限 | 优先级 |\n| --- | --- | --- | --- | --- |\n");
            for (OWLNamedIndividual o : opps) {
                Double attractiveness = dataNumber(o, D_MARKET_ATTRACTIVENESS);
                Double position = dataNumber(o, D_COMPETITIVE_POSITION);
                sb.append("| ").append(label(o.getIRI().toString()))
                        .append(" | ").append(attractiveness == null ? "—" : String.valueOf(attractiveness))
                        .append(" | ").append(position == null ? "—" : String.valueOf(position))
                        .append(" | ").append(orDash(singleTargetLabel(o, P_HAS_SPAN_QUADRANT)))
                        .append(" | ").append(orDash(resolvePriority(o)))
                        .append(" |\n");
            }
            sb.append('\n');
        }
        appendRetrievalAndAnalysis(sb, sceneLabel,
                List.of("市场机会点", "市场吸引力 增长空间", "竞争地位 相对优势", "新兴机会 蓝海",
                        "机会点 优先级"),
                List.of("前面「四看」检索所得的行业/市场/竞争/自身数据", "新兴细分市场与增量需求报告",
                        "政策扶持与补贴目录", "投融资与并购动向"),
                List.of("机会点名称", "市场吸引力（marketAttractiveness）", "竞争地位（competitivePosition）",
                        "SPAN 象限（hasSpanQuadrant）", "优先级（opportunityPriority）", "来源与时间"),
                List.of("按 SPAN 阈值（吸引力/竞争地位 = 3.0）对机会点做**分档定位**，确定象限",
                        "对各机会点的吸引力与竞争地位做**打分归一化**（统一量纲）",
                        "按 R1/R1b 规则（双高=HIGH、双低=LOW、其余=MEDIUM）计算优先级",
                        "排序识别 TOP 机会点及其理由",
                        "对证据不足的机会点标注「待补充」"),
                "该场景的机会点结论：机会点清单、SPAN 象限与优先级，及 TOP 机会点推荐。");
    }

    private void appendControlPointSection(StringBuilder sb, String sceneLabel) {
        List<OWLNamedIndividual> cps = globalInstances(C_CONTROL_POINT);
        cps.removeIf(i -> isInstance(i, C_CONTROL_POINT_TYPE));
        sb.append("#### 节点 6 · 定战略控制点（`define-control-point` → `controlPointResult`）—— 共 ").append(cps.size()).append(" 个\n\n");
        sb.append("**📊 本体实例数据（内部知识基线）**\n\n");
        if (cps.isEmpty()) {
            sb.append("> 无战略控制点数据，须以联网检索结果补齐。\n\n");
        } else {
            sb.append("| 控制点 | 类型（代码） | 关联机会点 |\n| --- | --- | --- |\n");
            for (OWLNamedIndividual cp : cps) {
                OWLNamedIndividual type = singleTarget(cp, P_HAS_CONTROL_POINT_TYPE);
                String typeLabel = type == null ? null : label(type.getIRI().toString());
                String typeCode = type == null ? null : dataString(type, D_CP_TYPE_CODE);
                String typeCell = (typeLabel == null) ? "—" : typeLabel + (typeCode == null ? "" : "（" + typeCode + "）");
                sb.append("| ").append(label(cp.getIRI().toString()))
                        .append(" | ").append(typeCell)
                        .append(" | ").append(orDash(singleTargetLabel(cp, P_ADDRESSES_OPPORTUNITY)))
                        .append(" |\n");
            }
            sb.append('\n');
        }
        appendRetrievalAndAnalysis(sb, sceneLabel,
                List.of("战略控制点 护城河", "控制点类型 标准 专利 生态", "行业壁垒 准入",
                        "标杆企业控制点", "控制点 可行性"),
                List.of("标杆企业公开资料（技术、标准、生态布局）", "专利/标准/认证数据库",
                        "行业准入与监管规则", "产业联盟与生态伙伴公开信息"),
                List.of("控制点名称", "控制点类型（hasControlPointType / cpTypeCode）", "关联机会点（addressesOpportunity）",
                        "可获取性与壁垒强度", "来源与时间"),
                List.of("将前一节点识别出的机会点**映射**到可构建的控制点",
                        "按控制点类型（标准/专利/生态/成本/品牌等）归类并给出类型代码",
                        "评估各控制点的**可行性与壁垒强度**（自建 vs 合作）",
                        "筛选可支撑场景目标的核心控制点并排序",
                        "对外部信息不足的控制点标注「待补充」"),
                "该场景的战略控制点结论：控制点清单、类型与关联机会点，及核心控制点建议。");
    }

    private boolean appendObjectiveSection(StringBuilder sb, String sceneLabel) {
        List<OWLNamedIndividual> objs = globalInstances(C_OBJECTIVE);
        boolean allCompliant = !objs.isEmpty();
        sb.append("#### 节点 7 · 定目标（`define-objective` → `objectiveResult`）—— 共 ").append(objs.size()).append(" 个\n\n");
        sb.append("**📊 本体实例数据（内部知识基线）**\n\n");
        if (objs.isEmpty()) {
            sb.append("> 无目标数据，须以联网检索结果补齐。\n\n");
        } else {
            sb.append("| 目标 | 指标 | 目标值 | 时间窗 | SMART |\n| --- | --- | --- | --- | --- |\n");
            for (OWLNamedIndividual ob : objs) {
                Boolean compliant = dataBoolean(ob, D_SMART_COMPLIANT);
                if (compliant == null) {
                    Boolean measurable = dataBoolean(ob, D_IS_MEASURABLE);
                    Boolean relevant = dataBoolean(ob, D_IS_RELEVANT);
                    String timeframe = dataString(ob, D_HAS_TIMEFRAME);
                    compliant = Boolean.TRUE.equals(measurable) && Boolean.TRUE.equals(relevant)
                            && timeframe != null && !timeframe.isBlank();
                }
                allCompliant = allCompliant && Boolean.TRUE.equals(compliant);
                Double targetValue = dataNumber(ob, D_TARGET_VALUE);
                sb.append("| ").append(label(ob.getIRI().toString()))
                        .append(" | ").append(orDash(dataString(ob, D_HAS_METRIC)))
                        .append(" | ").append(targetValue == null ? "—" : String.valueOf(targetValue))
                        .append(" | ").append(orDash(dataString(ob, D_HAS_TIMEFRAME)))
                        .append(" | ").append(compliant).append(" |\n");
            }
            sb.append("\n> SMART 合规 = ").append(allCompliant).append("。\n\n");
        }
        appendRetrievalAndAnalysis(sb, sceneLabel,
                List.of("目标 指标 标杆值", "行业标杆经营目标", "KPI 基准 对标", "目标 时间窗 分阶段",
                        "SMART 目标示例"),
                List.of("行业标杆企业年报与经营目标", "行业协会与权威机构的基线指标",
                        "内部经营指标与历史达成情况", "咨询机构的对标基准数据"),
                List.of("目标名称", "衡量指标（hasMetric）", "目标值（targetValue）", "时间窗（hasTimeframe）",
                        "可衡量性（isMeasurable）与相关性（isRelevant）", "来源与时间"),
                List.of("将战略控制点**转化为可衡量的目标**（Specific/Measurable）",
                        "以外部标杆值对标校准目标值，确保目标合理（Achievable/Relevant）",
                        "为目标设定分阶段时间窗（hasTimeframe），明确里程碑",
                        "逐条做 **SMART 校验**并给出合规判定（smartCompliant）",
                        "对无法量化或时间未定的目标标注「待补充」"),
                "该场景的 SMART 目标集：目标/指标/目标值/时间窗及 SMART 合规判定。");
        return allCompliant;
    }

    private void appendStrategySection(StringBuilder sb, String sceneLabel) {
        List<OWLNamedIndividual> strategies = globalInstances(C_STRATEGY);
        sb.append("#### 节点 8 · 定策略（`define-strategy` → `strategyResult`）—— 共 ").append(strategies.size()).append(" 个\n\n");
        sb.append("**📊 本体实例数据（内部知识基线）**\n\n");
        if (strategies.isEmpty()) {
            sb.append("> 本实例未产出策略（strategyCount = 0），须以联网检索结果补齐，待补充。\n\n");
        } else {
            sb.append("| 策略 | 类型 | 所属目标 |\n| --- | --- | --- |\n");
            for (OWLNamedIndividual st : strategies) {
                sb.append("| ").append(label(st.getIRI().toString()))
                        .append(" | ").append(orDash(mostSpecificType(st)))
                        .append(" | ").append(orDash(singleTargetLabel(st, P_STRATEGY_OF)))
                        .append(" |\n");
            }
            sb.append('\n');
        }
        appendRetrievalAndAnalysis(sb, sceneLabel,
                List.of("实施策略 举措", "标杆策略 最佳实践", "路线图 里程碑", "资源配置 投入",
                        "风险与应对"),
                List.of("行业标杆企业的战略举措与公开披露", "最佳实践案例与咨询报告",
                        "政策与产业规划文件", "内部可行性与资源盘点"),
                List.of("策略名称", "策略类型", "所属目标（strategyOf）", "关键举措与里程碑",
                        "资源需求与风险", "来源与时间"),
                List.of("将策略**映射到目标**（strategyOf），确保每个目标有对应策略支撑",
                        "评估各策略的**可执行性与资源需求**（人/财/技术）",
                        "按对目标达成的贡献度与实施难度排优先级，制定分阶段里程碑",
                        "识别关键风险并给出应对措施",
                        "形成策略组合与实施路线图建议"),
                "该场景的策略结论：策略组合、与目标的对应关系、实施优先级与路线图建议。");
    }

    private void appendDataNotes(StringBuilder sb, String domain, Map<String, Object> vars,
                                 String status, boolean smartCompliant) {
        List<OWLNamedIndividual> opps = filterByType(domainElements(domain), C_OPPORTUNITY);
        OWLNamedIndividual top = null;
        String topPriority = null;
        for (OWLNamedIndividual o : opps) {
            String p = resolvePriority(o);
            if (top == null || rank(p) > rank(topPriority)) {
                top = o;
                topPriority = p;
            }
        }
        sb.append("- 本域机会点数量：**").append(opps.size()).append("**");
        if (top != null && topPriority != null) {
            sb.append("；其中优先级最高为 **").append(label(top.getIRI().toString()))
                    .append("**（").append(orDash(singleTargetLabel(top, P_HAS_SPAN_QUADRANT)))
                    .append("，优先级 ").append(topPriority).append("）");
        }
        sb.append("。\n");
        sb.append("- 本体一致性校验：**").append(status).append("**")
                .append("CONSISTENT".equals(status) ? "（通过）。\n" : "（未通过，需人工核查）。\n");
        sb.append("- SMART 目标合规：**").append(smartCompliant).append("**。\n");
        Object dispatched = vars.get("alertDispatched");
        if (dispatched != null) {
            sb.append("- 高优先级机会点外发（alertDispatched）：**").append(dispatched).append("**。\n");
        }
        if (globalInstances(C_STRATEGY).isEmpty()) {
            sb.append("- 注意：本域策略（三定之「定策略」）数据为 0，生成报告时需注明「待补充」。\n");
        }
        sb.append("- 竞争信息部分为【待考】不指名项，生成报告时需注明「待补充」。\n\n");
    }

    private static String orDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private static String joinOrDash(List<String> values) {
        return (values == null || values.isEmpty()) ? "—" : String.join("、", values);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : Collections.emptyMap();
    }

    private static Object nested(Map<String, Object> vars, String key, String field) {
        return asMap(vars.get(key)).get(field);
    }

    private static Object sizeOfNested(Map<String, Object> vars, String key, String field) {
        Object v = nested(vars, key, field);
        return (v instanceof Collection) ? ((Collection<?>) v).size() : 0;
    }

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
