package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.GenericAxiomBuilder;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.query.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
        String newName = "spicy_chicken_new";
        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "SpicyChicken", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        PizzaInsertService inserter = new PizzaInsertService(backendService);

        // ⚠️ TODO: 将 Exception.class 替换为实际抛出的异常类型
        // 例如: DataAccessException / IllegalArgumentException / OBDAException 等
        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertPizzaComponent(newName, triples),
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
        String newName = "NeapolitanCrustInstance";
        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        PizzaInsertService inserter = new PizzaInsertService(backendService);

        // ⚠️ TODO: 将 Exception.class 替换为实际的唯一性约束异常
        // 例如: DuplicateKeyException / DataIntegrityViolationException 等
        Exception ex = assertThrows(Exception.class,
                () -> inserter.insertPizzaComponent(newName, triples),
                "插入重复 name 应违反数据库唯一性约束"
        );

        log.info("✅ 预期异常已捕获: [{}] {}", ex.getClass().getSimpleName(), ex.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("缺少 rdf:type 时应拒绝写入")
    void testInsertWithoutTypeShouldFail() {
        List<GenericAxiomBuilder.Triple> invalidTriples = List.of(
                new GenericAxiomBuilder.Triple("test", "supplier", "SupplierX", true)
        );

        PizzaInsertService inserter = new PizzaInsertService(backendService);
        assertThrows(IllegalArgumentException.class,
                () -> inserter.insertPizzaComponent("test", invalidTriples));
    }

    @Test
    @Order(6)
    @DisplayName("正面: 正常插入 NeapolitanCrust 实例并验证数据一致性")
    void testInsertValidPizzaComponent() throws Exception {
        // ⭐ 1. 准备测试数据
        String newName = "NeapolitanCrustInstanceTest_" + System.currentTimeMillis();
        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );

        PizzaInsertService inserter = new PizzaInsertService(backendService);

        // ⭐ 2. 验证写入过程无异常（补全DI参数）
        assertDoesNotThrow(
                () -> inserter.insertPizzaComponent(
                        newName,
                        triples
                ),
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
    @DisplayName("场景4: SWRL 实时推导 LowStockCrust")
    void testQueryWithLiveSwrl() {
        // ⚠️ 此测试依赖外部 InfModel 和 RDFConnection，需根据实际获取方式调整
        // 以下为结构模板，请替换为真实的 tboxInfModel 和 aboxConn 获取逻辑
        fail("请补充 InfModel 和 RDFConnection 的获取逻辑后启用此测试");

        /*
        // 取消注释并补全上方依赖后删除此行 fail()
        String sparql = """
            PREFIX pizza: <http://www.co-ode.org/ontologies/pizza/pizza.owl#>
            PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            SELECT ?crust ?stockQty WHERE {
                ?crust rdf:type pizza:LowStockCrust .
                ?crust pizza:hasStockQuantity ?stockQty .
            }
            """;

        Model aboxSnapshot = aboxConn.queryConstruct("""
            PREFIX pizza: <http://www.co-ode.org/ontologies/pizza/pizza.owl#>
            CONSTRUCT { ?s ?p ?o . }
            WHERE { ?s a pizza:PizzaCrust . ?s ?p ?o . }
            """);
        log.debug("拉取到 {} 条 ABox 三元组", aboxSnapshot.size());

        long start = System.currentTimeMillis();
        tboxInfModel.add(aboxSnapshot);
        ((org.semanticweb.owlapi.reasoner.OWLReasoner) tboxInfModel.getReasoner()).flush();
        long inferTime = System.currentTimeMillis() - start;
        log.info("⚡ SWRL 增量推理完成，耗时 {}ms", inferTime);

        try (QueryExecution qe = QueryExecutionFactory.create(sparql, tboxInfModel)) {
            ResultSet rs = qe.execSelect();
            int count = 0;
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                log.debug("✅ {} | 库存: {}",
                        sol.getResource("crust").getLocalName(),
                        sol.getLiteral("stockQty"));
                count++;
            }
            assertTrue(count > 0,
                    "SWRL 应推导出至少一个 LowStockCrust，若无结果请检查规则与数据类型匹配");
            log.info("📊 共找到 {} 个低库存饼底 (SWRL 实时推导)", count);
        } finally {
            tboxInfModel.remove(aboxSnapshot);
        }
        */
    }
}