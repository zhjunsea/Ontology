package com.ocean.ontologyframework.neinsight.web;

import com.ocean.utilities.ProcessOrchestrator;
import io.camunda.client.CamundaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/neinsight")
public class NEInsightController {

    private static final Logger log = LoggerFactory.getLogger(NEInsightController.class);

    private static final String INSIGHT_PROCESS_ID = "NewEnergyInsightProcess";

    @Autowired
    private CamundaClient client;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @PostMapping("/insight")
    public ResponseEntity<?> startInsight(@RequestBody Map<String, Object> body) {
        String insightScenario = body == null ? null : (String) body.get("insightScenario");
        if (insightScenario == null || insightScenario.isBlank()) {
            return ResponseEntity.badRequest().body(error("insightScenario 不能为空"));
        }

        try {
            boolean deployed = ProcessOrchestrator.deployIfAbsent(client, INSIGHT_PROCESS_ID, List.of(bpmnPath));
            if (!deployed) {
                log.warn("流程部署失败或已存在 | processId={}", INSIGHT_PROCESS_ID);
            }

            long instanceKey = ProcessOrchestrator.start(client, INSIGHT_PROCESS_ID, Map.of("insightScenario", insightScenario));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceKey", instanceKey);
            result.put("processId", INSIGHT_PROCESS_ID);
            result.put("insightScenario", insightScenario);

            log.info("✅ 洞察流程已启动 | insightScenario={} | instanceKey={}", insightScenario, instanceKey);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 启动洞察流程失败", e);
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

            if ("COMPLETED".equals(status)) {
                result.put("insightSummary", vars.getOrDefault("insightSummary", ""));
                result.put("instanceIri", vars.getOrDefault("instanceIri", ""));
                result.put("ontologyValidationStatus", vars.getOrDefault("ontologyValidationStatus", ""));
                result.put("insightSkillPath", vars.getOrDefault("insightSkillPath", ""));
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 查询洞察流程状态失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
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
