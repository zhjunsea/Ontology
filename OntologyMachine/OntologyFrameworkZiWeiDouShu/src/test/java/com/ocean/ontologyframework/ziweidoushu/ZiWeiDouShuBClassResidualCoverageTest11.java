package com.ocean.ontologyframework.ziweidoushu;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B 类补齐·⑪ 业务 BPMN 节点驱动批次（离线·Mockito 深桩，无需 Camunda/DB）。
 *
 * <p>以 {@link ActivatedJob}/{@link JobClient} 深桩直接驱动 JobWorker 的业务节点（安星/四化/庙陷/
 * 落宫/验盘/本命综合/流年/大限/叠宫/综合汇总/格局），用合成整盘 vars 走「成功完成」与「越界/缺参失败」
 * 两类路径，补此前仅端到端覆盖（正常盘）而欠缺的节点内部业务分支。OBDA/LLM/告警/持久化等 IT 分支不在本批范围。
 */
@DisplayName("B类补齐·业务 BPMN 节点驱动分支批次")
class ZiWeiDouShuBClassResidualCoverageTest11 {

    private static final long JOB_KEY = 9001L;
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] MAIN14 = {"紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府", "太阴",
            "贪狼", "巨门", "天相", "天梁", "七杀", "破军"};
    private static final String[] AUX = {"左辅", "右弼", "文昌", "文曲", "天魁", "天钺", "禄存", "擎羊",
            "陀罗", "火星", "铃星", "天空", "地劫", "天马", "天刑"};
    private static final String[] MISC = {"红鸾", "天喜", "台辅", "封诰", "三台", "八座", "恩光", "天贵",
            "天官", "天福", "天厨", "天巫", "天才", "天寿", "孤辰", "寡宿", "华盖", "咸池", "龙池", "凤阁",
            "天德", "月德", "解神", "天哭", "天虚", "天姚"};

    private final ZiWeiDouShuOntologyJobWorker worker = new ZiWeiDouShuOntologyJobWorker();

    private ActivatedJob jobWith(Map<String, Object> vars) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(JOB_KEY);
        when(job.getVariablesAsMap()).thenReturn(vars);
        return job;
    }

    private JobClient client() {
        return mock(JobClient.class, RETURNS_DEEP_STUBS);
    }

    private static String placements(String[] stars) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stars.length; i++) {
            sb.append(stars[i]).append("@").append(ZHI[i % 12]).append("；");
        }
        return sb.toString();
    }

    private static String skeleton() {
        return "命宫=子;身宫=午;兄弟=丑;夫妻=寅;子女=卯;财帛=辰;疾厄=巳;迁移=午;奴仆=未;官禄=申;田宅=酉;福德=戌;父母=亥;水二局";
    }

    private static final String CHART_BASE = "SOLAR=1990-05-15T10:30;GENDER=男;LM=4;LD=21;LY=1990";

    private static Map<String, Object> baseVars() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("chartBase", CHART_BASE);
        v.put("chartSkeleton", skeleton());
        v.put("mainStars", placements(MAIN14));
        v.put("auxiliaryStars", placements(AUX));
        v.put("miscStars", placements(MISC));
        v.put("fourTransform", "生年干庚：太阳化禄；武曲化权；太阴化科；天同化忌");
        v.put("subjectTraits", "相貌清秀、性情温和、面上有痣、面色红、身长、刚毅");
        v.put("candidateTimes", "子,丑,寅");
        v.put("verifyRounds", "0");
        v.put("birth", "1990-05-15 10:30");
        v.put("calendar", "公历");
        v.put("gender", "男");
        v.put("targetMonth", "6");
        v.put("targetDay", "15");
        v.put("granularity", "年");
        v.put("patternResult", "紫府朝垣；文星暗拱·三方朝拱；文星暗拱·昌曲夹命；文星暗拱·迁移照命");
        v.put("patternGrade", "上格（贵局）");
        v.put("starNature", "紫微：帝星");
        v.put("sanFangJiYao", "三方见吉");
        v.put("tongYuan", "同垣命中若干");
        v.put("jiaGongGe", "夹拱格命中");
        v.put("tanXingYaoLun", "谈星要论命中");
        v.put("mingGeGrade", "上格（贵局）");
        v.put("geXingShu", "格星数高下");
        v.put("anMingJiXiong", "生时安命吉");
        v.put("palaceZhiGrid", "得地合格");
        v.put("femaleReading", "女命专论示例");
        v.put("childReading", "小儿命专论示例");
        v.put("evilPatternTrigger", "羊陀迭并（本命羊/陀+流年流羊流陀冲会）；");
        return v;
    }

    // ============ placeMainStars：正常安十四主星 / 局日越界失败 ============
    @Test
    @DisplayName("placeMainStars：正常完成 + 局/日越界→throwError")
    void placeMainStarsBranches() {
        JobClient c1 = client();
        worker.handlePlaceMainStars(jobWith(baseVars()), c1);
        verify(c1).newCompleteCommand(JOB_KEY);
        verify(c1, never()).newThrowErrorCommand(anyLong());

        Map<String, Object> bad = baseVars();
        bad.put("chartSkeleton", "");   // bureau=0 → 越界
        JobClient c2 = client();
        worker.handlePlaceMainStars(jobWith(bad), c2);
        verify(c2).newThrowErrorCommand(JOB_KEY);
    }

    // ============ placeMiscStars：正常（命身俱在）/ 缺命身宫（跳过天才天寿） ============
    @Test
    @DisplayName("placeMiscStars：正常 + 缺命宫/身宫跳过分支")
    void placeMiscStarsBranches() {
        JobClient c1 = client();
        worker.handlePlaceMiscStars(jobWith(baseVars()), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        Map<String, Object> noMing = baseVars();
        noMing.put("chartSkeleton", "财帛=辰;水二局");
        JobClient c2 = client();
        worker.handlePlaceMiscStars(jobWith(noMing), c2);
        verify(c2).newCompleteCommand(JOB_KEY);
    }

    // ============ placeFourTransform：正常 / 四化星全未安（待考分支） ============
    @Test
    @DisplayName("placeFourTransform：正常 + 全未安待考")
    void placeFourTransformBranches() {
        JobClient c1 = client();
        worker.handlePlaceFourTransform(jobWith(baseVars()), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        Map<String, Object> empty = baseVars();
        empty.put("mainStars", "");
        empty.put("auxiliaryStars", "");
        JobClient c2 = client();
        worker.handlePlaceFourTransform(jobWith(empty), c2);
        verify(c2).newCompleteCommand(JOB_KEY);
    }

    // ============ assessBrightness：整盘庙陷/落闲/生克 ============
    @Test
    @DisplayName("assessBrightness：整盘完成")
    void assessBrightnessBranches() {
        JobClient c = client();
        worker.handleAssessBrightness(jobWith(baseVars()), c);
        verify(c).newCompleteCommand(JOB_KEY);
    }

    // ============ emptyPalace：稀疏主星（含对宫亦空）/ 满盘 ============
    @Test
    @DisplayName("emptyPalace：稀疏（借对宫/对宫空）与满盘")
    void emptyPalaceBranches() {
        Map<String, Object> sparse = baseVars();
        sparse.put("mainStars", "紫微@子；天机@丑；");
        JobClient c1 = client();
        worker.handleEmptyPalace(jobWith(sparse), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        JobClient c2 = client();
        worker.handleEmptyPalace(jobWith(baseVars()), c2);
        verify(c2).newCompleteCommand(JOB_KEY);
    }

    // ============ verifyChart：正常多维比对 / 缺 subjectTraits 失败 ============
    @Test
    @DisplayName("verifyChart：正常多维比对完成 + 缺特征→throwError")
    void verifyChartBranches() {
        JobClient c1 = client();
        worker.handleVerifyChart(jobWith(baseVars()), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        Map<String, Object> blank = baseVars();
        blank.put("subjectTraits", "   ");
        JobClient c2 = client();
        worker.handleVerifyChart(jobWith(blank), c2);
        verify(c2).newThrowErrorCommand(JOB_KEY);
    }

    // ============ generateNatalReading：成格明细非空 / 空 ============
    @Test
    @DisplayName("generateNatalReading：成格明细非空 + 空")
    void generateNatalReadingBranches() {
        JobClient c1 = client();
        worker.handleGenerateNatalReading(jobWith(baseVars()), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        Map<String, Object> noPattern = baseVars();
        noPattern.put("patternResult", "");
        JobClient c2 = client();
        worker.handleGenerateNatalReading(jobWith(noPattern), c2);
        verify(c2).newCompleteCommand(JOB_KEY);
    }

    // ============ parseBirthInput：公历 / 农历换算 / 空生辰失败 ============
    @Test
    @DisplayName("parseBirthInput：公历 + 农历换算 + 空生辰→throwError")
    void parseBirthInputBranches() {
        JobClient c1 = client();
        worker.handleParseBirthInput(jobWith(baseVars()), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        Map<String, Object> lunar = baseVars();
        lunar.put("calendar", "农历");
        lunar.put("birth", "1990-05-15 10:30");
        JobClient c2 = client();
        worker.handleParseBirthInput(jobWith(lunar), c2);
        verify(c2).newCompleteCommand(JOB_KEY);

        Map<String, Object> blank = baseVars();
        blank.put("birth", "   ");
        JobClient c3 = client();
        worker.handleParseBirthInput(jobWith(blank), c3);
        verify(c3).newThrowErrorCommand(JOB_KEY);
    }

    // ============ analyzeAnnualFortune：年/月/日/未知粒度 + 男/女 + 空盘 ============
    @Test
    @DisplayName("analyzeAnnualFortune：多粒度 + 男女 + 空盘")
    void analyzeAnnualFortuneBranches() {
        for (String gran : new String[]{"年", "月", "日", "未知"}) {
            Map<String, Object> v = baseVars();
            v.put("granularity", gran);
            JobClient c = client();
            worker.handleAnalyzeAnnualFortune(jobWith(v), c);
            verify(c).newCompleteCommand(JOB_KEY);
        }
        // 女命（翻转流年顺逆）+ 空盘（流年四化星未安→“—”）
        Map<String, Object> female = baseVars();
        female.put("chartBase", "SOLAR=1990-05-15T10:30;GENDER=女;LM=4;LD=21;LY=1990");
        female.put("mainStars", "");
        female.put("auxiliaryStars", "");
        female.put("miscStars", "");
        JobClient c2 = client();
        worker.handleAnalyzeAnnualFortune(jobWith(female), c2);
        verify(c2).newCompleteCommand(JOB_KEY);

        // 无主体事件（subjectTraits 空 → majorEventBacktrace 回退文案）
        Map<String, Object> noTraits = baseVars();
        noTraits.put("subjectTraits", "");
        JobClient c3 = client();
        worker.handleAnalyzeAnnualFortune(jobWith(noTraits), c3);
        verify(c3).newCompleteCommand(JOB_KEY);
    }

    // ============ analyzeMajorLimit：成年（已起限）/ 幼龄（未起限 dx<0） ============
    @Test
    @DisplayName("analyzeMajorLimit：成年 + 幼龄未起限")
    void analyzeMajorLimitBranches() {
        JobClient c1 = client();
        worker.handleAnalyzeMajorLimit(jobWith(baseVars()), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        int cy = java.time.LocalDate.now().getYear();
        Map<String, Object> young = baseVars();
        young.put("chartBase", "SOLAR=" + cy + "-06-15T10:30;GENDER=男;LM=4;LD=15;LY=" + cy);
        JobClient c2 = client();
        worker.handleAnalyzeMajorLimit(jobWith(young), c2);
        verify(c2).newCompleteCommand(JOB_KEY);
    }

    // ============ analyzePalaceOverlay：带流月流日 / 无（targetMonth=0 跳过） ============
    @Test
    @DisplayName("analyzePalaceOverlay：带月日 + 无月日")
    void analyzePalaceOverlayBranches() {
        JobClient c1 = client();
        worker.handleAnalyzePalaceOverlay(jobWith(baseVars()), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        Map<String, Object> noMd = baseVars();
        noMd.put("targetMonth", "0");
        noMd.put("targetDay", "0");
        JobClient c2 = client();
        worker.handleAnalyzePalaceOverlay(jobWith(noMd), c2);
        verify(c2).newCompleteCommand(JOB_KEY);
    }

    // ============ aggregateReading：全分节/女命/小儿/文星暗拱 + 空分节变体 ============
    @Test
    @DisplayName("aggregateReading：富集分节 + 文星暗拱 + 空分节/无专论")
    void aggregateReadingBranches() {
        JobClient c1 = client();
        worker.handleAggregateReading(jobWith(baseVars()), c1);
        verify(c1).newCompleteCommand(JOB_KEY);

        Map<String, Object> slim = baseVars();
        slim.put("patternResult", "紫府朝垣");   // 无「文星暗拱」→ 3282 假支
        slim.put("femaleReading", "");
        slim.put("childReading", "");
        slim.put("mingGeGrade", "");
        slim.put("geXingShu", "");
        slim.put("anMingJiXiong", "");
        slim.put("jiaGongGe", "");
        slim.put("tanXingYaoLun", "");
        slim.put("sanFangJiYao", "");
        slim.put("tongYuan", "");
        slim.put("palaceZhiGrid", "");
        JobClient c2 = client();
        worker.handleAggregateReading(jobWith(slim), c2);
        verify(c2).newCompleteCommand(JOB_KEY);
    }

    // ============ determinePattern：整盘格局判定 ============
    @Test
    @DisplayName("determinePattern：整盘格局判定完成")
    void determinePatternBranches() {
        JobClient c = client();
        worker.handleDeterminePattern(jobWith(baseVars()), c);
        verify(c).newCompleteCommand(JOB_KEY);
    }

    // ============ determinePattern：辅弼拱主「来拱」路径回归（inSanFang 传参修正） ============
    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("determinePattern：辅弼拱主·来拱（紫微守命，左辅/右弼入命三方四正，非夹）")
    void determinePatternFuBiGongZhuViaSanFang() {
        Map<String, Object> v = baseVars();
        // 命宫=子(0)，紫微@子守命（MAIN14[0]=紫微@子）；左辅@辰(4)、右弼@申(8) 均入命宫三方四正{0,4,8,6}，
        // 又不居子之两侧邻宫（亥11/丑1）→ 仅「来拱」路径可判（非夹），据此验证 inSanFang 传参修正。
        v.put("auxiliaryStars", "左辅@辰；右弼@申；");
        JobClient c = client();
        worker.handleDeterminePattern(jobWith(v), c);
        ArgumentCaptor<Map> cap = ArgumentCaptor.forClass(Map.class);
        verify(c.newCompleteCommand(JOB_KEY)).variables(cap.capture());
        assertThat(String.valueOf(cap.getValue())).as("辅弼拱主·来拱应命中 patternResult").contains("辅弼拱主");
    }

    // ============ 额外 handler 变体：越界/缺参/空盘/幼龄 ============
    @Test
    @DisplayName("额外 handler 变体：局日越界/验盘空&不匹配/大限缺身宫&空盘/叠宫幼龄/农历越界")
    void extraHandlerVariants() {
        // placeMainStars 日越界（LD=0 / LD=31）
        for (String ld : new String[]{"0", "31"}) {
            Map<String, Object> v = baseVars();
            v.put("chartBase", "SOLAR=1990-05-15T10:30;GENDER=男;LM=4;LD=" + ld + ";LY=1990");
            JobClient c = client();
            worker.handlePlaceMainStars(jobWith(v), c);
            verify(c).newThrowErrorCommand(JOB_KEY);
        }
        // verifyChart 缺特征(null) / 特征不匹配（stated 空→特征不足）
        Map<String, Object> nullTraits = baseVars();
        nullTraits.put("subjectTraits", null);
        JobClient cv1 = client();
        worker.handleVerifyChart(jobWith(nullTraits), cv1);
        verify(cv1).newThrowErrorCommand(JOB_KEY);

        Map<String, Object> noMatch = baseVars();
        noMatch.put("subjectTraits", "完全无关的描述xyz");
        JobClient cv2 = client();
        worker.handleVerifyChart(jobWith(noMatch), cv2);
        verify(cv2).newCompleteCommand(JOB_KEY);

        // majorLimit 缺身宫（shenWeak）/ 空盘（大限四化星未安→“—”）
        Map<String, Object> noShen = baseVars();
        noShen.put("chartSkeleton", "命宫=子;财帛=辰;水二局");
        JobClient cm1 = client();
        worker.handleAnalyzeMajorLimit(jobWith(noShen), cm1);
        verify(cm1).newCompleteCommand(JOB_KEY);

        Map<String, Object> emptyAt = baseVars();
        emptyAt.put("mainStars", "");
        emptyAt.put("auxiliaryStars", "");
        emptyAt.put("miscStars", "");
        emptyAt.put("fourTransform", "");
        JobClient cm2 = client();
        worker.handleAnalyzeMajorLimit(jobWith(emptyAt), cm2);
        verify(cm2).newCompleteCommand(JOB_KEY);

        // overlay 幼龄（dxIdx<0）
        int cy = java.time.LocalDate.now().getYear();
        Map<String, Object> young = baseVars();
        young.put("chartBase", "SOLAR=" + cy + "-06-15T10:30;GENDER=男;LM=4;LD=15;LY=" + cy);
        JobClient co = client();
        worker.handleAnalyzePalaceOverlay(jobWith(young), co);
        verify(co).newCompleteCommand(JOB_KEY);

        // parseBirthInput 农历越界（1850 → 换算失败 else 分支）
        Map<String, Object> outRange = baseVars();
        outRange.put("calendar", "农历");
        outRange.put("birth", "1850-05-15 10:30");
        JobClient cp = client();
        worker.handleParseBirthInput(jobWith(outRange), cp);
        verify(cp).newCompleteCommand(JOB_KEY);
    }
}
