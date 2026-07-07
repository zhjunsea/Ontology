package org.junzhang.hermitresolver;

import org.junit.jupiter.api.*;
import org.junzhang.ontologymachine.ReasonerService;
import org.semanticweb.owlapi.model.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class HermiTResolverTests {

    private static ReasonerService service;
    private static final String MAIN_ONTOLOGY_PATH = "D:/work/Ontology/pizza-ontology/ontology/myPizza.owl";

    private static final String CLASS_IRI = "http://example.org/pizza/NeapolitanPizza";
    private static final String OBJ_PROP_IRI = "http://example.org/pizza/hasProcessStep";
    private static final String DATA_PROP_IRI = "http://example.org/pizza/process/duration";
    private static final String INDIVIDUAL_IRI = "http://example.org/pizza/process/Task_DoughMix";
    private static final String ANNOTATION_PROP_IRI = "http://www.w3.org/2000/01/rdf-schema#label";
    private static final String DATATYPE_IRI = "http://www.w3.org/2001/XMLSchema#integer";

    @BeforeAll
    public static void initService() throws Exception {
        String path = System.getProperty("ontology.path", MAIN_ONTOLOGY_PATH);
        service = ReasonerService.getInstance(path);
        assertNotNull(service);
        assertTrue(service.isConsistent(), "本体不一致，无法继续测试");
    }

    @AfterAll
    public static void closeService() throws Exception {
        if (service != null) service.close();
    }

    @Test @Order(1)
    public void testClassQueries() {
        Set<OWLClass> superClasses = service.getSuperClasses(CLASS_IRI);
        assertNotNull(superClasses);
        System.out.println("=== 父类 ===");
        superClasses.forEach(c -> System.out.println(c.getIRI().getIRIString()));

        Set<OWLClass> subClasses = service.getSubClasses(CLASS_IRI);
        assertNotNull(subClasses);
        System.out.println("=== 子类 ===");
        subClasses.forEach(c -> System.out.println(c.getIRI().getIRIString()));

        Set<OWLNamedIndividual> instances = service.getIndividuals(CLASS_IRI);
        assertNotNull(instances);
        System.out.println("=== 实例 ===");
        instances.forEach(i -> System.out.println(i.getIRI().getIRIString()));

        assertFalse(subClasses.isEmpty() || instances.isEmpty(), "子类或实例至少有一项不为空");
    }

    @Test @Order(2)
    public void testObjectPropertyQueries() {
        Set<OWLClass> domains = service.getObjectPropertyDomains(OBJ_PROP_IRI);
        assertNotNull(domains);
        System.out.println("=== hasProcessStep 定义域 ===");
        domains.forEach(d -> System.out.println(d.getIRI().getIRIString()));

        Set<OWLClass> ranges = service.getObjectPropertyRanges(OBJ_PROP_IRI);
        assertNotNull(ranges);
        System.out.println("=== hasProcessStep 值域 ===");
        ranges.forEach(r -> System.out.println(r.getIRI().getIRIString()));

        Optional<OWLObjectPropertyExpression> inverse = service.getInverseProperty(OBJ_PROP_IRI);
        System.out.println("=== 逆属性 ===");
        inverse.ifPresentOrElse(
                inv -> {
                    if (inv.isOWLObjectProperty()) {
                        System.out.println(inv.asOWLObjectProperty().getIRI().getIRIString());
                    } else {
                        System.out.println("匿名逆属性(" + inv.getNamedProperty().getIRI().getIRIString() + " 的逆)");
                    }
                },
                () -> System.out.println("无逆属性")
        );
        assertFalse(domains.isEmpty(), "定义域不应为空");
    }

    @Test @Order(3)
    public void testDataPropertyQueries() {
        Set<OWLClass> domains = service.getDataPropertyDomains(DATA_PROP_IRI);
        assertNotNull(domains);
        System.out.println("=== duration 定义域 ===");
        domains.forEach(d -> System.out.println(d.getIRI().getIRIString()));

        Set<OWLDatatype> ranges = service.getDataPropertyRanges(DATA_PROP_IRI);
        assertNotNull(ranges);
        System.out.println("=== duration 值域（数据类型） ===");
        ranges.forEach(r -> System.out.println(r.getIRI().getIRIString()));

        assertFalse(ranges.isEmpty(), "值域不应为空");
    }

    @Test @Order(4)
    public void testIndividualQueries() {
        Set<OWLClass> types = service.getTypes(INDIVIDUAL_IRI);
        assertNotNull(types);
        System.out.println("=== Task_DoughMix 的类型 ===");
        types.forEach(t -> System.out.println(t.getIRI().getIRIString()));

        Map<OWLObjectPropertyExpression, Set<OWLIndividual>> objProps = service.getObjectPropertyValues(INDIVIDUAL_IRI);
        System.out.println("=== 对象属性值 ===");
        objProps.forEach((prop, vals) -> {
            System.out.print("属性: " + prop.getNamedProperty().getIRI().getIRIString() + " -> ");
            vals.forEach(v -> {
                if (v.isNamed()) {
                    System.out.print(v.asOWLNamedIndividual().getIRI().getIRIString() + " ");
                } else {
                    System.out.print(v.toString() + " ");
                }
            });
            System.out.println();
        });

        Map<OWLDataProperty, Set<OWLLiteral>> dataProps = service.getDataPropertyValues(INDIVIDUAL_IRI);
        System.out.println("=== 数据属性值 ===");
        dataProps.forEach((prop, vals) -> {
            System.out.print("属性: " + prop.getIRI().getIRIString() + " -> ");
            vals.forEach(v -> System.out.print(v.getLiteral() + " "));
            System.out.println();
        });

        assertTrue(types.stream().anyMatch(c -> c.getIRI().getIRIString().contains("ProcessStep")));
    }

    @Test @Order(5)
    public void testIndividualExtendedQueries() {
        System.out.println("\n=== 个体扩展查询 ===");
        Set<OWLClass> directTypes = service.getDirectTypes(INDIVIDUAL_IRI);
        System.out.println("直接声明类型：");
        directTypes.forEach(t -> System.out.println("  " + t.getIRI().getIRIString()));

        boolean isProcess = service.isInstanceOf(INDIVIDUAL_IRI, "http://example.org/pizza/process/ProcessStep");
        System.out.println("是否为 ProcessStep 实例（推理）：" + isProcess);
        assertTrue(isProcess);

        Set<OWLNamedIndividual> followsVals = service.getObjectPropertyValues(INDIVIDUAL_IRI, "http://example.org/pizza/process/follows");
        System.out.println("follows 属性值：");
        followsVals.forEach(v -> System.out.println("  " + v.getIRI().getIRIString()));

        Set<OWLLiteral> durations = service.getDataPropertyValues(INDIVIDUAL_IRI, DATA_PROP_IRI);
        System.out.print("duration 值：");
        durations.forEach(v -> System.out.print(v.getLiteral() + " "));
        System.out.println();

        Map<OWLAnnotationProperty, Set<String>> annotations = service.getIndividualAnnotations(INDIVIDUAL_IRI);
        System.out.println("个体的所有注释：");
        annotations.forEach((prop, vals) ->
                System.out.println("  " + prop.getIRI().getIRIString() + " : " + vals));
    }

    @Test @Order(6)
    public void testAnnotationPropertyQueries() {
        Set<String> labels = service.getAnnotationValues(CLASS_IRI, ANNOTATION_PROP_IRI, "zh");
        System.out.println("=== NeapolitanPizza 的中文标签 ===");
        labels.forEach(System.out::println);

        Set<String> labelsNoLang = service.getAnnotationValues(CLASS_IRI, ANNOTATION_PROP_IRI, null);
        System.out.println("=== NeapolitanPizza 所有语言标签 ===");
        labelsNoLang.forEach(System.out::println);

        assertFalse(labels.isEmpty(), "中文标签不应为空");
    }

    @Test @Order(7)
    public void testDatatypeQuery() {
        Optional<OWLDatatype> datatype = service.getDatatype(DATATYPE_IRI);
        assertTrue(datatype.isPresent());
        System.out.println("=== 数据类型 ===");
        System.out.println(datatype.get().getIRI().getIRIString());
    }

    @Test @Order(8)
    public void testIndividualPropertyChainQuery() {
        System.out.println("\n=== 属性链查询测试 ===");
        String chainJson = "[\"http://example.org/pizza/neapolitanPizzaInstance\", " +
                "\"http://example.org/pizza/hasProcessExecution\", " +
                "\"http://example.org/pizza/process/duration\"]";
        String duration = service.queryIndividualPropertyChain(chainJson, true);
        System.out.println("查询结果: " + duration);
        assertNotNull(duration, "未能获取到 duration 值");
        assertEquals("30", duration, "第一步时长应为30分钟");
    }
    @Test @Order(9)
    public void testGlobalPropertyChainQuery() {
        System.out.println("\n=== 属性链查询测试 ===");
        String chainJson = "[\"http://example.org/pizza/NeapolitanPizza\", " +
                "\"http://example.org/pizza/hasProcessStep\", " +
                "\"http://example.org/pizza/process/Task_DoughMix\", " +
                "\"http://example.org/pizza/process/duration\"]";
        String duration = service.queryGlobalPropertyChain(chainJson, true);
        System.out.println("查询结果: " + duration);
        assertNotNull(duration, "未能获取到 duration 值");
        assertEquals("30", duration, "第一步时长应为30分钟");
    }
}