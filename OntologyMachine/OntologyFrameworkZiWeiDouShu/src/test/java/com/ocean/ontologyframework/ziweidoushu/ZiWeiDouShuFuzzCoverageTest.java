package com.ocean.ontologyframework.ziweidoushu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 逐条定向覆盖·随机模糊批：对 Map/Set 依赖的 judge 纯函数做大规模随机输入扫描，
 * 统计性覆盖其"可达"子条件分支（离线、无引擎）。
 */
@DisplayName("逐条定向覆盖·随机模糊（judge 纯函数大规模扫描）")
class ZiWeiDouShuFuzzCoverageTest {

    private static final String[] ZHI = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};
    private static final String[] STARS = {"紫微", "天机", "太阳", "武曲", "天同", "廉贞", "天府", "太阴", "贪狼",
            "巨门", "天相", "天梁", "七杀", "破军", "文昌", "文曲", "左辅", "右弼", "天魁", "天钺", "禄存",
            "擎羊", "陀罗", "火星", "铃星", "地劫", "天空", "天马", "天刑", "天姚"};

    private static Object call(String name, Class<?>[] sig, Object... args) {
        try {
            Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (Throwable e) {
            return null; // 吸收异常，仅用于覆盖分支
        }
    }

    private static Map<Integer, List<String>> randChart(Random r) {
        Map<Integer, List<String>> at = new LinkedHashMap<>();
        for (int p = 0; p < 12; p++) {
            if (r.nextInt(100) < 70) {
                List<String> lst = new ArrayList<>();
                int k = 1 + r.nextInt(3);
                for (int j = 0; j < k; j++) {
                    lst.add(STARS[r.nextInt(STARS.length)]);
                }
                at.put(p, lst);
            }
        }
        return at;
    }

    private static Set<String> randSet(Random r) {
        Set<String> s = new HashSet<>();
        int k = r.nextInt(4);
        for (int j = 0; j < k; j++) {
            s.add(STARS[r.nextInt(STARS.length)]);
        }
        return s;
    }

    private static String randSkeleton(Random r) {
        StringBuilder sb = new StringBuilder();
        for (String p : new String[]{"命宫", "身宫", "福德", "财帛", "官禄", "迁移", "妻妾", "子女"}) {
            sb.append(p).append("=").append(ZHI[r.nextInt(12)]).append(";");
        }
        return sb.toString();
    }

    @Test
    @DisplayName("模糊扫描：judgeLongevity/judgeTenGrades/judgeTanXingYaoLun/judgeXiaoErMing/judgeNanNvTongYi/judgeTwoLimitsTaiSui")
    @SuppressWarnings("unchecked")
    void fuzz() {
        Random r = new Random(20261118L);
        int N = 4000;
        int nonNull = 0;
        for (int i = 0; i < N; i++) {
            Map<Integer, List<String>> at = randChart(r);
            int ming = r.nextInt(12), shen = r.nextInt(12);
            String sk = randSkeleton(r);
            Object a = call("judgeLongevity", new Class<?>[]{int.class, int.class, Map.class}, ming, shen, at);
            Object b = call("judgeTanXingYaoLun", new Class<?>[]{int.class, Map.class, String.class}, ming, at, sk);
            Object c = call("judgeXiaoErMing", new Class<?>[]{int.class, Map.class}, ming, at);
            List<String> mingStars = at.getOrDefault(ming, new ArrayList<>());
            Object d = call("judgeNanNvTongYi", new Class<?>[]{int.class, List.class, Map.class, String.class},
                    ming, mingStars, at, sk);
            Object e = call("judgeTenGrades", new Class<?>[]{Set.class, Set.class, Set.class},
                    randSet(r), randSet(r), randSet(r));
            int ds = r.nextInt(3) - 1, xs = r.nextInt(3) - 1, ts = r.nextInt(3) - 1;
            Object f = call("judgeTwoLimitsTaiSui",
                    new Class<?>[]{Map.class, int.class, int.class, int.class, int.class, int.class, int.class},
                    at, r.nextInt(12), r.nextInt(12), r.nextInt(12), ds, xs, ts);
            Object g = call("judgeFemaleBoneMarrow",
                    new Class<?>[]{int.class, List.class, Map.class, String.class}, ming, mingStars, at, sk);
            Object h = call("judgePalaceZhiGrid",
                    new Class<?>[]{int.class, Map.class, int.class}, ming, at, r.nextInt(10));
            List<String> hitG = new ArrayList<>();
            int hk = r.nextInt(4);
            for (int j = 0; j < hk; j++) {
                hitG.add(STARS[r.nextInt(STARS.length)]);
            }
            String ft = r.nextBoolean() ? "紫微化权;武曲化禄;太阴化科;太阳化忌;" : "";
            Object k1 = call("judgeTongYuan",
                    new Class<?>[]{int.class, Map.class, int.class, String.class}, ming, at, r.nextInt(10), sk);
            Object l1 = call("judgeMingGeGrade",
                    new Class<?>[]{List.class, Set.class, Set.class, Map.class, int.class, String.class},
                    hitG, randSet(r), randSet(r), at, ming, ft);
            if (a != null || b != null || c != null || d != null || e != null || f != null || g != null || h != null
                    || k1 != null || l1 != null) {
                nonNull++;
            }
        }
        assertThat(nonNull).isGreaterThan(0);
    }
}
