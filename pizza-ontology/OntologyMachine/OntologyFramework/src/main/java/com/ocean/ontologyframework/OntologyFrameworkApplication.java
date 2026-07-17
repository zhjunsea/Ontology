package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.BackendService;
import org.apache.jena.rdf.model.*;
import org.apache.jena.query.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class OntologyFrameworkApplication {

    private static final String ONTOP_ABOX_ENDPOINT = "http://localhost:8080/sparql";
    private static final String TBOX_FILE = "D:/work/Ontology/pizza-ontology/ontology/pizza-all.owl";
    private static BackendService backendService;
    private static final Logger log = LoggerFactory.getLogger(OntologyFrameworkApplication.class);

    public static void main(String[] args) throws Exception {
        log.info("=== TBox/ABox 分离架构演示 ===\n");
        OBDAHandler obdaHandler = OBDAHandler.getInstance();
        backendService = BackendService.getInstance(TBOX_FILE,obdaHandler);

        String subclassSparql = """
            PREFIX : <http://example.org/pizza/components/classes/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT DISTINCT ?subClass WHERE {
                ?subClass rdfs:subClassOf* :Crust .
            }
            """;
        //queryWithInferredSubclasses(subclassSparql);
        //queryWithInferredProperties();
        //insertARecord();
    }
    /**
     * 场景1: TBox 推导出子类层次，注入 ABox VALUES 查询
     * ✅ 变更: OntModel → Model, QueryExecutionFactory.create(sparql, model) 不变
     */
    private static void queryWithInferredSubclasses(String subclassSparql) {
        log.info("[Query 1] 基于 TBox 子类推理的 ABox 实例检索");
        PizzaQueryService pizzaQuery = new PizzaQueryService(backendService);
        List instAndSuppliers = pizzaQuery.getCrustInstancesAndSuppliers();
        if (log.isInfoEnabled()) {
            for (Object line : instAndSuppliers) {
                log.info("{}", line);
            }
        }
    }

    /**
     * 场景2: Union Model 合并ABox和TBox联合推理
     * ✅ 变更:
     *   - OntModel tbox → Model tbox
     *   - ModelFactory.createUnion(OntModel, Model) → ModelFactory.createUnion(Model, Model)
     *   - 不再依赖 OntModelSpec
     * ✅ 关键修复: createUnion 仅做数据合并，必须再挂载推理器才能实现联合推理
     */

    private static void queryWithInferredProperties() {
        log.info("[Query 2] OWLAPI + Openllet 联合推理查询");

        // ⭐ 将数据获取策略与业务查询解耦，SPARQL 作为显式参数传入
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

        try {
            // 1. 使用通用加载器按指定 SPARQL 拉取 ABox
            OWLOntology aboxOntology = backendService.getObdaHandler().loadAboxFromOntop(aboxSparql,backendService.getOntologyService().gettBoxOntology());
            //打印Abox三元组
            //ks.printAboxOntology(aboxOntology);
            PizzaQueryService pizzaQuery = new PizzaQueryService(backendService);

            // 2. 委托通用推理服务完成合并、推理、查询与资源释放
            pizzaQuery.queryPizzaComponentTypes(backendService.getOntologyService().gettBoxOntology(), aboxOntology);

        } catch (OWLOntologyCreationException e) {
            System.err.println("   ❌ 本体加载或推理失败: " + e.getMessage());
        } catch (IllegalStateException e) {
            // 捕获 loadAboxFromOntop 中新增的空结果防御异常
            System.err.println("   ⚠️ ABox 数据异常: " + e.getMessage());
        }
    }

    //测试插入一个新的数据
    /*
    private static void insertARecord() throws Exception {
        InsertService pzInserter = new InsertService(backendService);
        pzInserter.insertPizzaComponent();
    } */
    /**
     * ⭐ 核心查询方法：TBox(SWRL实时推理) + ABox(Ontop远程) 联合查询
     */
    private static void queryWithLiveSwrl(InfModel tboxInfModel, RDFConnection aboxConn) {
        String sparql = """
        PREFIX pizza: <http://www.co-ode.org/ontologies/pizza/pizza.owl#>
        PREFIX rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
        
        SELECT ?crust ?stockQty WHERE {
            ?crust rdf:type pizza:LowStockCrust .
            ?crust pizza:hasStockQuantity ?stockQty .
        }
        """;

        // Step 1: 从 Ontop 拉取当前 ABox 数据（仅相关三元组，避免全量拉取）
        log.info("📡 从 Ontop 拉取 ABox 实例数据...");
        Model aboxSnapshot = aboxConn.queryConstruct("""
        PREFIX pizza: <http://www.co-ode.org/ontologies/pizza/pizza.owl#>
        CONSTRUCT {
            ?s ?p ?o .
        } WHERE {
            ?s a pizza:PizzaCrust .
            ?s ?p ?o .
        }
        """);
        if (log.isDebugEnabled()) {
            log.debug("拉取到 {} 条 ABox 三元组", aboxSnapshot.size());
        }

        // Step 2: 将 ABox 快照加入实时 InfModel
        // ⚠️ add() 会触发 Openllet 增量推理，SWRL 自动重新评估
        long start = System.currentTimeMillis();
        tboxInfModel.add(aboxSnapshot);

        // Step 3: 通知推理器刷新（ABox 变更后必须调用）
        ((org.semanticweb.owlapi.reasoner.OWLReasoner) tboxInfModel.getReasoner()).flush();
        long inferTime = System.currentTimeMillis() - start;
        if (log.isInfoEnabled()) {
            log.info("⚡ SWRL 增量推理完成，耗时 {}ms", inferTime);
        }

        // Step 4: 在包含 SWRL 推导结果的模型上执行查询
        log.info("🔍 查询 LowStockCrust (SWRL 推导类):");
        try (QueryExecution qe = QueryExecutionFactory.create(sparql, tboxInfModel)) {
            ResultSet rs = qe.execSelect();
            int count = 0;
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                if (log.isDebugEnabled()) {
                    log.debug("✅ {} | 库存: {}",
                            sol.getResource("crust").getLocalName(),
                            sol.getLiteral("stockQty"));
                }
                count++;
            }
            if (count == 0) {
                log.info("   ❌ 无结果 — SWRL 规则可能未触发，请检查数据类型匹配");
            } else {
                if (log.isInfoEnabled()) {
                    log.info("📊 共找到 {} 个低库存饼底 (SWRL 实时推导)", count);
                }
            }
        }

        // Step 5: ⚠️ 清理本次 ABox 快照，为下次查询准备干净状态
        tboxInfModel.remove(aboxSnapshot);
    }


    /*
    @Test
    public void testSwrlRuleEffectivenessAfterABoxChange() throws Exception {
        // === 1. 获取本体组件 (使用正确的命名空间) ===
        OWLClass crustClass = service.getClass("http://example.org/pizza/components/classes/Crust");
        OWLClass lowStockCrustClass = service.getClass("http://example.org/pizza/components/classes/LowStockCrust");
        OWLDataProperty stockQtyProp = service.getDataProperty("http://example.org/pizza/components/classes/stockQuantity");

        OWLOntologyManager manager = service.gettBoxOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        // === 2. 准备测试个体 (优先复用，避免污染本体) ===
        Set<OWLNamedIndividual> existingCrusts = service.getIndividuals(crustClass.getIRI().toString());
        OWLNamedIndividual testCrust;

        if (!existingCrusts.isEmpty()) {
            testCrust = existingCrusts.iterator().next();
            log.info("♻️ 复用现有饼底个体: " + testCrust.getIRI().getShortForm());
        } else {
            testCrust = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components/individuals/swrlTestCrust"));
            manager.addAxiom(service.gettBoxOntology(), df.getOWLClassAssertionAxiom(crustClass, testCrust));
            log.info("✨ 创建临时测试个体: swrlTestCrust");
        }

        // === 3. 基准状态：高库存 (30 > 阈值20)，不应触发 SWRL ===
        updateStockQuantity(manager, df, testCrust, stockQtyProp, 30);
        service.refreshReasoner(); // ⚠️ 关键：触发 Openllet 重算 + Jena 缓存刷新

        Set<OWLClass> typesHighStock = service.getIndividualAllTypes(testCrust);
        log.info("📦 库存=30 时的类型集合:");
        service.printOWLClassSet(typesHighStock);

        assertFalse(typesHighStock.contains(lowStockCrustClass),
                "❌ [FAIL] 库存充足(30)时，SWRL不应推断为LowStockCrust");

        // === 4. 变更状态：低库存 (15 < 阈值20)，应触发 SWRL ===
        updateStockQuantity(manager, df, testCrust, stockQtyProp, 15);
        service.refreshReasoner(); // ⚠️ 关键：再次刷新，验证ABox变更是否被感知

        Set<OWLClass> typesLowStock = service.getIndividualAllTypes(testCrust);
        log.info("📦 库存=15 时的类型集合:");
        service.printOWLClassSet(typesLowStock);

        // === 5. 双重验证 (防止缓存与推理器不一致) ===
        // 验证方式A: 通过 service 封装的类型查询 (走 Jena InfModel 缓存)
        boolean inferredViaCache = typesLowStock.contains(lowStockCrustClass);

        // 验证方式B: 直接查询 Openllet Reasoner (绕过 Jena 缓存)
        boolean inferredViaReasoner = service.getReasoner()
                .getInstances(lowStockCrustClass, false)
                .entities()
                .anyMatch(i -> i.equals(testCrust));

        if (log.isDebugEnabled()) {
            log.debug("🔍 验证结果 -> Jena缓存命中: {} | Openllet直接命中: {}",
            inferredViaCache, inferredViaReasoner);
        }

        assertTrue(inferredViaCache && inferredViaReasoner,
                "❌ [FAIL] 库存低于20(15)时，SWRL应推断为LowStockCrust。" +
                        "若仅Reasoner命中而Cache未命中，说明refreshReasoner()未正确刷新Jena缓存！");
    }*/

    /**
     * 辅助方法：安全更新个体的数据属性值（先删旧值再赋新值）
     */
    /*private void updateStockQuantity(OWLOntologyManager mgr, OWLDataFactory df,
                                     OWLNamedIndividual ind, OWLDataProperty prop, int value) {
        // 清除旧的库存断言
        service.getDataPropertyAssertions(ind, prop)
                .forEach(ax -> mgr.removeAxiom(service.gettBoxOntology(), ax));
        // 添加新的库存断言
        service.addIndividualAxiom(ind, prop, df.getOWLLiteral(value));
    }*/
}