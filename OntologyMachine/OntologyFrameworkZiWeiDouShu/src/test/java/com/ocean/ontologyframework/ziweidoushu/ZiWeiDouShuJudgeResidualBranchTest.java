package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 判定族残余分支·可稳健命中批次（离线反射，不依赖 Broker/真实引擎）。
 *
 * <p>对应全环境 JaCoCo 盘点后仍未覆盖、且客观可达（非死代码/非语料缺失）的分支：
 * 空守卫、gradeOf 三方吉煞同现、judgeLiuNianTaiSui 命宫未定短路、judgeTanXingYaoLun 三态、
 * judgeTongYuan 命宫未定守卫与并见星缺位、judgeMingGongGe 空星与全星块遍历（覆盖块边界分支）。</p>
 *
 * <p>语料不可达（财宅/财福/(三方)/非身宫夹身等）与防御性死代码
 * （branches==null / sentStart&lt;0 / branch&lt;0 / fgArr.length==0 / fgFirst.isEmpty()）
 * 不在本类臆造覆盖，另在盘点台账登记。</p>
 */
@DisplayName("判定族残余分支·可达批次")
class ZiWeiDouShuJudgeResidualBranchTest {

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Integer, List<String>> at() {
        return new LinkedHashMap<>();
    }

    private static void put(Map<Integer, List<String>> m, int p, String... stars) {
        m.put(p, new ArrayList<>(Arrays.asList(stars)));
    }

    @Test
    @DisplayName("palaceStarQuote：未知宫位→get 返回 null，走空串守卫")
    void palaceStarQuoteNullRows() {
        assertThat((String) invoke("palaceStarQuote", new Class[]{String.class, String.class},
                "无此宫", "紫微")).isEmpty();
        assertThatCode(() -> invoke("palaceStarQuote", new Class[]{String.class, String.class},
                "命宫", "紫微")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("judgeMingGongGe：star==null 守卫 + 全主星块遍历(覆盖无后继块→end=size)")
    void judgeMingGongGeGuardsAndBlockEdges() {
        assertThat((String) invoke("judgeMingGongGe",
                new Class[]{String.class, int.class, int.class, Map.class},
                (String) null, 0, 0, (Map<Integer, List<String>>) null)).isEmpty();
        String[] mains = {"紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府",
                "太阴", "贪狼", "巨门", "天相", "天梁", "七杀", "破军"};
        for (String s : mains) {
            final String star = s;
            assertThatCode(() -> invoke("judgeMingGongGe",
                    new Class[]{String.class, int.class, int.class, Map.class},
                    star, 0, 0, (Map<Integer, List<String>>) null)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("gradeOf：三方吉煞同现(ji&&sha>0) 与 吉多无煞(ji&&sha==0) 两分支")
    void gradeOfResidualBranches() {
        Map<Integer, List<String>> jiSha = at();
        put(jiSha, 0, "左辅", "文昌", "擎羊");
        assertThat((String) invoke("gradeOf", new Class[]{List.class, Map.class, int.class},
                new ArrayList<String>(), jiSha, 0)).contains("吉凶交参");
        Map<Integer, List<String>> jiOnly = at();
        put(jiOnly, 0, "左辅", "文昌");
        assertThat((String) invoke("gradeOf", new Class[]{List.class, Map.class, int.class},
                new ArrayList<String>(), jiOnly, 0)).contains("上格");
    }

    @Test
    @DisplayName("judgeLiuNianTaiSui：命宫未定短路 + 太岁正临/对照 两态")
    void judgeLiuNianTaiSuiResidual() {
        assertThat((String) invoke("judgeLiuNianTaiSui",
                new Class[]{int.class, int.class, Map.class, String.class},
                -1, 0, at(), (String) null)).contains("太岁");
        assertThat((String) invoke("judgeLiuNianTaiSui",
                new Class[]{int.class, int.class, Map.class, String.class},
                0, 0, at(), (String) null)).contains("正临命宫");
        assertThat((String) invoke("judgeLiuNianTaiSui",
                new Class[]{int.class, int.class, Map.class, String.class},
                0, 1, at(), (String) null)).contains("对照命宫");
    }

    @Test
    @DisplayName("judgeTanXingYaoLun：八座六宫 俱吉/俱凶/交杂 三分支 + 命宫未定")
    void judgeTanXingYaoLunBranches() {
        assertThat((String) invoke("judgeTanXingYaoLun",
                new Class[]{int.class, Map.class, String.class}, -1, at(), (String) null))
                .contains("命宫未定");
        // 命宫已定但骨架缺失 → total==0，覆盖 L6103/L6105 的 total>0 假分支
        assertThat((String) invoke("judgeTanXingYaoLun",
                new Class[]{int.class, Map.class, String.class}, 0, at(), (String) null))
                .contains("吉凶交杂");

        String skel = "命宫=子；身宫=子；迁移=子；财帛=子；官禄=子；福德=子";
        Map<Integer, List<String>> allJi = at();
        put(allJi, 0, "左辅", "文昌", "天魁");
        assertThat((String) invoke("judgeTanXingYaoLun",
                new Class[]{int.class, Map.class, String.class}, 0, allJi, skel))
                .contains("富贵高寿");
        Map<Integer, List<String>> allSha = at();
        put(allSha, 0, "擎羊", "陀罗", "火星");
        assertThat((String) invoke("judgeTanXingYaoLun",
                new Class[]{int.class, Map.class, String.class}, 0, allSha, skel))
                .contains("孤寡夭寿");
        Map<Integer, List<String>> mix = at();
        put(mix, 0, "左辅", "擎羊");
        assertThat((String) invoke("judgeTanXingYaoLun",
                new Class[]{int.class, Map.class, String.class}, 0, mix, skel))
                .contains("吉凶交杂");
        // 六宫仅二宫见吉、无煞 且吉未过半 → L6103「jiCnt>=half+1」假分支
        String skel2 = "命宫=子；身宫=丑；迁移=寅；财帛=卯；官禄=辰；福德=巳";
        Map<Integer, List<String>> fewJi = at();
        put(fewJi, 0, "左辅");
        put(fewJi, 1, "文昌");
        assertThat((String) invoke("judgeTanXingYaoLun",
                new Class[]{int.class, Map.class, String.class}, 0, fewJi, skel2))
                .contains("吉凶交杂");
        // 六宫仅二宫见煞、无吉 且煞未过半 → L6105「shaCnt>=half+1」假分支
        Map<Integer, List<String>> fewSha = at();
        put(fewSha, 0, "擎羊");
        put(fewSha, 1, "陀罗");
        assertThat((String) invoke("judgeTanXingYaoLun",
                new Class[]{int.class, Map.class, String.class}, 0, fewSha, skel2))
                .contains("吉凶交杂");
    }

    @Test
    @DisplayName("judgeTongYuan：mingIdx<0 守卫 + 并见星缺位(sb==null) 路径")
    void judgeTongYuanGuards() {
        assertThat((String) invoke("judgeTongYuan",
                new Class[]{int.class, Map.class, int.class, String.class, Boolean.class, String.class},
                -1, at(), 0, (String) null, (Boolean) null, (String) null)).contains("命宫未定");
        String skel = "命宫=子；身宫=子；财帛=子；福德=子";
        assertThatCode(() -> invoke("judgeTongYuan",
                new Class[]{int.class, Map.class, int.class, String.class, Boolean.class, String.class},
                0, at(), 0, skel, (Boolean) null, (String) null)).doesNotThrowAnyException();
    }
}
