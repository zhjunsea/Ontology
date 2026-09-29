package com.ocean.ontologyframework.tcm.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断流程编排：启动 BPMN 实例、读取变量、处理人工任务、汇总诊断结果。
 *
 * <p>流程定义：{@code Process_Jingfang_Diagnosis}（{@code ontology/Jingfang_Diagnosis.bpmn}）。
 */
@Service
public class TCMDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(TCMDiagnosisService.class);

    public static final String PROCESS_ID = "Process_Jingfang_Diagnosis";

    /** 症状确认人工任务 */
    public static final String TASK_CONFIRM_SYMPTOMS = "Task_ConfirmSymptoms";
    /** 修改四诊信息人工任务 */
    public static final String TASK_REVISE_SIZHEN = "Task_ReviseSizhen";

    private static final long POLL_INTERVAL_MS = 250L;
    private static final long DEFAULT_SETTLE_TIMEOUT_MS = 30_000L;

    @Autowired(required = false)
    private CamundaClient camundaClient;

    private final ObjectMapper mapper = new ObjectMapper();

    public boolean isEngineAvailable() {
        return camundaClient != null;
    }

    // ============================================================
    // 启动
    // ============================================================

    /** 启动一次诊断。 */
    public Map<String, Object> start(String text) {
        requireEngine();
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("userInput", text == null ? "" : text.trim());
        vars.put("mappingRound", 0);

        ProcessInstanceEvent ev = camundaClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(vars)
                .send()
                .join();
        long key = ev.getProcessInstanceKey();
        log.info("[诊断] 流程已启动 processInstanceKey={} 输入={}", key, text);
        return awaitSettled(key, DEFAULT_SETTLE_TIMEOUT_MS);
    }

    // ============================================================
    // 快照
    // ============================================================

    /** 读取流程当前状态与结果。 */
    public Map<String, Object> snapshot(long processInstanceKey) {
        requireEngine();
        Map<String, Object> vars = readVariables(processInstanceKey);
        List<UserTask> tasks = readPendingTasks(processInstanceKey);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("processInstanceKey", processInstanceKey);
        out.put("status", statusOf(vars, tasks));
        out.put("mapping", mappingOf(vars));
        out.put("diagnosis", diagnosisOf(vars));
        out.put("pendingTasks", tasksOf(tasks));
        return out;
    }

    private String statusOf(Map<String, Object> vars, List<UserTask> tasks) {
        for (UserTask t : tasks) {
            if (TASK_CONFIRM_SYMPTOMS.equals(t.getElementId())) return "AWAITING_CONFIRMATION";
            if (TASK_REVISE_SIZHEN.equals(t.getElementId())) return "AWAITING_REVISION";
        }
        if (vars.containsKey("explanation")) return "COMPLETED";
        return "RUNNING";
    }

    private Map<String, Object> mappingOf(Map<String, Object> vars) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("detail", vars.getOrDefault("mappingDetail", Collections.emptyList()));
        m.put("ambiguous", vars.getOrDefault("ambiguousSymptoms", Collections.emptyList()));
        m.put("unmatched", vars.getOrDefault("unmatchedTexts", Collections.emptyList()));
        m.put("summary", vars.getOrDefault("mappingSummary", ""));
        m.put("needsConfirmation", vars.getOrDefault("needsConfirmation", false));
        m.put("round", vars.getOrDefault("mappingRound", 0));
        m.put("llmAvailable", vars.getOrDefault("llmAvailable", false));
        m.put("symptomIris", vars.getOrDefault("symptomIris", Collections.emptyList()));
        m.put("pulseIris", vars.getOrDefault("pulseIris", Collections.emptyList()));
        m.put("tongueIris", vars.getOrDefault("tongueIris", Collections.emptyList()));
        return m;
    }

    private Map<String, Object> diagnosisOf(Map<String, Object> vars) {
        Map<String, Object> d = new LinkedHashMap<>();
        // ---- 结果形态：CONFIRMED（确定结论）/ NO_MAIN_MATCH（无主证命中，给出双路径）----
        // 依铁律 16：NO_MAIN_MATCH 时 fangzheng 为「方证未定」，候选不得冒充结论。
        d.put("outcome", vars.getOrDefault("outcome", "CONFIRMED"));
        d.put("sixChannelCn", vars.get("sixChannelCn"));
        d.put("liujingTypesCn", vars.getOrDefault("liujingTypesCn", Collections.emptyList()));
        d.put("bagang", vars.get("bagangResult"));
        d.put("fangzhengCn", vars.get("fangzhengCn"));
        d.put("jianJiaZhengs", vars.getOrDefault("jianJiaZhengs", Collections.emptyList()));
        d.put("baseFormulaCn", vars.get("baseFormulaCn"));
        d.put("finalFormulaCn", vars.get("finalFormulaCn"));
        d.put("derived", vars.getOrDefault("derived", false));
        d.put("herbsCn", vars.getOrDefault("herbsCn", Collections.emptyList()));
        d.put("addedHerbCn", vars.getOrDefault("addedHerbCn", Collections.emptyList()));
        d.put("removedHerbCn", vars.getOrDefault("removedHerbCn", Collections.emptyList()));
        d.put("dosageChanges", vars.getOrDefault("dosageChanges", Collections.emptyList()));
        d.put("appliedRules", vars.getOrDefault("appliedRules", Collections.emptyList()));
        d.put("ruleSources", vars.getOrDefault("ruleSources", Collections.emptyList()));
        d.put("warnings", vars.getOrDefault("warnings", Collections.emptyList()));
        d.put("herbModificationSummary", vars.get("herbModificationSummary"));
        d.put("explanation", vars.get("explanation"));
        // ---- 候选方证（含 evidence 标记）----
        d.put("candidateFangzhengsCn",
                vars.getOrDefault("candidateFangzhengsCn", Collections.emptyList()));
        d.put("candidateScores", vars.getOrDefault("candidateScores", Collections.emptyList()));
        // ---- 每个候选方证的证据明细（与 candidateFangzhengsCn 下标一一对应）----
        // 命中主证 / 缺口主证 / 命中或然证，均为四诊发现的中文名。
        d.put("candidateMatchedMainCn",
                vars.getOrDefault("candidateMatchedMainCn", Collections.emptyList()));
        d.put("candidateMissingMainCn",
                vars.getOrDefault("candidateMissingMainCn", Collections.emptyList()));
        d.put("candidateMatchedPossCn",
                vars.getOrDefault("candidateMatchedPossCn", Collections.emptyList()));
        // ---- 结论方证的命中证据（四诊）----
        d.put("matchedMainSymptomsCn",
                vars.getOrDefault("matchedMainSymptomsCn", Collections.emptyList()));
        d.put("matchedPossSymptomsCn",
                vars.getOrDefault("matchedPossSymptomsCn", Collections.emptyList()));
        // ---- 双路径（仅 NO_MAIN_MATCH 时存在）----
        // pathA：追问（补充哪些症状可定八纲/六经/方证）
        // pathB：或然症候选（evidence=POSS_ONLY，仅供临床参考）
        d.put("pathA", vars.get("pathA"));
        d.put("pathB", vars.get("pathB"));
        return d;
    }

    // ============================================================
    // 人工任务
    // ============================================================

    /** 提交症状确认。 */
    public Map<String, Object> confirmSymptoms(long processInstanceKey,
                                               List<String> confirmed,
                                               List<String> rejected,
                                               List<String> extra) {
        requireEngine();
        UserTask task = findTask(processInstanceKey, TASK_CONFIRM_SYMPTOMS);
        if (task == null) {
            throw new IllegalStateException("当前流程没有待确认的症状映射任务");
        }
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("confirmed", confirmed == null ? List.of() : confirmed);
        vars.put("rejected", rejected == null ? List.of() : rejected);
        vars.put("extra", extra == null ? List.of() : extra);

        camundaClient.newCompleteUserTaskCommand(task.getUserTaskKey())
                .variables(vars)
                .send()
                .join();
        log.info("[诊断] 症状确认已提交 key={} confirmed={} rejected={} extra={}",
                processInstanceKey, confirmed, rejected, extra);
        return awaitSettled(processInstanceKey, DEFAULT_SETTLE_TIMEOUT_MS);
    }

    /** 通用：完成指定人工任务。 */
    public Map<String, Object> completeTask(long processInstanceKey, long userTaskKey, Map<String, Object> vars) {
        requireEngine();
        camundaClient.newCompleteUserTaskCommand(userTaskKey)
                .variables(vars == null ? Map.of() : vars)
                .send()
                .join();
        log.info("[诊断] 人工任务已完成 key={} taskKey={}", processInstanceKey, userTaskKey);
        return awaitSettled(processInstanceKey, DEFAULT_SETTLE_TIMEOUT_MS);
    }

    /** 列出待办人工任务。 */
    public List<Map<String, Object>> pendingTasks(long processInstanceKey) {
        requireEngine();
        return tasksOf(readPendingTasks(processInstanceKey));
    }

    private List<Map<String, Object>> tasksOf(List<UserTask> tasks) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserTask t : tasks) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userTaskKey", t.getUserTaskKey());
            m.put("elementId", t.getElementId());
            m.put("name", t.getName());
            m.put("state", t.getState() == null ? null : t.getState().toString());
            out.add(m);
        }
        return out;
    }

    private UserTask findTask(long processInstanceKey, String elementId) {
        for (UserTask t : readPendingTasks(processInstanceKey)) {
            if (elementId.equals(t.getElementId())) return t;
        }
        return null;
    }

    // ============================================================
    // 轮询
    // ============================================================

    private Map<String, Object> awaitSettled(long processInstanceKey, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        Map<String, Object> snap = snapshot(processInstanceKey);
        while ("RUNNING".equals(snap.get("status")) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            snap = snapshot(processInstanceKey);
        }
        return snap;
    }

    // ============================================================
    // 底层读取
    // ============================================================

    private Map<String, Object> readVariables(long processInstanceKey) {
        Map<String, Object> vars = new LinkedHashMap<>();
        try {
            List<Variable> items = camundaClient.newVariableSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey))
                    .send()
                    .join()
                    .items();
            for (Variable v : items) {
                vars.put(v.getName(), decode(v.getValue()));
            }
        } catch (Exception e) {
            log.warn("[诊断] 读取流程变量失败 key={}: {}", processInstanceKey, e.toString());
        }
        return vars;
    }

    private List<UserTask> readPendingTasks(long processInstanceKey) {
        try {
            return camundaClient.newUserTaskSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey))
                    .send()
                    .join()
                    .items();
        } catch (Exception e) {
            log.warn("[诊断] 读取人工任务失败 key={}: {}", processInstanceKey, e.toString());
            return List.of();
        }
    }

    private Object decode(String json) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    private void requireEngine() {
        if (camundaClient == null) {
            throw new IllegalStateException("Camunda 客户端不可用：请确认已启动 Zeebe/Camunda 8 并配置 camunda.client.*");
        }
    }
}
