package org.junzhang.ontologymachine;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.search.EntitySearcher;

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

    private ReasonerService(String mainOntologyPath) throws Exception {
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

    public OWLOntology getOntology(){
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

    // ================= 本体加载及本体的合理性验证 =================
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

    public OWLReasoner getReasoner() { return reasoner; }
    public boolean isConsistent() { return reasoner.isConsistent(); }
    // ============================================
    // ================= 类相关查询 =================
    // ============================================
    // 获取classIRI的所有个体
    public Set<OWLNamedIndividual> getIndividuals(String classIRI) {
        OWLClass cls = getClass(classIRI);
        return reasoner.getInstances(cls, true).entities().collect(Collectors.toSet());
    }

   public OWLClass getClass(String classIRI) {
        return ontology.classesInSignature()
                .filter(c -> c.getIRI().getIRIString().equals(classIRI))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("类未找到: " + classIRI));
    }

    public Set<OWLClass> getSuperClasses(String classIRI) {
        OWLClass cls = getClass(classIRI);
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

        // 1. 收集所有超类（包括自身）
        Set<OWLClass> allSuperClasses = new HashSet<>();
        allSuperClasses.add(cls);
        NodeSet<OWLClass> superNodes = reasoner.getSuperClasses(cls, true);
        // 修复1：使用 entities() 替代 flatten()
        Set<OWLClass> superClassesSet = superNodes.entities().collect(Collectors.toSet());
        allSuperClasses.addAll(superClassesSet);

        // 修复2：使用 Stream.forEach 遍历对象属性
        ontology.objectPropertiesInSignature().forEach(prop -> {
            // 修复3：通过公理获取定义域
            Set<OWLClassExpression> domains = new HashSet<>();
            ontology.getObjectPropertyDomainAxioms(prop.asOWLObjectProperty())
                    .forEach(axiom -> domains.add(axiom.getDomain()));

            if (domains.isEmpty()) {
                return; // 无域约束的属性跳过
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
        // 1. 获取该类的所有对象属性（依赖已有的推理逻辑）
        Set<OWLObjectPropertyExpression> allProps = getAllObjectPropertiesOfClass(cls);

        // 2. 遍历查找匹配 IRI 的属性
        for (OWLObjectPropertyExpression prop : allProps) {
            // 获取其底层命名属性的 IRI 字符串
            String currentIRI = prop.getNamedProperty().getIRI().toString();
            if (currentIRI.equals(propIRI)) {
                return prop; // 返回匹配到的属性表达式（可能是逆属性，保留其语义）
            }
        }

        // 3. 未找到返回 null（或可抛异常）
        return null;
    }

    public Set<OWLClassExpression> getObjectPropertyDomain(OWLObjectPropertyExpression prop) {
        Set<OWLClassExpression> domains = new HashSet<>();
        if (prop == null) {
            return domains;
        }
        // 通过遍历定义域公理，提取域表达式（includeImports = true 包括导入的本体）
        ontology.getObjectPropertyDomainAxioms(prop)
                .forEach(axiom -> domains.add(axiom.getDomain()));
        return domains;
    }

    public Set<OWLClassExpression> getObjectPropertyRange(OWLObjectPropertyExpression prop) {
        Set<OWLClassExpression> ranges = new HashSet<>();
        if (prop == null) {
            return ranges;
        }
        // 获取该属性的所有范围公理（includeImports = true 包含导入的本体）
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

    /*返回该类的指定属性的类的限制，嵌套的类的限制也会被取回，不过里面包括嵌套的属性和限制，比如：
    <rdfs:subClassOf>
            <owl:Restriction>
                <owl:onProperty rdf:resource="hasCrust"/>
                <owl:allValuesFrom>
                    <owl:Restriction>
                        <owl:onProperty rdf:resource="crustThicknessMm"/>
                        <owl:allValuesFrom rdf:resource="thinCrustRange"/>
                    </owl:Restriction>
                </owl:allValuesFrom>
            </owl:Restriction>
        </rdfs:subClassOf>
    hasCrust的限制会返回crustThicknessMm和thinCrustRange，而不仅仅是crustThicknessMm */
    public Set<OWLClassExpression> getObjectPropertyLimitations(OWLClass cls, OWLObjectPropertyExpression prop) {
        Set<OWLClassExpression> limitations = new HashSet<>();
        if (cls == null || prop == null) {
            return limitations;
        }

        // 1. 获取所有超类（包括自身）
        NodeSet<OWLClass> superNodes = reasoner.getSuperClasses(cls, true);
        Set<OWLClass> allSuperClasses = superNodes.entities().collect(Collectors.toSet());
        allSuperClasses.add(cls);

        // 2. 遍历当前本体及其所有导入的本体
        Set<OWLOntology> ontologiesToSearch = new HashSet<>();
        ontologiesToSearch.add(ontology);
        ontologiesToSearch.addAll(ontology.getImportsClosure());

        for (OWLOntology ont : ontologiesToSearch) {
            for (OWLClass superCls : allSuperClasses) {
                // 3. 使用正确的方法名，并遍历所有公理
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

    /**
     * 获取指定实体（类或个体）的所有注释属性及其字面量值。
     * @param entity OWLClass 或 OWLNamedIndividual 实例
     * @return Map<注释属性, 字面量值集合>
     */
    public Map<OWLAnnotationProperty, Set<OWLLiteral>> getAnnotations(OWLObject entity) {
        Map<OWLAnnotationProperty, Set<OWLLiteral>> result = new HashMap<>();
        if (entity == null) {
            return result;
        }

        // 提取实体的 IRI（类有个体的 IRI 都可以通过各自的 getIRI() 获取）
        IRI iri = null;
        if (entity instanceof OWLClass) {
            iri = ((OWLClass) entity).getIRI();
        } else if (entity instanceof OWLNamedIndividual) {
            iri = ((OWLNamedIndividual) entity).getIRI();
        } else {
            // 如果不支持的类型，直接返回空
            return result;
        }

        // 遍历所有导入的本体（避免遗漏导入文件中的注释）
        Set<OWLOntology> ontologies = new HashSet<>();
        ontologies.add(ontology);
        ontologies.addAll(ontology.getImportsClosure());

        // 获取以该 IRI 为主语的所有注释断言公理
        Set<OWLAnnotationAssertionAxiom> axioms = ontology.getAnnotationAssertionAxioms(iri);
        for (OWLAnnotationAssertionAxiom axiom : axioms) {
            OWLAnnotationProperty prop = axiom.getProperty();
            OWLAnnotationValue value = axiom.getValue();
            // 只保留字面量值（忽略指向其他 IRI 的注释，如 rdfs:seeAlso）
            if (value instanceof OWLLiteral) {
                result.computeIfAbsent(prop, k -> new HashSet<>())
                        .add((OWLLiteral) value);
            }
        }

        return result;
    }

    /**
     * 获取指定实体（类或个体）上指定注释属性的所有字面量值。
     * 本实现复用 getAnnotations 函数，避免重复遍历本体。
     * @param entity OWLEntity（可以是 OWLClass 或 OWLNamedIndividual）
     * @param annotationPropertyIRI 注释属性的完整 IRI（字符串）
     * @return 所有字面量值的集合（若不存在则返回空集合）
     */
    public Set<OWLLiteral> getAnnotationValue(OWLEntity entity, String annotationPropertyIRI) {
        if (entity == null || annotationPropertyIRI == null) {
            return new HashSet<>();
        }
        // 1. 获取该实体的所有注释（Map<属性, Set<字面量>>）
        Map<OWLAnnotationProperty, Set<OWLLiteral>> allAnnotations = getAnnotations(entity);
        // 2. 构造目标注释属性的对象
        OWLAnnotationProperty targetProp = ontology.getOWLOntologyManager().getOWLDataFactory().getOWLAnnotationProperty(IRI.create(annotationPropertyIRI));
        // 3. 返回对应的值，若没有则返回空集合
        return allAnnotations.getOrDefault(targetProp, new HashSet<>());
    }


    // ============================================
    // =============== 个体相关查询 =================
    // =============== 注释查询同类 =================
    // ============================================
    public OWLNamedIndividual getIndividual(String individualIRI) {
        return ontology.individualsInSignature()
                .filter(i -> i.getIRI().getIRIString().equals(individualIRI))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("个体未找到: " + individualIRI));
    }
    //获取个体所属直接类
    public Set<OWLClass> getIndividualDirctTypes(OWLNamedIndividual ind) {
        return reasoner.getTypes(ind, true).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }
    //获取个体所属直接类及父类
    public Set<OWLClass> getIndividualAllTypes(OWLNamedIndividual ind) {
        return reasoner.getTypes(ind, false).entities()
                .filter(c -> !c.isOWLThing())
                .collect(Collectors.toSet());
    }

    //返回个体的所有的对象属性
    public Set<OWLObjectPropertyExpression> getObjectPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLObjectPropertyExpression> properties = new HashSet<>();
        if (ind == null) {
            return properties;
        }
        Set<OWLObjectPropertyAssertionAxiom> axioms = ontology.getObjectPropertyAssertionAxioms(ind);
        for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
            // 提取属性表达式（可能是命名属性或逆属性）
            properties.add(axiom.getProperty());
        }
        return properties;
    }

    /**
     * 获取个体通过指定对象属性直接指向的宾语个体集合（仅显式断言，不推理）。
     * @param ind  主语个体
     * @param prop 对象属性表达式（可以是命名属性或逆属性）
     * @return 宾语个体的集合（若没有断言则返回空集合）
     */
    public Set<OWLNamedIndividual> getObjectPropertyDirectValueOfIndividual(
            OWLNamedIndividual ind,
            OWLObjectPropertyExpression prop) {

        Set<OWLNamedIndividual> result = new HashSet<>();
        if (ind == null || prop == null) {
            return result;
        }

        // 如果本体已合并，可只查 ontology；否则遍历导入闭包
        Set<OWLOntology> ontologiesToSearch = new HashSet<>();
        ontologiesToSearch.add(ontology);
        ontologiesToSearch.addAll(ontology.getImportsClosure());

        for (OWLOntology ont : ontologiesToSearch) {
            Set<OWLObjectPropertyAssertionAxiom> axioms = ont.getObjectPropertyAssertionAxioms(ind);
            for (OWLObjectPropertyAssertionAxiom axiom : axioms) {
                if (axiom.getProperty().equals(prop)) {
                    OWLIndividual object = axiom.getObject();
                    // 只保留有名个体（匿名个体通常不作为宾语显式写出）
                    if (object instanceof OWLNamedIndividual) {
                        result.add((OWLNamedIndividual) object);
                    }
                }
            }
        }

        return result;
    }
    /**
     * 使用推理机获取个体通过指定对象属性指向的所有宾语个体（包括推理得出的）。
     * @param ind  主语个体
     * @param prop 对象属性表达式
     * @return 所有宾语个体的集合（可能包含命名个体和匿名个体）
     */
    public Set<OWLNamedIndividual> getObjectPropertyAllValueOfIndividual(
            OWLNamedIndividual ind,
            OWLObjectPropertyExpression prop) {

        if (ind == null || prop == null || reasoner == null) {
            return new HashSet<>();
        }

        // 使用推理机获取所有宾语（包括通过逆属性、属性链等推导出的）
        NodeSet<OWLNamedIndividual> values = reasoner.getObjectPropertyValues(ind, prop);
        return values.entities().collect(Collectors.toSet());
    }

    /**
     * 获取个体直接断言的所有数据属性（不包含推理）。
     * @param ind 命名个体
     * @return 该个体所拥有的数据属性集合（若没有则返回空集合）
     */
    public Set<OWLDataProperty> getDirectDataPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLDataProperty> properties = new HashSet<>();
        if (ind == null) {
            return properties;
        }

        // 获取所有以 ind 为主语的数据属性断言
        Set<OWLDataPropertyAssertionAxiom> axioms = ontology.getDataPropertyAssertionAxioms(ind);
        for (OWLDataPropertyAssertionAxiom axiom : axioms) {
            // 提取属性表达式并转换为命名数据属性
            properties.add(axiom.getProperty().asOWLDataProperty());
        }
        return properties;
    }

    /**
     * 获取个体所拥有的所有数据属性（包括推理得出的）。
     * 逻辑：获取个体所有类型（含父类），汇总这些类的数据属性，再加上个体显式断言的数据属性。
     * @param ind 命名个体
     * @return 数据属性集合
     */
    public Set<OWLDataProperty> getAllAllowedDataPropertiesOfIndividual(OWLNamedIndividual ind) {
        Set<OWLDataProperty> allowedProperties = new HashSet<>();
        if (ind == null || reasoner == null || ontology == null) {
            return allowedProperties;
        }

        // 1. 使用推理机获取该个体的所有类型（包括直接和间接父类）
        Set<OWLClass> allClasses = this.getIndividualAllTypes(ind);
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();

        // 2. 对每个类，获取其定义的所有数据属性（基于域约束）
        for (OWLClass cls : allClasses) {
            // 3. 遍历本体中所有的数据属性，检查其 Domain 是否包含该类
            Set<OWLDataProperty> allDataProps = ontology.dataPropertiesInSignature()
                    .map(prop -> prop.asOWLDataProperty()) // 数据属性无逆属性，直接转换
                    .collect(Collectors.toSet());

            for (OWLDataProperty prop : allDataProps) {
                // 4. 获取该属性的所有定义域公理
                Set<OWLClass> domains = getDataPropertyDomains(prop);

                // 如果属性没有定义域，通常视为通用属性（任何类都可使用），根据业务决定是否跳过
                if (domains.isEmpty()) {
                    // 可根据需求决定是否添加（通常默认全局属性也算“允许”）
                    allowedProperties.add(prop);
                    continue;
                }

                // 5. 判断 cls 是否满足该属性的某个 Domain 约束
                for (OWLClassExpression domain : domains) {
                    // 利用推理机判断：cls 是否是 domain 的子类
                    if (reasoner.isEntailed(df.getOWLSubClassOfAxiom(cls, domain))) {
                        allowedProperties.add(prop);
                        break; // 只要满足一个 Domain 即可
                    }
                }
            }
        }

        return allowedProperties;
    }

    /**
     * 获取个体通过指定数据属性所拥有的所有字面量值（使用推理机）。
     * @param ind      命名个体
     * @param dataProp 数据属性
     * @return 所有字面量值的集合（若无值则返回空集合）
     */
    public Set<OWLLiteral> getDataPropertyValueOfIndividual(OWLNamedIndividual ind, OWLDataProperty dataProp) {
        if (ind == null || dataProp == null || reasoner == null) {
            return new HashSet<>();
        }
        // 正确：使用 NodeSet 类型接收返回值
        Set<OWLLiteral> dataValues = reasoner.getDataPropertyValues(ind, dataProp);
        return  dataValues;
    }
    public Set<OWLLiteral> getDataPropertyValueOfIndividual(OWLNamedIndividual ind, String dataPropIRI) {
        OWLDataProperty dataProp = getDataProperty(dataPropIRI);
        return getDataPropertyValueOfIndividual(ind,dataProp);
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

    /** 判断个体是否属于某个类（推理） */
    public boolean isInstanceOf(String individualIRI, String classIRI) {
        OWLNamedIndividual ind = getIndividual(individualIRI);
        OWLClass cls = getClass(classIRI);
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
        return reasoner.isEntailed(df.getOWLClassAssertionAxiom(cls, ind));
    }

    // ================= 辅助方法 =================
    private OWLObjectProperty getObjectProperty(String iri) {
        return ontology.objectPropertiesInSignature()
                .filter(p -> p.getIRI().getIRIString().equals(iri))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("对象属性未找到: " + iri));
    }

    private OWLDataProperty getDataProperty(String iri) {
        return ontology.dataPropertiesInSignature()
                .filter(p -> p.getIRI().getIRIString().equals(iri))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据属性未找到: " + iri));
    }

    // ================= 数据类型查询 =================
    public Optional<OWLDatatype> getDatatype(String datatypeIRI) {
        IRI dtIRI = IRI.create(datatypeIRI);
        return ontology.datatypesInSignature().filter(dt -> dt.getIRI().equals(dtIRI)).findFirst();
    }

    // ================= 实体类型判断 =================
    public String getEntityType(IRI iri) {
        if (ontology.containsClassInSignature(iri)) return "Class";
        if (ontology.containsIndividualInSignature(iri)) return "Individual";
        if (ontology.containsObjectPropertyInSignature(iri)) return "ObjectProperty";
        if (ontology.containsDataPropertyInSignature(iri)) return "DataProperty";
        if (ontology.containsAnnotationPropertyInSignature(iri)) return "AnnotationProperty";
        if (ontology.containsDatatypeInSignature(iri)) return "Datatype";
        return "Unknown";
    }

    /**
     * 从起点实体出发，通过对象属性自动规划路径，到达目标（类或数据属性），并返回值。
     * @param startIRI  起点实体 IRI（可以是类或个体）
     * @param targetIRI 目标实体 IRI（可以是类 IRI 或数据属性 IRI）
     * @param debug     是否打印调试信息
     * @return 路径末端的值（若是数据属性则返回字面量，若是类则返回该类的实例 IRI 或类本身 IRI）
     */
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
            // 如果起点是个体，直接获取数据属性值
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
                // 类没有直接值，需要找该类的实例？但这样会带出多个值。我们可以尝试通过对象属性找到个体，再取值。
                // 这里简单处理：先找该类的所有实例，取第一个，再查数据属性
                OWLClass cls = getClass(startIRI);
                // 使用推理机获取实例
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

        // 4. 如果目标是对象属性（期望得到该属性的值域或实例），或者目标是类（期望得到该类的实例）
        //    我们需要通过对象属性路径导航。
        //    使用 BFS 从起点出发，沿着对象属性走到目标类或属性。
        //    BFS 状态：当前实体 IRI，当前路径（记录经过的属性 IRI）
        Queue<String> queue = new LinkedList<>();
        Map<String, String> parentMap = new HashMap<>(); // 记录每个节点是从哪个节点来的
        Map<String, String> edgeMap = new HashMap<>();   // 记录到达该节点所用到的属性 IRI

        queue.add(startIRI);
        parentMap.put(startIRI, null);
        edgeMap.put(startIRI, null);

        Set<String> visited = new HashSet<>();
        visited.add(startIRI);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            String currentType = getEntityType(IRI.create(current));

            // 如果当前实体就是目标（类或对象属性），则停止
            if (current.equals(targetIRI)) {
                // 重建路径并执行最终查询（如果是对象属性，取其值域或实例）
                List<String> pathIRIs = new ArrayList<>();
                String node = current;
                while (node != null) {
                    pathIRIs.add(0, node);
                    node = parentMap.get(node);
                }
                // 移除第一个（起点），剩下的就是属性链
                if (pathIRIs.size() > 1) {
                    List<String> chain = pathIRIs.subList(1, pathIRIs.size()); // 属性 IRI 列表
                    // 调用 queryIndividualPropertyChain（假设该方法接受 List<String>）
                    return queryIndividualPropertyChain(chain, debug);
                } else {
                    return current; // 就是起点本身
                }
            }

            // 如果当前是类，探索其直接或间接的子类？更合理的做法是：从该类出发，通过对象属性（域或范围）导航。
            if ("Class".equals(currentType)) {
                OWLClass cls = getClass(current);
                // 获取该类上所有允许的对象属性（通过定义域）
                Set<OWLObjectProperty> props = getAllObjectPropertiesOfClass(cls);
                for (OWLObjectProperty prop : props) {
                    // 获取该属性的值域（Range）
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
                    // 如果目标是数据属性，也可以考虑直接检查该属性是否就是目标？但这里目标是类或对象属性
                }
            }

            // 如果当前是个体，可以沿着它的对象属性值导航到另一个个体
            if ("Individual".equals(currentType)) {
                OWLNamedIndividual ind = getIndividual(current);
                // 获取该个体所有的对象属性断言
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

            // 如果当前是对象属性，可以导航到它的范围类（如果目标是类）
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
    // ================= IRI链的查询 =================
    /*public String queryIndividualPropertyChain(String chainJson, boolean debug) {
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
                    // 获取该属性的 IRI（正向命名属性）
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
                    // 取第一个值作为新的当前实体
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
                    // 取第一个范围，并检查是否为原子类
                    OWLClassExpression rangeExpr = ranges.iterator().next();
                    if (rangeExpr.isOWLClass()) {
                        resultIRI = rangeExpr.asOWLClass().getIRI().toString();
                        if (debug) System.out.println("对象属性的范围类: " + resultIRI);
                    } else {
                        // 复杂范围，可尝试处理，这里简化
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
                    // 取第一个字面量，直接返回（链结束）
                    OWLLiteral first = literals.iterator().next();
                    literalValue = first.getLiteral();
                    if (debug) System.out.println("数据属性值: " + literalValue);
                    // 返回字面量值，不继续后续 IRIs（即使还有）
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

            // 如果得到的是 IRI，更新 currentIRI 继续循环
            if (resultIRI != null) {
                currentIRI = resultIRI;
            } else {
                // 如果没有 resultIRI 且不是返回值终止，说明出错了
                if (debug) System.out.println("未获得有效结果");
                return null;
            }
        }

        // 循环结束，返回最终的 currentIRI（如果整个链都处理完毕）
        if (debug) System.out.println("最终结果 IRI: " + currentIRI);
        return currentIRI;
    }*/

    // 辅助解析 JSON 数组（使用 Gson 库）
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
    public void close() { reasoner.dispose(); }
    /*
    // ================= 标签获取 =================
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
    } */
}