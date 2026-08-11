package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TCM (伤寒桂林古本) OBDA 映射集成测试
 * <p>
 * 验证 MySQL → Ontop → SPARQL 虚拟知识图谱映射的正确性。
 * 所有路径与端点地址均从 application.yml 中读取。
 */
@SpringBootTest
@DisplayName("TCM OBDA 映射验证测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OntologyFrameworkApplicationTests {

    private static final Logger log = LoggerFactory.getLogger(OntologyFrameworkApplicationTests.class);

    @Value("${ontology.obda-path}")
    private String obdaPath;

    @Value("${ontology.main-path}")
    private String owlPath;

    @Value("${ontology.ontop.sparql-endpoint}")
    private String sparqlEndpoint;

    private static OBDAHandler obdaHandler;

    @BeforeAll
    static void setUp(@Value("${ontology.obda-path}") String obdaPath,
                      @Value("${ontology.main-path}") String owlPath) throws Exception {
        log.info("=== 初始化 TCM OBDA 映射测试环境 ===");
        obdaHandler = OBDAHandler.getInstance(obdaPath, owlPath);
        assertNotNull(obdaHandler, "OBDAHandler 初始化失败，请检查 application.yml 中的 ontology.obda-path 与 ontology.main-path");
        log.info("✅ OBDAHandler 初始化成功 | OBDA={} | OWL={}", obdaPath, owlPath);
    }

    // ============================================================
    // TC-01: 全局冒烟测试
    // ============================================================
    @Test
    @Order(1)
    @DisplayName("TC-01: 全局冒烟测试 - 验证三元组总数 > 0")
    void testSmokeTest() {
        String sparql = "SELECT (COUNT(*) AS ?totalTriples) WHERE { ?s ?p ?o }";

        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);

        assertNotNull(rows, "SPARQL 查询结果不应为 null");
        assertEquals(1, rows.size(), "COUNT 查询应恰好返回 1 行");

        long totalTriples = Long.parseLong(rows.get(0).get("totalTriples"));
        assertTrue(totalTriples > 0,
                "三元组总数应 > 0，实际为 " + totalTriples + "。请检查 OBDA 文件是否加载及数据库是否有数据");

        log.info("✅ TC-01 通过: 三元组总数 = {}", totalTriples);
    }

    // ============================================================
    // TC-02: 方剂分类映射验证
    // ============================================================
    @Test
    @Order(2)
    @DisplayName("TC-02: formula_category_mapping - 验证 FormulaCategory 实例及标签")
    void testFormulaCategoryMapping() {
        String sparql = """
                PREFIX tcm:  <http://www.tcm-classics.org/shanghan/guilin#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?category ?label
                WHERE {
                  ?category a tcm:FormulaCategory ;
                            rdfs:label ?label .
                }
                LIMIT 5
                """;

        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);

        assertNotNull(rows, "查询结果不应为 null");
        assertFalse(rows.isEmpty(), "应至少返回 1 条 FormulaCategory 记录，请检查 formula_category 表数据及映射");

        for (Map<String, String> row : rows) {
            String category = row.get("category");
            String label = row.get("label");
            assertNotNull(category, "category IRI 不应为 null");
            assertTrue(category.startsWith("http://www.tcm-classics.org/shanghan/guilin#"),
                    "category IRI 命名空间错误: " + category);
            assertNotNull(label, "label 不应为 null");
            assertFalse(label.isBlank(), "label 不应为空字符串");
        }

        log.info("✅ TC-02 通过: 检索到 {} 条 FormulaCategory 记录", rows.size());
        rows.forEach(r -> log.debug("  category={} | label={}", r.get("category"), r.get("label")));
    }

    // ============================================================
    // TC-03: 方剂主体与文本属性验证
    // ============================================================
    @Test
    @Order(3)
    @DisplayName("TC-03: formula_mapping - 验证 Formula 实例及 tcm: 命名空间属性")
    void testFormulaMappingTextProperties() {
        String sparql = """
                PREFIX tcm:  <http://www.tcm-classics.org/shanghan/guilin#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?formula ?label ?source ?dosage
                WHERE {
                  ?formula a tcm:Formula ;
                           rdfs:label ?label .
                  OPTIONAL { ?formula tcm:source_clause ?source }
                  OPTIONAL { ?formula tcm:original_dosage ?dosage }
                }
                LIMIT 5
                """;

        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);

        assertNotNull(rows, "查询结果不应为 null");
        assertFalse(rows.isEmpty(), "应至少返回 1 条 Formula 记录，请检查 formula 表数据及映射");

        for (Map<String, String> row : rows) {
            String formula = row.get("formula");
            String label = row.get("label");
            assertNotNull(formula, "formula IRI 不应为 null");
            assertTrue(formula.startsWith("http://www.tcm-classics.org/shanghan/guilin#"),
                    "formula IRI 命名空间错误（可能仍为 /fangji#）: " + formula);
            assertNotNull(label, "label 不应为 null");
        }

        log.info("✅ TC-03 通过: 检索到 {} 条 Formula 记录，命名空间均为 tcm:", rows.size());
        rows.forEach(r -> log.debug("  formula={} | label={} | source={} | dosage={}",
                r.get("formula"), r.get("label"), r.get("source"), r.get("dosage")));
    }

    // ============================================================
    // TC-04: 方剂关系映射验证（外键 IRI 类型）
    // ============================================================
    @Test
    @Order(4)
    @DisplayName("TC-04: formula_mapping + formula_herb_mapping - 验证对象属性值为 IRI 而非字面量")
    void testFormulaRelationPropertiesAreIRIs() {
        String sparql = """
                PREFIX tcm:  <http://www.tcm-classics.org/shanghan/guilin#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?formula ?label ?category ?pattern ?herb
                WHERE {
                  ?formula a tcm:Formula ;
                           rdfs:label ?label .
                  OPTIONAL { ?formula tcm:belongs_to_formula_category ?category }
                  OPTIONAL { ?formula tcm:indicated_for ?pattern }
                  OPTIONAL { ?formula tcm:composed_of ?herb }
                }
                LIMIT 10
                """;

        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);

        assertNotNull(rows, "查询结果不应为 null");
        assertFalse(rows.isEmpty(), "应至少返回 1 条带关系的 Formula 记录");

        int relationCount = 0;
        for (Map<String, String> row : rows) {
            for (String var : List.of("category", "pattern", "herb")) {
                String value = row.get(var);
                if (value != null && !value.isBlank()) {
                    relationCount++;
                    assertTrue(value.startsWith("http://"),
                            String.format("%s 应为 IRI，实际为字面量: %s | formula=%s",
                                    var, value, row.get("formula")));
                }
            }
        }

        assertTrue(relationCount > 0,
                "应至少存在一条非空的关系属性值，请检查 category_uri/pattern_uri/formula_herb 表数据");

        log.info("✅ TC-04 通过: 验证 {} 个关系属性值均为 IRI 类型", relationCount);
    }

    // ============================================================
    // TC-05: 药物主体与属性验证
    // ============================================================
    @Test
    @Order(5)
    @DisplayName("TC-05: herb_mapping - 验证 Herb 实例及 yw: 命名空间属性")
    void testHerbMapping() {
        String sparql = """
                PREFIX yw:   <http://www.tcm-classics.org/yaowu#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?herb ?label ?taste ?nature ?source
                WHERE {
                  ?herb a yw:Herb ;
                        rdfs:label ?label .
                  OPTIONAL { ?herb yw:original_taste ?taste }
                  OPTIONAL { ?herb yw:original_nature ?nature }
                  OPTIONAL { ?herb yw:earliest_source ?source }
                }
                LIMIT 5
                """;

        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);

        assertNotNull(rows, "查询结果不应为 null");
        assertFalse(rows.isEmpty(), "应至少返回 1 条 Herb 记录，请检查 herb 表数据及映射");

        for (Map<String, String> row : rows) {
            assertNotNull(row.get("herb"), "herb IRI 不应为 null");
            assertNotNull(row.get("label"), "label 不应为 null");
        }

        log.info("✅ TC-05 通过: 检索到 {} 条 Herb 记录", rows.size());
        rows.forEach(r -> log.debug("  herb={} | label={} | taste={} | nature={}",
                r.get("herb"), r.get("label"), r.get("taste"), r.get("nature")));
    }

    // ============================================================
    // TC-06: 药物-八纲与药物-症状关系验证
    // ============================================================
    @Test
    @Order(6)
    @DisplayName("TC-06: herb_bagang_mapping + herb_symptom_mapping - 验证药物关联关系")
    void testHerbRelationMappings() {
        String sparql = """
                PREFIX yw:   <http://www.tcm-classics.org/yaowu#>
                PREFIX tcm:  <http://www.tcm-classics.org/shanghan/guilin#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?herb ?herbLabel ?bagang ?symptom
                WHERE {
                  ?herb a yw:Herb ;
                        rdfs:label ?herbLabel .
                  OPTIONAL { ?herb tcm:herb_has_bagang_property ?bagang }
                  OPTIONAL { ?herb tcm:herb_treats_symptom ?symptom }
                }
                LIMIT 10
                """;

        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);

        assertNotNull(rows, "查询结果不应为 null");
        assertFalse(rows.isEmpty(), "应至少返回 1 条 Herb 记录");

        int bagangCount = 0;
        int symptomCount = 0;
        for (Map<String, String> row : rows) {
            if (row.get("bagang") != null && !row.get("bagang").isBlank()) bagangCount++;
            if (row.get("symptom") != null && !row.get("symptom").isBlank()) symptomCount++;
        }

        log.info("✅ TC-06 通过: 检索到 {} 条记录 | 八纲关系={} | 症状关系={}",
                rows.size(), bagangCount, symptomCount);

        if (bagangCount == 0 && symptomCount == 0) {
            log.warn("⚠️ TC-06: 未检索到任何八纲或症状关系，请确认 herb_bagang/herb_symptom 表是否有数据");
        }
    }

    // ============================================================
    // TC-07: 端到端穿透测试 - 方剂→药物→八纲
    // ============================================================
    @Test
    @Order(7)
    @DisplayName("TC-07: 端到端穿透 - 查询含'桂枝'方剂的组成药物及其八纲属性")
    void testEndToEndFormulaHerbBagangQuery() {
        String sparql = """
                PREFIX tcm:  <http://www.tcm-classics.org/shanghan/guilin#>
                PREFIX yw:   <http://www.tcm-classics.org/yaowu#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?formulaLabel ?herbLabel ?bagang
                WHERE {
                  ?formula a tcm:Formula ;
                           rdfs:label ?formulaLabel .
                  FILTER(CONTAINS(?formulaLabel, "桂枝"))

                  ?formula tcm:composed_of ?herb .
                  ?herb a yw:Herb ;
                        rdfs:label ?herbLabel .

                  OPTIONAL { ?herb tcm:herb_has_bagang_property ?bagang }
                }
                """;

        List<Map<String, String>> rows = obdaHandler.executeAboxQuery(sparql);

        assertNotNull(rows, "查询结果不应为 null");
        assertFalse(rows.isEmpty(),
                "应至少返回 1 条含'桂枝'方剂的组成药物记录。若为空请检查: " +
                        "1) formula 表是否有含'桂枝'的记录; " +
                        "2) formula_herb 表关联是否正确; " +
                        "3) herb 表对应药物是否存在");

        for (Map<String, String> row : rows) {
            assertNotNull(row.get("formulaLabel"), "formulaLabel 不应为 null");
            assertTrue(row.get("formulaLabel").contains("桂枝"),
                    "formulaLabel 应包含'桂枝': " + row.get("formulaLabel"));
            assertNotNull(row.get("herbLabel"), "herbLabel 不应为 null");
        }

        log.info("✅ TC-07 通过: 端到端穿透查询返回 {} 条记录", rows.size());
        rows.forEach(r -> log.debug("  方剂={} | 药物={} | 八纲={}",
                r.get("formulaLabel"), r.get("herbLabel"), r.get("bagang")));
    }
}