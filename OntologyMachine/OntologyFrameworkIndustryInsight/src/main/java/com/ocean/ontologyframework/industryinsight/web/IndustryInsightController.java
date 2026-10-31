package com.ocean.ontologyframework.industryinsight.web;

import com.ocean.utilities.ProcessOrchestrator;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/industryinsight")
public class IndustryInsightController {

    private static final Logger log = LoggerFactory.getLogger(IndustryInsightController.class);

    private static final String INSIGHT_PROCESS_ID = "IndustryInsightProcess";

    @Autowired
    private CamundaClient client;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @PostMapping("/insight")
    public ResponseEntity<?> startInsight(@RequestBody Map<String, Object> body) {
        String scenario = body == null ? null : (String) body.get("scenario");
        if (scenario == null || scenario.isBlank()) {
            return ResponseEntity.badRequest().body(error("洞察场景（scenario）不能为空"));
        }

        try {
            boolean deployed = ProcessOrchestrator.deployIfAbsent(client, INSIGHT_PROCESS_ID, List.of(bpmnPath));
            if (!deployed) {
                log.warn("流程部署失败或已存在 | processId={}", INSIGHT_PROCESS_ID);
            }

            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("scenario", scenario);
            if (body.get("industry") != null) {
                vars.put("industry", body.get("industry"));
            }
            if (body.get("region") != null) {
                vars.put("region", body.get("region"));
            }
            if (body.get("timeWindow") != null) {
                vars.put("timeWindow", body.get("timeWindow"));
            }

            long instanceKey = ProcessOrchestrator.start(client, INSIGHT_PROCESS_ID, vars);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceKey", instanceKey);
            result.put("processId", INSIGHT_PROCESS_ID);
            result.put("scenario", scenario);

            log.info("✅ 行业洞察流程已启动 | instanceKey={}", instanceKey);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 启动行业洞察流程失败", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/insight/{key}")
    public ResponseEntity<?> getInsightStatus(@PathVariable("key") long key) {
        try {
            ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, key);
            String status = (info == null) ? "RUNNING"
                    : ProcessOrchestrator.statusOf(info.state(), info.hasIncident());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceKey", key);
            result.put("status", status);

            Map<String, Object> vars = ProcessOrchestrator.readVariables(client, key);
            result.put("variables", vars);

            // 二级存储导出延迟兜底：SKILL 终态产物已写入变量即可判定完成，消除「已完成仍显示 RUNNING」
            if (hasVar(vars, "insightSkillPath") || hasVar(vars, "insightSummary")) {
                status = "COMPLETED";
                result.put("status", status);
            }

            if ("COMPLETED".equals(status)) {
                result.put("insightSummary", vars.getOrDefault("insightSummary", ""));
                result.put("deliverables", vars.getOrDefault("deliverables", ""));
                result.put("insightSkillPath", vars.getOrDefault("insightSkillPath", ""));
                result.put("ontologyValidationStatus", vars.getOrDefault("ontologyValidationStatus", ""));
            } else if ("INCIDENT".equals(status) || "TERMINATED".equals(status)) {
                result.put("clarificationText", vars.getOrDefault("clarificationText", ""));
            } else if ("RUNNING".equals(status)) {
                // 按「已写入变量」反推当前步骤（单调、不回退，不受导出延迟影响）；不可用时回退活动元素查询
                String step = stepByVariables(vars);
                if (step == null) {
                    step = stepByActiveElement(client, key);
                }
                if (step != null) {
                    result.put("currentStep", step);
                }
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 查询流程状态失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    /**
     * 下载洞察 SKILL（节点 14 产出的「可执行指令文件」，Markdown）。
     * 优先读取落盘文件（{@code insightSkillPath}），回退为流程变量中的 SKILL 全文。
     */
    @GetMapping("/insight/{key}/skill")
    public ResponseEntity<?> downloadInsightSkill(@PathVariable("key") long key) {
        try {
            Map<String, Object> vars = ProcessOrchestrator.readVariables(client, key);

            String content = "";
            Object path = vars.get("insightSkillPath");
            if (path != null && !String.valueOf(path).isBlank()) {
                try {
                    Path p = Path.of(String.valueOf(path));
                    if (Files.isRegularFile(p)) {
                        content = Files.readString(p, StandardCharsets.UTF_8);
                    }
                } catch (Exception ioEx) {
                    log.warn("[API] 读取 SKILL 文件失败，回退流程变量 | key={} | {}", key, ioEx.getMessage());
                }
            }
            if (content.isBlank()) {
                content = String.valueOf(vars.getOrDefault("insightSummary", ""));
            }
            if (content.isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("洞察 SKILL 尚未生成"));
            }

            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"industryinsight-skill-" + key + ".md\"")
                    .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                    .body(bytes);

        } catch (Exception e) {
            log.error("[API] 下载洞察 SKILL 失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    /**
     * 13 步主线（{BPMN 节点名, 判据变量}），严格遵循「先五看、后三定」。条件节点
     * 「关键机会/风险消息外发」按优先级动态插入；「返回场景澄清提示」为校验不通过时的终点分支，
     * 均在推导时单独处理。
     */
    private static final String[][] MAIN_STEPS = {
            {"洞察场景解析与校验", "scenarioParsed"},
            {"看行业/趋势", "industryTrend"},
            {"看市场/客户", "marketCustomer"},
            {"看竞争", "competition"},
            {"看自己", "selfInsight"},
            {"看机会（机会点识别）", "opportunityList"},
            {"机会点 SPAN 定位与优先级判定", "opportunityPriority"},
            {"五看结论交叉验证与机会共识", "crossValidation"},
            {"洞察结论与关键判断", "insightConclusion"},
            {"定战略控制点", "controlPoints"},
            {"定目标（SMART 校验）", "objectives"},
            {"定策略（含定节奏/定路径）", "strategies"},
            {"生成洞察 SKILL", "insightSummary"},
    };

    private static final String STEP_CLARIFY = "返回场景澄清提示";
    private static final String STEP_SEND_MESSAGE = "关键机会/风险消息外发";
    private static final String STEP_SPAN = "机会点 SPAN 定位与优先级判定";

    /**
     * 依据「已写入的流程变量」反推当前步骤（对齐 TCM 的 inferDone 思路）：
     * 变量一经写入即持久，进度单调、不回退，规避 search API 二级存储（exporter 异步导出）
     * 延迟导致的「当前步骤滞后」。全部完成返回 {@code null}。
     */
    private static String stepByVariables(Map<String, Object> vars) {
        if ("不通过".equals(str(vars.get("validationResult")))) {
            return hasVar(vars, "clarificationText") ? null : STEP_CLARIFY;
        }
        List<String[]> chain = new ArrayList<>();
        for (String[] s : MAIN_STEPS) {
            chain.add(s);
            if (STEP_SPAN.equals(s[0]) && "高".equals(str(vars.get("priorityLevel")))) {
                chain.add(new String[]{STEP_SEND_MESSAGE, "outboundMessage"});
            }
        }
        for (String[] s : chain) {
            if (!hasVar(vars, s[1])) {
                return s[0];
            }
        }
        return null;
    }

    /** 判据变量是否「已写入且有值」（空串/空集合视为未写入）。 */
    private static boolean hasVar(Map<String, Object> vars, String name) {
        Object v = vars == null ? null : vars.get(name);
        if (v == null) {
            return false;
        }
        if (v instanceof String s) {
            return !s.isBlank();
        }
        if (v instanceof Collection<?> c) {
            return !c.isEmpty();
        }
        if (v instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        return true;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    /**
     * 回退方案：查询流程实例当前正在执行的活动节点名（取 BPMN 中活动的 SERVICE_TASK 的 name）。
     * 节点切换间（如流经网关）可能暂无活动 SERVICE_TASK，此时返回 {@code null}。
     * 注意：该查询走二级存储、存在导出延迟，仅在变量反推不可用时兜底使用。
     */
    private String stepByActiveElement(CamundaClient client, long key) {
        try {
            List<ElementInstance> items = client.newElementInstanceSearchRequest()
                    .filter(f -> f.processInstanceKey(key)
                            .state(ElementInstanceState.ACTIVE)
                            .type(ElementInstanceType.SERVICE_TASK))
                    .send()
                    .join()
                    .items();
            if (items == null || items.isEmpty()) {
                return null;
            }
            ElementInstance ei = items.get(0);
            String name = ei.getElementName();
            return (name == null || name.isBlank()) ? ei.getElementId() : name;
        } catch (Exception e) {
            log.debug("[API] 查询当前活动节点失败 key={}: {}", key, e.toString());
            return null;
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engineAvailable", client != null);
        out.put("processId", INSIGHT_PROCESS_ID);
        out.put("bpmnPath", bpmnPath);
        return out;
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg == null ? "未知错误" : msg);
        return m;
    }
}
