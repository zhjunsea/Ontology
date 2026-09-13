package org.junzhang.ontologymachine;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.vocab.PrefixOWLOntologyFormat;

import java.io.*;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReasonerService implements AutoCloseable {

    private static volatile ReasonerService instance;
    private static final Object lock = new Object();

    private final OWLOntology ontology;
    private final OWLReasoner reasoner;
    private static boolean debug = false;

    // 私有构造函数（原逻辑不变）
    private ReasonerService(String mainOntologyPath, String mappingDirs) throws Exception {
        OntModel jenaModel = loadOntology(mainOntologyPath);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        jenaModel.write(out, "RDF/XML");
        byte[] mergedBytes = out.toByteArray();

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        this.ontology = manager.loadOntologyFromOntologyDocument(
                new java.io.ByteArrayInputStream(mergedBytes));
        System.out.println("本体加载完成: " + ontology.getOntologyID());

        OWLReasonerFactory factory = new ReasonerFactory();
        this.reasoner = factory.createReasoner(ontology);
        this.reasoner.precomputeInferences(
                InferenceType.CLASS_HIERARCHY,
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.DATA_PROPERTY_ASSERTIONS);
    }

    /** 获取单例实例 */
    public static ReasonerService getInstance(String mainOntologyPath, String mappingDirs) throws Exception {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ReasonerService(mainOntologyPath, mappingDirs);
                }
            }
        }
        return instance;
    }

    // ================= 本体加载（原方法保留） =================
    private OntModel loadOntology(String mainFile) throws IOException {
        OntModel model = OntModelFactory.createModel();
        loadRecursive(model, mainFile, new HashSet<>());
        return model;
    }

    private void loadRecursive(Model model, String filePath, Set<String> loaded) throws IOException {
        String absolutePath = Paths.get(filePath).toRealPath().toString();
        if (loaded.contains(absolutePath)) return;
        loaded.add(absolutePath);
        if (debug) System.out.println("加载: " + absolutePath);
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
                    loadRecursive(model, path, loaded);
                } else if (debug) {
                    System.out.println("警告: 导入文件不存在 - " + path);
                }
            }
        }
    }
    /**
     * 反向查询（基于类层次上升）。
     * 格式：["起点IRI", "最终属性IRI"]   （中间属性链不再使用）
     * 从拥有最终属性的实体出发，沿父类链/个体所属类逐级上升，直到匹配起点。
     *
     * @param chainJson JSON 数组，例如 ["http://ex.org/NeapolitanPizza","http://ex.org/process/duration"]
     * @param debug     是否打印过程
     * @return 第一个匹配实体的最终属性值，未找到返回 null
     */
    /**
     * 反向属性链查询（基于公理，不依赖推理机）。
     * 格式：["起点IRI", "对象属性1", ..., "对象属性N", "最终属性IRI"]
     * 从最终属性出发，找到拥有该属性的主体个体，然后逆序穿越对象属性链，最后匹配起点（类或个体）。
     */
    public String reverseQueryPropertyChain(String chainJson, boolean debug) {
        List<String> iris = parseJsonArray(chainJson);
        if (iris.size() < 2) {
            System.err.println("IRI 链长度至少为2");
            return null;
        }

        String startIRI = iris.get(0);
        String finalPropIRI = iris.get(iris.size() - 1);
        List<String> middlePropIRIs = iris.subList(1, iris.size() - 1);
        Collections.reverse(middlePropIRIs); // 逆序，从后往前遍历属性链

        IRI startIri = IRI.create(startIRI);
        IRI finalPropIri = IRI.create(finalPropIRI);
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();

        String startType = getEntityType(ontology, startIri);
        if (debug) {
            System.out.println("反向查询起点: " + startIRI + " 类型: " + startType);
            System.out.println("中间属性链(逆序): " + middlePropIRIs);
            System.out.println("最终属性: " + finalPropIRI);
        }

        // 1. 收集所有具有最终属性的主体个体及其值
        Map<OWLNamedIndividual, Set<OWLLiteral>> subjectsWithValues = new LinkedHashMap<>();
        if ("DataProperty".equals(getEntityType(ontology, finalPropIri))) {
            OWLDataProperty dp = df.getOWLDataProperty(finalPropIri);
            for (OWLDataPropertyAssertionAxiom ax : ontology.axioms(AxiomType.DATA_PROPERTY_ASSERTION).collect(Collectors.toSet())) {
                if (ax.getProperty().equals(dp) && ax.getSubject().isNamed()) {
                    OWLNamedIndividual sub = ax.getSubject().asOWLNamedIndividual();
                    subjectsWithValues.computeIfAbsent(sub, k -> new HashSet<>()).add(ax.getObject());
                }
            }
        } else if ("ObjectProperty".equals(getEntityType(ontology, finalPropIri))) {
            OWLObjectProperty op = df.getOWLObjectProperty(finalPropIri);
            for (OWLObjectPropertyAssertionAxiom ax : ontology.axioms(AxiomType.OBJECT_PROPERTY_ASSERTION).collect(Collectors.toSet())) {
                if (ax.getProperty().equals(op) && ax.getSubject().isNamed() && ax.getObject().isNamed()) {
                    OWLNamedIndividual sub = ax.getSubject().asOWLNamedIndividual();
                    OWLLiteral val = df.getOWLLiteral(ax.getObject().asOWLNamedIndividual().getIRI().getIRIString());
                    subjectsWithValues.computeIfAbsent(sub, k -> new HashSet<>()).add(val);
                }
            }
        } else {
            System.err.println("不支持的最终属性类型");
            return null;
        }

        if (subjectsWithValues.isEmpty()) {
            if (debug) System.out.println("没有个体拥有该属性。");
            return null;
        }

        if (debug) {
            System.out.println("具有该属性的个体数: " + subjectsWithValues.size());
        }

        // 2. 对每个主体个体，逆序穿越属性链
        for (Map.Entry<OWLNamedIndividual, Set<OWLLiteral>> entry : subjectsWithValues.entrySet()) {
            OWLNamedIndividual current = entry.getKey();
            if (debug) {
                System.out.println("\n尝试主体: " + current.getIRI().getShortForm());
            }

            Set<OWLNamedIndividual> frontier = new HashSet<>();
            frontier.add(current);
            boolean pathExists = true;

            for (String propIRI : middlePropIRIs) {
                IRI pIRI = IRI.create(propIRI);
                OWLObjectProperty prop = df.getOWLObjectProperty(pIRI);
                Set<OWLNamedIndividual> newFrontier = new HashSet<>();
                for (OWLNamedIndividual ind : frontier) {
                    // 查找通过该属性指向 ind 的所有主体（即逆属性）
                    Set<OWLNamedIndividual> predecessors = getPredecessors(ind, prop);
                    if (debug) {
                        System.out.println("  逆属性 " + propIRI + " 从 " + ind.getIRI().getShortForm() +
                                " 获得 " + predecessors.size() + " 个前驱");
                    }
                    newFrontier.addAll(predecessors);
                }
                frontier = newFrontier;
                if (frontier.isEmpty()) {
                    if (debug) System.out.println("  此路径中断");
                    pathExists = false;
                    break;
                }
            }

            if (!pathExists) continue;

            // 3. 检查前驱集合中是否有匹配起点的实体
            for (OWLNamedIndividual predecessor : frontier) {
                if (matchesStartByAxioms(predecessor, startIri, startType)) {
                    if (debug) {
                        System.out.println("匹配起点: " + predecessor.getIRI().getShortForm());
                    }
                    return entry.getValue().iterator().next().getLiteral();
                }
            }
        }
        return null;
    }

    // 获取通过给定对象属性指向目标个体的所有主体（基于公理）
    private Set<OWLNamedIndividual> getPredecessors(OWLNamedIndividual target, OWLObjectProperty prop) {
        Set<OWLNamedIndividual> predecessors = new HashSet<>();
        for (OWLObjectPropertyAssertionAxiom ax : ontology.axioms(AxiomType.OBJECT_PROPERTY_ASSERTION).collect(Collectors.toSet())) {
            if (ax.getProperty().equals(prop) && ax.getObject().equals(target) && ax.getSubject().isNamed()) {
                predecessors.add(ax.getSubject().asOWLNamedIndividual());
            }
        }
        return predecessors;
    }

    // 判断个体是否匹配起点（类或个体），基于公理和父类递归
    private boolean matchesStartByAxioms(OWLNamedIndividual individual, IRI targetIri, String targetType) {
        if ("Individual".equals(targetType)) {
            return individual.getIRI().equals(targetIri);
        } else if ("Class".equals(targetType)) {
            // 获取个体直接声明的类型
            Set<OWLClass> types = getDirectTypes(individual);
            for (OWLClass type : types) {
                if (isClassOrSuperclass(type, targetIri)) return true;
            }
            return false;
        }
        return false;
    }

    // 直接类型（ClassAssertion）
    private Set<OWLClass> getDirectTypes(OWLNamedIndividual individual) {
        Set<OWLClass> types = new HashSet<>();
        for (OWLClassAssertionAxiom ax : ontology.axioms(AxiomType.CLASS_ASSERTION).collect(Collectors.toSet())) {
            if (ax.getIndividual().equals(individual) && ax.getClassExpression().isOWLClass()) {
                types.add(ax.getClassExpression().asOWLClass());
            }
        }
        return types;
    }

    // 递归判断类是否为 targetIri 类或其直接/间接父类（基于 SubClassOf 公理）
    private boolean isClassOrSuperclass(OWLClass cls, IRI targetIri) {
        if (cls.getIRI().equals(targetIri)) return true;
        Set<OWLClass> parents = new HashSet<>();
        for (OWLSubClassOfAxiom ax : ontology.axioms(AxiomType.SUBCLASS_OF).collect(Collectors.toSet())) {
            if (ax.getSubClass().equals(cls) && ax.getSuperClass().isOWLClass()) {
                parents.add(ax.getSuperClass().asOWLClass());
            }
        }
        for (OWLClass parent : parents) {
            if (isClassOrSuperclass(parent, targetIri)) return true;
        }
        return false;
    }

    /**
     * 递归沿类层次上升，判断实体是否能匹配目标。
     * @param entity    当前实体（个体或类）
     * @param targetIri 目标起点 IRI
     * @param targetType 目标类型
     * @param debug     是否输出调试信息
     * @return 是否匹配
     */
    private boolean matchByClimbing(OWLEntity entity, IRI targetIri, String targetType, boolean debug) {
        // 检查当前实体是否直接匹配
        if (entity.getIRI().equals(targetIri)) return true;
        if ("Class".equals(targetType) && entity.isOWLClass() && entity.asOWLClass().getIRI().equals(targetIri)) return true;
        if ("Individual".equals(targetType) && entity.isOWLNamedIndividual() && entity.asOWLNamedIndividual().getIRI().equals(targetIri)) return true;

        // 如果目标是个体，则类不可能通过上升变成个体，直接失败
        if ("Individual".equals(targetType) && entity.isOWLClass()) return false;

        // 上升：如果当前是个体，上升到其所属的类
        if (entity.isOWLNamedIndividual()) {
            OWLNamedIndividual ind = entity.asOWLNamedIndividual();
            Set<OWLClass> types = reasoner.getTypes(ind, true).entities()
                    .filter(c -> !c.isOWLThing())
                    .collect(Collectors.toSet());
            if (debug) {
                System.out.println("个体 " + ind.getIRI().getShortForm() + " 的类型: " +
                        types.stream().map(c -> c.getIRI().getShortForm()).collect(Collectors.toList()));
            }
            for (OWLClass cls : types) {
                if (matchByClimbing(cls, targetIri, targetType, debug)) return true;
            }
        }
        // 如果当前是类，上升到父类
        else if (entity.isOWLClass()) {
            OWLClass cls = entity.asOWLClass();
            Set<OWLClass> superClasses = reasoner.getSuperClasses(cls, true).entities()
                    .filter(c -> !c.isOWLThing())
                    .collect(Collectors.toSet());
            if (debug) {
                System.out.println("类 " + cls.getIRI().getShortForm() + " 的父类: " +
                        superClasses.stream().map(c -> c.getIRI().getShortForm()).collect(Collectors.toList()));
            }
            for (OWLClass sup : superClasses) {
                if (matchByClimbing(sup, targetIri, targetType, debug)) return true;
            }
        }
        return false;
    }

    // 辅助方法：判断个体是否匹配起点（类或个体）
    private boolean matchesStart(OWLNamedIndividual individual, IRI startIRI, String startType) {
        if ("Class".equals(startType)) {
            OWLClass cls = ontology.getOWLOntologyManager().getOWLDataFactory().getOWLClass(startIRI);
            return reasoner.getTypes(individual, true).entities().anyMatch(c -> c.equals(cls));
        } else if ("Individual".equals(startType)) {
            return individual.getIRI().equals(startIRI);
        }
        return false;
    }

    // ================= 推理 API（保留） =================
    public boolean isConsistent() { return reasoner.isConsistent(); }

    public Set<OWLClass> getTypes(String individualIRI) {
        OWLNamedIndividual ind = getIndividual(individualIRI);
        return reasoner.getTypes(ind, true).entities()
                .filter(cls -> !cls.isOWLThing())
                .collect(Collectors.toSet());
    }

    public IRI resolveFullIRI(OWLOntology ontology, IRI iri) {
        // 原有实现保留
        String iriString = iri.getIRIString();
        if (!iriString.startsWith("http://") && !iriString.startsWith("https://") &&
                !iriString.startsWith("file:/") && !iriString.startsWith("urn:") &&
                iriString.contains(":")) {
            OWLOntologyManager manager = ontology.getOWLOntologyManager();
            OWLDocumentFormat format = manager.getOntologyFormat(ontology);
            if (format instanceof PrefixOWLOntologyFormat) {
                PrefixOWLOntologyFormat prefixFormat = (PrefixOWLOntologyFormat) format;
                String prefixName = iriString.substring(0, iriString.indexOf(":") + 1);
                String localName = iriString.substring(iriString.indexOf(":") + 1);
                String namespace = prefixFormat.getPrefix(prefixName);
                if (namespace != null) {
                    return IRI.create(namespace + localName);
                }
            }
            if (iriString.startsWith("proc:")) {
                return IRI.create("http://example.org/pizza/process/" + iriString.substring(5));
            }
            if (iriString.startsWith("pizza:")) {
                return IRI.create("http://example.org/pizza/" + iriString.substring(6));
            }
        }
        return iri;
    }

    public String getLabel(OWLOntology ontology, IRI iri, String lang) {
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
        OWLAnnotationProperty rdfsLabel = df.getRDFSLabel();
        List<OWLLiteral> labels = new ArrayList<>();
        ontology.getEntitiesInSignature(iri).forEach(entity -> {
            try (Stream<OWLAnnotationAssertionAxiom> stream = EntitySearcher.getAnnotationAssertionAxioms(entity, ontology)) {
                stream.filter(ax -> ax.getProperty().equals(rdfsLabel))
                        .map(OWLAnnotationAssertionAxiom::getValue)
                        .filter(val -> val instanceof OWLLiteral)
                        .map(val -> (OWLLiteral) val)
                        .forEach(labels::add);
            }
        });
        if (labels.isEmpty()) {
            ontology.annotationAssertionAxioms(iri)
                    .filter(ax -> ax.getProperty().equals(rdfsLabel))
                    .map(OWLAnnotationAssertionAxiom::getValue)
                    .filter(val -> val instanceof OWLLiteral)
                    .map(val -> (OWLLiteral) val)
                    .forEach(labels::add);
        }
        if (labels.isEmpty()) {
            System.out.println("【调试】未找到 IRI: " + iri + " 的标签。");
            return null;
        }
        if (lang != null && !lang.trim().isEmpty()) {
            Optional<String> langLabel = labels.stream()
                    .filter(lit -> lit.hasLang(lang))
                    .map(OWLLiteral::getLiteral)
                    .findFirst();
            if (langLabel.isPresent()) return langLabel.get();
        }
        Optional<String> noLangLabel = labels.stream()
                .filter(lit -> !lit.hasLang())
                .map(OWLLiteral::getLiteral)
                .findFirst();
        if (noLangLabel.isPresent()) return noLangLabel.get();
        return labels.stream().map(OWLLiteral::getLiteral).findFirst().orElse(null);
    }

    public Set<OWLNamedIndividual> getInstances(String classIRI) {
        OWLClass cls = getClass(classIRI);
        return reasoner.getInstances(cls, true).entities().collect(Collectors.toSet());
    }

    public void printAllInferredTypes() {
        System.out.println("===== 推理后的类型断言 =====");
        ontology.individualsInSignature().forEach(ind -> {
            System.out.println("个体: " + ind.getIRI().getIRIString());
            reasoner.getTypes(ind, true).entities()
                    .filter(cls -> !cls.isOWLThing())
                    .forEach(cls -> System.out.println("  └─ 类型: " + cls.getIRI().getIRIString()));
        });
    }

    public OWLReasoner getReasoner() { return reasoner; }

    // ================= 属性查询 API（保留） =================
    public Set<OWLProperty> getSpecifiedPropertiesOfClass(String classIRI, Set<String> propertyIRIs) {
        OWLClass cls = getClass(classIRI);
        Set<OWLProperty> result = new HashSet<>();
        for (String iri : propertyIRIs) {
            ontology.objectPropertiesInSignature()
                    .filter(p -> p.getIRI().getIRIString().equals(iri))
                    .findFirst().ifPresent(result::add);
            ontology.dataPropertiesInSignature()
                    .filter(p -> p.getIRI().getIRIString().equals(iri))
                    .findFirst().ifPresent(result::add);
        }
        return result;
    }

    public Set<OWLProperty> getAllPropertiesOfClass(String classIRI) {
        OWLClass cls = getClass(classIRI);
        Set<OWLProperty> result = new HashSet<>();
        Set<OWLClass> relevantClasses = reasoner.getSuperClasses(cls, false).entities().collect(Collectors.toSet());
        relevantClasses.add(cls);
        ontology.objectPropertiesInSignature().forEach(prop -> {
            reasoner.getObjectPropertyDomains(prop, true).entities().forEach(domain -> {
                if (relevantClasses.contains(domain)) result.add(prop);
            });
        });
        ontology.dataPropertiesInSignature().forEach(prop -> {
            reasoner.getDataPropertyDomains(prop, true).entities().forEach(domain -> {
                if (relevantClasses.contains(domain)) result.add(prop);
            });
        });
        return result;
    }

    public Map<String, Set<String>> getSpecifiedPropertiesOfIndividual(String individualIRI, Set<String> propertyIRIs) {
        OWLNamedIndividual ind = getIndividual(individualIRI);
        Map<String, Set<String>> result = new HashMap<>();
        for (String iri : propertyIRIs) {
            Optional<OWLDataProperty> dataProp = ontology.dataPropertiesInSignature()
                    .filter(p -> p.getIRI().getIRIString().equals(iri)).findFirst();
            if (dataProp.isPresent()) {
                Set<OWLLiteral> values = reasoner.getDataPropertyValues(ind, dataProp.get());
                if (!values.isEmpty())
                    result.put(iri, values.stream().map(OWLLiteral::getLiteral).collect(Collectors.toSet()));
                continue;
            }
            Optional<OWLObjectProperty> objProp = ontology.objectPropertiesInSignature()
                    .filter(p -> p.getIRI().getIRIString().equals(iri)).findFirst();
            if (objProp.isPresent()) {
                Set<OWLNamedIndividual> values = reasoner.getObjectPropertyValues(ind, objProp.get()).entities().collect(Collectors.toSet());
                if (!values.isEmpty())
                    result.put(iri, values.stream().map(i -> i.getIRI().getIRIString()).collect(Collectors.toSet()));
            }
        }
        return result;
    }

    public Map<String, Set<String>> getAllPropertiesOfIndividual(String individualIRI) {
        OWLNamedIndividual ind = getIndividual(individualIRI);
        Map<String, Set<String>> result = new HashMap<>();
        ontology.dataPropertiesInSignature().forEach(prop -> {
            Set<OWLLiteral> values = reasoner.getDataPropertyValues(ind, prop);
            if (!values.isEmpty()) {
                Set<String> valueStrings = values.stream().map(OWLLiteral::getLiteral).collect(Collectors.toSet());
                result.put(prop.getIRI().getIRIString(), valueStrings);
            }
        });
        ontology.objectPropertiesInSignature().forEach(prop -> {
            Set<OWLNamedIndividual> values = reasoner.getObjectPropertyValues(ind, prop).entities().collect(Collectors.toSet());
            if (!values.isEmpty()) {
                Set<String> valueStrings = values.stream().map(i -> i.getIRI().getIRIString()).collect(Collectors.toSet());
                result.put(prop.getIRI().getIRIString(), valueStrings);
            }
        });
        return result;
    }

    private OWLNamedIndividual getIndividual(String individualIRI) {
        return ontology.individualsInSignature()
                .filter(i -> i.getIRI().getIRIString().equals(individualIRI))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("个体未找到: " + individualIRI));
    }

    private OWLClass getClass(String classIRI) {
        return ontology.classesInSignature()
                .filter(c -> c.getIRI().getIRIString().equals(classIRI))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("类未找到: " + classIRI));
    }

    // ================= 新增：属性链查询 =================
    /**
     * 根据 JSON 数组格式的 IRI 链查询最终数据属性的值。
     * 格式：["起始IRI", "对象属性IRI1", "对象属性IRI2", ..., "最终数据属性IRI"]
     * 起始可以是 OWL 类或个体。返回第一个找到的字面量值，未找到返回 null。
     */
    /**
     * 根据 IRI 链智能查询最终值。
     * 格式：["起始IRI", "中间对象属性IRI...", "最终属性/类IRI"]
     * 会判断每个 IRI 的类型并采取相应策略。
     *
     * @param chainJson JSON 数组，例如 ["http://ex.org/NeapolitanPizza","http://ex.org/hasProcessStep","http://ex.org/process/duration"]
     * @param debug     是否输出详细搜索路径
     * @return 查询到的字符串值，未找到返回 null
     */
    public String queryPropertyChain(String chainJson, boolean debug) {
        List<String> iris = parseJsonArray(chainJson);
        if (iris.size() < 2) {
            System.err.println("IRI 链长度至少为2");
            return null;
        }

        // 逐项 IRI 及其类型
        List<IRI> iriList = iris.stream().map(IRI::create).collect(Collectors.toList());
        List<String> typeList = iriList.stream().map(i -> getEntityType(ontology, i)).collect(Collectors.toList());

        if (debug) {
            System.out.println("===== 属性链智能查询 =====");
            for (int i = 0; i < iris.size(); i++) {
                System.out.println("  [" + i + "] " + iris.get(i) + " -> 类型: " + typeList.get(i));
            }
        }

        // 1. 处理起点
        Set<OWLNamedIndividual> currentIndividuals = resolveStart(iriList.get(0), typeList.get(0), debug);
        if (currentIndividuals.isEmpty()) {
            if (debug) System.out.println("起点未能获得任何个体，查询终止。");
            return null;
        }

        // 2. 遍历中间属性
        for (int i = 1; i < iris.size() - 1; i++) {
            IRI propIRI = iriList.get(i);
            String propType = typeList.get(i);

            if (debug) {
                System.out.println("中间步骤 " + i + " : 属性 " + propIRI + " 类型 " + propType +
                        " | 当前个体数 " + currentIndividuals.size());
            }

            if (!"ObjectProperty".equals(propType)) {
                System.err.println("中间属性只支持对象属性，但 " + propIRI + " 的类型是 " + propType);
                return null;
            }

            OWLObjectProperty objProp = ontology.getOWLOntologyManager().getOWLDataFactory()
                    .getOWLObjectProperty(propIRI);
            Set<OWLNamedIndividual> next = new HashSet<>();
            for (OWLNamedIndividual ind : currentIndividuals) {
                Set<OWLNamedIndividual> values = reasoner.getObjectPropertyValues(ind, objProp)
                        .entities().collect(Collectors.toSet());
                if (debug && !values.isEmpty()) {
                    System.out.println("    " + ind.getIRI().getShortForm() + " -> " +
                            values.stream().map(v -> v.getIRI().getShortForm()).collect(Collectors.toList()));
                }
                next.addAll(values);
            }
            currentIndividuals = next;
            if (currentIndividuals.isEmpty()) {
                if (debug) System.out.println("无后续个体，停止。");
                break;
            }
        }

        // 3. 处理终点
        IRI endIRI = iriList.get(iris.size() - 1);
        String endType = typeList.get(iris.size() - 1);

        if (debug) {
            System.out.println("终点 " + endIRI + " 类型 " + endType +
                    " | 当前个体数 " + currentIndividuals.size());
        }

        if (currentIndividuals.isEmpty()) return null;

        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();

        switch (endType) {
            case "DataProperty":
                OWLDataProperty dataProp = df.getOWLDataProperty(endIRI);
                for (OWLNamedIndividual ind : currentIndividuals) {
                    Set<OWLLiteral> values = reasoner.getDataPropertyValues(ind, dataProp);
                    if (!values.isEmpty()) {
                        String val = values.iterator().next().getLiteral();
                        if (debug) System.out.println("数据属性值: " + val);
                        return val;
                    }
                }
                break;

            case "ObjectProperty":
                // 返回第一个个体通过该对象属性关联到的第一个个体 IRI
                OWLObjectProperty objProp = df.getOWLObjectProperty(endIRI);
                for (OWLNamedIndividual ind : currentIndividuals) {
                    Set<OWLNamedIndividual> vals = reasoner.getObjectPropertyValues(ind, objProp).entities().collect(Collectors.toSet());
                    if (!vals.isEmpty()) {
                        String val = vals.iterator().next().getIRI().getIRIString();
                        if (debug) System.out.println("对象属性目标个体 IRI: " + val);
                        return val;
                    }
                }
                break;

            case "Class":
                // 返回当前个体集合中第一个个体的类型（推理机给出的直接类）
                for (OWLNamedIndividual ind : currentIndividuals) {
                    Set<OWLClass> types = reasoner.getTypes(ind, true).entities()
                            .filter(c -> !c.isOWLThing())
                            .collect(Collectors.toSet());
                    if (!types.isEmpty()) {
                        String val = types.iterator().next().getIRI().getIRIString();
                        if (debug) System.out.println("个体 " + ind.getIRI().getShortForm() + " 的类型: " + val);
                        return val;
                    }
                }
                break;

            default:
                System.err.println("不支持的终点类型: " + endType);
                return null;
        }

        return null;
    }

    // 智能解析起点：类 → 所有实例 + owl:hasValue 个体；个体 → 自身
    private Set<OWLNamedIndividual> resolveStart(IRI startIRI, String startType, boolean debug) {
        Set<OWLNamedIndividual> result = new LinkedHashSet<>();
        Set<IRI> visited = new HashSet<>();
        Queue<IRI> queue = new LinkedList<>();
        queue.add(startIRI);

        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();

        while (!queue.isEmpty()) {
            IRI currentIRI = queue.poll();
            if (!visited.add(currentIRI)) continue;

            String type = getEntityType(ontology, currentIRI);
            if (debug) {
                System.out.println("处理实体: " + currentIRI.getShortForm() + " 类型: " + type);
            }

            if ("Class".equals(type)) {
                OWLClass cls = df.getOWLClass(currentIRI);
                // 收集该类及其所有子类
                Set<OWLClass> allClasses = new HashSet<>();
                allClasses.add(cls);
                try {
                    Set<OWLClass> subClasses = reasoner.getSubClasses(cls, true).entities()
                            .filter(c -> !c.isOWLNothing())
                            .collect(Collectors.toSet());
                    allClasses.addAll(subClasses);
                } catch (Exception e) {
                    // 回退：仅使用公理中的直接子类
                    for (OWLSubClassOfAxiom ax : ontology.axioms(AxiomType.SUBCLASS_OF).collect(Collectors.toSet())) {
                        if (ax.getSuperClass().equals(cls) && ax.getSubClass().isOWLClass()) {
                            allClasses.add(ax.getSubClass().asOWLClass());
                        }
                    }
                }

                // 对每个类，收集实例和通过 owl:hasValue 关联的个体
                for (OWLClass c : allClasses) {
                    // 1) owl:hasValue 个体
                    for (OWLSubClassOfAxiom ax : ontology.axioms(AxiomType.SUBCLASS_OF).collect(Collectors.toSet())) {
                        if (ax.getSubClass().isOWLClass() && ax.getSubClass().asOWLClass().getIRI().equals(c.getIRI())) {
                            if (ax.getSuperClass() instanceof OWLObjectHasValue hasVal) {
                                if (hasVal.getFiller().isNamed()) {
                                    OWLNamedIndividual ind = hasVal.getFiller().asOWLNamedIndividual();
                                    if (result.add(ind)) {
                                        queue.add(ind.getIRI()); // 递归处理该个体
                                    }
                                }
                            }
                        }
                    }

                    // 2) 推理机实例（或公理实例）
                    Set<OWLNamedIndividual> instances = new HashSet<>();
                    try {
                        instances = reasoner.getInstances(c, true).entities().collect(Collectors.toSet());
                    } catch (Exception e) {
                        // 回退：显式 ClassAssertion
                        for (OWLClassAssertionAxiom ax : ontology.axioms(AxiomType.CLASS_ASSERTION).collect(Collectors.toSet())) {
                            if (ax.getClassExpression().equals(c) && ax.getIndividual().isNamed()) {
                                instances.add(ax.getIndividual().asOWLNamedIndividual());
                            }
                        }
                    }
                    for (OWLNamedIndividual ind : instances) {
                        if (result.add(ind)) {
                            queue.add(ind.getIRI());
                        }
                    }
                }

            } else if ("Individual".equals(type)) {
                // 将个体自身加入结果
                OWLNamedIndividual ind = df.getOWLNamedIndividual(currentIRI);
                result.add(ind);

                // 获取该个体的直接类型，并加入队列，以便发现类关联的其他个体
                Set<OWLClass> types = new HashSet<>();
                try {
                    types = reasoner.getTypes(ind, true).entities()
                            .filter(c -> !c.isOWLThing())
                            .collect(Collectors.toSet());
                } catch (Exception e) {
                    for (OWLClassAssertionAxiom ax : ontology.axioms(AxiomType.CLASS_ASSERTION).collect(Collectors.toSet())) {
                        if (ax.getIndividual().equals(ind) && ax.getClassExpression().isOWLClass()) {
                            types.add(ax.getClassExpression().asOWLClass());
                        }
                    }
                }
                for (OWLClass cls : types) {
                    queue.add(cls.getIRI());
                }

            } else {
                // 其他类型（属性、数据类型）暂不处理，可以忽略
                if (debug) {
                    System.out.println("  跳过不支持的类型: " + type);
                }
            }
        }

        if (debug) {
            System.out.println("最终收集到 " + result.size() + " 个关联个体");
        }
        return result;
    }

    // 辅助方法：智能获取起始个体
    private Set<OWLNamedIndividual> getStartIndividualsIntelligent(String startIRI, boolean debug) {
        Set<OWLNamedIndividual> result = new HashSet<>();
        IRI iri = IRI.create(startIRI);
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();

        if (ontology.containsClassInSignature(iri)) {
            OWLClass cls = df.getOWLClass(iri);
            // 1. 从类的 SubClassOf 公理中提取 owl:hasValue 个体
            for (OWLSubClassOfAxiom ax : ontology.axioms(AxiomType.SUBCLASS_OF).collect(Collectors.toSet())) {
                if (ax.getSubClass().isOWLClass() && ax.getSubClass().asOWLClass().getIRI().equals(iri)) {
                    if (ax.getSuperClass() instanceof OWLObjectHasValue hasVal) {
                        if (hasVal.getFiller().isNamed()) {
                            result.add(hasVal.getFiller().asOWLNamedIndividual());
                        }
                    }
                }
            }
            if (debug) System.out.println("从 owl:hasValue 获得 " + result.size() + " 个个体");
            // 2. 获取所有已知实例（推理机）
            Set<OWLNamedIndividual> instances = reasoner.getInstances(cls, true).entities().collect(Collectors.toSet());
            result.addAll(instances);
            if (debug) System.out.println("从推理机实例获得 " + instances.size() + " 个个体，合并后共 " + result.size() + " 个");
        } else if (ontology.containsIndividualInSignature(iri)) {
            result.add(df.getOWLNamedIndividual(iri));
            if (debug) System.out.println("起始为个体，直接使用");
        } else {
            if (debug) System.out.println("起始实体类型不支持");
        }
        return result;
    }

    private List<String> parseJsonArray(String json) {
        String trimmed = json.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
            if (trimmed.isEmpty()) return Collections.emptyList();
            return Arrays.stream(trimmed.split(","))
                    .map(s -> s.trim().replace("\"", ""))
                    .collect(Collectors.toList());
        }
        throw new IllegalArgumentException("无法解析的 JSON 数组: " + json);
    }

    public String getEntityType(OWLOntology ontology, IRI iri) {
        if (ontology.containsClassInSignature(iri)) {
            return "Class";
        }
        if (ontology.containsIndividualInSignature(iri)) {
            return "Individual";
        }
        if (ontology.containsObjectPropertyInSignature(iri)) {
            return "ObjectProperty";
        }
        if (ontology.containsDataPropertyInSignature(iri)) {
            return "DataProperty";
        }
        if (ontology.containsAnnotationPropertyInSignature(iri)) {
            return "AnnotationProperty";
        }
        if (ontology.containsDatatypeInSignature(iri)) {
            return "Datatype";
        }
        return "Unknown";
    }

    @Override
    public void close() { reasoner.dispose(); }
}