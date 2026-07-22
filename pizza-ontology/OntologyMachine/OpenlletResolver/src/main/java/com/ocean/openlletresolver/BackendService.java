package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.GenericDbWriter;
import com.ocean.ontopobdahandler.OBDAHandler;
import org.apache.jena.rdf.model.*;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.ocean.ontopobdahandler.OBDAHandler.escapeSparqlUri;
import static com.ocean.ontopobdahandler.OBDAHandler.queryConstruct;
import static com.ocean.openlletresolver.OntologyService.extractNamespace;
import static com.ocean.openlletresolver.OntologyService.validateTypeAxiom;

public class BackendService implements AutoCloseable {
    private static final ReadWriteLock lock = new ReentrantReadWriteLock();
    private static volatile BackendService instance;
    //private static final Object lock = new Object();

    public static void setInstance(BackendService instance) {
        BackendService.instance = instance;
    }

    private static final Logger log = LoggerFactory.getLogger(BackendService.class);

    public OntologyService getOntologyService() {
        return ontologyService;
    }

    private OntologyService ontologyService = null;

    public ReasonerService getReasonerService() {
        return reasonerService;
    }

    private ReasonerService reasonerService = null;

    public OBDAHandler getObdaHandler() {
        return obdaHandler;
    }

    private OBDAHandler obdaHandler;

    private static final List<String> excludePrefixes = List.of(
            "http://www.w3.org/2004/02/skos/core#",
            "http://www.w3.org/2008/05/skos#",
            "http://www.w3.org/2009/08/skos-simple#",
            "http://www.w3.org/2002/07/owl#",
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    );
    public record objectPair(String objectName, String columnName) {}

    // ================= 有后端数据库的构造函数 ================
    private BackendService(String mainOntologyPath, OBDAHandler obdaHandler) throws Exception {
        // 1. 创建 ontology 服务
        log.info("🧠 [TBox] 加载本体并执行 Openllet OWL DL 推理...");
        ontologyService = new OntologyService(mainOntologyPath);
        log.info("合并本体公理总数：" + ontologyService.gettBoxOntology().getAxiomCount());
        log.info("Manager已加载本体个数：" + ontologyService.getManager().ontologies().count());
        log.info("✅ TBox 推理完成！隐式公理已展开。\n");

        // ==========================================
        // 2. 连接 ABox: Ontop 虚拟化数据层
        // ==========================================
        this.obdaHandler = obdaHandler;

        // 3. 创建 reasoner 服务
        reasonerService = new ReasonerService(ontologyService);
    }

    // ================= 没有后端数据库的构造函数 ================
    private BackendService(String mainOntologyPath) throws Exception {
        // 1. 创建 ontology 服务
        log.info("🧠 [TBox] 加载本体并执行 Openllet OWL DL 推理...");
        ontologyService = new OntologyService(mainOntologyPath);
        log.info("合并本体公理总数：" + ontologyService.gettBoxOntology().getAxiomCount());
        log.info("Manager已加载本体个数：" + ontologyService.getManager().ontologies().count());
        log.info("✅ TBox 推理完成！隐式公理已展开。\n");

        // ==========================================
        // 2. 连接 ABox: Ontop 虚拟化数据层
        // ==========================================
        log.info("🗄️ [ABox] 连接 Ontop VKG Endpoint...");
        obdaHandler = OBDAHandler.getInstance();
        log.info("ABox 单例初始化成功\n");

        // 3. 创建 reasoner 服务
        reasonerService = new ReasonerService(ontologyService);
    }

    /**
     * 1. TBox 推理：获取指定类及其所有子类的 IRI 字符串集合
     * @param classIri 目标类的完整 IRI
     * @return 包含自身及所有子类的 IRI 字符串 Set
     */
    public Set<String> getSubClassIris(String classIri) {
        Set<OWLClass> subClasses = getSubClasses(classIri);

        if (subClasses == null || subClasses.isEmpty()) {
            return Collections.emptySet();
        }

        return subClasses.stream()
                .map(cls -> cls.getIRI().getIRIString())
                .collect(Collectors.toSet());
    }

    /**
     * 检测断言注入后，是否会引起矛盾
     * 写入路径：临时注入ABox → 验证一致性 → 立即清除
     * @return true=一致可写入, false=存在矛盾
     */
    public boolean validateAxioms(Set<OWLAxiom> tempAxioms) {
        lock.writeLock().lock();
        boolean consistent = true;
        try {
            ontologyService.getManager().addAxioms(ontologyService.gettBoxOntology(), tempAxioms);

            // 重新评估SWRL规则和一致性
            reasonerService.getReasoner().flush();
            if(!reasonerService.getReasoner().isConsistent()){
                consistent = false;
                reasonerService.ExplainInconsistencyWithOWLExplanation(ontologyService);
            }

            // 【关键】无论结果如何，立即移除临时ABox
            ontologyService.getManager().removeAxioms(ontologyService.gettBoxOntology(), tempAxioms);
            reasonerService.getReasoner().flush();

            return consistent;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static BackendService getInstance(String mainOntologyPath, OBDAHandler obdaHandler) throws Exception {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new BackendService(mainOntologyPath, obdaHandler);
                }
            }
        }
        return instance;
    }

    public static BackendService getInstance(String mainOntologyPath) throws Exception {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new BackendService(mainOntologyPath);
                }
            }
        }
        return instance;
    }

    // ============================================
    // ================= 类相关查询 =================
    // ============================================
    public Set<OWLNamedIndividual> getIndividuals(String classIRI) {
        OWLClass cls = getClass(classIRI);
        Set<OWLNamedIndividual> result = new HashSet<>();
        IRI targetIRI = cls.getIRI();

        ontologyService.gettBoxOntology().axioms(AxiomType.CLASS_ASSERTION)
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

        Set<OWLNamedIndividual> inferred = reasonerService.getReasoner().getInstances(cls, true)
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
        return reasonerService.getReasoner().getSuperClasses(cls, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getSubClasses(String classIRI) {
        OWLClass cls = getClass(classIRI);
        return reasonerService.getReasoner().getSubClasses(cls, true).entities()
                .filter(c -> !c.isOWLNothing())
                .collect(Collectors.toSet());
    }

    public Set<OWLObjectPropertyExpression> getAllObjectPropertiesOfClass(OWLClass cls) {
        Set<OWLObjectPropertyExpression> result = new HashSet<>();
        OWLDataFactory df = ontologyService.gettBoxOntology().getOWLOntologyManager().getOWLDataFactory();

        Set<OWLClass> allSuperClasses = new HashSet<>();
        allSuperClasses.add(cls);
        reasonerService.getReasoner().getSuperClasses(cls, true).entities().forEach(allSuperClasses::add);

        ontologyService.gettBoxOntology().objectPropertiesInSignature().forEach(prop -> {
            Set<OWLClassExpression> domains = ontologyService.gettBoxOntology().objectPropertyDomainAxioms(prop.asOWLObjectProperty())
                    .map(OWLObjectPropertyDomainAxiom::getDomain)
                    .collect(Collectors.toSet());

            if (domains.isEmpty()) {
                return;
            }

            boolean matched = domains.stream().anyMatch(domain ->
                    allSuperClasses.stream().anyMatch(superCls ->
                            reasonerService.getReasoner().isEntailed(df.getOWLSubClassOfAxiom(superCls, domain))));
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
        return ontologyService.gettBoxOntology().objectPropertyDomainAxioms(prop)
                .map(OWLObjectPropertyDomainAxiom::getDomain)
                .collect(Collectors.toSet());
    }

    public Set<OWLClassExpression> getObjectPropertyRange(OWLObjectPropertyExpression prop) {
        if (prop == null) {
            return new HashSet<>();
        }
        return ontologyService.gettBoxOntology().objectPropertyRangeAxioms(prop)
                .map(OWLObjectPropertyRangeAxiom::getRange)
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getObjectPropertyDomains(String propIRI) {
        OWLObjectProperty prop = getObjectProperty(propIRI);
        return reasonerService.getReasoner().getObjectPropertyDomains(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getObjectPropertyRanges(String propIRI) {
        OWLObjectProperty prop = getObjectProperty(propIRI);
        return reasonerService.getReasoner().getObjectPropertyRanges(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLClassExpression> getObjectPropertyLimitations(OWLClass cls, OWLObjectPropertyExpression prop) {
        if (cls == null || prop == null) {
            return new HashSet<>();
        }

        Set<OWLClass> allSuperClasses = new HashSet<>();
        allSuperClasses.add(cls);
        reasonerService.getReasoner().getSuperClasses(cls, true).entities().forEach(allSuperClasses::add);

        return Stream.concat(Stream.of(ontologyService.gettBoxOntology()), ontologyService.gettBoxOntology().importsClosure())
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
        return ontologyService.getManager().ontologies()
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
        Stream.concat(Stream.of(ontologyService.gettBoxOntology()), ontologyService.gettBoxOntology().importsClosure())
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
        OWLAnnotationProperty targetProp = ontologyService.gettBoxOntology().getOWLOntologyManager().getOWLDataFactory()
                .getOWLAnnotationProperty(IRI.create(annotationPropertyIRI));
        return allAnnotations.getOrDefault(targetProp, new HashSet<>());
    }

    // ============================================
    // =============== 个体相关查询 =================
    // ============================================
    public OWLNamedIndividual getIndividual(String individualIRI) {
        IRI indIRI = IRI.create(individualIRI);
        boolean exists = ontologyService.getManager().ontologies().anyMatch(ont -> ont.containsIndividualInSignature(indIRI));
        if (!exists) {
            throw new IllegalArgumentException("个体未找到: " + individualIRI);
        }
        return ontologyService.getManager().getOWLDataFactory().getOWLNamedIndividual(indIRI);
    }

    public Set<OWLClass> getIndividualDirectTypes(OWLNamedIndividual ind) {
        return reasonerService.getReasoner().getTypes(ind, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getIndividualAllTypes(OWLNamedIndividual ind) {
        Set<OWLClass> result = new HashSet<>();
        Set<OWLClass> directTypes = getIndividualDirectTypes(ind);
        log.info("直接类型: " + directTypes.stream().map(c -> c.getIRI().getShortForm()).toList());

        for (OWLClass cls : directTypes) {
            //IRI iri = resolveIRI(cls.getIRI().getIRIString());
            IRI iri = cls.getIRI();
            OWLDataFactory df = ontologyService.getManager().getOWLDataFactory();
            cls = df.getOWLClass(iri);
            result.add(cls);
            Set<OWLClass> supers = reasonerService.getReasoner().getSuperClasses(cls, false).entities()
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
            log.info("类集合为空");
            return;
        }
        log.info("类集合内容：");
        for (OWLClass cls : classes) {
            IRI iri = cls.getIRI();
            String shortForm = iri.getShortForm();
            String iriStr = iri.toString();
            String qname = null;
            log.info("  短名: %-30s  IRI: %-50s  QName: %s%n", shortForm, iriStr, qname);
        }
    }

    public OWLClass getClass(String classIRIOrQName) {
        //IRI iri = resolveIRI(classIRIOrQName);
        IRI iri = IRI.create(classIRIOrQName);
        return ontologyService.getManager().ontologies()
                .filter(ont -> ont.containsClassInSignature(iri))
                .findFirst()
                .map(ignored -> ontologyService.getManager().getOWLDataFactory().getOWLClass(iri))
                .orElseThrow(() -> new IllegalArgumentException("类未找到: " + classIRIOrQName));
    }

    public Set<OWLObjectPropertyExpression> getObjectPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLObjectPropertyExpression> properties = new HashSet<>();
        if (ind == null) return properties;

        ontologyService.gettBoxOntology().objectPropertyAssertionAxioms(ind)
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

        return Stream.concat(Stream.of(ontologyService.gettBoxOntology()), ontologyService.gettBoxOntology().importsClosure())
                .flatMap(ont -> ont.objectPropertyAssertionAxioms(ind))
                .filter(axiom -> axiom.getProperty().equals(prop))
                .map(OWLObjectPropertyAssertionAxiom::getObject)
                .filter(OWLNamedIndividual.class::isInstance)
                .map(OWLNamedIndividual.class::cast)
                .collect(Collectors.toSet());
    }

    public Set<OWLNamedIndividual> getObjectPropertyAllValueOfIndividual(
            OWLNamedIndividual ind, OWLObjectPropertyExpression prop) {
        if (ind == null || prop == null || reasonerService.getReasoner() == null) {
            return new HashSet<>();
        }
        NodeSet<OWLNamedIndividual> values = reasonerService.getReasoner().getObjectPropertyValues(ind, prop);
        return values.entities().collect(Collectors.toSet());
    }

    public Set<OWLDataProperty> getDirectDataPropertiesOfIndividual(OWLNamedIndividual ind) {
        if (ind == null) return new HashSet<>();
        return ontologyService.gettBoxOntology().dataPropertyAssertionAxioms(ind)
                .map(axiom -> axiom.getProperty().asOWLDataProperty())
                .collect(Collectors.toSet());
    }

    public Set<OWLDataProperty> getAllAllowedDataPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLDataProperty> allowedProperties = new HashSet<>();
        if (ind == null || reasonerService.getReasoner() == null || ontologyService.gettBoxOntology() == null) {
            return allowedProperties;
        }

        Set<OWLClass> allClasses = this.getIndividualAllTypes(ind);
        log.info("个体类型数量: " + allClasses.size());

        Set<OWLDataProperty> allDataProps = new HashSet<>();
        for (OWLDataPropertyExpression expr : ontologyService.gettBoxOntology().dataPropertiesInSignature().collect(Collectors.toList())) {
            allDataProps.add(expr.asOWLDataProperty());
        }
        log.info("总数据属性数量: " + allDataProps.size());

        Map<OWLDataProperty, Set<OWLClass>> propDomains = new HashMap<>();
        for (OWLDataProperty prop : allDataProps) {
            Set<OWLClass> domains = getDataPropertyDomains(prop);
            propDomains.put(prop, domains);
            log.debug("属性 " + prop.getIRI().getShortForm() + " 的域大小: " + domains.size());
        }

        OWLDataFactory df = ontologyService.gettBoxOntology().getOWLOntologyManager().getOWLDataFactory();

        for (OWLClass cls : allClasses) {
            log.debug("检查类: " + cls.getIRI().getShortForm());
            for (Map.Entry<OWLDataProperty, Set<OWLClass>> entry : propDomains.entrySet()) {
                OWLDataProperty prop = entry.getKey();
                Set<OWLClass> domains = entry.getValue();

                if (domains.isEmpty()) {
                    allowedProperties.add(prop);
                    log.debug("  -> 属性 " + prop.getIRI().getShortForm() + " 无域，允许");
                } else {
                    boolean isSubClass = false;
                    for (OWLClass domain : domains) {
                        if (reasonerService.getReasoner().isEntailed(df.getOWLSubClassOfAxiom(cls, domain))) {
                            isSubClass = true;
                            break;
                        }
                    }
                    if (isSubClass) {
                        allowedProperties.add(prop);
                        log.debug("  -> 属性 " + prop.getIRI().getShortForm() + " 匹配域，允许");
                    }
                }
            }
        }

        log.info("最终允许属性数量: " + allowedProperties.size());
        return allowedProperties;
    }

    public Set<OWLLiteral> getDataPropertyValueOfIndividual(OWLNamedIndividual ind, OWLDataProperty dataProp) {
        Set<OWLLiteral> result = new HashSet<>();
        if (ind == null || dataProp == null) {
            return result;
        }

        if (reasonerService.getReasoner() != null) {
            Set<OWLLiteral> values = reasonerService.getReasoner().getDataPropertyValues(ind, dataProp);
            result.addAll(values);
        }

        if (result.isEmpty() && ontologyService.gettBoxOntology() != null && ontologyService.getManager() != null) {
            Set<OWLClass> allTypes = getIndividualAllTypes(ind);
            Set<OWLLiteral> hasValueLiterals = allTypes.stream()
                    .flatMap(cls -> ontologyService.getManager().ontologies()
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
        return reasonerService.getReasoner().getDataPropertyDomains(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .map(c -> manager.getOWLDataFactory().getOWLClass(resolveIRI(c.getIRI().getIRIString())))
                .collect(Collectors.toSet()); */
        return reasonerService.getReasoner().getDataPropertyDomains(prop, true).entities()
                .filter(c -> !c.isOWLThing())
                .map(c -> ontologyService.getManager().getOWLDataFactory().getOWLClass(c.getIRI()))
                .collect(Collectors.toSet());
    }

    public Set<OWLDatatype> getDataPropertyRanges(String propIRI) {
        OWLDataProperty prop = getDataProperty(propIRI);
        return ontologyService.gettBoxOntology().dataPropertyRangeAxioms(prop)
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
        return ontologyService.getManager().ontologies()
                .filter(ont -> ont.containsObjectPropertyInSignature(propIRI))
                .findFirst()
                .map(ignored -> ontologyService.getManager().getOWLDataFactory().getOWLObjectProperty(propIRI))
                .orElseThrow(() -> new IllegalArgumentException("对象属性未找到: " + iri));
    }

    public OWLDataProperty getDataProperty(String iri) {
        IRI propIRI = IRI.create(iri);
        return ontologyService.getManager().ontologies()
                .filter(ont -> ont.containsDataPropertyInSignature(propIRI))
                .findFirst()
                .map(ignored -> ontologyService.getManager().getOWLDataFactory().getOWLDataProperty(propIRI))
                .orElseThrow(() -> new IllegalArgumentException("数据属性未找到: " + iri));
    }

    public Optional<OWLDatatype> getDatatype(String datatypeIRI) {
        IRI dtIRI = IRI.create(datatypeIRI);
        return ontologyService.gettBoxOntology().datatypesInSignature().filter(dt -> dt.getIRI().equals(dtIRI)).findFirst();
    }

    public String getEntityType(IRI iri) {
        return ontologyService.getManager().ontologies()
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
        if (reasonerService.getReasoner() != null) reasonerService.getReasoner().dispose();
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
        ontologyService.getManager().addAxiom(ontologyService.gettBoxOntology(), axiom);
        reasonerService.getReasoner().flush();
        //this.isCacheDirty = true;
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
        OWLDataPropertyAssertionAxiom axiom =
                ontologyService.getDataFactory().getOWLDataPropertyAssertionAxiom(property, individual, value);
        ontologyService.getManager().addAxiom(ontologyService.gettBoxOntology(), axiom);
        reasonerService.getReasoner().flush();
        //this.isCacheDirty = true;
    }

    /**
     * 便利方法：添加数据属性断言，值为整数。
     */
    public void addIndividualAxiom(OWLNamedIndividual individual,
                                   OWLDataProperty property,
                                   int value) {
        addIndividualAxiom(individual, property, ontologyService.getDataFactory().getOWLLiteral(value));
    }

    /**
     * 便利方法：添加数据属性断言，值为字符串。
     */
    public void addIndividualAxiom(OWLNamedIndividual individual,
                                   OWLDataProperty property,
                                   String value) {
        addIndividualAxiom(individual, property, ontologyService.getDataFactory().getOWLLiteral(value));
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
        OWLObjectPropertyAssertionAxiom axiom =
                ontologyService.getDataFactory().getOWLObjectPropertyAssertionAxiom(property, individual, object);
        ontologyService.getManager().addAxiom(ontologyService.gettBoxOntology(), axiom);
        reasonerService.getReasoner().flush();
        //this.isCacheDirty = true;
    }
    /**
     * 从本体中删除一个公理，并刷新推理机。
     * @param axiom 要删除的公理
     */
    public void removeAxiom(OWLAxiom axiom) {
        ontologyService.getManager().removeAxiom(ontologyService.gettBoxOntology(), axiom);
        reasonerService.getReasoner().flush();
        //this.isCacheDirty = true;
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
        ontologyService.getManager().removeAxioms(ontologyService.gettBoxOntology(), axioms);
        // 通知推理机本体已变化，进行增量更新
        reasonerService.getReasoner().flush();
        //this.isCacheDirty = true;
    }
    /**
     * 获取指定个体在某个数据属性上的所有断言公理。
     * @param individual 目标个体
     * @param property 数据属性
     * @return 数据属性断言公理集合
     */
    public Set<OWLDataPropertyAssertionAxiom> getDataPropertyAssertions(
            OWLNamedIndividual individual, OWLDataProperty property) {
        return ontologyService.gettBoxOntology().getDataPropertyAssertionAxioms(individual)
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
        return ontologyService.gettBoxOntology().getClassAssertionAxioms(individual);
    }
    /**
     * 获取指定个体的所有类断言公理，包含显式声明和推理结果。
     * @param individual 目标个体
     * @return 合并后的类断言公理集合
     */
    public Set<OWLClassAssertionAxiom> getAllClassAssertionAxioms(OWLNamedIndividual individual) {
        // 1. 显式公理
        Set<OWLClassAssertionAxiom> explicit = ontologyService.gettBoxOntology().getClassAssertionAxioms(individual);

        // 2. 推理得出的类型
        OWLDataFactory df = ontologyService.getManager().getOWLDataFactory();
        Set<OWLClass> reasonedTypes = reasonerService.getReasoner().getTypes(individual, false).getFlattened();
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
    // ================= 2. 核心安全写入引擎 (完全通用) =================
    public void safeVerifyAndDBExecution(Set<OWLAxiom> tempAxioms, String typeIRI, GenericDbWriter.DbWriteAction dbWriteAction)
            throws Exception {

        validateTypeAxiom(tempAxioms, typeIRI, reasonerService.getReasoner());
        OWLOntologyManager manager = ontologyService.getManager();

        log.info("[Step 1-2] 加载本体基线并注入临时公理...");
        OWLOntology baseline = ontologyService.gettBoxOntology();

        //测试，看看加入之前是否一致
        if (!reasonerService.getReasoner().isConsistent()) {
            reasonerService.ExplainInconsistencyWithBlackBoxExplanation(baseline);
            throw new IllegalStateException("一致性检查失败！新数据与现有本体存在矛盾，已拦截写入。");
        }
        manager.addAxioms(baseline, tempAxioms);

        log.info("[Step 3] 执行推理一致性预校验...");
        boolean consistent;
        try {
            //consistent = ReasonerService.checkConsistency(baseline);
            reasonerService.getReasoner().flush();
            consistent = reasonerService.getReasoner().isConsistent();
            if (!consistent) {
                reasonerService.ExplainInconsistencyWithBlackBoxExplanation(baseline);
                throw new IllegalStateException("一致性检查失败！新数据与现有本体存在矛盾，已拦截写入。");
            }
            log.info("✅ 预校验通过，无逻辑矛盾");
        } finally {
            // ⭐ 关键修复：无论校验成功还是失败，都必须从基线中移除临时公理
            // 防止污染内存中的 TBox 单例
            log.info("[Step 3.5] 清理临时公理，恢复本体基线...");
            manager.removeAxioms(baseline, tempAxioms);
            log.info("✅ 已移除 {} 条临时公理，本体基线已恢复", tempAxioms.size());
        }
        if(dbWriteAction == null){
            log.info("结束校验，不做数据库操作...");
            return;
        }
        log.info("[Step 4] 执行数据库持久化...");
        dbWriteAction.execute();
        log.info("✅ 数据库写入成功");

    }

    /*
    //tbox作为沙箱进行一致性验证，避免污染原tbox，上面的函数利用原tbox，好处是资源少
    public void safeVerifyAndDBExecution(Set<OWLAxiom> tempAxioms, String typeIRI, GenericDbWriter.DbWriteAction dbWriteAction)
            throws Exception {

        // Step 0: TBox 结构级校验（不涉及 ABox，使用全局 Reasoner 是安全的）
        validateTypeAxiom(tempAxioms, typeIRI, reasonerService.getReasoner());

        OWLOntologyManager manager = ontologyService.getManager();
        OWLDataFactory df = manager.getOWLDataFactory();
        OWLOntology baseline = ontologyService.gettBoxOntology();

        log.info("[Step 1-2] 创建隔离验证本体并注入临时公理...");
        OWLOntology validationOntology = null;
        OWLReasoner validationReasoner = null;

        try {
            // ⭐ 创建完全隔离的临时本体，通过 import 引用基线 TBox
            // 临时公理只加到这个副本上，全局单例零触碰
            IRI tempIRI = IRI.create("urn:temp:validation-" + System.nanoTime());
            validationOntology = manager.createOntology(tempIRI);

            // 导入基线本体（包含完整的 TBox + 已有 ABox + SWRL 规则）
            IRI baselineIRI = baseline.getOntologyID().getOntologyIRI()
                    .orElseThrow(() -> new IllegalStateException("基线本体缺少 IRI，无法创建 import"));
            OWLImportsDeclaration importDecl = df.getOWLImportsDeclaration(baselineIRI);
            manager.applyChange(new AddImport(validationOntology, importDecl));

            // 仅向隔离本体添加本次更新的临时公理
            manager.addAxioms(validationOntology, tempAxioms);
            log.info("   已注入 {} 条临时公理到隔离验证环境", tempAxioms.size());

            log.info("[Step 3] 执行推理一致性预校验（隔离模式）...");
            // ⭐ 为隔离本体创建独立的临时 Reasoner，零缓存干扰
            validationReasoner = reasonerService.getFactory().createReasoner(validationOntology);

            //测试本体中的公理
            validationOntology.axioms().forEach(ax -> System.out.println(ax));
            //测试导入了几个公理
            validationReasoner.getRootOntology().getImportsClosure().forEach(o ->
                    System.out.println("导入的本体: " + o.getOntologyID()));

            boolean consistent = validationReasoner.isConsistent();

            if (!consistent) {
                // BlackBox 在隔离本体上运行，解释结果精确且不受全局状态影响
                reasonerService.ExplainInconsistencyWithBlackBoxExplanation(validationOntology);
                throw new IllegalStateException("一致性检查失败！新数据与现有本体存在矛盾，已拦截写入。");
            }
            log.info("✅ 预校验通过，无逻辑矛盾");

        } finally {
            // ⭐ 无论成功/失败/异常，销毁临时资源，全局单例始终干净
            if (validationReasoner != null) {
                validationReasoner.dispose();
            }
            if (validationOntology != null) {
                manager.removeOntology(validationOntology);
            }
            log.info("✅ 隔离验证环境已销毁，全局本体基线未受任何影响");
        }

        // 预校验通过后，才执行数据库操作
        if (dbWriteAction == null) {
            log.info("结束校验，不做数据库操作...");
            return;
        }

        log.info("[Step 4] 执行数据库持久化...");
        dbWriteAction.execute();
        log.info("✅ 数据库写入成功");


        //System.out.println("[Step 5] 重新拉取 ABox 并验证推理结果...");
        //OWLOntology tbox = ontologyService.gettBoxOntology();
        //OWLOntology freshAbox = loadAboxFromOntop(verifySparql, ontologyService.gettBoxOntology());
        //PizzaQueryService.queryPizzaComponentTypes(tbox, freshAbox);
    }
    */

    /** 兜底方案：导出不一致本体供 Protégé 可视化分析 */
    private static void exportForProtege(OWLOntology ontology) {
        try {
            java.io.File out = new java.io.File("debug-inconsistent.ttl");
            OWLManager.createOWLOntologyManager().saveOntology(ontology,
                    new org.semanticweb.owlapi.formats.TurtleDocumentFormat(),
                    IRI.create(out));
            log.error("   📁 已导出至 " + out.getAbsolutePath() + "，请用 Protégé 打开分析");
        } catch (Exception ex) {
            log.error("   ❌ 导出失败: " + ex.getMessage());
        }
    }

    /**
     * 诊断指定类在推理机中的子类层次及真实领域个体分布
     * 自动过滤 SKOS 概念、元建模/punning 伪个体、内置命名空间个体
     * 同时打印每个真实个体的 IRI
     */
    public void diagnoseClassHierarchy(OWLClass targetCls, OWLOntology ontology) {
        log.info("\n=== " + targetCls.getIRI().getShortForm() + " 子类及真实个体诊断 ===");
        log.info("目标类 IRI: " + targetCls.getIRI());

        // 1. 诊断所有子类
        OWLReasoner reasoner = reasonerService.getReasoner();
        NodeSet<OWLClass> subClasses = reasoner.getSubClasses(targetCls, false);
        for (OWLClass sub : subClasses.getFlattened()) {
            if (sub.isOWLNothing()) continue;
            printClassDiagnostics(reasoner, sub, ontology, excludePrefixes, "  ");
        }

        // 2. 诊断目标类自身
        log.info("---");
        printClassDiagnostics(reasoner, targetCls, ontology, excludePrefixes, "  ");
        log.info("===================================\n");
    }

    /**
     * 打印单个类的真实个体统计、IRI 列表及断言类型诊断
     */
    private static void printClassDiagnostics(OWLReasoner reasoner, OWLClass cls,
                                              OWLOntology ontology, List<String> excludePrefixes,
                                              String indent) {
        // 获取并过滤直接实例与全部实例
        Set<OWLNamedIndividual> directReal = filterRealIndividuals(
                reasoner.getInstances(cls, true).getFlattened(), ontology);
        Set<OWLNamedIndividual> allReal = filterRealIndividuals(
                reasoner.getInstances(cls, false).getFlattened(), ontology);

        // 打印统计摘要
        if (log.isDebugEnabled()) {
            log.debug(String.format("%s%-40s | 真实直接实例: %3d | 真实全部实例: %3d",
                    indent, cls.getIRI().getShortForm(), directReal.size(), allReal.size()));
        }

        // ⭐ 打印每个真实个体的 IRI 及断言类型分布
        if (!allReal.isEmpty()) {
            allReal.stream()
                    .sorted(Comparator.comparing(ind -> ind.getIRI().toString()))
                    .forEach(ind -> {
                        // 统计该个体的各类断言数量
                        long dataCount = ontology.getDataPropertyAssertionAxioms(ind).size();
                        long annoCount = ontology.getAnnotationAssertionAxioms(ind.getIRI()).size();
                        long classCount = ontology.getClassAssertionAxioms(ind).size();
                        long objPropCount = ontology.getObjectPropertyAssertionAxioms(ind).size();

                        // 打印个体 IRI 及断言统计
                        if (log.isDebugEnabled()) {
                            log.debug(String.format("%s    → %-60s | Data:%-3d Anno:%-3d Class:%-3d ObjProp:%-3d",
                                    indent, ind.getIRI(), dataCount, annoCount, classCount, objPropCount));
                        }

                        // ⚠️ 若存在 AnnotationAssertion，采样打印前2条辅助排查降级问题
                        if (annoCount > 0) {
                            ontology.getAnnotationAssertionAxioms(ind.getIRI()).stream()
                                    .limit(2)
                                    .forEach(ax -> {
                                        if (log.isDebugEnabled()) {
                                            log.debug(String.format("%s        ⚠️ Annotation降级: %s = %s",
                                                    indent,
                                                    ax.getProperty().getIRI().getShortForm(),
                                                    ax.getValue()));
                                        }
                                    });
                        }
                    });
        }
    }

    /**
     * 过滤出真正的领域个体
     */
    public static Set<OWLNamedIndividual> filterRealIndividuals(
            Set<OWLNamedIndividual> individuals, OWLOntology ontology) {
        return individuals.stream()
                .filter(ind -> {
                    String iri = ind.getIRI().toString();
                    if (excludePrefixes.stream().anyMatch(iri::startsWith)) return false;
                    if (ontology.containsClassInSignature(ind.getIRI())) return false;
                    return true;
                })
                .collect(Collectors.toSet());
    }

    /**
     * 查询指定属性三元组并返回可直接添加到本体的 OWLAxiom
     * 复用项目已有的 GenericAxiomBuilder 进行转换
     */
    public Set<OWLAxiom> queryPropertyAxiom(String typeNS, String indNS, String individualName, String propertyIri) {
        String safeIndividual = escapeSparqlUri(individualName);
        String safeProperty = escapeSparqlUri(propertyIri);
        //String target_NS = extractNamespace(individualName); // ⚠️ 注意：这里应该是命名空间，不是完整IRI
        String target_NS = indNS;

        // CONSTRUCT 同时返回目标属性三元组和 rdf:type 三元组
        String constructSparql = """
        CONSTRUCT {
            ?individual ?property ?value .
            ?individual a ?type .
        }
        WHERE {
            VALUES (?individual ?property) { (<%s> <%s>) }
            ?individual ?property ?value ;
                      a ?type .
        }
        """.formatted(indNS+safeIndividual, safeProperty);

        Model resultModel = queryConstruct(constructSparql);
        try {
            if (resultModel.isEmpty()) {
                return null;
            }

            boolean isObjectProperty = ontologyService.checkIsObjectProperty(propertyIri);

            List<GenericAxiomBuilder.Triple> triples = new ArrayList<>();
            StmtIterator it = resultModel.listStatements();
            while (it.hasNext()) {
                org.apache.jena.graph.Triple jenaTriple = it.next().asTriple();
                triples.add(new GenericAxiomBuilder.Triple(
                        jenaTriple.getSubject().toString(),
                        jenaTriple.getPredicate().toString(),
                        jenaTriple.getObject().toString(),
                        isObjectProperty
                ));
            }

            if (triples.isEmpty()) {
                return null;
            }

            GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(this,typeNS,indNS);
            Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(triples);

            if (tempAxioms == null || tempAxioms.isEmpty()) {
                return null;
            }
            return tempAxioms;

        } finally {
            resultModel.close();
        }
    }

    // 直接类型（ClassAssertion）
    public Set<OWLClass> getDirectTypes(OWLNamedIndividual individual) {
        Set<OWLClass> types = new HashSet<>();
        for (OWLClassAssertionAxiom ax : getOntologyService().gettBoxOntology().axioms(AxiomType.CLASS_ASSERTION).collect(Collectors.toSet())) {
            if (ax.getIndividual().equals(individual) && ax.getClassExpression().isOWLClass()) {
                types.add(ax.getClassExpression().asOWLClass());
            }
        }
        return types;
    }

    public String findMostSpecificClass(Set<OWLClass> directTypes, OWLOntology ontology) {
        if (directTypes == null || directTypes.isEmpty()) return null;
        if (directTypes.size() == 1) return directTypes.iterator().next().getIRI().toString();

        OWLDataFactory dataFactory = ontology.getOWLOntologyManager().getOWLDataFactory();

        // 筛选出"不是集合中任何其他类的父类"的类
        List<OWLClass> mostSpecific = directTypes.stream()
                .filter(type -> !hasSubclassInSet(type, directTypes, dataFactory, ontology))
                .collect(Collectors.toList());

        OWLClass result = mostSpecific.isEmpty()
                ? directTypes.iterator().next()
                : mostSpecific.get(0);

        return result.getIRI().toString();
    }

    /**
     * 从类的 SubClassOf Restriction 中读取指定对象属性的 someValuesFrom 填充类
     */
    public Set<String> getPropertyFillerFromClass(OWLClass targetClass, String objectPropertyIri) {
        OWLObjectProperty targetProperty = ontologyService.gettBoxOntology().getOWLOntologyManager().getOWLDataFactory().getOWLObjectProperty(IRI.create(objectPropertyIri));

        Set<String> fillers = new LinkedHashSet<>();

        // 遍历该类所有的 SubClassOf 公理
        for (OWLSubClassOfAxiom subClassAxiom : ontologyService.gettBoxOntology()
                .subClassAxiomsForSubClass(targetClass)
                .collect(Collectors.toList())) {
            OWLClassExpression superExpr = subClassAxiom.getSuperClass();

            // 仅处理 ObjectSomeValuesFrom 类型的限制 (即 owl:someValuesFrom)
            if (superExpr instanceof OWLObjectSomeValuesFrom someValuesRestriction) {
                // 检查限制的属性是否匹配目标属性
                if (someValuesRestriction.getProperty().equals(targetProperty)) {
                    OWLClassExpression filler = someValuesRestriction.getFiller();

                    // 仅提取具名类作为填充值
                    if (filler instanceof OWLClass namedFiller) {
                        fillers.add(namedFiller.getIRI().toString());
                    }
                }
            }
            else if (superExpr instanceof OWLObjectExactCardinality exactCard) {
                if (exactCard.getProperty().equals(targetProperty)
                        && exactCard.getFiller() instanceof OWLClass namedFiller) {
                    fillers.add(namedFiller.getIRI().toString());
                }
            }
            else if (superExpr instanceof OWLObjectHasValue hasValue) {
                if (hasValue.getProperty().equals(targetProperty)
                        && hasValue.getFiller() instanceof OWLNamedIndividual individual) {
                    // ⚠️ 注意：hasValue 的 filler 是个体(Individual)，不是类(Class)
                    fillers.add(individual.getIRI().toString());
                }
            }
        }

        if (fillers.isEmpty()) {
            log.warn("类 {} 上未找到属性 {} 的 someValuesFrom 限制", targetClass, objectPropertyIri);
        } else {
            log.info("✅ 从类 {} 读取到属性 {} 的填充类: {}", targetClass, objectPropertyIri, fillers);
        }

        return fillers;
    }

    private boolean hasSubclassInSet(OWLClass parentClass, Set<OWLClass> candidateSet,
                                     OWLDataFactory dataFactory, OWLOntology ontology) {
        // ⚠️ 注意：getSubClasses 是 OWLReasoner 的方法，请确保你已传入或持有 reasoner 实例
        // 如果 reasoner 作为字段存在于当前类中，直接使用即可；否则需要将其加入方法签名
        NodeSet<OWLClass> subClasses = subClasses = reasonerService.getReasoner().getSubClasses(parentClass, false); // false = 包含间接子类

        // 直接在 OWLClass 对象层面比较，无需再转回 String
        return subClasses.entities().anyMatch(candidateSet::contains);
    }

    private boolean hasSubclassInSet(String parentIri, Set<String> candidateSet,
                                     OWLDataFactory dataFactory, OWLOntology ontology) {
        // ✅ 核心转换：String IRI → OWLClass（OWLClass 实现了 OWLClassExpression）
        IRI iri = IRI.create(parentIri);
        OWLClass owlClass = dataFactory.getOWLClass(iri);

        // 调用你已有的方法签名
        // 注意：这里必须传入 OWLReasoner，而非直接用 ontology
        // getSubClasses 是 OWLReasoner 的方法，不是 OWLOntology 的方法
        NodeSet<OWLClass> subClasses = reasonerService.getReasoner().getSubClasses(owlClass, false); // false = 包含间接子类

        return subClasses.entities()
                .map(sc -> sc.getIRI().toString())
                .anyMatch(candidateSet::contains);
    }

}