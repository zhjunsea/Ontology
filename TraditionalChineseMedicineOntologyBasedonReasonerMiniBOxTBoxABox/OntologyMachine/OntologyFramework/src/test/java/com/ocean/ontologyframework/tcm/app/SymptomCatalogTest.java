package com.ocean.ontologyframework.tcm.app;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 症状实例个体目录加载测试。
 *
 * <p>不依赖推理机 / OBDA / MySQL，仅解析 ABox 文件。
 */
class SymptomCatalogTest {

    private static String aboxDir;
    private static SymptomCatalog catalog;

    @BeforeAll
    static void setUp() {
        aboxDir = readAboxDir();
        catalog = new SymptomCatalog(aboxDir);
    }

    @SuppressWarnings("unchecked")
    static String readAboxDir() {
        try (InputStream is = SymptomCatalogTest.class.getClassLoader()
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

    @Test
    @DisplayName("ABox 目录存在且三个类别文件齐全")
    void aboxDirExists() {
        Path dir = Paths.get(aboxDir);
        assertThat(Files.isDirectory(dir)).as("ABox 目录: " + aboxDir).isTrue();
        assertThat(Files.isRegularFile(dir.resolve("tcm-zhengzhuang-abox.owl"))).isTrue();
        assertThat(Files.isRegularFile(dir.resolve("tcm-maixiang-abox.owl"))).isTrue();
        assertThat(Files.isRegularFile(dir.resolve("tcm-shexiang-abox.owl"))).isTrue();
    }

    @Test
    @DisplayName("目录规模：症状 593 / 脉象 68 / 舌象 72 / 腹证 4")
    void catalogSizes() {
        assertThat(catalog.countOf(SymptomCatalog.Category.ZHENGZHUANG)).isEqualTo(593);
        assertThat(catalog.countOf(SymptomCatalog.Category.MAIXIANG)).isEqualTo(68);
        assertThat(catalog.countOf(SymptomCatalog.Category.SHEXIANG)).isEqualTo(72);
        assertThat(catalog.countOf(SymptomCatalog.Category.FUZHENG)).isEqualTo(4);
        assertThat(catalog.size()).isEqualTo(593 + 68 + 72 + 4);
    }

    @Test
    @DisplayName("每条目录项都有非空 fragment / label / IRI，且 fragment 唯一")
    void entriesWellFormed() {
        assertThat(catalog.all()).isNotEmpty();
        assertThat(catalog.all()).allSatisfy(e -> {
            assertThat(e.getFragment()).isNotBlank();
            assertThat(e.getLabel()).isNotBlank();
            assertThat(e.getIri()).startsWith(SymptomCatalog.BASE_NS);
            assertThat(e.getCategory()).isNotNull();
        });
        long distinct = catalog.all().stream().map(SymptomCatalog.Entry::getFragment).distinct().count();
        assertThat(distinct).isEqualTo(catalog.size());
    }

    @Test
    @DisplayName("按 label / fragment 可检索到实例个体")
    void lookup() {
        assertThat(catalog.byLabel("发热")).isPresent()
                .get().extracting(SymptomCatalog.Entry::getFragment).isEqualTo("Fare_instance");
        assertThat(catalog.byFragment("Ehan_instance")).isPresent()
                .get().extracting(SymptomCatalog.Entry::getLabel).isEqualTo("恶寒");
        // 支持传入完整 IRI
        assertThat(catalog.byFragment(SymptomCatalog.BASE_NS + "Xiali_instance")).isPresent()
                .get().extracting(SymptomCatalog.Entry::getLabel).isEqualTo("下利");
        assertThat(catalog.byLabel("这个症状不存在")).isEmpty();
    }

    @Test
    @DisplayName("口语同义词全部指向真实存在的实例个体")
    void aliasesResolveToRealEntries() {
        assertThat(catalog.aliases()).isNotEmpty();
        catalog.aliases().forEach((alias, target) ->
                assertThat(catalog.byLabel(target))
                        .as("同义词 %s → %s 必须存在于目录中", alias, target)
                        .isPresent());
        assertThat(catalog.aliasTarget("拉肚子")).isEqualTo("下利");
        assertThat(catalog.aliasTarget("睡不着")).isEqualTo("不得眠");
        assertThat(catalog.aliasTarget("手脚冰凉")).isEqualTo("手足厥逆");
    }

    @Test
    @DisplayName("SKOS 词表已接入：症状同义词来自 tcm-zhengzhuang_skos.ttl")
    void aliasesComeFromSkos() {
        Path skos = Paths.get(aboxDir).resolve(SymptomCatalog.SKOS_FILE);
        assertThat(Files.isRegularFile(skos)).as("SKOS 词表: " + skos).isTrue();

        // 规模：SKOS 覆盖后同义词应远超旧硬编码的 ~87 条
        assertThat(catalog.aliases().size()).isGreaterThan(1000);
        assertThat(catalog.surfaceForms().size()).isGreaterThan(1500);

        // 旧硬编码覆盖不到、只有 SKOS 才有的口语表述
        assertThat(catalog.aliasTarget("两边肋骨下面胀痛")).isEqualTo("胸胁苦满");
        assertThat(catalog.aliasTarget("拉不消化的东西")).isEqualTo("下利清谷");
        assertThat(catalog.aliasTarget("想拉又拉不出")).isEqualTo("下重");
        assertThat(catalog.aliasTarget("说胡话")).isEqualTo("谵语");
        assertThat(catalog.aliasTarget("起鸡皮疙瘩")).isEqualTo("皮肤粟起");
        assertThat(catalog.aliasTarget("下午定时发热")).isEqualTo("日晡潮热");
        // 医理纠正：想吐 = 欲呕而未呕（旧硬编码误置于「呕吐」）
        assertThat(catalog.aliasTarget("想吐")).isEqualTo("欲呕");
        // 医理纠正：畏寒（里寒）≠ 恶寒（表证），二者为本体中的不同个体。
        // 「畏寒」本身即规范名，故不再作为别名指向「恶寒」。
        assertThat(catalog.aliasTarget("畏寒")).isNotEqualTo("恶寒");
        assertThat(catalog.byLabel("畏寒")).isPresent();
        assertThat(catalog.aliasTarget("低烧")).isEqualTo("微热");
        assertThat(catalog.aliasTarget("高烧")).isEqualTo("大热");
        assertThat(catalog.aliasTarget("小肚子疼")).isEqualTo("少腹痛");
        assertThat(catalog.aliasTarget("手脚发凉")).isEqualTo("手足冷");
        // 四诊其余通道（脉象/舌象/腹证）亦由 SKOS 覆盖，不再有 Java 硬编码补充表
        assertThat(catalog.aliasTarget("脉弦")).isEqualTo("弦脉");
        assertThat(catalog.aliasTarget("脉搏快")).isEqualTo("数脉");
        assertThat(catalog.aliasTarget("舌苔黄")).isEqualTo("黄苔");
        assertThat(catalog.aliasTarget("舌边有齿痕")).isEqualTo("齿痕舌");
        assertThat(catalog.aliasTarget("舌头红")).isEqualTo("红舌");
    }

    @Test
    @DisplayName("切词表面形式按长度降序，保证最长匹配优先")
    void surfaceFormsSortedByLengthDesc() {
        var forms = catalog.surfaceForms();
        assertThat(forms).isNotEmpty();
        for (int i = 1; i < forms.size(); i++) {
            assertThat(forms.get(i - 1).length())
                    .as("位置 %d 应不短于位置 %d", i - 1, i)
                    .isGreaterThanOrEqualTo(forms.get(i).length());
        }
    }

    @Test
    @DisplayName("自动补全检索：按类别与关键字过滤")
    void search() {
        var hit = catalog.search("胁", SymptomCatalog.Category.ZHENGZHUANG, 50);
        assertThat(hit).isNotEmpty();
        assertThat(hit).allSatisfy(e -> assertThat(e.getLabel()).contains("胁"));

        var limited = catalog.search(null, SymptomCatalog.Category.MAIXIANG, 5);
        assertThat(limited).hasSize(5);
        assertThat(limited).allSatisfy(e -> assertThat(e.getCategory()).isEqualTo(SymptomCatalog.Category.MAIXIANG));
    }
}
