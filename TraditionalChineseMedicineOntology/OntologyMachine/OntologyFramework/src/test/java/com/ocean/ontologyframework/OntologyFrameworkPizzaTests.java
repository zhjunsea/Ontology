package com.ocean.ontologyframework;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.ontologyframework.pizza.PizzaOntologyValidator;
import com.ocean.ontologyframework.pizza.ValidationResult;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.*;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.ChangeApplied;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("PizzaJunitTest")
@DisplayName("TBox/ABox 分离架构集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OntologyFrameworkPizzaTests {

    private static final Logger log = LoggerFactory.getLogger(OntologyFrameworkPizzaTests.class);

    private static final String ONTOP_ABOX_ENDPOINT = "http://localhost:8080/sparql";

    @Value("${ontology.main-path}")
    private static String TBOX_FILE;

    // ✅ 改为实例字段注入（更可靠）
    @Value("${ontology.obda-path}")
    private static String obdaPath;

    @Value("${ontology.obda-properties-path}")
    private static String obdaPropertiesPath;

    @Autowired
    private static Environment env;

    private static BackendService backendService;

    private static int expectedAxiomNum = 0;
    /** 保存测试开始前的 TBox 公理快照 */
    private Set<OWLAxiom> tboxBaselineSnapshot;
    private OWLReasoner baselineReasoner;

    @BeforeAll
    static void setUp(@Autowired Environment env) throws Exception {
        log.info("=== 初始化 TBox/ABox 分离架构测试环境 ===");
        String obdaPath = env.getProperty("ontology.obda-path");
        String TBOX_FILE = env.getProperty("ontology.main-path");
        String obdaPropertiesPath = env.getProperty("ontology.obda-properties-path");
        OBDAHandler.init(obdaPropertiesPath,obdaPath);
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
    void testSwrlRuleTriggerListenerCallbackWithRabbitMQ() throws Exception {
        List<String> triggeredInstances = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

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

            boolean callbackExecuted = latch.await(10, TimeUnit.SECONDS);

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
    @DisplayName("场景14: 正向OBDA映射完整性验证 - OntopMappingResolver 缓存校验")
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
        assertEquals("5", row.get("thickness"),
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

    @Test
    @Order(19)
    @DisplayName("场景19: SwrlRuleTriggerListener 通用框架，不指定Queue - 低库存自动触发RabbitMQ消息")
    void testSwrlRuleTriggerListenerCallback() throws Exception {
        RabbitMqHandler mqHandler = new RabbitMqHandler();
        // ✅ 复用 Handler 内部的 ObjectMapper，保证发送与断言序列化行为一致
        ObjectMapper mapper = mqHandler.getObjectMapper();

        String exchangeName = "pizza.low-stock.exchange";
        String routingKey = "low.stock.alert";

        Map<String, Map<String, String>> iriToPropsCache = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMsgRef = new AtomicReference<>();

        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetClassIri = typeNS + "LowStockCrust";

        SwrlRuleTriggerListener<String> listener = new SwrlRuleTriggerListener<>(
                new SwrlRuleTriggerListener.Config<>(
                        targetClassIri,
                        instanceIri -> {
                            log.info("[回调执行] 检测到低库存推导: {}", instanceIri);
                            Map<String, String> props = iriToPropsCache.get(instanceIri);
                            if (props != null) {
                                try {
                                    Map<String, Object> mqPayload = new LinkedHashMap<>();
                                    mqPayload.put("name", props.get(typeNS + "name"));
                                    mqPayload.put("type", props.get(typeNS + "type"));
                                    mqPayload.put("supplier", props.get(typeNS + "supplier"));
                                    mqPayload.put("stockQuantity", props.get(typeNS + "stockQuantity"));
                                    mqPayload.put("price", props.get(typeNS + "price"));

                                    // ✅ send() 内部已自动完成 JSON 序列化并设置 content_type
                                    mqHandler.send(exchangeName, routingKey, mqPayload);

                                    // ✅ 使用共享的 mapper 生成断言用 JSON，避免序列化差异
                                    String jsonMessage = mapper.writeValueAsString(mqPayload);
                                    receivedMsgRef.set(jsonMessage);
                                    log.info("[MQ发送成功] {}", jsonMessage);
                                } catch (Exception e) {
                                    log.error("[MQ发送失败]", e);
                                }
                            } else {
                                log.warn("[回调警告] 未在缓存中找到IRI对应的属性: {}", instanceIri);
                            }
                            latch.countDown();
                        },
                        String.class
                ),
                backendService
        );

        try {
            listener.start();
            log.info("🚀 场景19: Listener已启动，监控目标={}", targetClassIri);

            String testName = "ListenerTriggerTest_" + System.currentTimeMillis();
            String fullIri = indNS + testName;

            Map<String, String> lowStockProperties = new LinkedHashMap<>();
            lowStockProperties.put(typeNS + "name", testName);
            lowStockProperties.put(typeNS + "type", "NeapolitanCrust");
            lowStockProperties.put(typeNS + "supplier", "ListenerTestSupplier");
            lowStockProperties.put(typeNS + "price", "8.00");
            lowStockProperties.put(typeNS + "stockQuantity", "3");

            // ⭐ 写入本体前先将属性放入缓存，避免竞态条件
            iriToPropsCache.put(fullIri, lowStockProperties);

            GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
            Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(fullIri, lowStockProperties);

            InsertService inserter = new InsertService(backendService);
            assertDoesNotThrow(
                    () -> inserter.insertComponentAutoSplit(lowStockProperties, tempAxioms),
                    "低库存组件插入不应失败"
            );
            log.info("📝 场景19: 低库存组件已写入 name={} | stock=3", testName);

            boolean callbackExecuted = latch.await(10, TimeUnit.SECONDS);

            assertTrue(callbackExecuted, "SwrlRuleTriggerListener 应在10秒内触发回调");
            assertNotNull(receivedMsgRef.get(), "应成功生成并发送MQ消息");

            String sentMsg = receivedMsgRef.get();
            assertTrue(sentMsg.contains(testName), "消息应包含name");
            assertTrue(sentMsg.contains("NeapolitanCrust"), "消息应包含type");
            assertTrue(sentMsg.contains("ListenerTestSupplier"), "消息应包含supplier");
            assertTrue(sentMsg.contains("3"), "消息应包含stockQuantity");
            assertTrue(sentMsg.contains("8.00"), "消息应包含price");

            log.info("✅ 场景19通过: MQ消息内容={}", sentMsg);

        } finally {
            listener.shutdown();
            mqHandler.destroy();
            iriToPropsCache.clear();
            log.info("🧹 场景19: 资源已清理");
        }
    }

    @Test
    @Order(20)
    @DisplayName("场景20: SwrlRuleTriggerListener 端到端验证 - Exchange/Queue/Binding 完整投递")
    void testSwrlRuleTriggerEndToEndDelivery() throws Exception {
        RabbitMqHandler mqHandler = new RabbitMqHandler();
        ObjectMapper mapper = mqHandler.getObjectMapper();

        String exchangeName = "pizza.low-stock.exchange";
        String routingKey = "low.stock.alert";
        String queueName = "pizzaQueue";

        // ✅ 1. 声明完整的 AMQP 拓扑结构（幂等操作，重复声明无副作用）
        RabbitAdmin admin = new RabbitAdmin(mqHandler.getRabbitTemplate());
        DirectExchange exchange = new DirectExchange(exchangeName, true, false);
        Queue queue = new Queue(queueName, true, false, false);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(routingKey);

        admin.declareExchange(exchange);
        admin.declareQueue(queue);
        admin.declareBinding(binding);
        log.info("🏗️ 场景20: AMQP 拓扑已声明 | Exchange={} | Queue={} | RoutingKey={}",
                exchangeName, queueName, routingKey);

        // ✅ 【新增】预检并清空目标队列，确保测试不受历史残留消息干扰
        int purgedCount = 0;
        Message staleMsg;
        while ((staleMsg = mqHandler.getRabbitTemplate().receive(queueName, 100)) != null) {
            purgedCount++;
        }
        if (purgedCount > 0) {
            log.warn("🧹 场景20: 测试前清理队列 [{}] 中 {} 条残留消息", queueName, purgedCount);
        } else {
            log.info("✅ 场景20: 队列 [{}] 初始状态为空，无需清理", queueName);
        }

        Map<String, Map<String, String>> iriToPropsCache = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> sentJsonRef = new AtomicReference<>();

        String typeNS = "http://example.org/pizza/components/classes/";
        String indNS = "http://example.org/pizza/components/individuals/";
        String targetClassIri = typeNS + "LowStockCrust";

        SwrlRuleTriggerListener<String> listener = new SwrlRuleTriggerListener<>(
                new SwrlRuleTriggerListener.Config<>(
                        targetClassIri,
                        instanceIri -> {
                            log.info("[回调执行] 检测到低库存推导: {}", instanceIri);
                            Map<String, String> props = iriToPropsCache.get(instanceIri);
                            if (props != null) {
                                try {
                                    Map<String, Object> mqPayload = new LinkedHashMap<>();
                                    mqPayload.put("name", props.get(typeNS + "name"));
                                    mqPayload.put("type", props.get(typeNS + "type"));
                                    mqPayload.put("supplier", props.get(typeNS + "supplier"));
                                    mqPayload.put("stockQuantity", props.get(typeNS + "stockQuantity"));
                                    mqPayload.put("price", props.get(typeNS + "price"));

                                    log.info("🚨 即将发送消息到 exchange={}, routingKey={}", exchangeName, routingKey);
                                    mqHandler.send(exchangeName, routingKey, mqPayload);
                                    log.info("✅ send() 方法调用完成");

                                    sentJsonRef.set(mapper.writeValueAsString(mqPayload));
                                    log.info("[MQ发送成功] {}", sentJsonRef.get());
                                } catch (Exception e) {
                                    log.error("[MQ发送失败]", e);
                                }
                            } else {
                                log.warn("[回调警告] 未在缓存中找到IRI对应的属性: {}", instanceIri);
                            }
                            latch.countDown();
                        },
                        String.class
                ),
                backendService
        );

        try {
            listener.start();
            log.info("🚀 场景20: Listener已启动，监控目标={}", targetClassIri);

            String testName = "E2EDeliveryTest_" + System.currentTimeMillis();
            String fullIri = indNS + testName;

            Map<String, String> lowStockProperties = new LinkedHashMap<>();
            lowStockProperties.put(typeNS + "name", testName);
            lowStockProperties.put(typeNS + "type", "NeapolitanCrust");
            lowStockProperties.put(typeNS + "supplier", "E2ETestSupplier");
            lowStockProperties.put(typeNS + "price", "9.50");
            lowStockProperties.put(typeNS + "stockQuantity", "2");

            iriToPropsCache.put(fullIri, lowStockProperties);

            GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService, typeNS, indNS);
            Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(fullIri, lowStockProperties);

            InsertService inserter = new InsertService(backendService);
            assertDoesNotThrow(
                    () -> inserter.insertComponentAutoSplit(lowStockProperties, tempAxioms),
                    "低库存组件插入不应失败"
            );
            log.info("📝 场景20: 低库存组件已写入 name={} | stock=2", testName);

            // ✅ 2. 等待回调触发
            boolean callbackExecuted = latch.await(10, TimeUnit.SECONDS);
            assertTrue(callbackExecuted, "SwrlRuleTriggerListener 应在10秒内触发回调");
            assertNotNull(sentJsonRef.get(), "应成功生成并发送MQ消息");

            // ✅ 3. 从队列中拉取消息验证内容
            Message receivedMessage = mqHandler.getRabbitTemplate().receive(queueName, 5000);
            assertNotNull(receivedMessage, "pizzaQueue 中应存在至少一条消息");

            String receivedBody = new String(receivedMessage.getBody(), StandardCharsets.UTF_8);
            log.info("📨 场景20: 从队列消费到消息={}", receivedBody);

            assertEquals(sentJsonRef.get(), receivedBody, "队列中的消息应与发送的JSON完全一致");
            assertTrue(receivedBody.contains(testName), "消息应包含name");
            assertTrue(receivedBody.contains("NeapolitanCrust"), "消息应包含type");
            assertTrue(receivedBody.contains("E2ETestSupplier"), "消息应包含supplier");
            assertTrue(receivedBody.contains("2"), "消息应包含stockQuantity");
            assertTrue(receivedBody.contains("9.50"), "消息应包含price");

            assertEquals(MessageProperties.CONTENT_TYPE_JSON,
                    receivedMessage.getMessageProperties().getContentType(),
                    "消息的 content_type 应为 application/json");

            log.info("✅ 场景20通过: 端到端投递验证成功 | Queue={} | MsgLength={}", queueName, receivedBody.length());

            // ✅ 4. 通过 RabbitMQ Management HTTP API 验证 Publish 累计计数
            //     ⚠️ 必须在 deleteExchange 之前调用，否则统计会被清零
            verifyPublishCountViaApi(queueName);

        } finally {
            listener.shutdown();

            // 🧹 仅清理 Exchange，保留 pizzaQueue 供 UI 查看历史统计信息
            try {
                admin.deleteExchange(exchangeName);
                log.info("🧹 场景20: Exchange 已清理，Queue[{}] 已保留", queueName);
            } catch (Exception e) {
                log.warn("⚠️ Exchange 清理失败（可忽略）", e);
            }

            mqHandler.destroy();
            iriToPropsCache.clear();
            log.info("🧹 场景20: 本地资源已清理");
        }
    }

    @Test
    @Order(21)
    @DisplayName("场景21: JSON披萨实例本体合规性验证 - 读取/推理/不合规原因输出")
    void testPizzaInstanceOntologyComplianceValidation() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String resourcePath = "/pizzaInstances.json";

        // ✅ 1. 从 resource 文件夹读取 JSON 测试数据
        List<Map<String, Object>> pizzaInstances;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            assertNotNull(is, "pizzaInstances.json 未找到于 test/resources 目录");
                pizzaInstances = mapper.readValue(is, new TypeReference<>() {});
            log.info("📂 场景21: 成功加载 {} 条披萨实例 from {}", pizzaInstances.size(), resourcePath);
        }

        assertFalse(pizzaInstances.isEmpty(), "pizzaInstances.json 不应为空数组");

        // ✅ 2. 通过 BackendService 初始化验证器（替代旧的文件路径构造方式）
        // ⚠️ backendService 应为测试类的 @Autowired/@InjectMocks 字段或 @BeforeEach 中初始化的实例
        assertNotNull(backendService, "BackendService 未注入，请检查测试类配置");

        List<String> allViolationMessages = new ArrayList<>();
        int validCount = 0;
        int invalidCount = 0;

        // ✅ 3. 使用 try-with-resources 自动管理验证器生命周期
        try (PizzaOntologyValidator validator = new PizzaOntologyValidator(backendService)) {
            log.info("🏗️ 场景21: 本体推理验证器已通过 BackendService 初始化");

            // ✅ 4. 逐条验证实例并收集结果
            for (Map<String, Object> instance : pizzaInstances) {
                String name = String.valueOf(instance.getOrDefault("name", "UNKNOWN"));

                ValidationResult result = validator.validate(instance);

                if (result.isValid()) {
                    validCount++;
                    log.info("✅ [{}] 合规", name);
                } else {
                    invalidCount++;
                    log.warn("⚠️ [{}] 不合规，原因如下:", name);
                    result.violations().forEach(v -> {
                        log.warn("   - {}", v);
                        allViolationMessages.add(String.format("[%s] %s", name, v));
                    });
                }
            }

            log.info("📊 场景21: 验证完成 | 总计={} | 合规={} | 不合规={}",
                    pizzaInstances.size(), validCount, invalidCount);

            // ✅ 5. 断言验证结果的完整性
            assertEquals(pizzaInstances.size(), validCount + invalidCount,
                    "验证总数应与JSON记录数一致");

            assertTrue(validCount > 0, "应至少有一条合规的披萨实例");

            // ✅ 6. 对不合规记录进行内容校验
            if (invalidCount > 0) {
                assertFalse(allViolationMessages.isEmpty(),
                        "不合规实例应有具体的违规原因描述");

                allViolationMessages.forEach(msg -> {
                    assertNotNull(msg, "违规原因不应为null");
                    assertFalse(msg.isBlank(), "违规原因不应为空白字符串");
                    log.info("🔍 不合规详情: {}", msg);
                });

                log.info("⚠️ 场景21: 发现 {} 条不合规记录，已全部输出原因", invalidCount);
            } else {
                log.info("✅ 场景21: 所有 {} 条披萨实例均通过本体验证", validCount);
            }

            log.info("✅ 场景21通过: JSON披萨实例本体合规性验证完成 | Valid={} | Invalid={}",
                    validCount, invalidCount);

        } catch (IllegalStateException e) {
            // 验证器初始化失败时快速失败，避免后续 NPE
            fail("场景21: 验证器初始化失败: " + e.getMessage());
        } finally {
            allViolationMessages.clear();
            log.info("🧹 场景21: 本地资源已清理");
        }
    }

    /**
     * 通过 RabbitMQ Management HTTP API 验证队列的 Publish 累计计数 > 0
     * 解决 UI Message rates 因采样间隔或 Exchange 清理导致无法显示峰值的问题
     */
    private void verifyPublishCountViaApi(String queueName) throws Exception {
        RestTemplate restTemplate = new RestTemplate();

        // 1. 手动将 vhost "/" 替换为已编码的 "%2F"
        //    因为 encode() 不会编码 path 中的 "/"，必须在 expand 时直接传入编码后的值
        // 2. build(true) 告诉 Builder 变量值已经是编码过的，不要再动它
        // 第1步：生成正确的 URL 字符串（%2F 不会被二次编码）
        String uriString = org.springframework.web.util.UriComponentsBuilder
                .fromUriString("http://localhost:15672/api/queues/{vhost}/{queue}")
                .build(false)              // ← 禁用自动编码
                .expand("%2F", queueName)  // ← 传入已编码的值
                .toUriString();            // ← 返回 String，不是 URI

        // 第2步：将字符串转为 URI 对象（URI.create 不做任何编码）
        java.net.URI uri = java.net.URI.create(uriString);                        // ← 返回 URI 对象，阻止 RestTemplate 二次编码

        log.info("🔗 [API验证] 请求URL: {}", uri);

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("guest", "guest");
        headers.setAccept(List.of(org.springframework.http.MediaType.APPLICATION_JSON)); // ← 修复 406

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.exchange(
                uri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        ).getBody();

        assertNotNull(response, "RabbitMQ API 应返回有效响应");

        @SuppressWarnings("unchecked")
        Map<String, Object> messageStats =
                (Map<String, Object>) response.get("message_stats");
        assertNotNull(messageStats, "API 响应应包含 message_stats");

        long publishCount = ((Number) messageStats.getOrDefault("publish", 0)).longValue();
        log.info("📊 [API验证] {} 的历史 Publish 累计计数: {}", queueName, publishCount);

        assertTrue(publishCount > 0,
                String.format("RabbitMQ API 确认 %s 应有 Publish 记录，实际计数=%d", queueName, publishCount));
    }

    void verifyPublishCountViaApi() throws Exception {
        String url = "http://localhost:15672/api/queues/%2F/pizzaQueue";
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth("guest", "guest"); // 替换为你的账号密码

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        Map<String, Object> stats = (Map<String, Object>) response.getBody().get("message_stats");
        long publishCount = ((Number) stats.get("publish")).longValue();

        log.info("📊 API 确认 pizzaQueue 历史 Publish 总数: {}", publishCount);
        assertTrue(publishCount > 0, "消息应已被发布到队列");
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