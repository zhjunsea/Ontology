package org.junzhang.ontologymachine;

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
    private final OWLReasoner reasoner;
    private static boolean debug = false;
    private final DefaultPrefixManager prefixManager;

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
        if (loadedOntology != null) {
            System.out.println("主本体加载成功: " + loadedOntology.getOntologyID().getOntologyIRI().orElse(null));
        } else {
            System.err.println("警告：主本体加载失败！");
        }

        // 2. 合并所有本体（入口 + 所有导入）
        IRI mergedIri = IRI.create("http://example.org/pizza/merged_total");
        OWLOntology totalOnt = manager.createOntology(mergedIri);
        manager.ontologies()
                .filter(ont -> !ont.equals(totalOnt))
                .forEach(ont -> ont.axioms().forEach(totalOnt::addAxiom));
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

    public OWLOntology loadOntologyWithoutRecursive(String mainFile, OWLOntologyManager manager)
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
    }

    // ============================================
    // ================= 类相关查询 =================
    // ============================================
    public Set<OWLNamedIndividual> getIndividuals(String classIRI) {
        OWLClass cls = getClass(classIRI);
        Set<OWLNamedIndividual> result = new HashSet<>();
        IRI targetIRI = cls.getIRI();

        // 从合并后的本体获取所有类断言公理
        ontology.axioms(AxiomType.CLASS_ASSERTION)
                .forEach(ax -> {
                    OWLClassExpression expr = ax.getClassExpression();
                    if (expr.isOWLClass()) {
                        OWLClass asserted = expr.asOWLClass();
                        // 通过 resolveIRI 将短名转换为完整 IRI
                        IRI resolvedIRI = resolveIRI(asserted.getIRI().getIRIString());
                        if (resolvedIRI.equals(targetIRI)) {
                            OWLIndividual ind = ax.getIndividual();
                            if (ind instanceof OWLNamedIndividual) {
                                result.add((OWLNamedIndividual) ind);
                            }
                        }
                    }
                });

        // 添加推理机推断的实例（包含推断）
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
                .filter(OWLObjectRestriction.class::isInstance)        // 替换 lambda
                .map(OWLObjectRestriction.class::cast)                 // 替换 lambda
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
            IRI iri = resolveIRI(cls.getIRI().getIRIString());
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

    public OWLClass getClass(String classIRIOrQName) {
        IRI iri = resolveIRI(classIRIOrQName);
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
        return reasoner.getDataPropertyDomains(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .map(c -> manager.getOWLDataFactory().getOWLClass(resolveIRI(c.getIRI().getIRIString())))
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

    // ================= 数据类型查询 =================
    public Optional<OWLDatatype> getDatatype(String datatypeIRI) {
        IRI dtIRI = IRI.create(datatypeIRI);
        return ontology.datatypesInSignature().filter(dt -> dt.getIRI().equals(dtIRI)).findFirst();
    }

    // ================= 实体类型判断 =================
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

    // 辅助解析 JSON 数组（保留，未涉及废弃API）
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

        // 获取所有 rdfs:label 字面量流
        Stream<OWLLiteral> labels = ontology.annotationAssertionAxioms(iri)
                .filter(ax -> ax.getProperty().equals(rdfsLabel))
                .map(OWLAnnotationAssertionAxiom::getValue)
                .filter(OWLLiteral.class::isInstance)
                .map(OWLLiteral.class::cast);

        // 优先返回指定语言的标签，其次无语言标签，最后任意标签
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
}