package com.ocean.ontologyframework.tcm.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SKOS 词表（{@code tcm-zhengzhuang_skos.ttl}）结构与映射测试。
 *
 * <p>不依赖推理机 / OBDA / MySQL，仅解析 TTL 文本与 ABox 文件，因此可离线快速运行。
 *
 * <p>守护的不变量：
 * <ol>
 *   <li><b>结构完整</b>：每个 {@code skos:Concept} 都有 prefLabel@zh、notation、
 *       {@code skos:exactMatch}（→ ABox 个体）与 {@code zz:classMatch}（→ TBox 类）。</li>
 *   <li><b>无孤儿概念</b>：不存在「只有 classMatch、没有 exactMatch」的概念
 *       （即 TBox 有类而 ABox 缺个体的「孤儿类」已全部补齐）。</li>
 *   <li><b>表面形式唯一</b>：任一 altLabel / hiddenLabel 只归属一个概念，
 *       保证「一个口语表达 → 唯一规范名」，匹配结果确定。</li>
 *   <li><b>四诊全覆盖</b>：症状 / 脉象 / 舌象 / 腹证四个通道的口语形式均可映射。</li>
 *   <li><b>重复实体已清理</b>：常欲蹈其胸上 / 寒疝绕脐痛 各只保留一个规范个体。</li>
 * </ol>
 */
class SkosAliasTest {

    /** 症状通道概念数（= tcm-zhengzhuang-abox.owl 个体数）。 */
    private static final int ZHENGZHUANG_CONCEPTS = 593;
    /** 四诊其余通道概念数：脉象 68 + 舌象 72 + 腹证 4。 */
    private static final int MLFZ_CONCEPTS = 68 + 72 + 4;

    private static String aboxDir;
    private static Path skosPath;
    private static String ttl;
    private static SymptomCatalog catalog;

    @BeforeAll
    static void setUp() throws Exception {
        aboxDir = readAboxDir();
        skosPath = Paths.get(aboxDir).resolve(SymptomCatalog.SKOS_FILE);
        assertThat(Files.isRegularFile(skosPath)).as("SKOS 词表: " + skosPath).isTrue();
        ttl = Files.readString(skosPath, StandardCharsets.UTF_8);
        catalog = new SymptomCatalog(aboxDir);
    }

    @SuppressWarnings("unchecked")
    static String readAboxDir() {
        try (InputStream is = SkosAliasTest.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml 必须在 classpath 上").isNotNull();
            Map<String, Object> cfg = new Yaml().load(is);
            Map<String, Object> ontology = (Map<String, Object>) cfg.get("ontology");
            String dir = (String) ontology.get("abox-dir");
            if (dir == null || dir.isBlank()) {
                String main = (String) ontology.get("main-path");
                dir = Paths.get(main).getParent().toString();
            }
            return dir;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================
    // 极简 TTL 解析
    // ============================================================

    /** 一个 skos:Concept 的解析结果。 */
    static final class Concept {
        String id;
        String prefLabel;
        String notation;
        String exactMatch;
        String classMatch;
        final List<String> alt = new ArrayList<>();
        final List<String> hidden = new ArrayList<>();

        List<String> surfaces() {
            List<String> s = new ArrayList<>(alt);
            s.addAll(hidden);
            return s;
        }
    }

    private static final Pattern CONCEPT = Pattern.compile(
            "^zzskos:(\\w+) a skos:Concept ;(.*?)(?=^zzskos:\\w+ a skos:Concept ;|\\Z)",
            Pattern.DOTALL | Pattern.MULTILINE);
    private static final Pattern PREF = Pattern.compile("skos:prefLabel\\s+\"([^\"]+)\"@zh");
    private static final Pattern LABELS = Pattern.compile("skos:(altLabel|hiddenLabel)([^;]*);");
    private static final Pattern LABEL_VAL = Pattern.compile("\"([^\"]+)\"@zh");
    private static final Pattern NOTATION = Pattern.compile("skos:notation\\s+\"([^\"]+)\"");
    private static final Pattern EXACT = Pattern.compile("skos:exactMatch\\s+zz:(\\S+?)\\s*;");
    private static final Pattern CLASS_MATCH = Pattern.compile("zz:classMatch\\s+zz:(\\S+?)\\s*\\.");

    static List<Concept> parseConcepts() {
        List<Concept> out = new ArrayList<>();
        Matcher cm = CONCEPT.matcher(ttl);
        while (cm.find()) {
            Concept c = new Concept();
            c.id = cm.group(1);
            String body = cm.group(2);
            Matcher pm = PREF.matcher(body);
            c.prefLabel = pm.find() ? pm.group(1) : null;
            Matcher nm = NOTATION.matcher(body);
            c.notation = nm.find() ? nm.group(1) : null;
            Matcher em = EXACT.matcher(body);
            c.exactMatch = em.find() ? em.group(1) : null;
            Matcher km = CLASS_MATCH.matcher(body);
            c.classMatch = km.find() ? km.group(1) : null;
            Matcher lm = LABELS.matcher(body);
            while (lm.find()) {
                boolean isAlt = "altLabel".equals(lm.group(1));
                Matcher vm = LABEL_VAL.matcher(lm.group(2));
                while (vm.find()) {
                    (isAlt ? c.alt : c.hidden).add(vm.group(1));
                }
            }
            out.add(c);
        }
        return out;
    }

    // ============================================================
    // 1. 词表结构
    // ============================================================

    @Test
    @DisplayName("SKOS 词表：文件头、Ontology 声明与 ConceptScheme 齐备")
    void skosHeaderWellFormed() {
        assertThat(ttl).contains("@prefix skos: <http://www.w3.org/2004/02/skos/core#> .");
        assertThat(ttl).contains("@prefix zzskos: <http://www.tcm-classics.org/skos/zhengzhuang#> .");
        assertThat(ttl).contains("a owl:Ontology");
        assertThat(ttl).contains("a skos:ConceptScheme");
        assertThat(ttl).contains("skos:hasTopConcept");
    }

    @Test
    @DisplayName("概念总数 = 症状 593 + 脉象/舌象/腹证 144 = 737")
    void conceptCount() {
        List<Concept> concepts = parseConcepts();
        assertThat(concepts).hasSize(ZHENGZHUANG_CONCEPTS + MLFZ_CONCEPTS);
        // 与 SymptomCatalog 的目录规模一致（症状概念数 == 症状个体数）
        assertThat(concepts).hasSize(catalog.size());
    }

    @Test
    @DisplayName("每个概念都有 prefLabel@zh / notation / exactMatch / classMatch")
    void everyConceptFullyAnchored() {
        List<Concept> concepts = parseConcepts();
        assertThat(concepts).allSatisfy(c -> {
            assertThat(c.prefLabel).as("概念 %s 的 prefLabel", c.id).isNotBlank();
            assertThat(c.notation).as("概念 %s 的 notation", c.id).isNotBlank();
            assertThat(c.exactMatch).as("概念 %s 的 skos:exactMatch", c.id).isNotBlank();
            assertThat(c.classMatch).as("概念 %s 的 zz:classMatch", c.id).isNotBlank();
        });
    }

    @Test
    @DisplayName("无孤儿概念：不存在「只有 classMatch、无 exactMatch」的概念")
    void noOrphanConcepts() {
        List<Concept> concepts = parseConcepts();
        List<String> orphans = concepts.stream()
                .filter(c -> c.exactMatch == null || c.exactMatch.isBlank())
                .map(c -> c.id + "(" + c.prefLabel + ")")
                .toList();
        assertThat(orphans)
                .as("TBox 有类、ABox 缺个体的孤儿类应已全部补齐 ABox 个体")
                .isEmpty();
    }

    @Test
    @DisplayName("notation 与 exactMatch 一致，且对应 ABox 个体真实存在")
    void notationMatchesExactMatch() {
        List<Concept> concepts = parseConcepts();
        assertThat(concepts).allSatisfy(c ->
                assertThat(c.exactMatch).as("概念 %s 的 exactMatch 应等于 notation", c.id)
                        .isEqualTo(c.notation));
        // exactMatch 指向的个体必须能在目录中按 fragment 找到
        assertThat(concepts).allSatisfy(c ->
                assertThat(catalog.byFragment(c.exactMatch))
                        .as("concept %s 的 exactMatch 个体 %s 应在目录中", c.id, c.exactMatch)
                        .isPresent());
    }

    // ============================================================
    // 2. 唯一性不变量
    // ============================================================

    @Test
    @DisplayName("prefLabel 全局唯一（无重复规范名）")
    void prefLabelsUnique() {
        Map<String, List<String>> byLabel = new LinkedHashMap<>();
        for (Concept c : parseConcepts()) {
            byLabel.computeIfAbsent(c.prefLabel, k -> new ArrayList<>()).add(c.id);
        }
        Map<String, List<String>> dups = new LinkedHashMap<>();
        byLabel.forEach((k, v) -> {
            if (v.size() > 1) dups.put(k, v);
        });
        assertThat(dups).as("重复的 skos:prefLabel").isEmpty();
    }

    @Test
    @DisplayName("表面形式（altLabel / hiddenLabel）全局唯一：一个口语 → 唯一规范名")
    void surfaceFormsUniqueAcrossConcepts() {
        Map<String, List<String>> owners = new LinkedHashMap<>();
        for (Concept c : parseConcepts()) {
            for (String s : c.surfaces()) {
                owners.computeIfAbsent(s, k -> new ArrayList<>()).add(c.id);
            }
        }
        Map<String, List<String>> dups = new LinkedHashMap<>();
        owners.forEach((k, v) -> {
            if (v.size() > 1) dups.put(k, v);
        });
        assertThat(dups).as("跨概念重复的表面形式（会导致匹配歧义）").isEmpty();
    }

    @Test
    @DisplayName("表面形式不得与任何概念的 prefLabel 冲突（prefLabel 优先）")
    void surfacesDoNotShadowOtherPrefLabels() {
        List<Concept> concepts = parseConcepts();
        Set<String> prefLabels = new HashSet<>();
        for (Concept c : concepts) prefLabels.add(c.prefLabel);
        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        for (Concept c : concepts) {
            for (String s : c.surfaces()) {
                if (prefLabels.contains(s) && !s.equals(c.prefLabel)) {
                    conflicts.computeIfAbsent(s, k -> new ArrayList<>()).add(c.id);
                }
            }
        }
        assertThat(conflicts).as("表面形式与其它概念 prefLabel 冲突").isEmpty();
    }

    // ============================================================
    // 3. 四诊通道覆盖
    // ============================================================

    @Test
    @DisplayName("四诊全覆盖：症状 / 脉象 / 舌象 / 腹证 的口语形式均可映射")
    void allFourChannelsCovered() {
        // 症状
        assertThat(catalog.aliasTarget("拉肚子")).isEqualTo("下利");
        assertThat(catalog.aliasTarget("两边肋骨下面胀痛")).isEqualTo("胸胁苦满");
        // 脉象（规则化推导：脉X ↔ X脉）
        assertThat(catalog.aliasTarget("脉弦")).isEqualTo("弦脉");
        assertThat(catalog.aliasTarget("脉细")).isEqualTo("细脉");
        assertThat(catalog.aliasTarget("脉浮紧")).isEqualTo("浮紧脉");
        // 脉象（人工补录）
        assertThat(catalog.aliasTarget("脉搏快")).isEqualTo("数脉");
        assertThat(catalog.aliasTarget("脉搏慢")).isEqualTo("迟脉");
        // 舌象（规则化推导：舌苔X ↔ X苔）
        assertThat(catalog.aliasTarget("舌苔黄")).isEqualTo("黄苔");
        assertThat(catalog.aliasTarget("舌苔厚")).isEqualTo("厚苔");
        // 舌象（人工校订：舌体）
        assertThat(catalog.aliasTarget("舌头红")).isEqualTo("红舌");
        assertThat(catalog.aliasTarget("舌头淡")).isEqualTo("淡白舌");
        assertThat(catalog.aliasTarget("舌边有齿痕")).isEqualTo("齿痕舌");
        // 腹证
        assertThat(catalog.aliasTarget("按之硬")).isEqualTo("按之石硬");
        assertThat(catalog.aliasTarget("按下去软")).isEqualTo("按之濡");
    }

    @Test
    @DisplayName("脉象/舌象/腹证概念均带 exactMatch，且逐通道计数与目录一致")
    void mlfzConceptsAnchoredToCatalog() {
        List<Concept> concepts = parseConcepts();
        // 不靠 fragment 后缀猜类别（Fumai_Yin / Gemia / JixuKouchiMai / ShaoyinMaiHuashu / WeiseMai
        // 等个体名并不以 "mai_instance" 结尾），而是让每个概念经 exactMatch 落到目录项上，
        // 再按目录自报的类别计数比对 —— 这样词表与目录必须逐通道自洽。
        for (SymptomCatalog.Category cat : new SymptomCatalog.Category[]{
                SymptomCatalog.Category.MAIXIANG,
                SymptomCatalog.Category.SHEXIANG,
                SymptomCatalog.Category.FUZHENG}) {
            long n = concepts.stream()
                    .filter(c -> catalog.byFragment(c.exactMatch)
                            .map(e -> e.getCategory() == cat).orElse(false))
                    .count();
            assertThat(n).as("%s 通道的 SKOS 概念数应与目录计数一致", cat.cn())
                    .isEqualTo(catalog.countOf(cat));
        }
        // 四诊其余通道规模：脉象 68 / 舌象 72 / 腹证 4
        assertThat(catalog.countOf(SymptomCatalog.Category.MAIXIANG)).isEqualTo(68);
        assertThat(catalog.countOf(SymptomCatalog.Category.SHEXIANG)).isEqualTo(72);
        assertThat(catalog.countOf(SymptomCatalog.Category.FUZHENG)).isEqualTo(4);
        // 腹证 4 项
        assertThat(catalog.aliasTarget("按之无痛")).isEqualTo("按之不痛");
    }

    // ============================================================
    // 4. 重复实体清理
    // ============================================================

    @Test
    @DisplayName("重复实体已清理：常欲蹈其胸上 / 寒疝绕脐痛 各仅一个规范个体")
    void duplicateEntitiesCleaned() {
        // 常欲蹈其胸上：只保留 Changyudaoqixiongshang_instance（讹误名已删除）
        assertThat(catalog.byFragment("Changyudaoqixiongshang_instance")).isPresent();
        assertThat(catalog.byFragment("Changyudaqixiongshang_instance")).isEmpty();
        assertThat(catalog.byLabel("常欲蹈其胸上")).isPresent()
                .get().extracting(SymptomCatalog.Entry::getFragment)
                .isEqualTo("Changyudaoqixiongshang_instance");

        // 寒疝绕脐痛：个体名由讹误的 Hanshanraogitong_instance 更正为 Hanshanraoqitong_instance
        assertThat(catalog.byFragment("Hanshanraoqitong_instance")).isPresent();
        assertThat(catalog.byFragment("Hanshanraogitong_instance")).isEmpty();
        assertThat(catalog.byLabel("寒疝绕脐痛")).isPresent()
                .get().extracting(SymptomCatalog.Entry::getFragment)
                .isEqualTo("Hanshanraoqitong_instance");

        // 词表中二者各只出现一次
        assertThat(countOccurrences(ttl, "skos:prefLabel \"常欲蹈其胸上\"@zh")).isEqualTo(1);
        assertThat(countOccurrences(ttl, "skos:prefLabel \"寒疝绕脐痛\"@zh")).isEqualTo(1);
    }

    @Test
    @DisplayName("原孤儿类已补齐 ABox 个体，可参与匹配")
    void formerOrphanClassesNowMatchable() {
        String[] bases = {
                "Dabianmijie", "Fumantong", "HanreWanglai", "Jingshenweimi", "Jiuli",
                "Shiwo", "Wanguobuhua", "Xiaobianhuangchi", "Xiaobianqing", "Xiuzuoyoushi"
        };
        for (String b : bases) {
            assertThat(catalog.byFragment(b + "_instance"))
                    .as("孤儿类 %s 应已补建 ABox 个体", b).isPresent();
            assertThat(countOccurrences(ttl, "skos:exactMatch zz:" + b + "_instance"))
                    .as("SKOS 中 %s 应有 exactMatch 锚点", b).isEqualTo(1);
        }
        // 抽查：这些症状现在可被规范名直接命中
        assertThat(catalog.byLabel("大便秘结")).isPresent();
        assertThat(catalog.byLabel("寒热往来")).isPresent();
        assertThat(catalog.byLabel("完谷不化")).isPresent();
    }

    // ============================================================
    // 5. 与 SymptomCatalog 的集成
    // ============================================================

    @Test
    @DisplayName("SymptomCatalog 全部同义词均来自 SKOS（无硬编码兜底）")
    void catalogAliasesAllComeFromSkos() {
        // 规模：SKOS 覆盖后同义词应远超旧硬编码的 ~87 条
        assertThat(catalog.aliases().size()).isGreaterThan(1000);
        assertThat(catalog.surfaceForms().size()).isGreaterThan(1500);

        // 目录中的每一条同义词都必须能在 SKOS 词表中找到对应表面形式
        List<Concept> concepts = parseConcepts();
        Set<String> skosSurfaces = new HashSet<>();
        for (Concept c : concepts) {
            skosSurfaces.add(c.prefLabel);
            skosSurfaces.addAll(c.surfaces());
        }
        List<String> notInSkos = catalog.aliases().keySet().stream()
                .filter(k -> !skosSurfaces.contains(k))
                .toList();
        assertThat(notInSkos)
                .as("目录同义词键应全部来自 SKOS 词表（不应有硬编码残留）")
                .isEmpty();

        // 反向：SKOS 中每个「规范名存在于目录」的表面形式都应被目录采纳
        Map<String, String> aliasMap = catalog.aliases();
        List<String> missing = new ArrayList<>();
        for (Concept c : concepts) {
            if (!catalog.byLabel(c.prefLabel).isPresent()) continue;
            for (String s : c.surfaces()) {
                if (!aliasMap.containsKey(s)) missing.add(s + "→" + c.prefLabel);
            }
        }
        assertThat(missing).as("SKOS 表面形式应全部被目录采纳").isEmpty();
    }

    @Test
    @DisplayName("医理纠正项仍成立：想吐→欲呕、畏寒≠恶寒、低烧→微热")
    void medicalCorrectionsHold() {
        assertThat(catalog.aliasTarget("想吐")).isEqualTo("欲呕");
        assertThat(catalog.aliasTarget("畏寒")).isNotEqualTo("恶寒");
        assertThat(catalog.byLabel("畏寒")).isPresent();
        assertThat(catalog.aliasTarget("低烧")).isEqualTo("微热");
        assertThat(catalog.aliasTarget("高烧")).isEqualTo("大热");
        assertThat(catalog.aliasTarget("小肚子疼")).isEqualTo("少腹痛");
        assertThat(catalog.aliasTarget("手脚发凉")).isEqualTo("手足冷");
    }

    @Test
    @DisplayName("每个同义词目标都指向目录中真实存在的实例个体")
    void aliasTargetsExist() {
        Map<String, String> aliasMap = catalog.aliases();
        assertThat(aliasMap).isNotEmpty();
        Map<String, String> broken = new HashMap<>();
        aliasMap.forEach((alias, target) -> {
            if (!catalog.byLabel(target).isPresent()) broken.put(alias, target);
        });
        assertThat(broken).as("指向不存在实例的同义词").isEmpty();
    }

    private static int countOccurrences(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }
}
