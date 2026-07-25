package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.*;
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
    private static final String TBOX_FILE = "D:/work/Ontology/pizza-ontology/ontology/pizza-all.owl";

    private static BackendService backendService;

    @BeforeAll
    static void setUp() throws Exception {
        log.info("=== 初始化 TBox/ABox 分离架构测试环境 ===");
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        backendService = BackendService.getInstance(TBOX_FILE, obdaHandler);
        assertNotNull(backendService, "BackendService 初始化失败");
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
        String newName = "spicy_chicken_new_" + System.currentTimeMillis();

        Map<String, String> invalidProperties = new LinkedHashMap<>();
        invalidProperties.put(typeNS + "name", newName);
        invalidProperties.put(typeNS + "type", "SpicyChicken");  // ← 数据库中不存在的类型
        invalidProperties.put(typeNS + "supplier", "SupplierX");
        invalidProperties.put(typeNS + "price", "12.99");

        InsertService inserter = new InsertService(backendService);

        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertComponentAutoSplit(invalidProperties),
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
        String duplicateName = "NeapolitanCrustInstance";  // ← 假设数据库中已存在该名称

        Map<String, String> duplicateProperties = new LinkedHashMap<>();
        duplicateProperties.put(typeNS + "name", duplicateName);
        duplicateProperties.put(typeNS + "type", "NeapolitanCrust");
        duplicateProperties.put(typeNS + "supplier", "SupplierX");
        duplicateProperties.put(typeNS + "price", "12.99");

        InsertService inserter = new InsertService(backendService);

        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertComponentAutoSplit(duplicateProperties),
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
        String newName = "no_type_test_" + System.currentTimeMillis();

        Map<String, String> noTypeProperties = new LinkedHashMap<>();
        noTypeProperties.put(typeNS + "name", newName);
        // ⚠️ 故意不放入 type 属性
        noTypeProperties.put(typeNS + "supplier", "SupplierX");
        noTypeProperties.put(typeNS + "price", "12.99");

        InsertService inserter = new InsertService(backendService);

        assertThrows(IllegalArgumentException.class,
                () -> inserter.insertComponentAutoSplit(noTypeProperties),
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
        String newName = "NeapolitanCrustInstanceTest_" + System.currentTimeMillis();

        Map<String, String> allProperties = new LinkedHashMap<>();
        allProperties.put(typeNS + "name", newName);
        allProperties.put(typeNS + "type", "NeapolitanCrust");
        allProperties.put(typeNS + "supplier", "SupplierX");
        allProperties.put(typeNS + "price", "12.99");

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(allProperties),
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
        String targetName = "MultiUpdateTest_" + System.currentTimeMillis();

        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", targetName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "supplier", "OldSupplier");
        initProperties.put(typeNS + "price", "9.99");
        initProperties.put(typeNS + "stockQuantity", "100");

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(initProperties),
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
    // 场景8: SWRL 实时推导 LowStockCrust
    // ============================================================
    @Test
    @Order(8)
    @DisplayName("场景8: SWRL 实时推导 LowStockCrust (写入低库存 → Openllet推理 → SPARQL验证)")
    void testQueryWithLiveSwrl() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String lowStockName = "SwrlLowStockTest_" + System.currentTimeMillis();

        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", lowStockName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "supplier", "SwrlTestSupplier");
        initProperties.put(typeNS + "price", "5.00");
        initProperties.put(typeNS + "stockQuantity", "8");

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(initProperties),
                "前置低库存组件插入不应失败"
        );
        log.info("📝 场景8: 低库存组件已写入 name={} | stockQuantity=8", lowStockName);

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
                       :name ?name ;
                       rdf:type ?componentType ;
                       :stockQuantity ?stock .
                }
                """;

        long start = System.currentTimeMillis();
        OWLOntology aboxOntology = backendService.getObdaHandler()
                .loadAboxFromOntop(aboxSparql, backendService.getOntologyService().gettBoxOntology());
        assertNotNull(aboxOntology, "ABox 本体加载结果不应为 null");

        openllet.owlapi.OpenlletReasoner reasoner = openllet.owlapi.OpenlletReasonerFactory.getInstance()
                .createReasoner(aboxOntology);
        reasoner.flush();
        long inferTime = System.currentTimeMillis() - start;
        log.info("⚡ 场景8: SWRL 增量推理完成 耗时={}ms | ABox三元组数={}", inferTime, aboxOntology.getAxiomCount());

        String verifySparql = """
                PREFIX : <http://example.org/pizza/components/classes/>
                SELECT ?stockQty WHERE {
                    ?s a :NeapolitanCrust ;
                       :name "%s" ;
                       :stockQuantity ?stockQty .
                }
                """.formatted(lowStockName);

        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);
        assertFalse(results.isEmpty(), "应能通过 SPARQL 查询到低库存组件");
        assertEquals(1, results.size(), "应恰好返回 1 条匹配记录");

        int stockQty = Integer.parseInt(results.get(0).get("stockQty"));
        assertTrue(stockQty < 20,
                "stockQuantity=%d 应小于阈值20，SWRL 规则条件应满足".formatted(stockQty));

        OWLDataFactory df = aboxOntology.getOWLOntologyManager().getOWLDataFactory();
        IRI individualIri = IRI.create(indNS + lowStockName);
        OWLNamedIndividual ind = df.getOWLNamedIndividual(individualIri);
        OWLClass lowStockCrust = df.getOWLClass(IRI.create(typeNS + "LowStockCrust"));

        boolean isInferred = reasoner.getTypes(ind, false).containsEntity(lowStockCrust);
        assertTrue(isInferred,
                "Openllet 应将 %s(stock=%d) 推导为 LowStockCrust，若失败请检查 SWRL 规则文件是否已导入 TBox"
                        .formatted(lowStockName, stockQty));

        log.info("✅ 场景8通过: name={} | stock={} → LowStockCrust | 推理耗时={}ms",
                lowStockName, stockQty, inferTime);

        reasoner.dispose();
    }

    // ============================================================
    // 场景9: SWRL 动态响应性 - 库存变更触发/撤销 LowStockCrust 推导
    // ============================================================
    @Test
    @Order(9)
    @DisplayName("场景9: SWRL 动态响应性 - 库存变更触发/撤销 LowStockCrust 推导")
    void testSwrlDynamicResponseOnStockChange() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String testName = "SwrlDynamicTest_" + System.currentTimeMillis();

        InsertService inserter = new InsertService(backendService);
        UpdateService updater = new UpdateService(backendService);

        // ========== 阶段1: 安全库存 → 不应被推导为 LowStockCrust ==========
        Map<String, String> safeProperties = new LinkedHashMap<>();
        safeProperties.put(typeNS + "name", testName);
        safeProperties.put(typeNS + "type", "NeapolitanCrust");
        safeProperties.put(typeNS + "supplier", "DynamicTestSupplier");
        safeProperties.put(typeNS + "price", "10.00");
        safeProperties.put(typeNS + "stockQuantity", "50");

        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(safeProperties),
                "阶段1: 安全库存组件插入不应失败"
        );
        log.info("📝 场景9-阶段1: 写入安全库存 name={} | stock=50", testName);

        int phase1Read = readStockFromOntopEndpoint(testName, typeNS, indNS, typeNS + "stockQuantity");
        log.info("🔍 [阶段1-一致性] JDBC写入=50 | Ontop读取={} | 一致={}", phase1Read, phase1Read == 50);
        assertEquals(50, phase1Read, "阶段1一致性校验失败: Ontop 未读到写入的 stock=50");

        assertFalse(isInferredAsLowStockCrust(testName, typeNS, indNS),
                "阶段1失败: stock=50 不应被推导为 LowStockCrust");
        log.info("✅ 场景9-阶段1通过: stock=50 → 未推导出 LowStockCrust");

        // ========== 阶段2: 降低库存至阈值以下 → 应被推导为 LowStockCrust ==========
        Map<String, String> lowStockUpdate = new LinkedHashMap<>();
        lowStockUpdate.put(typeNS + "stockQuantity", "5");

        Map<String, String> identifierValues = new LinkedHashMap<>();
        identifierValues.put(typeNS + "name", testName);

        assertDoesNotThrow(
                () -> updater.updateComponentAutoSplit(identifierValues, lowStockUpdate),
                "阶段2: 更新 stockQuantity 至低库存不应失败"
        );
        log.info("🔄 场景9-阶段2: 库存已更新 name={} | stock=50→5", testName);

        int phase2Read = readStockFromOntopEndpoint(testName, typeNS, indNS, typeNS + "stockQuantity");
        log.info("🔍 [阶段2-一致性] JDBC写入=5 | Ontop读取={} | 一致={}", phase2Read, phase2Read == 5);
        assertEquals(5, phase2Read, "阶段2一致性校验失败: Ontop 未读到更新后的 stock=5");

        assertTrue(isInferredAsLowStockCrust(testName, typeNS, indNS),
                "阶段2失败: stock=5 应被推导为 LowStockCrust，SWRL 规则未响应数据变更");
        log.info("✅ 场景9-阶段2通过: stock=5 → 成功推导出 LowStockCrust");

        // ========== 阶段3: 恢复安全库存 → 应从 LowStockCrust 中移除 ==========
        Map<String, String> restoreUpdate = new LinkedHashMap<>();
        restoreUpdate.put(typeNS + "stockQuantity", "30");

        assertDoesNotThrow(
                () -> updater.updateComponentAutoSplit(identifierValues, restoreUpdate),
                "阶段3: 恢复库存不应失败"
        );
        log.info("🔄 场景9-阶段3: 库存已恢复 name={} | stock=5→30", testName);

        int phase3Read = readStockFromOntopEndpoint(testName, typeNS, indNS, typeNS + "stockQuantity");
        log.info("🔍 [阶段3-一致性] JDBC写入=30 | Ontop读取={} | 一致={}", phase3Read, phase3Read == 30);
        assertEquals(30, phase3Read,
                "阶段3一致性校验失败: Ontop 读到旧值 {}，疑似查询缓存未失效".formatted(phase3Read));

        assertFalse(isInferredAsLowStockCrust(testName, typeNS, indNS),
                "阶段3失败: stock=30 不应再被推导为 LowStockCrust，SWRL 推导未能随数据恢复而撤销");
        log.info("✅ 场景9-阶段3通过: stock=30 → LowStockCrust 推导已正确撤销");

        log.info("🧹 场景9完成: SWRL 动态响应性测试通过 个体={} 待环境清理", testName);
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

            InsertService inserter = new InsertService(backendService);
            assertDoesNotThrow(
                    () -> inserter.insertComponentAutoSplit(lowStockProperties),
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
        String targetName = "ToDeleteTest_" + System.currentTimeMillis();

        // ✅ 使用 insertComponentAutoSplit 前置插入
        Map<String, String> insertProperties = new LinkedHashMap<>();
        insertProperties.put(typeNS + "name", targetName);
        insertProperties.put(typeNS + "type", "NeapolitanCrust");
        insertProperties.put(typeNS + "supplier", "DeleteTestSupplier");
        insertProperties.put(typeNS + "price", "9.99");

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(insertProperties),
                "前置插入不应失败"
        );

        String preCheckSparql = """
                PREFIX : <http://example.org/pizza/components/classes/>
                SELECT ?s WHERE { ?s a :NeapolitanCrust ; :name "%s" }
                """.formatted(targetName);
        List<Map<String, String>> preResults = backendService.getObdaHandler().executeAboxQuery(preCheckSparql);
        assertEquals(1, preResults.size(), "前置插入后应恰好查到 1 条记录");
        log.info("📝 场景11: 前置插入验证通过 name={}", targetName);

        // ✅ 使用 deleteComponentAutoSplit 执行删除
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

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(allProperties),
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

        InsertService inserter = new InsertService(backendService);
        assertThrows(Exception.class,
                () -> inserter.insertComponentAutoSplit(allProperties),
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
        String targetName = "CrossTableTest_" + System.currentTimeMillis();

        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", targetName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "supplier", "OldSupplier");
        initProperties.put(typeNS + "price", "9.99");
        initProperties.put(typeNS + "crustThicknessMm", "5");

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(initProperties),
                "前置跨表插入不应失败"
        );
        log.info("📝 场景16: 前置跨表插入完成 name={} | price=9.99 | crustThicknessMm=5", targetName);

        Map<String, String> identifierValues = new LinkedHashMap<>();
        identifierValues.put(typeNS + "name", targetName);

        Map<String, String> updatedProperties = new LinkedHashMap<>();
        updatedProperties.put(typeNS + "type", "NeapolitanCrust");
        updatedProperties.put(typeNS + "price", "18.88");
        updatedProperties.put(typeNS + "crustThicknessMm", "12");

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
        assertEquals("12.0", row.get("thickness"),
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
        String targetName = "RollbackUpdateTest_" + System.currentTimeMillis();

        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", targetName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "price", "9.99");

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponentAutoSplit(initProperties),
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

    @Test
    @Order(18)
    @DisplayName("场景18：负面: 用insertComponent插入不满足数据库 type 限制的组件应被拒绝")
    void testInsertWithInvalidTypeShouldFailWithInsertComponent() {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String newName = "spicy_chicken_new";
        BackendService.objectPair objectPMapping = new BackendService.objectPair(newName, "name");

        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "SpicyChicken", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        InsertService inserter = new InsertService(backendService);

        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertComponent(typeNS, indNS, objectPMapping, triples, "pizza_components", "http://example.org/pizza/components/classes/PizzaComponent"),
                "插入 SpicyChicken 类型应因数据库 type 约束被拒绝"
        );

        log.info("✅ 预期异常已捕获: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage());
    }

    @Test
    @Order(19)
    @DisplayName("场景19：负面: 用insertComponent插入重复 name 的组件应违反唯一性约束")
    void testInsertDuplicateNameShouldFailWithInsertComponent() {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String newName = "NeapolitanCrustInstance";
        BackendService.objectPair objectPMapping = new BackendService.objectPair(newName, "name");

        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        InsertService inserter = new InsertService(backendService);

        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertComponent(typeNS, indNS, objectPMapping, triples, "pizza_components", "http://example.org/pizza/components/classes/PizzaComponent"),
                "插入重复 name 应违反数据库唯一性约束"
        );

        log.info("✅ 预期异常已捕获: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage());
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