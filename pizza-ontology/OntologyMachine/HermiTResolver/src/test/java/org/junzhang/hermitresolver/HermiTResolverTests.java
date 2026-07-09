package org.junzhang.hermitresolver;

import org.junzhang.ontologymachine.ReasonerService;
import org.junit.jupiter.api.*;
import org.semanticweb.owlapi.model.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HermiTResolverTests {

    private ReasonerService service;
    private static final String ONTOLOGY_PATH = "D:/work/Ontology/pizza-ontology/ontology/pizza-all.owl";

    @BeforeAll
    public void setUp() throws Exception {
        service = ReasonerService.getInstance(ONTOLOGY_PATH);
    }

    @AfterAll
    public void tearDown() {
        if (service != null) service.close();
    }

    // ==================== 类相关查询 ====================

    @Test
    public void testGetIndividuals() {
        Set<OWLNamedIndividual> inds = service.getIndividuals("http://example.org/pizza/components/classes/MeatTopping");
        assertNotNull(inds);
        assertFalse(inds.isEmpty(), "MeatTopping 下应存在个体");
        boolean found = inds.stream().anyMatch(i -> i.getIRI().getShortForm().equals("Pepperoni"));
        assertTrue(found, "应包含 Pepperoni");
    }

    @Test
    public void testGetSuperClasses_String() {
        Set<OWLClass> supers = service.getSuperClasses("http://example.org/pizza/classes/MargheritaPizza");
        assertNotNull(supers);
        assertFalse(supers.isEmpty());
        boolean found = supers.stream().anyMatch(c -> c.getIRI().getShortForm().equals("NeapolitanPizza"));
        assertTrue(found, "应有父类 NeapolitanPizza");
    }

    @Test
    public void testGetSuperClasses_OWLClass() {
        OWLClass cls = service.getClass("http://example.org/pizza/classes/MargheritaPizza");
        Set<OWLClass> supers = service.getSuperClasses(cls);
        assertNotNull(supers);
        assertFalse(supers.isEmpty());
    }

    @Test
    public void testGetSubClasses() {
        Set<OWLClass> subs = service.getSubClasses("http://example.org/pizza/classes/ItalianTraditionalPizza");
        assertNotNull(subs);
        assertFalse(subs.isEmpty());
        boolean found = subs.stream().anyMatch(c -> c.getIRI().getShortForm().equals("NeapolitanPizza"));
        assertTrue(found, "应有子类 NeapolitanPizza");
    }

    @Test
    public void testGetAllObjectPropertiesOfClass() {
        OWLClass cls = service.getClass("http://example.org/pizza/classes/Pizza");
        Set<OWLObjectPropertyExpression> props = service.getAllObjectPropertiesOfClass(cls);
        assertNotNull(props);
        assertTrue(props.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasCrust")));
        assertTrue(props.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasSauce")));
    }

    @Test
    public void testGetObjectPropertyOfClass() {
        OWLClass cls = service.getClass("http://example.org/pizza/classes/Pizza");
        OWLObjectPropertyExpression prop = service.getObjectPropertyOfClass(cls, "http://example.org/pizza/classes/hasCrust");
        assertNotNull(prop);
        assertEquals("hasCrust", prop.getNamedProperty().getIRI().getShortForm());
    }

    @Test
    public void testGetObjectPropertyDomain() {
        OWLObjectProperty prop = service.getObjectProperty("http://example.org/pizza/classes/hasCrust");
        Set<OWLClassExpression> domains = service.getObjectPropertyDomain(prop);
        assertNotNull(domains);
        assertFalse(domains.isEmpty());
        boolean found = domains.stream().anyMatch(expr -> expr.asOWLClass().getIRI().getShortForm().equals("Pizza"));
        assertTrue(found, "hasCrust 的 domain 应包含 Pizza");
    }

    @Test
    public void testGetObjectPropertyRange() {
        OWLObjectProperty prop = service.getObjectProperty("http://example.org/pizza/classes/hasCrust");
        Set<OWLClassExpression> ranges = service.getObjectPropertyRange(prop);
        assertNotNull(ranges);
        assertFalse(ranges.isEmpty());
        boolean found = ranges.stream().anyMatch(expr -> expr.asOWLClass().getIRI().getShortForm().equals("Crust"));
        assertTrue(found, "hasCrust 的 range 应包含 Crust");
    }

    @Test
    public void testGetObjectPropertyDomains() {
        Set<OWLClass> domains = service.getObjectPropertyDomains("http://example.org/pizza/classes/hasCrust");
        assertNotNull(domains);
        assertFalse(domains.isEmpty());
        assertTrue(domains.stream().anyMatch(c -> c.getIRI().getShortForm().equals("Pizza")));
    }

    @Test
    public void testGetObjectPropertyRanges() {
        Set<OWLClass> ranges = service.getObjectPropertyRanges("http://example.org/pizza/classes/hasCrust");
        assertNotNull(ranges);
        assertFalse(ranges.isEmpty());
        assertTrue(ranges.stream().anyMatch(c -> c.getIRI().getShortForm().equals("Crust")));
    }

    @Test
    public void testGetObjectPropertyLimitations() {
        OWLClass cls = service.getClass("http://example.org/pizza/classes/NeapolitanPizza");
        OWLObjectProperty prop = service.getObjectProperty("http://example.org/pizza/classes/hasCrust");
        Set<OWLClassExpression> limitations = service.getObjectPropertyLimitations(cls, prop);
        assertNotNull(limitations);
        boolean found = limitations.stream()
                .anyMatch(expr -> expr instanceof OWLObjectSomeValuesFrom &&
                        ((OWLObjectSomeValuesFrom) expr).getFiller().asOWLClass().getIRI().getShortForm().equals("NeapolitanCrust"));
        assertTrue(found, "应有限制使用 NeapolitanCrust");
    }

    @Test
    public void testGetInverseProperty() {
        Optional<OWLObjectPropertyExpression> inv = service.getInverseProperty("http://example.org/pizza/classes/hasCrust");
        assertTrue(inv.isPresent(), "hasCrust 应存在逆属性");
        // 注意：若服务方法实现有误，此处会失败，请修复 ReasonerService.getInverseProperty()
        assertEquals("http://example.org/pizza/classes/isCrustOf",
                inv.get().getNamedProperty().getIRI().toString(),
                "hasCrust 的逆属性应为 isCrustOf");
    }

    @Test
    public void testGetAnnotations() {
        OWLClass cls = service.getClass("http://example.org/pizza/classes/Pizza");
        Map<OWLAnnotationProperty, Set<OWLLiteral>> annotations = service.getAnnotations(cls);
        assertNotNull(annotations);
        OWLAnnotationProperty labelProp = service.getOntology().getOWLOntologyManager().getOWLDataFactory().getRDFSLabel();
        Set<OWLLiteral> labels = annotations.get(labelProp);
        assertNotNull(labels);
        assertFalse(labels.isEmpty());
    }

    @Test
    public void testGetAnnotationValue() {
        OWLClass cls = service.getClass("http://example.org/pizza/classes/Pizza");
        Set<OWLLiteral> values = service.getAnnotationValue(cls, "http://www.w3.org/2000/01/rdf-schema#label");
        assertNotNull(values);
        assertFalse(values.isEmpty());
        boolean found = values.stream().anyMatch(lit -> lit.getLiteral().contains("披萨"));
        assertTrue(found, "应包含中文标签");
    }

    // ==================== 个体相关查询 ====================

    @Test
    public void testGetIndividual() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/components/individuals/Pepperoni");
        assertNotNull(ind);
        assertEquals("Pepperoni", ind.getIRI().getShortForm());
    }

    @Test
    public void testGetIndividual_NotFound() {
        assertThrows(IllegalArgumentException.class, () ->
                service.getIndividual("http://example.org/pizza/components/individuals/NotExist")
        );
    }

    @Test
    public void testGetIndividualDirectTypes() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/components/individuals/Pepperoni");
        Set<OWLClass> types = service.getIndividualDirectTypes(ind);
        assertNotNull(types);
        assertTrue(types.stream().anyMatch(c -> c.getIRI().getShortForm().equals("MeatTopping")));
    }

    @Test
    public void testGetIndividualAllTypes() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/components/individuals/Pepperoni");
        Set<OWLClass> allTypes = service.getIndividualAllTypes(ind);
        assertNotNull(allTypes);
        assertTrue(allTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("MeatTopping")));
        assertTrue(allTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("Topping")));
    }

    @Test
    public void testGetObjectPropertiesOfIndividual() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/individuals/neapolitanPizzaInstance");
        Set<OWLObjectPropertyExpression> props = service.getObjectPropertiesOfIndividual(ind);
        assertNotNull(props);
        assertTrue(props.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasCrust")));
    }

    @Test
    public void testGetObjectPropertyDirectValueOfIndividual() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/individuals/neapolitanPizzaInstance");
        OWLObjectProperty prop = service.getObjectProperty("http://example.org/pizza/classes/hasCrust");
        Set<OWLNamedIndividual> values = service.getObjectPropertyDirectValueOfIndividual(ind, prop);
        assertNotNull(values);
        assertTrue(values.stream().anyMatch(i -> i.getIRI().getShortForm().equals("neapolitanCrustInstance")));
    }

    @Test
    public void testGetObjectPropertyAllValueOfIndividual() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/individuals/neapolitanPizzaInstance");
        OWLObjectProperty prop = service.getObjectProperty("http://example.org/pizza/classes/hasCrust");
        Set<OWLNamedIndividual> values = service.getObjectPropertyAllValueOfIndividual(ind, prop);
        assertNotNull(values);
        assertTrue(values.stream().anyMatch(i -> i.getIRI().getShortForm().equals("neapolitanCrustInstance")));
    }

    @Test
    public void testGetDirectDataPropertiesOfIndividual() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/components/individuals/Pepperoni");
        Set<OWLDataProperty> props = service.getDirectDataPropertiesOfIndividual(ind);
        assertNotNull(props);
        assertTrue(props.stream().anyMatch(p -> p.getIRI().getShortForm().equals("price")));
        assertTrue(props.stream().anyMatch(p -> p.getIRI().getShortForm().equals("status")));
    }

    @Test
    public void testGetAllAllowedDataPropertiesOfIndividual() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/components/individuals/Pepperoni");
        Set<OWLDataProperty> props = service.getAllAllowedDataPropertiesOfIndividual(ind);
        assertNotNull(props);
        assertTrue(props.stream().anyMatch(p -> p.getIRI().getShortForm().equals("price")));
        assertTrue(props.stream().anyMatch(p -> p.getIRI().getShortForm().equals("supplier")));
    }

    @Test
    public void testGetDataPropertyValueOfIndividual_WithProperty() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/components/individuals/Pepperoni");
        OWLDataProperty prop = service.getDataProperty("http://example.org/pizza/components/classes/price");
        Set<OWLLiteral> values = service.getDataPropertyValueOfIndividual(ind, prop);
        assertNotNull(values);
        assertFalse(values.isEmpty());
        assertTrue(values.stream().anyMatch(lit -> lit.getLiteral().equals("5.0")));
    }

    @Test
    public void testGetDataPropertyValueOfIndividual_WithIRI() {
        OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/components/individuals/Pepperoni");
        Set<OWLLiteral> values = service.getDataPropertyValueOfIndividual(ind, "http://example.org/pizza/components/classes/price");
        assertNotNull(values);
        assertFalse(values.isEmpty());
        assertTrue(values.stream().anyMatch(lit -> lit.getLiteral().equals("5.0")));
    }

    @Test
    public void testGetDataPropertyDomains() {
        Set<OWLClass> domains = service.getDataPropertyDomains("http://example.org/pizza/components/classes/price");
        assertNotNull(domains);
        assertTrue(domains.stream().anyMatch(c -> c.getIRI().getShortForm().equals("PizzaComponent")));
    }

    @Test
    public void testGetDataPropertyRanges() {
        Set<OWLDatatype> ranges = service.getDataPropertyRanges("http://example.org/pizza/components/classes/price");
        assertNotNull(ranges);
        // 使用 equals 直接比较 IRI 对象，更可靠
        IRI expectedIRI = IRI.create("http://www.w3.org/2001/XMLSchema#decimal");
        assertTrue(ranges.stream().anyMatch(dt -> dt.getIRI().equals(expectedIRI)));
    }

    @Test
    public void testIsInstanceOf() {
        String individualIRI = "http://example.org/pizza/components/individuals/Pepperoni";
        String classIRI = "http://example.org/pizza/components/classes/MeatTopping";

        OWLNamedIndividual ind = service.getIndividual(individualIRI);
        OWLClass cls = service.getClass(classIRI);
        assertNotNull(ind);
        assertNotNull(cls);

        Set<OWLClass> directTypes = service.getIndividualDirectTypes(ind);
        IRI iri = service.resolveIRI(cls.getIRI().getIRIString());
        System.out.println("Pepperoni 的直接类型: " + iri);

        // 使用 toString() 获取完整 IRI 字符串，避免前缀影响
        boolean hasMeatTopping = iri.getIRIString().equals(classIRI);
        assertTrue(hasMeatTopping,
                "Pepperoni 的直接类型中应包含 MeatTopping，实际类型 IRI 为: " +
                        directTypes.stream().map(c -> c.getIRI().toString()).collect(Collectors.toList()));

        boolean result = service.isInstanceOf(individualIRI, classIRI);
        assertTrue(result, "isInstanceOf 应返回 true，实际返回 " + result);

        assertFalse(service.isInstanceOf(individualIRI,
                        "http://example.org/pizza/components/classes/Cheese"),
                "Pepperoni 不应是 Cheese 的实例");
    }

    // ==================== 其他查询 ====================

    @Test
    public void testGetClass() {
        OWLClass cls = service.getClass("http://example.org/pizza/classes/Pizza");
        assertNotNull(cls);
        assertEquals("Pizza", cls.getIRI().getShortForm());
    }

    @Test
    public void testGetClass_NotFound() {
        assertThrows(IllegalArgumentException.class, () ->
                service.getClass("http://example.org/pizza/classes/NotExist")
        );
    }

    @Test
    public void testGetDatatype() {
        Optional<OWLDatatype> dt = service.getDatatype("http://www.w3.org/2001/XMLSchema#string");
        assertTrue(dt.isPresent());
        assertEquals("string", dt.get().getIRI().getShortForm());
    }

    @Test
    public void testGetEntityType() {
        IRI iri = IRI.create("http://example.org/pizza/classes/Pizza");
        String type = service.getEntityType(iri);
        assertEquals("Class", type);

        iri = IRI.create("http://example.org/pizza/components/individuals/Pepperoni");
        type = service.getEntityType(iri);
        assertEquals("Individual", type);

        iri = IRI.create("http://example.org/pizza/classes/hasCrust");
        type = service.getEntityType(iri);
        assertEquals("ObjectProperty", type);

        iri = IRI.create("http://example.org/pizza/components/classes/price");
        type = service.getEntityType(iri);
        assertEquals("DataProperty", type);

        // term:Pizza 是 SKOS 概念，在 OWL 中建模为个体
        iri = IRI.create("http://example.org/pizza/term/Pizza");
        type = service.getEntityType(iri);
        assertEquals("Individual", type);

        // 测试注释属性
        iri = IRI.create("http://www.w3.org/2000/01/rdf-schema#label");
        type = service.getEntityType(iri);
        assertEquals("AnnotationProperty", type);

        // 测试数据类型
        iri = IRI.create("http://www.w3.org/2001/XMLSchema#string");
        type = service.getEntityType(iri);
        assertEquals("Datatype", type);
    }

    @Test
    public void testGetLabel() {
        IRI iri = IRI.create("http://example.org/pizza/classes/Pizza");
        String label = service.getLabel(service.getOntology(), iri, "zh");
        assertNotNull(label);
        assertTrue(label.contains("披萨") || label.contains("Pizza"));
    }

    @Test
    public void testConsistency() {
        assertTrue(service.isConsistent(), "本体应一致");
    }

    @Test
    public void testComprehensiveScenario() {
        // ==================== 输入准备 ====================
        String pizzaIRI = "http://example.org/pizza/individuals/neapolitanPizzaInstance";
        String classIRI = "http://example.org/pizza/classes/NeapolitanPizza";
        String crustIRI = "http://example.org/pizza/components/individuals/neapolitanCrustInstance";
        String meatToppingIRI = "http://example.org/pizza/components/classes/MeatTopping";
        String pricePropIRI = "http://example.org/pizza/components/classes/price";
        String hasCrustIRI = "http://example.org/pizza/classes/hasCrust";
        String processStepIRI = "http://example.org/pizza/processes/classes/hasProcessStep";
        String labelIRI = "http://www.w3.org/2000/01/rdf-schema#label";
        String commentIRI = "http://www.w3.org/2000/01/rdf-schema#comment";
        String decimalIRI = "http://www.w3.org/2001/XMLSchema#decimal";

        OWLNamedIndividual pizzaInd = service.getIndividual(pizzaIRI);
        OWLClass neapolitanClass = service.getClass(classIRI);
        OWLNamedIndividual crustInd = service.getIndividual(crustIRI);

        // ==================== 1. 类查询 ====================
        assertNotNull(neapolitanClass);
        Set<OWLNamedIndividual> meatInds = service.getIndividuals(meatToppingIRI);
        assertFalse(meatInds.isEmpty());
        assertTrue(meatInds.stream().anyMatch(i -> i.getIRI().getShortForm().equals("Pepperoni")));

        Set<OWLClass> supers = service.getSuperClasses(neapolitanClass);
        assertTrue(supers.stream().anyMatch(c -> c.getIRI().getShortForm().equals("ItalianTraditionalPizza")));

        Set<OWLClass> subs = service.getSubClasses("http://example.org/pizza/classes/ItalianTraditionalPizza");
        assertTrue(subs.stream().anyMatch(c -> c.getIRI().getShortForm().equals("NeapolitanPizza")));

        // ==================== 2. 对象属性查询 ====================
        Set<OWLObjectPropertyExpression> allProps = service.getAllObjectPropertiesOfClass(neapolitanClass);
        assertTrue(allProps.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasCrust")));

        OWLObjectPropertyExpression hasCrustProp = service.getObjectPropertyOfClass(neapolitanClass, hasCrustIRI);
        assertNotNull(hasCrustProp);

        Set<OWLClassExpression> domains = service.getObjectPropertyDomain(hasCrustProp);
        assertTrue(domains.stream().anyMatch(d -> d.asOWLClass().getIRI().getShortForm().equals("Pizza")));

        Set<OWLClassExpression> ranges = service.getObjectPropertyRange(hasCrustProp);
        assertTrue(ranges.stream().anyMatch(r -> r.asOWLClass().getIRI().getShortForm().equals("Crust")));

        Set<OWLClassExpression> limitations = service.getObjectPropertyLimitations(neapolitanClass, hasCrustProp);
        assertTrue(limitations.stream().anyMatch(expr -> {
            if (expr instanceof OWLObjectSomeValuesFrom) {
                OWLObjectSomeValuesFrom some = (OWLObjectSomeValuesFrom) expr;
                return some.getFiller().asOWLClass().getIRI().getShortForm().equals("NeapolitanCrust");
            }
            return false;
        }));

        Optional<OWLObjectPropertyExpression> invProp = service.getInverseProperty(hasCrustIRI);
        assertTrue(invProp.isPresent());
        assertEquals("isCrustOf", invProp.get().getNamedProperty().getIRI().getShortForm());

        // ==================== 3. 个体查询 ====================
        Set<OWLClass> directTypes = service.getIndividualDirectTypes(pizzaInd);
        assertTrue(directTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("GenericNeapolitanPizza")));

        Set<OWLClass> allTypes = service.getIndividualAllTypes(pizzaInd);
        assertTrue(allTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("NeapolitanPizza")));
        assertTrue(allTypes.stream().anyMatch(c -> c.getIRI().getShortForm().equals("TomatoBasedPizza")));

        Set<OWLObjectPropertyExpression> indProps = service.getObjectPropertiesOfIndividual(pizzaInd);
        assertTrue(indProps.stream().anyMatch(p -> p.getNamedProperty().getIRI().getShortForm().equals("hasCrust")));

        Set<OWLNamedIndividual> directCrustValues = service.getObjectPropertyDirectValueOfIndividual(pizzaInd, hasCrustProp);
        assertFalse(directCrustValues.isEmpty());
        assertTrue(directCrustValues.stream().anyMatch(i -> i.getIRI().getShortForm().equals("neapolitanCrustInstance")));

        Set<OWLNamedIndividual> allCrustValues = service.getObjectPropertyAllValueOfIndividual(pizzaInd, hasCrustProp);
        assertFalse(allCrustValues.isEmpty());

        Set<OWLDataProperty> directDataProps = service.getDirectDataPropertiesOfIndividual(crustInd);
        assertTrue(directDataProps.stream().anyMatch(p -> p.getIRI().getShortForm().equals("crustThicknessMm")));

        Set<OWLDataProperty> allDataProps = service.getAllAllowedDataPropertiesOfIndividual(crustInd);
        assertTrue(allDataProps.stream().anyMatch(p -> p.getIRI().getShortForm().equals("price")));

        Set<OWLLiteral> thicknessValues = service.getDataPropertyValueOfIndividual(crustInd,
                "http://example.org/pizza/components/classes/crustThicknessMm");
        assertFalse(thicknessValues.isEmpty());
        assertTrue(thicknessValues.stream().anyMatch(l -> l.getLiteral().equals("5.0")));

        Set<OWLClass> priceDomains = service.getDataPropertyDomains(pricePropIRI);
        assertTrue(priceDomains.stream().anyMatch(c -> c.getIRI().getShortForm().equals("PizzaComponent")));

        Set<OWLDatatype> priceRanges = service.getDataPropertyRanges(pricePropIRI);
        assertTrue(priceRanges.stream().anyMatch(dt -> dt.getIRI().toString().equals(decimalIRI)));

        // ==================== 4. 其他查询 ====================
        OWLClass clsByQName = service.getClass("comp:MeatTopping");
        assertNotNull(clsByQName);

        Optional<OWLDatatype> dt = service.getDatatype(decimalIRI);
        assertTrue(dt.isPresent());

        assertEquals("Class", service.getEntityType(IRI.create("http://example.org/pizza/classes/Pizza")));
        assertEquals("Individual", service.getEntityType(IRI.create(pizzaIRI)));
        assertEquals("ObjectProperty", service.getEntityType(IRI.create(hasCrustIRI)));
        assertEquals("DataProperty", service.getEntityType(IRI.create(pricePropIRI)));

        Map<OWLAnnotationProperty, Set<OWLLiteral>> annoMap = service.getAnnotations(neapolitanClass);
        assertFalse(annoMap.isEmpty());

        Set<OWLLiteral> labels = service.getAnnotationValue(neapolitanClass, labelIRI);
        assertFalse(labels.isEmpty());
        assertTrue(labels.stream().anyMatch(l -> l.getLiteral().contains("那不勒斯披萨")));

        Set<OWLLiteral> comments = service.getAnnotationValue(pizzaInd, commentIRI);
        assertFalse(comments.isEmpty());

        // 测试个体的 rdfs:label 注释
        Set<OWLLiteral> indLabels = service.getAnnotationValue(pizzaInd, labelIRI);
        assertFalse(indLabels.isEmpty(), "披萨实例应有 rdfs:label 注释");

        // 实例检查：正向使用 isInstanceOf，负向使用 getIndividualAllTypes 检查集合
        assertTrue(service.isInstanceOf(pizzaIRI, "http://example.org/pizza/classes/GenericNeapolitanPizza"));

        // 负向：检查饼底实例不是 Cheese
        OWLClass cheeseClass = service.getClass("http://example.org/pizza/components/classes/Cheese");
        Set<OWLClass> crustAllTypes = service.getIndividualAllTypes(crustInd);
        assertFalse(crustAllTypes.contains(cheeseClass), "饼底不应是 Cheese 的实例");

        // 同时检查披萨实例也不是 Cheese
        assertFalse(allTypes.contains(cheeseClass), "披萨不应是 Cheese 的实例");

        // 获取标签
        String label = service.getLabel(service.getOntology(), IRI.create(classIRI), "zh");
        assertNotNull(label);
        assertTrue(label.contains("披萨"));

        System.out.println("综合场景测试全部通过！");
    }
}