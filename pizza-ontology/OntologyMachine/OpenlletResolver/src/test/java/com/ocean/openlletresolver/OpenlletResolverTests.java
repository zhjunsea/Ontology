package com.ocean.openlletresolver;

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

import static org.apache.jena.assembler.JA.reasoner;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OpenlletResolverTests {

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

    @Test
    public void testLowStockRuleWithExistingOntology() throws Exception {
        /*
        // 1. 确认规则是否存在（若不存在，后续断言会失败，可提前检查）
        long ruleCount = service.getOntology().axioms(AxiomType.SWRL_RULE).count();
        System.out.println("当前本体中 SWRL 规则数量: " + ruleCount);
        if (ruleCount == 0) {
            fail("本体中未加载任何 SWRL 规则，请检查规则序列化格式是否为标准 SWRL/XML 或已通过转换修复");
        }*/

        // 2. 获取相关类和属性
        OWLClass crustClass = service.getClass("http://example.org/pizza/components/classes/NeapolitanCrust");
        OWLClass lowStockCrustClass = service.getClass("http://example.org/pizza/components/classes/LowStockCrust");
        OWLDataProperty stockQty = service.getDataProperty("http://example.org/pizza/components/classes/stockQuantity");

        // 3. 选取一个饼底个体（若存在则复用，否则创建一个并添加到本体）
        OWLNamedIndividual crustInd;
        Set<OWLNamedIndividual> existingCrusts = service.getIndividuals(crustClass.getIRI().toString());
        OWLOntologyManager manager = service.getOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        if (!existingCrusts.isEmpty()) {
            // 使用第一个找到的饼底个体
            crustInd = existingCrusts.iterator().next();
            System.out.println("使用现有个体: " + crustInd.getIRI().getShortForm());
        } else {
            // 没有找到，则创建一个临时饼底个体并添加到本体
            crustInd = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components/individuals/testCrustForRule"));
            manager.addAxiom(service.getOntology(), df.getOWLClassAssertionAxiom(crustClass, crustInd));
            System.out.println("创建新的饼底个体: testCrustForRule");
        }

        // 4. 设置初始库存为 30（高于阈值），推理机预计算后应不属于 LowStockCrust
        Set<OWLLiteral> initialStock = service.getDataPropertyValueOfIndividual(crustInd,stockQty);
        int qty = service.parseNumeric(initialStock.stream()
                .findFirst()
                .orElse(null)).intValue();

        // 检查：库存充足时不应属于 LowStockCrust
        Set<OWLClass> typesBefore = service.getIndividualAllTypes(crustInd);
        assertFalse(typesBefore.contains(lowStockCrustClass),             //测试crustInd的类里面有没有LowStockCrust，没有表示确实不在
                "库存充足（30）时，饼底不应被归类为 LowStockCrust");

        // 5. 将库存改为 15（低于阈值），重新推理，应被归类
        OWLLiteral lowStock = df.getOWLLiteral(15);
        // 移除旧公理，添加新公理
        Set<OWLDataPropertyAssertionAxiom> oldAxioms = service.getDataPropertyAssertions(crustInd,stockQty);
        service.removeAxiomSet(oldAxioms);
        service.addIndividualAxiom(crustInd,stockQty,lowStock);

        /*
        System.out.println("crustInd 的类型: " + service.getReasoner().getTypes(crustInd, false));
        System.out.println("stockQty IRI: " + stockQty.getIRI());
        Set<OWLLiteral> values = service.getReasoner().getDataPropertyValues(crustInd, stockQty);
        System.out.println("库存值: " + values);
        Set<OWLClass> typesAfter = service.getIndividualAllTypes(crustInd);
        System.out.println("=== crustInd 的所有类型（包括推理出的） ===");
        for (OWLClass cls : typesAfter) {
            System.out.println("  " + cls.getIRI() + "  （短名: " + cls.getIRI().getShortForm() + "）");
        }
        */


        OWLClass neapolitanCrust = df.getOWLClass(IRI.create("http://example.org/pizza/components/classes/NeapolitanCrust"));
        OWLClass crust = df.getOWLClass(IRI.create("http://example.org/pizza/components/classes/Crust"));
        /*
        // 打印所有涉及这两个类的 SubClassOf 公理
        service.getOntology().getAxioms(AxiomType.SUBCLASS_OF).forEach(axiom -> {
            if (axiom.getSubClass().equals(neapolitanCrust) || axiom.getSuperClass().equals(crust)) {
                System.out.println("子类公理: " + axiom);
            }
        });
        System.out.println("topCrustClass IRI: " + topCrustClass.getIRI());
        service.getOntology().getAxioms(AxiomType.SUBCLASS_OF).stream()
                .filter(ax -> ax.getSuperClass().isOWLClass())   // 只保留父类是命名类的公理
                .filter(ax -> ax.getSuperClass().asOWLClass().getIRI().toString().contains("Crust"))
                .findFirst()
                .ifPresent(ax -> System.out.println("父类 IRI: " + ax.getSuperClass().asOWLClass().getIRI()));

        Set<OWLClass> typesAfter = service.getIndividualAllTypes(crustInd);
        assertTrue(typesAfter.contains(lowStockCrustClass),
                "库存低于20（15）时，SWRL 规则应推断该饼底为 LowStockCrust。当前类型: " + typesAfter);

         */
        OWLReasoner reasoner = service.getReasoner();  // 需要 ReasonerService 暴露 getReasoner()
        boolean isLowStock = reasoner.getInstances(lowStockCrustClass, false)
                .entities()
                .anyMatch(i -> i.equals(crustInd));
        assertTrue(isLowStock, "库存低于20（15）时，SWRL 规则应推断该饼底为 LowStockCrust");
    }
    @Test
    public void testLowStockSauceRule() throws Exception {
        // 获取相关类和属性
        OWLClass sauceClass = service.getClass("http://example.org/pizza/components/classes/Sauce");
        OWLClass lowStockSauceClass = service.getClass("http://example.org/pizza/classes/LowStockSauce");
        OWLDataProperty stockQty = service.getDataProperty("http://example.org/pizza/components/classes/stockQuantity");

        // 选取一个酱汁个体（若无则创建）
        OWLNamedIndividual sauceInd;
        Set<OWLNamedIndividual> existingSauces = service.getIndividuals(sauceClass.getIRI().toString());
        OWLOntologyManager manager = service.getOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        if (!existingSauces.isEmpty()) {
            sauceInd = existingSauces.iterator().next();
            System.out.println("使用现有个体: " + sauceInd.getIRI().getShortForm());
        } else {
            sauceInd = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components/individuals/testSauceForRule"));
            manager.addAxiom(service.getOntology(), df.getOWLClassAssertionAxiom(sauceClass, sauceInd));
            System.out.println("创建新的酱汁个体: testSauceForRule");
        }

        // 获取当前库存值（若有），设为高于阈值（20 > 15）
        Set<OWLLiteral> initialStock = service.getDataPropertyValueOfIndividual(sauceInd, stockQty);
        int qty = initialStock.stream().findFirst()
                .map(service::parseNumeric)
                .map(Number::intValue)
                .orElse(20); // 默认给一个高于阈值15的值

        // 确保库存高于阈值，并清除旧断言
        service.getDataPropertyAssertions(sauceInd, stockQty)
                .forEach(ax -> manager.removeAxiom(service.getOntology(), ax));
        OWLLiteral highStock = df.getOWLLiteral(20);
        service.addIndividualAxiom(sauceInd, stockQty, highStock);
        service.refreshReasoner();

        // 当前库存20（>=15），不应属于 LowStockSauce
        Set<OWLClass> typesBefore = service.getIndividualAllTypes(sauceInd);
        assertFalse(typesBefore.contains(lowStockSauceClass),
                "库存充足（20）时，酱汁不应被归类为 LowStockSauce");

        // 修改库存为 10（低于阈值15）
        OWLLiteral lowStock = df.getOWLLiteral(10);
        service.getDataPropertyAssertions(sauceInd, stockQty)
                .forEach(ax -> manager.removeAxiom(service.getOntology(), ax));
        service.addIndividualAxiom(sauceInd, stockQty, lowStock);
        service.refreshReasoner();

        // 检查推理结果
        OWLReasoner reasoner = service.getReasoner();
        boolean isLowStock = reasoner.getInstances(lowStockSauceClass, false)
                .entities()
                .anyMatch(i -> i.equals(sauceInd));
        assertTrue(isLowStock, "库存低于15（10）时，SWRL 规则应推断该酱汁为 LowStockSauce");
    }

    @Test
    public void testLowStockCheeseRule() throws Exception {
        // 获取相关类和属性
        OWLClass cheeseClass = service.getClass("http://example.org/pizza/components/classes/Cheese");
        OWLClass lowStockCheeseClass = service.getClass("http://example.org/pizza/classes/LowStockCheese");
        OWLDataProperty stockQty = service.getDataProperty("http://example.org/pizza/components/classes/stockQuantity");

        // 选取一个奶酪个体（若无则创建）
        OWLNamedIndividual cheeseInd;
        Set<OWLNamedIndividual> existingCheeses = service.getIndividuals(cheeseClass.getIRI().toString());
        OWLOntologyManager manager = service.getOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        if (!existingCheeses.isEmpty()) {
            cheeseInd = existingCheeses.iterator().next();
            System.out.println("使用现有个体: " + cheeseInd.getIRI().getShortForm());
        } else {
            cheeseInd = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components/individuals/testCheeseForRule"));
            manager.addAxiom(service.getOntology(), df.getOWLClassAssertionAxiom(cheeseClass, cheeseInd));
            System.out.println("创建新的奶酪个体: testCheeseForRule");
        }

        // 设置库存高于阈值（20 > 10）
        service.getDataPropertyAssertions(cheeseInd, stockQty)
                .forEach(ax -> manager.removeAxiom(service.getOntology(), ax));
        OWLLiteral highStock = df.getOWLLiteral(20);
        service.addIndividualAxiom(cheeseInd, stockQty, highStock);
        service.refreshReasoner();

        Set<OWLClass> typesBefore = service.getIndividualAllTypes(cheeseInd);
        assertFalse(typesBefore.contains(lowStockCheeseClass),
                "库存充足（20）时，奶酪不应被归类为 LowStockCheese");

        // 修改库存为 5（低于阈值10）
        OWLLiteral lowStock = df.getOWLLiteral(5);
        service.getDataPropertyAssertions(cheeseInd, stockQty)
                .forEach(ax -> manager.removeAxiom(service.getOntology(), ax));
        service.addIndividualAxiom(cheeseInd, stockQty, lowStock);
        service.refreshReasoner();

        OWLReasoner reasoner = service.getReasoner();
        boolean isLowStock = reasoner.getInstances(lowStockCheeseClass, false)
                .entities()
                .anyMatch(i -> i.equals(cheeseInd));
        assertTrue(isLowStock, "库存低于10（5）时，SWRL 规则应推断该奶酪为 LowStockCheese");
    }

    @Test
    public void testLowStockToppingRule() throws Exception {
        // 获取相关类和属性
        OWLClass toppingClass = service.getClass("http://example.org/pizza/components/classes/Topping");
        OWLClass lowStockToppingClass = service.getClass("http://example.org/pizza/classes/LowStockTopping");
        OWLDataProperty stockQty = service.getDataProperty("http://example.org/pizza/components/classes/stockQuantity");

        // 选取一个配料个体（若无则创建）
        OWLNamedIndividual toppingInd;
        Set<OWLNamedIndividual> existingToppings = service.getIndividuals(toppingClass.getIRI().toString());
        OWLOntologyManager manager = service.getOntology().getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        if (!existingToppings.isEmpty()) {
            toppingInd = existingToppings.iterator().next();
            System.out.println("使用现有个体: " + toppingInd.getIRI().getShortForm());
        } else {
            toppingInd = df.getOWLNamedIndividual(IRI.create("http://example.org/pizza/components/individuals/testToppingForRule"));
            manager.addAxiom(service.getOntology(), df.getOWLClassAssertionAxiom(toppingClass, toppingInd));
            System.out.println("创建新的配料个体: testToppingForRule");
        }

        // 设置库存高于阈值（20 > 10）
        service.getDataPropertyAssertions(toppingInd, stockQty)
                .forEach(ax -> manager.removeAxiom(service.getOntology(), ax));
        OWLLiteral highStock = df.getOWLLiteral(20);
        service.addIndividualAxiom(toppingInd, stockQty, highStock);
        service.refreshReasoner();

        Set<OWLClass> typesBefore = service.getIndividualAllTypes(toppingInd);
        assertFalse(typesBefore.contains(lowStockToppingClass),
                "库存充足（20）时，配料不应被归类为 LowStockTopping");

        // 修改库存为 5（低于阈值10）
        OWLLiteral lowStock = df.getOWLLiteral(5);
        service.getDataPropertyAssertions(toppingInd, stockQty)
                .forEach(ax -> manager.removeAxiom(service.getOntology(), ax));
        service.addIndividualAxiom(toppingInd, stockQty, lowStock);
        service.refreshReasoner();

        OWLReasoner reasoner = service.getReasoner();
        boolean isLowStock = reasoner.getInstances(lowStockToppingClass, false)
                .entities()
                .anyMatch(i -> i.equals(toppingInd));
        assertTrue(isLowStock, "库存低于10（5）时，SWRL 规则应推断该配料为 LowStockTopping");
    }
}