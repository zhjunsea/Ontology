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
        List<Map<String, String>> result = handler.getInstanceProperties("http://example.org/pizza/components/classes/",
                "http://example.org/pizza/components/individuals/NeapolitanCrustInstance");
        assertFalse(result.isEmpty(), "NeapolitanCrust 应有属性返回");
        System.out.println("属性数量: " + result.size());
    }

    @Test @Order(2)
    @DisplayName("[查] 供应商聚合统计查询")
    void testQueryAggregation() {
        // 使用通用聚合函数，参数对应原硬编码 SPARQL
        List<Map<String, Object>> result = handler.queryAggregation(
                "http://example.org/pizza/components/classes/",
                "PizzaComponent",
                "supplier",
                "price",
                0  // 不限制行数
        );

        // 1. 基础非空校验（确保查询正常执行且数据库中有匹配数据）
        assertNotNull(result, "聚合查询结果不应为 null");
        assertFalse(result.isEmpty(), "聚合查询应至少返回一个供应商分组");

        // 2. 逐行验证字段完整性与数据类型
        for (Map<String, Object> row : result) {
            assertTrue(row.containsKey("supplier"), "每行必须包含 supplier 字段");
            assertTrue(row.containsKey("count"), "每行必须包含 count 字段");
            assertTrue(row.containsKey("avg_price"), "每行必须包含 avg_price 字段");

            assertInstanceOf(String.class, row.get("supplier"), "supplier 应为 String");
            assertInstanceOf(Number.class, row.get("count"), "count 应为数值类型");
            assertInstanceOf(Number.class, row.get("avg_price"), "avg_price 应为数值类型");

            // COUNT 结果必须 >= 1（GROUP BY 语义保证）
            int count = ((Number) row.get("count")).intValue();
            assertTrue(count >= 1, "每个分组的 count 应 >= 1，实际: " + count);

            // AVG 价格应为正数
            double avgPrice = ((Number) row.get("avg_price")).doubleValue();
            assertTrue(avgPrice > 0, "avg_price 应为正数，实际: " + avgPrice);
        }

        // 3. 验证 ORDER BY DESC(?count) 排序正确性
        for (int i = 0; i < result.size() - 1; i++) {
            int current = ((Number) result.get(i).get("count")).intValue();
            int next = ((Number) result.get(i + 1).get("count")).intValue();
            assertTrue(current >= next,
                    String.format("DESC 排序错误: 第%d行 count=%d < 第%d行 count=%d",
                            i, current, i + 1, next));
        }

        // 打印便于调试
        System.out.println("聚合结果共 " + result.size() + " 个供应商分组:");
        result.forEach(r -> System.out.printf("  供应商: %-20s | 数量: %d | 均价: %.2f%n",
                r.get("supplier"),
                ((Number) r.get("count")).intValue(),
                ((Number) r.get("avg_price")).doubleValue()));
    }

    @Test @Order(3)
    @DisplayName("[查] 推理查询")
    void testInference() {
        List<Map<String, String>> result = handler.queryWithInference("http://example.org/pizza/components/individuals/","http://example.org/pizza/components/classes/PizzaComponent", 5);
        assertTrue(result.size() <= 5, "LIMIT 未生效");
        result.forEach(r -> System.out.printf("  %s → %s%n",
                r.get("individual"), r.get("type")));
    }

    @Test @Order(4)
    @DisplayName("[增→查] 写入并验证闭环")
    void testAddAndVerify() {
        int rows = handler.addComponent(
                "pizza_components",
                List.of("name", "supplier", "price", "type"),
                List.of(TEST_NAME, "单例测试供应商", 77.7, "Topping")
        );
        assertEquals(1, rows);

        // Ontop virtual 模式实时可见
        String uri = "http://example.org/pizza/components/individuals/" + TEST_NAME;
        List<Map<String, String>> props = handler.getInstanceProperties("http://example.org/pizza/components/classes/",uri);
        assertFalse(props.isEmpty(), "写入后 SPARQL 应立即可查");
    }

    @Test @Order(5)
    @DisplayName("[改→查] 更新价格并验证")
    void testUpdateAndVerify() {
        int rows = handler.updateComponent(
                "pizza_components",
                List.of("price"), List.of(150.0),
                "name", TEST_NAME
        );
        String uri = "http://example.org/pizza/components/individuals/" + TEST_NAME;
        List<Map<String, String>> props = handler.getInstanceProperties("http://example.org/pizza/components/classes/",uri);

        boolean priceUpdated = props.stream()
                .anyMatch(p -> "price".equals(p.get("property"))
                        && p.get("value").contains("150"));
        assertTrue(priceUpdated, "价格应更新为 150.0");
    }

    @Test @Order(6)
    @DisplayName("[删→查] 删除并验证不可见")
    void testDeleteAndVerify() {
        int rows = handler.deleteComponent("pizza_components", "name", TEST_NAME);
        String uri = "http://example.org/pizza/components/individuals/" + TEST_NAME;
        List<Map<String, String>> props = handler.getInstanceProperties("http://example.org/pizza/components/classes/",uri);
        assertTrue(props.isEmpty(), "删除后 SPARQL 应返回空");
    }
}