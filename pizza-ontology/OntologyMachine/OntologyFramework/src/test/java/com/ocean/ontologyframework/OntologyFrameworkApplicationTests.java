package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    @Test
    @Order(1)
    @DisplayName("场景1: 基于 TBox 子类推理的 ABox 实例检索")
    void testQueryWithInferredSubclasses() {
        PizzaQueryService pizzaQuery = new PizzaQueryService(backendService);
        List<?> instAndSuppliers = pizzaQuery.getCrustInstancesAndSuppliers();

        assertNotNull(instAndSuppliers, "查询结果不应为 null");
        assertFalse(instAndSuppliers.isEmpty(), "应至少返回一个饼底实例及供应商");

        if (log.isInfoEnabled()) {
            log.info("共检索到 {} 条饼底-供应商记录", instAndSuppliers.size());
            instAndSuppliers.forEach(line -> log.info("  {}", line));
        }
    }

    @Test
    @Order(2)
    @DisplayName("场景2: OWLAPI + Openllet 联合推理查询")
    void testQueryWithInferredProperties() throws OWLOntologyCreationException {
        String aboxSparql = """
                PREFIX : <http://example.org/pizza/components/classes/>
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
                """;

        OWLOntology aboxOntology = backendService.getObdaHandler()
                .loadAboxFromOntop(aboxSparql, backendService.getOntologyService().gettBoxOntology());
        assertNotNull(aboxOntology, "ABox 本体加载结果不应为 null");
        assertFalse(aboxOntology.isEmpty(), "ABox 本体不应为空，请检查 Ontop 端点与 SPARQL");

        PizzaQueryService pizzaQuery = new PizzaQueryService(backendService);
        // 若 queryPizzaComponentTypes 内部有断言逻辑，此处可改为返回值校验
        assertDoesNotThrow(() ->
                        pizzaQuery.queryPizzaComponentTypes(
                                backendService.getOntologyService().gettBoxOntology(), aboxOntology),
                "联合推理查询不应抛出异常"
        );
    }

    @Test
    @Order(3)
    @DisplayName("负面: 插入不满足数据库 type 限制的组件应被拒绝")
    void testInsertWithInvalidTypeShouldFail() {
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String newName = "spicy_chicken_new";
        BackendService.objectPair objectPMapping = new BackendService.objectPair(newName,"name");

        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "SpicyChicken", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        InsertService inserter = new InsertService(backendService);

        // ⚠️ TODO: 将 Exception.class 替换为实际抛出的异常类型
        // 例如: DataAccessException / IllegalArgumentException / OBDAException 等
        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertComponent(typeNS,indNS, objectPMapping, triples, "pizza_components","http://example.org/pizza/components/classes/PizzaComponent"),
                "插入 SpicyChicken 类型应因数据库 type 约束被拒绝"
        );

        log.info("✅ 预期异常已捕获: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("负面: 插入重复 name 的组件应违反唯一性约束")
    void testInsertDuplicateNameShouldFail() {
        // ⚠️ 前置条件: "NeapolitanCrustInstance" 必须已存在于数据库中
        // 若测试环境每次重建，需先执行一次正常插入作为 setup
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String newName = "NeapolitanCrustInstance";
        BackendService.objectPair objectPMapping = new BackendService.objectPair(newName,"name");

        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        InsertService inserter = new InsertService(backendService);

        // ⚠️ TODO: 将 Exception.class 替换为实际的唯一性约束异常
        // 例如: DuplicateKeyException / DataIntegrityViolationException 等
        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertComponent(typeNS,indNS, objectPMapping, triples, "pizza_components","http://example.org/pizza/components/classes/PizzaComponent"),
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
        BackendService.objectPair objectPMapping = new BackendService.objectPair(newName,"name");

        List<GenericAxiomBuilder.Triple> twoTriples = List.of(
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
        new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        InsertService inserter = new InsertService(backendService);
        assertThrows(IllegalArgumentException.class,
                () -> inserter.insertComponent(typeNS,indNS, objectPMapping, twoTriples, "pizza_components","http://example.org/pizza/components/classes/PizzaComponent"),
                "缺少 rdf:type 时应拒绝写入");
        log.info("缺少 rdf:type 时应拒绝写入");
    }

    @Test
    @Order(6)
    @DisplayName("正面: 正常插入 NeapolitanCrust 实例并验证数据一致性")
    void testInsertValidPizzaComponent() throws Exception {
        // ⭐ 1. 准备测试数据
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String newName = "NeapolitanCrustInstanceTest_" + System.currentTimeMillis();
        BackendService.objectPair objectPMapping = new BackendService.objectPair(newName,"name");

        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        InsertService inserter = new InsertService(backendService);

        // ⭐ 2. 验证写入过程无异常（补全DI参数）
        assertDoesNotThrow(
                () -> inserter.insertComponent(typeNS,indNS, objectPMapping, triples, "pizza_components","http://example.org/pizza/components/classes/PizzaComponent"),
                "合法三元组写入不应抛出任何异常"
        );

        // ⭐ 3. 写入后语义层验证（确认 Ontop 能查到新数据）
        String verifySparql = """
            PREFIX : <http://example.org/pizza/components/classes/>
            SELECT ?supplier ?price WHERE {
                ?s a :NeapolitanCrust ;
                   :name "%s" ;
                   :supplier ?supplier ;
                   :price ?price .
            }
            """.formatted(newName);

        // 接收类型改为 Map，适配 executeAboxQuery 的实际返回值
        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);

        assertFalse(results.isEmpty(), "写入后应能通过 SPARQL 查询到新插入的组件");
        assertEquals(1, results.size(), "应恰好返回 1 条匹配记录");

        // 使用 Map API 替代 QuerySolution API
        Map<String, String> row = results.get(0);
        String supplier = row.get("supplier");
        String priceStr = row.get("price");

        assertNotNull(supplier, "supplier 字段不应为 null");
        assertNotNull(priceStr, "price 字段不应为 null");
        assertEquals("SupplierX", supplier, "supplier 值应与写入一致");
        assertEquals(new BigDecimal("12.99"), new BigDecimal(priceStr), "price 值应与写入一致");

        log.info("✅ 正常插入验证通过: name={}, supplier={}, price={}",
                newName, supplier, priceStr);
    }

    @Test
    @Order(7)
    @DisplayName("正面: 安全更新 price/supplier/stockQuantity 并验证本体与数据库一致性")
    void testUpdateMultipleProperties() throws Exception {
        // ⭐ 1. 前置条件：插入包含三个待更新属性的已知个体
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetName = "MultiUpdateTest_" + System.currentTimeMillis();
        BackendService.objectPair objectPMapping = new BackendService.objectPair(targetName,"name");

        List<GenericAxiomBuilder.Triple> initTriples = List.of(
                new GenericAxiomBuilder.Triple(targetName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(targetName, "supplier", "OldSupplier", false),
                new GenericAxiomBuilder.Triple(targetName, "price", "9.99", false),
                new GenericAxiomBuilder.Triple(targetName, "stockQuantity", "100", false)
        );

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponent(typeNS,indNS, objectPMapping, initTriples, "pizza_components","http://example.org/pizza/components/classes/PizzaComponent"),
                "前置插入不应失败"
        );
        log.info("📝 前置插入完成: name={} | supplier=OldSupplier | price=9.99 | stock=100", targetName);

        // ⭐ 2. 依次更新三个属性
        UpdateService updater = new UpdateService(backendService);
        String ns = "http://example.org/pizza/components/classes/";
        String target_ns = "http://example.org/pizza/components/individuals/";


        assertDoesNotThrow(
                () -> updater.updateIndividual(typeNS, indNS, objectPMapping, "http://example.org/pizza/components/classes/supplier","NewSupplier","pizza_components","http://example.org/pizza/components/classes/PizzaComponent"),
                "更新 supplier 不应抛出异常"
        );
        assertDoesNotThrow(
                () -> updater.updateIndividual(typeNS, indNS, objectPMapping, "http://example.org/pizza/components/classes/price", "15.50","pizza_components","http://example.org/pizza/components/classes/PizzaComponent"),
                "更新 price 不应抛出异常"
        );
        assertDoesNotThrow(
                () -> updater.updateIndividual(typeNS, indNS, objectPMapping,"http://example.org/pizza/components/classes/stockQuantity", "42","pizza_components","http://example.org/pizza/components/classes/PizzaComponent"),
                "更新 stockQuantity 不应抛出异常"
        );
        log.info("🔄 三次属性更新执行完毕");

        // ⭐ 3. 通过 Ontop SPARQL 一次性查询验证所有字段
        String verifySparql = """
            PREFIX : <%s>
            SELECT ?supplier ?price ?stock WHERE {
                ?s a :NeapolitanCrust ;
                   :name "%s" ;
                   :supplier ?supplier ;
                   :price ?price ;
                   :stockQuantity ?stock .
            }
            """.formatted(ns, targetName);

        List<Map<String, String>> results = backendService.getObdaHandler().executeAboxQuery(verifySparql);

        assertFalse(results.isEmpty(), "更新后应能通过 SPARQL 查询到该组件");
        assertEquals(1, results.size(), "应恰好返回 1 条匹配记录");

        Map<String, String> row = results.get(0);

        // ⭐ 4. 分字段断言：类型转换 + 值正确性
        assertEquals("NewSupplier", row.get("supplier"),
                "supplier 应被更新为 NewSupplier");
        assertEquals(new BigDecimal("15.50"), new BigDecimal(row.get("price")),
                "price 应从 9.99 更新为 15.50（数值精度一致）");
        assertEquals(42, Integer.parseInt(row.get("stock")),
                "stockQuantity 应从 100 更新为 42（整数类型正确）");

        log.info("✅ 多属性更新验证通过: name={} | supplier={} | price={} | stock={}",
                targetName, row.get("supplier"), row.get("price"), row.get("stock"));
    }
    @Test
    @Order(8)
    @DisplayName("场景4: SWRL 实时推导 LowStockCrust (写入低库存 → Openllet推理 → SPARQL验证)")
    void testQueryWithLiveSwrl() throws Exception {
        // ⭐ 1. 准备测试数据：插入一个库存低于阈值(20)的 NeapolitanCrust
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String lowStockName = "SwrlLowStockTest_" + System.currentTimeMillis();
        BackendService.objectPair objectPMapping = new BackendService.objectPair(lowStockName, "name");

        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(lowStockName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(lowStockName, "supplier", "SwrlTestSupplier", false),
                new GenericAxiomBuilder.Triple(lowStockName, "price", "5.00", false),
                new GenericAxiomBuilder.Triple(lowStockName, "stockQuantity", "8", false) // < 20, 应触发规则
        );

        InsertService inserter = new InsertService(backendService);
        assertDoesNotThrow(
                () -> inserter.insertComponent(typeNS, indNS, objectPMapping, triples,
                        "pizza_components", "http://example.org/pizza/components/classes/PizzaComponent"),
                "前置低库存组件插入不应失败"
        );
        log.info("📝 低库存组件已写入: name={} | stockQuantity=8", lowStockName);

        // ⭐ 2. 触发 Openllet SWRL 推理（通过 BackendService 加载 ABox 并推理）
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
        org.semanticweb.owlapi.model.OWLOntology aboxOntology = backendService.getObdaHandler()
                .loadAboxFromOntop(aboxSparql, backendService.getOntologyService().gettBoxOntology());
        assertNotNull(aboxOntology, "ABox 本体加载结果不应为 null");

        // 使用 Openllet 进行 SWRL 推理
        openllet.owlapi.OpenlletReasoner reasoner = openllet.owlapi.OpenlletReasonerFactory.getInstance()
                .createReasoner(aboxOntology);
        reasoner.flush();
        long inferTime = System.currentTimeMillis() - start;
        log.info("⚡ SWRL 增量推理完成，耗时 {}ms | ABox三元组数={}", inferTime, aboxOntology.getAxiomCount());

        // ⭐ 3. 通过 Ontop SPARQL 查询验证 LowStockCrust 推导结果
        //     注意：SWRL 推导出的类型需通过 Ontop 映射或直接在推理后的本体中查询
        //     此处使用 Ontop 端点查询原始数据 + 推理类型联合验证
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

        // ⭐ 4. 验证 Openllet 推理机确实推导出了 LowStockCrust 类型
        org.semanticweb.owlapi.model.OWLDataFactory df = aboxOntology.getOWLOntologyManager().getOWLDataFactory();
        org.semanticweb.owlapi.model.IRI individualIri = org.semanticweb.owlapi.model.IRI.create(indNS + lowStockName);
        org.semanticweb.owlapi.model.OWLNamedIndividual ind = df.getOWLNamedIndividual(individualIri);
        org.semanticweb.owlapi.model.OWLClass lowStockCrust = df.getOWLClass(
                org.semanticweb.owlapi.model.IRI.create(typeNS + "LowStockCrust"));

        boolean isInferred = reasoner.getTypes(ind, false).containsEntity(lowStockCrust);
        assertTrue(isInferred,
                "Openllet 应将 %s(stock=%d) 推导为 LowStockCrust，若失败请检查 SWRL 规则文件是否已导入 TBox"
                        .formatted(lowStockName, stockQty));

        log.info("✅ SWRL 实时推导验证通过: name={} | stock={} → LowStockCrust | 推理耗时={}ms",
                lowStockName, stockQty, inferTime);

        // ⭐ 5. 清理推理资源
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

        // 【一致性校验】通过 Ontop Endpoint 直接读取，绕过内存本体签名检查
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

        // 【关键一致性校验】← 排查缓存问题的核心断言
        int phase3Read = readStockFromOntopEndpoint(testName, typeNS, indNS, stockPropIRI);
        log.info("🔍 [阶段3-一致性] JDBC写入=30 | Ontop读取={} | 一致={}", phase3Read, phase3Read == 30);
        assertEquals(30, phase3Read,
                "阶段3一致性校验失败: Ontop 读到旧值 {}，疑似查询缓存未失效".formatted(phase3Read));

        assertFalse(isInferredAsLowStockCrust(testName, typeNS, indNS),
                "阶段3失败: stock=30 不应再被推导为 LowStockCrust，SWRL 推导未能随数据恢复而撤销");
        log.info("✅ 阶段3通过: stock=30 → LowStockCrust 推导已正确撤销");

        // ========== 阶段4: 清理 ==========
        log.info("🧹 阶段4: SWRL 动态响应性测试完成，测试个体 {} 待环境清理", testName);
    }

    /**
     * 通过 Ontop Endpoint 读取库存值，用于一致性校验
     * 委托给 BackendService.extractDataPropertyIntValue 实现安全提取
     */
    private int readStockFromOntopEndpoint(String individualName, String typeNS,
                                           String indNS, String propertyIRI) {
        Set<OWLAxiom> axioms = backendService.queryPropertyAxiom(
                typeNS, indNS, individualName, propertyIRI);
        return extractDataPropertyIntValue(
                axioms, indNS, individualName, propertyIRI);
    }

    /**
     * @param axioms         queryPropertyAxiom 返回的公理集合（可为 null）
     * @param indNS          个体命名空间
     * @param individualName 个体本地名
     * @param propertyIRI    数据属性完整 IRI
     * @return 解析后的整数值，未找到或解析失败返回 -1
     */
    public int extractDataPropertyIntValue(Set<OWLAxiom> axioms, String indNS,
                                           String individualName, String propertyIRI) {
        if (axioms == null || axioms.isEmpty()) {
            log.warn("⚠️ [Extract] 公理集合为空 | individual={} | property={}", individualName, propertyIRI);
            return -1;
        }

        // 1. 获取属性对象（带异常兜底）
        OWLDataProperty prop;
        try {
            prop = backendService.getDataProperty(propertyIRI);
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ [Extract] 数据属性未找到: {}", e.getMessage());
            return -1;
        }

        IRI indIRI = IRI.create(indNS + individualName);
        log.debug("🔍 [Extract] 开始提取 | individual={} | property={} | 候选公理数={}",
                individualName, propertyIRI, axioms.size());

        // 2. 显式遍历，替代 Stream 链
        for (OWLAxiom ax : axioms) {
            // 2.1 类型检查
            if (!(ax instanceof OWLDataPropertyAssertionAxiom dpAx)) {
                log.trace("  ⏭️ 跳过非数据属性断言: {}", ax.getClass().getSimpleName());
                continue;
            }

            // 2.2 属性匹配
            if (!dpAx.getProperty().asOWLDataProperty().equals(prop)) {
                log.trace("  ⏭️ 属性不匹配: expected={}, actual={}",
                        prop.getIRI(), dpAx.getProperty());
                continue;
            }

            // 2.3 个体匹配
            if (!dpAx.getSubject().asOWLNamedIndividual().getIRI().equals(indIRI)) {
                log.trace("  ⏭️ 个体不匹配: expected={}, actual={}",
                        indIRI, dpAx.getSubject());
                continue;
            }

            // 2.4 字面量解析
            OWLLiteral literal = dpAx.getObject();
            log.debug("  🎯 匹配成功! 原始字面量: {} | datatype: {}",
                    literal.getLiteral(), literal.getDatatype());

            Number parsed = backendService.parseNumeric(literal);
            if (parsed == null) {
                log.warn("  ❌ parseNumeric 返回 null | 字面量: {} | datatype: {}",
                        literal.getLiteral(), literal.getDatatype());
                continue;
            }

            int result = parsed.intValue();
            log.info("  ✅ 提取成功 | individual={} | property={} | value={}",
                    individualName, propertyIRI, result);
            return result;
        }

        log.warn("❌ [Extract] 未找到匹配的数据属性断言 | individual={} | property={} | 已扫描{}条公理",
                individualName, propertyIRI, axioms.size());
        return -1;
    }

    /**
     * 辅助方法: 从 Ontop 加载最新 ABox → Openllet 推理 → 检查是否被推导为 LowStockCrust
     * 每次调用都会重新拉取数据并创建新的推理机，确保反映数据库最新状态
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

        org.semanticweb.owlapi.model.OWLOntology aboxOntology = backendService.getObdaHandler()
                .loadAboxFromOntop(aboxSparql, backendService.getOntologyService().gettBoxOntology());

        openllet.owlapi.OpenlletReasoner reasoner = null;
        try {
            reasoner = openllet.owlapi.OpenlletReasonerFactory.getInstance().createReasoner(aboxOntology);
            reasoner.flush();

            org.semanticweb.owlapi.model.OWLDataFactory df = aboxOntology.getOWLOntologyManager().getOWLDataFactory();
            org.semanticweb.owlapi.model.IRI individualIri = org.semanticweb.owlapi.model.IRI.create(indNS + individualName);
            org.semanticweb.owlapi.model.OWLNamedIndividual ind = df.getOWLNamedIndividual(individualIri);
            org.semanticweb.owlapi.model.OWLClass lowStockCrust = df.getOWLClass(
                    org.semanticweb.owlapi.model.IRI.create(typeNS + "LowStockCrust"));
            // ========== 新增：打印 LowStockCrust 包含的所有个体 ==========
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

    @Test
    @Order(10)
    @DisplayName("场景6: SwrlRuleTriggerListener 通用框架 - 写入低库存自动触发异步回调")
    void testSwrlRuleTriggerListenerCallback() throws Exception {
        // ⭐ 1. 准备线程安全的回调结果收集器
        List<String> triggeredInstances = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        // ⭐ 2. 配置通用监听器（Pizza 业务逻辑仅出现在此处）
        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetClassIri = typeNS + "LowStockCrust";

        // ✅ 修复1: 移除 TBOX_FILE（新Config不再需要本体路径）
        // ✅ 修复2: 注入 backendService 复用全局 Manager 和 Reasoner
        // ✅ 修复3: 修复中文全角引号 ” → 英文半角 "
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
                backendService  // ← 必须注入 BackendService
        );

        try {
            // ⭐ 3. 启动监听器
            listener.start();
            log.info("🚀 SwrlRuleTriggerListener 已启动，监控目标: {}", targetClassIri);

            // ⭐ 4. 写入低库存数据以触发 SWRL 规则
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

            // ⭐ 5. 等待异步回调执行
            boolean callbackExecuted = latch.await(10, java.util.concurrent.TimeUnit.SECONDS);

            // ⭐ 6. 断言验证
            assertTrue(callbackExecuted,
                    "SwrlRuleTriggerListener 应在 10 秒内检测到 LowStockCrust 推导并触发回调");
            assertFalse(triggeredInstances.isEmpty(), "回调结果列表不应为空");
            assertTrue(triggeredInstances.stream().anyMatch(iri -> iri.contains(testName)),
                    "回调参数应包含刚写入的个体 IRI: " + testName);

            log.info("✅ SwrlRuleTriggerListener 集成测试通过: 捕获到 {} 次回调, 实例={}",
                    triggeredInstances.size(), triggeredInstances);

        } finally {
            // ⭐ 7. 清理监听器资源
            listener.shutdown();
            log.info("🧹 SwrlRuleTriggerListener 已关闭");
        }
    }
}