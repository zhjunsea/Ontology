package com.ocean.ontologyframework.tcm.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link TCMDiagnosisController} 离线单元测试（Mockito 模拟 Service + Catalog，无需 Camunda/Ontop/MySQL）。
 *
 * <p>覆盖 REST 层全部分支：参数校验(400)、成功(202/200)、引擎异常(503/409)、
 * 请求体解析（列表 / 分隔符字符串 / null）、catalog 检索与 limit 夹取、health。
 */
@DisplayName("TCMDiagnosisController REST 层：全接口与校验/异常分支")
class TCMDiagnosisControllerTest {

    private final TCMDiagnosisService service = mock(TCMDiagnosisService.class);
    private final SymptomCatalog catalog = mock(SymptomCatalog.class);
    private final TCMDiagnosisController controller = new TCMDiagnosisController(service, catalog);

    private static int status(ResponseEntity<?> re) {
        return re.getStatusCode().value();
    }

    // -------- start --------

    @Test
    @DisplayName("start：空 text / null body → 400；正常 → 202；异常 → 503")
    void start() {
        assertEquals(400, status(controller.start(null)));
        assertEquals(400, status(controller.start(Map.of("text", "   "))));
        assertEquals(400, status(controller.start(new HashMap<>())));

        when(service.start("发热恶寒")).thenReturn(Map.of("processInstanceKey", 1L));
        ResponseEntity<?> ok = controller.start(Map.of("text", "发热恶寒"));
        assertEquals(202, status(ok));

        when(service.start("boom")).thenThrow(new IllegalStateException("camunda down"));
        ResponseEntity<?> err = controller.start(Map.of("text", "boom"));
        assertEquals(503, status(err));
        assertTrue(((Map<?, ?>) err.getBody()).containsKey("error"));
    }

    // -------- get --------

    @Test
    @DisplayName("get：正常 200；异常 503")
    void get() {
        when(service.snapshot(7L)).thenReturn(Map.of("status", "COMPLETED"));
        assertEquals(200, status(controller.get(7L)));

        when(service.snapshot(8L)).thenThrow(new RuntimeException("nope"));
        assertEquals(503, status(controller.get(8L)));
    }

    // -------- confirm --------

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("confirm：列表/分隔符/ null body 均解析并提交；异常 409")
    void confirm() {
        when(service.confirmSymptoms(anyLong(), any(), any(), any())).thenReturn(Map.of("ok", true));

        // 列表形式
        ResponseEntity<?> r1 = controller.confirm(1L, Map.of(
                "confirmed", List.of("恶寒", " "), "rejected", List.of("发热"), "extra", List.of("无汗")));
        assertEquals(200, status(r1));

        ArgumentCaptor<List<String>> capC = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<List<String>> capE = ArgumentCaptor.forClass(List.class);
        verify(service).confirmSymptoms(eq(1L), capC.capture(), any(), capE.capture());
        assertEquals(List.of("恶寒"), capC.getValue()); // 空白元素被过滤
        assertEquals(List.of("无汗"), capE.getValue());

        // 分隔符字符串 + null body
        controller.confirm(2L, Map.of("confirmed", "a, b  c"));
        controller.confirm(3L, null);
        verify(service).confirmSymptoms(eq(2L), eq(List.of("a", "b", "c")), eq(List.of()), eq(List.of()));
        verify(service).confirmSymptoms(eq(3L), eq(List.of()), eq(List.of()), eq(List.of()));

        // 异常 → 409
        when(service.confirmSymptoms(eq(9L), any(), any(), any()))
                .thenThrow(new IllegalStateException("no task"));
        assertEquals(409, status(controller.confirm(9L, Map.of())));
    }

    // -------- tasks / completeTask --------

    @Test
    @DisplayName("tasks：正常 200 / 异常 503；completeTask：正常 200 / 异常 409")
    void tasksAndComplete() {
        when(service.pendingTasks(1L)).thenReturn(List.of(Map.of("userTaskKey", 5L)));
        assertEquals(200, status(controller.tasks(1L)));
        when(service.pendingTasks(2L)).thenThrow(new RuntimeException("x"));
        assertEquals(503, status(controller.tasks(2L)));

        when(service.completeTask(eq(1L), eq(5L), any())).thenReturn(Map.of("done", true));
        assertEquals(200, status(controller.completeTask(1L, 5L, Map.of("k", "v"))));
        when(service.completeTask(eq(2L), eq(6L), any())).thenThrow(new RuntimeException("x"));
        assertEquals(409, status(controller.completeTask(2L, 6L, null)));
    }

    // -------- catalog --------

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("catalog：检索 + limit 夹取 + counts；category 中英解析")
    void catalogSearch() {
        SymptomCatalog.Entry e = new SymptomCatalog.Entry("FaRe", "发热", SymptomCatalog.Category.ZHENGZHUANG);
        when(catalog.search(any(), any(), anyInt())).thenReturn(List.of(e));
        when(catalog.size()).thenReturn(747);
        when(catalog.countOf(any())).thenReturn(5L);

        Map<String, Object> out = controller.catalog("症状", "热", 0); // limit 0 → 夹到 1
        assertEquals(747, out.get("total"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) out.get("items");
        assertEquals(1, items.size());
        assertEquals("FaRe", items.get(0).get("fragment"));
        assertEquals("发热", items.get(0).get("label"));
        assertEquals("症状", items.get(0).get("category"));
        assertNotNull(out.get("counts"));
        // 验证 limit 被夹到 1
        verify(catalog).search(eq("热"), eq(SymptomCatalog.Category.ZHENGZHUANG), eq(1));

        // category 为 null、limit 超上限 999 → 夹到 500
        controller.catalog(null, null, 999);
        verify(catalog).search(isNull(), isNull(), eq(500));
    }

    // -------- health --------

    @Test
    @DisplayName("health：engineAvailable + catalogSize + processId")
    void health() {
        when(service.isEngineAvailable()).thenReturn(true);
        when(catalog.size()).thenReturn(747);
        Map<String, Object> h = controller.health();
        assertEquals(true, h.get("engineAvailable"));
        assertEquals(747, h.get("catalogSize"));
        assertEquals(TCMDiagnosisService.PROCESS_ID, h.get("processId"));
    }
}
