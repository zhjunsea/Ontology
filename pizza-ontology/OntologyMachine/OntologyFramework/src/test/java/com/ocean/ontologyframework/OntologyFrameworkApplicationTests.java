package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TBox/ABox 分离架构集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OntologyFrameworkApplicationTests {

    private static final Logger log = LoggerFactory.getLogger(OntologyFrameworkApplicationTests.class);

    private static final String ONTOP_ABOX_ENDPOINT = "http://localhost:8080/sparql";
    private static final String TBOX_FILE = "D:/work/Ontology/pizza-ontology/ontology/pizza-all-classes.owl";

    private static BackendService backendService;

    private static int expectedAxiomNum = 0;
    /** 保存测试开始前的 TBox 公理快照 */
    private Set<OWLAxiom> tboxBaselineSnapshot;
    private OWLReasoner baselineReasoner;

    @BeforeAll
    static void setUp() throws Exception {
        log.info("=== 初始化 TBox/ABox 分离架构测试环境 ===");
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        backendService = BackendService.getInstance(TBOX_FILE, obdaHandler);
        expectedAxiomNum = backendService.getOntologyService().gettBoxOntology().getAxiomCount();
        assertNotNull(backendService, "BackendService 初始化失败");
    }

    private Set<OWLAxiom> fullBaselineSnapshot; // ⭐ 改为全量快照

    @BeforeEach
    void snapshotFullBaseline() {
        OWLOntology ontology = backendService.getOntologyService().gettBoxOntology();
        // ⭐ 快照所有公理（TBox + ABox），而非仅 TBox
        fullBaselineSnapshot = Collections.unmodifiableSet(
                new HashSet<>(ontology.getAxioms())
        );

        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        reasoner.flush();
        log.info("📸 全量快照已保存，基线公理数: {}", fullBaselineSnapshot.size());
    }

    @AfterEach
    void restoreOntologyAndReasoner() {
        if (fullBaselineSnapshot == null) return;

        try {
            OWLOntology ontology = backendService.getOntologyService().gettBoxOntology();
            Set<OWLAxiom> currentAxioms = ontology.getAxioms();

            // 快速路径：无变更则跳过
            if (currentAxioms.size() == fullBaselineSnapshot.size()
                    && currentAxioms.containsAll(fullBaselineSnapshot)) {
                log.info("✅ 本体无变更，无需恢复");
                return;
            }

            log.warn("⚠️ 检测到本体漂移，正在原子恢复...");
            OWLOntologyManager manager = ontology.getOWLOntologyManager();

            // ⭐ OWL API 5.x: removeAxioms/addAxioms 返回 ChangeApplied 枚举，不再是 List
            ChangeApplied removeResult = manager.removeAxioms(ontology, currentAxioms);
            if (removeResult != ChangeApplied.SUCCESSFULLY) {
                throw new IllegalStateException("批量移除公理失败: " + removeResult);
            }

            ChangeApplied addResult = manager.addAxioms(ontology, fullBaselineSnapshot);
            if (addResult != ChangeApplied.SUCCESSFULLY) {
                throw new IllegalStateException("批量恢复基线公理失败: " + addResult);
            }

            // ⭐ flush 单例推理器同步新状态
            OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
            reasoner.flush();

            log.info("🧹 本体及推理器已恢复至基线状态，当前公理数: {}",
                    ontology.getAxiomCount());

        } catch (Exception e) {
            log.error("❌ 本体恢复失败！单例推理器可能已处于不一致状态", e);
            throw new RuntimeException("测试隔离失败，请检查 Openllet 状态", e);
        } finally {
            fullBaselineSnapshot = null;
        }
    }

    // ============================================================
    // 场景1: 基于 TBox 子类推理的 ABox 实例检索
    // ============================================================
    @Test
    @Order(1)
    @DisplayName("场景1: 基于 TBox 子类推理的 ABox 实例检索")
    void testQueryWithInferredSubclasses() {
        String ns = "http://example.org/pizza/components/classes/";

        Set<String> crustClassIris = backendService.getSubClassIris(ns + "Crust");
        assertNotNull(crustClassIris, "Crust 子类集合不应为 null");
        assertFalse(crustClassIris.isEmpty(), "TBox 中应至少存在一个 Crust 子类");

        String valuesClause = backendService.getOntologyService().buildValuesClause("cls", crustClassIris);
        String sparql = """
                PREFIX : <%s>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                SELECT DISTINCT ?instance ?type WHERE {
                    %s
                    ?instance rdf:type ?cls .
                    ?instance :type ?type .
                }
                """.formatted(ns, valuesClause);

        List<Map<String, String>> rows = backendService.getObdaHandler().executeAboxQuery(sparql);

        assertNotNull(rows, "SPARQL 查询结果不应为 null");
        assertFalse(rows.isEmpty(), "应至少返回一个饼底实例及供应商");

        for (Map<String, String> row : rows) {
            assertNotNull(row.get("instance"), "instance IRI 不应为 null");
            assertNotNull(row.get("type"), "type 值不应为 null");
            assertFalse(row.get("type").isBlank(), "type 值不应为空字符串");
        }

        log.info("✅ 场景1通过: 共检索到 {} 条饼底-供应商记录", rows.size());
        if (log.isDebugEnabled()) {
            rows.forEach(r -> log.debug("  instance={} | type={}", r.get("instance"), r.get("type")));
        }
    }

    // ============================================================
    // 场景2: OWLAPI + Openllet 联合推理查询（通用框架版）
    // ============================================================
    @Test
    @Order(2)
    @DisplayName("场景2: OWLAPI + Openllet 联合推理查询（通用框架）")
    void testQueryWithInferredProperties() throws OWLOntologyCreationException {
        String ns = "http://example.org/pizza/components/classes/";

        String aboxSparql = """
                PREFIX : <%s>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                CONSTRUCT {
                    ?s a :PizzaComponent ;
                       :name ?name ;
                       rdf:type ?componentType ;
                       :supplier ?supplier ;
                       :price ?price .
                }
                WHERE {
                    ?s a :PizzaComponent ;
                       :name ?name ;
                       rdf:type ?componentType ;
                       :supplier ?supplier ;
                       :price ?price .
                }
                LIMIT 100
                """.formatted(ns);

        OWLOntology aboxOntology = backendService.getObdaHandler()
                .loadAboxFromOntop(aboxSparql, backendService.getOntologyService().gettBoxOntology());
        assertNotNull(aboxOntology, "ABox 本体加载结果不应为 null");
        assertFalse(aboxOntology.isEmpty(), "ABox 本体不应为空，请检查 Ontop 端点与 SPARQL");

        QueryService queryService = new QueryService(backendService);

        List<QueryService.IndividualRecord> records = queryService.queryInstances(
                backendService.getOntologyService().gettBoxOntology(),
                aboxOntology,
                QueryService.QueryConfig.builder(ns + "PizzaComponent")
                        .dataProperties(ns + "supplier", ns + "price")
                        .maxResults(200)
                        .build()
        );

        assertNotNull(records, "查询结果列表不应为 null");
        assertFalse(records.isEmpty(), "应至少返回一个 PizzaComponent 实例");

        for (QueryService.IndividualRecord record : records) {
            assertNotNull(record.localName(), "个体本地名不应为 null");
            assertNotNull(record.inferredTypes(), "推断类型列表不应为 null");
            assertTrue(record.dataProperties().containsKey("supplier"),
                    "结果应包含 supplier 属性键 | individual=" + record.localName());
            assertTrue(record.dataProperties().containsKey("price"),
                    "结果应包含 price 属性键 | individual=" + record.localName());
        }

        log.info("✅ 场景2通过: 共查询到 {} 个 PizzaComponent 实例", records.size());
        if (log.isInfoEnabled()) {
            records.forEach(r -> {
                String types = r.inferredTypes().isEmpty() ? "-" : String.join(", ", r.inferredTypes());
                String supplier = r.dataProperties().getOrDefault("supplier", "-");
                String price = r.dataProperties().getOrDefault("price", "-");
                log.info("   🍕 {} ⇒ {} | 供应商: {} | 价格: {}", r.localName(), types, supplier, price);
            });
        }
    }

    // ============================================================
    // 场景3: 插入不满足数据库 type 限制的组件应被拒绝
    // ============================================================
    @Test
    @Order(3)
    @DisplayName("场景3: 负面 - 插入不满足数据库 type 限制的组件应被拒绝")
    void testInsertWithInvalidTypeShouldFail() {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String newName = "spicy_chicken_new_" + System.currentTimeMillis();

        Map<String, String> invalidProperties = new LinkedHashMap<>();
        invalidProperties.put(typeNS + "name", newName);
        invalidProperties.put(typeNS + "type", "SpicyChicken");
        invalidProperties.put(typeNS + "supplier", "SupplierX");
        invalidProperties.put(typeNS + "price", "12.99");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + newName, invalidProperties);

        InsertService inserter = new InsertService(backendService);

        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertComponentAutoSplit(invalidProperties, tempAxioms),
                "插入 SpicyChicken 类型应因数据库 type 约束被拒绝"
        );

        log.info("✅ 场景3通过: 预期异常已捕获 [{}] {}", ex.getClass().getSimpleName(), ex.getMessage());
    }

    // ============================================================
    // 场景4: 插入重复 name 的组件应违反唯一性约束
    // ============================================================
    @Test
    @Order(4)
    @DisplayName("场景4: 负面 - 插入重复 name 的组件应违反唯一性约束")
    void testInsertDuplicateNameShouldFail() {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String duplicateName = "NeapolitanCrustInstance";

        Map<String, String> duplicateProperties = new LinkedHashMap<>();
        duplicateProperties.put(typeNS + "name", duplicateName);
        duplicateProperties.put(typeNS + "type", "NeapolitanCrust");
        duplicateProperties.put(typeNS + "supplier", "SupplierX");
        duplicateProperties.put(typeNS + "price", "12.99");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + duplicateName, duplicateProperties);

        InsertService inserter = new InsertService(backendService);

        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertComponentAutoSplit(duplicateProperties, tempAxioms),
                "插入重复 name 应违反数据库唯一性约束"
        );

        log.info("✅ 场景4通过: 预期异常已捕获 [{}] {}", ex.getClass().getSimpleName(), ex.getMessage());
    }

    // ============================================================
    // 场景5: 缺少 rdf:type 时应拒绝写入
    // ============================================================
    @Test
    @Order(5)
    @DisplayName("场景5: 负面 - 缺少 type 属性时应拒绝写入")
    void testInsertWithoutTypeShouldFail() {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String newName = "no_type_test_" + System.currentTimeMillis();

        Map<String, String> noTypeProperties = new LinkedHashMap<>();
        noTypeProperties.put(typeNS + "name", newName);
        noTypeProperties.put(typeNS + "supplier", "SupplierX");
        noTypeProperties.put(typeNS + "price", "12.99");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + newName, noTypeProperties);

        InsertService inserter = new InsertService(backendService);

        assertThrows(IllegalArgumentException.class,
                () -> inserter.insertComponentAutoSplit(noTypeProperties, tempAxioms),
                "缺少 type 属性时应拒绝写入"
        );

        log.info("✅ 场景5通过: 缺少 type 时正确拒绝写入");
    }

    // ============================================================
    // 场景6: 正常插入验证（适配 OntopMappingResolver 自动拆分）
    // ============================================================
    @Test
    @Order(6)
    @DisplayName("场景6: 正面 - 正常插入 NeapolitanCrust 实例并验证数据一致性")
    void testInsertValidPizzaComponent() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String newName = "NeapolitanCrustInstanceTest_" + System.currentTimeMillis();

        Map<String, String> allProperties = new LinkedHashMap<>();
        allProperties.put(typeNS + "name", newName);
        allProperties.put(typeNS + "type", "NeapolitanCrust");
        allProperties.put(typeNS + "supplier", "SupplierX");
        allProperties.put(typeNS + "price", "12.99");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + newName, allProperties);

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(allProperties, tempAxioms),
                "合法属性写入不应抛出任何异常"
        );
        log.info("📝 场景6: 自动拆分写入完成 name={} | 属性数={}", newName, allProperties.size());

        String verifySparql = """
            PREFIX : <%s>
            SELECT ?supplier ?price WHERE {
                ?s a :NeapolitanCrust ;
                   :name "%s" ;
                   :supplier ?supplier ;
                   :price ?price .
            }
            """.formatted(typeNS, newName);

        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);

        assertFalse(results.isEmpty(), "写入后应能通过 SPARQL 查询到新插入的组件");
        assertEquals(1, results.size(), "应恰好返回 1 条匹配记录");

        Map<String, String> row = results.get(0);
        String supplier = row.get("supplier");
        String priceStr = row.get("price");

        assertNotNull(supplier, "supplier 字段不应为 null");
        assertNotNull(priceStr, "price 字段不应为 null");
        assertEquals("SupplierX", supplier, "supplier 值应与写入一致");
        assertEquals(new BigDecimal("12.99"), new BigDecimal(priceStr), "price 值应与写入一致");

        log.info("✅ 场景6通过: name={} | supplier={} | price={}", newName, supplier, priceStr);
    }

    // ============================================================
    // 场景7: 多属性更新验证（适配 OntopMappingResolver 自动拆分）
    // ============================================================
    @Test
    @Order(7)
    @DisplayName("场景7: 正面 - 安全更新 price/supplier/stockQuantity 并验证本体与数据库一致性")
    void testUpdateMultipleProperties() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetName = "MultiUpdateTest_" + System.currentTimeMillis();

        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", targetName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "supplier", "OldSupplier");
        initProperties.put(typeNS + "price", "9.99");
        initProperties.put(typeNS + "stockQuantity", "100");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + targetName, initProperties);

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(initProperties, tempAxioms),
                "前置插入不应失败"
        );
        log.info("📝 场景7: 前置插入完成 name={} | supplier=OldSupplier | price=9.99 | stock=100", targetName);

        Map<String, String> updatedProperties = new LinkedHashMap<>();
        updatedProperties.put(typeNS + "type", "NeapolitanCrust");
        updatedProperties.put(typeNS + "supplier", "NewSupplier");
        updatedProperties.put(typeNS + "price", "15.50");
        updatedProperties.put(typeNS + "stockQuantity", "42");

        UpdateService updater = new UpdateService(backendService);
        Map<String, String> identifierValues = new LinkedHashMap<>();
        identifierValues.put(typeNS + "name", targetName);

        assertDoesNotThrow(
                () -> updater.updateComponentAutoSplit(identifierValues, updatedProperties),
                "多属性覆盖更新不应抛出异常"
        );
        log.info("🔄 场景7: 多属性覆盖更新执行完毕 supplier→NewSupplier | price→15.50 | stock→42");

        String verifySparql = """
            PREFIX : <%s>
            SELECT ?supplier ?price ?stock WHERE {
                ?s a :NeapolitanCrust ;
                   :name "%s" ;
                   :supplier ?supplier ;
                   :price ?price ;
                   :stockQuantity ?stock .
            }
            """.formatted(typeNS, targetName);

        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);

        assertFalse(results.isEmpty(), "更新后应能通过 SPARQL 查询到该组件");
        assertEquals(1, results.size(), "应恰好返回 1 条匹配记录");

        Map<String, String> row = results.get(0);
        assertEquals("NewSupplier", row.get("supplier"), "supplier 应被更新为 NewSupplier");
        assertEquals(new BigDecimal("15.50"), new BigDecimal(row.get("price")), "price 应从 9.99 更新为 15.50");
        assertEquals(42, Integer.parseInt(row.get("stock")), "stockQuantity 应从 100 更新为 42");

        log.info("✅ 场景7通过: name={} | supplier={} | price={} | stock={}",
                targetName, row.get("supplier"), row.get("price"), row.get("stock"));
    }

    // ============================================================
    @Test
    @Order(8)
    @DisplayName("场景8: SWRL 实时推导 LowStockCrust (BackendService注入 → Openllet推理 → 验证)")
    void testQueryWithLiveSwrl() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String lowStockName = "SwrlLowStockTest_" + System.currentTimeMillis();

        // 1. 仅构造低库存组件公理 (stockQuantity=8 < 阈值20)
        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", lowStockName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "supplier", "SwrlTestSupplier");
        initProperties.put(typeNS + "price", "5.00");
        initProperties.put(typeNS + "stockQuantity", "8");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + lowStockName, initProperties);
        log.info("📝 场景8: 已构造低库存组件公理 name={} | stockQuantity=8", lowStockName);

        try {
            // 2. ⭐ 通过 BackendService 将公理注入内存本体并触发推理刷新
            backendService.getOntologyService().getManager().addAxioms(backendService.getOntologyService().gettBoxOntology(),tempAxioms);
            backendService.getReasonerService().getReasoner().flush();
            log.info("💉 场景8: 已通过 BackendService 注入 {} 条公理并刷新推理器", tempAxioms.size());

            // 3. ⭐ 通过 BackendService 获取单例推理器验证 SWRL 推导结果
            OWLDataFactory df = backendService.getOntologyService().gettBoxOntology()
                    .getOWLOntologyManager().getOWLDataFactory();
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(indNS + lowStockName));
            OWLClass lowStockCrust = df.getOWLClass(IRI.create(typeNS + "LowStockCrust"));

            boolean isInferred = backendService.getReasonerService().getReasoner()
                    .getTypes(ind, false).containsEntity(lowStockCrust);

            assertTrue(isInferred,
                    "Openllet 应将 %s(stock=8) 推导为 LowStockCrust，若失败请检查 SWRL 规则是否已导入 TBox"
                            .formatted(lowStockName));

            log.info("✅ 场景8通过: name={} | stock=8 → 成功推导为 LowStockCrust", lowStockName);

        } finally {
            // 4. ⭐ 通过 BackendService 清理临时公理，确保测试隔离
            backendService.getOntologyService().getManager().removeAxioms(backendService.getOntologyService().gettBoxOntology(),tempAxioms);
            log.info("🧹 场景8: 已通过 BackendService 清理临时公理");
        }
    }

    // ============================================================
    // 场景9: SWRL 动态响应性 - 库存变更触发/撤销 LowStockCrust 推导
    // ============================================================
    @Test
    @Order(9)
    @DisplayName("场景9: SWRL 动态响应性 - 内存库存变更触发/撤销 LowStockCrust 推导")
    void testSwrlDynamicResponseOnStockChange() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String testName = "SwrlDynamicTest_" + System.currentTimeMillis();
        String indIRI = indNS + testName;

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        OWLOntology ontology = backendService.getOntologyService().gettBoxOntology();
        OWLOntologyManager mgr = ontology.getOWLOntologyManager();
        OWLDataFactory df = mgr.getOWLDataFactory();
        OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(indIRI));
        OWLClass lowStockCrust = df.getOWLClass(IRI.create(typeNS + "LowStockCrust"));

        // 辅助方法：注入公理 → flush → 检查推导结果
        // 使用局部变量捕获，避免 lambda 中重复获取 service
        var reasonerService = backendService.getReasonerService();

        try {
            // ========== 阶段1: 安全库存(50) → 不应被推导为 LowStockCrust ==========
            Map<String, String> safeProps = new LinkedHashMap<>();
            safeProps.put(typeNS + "name", testName);
            safeProps.put(typeNS + "type", "NeapolitanCrust");
            safeProps.put(typeNS + "supplier", "DynamicTestSupplier");
            safeProps.put(typeNS + "price", "10.00");
            safeProps.put(typeNS + "stockQuantity", "50");

            Set<OWLAxiom> phase1Axioms = axiomBuilder.buildAxioms(indIRI, safeProps);
            mgr.addAxioms(ontology, phase1Axioms);
            reasonerService.getReasoner().flush();
            log.info("📝 场景9-阶段1: 注入安全库存 name={} | stock=50", testName);

            boolean phase1Inferred = reasonerService.getReasoner()
                    .getTypes(ind, false).containsEntity(lowStockCrust);
            assertFalse(phase1Inferred, "阶段1失败: stock=50 不应被推导为 LowStockCrust");
            log.info("✅ 场景9-阶段1通过: stock=50 → 未推导出 LowStockCrust");

            // ========== 阶段2: 降低库存至阈值以下(5) → 应被推导为 LowStockCrust ==========
            // 先移除旧公理，再注入新库存值的公理（模拟 updateComponentAutoSplit 的内存效果）
            mgr.removeAxioms(ontology, phase1Axioms);

            Map<String, String> lowStockProps = new LinkedHashMap<>(safeProps);
            lowStockProps.put(typeNS + "stockQuantity", "5");
            Set<OWLAxiom> phase2Axioms = axiomBuilder.buildAxioms(indIRI, lowStockProps);
            mgr.addAxioms(ontology, phase2Axioms);
            reasonerService.getReasoner().flush();
            log.info("🔄 场景9-阶段2: 库存变更 name={} | stock=50→5", testName);

            boolean phase2Inferred = reasonerService.getReasoner()
                    .getTypes(ind, false).containsEntity(lowStockCrust);
            assertTrue(phase2Inferred,
                    "阶段2失败: stock=5 应被推导为 LowStockCrust，SWRL 规则未响应数据变更");
            log.info("✅ 场景9-阶段2通过: stock=5 → 成功推导出 LowStockCrust");

            // ========== 阶段3: 恢复安全库存(30) → 应从 LowStockCrust 中移除 ==========
            mgr.removeAxioms(ontology, phase2Axioms);

            Map<String, String> restoreProps = new LinkedHashMap<>(safeProps);
            restoreProps.put(typeNS + "stockQuantity", "30");
            Set<OWLAxiom> phase3Axioms = axiomBuilder.buildAxioms(indIRI, restoreProps);
            mgr.addAxioms(ontology, phase3Axioms);
            reasonerService.getReasoner().flush();
            log.info("🔄 场景9-阶段3: 库存恢复 name={} | stock=5→30", testName);

            boolean phase3Inferred = reasonerService.getReasoner()
                    .getTypes(ind, false).containsEntity(lowStockCrust);
            assertFalse(phase3Inferred,
                    "阶段3失败: stock=30 不应再被推导为 LowStockCrust，SWRL 推导未能随数据恢复而撤销");
            log.info("✅ 场景9-阶段3通过: stock=30 → LowStockCrust 推导已正确撤销");

            log.info("🎉 场景9完成: SWRL 动态响应性测试全部通过 | 个体={}", testName);

        } finally {
            // ⭐ 清理所有可能残留的临时公理（三个阶段中任一异常都可能遗留）
            // 安全做法：尝试移除所有阶段生成的公理集合，removeAxioms 对不存在的公理是幂等的
            Set<OWLAxiom> allPossibleAxioms = new LinkedHashSet<>();
            // 重新构建各阶段公理以确保 IRI 一致（axiomBuilder 是确定性的）
            Map<String, String> baseProps = new LinkedHashMap<>();
            baseProps.put(typeNS + "name", testName);
            baseProps.put(typeNS + "type", "NeapolitanCrust");
            baseProps.put(typeNS + "supplier", "DynamicTestSupplier");
            baseProps.put(typeNS + "price", "10.00");

            for (String qty : List.of("50", "5", "30")) {
                Map<String, String> props = new LinkedHashMap<>(baseProps);
                props.put(typeNS + "stockQuantity", qty);
                allPossibleAxioms.addAll(axiomBuilder.buildAxioms(indIRI, props));
            }
            mgr.removeAxioms(ontology, allPossibleAxioms);
            log.info("🧹 场景9: 已清理所有临时公理");
        }
    }

    // ============================================================
    // 场景10: SwrlRuleTriggerListener 通用框架 - 写入低库存自动触发异步回调
    // ============================================================
    @Test
    @Order(10)
    @DisplayName("场景10: SwrlRuleTriggerListener 通用框架 - 写入低库存自动触发异步回调")
    void testSwrlRuleTriggerListenerCallback() throws Exception {
        List<String> triggeredInstances = Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetClassIri = typeNS + "LowStockCrust";

        SwrlRuleTriggerListener<String> listener = new SwrlRuleTriggerListener<>(
                new SwrlRuleTriggerListener.Config<>(
                        targetClassIri,
                        instanceIri -> {
                            log.info("[回调执行] 低库存告警触发: {}", instanceIri);
                            triggeredInstances.add(instanceIri);
                            latch.countDown();
                        },
                        String.class
                ),
                backendService
        );

        try {
            listener.start();
            log.info("🚀 场景10: SwrlRuleTriggerListener 已启动 监控目标={}", targetClassIri);

            String testName = "ListenerTriggerTest_" + System.currentTimeMillis();

            Map<String, String> lowStockProperties = new LinkedHashMap<>();
            lowStockProperties.put(typeNS + "name", testName);
            lowStockProperties.put(typeNS + "type", "NeapolitanCrust");
            lowStockProperties.put(typeNS + "supplier", "ListenerTestSupplier");
            lowStockProperties.put(typeNS + "price", "8.00");
            lowStockProperties.put(typeNS + "stockQuantity", "3");

            GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
            Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + testName, lowStockProperties);

            InsertService inserter = new InsertService(backendService);
            assertDoesNotThrow(
                    () -> inserter.insertComponentAutoSplit(lowStockProperties, tempAxioms),
                    "低库存组件插入不应失败"
            );
            log.info("📝 场景10: 低库存组件已写入 name={} | stock=3", testName);

            boolean callbackExecuted = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);

            assertTrue(callbackExecuted,
                    "SwrlRuleTriggerListener 应在 10 秒内检测到 LowStockCrust 推导并触发回调");
            assertFalse(triggeredInstances.isEmpty(), "回调结果列表不应为空");
            assertTrue(triggeredInstances.stream().anyMatch(iri -> iri.contains(testName)),
                    "回调参数应包含刚写入的个体 IRI: " + testName);

            log.info("✅ 场景10通过: 捕获到 {} 次回调 实例={}",
                    triggeredInstances.size(), triggeredInstances);

        } finally {
            listener.shutdown();
            log.info("🧹 场景10: SwrlRuleTriggerListener 已关闭");
        }
    }

    // ============================================================
    // 场景11: 删除已存在的组件应成功且后续 SPARQL 查询返回空
    // ============================================================
    @Test
    @Order(11)
    @DisplayName("场景11: 正面 - 删除已存在的组件应成功且后续 SPARQL 查询返回空")
    void testDeleteExistingComponent() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetName = "ToDeleteTest_" + System.currentTimeMillis();

        Map<String, String> insertProperties = new LinkedHashMap<>();
        insertProperties.put(typeNS + "name", targetName);
        insertProperties.put(typeNS + "type", "NeapolitanCrust");
        insertProperties.put(typeNS + "supplier", "DeleteTestSupplier");
        insertProperties.put(typeNS + "price", "9.99");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + targetName, insertProperties);

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(insertProperties, tempAxioms),
                "前置插入不应失败"
        );

        String preCheckSparql = """
                PREFIX : <http://example.org/pizza/components/classes/>
                SELECT ?s WHERE { ?s a :NeapolitanCrust ; :name "%s" }
                """.formatted(targetName);
        List<Map<String, String>> preResults = backendService.getObdaHandler().executeAboxQuery(preCheckSparql);
        assertEquals(1, preResults.size(), "前置插入后应恰好查到 1 条记录");
        log.info("📝 场景11: 前置插入验证通过 name={}", targetName);

        DeleteService deleter = new DeleteService(backendService);
        Map<String, String> identifierValues = new LinkedHashMap<>();
        identifierValues.put(typeNS + "name", targetName);

        assertDoesNotThrow(
                () -> deleter.deleteComponentAutoSplit(identifierValues),
                "删除已存在组件不应抛出异常"
        );

        List<Map<String, String>> postResults = backendService.getObdaHandler().executeAboxQuery(preCheckSparql);
        assertTrue(postResults.isEmpty(),
                "删除后不应再通过 SPARQL 查询到该组件 | name=" + targetName);

        log.info("✅ 场景11通过: name={} 已成功删除", targetName);
    }

    // ============================================================
    // 场景12: 删除不存在的个体不应抛异常（幂等性验证）
    // ============================================================
    @Test
    @Order(12)
    @DisplayName("场景12: 正面 - 删除不存在的个体不应抛异常（幂等性验证）")
    void testDeleteNonExistentIsIdempotent() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String nonExistentName = "NonExistent_" + System.currentTimeMillis();

        DeleteService deleter = new DeleteService(backendService);
        Map<String, String> identifierValues = new LinkedHashMap<>();
        identifierValues.put(typeNS + "name", nonExistentName);

        assertDoesNotThrow(
                () -> deleter.deleteComponentAutoSplit(identifierValues),
                "删除不存在的个体应保持幂等，不应抛出异常"
        );

        String verifySparql = """
                PREFIX : <http://example.org/pizza/components/classes/>
                SELECT ?s WHERE { ?s a :NeapolitanCrust ; :name "%s" }
                """.formatted(nonExistentName);
        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);
        assertTrue(results.isEmpty(),
                "不存在的个体在删除前后都不应被查到 | name=" + nonExistentName);

        log.info("✅ 场景12通过: 删除不存在个体 name={} 未抛异常且数据库状态无变化", nonExistentName);
    }

    // ============================================================
    // 场景13: 多表自动拆分写入验证 - 单调用事务原子性
    // ============================================================
    @Test
    @Order(13)
    @DisplayName("场景13: 多表自动拆分写入验证 - 单调用事务原子性")
    void testMultiTableWriteAndQuery() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String testName = "MultiTblAuto_" + System.currentTimeMillis();

        Map<String, String> allProperties = new LinkedHashMap<>();
        allProperties.put(typeNS + "name", testName);
        allProperties.put(typeNS + "type", "NeapolitanCrust");
        allProperties.put(typeNS + "price", "22.50");
        allProperties.put(typeNS + "supplier", "FlourSupplierA");
        allProperties.put(typeNS + "stockQuantity", "60");
        allProperties.put(typeNS + "crustThicknessMm", "3.5");
        allProperties.put(typeNS + "bakingTemperatureCelsius", "450");
        allProperties.put(typeNS + "flourType", "Tipo00");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + testName, allProperties);

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(allProperties, tempAxioms),
                "多表自动拆分写入不应抛出异常"
        );
        log.info("📝 场景13: 单调用多表写入完成 name={} | 总属性={}", testName, allProperties.size());

        Map<String, Set<String>> mappingCache = backendService.getObdaHandler().getAllMappedPropertiesWithVariables();
        long resolvedCount = allProperties.keySet().stream()
                .filter(iri -> mappingCache.containsKey(iri) && !mappingCache.get(iri).isEmpty())
                .count();
        assertEquals(allProperties.size(), resolvedCount,
                "所有 %d 个属性均应在 OntopMappingResolver 缓存中找到映射".formatted(allProperties.size()));
        log.info("🔍 场景13: 正向映射验证 {}/{} 个属性已成功解析", resolvedCount, allProperties.size());

        String verifySparql = """
            PREFIX : <%s>
            SELECT ?supplier ?price ?stock ?thickness ?temp ?flour WHERE {
                ?s a :NeapolitanCrust ;
                   :name "%s" ;
                   :supplier ?supplier ;
                   :price ?price ;
                   :stockQuantity ?stock ;
                   :crustThicknessMm ?thickness ;
                   :bakingTemperatureCelsius ?temp ;
                   :flourType ?flour .
            }
            """.formatted(typeNS, testName);

        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);
        assertFalse(results.isEmpty(), "跨表聚合查询应返回结果（若为空说明事务未完整提交或拆分失败）");
        assertEquals(1, results.size(), "应恰好返回 1 条聚合记录");

        Map<String, String> row = results.get(0);
        assertEquals("FlourSupplierA", row.get("supplier"));
        assertEquals(new BigDecimal("22.50"), new BigDecimal(row.get("price")));
        assertEquals(60, Integer.parseInt(row.get("stock")));
        assertEquals(new BigDecimal("3.5"), new BigDecimal(row.get("thickness")));
        assertEquals(450, Integer.parseInt(row.get("temp")));
        assertEquals("Tipo00", row.get("flour"));

        log.info("✅ 场景13通过: 单调用多表自动拆分+事务原子性+查询闭环验证成功 | name={}", testName);
    }

    // ============================================================
    // 场景14: 正向映射完整性验证 - OntopMappingResolver 缓存校验
    // ============================================================
    @Test
    @Order(14)
    @DisplayName("场景14: 正向映射完整性验证 - OntopMappingResolver 缓存校验")
    void testForwardMappingCompleteness() {
        String typeNS = "http://example.org/pizza/components/classes/";

        Map<String, Set<String>> mappingCache = backendService.getObdaHandler().getAllMappedPropertiesWithVariables();
        assertNotNull(mappingCache, "映射缓存不应为 null");
        assertFalse(mappingCache.isEmpty(), "映射缓存不应为空");

        List<String> expectedProperties = List.of(
                typeNS + "name", typeNS + "type", typeNS + "supplier",
                typeNS + "price", typeNS + "stockQuantity",
                typeNS + "crustThicknessMm", typeNS + "bakingTemperatureCelsius",
                typeNS + "flourType", typeNS + "status", typeNS + "batchNumber"
        );

        for (String propIri : expectedProperties) {
            Set<String> variables = mappingCache.get(propIri);
            assertNotNull(variables, "属性应有映射 | " + propIri);
            assertFalse(variables.isEmpty(), "属性映射变量集不应为空 | " + propIri);
            log.debug("  ✅ {} → {}", propIri, variables);
        }
        log.info("📋 场景14: 正向映射验证 共 {} 个属性已解析 | 缓存总条目={}", expectedProperties.size(), mappingCache.size());

        assertThrows(UnsupportedOperationException.class,
                () -> mappingCache.put("test", Set.of("col")),
                "映射缓存应为只读视图");
        log.info("🔒 场景14: 映射缓存只读保护验证通过");

        assertNull(mappingCache.get("http://nonexistent.org/property"),
                "未映射属性应返回 null");
        log.info("✅ 场景14通过: 正向映射完整性验证完成 | 缓存条目数={}", mappingCache.size());
    }

    // ============================================================
    // 场景15: 多表自动拆分写入事务回滚 - 第二表失败时首表同步回退
    // ============================================================
    @Test
    @Order(15)
    @DisplayName("场景15: 多表自动拆分写入事务回滚 - 第二表失败时首表同步回退")
    void testMultiTableWriteTransactionRollback() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String testName = "RollbackTest_" + System.currentTimeMillis();

        Map<String, String> allProperties = new LinkedHashMap<>();
        allProperties.put(typeNS + "name", testName);
        allProperties.put(typeNS + "type", "NeapolitanCrust");
        allProperties.put(typeNS + "price", "18.00");
        allProperties.put(typeNS + "supplier", "FlourSupplierB");
        allProperties.put(typeNS + "stockQuantity", "30");
        allProperties.put(typeNS + "crustThicknessMm", "-999.99");
        allProperties.put(typeNS + "bakingTemperatureCelsius", "450");
        allProperties.put(typeNS + "flourType", "Tipo00");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + testName, allProperties);

        InsertService inserter = new InsertService(backendService);
        assertThrows(Exception.class,
                () -> inserter.insertComponentAutoSplit(allProperties, tempAxioms),
                "Join 表约束违反应导致 insertComponentAutoSplit 抛出异常"
        );
        log.info("⚠️ 场景15: 预期异常已捕获 name={} | 第二表写入失败触发事务回滚", testName);

        String verifySparql = """
            PREFIX : <%s>
            SELECT ?name WHERE {
                ?s a :NeapolitanCrust ;
                   :name "%s" ;
                   :name ?name .
            }
            """.formatted(typeNS, testName);

        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);
        assertTrue(results.isEmpty(),
                "事务回滚后 Base 表中不应残留任何数据（若查到记录说明第一张表的 INSERT 未被回滚）"
        );
        log.info("✅ 场景15通过: 第二表写入失败 → 第一表 INSERT 已完整回滚 | name={}", testName);
    }

    // ============================================================
    // 场景16: 跨表更新 price(components) + crustThicknessMm(details) 并验证 JOIN 键自动填充
    // ============================================================
    @Test
    @Order(16)
    @DisplayName("场景16: 正面 - 跨表更新 price + crustThicknessMm 并验证 JOIN 键自动填充")
    void testUpdateAcrossMultipleTables() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetName = "CrossTableTest_" + System.currentTimeMillis();

        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", targetName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "supplier", "OldSupplier");
        initProperties.put(typeNS + "price", "9.99");
        initProperties.put(typeNS + "crustThicknessMm", "4");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + targetName, initProperties);

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(initProperties, tempAxioms),
                "前置跨表插入不应失败"
        );
        log.info("📝 场景16: 前置跨表插入完成 name={} | price=9.99 | crustThicknessMm=5", targetName);

        Map<String, String> identifierValues = new LinkedHashMap<>();
        identifierValues.put(typeNS + "name", targetName);

        Map<String, String> updatedProperties = new LinkedHashMap<>();
        updatedProperties.put(typeNS + "type", "NeapolitanCrust");
        updatedProperties.put(typeNS + "price", "18.88");
        updatedProperties.put(typeNS + "crustThicknessMm", "5");

        UpdateService updater = new UpdateService(backendService);
        assertDoesNotThrow(
                () -> updater.updateComponentAutoSplit(identifierValues, updatedProperties),
                "跨表更新不应抛出异常，JOIN 键应被自动填充"
        );
        log.info("🔄 场景16: 跨表更新执行完毕 price→18.88 | crustThicknessMm→12");

        String verifySparql = """
            PREFIX : <%s>
            SELECT ?price ?thickness WHERE {
                ?s a :NeapolitanCrust ;
                   :name "%s" ;
                   :price ?price ;
                   :crustThicknessMm ?thickness .
            }
            """.formatted(typeNS, targetName);

        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);

        assertFalse(results.isEmpty(), "跨表更新后应能通过 SPARQL 查询到完整实例");
        assertEquals(1, results.size(), "应恰好返回 1 条匹配记录");

        Map<String, String> row = results.get(0);
        assertEquals(new BigDecimal("18.88"), new BigDecimal(row.get("price")),
                "主表 price 应从 9.99 更新为 18.88");
        assertEquals("5.0", row.get("thickness"),
                "子表 crustThicknessMm 应从 5 更新为 12");

        log.info("✅ 场景16通过: name={} | price={} | crustThicknessMm={}",
                targetName, row.get("price"), row.get("thickness"));
    }

    // ============================================================
    // 场景17: 子表JOIN键缺失时应抛出异常并回滚主表更新
    // ============================================================
    @Test
    @Order(17)
    @DisplayName("场景17: 负面 - 子表JOIN键缺失时应抛出异常并回滚主表更新")
    void testUpdateAcrossTablesShouldRollbackOnSubTableFailure() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetName = "RollbackUpdateTest_" + System.currentTimeMillis();

        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", targetName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "price", "9.99");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + targetName, initProperties);

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(initProperties, tempAxioms),
                "前置主表插入不应失败"
        );
        log.info("📝 场景17: 前置插入完成(仅主表) name={} | price=9.99", targetName);

        Map<String, String> identifierValues = new LinkedHashMap<>();
        identifierValues.put(typeNS + "name", targetName);

        Map<String, String> updatedProperties = new LinkedHashMap<>();
        updatedProperties.put(typeNS + "type", "NeapolitanCrust");
        updatedProperties.put(typeNS + "price", "99.99");
        updatedProperties.put(typeNS + "crustThicknessMm_notexist", "20");

        UpdateService updater = new UpdateService(backendService);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> updater.updateComponentAutoSplit(identifierValues, updatedProperties),
                "子表缺少JOIN键时必须抛出异常触发回滚，不能静默跳过"
        );

        assertTrue(ex.getMessage().contains("无有效 OBDA 映射"),
                "异常消息应明确指出属性无映射: " + ex.getMessage());
        log.info("⚠️ 场景17: 预期异常已捕获 {}", ex.getMessage());

        String verifySparql = """
            PREFIX : <%s>
            SELECT ?price WHERE {
                ?s a :NeapolitanCrust ;
                   :name "%s" ;
                   :price ?price .
            }
            """.formatted(typeNS, targetName);

        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);

        assertFalse(results.isEmpty(), "原始主表记录应仍然存在");
        assertEquals(new BigDecimal("9.99"), new BigDecimal(results.get(0).get("price")),
                "❌ 事务回滚失败! 主表 price 应为 9.99(原值)，实际为 " + results.get(0).get("price")
                        + "。说明主表UPDATE未被回滚，存在数据不一致风险！");

        log.info("✅ 场景17通过: 主表 price 保持原值 9.99，子表更新失败已正确回滚整个事务");
    }
    // ============================================================
    // 场景18: 正面 - 跨表删除两表均有记录时应整体成功
    // ============================================================
    @Test
    @Order(18)
    @DisplayName("场景18: 正面 - 跨表删除两表均有记录时应整体成功")
    void testDeleteAcrossMultipleTablesSuccess() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetName = "CrossDelSuccess_" + System.currentTimeMillis();

        // ========== 1. 前置：插入一条横跨两表的完整记录 ==========
        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", targetName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "supplier", "DelSuccessSupplier");
        initProperties.put(typeNS + "price", "12.50");
        initProperties.put(typeNS + "crustThicknessMm", "4");

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(indNS + targetName, initProperties);

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(initProperties, tempAxioms),
                "前置跨表插入不应失败"
        );
        log.info("📝 场景18: 前置跨表插入完成 name={} | price=12.50 | crustThicknessMm=4", targetName);

        // 验证两表均有数据
        String verifySparql = """
            PREFIX : <%s>
            SELECT ?price ?thickness WHERE {
                ?s a :NeapolitanCrust ;
                   :name "%s" ;
                   :price ?price ;
                   :crustThicknessMm ?thickness .
            }
            """.formatted(typeNS, targetName);
        assertEquals(1, backendService.getObdaHandler().executeAboxQuery(verifySparql).size(),
                "前置插入后应能通过 SPARQL 联表查到 1 条完整记录");

        // ========== 2. 执行跨表删除 ==========
        DeleteService deleter = new DeleteService(backendService);
        Map<String, String> identifierValues = new LinkedHashMap<>();
        identifierValues.put(typeNS + "name", targetName);

        assertDoesNotThrow(
                () -> deleter.deleteComponentAutoSplit(identifierValues),
                "跨表删除已存在的完整记录不应抛出异常"
        );
        log.info("🗑️ 场景18: 跨表删除执行完毕 name={}", targetName);

        // ========== 3. 验证两表均已清空（SPARQL 联表查询返回空）==========
        List<Map<String, String>> postResults = backendService.getObdaHandler().executeAboxQuery(verifySparql);
        assertTrue(postResults.isEmpty(),
                "删除后不应再通过 SPARQL 联表查询到该组件 | name=" + targetName);

        // 额外验证：单独查主表也应返回空（防止仅删了子表）
        String mainOnlySparql = """
            PREFIX : <%s>
            SELECT ?s WHERE { ?s a :PizzaComponent ; :name "%s" }
            """.formatted(typeNS, targetName);
        assertTrue(backendService.getObdaHandler().executeAboxQuery(mainOnlySparql).isEmpty(),
                "删除后主表也不应再查到该组件 | name=" + targetName);

        log.info("✅ 场景18通过: name={} 跨表删除成功，主表+子表均已清除", targetName);
    }

    /**
     * 通过 Ontop Endpoint 读取库存值，用于一致性校验
     */
    private int readStockFromOntopEndpoint(String individualName, String typeNS,
                                           String indNS, String propertyIRI) {
        Set<OWLAxiom> axioms = backendService.queryPropertyAxiom(
                typeNS, indNS, individualName, propertyIRI);
        return extractDataPropertyIntValue(axioms, indNS, individualName, propertyIRI);
    }

    /**
     * 从公理集合中提取数据属性的整数值
     */
    public int extractDataPropertyIntValue(Set<OWLAxiom> axioms, String indNS,
                                           String individualName, String propertyIRI) {
        if (axioms == null || axioms.isEmpty()) {
            log.warn("⚠️ [Extract] 公理集合为空 | individual={} | property={}", individualName, propertyIRI);
            return -1;
        }

        OWLDataProperty prop;
        try {
            prop = backendService.getDataProperty(propertyIRI);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ [Extract] 数据属性未找到: {}", e.getMessage());
            return -1;
        }

        IRI indIRI = IRI.create(indNS + individualName);

        for (OWLAxiom ax : axioms) {
            if (!(ax instanceof OWLDataPropertyAssertionAxiom dpAx)) continue;
            if (!dpAx.getProperty().asOWLDataProperty().equals(prop)) continue;
            if (!dpAx.getSubject().asOWLNamedIndividual().getIRI().equals(indIRI)) continue;

            OWLLiteral literal = dpAx.getObject();
            Number parsed = backendService.parseNumeric(literal);
            if (parsed == null) continue;

            int result = parsed.intValue();
            log.info("  ✅ 提取成功 | individual={} | property={} | value={}",
                    individualName, propertyIRI, result);
            return result;
        }

        log.warn("❌ [Extract] 未找到匹配的数据属性断言 | individual={} | property={}", individualName, propertyIRI);
        return -1;
    }

    /**
     * 辅助方法: 从 Ontop 加载最新 ABox → Openllet 推理 → 检查是否被推导为 LowStockCrust
     */
    private boolean isInferredAsLowStockCrust(String individualName, String typeNS, String indNS) throws Exception {
        String aboxSparql = """
                PREFIX : <http://example.org/pizza/components/classes/>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                CONSTRUCT {
                    ?s a :PizzaComponent ;
                       :name ?name ;
                       rdf:type ?componentType ;
                       :stockQuantity ?stock .
                }
                WHERE {
                    ?s a :PizzaComponent ;
                       :name "%s" ;
                       rdf:type ?componentType ;
                       :stockQuantity ?stock .
                }
                """.formatted(individualName);

        OWLOntology aboxOntology = backendService.getObdaHandler()
                .loadAboxFromOntop(aboxSparql, backendService.getOntologyService().gettBoxOntology());

        openllet.owlapi.OpenlletReasoner reasoner = null;
        try {
            reasoner = openllet.owlapi.OpenlletReasonerFactory.getInstance().createReasoner(aboxOntology);
            reasoner.flush();

            OWLDataFactory df = aboxOntology.getOWLOntologyManager().getOWLDataFactory();
            IRI individualIri = IRI.create(indNS + individualName);
            OWLNamedIndividual ind = df.getOWLNamedIndividual(individualIri);
            OWLClass lowStockCrust = df.getOWLClass(IRI.create(typeNS + "LowStockCrust"));

            var instances = reasoner.getInstances(lowStockCrust, false).getFlattened();
            log.info("🔍 [推理诊断] LowStockCrust 当前包含 {} 个个体: {}",
                    instances.size(),
                    instances.stream()
                            .map(i -> i.getIRI().getFragment())
                            .collect(java.util.stream.Collectors.joining(", ")));
            return reasoner.getTypes(ind, false).containsEntity(lowStockCrust);
        } finally {
            if (reasoner != null) reasoner.dispose();
        }
    }
}