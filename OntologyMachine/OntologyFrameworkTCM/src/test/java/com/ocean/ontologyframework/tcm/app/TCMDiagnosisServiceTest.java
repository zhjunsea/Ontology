package com.ocean.ontologyframework.tcm.app;

import io.camunda.client.api.search.response.UserTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link TCMDiagnosisService} 纯逻辑方法离线单元测试（无需真实 Camunda/Ontop/MySQL）。
 *
 * <p>通过反射覆盖其不依赖 CamundaClient 的私有方法：
 * {@code statusOf / mappingOf / diagnosisOf / decode / tasksOf / requireEngine}，以及公开的
 * {@code isEngineAvailable}。这些决定了 /api/diagnosis 返回的 status / mapping / diagnosis 结构。
 */
@DisplayName("TCMDiagnosisService 纯逻辑方法：status/mapping/diagnosis/decode/tasks/engine")
class TCMDiagnosisServiceTest {

    private final TCMDiagnosisService svc = new TCMDiagnosisService();

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = target.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    @Test
    @DisplayName("isEngineAvailable / requireEngine：无 Camunda 客户端")
    void engineGuard() throws Exception {
        assertFalse(svc.isEngineAvailable());
        try {
            call(svc, "requireEngine", new Class<?>[]{});
            fail("应抛 IllegalStateException");
        } catch (InvocationTargetException e) {
            assertInstanceOf(IllegalStateException.class, e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("statusOf：AWAITING_CONFIRMATION / AWAITING_REVISION / COMPLETED / RUNNING")
    void statusOf() throws Exception {
        Map<String, Object> noExp = new HashMap<>();
        assertEquals("RUNNING", call(svc, "statusOf", new Class<?>[]{Map.class, List.class}, noExp, new ArrayList<UserTask>()));

        Map<String, Object> exp = new HashMap<>();
        exp.put("explanation", "已解释");
        assertEquals("COMPLETED", call(svc, "statusOf", new Class<?>[]{Map.class, List.class}, exp, new ArrayList<UserTask>()));

        UserTask confirm = mock(UserTask.class);
        when(confirm.getElementId()).thenReturn(TCMDiagnosisService.TASK_CONFIRM_SYMPTOMS);
        assertEquals("AWAITING_CONFIRMATION",
                call(svc, "statusOf", new Class<?>[]{Map.class, List.class}, noExp, List.of(confirm)));

        UserTask revise = mock(UserTask.class);
        when(revise.getElementId()).thenReturn(TCMDiagnosisService.TASK_REVISE_SIZHEN);
        assertEquals("AWAITING_REVISION",
                call(svc, "statusOf", new Class<?>[]{Map.class, List.class}, noExp, List.of(revise)));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("mappingOf：空变量用默认；有值时透传")
    void mappingOf() throws Exception {
        Map<String, Object> empty = new LinkedHashMap<>();
        Map<String, Object> m = (Map<String, Object>) call(svc, "mappingOf", new Class<?>[]{Map.class}, empty);
        assertEquals(Collections.emptyList(), m.get("detail"));
        assertEquals(0, m.get("round"));
        assertEquals(false, m.get("needsConfirmation"));

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("mappingDetail", List.of("a"));
        vars.put("mappingSummary", "摘要");
        vars.put("mappingRound", 2);
        vars.put("symptomIris", List.of("X"));
        Map<String, Object> m2 = (Map<String, Object>) call(svc, "mappingOf", new Class<?>[]{Map.class}, vars);
        assertEquals(List.of("a"), m2.get("detail"));
        assertEquals("摘要", m2.get("summary"));
        assertEquals(2, m2.get("round"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("diagnosisOf：默认 CONFIRMED；NO_MAIN_MATCH 透传")
    void diagnosisOf() throws Exception {
        Map<String, Object> empty = new LinkedHashMap<>();
        Map<String, Object> d = (Map<String, Object>) call(svc, "diagnosisOf", new Class<?>[]{Map.class}, empty);
        assertEquals("CONFIRMED", d.get("outcome"));
        assertEquals(Collections.emptyList(), d.get("liujingTypesCn"));
        assertNull(d.get("sixChannelCn"));

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("outcome", "NO_MAIN_MATCH");
        vars.put("sixChannelCn", "太阳病");
        vars.put("warnings", List.of("十八反：x 反 y"));
        Map<String, Object> d2 = (Map<String, Object>) call(svc, "diagnosisOf", new Class<?>[]{Map.class}, vars);
        assertEquals("NO_MAIN_MATCH", d2.get("outcome"));
        assertEquals("太阳病", d2.get("sixChannelCn"));
        assertEquals(List.of("十八反：x 反 y"), d2.get("warnings"));
    }

    @Test
    @DisplayName("decode：合法 JSON → 对象；非法 → 原串；null → null")
    void decode() throws Exception {
        assertNull(call(svc, "decode", new Class<?>[]{String.class}, (Object) null));
        Object parsed = call(svc, "decode", new Class<?>[]{String.class}, "{\"a\":1}");
        assertInstanceOf(Map.class, parsed);
        assertEquals("not-json", call(svc, "decode", new Class<?>[]{String.class}, "not-json"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("tasksOf：空列表；含任务（state=null）映射")
    void tasksOf() throws Exception {
        List<Map<String, Object>> empty = (List<Map<String, Object>>) call(svc, "tasksOf", new Class<?>[]{List.class}, new ArrayList<UserTask>());
        assertTrue(empty.isEmpty());

        UserTask t = mock(UserTask.class);
        when(t.getUserTaskKey()).thenReturn(5L);
        when(t.getElementId()).thenReturn("Task_X");
        when(t.getName()).thenReturn("症状确认");
        when(t.getState()).thenReturn(null);
        List<Map<String, Object>> out = (List<Map<String, Object>>) call(svc, "tasksOf", new Class<?>[]{List.class}, List.of(t));
        assertEquals(1, out.size());
        assertEquals(5L, out.get(0).get("userTaskKey"));
        assertEquals("Task_X", out.get(0).get("elementId"));
        assertEquals("症状确认", out.get(0).get("name"));
        assertNull(out.get(0).get("state"));
    }
}
