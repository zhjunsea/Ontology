package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.utilities.ServiceProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批次1 回归：在「正确的本应用 Ontop 端点」下，验证 {@code loadOntologyDomain()} 真正装载成功。
 *
 * <p>背景（本任务诊断结论）：{@code loadOntologyDomain()} 的三条 SPARQL 本身正确——在正确端点下
 * 先天数/五行/方位均返回 8/8 且与内置表逐例一致；线上出现「回退内置表」的真因是 SPARQL 端点不可达
 * 或指向了其它应用的 Ontop（如 8080 被其它应用占用），而**不是**此前注释误以为的
 * 「Ontop/OWL2QL 不投影本体侧 rdfs:label」。本测试即锁定该结论作为回归护栏。
 *
 * <p><b>为何是「开关型」集成测试</b>：{@link OBDAHandler} 为全局单例（{@code init} 只允许一次，
 * {@code Holder} 静态初始化时即建 Hikari 连接池并固定 SPARQL 端点），无法在常规离线单测里安全切换端点。
 * 故本类默认禁用，仅在显式开启且外部服务就绪时运行；未开启时被 JUnit 判为 disabled（跳过），
 * 不触碰任何外部环境，也不影响 {@code AllPlumBlossomSuiteTest} 与常规 {@code mvn test}。
 *
 * <p><b>运行方式</b>（先起本应用 Ontop 与已灌数据的 MySQL，再单独运行本类以独占 JVM）：
 * <pre>
 *   # 1) 模块根 OntologyFrameworkPlumBlossom/ 下起本应用 Ontop（端口 8089，避免 8080 被它应用占用）
 *   &lt;ontop-cli&gt;/ontop.bat endpoint \
 *       -m ontology/database/plumBlossom.obda \
 *       -t ontology/plumblossom-abox.owl \
 *       -p target/ontop-it.properties \
 *       -x ontology/catalog-v001.xml --port 8089
 *   #    （target/ontop-it.properties 至少含 port=8089 与 jdbc.* 指向 plumblossomdb）
 *
 *   # 2) 单独运行本集成测试（-Dtest 限定，独占 fork 保证 OBDAHandler 单例干净）
 *   mvn -pl OntologyFrameworkPlumBlossom -Dtest=PlumBlossomDomainLoadIntegrationTest test \
 *       -Dplumblossom.it.enabled=true \
 *       -Dplumblossom.it.endpoint=http://localhost:8089/sparql \
 *       -Dplumblossom.it.mysql.host=127.0.0.1 -Dplumblossom.it.mysql.port=3306
 * </pre>
 */
@EnabledIfSystemProperty(named = "plumblossom.it.enabled", matches = "true",
        disabledReason = "集成测试：需外部本应用 Ontop + MySQL，显式 -Dplumblossom.it.enabled=true 且环境就绪才运行")
@DisplayName("批次1 回归：正确端点下本体域知识装载 8/8（集成，需 Ontop+MySQL）")
class PlumBlossomDomainLoadIntegrationTest {

    private static final Path PROP_SRC = Path.of("ontology", "database", "plumBlossom.properties");
    private static final Path OBDA = Path.of("ontology", "database", "plumBlossom.obda");
    private static final Path MAIN = Path.of("ontology", "plumblossom-all.owl");
    private static final Path TMP_PROP = Path.of("target", "plumBlossom-it.properties");

    private static final String ENDPOINT = System.getProperty("plumblossom.it.endpoint", "http://localhost:8089/sparql");
    private static final String MYSQL_HOST = System.getProperty("plumblossom.it.mysql.host", "127.0.0.1");
    private static final int MYSQL_PORT = Integer.parseInt(System.getProperty("plumblossom.it.mysql.port", "3306"));

    private static boolean ran = false;

    @BeforeAll
    static void prepare() throws Exception {
        URI ep = URI.create(ENDPOINT);
        ServiceProbe.assumeReachable("本应用 Ontop 端点", ep.getHost(), ep.getPort());
        ServiceProbe.assumeReachable("MySQL(plumblossomdb)", MYSQL_HOST, MYSQL_PORT);

        assertThat(Files.isRegularFile(PROP_SRC)).as("应存在属性文件: %s", PROP_SRC.toAbsolutePath()).isTrue();
        assertThat(Files.isRegularFile(OBDA)).as("应存在 OBDA 映射: %s", OBDA.toAbsolutePath()).isTrue();
        assertThat(Files.isRegularFile(MAIN)).as("应存在 TBox 入口: %s", MAIN.toAbsolutePath()).isTrue();

        // 生成临时属性文件：仅把 SPARQL 端点改指向被验证的「正确端点」（.obda 无 endpoint 行，端点回退到本文件）。
        String text = Files.readString(PROP_SRC, StandardCharsets.UTF_8);
        String replaced = text.replaceAll("(?m)^sparql\\.endpoint=.*$", "sparql.endpoint=" + ENDPOINT);
        if (replaced.equals(text)) {
            replaced = text + System.lineSeparator() + "sparql.endpoint=" + ENDPOINT + System.lineSeparator();
        }
        Files.createDirectories(TMP_PROP.getParent());
        Files.writeString(TMP_PROP, replaced, StandardCharsets.UTF_8);
    }

    @AfterAll
    static void cleanup() {
        try {
            Files.deleteIfExists(TMP_PROP);
        } catch (Exception ignore) {
            // 清理失败不阻断测试结论
        }
        if (ran) {
            try {
                OBDAHandler.shutdown();
            } catch (Throwable ignore) {
                // 单例资源已在测试结束，关闭异常忽略
            }
        }
    }

    @Test
    @DisplayName("loadOntologyDomain 经正确端点装载：8/8 命中且 ontologyDomainReady=true")
    void testDomainLoadedFromCorrectEndpoint() throws Exception {
        ran = true;

        PlumBlossomOntologyJobWorker worker = new PlumBlossomOntologyJobWorker();
        setField(worker, "mainOntologyPath", MAIN.toString());
        setField(worker, "obdaPath", OBDA.toString());
        setField(worker, "obdaPropertiesPath", TMP_PROP.toString());

        // @PostConstruct 入口：initOntologyPipeline() → createBackendService() → afterBackendServiceReady() → loadOntologyDomain()
        worker.init();

        boolean ready = (boolean) getField(worker, "ontologyDomainReady");
        assertThat(ready).as("正确端点下 ontologyDomainReady 应为 true（不应回退内置表）").isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Integer> num = (Map<String, Integer>) getField(worker, "ontoTrigramNumber");
        @SuppressWarnings("unchecked")
        Map<String, String> five = (Map<String, String>) getField(worker, "ontoTrigramFive");
        @SuppressWarnings("unchecked")
        Map<String, String> dir = (Map<String, String>) getField(worker, "ontoTrigramDir");

        assertThat(num).as("先天数装载条目").hasSize(8);
        assertThat(five).as("五行装载条目").hasSize(8);
        assertThat(dir).as("方位装载条目").hasSize(8);

        // 与《梅花易数》原著先天八卦口径逐例比对（独立第三方期望，不依赖被测类私有常量）
        assertTrigram(num, five, dir, "乾", 1, "金", "西北");
        assertTrigram(num, five, dir, "兑", 2, "金", "西");
        assertTrigram(num, five, dir, "离", 3, "火", "南");
        assertTrigram(num, five, dir, "震", 4, "木", "东");
        assertTrigram(num, five, dir, "巽", 5, "木", "东南");
        assertTrigram(num, five, dir, "坎", 6, "水", "北");
        assertTrigram(num, five, dir, "艮", 7, "土", "东北");
        assertTrigram(num, five, dir, "坤", 8, "土", "西南");

        assertRelationDomainLoaded(worker);
    }

    @SuppressWarnings("unchecked")
    private static void assertRelationDomainLoaded(PlumBlossomOntologyJobWorker worker) throws Exception {
        java.util.Set<String> gen = (java.util.Set<String>) getField(worker, "ontoGenerates");
        java.util.Set<String> ov = (java.util.Set<String>) getField(worker, "ontoOvercomes");
        assertThat(gen).as("五行相生边").hasSize(5).contains("金>水", "水>木", "木>火", "火>土", "土>金");
        assertThat(ov).as("五行相克边").hasSize(5).contains("金>木", "木>土", "土>水", "水>火", "火>金");

        java.util.Map<String, String> fdir = (java.util.Map<String, String>) getField(worker, "ontoFiveDir");
        java.util.Map<String, String> fcol = (java.util.Map<String, String>) getField(worker, "ontoFiveColor");
        java.util.Map<String, String> fsea = (java.util.Map<String, String>) getField(worker, "ontoFiveSeason");
        java.util.Map<String, String> fobj = (java.util.Map<String, String>) getField(worker, "ontoFiveObject");
        assertThat(fdir).as("五行方位").hasSize(5);
        assertThat(fdir.get("木")).as("木之方位").isEqualTo("东方");
        assertThat(fcol).as("五行五色").hasSize(5);
        assertThat(fcol.get("火")).as("火之五色").isEqualTo("红、紫、赤");
        assertThat(fsea).as("五行时令").hasSize(5);
        assertThat(fobj).as("五行器物").hasSize(5);

        java.util.Map<String, String> vit = (java.util.Map<String, String>) getField(worker, "ontoSeasonVitality");
        assertThat(vit).as("旺衰规则 5×5").hasSize(25);
        assertThat(vit.get("木|春")).as("木旺于春").isEqualTo("旺");
        assertThat(vit.get("金|秋")).as("金旺于秋").isEqualTo("旺");
        assertThat(vit.get("水|夏")).as("水囚于夏").isEqualTo("囚");

        java.util.Map<Integer, String> bnm = (java.util.Map<Integer, String>) getField(worker, "ontoBodyUseName");
        java.util.Map<Integer, String> bau = (java.util.Map<Integer, String>) getField(worker, "ontoBodyUseLabel");
        assertThat(bnm).as("体用五级名称").hasSize(5);
        assertThat(bnm.get(1)).as("level1").isEqualTo("用生体");
        assertThat(bnm.get(5)).as("level5").isEqualTo("用克体");
        assertThat(bau).as("体用五级吉凶").hasSize(5);
        assertThat(bau.get(1)).as("level1 吉凶").isEqualTo("大吉");
        assertThat(bau.get(5)).as("level5 吉凶").isEqualTo("大凶");

        java.util.Map<String, Integer> aus = (java.util.Map<String, Integer>) getField(worker, "ontoAuspiciousness");
        assertThat(aus).as("吉凶等级（DB 映射后可查）").hasSize(5);
        assertThat(aus.get("大吉")).as("大吉等级").isEqualTo(5);
        assertThat(aus.get("大凶")).as("大凶等级").isEqualTo(1);
    }

    private static void assertTrigram(Map<String, Integer> num, Map<String, String> five,
                                      Map<String, String> dir, String name, int expectedNum,
                                      String expectedFive, String expectedDir) {
        assertThat(num.get(name)).as("%s 先天数", name).isEqualTo(expectedNum);
        assertThat(five.get(name)).as("%s 五行", name).isEqualTo(expectedFive);
        assertThat(dir.get(name)).as("%s 方位", name).isEqualTo(expectedDir);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
