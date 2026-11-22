package com.ocean.ontologyframework.tcm.app;

import com.ocean.utilities.ProcessOrchestrator;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.UserTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TCMDiagnosisService} 编排方法离线单元测试。
 *
 * <p>用 {@code mockStatic(ProcessOrchestrator)} 桩化启动/读变量，用 Mockito 深桩
 * {@code RETURNS_DEEP_STUBS} 桩化 {@link CamundaClient} 的流式 API（人工任务查询/完成），
 * 从而在**无需真实 Camunda** 的情况下覆盖 {@code start/snapshot/pendingTasks/completeTask/
 * confirmSymptoms/readPendingTasks/findTask/awaitSettled} 全部分支。
 */
@DisplayName("TCMDiagnosisService 编排层：start/snapshot/confirm/complete/tasks（mock Camunda）")
class TCMDiagnosisServiceCamundaTest {

    private static void setClient(TCMDiagnosisService s, CamundaClient c) throws Exception {
        Field f = TCMDiagnosisService.class.getDeclaredField("camundaClient");
        f.setAccessible(true);
        f.set(s, c);
    }

    private static Map<String, Object> completedVars() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("explanation", "已解释");
        m.put("outcome", "CONFIRMED");
        return m;
    }

    private static UserTask task(long key, String elementId) {
        UserTask t = mock(UserTask.class);
        when(t.getUserTaskKey()).thenReturn(key);
        when(t.getElementId()).thenReturn(elementId);
        when(t.getName()).thenReturn("任务");
        when(t.getState()).thenReturn(null);
        return t;
    }

    @Test
    @DisplayName("isEngineAvailable：注入客户端后为 true")
    void engineAvailable() throws Exception {
        TCMDiagnosisService s = new TCMDiagnosisService();
        setClient(s, mock(CamundaClient.class, RETURNS_DEEP_STUBS));
        assertTrue(s.isEngineAvailable());
    }

    @Test
    @DisplayName("start：mock ProcessOrchestrator.start/readVariables → COMPLETED")
    void start() throws Exception {
        TCMDiagnosisService s = new TCMDiagnosisService();
        CamundaClient client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
        setClient(s, client);
        when(client.newUserTaskSearchRequest().filter(any(java.util.function.Consumer.class)).send().join().items())
                .thenReturn(new ArrayList<UserTask>());

        try (MockedStatic<ProcessOrchestrator> ms = mockStatic(ProcessOrchestrator.class)) {
            ms.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenReturn(111L);
            ms.when(() -> ProcessOrchestrator.readVariables(any(), eq(111L))).thenReturn(completedVars());

            Map<String, Object> snap = s.start("发热恶寒");
            assertEquals(111L, snap.get("processInstanceKey"));
            assertEquals("COMPLETED", snap.get("status"));
            assertNotNull(snap.get("mapping"));
            assertNotNull(snap.get("diagnosis"));
        }
    }

    @Test
    @DisplayName("snapshot：有待确认任务 → AWAITING_CONFIRMATION；无任务无解释 → RUNNING")
    void snapshot() throws Exception {
        TCMDiagnosisService s = new TCMDiagnosisService();
        CamundaClient client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
        setClient(s, client);
        try (MockedStatic<ProcessOrchestrator> ms = mockStatic(ProcessOrchestrator.class)) {
            Map<String, Object> runVars = new LinkedHashMap<>(); // 无 explanation
            ms.when(() -> ProcessOrchestrator.readVariables(any(), anyLong())).thenReturn(runVars);

            UserTask confirmTask = task(5L, TCMDiagnosisService.TASK_CONFIRM_SYMPTOMS);
            when(client.newUserTaskSearchRequest().filter(any(java.util.function.Consumer.class)).send().join().items())
                    .thenReturn(List.of(confirmTask));
            assertEquals("AWAITING_CONFIRMATION", s.snapshot(222L).get("status"));

            when(client.newUserTaskSearchRequest().filter(any(java.util.function.Consumer.class)).send().join().items())
                    .thenReturn(new ArrayList<UserTask>());
            assertEquals("RUNNING", s.snapshot(333L).get("status"));
        }
    }

    @Test
    @DisplayName("pendingTasks：返回人工任务列表；读取异常时降级为空")
    void pendingTasks() throws Exception {
        TCMDiagnosisService s = new TCMDiagnosisService();
        CamundaClient client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
        setClient(s, client);

        UserTask pendTask = task(5L, TCMDiagnosisService.TASK_CONFIRM_SYMPTOMS);
        when(client.newUserTaskSearchRequest().filter(any(java.util.function.Consumer.class)).send().join().items())
                .thenReturn(List.of(pendTask));
        List<Map<String, Object>> list = s.pendingTasks(1L);
        assertEquals(1, list.size());
        assertEquals(5L, list.get(0).get("userTaskKey"));

        // 读取异常 → readPendingTasks 捕获 → 空
        CamundaClient boom = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
        when(boom.newUserTaskSearchRequest()).thenThrow(new RuntimeException("camunda down"));
        setClient(s, boom);
        assertTrue(s.pendingTasks(2L).isEmpty());
    }

    @Test
    @DisplayName("completeTask：完成人工任务 → COMPLETED")
    void completeTask() throws Exception {
        TCMDiagnosisService s = new TCMDiagnosisService();
        CamundaClient client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
        setClient(s, client);
        when(client.newUserTaskSearchRequest().filter(any(java.util.function.Consumer.class)).send().join().items())
                .thenReturn(new ArrayList<UserTask>());

        try (MockedStatic<ProcessOrchestrator> ms = mockStatic(ProcessOrchestrator.class)) {
            ms.when(() -> ProcessOrchestrator.readVariables(any(), anyLong())).thenReturn(completedVars());
            Map<String, Object> r = s.completeTask(1L, 9L, Map.of("k", "v"));
            assertEquals("COMPLETED", r.get("status"));
        }
    }

    @Test
    @DisplayName("confirmSymptoms：有确认任务 → COMPLETED；无任务 → IllegalStateException")
    void confirmSymptoms() throws Exception {
        TCMDiagnosisService s = new TCMDiagnosisService();
        CamundaClient client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
        setClient(s, client);

        // 有任务
        UserTask confirmT = task(7L, TCMDiagnosisService.TASK_CONFIRM_SYMPTOMS);
        when(client.newUserTaskSearchRequest().filter(any(java.util.function.Consumer.class)).send().join().items())
                .thenReturn(List.of(confirmT))           // 首次：findTask 命中确认任务
                .thenReturn(new ArrayList<UserTask>());  // 完成后：任务列表为空
        try (MockedStatic<ProcessOrchestrator> ms = mockStatic(ProcessOrchestrator.class)) {
            ms.when(() -> ProcessOrchestrator.readVariables(any(), anyLong())).thenReturn(completedVars());
            Map<String, Object> r = s.confirmSymptoms(1L, List.of("恶寒"), List.of(), List.of("无汗"));
            assertEquals("COMPLETED", r.get("status"));
        }

        // 无任务 → 抛 IllegalStateException
        when(client.newUserTaskSearchRequest().filter(any(java.util.function.Consumer.class)).send().join().items())
                .thenReturn(new ArrayList<UserTask>());
        assertThrows(IllegalStateException.class,
                () -> s.confirmSymptoms(2L, List.of(), List.of(), List.of()));
    }
}
