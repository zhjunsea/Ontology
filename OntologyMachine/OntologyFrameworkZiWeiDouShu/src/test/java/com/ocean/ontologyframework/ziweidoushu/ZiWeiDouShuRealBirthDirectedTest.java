package com.ocean.ontologyframework.ziweidoushu;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 真实出生「定向命例」枚举：用生产安星纯函数(bureauOf/ziweiBranch/wuhuYinGan)把真实出生(年干×农历月×农历日×时)
 * 实算成真实主星盘，按(紫微支,命宫)全覆盖取代表，喂给 handleDeterminePattern。**不手摆星**（主星落宫全由生产安星决定）。
 * 覆盖「仅依赖主星/命身/日月支」的真实可达分支（L2222 杀破狼各短路侧、L2494-96 明珠出海各子句）。
 */
@DisplayName("handleDeterminePattern · 真实出生枚举定向覆盖（生产安星实算）")
class ZiWeiDouShuRealBirthDirectedTest {

    private static final long JOB_KEY = 9401L;
    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] ZIWEI_SERIES = {"紫微", "天机", "太阳", "武曲", "天同", "廉贞"};
    private static final int[] ZIWEI_SERIES_OFF = {0, -1, -3, -4, -5, -8};
    private static final String[] TIANFU_SERIES = {"天府", "太阴", "贪狼", "巨门", "天相", "天梁", "七杀", "破军"};
    private static final int[] TIANFU_SERIES_OFF = {0, 1, 2, 3, 4, 5, 6, 10};
    // 辅星（卷二·安星诀；与生产 LUCUN/KUI_YUE/HUOXING/LINGXING/TIANMA 表一致）
    private static final int[] LUCUN = {2, 3, 5, 6, 5, 6, 8, 9, 11, 0};
    private static final int[][] KUI_YUE = {{1, 7}, {0, 8}, {11, 10}, {11, 10}, {1, 7}, {0, 8}, {1, 7}, {2, 6}, {3, 5}, {3, 5}};
    private static final int[] HUOXING = {2, 3, 1, 9, 2, 3, 1, 9, 2, 3, 1, 9};
    private static final int[] LINGXING = {10, 10, 3, 10, 10, 10, 3, 10, 10, 10, 3, 10};
    private static final int[] TIANMA = {2, 11, 8, 5, 2, 11, 8, 5, 2, 11, 8, 5};
    private static final int[] TIAN_GUAN_BR = {7, 4, 5, 2, 3, 9, 11, 9, 10, 6};
    private static final int[] TIAN_FU_BR = {9, 8, 0, 11, 3, 2, 6, 5, 6, 5};

    /** 真实辅/杂星盘串（按生产安星诀逐字复用；覆盖 YUNXIAN_JI 全集：禄存/魁钺/辅弼/昌曲/天官/天福/恩光/天贵 等）。 */
    private static String auxStars(int g, int b, int month, int day, int hour) {
        int lu = LUCUN[g];
        StringBuilder s = new StringBuilder();
        s.append("禄存@").append(ZHI[lu]).append("；");
        s.append("擎羊@").append(ZHI[m12(lu + 1)]).append("；");
        s.append("陀罗@").append(ZHI[m12(lu - 1)]).append("；");
        s.append("天魁@").append(ZHI[KUI_YUE[g][0]]).append("；");
        s.append("天钺@").append(ZHI[KUI_YUE[g][1]]).append("；");
        s.append("火星@").append(ZHI[HUOXING[b]]).append("；");
        s.append("铃星@").append(ZHI[LINGXING[b]]).append("；");
        s.append("天马@").append(ZHI[TIANMA[b]]).append("；");
        s.append("文昌@").append(ZHI[m12(10 - hour)]).append("；");
        s.append("文曲@").append(ZHI[m12(4 + hour)]).append("；");
        s.append("左辅@").append(ZHI[m12(4 + (month - 1))]).append("；");
        s.append("右弼@").append(ZHI[m12(10 - (month - 1))]).append("；");
        s.append("天官@").append(ZHI[TIAN_GUAN_BR[g]]).append("；");
        s.append("天福@").append(ZHI[TIAN_FU_BR[g]]).append("；");
        s.append("恩光@").append(ZHI[m12((10 - hour) + day - 2)]).append("；"); // 文昌起初一顺至生日退一位
        s.append("天贵@").append(ZHI[m12((4 + hour) + day - 2)]).append("；");  // 文曲同法
        return s.toString();
    }

    private static int m12(int x) { return ((x % 12) + 12) % 12; }

    private static Object callStatic(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
    private static String[][] sihuaTable() {
        try {
            Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField("SIHUA");
            f.setAccessible(true);
            return (String[][]) f.get(null);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private ActivatedJob jobWith(Map<String, Object> vars) {
        ActivatedJob job = mock(ActivatedJob.class);
        when(job.getKey()).thenReturn(JOB_KEY);
        when(job.getVariablesAsMap()).thenReturn(vars);
        return job;
    }
    private static JobClient client() { return mock(JobClient.class, RETURNS_DEEP_STUBS); }

    /** 由真实出生(年干,农历月,农历日,时)算 [命宫,身宫,紫微支,主星盘串]；非法局返回 null。 */
    private static Object[] buildChart(int yearGan, int month, int day, int hour) {
        int mingIdx = m12(2 + (month - 1) - hour);
        int shenIdx = m12(2 + (month - 1) + hour);
        int yinGan = (int) callStatic("wuhuYinGan", new Class<?>[]{int.class}, yearGan);
        int mingGan = ((yinGan + m12(mingIdx - 2)) % 10 + 10) % 10;
        int bureau;
        try { bureau = (int) callStatic("bureauOf", new Class<?>[]{int.class, int.class}, mingGan, mingIdx); }
        catch (Exception e) { return null; }
        if (bureau < 2 || bureau > 6) return null;
        int ziwei = (int) callStatic("ziweiBranch", new Class<?>[]{int.class, int.class}, bureau, day);
        int tianfu = m12(4 - ziwei);
        StringBuilder ms = new StringBuilder();
        for (int i = 0; i < ZIWEI_SERIES.length; i++) ms.append(ZIWEI_SERIES[i]).append("@").append(ZHI[m12(ziwei + ZIWEI_SERIES_OFF[i])]).append("；");
        for (int i = 0; i < TIANFU_SERIES.length; i++) ms.append(TIANFU_SERIES[i]).append("@").append(ZHI[m12(tianfu + TIANFU_SERIES_OFF[i])]).append("；");
        return new Object[]{mingIdx, shenIdx, ziwei, ms.toString()};
    }

    @Test
    @DisplayName("枚举真实出生：按(紫微支,命宫)全覆盖取代表，真实盘跑通 determinePattern")
    void realBirthSweep() {
        String[][] sihua = sihuaTable();
        Map<String, int[]> reps = new LinkedHashMap<>(); // (ziwei|ming) -> {yearGan,month,day,hour}
        for (int yearGan = 0; yearGan < 10; yearGan++) {
            for (int month = 1; month <= 12; month++) {
                for (int hour = 0; hour < 12; hour++) {
                    for (int day = 1; day <= 30; day++) {
                        Object[] c = buildChart(yearGan, month, day, hour);
                        if (c == null) continue;
                        String key = c[2] + "|" + c[0];
                        if (!reps.containsKey(key)) reps.put(key, new int[]{yearGan, month, day, hour});
                    }
                }
            }
        }
        assertThat(reps.size()).as("应覆盖较多 (紫微支,命宫) 组合").isGreaterThan(80);
        int run = 0;
        for (Map.Entry<String, int[]> e : reps.entrySet()) {
            int[] b = e.getValue();
            int yearGan = b[0], month = b[1], day = b[2], hour = b[3];
            Object[] c = buildChart(yearGan, month, day, hour);
            int mingIdx = (int) c[0], shenIdx = (int) c[1];
            String mainStars = (String) c[3];
            int solarYear = 1984 + yearGan; // 1984=甲，gan=yearGan
            int hh = (hour == 0) ? 0 : hour * 2;
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("chartBase", "SOLAR=" + solarYear + "-06-15T" + String.format("%02d", hh) + ":00;GENDER=男;LM=" + month + ";LD=" + day + ";LY=" + solarYear);
            v.put("chartSkeleton", "命宫=" + ZHI[mingIdx] + "；身宫=" + ZHI[shenIdx] + "；水二局");
            v.put("mainStars", mainStars);
            v.put("auxiliaryStars", "");
            v.put("miscStars", "");
            v.put("fourTransform", sihua[yearGan][0] + "化禄；" + sihua[yearGan][1] + "化权；" + sihua[yearGan][2] + "化科；" + sihua[yearGan][3] + "化忌");
            JobClient cl = client();
            new ZiWeiDouShuOntologyJobWorker().handleDeterminePattern(jobWith(v), cl);
            verify(cl).newCompleteCommand(JOB_KEY);
            run++;
        }
        System.out.println("REALBIRTH_REPS run=" + run);
    }

    @Test
    @DisplayName("真实出生+真实辅星枚举：覆盖君子在野煞凑等完整盘分支")
    void realBirthAuxSweep() {
        String[][] sihua = sihuaTable();
        int run = 0;
        for (int g = 0; g < 10; g++) {
            for (int b = 0; b < 12; b++) {
                if ((g % 2) != (b % 2)) continue; // 合法干支对（同阴阳）
                for (int month = 1; month <= 12; month++) {
                    for (int hour = 0; hour < 12; hour++) {
                        for (int day = 1; day <= 30; day += 14) { // 1/15/29
                            Object[] c = buildChart(g, month, day, hour);
                            if (c == null) continue;
                            int mingIdx = (int) c[0], shenIdx = (int) c[1];
                            String mainStars = (String) c[3];
                            int solarYear = 1984 + g;
                            int hh = (hour == 0) ? 0 : hour * 2;
                            Map<String, Object> v = new LinkedHashMap<>();
                            v.put("chartBase", "SOLAR=" + solarYear + "-06-15T" + String.format("%02d", hh) + ":00;GENDER=男;LM=" + month + ";LD=" + day + ";LY=" + solarYear);
                            v.put("chartSkeleton", "命宫=" + ZHI[mingIdx] + "；身宫=" + ZHI[shenIdx] + "；水二局");
                            v.put("mainStars", mainStars);
                            v.put("auxiliaryStars", auxStars(g, b, month, day, hour));
                            v.put("miscStars", "");
                            v.put("fourTransform", sihua[g][0] + "化禄；" + sihua[g][1] + "化权；" + sihua[g][2] + "化科；" + sihua[g][3] + "化忌");
                            JobClient cl = client();
                            new ZiWeiDouShuOntologyJobWorker().handleDeterminePattern(jobWith(v), cl);
                            verify(cl).newCompleteCommand(JOB_KEY);
                            run++;
                        }
                    }
                }
            }
        }
        System.out.println("REALBIRTH_AUX run=" + run);
    }

    @Test
    @DisplayName("真实出生+真实杂曜枚举：定向覆盖 羊刃会化禄（擎羊守辰戌丑未·三方无吉·禄权科齐）")
    void realBirthYangRenSweep() {
        String[][] sihua = sihuaTable();
        int run = 0;
        for (int g = 1; g <= 9; g += 2) {           // 这些年干擎羊落 辰/未/戌/丑
            for (int b = 0; b < 12; b++) {
                if ((g % 2) != (b % 2)) continue;
                for (int month = 1; month <= 12; month++) {
                    for (int hour = 0; hour < 12; hour++) {
                        int ming = m12(2 + (month - 1) - hour);
                        int qy = m12(LUCUN[g] + 1);
                        if (ming != qy) continue;       // 擎羊守命
                        for (int day = 1; day <= 30; day++) {
                            Object[] c = buildChart(g, month, day, hour);
                            if (c == null) continue;
                            int shen = (int) c[1];
                            String mainStars = (String) c[3];
                            int solarYear = 1984 + g;
                            int hh = (hour == 0) ? 0 : hour * 2;
                            Map<String, Object> v = new LinkedHashMap<>();
                            v.put("chartBase", "SOLAR=" + solarYear + "-06-15T" + String.format("%02d", hh) + ":00;GENDER=男;LM=" + month + ";LD=" + day + ";LY=" + solarYear);
                            v.put("chartSkeleton", "命宫=" + ZHI[ming] + "；身宫=" + ZHI[shen] + "；水二局");
                            v.put("mainStars", mainStars);
                            v.put("auxiliaryStars", auxStars(g, b, month, day, hour));
                            v.put("miscStars", "");
                            v.put("fourTransform", sihua[g][0] + "化禄；" + sihua[g][1] + "化权；" + sihua[g][2] + "化科；" + sihua[g][3] + "化忌");
                            JobClient cl = client();
                            new ZiWeiDouShuOntologyJobWorker().handleDeterminePattern(jobWith(v), cl);
                            verify(cl).newCompleteCommand(JOB_KEY);
                            run++;
                        }
                    }
                }
            }
        }
        System.out.println("REALBIRTH_YANGREN run=" + run);
    }
}
