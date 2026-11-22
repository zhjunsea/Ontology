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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 复合条件单操作数边界：pinyinGanzhi / tianKeDiChong / inCategory / tenGodOntologyMap。 */
@DisplayName("操作数边界·拼音/天克地冲/十神类目/十神映射字段")
class SiZhuBaZiOperandEdgeTest {

    private static final Class<?> WORKER = SiZhuBaZiOntologyJobWorker.class;

    private static Object stat(String name, Class<?>[] t, Object... a) throws Exception {
        Method m = WORKER.getDeclaredMethod(name, t);
        m.setAccessible(true);
        try { return m.invoke(null, a); }
        catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) { throw re; }
            if (c instanceof Error er) { throw er; }
            throw e;
        }
    }

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

    private static SiZhuBaZiOntologyJobWorker workerWithObda(Function<String, List<Map<String, String>>> f) throws Exception {
        OBDAHandler obda = mock(OBDAHandler.class);
        when(obda.executeAboxQuery(anyString())).thenAnswer((InvocationOnMock inv) -> f.apply(inv.getArgument(0)));
        BackendService bs = mock(BackendService.class);
        when(bs.getObdaHandler()).thenReturn(obda);
        SiZhuBaZiOntologyJobWorker w = newWorker();
        setField(w, "backendService", bs);
        return w;
    }

    @Test
    @DisplayName("resolveBirthDateTime：null（str 返回 null）与空白 → IllegalArgumentException；正常串可解析")
    void birthResolveNullBlank() throws Exception {
        Class<?>[] t = {Object.class};
        assertThatThrownBy(() -> stat("resolveBirthDateTime", t, (Object) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
        assertThatThrownBy(() -> stat("resolveBirthDateTime", t, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        Object dt = stat("resolveBirthDateTime", t, "1990-01-01 00:00");
        assertThat(dt).isNotNull();
    }

    @Test
    @DisplayName("pinyinGanzhi：非法干(X子)/非法支(甲X) → null")
    void pinyinInvalid() throws Exception {
        Class<?>[] t = {String.class};
        assertThat(stat("pinyinGanzhi", t, "X子")).isNull();
        assertThat(stat("pinyinGanzhi", t, "甲X")).isNull();
    }

    @Test
    @DisplayName("tianKeDiChong：第二柱非法干(X午)/非法支(甲X)/null")
    void tianKeSecondInvalid() throws Exception {
        Class<?>[] t = {String.class, String.class};
        assertThat((Boolean) stat("tianKeDiChong", t, "甲子", "X午")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", t, "甲子", "甲X")).isFalse();
        assertThat((Boolean) stat("tianKeDiChong", t, "甲子", (Object) null)).isFalse();
    }

    @Test
    @DisplayName("inCategory 食伤：食神/伤官 双操作数 true，异类 false")
    void inCategoryShiShang() throws Exception {
        Class<?>[] t = {String.class, String.class};
        assertThat((Boolean) stat("inCategory", t, "食神", "食伤")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "伤官", "食伤")).isTrue();
        assertThat((Boolean) stat("inCategory", t, "正财", "食伤")).isFalse();
    }

    @Test
    @DisplayName("tenGodOntologyMap：d/t/g 各 null 与 g 空白行跳过，保留有效行")
    @SuppressWarnings("unchecked")
    void tenGodMapNullFields() throws Exception {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(row("d", null, "t", "甲", "g", "x"));
        rows.add(row("d", "甲", "t", null, "g", "x"));
        rows.add(row("d", "甲", "t", "乙", "g", null));
        rows.add(row("d", "甲", "t", "乙", "g", " "));
        rows.add(row("d", "丙", "t", "丁", "g", "伤官"));
        SiZhuBaZiOntologyJobWorker w = workerWithObda(q -> q.contains("TenGodMap") ? rows : new ArrayList<>());
        Map<String, String> m = (Map<String, String>) inst(w, "tenGodOntologyMap", new Class<?>[]{});
        assertThat(m).isNotNull();
        assertThat(m.get("丙|丁")).isEqualTo("伤官");
    }
}
