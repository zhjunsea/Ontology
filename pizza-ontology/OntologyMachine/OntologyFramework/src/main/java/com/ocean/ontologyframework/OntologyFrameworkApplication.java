package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.openlletresolver.ReasonerService;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.query.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.vocabulary.RDFS;

public class OntologyFrameworkApplication {

    private static final String ONTOP_ABOX_ENDPOINT = "http://localhost:8080/sparql";
    private static ReasonerService service;
    private static final String TBOX_FILE = "D:/work/Ontology/pizza-ontology/ontology/pizza-all.owl";
    private static OBDAHandler handler;

    public static void main(String[] args) throws Exception {
        System.out.println("=== TBox/ABox 分离架构演示 ===\n");

        // ==========================================
        // 1. 初始化 TBox: Openllet 纯模式推理
        // ==========================================
        System.out.println("🧠 [TBox] 加载本体并执行 Openllet OWL DL 推理...");
        service = ReasonerService.getInstance(TBOX_FILE);
        System.out.println("✅ TBox 推理完成！隐式公理已展开。\n");

        // ==========================================
        // 2. 连接 ABox: Ontop 虚拟化数据层
        // ==========================================
        System.out.println("🗄️ [ABox] 连接 Ontop VKG Endpoint...");
        handler = OBDAHandler.getInstance();
        System.out.println("ABox 单例初始化成功\n");

        // ✅ 获取推理后的 TBox 模型（InfModel 替代 OntModel）
        Model tboxModel = service.getInferredModel();

        // ✅ 使用 RDFConnectionRemote 连接 Ontop SPARQL 端点
        try (RDFConnection aboxConn = RDFConnectionRemote.create()
                .destination(ONTOP_ABOX_ENDPOINT)
                .build()) {

            queryWithInferredSubclasses(tboxModel, aboxConn);
            queryWithInferredProperties(tboxModel, aboxConn);
        }
    }

    /**
     * 场景1: TBox 推导出子类层次，注入 ABox VALUES 查询
     * ✅ 变更: OntModel → Model, QueryExecutionFactory.create(sparql, model) 不变
     */
    private static void queryWithInferredSubclasses(Model tbox, RDFConnection abox) {
        System.out.println("[Query 1] 基于 TBox 子类推理的 ABox 实例检索");

        String subclassSparql = """
            PREFIX : <http://example.org/pizza/components/classes/>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT DISTINCT ?subClass WHERE {
                ?subClass rdfs:subClassOf* :Crust .
            }
            """;

        StringBuilder valuesClause = new StringBuilder("VALUES ?type { ");
        try (QueryExecution qe = QueryExecutionFactory.create(subclassSparql, tbox)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                Resource cls = rs.next().getResource("subClass");
                valuesClause.append("<").append(cls.getURI()).append("> ");
            }
        }
        valuesClause.append("}");

        String combinedSparql = String.format("""
            PREFIX : <http://example.org/pizza/components/classes/>
            SELECT ?instance ?type WHERE {
                %s
                ?instance a ?type .
            }
            LIMIT 20
            """, valuesClause.toString());

        abox.querySelect(combinedSparql, qs -> {
            System.out.printf("   🌶️ %-25s ⇒ %s%n",
                    qs.getResource("instance").getLocalName(),
                    qs.getResource("type").getLocalName());
        });
        System.out.println();
    }

    /**
     * 场景2: Union Model 联合推理
     * ✅ 变更:
     *   - OntModel tbox → Model tbox
     *   - ModelFactory.createUnion(OntModel, Model) → ModelFactory.createUnion(Model, Model)
     *   - 不再依赖 OntModelSpec
     * ✅ 关键修复: createUnion 仅做数据合并，必须再挂载推理器才能实现联合推理
     */
    private static void queryWithInferredProperties(Model tbox, RDFConnection abox) {
        System.out.println("[Query 2] Union Model 联合推理查询");

        // 1. 从 Ontop 拉取 ABox 实例数据
        Model aboxData = abox.queryConstruct("""
            PREFIX : <http://example.org/pizza/components/classes/>
            CONSTRUCT { ?s ?p ?o } 
            WHERE { ?s a :PizzaComponent ; ?p ?o } 
            LIMIT 5000
            """);

        //测试 在创建 unionModel 之前插入
        /*System.out.printf("   📊 TBox 三元组数: %d%n", tbox.size());
        tbox.listStatements(null, RDFS.subClassOf, (RDFNode) null)
                .forEachRemaining(stmt ->
                        System.out.printf("      %s rdfs:subClassOf %s%n",
                                stmt.getSubject().getLocalName(),
                                stmt.getObject().asResource().getLocalName()));*/
        // 2. 创建联合模型（纯数据合并，此时无推理能力）
        Model unionModel = ModelFactory.createUnion(tbox, aboxData);

        // 3. ⭐ 关键修复：在联合模型上挂载 RDFS/OWL 推理器
        //    让推理器同时看到 TBox 公理 + ABox 实例，才能推导出实例的隐含类型
        Reasoner reasoner = ReasonerRegistry.getRDFSReasoner();
        InfModel inferredUnion = ModelFactory.createInfModel(reasoner, unionModel);

        // 4. 在推理后的联合模型上执行查询
        String sparql = """
            PREFIX : <http://example.org/pizza/components/classes/>
            SELECT DISTINCT ?component ?inferredType WHERE {
                ?component a :PizzaComponent .
                ?component a ?inferredType .
                FILTER(?inferredType != :PizzaComponent)
            }
            LIMIT 15
            """;

        try (QueryExecution qe = QueryExecutionFactory.create(sparql, inferredUnion)) {
            ResultSet rs = qe.execSelect();
            int count = 0;
            while (rs.hasNext()) {
                QuerySolution qs = rs.next();
                count++;
                System.out.printf("   🍕 %-25s ⇒ %s%n",
                        qs.getResource("component").getLocalName(),
                        qs.get("inferredType").isResource()
                                ? qs.getResource("inferredType").getLocalName()
                                : qs.get("inferredType").toString());
            }
            if (count == 0) {
                System.out.println("   ⚠️ 未找到隐含类型，请检查 TBox 中是否定义了 rdfs:subClassOf 层次关系");
            }
        }
        System.out.println();
    }
}