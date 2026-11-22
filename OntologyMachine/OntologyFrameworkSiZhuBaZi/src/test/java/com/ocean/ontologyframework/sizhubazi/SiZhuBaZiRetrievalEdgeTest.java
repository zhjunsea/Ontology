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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 引据/读取族·精确补测：用「含 null 标签 / 重复标签 / 空类目 / 体象 / 篇名 / 具名目录」的受控行，
 * 覆盖 readReadingFamily / pickReading / pickPattern / pickYongShen / citePatternCatalog /
 * citeTechniqueBody / citeFu 的去空、过滤、去重、匹配分支（纯工程逻辑，不涉命理判理真值）。
 */
@DisplayName("引据/读取族·精确补测（去空/过滤/去重/体象/篇名/具名目录）")
class SiZhuBaZiRetrievalEdgeTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static SiZhuBaZiOntologyJobWorker newWorker() throws Exception {
        Constructor<?> c = WORKER.getDeclaredConstructor();
        c.setAccessible(true);
        return (SiZhuBaZiOntologyJobWorker) c.newInstance();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object call(Object w, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(w, args);
    }

    private static Map<String, String> row(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @SafeVarargs
    private static List<Map<String, String>> list(Map<String, String>... rows) {
        return new ArrayList<>(Arrays.asList(rows));
    }

    /** 定制假 OBDA：返回含「null 标签 / 重复标签 / 多类目」的受控行，专门触发去空/去重/过滤分支。 */
    private static List<Map<String, String>> canned(String q) {
        if (q.contains("AssimilationPattern"))
            return list(row("label", "从象", "name", "从象", "text", "从象原文", "src", "滴天髓"),
                        row("label", "x", "name", null, "text", "空名", "src", "s"));
        if (q.contains("ImagePattern"))
            return list(row("label", "形象", "name", "形象", "text", "形象原文", "src", "滴天髓"));
        if (q.contains("DirectionPattern"))
            return list(row("label", "方局", "name", "方局", "text", "方局原文", "src", "滴天髓"));
        if (q.contains("pattern:Pattern"))
            return list(row("label", "食神格", "name", "食神格", "text", "食神格原文", "src", "三命通会卷五"),
                        row("label", "食神格副", "name", "食神格", "text", "重复名", "src", "s"),
                        row("label", "空名", "name", null, "text", "空名", "src", "s"));
        if (q.contains("yongshen:BingYao"))
            return list(row("label", "病药说", "text", "病药原文"),
                        row("label", "其他说", "text", "非病药"),
                        row("label", null, "text", "空标签"));
        if (q.contains("reading:ReadingItem"))
            return list(
                    row("label", "甲体象", "cat", "十干体象", "text", "甲木参天", "src", "三命通会"),
                    row("label", "甲体象", "cat", "十干体象", "text", "重复甲", "src", "s"),
                    row("label", "午体象", "cat", "十二支体象", "text", "午火", "src", "三命通会"),
                    row("label", "论用神", "cat", "子平真诠", "text", "论用神原文", "src", "子平真诠"),
                    row("label", null, "cat", "子平真诠", "text", "空标签", "src", "s"));
        if (q.contains("reading:CategoryFortune"))
            return list(
                    row("label", "官星赋", "cat", "", "text", "官星赋原文", "src", "渊海子平"),
                    row("label", "官星赋", "cat", "", "text", "重复官星", "src", "s"),
                    row("label", "财神论", "cat", "", "text", "财神论原文", "src", "渊海子平"),
                    row("label", null, "cat", "", "text", "空标签", "src", "s"));
        return new ArrayList<>();
    }

    private static SiZhuBaZiOntologyJobWorker workerWithCustomObda() throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenAnswer((InvocationOnMock inv) -> canned(inv.getArgument(0)));
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    @Test
    @DisplayName("readReadingFamily·空类目(null/blank)与类目过滤两侧")
    @SuppressWarnings("unchecked")
    void readFamilyBlankAndFilter() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithCustomObda();
        List<Map<String, String>> all = (List<Map<String, String>>) call(w, "readReadingFamily",
                new Class<?>[]{String.class, String.class}, "reading:ReadingItem", null);
        assertThat(all).hasSize(5);
        List<Map<String, String>> allBlank = (List<Map<String, String>>) call(w, "readReadingFamily",
                new Class<?>[]{String.class, String.class}, "reading:ReadingItem", "");
        assertThat(allBlank).as("空串类目按空类目处理").hasSize(5);
        List<Map<String, String>> filtered = (List<Map<String, String>>) call(w, "readReadingFamily",
                new Class<?>[]{String.class, String.class}, "reading:ReadingItem", "子平真诠");
        assertThat(filtered).as("按类目过滤命中").isNotEmpty();
        for (Map<String, String> r : filtered) {
            assertThat(r.get("cat")).isEqualTo("子平真诠");
        }
    }

    @Test
    @DisplayName("pickReading·跳过 null 标签 + 去重 + 关键词过滤")
    @SuppressWarnings("unchecked")
    void pickReadingEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithCustomObda();
        List<String> out = (List<String>) call(w, "pickReading",
                new Class<?>[]{String.class, String.class, String.class, String.class, int.class},
                "reading:ReadingItem", null, null, "T", 10);
        assertThat(out).as("去空+去重后=甲体象/午体象/论用神").hasSize(3);
        List<String> kw = (List<String>) call(w, "pickReading",
                new Class<?>[]{String.class, String.class, String.class, String.class, int.class},
                "reading:ReadingItem", null, "体象", "T", 10);
        assertThat(kw).as("仅体象").hasSize(2);
    }

    @Test
    @DisplayName("pickPattern·跳过 null 名 + 去重")
    @SuppressWarnings("unchecked")
    void pickPatternEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithCustomObda();
        List<String> out = (List<String>) call(w, "pickPattern",
                new Class<?>[]{String.class, String.class, String.class, int.class},
                "pattern:Pattern", "T", null, 10);
        assertThat(out).as("食神格去重后=1").hasSize(1);
    }

    @Test
    @DisplayName("pickYongShen·跳过 null 标签")
    @SuppressWarnings("unchecked")
    void pickYongShenEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithCustomObda();
        List<String> out = (List<String>) call(w, "pickYongShen",
                new Class<?>[]{String.class, String.class, String.class, int.class},
                "yongshen:BingYao", "病药", "T", 10);
        assertThat(out).as("病药说 1 条（null 跳过）").hasSize(1);
    }

    @Test
    @DisplayName("citeFu·跳过 null + 重复标签去重 + 关键词匹配")
    @SuppressWarnings("unchecked")
    void citeFuEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithCustomObda();
        List<String> out = (List<String>) call(w, "citeFu",
                new Class<?>[]{java.util.Collection.class}, java.util.Arrays.asList("官星"));
        assertThat(out).as("官星赋一条（重复/空/无关键词者排除）").hasSize(1);
    }

    @Test
    @DisplayName("citePatternCatalog·null/空入参返空；重复命中去重")
    @SuppressWarnings("unchecked")
    void citePatternCatalogEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithCustomObda();
        List<String> n = (List<String>) call(w, "citePatternCatalog", new Class<?>[]{java.util.List.class}, (Object) null);
        assertThat(n).isEmpty();
        List<String> e = (List<String>) call(w, "citePatternCatalog", new Class<?>[]{java.util.List.class}, new java.util.ArrayList<>());
        assertThat(e).isEmpty();
        List<String[]> hits = new java.util.ArrayList<>();
        hits.add(new String[]{"食神格"});
        hits.add(new String[]{"食神格"});
        List<String> dup = (List<String>) call(w, "citePatternCatalog", new Class<?>[]{java.util.List.class}, hits);
        assertThat(dup).as("重复命中去重=1").hasSize(1);
    }

    @Test
    @DisplayName("citeTechniqueBody·十干/十二支体象匹配与不匹配两侧")
    @SuppressWarnings("unchecked")
    void citeTechniqueBodyEdges() throws Exception {
        SiZhuBaZiOntologyJobWorker w = workerWithCustomObda();
        List<String> hit = (List<String>) call(w, "citeTechniqueBody", new Class<?>[]{String.class, String.class}, "甲", "午");
        assertThat(hit).as("甲体象+午体象").hasSize(2);
        List<String> miss = (List<String>) call(w, "citeTechniqueBody", new Class<?>[]{String.class, String.class}, "癸", "亥");
        assertThat(miss).as("无对应体象").isEmpty();
    }
}
