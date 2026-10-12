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
    // 洞察域扫描：三选一（scan-it-space / scan-cloud-space / scan-combined-space）
    // ==========================================================================
    @JobWorker(type = "scan-it-space", autoComplete = false)
    public void handleScanItSpace(final ActivatedJob job, final JobClient client) {
        handleDomainScan(job, client, "IT_SPACE", "SCAN_IT_SPACE_FAILED");
    }

    @JobWorker(type = "scan-cloud-space", autoComplete = false)
    public void handleScanCloudSpace(final ActivatedJob job, final JobClient client) {
        handleDomainScan(job, client, "CLOUD_SPACE", "SCAN_CLOUD_SPACE_FAILED");
    }

    @JobWorker(type = "scan-combined-space", autoComplete = false)
    public void handleScanCombinedSpace(final ActivatedJob job, final JobClient client) {
        handleDomainScan(job, client, "COMBINED", "SCAN_COMBINED_SPACE_FAILED");
    }

    private void handleDomainScan(final ActivatedJob job, final JobClient client, String defaultDomain, String errorCode) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();
            String insightDomain = str(vars.get("insightDomain"));
            if (insightDomain == null || insightDomain.isBlank()) {
                insightDomain = defaultDomain;
            }
            Map<String, Object> baseline = buildDomainBaseline(insightDomain);
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of("domainBaseline", baseline))
                    .send().join();
            log.info("✅ 域扫描完成 | jobType={} | domain={} | elements={}",
                    errorCode, insightDomain, baseline.get("elementCount"));
        } catch (Exception e) {
            fail(job, client, errorCode, e);
        }
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
            String domain = str(vars.get("insightDomain"));
            if (domain == null || domain.isBlank()) {
                domain = "COMBINED";
            }

            boolean consistent;
            try {
                consistent = backendService.getReasonerService().getReasoner().isConsistent();
            } catch (Exception e) {
                consistent = false;
                log.error("❌ 一致性校验异常", e);
            }
            String status = consistent ? "CONSISTENT" : "INCONSISTENT";

            String summary = composeSummary(vars, domain);

            String skillPath = null;
            try {
                skillPath = writeInsightSkill(job, domain, vars, summary, status);
            } catch (Exception e) {
                log.error("⚠️ 洞察 SKILL 落盘失败（不影响流程完成）| domain={}", domain, e);
            }

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("insightSummary", summary);
            out.put("instanceIri", domainIndividualIri(domain));
            out.put("ontologyValidationStatus", status);
            if (skillPath != null) {
                out.put("insightSkillPath", skillPath);
            }
            complete(client, job, out);
            log.info("✅ summarize-insight 完成 | domain={} | validation={} | skill={} | summary={}",
                    domain, status, skillPath, summary);
        } catch (Exception e) {
            fail(job, client, "SUMMARIZE_INSIGHT_FAILED", e);
        }
    }

    // ==========================================================================
    // 私有辅助
    // ==========================================================================

    private String currentDomain(ActivatedJob job) {
        String d = str(job.getVariablesAsMap().get("insightDomain"));
        if (d == null || d.isBlank()) {
            throw new IllegalArgumentException("流程变量 insightDomain 不能为空");
        }
        return d;
    }

    private String domainIndividualIri(String insightDomain) {
        return switch (insightDomain) {
            case "IT_SPACE" -> ABOX_NS + "IT_SPACE";
            case "CLOUD_SPACE" -> ABOX_NS + "CLOUD_SPACE";
            case "COMBINED" -> ABOX_NS + "COMBINED_SPACE";
            default -> throw new IllegalArgumentException("未知洞察域: " + insightDomain);
        };
    }

    private Map<String, Object> buildDomainBaseline(String insightDomain) {
        String domainIri = domainIndividualIri(insightDomain);
        List<OWLNamedIndividual> elements = domainElements(insightDomain);
        List<String> labels = new ArrayList<>();
        List<String> iris = new ArrayList<>();
        for (OWLNamedIndividual e : elements) {
            labels.add(label(e.getIRI().toString()));
            iris.add(e.getIRI().toString());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("domain", insightDomain);
        m.put("domainIri", domainIri);
        m.put("domainLabel", label(domainIri));
        m.put("elementCount", elements.size());
        m.put("elementLabels", labels);
        m.put("elementIris", iris);
        return m;
    }

    /** 收集洞察域下的全部要素（沿 hasElement 递归展开；含 COMBINED 的子域要素）。 */
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

    private String composeSummary(Map<String, Object> vars, String domain) {
        StringBuilder sb = new StringBuilder();
        sb.append("【新能源").append(domainLabel(domain)).append("「五看三定」洞察结论】");
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
     * 内嵌本次流程从本体 TBox/ABox 检索得到的「五看三定」洞察内容；大模型据此生成洞察报告。
     * 落盘至 {@code <neinsight.skill.dir>/neinsight-<instanceKey>.md}。
     * 全程经现存 API（{@link BackendService}）读取本体，不硬编码业务数据、不硬编码绝对路径（★铁律21）。
     */
    private String writeInsightSkill(ActivatedJob job, String domain, Map<String, Object> vars,
                                     String summary, String status) throws Exception {
        long instanceKey = job.getProcessInstanceKey();
        String processId = job.getBpmnProcessId();
        if (processId == null || processId.isBlank()) {
            processId = INSIGHT_PROCESS_ID;
        }
        String markdown = composeSkill(instanceKey, processId, domain, vars, summary, status);

        Path dir = Path.of(skillDir);
        Files.createDirectories(dir);
        Path target = dir.resolve("neinsight-" + instanceKey + ".md");
        Files.writeString(target, markdown, StandardCharsets.UTF_8);
        log.info("📄 洞察 SKILL 已落盘 | path={}", target.toAbsolutePath());
        return target.toAbsolutePath().toString();
    }

    private String composeSkill(long instanceKey, String processId, String domain,
                                Map<String, Object> vars, String summary, String status) {
        StringBuilder sb = new StringBuilder();
        String domainLabel = domainLabel(domain);
        String instanceIri = domainIndividualIri(domain);

        sb.append("# 洞察报告生成 SKILL（").append(domainLabel).append("）\n\n");
        sb.append("> 本文件由 NEInsight 洞察流程自动生成，是一份可**直接输入大模型**的提示词 SKILL。")
                .append("将本文件全文作为提示词输入大模型，即可据此生成《新能源行业「")
                .append(domainLabel).append("」洞察报告》。\n\n");

        sb.append("## 一、角色设定\n\n");
        sb.append("你是一名资深的**战略洞察分析师**，精通华为「五看三定」方法论，")
                .append("善于从行业趋势、市场与客户、竞争格局、自身能力、机会点等维度提炼洞察，")
                .append("并给出可执行的战略控制点、目标与策略建议。\n\n");
        sb.append("本次洞察域为 **").append(domainLabel).append("（").append(domain).append("）**，")
                .append("业务方法论为华为「五看三定」。\n\n");

        sb.append("## 二、任务指令\n\n");
        sb.append("请以「角色设定」中的身份，**按下方「三、洞察内容」所列 BPMN 流程节点的先后顺序，逐节点逐步构建**《新能源行业「")
                .append(domainLabel).append("」洞察报告》——即依次完成「五看」各节点（看行业/趋势 → 看市场/客户 → 看竞争 → 看自己 → 看机会）与「三定」各节点（定战略控制点 → 定目标 → 定策略），最后汇总形成结论。要求：\n\n");
        sb.append("1. 严格按 BPMN 流程节点顺序（「五看三定」）逐节构建，不跳节、不打乱次序；\n");
        sb.append("2. 所有结论必须**有据可依**，仅使用「三、洞察内容」中对应节点给出的数据，不得编造或引入外部信息；\n");
        sb.append("3. 对数据缺失或标注为【待考】的内容，须明确注明「待补充」，不得臆测；\n");
        sb.append("4. 使用规范的 Markdown 输出（标题、摘要、分节点小节、结论），语言专业、简洁。\n\n");

        sb.append("## 三、洞察内容（按 BPMN 流程逐步构建）\n\n");
        sb.append("> 以下各节严格按 BPMN 流程 `").append(processId).append("` 的 serviceTask 执行顺序排列；每节标注对应节点（`节点 id` / `jobType`）与其产出的流程变量，内容即该节点从本体检索到的实例数据。\n\n");

        sb.append("### 3.1 流程元信息\n\n");
        sb.append("| 项 | 值 |\n| --- | --- |\n");
        sb.append("| 流程实例 Key | `").append(instanceKey).append("` |\n");
        sb.append("| 流程定义 | `").append(processId).append("` |\n");
        sb.append("| 洞察域 | ").append(domain).append("（").append(domainLabel).append("） |\n");
        sb.append("| 实例 IRI | `").append(instanceIri).append("` |\n");
        sb.append("| 本体一致性校验 | **").append(status).append("**（Openllet） |\n");
        sb.append("| SKILL 生成时间 | ").append(Instant.now()).append(" |\n\n");

        appendDomainScanSection(sb, domain);

        sb.append("### 3.3 洞察数据摘要\n\n> ").append(summary).append("\n\n");

        sb.append("### 3.4 【五看】按 BPMN 节点逐步构建\n\n");
        appendTrendSection(sb, domain);
        appendMarketCustomerSection(sb);
        appendCompetitionSection(sb);
        appendSelfSection(sb);
        appendOpportunitySection(sb, domain);

        sb.append("### 3.5 【三定】按 BPMN 节点逐步构建\n\n");
        appendControlPointSection(sb);
        boolean smartCompliant = appendObjectiveSection(sb);
        appendStrategySection(sb);

        sb.append("### 3.6 数据校验与提示（`Task_Summarize` → `summarize-insight`）\n\n");
        appendDataNotes(sb, domain, vars, status, smartCompliant);

        sb.append("---\n\n");
        sb.append("*数据来源：NEInsight 洞察流程（Camunda 实例 `").append(instanceKey).append("`）；")
                .append("洞察维度骨架取自本体 TBox，实例数据检索自 ABox，经现存 API 生成。*\n");
        return sb.toString();
    }

    /** 流程起点节点：洞察域扫描（`scan-it-space` / `scan-cloud-space` / `scan-combined-space` → `domainBaseline`）。 */
    private void appendDomainScanSection(StringBuilder sb, String domain) {
        Map<String, Object> baseline = buildDomainBaseline(domain);
        String jobType = switch (domain) {
            case "IT_SPACE" -> "scan-it-space";
            case "CLOUD_SPACE" -> "scan-cloud-space";
            default -> "scan-combined-space";
        };
        sb.append("### 3.2 洞察域扫描（流程起点 · `").append(jobType).append("` → `domainBaseline`）\n\n");
        sb.append("- **洞察域**：").append(baseline.get("domainLabel")).append("（").append(domain).append("）\n");
        sb.append("- **域 IRI**：`").append(baseline.get("domainIri")).append("`\n");
        sb.append("- **域要素**（共 ").append(baseline.getOrDefault("elementCount", 0)).append(" 个）：");
        Object labels = baseline.get("elementLabels");
        if (labels instanceof List<?> ls) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) ls;
            sb.append(joinOrDash(list));
        } else {
            sb.append("—");
        }
        sb.append("\n\n");
    }

    private void appendTrendSection(StringBuilder sb, String domain) {
        List<OWLNamedIndividual> trends = filterByType(domainElements(domain), C_INDUSTRY_TREND);
        sb.append("#### 节点 1 · 看行业/趋势（`scan-industry-trend` → `industryTrendResult`）—— 共 ").append(trends.size()).append(" 条\n\n");
        if (trends.isEmpty()) {
            sb.append("> 本域无趋势数据。\n\n");
            return;
        }
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

    private void appendMarketCustomerSection(StringBuilder sb) {
        List<OWLNamedIndividual> markets = globalInstances(C_MARKET);
        List<OWLNamedIndividual> customers = globalInstances(C_CUSTOMER);
        sb.append("#### 节点 2 · 看市场/客户（`scan-market-customer` → `marketCustomerResult`）—— 市场 ").append(markets.size())
                .append(" / 客户 ").append(customers.size()).append("\n\n");

        List<String> marketLabels = new ArrayList<>();
        for (OWLNamedIndividual m : markets) {
            marketLabels.add(label(m.getIRI().toString()));
        }
        sb.append("**市场**：").append(joinOrDash(marketLabels)).append("\n\n");

        if (customers.isEmpty()) {
            sb.append("> 无客户数据。\n\n");
            return;
        }
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

    private void appendCompetitionSection(StringBuilder sb) {
        List<OWLNamedIndividual> landscapes = globalInstances(C_COMPETITIVE_LANDSCAPE);
        sb.append("#### 节点 3 · 看竞争（`scan-competition` → `competitionResult`）—— 共 ").append(landscapes.size()).append(" 组\n\n");
        if (landscapes.isEmpty()) {
            sb.append("> 无竞争格局数据。\n\n");
            return;
        }
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

    private void appendSelfSection(StringBuilder sb) {
        List<OWLNamedIndividual> selfs = globalInstances(C_SELF_ASSESSMENT);
        List<OWLNamedIndividual> capabilities = new ArrayList<>();
        List<String> shortBoards = new ArrayList<>();
        for (OWLNamedIndividual s : selfs) {
            capabilities.addAll(targets(s, P_HAS_CAPABILITY));
            shortBoards.addAll(targetLabels(s, P_HAS_SHORT_BOARD));
        }
        sb.append("#### 节点 4 · 看自己（`scan-self` → `selfAssessmentResult`）—— 能力 ").append(capabilities.size())
                .append(" / 短板 ").append(shortBoards.size()).append("\n\n");
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
    }

    private void appendOpportunitySection(StringBuilder sb, String domain) {
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
        if (opps.isEmpty()) {
            sb.append("> 本域无机会点数据。\n\n");
            return;
        }
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

    private void appendControlPointSection(StringBuilder sb) {
        List<OWLNamedIndividual> cps = globalInstances(C_CONTROL_POINT);
        cps.removeIf(i -> isInstance(i, C_CONTROL_POINT_TYPE));
        sb.append("#### 节点 6 · 定战略控制点（`define-control-point` → `controlPointResult`）—— 共 ").append(cps.size()).append(" 个\n\n");
        if (cps.isEmpty()) {
            sb.append("> 无战略控制点数据。\n\n");
            return;
        }
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

    private boolean appendObjectiveSection(StringBuilder sb) {
        List<OWLNamedIndividual> objs = globalInstances(C_OBJECTIVE);
        boolean allCompliant = !objs.isEmpty();
        sb.append("#### 节点 7 · 定目标（`define-objective` → `objectiveResult`）—— 共 ").append(objs.size()).append(" 个\n\n");
        if (objs.isEmpty()) {
            sb.append("> 无目标数据。\n\n");
            return false;
        }
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
        return allCompliant;
    }

    private void appendStrategySection(StringBuilder sb) {
        List<OWLNamedIndividual> strategies = globalInstances(C_STRATEGY);
        sb.append("#### 节点 8 · 定策略（`define-strategy` → `strategyResult`）—— 共 ").append(strategies.size()).append(" 个\n\n");
        if (strategies.isEmpty()) {
            sb.append("> 本实例未产出策略（strategyCount = 0），待补充。\n\n");
            return;
        }
        sb.append("| 策略 | 类型 | 所属目标 |\n| --- | --- | --- |\n");
        for (OWLNamedIndividual st : strategies) {
            sb.append("| ").append(label(st.getIRI().toString()))
                    .append(" | ").append(orDash(mostSpecificType(st)))
                    .append(" | ").append(orDash(singleTargetLabel(st, P_STRATEGY_OF)))
                    .append(" |\n");
        }
        sb.append('\n');
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
