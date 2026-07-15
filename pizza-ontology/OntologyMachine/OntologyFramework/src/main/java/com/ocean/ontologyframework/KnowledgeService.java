package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.WriteResult;
import com.ocean.openlletresolver.ABoxTypeFixer;
import com.ocean.openlletresolver.ReasonerService;
import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.InfModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.OWLOntologyDocumentTarget;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.vocab.OWL2Datatype;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.ocean.openlletresolver.ReasonerService.checkConsistency;
import static com.ocean.openlletresolver.ReasonerService.explainInconsistency;

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

    private static OWLOntology loadTbox(OWLOntologyManager m, String path) throws OWLOntologyCreationException {
        return m.loadOntologyFromOntologyDocument(new java.io.File(path));
    }

    private static OWLOntology loadTbox(String path) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        return manager.loadOntologyFromOntologyDocument(
                new java.io.File(path));
    }

    /**
     * 通用 Ontop ABox 拉取并转换为 OWLOntology
     * @param constructSparql CONSTRUCT 查询语句（必须包含 PREFIX 和 LIMIT）
     * 所有字面量都缺少显式数据类型标签。这就是 OWL API 将其降级为 AnnotationAssertion 的直接原因。
     * 即使共享了 TBox Manager，OWL API 的 Turtle 解析器在处理无类型字面量（Plain Literal） 时，
     * 如果当前文档片段内没有该属性的局部声明，仍然会保守地将其解析为 Annotation。
     */
    public static OWLOntology loadAboxFromOntop(String constructSparql,
                                                OWLOntology tboxOntology) throws OWLOntologyCreationException {
        // Step 1: 从 Ontop 拉取纯 ABox 数据
        Model aboxData = aBoxService.queryConstruct(constructSparql);
        if (aboxData.isEmpty()) {
            throw new IllegalStateException("CONSTRUCT 查询返回空结果，请检查 SPARQL 或数据源");
        }

        // Step 2: 转换为 TTL 字节流（强制保留所有 XSD 类型标签）
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        aboxData.write(baos, "N-TRIPLES"); // ⚠️ 强烈建议用 N-TRIPLES，避免 Turtle 缩写导致正则解析失败
        byte[] turtleBytes = baos.toByteArray();
        String rawTurtle = new String(turtleBytes, StandardCharsets.UTF_8);

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology aboxOntology = manager.loadOntologyFromOntologyDocument(
                new ByteArrayInputStream(turtleBytes)
        );
        // ✅ 新增：一行调用完成类型修复
        ABoxTypeFixer.fixDataPropertyTypes(tboxOntology,aboxOntology, rawTurtle);

        //测试
        // ⭐ 诊断：打印 Ontop 生成的原始 Turtle
        rawTurtle = baos.toString(StandardCharsets.UTF_8);
        System.out.println("========== Ontop CONSTRUCT 原始输出 ==========");
        System.out.println(rawTurtle);
        System.out.println("===============================================");

        // ⭐ Step 3: 将 ABox 公理合并到 TBox 本体中（或保持独立但共享 Manager）
        // 如果后续推理需要统一本体，执行合并：
        manager.addAxioms(tboxOntology, aboxOntology.getAxioms());
        manager.removeOntology(aboxOntology); // 清理临时本体，释放内存

        return tboxOntology; // 返回包含完整 TBox + ABox 的本体
    }

    @FunctionalInterface
    interface DbWriteAction {
        void execute() throws Exception;
    }
    // ==================== 内部工具方法 ====================

    /**
     * 打印 OWLOntology 中包含的所有 ABox 三元组信息
     *
     * @param ontology 通过 loadAboxFromOntop 加载的本体对象
     */
    public static void printAboxOntology(OWLOntology ontology) {
        // 1. 空值与空本体检查
        if (ontology == null) {
            System.out.println("[WARN] ontology 为 null，跳过打印");
            return;
        }
        int axiomCount = ontology.getAxiomCount();
        int individualCount = ontology.getIndividualsInSignature().size();
        System.out.println("========== ABox 概览 ==========");
        System.out.println("公理(Axiom)总数: " + axiomCount);
        System.out.println("个体(Individual)数: " + individualCount);

        if (axiomCount == 0) {
            System.out.println("[WARN] ABox 为空，CONSTRUCT 查询可能未返回数据");
            System.out.println("===============================");
            return;
        }

        // 2. 按公理类型分组统计并逐条打印
        Map<String, Long> typeStats = ontology.getAxioms().stream()
                .collect(Collectors.groupingBy(
                        ax -> ax.getAxiomType().getName(),
                        Collectors.counting()
                ));

        System.out.println("\n========== 分类统计 ==========");
        typeStats.forEach((type, count) ->
                System.out.printf("  %-35s : %d%n", type, count)
        );

        System.out.println("\n========== 详细公理列表 ==========");
        ontology.getAxioms().forEach(ax ->
                System.out.println("  " + ax)
        );

        // 3. 序列化为 Turtle 格式（便于阅读完整 RDF 结构）
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // ✅ 使用标准重载：saveOntology(OWLDocumentFormat, OutputStream)
            ontology.saveOntology(new TurtleDocumentFormat(), baos);

            String turtleContent = baos.toString(StandardCharsets.UTF_8.name());
            System.out.println("\n========== Turtle 序列化 ==========");
            System.out.println(turtleContent);
        } catch (Exception e) {
            System.err.println("[ERROR] Turtle 序列化失败: " + e.getMessage());
        }

        System.out.println("===================================");
    }
    // ================= 2. 核心安全写入引擎 (完全通用) =================

    static void safeInsertAndVerify(Set<OWLAxiom> tempAxioms, String typeIRI, String verifySparql, DbWriteAction dbWriteAction)
            throws Exception {

        validateTypeAxiom(tempAxioms, typeIRI, tBoxService.getReasoner());
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        System.out.println("[Step 1-2] 加载本体基线并注入临时公理...");
        OWLOntology baseline = tBoxService.getOntology();
        manager.addAxioms(baseline, tempAxioms);

        System.out.println("[Step 3] 执行推理一致性预校验...");
        boolean consistent;
        try {
            consistent = checkConsistency(baseline);
            if (!consistent) {
                explainInconsistency(baseline);
                throw new IllegalStateException("一致性检查失败！新数据与现有本体存在矛盾，已拦截写入。");
            }
            System.out.println("✅ 预校验通过，无逻辑矛盾");
        } finally {
            // ⭐ 关键修复：无论校验成功还是失败，都必须从基线中移除临时公理
            // 防止污染内存中的 TBox 单例
            System.out.println("[Step 3.5] 清理临时公理，恢复本体基线...");
            manager.removeAxioms(baseline, tempAxioms);
            System.out.printf("✅ 已移除 %d 条临时公理，本体基线已恢复%n", tempAxioms.size());
        }

        System.out.println("[Step 4] 执行数据库持久化...");
        dbWriteAction.execute();
        System.out.println("✅ 数据库写入成功");

        System.out.println("[Step 5] 重新拉取 ABox 并验证推理结果...");
        OWLOntology tbox = tBoxService.getOntology();
        OWLOntology freshAbox = loadAboxFromOntop(verifySparql, tBoxService.getOntology());
        PizzaQueryService.queryPizzaComponentTypes(tbox, freshAbox);
    }

    /**
     * 校验 tempAxioms 中是否存在合法的 rdf:type 三元组
     *
     * @param tempAxioms  待校验的公理集合
     * @param targetClassIri 目标类的 IRI（如 PizzaComponent）
     * @param reasoner    OWL 推理器（用于判断子类关系）
     * @throws IllegalArgumentException 无 type 三元组或 type 不合法时抛出
     */
    public static void validateTypeAxiom(
            Set<OWLAxiom> tempAxioms,
            String targetClassIri,
            OWLReasoner reasoner) {

        // 1. 过滤出所有 rdf:type 断言（即 OWLClassAssertionAxiom）
        Set<OWLClassAssertionAxiom> typeAxioms = tempAxioms.stream()
                .filter(ax -> ax instanceof OWLClassAssertionAxiom)
                .map(ax -> (OWLClassAssertionAxiom) ax)
                .collect(java.util.stream.Collectors.toSet());

        // 2. 没有 type 三元组 → 直接报错
        if (typeAxioms.isEmpty()) {
            throw new IllegalArgumentException(
                    "❌ tempAxioms 中不存在任何 rdf:type 三元组，无法确定个体类型");
        }

        // 3. 获取目标类对象
        OWLDataFactory df = reasoner.getRootOntology().getOWLOntologyManager().getOWLDataFactory();
        OWLClass targetClass = df.getOWLClass(IRI.create(targetClassIri));

        // 4. 检查是否有至少一个 type 是目标类或其子类
        boolean hasValidType = typeAxioms.stream()
                .map(OWLClassAssertionAxiom::getClassExpression)
                .filter(ce -> ce instanceof OWLClass)          // 只处理具名类，忽略匿名表达式
                .map(ce -> (OWLClass) ce)
                .anyMatch(cls -> {
                    // 同一个类
                    if (cls.equals(targetClass)) return true;
                    // 通过推理器判断是否为子类
                    return reasoner.getSubClasses(targetClass, false)
                            .containsEntity(cls);
                });

        if (!hasValidType) {
            String actualTypes = typeAxioms.stream()
                    .map(ax -> ax.getClassExpression().toString())
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalArgumentException(String.format(
                    "❌ tempAxioms 中的 type [%s] 均不属于 <%s> 或其子类",
                    actualTypes, targetClassIri));
        }

        System.out.println("✅ rdf:type 校验通过，存在合法的 PizzaComponent 或其子类断言");
    }

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

    public static void printOntologyClasses(OWLOntology ontology){
        ontology.getAxioms(AxiomType.CLASS_ASSERTION).stream()
                .map(ax -> ((OWLClassAssertionAxiom) ax).getClassExpression().toString())
                .distinct()
                .forEach(System.out::println);
    }
    public static void printOntologyIndividuals(OWLOntology ontology){
        // === 开始：打印 ABox 个体诊断信息 ===
        int maxOutputNum = 200;
        System.out.println("========== ABox 个体诊断 ==========");

        // 1. 打印本体中所有的命名个体总数
        Set<OWLNamedIndividual> allIndividuals = ontology.getIndividualsInSignature();
        System.out.println("签名中的命名个体总数: " + allIndividuals.size());

        if (allIndividuals.isEmpty()) {
            System.err.println("⚠️ 警告: 未检测到任何命名个体！请检查文件是否加载正确。");
        } else {
            // 2. 遍历并打印前 30 个个体的详细信息（防止数据量过大刷屏）
            allIndividuals.stream().limit(maxOutputNum).forEach(ind -> {
                // 获取该个体在 ABox 中显式声明的类型 (ClassAssertion)
                String assertedTypes = ontology.getClassAssertionAxioms(ind).stream()
                        .map(ax -> ax.getClassExpression().toString())
                        .collect(Collectors.joining(", "));

                // (可选) 如果已初始化 Reasoner，可获取推理后的直接类型
                // String inferredTypes = reasoner.getTypes(ind, true).getFlattened().stream()
                //         .map(OWLClass::toString)
                //         .collect(Collectors.joining(", "));

                System.out.printf("个体: %-50s | 显式类型: %s%n",
                        ind.getIRI().getFragment(),
                        assertedTypes.isEmpty() ? "(无)" : assertedTypes);
            });

            if (allIndividuals.size() > maxOutputNum) {
                System.out.println("... 还有 " + (allIndividuals.size() - maxOutputNum) + " 个个体未显示 ...");
            }
        }
        System.out.println("===================================");
        // === 结束：打印 ABox 个体诊断信息 ===
    }
    @Override
    public void close() throws Exception {
        tBoxService.close();
        aBoxService.shutdown();
    }
}