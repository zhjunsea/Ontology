package com.ocean.openlletresolver;

import openllet.owlapi.OpenlletReasonerFactory;
/*
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.impl.blackbox.checker.InconsistentOntologyExplanationGeneratorFactory;
 */
import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.impl.blackbox.checker.InconsistentOntologyExplanationGeneratorFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.formats.RDFXMLDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
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

    private final OWLOntologyManager manager;
    private final OWLOntology ontology;
    private OWLReasoner reasoner;
    private static boolean debug = false;
    private final DefaultPrefixManager prefixManager;

    // ================= 构造函数 ================
    private ReasonerService(String mainOntologyPath) throws Exception {
        // 1. 创建 manager
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        this.manager = manager;

        OWLOntology loadedOntology = loadOntologyWithoutRecursive(mainOntologyPath);
/*
        if (loadedOntology == null) {
            // 备用方案：用 Jena 加载并转换
            OntModel jenaModel = loadOntology(mainOntologyPath);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            jenaModel.write(out, "RDF/XML");
            byte[] mergedBytes = out.toByteArray();

            loadedOntology = manager.loadOntologyFromOntologyDocument(
                    new ByteArrayInputStream(mergedBytes));
        } */
        if (loadedOntology != null) {
            System.out.println("主本体加载成功: " + loadedOntology.getOntologyID().getOntologyIRI().orElse(null));
        } else {
            System.err.println("警告：主本体加载失败！");
        }

        // 2. 合并所有本体（入口 + 所有导入）
        // ================= 提取前缀映射（从 Jena 模型）并去重 =================
        OntModel jenaModel = loadOntology(mainOntologyPath);
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

        System.out.println("已注册前缀数量: " + uniquePrefixMap.size());
        uniquePrefixMap.forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        IRI mergedIri = IRI.create("http://example.org/pizza/merged_total");
        OWLOntology totalOnt = manager.createOntology(mergedIri);
        manager.ontologies()
                .filter(ont -> !ont.equals(totalOnt))
                .forEach(ont -> ont.axioms().forEach(totalOnt::addAxiom));
        // 将去重后的前缀绑定到合并本体
        OWLDocumentFormat format = new RDFXMLDocumentFormat();
        if (format instanceof PrefixDocumentFormat) {
            ((PrefixDocumentFormat) format).setPrefixManager(this.prefixManager);
        }
        manager.setOntologyFormat(totalOnt, format);

        this.ontology = totalOnt;
        /*
        OWLDocumentFormat format1 = manager.getOntologyFormat(ontology);
        if (format1 instanceof PrefixDocumentFormat) {
            PrefixDocumentFormat prefixFormat = (PrefixDocumentFormat) format1;
            System.out.println("本体 " + ontology.getOntologyID() + " 的前缀：");
            prefixFormat.getPrefixNames().forEach(prefix -> {
                String namespace = prefixFormat.getPrefix(prefix);
                System.out.println("  " + prefix + " -> " + namespace);
            });
        }

        //测试
        OWLNamedIndividual ind = getIndividual("http://example.org/pizza/components/individuals/Pepperoni");
        Set<OWLClass> rawAssertTypes = ontology
                .getAxioms(AxiomType.CLASS_ASSERTION)
                .stream()
                // 筛选出属于当前个体的类断言
                .filter(ax -> ax.getIndividual().equals(ind))
                .map(OWLClassAssertionAxiom::getClassExpression)
                // 只保留命名类，排除匿名交集/并集等复杂表达式
                .filter(ce -> ce.isNamed())
                .map(ce -> ce.asOWLClass())
                .collect(Collectors.toSet());
        // 打印每一个原始声明类型的IRI，定位是解析成功还是前缀失效
        for (OWLClass cls : rawAssertTypes) {
            System.out.println("原始断言类型IRI：" + cls.getIRI());
            System.out.println(cls.getIRI().getIRIString());
        }

        System.out.println("----- 检查各本体中的 SWRL 规则数量 -----");
        for (OWLOntology ont : manager.ontologies().collect(Collectors.toList())) {
            long count = ont.axioms(AxiomType.SWRL_RULE).count();
            System.out.println("本体 " + ont.getOntologyID() + " -> SWRL规则: " + count);
        }
        totalOnt.axioms().forEach(ax -> {
            if (ax.toString().contains("swrl")) {
                System.out.println(ax);
            }
        });  */

        // ================= 提取前缀映射 =================
        /*
        OntModel jenaModel = loadOntology(mainOntologyPath);
        Map<String, String> nsMap = jenaModel.getNsPrefixMap();
        this.prefixManager = new DefaultPrefixManager();
        for (Map.Entry<String, String> entry : nsMap.entrySet()) {
            this.prefixManager.setPrefix(entry.getKey(), entry.getValue());
        }
        System.out.println("已注册前缀数量: " + nsMap.size());
        nsMap.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
        */

        // 查看SWRL是否加载成功
        long ruleCount = totalOnt.axioms(AxiomType.SWRL_RULE).count();
        System.out.println("合并后 SWRL 规则数量: " + ruleCount);
        totalOnt.axioms(AxiomType.SWRL_RULE).forEach(System.out::println);

        // 3. 创建推理机（改用 Openllet）
        OWLReasonerFactory factory = new OpenlletReasonerFactory();
        OWLReasoner tmpReasoner = factory.createReasoner(totalOnt);
        tmpReasoner.flush();

        // ================= 一致性检查与解释生成 =================
        if (!tmpReasoner.isConsistent()) {
            System.err.println("本体不一致！开始生成冲突解释...");

            try {
                Supplier<OWLOntologyManager> managerSupplier = () -> manager;
                long timeout = 5000;
                ExplanationGeneratorFactory<OWLAxiom> genFac =
                        new InconsistentOntologyExplanationGeneratorFactory(
                                factory,
                                manager.getOWLDataFactory(),
                                managerSupplier,
                                timeout
                        );
                ExplanationGenerator<OWLAxiom> gen = genFac.createExplanationGenerator(totalOnt);

                OWLDataFactory df = manager.getOWLDataFactory();
                OWLAxiom inconsistencyAxiom = df.getOWLSubClassOfAxiom(
                        df.getOWLThing(),
                        df.getOWLNothing()
                );

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
                e.printStackTrace(System.err);
            }

            throw new InconsistentOntologyException("本体不一致，已输出冲突解释（见上方），推理终止。");
        }

        // 4. 预计算推理
        /*tmpReasoner.precomputeInferences(
                InferenceType.CLASS_HIERARCHY,
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.DATA_PROPERTY_ASSERTIONS
        );*/
        tmpReasoner.precomputeInferences();
        this.reasoner = tmpReasoner;

        System.out.println("合并本体公理总数：" + totalOnt.getAxiomCount());
        System.out.println("manager已加载本体个数：" + manager.ontologies().count());
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

    public OWLOntology loadOntologyWithoutRecursive(String mainFile)
            throws OWLOntologyCreationException, FileNotFoundException {
        File file = new File(mainFile);
        if (!file.exists()) {
            throw new FileNotFoundException("文件不存在: " + mainFile);
        }
        IRI documentIRI = IRI.create(file);
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(documentIRI);

        List<OWLOntology> ontologyList = manager.ontologies().toList();
        System.out.println("已加载本体数: " + ontologyList.size());
        for (OWLOntology ont : ontologyList) {
            System.out.println("  " + ont.getOntologyID().getOntologyIRI().map(IRI::toString).orElse("无 IRI"));
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

    public void refreshReasoner() {
        if (this.reasoner != null) {
            reasoner.flush();
            System.out.println("推理机已刷新，基于修改后的本体重新推理。");
        }
        else{
            System.out.println("推理机为空");
        }
    }

    /*
    public IRI resolveIRI(String str) {
        if (str.startsWith("http://") || str.startsWith("https://")) {
            return IRI.create(str);
        }
        if (str.contains(":")) {
            IRI iri = prefixManager.getIRI(str);
            if (iri != null) {
                return iri;
            }
        }
        return IRI.create(str);
    }*/

    // ============================================
    // ================= 类相关查询 =================
    // ============================================
    public Set<OWLNamedIndividual> getIndividuals(String classIRI) {
        OWLClass cls = getClass(classIRI);
        Set<OWLNamedIndividual> result = new HashSet<>();
        IRI targetIRI = cls.getIRI();

        ontology.axioms(AxiomType.CLASS_ASSERTION)
                .forEach(ax -> {
                    OWLClassExpression expr = ax.getClassExpression();
                    if (expr.isOWLClass()) {
                        OWLClass asserted = expr.asOWLClass();
                        //IRI resolvedIRI = resolveIRI(asserted.getIRI().getIRIString());
                        IRI resolvedIRI = asserted.getIRI();
                        if (resolvedIRI.equals(targetIRI)) {
                            OWLIndividual ind = ax.getIndividual();
                            if (ind instanceof OWLNamedIndividual) {
                                result.add((OWLNamedIndividual) ind);
                            }
                        }
                    }
                });

        Set<OWLNamedIndividual> inferred = reasoner.getInstances(cls, true)
                .entities()
                .collect(Collectors.toSet());
        result.addAll(inferred);

        return result;
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
        reasoner.getSuperClasses(cls, true).entities().forEach(allSuperClasses::add);

        ontology.objectPropertiesInSignature().forEach(prop -> {
            Set<OWLClassExpression> domains = ontology.objectPropertyDomainAxioms(prop.asOWLObjectProperty())
                    .map(OWLObjectPropertyDomainAxiom::getDomain)
                    .collect(Collectors.toSet());

            if (domains.isEmpty()) {
                return;
            }

            boolean matched = domains.stream().anyMatch(domain ->
                    allSuperClasses.stream().anyMatch(superCls ->
                            reasoner.isEntailed(df.getOWLSubClassOfAxiom(superCls, domain))));
            if (matched) {
                result.add(prop);
            }
        });

        return result;
    }

    public OWLObjectPropertyExpression getObjectPropertyOfClass(OWLClass cls, String propIRI) {
        Set<OWLObjectPropertyExpression> allProps = getAllObjectPropertiesOfClass(cls);
        for (OWLObjectPropertyExpression prop : allProps) {
            if (prop.getNamedProperty().getIRI().toString().equals(propIRI)) {
                return prop;
            }
        }
        return null;
    }

    public Set<OWLClassExpression> getObjectPropertyDomain(OWLObjectPropertyExpression prop) {
        if (prop == null) {
            return new HashSet<>();
        }
        return ontology.objectPropertyDomainAxioms(prop)
                .map(OWLObjectPropertyDomainAxiom::getDomain)
                .collect(Collectors.toSet());
    }

    public Set<OWLClassExpression> getObjectPropertyRange(OWLObjectPropertyExpression prop) {
        if (prop == null) {
            return new HashSet<>();
        }
        return ontology.objectPropertyRangeAxioms(prop)
                .map(OWLObjectPropertyRangeAxiom::getRange)
                .collect(Collectors.toSet());
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
        if (cls == null || prop == null) {
            return new HashSet<>();
        }

        Set<OWLClass> allSuperClasses = new HashSet<>();
        allSuperClasses.add(cls);
        reasoner.getSuperClasses(cls, true).entities().forEach(allSuperClasses::add);

        return Stream.concat(Stream.of(ontology), ontology.importsClosure())
                .flatMap(ont -> allSuperClasses.stream()
                        .flatMap(superCls -> ont.subClassAxiomsForSubClass(superCls)))
                .map(OWLSubClassOfAxiom::getSuperClass)
                .filter(OWLObjectRestriction.class::isInstance)
                .map(OWLObjectRestriction.class::cast)
                .filter(restriction -> restriction.getProperty().equals(prop))
                .collect(Collectors.toSet());
    }

    public Optional<OWLObjectPropertyExpression> getInverseProperty(String propIRI) {
        OWLObjectProperty prop = getObjectProperty(propIRI);
        return manager.ontologies()
                .flatMap(ont -> ont.inverseObjectPropertyAxioms(prop))
                .findFirst()
                .map(OWLInverseObjectPropertiesAxiom::getFirstProperty);
    }

    public Map<OWLAnnotationProperty, Set<OWLLiteral>> getAnnotations(OWLObject entity) {
        Map<OWLAnnotationProperty, Set<OWLLiteral>> result = new HashMap<>();
        if (entity == null) {
            return result;
        }

        IRI iri;
        if (entity instanceof OWLClass) {
            iri = ((OWLClass) entity).getIRI();
        } else if (entity instanceof OWLNamedIndividual) {
            iri = ((OWLNamedIndividual) entity).getIRI();
        } else {
            return result;
        }

        final IRI targetIRI = iri;
        Stream.concat(Stream.of(ontology), ontology.importsClosure())
                .flatMap(ont -> ont.annotationAssertionAxioms(targetIRI))
                .forEach(ax -> {
                    OWLAnnotationProperty prop = ax.getProperty();
                    OWLAnnotationValue value = ax.getValue();
                    if (value instanceof OWLLiteral) {
                        result.computeIfAbsent(prop, ignored -> new HashSet<>()).add((OWLLiteral) value);
                    }
                });
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
    public OWLNamedIndividual getIndividual(String individualIRI) {
        IRI indIRI = IRI.create(individualIRI);
        boolean exists = manager.ontologies().anyMatch(ont -> ont.containsIndividualInSignature(indIRI));
        if (!exists) {
            throw new IllegalArgumentException("个体未找到: " + individualIRI);
        }
        return manager.getOWLDataFactory().getOWLNamedIndividual(indIRI);
    }

    public Set<OWLClass> getIndividualDirectTypes(OWLNamedIndividual ind) {
        return reasoner.getTypes(ind, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getIndividualAllTypes(OWLNamedIndividual ind) {
        Set<OWLClass> result = new HashSet<>();
        Set<OWLClass> directTypes = getIndividualDirectTypes(ind);
        System.out.println("直接类型: " + directTypes.stream().map(c -> c.getIRI().getShortForm()).toList());

        for (OWLClass cls : directTypes) {
            //IRI iri = resolveIRI(cls.getIRI().getIRIString());
            IRI iri = cls.getIRI();
            OWLDataFactory df = manager.getOWLDataFactory();
            cls = df.getOWLClass(iri);
            result.add(cls);
            Set<OWLClass> supers = reasoner.getSuperClasses(cls, false).entities()
                    .filter(c -> !c.isOWLThing())
                    .collect(Collectors.toSet());
            result.addAll(supers);
        }
        return result;
    }

    /**
     * 打印 Set<OWLClass> 中每个类的短名、IRI 和 QName
     *
     * @param classes 要打印的类集合
     */
    public void printOWLClassSet(Set<OWLClass> classes) {
        if (classes == null || classes.isEmpty()) {
            System.out.println("类集合为空");
            return;
        }
        System.out.println("类集合内容：");
        for (OWLClass cls : classes) {
            IRI iri = cls.getIRI();
            String shortForm = iri.getShortForm();
            String iriStr = iri.toString();
            String qname = null;
            /*
            if (prefixManager != null) {
                qname = prefixManager.getPrefixIRI(iri);  // 返回形如 "prefix:localName" 的字符串
            } else {
                qname = iriStr;  // 没有前缀管理器时回退为完整 IRI
            }*/
            System.out.printf("  短名: %-30s  IRI: %-50s  QName: %s%n", shortForm, iriStr, qname);
        }
    }

    public OWLClass getClass(String classIRIOrQName) {
        //IRI iri = resolveIRI(classIRIOrQName);
        IRI iri = IRI.create(classIRIOrQName);
        return manager.ontologies()
                .filter(ont -> ont.containsClassInSignature(iri))
                .findFirst()
                .map(ignored -> manager.getOWLDataFactory().getOWLClass(iri))
                .orElseThrow(() -> new IllegalArgumentException("类未找到: " + classIRIOrQName));
    }

    public Set<OWLObjectPropertyExpression> getObjectPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLObjectPropertyExpression> properties = new HashSet<>();
        if (ind == null) return properties;

        ontology.objectPropertyAssertionAxioms(ind)
                .map(OWLObjectPropertyAssertionAxiom::getProperty)
                .forEach(properties::add);

        getIndividualAllTypes(ind).forEach(cls ->
                properties.addAll(getAllObjectPropertiesOfClass(cls))
        );
        return properties;
    }

    public Set<OWLNamedIndividual> getObjectPropertyDirectValueOfIndividual(
            OWLNamedIndividual ind, OWLObjectPropertyExpression prop) {
        if (ind == null || prop == null) return new HashSet<>();

        return Stream.concat(Stream.of(ontology), ontology.importsClosure())
                .flatMap(ont -> ont.objectPropertyAssertionAxioms(ind))
                .filter(axiom -> axiom.getProperty().equals(prop))
                .map(OWLObjectPropertyAssertionAxiom::getObject)
                .filter(OWLNamedIndividual.class::isInstance)
                .map(OWLNamedIndividual.class::cast)
                .collect(Collectors.toSet());
    }

    public Set<OWLNamedIndividual> getObjectPropertyAllValueOfIndividual(
            OWLNamedIndividual ind, OWLObjectPropertyExpression prop) {
        if (ind == null || prop == null || reasoner == null) {
            return new HashSet<>();
        }
        NodeSet<OWLNamedIndividual> values = reasoner.getObjectPropertyValues(ind, prop);
        return values.entities().collect(Collectors.toSet());
    }

    public Set<OWLDataProperty> getDirectDataPropertiesOfIndividual(OWLNamedIndividual ind) {
        if (ind == null) return new HashSet<>();
        return ontology.dataPropertyAssertionAxioms(ind)
                .map(axiom -> axiom.getProperty().asOWLDataProperty())
                .collect(Collectors.toSet());
    }

    public Set<OWLDataProperty> getAllAllowedDataPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLDataProperty> allowedProperties = new HashSet<>();
        if (ind == null || reasoner == null || ontology == null) {
            return allowedProperties;
        }

        Set<OWLClass> allClasses = this.getIndividualAllTypes(ind);
        if (debug) System.out.println("个体类型数量: " + allClasses.size());

        Set<OWLDataProperty> allDataProps = new HashSet<>();
        for (OWLDataPropertyExpression expr : ontology.dataPropertiesInSignature().collect(Collectors.toList())) {
            allDataProps.add(expr.asOWLDataProperty());
        }
        if (debug) System.out.println("总数据属性数量: " + allDataProps.size());

        Map<OWLDataProperty, Set<OWLClass>> propDomains = new HashMap<>();
        for (OWLDataProperty prop : allDataProps) {
            Set<OWLClass> domains = getDataPropertyDomains(prop);
            propDomains.put(prop, domains);
            if (debug) System.out.println("属性 " + prop.getIRI().getShortForm() + " 的域大小: " + domains.size());
        }

        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();

        for (OWLClass cls : allClasses) {
            if (debug) System.out.println("检查类: " + cls.getIRI().getShortForm());
            for (Map.Entry<OWLDataProperty, Set<OWLClass>> entry : propDomains.entrySet()) {
                OWLDataProperty prop = entry.getKey();
                Set<OWLClass> domains = entry.getValue();

                if (domains.isEmpty()) {
                    allowedProperties.add(prop);
                    if (debug) System.out.println("  -> 属性 " + prop.getIRI().getShortForm() + " 无域，允许");
                } else {
                    boolean isSubClass = false;
                    for (OWLClass domain : domains) {
                        if (reasoner.isEntailed(df.getOWLSubClassOfAxiom(cls, domain))) {
                            isSubClass = true;
                            break;
                        }
                    }
                    if (isSubClass) {
                        allowedProperties.add(prop);
                        if (debug) System.out.println("  -> 属性 " + prop.getIRI().getShortForm() + " 匹配域，允许");
                    }
                }
            }
        }

        if (debug) System.out.println("最终允许属性数量: " + allowedProperties.size());
        return allowedProperties;
    }

    public Set<OWLLiteral> getDataPropertyValueOfIndividual(OWLNamedIndividual ind, OWLDataProperty dataProp) {
        Set<OWLLiteral> result = new HashSet<>();
        if (ind == null || dataProp == null) {
            return result;
        }

        if (reasoner != null) {
            Set<OWLLiteral> values = reasoner.getDataPropertyValues(ind, dataProp);
            result.addAll(values);
        }

        if (result.isEmpty() && ontology != null && manager != null) {
            Set<OWLClass> allTypes = getIndividualAllTypes(ind);
            Set<OWLLiteral> hasValueLiterals = allTypes.stream()
                    .flatMap(cls -> manager.ontologies()
                            .flatMap(ont -> ont.subClassAxiomsForSubClass(cls)))
                    .map(OWLSubClassOfAxiom::getSuperClass)
                    .filter(expr -> expr instanceof OWLDataHasValue)
                    .map(expr -> (OWLDataHasValue) expr)
                    .filter(restriction -> restriction.getProperty().equals(dataProp))
                    .map(OWLDataHasValue::getFiller)
                    .collect(Collectors.toSet());
            result.addAll(hasValueLiterals);
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
        /*
        return reasoner.getDataPropertyDomains(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .map(c -> manager.getOWLDataFactory().getOWLClass(resolveIRI(c.getIRI().getIRIString())))
                .collect(Collectors.toSet()); */
        return reasoner.getDataPropertyDomains(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .map(c -> manager.getOWLDataFactory().getOWLClass(c.getIRI()))
                .collect(Collectors.toSet());
    }

    public Set<OWLDatatype> getDataPropertyRanges(String propIRI) {
        OWLDataProperty prop = getDataProperty(propIRI);
        return ontology.dataPropertyRangeAxioms(prop)
                .map(OWLDataPropertyRangeAxiom::getRange)
                .filter(OWLDatatype.class::isInstance)
                .map(OWLDataRange::asOWLDatatype)
                .collect(Collectors.toSet());
    }

    public boolean isInstanceOf(String individualIRI, String classIRI) {
        OWLNamedIndividual ind = getIndividual(individualIRI);
        OWLClass cls = getClass(classIRI);
        return getIndividualAllTypes(ind).contains(cls);
    }

    // ================= 辅助方法 =================
    public OWLObjectProperty getObjectProperty(String iri) {
        IRI propIRI = IRI.create(iri);
        return manager.ontologies()
                .filter(ont -> ont.containsObjectPropertyInSignature(propIRI))
                .findFirst()
                .map(ignored -> manager.getOWLDataFactory().getOWLObjectProperty(propIRI))
                .orElseThrow(() -> new IllegalArgumentException("对象属性未找到: " + iri));
    }

    public OWLDataProperty getDataProperty(String iri) {
        IRI propIRI = IRI.create(iri);
        return manager.ontologies()
                .filter(ont -> ont.containsDataPropertyInSignature(propIRI))
                .findFirst()
                .map(ignored -> manager.getOWLDataFactory().getOWLDataProperty(propIRI))
                .orElseThrow(() -> new IllegalArgumentException("数据属性未找到: " + iri));
    }

    public Optional<OWLDatatype> getDatatype(String datatypeIRI) {
        IRI dtIRI = IRI.create(datatypeIRI);
        return ontology.datatypesInSignature().filter(dt -> dt.getIRI().equals(dtIRI)).findFirst();
    }

    public String getEntityType(IRI iri) {
        return manager.ontologies()
                .flatMap(ont -> Stream.of(
                        ont.containsClassInSignature(iri) ? Optional.of("Class") : Optional.<String>empty(),
                        ont.containsIndividualInSignature(iri) ? Optional.of("Individual") : Optional.<String>empty(),
                        ont.containsObjectPropertyInSignature(iri) ? Optional.of("ObjectProperty") : Optional.<String>empty(),
                        ont.containsDataPropertyInSignature(iri) ? Optional.of("DataProperty") : Optional.<String>empty(),
                        ont.containsAnnotationPropertyInSignature(iri) ? Optional.of("AnnotationProperty") : Optional.<String>empty(),
                        ont.containsDatatypeInSignature(iri) ? Optional.of("Datatype") : Optional.<String>empty()
                ))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse("Unknown");
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

    @Override
    public void close() {
        if (reasoner != null) reasoner.dispose();
    }

    public String getLabel(OWLOntology ontology, IRI iri, String lang) {
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
        OWLAnnotationProperty rdfsLabel = df.getRDFSLabel();

        Stream<OWLLiteral> labels = ontology.annotationAssertionAxioms(iri)
                .filter(ax -> ax.getProperty().equals(rdfsLabel))
                .map(OWLAnnotationAssertionAxiom::getValue)
                .filter(OWLLiteral.class::isInstance)
                .map(OWLLiteral.class::cast);

        return Stream.<Supplier<Optional<String>>>of(
                        () -> labels.filter(lit -> lit.hasLang(lang)).findFirst().map(OWLLiteral::getLiteral),
                        () -> labels.filter(lit -> !lit.hasLang()).findFirst().map(OWLLiteral::getLiteral),
                        () -> labels.findFirst().map(OWLLiteral::getLiteral)
                )
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(null);
    }
    /**
     * 向本体中添加一个公理，并刷新推理机。
     * @param axiom 要添加的 OWLAxiom（可以是断言、规则、声明等）
     */
    public void addAxiom(OWLAxiom axiom) {
        manager.addAxiom(ontology, axiom);
        reasoner.flush();
    }
    /**
     * 添加一条数据属性断言公理，并刷新推理机。
     * @param individual  主体个体
     * @param property    数据属性
     * @param value       字面量值（OWLLiteral）
     */
    public void addIndividualAxiom(OWLNamedIndividual individual,
                                   OWLDataProperty property,
                                   OWLLiteral value) {
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLDataPropertyAssertionAxiom axiom =
                df.getOWLDataPropertyAssertionAxiom(property, individual, value);
        manager.addAxiom(ontology, axiom);
        reasoner.flush();
    }

    /**
     * 便利方法：添加数据属性断言，值为整数。
     */
    public void addIndividualAxiom(OWLNamedIndividual individual,
                                   OWLDataProperty property,
                                   int value) {
        OWLDataFactory df = manager.getOWLDataFactory();
        addIndividualAxiom(individual, property, df.getOWLLiteral(value));
    }

    /**
     * 便利方法：添加数据属性断言，值为字符串。
     */
    public void addIndividualAxiom(OWLNamedIndividual individual,
                                   OWLDataProperty property,
                                   String value) {
        OWLDataFactory df = manager.getOWLDataFactory();
        addIndividualAxiom(individual, property, df.getOWLLiteral(value));
    }
    /**
     * 添加一条对象属性断言公理，并刷新推理机。
     * @param individual  主体个体
     * @param property    对象属性
     * @param object      宾语个体
     */
    public void addIndividualAxiom(OWLNamedIndividual individual,
                                   OWLObjectProperty property,
                                   OWLNamedIndividual object) {
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLObjectPropertyAssertionAxiom axiom =
                df.getOWLObjectPropertyAssertionAxiom(property, individual, object);
        manager.addAxiom(ontology, axiom);
        reasoner.flush();
    }
    /**
     * 从本体中删除一个公理，并刷新推理机。
     * @param axiom 要删除的公理
     */
    public void removeAxiom(OWLAxiom axiom) {
        manager.removeAxiom(ontology, axiom);
        reasoner.flush();
    }
    /**
     * 批量删除一组公理，并刷新推理机。
     * @param axioms 要删除的公理集合
     */
    public void removeAxiomSet(Set<? extends OWLAxiom> axioms) {
        if (axioms == null || axioms.isEmpty()) {
            return;
        }
        // 批量移除（使用 OWLOntologyManager 的 removeAxioms 方法）
        manager.removeAxioms(ontology, axioms);
        // 通知推理机本体已变化，进行增量更新
        reasoner.flush();
    }
    /**
     * 获取指定个体在某个数据属性上的所有断言公理。
     * @param individual 目标个体
     * @param property 数据属性
     * @return 数据属性断言公理集合
     */
    public Set<OWLDataPropertyAssertionAxiom> getDataPropertyAssertions(
            OWLNamedIndividual individual, OWLDataProperty property) {
        return ontology.getDataPropertyAssertionAxioms(individual)
                .stream()
                .filter(ax -> ax.getProperty().equals(property))
                .collect(Collectors.toSet());
    }
    /**
     * 获取指定个体的所有类断言公理（显式声明的类型，非推理结果）。
     * @param individual 目标个体
     * @return 类断言公理集合
     */
    public Set<OWLClassAssertionAxiom> getClassAssertions(OWLNamedIndividual individual) {
        return ontology.getClassAssertionAxioms(individual);
    }
    /**
     * 获取指定个体的所有类断言公理，包含显式声明和推理结果。
     * @param individual 目标个体
     * @return 合并后的类断言公理集合
     */
    public Set<OWLClassAssertionAxiom> getAllClassAssertionAxioms(OWLNamedIndividual individual) {
        // 1. 显式公理
        Set<OWLClassAssertionAxiom> explicit = ontology.getClassAssertionAxioms(individual);

        // 2. 推理得出的类型
        OWLDataFactory df = manager.getOWLDataFactory();
        Set<OWLClass> reasonedTypes = reasoner.getTypes(individual, false).getFlattened();
        Set<OWLClassAssertionAxiom> reasonedAxioms = new HashSet<>();
        for (OWLClass cls : reasonedTypes) {
            reasonedAxioms.add(df.getOWLClassAssertionAxiom(cls, individual));
        }

        // 3. 合并去重
        Set<OWLClassAssertionAxiom> result = new HashSet<>(explicit);
        result.addAll(reasonedAxioms);
        return result;
    }

    public Number parseNumeric(OWLLiteral literal) {
        if (literal == null) return null;
        try {
            if (literal.isInteger()) {
                return literal.parseInteger();
            } else if (literal.isDouble()) {
                return literal.parseDouble();
            } else if (literal.isFloat()) {
                return literal.parseFloat();
            } else {
                // 尝试按 double 解析字符串
                return Double.parseDouble(literal.getLiteral());
            }
        } catch (Exception e) {
            return null;
        }
    }
}