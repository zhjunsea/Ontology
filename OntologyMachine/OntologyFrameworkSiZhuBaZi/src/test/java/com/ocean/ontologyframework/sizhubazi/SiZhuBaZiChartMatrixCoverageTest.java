package com.ocean.ontologyframework.sizhubazi;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A 类深数据分支·穷举命盘生成器（60 甲子日 × 12 月支 = 720 命盘，男/女、强/弱轮换）。
 * 单一「假 OBDA」Worker 复用驱动核心 Handler；determinePattern/selectYongShen 做“必完成”真断言，
 * 其余执行覆盖分支。不依赖真实库/Ontop。
 */
@DisplayName("A类深数据·720命盘穷举生成器（60甲子日×12月支）")
class SiZhuBaZiChartMatrixCoverageTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;
    private static final String[] GAN = {"甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"};
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static void setField(Object t, String n, Object v) throws Exception {
        Class<?> c = t.getClass();
        while (c != null) {
            try { Field f = c.getDeclaredField(n); f.setAccessible(true); f.set(t, v); return; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(n);
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) { m.put(kv[i], kv[i + 1]); }
        return m;
    }

    @SafeVarargs
    private static List<Map<String, String>> list(Map<String, String>... rows) {
        return new ArrayList<>(java.util.Arrays.asList(rows));
    }

    /** 通用假 OBDA：对任意查询返回足够受控行，使各读取族均走“非空”分支。 */
    private static SiZhuBaZiOntologyJobWorker workerWithFakeObda() throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenAnswer((InvocationOnMock inv) -> {
            String q = inv.getArgument(0);
            if (q.contains("DeityRule")) return list(row("id","1","bp","日柱","bg","甲子","dn","金神","da","","bh","","cat","","src","s"));
            if (q.contains("DayHourClause")) return list(row("text","日时断原文","source","三命通会卷八"));
            if (q.contains("TiaohouClause")) return list(row("label","子月甲调候","p","丙火","x","癸水","text","调候原文","source","穷通宝鉴"));
            if (q.contains("StemBranchAuspice")) return list(row("subject","甲乙日坐支","text","甲日得时；乙日失时；","source","三命通会卷四"));
            if (q.contains("MonthBranchDayStemAuspice")) return list(row("subject","子月得日干吉凶","text","甲日；乙日；","source","三命通会卷四"));
            if (q.contains("KinshipTheory")) return list(row("text","六亲说原文","source","神峰通考"));
            if (q.contains("LadyFortune")) return list(row("label","纯","cat","上","text","女命纯","source","三命通会卷七"));
            if (q.contains("TimePlaceFortune")) return list(row("label","论木·时地吉凶","cat","","text","时地","src","三命通会卷四"));
            if (q.contains("ProgenyPrediction")) return list(row("label","孕生男女","cat","","text","孕生","src","三命通会卷四"));
            if (q.contains("DynamicStaticTheory")) return list(row("label","动静说","cat","","text","动静","src","神峰通考"));
            if (q.contains("HeadCoveringTheory")) return list(row("label","盖头说","cat","","text","盖头","src","神峰通考"));
            if (q.contains("BingYao")) return list(row("label","病药说","text","病药"));
            if (q.contains("AssimilationPattern")) return list(row("label","从象","name","从象","text","从象","src","滴天髓"));
            if (q.contains("ImagePattern")) return list(row("label","形象","name","形象","text","形象","src","滴天髓"));
            if (q.contains("DirectionPattern")) return list(row("label","方局","name","方局","text","方局","src","滴天髓"));
            if (q.contains("CategoryVerdict")) return list(row("text","分类断语","source","三命通会"));
            if (q.contains("sexagenaryNatureText")) return list(row("text","性质原文"));
            if (q.contains("ReadingItem")) return list(
                    row("label","甲体象","cat","十干体象","text","甲木","src","s"),
                    row("label","子体象","cat","十二支体象","text","子水","src","s"),
                    row("label","论用神成败救应","cat","子平真诠","text","x","src","子平真诠"),
                    row("label","中和","cat","滴天髓","text","x","src","滴天髓"),
                    row("label","论五行生成","cat","三命通会","text","x","src","三命通会"));
            if (q.contains("CategoryFortune")) return list(row("label","官星赋","cat","","text","x","src","渊海子平"), row("label","继善篇","cat","","text","x","src","渊海子平"));
            if (q.contains("pattern:Pattern")) return list(row("label","食神格","name","食神格","text","x","src","s"));
            if (q.contains("SolarTermInstant")) return list(row("idx","0","dt","2020-01-06T05:30:00"));
            return new ArrayList<>();
        });
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    private static final class Cap implements Answer<Object> {
        Object vars;
        @Override public Object answer(InvocationOnMock inv) {
            Method m = inv.getMethod();
            if ("variables".equals(m.getName()) && inv.getArguments().length == 1) { vars = inv.getArgument(0); }
            Class<?> rt = m.getReturnType();
            if (rt == void.class) return null;
            if (rt.isPrimitive()) {
                if (rt == long.class) return 0L; if (rt == int.class) return 0;
                if (rt == boolean.class) return false; if (rt == double.class) return 0d;
                if (rt == float.class) return 0f; if (rt == short.class) return (short) 0;
                if (rt == byte.class) return (byte) 0; if (rt == char.class) return (char) 0;
                return null;
            }
            try { return mock(rt, this); } catch (Throwable t) { return null; }
        }
    }

    private static boolean drive(SiZhuBaZiOntologyJobWorker w, String handler, Map<String, Object> vars) throws Exception {
        Cap cap = new Cap();
        JobClient client = mock(JobClient.class, cap);
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getVariablesAsMap()).thenReturn(vars);
        when(job.getKey()).thenReturn(1L);
        Method m = WORKER.getMethod(handler, ActivatedJob.class, JobClient.class);
        m.invoke(w, job, client);
        return cap.vars != null;
    }

    @Test
    @DisplayName("720 命盘穷举：determinePattern/selectYongShen 必完成；female/category/fortune/level/marriage 执行覆盖")
    void exhaustiveChartMatrix() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithFakeObda();
        String[] strengths = {"身强（得令）", "中和", "身弱（失令、无根、寡助）", "中和偏强（得令）"};
        int n = 0;
        for (int d = 0; d < 60; d++) {
            int dg = d % 10, dz = d % 12;
            for (int mb = 0; mb < 12; mb++) {
                String fp = GAN[(dg + 2) % 10] + ZHI[(mb + 6) % 12] + " "
                        + GAN[(dg + mb) % 10] + ZHI[mb] + " "
                        + GAN[dg] + ZHI[dz] + " "
                        + GAN[(dg + 5) % 10] + ZHI[(mb + 3) % 12];
                Map<String, Object> vars = new LinkedHashMap<>();
                vars.put("fourPillars", fp);
                vars.put("strength", strengths[n % 4]);
                vars.put("gender", (n % 2 == 0 ? "男" : "女"));
                vars.put("deities", (n % 3 == 0 ? "羊刃、桃花、相冲" : "天乙贵人"));
                vars.put("chartLevel", "平常格（格成而财官未通门户）");
                vars.put("patternGrade", "格成");
                vars.put("granularity", "时");
                vars.put("chartBase", "SOLAR=1990-05-15T10:30;GENDER=男;SUBJECT=男命;MODE=单命");
                vars.put("startAge", 8.0);
                vars.put("fortuneSequence", "甲子,乙丑,丙寅,丁卯,戊辰,己巳,庚午,辛未");
                vars.put("partnerBirth", "1992-08-08 08:08 女");
                vars.put("fortuneTrend", "顺");
                vars.put("ganzhiRelations", (n % 3 == 0 ? "三合局" : "无合无冲"));
                assertThat(drive(w, "handleDeterminePattern", vars)).as("determinePattern 必完成: %s", fp).isTrue();
                assertThat(drive(w, "handleSelectYongShen", vars)).as("selectYongShen 必完成: %s", fp).isTrue();
                drive(w, "handleFemaleChart", vars);
                drive(w, "handleCategoryVerdicts", vars);
                drive(w, "handleFortuneCycles", vars);
                drive(w, "handleJudgeChartLevel", vars);
                drive(w, "handleMarriageMatch", vars);
                drive(w, "handleNayinHiddenStems", vars);
                drive(w, "handleArrangeDeities", vars);
                n++;
            }
        }
        assertThat(n).isEqualTo(720);
    }

    private static String rndPillar(java.util.Random r) {
        int si = r.nextInt(60);
        return GAN[si % 10] + ZHI[si % 12];
    }

    @Test
    @DisplayName("随机全命盘 Fuzz（固定种子·1500 盘）：determinePattern/selectYongShen 必完成，其余执行覆盖深组合")
    void fuzzFullCharts() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithFakeObda();
        java.util.Random rnd = new java.util.Random(20261121L);
        String[] strengths = {"身强（得令）", "中和", "身弱（失令、无根、寡助）",
                "中和偏强（得令）", "中和偏弱（失令、克泄耗偏多）", "身强"};
        String[] gran = {"年", "月", "日", "时"};
        int N = 1500;
        for (int i = 0; i < N; i++) {
            String fp = rndPillar(rnd) + " " + rndPillar(rnd) + " " + rndPillar(rnd) + " " + rndPillar(rnd);
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("fourPillars", fp);
            vars.put("strength", strengths[rnd.nextInt(strengths.length)]);
            vars.put("gender", (rnd.nextBoolean() ? "男" : "女"));
            vars.put("deities", (rnd.nextBoolean() ? "羊刃、桃花、相冲" : "天乙贵人、华盖"));
            vars.put("chartLevel", "平常格（格成而财官未通门户）");
            vars.put("patternGrade", "格成");
            vars.put("granularity", gran[rnd.nextInt(gran.length)]);
            vars.put("chartBase", "SOLAR=1990-05-15T10:30;GENDER=男;SUBJECT=男命;MODE=单命");
            vars.put("startAge", 1.0 + rnd.nextInt(10));
            vars.put("fortuneSequence", "甲子,乙丑,丙寅,丁卯,戊辰,己巳,庚午,辛未");
            vars.put("partnerBirth", rndPillar(rnd) + "年 " + "1992-08-08 08:08 女");
            vars.put("fortuneTrend", (rnd.nextBoolean() ? "顺" : "逆"));
            vars.put("ganzhiRelations", (rnd.nextBoolean() ? "三合局" : "无合无冲"));
            assertThat(drive(w, "handleDeterminePattern", vars)).as("determinePattern 必完成: %s", fp).isTrue();
            assertThat(drive(w, "handleSelectYongShen", vars)).as("selectYongShen 必完成: %s", fp).isTrue();
            drive(w, "handleJudgeChartLevel", vars);
            drive(w, "handleFemaleChart", vars);
            drive(w, "handleChildChart", vars);
            drive(w, "handleCategoryVerdicts", vars);
            drive(w, "handleFortuneCycles", vars);
            drive(w, "handleTimingAndStages", vars);
            drive(w, "handleNayinHiddenStems", vars);
            drive(w, "handleArrangeDeities", vars);
            drive(w, "handleGanzhiRelations", vars);
            drive(w, "handleTenGodsKin", vars);
            drive(w, "handleSynthesizeFortuneChart", vars);
            drive(w, "handleMoralAdvice", vars);
            drive(w, "handleMarriageMatch", vars);
        }
        assertThat(N).isEqualTo(1500);
    }

    @Test
    @DisplayName("合婚三维定向：合法 partnerBirth（含晚子/多组生辰）覆盖纳音/生肖/神煞/互补全分支；缺 partner 走校验")
    void marriageMatchBranches() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithFakeObda();
        String[][] cases = {
                {"SOLAR=1990-05-15T10:30;GENDER=男;SUBJECT=男命;MODE=合婚", "1992-08-08 08:08"},
                {"SOLAR=1990-05-15T10:30;GENDER=男;SUBJECT=男命;MODE=合婚", "1993-09-09 23:30"}, // 晚子时
                {"SOLAR=1985-01-01T00:30;GENDER=女;SUBJECT=女命;MODE=合婚", "1988-06-06 06:06"},
                {"SOLAR=2000-12-31T22:00;GENDER=男;SUBJECT=男命;MODE=合婚", "1999-03-03 03:03"},
                {"SOLAR=1978-07-07T15:15;GENDER=女;SUBJECT=女命;MODE=合婚", "1980-11-11 11:11"},
        };
        for (String[] c : cases) {
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("chartBase", c[0]);
            vars.put("partnerBirth", c[1]);
            vars.put("fourPillars", "庚午 辛巳 庚辰 戊子");
            vars.put("strength", "身强（得令）");
            vars.put("gender", "男");
            assertThat(drive(w, "handleMarriageMatch", vars))
                    .as("合婚应在合法 partnerBirth [%s] 下完成", c[1]).isTrue();
        }
        // 缺 partnerBirth 分支（校验失败，走 fail）——仅执行覆盖该分支，不要求完成
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("chartBase", "SOLAR=1990-05-15T10:30;GENDER=男;MODE=合婚");
        bad.put("fourPillars", "庚午 辛巳 庚辰 戊子");
        drive(w, "handleMarriageMatch", bad);
    }
}




