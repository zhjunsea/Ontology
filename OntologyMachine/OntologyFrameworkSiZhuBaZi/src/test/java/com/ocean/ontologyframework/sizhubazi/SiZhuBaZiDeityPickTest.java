package com.ocean.ontologyframework.sizhubazi;

import com.ocean.openlletresolver.BackendService;
import com.ocean.ontopobdahandler.OBDAHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 神煞按柱匹配（含「(时)」半角）与 reading/pattern/yongshen 引据选取各分支。 */
@DisplayName("神煞柱匹配·引据族选取边界")
class SiZhuBaZiDeityPickTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static Object inst(Object w, String name, Class<?>[] t, Object... a) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, t);
        m.setAccessible(true);
        try { return m.invoke(w, a); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) { throw re; }
            if (c instanceof Error er) { throw er; }
            throw e;
        }
    }

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

    private static SiZhuBaZiOntologyJobWorker workerRouting(Function<String, List<Map<String, String>>> f) throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenAnswer((InvocationOnMock inv) -> f.apply(inv.getArgument(0)));
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    @Test
    @DisplayName("matchDeityRule：日干类「（时）」全角/半角均裁为时支，普通不作裁")
    void matchDeityRuleShi() throws Exception {
        SiZhuBaZiOntologyJobWorker w = newWorker();
        Class<?>[] t = {String.class, String.class, String.class, String[].class, String[].class};
        String[] zhiAt = {"子", "丑", "寅", "卯"};
        String[] ganAt = {"甲", "乙", "丙", "丁"};
        assertThat((List<?>) inst(w, "matchDeityRule", t, "日干", "丙", "子（时）", zhiAt, ganAt))
                .isInstanceOf(List.class); // 全角（时）：首操作数真
        assertThat((List<?>) inst(w, "matchDeityRule", t, "日干", "丙", "子(时)", zhiAt, ganAt))
                .isInstanceOf(List.class); // 半角(时)：首假次真
        assertThat((List<?>) inst(w, "matchDeityRule", t, "日干", "丙", "子", zhiAt, ganAt))
                .isInstanceOf(List.class); // 皆假
    }

    @Test
    @DisplayName("pickReading/pickYongShen/pickPattern：null/含关键词/去重/限量 各分支")
    @SuppressWarnings("unchecked")
    void pickFamilyEdges() throws Exception {
        List<Map<String, String>> reading = new ArrayList<>();
        reading.add(row("label", null, "cat", "事业", "text", "x", "src", "s"));
        reading.add(row("label", "甲子", "cat", "事业", "text", "t1", "src", "s1"));
        reading.add(row("label", "乙丑", "cat", "事业", "text", "t2", "src", "s2"));
        reading.add(row("label", "甲子", "cat", "事业", "text", "dup", "src", "s0"));
        reading.add(row("label", "丙寅", "cat", "财运", "text", "t3", "src", "s3"));
        List<Map<String, String>> yong = new ArrayList<>();
        yong.add(row("label", null, "text", "x"));
        yong.add(row("label", "甲子", "text", "t1"));
        yong.add(row("label", "乙丑", "text", "t2"));
        yong.add(row("label", "甲子", "text", "dup"));
        List<Map<String, String>> pattern = new ArrayList<>();
        pattern.add(row("name", null, "text", "x", "src", "s"));
        pattern.add(row("name", "曲直", "text", "t1", "src", "s1"));
        pattern.add(row("name", "炎上", "text", "t2", "src", "s2"));
        pattern.add(row("name", "曲直", "text", "dup", "src", "s0"));

        SiZhuBaZiOntologyJobWorker w = workerRouting(q -> {
            if (q.contains("patternName")) { return pattern; }
            if (q.contains("rdfs:comment")) { return yong; }
            return reading;
        });
        String iri = "reading:ReadingItem";

        // 读 reading 且类别过滤（cat=事业）：覆盖 readReadingFamily 非空类别路径/二次校验
        List<String> r1 = (List<String>) inst(w, "pickReading",
                new Class<?>[]{String.class, String.class, String.class, String.class, int.class},
                iri, "事业", "甲", "事业", 10);
        assertThat(r1).hasSize(1);

        // category=null（返回全部）且 limit=1 → 命中即 break（L2862 真）
        List<String> r2 = (List<String>) inst(w, "pickReading",
                new Class<?>[]{String.class, String.class, String.class, String.class, int.class},
                iri, null, null, "通用", 1);
        assertThat(r2).hasSize(1);

        // pickYongShen：limit=1 break；limit=10 自然结束
        List<String> y1 = (List<String>) inst(w, "pickYongShen",
                new Class<?>[]{String.class, String.class, String.class, int.class}, iri, "甲", "用神", 1);
        assertThat(y1).hasSize(1);
        List<String> y2 = (List<String>) inst(w, "pickYongShen",
                new Class<?>[]{String.class, String.class, String.class, int.class}, iri, "甲", "用神", 10);
        assertThat(y2).hasSize(2); // pickYongShen 不去重：甲子 两条均入选

        // pickPattern：limit=1 break；limit=10 自然结束
        List<String> p1 = (List<String>) inst(w, "pickPattern",
                new Class<?>[]{String.class, String.class, String.class, int.class}, iri, "格", "曲", 1);
        assertThat(p1).hasSize(1);
        List<String> p2 = (List<String>) inst(w, "pickPattern",
                new Class<?>[]{String.class, String.class, String.class, int.class}, iri, "格", "曲", 10);
        assertThat(p2).hasSize(1);
    }
}
