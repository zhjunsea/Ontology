package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.WriteResult;
import com.ocean.openlletresolver.ReasonerService;
import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KnowledgeService implements AutoCloseable {
    private static final String ONTOP_ABOX_ENDPOINT = "http://localhost:8080/sparql";
    private static ReasonerService tBoxService;
    private static final String TBOX_FILE = "D:/work/Ontology/pizza-ontology/ontology/pizza-all.owl";
    private static OBDAHandler aBoxService;

    private OWLDataFactory df = null;
    public KnowledgeService(String owlFile, String obdaFile, String propsFile) throws Exception {
        //待补充
    }
    public KnowledgeService() throws Exception {
        // ==========================================
        // 1. 初始化 TBox: Openllet 纯模式推理
        // ==========================================
        System.out.println("🧠 [TBox] 加载本体并执行 Openllet OWL DL 推理...");
        tBoxService = ReasonerService.getInstance(TBOX_FILE);
        System.out.println("✅ TBox 推理完成！隐式公理已展开。\n");

        // ==========================================
        // 2. 连接 ABox: Ontop 虚拟化数据层
        // ==========================================
        System.out.println("🗄️ [ABox] 连接 Ontop VKG Endpoint...");
        aBoxService = OBDAHandler.getInstance();
        System.out.println("ABox 单例初始化成功\n");

        this.df = tBoxService.getOntology().getOWLOntologyManager().getOWLDataFactory();
    }

    // ==================== 写入路径 ====================

    /**
     * 安全写入：验证 → 写库
     */
    public WriteResult safeWrite(List<Triple> triples) {
        Set<OWLAxiom> tempAxioms = convertToOwlAxioms(triples);

        boolean consistent = tBoxService.validateABox(tempAxioms);
        if (!consistent) {
            return WriteResult.rejected("ABox与TBox/SWRL规则存在矛盾");
        }

        // 验证通过，执行数据库写入（此处省略JDBC/Ontop更新逻辑）
        persistToDatabase(triples);
        return WriteResult.accepted();
    }

    // ==================== 读取路径 ====================

    /**
     * 组合查询：TBox缓存 ∪ Ontop ABox
     */
    public List<Map<String, String>>  executeAboxQuery(String sparql) {
        // 简化示例：实际需解析SPARQL判断是否涉及TBox推导
        // 这里演示直接委托Ontop查询ABox数据
        List<Map<String, String>>  qe = aBoxService.getInstance().executeSelect(sparql);
        return qe;
    }

    public ReasonerService  getReasonerService() {
        return tBoxService;
    }
    /**
     * 1. TBox 推理：获取指定类及其所有子类的 IRI 字符串集合
     * @param classIri 目标类的完整 IRI
     * @return 包含自身及所有子类的 IRI 字符串 Set
     */
    public Set<String> getSubClassIris(String classIri) {
        Set<OWLClass> subClasses = tBoxService.getSubClasses(classIri);

        if (subClasses == null || subClasses.isEmpty()) {
            return Collections.emptySet();
        }

        return subClasses.stream()
                .map(cls -> cls.getIRI().getIRIString())
                .collect(Collectors.toSet());
    }

    /**
     * 2. SPARQL 构建：将 IRI 集合转换为安全的 VALUES 子句
     * @param variableName SPARQL 变量名（不含 ?）
     * @param iris IRI 字符串集合
     * @return 格式化的 VALUES 子句，若集合为空则返回 "VALUES ?var { UNDEF }" 以确保查询安全返回空结果
     */
    public String buildValuesClause(String variableName, Collection<String> iris) {
        if (iris == null || iris.isEmpty()) {
            // 使用 UNDEF 保证语法合法且查询结果为空，避免拼接出非法 SPARQL
            return String.format("VALUES ?%s { UNDEF }", variableName);
        }

        String values = iris.stream()
                .map(iri -> "<" + iri + ">")
                .collect(Collectors.joining(" "));

        return String.format("VALUES ?%s { %s }", variableName, values);
    }

    /**
     * 3. ABox 查询执行：执行 SPARQL 并将结果映射为指定类型的列表
     * @param sparql 完整的 SPARQL SELECT 查询字符串
     * @param mapper 将单行查询结果 Map 转换为目标对象的函数
     * @return 映射后的对象列表
     */
    public <T> List<T> executeAndMap(String sparql, Function<Map<String, String>, T> mapper) {
        try {
            List<Map<String, String>> rawResults = executeAboxQuery(sparql);
            return rawResults.stream()
                    .map(mapper)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("ABox query execution failed: " + e.getMessage(), e);
        }
    }
    /**
     * 1. 从 Ontop 虚拟端点按需拉取 ABox 子图
     * @param constructSparql CONSTRUCT 查询字符串
     * @return 包含实例数据的 Jena Model
     */
    public static Model fetchAboxSubgraph(String constructSparql) {
        return aBoxService.queryConstruct(constructSparql);
    }

    /**
     * 2. 创建带 Openllet 推理能力的联合模型
     * ⚠️ 注意：此操作计算开销大，生产环境应缓存返回的 InfModel 或 Reasoner 实例
     * @param tboxModel 本体 TBox 模型
     * @param aboxModel 实例 ABox 模型
     * @return 绑定 Openllet 推理器的推理模型
     */


    /**
     * 3. 在推理模型上执行 SELECT 查询并映射结果
     * @param inferredModel 已绑定推理器的 InfModel
     * @param selectSparql  SELECT 查询字符串
     * @param mapper        将 QuerySolution 转换为目标对象的函数
     * @return 映射后的结果列表
     */
    public static <T> List<T> queryInferredModel(
            InfModel inferredModel,
            String selectSparql,
            Function<QuerySolution, T> mapper) {

        List<T> results = new ArrayList<>();
        try (QueryExecution qe = QueryExecutionFactory.create(selectSparql, inferredModel)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                results.add(mapper.apply(rs.next()));
            }
        }
        return results;
    }
    /**
     * 通用 Ontop ABox 拉取并转换为 OWLOntology
     * @param constructSparql CONSTRUCT 查询语句（必须包含 PREFIX 和 LIMIT）
     */
    public static OWLOntology loadAboxFromOntop(String constructSparql) throws OWLOntologyCreationException {
        // Step 1: 使用传入的 SPARQL 从 Ontop 拉取数据
        Model aboxData = aBoxService.queryConstruct(constructSparql);

        if (aboxData.isEmpty()) {
            throw new IllegalStateException("CONSTRUCT 查询返回空结果，请检查 SPARQL 或数据源");
        }

        // Step 2: 通过内存 TTL 流转换为 OWLOntology（零文件 IO）
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        aboxData.write(baos, "TURTLE");

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        return manager.loadOntologyFromOntologyDocument(
                new ByteArrayInputStream(baos.toByteArray())
        );
    }

    // ==================== 内部工具方法 ====================

    private Set<OWLAxiom> convertToOwlAxioms(List<Triple> triples) {
        return triples.stream().map(t -> {
            IRI subj = IRI.create(t.getSubject().getURI());
            IRI pred = IRI.create(t.getPredicate().getURI());

            if (t.getObject().isURI()) {
                return df.getOWLObjectPropertyAssertionAxiom(
                        df.getOWLObjectProperty(pred),
                        df.getOWLNamedIndividual(subj),
                        df.getOWLNamedIndividual(IRI.create(t.getObject().getURI()))
                );
            } else {
                OWLLiteral lit = df.getOWLLiteral(
                        t.getObject().getLiteralLexicalForm(),
                        df.getOWLDatatype(IRI.create(t.getObject().getLiteralDatatypeURI()))
                );
                return df.getOWLDataPropertyAssertionAxiom(
                        df.getOWLDataProperty(pred),
                        df.getOWLNamedIndividual(subj),
                        lit
                );
            }
        }).collect(Collectors.toSet());
    }

    private void persistToDatabase(List<Triple> triples) {
        // TODO: 通过JDBC或Ontop UPDATE接口写入MySQL
    }

    @Override
    public void close() throws Exception {
        tBoxService.close();
        aBoxService.shutdown();
    }
}