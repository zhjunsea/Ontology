package com.ocean.ontologyframework.sizhubazi.validation;

import openllet.owlapi.OpenlletReasonerFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.TurtleDocumentFormat;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.util.SimpleIRIMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SiZhuBaZi 本体合规校验（纯 OWL API + Openllet，离线，不依赖任何外部服务）。
 *
 * <p>被测对象：第 9 步拆分后——主入口 {@code ontology/sizhubazi-all.owl}（聚合 TBox，ABox imports 依铁律 11 注释掉）
 * 与 ABox {@code ontology/sizhubazi-abox.owl}（显式 imports TBox）。
 * 本类以与框架一致的 {@link AutoIRIMapper} 解析 imports；因 ABox 不进主入口闭包，
 * 故**显式加载 ABox**（与应用侧 {@code ontology.abox-path} 独立加载一致）并对两文件 {@code importsClosure()} 的并集断言。
 *
 * <p>校验：可解析性、Openllet 一致性、ABox 保留的静态/结构性个体（阴阳/五行/十神名录/宫位/六亲/
 * 神煞名录/格局名录/层次/告警标志/长生十二宫）、Restriction 与 SWRL 结构、
 * {@code chart:FourPillars} 四柱各 {@code qualifiedCardinality 1} 约束。
 *
 * <p><b>第 11 步（闸门⑩，A 迁移口径）</b>：查表型历法个体（天干/地支/六十甲子/纳音/藏干/节气/月建）
 * 已迁出至 MySQL（{@code sizhubazidb}），DB 为唯一真源、经 Ontop OBDA 虚拟访问；故离线合规测试
 * 不再断言这些个体存在于 ABox，改由 {@link #step11DatabaseArtifactsAndMigration()} 校验 DB 工件齐备
 * 且这些个体确已迁出。
 *
 * <p>注：本体由带前缀的 OWLAPI 格式加载，实体 IRI 保留短前缀形式（如 {@code core:Stem_Jia}），
 * 故按签名 IRI 的**片段**（末段）断言，不硬编码完整 IRI 串。
 */
@DisplayName("四柱命理本体合规校验：解析 / 一致性 / 域知识完整性（离线）")
public class SiZhuBaZiOntologyComplianceTest {

    private static final Logger log = LoggerFactory.getLogger(SiZhuBaZiOntologyComplianceTest.class);

    private static final Path MAIN_FILE = Path.of("ontology", "sizhubazi-all.owl");
    private static final Path ABOX_FILE = Path.of("ontology", "sizhubazi-abox.owl");
    private static final Path DB_DIR = Path.of("ontology", "database");

    /** SKOS 术语表命名空间（第 13 步）：其 skos:Concept 个体不计入 OWL 本体个体统计。 */
    private static final String SKOS_TERM_NS = "http://example.org/sizhubazi/term";

    private static OWLOntology rootOntology;
    private static OWLOntology aboxOntology;
    private static List<OWLOntology> closure;
    private static Set<String> individualIris;
    private static Set<String> individualFrags;
    /** ABox 源文件文本（第 11 步 A 迁移：文本级断言迁出个体不再被“声明”）。 */
    private static String aboxText;

    @BeforeAll
    static void loadOntology() throws Exception {
        assertThat(Files.isRegularFile(MAIN_FILE))
                .as("本体入口应存在: %s (cwd=%s)", MAIN_FILE.toAbsolutePath(), Path.of("").toAbsolutePath())
                .isTrue();
        assertThat(Files.isRegularFile(ABOX_FILE))
                .as("ABox 文件应存在（第 9 步拆分产物）: %s", ABOX_FILE.toAbsolutePath())
                .isTrue();

        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        // 与框架 OntologyService 一致：扫描本体目录，自动解析 owl:imports
        File parentDir = MAIN_FILE.toFile().getParentFile();
        AutoIRIMapper autoMapper = new AutoIRIMapper(parentDir, true);
        autoMapper.update();
        manager.getIRIMappers().add(autoMapper);

        // 第 13 步：TBox 入口经 owl:imports 并入 SKOS 术语表（sizhubazi-terminology.ttl）。
        // 与框架 OntologyService 一致——AutoIRIMapper 不处理 .ttl，须显式以 Turtle 格式解析、
        // 按 ontologyIRI 注册 SimpleIRIMapper，否则加载 all.owl 时 term: 导入不可解析。
        OWLOntologyManager tempManager = OWLManager.createOWLOntologyManager();
        tempManager.getOntologyConfigurator()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
        File[] ttlFiles = parentDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".ttl"));
        if (ttlFiles != null) {
            for (File ttlFile : ttlFiles) {
                try {
                    OWLOntology temp = tempManager.loadOntologyFromOntologyDocument(
                            new FileDocumentSource(ttlFile, new TurtleDocumentFormat()));
                    temp.getOntologyID().getOntologyIRI().ifPresent(
                            iri -> manager.getIRIMappers().add(new SimpleIRIMapper(iri, IRI.create(ttlFile))));
                    tempManager.removeOntology(temp);
                } catch (Exception e) {
                    log.warn("无法解析 TTL {}: {}", ttlFile.getName(), e.getMessage());
                }
            }
        }

        rootOntology = manager.loadOntologyFromOntologyDocument(MAIN_FILE.toFile());
        assertThat(rootOntology.getOntologyID().getOntologyIRI())
                .as("本体 IRI 应可解析").isPresent();

        // 第 9 步 TBox/ABox 分离后：ABox 依铁律 11 不被主入口 imports，
        // 故测试显式加载 sizhubazi-abox.owl（与应用侧经 ontology.abox-path 独立加载一致）。
        aboxOntology = manager.loadOntologyFromOntologyDocument(ABOX_FILE.toFile());
        aboxText = Files.readString(ABOX_FILE);

        Set<OWLOntology> merged = new HashSet<>();
        rootOntology.importsClosure().forEach(merged::add);
        aboxOntology.importsClosure().forEach(merged::add);
        closure = new ArrayList<>(merged);
        individualIris = closure.stream()
                .flatMap(o -> o.individualsInSignature())
                .map(i -> i.getIRI().toString())
                // 第 13 步：SKOS 术语表的 skos:Concept 个体不计入 OWL 本体个体统计
                .filter(iri -> !iri.startsWith(SKOS_TERM_NS))
                .collect(Collectors.toSet());
        individualFrags = individualIris.stream().map(SiZhuBaZiOntologyComplianceTest::frag)
                .collect(Collectors.toSet());

        log.info("本体加载成功 | mainClosure={} aboxClosure={} | axioms={} | individuals={}",
                rootOntology.importsClosure().count(), aboxOntology.importsClosure().count(),
                closure.stream().mapToInt(OWLOntology::getAxiomCount).sum(),
                individualIris.size());
    }

    @Test
    @DisplayName("P-1 本体可解析且逻辑一致（Openllet isConsistent，TBox+ABox 合并视图）")
    void ontologyLoadsAndIsConsistent() {
        // ABox imports TBox，故对 aboxOntology 推理即覆盖 TBox∪ABox（等价于分离前单文件一致性校验）
        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(aboxOntology);
        try {
            reasoner.flush();
            assertThat(reasoner.isConsistent())
                    .as("本体应逻辑一致（一致性的必要前提）").isTrue();
        } finally {
            reasoner.dispose();
        }
    }

    @Test
    @DisplayName("P-5 TBox 独立可推理（分离后 main-path 仅 TBox，单独一致）")
    void tboxStandaloneConsistentWithoutAbox() {
        assertThat(rootOntology.importsClosure().anyMatch(o ->
                o.getOntologyID().getOntologyIRI()
                        .map(i -> i.toString().endsWith("/abox")).orElse(false)))
                .as("主入口 imports 闭包不应包含 ABox（铁律 11：ABox imports 注释掉）").isFalse();

        OWLReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(rootOntology);
        try {
            reasoner.flush();
            assertThat(reasoner.isConsistent())
                    .as("TBox 应可独立推理且逻辑一致").isTrue();
        } finally {
            reasoner.dispose();
        }
    }

    @Test
    @DisplayName("P-2 枚举型个体全量（阴阳2/五行5/十神名录10/层次4/告警3；天干/地支/月建已迁 DB）")
    void enumeratedIndividualsComplete() {
        assertThat(individualFrags).as("阴阳应为 2（Yin/Yang）").contains("Yin", "Yang");

        assertThat(individualFrags).as("五行应齐备（木火土金水）")
                .contains("Huo", "Mu", "Shui", "Jin", "Tu");

        assertThat(individualFrags).as("十神名录应齐备（正官…劫财）")
                .contains("ZhengGuan", "PianGuan", "ZhengCai", "PianCai", "ZhengYin",
                        "PianYin", "ShiShen", "ShangGuan", "BiJian", "JieCai");

        assertThat(individualFrags).as("命局层次四档应齐备（贵格/富格/平常/贫贱）")
                .contains("GuiGe", "FuGe", "PingChang", "PinJian");

        assertThat(individualFrags).as("告警三类应齐备（大凶/寿元关/重大疾病）")
                .contains("AlertDaXiong", "AlertShouYuanGuan", "AlertJiBing");

        // 第 11 步（A 迁移口径）：天干/地支/月建个体已迁出 ABox（DB 唯一真源），ABox 源不应再“声明”。
        // 注：demo 命例仍“引用”少量干支（chart/Demo_*Pillar 的 hasStem/hasBranch），
        //     经 Ontop 由 DB 提供——此处按“声明”判定（rdf:about），非按签名引用判定。
        assertThat(aboxText).as("ABox 不应再声明天干个体（第 11 步 A 迁移）")
                .doesNotContain("rdf:about=\"core/Stem_");
        assertThat(aboxText).as("ABox 不应再声明地支个体（第 11 步 A 迁移）")
                .doesNotContain("rdf:about=\"core/Branch_");
        assertThat(aboxText).as("ABox 不应再声明月建个体（第 11 步 A 迁移）")
                .doesNotContain("rdf:about=\"calendar/Month");
    }

    @Test
    @DisplayName("P-3 保留个体齐备（神煞名录/格局名录/长生十二宫；查表型历法已迁 DB）")
    void representativeIndividualsPresent() {
        assertThat(individualFrags).as("神煞与空亡名录个体应齐备（天乙/羊刃/驿马/旬空）")
                .contains("TianYi", "YangRen", "YiMa", "Void_Xun");

        assertThat(individualFrags).as("正格名录个体应齐备（正官格/正财格/伤官格）")
                .contains("ZhengGuanGe", "ZhengCaiGe", "ShangGuanGe");

        assertThat(individualFrags.stream().filter(x -> x.startsWith("Stage_")).count())
                .as("长生十二宫应齐备（Stage_* = 12）").isEqualTo(12);

        // 第 11 步（A 迁移口径）：六十甲子/纳音/藏干/节气个体已迁出 ABox（DB 唯一真源）
        assertThat(aboxText).as("ABox 不应再声明纳音个体").doesNotContain("rdf:about=\"core/NaYin_");
        assertThat(aboxText).as("ABox 不应再声明节气个体").doesNotContain("rdf:about=\"calendar/Term_");
        assertThat(aboxText).as("ABox 不应再声明地支藏干个体")
                .doesNotContain("rdf:about=\"core/ZiHidden").doesNotContain("rdf:about=\"core/YinHidden");
        assertThat(aboxText).as("ABox 不应再声明六十甲子个体")
                .doesNotContain("rdf:type rdf:resource=\"core/SexagenaryCycle\"");
    }

    @Test
    @DisplayName("P-6 第 11 步 DB 工件齐备（DDL/数据/映射/属性，A 迁移口径）")
    void step11DatabaseArtifactsAndMigration() {
        assertThat(Files.isRegularFile(DB_DIR.resolve("createdb_tables.ddl")))
                .as("建库建表 DDL 应存在: %s", DB_DIR.resolve("createdb_tables.ddl").toAbsolutePath()).isTrue();
        assertThat(Files.isRegularFile(DB_DIR.resolve("data.sql")))
                .as("典型 ABox 数据脚本应存在").isTrue();
        assertThat(Files.isRegularFile(DB_DIR.resolve("sizhubazi.obda")))
                .as("OBDA 映射应存在").isTrue();
        assertThat(Files.isRegularFile(DB_DIR.resolve("sizhubazi.properties")))
                .as("Ontop 属性应存在").isTrue();

        // ★23：.obda 禁 `#` 注释行
        try {
            String obda = Files.readString(DB_DIR.resolve("sizhubazi.obda"));
            assertThat(obda.lines().anyMatch(l -> l.stripLeading().startsWith("#")))
                    .as(".obda 不应包含以 # 起始的注释行（★23）").isFalse();
            assertThat(obda).as(".obda 应含映射声明块")
                    .contains("[MappingDeclaration]").contains("[PrefixDeclaration]");
            assertThat(obda).as("第 15 步：大条文库应经 OBDA 暴露")
                    .contains("day_hour_clause").contains("tiaohou_clause")
                    .contains("reading:DayHourClause").contains("yongshen:TiaohouClause");
            assertThat(obda).as("第 16 步：六典籍名录库应经 OBDA 暴露")
                    .contains("pattern_catalog").contains("lady_fortune_catalog")
                    .contains("technique_catalog").contains("sexagenary_nature")
                    .contains("pattern:Pattern").contains("reading:LadyFortune")
                    .contains("core:sexagenaryNatureText");
            assertThat(obda).as("第 17 步 I3：三张断语条文库应经 OBDA 暴露（G1/G2/G5/G6/G7 正文落库）")
                    .contains("auspice_clause").contains("theory_clause").contains("dts_clause")
                    .contains("reading:StemBranchAuspice").contains("reading:ProgenyPrediction")
                    .contains("yongshen:BingYao").contains("reading:KinshipTheory")
                    .contains("pattern:ImagePattern").contains("pattern:SovereignPattern")
                    .contains("yongshen:IdleGod").contains("fortune:ZhenYuan");
            assertThat(obda).as("第 18 步 R3：赋/歌/诗诀/独步/诀/论/篇/说/图 条文库应经 OBDA 暴露为 reading:CategoryFortune")
                    .contains("fu_clause").contains("reading:CategoryFortune");
            assertThat(obda).as("第 23 步：子平真诠 47 篇正文条文库应经 OBDA 暴露为 reading:ReadingItem")
                    .contains("ziping_clause").contains("reading:ReadingItem");
            assertThat(obda).as("第 26 步：六书正文全文库应经 OBDA 暴露为 reading:ReadingItem")
                    .contains("classic_text").contains("reading:ReadingItem");
            assertThat(obda).as("第 31 步：节气精确时刻表应经 OBDA 暴露为 calendar:SolarTermInstant")
                    .contains("solar_term_time").contains("calendar:SolarTermInstant")
                    .contains("calendar:termDatetime");
            assertThat(obda).as("第 17 步：pattern/fortune 前缀应已声明（修复既有 pattern_catalog 未声明前缀）")
                    .contains("pattern: http://example.org/sizhubazi/pattern/")
                    .contains("fortune: http://example.org/sizhubazi/fortune/");

            String ddl = Files.readString(DB_DIR.resolve("createdb_tables.ddl"));
            assertThat(ddl).as("DDL 应含第 15 步新增 2 表（13 张）")
                    .contains("CREATE TABLE `day_hour_clause`").contains("CREATE TABLE `tiaohou_clause`");
            assertThat(ddl).as("DDL 应含第 16 步新增 4 表（合计 17 张）")
                    .contains("CREATE TABLE `pattern_catalog`").contains("CREATE TABLE `lady_fortune_catalog`")
                    .contains("CREATE TABLE `technique_catalog`").contains("CREATE TABLE `sexagenary_nature`");
            assertThat(ddl).as("DDL 应含第 17 步新增 3 表（合计 20 张）")
                    .contains("CREATE TABLE `auspice_clause`").contains("CREATE TABLE `theory_clause`")
                    .contains("CREATE TABLE `dts_clause`");
            assertThat(ddl).as("DDL 应含第 18 步 R3 新增 1 表（合计 21 张）")
                    .contains("CREATE TABLE `fu_clause`");
            assertThat(ddl).as("DDL 应含第 23 步新增 1 表（合计 22 张）")
                    .contains("CREATE TABLE `ziping_clause`");
            assertThat(ddl).as("DDL 应含第 26 步新增 1 表（合计 23 张）")
                    .contains("CREATE TABLE `classic_text`");
            assertThat(ddl).as("DDL 应含第 31 步新增 1 表（合计 24 张，节气精确时刻）")
                    .contains("CREATE TABLE `solar_term_time`");

            // ★防回归：Ontop 原生映射把 <prefix:...> 视为「绝对 IRI」（scheme=prefix），不展开前缀，
            // 导致 target 三元组被静默丢弃（映射 0 命中）。前缀名必须裸写（core:{name}），仅完整 IRI 用 <>。
            assertThat(obda).as(".obda 不应把带前缀的名字写成 <prefix:...>（Ontop 会误判为绝对 IRI 而不展开）")
                    .doesNotContain("<core:").doesNotContain("<calendar:").doesNotContain("<deity:")
                    .doesNotContain("<tengod:").doesNotContain("<reading:").doesNotContain("<rules:")
                    .doesNotContain("<pattern:").doesNotContain("<fortune:")
                    .doesNotContain("<rdfs:").doesNotContain("<xsd:").doesNotContain("<owl:")
                    .doesNotContain("<rdf:");
        } catch (Exception e) {
            throw new AssertionError(".obda 读取失败: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("P-4 Restriction 与 SWRL 规则结构存在（imports 闭包内）")
    void restrictionsAndSwrlRulesPresent() {
        long subClassOf = closure.stream()
                .mapToLong(o -> o.axioms(AxiomType.SUBCLASS_OF).count()).sum();
        long swrlRules = closure.stream()
                .mapToLong(o -> o.axioms(AxiomType.SWRL_RULE).count()).sum();

        assertThat(subClassOf).as("应存在 SubClassOf（含 owl:Restriction）公理").isGreaterThan(0);
        assertThat(swrlRules).as("应存在 SWRL 规则（已序列化 R1/R5/R6/R19/R21/R22）").isGreaterThan(0);
    }

    @Test
    @DisplayName("B-1 chart:FourPillars 四柱各 qualifiedCardinality 1 约束存在")
    void fourPillarsCardinalityPresent() {
        Set<String> pillarProps = new HashSet<>();
        for (OWLOntology o : closure) {
            for (OWLSubClassOfAxiom ax : o.getAxioms(AxiomType.SUBCLASS_OF)) {
                if (!(ax.getSubClass() instanceof OWLClass)) {
                    continue;
                }
                OWLClass c = (OWLClass) ax.getSubClass();
                if (!"FourPillars".equals(frag(c.getIRI().toString()))) {
                    continue;
                }
                OWLClassExpression sup = ax.getSuperClass();
                if (sup instanceof OWLObjectCardinalityRestriction) {
                    OWLObjectCardinalityRestriction cr = (OWLObjectCardinalityRestriction) sup;
                    if (cr.getCardinality() == 1 && cr.getProperty().isObjectPropertyExpression()
                            && cr.getProperty().asOWLObjectProperty().isNamed()) {
                        pillarProps.add(frag(cr.getProperty().asOWLObjectProperty().getIRI().toString()));
                    }
                }
            }
        }
        assertThat(pillarProps)
                .as("chart:FourPillars 应有年/月/日/时四柱的基数 1 约束")
                .contains("hasYearPillar", "hasMonthPillar", "hasDayPillar", "hasHourPillar");
    }

    @Test
    @DisplayName("P-7 第 12 步全集数据脚本完备（data.sql：历法/纳音/藏干/节气/神煞/十神全集边界项与规模）")
    void step12FullDataScriptComplete() {
        Path dataSql = DB_DIR.resolve("data.sql");
        assertThat(Files.isRegularFile(dataSql)).as("全集数据脚本应存在").isTrue();
        String sql;
        try {
            sql = Files.readString(dataSql);
        } catch (Exception e) {
            throw new AssertionError("data.sql 读取失败: " + e.getMessage(), e);
        }
        assertThat(sql).as("十天干全集").contains("Stem_Gui");
        assertThat(sql).as("十二地支全集").contains("Branch_Hai");
        assertThat(sql).as("六十甲子全集（末柱癸亥）").contains("'GuiHai', '癸亥'");
        assertThat(sql).as("纳音全集（末位大海水）").contains("NaYin_DaHaiShui");
        assertThat(sql).as("藏干全集（亥藏甲）").contains("HaiHiddenJia");
        assertThat(sql).as("二十四节气全集（末位大寒）").contains("Term_DaHan");
        assertThat(sql).as("第 31 步：节气精确交节时刻库应入库（含 2024 立春）")
                .contains("INSERT INTO `solar_term_time`").contains("'TermTime_2024_LiChun'");
        assertThat(sql).as("神煞起例全集（孤辰寡宿末组）").contains("DR_GuaSu_ShenYouXu");
        assertThat(sql).as("神煞起例·渊海子平卷之一补录（天赦）").contains("DR_TianShe");
        assertThat(sql).as("神煞起例·渊海子平卷之一补录（天地转杀）").contains("DR_TianDiZhuanSha");
        assertThat(sql).as("神煞起例·渊海子平卷之一补录（三元）").contains("DR_SanYuan");
        assertThat(sql).as("十神映射全集（末位癸/癸）").contains("'TGM_Gui_Gui'");
        assertThat(sql).as("日时断条文库首条（甲子日甲子时）").contains("'DHC_甲子_甲子'");
        assertThat(sql).as("日时断条文库末条（癸亥日癸亥时）").contains("'DHC_癸亥_癸亥'");
        assertThat(sql).as("调候逐月条文库首条（甲·寅月）").contains("'THC_甲_寅'");
        assertThat(sql).as("调候逐月条文库末条（癸·丑月）").contains("'THC_癸_丑'");
        assertThat(sql).as("第 16 步 H4：六十甲子性质吉凶应入库（甲子条）")
                .contains("INSERT INTO `sexagenary_nature`");
        assertThat(sql).as("第 17 步 I3：十干坐支/十二月支/五行时地/孕生男女 断语条文库应入库")
                .contains("INSERT INTO `auspice_clause`").contains("'AC_StemSeat_JiaYi'");
        assertThat(sql).as("第 17 步 I3：病药/动静/盖头/六亲诸说 断语条文库应入库")
                .contains("INSERT INTO `theory_clause`").contains("'TC_BingYao_Shuo'");
        assertThat(sql).as("第 17 步 I3：滴天髓通神论/六亲论诸篇 断语条文库应入库")
                .contains("INSERT INTO `dts_clause`").contains("'DC_ImagePattern'");
        assertThat(sql).as("第 18 步 R3：赋/歌/诗诀/独步/诀/论/篇/说/图 条文库应入库（首条/末条）")
                .contains("INSERT INTO `fu_clause`").contains("'FU_YH_001'").contains("'FU_SF_043'");
        assertThat(sql).as("第 20 步（第五轮正文级复核 P1~P8 补录）新增名录/条文应入库")
                .contains("'PAT_ZhiHouYiZi'").contains("'PAT_FengHuangDi'")
                .contains("'TEC_QiTaiFa'").contains("'TEC_QiXiFa'")
                .contains("'TEC_QiBianFa'").contains("'TEC_QiTongFa'")
                .contains("'TEC_JinJiaoTuiFu'").contains("'TEC_ShiGanTianWen'")
                .contains("'TEC_ShiErZhiDiLi'").contains("'TEC_YueLingXiangBian'")
                .contains("'TEC_WuXingZongLun'")
                .contains("'FU_SMTH_MuYun'").contains("'FU_SF_ZhiZaoTu'")
                .contains("'FU_SF_YangShunYinNiTu'");
        assertThat(sql).as("第 21 步（第五轮复核续·穷通宝鉴十干分季总论 P9 补录）新增名录应入库")
                .contains("'TEC_QT_Jia_Chun'").contains("'TEC_QT_Jia_Xia'")
                .contains("'TEC_QT_Jia_Qiu'").contains("'TEC_QT_Jia_Dong'")
                .contains("'TEC_QT_Yi_Chun'").contains("'TEC_QT_Yi_Xia'").contains("'TEC_QT_Yi_Qiu'")
                .contains("'TEC_QT_Bing_Chun'").contains("'TEC_QT_Bing_Xia'")
                .contains("'TEC_QT_Wu_Dong'")
                .contains("'TEC_QT_Ji_Chun'").contains("'TEC_QT_Ji_Qiu'").contains("'TEC_QT_Ji_Dong'");
        assertThat(sql).as("第 21 步（P10·穷通宝鉴五行分论补充）新增名录应入库（P7 声明范围补全）")
                .contains("'TEC_QT_WuXing_Mu'").contains("'TEC_QT_WuXing_Huo'")
                .contains("'TEC_QT_WuXing_Tu'").contains("'TEC_QT_SiJiYueTu'")
                .contains("'TEC_QT_WuXing_Jin'").contains("'TEC_QT_WuXing_Shui'");
        assertThat(sql).as("第 22 步（第六轮正文级复核 P11 补录）渊海子平卷二具名格名录应入库")
                .contains("'PAT_ZaQiCaiGuanYinShou'").contains("'PAT_ZaQiYinShou'")
                .contains("'PAT_YueShangPianGuan'");
        assertThat(sql).as("第 23 步（第六轮正文级复核·子平真诠正文补录）条文库应入库（首篇/末篇）")
                .contains("INSERT INTO `ziping_clause`").contains("'ZP_01'").contains("'ZP_47'");
        assertThat(sql).as("第 26 步（第九轮正文内容级复核·六书全文补齐）六书正文全文库应入库（六书首篇）")
                .contains("INSERT INTO `classic_text`")
                .contains("'CT_DTS_01'").contains("'CT_QT_01'").contains("'CT_SF_01'").contains("'CT_SMTH_001'");
        assertThat(sql).as("第 36 步（渊海子平 epub 目录全量复核·1 图题 + 5 卷题缺口补录）应入库")
                .contains("'FU_YH_154'").contains("'阴阳顺逆生旺死绝之图'")
                .contains("'CT_YH_149'").contains("'CT_YH_192'").contains("'CT_YH_220'")
                .contains("'CT_YH_281'").contains("'CT_YH_296'");

        long deityRows = sql.lines().filter(l -> l.stripLeading().startsWith("('DR_")).count();
        long tgRows = sql.lines().filter(l -> l.stripLeading().startsWith("('TGM_")).count();
        long dayHourRows = sql.lines().filter(l -> l.stripLeading().startsWith("('DHC_")).count();
        long tiaohouRows = sql.lines().filter(l -> l.stripLeading().startsWith("('THC_")).count();
        long patternRows = sql.lines().filter(l -> l.stripLeading().startsWith("('PAT_")).count();
        long ladyRows = sql.lines().filter(l -> l.stripLeading().startsWith("('LF_")).count();
        long techRows = sql.lines().filter(l -> l.stripLeading().startsWith("('TEC_")).count();
        long auspiceRows = sql.lines().filter(l -> l.stripLeading().startsWith("('AC_")).count();
        long theoryRows = sql.lines().filter(l -> l.stripLeading().startsWith("('TC_")).count();
        long dtsRows = sql.lines().filter(l -> l.stripLeading().startsWith("('DC_")).count();
        long fuRows = sql.lines().filter(l -> l.stripLeading().startsWith("('FU_")).count();
        long zipingRows = sql.lines().filter(l -> l.stripLeading().startsWith("('ZP_")).count();
        long classicRows = sql.lines().filter(l -> l.stripLeading().startsWith("('CT_")).count();
        long termTimeRows = sql.lines().filter(l -> l.stripLeading().startsWith("('TermTime_")).count();
        assertThat(deityRows).as("神煞起例应达 186 条（第 16 步 H1 +49；第 17 步 I2 细煞 +33）").isEqualTo(186);
        assertThat(tgRows).as("十神映射应达 100 条").isEqualTo(100);
        assertThat(dayHourRows).as("日时断条文库应达 720 条（60 日柱×12 时支）").isEqualTo(720);
        assertThat(tiaohouRows).as("调候逐月条文库应达 120 条（十天干×十二月）").isEqualTo(120);
        assertThat(patternRows).as("具名格局名录应达 102 条（第 16 步 H2 95；第 17 步 I1 +2；第 20 步 P2 +2；第 22 步 P11 +3）").isEqualTo(102);
        assertThat(ladyRows).as("女命细分名录应达 17 条（第 16 步 H3）").isEqualTo(17);
        assertThat(techRows).as("技法/篇章名录应达 66 条（第 16 步 H4 38；第 20 步 P1/P3/P4/P6/P7 +9；第 21 步 P9 穷通十干分季总论 +13、P10 穷通五行分论 +6）").isEqualTo(66);
        assertThat(auspiceRows).as("十干坐支/十二月支/五行时地/孕生男女 断语应达 25 条（第 17 步 I3）").isEqualTo(25);
        assertThat(theoryRows).as("病药/动静/盖头/六亲诸说 断语应达 12 条（第 17 步 I3）").isEqualTo(12);
        assertThat(dtsRows).as("滴天髓通神论/六亲论诸篇 断语应达 26 条（第 17 步 I3）").isEqualTo(26);
        assertThat(fuRows).as("赋/歌/诗诀/… 条文库应达 209 条（第 18 步 R3 205；第 20 步 P5/P8 +3；第 36 步渊海图题 +1）").isEqualTo(209);
        assertThat(zipingRows).as("子平真诠 47 篇正文条文库应达 47 条（第 23 步）").isEqualTo(47);
        assertThat(classicRows).as("典籍正文全文库应达 927 篇（第 26 步：滴天髓63+穷通宝鉴142+神峰通考128+三命通会298+渊海子平291；第 34 步拆分新增六亲论·十二从象；第 36 步渊海卷之三~七 5 条卷题）").isEqualTo(927);
        assertThat(termTimeRows).as("节气精确交节时刻应达 2424 条（1950–2050 共 101 年 ×24，第 31 步）").isEqualTo(2424);
    }

    @Test
    @DisplayName("第 31 步 节气精度升级：calendar:SolarTermInstant 类与 termYear/termIndex/termDatetime/instantOf 声明齐备")
    void step31SolarTermInstantDeclared() {
        Set<String> classFrags = new HashSet<>();
        Set<String> dtPropFrags = new HashSet<>();
        Set<String> opPropFrags = new HashSet<>();
        for (OWLOntology o : closure) {
            o.classesInSignature().forEach(c -> classFrags.add(frag(c.getIRI().toString())));
            o.dataPropertiesInSignature().forEach(p -> dtPropFrags.add(frag(p.getIRI().toString())));
            o.objectPropertiesInSignature().forEach(p -> opPropFrags.add(frag(p.getIRI().toString())));
        }
        assertThat(classFrags).as("应声明 calendar:SolarTermInstant 类").contains("SolarTermInstant");
        assertThat(dtPropFrags).as("应声明 termYear/termIndex/termDatetime 数据属性")
                .contains("termYear", "termIndex", "termDatetime");
        assertThat(opPropFrags).as("应声明 instantOf 对象属性").contains("instantOf");
    }

    private static String frag(String iri) {
        int i = Math.max(iri.lastIndexOf('/'), iri.lastIndexOf('#'));
        return i >= 0 && i + 1 < iri.length() ? iri.substring(i + 1) : iri;
    }
}
