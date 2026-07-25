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
    // Order(1): 基于 TBox 子类推理的 ABox 实例检索
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
                SELECT DISTINCT ?instance ?supplier WHERE {
                    %s
                    ?instance rdf:type ?cls .
                    ?instance :supplier ?supplier .
                }
                """.formatted(ns, valuesClause);

        List<Map<String, String>> rows = backendService.getObdaHandler().executeAboxQuery(sparql);

        assertNotNull(rows, "SPARQL 查询结果不应为 null");
        assertFalse(rows.isEmpty(), "应至少返回一个饼底实例及供应商");

        for (Map<String, String> row : rows) {
            assertNotNull(row.get("instance"), "instance IRI 不应为 null");
            assertNotNull(row.get("supplier"), "supplier 值不应为 null");
            assertFalse(row.get("supplier").isBlank(), "supplier 值不应为空字符串");
        }

        log.info("✅ 场景1通过: 共检索到 {} 条饼底-供应商记录", rows.size());
        if (log.isDebugEnabled()) {
            rows.forEach(r -> log.debug("  instance={} | supplier={}", r.get("instance"), r.get("supplier")));
        }
    }

    // ============================================================
    // Order(2): OWLAPI + Openllet 联合推理查询（通用框架版）
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

    @Test
    @Order(3)
    @DisplayName("负面: 插入不满足数据库 type 限制的组件应被拒绝")
    void testInsertWithInvalidTypeShouldFail() {
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
    @Order(4)
    @DisplayName("负面: 插入重复 name 的组件应违反唯一性约束")
    void testInsertDuplicateNameShouldFail() {
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

    @Test
    @Order(5)
    @DisplayName("缺少 rdf:type 时应拒绝写入")
    void testInsertWithoutTypeShouldFail() {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String newName = "test";
        BackendService.objectPair objectPMapping = new BackendService.objectPair(newName, "name");

        List<GenericAxiomBuilder.Triple> twoTriples = List.of(
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        InsertService inserter = new InsertService(backendService);
        assertThrows(IllegalArgumentException.class,
                () -> inserter.insertComponent(typeNS, indNS, objectPMapping, twoTriples, "pizza_components", "http://example.org/pizza/components/classes/PizzaComponent"),
                "缺少 rdf:type 时应拒绝写入");
        log.info("✅ 缺少 rdf:type 时正确拒绝写入");
    }

    // ============================================================
    // Order(6): 正常插入验证（适配 OntopMappingResolver 自动拆分）
    // ============================================================
    @Test
    @Order(6)
    @DisplayName("正面: 正常插入 NeapolitanCrust 实例并验证数据一致性")
    void testInsertValidPizzaComponent() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String newName = "NeapolitanCrustInstanceTest_" + System.currentTimeMillis();

        // ✅ 使用属性 Map 替代手动三元组构建，由 insertComponentAutoSplit 自动识别目标表
        Map<String, String> allProperties = new LinkedHashMap<>();
        allProperties.put(typeNS + "name", newName);
        allProperties.put(typeNS + "type", "NeapolitanCrust");
        allProperties.put(typeNS + "supplier", "SupplierX");
        allProperties.put(typeNS + "price", "12.99");

        // ✅ 单次调用自动拆分写入，无需手动指定表名和三元组列表
        assertDoesNotThrow(
                () -> backendService.getObdaHandler().insertComponentAutoSplit(allProperties),
                "合法属性写入不应抛出任何异常"
        );
        log.info("📝 自动拆分写入完成: name={} | 属性数={}", newName, allProperties.size());

        // ✅ SPARQL 端到端验证数据一致性
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

        log.info("✅ 正常插入验证通过: name={}, supplier={}, price={}", newName, supplier, priceStr);
    }
    // ============================================================
    // Order(7): 多属性更新验证（适配 OntopMappingResolver 自动拆分）
    // ============================================================
    @Test
    @Order(7)
    @DisplayName("正面: 安全更新 price/supplier/stockQuantity 并验证本体与数据库一致性")
    void testUpdateMultipleProperties() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String targetName = "MultiUpdateTest_" + System.currentTimeMillis();

        // ✅ 前置插入：使用自动拆分写入初始数据
        Map<String, String> initProperties = new LinkedHashMap<>();
        initProperties.put(typeNS + "name", targetName);
        initProperties.put(typeNS + "type", "NeapolitanCrust");
        initProperties.put(typeNS + "supplier", "OldSupplier");
        initProperties.put(typeNS + "price", "9.99");
        initProperties.put(typeNS + "stockQuantity", "100");

        assertDoesNotThrow(
                () -> backendService.getObdaHandler().insertComponentAutoSplit(initProperties),
                "前置插入不应失败"
        );
        log.info("📝 前置插入完成: name={} | supplier=OldSupplier | price=9.99 | stock=100", targetName);

        // ✅ 执行更新：构造包含所有目标属性的完整 Map，一次性覆盖写入
        // insertComponentAutoSplit 在检测到已有记录时自动转为 UPDATE 语义
        Map<String, String> updatedProperties = new LinkedHashMap<>();
        updatedProperties.put(typeNS + "name", targetName);           // 定位键，不可省略
        updatedProperties.put(typeNS + "type", "NeapolitanCrust");    // 类型键，确保路由正确
        updatedProperties.put(typeNS + "supplier", "NewSupplier");    // ← 更新字段
        updatedProperties.put(typeNS + "price", "15.50");             // ← 更新字段
        updatedProperties.put(typeNS + "stockQuantity", "42");        // ← 更新字段

        assertDoesNotThrow(
                () -> backendService.getObdaHandler().insertComponentAutoSplit(updatedProperties),
                "多属性覆盖更新不应抛出异常"
        );
        log.info("🔄 多属性覆盖更新执行完毕: supplier→NewSupplier | price→15.50 | stock→42");

        // ✅ SPARQL 端到端验证更新结果
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

        log.info("✅ 多属性更新验证通过: name={} | supplier={} | price={} | stock={}",
                targetName, row.get("supplier"), row.get("price"), row.get("stock"));
    }

    @Test
    @Order(8)
    @DisplayName("场景4: SWRL 实时推导 LowStockCrust (写入低库存 → Openllet推理 → SPARQL验证)")
    void testQueryWithLiveSwrl() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String lowStockName = "SwrlLowStockTest_" + System.currentTimeMillis();
        BackendService.objectPair objectPMapping = new BackendService.objectPair(lowStockName, "name");

        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(lowStockName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(lowStockName, "supplier", "SwrlTestSupplier", false),
                new GenericAxiomBuilder.Triple(lowStockName, "price", "5.00", false),
                new GenericAxiomBuilder.Triple(lowStockName, "stockQuantity", "8", false)
        );

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponent(typeNS, indNS, objectPMapping, triples,
                        "pizza_components", "http://example.org/pizza/components/classes/PizzaComponent"),
                "前置低库存组件插入不应失败"
        );
        log.info("📝 低库存组件已写入: name={} | stockQuantity=8", lowStockName);

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
        log.info("⚡ SWRL 增量推理完成，耗时 {}ms | ABox三元组数={}", inferTime, aboxOntology.getAxiomCount());

        String verifySparql = """
                PREFIX : <http://example.org/pizza/components/classes/>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                SELECT ?name ?stockQty WHERE {
                    ?s a :NeapolitanCrust ;
                       :name "%s" ;
                       :stockQuantity ?stockQty .
                }
                """.formatted(lowStockName);

        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);
        assertFalse(results.isEmpty(), "应能通过 SPARQL 查询到低库存组件");
        assertEquals(1, results.size(), "应恰好返回 1 条匹配记录");

        Map<String, String> row = results.get(0);
        int stockQty = Integer.parseInt(row.get("stockQty"));
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

        log.info("✅ SWRL 实时推导验证通过: name={} | stock={} → LowStockCrust | 推理耗时={}ms",
                lowStockName, stockQty, inferTime);

        reasoner.dispose();
    }

    @Test
    @Order(9)
    @DisplayName("场景5: SWRL 动态响应性 - 库存变更触发/撤销 LowStockCrust 推导")
    void testSwrlDynamicResponseOnStockChange() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String testName = "SwrlDynamicTest_" + System.currentTimeMillis();
        String stockPropIRI = typeNS + "stockQuantity";
        BackendService.objectPair objectPMapping = new BackendService.objectPair(testName, "name");

        InsertService inserter = new InsertService(backendService);
        UpdateService updater = new UpdateService(backendService);

        // ========== 阶段1: 安全库存 → 不应被推导为 LowStockCrust ==========
        List<GenericAxiomBuilder.Triple> safeTriples = List.of(
                new GenericAxiomBuilder.Triple(testName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(testName, "supplier", "DynamicTestSupplier", false),
                new GenericAxiomBuilder.Triple(testName, "price", "10.00", false),
                new GenericAxiomBuilder.Triple(testName, "stockQuantity", "50", false)
        );

        assertDoesNotThrow(
                () -> inserter.insertComponent(typeNS, indNS, objectPMapping, safeTriples,
                        "pizza_components", typeNS + "PizzaComponent"),
                "阶段1: 安全库存组件插入不应失败"
        );
        log.info("📝 阶段1: 写入安全库存 name={} | stock=50", testName);

        int phase1Read = readStockFromOntopEndpoint(testName, typeNS, indNS, stockPropIRI);
        log.info("🔍 [阶段1-一致性] JDBC写入=50 | Ontop读取={} | 一致={}", phase1Read, phase1Read == 50);
        assertEquals(50, phase1Read, "阶段1一致性校验失败: Ontop 未读到写入的 stock=50");

        assertFalse(isInferredAsLowStockCrust(testName, typeNS, indNS),
                "阶段1失败: stock=50 不应被推导为 LowStockCrust");
        log.info("✅ 阶段1通过: stock=50 → 未推导出 LowStockCrust");

        // ========== 阶段2: 降低库存至阈值以下 → 应被推导为 LowStockCrust ==========
        assertDoesNotThrow(
                () -> updater.updateIndividual(typeNS, indNS, objectPMapping,
                        stockPropIRI, "5",
                        "pizza_components", typeNS + "PizzaComponent"),
                "阶段2: 更新 stockQuantity 不应失败"
        );
        log.info("🔄 阶段2: 库存已更新 name={} | stock=50→5", testName);

        int phase2Read = readStockFromOntopEndpoint(testName, typeNS, indNS, stockPropIRI);
        log.info("🔍 [阶段2-一致性] JDBC写入=5 | Ontop读取={} | 一致={}", phase2Read, phase2Read == 5);
        assertEquals(5, phase2Read, "阶段2一致性校验失败: Ontop 未读到更新后的 stock=5");

        assertTrue(isInferredAsLowStockCrust(testName, typeNS, indNS),
                "阶段2失败: stock=5 应被推导为 LowStockCrust，SWRL 规则未响应数据变更");
        log.info("✅ 阶段2通过: stock=5 → 成功推导出 LowStockCrust");

        // ========== 阶段3: 恢复安全库存 → 应从 LowStockCrust 中移除 ==========
        assertDoesNotThrow(
                () -> updater.updateIndividual(typeNS, indNS, objectPMapping,
                        stockPropIRI, "30",
                        "pizza_components", typeNS + "PizzaComponent"),
                "阶段3: 恢复库存不应失败"
        );
        log.info("🔄 阶段3: 库存已恢复 name={} | stock=5→30", testName);

        int phase3Read = readStockFromOntopEndpoint(testName, typeNS, indNS, stockPropIRI);
        log.info("🔍 [阶段3-一致性] JDBC写入=30 | Ontop读取={} | 一致={}", phase3Read, phase3Read == 30);
        assertEquals(30, phase3Read,
                "阶段3一致性校验失败: Ontop 读到旧值 {}，疑似查询缓存未失效".formatted(phase3Read));

        assertFalse(isInferredAsLowStockCrust(testName, typeNS, indNS),
                "阶段3失败: stock=30 不应再被推导为 LowStockCrust，SWRL 推导未能随数据恢复而撤销");
        log.info("✅ 阶段3通过: stock=30 → LowStockCrust 推导已正确撤销");

        log.info("🧹 阶段4: SWRL 动态响应性测试完成，测试个体 {} 待环境清理", testName);
    }

    @Test
    @Order(10)
    @DisplayName("场景6: SwrlRuleTriggerListener 通用框架 - 写入低库存自动触发异步回调")
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
            log.info("🚀 SwrlRuleTriggerListener 已启动，监控目标: {}", targetClassIri);

            String testName = "ListenerTriggerTest_" + System.currentTimeMillis();
            BackendService.objectPair objectPMapping = new BackendService.objectPair(testName, "name");

            List<GenericAxiomBuilder.Triple> triples = List.of(
                    new GenericAxiomBuilder.Triple(testName, "rdf:type", "NeapolitanCrust", false),
                    new GenericAxiomBuilder.Triple(testName, "supplier", "ListenerTestSupplier", false),
                    new GenericAxiomBuilder.Triple(testName, "price", "8.00", false),
                    new GenericAxiomBuilder.Triple(testName, "stockQuantity", "3", false)
            );

            InsertService inserter = new InsertService(backendService);
            assertDoesNotThrow(
                    () -> inserter.insertComponent(typeNS, indNS, objectPMapping, triples,
                            "pizza_components", typeNS + "PizzaComponent"),
                    "低库存组件插入不应失败"
            );
            log.info("📝 低库存组件已写入: name={} | stock=3", testName);

            boolean callbackExecuted = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);

            assertTrue(callbackExecuted,
                    "SwrlRuleTriggerListener 应在 10 秒内检测到 LowStockCrust 推导并触发回调");
            assertFalse(triggeredInstances.isEmpty(), "回调结果列表不应为空");
            assertTrue(triggeredInstances.stream().anyMatch(iri -> iri.contains(testName)),
                    "回调参数应包含刚写入的个体 IRI: " + testName);

            log.info("✅ SwrlRuleTriggerListener 集成测试通过: 捕获到 {} 次回调, 实例={}",
                    triggeredInstances.size(), triggeredInstances);

        } finally {
            listener.shutdown();
            log.info("🧹 SwrlRuleTriggerListener 已关闭");
        }
    }

    @Test
    @Order(11)
    @DisplayName("正面: 删除已存在的组件应成功且后续 SPARQL 查询返回空")
    void testDeleteExistingComponent() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetName = "ToDeleteTest_" + System.currentTimeMillis();
        BackendService.objectPair objectPMapping = new BackendService.objectPair(targetName, "name");

        List<GenericAxiomBuilder.Triple> insertTriples = List.of(
                new GenericAxiomBuilder.Triple(targetName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(targetName, "supplier", "DeleteTestSupplier", true),
                new GenericAxiomBuilder.Triple(targetName, "price", "9.99", true)
        );

        InsertService inserter = new InsertService(backendService);

        assertDoesNotThrow(
                () -> inserter.insertComponent(typeNS, indNS, objectPMapping, insertTriples,
                        "pizza_components", typeNS + "PizzaComponent"),
                "前置插入不应失败"
        );

        String preCheckSparql = """
                PREFIX : <http://example.org/pizza/components/classes/>
                SELECT ?s WHERE { ?s a :NeapolitanCrust ; :name "%s" }
                """.formatted(targetName);
        List<Map<String, String>> preResults = backendService.getObdaHandler().executeAboxQuery(preCheckSparql);
        assertEquals(1, preResults.size(), "前置插入后应恰好查到 1 条记录");
        log.info("📝 前置插入验证通过: name={}", targetName);

        DeleteService deleter = new DeleteService(backendService);
        List<GenericAxiomBuilder.Triple> deleteTriples = List.of(
                new GenericAxiomBuilder.Triple(targetName, "rdf:type", "NeapolitanCrust", false)
        );

        assertDoesNotThrow(
                () -> deleter.deleteComponent(typeNS, indNS, objectPMapping, deleteTriples,
                        "pizza_components", typeNS + "PizzaComponent"),
                "删除已存在组件不应抛出异常"
        );

        List<Map<String, String>> postResults = backendService.getObdaHandler().executeAboxQuery(preCheckSparql);
        assertTrue(postResults.isEmpty(),
                "删除后不应再通过 SPARQL 查询到该组件 | name=" + targetName);

        log.info("✅ 删除已存在组件验证通过: name={} 已从 pizza_components 中移除", targetName);
    }

    @Test
    @Order(12)
    @DisplayName("正面: 删除不存在的个体不应抛异常（幂等性验证）")
    void testDeleteNonExistentIsIdempotent() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String nonExistentName = "NonExistent_" + System.currentTimeMillis();
        BackendService.objectPair objectPMapping = new BackendService.objectPair(nonExistentName, "name");

        List<GenericAxiomBuilder.Triple> deleteTriples = List.of(
                new GenericAxiomBuilder.Triple(nonExistentName, "rdf:type", "NeapolitanCrust", false)
        );

        DeleteService deleter = new DeleteService(backendService);
        assertDoesNotThrow(
                () -> deleter.deleteComponent(typeNS, indNS, objectPMapping, deleteTriples,
                        "pizza_components", typeNS + "PizzaComponent"),
                "删除不存在的个体应保持幂等，不应抛出异常"
        );

        String verifySparql = """
                PREFIX : <http://example.org/pizza/components/classes/>
                SELECT ?s WHERE { ?s a :NeapolitanCrust ; :name "%s" }
                """.formatted(nonExistentName);
        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);
        assertTrue(results.isEmpty(),
                "不存在的个体在删除前后都不应被查到 | name=" + nonExistentName);

        log.info("✅ 幂等性验证通过: 删除不存在个体 name={} 未抛异常且数据库状态无变化", nonExistentName);
    }

    // ============================================================
    // Order(13): 多表写入验证（适配 OntopMappingResolver）
    // ============================================================
    @Test
    @Order(13)
    @DisplayName("场景7: 多表自动拆分写入验证 - 单调用事务原子性")
    void testMultiTableWriteAndQuery() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String testName = "MultiTblAuto_" + System.currentTimeMillis();

        // ✅ 构造一条完整记录：包含 Base 表和 Join 表的所有属性
        // insertComponentAutoSplit 应根据 MAPPING_CACHE 自动识别哪些列属于哪张表
        Map<String, String> allProperties = new LinkedHashMap<>();
        // Base 表 (pizza_components) 属性
        allProperties.put(typeNS + "name", testName);
        allProperties.put(typeNS + "type", "NeapolitanCrust");
        allProperties.put(typeNS + "price", "22.50");
        allProperties.put(typeNS + "supplier", "FlourSupplierA");
        allProperties.put(typeNS + "stockQuantity", "60");
        // Join 表 (crust_component) 属性
        allProperties.put(typeNS + "crustThicknessMm", "3.5");
        allProperties.put(typeNS + "bakingTemperatureCelsius", "450");
        allProperties.put(typeNS + "flourType", "Tipo00");

        // ✅ 单次调用：方法内部自动按表拆分 + 事务包裹
        assertDoesNotThrow(
                () -> backendService.getObdaHandler().insertComponentAutoSplit(allProperties),
                "多表自动拆分写入不应抛出异常"
        );
        log.info("📝 单调用多表写入完成: name={} | 总属性={}", testName, allProperties.size());

        // ✅ 验证正向映射完整性
        Map<String, Set<String>> mappingCache = backendService.getObdaHandler().getAllMappedPropertiesWithVariables();
        long resolvedCount = allProperties.keySet().stream()
                .filter(iri -> mappingCache.containsKey(iri) && !mappingCache.get(iri).isEmpty())
                .count();
        assertEquals(allProperties.size(), resolvedCount,
                "所有 %d 个属性均应在 OntopMappingResolver 缓存中找到映射".formatted(allProperties.size()));
        log.info("🔍 正向映射验证: {}/{} 个属性已成功解析", resolvedCount, allProperties.size());

        // ⭐ 端到端 SPARQL 聚合验证（间接验证多表 JOIN 正确性 + 事务一致性）
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

        log.info("✅ 场景7通过: 单调用多表自动拆分+事务原子性+查询闭环验证成功 | name={}", testName);
    }

    // ============================================================
    // Order(14): 正向映射完整性验证（适配 OntopMappingResolver）
    // ============================================================
    @Test
    @Order(14)
    @DisplayName("场景8: 正向映射完整性验证 - OntopMappingResolver 缓存校验")
    void testForwardMappingCompleteness() {
        String typeNS = "http://example.org/pizza/components/classes/";

        // ✅ OntopMappingResolver 仅提供 属性IRI → SQL变量 正向映射
        // 不再支持 表→谓词 反向索引（因纯文本解析不含表名元数据）
        Map<String, Set<String>> mappingCache = backendService.getObdaHandler().getAllMappedPropertiesWithVariables();
        assertNotNull(mappingCache, "映射缓存不应为 null");
        assertFalse(mappingCache.isEmpty(), "映射缓存不应为空");

        // ⭐ 1. 验证关键属性均已解析
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
        log.info("📋 正向映射验证: 共 {} 个属性已解析 | 缓存总条目={}", expectedProperties.size(), mappingCache.size());

        // ⭐ 2. 验证不可变保护
        assertThrows(UnsupportedOperationException.class,
                () -> mappingCache.put("test", Set.of("col")),
                "映射缓存应为只读视图");
        log.info("🔒 映射缓存只读保护验证通过");

        // ⭐ 3. 验证未映射属性返回 null
        assertNull(mappingCache.get("http://nonexistent.org/property"),
                "未映射属性应返回 null");
        log.info("✅ 场景8通过: 正向映射完整性验证完成 | 缓存条目数={}", mappingCache.size());
    }
    // ============================================================
    // Order(15): 多表写入事务回滚验证（适配 OntopMappingResolver）
    // ============================================================
    @Test
    @Order(15)
    @DisplayName("场景8: 多表自动拆分写入事务回滚 - 第二表失败时首表同步回退")
    void testMultiTableWriteTransactionRollback() throws Exception {
        String typeNS = "http://example.org/pizza/components/classes/";
        String testName = "RollbackTest_" + System.currentTimeMillis();

        // ✅ 构造一条包含 Base 表合法属性 + Join 表非法属性的记录
        // crustThicknessMm 设为超出数据库列约束的值（如负数或超长字符串），强制触发第二张表写入失败
        Map<String, String> allProperties = new LinkedHashMap<>();
        // Base 表 (pizza_components) - 完全合法，若无事务保护则会持久化
        allProperties.put(typeNS + "name", testName);
        allProperties.put(typeNS + "type", "NeapolitanCrust");
        allProperties.put(typeNS + "price", "18.00");
        allProperties.put(typeNS + "supplier", "FlourSupplierB");
        allProperties.put(typeNS + "stockQuantity", "30");
        // Join 表 (crust_component) - 故意制造约束违反以触发异常
        allProperties.put(typeNS + "crustThicknessMm", "-999.99");       // 假设 DB 有 CHECK(thickness > 0)
        allProperties.put(typeNS + "bakingTemperatureCelsius", "450");
        allProperties.put(typeNS + "flourType", "Tipo00");

        // ✅ 验证写入确实因第二张表失败而抛出异常
        assertThrows(Exception.class,
                () -> backendService.getObdaHandler().insertComponentAutoSplit(allProperties),
                "Join 表约束违反应导致 insertComponentAutoSplit 抛出异常"
        );
        log.info("⚠️ 预期异常已捕获: name={} | 第二表写入失败触发事务回滚", testName);

        // ✅ 核心断言：Base 表数据必须已被回滚，数据库中不应存在该记录
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
        log.info("✅ 场景8通过: 第二表写入失败 → 第一表 INSERT 已完整回滚 | name={}", testName);
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