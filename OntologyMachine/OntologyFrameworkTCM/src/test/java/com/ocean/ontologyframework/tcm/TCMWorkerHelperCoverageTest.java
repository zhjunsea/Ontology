package com.ocean.ontologyframework.tcm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TCMOntologyJobWorker} 纯逻辑 / 低耦合方法的离线单元测试。
 *
 * <p>无需 BackendService / Openllet 推理器 / Ontop / MySQL / Camunda：被覆盖的方法仅依赖
 * 普通字段（{@code huchiIndex} / {@code shibafanMap} / {@code shijiuweiMap} / {@code yaowuLabelMap}，
 * 均默认为空 Map），故直接 {@code new TCMOntologyJobWorker()} 后反射驱动即可稳定命中其真实业务分支。
 *
 * <p>目的：真实提升生产类 {@code TCMOntologyJobWorker} 的代码覆盖率（这些是实际下发给患者的
 * 十八反/十九畏配伍禁忌、四诊互斥冲突等业务规则），而非为遗留死代码凑覆盖。
 */
@DisplayName("TCMOntologyJobWorker 纯逻辑方法离线覆盖（strOf/intOf/frag/互斥冲突/配伍禁忌/空方）")
class TCMWorkerHelperCoverageTest {

    private static TCMOntologyJobWorker newWorker() {
        return new TCMOntologyJobWorker();
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static void setField(Object target, String name, Object val) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, val);
    }

    @Test
    @DisplayName("strOf：null / 空白 / 去空白 / 数字")
    void strOf() throws Exception {
        TCMOntologyJobWorker w = newWorker();
        assertNull(call(w, "strOf", new Class<?>[]{Object.class}, (Object) null));
        assertEquals("abc", call(w, "strOf", new Class<?>[]{Object.class}, "  abc  "));
        assertNull(call(w, "strOf", new Class<?>[]{Object.class}, "   "));
        assertNull(call(w, "strOf", new Class<?>[]{Object.class}, ""));
        assertEquals("123", call(w, "strOf", new Class<?>[]{Object.class}, 123));
    }

    @Test
    @DisplayName("intOf：Number / 数字串 / 含空白数字串 / 非数字默认 / null 默认")
    void intOf() throws Exception {
        TCMOntologyJobWorker w = newWorker();
        assertEquals(5, call(w, "intOf", new Class<?>[]{Object.class, int.class}, 5, 0));
        assertEquals(42, call(w, "intOf", new Class<?>[]{Object.class, int.class}, "42", 0));
        assertEquals(7, call(w, "intOf", new Class<?>[]{Object.class, int.class}, "  7 ", 0));
        assertEquals(-1, call(w, "intOf", new Class<?>[]{Object.class, int.class}, "abc", -1));
        assertEquals(-2, call(w, "intOf", new Class<?>[]{Object.class, int.class}, null, -2));
    }

    @Test
    @DisplayName("frag：实例 IRI 去 baseNs 与 _instance；外域 IRI 原样")
    void frag() throws Exception {
        TCMOntologyJobWorker w = newWorker();
        assertEquals("Fare", call(w, "frag", new Class<?>[]{String.class}, "Fare_instance"));
        // 外域 IRI 不以 baseNs 开头 → 原样返回完整 IRI（见 ObdaQueryUtils.frag 语义）
        assertEquals("http://other/ns#OuterNs",
                call(w, "frag", new Class<?>[]{String.class}, "http://other/ns#OuterNs"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("detectSizhenConflicts：null / 空 / 无互斥索引 / 命中互斥对")
    void detectSizhenConflicts() throws Exception {
        TCMOntologyJobWorker w = newWorker();
        // 默认 huchiIndex 为空 → 任何输入都返回空
        assertTrue(((List<?>) call(w, "detectSizhenConflicts", new Class<?>[]{Set.class},
                new HashSet<String>())).isEmpty());
        assertTrue(((List<?>) call(w, "detectSizhenConflicts", new Class<?>[]{Set.class},
                (Object) null)).isEmpty());

        // 建立互斥索引：aaa 与 zzz 互斥（aaa 字典序在前，正向扫描可命中）
        Map<String, Set<String>> idx = new HashMap<>();
        idx.put("aaa", new HashSet<>(Collections.singletonList("zzz")));
        setField(w, "huchiIndex", idx);

        List<List<String>> conf = (List<List<String>>) call(w, "detectSizhenConflicts",
                new Class<?>[]{Set.class}, new LinkedHashSet<>(Arrays.asList("aaa", "zzz")));
        assertEquals(1, conf.size());
        assertEquals(Arrays.asList("aaa", "zzz"), conf.get(0));

        // 存在互斥索引但患者不含互斥项 → 无冲突（覆盖 excl 非空但无正向命中分支）
        List<List<String>> none = (List<List<String>>) call(w, "detectSizhenConflicts",
                new Class<?>[]{Set.class}, new LinkedHashSet<>(Arrays.asList("aaa", "yyy")));
        assertTrue(none.isEmpty());
    }

    @Test
    @DisplayName("removeMutuallyExclusive：空守卫 / 剔除与 satisfied 互斥的候选")
    void removeMutuallyExclusive() throws Exception {
        TCMOntologyJobWorker w = newWorker();
        // 空守卫分支
        List<String> a = new ArrayList<>(Arrays.asList("aaa"));
        call(w, "removeMutuallyExclusive", new Class<?>[]{List.class, Set.class}, a, new HashSet<String>());
        assertEquals(1, a.size()); // satisfied 空 → 不剔除

        Map<String, Set<String>> idx = new HashMap<>();
        idx.put("aaa", new HashSet<>(Collections.singletonList("zzz")));
        setField(w, "huchiIndex", idx);

        List<String> frags = new ArrayList<>(Arrays.asList("aaa", "zzz", "mmm"));
        call(w, "removeMutuallyExclusive", new Class<?>[]{List.class, Set.class}, frags,
                new HashSet<>(Collections.singletonList("aaa")));
        // zzz 与 aaa 互斥 → 被剔除
        assertFalse(frags.contains("zzz"));
        assertTrue(frags.contains("aaa"));
        assertTrue(frags.contains("mmm"));

        // 无排除项时的分支（satisfied 中的项在 huchiIndex 中无对应）
        List<String> frags2 = new ArrayList<>(Arrays.asList("aaa", "mmm"));
        call(w, "removeMutuallyExclusive", new Class<?>[]{List.class, Set.class}, frags2,
                new HashSet<>(Collections.singletonList("unknown")));
        assertEquals(2, frags2.size());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("checkIncompatibilities：null / 单味 / 十八反 / 十九畏")
    void checkIncompatibilities() throws Exception {
        TCMOntologyJobWorker w = newWorker();
        // null 与单味守卫
        assertTrue(((List<?>) call(w, "checkIncompatibilities", new Class<?>[]{List.class},
                (Object) null)).isEmpty());
        assertTrue(((List<?>) call(w, "checkIncompatibilities", new Class<?>[]{List.class},
                Collections.singletonList("gancao"))).isEmpty());

        // 十八反：甘草 反 甘遂
        Map<String, Set<String>> shibafan = new HashMap<>();
        shibafan.put("gancao", new HashSet<>(Collections.singletonList("ganzhu")));
        setField(w, "shibafanMap", shibafan);
        // yaowuLabelMap 为空 → labelOf 回退 fragment
        List<String> warnings = (List<String>) call(w, "checkIncompatibilities",
                new Class<?>[]{List.class}, new ArrayList<>(Arrays.asList("gancao", "ganzhu")));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("十八反"));
        assertTrue(warnings.get(0).contains("gancao"));
        assertTrue(warnings.get(0).contains("ganzhu"));

        // 十九畏：aaa 畏 zzz（覆盖 checkPairs 的“畏”分支与 label.equals 判断）
        Map<String, Set<String>> shijiuwei = new HashMap<>();
        shijiuwei.put("aaa", new HashSet<>(Collections.singletonList("zzz")));
        setField(w, "shijiuweiMap", shijiuwei);
        List<String> warnings2 = (List<String>) call(w, "checkIncompatibilities",
                new Class<?>[]{List.class}, new ArrayList<>(Arrays.asList("aaa", "zzz")));
        assertEquals(1, warnings2.size());
        assertTrue(warnings2.get(0).contains("十九畏"));
        assertTrue(warnings2.get(0).contains("畏"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("emptyRx：空处方模板键完整且为 null/空")
    void emptyRx() throws Exception {
        TCMOntologyJobWorker w = newWorker();
        Map<String, Object> rx = (Map<String, Object>) call(w, "emptyRx", new Class<?>[]{});
        assertTrue(rx.containsKey("finalFormula"));
        assertNull(rx.get("finalFormula"));
        assertInstanceOf(List.class, rx.get("herbs"));
        assertTrue(((List<?>) rx.get("herbs")).isEmpty());
        assertTrue(rx.containsKey("warnings"));
    }
}
