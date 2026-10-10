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
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;
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
import org.semanticweb.owlapi.util.SimpleIRIMapper;
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

    private OWLOntology aBoxOntology = null;

    public OWLOntology gettBoxOntology() {
        return tBoxOntology;
    }

    private OWLOntology tBoxOntology = null;

    private OWLOntology mergedOntology = null;

    public OWLDataFactory getDataFactory() {
        return dataFactory;
    }

    private OWLDataFactory dataFactory = null;

    public OWLOntologyManager getManager() {
        return manager;
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

    public OWLOntology mergeInMemory(OWLOntology tbox, OWLOntology abox)
            throws OWLOntologyCreationException {
        this.manager = OWLManager.createOWLOntologyManager();

        manager = tbox.getOWLOntologyManager();
        IRI mergedIRI = IRI.create(tBoxOntology.getOntologyID().getOntologyIRI().map(IRI::toString).orElse(null) +"_merged_total");

        if(mergedOntology != null) //清除老的merged ontology
            manager.removeOntology(mergedOntology);
        mergedOntology = manager.createOntology(mergedIRI);
        manager.addAxioms(mergedOntology, tbox.axioms());
        if(abox != null)
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

        IRI mergedIri = IRI.create(tBoxOntology.getOntologyID().getOntologyIRI().map(IRI::toString).orElse(null) +"_prefix_merged_all");
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
    private OWLOntology loadOntologyFilesWithOWL(String mainFile)
            throws OWLOntologyCreationException, FileNotFoundException {
        File file = new File(mainFile);
        if (!file.exists()) {
            log.error("文件不存在, path={}", mainFile);
            throw new FileNotFoundException("文件不存在: " + mainFile);
        }

        File parentDir = file.getParentFile();
        if (parentDir != null && parentDir.isDirectory()) {

            // ========== 1. AutoIRIMapper：处理 .owl / .rdf / .xml / .omn / .ofn ==========
            AutoIRIMapper autoMapper = new AutoIRIMapper(parentDir, true);
            autoMapper.update();
            manager.getIRIMappers().add(autoMapper);
            log.info("📂 AutoIRIMapper 已启用，扫描目录: {}", parentDir.getAbsolutePath());

            // ========== 2. SimpleIRIMapper：手动注册所有 .ttl 本体 ==========
            File[] ttlFiles = parentDir.listFiles((dir, name) ->
                    name.toLowerCase().endsWith(".ttl"));
            if (ttlFiles != null && ttlFiles.length > 0) {
                OWLOntologyManager tempManager = OWLManager.createOWLOntologyManager();

                // ✅ 关键：为 tempManager 也设置缺失导入策略，避免 import 解析失败
                tempManager.getOntologyConfigurator()
                        .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);

                int ttlCount = 0;
                for (File ttlFile : ttlFiles) {
                    try {
                        // ✅ 关键修复：显式指定 Turtle 格式解析 .ttl 文件
                        OWLOntology tempOnt = tempManager.loadOntologyFromOntologyDocument(
                                new FileDocumentSource(ttlFile, new TurtleDocumentFormat())
                        );

                        Optional<IRI> ontologyIRI = tempOnt.getOntologyID().getOntologyIRI();
                        if (ontologyIRI.isPresent()) {
                            manager.getIRIMappers().add(
                                    new SimpleIRIMapper(ontologyIRI.get(), IRI.create(ttlFile))
                            );
                            log.debug("📄 TTL 映射: {} → {}", ontologyIRI.get(), ttlFile.getAbsolutePath());
                            ttlCount++;
                        } else {
                            log.warn("⚠️ TTL 缺少 owl:Ontology 声明: {}", ttlFile.getName());
                        }
                        tempManager.removeOntology(tempOnt);
                    } catch (Exception e) {
                        log.warn("⚠️ 无法解析 TTL {}: {}", ttlFile.getName(), e.getMessage());
                    }
                }
                log.info("✅ 已手动注册 {} 个 TTL 本体映射", ttlCount);
            }
            // ============================================================

        } else {
            log.warn("⚠️ 无法启用本体映射，无效目录: {}", parentDir);
        }

        // ========== 加载主本体（import 解析将自动使用上述两种 Mapper）==========
        IRI documentIRI = IRI.create(file);
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(documentIRI);

        List<OWLOntology> ontologyList = manager.ontologies().toList();
        log.info("已加载本体数: {}", ontologyList.size());

        // 打印每个已加载本体的 ontology IRI 和物理文件位置
        log.info("========== [加载验证] 已加载本体列表 ==========");
        for (OWLOntology ont : ontologyList) {
            String ontIri = ont.getOntologyID().getOntologyIRI()
                    .map(IRI::toString)
                    .orElse("(无 ontology IRI)");
            IRI docIri = manager.getOntologyDocumentIRI(ont);
            log.info("[加载验证] {}  ->  {}", ontIri, docIri);
        }
        log.info("========== [加载验证] 结束 ==========");

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

    // ✅ 必须返回 boolean，而非 void
    public static boolean validateSpecificTypeAxiom(
            Set<OWLAxiom> tempAxioms,
            OWLOntology tbox) {

        Set<OWLClassAssertionAxiom> typeAxioms = tempAxioms.stream()
                .filter(ax -> ax instanceof OWLClassAssertionAxiom)
                .map(ax -> (OWLClassAssertionAxiom) ax)
                .collect(Collectors.toSet());
        /*
        if (typeAxioms.isEmpty()) {
            log.error("⚠️ 未找到任何 rdf:type 断言，必须包含至少一条合法的 rdf:type 声明");
            return false; // 无类型断言视为合法
        }*/

        boolean allValid = typeAxioms.stream().allMatch(ax -> {
            OWLIndividual individual = ax.getIndividual();
            if (individual.isAnonymous()) {
                log.error("❌ 非法: 匿名个体不允许出现在 ClassAssertion 中 -> " + ax);
                return false;
            }

            OWLClassExpression classExpr = ax.getClassExpression();
            if (!classExpr.isOWLClass()) {
                log.error("❌ 非法: ClassAssertion 的类必须是命名类，而非复杂表达式 -> " + ax);
                return false;
            }

            IRI classIRI = classExpr.asOWLClass().getIRI();
            if (!tbox.getClassesInSignature().stream()
                    .anyMatch(c -> c.getIRI().equals(classIRI))) {
                log.error("❌ 非法: 类 " + classIRI.getShortForm()
                        + " (" + classIRI + ") 未在 TBox 中定义，无法作为 rdf:type 的目标");
                return false;
            }

            return true;
        });

        if (allValid) {
            log.info("✅ 所有 rdf:type 断言均合法且在 TBox 中有定义，共 " + typeAxioms.size() + " 条");
        } else {
            log.info("❌ 存在非法的 rdf:type 断言，请检查上方错误日志");
        }

        return allValid; // ← 关键：将校验结果返回给调用方
    }


    private void persistToDatabase(List<Triple> triples) {
        // TODO: 通过JDBC或Ontop UPDATE接口写入MySQL
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