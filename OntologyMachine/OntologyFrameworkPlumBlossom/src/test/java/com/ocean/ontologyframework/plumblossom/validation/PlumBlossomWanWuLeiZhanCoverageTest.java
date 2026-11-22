package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 卷之一·八卦万物类占（八宫×类目，照录）覆盖测试。
 *
 * <p>直接读取 classpath 资源 `plumblossom/wanwu-leizhan.tsv`（据 epub 原文结构化），核验：
 * 八宫齐备、条目总数、跨行续读补齐、脚注/全角括号清洗、按语；并核验各类之应逐宫可取。
 */
@DisplayName("卷之一·八卦万物类占（八宫×类目·照录）覆盖")
public class PlumBlossomWanWuLeiZhanCoverageTest {

    private static Object callStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method m = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(null, args);
    }

    private Object note(PlumBlossomOntologyJobWorker w, String hex) throws Exception {
        Method m = PlumBlossomOntologyJobWorker.class.getDeclaredMethod("wanWuLeiZhanNote", String.class);
        m.setAccessible(true);
        return m.invoke(w, hex);
    }

    @Test
    @DisplayName("WLZ-01 资源装载：八宫齐备、条目 215、按语非空")
    @SuppressWarnings("unchecked")
    void tableLoaded() throws Exception {
        Map<String, Map<String, String>> t =
                (Map<String, Map<String, String>>) callStatic("wanWuLeiZhanTable", new Class<?>[]{});
        assertEquals(8, t.size(), "应八宫齐备");
        int rows = 0;
        for (Map<String, String> m : t.values()) {
            rows += m.size();
        }
        assertEquals(215, rows, "八宫×类目总条目数");
        Object epi = com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker.class
                .getDeclaredField("WAN_WU_LEI_ZHAN_EPILOGUE");
        ((java.lang.reflect.Field) epi).setAccessible(true);
        assertTrue(String.valueOf(((java.lang.reflect.Field) epi).get(null)).contains("右万物之象"));
    }

    @Test
    @DisplayName("WLZ-02 逐宫取类占：乾/坤/震/坎/离 关键类目与续行/清洗正确")
    void perPalace() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        String qian = String.valueOf(note(w, "乾为天"));
        assertTrue(qian.contains("乾宫"));
        assertTrue(qian.contains("天时：天、冰、雹、霰"));
        assertTrue(qian.contains("地理：西北方"));
        assertTrue(qian.contains("五味：辛、辣"));

        String kun = String.valueOf(note(w, "坤为地"));
        assertTrue(kun.contains("坤宫"));
        assertTrue(kun.contains("天时：云阴、雾气"));

        // 震·静物 为跨行续读补齐（原文跨行）
        String zhen = String.valueOf(note(w, "震为雷"));
        assertTrue(zhen.contains("静物：木竹、萑苇、乐器（属竹木者）、花草繁鲜之物"));

        // 坎·五色 原为［黑］，清洗后为「黑」
        String kan = String.valueOf(note(w, "坎为水"));
        assertTrue(kan.contains("五色：黑"));

        String li = String.valueOf(note(w, "离为火"));
        assertTrue(li.contains("离宫"));
        assertTrue(li.contains("地理：南方、干亢之地"));
    }

    @Test
    @DisplayName("WLZ-03 其余宫（巽/艮/兑）可取；空/未列卦返回空")
    void otherPalacesAndEmpty() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        assertTrue(String.valueOf(note(w, "巽为风")).contains("巽宫"));
        assertTrue(String.valueOf(note(w, "艮为山")).contains("艮宫"));
        assertTrue(String.valueOf(note(w, "兑为泽")).contains("兑宫"));
        assertEquals("", String.valueOf(note(w, "")));
        assertEquals("", String.valueOf(note(w, "未列之卦")));
    }
}
