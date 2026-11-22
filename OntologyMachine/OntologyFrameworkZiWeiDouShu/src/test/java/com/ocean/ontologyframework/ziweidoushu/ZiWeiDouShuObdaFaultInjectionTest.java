package com.ocean.ontologyframework.ziweidoushu;

import com.ocean.ontopobdahandler.OBDAHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * D 类「防御/容错分支」故障注入测试（离线）。覆盖此前 0 覆盖的：
 * <ul>
 *   <li>{@code loadStateFromObda}：OBDAHandler 为 null / 抛异常 / 查询空结果的 fallback 与 catch 分支；</li>
 *   <li>{@code verifyConsistency}：DB 缓存与内置表「不一致/缺项/行数不符」的 diff 分支。</li>
 * </ul>
 * 通过反射设置 {@code ONTO_*} 静态缓存造“坏数据”，并在 {@code @BeforeEach} 快照、{@code @AfterEach} 复原，
 * 保证不污染其它测试类（同一 JVM 顺序运行）。
 */
@DisplayName("D类故障注入：OBDA载入容错 + 一致性自检差异分支")
class ZiWeiDouShuObdaFaultInjectionTest {

    private final List<Field> ontoFields = new ArrayList<>();
    private final List<Object> ontoOriginal = new ArrayList<>();

    @BeforeEach
    void snapshotAndNull() throws Exception {
        ontoFields.clear();
        ontoOriginal.clear();
        for (Field f : ZiWeiDouShuOntologyJobWorker.class.getDeclaredFields()) {
            if (f.getName().startsWith("ONTO_") && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                ontoFields.add(f);
                ontoOriginal.add(f.get(null));
                f.set(null, null); // 统一先置空，保证可控起点
            }
        }
    }

    @AfterEach
    void restore() throws Exception {
        for (int i = 0; i < ontoFields.size(); i++) {
            ontoFields.get(i).set(null, ontoOriginal.get(i));
        }
    }

    private static void setStatic(String name, Object value) throws Exception {
        Field f = ZiWeiDouShuOntologyJobWorker.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void invokeNoArg(ZiWeiDouShuOntologyJobWorker w, String name) throws Exception {
        Method m = ZiWeiDouShuOntologyJobWorker.class.getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(w);
    }

    @Test
    @DisplayName("verifyConsistency：DB缓存与内置表不一致/缺项/行数不符 → 全 diff 分支不抛错")
    void verifyConsistencyMismatchBranches() throws Exception {
        // 庙陷：一缺项(null -> bMiss) + 一错值(错 -> bDiff)
        String[] ziweiRow = new String[12];
        ziweiRow[0] = null;
        ziweiRow[1] = "错";
        Map<String, String[]> bmap = new LinkedHashMap<>();
        bmap.put("紫微", ziweiRow);
        setStatic("ONTO_BRIGHTNESS", bmap);
        // 四化：错值 -> sDiff
        Map<String, String> smap = new LinkedHashMap<>();
        smap.put("甲|化禄", "错星");
        setStatic("ONTO_SIHUA", smap);
        // 紫微定位：错值 -> zDiff
        Map<String, Integer> zmap = new LinkedHashMap<>();
        zmap.put("水二局|1", -99);
        setStatic("ONTO_ZIWEI", zmap);
        // 流禄/五鼠遁：错值 -> luDiff/wuDiff
        Map<String, Integer> lu = new LinkedHashMap<>();
        lu.put("甲", 99);
        setStatic("ONTO_LUCUN", lu);
        Map<String, Integer> wu = new LinkedHashMap<>();
        wu.put("甲", 99);
        setStatic("ONTO_WUHU", wu);
        // 十二神：错值 -> shenDiff
        String[] deity = new String[12];
        java.util.Arrays.fill(deity, "错神");
        setStatic("ONTO_DEITY_12", deity);
        // 同垣：行数不符 -> tyDiff（空 list 非 null）
        setStatic("ONTO_TONGYUAN", Collections.emptyList());
        // 星情化气：错行 -> pDiff
        List<String[]> sc = new ArrayList<>();
        sc.add(new String[]{"紫微", "错化气", "错所主"});
        setStatic("ONTO_STAR_CHARACTER", sc);

        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        assertThatCode(() -> invokeNoArg(w, "verifyConsistency")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("loadStateFromObda：OBDAHandler 为 null → 走兜底守卫，不抛错")
    void loadStateNullHandlerGuard() {
        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        try (MockedStatic<OBDAHandler> ms = mockStatic(OBDAHandler.class)) {
            ms.when(OBDAHandler::getInstance).thenReturn(null);
            assertThatCode(() -> invokeNoArg(w, "loadStateFromObda")).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("loadStateFromObda：查询抛异常 → catch 回退兜底，不抛错")
    void loadStateExceptionFallback() {
        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        try (MockedStatic<OBDAHandler> ms = mockStatic(OBDAHandler.class)) {
            OBDAHandler h = mock(OBDAHandler.class);
            when(h.executeAboxQuery(anyString())).thenThrow(new RuntimeException("boom"));
            ms.when(OBDAHandler::getInstance).thenReturn(h);
            assertThatCode(() -> invokeNoArg(w, "loadStateFromObda")).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("loadStateFromObda：查询全返回空 → 触发 27 类 fallback 空载告警分支，不抛错")
    void loadStateEmptyResultsFallbackBranches() {
        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        try (MockedStatic<OBDAHandler> ms = mockStatic(OBDAHandler.class)) {
            OBDAHandler h = mock(OBDAHandler.class);
            when(h.executeAboxQuery(anyString())).thenReturn(Collections.emptyList());
            ms.when(OBDAHandler::getInstance).thenReturn(h);
            assertThatCode(() -> invokeNoArg(w, "loadStateFromObda")).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("loadStateFromObda：行含 null 字段 → 逐行 null 守卫(continue)分支，不抛错")
    void loadStateNullFieldRows() {
        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        Map<String, String> nullRow = new LinkedHashMap<>();
        for (String k : new String[]{"star", "palace", "name", "kind", "id", "base", "branch", "stem", "type"}) {
            nullRow.put(k, null);
        }
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(nullRow);
        try (MockedStatic<OBDAHandler> ms = mockStatic(OBDAHandler.class)) {
            OBDAHandler h = mock(OBDAHandler.class);
            when(h.executeAboxQuery(anyString())).thenReturn(rows);
            ms.when(OBDAHandler::getInstance).thenReturn(h);
            assertThatCode(() -> invokeNoArg(w, "loadStateFromObda")).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("loadStateFromObda：非空但不满额的行 → 触发完整性自检 partial 分支，不抛错")
    void loadStatePartialRowsCompleteness() {
        ZiWeiDouShuOntologyJobWorker w = new ZiWeiDouShuOntologyJobWorker();
        Map<String, String> row = new LinkedHashMap<>();
        for (String k : new String[]{"star", "palace", "name", "stem", "type", "branch", "id",
                "kind", "base", "group", "subject", "acc", "quote", "bureau", "day"}) {
            row.put(k, "1");
        }
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(row);
        try (MockedStatic<OBDAHandler> ms = mockStatic(OBDAHandler.class)) {
            OBDAHandler h = mock(OBDAHandler.class);
            when(h.executeAboxQuery(anyString())).thenReturn(rows);
            ms.when(OBDAHandler::getInstance).thenReturn(h);
            assertThatCode(() -> invokeNoArg(w, "loadStateFromObda")).doesNotThrowAnyException();
        }
    }
}

