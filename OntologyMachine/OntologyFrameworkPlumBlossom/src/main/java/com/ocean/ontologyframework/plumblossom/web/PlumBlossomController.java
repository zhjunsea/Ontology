package com.ocean.ontologyframework.plumblossom.web;

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
@RequestMapping("/api/plumblossom")
public class PlumBlossomController {

    private static final Logger log = LoggerFactory.getLogger(PlumBlossomController.class);

    private static final String DIVINATION_PROCESS_ID = "PlumBlossomDivinationProcess";

    @Autowired
    private CamundaClient client;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @PostMapping("/divination")
    public ResponseEntity<?> startDivination(@RequestBody Map<String, Object> body) {
        String subject = body == null ? null : (String) body.get("subject");
        if (subject == null || subject.isBlank()) {
            return ResponseEntity.badRequest().body(error("subject 不能为空"));
        }

        try {
            boolean deployed = ProcessOrchestrator.deployIfAbsent(client, DIVINATION_PROCESS_ID, List.of(bpmnPath));
            if (!deployed) {
                log.warn("流程部署失败或已存在 | processId={}", DIVINATION_PROCESS_ID);
            }

            Map<String, Object> variables = new LinkedHashMap<>();
            body.forEach((k, v) -> {
                if (v != null) {
                    variables.put(k, v);
                }
            });

            long instanceKey = ProcessOrchestrator.start(client, DIVINATION_PROCESS_ID, variables);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceKey", instanceKey);
            result.put("processId", DIVINATION_PROCESS_ID);
            result.put("subject", subject);

            log.info("✅ 占断流程已启动 | subject={} | instanceKey={}", subject, instanceKey);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 启动占断流程失败", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/divination/{key}")
    public ResponseEntity<?> getDivinationStatus(@PathVariable("key") long key) {
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
                result.put("alertText", vars.getOrDefault("alertText", ""));
                result.put("triggeredPrecept", vars.getOrDefault("triggeredPrecept", ""));
                result.put("originalHexagram", vars.getOrDefault("originalHexagram", ""));
                result.put("changedHexagram", vars.getOrDefault("changedHexagram", ""));
                result.put("mutualHexagram", vars.getOrDefault("mutualHexagram", ""));
                result.put("auspiciousness", vars.getOrDefault("auspiciousness", ""));
                result.put("divinationResult", vars.getOrDefault("divinationResult", ""));
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 查询占断状态失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engineAvailable", client != null);
        out.put("processId", DIVINATION_PROCESS_ID);
        out.put("bpmnPath", bpmnPath);
        return out;
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg == null ? "未知错误" : msg);
        return m;
    }
}
