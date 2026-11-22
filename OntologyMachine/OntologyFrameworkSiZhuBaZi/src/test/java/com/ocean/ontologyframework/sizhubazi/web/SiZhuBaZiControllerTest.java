package com.ocean.ontologyframework.sizhubazi.web;

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
 * 四柱命理 REST 接口层（SiZhuBaZiController）单元测试。
 *
 * <p>standalone MockMvc（不启动 Spring 上下文）+ {@link Mockito#mockStatic} 打桩
 * {@link ProcessOrchestrator}，覆盖 {@code /divine}、{@code /divine/{key}}、{@code /health}
 * 的成功路径与全部校验/异常分支。
 */
@DisplayName("四柱命理 REST 接口层（Controller）：全部接口与校验/异常分支")
public class SiZhuBaZiControllerTest {

    private static final String BPMN_PATH = "ontology/bpmn/sizhubaziProcess.bpmn";

    private MockMvc mockMvc;
    private SiZhuBaZiController controller;
    private CamundaClient clientMock;
    private MockedStatic<ProcessOrchestrator> orchestrator;

    @BeforeEach
    void setUp() {
        clientMock = Mockito.mock(CamundaClient.class);
        controller = new SiZhuBaZiController();
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

    // ============ POST /divine 校验分支 ============

    @Test
    @DisplayName("POST /divine：body 为 null → 400 birth 不能为空（直调覆盖 body==null 分支）")
    void startDivine_nullBody_badRequest() {
        ResponseEntity<?> resp = controller.startDivine(null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) resp.getBody()).get("error").toString()).contains("birth");
    }

    @Test
    @DisplayName("POST /divine：缺 birth → 400")
    void startDivine_missingBirth_badRequest() throws Exception {
        mockMvc.perform(post("/api/sizhubazi/divine").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("birth")));
    }

    @Test
    @DisplayName("POST /divine：mode 非法 → 400")
    void startDivine_badMode_badRequest() throws Exception {
        mockMvc.perform(post("/api/sizhubazi/divine").contentType(MediaType.APPLICATION_JSON)
                        .content(json("birth", "1990-05-15 14:30", "mode", "错误模式")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("mode")));
    }

    @Test
    @DisplayName("POST /divine：合婚缺 partnerBirth → 400")
    void startDivine_marriageMissingPartner_badRequest() throws Exception {
        mockMvc.perform(post("/api/sizhubazi/divine").contentType(MediaType.APPLICATION_JSON)
                        .content(json("birth", "1990-05-15 14:30", "mode", "合婚")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("partnerBirth")));
    }

    // ============ POST /divine 成功/异常分支 ============

    @Test
    @DisplayName("POST /divine：正常 → 200，返回 instanceKey（deploy=true）")
    void startDivine_ok() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenReturn(123L);
        mockMvc.perform(post("/api/sizhubazi/divine").contentType(MediaType.APPLICATION_JSON)
                        .content(json("birth", "1990-05-15 14:30", "mode", "单命")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(123))
                .andExpect(jsonPath("$.processId").value("SiZhuBaZiProcess"))
                .andExpect(jsonPath("$.mode").value("单命"));
    }

    @Test
    @DisplayName("POST /divine：deploy 返回 false（部署失败/已存在）→ 仍 200")
    void startDivine_deployFalse_ok() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(false);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenReturn(124L);
        mockMvc.perform(post("/api/sizhubazi/divine").contentType(MediaType.APPLICATION_JSON)
                        .content(json("birth", "1990-05-15 14:30")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(124));
    }

    @Test
    @DisplayName("POST /divine：body 含 null 字段 → 变量装配跳过 null（200）")
    void startDivine_nullFieldSkipped_ok() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenReturn(125L);
        mockMvc.perform(post("/api/sizhubazi/divine").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birth\":\"1990-05-15 14:30\",\"x\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(125));
    }

    @Test
    @DisplayName("POST /divine：启动异常有 message → 503 error=message")
    void startDivine_throwWithMessage() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap()))
                .thenThrow(new RuntimeException("engine down"));
        mockMvc.perform(post("/api/sizhubazi/divine").contentType(MediaType.APPLICATION_JSON)
                        .content(json("birth", "1990-05-15 14:30")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("engine down"));
    }

    @Test
    @DisplayName("POST /divine：启动异常无 message → 503 error=未知错误")
    void startDivine_throwWithoutMessage() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.deployIfAbsent(any(), anyString(), anyList())).thenReturn(true);
        orchestrator.when(() -> ProcessOrchestrator.start(any(), anyString(), anyMap())).thenThrow(new RuntimeException());
        mockMvc.perform(post("/api/sizhubazi/divine").contentType(MediaType.APPLICATION_JSON)
                        .content(json("birth", "1990-05-15 14:30")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("未知错误"));
    }

    // ============ GET /divine/{key} ============

    @Test
    @DisplayName("GET /divine/{key}：fetchInstance 返回 null → status=RUNNING")
    void getDivineStatus_running() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.fetchInstance(any(), anyLong())).thenReturn(null);
        orchestrator.when(() -> ProcessOrchestrator.readVariables(any(), anyLong())).thenReturn(new LinkedHashMap<>());
        mockMvc.perform(get("/api/sizhubazi/divine/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("GET /divine/{key}：COMPLETED → 返回 chart 字段")
    void getDivineStatus_completed() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.fetchInstance(any(), anyLong()))
                .thenReturn(new ProcessOrchestrator.InstanceInfo("COMPLETED", false));
        orchestrator.when(() -> ProcessOrchestrator.statusOf(any(), anyBoolean())).thenReturn("COMPLETED");
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("chartBase", "SOLAR=1990-05-15");
        vars.put("fourPillars", "庚午 辛巳 辛巳 戊子");
        vars.put("alertTriggered", true);
        orchestrator.when(() -> ProcessOrchestrator.readVariables(any(), anyLong())).thenReturn(vars);
        mockMvc.perform(get("/api/sizhubazi/divine/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.chartBase").value("SOLAR=1990-05-15"))
                .andExpect(jsonPath("$.fourPillars").value("庚午 辛巳 辛巳 戊子"))
                .andExpect(jsonPath("$.alertTriggered").value(true));
    }

    @Test
    @DisplayName("GET /divine/{key}：查询异常 → 503")
    void getDivineStatus_exception() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.fetchInstance(any(), anyLong()))
                .thenThrow(new RuntimeException("query fail"));
        mockMvc.perform(get("/api/sizhubazi/divine/11"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("query fail"));
    }

    @Test
    @DisplayName("GET /divine/{key}：info 非空但状态非 COMPLETED → 返回 status=RUNNING（不填 chart 字段）")
    void getDivineStatus_runningFromInfo() throws Exception {
        orchestrator.when(() -> ProcessOrchestrator.fetchInstance(any(), anyLong()))
                .thenReturn(new ProcessOrchestrator.InstanceInfo("ACTIVE", false));
        orchestrator.when(() -> ProcessOrchestrator.statusOf(any(), anyBoolean())).thenReturn("RUNNING");
        orchestrator.when(() -> ProcessOrchestrator.readVariables(any(), anyLong())).thenReturn(new LinkedHashMap<>());
        mockMvc.perform(get("/api/sizhubazi/divine/12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.chartBase").doesNotExist());
    }

    // ============ GET /health ============

    @Test
    @DisplayName("GET /health：返回 engineAvailable/processId/bpmnPath")
    void health_ok() throws Exception {
        mockMvc.perform(get("/api/sizhubazi/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineAvailable").value(true))
                .andExpect(jsonPath("$.processId").value("SiZhuBaZiProcess"))
                .andExpect(jsonPath("$.bpmnPath").value(BPMN_PATH));
    }

    @Test
    @DisplayName("GET /health：client 为 null → engineAvailable=false")
    void health_clientNull_engineUnavailable() throws Exception {
        ReflectionTestUtils.setField(controller, "client", null);
        mockMvc.perform(get("/api/sizhubazi/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineAvailable").value(false));
    }
}


