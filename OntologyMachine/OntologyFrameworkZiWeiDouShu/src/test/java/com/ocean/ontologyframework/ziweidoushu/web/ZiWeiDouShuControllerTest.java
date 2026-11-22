package com.ocean.ontologyframework.ziweidoushu.web;

import com.ocean.utilities.ProcessOrchestrator;
import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 紫微斗数 REST 接口层（ZiWeiDouShuController）单元测试（第 8 步·代码覆盖率补强）。
 *
 * <p>采用 standalone MockMvc（不启动 Spring 上下文），并以 {@link Mockito#mockStatic} 打桩
 * {@link ProcessOrchestrator} 静态编排工具，逐一覆盖 {@code /chart}、{@code /chart/{key}}、{@code /health}
 * 的成功路径与全部校验/异常分支，补齐原端到端测试绕过 HTTP 入口造成的 Web 层覆盖空白。
 */
@DisplayName("紫微斗数 REST 接口层（Controller）：全部接口与校验/异常分支")
public class ZiWeiDouShuControllerTest {

    private static final String BPMN_PATH = "ontology/bpmn/ziweidoushuProcess.bpmn";

    private MockMvc mockMvc;
    private ZiWeiDouShuController controller;
    private CamundaClient clientMock;
    private MockedStatic<ProcessOrchestrator> orchestrator;

    @BeforeEach
    void setUp() {
        clientMock = Mockito.mock(CamundaClient.class);
        controller = new ZiWeiDouShuController();
        ReflectionTestUtils.setField(controller, "client", clientMock);
        ReflectionTestUtils.setField(controller, "bpmnPath", BPMN_PATH);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        orchestrator = Mockito.mockStatic(ProcessOrchestrator.class);
    }

    @AfterEach
    void tearDown() {
        orchestrator.close();
    }

    private String json(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(kv[i]).append("\":\"").append(kv[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    // ============ POST /chart 校验分支 ============

    @Test
    @DisplayName("POST /chart：body 为 null → 400 birth 不能为空（直调覆盖 body==null 分支）")
    void startChart_nullBody_badRequest() {
        ResponseEntity<?> resp = controller.startChart(null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) resp.getBody()).get("error").toString()).contains("birth");
    }

    @Test
    @DisplayName("POST /chart：缺 birth → 400 birth 不能为空")
    void startChart_missingBirth_badRequest() throws Exception {
        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mode\":\"直接排盘\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("birth")));
    }

    @Test
    @DisplayName("POST /chart：非法 mode → 400 mode 只能为「直接排盘」或「验盘」")
    void startChart_invalidMode_badRequest() throws Exception {
        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON).content(json("birth", "1990-05-15 14:30", "mode", "乱码")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("mode")));
    }

    @Test
    @DisplayName("POST /chart：验盘缺 subjectTraits → 400")
    void startChart_verifyWithoutTraits_badRequest() throws Exception {
        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON).content(json("birth", "1990-05-15 14:30", "mode", "验盘")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("subjectTraits")));
    }

    @Test
    @DisplayName("POST /chart：非法 depth → 400 depth 只能为三档")
    void startChart_invalidDepth_badRequest() throws Exception {
        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON).content(json("birth", "1990-05-15 14:30", "depth", "十年")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("depth")));
    }

    // ============ POST /chart 成功/异常分支 ============

    @Test
    @DisplayName("POST /chart：默认 mode=直接排盘、depth=本命 → 200 返回 instanceKey")
    void startChart_defaults_ok() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenReturn(12345L);

        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON).content(json("birth", "1990-05-15 14:30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(12345))
                .andExpect(jsonPath("$.processId").value("ZiWeiDouShuChartProcess"))
                .andExpect(jsonPath("$.inputMode").value("直接排盘"))
                .andExpect(jsonPath("$.depth").value("本命"));
    }

    @Test
    @DisplayName("POST /chart：部署失败(warn)但流程仍能启动 → 200")
    void startChart_deployFalse_stillStarts() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(false);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenReturn(100L);

        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON).content(json("birth", "1990-05-15 14:30", "depth", "本命+大限")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(100))
                .andExpect(jsonPath("$.depth").value("本命+大限"));
    }

    @Test
    @DisplayName("POST /chart：启动抛异常 → 503 返回错误信息")
    void startChart_startThrows_serviceUnavailable() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("engine boom"));

        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON).content(json("birth", "1990-05-15 14:30")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(containsString("engine boom")));
    }

    // ============ GET /chart/{key} ============

    @Test
    @DisplayName("GET /chart/{key}：实例尚未导出(null) → 200 status=RUNNING")
    void getChartStatus_notExported_running() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.fetchInstance(any(), anyLong())).thenReturn(null);
        orchestrator.when(() -> ProcessOrchestrator.readVariables(any(), anyLong())).thenReturn(new LinkedHashMap<>());

        mockMvc.perform(get("/api/ziweidoushu/chart/555"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(555))
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("GET /chart/{key}：COMPLETED → 200 返回盘面字段集")
    void getChartStatus_completed_returnsChartFields() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.fetchInstance(any(), anyLong()))
                .thenReturn(new ProcessOrchestrator.InstanceInfo("COMPLETED", false));
        orchestrator.when(() -> ProcessOrchestrator.statusOf(any(), anyBoolean())).thenReturn("COMPLETED");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("chartBase", "紫微在辰");
        vars.put("reading", "第九条：大凶");
        vars.put("alertTriggered", true);
        orchestrator.when(() -> ProcessOrchestrator.readVariables(any(), anyLong())).thenReturn(vars);

        mockMvc.perform(get("/api/ziweidoushu/chart/777"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.chartBase").value("紫微在辰"))
                .andExpect(jsonPath("$.alertTriggered").value(true));
    }

    @Test
    @DisplayName("GET /chart/{key}：查询抛异常 → 503")
    void getChartStatus_throws_serviceUnavailable() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.fetchInstance(any(), anyLong()))
                .thenThrow(new RuntimeException("query down"));

        mockMvc.perform(get("/api/ziweidoushu/chart/999"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value(containsString("query down")));
    }

    @Test
    @DisplayName("POST /chart：验盘且提供 subjectTraits → 200 正常启动")
    void startChart_verifyWithTraits_ok() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenReturn(321L);

        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birth\":\"1990-05-15 14:30\",\"mode\":\"验盘\",\"subjectTraits\":\"相貌清秀\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inputMode").value("验盘"))
                .andExpect(jsonPath("$.instanceKey").value(321));
    }

    @Test
    @DisplayName("POST /chart：depth=本命+大限+流年（第三档）→ 200")
    void startChart_fullDepth_ok() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenReturn(322L);

        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("birth", "1990-05-15 14:30", "depth", "本命+大限+流年")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.depth").value("本命+大限+流年"));
    }

    @Test
    @DisplayName("POST /chart：body 含 null 字段 → 变量装配跳过 null（200）")
    void startChart_nullFieldSkipped_ok() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenReturn(323L);

        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birth\":\"1990-05-15 14:30\",\"granularity\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(323));
    }

    @Test
    @DisplayName("POST /chart：启动异常且无 message → 503 error=未知错误")
    void startChart_throwWithoutMessage_unknownError() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap()))
                .thenThrow(new RuntimeException());

        mockMvc.perform(post("/api/ziweidoushu/chart")
                        .contentType(MediaType.APPLICATION_JSON).content(json("birth", "1990-05-15 14:30")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("未知错误"));
    }

    @Test
    @DisplayName("GET /chart/{key}：COMPLETED 但无 reading → alertType 走空串分支")
    void getChartStatus_completedWithoutReading_ok() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.fetchInstance(any(), anyLong()))
                .thenReturn(new ProcessOrchestrator.InstanceInfo("COMPLETED", false));
        orchestrator.when(() -> ProcessOrchestrator.statusOf(any(), anyBoolean())).thenReturn("COMPLETED");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("chartBase", "天府在子");
        orchestrator.when(() -> ProcessOrchestrator.readVariables(any(), anyLong())).thenReturn(vars);

        mockMvc.perform(get("/api/ziweidoushu/chart/778"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.chartBase").value("天府在子"));
    }

    // ============ GET /health ============

    @Test
    @DisplayName("GET /health：返回 engineAvailable/processId/bpmnPath")
    void health_ok() throws Exception {
        mockMvc.perform(get("/api/ziweidoushu/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineAvailable").value(true))
                .andExpect(jsonPath("$.processId").value("ZiWeiDouShuChartProcess"))
                .andExpect(jsonPath("$.bpmnPath").value(BPMN_PATH));
    }

    @Test
    @DisplayName("GET /health：client 为 null → engineAvailable=false")
    void health_clientNull_engineUnavailable() throws Exception {
        ReflectionTestUtils.setField(controller, "client", null);

        mockMvc.perform(get("/api/ziweidoushu/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineAvailable").value(false));
    }
}
