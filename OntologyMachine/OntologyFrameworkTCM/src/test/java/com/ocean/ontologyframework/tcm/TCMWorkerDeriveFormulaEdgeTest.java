package com.ocean.ontologyframework.tcm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TCMOntologyJobWorker#deriveFormula}（方后注字符串规则引擎 / SWRL 回退路径）的**边界分支**离线单测。
 *
 * <p>与 {@code HerbRuleEngineTest} 互补：后者覆盖 adds/removes/dosage/retain/OR 组等主路径；
 * 本类补齐边界分支——空患者、无规则方证、去「不在母方之药」、retain「母方缺失时补入」等，
 * 不依赖 Spring / Ontop / MySQL / Camunda（经公开测试钩子 {@code loadHerbRulesForTest} +
 * {@code deriveFormulaForTest} 驱动）。
 */
@DisplayName("TCMOntologyJobWorker.deriveFormula 边界分支（空患者/无规则/去缺失药/retain补入）")
public class TCMWorkerDeriveFormulaEdgeTest {

    private static TCMOntologyJobWorker worker;

    @BeforeAll
    static void setUp() {
        String dir = JingfangTestSupport.ontologyDir().toString();
        String mainPath = Paths.get(dir, "tcm-all.owl").toString();
        worker = new TCMOntologyJobWorker();
        worker.loadHerbRulesForTest(mainPath); // 加载 fangzheng/rules.owl（离线，纯 XML 解析）
    }

    @SuppressWarnings("unchecked")
    private static List<String> list(Object v) {
        return v == null ? List.of() : (List<String>) v;
    }

    @Test
    @DisplayName("空患者症状 → 不派生，母方组成原样（覆盖 patientFrags.isEmpty 守卫）")
    void emptyPatient() {
        List<String> mother = List.of("Chaihu", "Huangqin");
        Map<String, Object> r = worker.deriveFormulaForTest(
                "Xiaochaihutangzheng", Collections.emptySet(), mother);
        assertEquals(false, r.get("derived"));
        assertEquals(mother, list(r.get("herbsCn")));
    }

    @Test
    @DisplayName("无规则方证（rules 为 null）→ 不派生")
    void noRuleFangzheng() {
        Map<String, Object> r = worker.deriveFormulaForTest(
                "NoSuchFangzhengFragment", Set.of("Kesou"), List.of("Chaihu"));
        assertEquals(false, r.get("derived"));
    }

    @Test
    @DisplayName("去「不在母方之药」为空操作 + 加味命中（覆盖 removes idx<0 与 adds idx<0）")
    void removeAbsentAddPresent() {
        // 小柴胡汤证 + 咳：去人参/大枣/生姜（母方未含 → 空操作），加五味子/干姜
        List<String> mother = List.of("Chaihu", "Huangqin", "Banxia", "Gancao");
        Map<String, Object> r = worker.deriveFormulaForTest(
                "Xiaochaihutangzheng", Set.of("Kesou"), mother);
        assertEquals(true, r.get("derived"));
        assertTrue(list(r.get("addedCn")).contains("Wuweizi"));
        assertTrue(list(r.get("removedCn")).isEmpty()); // 母方本无被去之药
    }

    @Test
    @DisplayName("retain：母方缺失该药时补入（覆盖 retain 的 indexOfHerb<0 分支）")
    void retainAddsWhenAbsent() {
        // 真武汤证 + 小便不利：规则 retain 茯苓；母方不含茯苓 → 应补入
        List<String> mother = List.of("Shaoyao", "Baizhu");
        Map<String, Object> r = worker.deriveFormulaForTest(
                "Zhenwutangzheng", Set.of("Xiaobianbuli"), mother);
        assertTrue(list(r.get("herbsCn")).contains("Fuling"));
    }
}
