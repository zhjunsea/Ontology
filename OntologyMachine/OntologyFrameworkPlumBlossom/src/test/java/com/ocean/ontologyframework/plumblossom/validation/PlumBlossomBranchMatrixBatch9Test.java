package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.web.PlumBlossomController;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.fetch.ProcessInstanceGetRequest;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.request.ProcessDefinitionSearchRequest;
import io.camunda.client.api.search.request.VariableSearchRequest;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.SearchResponsePage;
import io.camunda.client.api.search.response.Variable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 第九批·Web 控制层引擎成功路径（standalone MockMvc + mock CamundaClient，纯离线）。
 *
 * <p>第八批以 {@code CamundaClient=null} 覆盖了降级路径（400/503/RUNNING）。本批补齐
 * {@code ProcessOrchestrator} 调用链被 mock 后的「成功/完成/异常状态」主路径：
 * <ul>
 *   <li>POST /divination 成功：deployIfAbsent→start→200，返回 instanceKey/processId/subject，且 body 中 null 值被过滤；</li>
 *   <li>POST /divination 部署失败（deployIfAbsent 吞异常返 false，仅 warn）仍继续 start→200；start 抛异常→503；</li>
 *   <li>GET /divination/{key}：COMPLETED 回填 14 字段（全/缺省两种情况）、TERMINATED / INCIDENT / RUNNING 状态分派；</li>
 *   <li>readVariables 抛异常降级为空 map，状态仍 200。</li>
 * </ul>
 * mock 仅作用于 Camunda fluent 客户端，不触达真实引擎/RabbitMQ/MySQL。
 */
public class PlumBlossomBranchMatrixBatch9Test {

    private CamundaClient client;
    private PlumBlossomController controller;
    private MockMvc mvc;

    private static final String[] COMPLETED_FIELDS = {
            "alertText", "triggeredPrecept", "originalHexagram", "changedHexagram", "mutualHexagram",
            "auspiciousness", "divinationResult", "divinationFactSheet", "aiInterpretation",
            "aiInterpretationPlain", "originalHexagramDictumText", "originalHexagramDictumInterpretation",
            "movingLineDictumText", "movingLineDictumInterpretation"
    };

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {
        client = mock(CamundaClient.class);
        PlumBlossomController controller = new PlumBlossomController();
        Field cmdField = PlumBlossomController.class.getDeclaredField("client");
        cmdField.setAccessible(true);
        cmdField.set(controller, client);
        Field bpmnField = PlumBlossomController.class.getDeclaredField("bpmnPath");
        bpmnField.setAccessible(true);
        bpmnField.set(controller, "dummy/plumBlossomProcess.bpmn");
        this.controller = controller;
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ======================================================================
    // 一、mock 装配辅助
    // ======================================================================

    /** 幂等部署返回「无既有定义 → 部署成功」。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubDeploySuccess() {
        ProcessDefinitionSearchRequest preq = mock(ProcessDefinitionSearchRequest.class);
        CamundaFuture<SearchResponse<ProcessDefinition>> pfut = mock(CamundaFuture.class);
        SearchResponse<ProcessDefinition> presp = mock(SearchResponse.class);
        when(client.newProcessDefinitionSearchRequest()).thenReturn(preq);
        when(preq.filter(any(Consumer.class))).thenReturn(preq);
        when(preq.send()).thenReturn(pfut);
        when(pfut.join()).thenReturn(presp);
        when(presp.items()).thenReturn(Collections.emptyList());

        DeployResourceCommandStep1 d1 = mock(DeployResourceCommandStep1.class);
        DeployResourceCommandStep2 d2 = mock(DeployResourceCommandStep2.class);
        CamundaFuture<DeploymentEvent> dfut = mock(CamundaFuture.class);
        DeploymentEvent dev = mock(DeploymentEvent.class);
        when(client.newDeployResourceCommand()).thenReturn(d1);
        when(d1.addResourceFile(anyString())).thenReturn(d2);
        when(d2.send()).thenReturn(dfut);
        when(dfut.join()).thenReturn(dev);
        when(dev.getProcesses()).thenReturn(Collections.emptyList());
    }

    /** 启动流程成功，返回指定 instanceKey。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubStartSuccess(long instanceKey) {
        CreateProcessInstanceCommandStep1 c1 = mock(CreateProcessInstanceCommandStep1.class);
        CreateProcessInstanceCommandStep2 c2 = mock(CreateProcessInstanceCommandStep2.class);
        CreateProcessInstanceCommandStep3 c3 = mock(CreateProcessInstanceCommandStep3.class);
        CamundaFuture<ProcessInstanceEvent> cfut = mock(CamundaFuture.class);
        ProcessInstanceEvent ev = mock(ProcessInstanceEvent.class);
        when(client.newCreateInstanceCommand()).thenReturn(c1);
        when(c1.bpmnProcessId(anyString())).thenReturn(c2);
        when(c2.latestVersion()).thenReturn(c3);
        when(c3.variables(anyMap())).thenReturn(c3);
        when(c3.send()).thenReturn(cfut);
        when(cfut.join()).thenReturn(ev);
        when(ev.getProcessInstanceKey()).thenReturn(instanceKey);
    }

    /** 启动流程 send().join() 抛异常。 */
    private void stubStartThrow() {
        stubStartThrow(new RuntimeException("engine down"));
    }

    /** 启动流程 send().join() 抛出指定异常。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubStartThrow(RuntimeException toThrow) {
        CreateProcessInstanceCommandStep1 c1 = mock(CreateProcessInstanceCommandStep1.class);
        CreateProcessInstanceCommandStep2 c2 = mock(CreateProcessInstanceCommandStep2.class);
        CreateProcessInstanceCommandStep3 c3 = mock(CreateProcessInstanceCommandStep3.class);
        CamundaFuture<ProcessInstanceEvent> cfut = mock(CamundaFuture.class);
        when(client.newCreateInstanceCommand()).thenReturn(c1);
        when(c1.bpmnProcessId(anyString())).thenReturn(c2);
        when(c2.latestVersion()).thenReturn(c3);
        when(c3.variables(anyMap())).thenReturn(c3);
        when(c3.send()).thenReturn(cfut);
        when(cfut.join()).thenThrow(toThrow);
    }

    /** fetchInstance 返回指定状态（nullInstance=true 时返回 null，按 RUNNING 处理）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubFetch(ProcessInstanceState state, boolean hasIncident, boolean nullInstance) {
        ProcessInstanceGetRequest greq = mock(ProcessInstanceGetRequest.class);
        CamundaFuture<ProcessInstance> gfut = mock(CamundaFuture.class);
        when(client.newProcessInstanceGetRequest(anyLong())).thenReturn(greq);
        when(greq.send()).thenReturn(gfut);
        if (nullInstance) {
            when(gfut.join()).thenReturn(null);
            return;
        }
        ProcessInstance pi = mock(ProcessInstance.class);
        when(gfut.join()).thenReturn(pi);
        when(pi.getState()).thenReturn(state);
        when(pi.getHasIncident()).thenReturn(hasIncident);
    }

    /** readVariables 返回给定键值（值按原始字符串解码，非 JSON 会原样返回）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubVariables(Map<String, String> keyValues) {
        VariableSearchRequest vreq = mock(VariableSearchRequest.class);
        CamundaFuture<SearchResponse<Variable>> vfut = mock(CamundaFuture.class);
        SearchResponse<Variable> vresp = mock(SearchResponse.class);
        SearchResponsePage page = mock(SearchResponsePage.class);
        List<Variable> items = new ArrayList<>();
        for (Map.Entry<String, String> e : keyValues.entrySet()) {
            Variable v = mock(Variable.class);
            when(v.getName()).thenReturn(e.getKey());
            when(v.getValue()).thenReturn(e.getValue());
            items.add(v);
        }
        when(client.newVariableSearchRequest()).thenReturn(vreq);
        when(vreq.filter(any(Consumer.class))).thenReturn(vreq);
        when(vreq.page(any(Consumer.class))).thenReturn(vreq);
        when(vreq.send()).thenReturn(vfut);
        when(vfut.join()).thenReturn(vresp);
        when(vresp.items()).thenReturn(items);
        when(vresp.page()).thenReturn(page);
        when(page.totalItems()).thenReturn((long) items.size());
    }

    /** readVariables 读取时抛异常（走降级返回空 map）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubVariablesThrow() {
        VariableSearchRequest vreq = mock(VariableSearchRequest.class);
        when(client.newVariableSearchRequest()).thenReturn(vreq);
        when(vreq.filter(any(Consumer.class))).thenReturn(vreq);
        when(vreq.page(any(Consumer.class))).thenReturn(vreq);
        when(vreq.send()).thenThrow(new RuntimeException("var read boom"));
    }

    // ======================================================================
    // 二、POST /divination 启动路径
    // ======================================================================

    @Test
    @DisplayName("B9-01 POST /divination：部署+启动成功 → 200，回显 instanceKey/processId/subject，body 中 null 值被过滤")
    void divination_success() throws Exception {
        stubDeploySuccess();
        stubStartSuccess(9527L);
        mvc.perform(post("/api/plumblossom/divination")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"测求职\",\"note\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(9527))
                .andExpect(jsonPath("$.processId").value("PlumBlossomDivinationProcess"))
                .andExpect(jsonPath("$.subject").value("测求职"));
    }

    @Test
    @DisplayName("B9-02 POST /divination：deployIfAbsent 抛异常吞为 false（仅 warn）仍继续 start → 200")
    void divination_deployFailsButStartOk() throws Exception {
        when(client.newProcessDefinitionSearchRequest()).thenThrow(new RuntimeException("no deploy api"));
        stubStartSuccess(11L);
        mvc.perform(post("/api/plumblossom/divination")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"测出行\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceKey").value(11))
                .andExpect(jsonPath("$.subject").value("测出行"));
    }

    @Test
    @DisplayName("B9-03 POST /divination：start 抛异常 → 503 且 error 非空")
    void divination_startThrows() throws Exception {
        stubDeploySuccess();
        stubStartThrow();
        mvc.perform(post("/api/plumblossom/divination")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"测诉讼\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
    }

    // ======================================================================
    // 三、GET /divination/{key} 状态查询主路径
    // ======================================================================

    @Test
    @DisplayName("B9-04 GET /divination/{key}：COMPLETED → 14 字段全回填（取变量值）")
    void status_completed_fullFields() throws Exception {
        stubFetch(ProcessInstanceState.COMPLETED, false, false);
        Map<String, String> vars = new LinkedHashMap<>();
        for (String k : COMPLETED_FIELDS) {
            vars.put(k, "V_" + k);
        }
        stubVariables(vars);

        mvc.perform(get("/api/plumblossom/divination/7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.variables.alertText").value("V_alertText"))
                .andExpect(jsonPath("$.alertText").value("V_alertText"))
                .andExpect(jsonPath("$.triggeredPrecept").value("V_triggeredPrecept"))
                .andExpect(jsonPath("$.originalHexagram").value("V_originalHexagram"))
                .andExpect(jsonPath("$.changedHexagram").value("V_changedHexagram"))
                .andExpect(jsonPath("$.mutualHexagram").value("V_mutualHexagram"))
                .andExpect(jsonPath("$.auspiciousness").value("V_auspiciousness"))
                .andExpect(jsonPath("$.divinationResult").value("V_divinationResult"))
                .andExpect(jsonPath("$.divinationFactSheet").value("V_divinationFactSheet"))
                .andExpect(jsonPath("$.aiInterpretation").value("V_aiInterpretation"))
                .andExpect(jsonPath("$.aiInterpretationPlain").value("V_aiInterpretationPlain"))
                .andExpect(jsonPath("$.originalHexagramDictumText").value("V_originalHexagramDictumText"))
                .andExpect(jsonPath("$.originalHexagramDictumInterpretation").value("V_originalHexagramDictumInterpretation"))
                .andExpect(jsonPath("$.movingLineDictumText").value("V_movingLineDictumText"))
                .andExpect(jsonPath("$.movingLineDictumInterpretation").value("V_movingLineDictumInterpretation"));
    }

    @Test
    @DisplayName("B9-05 GET /divination/{key}：COMPLETED 但变量缺字段 → 缺失字段回退为空串")
    void status_completed_missingDefaults() throws Exception {
        stubFetch(ProcessInstanceState.COMPLETED, false, false);
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("alertText", "有天警");
        stubVariables(vars);

        mvc.perform(get("/api/plumblossom/divination/7002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.alertText").value("有天警"))
                .andExpect(jsonPath("$.originalHexagram").value(""))
                .andExpect(jsonPath("$.movingLineDictumInterpretation").value(""));
    }

    @Test
    @DisplayName("B9-06 GET /divination/{key}：TERMINATED → 状态映射，不触发 14 字段回填")
    void status_terminated() throws Exception {
        stubFetch(ProcessInstanceState.TERMINATED, false, false);
        stubVariables(Collections.emptyMap());

        mvc.perform(get("/api/plumblossom/divination/7003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINATED"))
                .andExpect(jsonPath("$.alertText").doesNotExist());
    }

    @Test
    @DisplayName("B9-07 GET /divination/{key}：ACTIVE+incident → INCIDENT")
    void status_incident() throws Exception {
        stubFetch(ProcessInstanceState.ACTIVE, true, false);
        stubVariables(Collections.emptyMap());

        mvc.perform(get("/api/plumblossom/divination/7004"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCIDENT"));
    }

    @Test
    @DisplayName("B9-08 GET /divination/{key}：ACTIVE 无 incident → RUNNING")
    void status_running() throws Exception {
        stubFetch(ProcessInstanceState.ACTIVE, false, false);
        stubVariables(Collections.emptyMap());

        mvc.perform(get("/api/plumblossom/divination/7005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("B9-09 GET /divination/{key}：readVariables 抛异常降级为空 map，状态 COMPLETED 仍 200 且字段为空串")
    void status_variablesThrow() throws Exception {
        stubFetch(ProcessInstanceState.COMPLETED, false, false);
        stubVariablesThrow();

        mvc.perform(get("/api/plumblossom/divination/7006"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.variables").isEmpty())
                .andExpect(jsonPath("$.alertText").value(""));
    }

    @Test
    @DisplayName("B9-10 fetchInstance 返回 null（未导出/404）→ 按 RUNNING 处理")
    void status_instanceNull() throws Exception {
        stubFetch(null, false, true);
        stubVariables(Collections.emptyMap());

        mvc.perform(get("/api/plumblossom/divination/7007"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING"));
    }

    @Test
    @DisplayName("B9-11 GET /health：引擎注入非 null → engineAvailable=true")
    void health_enginePresent() throws Exception {
        mvc.perform(get("/api/plumblossom/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engineAvailable").value(true));
    }

    @Test
    @DisplayName("B9-12 POST /divination：body 为 null → 400（subject 取 null 分支）")
    void divination_nullBody() {
        ResponseEntity<?> resp = controller.startDivination(null);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    @DisplayName("B9-13 POST /divination：start 抛无消息异常 → 503 且 error=未知错误（error(null) 分支）")
    void divination_startThrowsNoMessage() throws Exception {
        stubDeploySuccess();
        stubStartThrow(new RuntimeException());
        mvc.perform(post("/api/plumblossom/divination")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subject\":\"测病\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("未知错误"));
    }
}
