package org.junzhang.ontologymachine;

import com.clarkparsia.owlapi.explanation.BlackBoxExplanation;
import com.clarkparsia.owlapi.explanation.HSTExplanationGenerator;
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.impl.blackbox.checker.InconsistentOntologyExplanationGeneratorFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.DefaultPrefixManager;

import java.io.*;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReasonerService implements AutoCloseable {

    private static volatile ReasonerService instance;
    private static final Object lock = new Object();

    private final OWLOntologyManager manager;       // <-- 新增
    private final OWLOntology ontology;
    private final OWLReasoner reasoner;
    private static boolean debug = false;
    private final DefaultPrefixManager prefixManager;;

    // ================= 构造函数 ================
    private ReasonerService(String mainOntologyPath) throws Exception {
        // 1. 创建 manager
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        this.manager = manager;

        OWLOntology loadedOntology = loadOntologyWithoutRecursive(mainOntologyPath, manager);

        if (loadedOntology == null) {
            // 备用方案：用 Jena 加载并转换
            OntModel jenaModel = loadOntology(mainOntologyPath);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            jenaModel.write(out, "RDF/XML");
            byte[] mergedBytes = out.toByteArray();

            loadedOntology = manager.loadOntologyFromOntologyDocument(
                    new java.io.ByteArrayInputStream(mergedBytes));
        }

        // 2. 合并所有本体（入口 + 所有导入）
        IRI mergedIri = IRI.create("http://example.org/pizza/merged_total");
        OWLOntology totalOnt = manager.createOntology(mergedIri);
        for (OWLOntology ont : manager.getOntologies()) {
            totalOnt.addAxioms(ont.getAxioms());
        }
        this.ontology = totalOnt;

        // ================= 提取前缀映射 =================
        OntModel jenaModel = loadOntology(mainOntologyPath);
        Map<String, String> nsMap = jenaModel.getNsPrefixMap();
        this.prefixManager = new DefaultPrefixManager();
        for (Map.Entry<String, String> entry : nsMap.entrySet()) {
            this.prefixManager.setPrefix(entry.getKey(), entry.getValue());
        }
        System.out.println("已注册前缀数量: " + nsMap.size());
        nsMap.forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        // 3. 创建推理机
        ReasonerFactory factory = new ReasonerFactory();
        Reasoner tmpReasoner = (Reasoner) factory.createReasoner(totalOnt);
        tmpReasoner.flush();

        // ================= 一致性检查与解释生成 =================
        if (!tmpReasoner.isConsistent()) {
            System.err.println("本体不一致！开始生成冲突解释...");

            try {
                // 构造解释生成器工厂（使用正确的签名）
                Supplier<OWLOntologyManager> managerSupplier = () -> manager;
                long timeout = 5000; // 超时 5 秒，可根据本体大小调整
                ExplanationGeneratorFactory<OWLAxiom> genFac =
                        new InconsistentOntologyExplanationGeneratorFactory(
                                factory,                     // OWLReasonerFactory
                                manager.getOWLDataFactory(), // OWLDataFactory
                                managerSupplier,             // Supplier<OWLOntologyManager>
                                timeout                      // long timeout in ms
                        );
                ExplanationGenerator<OWLAxiom> gen = genFac.createExplanationGenerator(totalOnt);

                // 不一致性表示为 SubClassOf(owl:Thing owl:Nothing)
                OWLDataFactory df = manager.getOWLDataFactory();
                OWLAxiom inconsistencyAxiom = df.getOWLSubClassOfAxiom(
                        df.getOWLThing(),
                        df.getOWLNothing()
                );

                // 获取最多 5 个解释（如果解释很多，限制输出数量）
                Set<Explanation<OWLAxiom>> explanations = gen.getExplanations(inconsistencyAxiom, 5);
                int idx = 0;
                for (Explanation<OWLAxiom> exp : explanations) {
                    System.err.println("--- 冲突解释 #" + (++idx) + " ---");
                    for (OWLAxiom axiom : exp.getAxioms()) {
                        System.err.println(axiom);
                    }
                }

                if (explanations.isEmpty()) {
                    System.err.println("未生成任何解释，请检查本体或增加解释数量限制。");
                }

            } catch (Exception e) {
                System.err.println("生成解释时发生异常：" + e.getMessage());
                e.printStackTrace();
            }

            // 抛出异常，终止推理
            throw new InconsistentOntologyException("本体不一致，已输出冲突解释（见上方），推理终止。");
        }
        // ================= 一致性检查结束 =================

        // 4. 预计算推理
        tmpReasoner.precomputeInferences(
                InferenceType.CLASS_HIERARCHY,
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.DATA_PROPERTY_ASSERTIONS
        );
        this.reasoner = tmpReasoner;

        System.out.println("合并本体公理总数：" + totalOnt.getAxiomCount());
        System.out.println("manager已加载本体个数：" + manager.getOntologies().size());
    }

    public OWLOntology getOntology() {
        return this.ontology;
    }

    public static ReasonerService getInstance(String mainOntologyPath) throws Exception {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ReasonerService(mainOntologyPath);
                }
            }
        }
        return instance;
    }

    // ================= 本体加载 =================
    private OntModel loadOntology(String mainFile) throws IOException {
        OntModel model = OntModelFactory.createModel();
        loadRecursive(model, mainFile, new HashSet<>());
        return model;
    }

    // 修改：增加 OWLOntologyManager 参数
    public OWLOntology loadOntologyWithoutRecursive(String mainFile, OWLOntologyManager manager)
            throws OWLOntologyCreationException, FileNotFoundException {
        File file = new File(mainFile);
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + mainFile);
        }
        IRI documentIRI = IRI.create(file);
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(documentIRI);

        System.out.println("已加载本体数: " + manager.getOntologies().size());
        for (OWLOntology ont : manager.getOntologies()) {
            System.out.println("  " + ont.getOntologyID().getOntologyIRI().get().toString());
        }
        return ontology;
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

    public OWLReasoner getReasoner() { return reasoner; }
    public boolean isConsistent() { return reasoner.isConsistent(); }

    private IRI resolveIRI(String str) {
        if (str.startsWith("http://") || str.startsWith("https://")) {
            return IRI.create(str);
        }
        if (str.contains(":")) {
            IRI iri = prefixManager.getIRI(str);
            if (iri != null) {
                return iri;
            }
        }
        // 如果无法解析，尝试直接作为 IRI（可能失败）
        return IRI.create(str);
    }
    // ============================================
    // ================= 类相关查询 =================
    // ============================================
    public Set<OWLNamedIndividual> getIndividuals(String classIRI) {
        OWLClass cls = getClass(classIRI);
        return reasoner.getInstances(cls, true).entities().collect(Collectors.toSet());
    }

    public Set<OWLClass> getSuperClasses(String classIRI) {
        OWLClass cls = getClass(classIRI);
        return getSuperClasses(cls);
    }

    public Set<OWLClass> getSuperClasses(OWLClass cls) {
        return reasoner.getSuperClasses(cls, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getSubClasses(String classIRI) {
        OWLClass cls = getClass(classIRI);
        return reasoner.getSubClasses(cls, true).entities()
                .filter(c -> !c.isOWLNothing())
                .collect(Collectors.toSet());
    }

    public Set<OWLObjectPropertyExpression> getAllObjectPropertiesOfClass(OWLClass cls) {
        Set<OWLObjectPropertyExpression> result = new HashSet<>();
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();

        Set<OWLClass> allSuperClasses = new HashSet<>();
        allSuperClasses.add(cls);
        NodeSet<OWLClass> superNodes = reasoner.getSuperClasses(cls, true);
        Set<OWLClass> superClassesSet = superNodes.entities().collect(Collectors.toSet());
        allSuperClasses.addAll(superClassesSet);

        ontology.objectPropertiesInSignature().forEach(prop -> {
            Set<OWLClassExpression> domains = new HashSet<>();
            ontology.getObjectPropertyDomainAxioms(prop.asOWLObjectProperty())
                    .forEach(axiom -> domains.add(axiom.getDomain()));

            if (domains.isEmpty()) {
                return;
            }

            boolean matched = false;
            for (OWLClassExpression domain : domains) {
                for (OWLClass superCls : allSuperClasses) {
                    if (reasoner.isEntailed(df.getOWLSubClassOfAxiom(superCls, domain))) {
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (matched) {
                result.add(prop);
            }
        });

        return result;
    }

    public OWLObjectPropertyExpression getObjectPropertyOfClass(OWLClass cls, String propIRI) {
        Set<OWLObjectPropertyExpression> allProps = getAllObjectPropertiesOfClass(cls);
        for (OWLObjectPropertyExpression prop : allProps) {
            String currentIRI = prop.getNamedProperty().getIRI().toString();
            if (currentIRI.equals(propIRI)) {
                return prop;
            }
        }
        return null;
    }

    public Set<OWLClassExpression> getObjectPropertyDomain(OWLObjectPropertyExpression prop) {
        Set<OWLClassExpression> domains = new HashSet<>();
        if (prop == null) {
            return domains;
        }
        ontology.getObjectPropertyDomainAxioms(prop)
                .forEach(axiom -> domains.add(axiom.getDomain()));
        return domains;
    }

    public Set<OWLClassExpression> getObjectPropertyRange(OWLObjectPropertyExpression prop) {
        Set<OWLClassExpression> ranges = new HashSet<>();
        if (prop == null) {
            return ranges;
        }
        ontology.getObjectPropertyRangeAxioms(prop)
                .forEach(axiom -> ranges.add(axiom.getRange()));
        return ranges;
    }

    public Set<OWLClass> getObjectPropertyDomains(String propIRI) {
        OWLObjectProperty prop = getObjectProperty(propIRI);
        return reasoner.getObjectPropertyDomains(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getObjectPropertyRanges(String propIRI) {
        OWLObjectProperty prop = getObjectProperty(propIRI);
        return reasoner.getObjectPropertyRanges(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLClassExpression> getObjectPropertyLimitations(OWLClass cls, OWLObjectPropertyExpression prop) {
        Set<OWLClassExpression> limitations = new HashSet<>();
        if (cls == null || prop == null) {
            return limitations;
        }

        NodeSet<OWLClass> superNodes = reasoner.getSuperClasses(cls, true);
        Set<OWLClass> allSuperClasses = superNodes.entities().collect(Collectors.toSet());
        allSuperClasses.add(cls);

        Set<OWLOntology> ontologiesToSearch = new HashSet<>();
        ontologiesToSearch.add(ontology);
        ontologiesToSearch.addAll(ontology.getImportsClosure());

        for (OWLOntology ont : ontologiesToSearch) {
            for (OWLClass superCls : allSuperClasses) {
                Set<OWLSubClassOfAxiom> axioms = ont.getSubClassAxiomsForSubClass(superCls);
                for (OWLSubClassOfAxiom axiom : axioms) {
                    OWLClassExpression superClassExpr = axiom.getSuperClass();
                    if (superClassExpr instanceof OWLObjectRestriction) {
                        OWLObjectRestriction restriction = (OWLObjectRestriction) superClassExpr;
                        if (restriction.getProperty().equals(prop)) {
                            limitations.add(restriction);
                        }
                    }
                }
            }
        }

        return limitations;
    }

    public Optional<OWLObjectPropertyExpression> getInverseProperty(String propIRI) {
        OWLObjectProperty prop = getObjectProperty(propIRI);
        return Optional.ofNullable(prop.getInverseProperty());
    }

    public Map<OWLAnnotationProperty, Set<OWLLiteral>> getAnnotations(OWLObject entity) {
        Map<OWLAnnotationProperty, Set<OWLLiteral>> result = new HashMap<>();
        if (entity == null) {
            return result;
        }

        IRI iri = null;
        if (entity instanceof OWLClass) {
            iri = ((OWLClass) entity).getIRI();
        } else if (entity instanceof OWLNamedIndividual) {
            iri = ((OWLNamedIndividual) entity).getIRI();
        } else {
            return result;
        }

        Set<OWLOntology> ontologies = new HashSet<>();
        ontologies.add(ontology);
        ontologies.addAll(ontology.getImportsClosure());

        for (OWLOntology ont : ontologies) {
            ont.getAnnotationAssertionAxioms(iri).forEach(ax -> {
                OWLAnnotationProperty prop = ax.getProperty();
                OWLAnnotationValue value = ax.getValue();
                if (value instanceof OWLLiteral) {
                    result.computeIfAbsent(prop, k -> new HashSet<>()).add((OWLLiteral) value);
                }
            });
        }

        return result;
    }

    public Set<OWLLiteral> getAnnotationValue(OWLEntity entity, String annotationPropertyIRI) {
        if (entity == null || annotationPropertyIRI == null) {
            return new HashSet<>();
        }
        Map<OWLAnnotationProperty, Set<OWLLiteral>> allAnnotations = getAnnotations(entity);
        OWLAnnotationProperty targetProp = ontology.getOWLOntologyManager().getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create(annotationPropertyIRI));
        return allAnnotations.getOrDefault(targetProp, new HashSet<>());
    }

    // ============================================
    // =============== 个体相关查询 =================
    // ============================================
    // 修改：遍历所有本体查找个体
    public OWLNamedIndividual getIndividual(String individualIRI) {
        IRI indIRI = IRI.create(individualIRI);
        for (OWLOntology ont : manager.getOntologies()) {
            if (ont.containsIndividualInSignature(indIRI)) {
                return manager.getOWLDataFactory().getOWLNamedIndividual(indIRI);
            }
        }
        throw new IllegalArgumentException("个体未找到: " + individualIRI);
    }

    public Set<OWLClass> getIndividualDirectTypes(OWLNamedIndividual ind) {
        return reasoner.getTypes(ind, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getIndividualAllTypes(OWLNamedIndividual ind) {
        Set<OWLClass> result = new HashSet<>();
        Set<OWLClass> directTypes = getIndividualDirectTypes(ind);
        System.out.println("直接类型: " + directTypes.stream().map(c -> c.getIRI().getShortForm()).collect(Collectors.toList()));

        for (OWLClass cls : directTypes) {
            IRI iri = resolveIRI(cls.getIRI().getIRIString());
            OWLDataFactory df = manager.getOWLDataFactory();
            cls = df.getOWLClass(iri);
            result.add(cls);
            Set<OWLClass> supers = reasoner.getSuperClasses(cls, false).entities()
                    .filter(c -> !c.isOWLThing())
                    .collect(Collectors.toSet());
            result.addAll(supers);
        }
        /*测试代码
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLClass testClass = df.getOWLClass(IRI.create("http://example.org/pizza/components/classes/NeapolitanCrust"));
        System.out.println("直接父类: " + reasoner.getSuperClasses(testClass, false).entities()
                .filter(c -> !c.isOWLThing())
                .map(c -> c.getIRI().getShortForm())
                .collect(Collectors.toList()));
        System.out.println("所有父类: " + reasoner.getSuperClasses(testClass, true).entities()
                .filter(c -> !c.isOWLThing())
                .map(c -> c.getIRI().getShortForm())
                .collect(Collectors.toList()));
        */
        return result;
    }

    public OWLClass getClass(String classIRIOrQName) {
        IRI iri = resolveIRI(classIRIOrQName);
        for (OWLOntology ont : manager.getOntologies()) {
            if (ont.containsClassInSignature(iri)) {
                return manager.getOWLDataFactory().getOWLClass(iri);
            }
        }
        throw new IllegalArgumentException("类未找到: " + classIRIOrQName);
    }

    public Set<OWLObjectPropertyExpression> getObjectPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLObjectPropertyExpression> properties = new HashSet<>();
        if (ind == null) {
            return properties;
        }

        // 1. 获取个体直接断言的对象属性
        Set<OWLObjectPropertyAssertionAxiom> axioms = ontology.getObjectPropertyAssertionAxioms(ind);
        for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
            properties.add(axiom.getProperty());
        }

        // 2. 获取个体所有类型（包括直接和间接父类）
        Set<OWLClass> allTypes = getIndividualAllTypes(ind);  // 需要确保该方法返回所有类型
        for (OWLClass cls : allTypes) {
            // 获取该类上定义的所有对象属性（包括继承的）
            Set<OWLObjectPropertyExpression> classProps = getAllObjectPropertiesOfClass(cls);
            properties.addAll(classProps);
        }

        return properties;
    }

    public Set<OWLNamedIndividual> getObjectPropertyDirectValueOfIndividual(
            OWLNamedIndividual ind,
            OWLObjectPropertyExpression prop) {

        Set<OWLNamedIndividual> result = new HashSet<>();
        if (ind == null || prop == null) {
            return result;
        }

        Set<OWLOntology> ontologiesToSearch = new HashSet<>();
        ontologiesToSearch.add(ontology);
        ontologiesToSearch.addAll(ontology.getImportsClosure());

        for (OWLOntology ont : ontologiesToSearch) {
            Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(ind);
            for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
                if (axiom.getProperty().equals(prop)) {
                    OWLIndividual object = axiom.getObject();
                    if (object instanceof OWLNamedIndividual) {
                        result.add((OWLNamedIndividual) object);
                    }
                }
            }
        }

        return result;
    }

    public Set<OWLNamedIndividual> getObjectPropertyAllValueOfIndividual(
            OWLNamedIndividual ind,
            OWLObjectPropertyExpression prop) {

        if (ind == null || prop == null || reasoner == null) {
            return new HashSet<>();
        }

        NodeSet<OWLNamedIndividual> values = reasoner.getObjectPropertyValues(ind, prop);
        return values.entities().collect(Collectors.toSet());
    }

    public Set<OWLDataProperty> getDirectDataPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLDataProperty> properties = new HashSet<>();
        if (ind == null) {
            return properties;
        }

        Set<OWLDataPropertyAssertionAxiom> axioms = ontology.getDataPropertyAssertionAxioms(ind);
        for (OWLDataPropertyAssertionAxiom axiom : axioms) {
            properties.add(axiom.getProperty().asOWLDataProperty());
        }
        return properties;
    }

    public Set<OWLDataProperty> getAllAllowedDataPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLDataProperty> allowedProperties = new HashSet<>();
        if (ind == null || reasoner == null || ontology == null) {
            return allowedProperties;
        }

        Set<OWLClass> allClasses = this.getIndividualAllTypes(ind);
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();

        for (OWLClass cls : allClasses) {
            Set<OWLDataProperty> allDataProps = ontology.dataPropertiesInSignature()
                    .map(prop -> prop.asOWLDataProperty())
                    .collect(Collectors.toSet());

            for (OWLDataProperty prop : allDataProps) {
                Set<OWLClass> domains = getDataPropertyDomains(prop);
                if (domains.isEmpty()) {
                    allowedProperties.add(prop);
                    continue;
                }
                for (OWLClassExpression domain : domains) {
                    if (reasoner.isEntailed(df.getOWLSubClassOfAxiom(cls, domain))) {
                        allowedProperties.add(prop);
                        break;
                    }
                }
            }
        }

        return allowedProperties;
    }

    public Set<OWLLiteral> getDataPropertyValueOfIndividual(OWLNamedIndividual ind, OWLDataProperty dataProp) {
        Set<OWLLiteral> result = new HashSet<>();
        if (ind == null || dataProp == null) {
            return result;
        }

        // 1. 使用推理机获取所有值（包括从父类 hasValue 约束推导出的）
        if (reasoner != null) {
            // 直接使用 Set 接收（根据编译器反馈，返回类型为 Set）
            Set<OWLLiteral> values = reasoner.getDataPropertyValues(ind, dataProp);
            result.addAll(values);
        }

        // 2. 如果推理机未返回任何值，手动从父类的 hasValue 约束中查找
        if (result.isEmpty() && ontology != null && manager != null) {
            Set<OWLClass> allTypes = getIndividualAllTypes(ind);
            OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
            for (OWLClass cls : allTypes) {
                for (OWLOntology ont : manager.getOntologies()) {
                    for (OWLSubClassOfAxiom axiom : ont.getSubClassAxiomsForSubClass(cls)) {
                        OWLClassExpression superClass = axiom.getSuperClass();
                        if (superClass instanceof OWLDataHasValue) {
                            OWLDataHasValue restriction = (OWLDataHasValue) superClass;
                            if (restriction.getProperty().equals(dataProp)) {
                                result.add(restriction.getFiller());
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    public Set<OWLLiteral> getDataPropertyValueOfIndividual(OWLNamedIndividual ind, String dataPropIRI) {
        OWLDataProperty dataProp = getDataProperty(dataPropIRI);
        return getDataPropertyValueOfIndividual(ind, dataProp);
    }

    public Set<OWLClass> getDataPropertyDomains(String propIRI) {
        OWLDataProperty prop = getDataProperty(propIRI);
        return getDataPropertyDomains(prop);
    }

    public Set<OWLClass> getDataPropertyDomains(OWLDataProperty prop) {
        return reasoner.getDataPropertyDomains(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLDatatype> getDataPropertyRanges(String propIRI) {
        OWLDataProperty prop = getDataProperty(propIRI);
        return ontology.dataPropertyRangeAxioms(prop)
                .map(OWLDataPropertyRangeAxiom::getRange)
                .filter(range -> range.isOWLDatatype())
                .map(range -> range.asOWLDatatype())
                .collect(Collectors.toSet());
    }

    public boolean isInstanceOf(String individualIRI, String classIRI) {
        OWLNamedIndividual ind = getIndividual(individualIRI);
        OWLClass cls = getClass(classIRI);
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
        return reasoner.isEntailed(df.getOWLClassAssertionAxiom(cls, ind));
    }

    // ================= 辅助方法 =================
    // 修改：遍历所有本体查找对象属性
    private OWLObjectProperty getObjectProperty(String iri) {
        IRI propIRI = IRI.create(iri);
        for (OWLOntology ont : manager.getOntologies()) {
            if (ont.containsObjectPropertyInSignature(propIRI)) {
                return manager.getOWLDataFactory().getOWLObjectProperty(propIRI);
            }
        }
        throw new IllegalArgumentException("对象属性未找到: " + iri);
    }

    // 修改：遍历所有本体查找数据属性
    private OWLDataProperty getDataProperty(String iri) {
        IRI propIRI = IRI.create(iri);
        for (OWLOntology ont : manager.getOntologies()) {
            if (ont.containsDataPropertyInSignature(propIRI)) {
                return manager.getOWLDataFactory().getOWLDataProperty(propIRI);
            }
        }
        throw new IllegalArgumentException("数据属性未找到: " + iri);
    }

    // ================= 数据类型查询 =================
    public Optional<OWLDatatype> getDatatype(String datatypeIRI) {
        IRI dtIRI = IRI.create(datatypeIRI);
        return ontology.datatypesInSignature().filter(dt -> dt.getIRI().equals(dtIRI)).findFirst();
    }

    // ================= 实体类型判断 =================
    // 修改：遍历所有本体判断类型
    public String getEntityType(IRI iri) {
        for (OWLOntology ont : manager.getOntologies()) {
            if (ont.containsClassInSignature(iri)) return "Class";
            if (ont.containsIndividualInSignature(iri)) return "Individual";
            if (ont.containsObjectPropertyInSignature(iri)) return "ObjectProperty";
            if (ont.containsDataPropertyInSignature(iri)) return "DataProperty";
            if (ont.containsAnnotationPropertyInSignature(iri)) return "AnnotationProperty";
            if (ont.containsDatatypeInSignature(iri)) return "Datatype";
        }
        return "Unknown";
    }

    // ================= IRI链的查询 =================
    // 以下方法被注释掉，但完整保留
    /*
    public String queryIndividualPropertyChain(String chainJson, boolean debug) {
        // 1. 解析 JSON 为 List<String>
        List<String> iris = parseJsonArray(chainJson);
        if (iris.isEmpty()) {
            if (debug) System.out.println("IRI 列表为空");
            return null;
        }

        // 2. 初始化 current 为第一个 IRI
        String currentIRI = iris.get(0);
        if (debug) System.out.println("起始 IRI: " + currentIRI);

        // 3. 从索引 1 开始遍历
        for (int i = 1; i < iris.size(); i++) {
            String nextIRI = iris.get(i);
            if (debug) System.out.println("处理步骤 " + i + ": 当前 = " + currentIRI + ", 下一个 = " + nextIRI);

            // 获取当前和下一个实体的类型
            String currentType = getEntityType(IRI.create(currentIRI));
            String nextType = getEntityType(IRI.create(nextIRI));

            if (debug) System.out.println("当前类型: " + currentType + ", 下一个类型: " + nextType);

            // 根据类型组合决定导航逻辑
            String resultIRI = null;
            String literalValue = null;

            // --- 情况 1: 当前是类，下一个是对象属性 ---
            if ("Class".equals(currentType) && "ObjectProperty".equals(nextType)) {
                OWLClass cls = getClass(currentIRI);
                OWLObjectPropertyExpression propExpr = getObjectPropertyOfClass(cls, nextIRI);
                if (propExpr != null) {
                    resultIRI = propExpr.getNamedProperty().getIRI().toString();
                    if (debug) System.out.println("通过类获取对象属性，得到属性 IRI: " + resultIRI);
                } else {
                    if (debug) System.out.println("类 " + currentIRI + " 不包含对象属性 " + nextIRI);
                    return null;
                }
            }
            // --- 情况 2: 当前是个体，下一个是对象属性 ---
            else if ("Individual".equals(currentType) && "ObjectProperty".equals(nextType)) {
                OWLNamedIndividual ind = getIndividual(currentIRI);
                OWLObjectProperty prop = getObjectProperty(nextIRI);
                Set<OWLNamedIndividual> values = getObjectPropertyAllValueOfIndividual(ind, prop);
                if (!values.isEmpty()) {
                    OWLNamedIndividual first = values.iterator().next();
                    resultIRI = first.getIRI().toString();
                    if (debug) System.out.println("个体通过对象属性得到值: " + resultIRI);
                } else {
                    if (debug) System.out.println("个体 " + currentIRI + " 没有对象属性 " + nextIRI + " 的值");
                    return null;
                }
            }
            // --- 情况 3: 当前是对象属性，下一个是类（获取属性范围） ---
            else if ("ObjectProperty".equals(currentType) && "Class".equals(nextType)) {
                OWLObjectProperty prop = getObjectProperty(currentIRI);
                Set<OWLClassExpression> ranges = getObjectPropertyRange(prop);
                if (!ranges.isEmpty()) {
                    OWLClassExpression rangeExpr = ranges.iterator().next();
                    if (rangeExpr.isOWLClass()) {
                        resultIRI = rangeExpr.asOWLClass().getIRI().toString();
                        if (debug) System.out.println("对象属性的范围类: " + resultIRI);
                    } else {
                        if (debug) System.out.println("对象属性的范围不是原子类，忽略");
                        return null;
                    }
                } else {
                    if (debug) System.out.println("对象属性 " + currentIRI + " 没有范围定义");
                    return null;
                }
            }
            // --- 情况 4: 当前是个体，下一个是数据属性（获取字面量值，终止链） ---
            else if ("Individual".equals(currentType) && "DataProperty".equals(nextType)) {
                OWLNamedIndividual ind = getIndividual(currentIRI);
                OWLDataProperty dataProp = getDataProperty(nextIRI);
                Set<OWLLiteral> literals = getDataPropertyValueOfIndividual(ind, dataProp);
                if (!literals.isEmpty()) {
                    OWLLiteral first = literals.iterator().next();
                    literalValue = first.getLiteral();
                    if (debug) System.out.println("数据属性值: " + literalValue);
                    return literalValue;
                } else {
                    if (debug) System.out.println("个体 " + currentIRI + " 没有数据属性 " + nextIRI + " 的值");
                    return null;
                }
            }
            // --- 情况 5: 其他组合暂不支持 ---
            else {
                if (debug) System.out.println("不支持的类型组合: " + currentType + " + " + nextType);
                return null;
            }

            if (resultIRI != null) {
                currentIRI = resultIRI;
            } else {
                if (debug) System.out.println("未获得有效结果");
                return null;
            }
        }

        if (debug) System.out.println("最终结果 IRI: " + currentIRI);
        return currentIRI;
    }
    */

    // 保留被注释的 findPathAndQuery 方法
    /*
    public String findPathAndQuery(String startIRI, String targetIRI, boolean debug) {
        // 1. 解析起点实体类型
        IRI start = IRI.create(startIRI);
        String startType = getEntityType(start);
        if ("Unknown".equals(startType)) {
            if (debug) System.out.println("起点实体未知");
            return null;
        }

        // 2. 如果起点就是目标（直接匹配）
        if (startIRI.equals(targetIRI)) {
            return startIRI;
        }

        // 3. 如果目标是数据属性，直接尝试从起点个体获取
        if ("DataProperty".equals(getEntityType(IRI.create(targetIRI)))) {
            if ("Individual".equals(startType)) {
                OWLNamedIndividual ind = getIndividual(startIRI);
                OWLDataProperty dataProp = getDataProperty(targetIRI);
                Set<OWLLiteral> values = getDataPropertyValueOfIndividual(ind, dataProp);
                if (!values.isEmpty()) {
                    return values.iterator().next().getLiteral();
                } else {
                    if (debug) System.out.println("个体无该数据属性值");
                    return null;
                }
            } else if ("Class".equals(startType)) {
                OWLClass cls = getClass(startIRI);
                NodeSet<OWLNamedIndividual> instances = reasoner.getInstances(cls, false);
                Set<OWLNamedIndividual> inds = instances.entities().collect(Collectors.toSet());
                if (!inds.isEmpty()) {
                    OWLNamedIndividual first = inds.iterator().next();
                    OWLDataProperty dataProp = getDataProperty(targetIRI);
                    Set<OWLLiteral> values = getDataPropertyValueOfIndividual(first, dataProp);
                    if (!values.isEmpty()) {
                        return values.iterator().next().getLiteral();
                    }
                }
                if (debug) System.out.println("类无实例或实例无该数据属性");
                return null;
            } else {
                if (debug) System.out.println("起点类型无法直接获取数据属性");
                return null;
            }
        }

        // 4. BFS 路径搜索
        Queue<String> queue = new LinkedList<>();
        Map<String, String> parentMap = new HashMap<>();
        Map<String, String> edgeMap = new HashMap<>();

        queue.add(startIRI);
        parentMap.put(startIRI, null);
        edgeMap.put(startIRI, null);

        Set<String> visited = new HashSet<>();
        visited.add(startIRI);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            String currentType = getEntityType(IRI.create(current));

            if (current.equals(targetIRI)) {
                List<String> pathIRIs = new ArrayList<>();
                String node = current;
                while (node != null) {
                    pathIRIs.add(0, node);
                    node = parentMap.get(node);
                }
                if (pathIRIs.size() > 1) {
                    List<String> chain = pathIRIs.subList(1, pathIRIs.size());
                    return queryIndividualPropertyChain(chain, debug);
                } else {
                    return current;
                }
            }

            if ("Class".equals(currentType)) {
                OWLClass cls = getClass(current);
                Set<OWLObjectProperty> props = getAllObjectPropertiesOfClass(cls);
                for (OWLObjectProperty prop : props) {
                    Set<OWLClassExpression> ranges = getObjectPropertyRange(prop);
                    for (OWLClassExpression range : ranges) {
                        if (range.isOWLClass()) {
                            String rangeIRI = range.asOWLClass().getIRI().toString();
                            if (!visited.contains(rangeIRI)) {
                                visited.add(rangeIRI);
                                parentMap.put(rangeIRI, current);
                                edgeMap.put(rangeIRI, prop.getIRI().toString());
                                queue.add(rangeIRI);
                            }
                        }
                    }
                }
            }

            if ("Individual".equals(currentType)) {
                OWLNamedIndividual ind = getIndividual(current);
                Set<OWLObjectPropertyAssertionAxiom> axioms = ontology.getObjectPropertyAssertionAxioms(ind);
                for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
                    OWLObjectPropertyExpression propExpr = axiom.getProperty();
                    OWLIndividual object = axiom.getObject();
                    if (object instanceof OWLNamedIndividual) {
                        String nextIRI = ((OWLNamedIndividual) object).getIRI().toString();
                        if (!visited.contains(nextIRI)) {
                            visited.add(nextIRI);
                            parentMap.put(nextIRI, current);
                            edgeMap.put(nextIRI, propExpr.getNamedProperty().getIRI().toString());
                            queue.add(nextIRI);
                        }
                    }
                }
            }

            if ("ObjectProperty".equals(currentType)) {
                OWLObjectProperty prop = getObjectProperty(current);
                Set<OWLClassExpression> ranges = getObjectPropertyRange(prop);
                for (OWLClassExpression range : ranges) {
                    if (range.isOWLClass()) {
                        String rangeIRI = range.asOWLClass().getIRI().toString();
                        if (!visited.contains(rangeIRI)) {
                            visited.add(rangeIRI);
                            parentMap.put(rangeIRI, current);
                            edgeMap.put(rangeIRI, prop.getIRI().toString());
                            queue.add(rangeIRI);
                        }
                    }
                }
            }
        }

        if (debug) System.out.println("未找到从起点到目标的路径");
        return null;
    }
    */

    // 辅助解析 JSON 数组
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

    @Override
    public void close() {
        if (reasoner != null) reasoner.dispose();
    }

    // ================= 被注释掉的 getLabel 方法 =================
    /*
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
    */
}