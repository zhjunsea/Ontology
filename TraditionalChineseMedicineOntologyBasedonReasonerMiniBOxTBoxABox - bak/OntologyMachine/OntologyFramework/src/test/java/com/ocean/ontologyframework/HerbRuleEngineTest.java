package com.ocean.ontologyframework;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 方后注加减规则引擎单元测试。
 *
 * <p>覆盖「有母方证、症状超出该方证标准证候时，用该方证的方后注加减法派生出新方」这一能力：
 * 从 rules.owl 加载规则 → 解析 IF/THEN → 对患者症状求值 → 作用于母方组成 → 输出派生方。
 *
 * <p>本测试不依赖 Spring / Camunda / Ontop / MySQL，可独立运行。
 */
class HerbRuleEngineTest {

    private static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";

    private static final String RULES_OWL =
            "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/fangzheng/rules.owl";

    private static TCMOntologyJobWorker worker;
    private static Map<String, Integer> ruleIndex;

    @BeforeAll
    static void setUp() {
        Path rules = Paths.get(RULES_OWL);
        assumeTrue(Files.isRegularFile(rules), "rules.owl 不存在，跳过规则引擎测试: " + RULES_OWL);

        worker = new TCMOntologyJobWorker();
        // mainOntologyPath 指向 ontology/tcm-all.owl，loadHerbRules 会据此推导 fangzheng/rules.owl
        String mainPath = rules.getParent().getParent().resolve("tcm-all.owl").toString();
        ruleIndex = worker.loadHerbRulesForTest(mainPath);
    }

    @Test
    @DisplayName("rules.owl 全部规则可加载且无解析失败")
    void loadsAllRules() {
        assertThat(ruleIndex).isNotEmpty();
        int total = ruleIndex.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(total).isEqualTo(84);
        assertThat(ruleIndex).containsKeys(
                "Xiaochaihutangzheng", "Lizhongtangzheng", "Zhenwutangzheng",
                "Sinisanzheng", "Tongmaisinitangzheng", "Dachaihutangzheng",
                "Chaihujialonggumulitangzheng");
    }

    @Test
    @DisplayName("小柴胡汤证 + 咳 → 去人参大枣生姜，加五味子干姜")
    void xiaochaihuWithCough() {
        Map<String, Object> r = worker.deriveFormulaForTest(
                "Xiaochaihutangzheng",
                Set.of("Kesou"),
                List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang"));

        assertThat(r.get("derived")).isEqualTo(true);
        assertThat(cn(r, "removedCn")).containsExactlyInAnyOrder("Renshen", "Dazao", "Shengjiang");
        assertThat(cn(r, "addedCn")).containsExactlyInAnyOrder("Wuweizi", "Ganjiang");
        assertThat(cn(r, "herbsCn")).containsExactlyInAnyOrder(
                "Chaihu", "Huangqin", "Banxia", "Gancao", "Wuweizi", "Ganjiang");
        assertThat(sources(r)).anyMatch(s -> s.contains("96条方后注"));
    }

    @Test
    @DisplayName("小柴胡汤证 + 渴 → 去半夏，人参加量，加栝蒌根（剂量调整不重复入药）")
    void xiaochaihuWithThirst() {
        Map<String, Object> r = worker.deriveFormulaForTest(
                "Xiaochaihutangzheng",
                Set.of("Kouke"),
                List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang"));

        assertThat(r.get("derived")).isEqualTo(true);
        assertThat(cn(r, "removedCn")).containsExactly("Banxia");
        assertThat(cn(r, "addedCn")).containsExactly("Gualougen");
        // 人参本有 → 记为剂量调整，不重复加入组成
        assertThat(cn(r, "dosageChanges")).hasSize(1);
        assertThat(cn(r, "herbsCn")).contains("Renshen").doesNotContain("Banxia").contains("Gualougen");
    }

    @Test
    @DisplayName("OR 组条件：(Dabianying OR Xiali) 命中任一即成立")
    void orGroupCondition() {
        List<String> mother = List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang");

        Map<String, Object> hit1 = worker.deriveFormulaForTest(
                "Dachaihutangzheng", Set.of("Xinxiaji", "Yuyuweifan", "Dabianying"), mother);
        assertThat(hit1.get("derived")).isEqualTo(true);

        Map<String, Object> hit2 = worker.deriveFormulaForTest(
                "Dachaihutangzheng", Set.of("Xinxiaji", "Yuyuweifan", "Xiali"), mother);
        assertThat(hit2.get("derived")).isEqualTo(true);

        Map<String, Object> miss = worker.deriveFormulaForTest(
                "Dachaihutangzheng", Set.of("Xinxiaji", "Yuyuweifan"), mother);
        assertThat(miss.get("derived")).isEqualTo(false);
        assertThat(cn(miss, "herbsCn")).isEqualTo(mother);
    }

    @Test
    @DisplayName("多药加味 + 去药：柴胡加龙骨牡蛎汤证")
    void chaihuLongguMuli() {
        Map<String, Object> r = worker.deriveFormulaForTest(
                "Chaihujialonggumulitangzheng",
                Set.of("Xiongman", "Fanzao", "Xiaobianbuli", "Zhanyu"),
                List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang"));

        assertThat(r.get("derived")).isEqualTo(true);
        assertThat(cn(r, "removedCn")).containsExactly("Gancao");
        assertThat(cn(r, "addedCn")).containsExactlyInAnyOrder(
                "Longgu", "Muli", "Guizhi", "Fuling", "Dahuang", "Qiandan");
    }

    @Test
    @DisplayName("retain 语义：真武汤证 + 小便不利 → 保留茯苓（不新增）")
    void retainSemantics() {
        Map<String, Object> r = worker.deriveFormulaForTest(
                "Zhenwutangzheng",
                Set.of("Xiaobianbuli"),
                List.of("Fuling", "Shaoyao", "Baizhu", "Shengjiang", "Fuzi"));

        assertThat(cn(r, "herbsCn")).contains("Fuling");
        assertThat(cn(r, "addedCn")).doesNotContain("Fuling");
    }

    @Test
    @DisplayName("条件不满足 / 母方证无规则 → 不派生，组成保持原样")
    void noDerivation() {
        List<String> mother = List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang");

        Map<String, Object> noCond = worker.deriveFormulaForTest(
                "Xiaochaihutangzheng", Set.of("Touyun"), mother);
        assertThat(noCond.get("derived")).isEqualTo(false);
        assertThat(cn(noCond, "herbsCn")).isEqualTo(mother);

        // 桂枝汤证本身未挂方后注规则
        Map<String, Object> noRule = worker.deriveFormulaForTest(
                "Guizhitangzheng", Set.of("Xiangqiang"),
                List.of("Guizhi", "Shaoyao", "Shengjiang", "Dazao", "Gancao"));
        assertThat(noRule.get("derived")).isEqualTo(false);
    }

    @SuppressWarnings("unchecked")
    private static List<String> cn(Map<String, Object> r, String key) {
        return (List<String>) r.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<String> sources(Map<String, Object> r) {
        return (List<String>) r.get("ruleSources");
    }
}
