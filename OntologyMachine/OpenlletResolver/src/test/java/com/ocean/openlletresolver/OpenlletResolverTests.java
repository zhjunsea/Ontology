package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OpenlletResolverTests {

    private BackendService backendService;
    // TBox 入口：仅加载 TBox（components-abox / core-abox 的 import 已注释），
    // ABox 由数据库经 Ontop 虚拟化层提供，见 setUp。
    private static final String ONTOLOGY_PATH = "../OntologyFrameworkExample/ontology/pizza-all.owl";
    // OBDA/数据库配置单一来源在 OntologyFrameworkExample/ontology/database/
    private static final String OBDA_PATH = "../OntologyFrameworkExample/ontology/database/myPizza.obda";
    private static final String OBDA_PROPS_PATH = "../OntologyFrameworkExample/ontology/database/myPizza.properties";

    @BeforeAll
    public void setUp() throws Exception {
        // 1. 显式初始化 OBDAHandler，否则 Holder 静态初始化将 NPE
        OBDAHandler.init(OBDA_PROPS_PATH, OBDA_PATH);

        // 2. 加载 TBox（pizza-all.owl）
        backendService = BackendService.getInstance(ONTOLOGY_PATH);

        // 3. ABox 从数据库（Ontop 虚拟化层）拉取并并入本体，ABox 数据不再来自 OWL 文件
        String componentsAbox = """
                CONSTRUCT { ?s ?p ?o }
                WHERE { ?s ?p ?o . FILTER(STRSTARTS(STR(?s), "http://example.org/pizza/components-abox/")) }
                """;
        String coreAbox = """
                CONSTRUCT { ?s ?p ?o }
                WHERE { ?s ?p ?o . FILTER(STRSTARTS(STR(?s), "http://example.org/pizza/core-abox/")) }
                """;
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();
        OBDAHandler.loadAboxFromOntop(componentsAbox, tbox);
        OBDAHandler.loadAboxFromOntop(coreAbox, tbox);

        // loadAboxFromOntop 内部经独立的 OWLOntologyManager 注入公理，
        // BackendService 原有推理器无法感知该变更；重建推理器使其读取含 ABox 的完整本体。
        OWLReasoner rebuiltReasoner = backendService.getReasonerService().getFactory().createReasoner(tbox);
        rebuiltReasoner.flush();
        backendService.getReasonerService().setReasoner(rebuiltReasoner);
    }

    @AfterAll
    public void tearDown() {
        if (backendService != null) backendService.close();
    }

    // ==================== 类相关查询 ====================

    @Test
    public void testGetIndividuals() {
        Set<OWLNamedIndividual> inds = backendService.getIndividuals("http://example.org/pizza/components/MeatTopping");
        assertNotNull(inds);
        assertFalse(inds.isEmpty(), "MeatTopping 下应存在个体");
        boolean found = inds.stream().anyMatch(i -> i.getIRI().getShortForm().equals("PepperoniInstance"));
        assertTrue(found, "应包含 PepperoniInstance");
    }

    @Test
    public void testGetSuperClasses_String() {
        Set<OWLClass> supers = backendService.getSuperClasses("http://example.org/pizza/core/MargheritaPizza");
        assertNotNull(supers);
        assertFalse(supers.isEmpty());
        boolean found = supers.stream().anyMatch(c -> c.getIRI().getShortForm().equals("NeapolitanPizza"));
        assertTrue(found, "应有父类 NeapolitanPizza");
    }

    @Test
    public void testGetSuperClasses_OWLClass() {
        OWLClass cls = backendService.getClass("http://example.org/pizza/core/MargheritaPizza");
        Set<OWLClass> supers = backendService.getSuperClasses(cls);
        assertNotNull(supers);
        assertFalse(supers.isEmpty());
    }

    @Test
    public void testGetSubClasses() {
        Set<OWLClass> subs = backendService.getSubClasses("http://example.org/pizza/core/ItalianTraditionalPizza");
        assertNotNull(subs);
        assertFalse(subs.isEmpty());
        boolean found = subs.stream().anyMatch(c -> c.getIRI().getShortForm().equals("NeapolitanPizza"));
        assertTrue(found, "应有子类 NeapolitanPizza");
    }

    @Test
    public void testGetAllObjectPropertiesOfClass() {
        OWLClass cls = backendService.getClass("http://example.org/pizza/core/Pizza");
        Set<OWLObjectPropertyExpression> props = backendService.getAllObjectPropertiesOfClass(cls);
        assertNotNull(props);
        assertTrue(props.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasCrust")));
        assertTrue(props.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasSauce")));
    }

    @Test
    public void testGetObjectPropertyOfClass() {
        OWLClass cls = backendService.getClass("http://example.org/pizza/core/Pizza");
        OWLObjectPropertyExpression prop = backendService.getObjectPropertyOfClass(cls, "http://example.org/pizza/core/hasCrust");
        assertNotNull(prop);
        assertEquals("hasCrust", prop.getNamedProperty().getIRI().getShortForm());
    }

    @Test
    public void testGetObjectPropertyDomain() {
        OWLObjectProperty prop = backendService.getObjectProperty("http://example.org/pizza/core/hasCrust");
        Set<OWLClassExpression> domains = backendService.getObjectPropertyDomain(prop);
        assertNotNull(domains);
        assertFalse(domains.isEmpty());
        boolean found = domains.stream().anyMatch(expr -> expr.asOWLClass().getIRI().getShortForm().equals("Pizza"));
        assertTrue(found, "hasCrust 的 domain 应包含 Pizza");
    }

    @Test
    public void testGetObjectPropertyRange() {
        OWLObjectProperty prop = backendService.getObjectProperty("http://example.org/pizza/core/hasCrust");
        Set<OWLClassExpression> ranges = backendService.getObjectPropertyRange(prop);
        assertNotNull(ranges);
        assertFalse(ranges.isEmpty());
        boolean found = ranges.stream().anyMatch(expr -> expr.asOWLClass().getIRI().getShortForm().equals("Crust"));
        assertTrue(found, "hasCrust 的 range 应包含 Crust");
    }

    @Test
    public void testGetObjectPropertyDomains() {
        Set<OWLClass> domains = backendService.getObjectPropertyDomains("http://example.org/pizza/core/hasCrust");
        assertNotNull(domains);
        assertFalse(domains.isEmpty());
        assertTrue(domains.stream().anyMatch(c -> c.getIRI().getShortForm().equals("Pizza")));
    }

    @Test
    public void testGetObjectPropertyRanges() {
        Set<OWLClass> ranges = backendService.getObjectPropertyRanges("http://example.org/pizza/core/hasCrust");
        assertNotNull(ranges);
        assertFalse(ranges.isEmpty());
        assertTrue(ranges.stream().anyMatch(c -> c.getIRI().getShortForm().equals("Crust")));
    }

    @Test
    public void testGetObjectPropertyLimitations() {
        OWLClass cls = backendService.getClass("http://example.org/pizza/core/NeapolitanPizza");
        OWLObjectProperty prop = backendService.getObjectProperty("http://example.org/pizza/core/hasCrust");
        Set<OWLClassExpression> limitations = backendService.getObjectPropertyLimitations(cls, prop);
        assertNotNull(limitations);
        boolean found = limitations.stream()
                .anyMatch(expr -> expr instanceof OWLObjectSomeValuesFrom &&
                        ((OWLObjectSomeValuesFrom) expr).getFiller().asOWLClass().getIRI().getShortForm().equals("NeapolitanCrust"));
        assertTrue(found, "应有限制使用 NeapolitanCrust");
    }

    @Test
    public void testGetInverseProperty() {
        Optional<OWLObjectPropertyExpression> inv = backendService.getInverseProperty("http://example.org/pizza/core/hasCrust");
        assertTrue(inv.isPresent(), "hasCrust 应存在逆属性");
        // 注意：若服务方法实现有误，此处会失败，请修复 BackendService.getInverseProperty()
        assertEquals("http://example.org/pizza/core/isCrustOf",
                inv.get().getNamedProperty().getIRI().toString(),
                "hasCrust 的逆属性应为 isCrustOf");
    }

    @Test
    public void testGetAnnotations() {
        OWLClass cls = backendService.getClass("http://example.org/pizza/core/Pizza");
        Map<OWLAnnotationProperty, Set<OWLLiteral>> annotations = backendService.getAnnotations(cls);
        assertNotNull(annotations);
        OWLAnnotationProperty labelProp = backendService.getOntologyService().gettBoxOntology().getOWLOntologyManager().getOWLDataFactory().getRDFSLabel();
        Set<OWLLiteral> labels = annotations.get(labelProp);
        assertNotNull(labels);
        assertFalse(labels.isEmpty());
    }

    @Test
    public void testGetAnnotationValue() {
        OWLClass cls = backendService.getClass("http://example.org/pizza/core/Pizza");
        Set<OWLLiteral> values = backendService.getAnnotationValue(cls, "http://www.w3.org/2000/01/rdf-schema#label");
        assertNotNull(values);
        assertFalse(values.isEmpty());
        boolean found = values.stream().anyMatch(lit -> lit.getLiteral().contains("披萨"));
        assertTrue(found, "应包含中文标签");
    }

    // ==================== 个体相关查询 ====================

    @Test
    public void testGetIndividual() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/components-abox/PepperoniInstance");
        assertNotNull(ind);
        assertEquals("PepperoniInstance", ind.getIRI().getShortForm());
    }

    @Test
    public void testGetIndividual_NotFound() {
        assertThrows(IllegalArgumentException.class, () ->
                backendService.getIndividual("http://example.org/pizza/components-abox/NotExist")
        );
    }

    @Test
    public void testGetIndividualDirectTypes() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/components-abox/PepperoniInstance");
        Set<OWLClass> types = backendService.getIndividualDirectTypes(ind);
        assertNotNull(types);
        assertTrue(types.stream().anyMatch(c -> c.getIRI().getShortForm().equals("MeatTopping")));
    }

    @Test
    public void testGetIndividualAllTypes() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/components-abox/PepperoniInstance");
        Set<OWLClass> allTypes = backendService.getIndividualAllTypes(ind);
        //测试
        backendService.printOWLClassSet(allTypes);
        assertNotNull(allTypes);
        assertTrue(allTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("MeatTopping")));
        assertTrue(allTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("Topping")));
    }

    @Test
    public void testGetObjectPropertiesOfIndividual() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/core-abox/那不勒斯蘑菇披萨");
        Set<OWLObjectPropertyExpression> props = backendService.getObjectPropertiesOfIndividual(ind);
        assertNotNull(props);
        assertTrue(props.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasCrust")));
    }

    @Test
    public void testGetObjectPropertyDirectValueOfIndividual() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/core-abox/那不勒斯蘑菇披萨");
        OWLObjectProperty prop = backendService.getObjectProperty("http://example.org/pizza/core/hasCrust");
        Set<OWLNamedIndividual> values = backendService.getObjectPropertyDirectValueOfIndividual(ind, prop);
        assertNotNull(values);
        assertTrue(values.stream().anyMatch(i -> i.getIRI().getShortForm().equals("NeapolitanCrustInstance")));
    }

    @Test
    public void testGetObjectPropertyAllValueOfIndividual() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/core-abox/那不勒斯蘑菇披萨");
        OWLObjectProperty prop = backendService.getObjectProperty("http://example.org/pizza/core/hasCrust");
        Set<OWLNamedIndividual> values = backendService.getObjectPropertyAllValueOfIndividual(ind, prop);
        assertNotNull(values);
        assertTrue(values.stream().anyMatch(i -> i.getIRI().getShortForm().equals("NeapolitanCrustInstance")));
    }

    @Test
    public void testGetDirectDataPropertiesOfIndividual() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/components-abox/PepperoniInstance");
        Set<OWLDataProperty> props = backendService.getDirectDataPropertiesOfIndividual(ind);
        assertNotNull(props);
        assertTrue(props.stream().anyMatch(p -> p.getIRI().getShortForm().equals("price")));
        assertTrue(props.stream().anyMatch(p -> p.getIRI().getShortForm().equals("status")));
    }

    @Test
    public void testGetAllAllowedDataPropertiesOfIndividual() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/components-abox/PepperoniInstance");
        Set<OWLDataProperty> props = backendService.getAllAllowedDataPropertiesOfIndividual(ind);
        assertNotNull(props);
        assertTrue(props.stream().anyMatch(p -> p.getIRI().getShortForm().equals("price")));
        assertTrue(props.stream().anyMatch(p -> p.getIRI().getShortForm().equals("supplier")));
    }

    @Test
    public void testGetDataPropertyValueOfIndividual_WithProperty() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/components-abox/PepperoniInstance");
        OWLDataProperty prop = backendService.getDataProperty("http://example.org/pizza/components/price");
        Set<OWLLiteral> values = backendService.getDataPropertyValueOfIndividual(ind, prop);
        assertNotNull(values);
        assertFalse(values.isEmpty());
        assertTrue(values.stream().anyMatch(lit -> lit.getLiteral().equals("5.0")));
    }

    @Test
    public void testGetDataPropertyValueOfIndividual_WithIRI() {
        OWLNamedIndividual ind = backendService.getIndividual("http://example.org/pizza/components-abox/PepperoniInstance");
        Set<OWLLiteral> values = backendService.getDataPropertyValueOfIndividual(ind, "http://example.org/pizza/components/price");
        assertNotNull(values);
        assertFalse(values.isEmpty());
        assertTrue(values.stream().anyMatch(lit -> lit.getLiteral().equals("5.0")));
    }

    @Test
    public void testGetDataPropertyDomains() {
        Set<OWLClass> domains = backendService.getDataPropertyDomains("http://example.org/pizza/components/price");
        assertNotNull(domains);
        assertTrue(domains.stream().anyMatch(c -> c.getIRI().getShortForm().equals("PizzaComponent")));
    }

    @Test
    public void testGetDataPropertyRanges() {
        Set<OWLDatatype> ranges = backendService.getDataPropertyRanges("http://example.org/pizza/components/price");
        assertNotNull(ranges);
        // 使用 equals 直接比较 IRI 对象，更可靠
        IRI expectedIRI = IRI.create("http://www.w3.org/2001/XMLSchema#decimal");
        assertTrue(ranges.stream().anyMatch(dt -> dt.getIRI().equals(expectedIRI)));
    }

    @Test
    public void testIsInstanceOf() {
        String individualIRI = "http://example.org/pizza/components-abox/PepperoniInstance";
        String classIRI = "http://example.org/pizza/components/MeatTopping";

        OWLNamedIndividual ind = backendService.getIndividual(individualIRI);
        OWLClass cls = backendService.getClass(classIRI);
        assertNotNull(ind);
        assertNotNull(cls);

        Set<OWLClass> directTypes = backendService.getIndividualDirectTypes(ind);
        //IRI iri = backendService.resolveIRI(cls.getIRI().getIRIString());
        IRI iri = cls.getIRI();
        System.out.println("Pepperoni 的直接类型: " + iri);

        // 使用 toString() 获取完整 IRI 字符串，避免前缀影响
        boolean hasMeatTopping = iri.getIRIString().equals(classIRI);
        assertTrue(hasMeatTopping,
                "Pepperoni 的直接类型中应包含 MeatTopping，实际类型 IRI 为: " +
                        directTypes.stream().map(c -> c.getIRI().toString()).collect(Collectors.toList()));

        boolean result = backendService.isInstanceOf(individualIRI, classIRI);
        assertTrue(result, "isInstanceOf 应返回 true，实际返回 " + result);

        assertFalse(backendService.isInstanceOf(individualIRI,
                        "http://example.org/pizza/components/Cheese"),
                "Pepperoni 不应是 Cheese 的实例");
    }

    // ==================== 其他查询 ====================

    @Test
    public void testGetClass() {
        OWLClass cls = backendService.getClass("http://example.org/pizza/core/Pizza");
        assertNotNull(cls);
        assertEquals("Pizza", cls.getIRI().getShortForm());
    }

    @Test
    public void testGetClass_NotFound() {
        assertThrows(IllegalArgumentException.class, () ->
                backendService.getClass("http://example.org/pizza/core/NotExist")
        );
    }

    @Test
    public void testGetDatatype() {
        Optional<OWLDatatype> dt = backendService.getDatatype("http://www.w3.org/2001/XMLSchema#string");
        assertTrue(dt.isPresent());
        assertEquals("string", dt.get().getIRI().getShortForm());
    }

    @Test
    public void testGetEntityType() {
        IRI iri = IRI.create("http://example.org/pizza/core/Pizza");
        String type = backendService.getEntityType(iri);
        assertEquals("Class", type);

        iri = IRI.create("http://example.org/pizza/components-abox/PepperoniInstance");
        type = backendService.getEntityType(iri);
        assertEquals("Individual", type);

        iri = IRI.create("http://example.org/pizza/core/hasCrust");
        type = backendService.getEntityType(iri);
        assertEquals("ObjectProperty", type);

        iri = IRI.create("http://example.org/pizza/components/price");
        type = backendService.getEntityType(iri);
        assertEquals("DataProperty", type);

        // term:Pizza 是 SKOS 概念，在 OWL 中建模为个体
        iri = IRI.create("http://example.org/pizza/term/Pizza");
        type = backendService.getEntityType(iri);
        assertEquals("Individual", type);

        // 测试注释属性
        iri = IRI.create("http://www.w3.org/2000/01/rdf-schema#label");
        type = backendService.getEntityType(iri);
        assertEquals("AnnotationProperty", type);

        // 测试数据类型
        iri = IRI.create("http://www.w3.org/2001/XMLSchema#string");
        type = backendService.getEntityType(iri);
        assertEquals("Datatype", type);
    }

    @Test
    public void testGetLabel() {
        IRI iri = IRI.create("http://example.org/pizza/core/Pizza");
        String label = backendService.getLabel(backendService.getOntologyService().gettBoxOntology(), iri, "zh");
        assertNotNull(label);
        assertTrue(label.contains("披萨") || label.contains("Pizza"));
    }

    @Test
    public void testConsistency() {
        assertTrue(backendService.getReasonerService().getReasoner().isConsistent(), "本体应一致");
    }

    @Test
    public void testComprehensiveScenario() {
        // ==================== 输入准备 ====================
        String pizzaIRI = "http://example.org/pizza/core-abox/那不勒斯蘑菇披萨";
        String classIRI = "http://example.org/pizza/core/NeapolitanPizza";
        String crustIRI = "http://example.org/pizza/components-abox/NeapolitanCrustInstance";
        String meatToppingIRI = "http://example.org/pizza/components/MeatTopping";
        String pricePropIRI = "http://example.org/pizza/components/price";
        String hasCrustIRI = "http://example.org/pizza/core/hasCrust";
        String processStepIRI = "http://example.org/pizza/processes/hasProcessStep";
        String labelIRI = "http://www.w3.org/2000/01/rdf-schema#label";
        String commentIRI = "http://www.w3.org/2000/01/rdf-schema#comment";
        String decimalIRI = "http://www.w3.org/2001/XMLSchema#decimal";

        OWLNamedIndividual pizzaInd = backendService.getIndividual(pizzaIRI);
        OWLClass neapolitanClass = backendService.getClass(classIRI);
        OWLNamedIndividual crustInd = backendService.getIndividual(crustIRI);

        // ==================== 1. 类查询 ====================
        assertNotNull(neapolitanClass);
        Set<OWLNamedIndividual> meatInds = backendService.getIndividuals(meatToppingIRI);
        assertFalse(meatInds.isEmpty());
        assertTrue(meatInds.stream().anyMatch(i -> i.getIRI().getShortForm().equals("PepperoniInstance")));

        Set<OWLClass> supers = backendService.getSuperClasses(neapolitanClass);
        assertTrue(supers.stream().anyMatch(c -> c.getIRI().getShortForm().equals("ItalianTraditionalPizza")));

        Set<OWLClass> subs = backendService.getSubClasses("http://example.org/pizza/core/ItalianTraditionalPizza");
        assertTrue(subs.stream().anyMatch(c -> c.getIRI().getShortForm().equals("NeapolitanPizza")));

        // ==================== 2. 对象属性查询 ====================
        Set<OWLObjectPropertyExpression> allProps = backendService.getAllObjectPropertiesOfClass(neapolitanClass);
        assertTrue(allProps.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasCrust")));

        OWLObjectPropertyExpression hasCrustProp = backendService.getObjectPropertyOfClass(neapolitanClass, hasCrustIRI);
        assertNotNull(hasCrustProp);

        Set<OWLClassExpression> domains = backendService.getObjectPropertyDomain(hasCrustProp);
        assertTrue(domains.stream().anyMatch(d -> d.asOWLClass().getIRI().getShortForm().equals("Pizza")));

        Set<OWLClassExpression> ranges = backendService.getObjectPropertyRange(hasCrustProp);
        assertTrue(ranges.stream().anyMatch(r -> r.asOWLClass().getIRI().getShortForm().equals("Crust")));

        Set<OWLClassExpression> limitations = backendService.getObjectPropertyLimitations(neapolitanClass, hasCrustProp);
        assertTrue(limitations.stream().anyMatch(expr -> {
            if (expr instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom some = (OWLObjectSomeValuesFrom) expr;
                return some.getFiller().asOWLClass().getIRI().getShortForm().equals("NeapolitanCrust");
            }
            return false;
        }));

        Optional<OWLObjectPropertyExpression> invProp = backendService.getInverseProperty(hasCrustIRI);
        assertTrue(invProp.isPresent());
        assertEquals("isCrustOf", invProp.get().getNamedProperty().getIRI().getShortForm());

        // ==================== 3. 个体查询 ====================
        Set<OWLClass> directTypes = backendService.getIndividualDirectTypes(pizzaInd);
        assertTrue(directTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("GenericNeapolitanPizza")));

        Set<OWLClass> allTypes = backendService.getIndividualAllTypes(pizzaInd);
        assertTrue(allTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("NeapolitanPizza")));
        assertTrue(allTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("TomatoBasedPizza")));

        Set<OWLObjectPropertyExpression> indProps = backendService.getObjectPropertiesOfIndividual(pizzaInd);
        assertTrue(indProps.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasCrust")));

        Set<OWLNamedIndividual> directCrustValues = backendService.getObjectPropertyDirectValueOfIndividual(pizzaInd, hasCrustProp);
        assertFalse(directCrustValues.isEmpty());
        assertTrue(directCrustValues.stream().anyMatch(i -> i.getIRI().getShortForm().equals("NeapolitanCrustInstance")));

        Set<OWLNamedIndividual> allCrustValues = backendService.getObjectPropertyAllValueOfIndividual(pizzaInd, hasCrustProp);
        assertFalse(allCrustValues.isEmpty());

        Set<OWLDataProperty> directDataProps = backendService.getDirectDataPropertiesOfIndividual(crustInd);
        assertTrue(directDataProps.stream().anyMatch(p -> p.getIRI().getShortForm().equals("crustThicknessMm")));

        Set<OWLDataProperty> allDataProps = backendService.getAllAllowedDataPropertiesOfIndividual(crustInd);
        assertTrue(allDataProps.stream().anyMatch(p -> p.getIRI().getShortForm().equals("price")));

        Set<OWLLiteral> thicknessValues = backendService.getDataPropertyValueOfIndividual(crustInd,
                "http://example.org/pizza/components/crustThicknessMm");
        assertFalse(thicknessValues.isEmpty());
        assertTrue(thicknessValues.stream().anyMatch(l -> l.getLiteral().equals("5.0")));

        Set<OWLClass> priceDomains = backendService.getDataPropertyDomains(pricePropIRI);
        assertTrue(priceDomains.stream().anyMatch(c -> c.getIRI().getShortForm().equals("PizzaComponent")));

        Set<OWLDatatype> priceRanges = backendService.getDataPropertyRanges(pricePropIRI);
        assertTrue(priceRanges.stream().anyMatch(dt -> dt.getIRI().toString().equals(decimalIRI)));

        // ==================== 4. 其他查询 ====================
        //OWLClass clsByQName = backendService.getClass("comp:MeatTopping");
        //assertNotNull(clsByQName);

        Optional<OWLDatatype> dt = backendService.getDatatype(decimalIRI);
        assertTrue(dt.isPresent());

        assertEquals("Class", backendService.getEntityType(IRI.create("http://example.org/pizza/core/Pizza")));
        assertEquals("Individual", backendService.getEntityType(IRI.create(pizzaIRI)));
        assertEquals("ObjectProperty", backendService.getEntityType(IRI.create(hasCrustIRI)));
        assertEquals("DataProperty", backendService.getEntityType(IRI.create(pricePropIRI)));

        Map<OWLAnnotationProperty, Set<OWLLiteral>> annoMap = backendService.getAnnotations(neapolitanClass);
        assertFalse(annoMap.isEmpty());

        Set<OWLLiteral> labels = backendService.getAnnotationValue(neapolitanClass, labelIRI);
        assertFalse(labels.isEmpty());
        assertTrue(labels.stream().anyMatch(l -> l.getLiteral().contains("那不勒斯披萨")));

        // DB 映射 mypizza_complete 未为披萨实例提供 rdfs:comment，故此处应为空
        Set<OWLLiteral> comments = backendService.getAnnotationValue(pizzaInd, commentIRI);
        assertTrue(comments.isEmpty(), "DB 映射未为披萨实例提供 rdfs:comment");

        // DB 映射 component_business 为组件实例提供 rdfs:label（@zh）
        Set<OWLLiteral> indLabels = backendService.getAnnotationValue(crustInd, labelIRI);
        assertFalse(indLabels.isEmpty(), "组件实例应有 rdfs:label 注释");

        // 实例检查：正向使用 isInstanceOf，负向使用 getIndividualAllTypes 检查集合
        assertTrue(backendService.isInstanceOf(pizzaIRI, "http://example.org/pizza/core/GenericNeapolitanPizza"));

        // 负向：检查饼底实例不是 Cheese
        OWLClass cheeseClass = backendService.getClass("http://example.org/pizza/components/Cheese");
        Set<OWLClass> crustAllTypes = backendService.getIndividualAllTypes(crustInd);
        assertFalse(crustAllTypes.contains(cheeseClass), "饼底不应是 Cheese 的实例");

        // 同时检查披萨实例也不是 Cheese
        assertFalse(allTypes.contains(cheeseClass), "披萨不应是 Cheese 的实例");

        // 获取标签
        String label = backendService.getLabel(backendService.getOntologyService().gettBoxOntology(), IRI.create(classIRI), "zh");
        assertNotNull(label);
        assertTrue(label.contains("披萨"));

        System.out.println("综合场景测试全部通过！");
    }

    @Test
    public void testLowStockNeapolitanCrustRule() throws Exception {
        /*
        // 1. 确认规则是否存在（若不存在，后续断言会失败，可提前检查）
        long ruleCount = backendService.gettBoxOntology().axioms(AxiomType.SWRL_RULE).count();
        System.out.println("当前本体中 SWRL 规则数量: " + ruleCount);
        if (ruleCount == 0) {
            fail("本体中未加载任何 SWRL 规则，请检查规则序列化格式是否为标准 SWRL/XML 或已通过转换修复");
        }*/

        // 2. 获取相关类和属性
        OWLClass crustClass = backendService.getClass("http://example.org/pizza/components/NeapolitanCrust");
        OWLClass lowStockCrustClass = backendService.getClass("http://example.org/pizza/components/LowStockCrust");
        OWLDataProperty stockQty = backendService.getDataProperty("http://example.org/pizza/components/stockQuantity");

        // 3. 选取一个饼底个体（若存在则复用，否则创建一个并添加到本体）
        OWLNamedIndividual crustInd;
        Set<OWLNamedIndividual> existingCrusts = backendService.getIndividuals(crustClass.getIRI().toString());
        OWLOntologyManager manager = backendService.getOntologyService().gettBoxOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        if (!existingCrusts.isEmpty()) {
            // 使用第一个找到的饼底个体
            crustInd = existingCrusts.iterator().next();
            System.out.println("使用现有个体: " + crustInd.getIRI().getShortForm());
        } else {
            // 没有找到，则创建一个临时饼底个体并添加到本体
            crustInd = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components-abox/testCrustForRule"));
            manager.addAxiom(backendService.getOntologyService().gettBoxOntology(), df.getOWLClassAssertionAxiom(crustClass, crustInd));
            System.out.println("创建新的饼底个体: testCrustForRule");
        }

        // 4. 显式将库存设为 30（高于阈值 20），消除对 DB 个体初始库存的依赖
        backendService.getDataPropertyAssertions(crustInd, stockQty)
                .forEach(ax -> manager.removeAxiom(backendService.getOntologyService().gettBoxOntology(), ax));
        backendService.addIndividualAxiom(crustInd, stockQty, df.getOWLLiteral(30));
        backendService.getReasonerService().getReasoner().flush();

        // 检查：库存充足时不应属于 LowStockCrust
        Set<OWLClass> typesBefore = backendService.getIndividualAllTypes(crustInd);
        //测试
        backendService.printOWLClassSet(typesBefore);
        assertFalse(typesBefore.contains(lowStockCrustClass),             //测试crustInd的类里面有没有LowStockCrust，没有表示确实不在
                "库存充足（30）时，饼底不应被归类为 LowStockCrust");

        // 5. 将库存改为 15（低于阈值），重新推理，应被归类
        OWLLiteral lowStock = df.getOWLLiteral(15);
        // 移除旧公理，添加新公理
        Set<OWLDataPropertyAssertionAxiom> oldAxioms = backendService.getDataPropertyAssertions(crustInd,stockQty);
        backendService.removeAxiomSet(oldAxioms);
        backendService.addIndividualAxiom(crustInd,stockQty,lowStock);


        System.out.println("crustInd 的类型: " + backendService.getReasonerService().getReasoner().getTypes(crustInd, false));
        System.out.println("stockQty IRI: " + stockQty.getIRI());
        Set<OWLLiteral> values = backendService.getReasonerService().getReasoner().getDataPropertyValues(crustInd, stockQty);
        System.out.println("库存值: " + values);
        Set<OWLClass> typesAfter = backendService.getIndividualAllTypes(crustInd);
        System.out.println("=== crustInd 的所有类型（包括推理出的） ===");
        for (OWLClass cls : typesAfter) {
            System.out.println("  " + cls.getIRI() + "  （短名: " + cls.getIRI().getShortForm() + "）");
        }


        /*
        OWLClass neapolitanCrust = df.getOWLClass(IRI.create("http://example.org/pizza/components/NeapolitanCrust"));
        OWLClass crust = df.getOWLClass(IRI.create("http://example.org/pizza/components/Crust"));
        // 打印所有涉及这两个类的 SubClassOf 公理
        backendService.gettBoxOntology().getAxioms(AxiomType.SUBCLASS_OF).forEach(axiom -> {
            if (axiom.getSubClass().equals(neapolitanCrust) || axiom.getSuperClass().equals(crust)) {
                System.out.println("子类公理: " + axiom);
            }
        });
        System.out.println("topCrustClass IRI: " + topCrustClass.getIRI());
        backendService.gettBoxOntology().getAxioms(AxiomType.SUBCLASS_OF).stream()
                .filter(ax -> ax.getSuperClass().isOWLClass())   // 只保留父类是命名类的公理
                .filter(ax -> ax.getSuperClass().asOWLClass().getIRI().toString().contains("Crust"))
                .findFirst()
                .ifPresent(ax -> System.out.println("父类 IRI: " + ax.getSuperClass().asOWLClass().getIRI()));

        OWLReasoner reasoner = backendService.getReasoner();  // 需要 BackendService 暴露 getReasoner()
        boolean isLowStock = reasoner.getInstances(lowStockCrustClass, false)
                .entities()
                .anyMatch(i -> i.equals(crustInd));
        assertTrue(isLowStock, "库存低于20（15）时，SWRL 规则应推断该饼底为 LowStockCrust");
        */
        //Set<OWLClass> typesAfter = backendService.getIndividualAllTypes(crustInd);
        //测试
        backendService.printOWLClassSet(typesAfter);
        assertTrue(typesAfter.contains(lowStockCrustClass),
                "库存低于20（15）时，SWRL 规则应推断该饼底为 LowStockCrust。当前类型: " + typesAfter);

    }

    @Test
    public void testLowStockCrustRule() throws Exception {
        // 获取相关类和属性
        OWLClass crustClass = backendService.getClass("http://example.org/pizza/components/Crust");
        OWLClass lowStockCrustClass = backendService.getClass("http://example.org/pizza/components/LowStockCrust");
        OWLDataProperty stockQty = backendService.getDataProperty("http://example.org/pizza/components/stockQuantity");

        // 选取一个饼底个体（若无则创建）
        OWLNamedIndividual crustInd;
        Set<OWLNamedIndividual> existingCrusts = backendService.getIndividuals(crustClass.getIRI().toString());
        OWLOntologyManager manager = backendService.getOntologyService().gettBoxOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        if (!existingCrusts.isEmpty()) {
            crustInd = existingCrusts.iterator().next();
            System.out.println("使用现有个体: " + crustInd.getIRI().getShortForm());
        } else {
            crustInd = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components-abox/testCrustForRule"));
            manager.addAxiom(backendService.getOntologyService().gettBoxOntology(), df.getOWLClassAssertionAxiom(crustClass, crustInd));
            System.out.println("创建新的饼底个体: testCrustForRule");
        }

        // 设置库存高于阈值（30 > 20）
        backendService.getDataPropertyAssertions(crustInd, stockQty)
                .forEach(ax -> manager.removeAxiom(backendService.getOntologyService().gettBoxOntology(), ax));
        OWLLiteral highStock = df.getOWLLiteral(30);
        backendService.addIndividualAxiom(crustInd, stockQty, highStock);
        backendService.getReasonerService().getReasoner().flush();

        Set<OWLClass> typesBefore = backendService.getIndividualAllTypes(crustInd);
        assertFalse(typesBefore.contains(lowStockCrustClass),
                "库存充足（30）时，饼底不应被归类为 LowStockCrust");
        //测试
        backendService.printOWLClassSet(typesBefore);

        // 修改库存为 15（低于阈值20）
        OWLLiteral lowStock = df.getOWLLiteral(15);
        backendService.getDataPropertyAssertions(crustInd, stockQty)
                .forEach(ax -> manager.removeAxiom(backendService.getOntologyService().gettBoxOntology(), ax));
        backendService.addIndividualAxiom(crustInd, stockQty, lowStock);
        backendService.getReasonerService().getReasoner().flush();

        Set<OWLClass> typesAfter = backendService.getIndividualAllTypes(crustInd);
        //测试
        backendService.printOWLClassSet(typesAfter);

        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        boolean isLowStock = reasoner.getInstances(lowStockCrustClass, false)
                .entities()
                .anyMatch(i -> i.equals(crustInd));
        assertTrue(isLowStock, "库存低于20（15）时，SWRL 规则应推断该饼底为 LowStockCrust");
    }

    @Test
    public void testLowStockSauceRule() throws Exception {
        // 获取相关类和属性
        OWLClass sauceClass = backendService.getClass("http://example.org/pizza/components/Sauce");
        OWLClass lowStockSauceClass = backendService.getClass("http://example.org/pizza/components/LowStockSauce");
        OWLDataProperty stockQty = backendService.getDataProperty("http://example.org/pizza/components/stockQuantity");

        // 选取一个酱汁个体（若无则创建）
        OWLNamedIndividual sauceInd;
        Set<OWLNamedIndividual> existingSauces = backendService.getIndividuals(sauceClass.getIRI().toString());
        OWLOntologyManager manager = backendService.getOntologyService().gettBoxOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        if (!existingSauces.isEmpty()) {
            sauceInd = existingSauces.iterator().next();
            System.out.println("使用现有个体: " + sauceInd.getIRI().getShortForm());
        } else {
            sauceInd = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components-abox/testSauceForRule"));
            manager.addAxiom(backendService.getOntologyService().gettBoxOntology(), df.getOWLClassAssertionAxiom(sauceClass, sauceInd));
            System.out.println("创建新的酱汁个体: testSauceForRule");
        }

        // 获取当前库存值（若有），设为高于阈值（20 > 15）
        Set<OWLLiteral> initialStock = backendService.getDataPropertyValueOfIndividual(sauceInd, stockQty);
        int qty = initialStock.stream().findFirst()
                .map(backendService::parseNumeric)
                .map(Number::intValue)
                .orElse(20); // 默认给一个高于阈值15的值

        // 确保库存高于阈值，并清除旧断言
        backendService.getDataPropertyAssertions(sauceInd, stockQty)
                .forEach(ax -> manager.removeAxiom(backendService.getOntologyService().gettBoxOntology(), ax));
        OWLLiteral highStock = df.getOWLLiteral(20);
        backendService.addIndividualAxiom(sauceInd, stockQty, highStock);
        backendService.getReasonerService().getReasoner().flush();

        // 当前库存20（>=15），不应属于 LowStockSauce
        Set<OWLClass> typesBefore = backendService.getIndividualAllTypes(sauceInd);
        assertFalse(typesBefore.contains(lowStockSauceClass),
                "库存充足（20）时，酱汁不应被归类为 LowStockSauce");

        // 修改库存为 10（低于阈值15）
        OWLLiteral lowStock = df.getOWLLiteral(10);
        backendService.getDataPropertyAssertions(sauceInd, stockQty)
                .forEach(ax -> manager.removeAxiom(backendService.getOntologyService().gettBoxOntology(), ax));
        backendService.addIndividualAxiom(sauceInd, stockQty, lowStock);
        backendService.getReasonerService().getReasoner().flush();

        // 检查推理结果
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        boolean isLowStock = reasoner.getInstances(lowStockSauceClass, false)
                .entities()
                .anyMatch(i -> i.equals(sauceInd));
        assertTrue(isLowStock, "库存低于15（10）时，SWRL 规则应推断该酱汁为 LowStockSauce");
    }

    @Test
    public void testLowStockCheeseRule() throws Exception {
        // 获取相关类和属性
        OWLClass cheeseClass = backendService.getClass("http://example.org/pizza/components/Cheese");
        OWLClass lowStockCheeseClass = backendService.getClass("http://example.org/pizza/components/LowStockCheese");
        OWLDataProperty stockQty = backendService.getDataProperty("http://example.org/pizza/components/stockQuantity");

        // 选取一个奶酪个体（若无则创建）
        OWLNamedIndividual cheeseInd;
        Set<OWLNamedIndividual> existingCheeses = backendService.getIndividuals(cheeseClass.getIRI().toString());
        OWLOntologyManager manager = backendService.getOntologyService().gettBoxOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        if (!existingCheeses.isEmpty()) {
            cheeseInd = existingCheeses.iterator().next();
            System.out.println("使用现有个体: " + cheeseInd.getIRI().getShortForm());
        } else {
            cheeseInd = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components-abox/testCheeseForRule"));
            manager.addAxiom(backendService.getOntologyService().gettBoxOntology(), df.getOWLClassAssertionAxiom(cheeseClass, cheeseInd));
            System.out.println("创建新的奶酪个体: testCheeseForRule");
        }

        // 设置库存高于阈值（20 > 10）
        backendService.getDataPropertyAssertions(cheeseInd, stockQty)
                .forEach(ax -> manager.removeAxiom(backendService.getOntologyService().gettBoxOntology(), ax));
        OWLLiteral highStock = df.getOWLLiteral(20);
        backendService.addIndividualAxiom(cheeseInd, stockQty, highStock);
        backendService.getReasonerService().getReasoner().flush();

        Set<OWLClass> typesBefore = backendService.getIndividualAllTypes(cheeseInd);
        assertFalse(typesBefore.contains(lowStockCheeseClass),
                "库存充足（20）时，奶酪不应被归类为 LowStockCheese");

        // 修改库存为 5（低于阈值10）
        OWLLiteral lowStock = df.getOWLLiteral(5);
        backendService.getDataPropertyAssertions(cheeseInd, stockQty)
                .forEach(ax -> manager.removeAxiom(backendService.getOntologyService().gettBoxOntology(), ax));
        backendService.addIndividualAxiom(cheeseInd, stockQty, lowStock);
        backendService.getReasonerService().getReasoner().flush();

        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        boolean isLowStock = reasoner.getInstances(lowStockCheeseClass, false)
                .entities()
                .anyMatch(i -> i.equals(cheeseInd));
        assertTrue(isLowStock, "库存低于10（5）时，SWRL 规则应推断该奶酪为 LowStockCheese");
    }

    @Test
    public void testLowStockToppingRule() throws Exception {
        // 获取相关类和属性
        OWLClass toppingClass = backendService.getClass("http://example.org/pizza/components/Topping");
        OWLClass lowStockToppingClass = backendService.getClass("http://example.org/pizza/components/LowStockTopping");
        OWLDataProperty stockQty = backendService.getDataProperty("http://example.org/pizza/components/stockQuantity");

        // 选取一个配料个体（若无则创建）
        OWLNamedIndividual toppingInd;
        Set<OWLNamedIndividual> existingToppings = backendService.getIndividuals(toppingClass.getIRI().toString());
        OWLOntologyManager manager = backendService.getOntologyService().gettBoxOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        if (!existingToppings.isEmpty()) {
            toppingInd = existingToppings.iterator().next();
            System.out.println("使用现有个体: " + toppingInd.getIRI().getShortForm());
        } else {
            toppingInd = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components-abox/testToppingForRule"));
            manager.addAxiom(backendService.getOntologyService().gettBoxOntology(), df.getOWLClassAssertionAxiom(toppingClass, toppingInd));
            System.out.println("创建新的配料个体: testToppingForRule");
        }
        //测试
        backendService.printOWLClassSet(backendService.getIndividualAllTypes(toppingInd));

        // 设置库存高于阈值（20 > 10）
        backendService.getDataPropertyAssertions(toppingInd, stockQty)
                .forEach(ax -> manager.removeAxiom(backendService.getOntologyService().gettBoxOntology(), ax));
        OWLLiteral highStock = df.getOWLLiteral(20);
        backendService.addIndividualAxiom(toppingInd, stockQty, highStock);
        backendService.getReasonerService().getReasoner().flush();

        Set<OWLClass> typesBefore = backendService.getIndividualAllTypes(toppingInd);
        //测试
        backendService.printOWLClassSet(typesBefore);
        assertFalse(typesBefore.contains(lowStockToppingClass),
                "库存充足（20）时，配料不应被归类为 LowStockTopping");

        // 修改库存为 5（低于阈值10）
        OWLLiteral lowStock = df.getOWLLiteral(5);
        backendService.getDataPropertyAssertions(toppingInd, stockQty)
                .forEach(ax -> manager.removeAxiom(backendService.getOntologyService().gettBoxOntology(), ax));
        backendService.addIndividualAxiom(toppingInd, stockQty, lowStock);
        backendService.getReasonerService().getReasoner().flush();

        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        boolean isLowStock = reasoner.getInstances(lowStockToppingClass, false)
                .entities()
                .anyMatch(i -> i.equals(toppingInd));
        assertTrue(isLowStock, "库存低于10（5）时，SWRL 规则应推断该配料为 LowStockTopping");
    }
}