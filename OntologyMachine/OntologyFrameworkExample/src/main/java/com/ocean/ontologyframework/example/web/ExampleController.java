package com.ocean.ontologyframework.example.web;

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
@RequestMapping("/api/example")
public class ExampleController {

    private static final Logger log = LoggerFactory.getLogger(ExampleController.class);

    private static final String BAKING_PROCESS_ID = "PizzaMakingStandardProcess";

    @Autowired
    private CamundaClient client;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @PostMapping("/baking")
    public ResponseEntity<?> startBaking(@RequestBody Map<String, Object> body) {
        String pizzaType = body == null ? null : (String) body.get("pizzaType");
        if (pizzaType == null || pizzaType.isBlank()) {
            return ResponseEntity.badRequest().body(error("pizzaType 不能为空"));
        }

        try {
            boolean deployed = ProcessOrchestrator.deployIfAbsent(client, BAKING_PROCESS_ID, List.of(bpmnPath));
            if (!deployed) {
                log.warn("流程部署失败或已存在 | processId={}", BAKING_PROCESS_ID);
            }

            long instanceKey = ProcessOrchestrator.start(client, BAKING_PROCESS_ID, Map.of("pizzaType", pizzaType));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceKey", instanceKey);
            result.put("processId", BAKING_PROCESS_ID);
            result.put("pizzaType", pizzaType);

            log.info("✅ 烘焙流程已启动 | pizzaType={} | instanceKey={}", pizzaType, instanceKey);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 启动烘焙流程失败", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/baking/{key}")
    public ResponseEntity<?> getBakingStatus(@PathVariable("key") long key) {
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
                result.put("pizzaSummary", vars.getOrDefault("pizzaSummary", ""));
                result.put("myPizzaInstanceIri", vars.getOrDefault("myPizzaInstanceIri", ""));
                result.put("ontologyValidationStatus", vars.getOrDefault("ontologyValidationStatus", ""));
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 查询流程状态失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engineAvailable", client != null);
        out.put("processId", BAKING_PROCESS_ID);
        out.put("bpmnPath", bpmnPath);
        return out;
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg == null ? "未知错误" : msg);
        return m;
    }
}
