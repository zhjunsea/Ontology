package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.WriteResult;
import org.apache.jena.graph.Triple;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.semanticweb.owlapi.util.OWLOntologyMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OntologyService implements AutoCloseable {

    //private static BackendService tBoxService;
    private static OBDAHandler aBoxService;
    private DefaultPrefixManager prefixManager;
    private static final Logger log = LoggerFactory.getLogger(OntologyService.class);

    public OWLOntology getaBoxOntology() {
        return aBoxOntology;
    }

    public void setaBoxOntology(OWLOntology aBoxOntology) {
        if(this.aBoxOntology != null)
            manager.removeOntology(this.aBoxOntology);
        this.aBoxOntology = aBoxOntology;
    }

    private OWLOntology aBoxOntology = null;

    public OWLOntology gettBoxOntology() {
        return tBoxOntology;
    }

    public void settBoxOntology(OWLOntology tBoxOntology) {
        if(this.tBoxOntology != null)
            manager.removeOntology(this.tBoxOntology);
        this.tBoxOntology = tBoxOntology;
    }

    private OWLOntology tBoxOntology = null;

    public OWLOntology getMergedOntology() {
        return mergedOntology;
    }

    public void setMergedOntology(OWLOntology mergedOntology) {
        if(this.mergedOntology != null)
            manager.removeOntology(this.mergedOntology);
        this.mergedOntology = mergedOntology;
    }

    private OWLOntology mergedOntology = null;

    public OWLDataFactory getDataFactory() {
        return dataFactory;
    }

    public void setDataFactory(OWLDataFactory dataFactory) {
        this.dataFactory = dataFactory;
    }

    private OWLDataFactory dataFactory = null;

    public OWLOntologyManager getManager() {
        return manager;
    }

    public void setManager(OWLOntologyManager manager) {
        this.manager = manager;
    }

    private OWLOntologyManager manager = null;

    public OntologyService(String mainOntologyPath) throws Exception {
        // 1. 创建 manager
        this.manager = OWLManager.createOWLOntologyManager();
        tBoxOntology = loadOntologyFilesWithOWL(mainOntologyPath);
        dataFactory = manager.getOWLDataFactory();

        // 2. 获取前缀并注入本体
        getPrefixSpaceAndInjectToOntology(mainOntologyPath);

        // 3. 统计SWRL
        swrlCheck();
    }

    public OWLOntology mergeInFile(File tboxFile, File aboxFile)
            throws OWLOntologyCreationException {
        this.manager = OWLManager.createOWLOntologyManager();

        tBoxOntology = manager.loadOntologyFromOntologyDocument(tboxFile);
        aBoxOntology = manager.loadOntologyFromOntologyDocument(aboxFile);

        IRI mergedOntologyIRI = IRI.create(tBoxOntology.getOntologyID().getOntologyIRI().map(IRI::toString) +"_merged_total");

        // ⭐ 修正：Manager 在前，IRI 在后
        if(mergedOntology != null) //清除老的merged ontology
            manager.removeOntology(mergedOntology);
        mergedOntology = (new OWLOntologyMerger(manager)).createMergedOntology(manager, mergedOntologyIRI);
        return mergedOntology;
    }

    public OWLOntology mergeInMemory(OWLOntology tbox, OWLOntology abox)
            throws OWLOntologyCreationException {
        this.manager = OWLManager.createOWLOntologyManager();

        manager = tbox.getOWLOntologyManager();
        IRI mergedIRI = IRI.create(tBoxOntology.getOntologyID().getOntologyIRI().map(IRI::toString) +"_merged_total");

        if(mergedOntology != null) //清除老的merged ontology
            manager.removeOntology(mergedOntology);
        mergedOntology = manager.createOntology(mergedIRI);
        manager.addAxioms(mergedOntology, tbox.axioms());
        manager.addAxioms(mergedOntology, abox.axioms());

        return mergedOntology;
    }

    private void swrlCheck() {
        // 查看SWRL是否加载成功
        long ruleCount = tBoxOntology.axioms(AxiomType.SWRL_RULE).count();
        log.info("合并后 SWRL 规则数量: " + ruleCount);
        tBoxOntology.axioms(AxiomType.SWRL_RULE)
                .forEach(rule -> log.debug("{}", rule));
    }

    private void getPrefixSpaceAndInjectToOntology(String mainOntologyPath) throws IOException, OWLOntologyCreationException {
        // ================= 提取前缀映射（从 Jena 模型）并去重 =================
        OntModel jenaModel = loadOntologyMainFileWithJena(mainOntologyPath);
        Map<String, String> rawPrefixMap = jenaModel.getNsPrefixMap();

        // 去重：每个命名空间只保留第一个碰到的非空前缀（若无非空前缀则保留空字符串）
        Map<String, String> uniquePrefixMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawPrefixMap.entrySet()) {
            String prefix = entry.getKey();
            String namespace = entry.getValue();
            // 如果该命名空间尚未出现，直接添加
            if (!uniquePrefixMap.containsValue(namespace)) {
                uniquePrefixMap.put(prefix, namespace);
            } else if (!prefix.isEmpty()) {
                // 如果已经存在，但当前前缀非空且之前存的是空字符串，则替换为更友好的前缀
                uniquePrefixMap.entrySet().removeIf(e -> e.getValue().equals(namespace) && e.getKey().isEmpty());
                uniquePrefixMap.put(prefix, namespace);
            }
        }

        // 构造 DefaultPrefixManager
        this.prefixManager = new DefaultPrefixManager();
        uniquePrefixMap.forEach(this.prefixManager::setPrefix);

        IRI mergedIri = IRI.create(tBoxOntology.getOntologyID().getOntologyIRI().map(IRI::toString) +"_merged_total");
        OWLOntology totalOnt = manager.createOntology(mergedIri);
        manager.ontologies()
                .filter(ont -> !ont.equals(totalOnt))
                .forEach(ont -> ont.axioms().forEach(totalOnt::addAxiom));
        // 将去重后的前缀绑定到合并本体
        OWLDocumentFormat format = new RDFXMLDocumentFormat();
        if (format instanceof PrefixDocumentFormat) {
            ((PrefixDocumentFormat) format).setPrefixManager(this.prefixManager);
        }

        log.info("已注册前缀数量: " + uniquePrefixMap.size());
        uniquePrefixMap.forEach((k, v) -> log.debug("  {} -> {}", k, v));

        manager.setOntologyFormat(totalOnt, format);
        this.tBoxOntology = totalOnt;
    }

    private OntModel loadOntologyMainFileWithJena(String mainFile) throws IOException {
        OntModel model = OntModelFactory.createModel();
        loadOntologRestFilesWIthJena(model, mainFile, new HashSet<>());
        return model;
    }

    private void loadOntologRestFilesWIthJena(Model model, String filePath, Set<String> loaded) throws IOException {
        String absolutePath = Paths.get(filePath).toRealPath().toString();
        if (loaded.contains(absolutePath)) return;
        loaded.add(absolutePath);
        log.info("加载: " + absolutePath);
        Model temp = RDFDataMgr.loadModel(absolutePath);
        model.add(temp);
        StmtIterator iter = temp.listStatements(null, OWL.imports, (RDFNode) null);
        while (iter.hasNext()) {
            Statement st = iter.next();
            String uri = st.getResource().getURI();
            if (uri != null && uri.startsWith("file:///")) {
                String path = Paths.get(URI.create(uri)).toString();
                File f = new File(path);
                if (!f.isAbsolute()) {
                    path = new File(new File(absolutePath).getParent(), path).getAbsolutePath();
                }
                if (new File(path).exists()) {
                    loadOntologRestFilesWIthJena(model, path, loaded);
                } else{
                    log.error("警告: 导入文件不存在 - " + path);
                }
            }
        }
    }
    public OWLOntology loadOntologyFilesWithOWL(String mainFile)
            throws OWLOntologyCreationException, FileNotFoundException {
        File file = new File(mainFile);
        if (!file.exists()) {
            log.error("文件不存在, path={}", mainFile);
            throw new FileNotFoundException("文件不存在: " + mainFile);
        }
        IRI documentIRI = IRI.create(file);
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(documentIRI);

        List<OWLOntology> ontologyList = manager.ontologies().toList();
        log.info("已加载本体数: " + ontologyList.size());

        ontologyList.forEach(ont ->
                log.debug("  {}", ont.getOntologyID().getOntologyIRI()
                        .map(IRI::toString)
                        .orElse("无 IRI"))
        );
        return ontology;
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

    // ==================== 内部工具方法 ====================

    /**
     * 打印 OWLOntology 中包含的所有 ABox 三元组信息
     *
     * @param ontology 通过 loadAboxFromOntop 加载的本体对象
     */
    public static void printAboxOntology(OWLOntology ontology) {
        // 1. 空值与空本体检查
        if (ontology == null) {
            log.error("[WARN] ontology 为 null，跳过打印");
            return;
        }
        int axiomCount = ontology.getAxiomCount();
        int individualCount = ontology.getIndividualsInSignature().size();
        log.info("========== ABox 概览 ==========");
        log.info("公理(Axiom)总数: " + axiomCount);
        log.info("个体(Individual)数: " + individualCount);

        if (axiomCount == 0) {
            log.error("[WARN] ABox 为空，CONSTRUCT 查询可能未返回数据");
            log.info("===============================");
            return;
        }

        // 2. 按公理类型分组统计并逐条打印
        Map<String, Long> typeStats = ontology.getAxioms().stream()
                .collect(Collectors.groupingBy(
                        ax -> ax.getAxiomType().getName(),
                        Collectors.counting()
                ));

        log.info("\n========== 分类统计 ==========");
        typeStats.forEach((type, count) ->
                System.out.printf("  %-35s : %d%n", type, count)
        );

        log.info("\n========== 详细公理列表 ==========");
        ontology.getAxioms().forEach(ax ->
                log.debug("  " + ax)
        );

        // 3. 序列化为 Turtle 格式（便于阅读完整 RDF 结构）
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // ✅ 使用标准重载：saveOntology(OWLDocumentFormat, OutputStream)
            ontology.saveOntology(new TurtleDocumentFormat(), baos);

            String turtleContent = baos.toString(StandardCharsets.UTF_8.name());
            log.info("\n========== Turtle 序列化 ==========");
            log.info(turtleContent);
        } catch (Exception e) {
            System.err.println("[ERROR] Turtle 序列化失败: " + e.getMessage());
        }

        log.info("===================================");
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
                .collect(Collectors.toSet());

        // 2. 没有 type 三元组 → 直接报错
        if (typeAxioms.isEmpty()) {
            throw new IllegalArgumentException(
                    "❌ tempAxioms 中不存在任何 OWLClassAssertionAxiom（rdf:type） 三元组，无法确定个体类型");
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
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(String.format(
                    "❌ tempAxioms 中的 type [%s] 均不属于 <%s> 或其子类",
                    actualTypes, targetClassIri));
        }

        log.info("✅ rdf:type 校验通过，存在合法的 PizzaComponent 或其子类断言");
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
        log.info("========== ABox 个体诊断 ==========");

        // 1. 打印本体中所有的命名个体总数
        Set<OWLNamedIndividual> allIndividuals = ontology.getIndividualsInSignature();
        log.info("签名中的命名个体总数: " + allIndividuals.size());

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
                log.info("... 还有 " + (allIndividuals.size() - maxOutputNum) + " 个个体未显示 ...");
            }
        }
        log.info("===================================");
        // === 结束：打印 ABox 个体诊断信息 ===
    }

    /**
     * 判断给定 IRI 是否为 ObjectProperty
     * 直接复用本服务已加载的 tBoxOntology，零额外开销
     *
     * @param propertyIri 属性的完整 IRI 字符串
     * @return true=ObjectProperty, false=DataProperty或未声明
     */
    public boolean checkIsObjectProperty(String propertyIri) {
        if (tBoxOntology == null || dataFactory == null) {
            log.warn("checkIsObjectProperty: tBoxOntology 或 dataFactory 未初始化, 默认返回 false, iri={}", propertyIri);
            return false;
        }
        try {
            OWLObjectProperty op = dataFactory.getOWLObjectProperty(IRI.create(propertyIri));
            // containsEntityInSignature 是 O(1) 签名查找，远快于遍历公理
            return tBoxOntology.containsEntityInSignature(op);
        } catch (Exception e) {
            log.warn("checkIsObjectProperty 判断异常, 默认返回 false, iri={}, error={}", propertyIri, e.getMessage());
            return false;
        }
    }

    /**
     * 从 IRI 中提取命名空间部分。
     * 支持以 #、/、: 结尾的命名空间。
     *
     * @param iri 完整的 IRI，例如 "http://example.org/pizza/components/classes/Pizza"
     * @return 命名空间，例如 "http://example.org/pizza/components/classes/"
     * @throws IllegalArgumentException 如果 IRI 为空或无法识别命名空间分隔符
     */
    public static String extractNamespace(String iri) {
        if (iri == null || iri.isBlank()) {
            throw new IllegalArgumentException("IRI cannot be null or blank");
        }

        // 按优先级依次查找分隔符：# > / > :
        // '#' 优先于 '/'，因为 "http://example.org/ns#LocalName" 中
        // 命名空间是 "http://example.org/ns#" 而非 "http://example.org/"
        int hashIdx = iri.lastIndexOf('#');
        int slashIdx = iri.lastIndexOf('/');
        int colonIdx = iri.lastIndexOf(':');

        int separatorIdx = Math.max(hashIdx, Math.max(slashIdx, colonIdx));

        if (separatorIdx < 0) {
            throw new IllegalArgumentException("No namespace separator found in IRI: " + iri);
        }

        // 保留分隔符本身（命名空间包含末尾的 #、/ 或 :）
        return iri.substring(0, separatorIdx + 1);
    }

    /**
     * 从 IRI 字符串中提取 local name（前缀之后的部分）
     * 例如: "http://example.org/pizza#Margherita" → "Margherita"
     *       "http://example.org/ontology/hasTopping" → "hasTopping"
     */
    public static String getLocalName(String iriString) {
        if (iriString == null || iriString.isEmpty()) return iriString;
        return IRI.create(iriString).getShortForm();
    }

    @Override
    public void close() throws Exception {
        aBoxService.shutdown();
    }
}