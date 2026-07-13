package com.ocean.ontopobdahandler;

import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VKG OBDAHandler 单例集成测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OntopObdaHandlerApplicationTests {

    private static OBDAHandler handler;
    private static final String TEST_NAME = "SingletonTest_" + System.currentTimeMillis();

    @BeforeAll
    static void init() {
        handler = OBDAHandler.getInstance();
        assertNotNull(handler, "单例初始化失败");

        // 验证第二次获取是否为同一实例
        assertSame(handler, OBDAHandler.getInstance(), "违反单例约束！");
        System.out.println("✅ OBDAHandler 单例就绪");
    }

    @AfterAll
    static void cleanup() {
        OBDAHandler.shutdown();
    }

    @Test @Order(1)
    @DisplayName("[查] 基础属性查询")
    void testGetProperties() {
        List<Map<String, String>> result = handler.getInstanceProperties(
                "http://example.org/pizza/components/classes/NeapolitanCrust");
        assertFalse(result.isEmpty(), "NeapolitanCrust 应有属性返回");
        System.out.println("属性数量: " + result.size());
    }

    @Test @Order(2)
    @DisplayName("[查] 推理查询")
    void testInference() {
        List<Map<String, String>> result = handler.queryWithInference(":PizzaComponent", 5);
        assertTrue(result.size() <= 5, "LIMIT 未生效");
        result.forEach(r -> System.out.printf("  %s → %s%n",
                r.get("individual"), r.get("type")));
    }

    @Test @Order(3)
    @DisplayName("[增→查] 写入并验证闭环")
    void testAddAndVerify() {
        int rows = handler.addComponent(TEST_NAME, "单例测试供应商", 77.7, "Topping");
        assertEquals(1, rows);

        // Ontop virtual 模式实时可见
        String uri = "http://example.org/pizza/" + TEST_NAME;
        List<Map<String, String>> props = handler.getInstanceProperties(uri);
        assertFalse(props.isEmpty(), "写入后 SPARQL 应立即可查");
    }

    @Test @Order(4)
    @DisplayName("[改→查] 更新价格并验证")
    void testUpdateAndVerify() {
        handler.updatePrice(TEST_NAME, 150.0);
        String uri = "http://example.org/pizza/" + TEST_NAME;
        List<Map<String, String>> props = handler.getInstanceProperties(uri);

        boolean priceUpdated = props.stream()
                .anyMatch(p -> "price".equals(p.get("property"))
                        && p.get("value").contains("150"));
        assertTrue(priceUpdated, "价格应更新为 150.0");
    }

    @Test @Order(5)
    @DisplayName("[删→查] 删除并验证不可见")
    void testDeleteAndVerify() {
        handler.deleteComponent(TEST_NAME);
        String uri = "http://example.org/pizza/" + TEST_NAME;
        List<Map<String, String>> props = handler.getInstanceProperties(uri);
        assertTrue(props.isEmpty(), "删除后 SPARQL 应返回空");
    }
}