package com.ocean.ontologyframework.tcm;

import com.ocean.ontologyframework.TCMOntologyJobWorker;
import com.ocean.ontologyframework.tcm.app.SymptomCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 方后注加减规则引擎单元测试。
 *
 * <p>覆盖「有母方证、症状超出该方证标准证候时，用该方证的方后注加减法派生出新方」这一能力：
 * 从 rules.owl 加载规则 → 解析 IF/THEN → 对患者症状求值 → 作用于母方组成 → 输出派生方。
 *
 * <p>本测试不依赖 Spring / Camunda / Ontop / MySQL，可独立运行。
 *
 * <p><b>输出</b>：每个用例都会把「症状 → 命中规则原文 → 出处 → 去药/加药/剂量调整 → 最终组成」
 * 打印到控制台；{@link #derivationReport()} 还会把完整报告写入
 * {@code target/herb-rule-report.txt}，可直接查看加减药是否成功、以及为什么加这些药。
 *
 * <p><b>中文名来源</b>：生产环境的药名中文由 OBDA 查询填充（{@code yaowuLabelMap}），离线测试无
 * Ontop/MySQL，故本测试自行从 {@code tcm-yaowu-abox.owl} 解析药名，从
 * {@code SymptomCatalog} 解析症状名，从 {@code ontology/fangzheng/*.owl} 解析方证名。
 *
 * <p><b>可见性</b>：本类声明为 {@code public} 以便被聚合套件 {@link AllFangzhengSuiteTest}
 * 通过 {@code HerbRuleEngineTest.class} 引用（JUnit 5 的 {@code @SelectClasses} 需要类可见）。
 * 测试方法保持包私有即可，JUnit 5 允许。
 */
public class HerbRuleEngineTest {

    private static final String BASE_NS = "http://www.tcm-classics.org/jingfang#";

    private static final String ONTOLOGY_DIR =
            "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";

    private static final String RULES_OWL = ONTOLOGY_DIR + "/fangzheng/rules.owl";

    /** 报告落盘位置（相对模块目录，surefire 的工作目录即模块 basedir）。 */
    private static final Path REPORT_FILE = Paths.get("target", "herb-rule-report.txt");

    private static final String LINE = "=".repeat(88);
    private static final String THIN = "-".repeat(88);

    /** ABox 中症状/脉象/舌象个体带 {@code _instance} 后缀，药名个体不带。 */
    private static final String INSTANCE_SUFFIX = "_instance";

    private static TCMOntologyJobWorker worker;
    private static Map<String, Integer> ruleIndex;

    /** 症状/脉象/舌象目录：fragment → 中文 label。 */
    private static SymptomCatalog catalog;

    /** 药名 fragment → 中文名（从 tcm-yaowu-abox.owl 解析）。 */
    private static final Map<String, String> HERB_LABELS = new LinkedHashMap<>();

    /** 方证 fragment → 中文名（从 ontology/fangzheng/*.owl 的 rdfs:label 解析）。 */
    private static final Map<String, String> FANGZHENG_LABELS = new LinkedHashMap<>();

    @BeforeAll
    static void setUp() {
        Path rules = Paths.get(RULES_OWL);
        assumeTrue(Files.isRegularFile(rules), "rules.owl 不存在，跳过规则引擎测试: " + RULES_OWL);

        worker = new TCMOntologyJobWorker();
        // mainOntologyPath 指向 ontology/tcm-all.owl，loadHerbRules 会据此推导 fangzheng/rules.owl
        String mainPath = rules.getParent().getParent().resolve("tcm-all.owl").toString();
        ruleIndex = worker.loadHerbRulesForTest(mainPath);

        catalog = new SymptomCatalog(ONTOLOGY_DIR);
        loadHerbLabels(Paths.get(ONTOLOGY_DIR, "tcm-yaowu-abox.owl"));
        loadFangzhengLabels(rules.getParent());
    }

    // ============================================================
    // 规则加载
    // ============================================================

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

        StringBuilder sb = new StringBuilder();
        sb.append(LINE).append('\n');
        sb.append("【规则加载】rules.owl 共 ").append(ruleIndex.size()).append(" 个方证 / ")
                .append(total).append(" 条方后注加减规则\n");
        sb.append(THIN).append('\n');
        ruleIndex.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> sb.append(String.format(Locale.ROOT, "  %-40s %2d 条  %s%n",
                        e.getKey(), e.getValue(), fzLabel(e.getKey()))));
        sb.append(LINE).append('\n');
        out(sb.toString());
    }

    // ============================================================
    // 派生用例
    // ============================================================

    @Test
    @DisplayName("小柴胡汤证 + 咳 → 去人参大枣生姜，加五味子干姜")
    void xiaochaihuWithCough() {
        List<String> mother = List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang");
        Set<String> symptoms = ordered("Kesou");

        Map<String, Object> r = worker.deriveFormulaForTest("Xiaochaihutangzheng", symptoms, mother);

        assertThat(r.get("derived")).isEqualTo(true);
        assertThat(cn(r, "removedCn")).containsExactlyInAnyOrder("Renshen", "Dazao", "Shengjiang");
        assertThat(cn(r, "addedCn")).containsExactlyInAnyOrder("Wuweizi", "Ganjiang");
        assertThat(cn(r, "herbsCn")).containsExactlyInAnyOrder(
                "Chaihu", "Huangqin", "Banxia", "Gancao", "Wuweizi", "Ganjiang");
        assertThat(sources(r)).anyMatch(s -> s.contains("96条方后注"));

        printDerivation("小柴胡汤证 + 咳嗽（方后注「若咳者」）",
                "Xiaochaihutangzheng", mother, symptoms, r, null);
    }

    @Test
    @DisplayName("小柴胡汤证 + 渴 → 去半夏，人参加量，加栝蒌根（剂量调整不重复入药）")
    void xiaochaihuWithThirst() {
        List<String> mother = List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang");
        Set<String> symptoms = ordered("Kouke");

        Map<String, Object> r = worker.deriveFormulaForTest("Xiaochaihutangzheng", symptoms, mother);

        assertThat(r.get("derived")).isEqualTo(true);
        assertThat(cn(r, "removedCn")).containsExactly("Banxia");
        assertThat(cn(r, "addedCn")).containsExactly("Gualougen");
        // 人参本有 → 记为剂量调整，不重复加入组成
        assertThat(cn(r, "dosageChanges")).hasSize(1);
        assertThat(cn(r, "herbsCn")).contains("Renshen").doesNotContain("Banxia").contains("Gualougen");

        printDerivation("小柴胡汤证 + 口渴（方后注「若渴」）",
                "Xiaochaihutangzheng", mother, symptoms, r, null);
    }

    @Test
    @DisplayName("OR 组条件：(Dabianying OR Xiali) 命中任一即成立")
    void orGroupCondition() {
        List<String> mother = List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang");

        Set<String> s1 = ordered("Xinxiaji", "Yuyuweifan", "Dabianying");
        Set<String> s2 = ordered("Xinxiaji", "Yuyuweifan", "Xiali");
        Set<String> s3 = ordered("Xinxiaji", "Yuyuweifan");

        Map<String, Object> hit1 = worker.deriveFormulaForTest("Dachaihutangzheng", s1, mother);
        assertThat(hit1.get("derived")).isEqualTo(true);

        Map<String, Object> hit2 = worker.deriveFormulaForTest("Dachaihutangzheng", s2, mother);
        assertThat(hit2.get("derived")).isEqualTo(true);

        Map<String, Object> miss = worker.deriveFormulaForTest("Dachaihutangzheng", s3, mother);
        assertThat(miss.get("derived")).isEqualTo(false);
        assertThat(cn(miss, "herbsCn")).isEqualTo(mother);

        printDerivation("大柴胡汤证 + 大便硬（OR 组命中左分支 Dabianying）",
                "Dachaihutangzheng", mother, s1, hit1, null);
        printDerivation("大柴胡汤证 + 下利（OR 组命中右分支 Xiali）",
                "Dachaihutangzheng", mother, s2, hit2, null);
        printDerivation("大柴胡汤证 + 仅心下急/郁郁微烦（OR 组两支均不满足）",
                "Dachaihutangzheng", mother, s3, miss, "条件不满足 → 不派生，组成保持母方原样");
    }

    @Test
    @DisplayName("多药加味 + 去药：柴胡加龙骨牡蛎汤证")
    void chaihuLongguMuli() {
        List<String> mother = List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang");
        Set<String> symptoms = ordered("Xiongman", "Fanzao", "Xiaobianbuli", "Zhanyu");

        Map<String, Object> r = worker.deriveFormulaForTest(
                "Chaihujialonggumulitangzheng", symptoms, mother);

        assertThat(r.get("derived")).isEqualTo(true);
        assertThat(cn(r, "removedCn")).containsExactly("Gancao");
        assertThat(cn(r, "addedCn")).containsExactlyInAnyOrder(
                "Longgu", "Muli", "Guizhi", "Fuling", "Dahuang", "Qiandan");

        printDerivation("柴胡加龙骨牡蛎汤证 + 胸满/烦躁/小便不利/谵语",
                "Chaihujialonggumulitangzheng", mother, symptoms, r, null);
    }

    @Test
    @DisplayName("retain 语义：真武汤证 + 小便不利 → 保留茯苓（不新增）")
    void retainSemantics() {
        List<String> mother = List.of("Fuling", "Shaoyao", "Baizhu", "Shengjiang", "Fuzi");
        Set<String> symptoms = ordered("Xiaobianbuli");

        Map<String, Object> r = worker.deriveFormulaForTest("Zhenwutangzheng", symptoms, mother);

        assertThat(cn(r, "herbsCn")).contains("Fuling");
        assertThat(cn(r, "addedCn")).doesNotContain("Fuling");

        printDerivation("真武汤证 + 小便不利（方后注「若小便利者，去茯苓」）",
                "Zhenwutangzheng", mother, symptoms, r,
                "规则动作为 retain 茯苓，而母方本就有茯苓 → 组成无变化 → 不标记为派生（符合预期）");
    }

    @Test
    @DisplayName("条件不满足 / 母方证无规则 → 不派生，组成保持原样")
    void noDerivation() {
        List<String> mother = List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang");

        Set<String> noCondSym = ordered("Xiangqiang");
        Map<String, Object> noCond = worker.deriveFormulaForTest(
                "Xiaochaihutangzheng", noCondSym, mother);
        assertThat(noCond.get("derived")).isEqualTo(false);
        assertThat(cn(noCond, "herbsCn")).isEqualTo(mother);

        // 桂枝汤证本身未挂方后注规则
        List<String> guizhiMother = List.of("Guizhi", "Shaoyao", "Shengjiang", "Dazao", "Gancao");
        Set<String> guizhiSym = ordered("Xiangqiang");
        Map<String, Object> noRule = worker.deriveFormulaForTest(
                "Guizhitangzheng", guizhiSym, guizhiMother);
        assertThat(noRule.get("derived")).isEqualTo(false);

        printDerivation("小柴胡汤证 + 项强（母方证有规则，但条件不满足）",
                "Xiaochaihutangzheng", mother, noCondSym, noCond,
                "该方证共 7 条方后注规则，但无一条的条件被满足 → 不派生");
        printDerivation("桂枝汤证 + 项强（该方证未挂方后注规则）",
                "Guizhitangzheng", guizhiMother, guizhiSym, noRule,
                "桂枝汤证在 rules.owl 中无规则（其加减已固化为具名方证）→ 不派生");
    }

    // ============================================================
    // 汇总报告（落盘）
    // ============================================================

    @Test
    @DisplayName("生成加减药派生报告：症状 → 命中规则 → 加/去药 → 依据（落盘 target/herb-rule-report.txt）")
    void derivationReport() throws IOException {
        List<Case> cases = List.of(
                new Case("小柴胡汤证 + 咳嗽", "Xiaochaihutangzheng",
                        List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang"),
                        List.of("Kesou"), null),
                new Case("小柴胡汤证 + 口渴", "Xiaochaihutangzheng",
                        List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang"),
                        List.of("Kouke"), null),
                new Case("大柴胡汤证 + 心下急/郁郁微烦/大便硬", "Dachaihutangzheng",
                        List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang"),
                        List.of("Xinxiaji", "Yuyuweifan", "Dabianying"), null),
                new Case("柴胡加龙骨牡蛎汤证 + 胸满/烦躁/小便不利/谵语", "Chaihujialonggumulitangzheng",
                        List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang"),
                        List.of("Xiongman", "Fanzao", "Xiaobianbuli", "Zhanyu"), null),
                new Case("真武汤证 + 小便不利", "Zhenwutangzheng",
                        List.of("Fuling", "Shaoyao", "Baizhu", "Shengjiang", "Fuzi"),
                        List.of("Xiaobianbuli"),
                        "规则为 retain 茯苓，母方本有 → 无变化，不派生"),
                new Case("小柴胡汤证 + 项强（反例：条件不满足）", "Xiaochaihutangzheng",
                        List.of("Chaihu", "Huangqin", "Banxia", "Renshen", "Gancao", "Dazao", "Shengjiang"),
                        List.of("Xiangqiang"), "7 条规则的条件均未被满足"),
                new Case("桂枝汤证 + 项强（反例：该方证无方后注规则）", "Guizhitangzheng",
                        List.of("Guizhi", "Shaoyao", "Shengjiang", "Dazao", "Gancao"),
                        List.of("Xiangqiang"), "该方证在 rules.owl 中无规则"));

        StringBuilder sb = new StringBuilder();
        sb.append("中医经方 · 方后注加减药派生报告\n");
        sb.append("规则来源：").append(RULES_OWL).append('\n');
        sb.append("规则规模：").append(ruleIndex.size()).append(" 个方证 / ")
                .append(ruleIndex.values().stream().mapToInt(Integer::intValue).sum()).append(" 条规则\n\n");

        int derivedCount = 0;
        for (Case c : cases) {
            Set<String> symptoms = ordered(c.symptoms().toArray(new String[0]));
            Map<String, Object> r = worker.deriveFormulaForTest(c.fz(), symptoms, c.mother());
            if (Boolean.TRUE.equals(r.get("derived"))) derivedCount++;
            sb.append(render(c.title(), c.fz(), c.mother(), symptoms, r, c.remark()));
        }

        sb.append(LINE).append('\n');
        sb.append("汇总：").append(cases.size()).append(" 个用例，其中 ")
                .append(derivedCount).append(" 个成功派生新方（加/去药生效），")
                .append(cases.size() - derivedCount).append(" 个未派生（符合预期）\n");
        sb.append(LINE).append('\n');

        String report = sb.toString();
        out(report);

        Files.createDirectories(REPORT_FILE.toAbsolutePath().getParent());
        Files.writeString(REPORT_FILE, report, StandardCharsets.UTF_8);
        out("报告已写入：" + REPORT_FILE.toAbsolutePath());

        assertThat(derivedCount).as("应有 4 个用例成功派生（真武汤 retain 无变化，不计）").isEqualTo(4);
    }

    // ============================================================
    // 报告渲染
    // ============================================================

    /** 打印单个用例的派生报告。 */
    private static void printDerivation(String title, String fzFragment, List<String> mother,
                                        Set<String> symptoms, Map<String, Object> r, String remark) {
        out(render(title, fzFragment, mother, symptoms, r, remark));
    }

    /** 渲染单个用例：症状 → 命中规则原文 → 出处 → 去药/加药/剂量调整 → 最终组成。 */
    private static String render(String title, String fzFragment, List<String> mother,
                                 Set<String> symptoms, Map<String, Object> r, String remark) {
        boolean derived = Boolean.TRUE.equals(r.get("derived"));
        List<String> added = cn(r, "addedCn");
        List<String> removed = cn(r, "removedCn");
        List<String> dosage = cn(r, "dosageChanges");
        List<String> rules = cn(r, "appliedRules");
        List<String> srcs = cn(r, "ruleSources");
        List<String> finalHerbs = cn(r, "herbsCn");
        int declared = ruleIndex.getOrDefault(fzFragment, 0);

        StringBuilder sb = new StringBuilder();
        sb.append(LINE).append('\n');
        sb.append("【用例】").append(title).append('\n');
        sb.append(THIN).append('\n');
        sb.append("母方证　：").append(fzLabel(fzFragment)).append("（").append(fzFragment).append("）\n");
        sb.append("母方组成：").append(joinHerbs(mother)).append("（").append(mother.size()).append(" 味）\n");
        sb.append("患者症状：").append(joinSymptoms(symptoms)).append('\n');
        sb.append(THIN).append('\n');

        sb.append("命中规则：").append(rules.size()).append(" 条");
        if (rules.isEmpty()) {
            sb.append(declared > 0
                    ? "（该方证共 " + declared + " 条规则，但条件均未满足或未产生组成变化）"
                    : "（该方证未挂方后注加减规则）");
        }
        sb.append('\n');
        for (int i = 0; i < rules.size(); i++) {
            sb.append("  [").append(i + 1).append("] ").append(rules.get(i)).append('\n');
            if (i < srcs.size()) sb.append("      出处：").append(srcs.get(i)).append('\n');
        }
        sb.append(THIN).append('\n');

        sb.append("去药　　：").append(removed.isEmpty() ? "无" : joinHerbs(removed)).append('\n');
        sb.append("加药　　：").append(added.isEmpty() ? "无" : joinHerbs(added)).append('\n');
        sb.append("剂量调整：").append(dosage.isEmpty() ? "无" : joinDosage(dosage)).append('\n');
        sb.append(THIN).append('\n');

        sb.append("派生结果：").append(derived ? "已派生新方（加/去药生效）" : "未派生（保持母方原样）").append('\n');
        sb.append("最终组成：").append(joinHerbs(finalHerbs)).append("（").append(finalHerbs.size()).append(" 味）\n");
        if (derived) {
            sb.append("派生方名：").append(derivedName(fzLabel(fzFragment), removed, added, dosage)).append('\n');
        }
        if (remark != null && !remark.isBlank()) {
            sb.append("说明　　：").append(remark).append('\n');
        }
        sb.append(LINE).append('\n');
        return sb.toString();
    }

    /**
     * 派生方名：母方名 + 去X + 加Y（与 {@code TCMOntologyJobWorker#buildDerivedName} 语义一致）。
     * 母方名由方证名去掉末字「证」得到（小柴胡汤证 → 小柴胡汤）。
     */
    private static String derivedName(String fzCn, List<String> removed, List<String> added,
                                      List<String> dosage) {
        String base = fzCn.endsWith("证") ? fzCn.substring(0, fzCn.length() - 1) : fzCn;
        StringBuilder sb = new StringBuilder(base);
        if (!removed.isEmpty()) sb.append("去").append(joinHerbs(removed));
        if (!added.isEmpty()) sb.append("加").append(joinHerbs(added));
        if (removed.isEmpty() && added.isEmpty() && !dosage.isEmpty()) sb.append("加减");
        return sb.toString();
    }

    // ============================================================
    // 中文名解析
    // ============================================================

    /** 症状/脉象/舌象 fragment → 中文 label（ABox 个体带 {@code _instance} 后缀）；查不到回退 fragment。 */
    private static String label(String fragment) {
        if (catalog == null) return fragment;
        return catalog.byFragment(fragment + INSTANCE_SUFFIX)
                .or(() -> catalog.byFragment(fragment))
                .map(SymptomCatalog.Entry::getLabel)
                .orElse(fragment);
    }

    /** 药名 fragment → 中文名；查不到回退 fragment。 */
    private static String herbCn(String fragment) {
        return HERB_LABELS.getOrDefault(fragment, fragment);
    }

    /** 方证 fragment → 中文名；查不到回退 fragment。 */
    private static String fzLabel(String fragment) {
        return FANGZHENG_LABELS.getOrDefault(fragment, fragment);
    }

    private static String joinHerbs(List<String> herbs) {
        if (herbs.isEmpty()) return "（无）";
        List<String> cn = new ArrayList<>(herbs.size());
        for (String h : herbs) cn.add(herbCn(h));
        return String.join("、", cn);
    }

    /** 剂量调整项形如 {@code Renshen：加至四两半}，把药名换成中文。 */
    private static String joinDosage(List<String> dosage) {
        List<String> cn = new ArrayList<>(dosage.size());
        for (String d : dosage) {
            int sep = d.indexOf('：');
            cn.add(sep > 0 ? herbCn(d.substring(0, sep)) + d.substring(sep) : d);
        }
        return String.join("；", cn);
    }

    private static String joinSymptoms(Set<String> symptoms) {
        List<String> parts = new ArrayList<>(symptoms.size());
        for (String s : symptoms) parts.add(label(s) + "（" + s + "）");
        return String.join("、", parts);
    }

    /** 从 tcm-yaowu-abox.owl 解析「药名 fragment → rdfs:label」。 */
    private static void loadHerbLabels(Path yaowuAbox) {
        if (!Files.isRegularFile(yaowuAbox)) return;
        try {
            String xml = Files.readString(yaowuAbox, StandardCharsets.UTF_8);
            Matcher m = INDIVIDUAL.matcher(xml);
            while (m.find()) {
                String frag = m.group(1).trim();
                Matcher lm = LABEL.matcher(m.group(2));
                if (!frag.isEmpty() && lm.find()) {
                    HERB_LABELS.putIfAbsent(frag, lm.group(1).trim());
                }
            }
        } catch (IOException e) {
            // 中文名仅用于展示，解析失败时回退为 fragment，不影响断言
        }
    }

    /** 从 ontology/fangzheng/*.owl 解析「方证 fragment → rdfs:label」。 */
    private static void loadFangzhengLabels(Path fangzhengDir) {
        if (!Files.isDirectory(fangzhengDir)) return;
        try (var stream = Files.list(fangzhengDir)) {
            for (Path f : stream.filter(p -> p.getFileName().toString().endsWith(".owl")).toList()) {
                String xml = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = OWL_CLASS.matcher(xml);
                while (m.find()) {
                    Matcher lm = LABEL.matcher(m.group(2));
                    if (lm.find()) {
                        FANGZHENG_LABELS.putIfAbsent(m.group(1), lm.group(1).trim());
                    }
                }
            }
        } catch (IOException e) {
            // 同上
        }
    }

    private static final Pattern INDIVIDUAL = Pattern.compile(
            "<owl:NamedIndividual\\s+rdf:about=\"#([^\"]+)\"\\s*>(.*?)</owl:NamedIndividual>",
            Pattern.DOTALL);

    private static final Pattern OWL_CLASS = Pattern.compile(
            "<owl:Class\\s+rdf:about=\"#([^\"]+)\"\\s*>(.*?)</owl:Class>", Pattern.DOTALL);

    private static final Pattern LABEL = Pattern.compile(
            "<rdfs:label\\s+xml:lang=\"zh\">([^<]*)</rdfs:label>");

    /** 保持症状顺序的 Set（便于报告输出稳定）。 */
    private static Set<String> ordered(String... frags) {
        return new LinkedHashSet<>(List.of(frags));
    }

    private static void out(String s) {
        System.out.println(s);
    }

    @SuppressWarnings("unchecked")
    private static List<String> cn(Map<String, Object> r, String key) {
        Object v = r.get(key);
        return v == null ? List.of() : (List<String>) v;
    }

    /** 命中的规则出处。 */
    private static List<String> sources(Map<String, Object> r) {
        return cn(r, "ruleSources");
    }

    /** 报告用例。 */
    private record Case(String title, String fz, List<String> mother, List<String> symptoms, String remark) {
    }
}
