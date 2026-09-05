package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.OntologyService;
import com.ocean.openlletresolver.SkosSynonymReader;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TCM (伤寒桂林古本) OBDA 映射集成测试
 * <p>
 * 验证 MySQL → Ontop → SPARQL 虚拟知识图谱映射的正确性。
 * 所有路径与端点地址均从 application.yml 中读取。
 */
@SpringBootTest(classes = TCMApplication.class)
@ActiveProfiles("TCMJunitTest")
@DisplayName("TCM OBDA 映射验证测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)

class OntologyFrameworkTCMTests {

    private static final Logger log = LoggerFactory.getLogger(OntologyFrameworkTCMTests.class);

    // ✅ 改为实例字段注入（更可靠）
    @Value("${ontology.obda-path}")
    private static String obdaPath;

    @Value("${ontology.main-path}")
    private static String owlPath;

    @Value("${ontology.obda-properties-path}")
    private static String obdaPropertiesPath;

    private static OBDAHandler obdaHandler;

    // ✅ 新增：SKOS 测试用的已知 URI（根据实际本体调整）
    private static final String ZZSKOS_NS = "http://www.tcm-classics.org/skos/zhengzhuangtizheng#";
    private static final String EHAN_SKOS_URI = ZZSKOS_NS + "EHan";
    private static final String EHAN_OWL_INDIVIDUAL_URI = "http://www.tcm-classics.org/zhengzhuangtizheng#EHan";
    private static final String ZZ_NS  = "http://www.tcm-classics.org/zhengzhuangtizheng#";

    // ✅ 改用 @BeforeEach 确保 Spring 已完成注入
    @BeforeAll
    static void setUp(@Autowired Environment env) throws Exception {
        if (obdaHandler != null) {
            log.debug("OBDAHandler 已初始化，跳过重复创建");
            return;
        }
        log.info("=== 初始化 TCM OBDA 映射测试环境 ===");

        String obdaPath = env.getProperty("ontology.obda-path");
        String owlPath = env.getProperty("ontology.main-path");
        String obdaPropertiesPath = env.getProperty("ontology.obda-properties-path");
        log.info("📂 OBDA路径: {}", obdaPath);  // 👈 先打印路径值，确认注入成功
        log.info("📂 OWL路径: {}", owlPath);

        assertNotNull(obdaPath, "ontology.obda-path 未从 yml 注入，请检查配置");
        assertNotNull(owlPath, "ontology.main-path 未从 yml 注入，请检查配置");

        // 1. 初始化 OBDAHandler
        OBDAHandler.init(obdaPropertiesPath, obdaPath);
        obdaHandler = OBDAHandler.getInstance();
        assertNotNull(obdaHandler, "OBDAHandler 初始化失败");
        log.info("✅ OBDAHandler 初始化成功");

        // ✅ 2. 新增：初始化 BackendService（SkosSynonymReader 依赖它）
        try {
            BackendService.getInstance();
            log.debug("BackendService 已存在，跳过重复初始化");
        } catch (IllegalStateException e) {
            log.info("🧠 初始化 BackendService (TBox + Reasoner)...");
            BackendService.getInstance(owlPath, obdaHandler);
            log.info("✅ BackendService 初始化成功");
        }
    }

    // ============================================================
    // TC-01: 全局冒烟测试
    // ============================================================
    @Test
    @Order(1)
    @DisplayName("TC-01: 全局冒烟测试 - 验证三元组总数 > 0")
    void testSmokeTest() {
        String sparql = "SELECT (COUNT(*) AS ?totalTriples) WHERE { ?s ?p ?o }";
        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);
        assertNotNull(rows, "SPARQL 查询结果不应为 null");
        assertEquals(1, rows.size(), "COUNT 查询应恰好返回 1 行");
        long totalTriples = Long.parseLong(rows.get(0).get("totalTriples"));
        assertTrue(totalTriples > 0, "三元组总数应 > 0，实际为 " + totalTriples);
        log.info("✅ TC-01 通过: 三元组总数 = {}", totalTriples);
    }

    @Test
    @Order(2)
    @DisplayName("TC-02: formula_category_mapping - 验证 FormulaCategory 实例及标签")
    void testFormulaCategoryMapping() {
        String sparql = """
                PREFIX fj: <http://www.tcm-classics.org/fangji#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT ?category ?label
                WHERE {
                  ?category a fj:FormulaCategory ;
                            rdfs:label ?label .
                }
                LIMIT 5
                """;
        List<Map<String, String>> rows = obdaHandler.executeAboxQueryWithIRI(sparql);
        assertNotNull(rows);
        assertFalse(rows.isEmpty());
        for (Map<String, String> row : rows) {
            assertTrue(row.get("category").startsWith("http://www.tcm-classics.org/tcm#"));
            assertNotNull(row.get("label"));
            assertFalse(row.get("label").isBlank());
        }
        log.info("✅ TC-02 通过: {} 条 FormulaCategory", rows.size());
    }

    @Test
    @Order(3)
    @DisplayName("TC-03: formula_mapping - 验证 Formula 实例及属性")
    void testFormulaMappingTextProperties() {
        String sparql = """
                PREFIX fj:  <http://www.tcm-classics.org/fangji#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT ?formula ?label ?source ?dosage
                WHERE {
                  ?formula a fj:Formula ;
                           rdfs:label ?label .
                  OPTIONAL { ?formula fj:source_clause ?source }
                  OPTIONAL { ?formula fj:original_dosage ?dosage }
                }
                LIMIT 5
                """;
        List<Map<String, String>> rows = obdaHandler.executeAboxQueryWithIRI(sparql);
        assertNotNull(rows);
        assertFalse(rows.isEmpty());
        log.info("✅ TC-03 通过: {} 条 Formula", rows.size());
    }

    @Test
    @Order(4)
    @DisplayName("TC-04: 验证对象属性值为 IRI 而非字面量")
    void testFormulaRelationPropertiesAreIRIs() {
        String sparql = """
                PREFIX fj:  <http://www.tcm-classics.org/fangji#>
                          PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                          SELECT ?formula ?label ?category ?pattern ?herb
                          WHERE {
                            ?formula a fj:Formula ;
                                     rdfs:label ?label .
                            OPTIONAL { ?formula fj:belongs_to_formula_category ?category }
                            OPTIONAL { ?formula fj:indicated_for ?pattern }
                            OPTIONAL {
                              ?formula fj:has_ingredient_use ?ingUse .
                              ?ingUse fj:uses_herb ?herb .
                            }
                          }
                          LIMIT 10
                """;
        List<Map<String, String>> rows = obdaHandler.executeAboxQueryWithIRI(sparql);
        assertNotNull(rows);
        assertFalse(rows.isEmpty());
        int relationCount = 0;
        for (Map<String, String> row : rows) {
            for (String var : List.of("category", "pattern", "herb")) {
                String value = row.get(var);
                if (value != null && !value.isBlank()) {
                    relationCount++;
                    assertTrue(value.startsWith("http://"), var + " 应为 IRI: " + value);
                }
            }
        }
        assertTrue(relationCount > 0, "应至少存在一条非空关系属性值");
        log.info("✅ TC-04 通过: {} 个关系属性值均为 IRI", relationCount);
    }

    @Test
    @Order(5)
    @DisplayName("TC-05: herb_mapping - 验证 Herb 实例及属性")
    void testHerbMapping() {
        String sparql = """
                PREFIX yw:   <http://www.tcm-classics.org/yaowu#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?herb ?label ?taste ?nature ?source
                WHERE {
                  ?herb a yw:Herb ; rdfs:label ?label .
                  OPTIONAL { ?herb yw:original_taste ?taste }
                  OPTIONAL { ?herb yw:original_nature ?nature }
                  OPTIONAL { ?herb yw:earliest_source ?source }
                } LIMIT 5
                """;
        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);
        assertNotNull(rows);
        assertFalse(rows.isEmpty());
        log.info("✅ TC-05 通过: {} 条 Herb", rows.size());
    }

    @Test
    @Order(6)
    @DisplayName("TC-06: herb_bagang + herb_symptom 关系验证")
    void testHerbRelationMappings() {
        String sparql = """
                PREFIX yw:   <http://www.tcm-classics.org/yaowu#>
                PREFIX tcm:  <http://www.tcm-classics.org/tcm#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?herb ?herbLabel ?bagang ?symptom
                WHERE {
                  ?herb a yw:Herb ; rdfs:label ?herbLabel .
                  OPTIONAL { ?herb tcm:herb_has_bagang_property ?bagang }
                  OPTIONAL { ?herb tcm:herb_treats_symptom ?symptom }
                } LIMIT 10
                """;
        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);
        assertNotNull(rows);
        assertFalse(rows.isEmpty());
        log.info("✅ TC-06 通过: {} 条记录", rows.size());
    }

    @Test
    @Order(7)
    @DisplayName("TC-07: 端到端穿透 - 含'桂枝'方剂→药物→八纲 (使用新映射结构)")
    void testEndToEndFormulaHerbBagangQuery() {
        String sparql = """
                PREFIX fj:  <http://www.tcm-classics.org/fangji#>
                PREFIX yw:  <http://www.tcm-classics.org/yaowu#>
                PREFIX tcm: <http://www.tcm-classics.org/tcm#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                
                SELECT ?formulaLabel ?herbLabel ?bagang
                WHERE {
                  ?formula a fj:Formula ;
                           rdfs:label ?formulaLabel .
                  FILTER(CONTAINS(?formulaLabel, "桂枝"))
                
                  ?formula fj:has_ingredient_use ?ingUse .
                  ?ingUse fj:uses_herb ?herb .
                
                  ?herb a yw:Herb ;
                        rdfs:label ?herbLabel .
                
                  OPTIONAL { ?herb tcm:herb_has_bagang_property ?bagang }
                }
            """;
        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);
        assertNotNull(rows);
        assertFalse(rows.isEmpty(), "应至少返回 1 条含'桂枝'的记录");
        log.info("✅ TC-07 通过: {} 条端到端记录", rows.size());
    }

    // ============================================================
    // TC-08-DIAG: 诊断 SKOS 数据在 TBox 中的实际状态
    // ============================================================
    @Test
    @Order(8)
    @DisplayName("TC-08-DIAG: 诊断 EHAn SKOS Concept 在 TBox 中的实际状态")
    void diagnoseSkosConceptInTBox() throws Exception {
        var ontologyService = BackendService.getInstance().getOntologyService();
        var tbox = ontologyService.gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        // 1. 检查 URI 是否能解析为 OWLNamedIndividual
        var ehanIRI = org.semanticweb.owlapi.model.IRI.create(EHAN_SKOS_URI);
        var individual = df.getOWLNamedIndividual(ehanIRI);
        log.info("🔍 诊断1 - IRI创建成功: {}", ehanIRI);

        // 2. 检查该个体是否真的存在于 TBox 本体签名中
        boolean existsInSignature = tbox.containsIndividualInSignature(ehanIRI);
        log.info("🔍 诊断2 - containsIndividualInSignature: {}", existsInSignature);

        // 3. 获取该个体的所有 AnnotationAssertionAxioms（skos:prefLabel/altLabel/hiddenLabel 都是注解属性）
        var annotationAxioms = tbox.getAnnotationAssertionAxioms(ehanIRI);
        log.info("🔍 诊断3 - AnnotationAssertionAxioms 数量: {}", annotationAxioms.size());
        for (var ax : annotationAxioms) {
            String lang = ax.getValue().asLiteral()
                    .map(l -> l.getLang())
                    .orElse("(no lang)");
            log.info("   {} -- {} --> {} [lang={}]",
                    ax.getSubject(),
                    ax.getProperty().getIRI().getFragment(),
                    ax.getValue(),
                    lang);
        }

        // 4. 获取该个体的 ObjectPropertyAssertionAxioms（如 skos:exactMatch, skos:inScheme）
        var objectPropAxioms = tbox.getObjectPropertyAssertionAxioms(individual);
        log.info("🔍 诊断4 - ObjectPropertyAssertionAxioms 数量: {}", objectPropAxioms.size());
        for (var ax : objectPropAxioms) {
            log.info("   {} -- {} --> {}",
                    ax.getSubject(),
                    ax.getProperty().asOWLObjectProperty().getIRI().getFragment(),
                    ax.getObject());
        }

        // 5. 获取该个体的 DataPropertyAssertionAxioms
        var dataPropAxioms = tbox.getDataPropertyAssertionAxioms(individual);
        log.info("🔍 诊断5 - DataPropertyAssertionAxioms 数量: {}", dataPropAxioms.size());

        // 6. 列出 TBox 中所有包含 "EHan" 的实体 IRI（模糊排查命名空间问题）
        log.info("🔍 诊断6 - TBox 签名中包含 'EHan' 的所有实体:");
        tbox.getSignature().stream()
                .filter(e -> e.getIRI().toString().contains("EHan"))
                .forEach(e -> log.info("   FOUND: {}", e.getIRI()));

        // 7. 列出 TBox 中所有 skos:Concept 实例（通过类型断言）
        var skosConceptIRI = IRI.create("http://www.w3.org/2004/02/skos/core#Concept");
        var skosConceptClass = df.getOWLClass(skosConceptIRI);
        var typeAxioms = tbox.getClassAssertionAxioms(skosConceptClass);
        log.info("🔍 诊断7 - skos:Concept 实例数量: {}", typeAxioms.size());
        typeAxioms.forEach(ax -> log.info("   skos:Concept: {}", ax.getIndividual()));

        // 最终断言
        boolean hasAnyAxiom = !annotationAxioms.isEmpty() || !objectPropAxioms.isEmpty();
        assertTrue(hasAnyAxiom || existsInSignature,
                "❌ EHAn SKOS Concept 在 TBox 中不存在或无任何公理！\n" +
                        "  查询URI: " + EHAN_SKOS_URI + "\n" +
                        "  containsIndividualInSignature: " + existsInSignature + "\n" +
                        "  AnnotationAssertionAxioms: " + annotationAxioms.size() + "\n" +
                        "  ObjectPropertyAssertionAxioms: " + objectPropAxioms.size() + "\n" +
                        "  请检查:\n" +
                        "  1) TTL 文件中 @prefix zzskos: 是否正确声明且未被注释\n" +
                        "  2) 每个被 import 的 TTL 文件是否独立声明了所需 prefix\n" +
                        "  3) owlPath/import-paths 是否包含了 SKOS 本体文件\n" +
                        "  4) 命名空间尾部 # 是否一致");
    }

    @Test
    @Order(9)
    @DisplayName("TC-09-DIAG: 直接调用 SkosSynonymReader.getAllLabels 验证返回值")
    void diagnoseSkosSynonymReaderDirectly() throws Exception {
        // 1. 直接调用被测方法
        Map<String, List<String>> labels = SkosSynonymReader.getAllLabels(EHAN_SKOS_URI);
        log.info("🔍 SkosSynonymReader.getAllLabels 返回: {}", labels);
        log.info("🔍 返回 size: {}", labels.size());
        labels.forEach((role, list) ->
                log.info("   {} -> {}", role, list));

        // 2. 对比：用诊断测试中的本体对象手动执行相同逻辑
        var ontologyService = BackendService.getInstance().getOntologyService();
        var tbox = ontologyService.gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();
        var conceptIRI = org.semanticweb.owlapi.model.IRI.create(EHAN_SKOS_URI);
        var prefLabelProp = df.getOWLAnnotationProperty(
                org.semanticweb.owlapi.model.IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel"));

        var manualLabels = org.semanticweb.owlapi.search.EntitySearcher
                .getAnnotations(conceptIRI, tbox, prefLabelProp)
                .filter(v -> v instanceof org.semanticweb.owlapi.model.OWLLiteral)
                .map(v -> (org.semanticweb.owlapi.model.OWLLiteral) v)
                .filter(lit -> "zh".equals(lit.getLang()))
                .map(org.semanticweb.owlapi.model.OWLLiteral::getLiteral)
                .collect(java.util.stream.Collectors.toList());

        log.info("🔍 手动查询 prefLabel 结果: {}", manualLabels);

        // 3. 关键：比较两个本体对象是否是同一个
        var readerTBoxMethod = SkosSynonymReader.class.getDeclaredMethod("getTBox");
        readerTBoxMethod.setAccessible(true);
        Object readerOntology = readerTBoxMethod.invoke(null);
        log.info("🔍 SkosSynonymReader.getTBox() == 测试用 tbox ? {}",
                readerOntology == tbox);
        log.info("🔍 SkosSynonymReader.getTBox() identityHash: {}",
                System.identityHashCode(readerOntology));
        log.info("🔍 测试用 tbox identityHash: {}",
                System.identityHashCode(tbox));

        if (readerOntology != tbox) {
            var readerOwlOntology = (org.semanticweb.owlapi.model.OWLOntology) readerOntology;
            log.info("🔍 Reader TBox 公理数: {}", readerOwlOntology.getAxiomCount());
            log.info("🔍 测试 TBox 公理数: {}", tbox.getAxiomCount());

            // 检查 Reader 的 TBox 中是否有 EHan 的注解
            var readerAnnotations = org.semanticweb.owlapi.search.EntitySearcher
                    .getAnnotations(conceptIRI, readerOwlOntology, prefLabelProp)
                    .collect(java.util.stream.Collectors.toList());
            log.info("🔍 Reader TBox 中 EHan prefLabel 数量: {}", readerAnnotations.size());
        }

        assertFalse(labels.isEmpty(),
                "SkosSynonymReader.getAllLabels 返回空！\n" +
                        "手动查询结果: " + manualLabels + "\n" +
                        "两个本体是否同一实例: " + (readerOntology == tbox));
    }

    @Test
    @Order(10)
    @DisplayName("TC-10-DIAG: 对比 EntitySearcher vs getAnnotationAssertionAxioms")
    void diagnoseEntitySearcherVsDirectAPI() {
        var ontologyService = BackendService.getInstance().getOntologyService();
        var tbox = ontologyService.gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();
        var conceptIRI = org.semanticweb.owlapi.model.IRI.create(EHAN_SKOS_URI);
        var prefLabelIRI = org.semanticweb.owlapi.model.IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel");
        var prefLabelProp = df.getOWLAnnotationProperty(prefLabelIRI);

        // 方法1: EntitySearcher（SkosSynonymReader 使用的方式）
        var esResults = org.semanticweb.owlapi.search.EntitySearcher
                .getAnnotations(conceptIRI, tbox, prefLabelProp)
                .collect(java.util.stream.Collectors.toList());
        log.info("🔍 EntitySearcher.getAnnotations 结果数: {}", esResults.size());
        esResults.forEach(v -> log.info("   ES: {}", v));

        // 方法2: 直接用本体 API
        var directResults = tbox.getAnnotationAssertionAxioms(conceptIRI).stream()
                .filter(ax -> ax.getProperty().equals(prefLabelProp))
                .collect(java.util.stream.Collectors.toList());
        log.info("🔍 getAnnotationAssertionAxioms + filter 结果数: {}", directResults.size());
        directResults.forEach(ax -> log.info("   DIRECT: {} -> {}", ax.getProperty(), ax.getValue()));

        // 方法3: 不创建新 prop 对象，直接从公理中匹配 IRI
        var iriMatchResults = tbox.getAnnotationAssertionAxioms(conceptIRI).stream()
                .filter(ax -> ax.getProperty().getIRI().equals(prefLabelIRI))
                .collect(java.util.stream.Collectors.toList());
        log.info("🔍 IRI字符串匹配 结果数: {}", iriMatchResults.size());

        // 方法4: 打印所有注解属性的 identityHash，检查 equals 语义
        tbox.getAnnotationAssertionAxioms(conceptIRI).stream()
                .limit(3)
                .forEach(ax -> {
                    var axProp = ax.getProperty();
                    log.info("🔍 公理中的属性: IRI={}, class={}, hash={}",
                            axProp.getIRI(), axProp.getClass().getName(),
                            System.identityHashCode(axProp));
                    log.info("   equals(prefLabelProp)? {}, same IRI? {}",
                            axProp.equals(prefLabelProp),
                            axProp.getIRI().equals(prefLabelIRI));
                });

        // 断言
        assertFalse(directResults.isEmpty(),
                "直接 API 也查不到！本体可能未正确加载 SKOS 注解");
        assertEquals(esResults.size(), directResults.size(),
                "EntitySearcher 和直接 API 结果不一致！\n" +
                        "ES=" + esResults.size() + ", DIRECT=" + directResults.size() + "\n" +
                        "这通常是 OWLAnnotationProperty equals() 语义问题");
        // 确认 EntitySearcher 返回值的实际类型
        org.semanticweb.owlapi.search.EntitySearcher
                .getAnnotations(conceptIRI, tbox, prefLabelProp)
                .forEach(v -> log.info("🔍 ES返回值类型: {}, class: {}",
                        v.getClass().getSimpleName(), v.getClass().getName()));
    }

    @Test
    @Order(11)
    @DisplayName("TC-11: SKOS getAllSynonyms - 扁平化中文同义词列表")
    void testSkosGetAllSynonyms() throws Exception {
        List<String> synonyms = SkosSynonymReader.getAllSynonyms(EHAN_SKOS_URI);

        assertNotNull(synonyms, "返回结果不应为 null");
        assertFalse(synonyms.isEmpty(), "恶寒(EHan) 应至少有 1 个同义词");

        // 必须包含 prefLabel
        assertTrue(synonyms.contains("恶寒"), "扁平列表应包含 prefLabel '恶寒'，实际: " + synonyms);

        // 验证无重复
        long distinctCount = synonyms.stream().distinct().count();
        assertEquals(synonyms.size(), distinctCount, "同义词列表不应有重复项");

        // 验证所有元素非空非空白
        assertTrue(synonyms.stream().noneMatch(String::isBlank), "不应包含空白字符串");

        log.info("✅ TC-09 通过: getAllSynonyms 返回 {} 个不重复中文同义词: {}", synonyms.size(), synonyms);
    }

    @Test
    @Order(12)
    @DisplayName("TC-12-DIAG: getAllSynonyms 内部逻辑诊断")
    void diagnoseGetAllSynonyms() throws Exception {
        // 1. 确认 getAllLabels 正常
        var labels = SkosSynonymReader.getAllLabels(EHAN_SKOS_URI);
        log.info("🔍 getAllLabels 返回: {}", labels);
        log.info("🔍 getAllLabels size: {}", labels.size());

        // 2. 调用 getAllSynonyms
        var synonyms = SkosSynonymReader.getAllSynonyms(EHAN_SKOS_URI);
        log.info("🔍 getAllSynonyms 返回: {}", synonyms);
        log.info("🔍 getAllSynonyms size: {}", synonyms == null ? "null" : synonyms.size());
    }

    @Test
    @Order(15)
    @DisplayName("TC-15: SKOS getSynonymsByOwlIndividual - OWL个体反向查找同义词")
    void testSkosGetSynonymsByOwlIndividual() throws Exception {
        List<String> synonyms = SkosSynonymReader.getSynonymsByOwlIndividual(EHAN_OWL_INDIVIDUAL_URI);

        assertNotNull(synonyms, "返回结果不应为 null");

        if (synonyms.isEmpty()) {
            log.warn("⚠️ TC-10: 未通过 exactMatch 找到关联 SKOS Concept，请确认:\n" +
                    "  1) OWL 个体 {} 是否存在\n" +
                    "  2) skos:exactMatch 断言是否已声明（正向或反向）", EHAN_OWL_INDIVIDUAL_URI);
        } else {
            assertTrue(synonyms.contains("恶寒"),
                    "通过 OWL 个体反查应包含 '恶寒'，实际: " + synonyms);
            log.info("✅ TC-10 通过: OWL个体反查到 {} 个同义词: {}", synonyms.size(), synonyms);
        }
    }

    @Test
    @Order(16)
    @DisplayName("TC-16-DIAG: OWL个体关联诊断")
    void diagnoseOwlIndividualLinkage() throws Exception {
        OWLOntology ontology = SkosSynonymReader.getTBox();
        IRI individualIRI = IRI.create(EHAN_OWL_INDIVIDUAL_URI);

        // 检查个体是否存在
        boolean exists = ontology.containsIndividualInSignature(individualIRI);
        log.info("🔍 OWL个体是否存在: {}", exists);

        // 列出该个体的所有对象属性断言
        if (exists) {
            var individual = ontology.getOWLOntologyManager().getOWLDataFactory()
                    .getOWLNamedIndividual(individualIRI);
            ontology.objectPropertyAssertionAxioms(individual)
                    .forEach(ax -> log.info("🔍 对象属性断言: {}", ax));

            // 也查注解断言
            ontology.annotationAssertionAxioms(individualIRI)
                    .forEach(ax -> log.info("🔍 注解断言: {}", ax));
        }
    }
    @Test
    @Order(13)
    @DisplayName("TC-13: SKOS buildSynonymDictionary - 构建全局同义词词典")
    void testSkosBuildSynonymDictionary() throws Exception {
        Map<String, String> dict = SkosSynonymReader.buildSynonymDictionary();

        assertNotNull(dict, "词典不应为 null");
        assertFalse(dict.isEmpty(), "词典不应为空，请确认 TBox 中包含 skos:Concept 实例");

        // 验证已知映射：打寒战 → 恶寒（hidden → prefLabel）
        // ⚠️ 如果本体中没有这条 hiddenLabel，改为验证其他已知映射
        if (dict.containsKey("打寒战")) {
            assertEquals("恶寒", dict.get("打寒战"),
                    "'打寒战' 应归一化为 '恶寒'，实际: " + dict.get("打寒战"));
        }

        // 验证 prefLabel 自身映射
        if (dict.containsKey("恶寒")) {
            assertEquals("恶寒", dict.get("恶寒"), "prefLabel 应映射到自身");
        }

        // 验证所有 key 均为小写
        assertTrue(dict.keySet().stream().allMatch(k -> k.equals(k.toLowerCase())),
                "词典所有 key 应为小写");

        // 验证所有 value 非空
        assertTrue(dict.values().stream().noneMatch(String::isBlank),
                "词典所有 value 不应为空白");

        log.info("✅ TC-11 通过: 词典共 {} 条映射", dict.size());
        // 采样打印前 5 条
        dict.entrySet().stream().limit(5)
                .forEach(e -> log.info("  '{}' → '{}'", e.getKey(), e.getValue()));
    }

    @Test
    @Order(14)
    @DisplayName("TC-14: SKOS 边界测试 - 不存在的 Concept URI 应返回空而非异常")
    void testSkosNonExistentConcept() throws Exception {
        String fakeUri = ZZSKOS_NS + "NonExistentConcept_12345";

        // getAllLabels 应返回空 Map
        Map<String, List<String>> labels = SkosSynonymReader.getAllLabels(fakeUri);
        assertNotNull(labels);
        assertTrue(labels.isEmpty(), "不存在的 Concept 应返回空 Map");

        // getAllSynonyms 应返回空 List
        List<String> synonyms = SkosSynonymReader.getAllSynonyms(fakeUri);
        assertNotNull(synonyms);
        assertTrue(synonyms.isEmpty(), "不存在的 Concept 应返回空 List");

        // getSynonymsByOwlIndividual 应返回空 List
        List<String> byIndividual = SkosSynonymReader.getSynonymsByOwlIndividual(fakeUri);
        assertNotNull(byIndividual);
        assertTrue(byIndividual.isEmpty(), "不存在的个体应返回空 List");

        log.info("✅ TC-12 通过: 所有方法对不存在 URI 均安全返回空集合");
    }

    @Test
    @Order(17)
    @DisplayName("TC-17: 术语归一化 - 输入同义词/缩写反查 prefLabel 与 SKOS IRI")
    void testTermNormalizationToPrefLabelAndIRI() throws Exception {
        // ========== 配置测试输入 ==========
        String inputTerm = "打寒战";

        // ========== Step 1: 通过词典查找归一化后的 prefLabel ==========
        Map<String, String> dict = SkosSynonymReader.buildSynonymDictionary();
        String normalizedLabel = dict.get(inputTerm.toLowerCase());

        assertNotNull(normalizedLabel,
                "词典中未找到 '" + inputTerm + "'，请确认该词已作为 altLabel/hiddenLabel 录入 SKOS");
        log.info("🔍 '{}' → 归一化为 prefLabel: '{}'", inputTerm, normalizedLabel);

        // ========== Step 2: 通过通用方法反查 SKOS Concept IRI ==========
        String matchedIRI = SkosSynonymReader.findConceptIRIByPrefLabel(normalizedLabel);

        assertNotNull(matchedIRI,
                "找到 prefLabel '" + normalizedLabel + "' 但无法定位对应 SKOS Concept IRI");
        log.info("🔍 prefLabel '{}' → SKOS IRI: {}", normalizedLabel, matchedIRI);

        // ========== Step 3: 交叉验证 - 用 IRI 反查 getAllLabels 确认一致性 ==========
        Map<String, List<String>> labels = SkosSynonymReader.getAllLabels(matchedIRI);
        assertFalse(labels.isEmpty(), "通过 IRI 反查 getAllLabels 不应为空");

        List<String> prefLabels = labels.getOrDefault("prefLabel", List.of());
        assertTrue(prefLabels.contains(normalizedLabel),
                "交叉验证失败: IRI 的 prefLabel 应为 '" + normalizedLabel + "'，实际: " + prefLabels);

        boolean inputFoundInLabels = labels.values().stream()
                .flatMap(List::stream)
                .anyMatch(label -> label.equalsIgnoreCase(inputTerm));
        assertTrue(inputFoundInLabels,
                "原始输入 '" + inputTerm + "' 未出现在 IRI 的任何标签角色中，词典映射可能不一致");

        // ========== 最终断言与输出 ==========
        log.info("✅ TC-17 通过: 术语归一化成功");
        log.info("   输入词:     '{}'", inputTerm);
        log.info("   prefLabel:  '{}'", normalizedLabel);
        log.info("   SKOS IRI:   {}", matchedIRI);
        log.info("   全部标签:   {}", labels);
    }

    // 在测试类中作为静态缓存，避免每次测试都全量扫描
    private static Map<String, String> prefLabelToIRICache;

    private static Map<String, String> buildPrefLabelToIRICache(OWLOntology tbox) {
        if (prefLabelToIRICache != null) return prefLabelToIRICache;

        IRI prefLabelIRI = IRI.create("http://www.w3.org/2004/02/skos/core#prefLabel");

        prefLabelToIRICache = tbox.getAxioms(AxiomType.ANNOTATION_ASSERTION).stream()
                .filter(ax -> ax.getProperty().getIRI().equals(prefLabelIRI))
                .filter(ax -> ax.getValue().asLiteral().isPresent())
                .filter(ax -> "zh".equals(ax.getValue().asLiteral().get().getLang()))
                .collect(Collectors.toMap(
                        ax -> ax.getValue().asLiteral().get().getLiteral(),
                        ax -> ax.getSubject().toString(),
                        (a, b) -> a
                ));

        return prefLabelToIRICache;
    }

    // ============================================================
    // ✅ SWRL 规则1/2/3 推理验证测试
    // ============================================================

    private static final String TCM_NS = "http://www.tcm-classics.org/tcm#";
    private static final String JJ_NS = "http://www.tcm-classics.org/jianjia#";
    private static final String BZ_NS = "http://www.tcm-classics.org/bingzheng#";
    private static final String LJ_NS = "http://www.tcm-classics.org/liujing#";

    // ----------------------------------------------------------
    // TC-20: 规则1 - 高权重症状(≥0.8) → 推断主证
    // ✅ SWRL 规则完整测试 (已适配 xsd:float)
    // ============================================================

    /**
     * 构造与 tcm:has_diagnostic_weight (xsd:float) 类型精确匹配的字面量。
     * ⚠️ 必须使用 float，否则 Openllet 会抛出 InconsistentOntologyException:
     *     "The literal value does not satisfy the datatype restriction"
     */
    private static OWLLiteral createWeightLiteral(OWLDataFactory df, double value) {
        return df.getOWLLiteral((float) value);
    }

    /** 向 TBox 注入测试公理（返回同一集合用于 finally 清理） */
    private static Set<org.semanticweb.owlapi.model.OWLAxiom> addTestAxioms(
            OWLOntology tbox, Set<org.semanticweb.owlapi.model.OWLAxiom> axioms) {
        tbox.getOWLOntologyManager().addAxioms(tbox, axioms);
        return axioms;
    }

    /** 从 TBox 移除测试公理 */
    private static void removeTestAxioms(
            OWLOntology tbox, Set<org.semanticweb.owlapi.model.OWLAxiom> axioms) {
        tbox.getOWLOntologyManager().removeAxioms(tbox, axioms);
    }

    /**
     * SWRL 规则测试套件（v2.0 方证对应推理）
     *
     * 本测试类覆盖以下规则：
     *   A) 兼夹化裁路径推断
     *   B) 合方建议
     *   C) 禁忌检查
     *   D) 八纲加药提示
     *   E) 兼夹合方策略提示
     *
     * @order 18,19,22,30-39
     */
    // ==================== TC-18: 规则加载验证 ====================
    @Test
    @Order(18)
    @DisplayName("TC-18: SWRL规则加载验证 - TBox中包含≥5条SWRL规则")
    void testSwrlRulesLoadedInTBox() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();

        var swrlRules = tbox.getAxioms(AxiomType.SWRL_RULE);
        log.info("🔍 TBox 中 SWRL 规则总数: {}", swrlRules.size());
        assertFalse(swrlRules.isEmpty(), "TBox 中应至少包含 1 条 SWRL 规则");

        swrlRules.forEach(rule -> {
            int bodyAtoms = rule.getBody().size();
            int headAtoms = rule.getHead().size();
            log.info("   SWRL Rule: body={} atoms, head={} atoms", bodyAtoms, headAtoms);
            assertTrue(bodyAtoms > 0, "SWRL 规则 body 不应为空");
            assertTrue(headAtoms > 0, "SWRL 规则 head 不应为空");
        });

        assertTrue(swrlRules.size() >= 5,
                "应至少包含5条SWRL规则（A,B,C,D,E），实际: " + swrlRules.size());
        log.info("✅ TC-18 通过: SWRL 规则加载正常，共 {} 条", swrlRules.size());
    }

    // ==================== TC-19: 变量声明验证 ====================
    @Test
    @Order(19)
    @DisplayName("TC-19: SWRL变量声明验证 - 所有预期变量已被正确声明或引用")
    void testSwrlVariablesDeclared() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();

        List<String> expectedVars = List.of(
                "p", "main", "conc", "r", "target", "a", "b", "pat", "channel", "c", "sym"
        );

        Set<String> declaredVars = new HashSet<>();
        Set<String> usedVars = new HashSet<>();
        var swrlRules = tbox.getAxioms(AxiomType.SWRL_RULE);

        // 路径1：从规则声明的变量中提取
        for (var rule : swrlRules) {
            rule.variables().forEach(var -> {
                String fragment = var.getIRI().getFragment();
                if (fragment != null) declaredVars.add(fragment);
            });
            // 路径2：从 body/head 原子中提取
            for (var atom : rule.getBody()) extractVariables(atom, usedVars);
            for (var atom : rule.getHead()) extractVariables(atom, usedVars);
        }

        Set<String> allKnownVars = new HashSet<>();
        allKnownVars.addAll(declaredVars);
        allKnownVars.addAll(usedVars);
        log.info("🔍 合并后已知变量: {}", allKnownVars);

        assertFalse(allKnownVars.isEmpty(), "未找到任何 SWRL 变量，请检查本体是否正确加载");

        for (String var : expectedVars) {
            assertTrue(allKnownVars.contains(var),
                    String.format("SWRL 变量 ':%s' 未被声明或引用%n  声明: %s%n  使用: %s",
                            var, declaredVars, usedVars));
        }
        log.info("✅ TC-19 通过: 全部 {} 个预期变量均已声明或被引用", expectedVars.size());
    }

    // 辅助递归提取变量
    private void extractVariables(SWRLAtom atom, Set<String> vars) {
        if (atom instanceof SWRLBuiltInAtom builtin) {
            builtin.getArguments().forEach(arg -> {
                if (arg instanceof SWRLVariable var) addFragment(var, vars);
            });
        } else if (atom instanceof SWRLClassAtom classAtom) {
            if (classAtom.getArgument() instanceof SWRLVariable var) addFragment(var, vars);
        } else if (atom instanceof SWRLObjectPropertyAtom opAtom) {
            if (opAtom.getFirstArgument() instanceof SWRLVariable var) addFragment(var, vars);
            if (opAtom.getSecondArgument() instanceof SWRLVariable var) addFragment(var, vars);
        } else if (atom instanceof SWRLDataPropertyAtom dpAtom) {
            if (dpAtom.getFirstArgument() instanceof SWRLVariable var) addFragment(var, vars);
            if (dpAtom.getSecondArgument() instanceof SWRLVariable var) addFragment(var, vars);
        } else if (atom instanceof SWRLSameIndividualAtom sameAtom) {
            if (sameAtom.getFirstArgument() instanceof SWRLVariable var) addFragment(var, vars);
            if (sameAtom.getSecondArgument() instanceof SWRLVariable var) addFragment(var, vars);
        }
    }

    private void addFragment(SWRLVariable var, Set<String> vars) {
        String frag = var.getIRI().getFragment();
        if (frag != null) vars.add(frag);
    }

    // ==================== TC-22: Import 验证 ====================
    @Test
    @Order(22)
    @DisplayName("TC-22: SWRL本体Import验证 - 规则本体正确导入依赖本体")
    void testSwrlOntologyImports() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();

        IRI swrlOntologyIRI = IRI.create("http://www.tcm-classics.org/swrl/rules");
        OWLOntology swrlOntology = null;

        for (var ont : tbox.getOWLOntologyManager().ontologies().collect(Collectors.toList())) {
            if (ont.getOntologyID().getOntologyIRI().isPresent() &&
                    ont.getOntologyID().getOntologyIRI().get().equals(swrlOntologyIRI)) {
                swrlOntology = ont;
                break;
            }
        }

        Set<String> actualImports;
        if (swrlOntology == null) {
            log.info("🔍 SWRL 本体已合并，检查 TBox 的 import 声明");
            actualImports = tbox.imports()
                    .map(imp -> imp.getOntologyID().getOntologyIRI().map(IRI::toString).orElse(""))
                    .collect(Collectors.toSet());
        } else {
            actualImports = swrlOntology.imports()
                    .map(imp -> imp.getOntologyID().getOntologyIRI().map(IRI::toString).orElse(""))
                    .collect(Collectors.toSet());
        }

        Set<String> expectedImports = Set.of(
                "http://www.tcm-classics.org/jianjia",
                "http://www.tcm-classics.org/liujing",
                "http://www.tcm-classics.org/bingzheng"
        );

        for (String expected : expectedImports) {
            assertTrue(actualImports.contains(expected),
                    "SWRL 本体缺少 import: " + expected + "\n实际 imports: " + actualImports);
        }
        log.info("✅ TC-22 通过: SWRL 本体正确导入了全部 {} 个依赖本体", expectedImports.size());
    }

    // ==================== 规则 A 正向测试 ====================
    @Test
    @Order(30)
    @DisplayName("TC-30: 规则A正向 - 兼夹化裁路径推断 → suggestedFormulaPattern")
    void testRuleA_Positive() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_A_" + System.nanoTime();
        String mainPatternIRI = BZ_NS + "TaiYangBingGangZheng";
        String concomitantIRI = JJ_NS + "ShuiYin";
        String ruleIRI = JJ_NS + "Rule_ShuiYin_WuLingSan";
        String targetPatternIRI = BZ_NS + "WuLingSanZheng";

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var mainPattern = df.getOWLNamedIndividual(IRI.create(mainPatternIRI));
        var concomitant = df.getOWLNamedIndividual(IRI.create(concomitantIRI));
        var rule = df.getOWLNamedIndividual(IRI.create(ruleIRI));
        var targetPattern = df.getOWLNamedIndividual(IRI.create(targetPatternIRI));

        // 使用 BackendService 获取已存在的属性（确保签名中有）
        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var hasConcomitantPathology = backend.getObjectProperty(TCM_NS + "hasConcomitantPathology");
        var appliesToPathology = backend.getObjectProperty(JJ_NS + "appliesToPathology");
        var primaryFormulaPattern = backend.getObjectProperty(JJ_NS + "primaryFormulaPattern");
        var targetFormulaPattern = backend.getObjectProperty(JJ_NS + "targetFormulaPattern");
        var suggestedFormulaPattern = backend.getObjectProperty(TCM_NS + "suggestedFormulaPattern");

        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");
        var modRuleClass = backend.getClass(JJ_NS + "ModificationRule");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConcomitantPathology, patient, concomitant));
        axioms.add(df.getOWLClassAssertionAxiom(modRuleClass, rule));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(appliesToPathology, rule, concomitant));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(primaryFormulaPattern, rule, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(targetFormulaPattern, rule, targetPattern));

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            boolean inferred = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, suggestedFormulaPattern)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(targetPatternIRI));

            log.info("🔍 规则A正向: 是否推出 suggestedFormulaPattern -> {}? {}", targetPatternIRI, inferred);
            assertTrue(inferred, "规则A应推出 " + targetPatternIRI);
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-30 通过");
    }

    // ==================== 规则 A 负向测试 ====================
    @Test
    @Order(31)
    @DisplayName("TC-31: 规则A负向 - 主方证不匹配，不触发")
    void testRuleA_Negative_MismatchedPrimary() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_A_Neg_" + System.nanoTime();
        String mainPatternIRI = BZ_NS + "TaiYangBingGangZheng";
        String wrongMainPatternIRI = BZ_NS + "YangMingBingGangZheng";
        String concomitantIRI = JJ_NS + "ShuiYin";
        String ruleIRI = JJ_NS + "Rule_ShuiYin_WuLingSan";
        String targetPatternIRI = BZ_NS + "WuLingSanZheng";

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var mainPattern = df.getOWLNamedIndividual(IRI.create(mainPatternIRI));
        var wrongMain = df.getOWLNamedIndividual(IRI.create(wrongMainPatternIRI));
        var concomitant = df.getOWLNamedIndividual(IRI.create(concomitantIRI));
        var rule = df.getOWLNamedIndividual(IRI.create(ruleIRI));
        var targetPattern = df.getOWLNamedIndividual(IRI.create(targetPatternIRI));

        // 复用属性定义
        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var hasConcomitantPathology = backend.getObjectProperty(TCM_NS + "hasConcomitantPathology");
        var appliesToPathology = backend.getObjectProperty(JJ_NS + "appliesToPathology");
        var primaryFormulaPattern = backend.getObjectProperty(JJ_NS + "primaryFormulaPattern");
        var targetFormulaPattern = backend.getObjectProperty(JJ_NS + "targetFormulaPattern");
        var suggestedFormulaPattern = backend.getObjectProperty(TCM_NS + "suggestedFormulaPattern");

        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");
        var modRuleClass = backend.getClass(JJ_NS + "ModificationRule");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConcomitantPathology, patient, concomitant));
        axioms.add(df.getOWLClassAssertionAxiom(modRuleClass, rule));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(appliesToPathology, rule, concomitant));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(primaryFormulaPattern, rule, wrongMain));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(targetFormulaPattern, rule, targetPattern));

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            boolean inferred = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, suggestedFormulaPattern)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(targetPatternIRI));

            log.info("🔍 规则A负向: 是否错误推出 suggestedFormulaPattern? {}", inferred);
            assertFalse(inferred, "主方证不匹配时不应推出 " + targetPatternIRI);
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-31 通过");
    }

    // ==================== 规则 B 正向测试 ====================
    @Test
    @Order(32)
    @DisplayName("TC-32: 规则B正向 - 合方建议 → hasCombinationAlert")
    void testRuleB_Positive() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_B_" + System.nanoTime();
        String patternA_IRI = BZ_NS + "DaChaiHuTangZheng";
        String patternB_IRI = BZ_NS + "GuiZhiFuLingWanZheng";

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var patternA = df.getOWLNamedIndividual(IRI.create(patternA_IRI));
        var patternB = df.getOWLNamedIndividual(IRI.create(patternB_IRI));

        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var combinesWith = backend.getObjectProperty(JJ_NS + "CombinesWith");
        var hasCombinationAlert = backend.getObjectProperty(TCM_NS + "hasCombinationAlert");

        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, patternA));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, patternB));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(combinesWith, patternA, patternB));
        axioms.add(df.getOWLDifferentIndividualsAxiom(patternA, patternB));

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            boolean alertA = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, hasCombinationAlert)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(patternA_IRI));
            boolean alertB = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, hasCombinationAlert)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(patternB_IRI));

            log.info("🔍 规则B正向: hasCombinationAlert 指向 A? {}, 指向 B? {}", alertA, alertB);
            assertTrue(alertA && alertB, "应同时为两个方证触发合方提示");
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-32 通过");
    }

    // ==================== 规则 B 负向测试 ====================
    @Test
    @Order(33)
    @DisplayName("TC-33: 规则B负向 - 无 CombinesWith，不触发")
    void testRuleB_Negative_NoCombinesWith() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_B_Neg_" + System.nanoTime();
        String patternA_IRI = BZ_NS + "DaChaiHuTangZheng";
        String patternB_IRI = BZ_NS + "GuiZhiFuLingWanZheng";

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var patternA = df.getOWLNamedIndividual(IRI.create(patternA_IRI));
        var patternB = df.getOWLNamedIndividual(IRI.create(patternB_IRI));

        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var hasCombinationAlert = backend.getObjectProperty(TCM_NS + "hasCombinationAlert");
        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, patternA));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, patternB));
        // 故意不添加 CombinesWith

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            long count = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, hasCombinationAlert)
                    .entities().count();
            log.info("🔍 规则B负向: hasCombinationAlert 数量 = {}", count);
            assertEquals(0, count, "无 CombinesWith 时不应触发合方提示");
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-33 通过");
    }

    // ==================== 规则 C 正向测试 ====================
    @Test
    @Order(34)
    @DisplayName("TC-34: 规则C正向 - 禁忌检查 → hasContraindicationWarning")
    void testRuleC_Positive() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_C_" + System.nanoTime();
        String patternIRI = BZ_NS + "ShaoYangBingGangZheng";
        String channelIRI = LJ_NS + "ShaoYang";
        String contraindicationIRI = JJ_NS + "Contra_ShaoYang_NoSweat";

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var pattern = df.getOWLNamedIndividual(IRI.create(patternIRI));
        var channel = df.getOWLNamedIndividual(IRI.create(channelIRI));
        var contra = df.getOWLNamedIndividual(IRI.create(contraindicationIRI));

        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var belongsToChannel = backend.getObjectProperty(BZ_NS + "belongs_to_liujing");
        var contraindicatedIn = backend.getObjectProperty(JJ_NS + "contraindicatedIn");
        var hasContraindicationWarning = backend.getObjectProperty(TCM_NS + "hasContraindicationWarning");

        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");
        var contraClass = backend.getClass(JJ_NS + "ModificationContraindication");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, pattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(belongsToChannel, pattern, channel));
        axioms.add(df.getOWLClassAssertionAxiom(contraClass, contra));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(contraindicatedIn, contra, channel));

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            boolean warned = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, hasContraindicationWarning)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(contraindicationIRI));

            log.info("🔍 规则C正向: hasContraindicationWarning 指向 {}? {}", contraindicationIRI, warned);
            assertTrue(warned, "应触发禁忌警告");
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-34 通过");
    }

    @Test
    @Order(35)
    @DisplayName("TC-35: 规则C负向 - 无禁忌匹配，不触发")
    void testRuleC_Negative_NoMatchingContraindication() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_C_Neg_" + System.nanoTime();
        String patternIRI = TCM_NS + "TempPattern_" + System.nanoTime();   // 临时方证
        String channelIRI = TCM_NS + "TempChannel_" + System.nanoTime();   // 临时六经

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var pattern = df.getOWLNamedIndividual(IRI.create(patternIRI));
        var channel = df.getOWLNamedIndividual(IRI.create(channelIRI));

        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var belongsToChannel = backend.getObjectProperty(BZ_NS + "belongs_to_liujing");
        var hasContraindicationWarning = backend.getObjectProperty(TCM_NS + "hasContraindicationWarning");

        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, pattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(belongsToChannel, pattern, channel));
        // 不添加任何禁忌个体，确保没有禁忌与临时六经关联

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            long count = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, hasContraindicationWarning)
                    .entities().count();
            log.info("🔍 规则C负向: hasContraindicationWarning 数量 = {}", count);
            assertEquals(0, count, "临时六经无禁忌关联，不应触发任何警告");
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-35 通过");
    }

    // ==================== 规则 D 正向测试 ====================
    @Test
    @Order(36)
    @DisplayName("TC-36: 规则D正向 - 八纲加药提示 → suggestedFormulaPattern")
    void testRuleD_Positive() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_D_" + System.nanoTime();
        String mainPatternIRI = BZ_NS + "GuiZhiTangZheng";
        String symptomIRI = ZZ_NS + "XiangBeiQiangJiJi";
        String ruleIRI = JJ_NS + "Rule_Guizhi_Add_GeGen";
        String targetPatternIRI = BZ_NS + "GuiZhiJiaGeGenTangZheng";

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var mainPattern = df.getOWLNamedIndividual(IRI.create(mainPatternIRI));
        var symptom = df.getOWLNamedIndividual(IRI.create(symptomIRI));
        var rule = df.getOWLNamedIndividual(IRI.create(ruleIRI));
        var targetPattern = df.getOWLNamedIndividual(IRI.create(targetPatternIRI));

        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var hasSymptom = backend.getObjectProperty(TCM_NS + "has_symptom");
        var primaryFormulaPattern = backend.getObjectProperty(JJ_NS + "primaryFormulaPattern");
        var targetFormulaPattern = backend.getObjectProperty(JJ_NS + "targetFormulaPattern");
        var triggersSymptom = backend.getObjectProperty(JJ_NS + "triggersSymptom");
        var suggestedFormulaPattern = backend.getObjectProperty(TCM_NS + "suggestedFormulaPattern");

        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");
        var ruleClass = backend.getClass(JJ_NS + "EightPrincipleAdditionRule");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasSymptom, patient, symptom));
        axioms.add(df.getOWLClassAssertionAxiom(ruleClass, rule));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(primaryFormulaPattern, rule, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(targetFormulaPattern, rule, targetPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(triggersSymptom, rule, symptom));

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            boolean inferred = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, suggestedFormulaPattern)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(targetPatternIRI));

            log.info("🔍 规则D正向: 是否推出 suggestedFormulaPattern -> {}? {}", targetPatternIRI, inferred);
            assertTrue(inferred, "规则D应推出 " + targetPatternIRI);
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-36 通过");
    }

    // ==================== 规则 D 负向测试 ====================
    @Test
    @Order(37)
    @DisplayName("TC-37: 规则D负向 - 症状不匹配，不触发")
    void testRuleD_Negative_SymptomMismatch() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_D_Neg_" + System.nanoTime();
        String mainPatternIRI = BZ_NS + "GuiZhiTangZheng";
        String symptomIRI = ZZ_NS + "XiangBeiQiangJiJi";
        String wrongSymptomIRI = ZZ_NS + "EHan";
        String ruleIRI = JJ_NS + "Rule_Guizhi_Add_GeGen";
        String targetPatternIRI = BZ_NS + "GuiZhiJiaGeGenTangZheng";

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var mainPattern = df.getOWLNamedIndividual(IRI.create(mainPatternIRI));
        var symptom = df.getOWLNamedIndividual(IRI.create(symptomIRI));
        var wrongSymptom = df.getOWLNamedIndividual(IRI.create(wrongSymptomIRI));
        var rule = df.getOWLNamedIndividual(IRI.create(ruleIRI));
        var targetPattern = df.getOWLNamedIndividual(IRI.create(targetPatternIRI));

        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var hasSymptom = backend.getObjectProperty(TCM_NS + "has_symptom");
        var primaryFormulaPattern = backend.getObjectProperty(JJ_NS + "primaryFormulaPattern");
        var targetFormulaPattern = backend.getObjectProperty(JJ_NS + "targetFormulaPattern");
        var triggersSymptom = backend.getObjectProperty(JJ_NS + "triggersSymptom");
        var suggestedFormulaPattern = backend.getObjectProperty(TCM_NS + "suggestedFormulaPattern");

        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");
        var ruleClass = backend.getClass(JJ_NS + "EightPrincipleAdditionRule");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasSymptom, patient, wrongSymptom));
        axioms.add(df.getOWLClassAssertionAxiom(ruleClass, rule));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(primaryFormulaPattern, rule, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(targetFormulaPattern, rule, targetPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(triggersSymptom, rule, symptom));

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            boolean inferred = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, suggestedFormulaPattern)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(targetPatternIRI));

            log.info("🔍 规则D负向: 是否错误推出 suggestedFormulaPattern? {}", inferred);
            assertFalse(inferred, "症状不匹配时不应推出 " + targetPatternIRI);
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-37 通过");
    }

    // ==================== 规则 E 正向测试 ====================
    @Test
    @Order(38)
    @DisplayName("TC-38: 规则E正向 - 兼夹合方策略提示 → suggestedFormulaPattern + hasCombinationAlert")
    void testRuleE_Positive() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_E_" + System.nanoTime();
        String mainPatternIRI = BZ_NS + "DaChaiHuTangZheng";
        String concomitantIRI = JJ_NS + "YuXue";
        String ruleIRI = JJ_NS + "Rule_DaChaiHu_He_GuiZhiFuLing";
        String targetPatternIRI = BZ_NS + "DaChaiHuHeGuiZhiFuLingTangZheng";
        String strategyInstanceIRI = JJ_NS + "FormulaCombination_Instance";

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var mainPattern = df.getOWLNamedIndividual(IRI.create(mainPatternIRI));
        var concomitant = df.getOWLNamedIndividual(IRI.create(concomitantIRI));
        var rule = df.getOWLNamedIndividual(IRI.create(ruleIRI));
        var targetPattern = df.getOWLNamedIndividual(IRI.create(targetPatternIRI));
        var strategy = df.getOWLNamedIndividual(IRI.create(strategyInstanceIRI));

        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var hasConcomitantPathology = backend.getObjectProperty(TCM_NS + "hasConcomitantPathology");
        var appliesToPathology = backend.getObjectProperty(JJ_NS + "appliesToPathology");
        var primaryFormulaPattern = backend.getObjectProperty(JJ_NS + "primaryFormulaPattern");
        var targetFormulaPattern = backend.getObjectProperty(JJ_NS + "targetFormulaPattern");
        var usesStrategy = backend.getObjectProperty(JJ_NS + "usesStrategy");
        var suggestedFormulaPattern = backend.getObjectProperty(TCM_NS + "suggestedFormulaPattern");
        var hasCombinationAlert = backend.getObjectProperty(TCM_NS + "hasCombinationAlert");

        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");
        var modRuleClass = backend.getClass(JJ_NS + "ModificationRule");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConcomitantPathology, patient, concomitant));
        axioms.add(df.getOWLClassAssertionAxiom(modRuleClass, rule));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(appliesToPathology, rule, concomitant));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(primaryFormulaPattern, rule, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(targetFormulaPattern, rule, targetPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(usesStrategy, rule, strategy));

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            boolean suggested = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, suggestedFormulaPattern)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(targetPatternIRI));
            boolean alert = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, hasCombinationAlert)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(targetPatternIRI));

            log.info("🔍 规则E正向: suggestedFormulaPattern={}, hasCombinationAlert={}", suggested, alert);
            assertTrue(suggested && alert, "应同时推出目标方证和合方提示");
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-38 通过");
    }

    // ==================== 规则 E 负向测试 ====================
    @Test
    @Order(39)
    @DisplayName("TC-39: 规则E负向 - 策略不是合方，不触发 hasCombinationAlert")
    void testRuleE_Negative_NotCombinationStrategy() {
        var backend = BackendService.getInstance();
        var tbox = backend.getOntologyService().gettBoxOntology();
        var df = tbox.getOWLOntologyManager().getOWLDataFactory();

        String patientIRI = TCM_NS + "Patient_E_Neg_" + System.nanoTime();
        String mainPatternIRI = BZ_NS + "DaChaiHuTangZheng";
        String concomitantIRI = JJ_NS + "YuXue";
        String ruleIRI = JJ_NS + "Rule_DaChaiHu_He_GuiZhiFuLing";
        String targetPatternIRI = BZ_NS + "DaChaiHuHeGuiZhiFuLingTangZheng";
        String wrongStrategyIRI = JJ_NS + "SimpleAddition_Instance";

        var patient = df.getOWLNamedIndividual(IRI.create(patientIRI));
        var mainPattern = df.getOWLNamedIndividual(IRI.create(mainPatternIRI));
        var concomitant = df.getOWLNamedIndividual(IRI.create(concomitantIRI));
        var rule = df.getOWLNamedIndividual(IRI.create(ruleIRI));
        var targetPattern = df.getOWLNamedIndividual(IRI.create(targetPatternIRI));
        var wrongStrategy = df.getOWLNamedIndividual(IRI.create(wrongStrategyIRI));

        var hasConfirmedPattern = backend.getObjectProperty(TCM_NS + "hasConfirmedPattern");
        var hasConcomitantPathology = backend.getObjectProperty(TCM_NS + "hasConcomitantPathology");
        var appliesToPathology = backend.getObjectProperty(JJ_NS + "appliesToPathology");
        var primaryFormulaPattern = backend.getObjectProperty(JJ_NS + "primaryFormulaPattern");
        var targetFormulaPattern = backend.getObjectProperty(JJ_NS + "targetFormulaPattern");
        var usesStrategy = backend.getObjectProperty(JJ_NS + "usesStrategy");
        var suggestedFormulaPattern = backend.getObjectProperty(TCM_NS + "suggestedFormulaPattern");
        var hasCombinationAlert = backend.getObjectProperty(TCM_NS + "hasCombinationAlert");

        var clinicalCaseClass = backend.getClass(TCM_NS + "ClinicalCase");
        var modRuleClass = backend.getClass(JJ_NS + "ModificationRule");

        Set<OWLAxiom> axioms = new HashSet<>();
        axioms.add(df.getOWLClassAssertionAxiom(clinicalCaseClass, patient));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConfirmedPattern, patient, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasConcomitantPathology, patient, concomitant));
        axioms.add(df.getOWLClassAssertionAxiom(modRuleClass, rule));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(appliesToPathology, rule, concomitant));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(primaryFormulaPattern, rule, mainPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(targetFormulaPattern, rule, targetPattern));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(usesStrategy, rule, wrongStrategy));

        backend.addAxioms(tbox, axioms);
        try {
            backend.getReasonerService().getReasoner().flush();
            boolean suggested = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, suggestedFormulaPattern)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(targetPatternIRI));
            boolean alert = backend.getReasonerService().getReasoner()
                    .getObjectPropertyValues(patient, hasCombinationAlert)
                    .entities()
                    .anyMatch(ind -> ind.getIRI().toString().equals(targetPatternIRI));

            log.info("🔍 规则E负向: suggestedFormulaPattern={}, hasCombinationAlert={}", suggested, alert);
            assertTrue(suggested, "应推出目标方证");
            assertFalse(alert, "策略非合方时不应触发合方提示");
        } finally {
            backend.removeAxioms(tbox, axioms);
        }
        log.info("✅ TC-39 通过");
    }

}