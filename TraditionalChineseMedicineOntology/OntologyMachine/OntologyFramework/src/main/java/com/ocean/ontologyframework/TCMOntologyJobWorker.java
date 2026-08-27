package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.rdf.model.RDFNode;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;

import java.util.*;
import java.util.stream.Collectors;

import static org.semanticweb.owlapi.vocab.OWLRDFVocabulary.RDF_TYPE;

@Component
@Profile("TCMBPMN")
public class TCMOntologyJobWorker {

    private static final Logger log = LoggerFactory.getLogger(TCMOntologyJobWorker.class);

    @Value("${ontology.main-path}")
    private String mainOntologyPath;

    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private String obdaPropertiesPath;

    private InsertService insertService;
    private UpdateService updateService;
    private DeleteService deleteService;
    private QueryService queryService;
    private BackendService backendService;

    // 命名空间常量
    private static final String TCM_NS = "http://www.tcm-classics.org/tcm#";
    private static final String BZ_NS = "http://www.tcm-classics.org/bingzheng#";
    private static final String ZZ_NS = "http://www.tcm-classics.org/zhengzhuangtizheng#";
    private static final String FJ_NS = "http://www.tcm-classics.org/fangji#";
    private static final String JJ_NS = "http://www.tcm-classics.org/jianjia#";

    @PostConstruct
    public void init() throws Exception {
        log.info("🔧 初始化 TCMOntologyJobWorker 依赖链...");

        OBDAHandler.init(obdaPropertiesPath, obdaPath);
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        this.backendService = BackendService.getInstance(mainOntologyPath, obdaHandler);
        if (this.backendService == null) {
            throw new IllegalStateException("BackendService 初始化失败，请检查本体路径和 OBDA 连接");
        }

        this.insertService = new InsertService(this.backendService);
        this.updateService = new UpdateService(this.backendService);
        this.deleteService = new DeleteService(this.backendService);
        this.queryService = new QueryService(this.backendService);

        log.info("✅ TCMOntologyJobWorker 初始化完成 | ontologyPath={}", mainOntologyPath);
        // 加载方剂 ABox 数据到推理器
        loadFormulaAbox();

        log.info("✅ TCMOntologyJobWorker 初始化完成，包含方剂 ABox 数据");
        Set<OWLNamedIndividual> formulaInstances = backendService.getIndividuals("http://www.tcm-classics.org/tcm#Formula");
        log.info("加载后方剂个体数：{}", formulaInstances.size());
    }

    /**
     * 通过 Ontop 加载方剂 ABox 数据（方剂个体及其主治病证）
     */
    private void loadFormulaAbox() throws Exception {
        // 构造 CONSTRUCT 查询，利用已有的 formula_mapping 中的 tcm:treats_pattern
        // 将其转换为 tcm:indicated_for 关系，并声明方剂个体类型
        String constructSparql = """
        PREFIX tcm: <http://www.tcm-classics.org/tcm#>
        PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        CONSTRUCT {
            ?formula a tcm:Formula ;
                     tcm:indicated_for ?pattern .
        }
        WHERE {
            ?formula a tcm:Formula ;
                     tcm:treats_pattern ?pattern .
        }
    """;

        // 获取 TBox 本体（已加载所有 OWL 文件）
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        // 通过 OBDAHandler 执行查询，并将结果公理直接添加到 TBox 中
        backendService.getObdaHandler().loadAboxFromOntop(constructSparql, tbox);

        // 刷新推理器，使新增的 ABox 公理生效
        backendService.getReasonerService().getReasoner().flush();

        log.info("✅ 方剂 ABox 加载完成，共添加了方剂实例及其 indicated_for 关系");
    }
    // ==================== 第一步：症状映射（文本→本体IRI） ====================
    @JobWorker(type = "step1-sizhen", autoComplete = false)
    public void handleStep1SiZhen(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> symptomInputs = (List<String>) vars.get("symptomInputs");
            if (symptomInputs == null || symptomInputs.isEmpty()) {
                throw new IllegalArgumentException("缺少症状输入列表（流程变量 symptomInputs）");
            }

            List<String> symptomIris = new ArrayList<>();
            for (String input : symptomInputs) {
                String iri = null;
                // 依次尝试四个本体
                iri = findMatchInOntology(input,
                        "http://www.tcm-classics.org/zhengzhuangtizheng#",
                        Arrays.asList("http://www.tcm-classics.org/zhengzhuangtizheng#originalDescription")
                );
                // 在 handleStep1SiZhen 方法中，替换原有的脉象匹配逻辑：

                if (iri == null && input.contains("脉")) {
                    List<String> pulseParts = PulseParser.splitPulseDescriptions(input);
                    List<String> matchedPulseIris = new ArrayList<>();
                    for (String pulsePart : pulseParts) {
                        iri = findMatchInOntology(pulsePart,
                                "http://www.tcm-classics.org/maixiang#",
                                Arrays.asList("http://www.tcm-classics.org/maixiang#finger_feeling_description")
                        );
                        if (iri != null && !matchedPulseIris.contains(iri)) {
                            matchedPulseIris.add(iri);
                            log.debug("✅ 脉象匹配成功: {} → {}", pulsePart, iri);
                        } else {
                            log.warn("⚠️ 脉象未找到: {}", pulsePart);
                        }
                    }
                    if (!matchedPulseIris.isEmpty()) {
                        symptomIris.addAll(matchedPulseIris);
                        continue; // 脉象匹配成功，跳过后续本体
                    }
                }
                if (iri == null) {
                    iri = findMatchInOntology(input,
                            "http://www.tcm-classics.org/shexiang#",
                            Collections.emptyList()
                    );
                }
                if (iri == null) {
                    iri = findMatchInOntology(input,
                            "http://www.tcm-classics.org/jianjia#",
                            Collections.emptyList()
                    );
                }
                if (iri != null) {
                    symptomIris.add(iri);
                    log.debug("✅ 匹配成功: {} → {}", input, iri);
                } else {
                    log.warn("⚠️ 未找到精确匹配: {}", input);
                }
            }

            //String caseIri = createClinicalCase(symptomIris);
            String caseIri = "随后增加";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("symptomIris", symptomIris);
            result.put("symptomLiterals", symptomInputs);
            result.put("clinicalCaseIri", caseIri);

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("✅ step1-sizhen 完成 | jobKey={} | 匹配到 {} 个症状", job.getKey(), symptomIris.size());

        } catch (Exception e) {
            log.error("❌ step1-sizhen 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP1_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * 在指定命名空间的本体中根据文本查找对应个体IRI（含SKOS映射）
     *
     * @param text          待匹配的文本
     * @param namespace     目标命名空间（如 "http://www.tcm-classics.org/zhengzhuangtizheng#"）
     * @param extraPropIris 额外要检查的注释属性IRI列表（如 originalDescription），可为空列表
     * @return 匹配的本体个体IRI，若未找到返回null
     */
    private String findMatchInOntology(String text, String namespace, List<String> extraPropIris) {
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        List<OWLAnnotationProperty> searchProps = new ArrayList<>();
        // 基础属性：rdfs:label 和 SKOS 四类标签
        searchProps.add(df.getRDFSLabel());
        searchProps.add(df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel")));
        searchProps.add(df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#altLabel")));
        searchProps.add(df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#hiddenLabel")));
        searchProps.add(df.getOWLAnnotationProperty(IRI.create("http://www.w3.org/2004/02/skos/core#note")));
        // 添加额外属性（如适用）
        if (extraPropIris != null) {
            for (String iri : extraPropIris) {
                searchProps.add(df.getOWLAnnotationProperty(IRI.create(iri)));
            }
        }
        return queryService.findIndividualByLabel(
                text,
                namespace,
                searchProps,
                true,   // 包括 SKOS 搜索
                "http://www.w3.org/2004/02/skos/core#Concept",
                "http://www.w3.org/2004/02/skos/core#exactMatch"
        );
    }

// 原有的 createClinicalCase 等方法保持不变

    /**
     * 转义字符串中的特殊字符，用于SPARQL FILTER
     */
    private String escapeString(String s) {
        // 简单转义：将双引号、反斜杠等替换
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ==================== 第二步：分辨病位 ====================
    @JobWorker(type = "step2-bingwei", autoComplete = false)
    public void handleStep2BingWei(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            if (symptomIris == null || symptomIris.isEmpty()) {
                throw new IllegalArgumentException("缺少症状IRI，请先执行第一步");
            }

            // 调用推理服务判断病位
            String bingWei = inferBingWei(symptomIris); // 返回 "表"、"里"、"半表半里"
            if (bingWei == null) bingWei = "未知";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bingWei", bingWei);

            // 保留原有变量以便后续步骤
            result.put("symptomIris", symptomIris);
            result.put("clinicalCaseIri", vars.get("clinicalCaseIri"));

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("✅ step2-bingwei 完成 | jobKey={} | 病位={}", job.getKey(), bingWei);

        } catch (Exception e) {
            log.error("❌ step2-bingwei 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP2_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * 根据症状 IRI 列表推断病位（表/里/半表半里）
     * 使用推理器获取每个症状的 bagang 归属，并结合诊断权重进行加权投票。
     *
     * @param symptomIris 症状本体个体 IRI 列表
     * @return 病位名称（"表" / "里" / "半表半里" / "未知"）
     */
    /**
     * 根据症状 IRI 列表推断病位（表/里/半表半里）
     * 使用推理器获取每个症状的 symptom_indicates_bagang 值，结合诊断权重加权投票。
     *
     * @param symptomIris 症状本体个体 IRI 列表
     * @return 病位名称（"表" / "里" / "半表半里" / "未知"）
     */
    private String inferBingWei(List<String> symptomIris) {
        if (symptomIris == null || symptomIris.isEmpty()) return "未知";

        Map<String, Double> scores = new HashMap<>();
        scores.put("表", 0.0);
        scores.put("里", 0.0);
        scores.put("半表半里", 0.0);

        OWLOntology ontology = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        if (reasoner == null) {
            log.warn("推理器不可用，无法进行病位推理");
            return "未知";
        }

        OWLObjectProperty indicatesBagangProp = df.getOWLObjectProperty(
                IRI.create("http://www.tcm-classics.org/tcm#symptom_indicates_bagang"));
        OWLDataProperty weightProp = df.getOWLDataProperty(
                IRI.create("http://www.tcm-classics.org/tcm#has_diagnostic_weight"));

        IRI biaoIRI = IRI.create("http://www.tcm-classics.org/bagang#Biao");
        IRI liIRI = IRI.create("http://www.tcm-classics.org/bagang#Li");
        IRI banIRI = IRI.create("http://www.tcm-classics.org/bagang#BanBiaoBanLi");

        for (String iriStr : symptomIris) {
            OWLNamedIndividual symptom = df.getOWLNamedIndividual(IRI.create(iriStr));

            // 诊断权重（缺失则 0）
            double weight = 0.0;
            Set<OWLLiteral> weightLiterals = reasoner.getDataPropertyValues(symptom, weightProp);
            if (!weightLiterals.isEmpty()) {
                try {
                    weight = Double.parseDouble(weightLiterals.iterator().next().getLiteral());
                } catch (NumberFormatException ignored) {}
            }

            // 获取症状提示的八纲要素（推理）
            NodeSet<OWLNamedIndividual> bagangValues = reasoner.getObjectPropertyValues(symptom, indicatesBagangProp);
            for (OWLNamedIndividual bagangInd : bagangValues.entities().collect(Collectors.toList())) {
                IRI iri = bagangInd.getIRI();
                if (iri.equals(biaoIRI)) scores.put("表", scores.get("表") + weight);
                else if (iri.equals(liIRI)) scores.put("里", scores.get("里") + weight);
                else if (iri.equals(banIRI)) scores.put("半表半里", scores.get("半表半里") + weight);
            }
        }

        String best = "未知";
        double max = 0.0;
        for (Map.Entry<String, Double> e : scores.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        return max > 0 ? best : "未知";
    }

    // ==================== 第三步：区分病性 ====================
    @JobWorker(type = "step3-bingxing", autoComplete = false)
    public void handleStep3BingXing(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            if (symptomIris == null || symptomIris.isEmpty()) {
                throw new IllegalArgumentException("缺少症状IRI，请先执行第一步");
            }

            // 调用推理服务判断病性
            String bingXing = inferBingXing(symptomIris); // 返回 "阳" 或 "阴"
            if (bingXing == null) bingXing = "未知";

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bingXing", bingXing);

            // 保留已有变量
            result.put("symptomIris", symptomIris);
            result.put("bingWei", vars.get("bingWei"));
            result.put("clinicalCaseIri", vars.get("clinicalCaseIri"));

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("✅ step3-bingxing 完成 | jobKey={} | 病性={}", job.getKey(), bingXing);

        } catch (Exception e) {
            log.error("❌ step3-bingxing 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP3_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * 根据症状 IRI 列表推断病性（阳/阴）
     * 使用推理器获取每个症状所提示的八纲要素，并通过其 summarisesAs 属性映射到阴阳，
     * 结合诊断权重进行加权投票。
     *
     * @param symptomIris 症状本体个体 IRI 列表
     * @return 病性名称（"阳" / "阴" / "未知"）
     */
    /**
     * 根据症状IRI推断病性（阳/阴）
     * 使用 tcm:symptom_indicates_bagang → bagang:summarizesAs 链式推理
     */
    private String inferBingXing(List<String> symptomIris) {
        if (symptomIris == null || symptomIris.isEmpty()) return "未知";

        double yangScore = 0.0, yinScore = 0.0;

        OWLOntology ontology = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        if (reasoner == null) {
            log.warn("推理器不可用");
            return "未知";
        }

        OWLObjectProperty indicatesBagangProp = df.getOWLObjectProperty(
                IRI.create("http://www.tcm-classics.org/tcm#symptom_indicates_bagang"));
        OWLObjectProperty summarizesAsProp = df.getOWLObjectProperty(
                IRI.create("http://www.tcm-classics.org/bagang#summarizesAs"));
        OWLDataProperty weightProp = df.getOWLDataProperty(
                IRI.create("http://www.tcm-classics.org/tcm#has_diagnostic_weight"));

        IRI yangIRI = IRI.create("http://www.tcm-classics.org/bagang#Yang");
        IRI yinIRI = IRI.create("http://www.tcm-classics.org/bagang#Yin");

        for (String iriStr : symptomIris) {
            OWLNamedIndividual symptom = df.getOWLNamedIndividual(IRI.create(iriStr));

            double weight = 0.0;
            Set<OWLLiteral> weightLiterals = reasoner.getDataPropertyValues(symptom, weightProp);
            if (!weightLiterals.isEmpty()) {
                try {
                    weight = Double.parseDouble(weightLiterals.iterator().next().getLiteral());
                } catch (NumberFormatException ignored) {}
            }

            NodeSet<OWLNamedIndividual> bagangValues = reasoner.getObjectPropertyValues(symptom, indicatesBagangProp);
            for (OWLNamedIndividual bagangInd : bagangValues.entities().collect(Collectors.toList())) {
                IRI bagangIRI = bagangInd.getIRI();
                // 如果症状直接指向阴阳
                if (bagangIRI.equals(yangIRI)) {
                    yangScore += weight;
                    continue;
                } else if (bagangIRI.equals(yinIRI)) {
                    yinScore += weight;
                    continue;
                }

                // 否则查找该八纲要素的 summarisesAs 值
                NodeSet<OWLNamedIndividual> summaries = reasoner.getObjectPropertyValues(bagangInd, summarizesAsProp);
                for (OWLNamedIndividual summary : summaries.entities().collect(Collectors.toList())) {
                    IRI summaryIRI = summary.getIRI();
                    if (summaryIRI.equals(yangIRI)) yangScore += weight;
                    else if (summaryIRI.equals(yinIRI)) yinScore += weight;
                }
            }
        }

        if (yangScore > yinScore) return "阳";
        else if (yinScore > yangScore) return "阴";
        else return "未知";
    }

    // ==================== 第四步：定六经 ====================
    @JobWorker(type = "step4-liujing", autoComplete = false)
    public void handleStep4LiuJing(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String bingWei = (String) vars.get("bingWei");
            String bingXing = (String) vars.get("bingXing");
            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            String caseIri = (String) vars.get("clinicalCaseIri");

            if (symptomIris == null || symptomIris.isEmpty()) {
                throw new IllegalArgumentException("缺少症状IRI，请先执行第一步");
            }

            String sixChannel = null;

            // 1. 如果已有病位和病性，则直接组合六经
            if (bingWei != null && bingXing != null && !"未知".equals(bingWei) && !"未知".equals(bingXing)) {
                sixChannel = combineToSixChannel(bingWei, bingXing);
            }

            // 2. 如果组合失败或没有病位病性，则使用症状推理（回退方案）
            if (sixChannel == null) {
                Set<String> possibleLiujing = getBestMatchedTypeForSymptoms(symptomIris,
                        "http://www.tcm-classics.org/liujing#SixChannelSyndrome");
                if (possibleLiujing != null && !possibleLiujing.isEmpty()) {
                    // 取第一个结果（通常只有一个）
                    sixChannel = possibleLiujing.iterator().next();
                }
            }

            if (sixChannel == null) {
                sixChannel = "http://www.tcm-classics.org/liujing#Unknown_Instance";
            }

            // 更新临床案例的六经属性
            /*
            if (caseIri != null) {
                Map<String, String> updateProps = new LinkedHashMap<>();
                updateProps.put("http://www.tcm-classics.org/tcm#belongs_to_channel", sixChannel);
                updateService.updateComponentAutoSplit(
                        Map.of("http://www.tcm-classics.org/tcm#name", caseIri),
                        updateProps
                );
            }*/

            // 获取六经标签（通过SKOS反向查找）
            List<String> labels = SkosSynonymReader.getSynonymsByOwlIndividual(sixChannel);
            String label = labels.isEmpty() ? sixChannel : labels.get(0);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sixChannel", sixChannel);
            result.put("sixChannelLabel", label);
            result.put("clinicalCaseIri", caseIri);

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("✅ step4-liujing 完成 | jobKey={} | 六经={}", job.getKey(), sixChannel);

        } catch (Exception e) {
            log.error("❌ step4-liujing 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP4_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * 根据症状IRI列表推断可能的六经（Set形式，便于未来扩展合病）
     * 若无法确定则返回空集。
     *
     * @param symptomIris 症状IRI列表
     * @param topClassIRI 六经顶层类IRI（仅用于类型标识，实际推理不依赖此参数）
     * @return 六经实例IRI集合（通常仅一个元素）
     */
    private Set<String> getBestMatchedTypeForSymptoms(List<String> symptomIris, String topClassIRI) {
        Set<String> result = new HashSet<>();
        if (symptomIris == null || symptomIris.isEmpty()) {
            return result;
        }

        // 复用第二步和第三步的推理逻辑
        String bingWei = inferBingWei(symptomIris);
        String bingXing = inferBingXing(symptomIris);

        if (bingWei != null && bingXing != null && !"未知".equals(bingWei) && !"未知".equals(bingXing)) {
            String sixChannel = combineToSixChannel(bingWei, bingXing);
            if (sixChannel != null) {
                result.add(sixChannel);
            }
        }
        return result;
    }

    /**
     * 根据病位和病性组合成六经IRI
     * 胡希恕对应关系：
     * 表+阳 → 太阳病 (Taiyang_Instance)
     * 表+阴 → 少阴病 (Shaoyin_Instance)
     * 里+阳 → 阳明病 (Yangming_Instance)
     * 里+阴 → 太阴病 (Taiyin_Instance)
     * 半表半里+阳 → 少阳病 (Shaoyang_Instance)
     * 半表半里+阴 → 厥阴病 (Jueyin_Instance)
     */
    private String combineToSixChannel(String bingWei, String bingXing) {
        String key = bingWei + "+" + bingXing;
        switch (key) {
            case "表+阳": return "http://www.tcm-classics.org/liujing#Taiyang_Instance";
            case "表+阴": return "http://www.tcm-classics.org/liujing#Shaoyin_Instance";
            case "里+阳": return "http://www.tcm-classics.org/liujing#Yangming_Instance";
            case "里+阴": return "http://www.tcm-classics.org/liujing#Taiyin_Instance";
            case "半表半里+阳": return "http://www.tcm-classics.org/liujing#Shaoyang_Instance";
            case "半表半里+阴": return "http://www.tcm-classics.org/liujing#Jueyin_Instance";
            default: return null;
        }
    }

    // ==================== 第五步：辨兼夹 ====================
    @JobWorker(type = "step5-jianjia", autoComplete = false)
    public void handleStep5JianJia(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            @SuppressWarnings("unchecked")
            List<String> symptomIris = (List<String>) vars.get("symptomIris");
            String caseIri = (String) vars.get("clinicalCaseIri");

            // 调用推理判断是否存在水饮、瘀血、食积、气滞
            Set<String> concomitantPathologies = new LinkedHashSet<>();
            if (hasSymptomMatching(symptomIris, JJ_NS + "WaterRetention_Instance")) {
                concomitantPathologies.add("http://www.tcm-classics.org/jianjia#WaterRetention_Instance");
            }
            if (hasSymptomMatching(symptomIris, JJ_NS + "BloodStasis_Instance")) {
                concomitantPathologies.add("http://www.tcm-classics.org/jianjia#BloodStasis_Instance");
            }
            if (hasSymptomMatching(symptomIris, JJ_NS + "FoodStagnation_Instance")) {
                concomitantPathologies.add("http://www.tcm-classics.org/jianjia#FoodStagnation_Instance");
            }
            if (hasSymptomMatching(symptomIris, JJ_NS + "QiStagnation_Instance")) {
                concomitantPathologies.add("http://www.tcm-classics.org/jianjia#QiStagnation_Instance");
            }

            // 存入案例
            /*
            if (caseIri != null) {
                Map<String, String> updateProps = new LinkedHashMap<>();
                updateProps.put("http://www.tcm-classics.org/jianjia#hasConcomitantPathology",
                        String.join(",", concomitantPathologies));
                updateService.updateComponentAutoSplit(
                        Map.of("http://www.tcm-classics.org/tcm#name", caseIri),
                        updateProps
                );
            }*/

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("concomitantPathologies", new ArrayList<>(concomitantPathologies));
            result.put("clinicalCaseIri", caseIri);

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("✅ step5-jianjia 完成 | jobKey={} | 兼夹数={}", job.getKey(), concomitantPathologies.size());

        } catch (Exception e) {
            log.error("❌ step5-jianjia 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP5_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * 检查给定的症状列表中是否包含某个兼夹证实例的识别症状
     *
     * @param symptomIris      症状IRI列表（来自第一步的本体匹配结果）
     * @param concomitantIri   兼夹证实例的完整IRI（如 http://www.tcm-classics.org/jianjia#WaterRetention_Instance）
     * @return true 如果症状列表中至少有一个症状是该兼夹证的识别症状；否则 false
     */
    private boolean hasSymptomMatching(List<String> symptomIris, String concomitantIri) {
        if (symptomIris == null || symptomIris.isEmpty()) {
            return false;
        }

        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        if (reasoner == null) {
            log.warn("推理器不可用，无法进行兼夹证匹配");
            return false;
        }

        try {
            OWLNamedIndividual concomitant = df.getOWLNamedIndividual(IRI.create(concomitantIri));
            OWLObjectProperty identificationProp = df.getOWLObjectProperty(
                    IRI.create("http://www.tcm-classics.org/jianjia#hasIdentificationSymptom")
            );

            // 获取该兼夹证实例所关联的所有识别症状（推理后）
            NodeSet<OWLNamedIndividual> symptomNodes = reasoner.getObjectPropertyValues(concomitant, identificationProp);
            Set<String> identificationSymptoms = symptomNodes.entities()
                    .map(ind -> ind.getIRI().toString())
                    .collect(Collectors.toSet());

            if (identificationSymptoms.isEmpty()) {
                log.debug("兼夹证 {} 未定义识别症状，忽略", concomitantIri);
                return false;
            }

            // 检查交集
            for (String symptom : symptomIris) {
                if (identificationSymptoms.contains(symptom)) {
                    log.debug("症状 {} 匹配兼夹证 {} 的识别症状列表", symptom, concomitantIri);
                    return true;
                }
            }
        } catch (Exception e) {
            log.error("匹配兼夹证 {} 时发生异常", concomitantIri, e);
        }
        return false;
    }

    // ==================== 第六步：主证方初筛 ====================
    @JobWorker(type = "step6-mainformula", autoComplete = false)
    public void handleStep6MainFormula(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String caseIri = (String) vars.get("clinicalCaseIri");
            String formulaIri = getFormulaByCase(caseIri);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mainFormulaIri", formulaIri);
            result.put("mainFormulaLabel", formulaIri != null ? SkosSynonymReader.getAllLabels(formulaIri) : "未找到合适方剂");
            result.put("clinicalCaseIri", caseIri);

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("✅ step6-mainformula 完成 | jobKey={} | 方剂={}", job.getKey(), formulaIri);

        } catch (Exception e) {
            log.error("❌ step6-mainformula 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP6_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    /**
     * 通过临床案例推理主证，再反向查找对应的方剂
     */
    /**
     * 通过临床案例推理主证，再反向查找对应的方剂。
     * 优先使用推理机的SWRL规则结果，若失败则手动根据高权重症状推断。
     */
    private String getFormulaByCase(String caseIri) {
        if (caseIri == null) return null;
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        OWLOntology ontology = backendService.getOntologyService().gettBoxOntology();

        // 获取案例个体
        OWLNamedIndividual caseInd = df.getOWLNamedIndividual(IRI.create(caseIri));
        OWLObjectProperty hasPrimaryPatternProp = df.getOWLObjectProperty(
                IRI.create("http://www.tcm-classics.org/tcm#has_primary_pattern"));

        // ========== 第一步：尝试通过推理机获取主证 ==========
        NodeSet<OWLNamedIndividual> primaryPatterns = reasoner.getObjectPropertyValues(caseInd, hasPrimaryPatternProp);
        OWLNamedIndividual pattern = primaryPatterns.entities().findFirst().orElse(null);

        if (pattern != null) {
            log.info("✅ 推理机成功推出主证: {}", pattern.getIRI());
            return findFormulaByPattern(pattern.getIRI().toString());
        }

        // ========== 第二步：推理失败，手动根据高权重症状推断 ==========
        log.warn("⚠️ 推理机未推出主证，将使用手动推断（基于高权重症状）");

        // 获取案例的所有症状
        OWLObjectProperty hasSymptomProp = df.getOWLObjectProperty(
                IRI.create("http://www.tcm-classics.org/tcm#has_symptom"));
        NodeSet<OWLNamedIndividual> symptomNodes = reasoner.getObjectPropertyValues(caseInd, hasSymptomProp);
        Set<OWLNamedIndividual> caseSymptoms = symptomNodes.entities().collect(Collectors.toSet());

        if (caseSymptoms.isEmpty()) {
            log.warn("⚠️ 案例无任何症状，无法推断主证");
            return null;
        }

        // 获取每个症状的诊断权重，筛选出高权重症状（≥0.8）
        OWLDataProperty weightProp = df.getOWLDataProperty(
                IRI.create("http://www.tcm-classics.org/tcm#has_diagnostic_weight"));
        List<OWLNamedIndividual> highWeightSymptoms = new ArrayList<>();
        for (OWLNamedIndividual symptom : caseSymptoms) {
            Set<OWLLiteral> weightLiterals = reasoner.getDataPropertyValues(symptom, weightProp);
            if (!weightLiterals.isEmpty()) {
                try {
                    double weight = Double.parseDouble(weightLiterals.iterator().next().getLiteral());
                    if (weight >= 0.8) {
                        highWeightSymptoms.add(symptom);
                        log.debug("高权重症状: {} (权重={})", symptom.getIRI(), weight);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        if (highWeightSymptoms.isEmpty()) {
            log.warn("⚠️ 未找到高权重症状（≥0.8），无法推断主证");
            return null;
        }

        // 通过高权重症状查找它们所属的病证（part_of_pattern），取出现次数最多的病证作为主证
        OWLObjectProperty partOfPatternProp = df.getOWLObjectProperty(
                IRI.create("http://www.tcm-classics.org/zhengzhuangtizheng#part_of_pattern"));
        Map<String, Integer> patternCount = new HashMap<>();
        for (OWLNamedIndividual symptom : highWeightSymptoms) {
            NodeSet<OWLNamedIndividual> patterns = reasoner.getObjectPropertyValues(symptom, partOfPatternProp);
            patterns.entities().forEach(p -> {
                String iri = p.getIRI().toString();
                patternCount.put(iri, patternCount.getOrDefault(iri, 0) + 1);
            });
        }

        if (patternCount.isEmpty()) {
            log.warn("⚠️ 高权重症状未关联任何病证");
            return null;
        }

        // 选择出现次数最多的病证
        String bestPattern = patternCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        log.info("✅ 手动推断主证: {} (匹配症状数: {})", bestPattern, patternCount.get(bestPattern));

        return findFormulaByPattern(bestPattern);
    }

    /**
     * 根据病证IRI查找主治该病证的方剂
     */
    private String findFormulaByPattern(String patternIri) {
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();

        OWLClass formulaClass = df.getOWLClass(IRI.create("http://www.tcm-classics.org/tcm#Formula"));
        OWLObjectProperty indicatedForProp = df.getOWLObjectProperty(
                IRI.create("http://www.tcm-classics.org/tcm#indicated_for"));

        Set<OWLNamedIndividual> formulaIndividuals = reasoner.getInstances(formulaClass, true)
                .entities().collect(Collectors.toSet());

        for (OWLNamedIndividual formulaInd : formulaIndividuals) {
            NodeSet<OWLNamedIndividual> patterns = reasoner.getObjectPropertyValues(formulaInd, indicatedForProp);
            if (patterns.entities().anyMatch(p -> p.getIRI().toString().equals(patternIri))) {
                return formulaInd.getIRI().toString();
            }
        }

        log.warn("⚠️ 未找到主治病证 {} 的方剂", patternIri);
        return null;
    }

    /**
     * 内部类：方剂候选得分
     */
    private static class FormulaScore {
        String formulaIri;
        String patternIri;
        double score; // 匹配度（匹配症状数 / 病证总症状数，或仅匹配数）
        FormulaScore(String formula, String pattern, double score) {
            this.formulaIri = formula;
            this.patternIri = pattern;
            this.score = score;
        }
    }

    /**
     * 获取病证所属的六经IRI（用于过滤）
     */
    private String getDiseasePatternChannel(String patternIri) {
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        if (reasoner == null) return null;
        OWLNamedIndividual pattern = df.getOWLNamedIndividual(IRI.create(patternIri));
        OWLObjectProperty belongsToChannelProp = df.getOWLObjectProperty(
                IRI.create("http://www.tcm-classics.org/tcm#belongs_to_channel"));
        NodeSet<OWLNamedIndividual> channels = reasoner.getObjectPropertyValues(pattern, belongsToChannelProp);
        return channels.entities().findFirst()
                .map(ind -> ind.getIRI().toString())
                .orElse(null);
    }

    // ==================== 第七步：判定治法 ====================
    @JobWorker(type = "step7-zhifa", autoComplete = false)
    public void handleStep7ZhiFa(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String formulaIri = (String) vars.get("mainFormulaIri");
            String sixChannel = (String) vars.get("sixChannel");

            // 从本体查询该方剂的治则、禁忌等
            List<String> treatmentMethods = queryService.queryPropertyValueInOntology(formulaIri,
                    "http://www.tcm-classics.org/tcm#has_treatment_method");
            List<String> contraindications = queryService.queryPropertyValueInOntology(formulaIri,
                    "http://www.tcm-classics.org/tcm#contraindication");

            // 也可以根据六经查询通用禁忌
            if (sixChannel != null && sixChannel.contains("Shaoyang")) {
                contraindications.add("禁汗、禁下、禁吐");
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("treatmentMethods", treatmentMethods);
            result.put("contraindications", contraindications);
            result.put("treatmentSummary", String.join("；", treatmentMethods) +
                    (contraindications.isEmpty() ? "" : "；禁忌：" + String.join("；", contraindications)));

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("✅ step7-zhifa 完成 | jobKey={} | 治法数={}", job.getKey(), treatmentMethods.size());

        } catch (Exception e) {
            log.error("❌ step7-zhifa 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP7_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    // ==================== 第八步：复诊观测 ====================
    @JobWorker(type = "step8-fuzhen", autoComplete = false)
    public void handleStep8FuZhen(final ActivatedJob job, final JobClient client) {
        try {
            Map<String, Object> vars = job.getVariablesAsMap();

            String caseIri = (String) vars.get("clinicalCaseIri");
            @SuppressWarnings("unchecked")
            List<String> newSymptoms = (List<String>) vars.get("followupSymptoms");
            if (newSymptoms == null) newSymptoms = Collections.emptyList();

            @SuppressWarnings("unchecked")
            List<String> originalSymptoms = (List<String>) vars.get("symptomIris");
            boolean allResolved = originalSymptoms != null && !originalSymptoms.isEmpty() &&
                    newSymptoms.stream().noneMatch(originalSymptoms::contains);

            boolean cured = allResolved || (newSymptoms.isEmpty() && vars.get("mainFormulaIri") != null);

            boolean hasTransmission = false;
            if (!cured && !newSymptoms.isEmpty()) {
                String newSix = getBestMatchedLiuJing(newSymptoms);
                String oldSix = (String) vars.get("sixChannel");
                if (newSix != null && !newSix.equals(oldSix)) {
                    hasTransmission = true;
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cured", cured);
            result.put("hasTransmission", hasTransmission);
            result.put("followupSymptomIris", newSymptoms);

            if (hasTransmission) {
                Map<String, String> update = new LinkedHashMap<>();
                update.put("http://www.tcm-classics.org/tcm#belongs_to_channel",
                        getBestMatchedLiuJing(newSymptoms));
                updateService.updateComponentAutoSplit(
                        Map.of("http://www.tcm-classics.org/tcm#name", caseIri),
                        update
                );
            }

            client.newCompleteCommand(job.getKey())
                    .variables(result)
                    .send().join();

            log.info("✅ step8-fuzhen 完成 | jobKey={} | 痊愈={} | 传变={}", job.getKey(), cured, hasTransmission);

        } catch (Exception e) {
            log.error("❌ step8-fuzhen 失败", e);
            client.newThrowErrorCommand(job.getKey())
                    .errorCode("STEP8_FAILED")
                    .errorMessage(e.getMessage())
                    .send().join();
        }
    }

    //测试占位
    private String getBestMatchedLiuJing(List<String> newSymptoms) {
        return null;
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建临床案例实例（插入 ABox）
     */
    private String createClinicalCase(List<String> symptomIris) throws OWLOntologyCreationException {
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OWLDataFactory df = backendService.getOntologyService().getDataFactory();
        OWLOntologyManager manager = backendService.getOntologyService().getManager();

        String indNS = "http://www.tcm-classics.org/clinical/";
        String instanceName = "Case_" + System.currentTimeMillis();
        IRI individualIRI = IRI.create(indNS + instanceName);
        OWLNamedIndividual caseInd = df.getOWLNamedIndividual(individualIRI);

        // 添加类型断言 ClinicalCase
        OWLClass clinicalCaseClass = df.getOWLClass(IRI.create("http://www.tcm-classics.org/tcm#ClinicalCase"));
        manager.addAxiom(tbox, df.getOWLClassAssertionAxiom(clinicalCaseClass, caseInd));

        // 添加名称（数据属性）
        OWLDataProperty nameProp = df.getOWLDataProperty(IRI.create("http://www.tcm-classics.org/tcm#name"));
        manager.addAxiom(tbox, df.getOWLDataPropertyAssertionAxiom(nameProp, caseInd, df.getOWLLiteral(instanceName)));

        // 添加症状（对象属性），每个症状单独一个断言
        OWLObjectProperty hasSymptomProp = df.getOWLObjectProperty(IRI.create("http://www.tcm-classics.org/tcm#has_symptom"));
        for (String symptomIri : symptomIris) {
            if (symptomIri != null && !symptomIri.isBlank()) {
                OWLNamedIndividual symptomInd = df.getOWLNamedIndividual(IRI.create(symptomIri));
                manager.addAxiom(tbox, df.getOWLObjectPropertyAssertionAxiom(hasSymptomProp, caseInd, symptomInd));
            }
        }

        // 刷新推理机，使新断言生效
        backendService.getReasonerService().getReasoner().flush();

        return individualIRI.toString();
    }
}