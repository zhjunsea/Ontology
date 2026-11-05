package com.ocean.ontologyframework.sizhubazi.web;

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
@RequestMapping("/api/sizhubazi")
public class SiZhuBaZiController {

    private static final Logger log = LoggerFactory.getLogger(SiZhuBaZiController.class);

    private static final String PROCESS_ID = "SiZhuBaZiProcess";

    @Autowired
    private CamundaClient client;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @PostMapping("/divine")
    public ResponseEntity<?> startDivine(@RequestBody Map<String, Object> body) {
        Object birth = body == null ? null : body.get("birth");
        if (birth == null) {
            return ResponseEntity.badRequest().body(error("birth（生辰）不能为空"));
        }

        String mode = body.get("mode") == null ? "单命" : String.valueOf(body.get("mode"));
        if (!"单命".equals(mode) && !"合婚".equals(mode)) {
            return ResponseEntity.badRequest().body(error("mode 只能为「单命」或「合婚」"));
        }
        if ("合婚".equals(mode) && body.get("partnerBirth") == null) {
            return ResponseEntity.badRequest().body(error("合婚模式须提供 partnerBirth（对方生辰）"));
        }

        try {
            boolean deployed = ProcessOrchestrator.deployIfAbsent(client, PROCESS_ID,
                    List.of(com.ocean.ontologyframework.sizhubazi.OntologyPathResolver.resolve(bpmnPath)));
            if (!deployed) {
                log.warn("流程部署失败或已存在 | processId={}", PROCESS_ID);
            }

            Map<String, Object> variables = new LinkedHashMap<>();
            body.forEach((k, v) -> {
                if (v != null) {
                    variables.put(k, v);
                }
            });
            variables.put("inputMode", mode);

            long instanceKey = ProcessOrchestrator.start(client, PROCESS_ID, variables);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceKey", instanceKey);
            result.put("processId", PROCESS_ID);
            result.put("mode", mode);

            log.info("✅ 四柱命理推演流程已启动 | mode={} | instanceKey={}", mode, instanceKey);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 启动四柱命理推演流程失败", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/divine/{key}")
    public ResponseEntity<?> getDivineStatus(@PathVariable("key") long key) {
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
                result.put("chartBase", vars.getOrDefault("chartBase", ""));
                result.put("fourPillars", vars.getOrDefault("fourPillars", ""));
                result.put("dayPillarTraits", vars.getOrDefault("dayPillarTraits", ""));
                result.put("ganzhiRelations", vars.getOrDefault("ganzhiRelations", ""));
                result.put("tenGods", vars.getOrDefault("tenGods", ""));
                result.put("deities", vars.getOrDefault("deities", ""));
                result.put("voidness", vars.getOrDefault("voidness", ""));
                result.put("patternResult", vars.getOrDefault("patternResult", ""));
                result.put("yongShen", vars.getOrDefault("yongShen", ""));
                result.put("tiaohou", vars.getOrDefault("tiaohou", ""));
                result.put("chartLevel", vars.getOrDefault("chartLevel", ""));
                result.put("purityTruth", vars.getOrDefault("purityTruth", ""));
                result.put("fortuneCycles", vars.getOrDefault("fortuneCycles", ""));
                result.put("fortuneAnalysis", vars.getOrDefault("fortuneAnalysis", ""));
                result.put("timing", vars.getOrDefault("timing", ""));
                result.put("categoryVerdicts", vars.getOrDefault("categoryVerdicts", ""));
                result.put("clauseCitations", vars.getOrDefault("clauseCitations", ""));
                result.put("reading", vars.getOrDefault("reading", ""));
                result.put("marriageConclusion", vars.getOrDefault("marriageConclusion", ""));
                result.put("femaleVerdict", vars.getOrDefault("femaleVerdict", ""));
                result.put("childVerdict", vars.getOrDefault("childVerdict", ""));
                result.put("moralAdvice", vars.getOrDefault("moralAdvice", ""));
                result.put("aiInterpretation", vars.getOrDefault("aiInterpretation", ""));
                result.put("aiInterpretationPlain", vars.getOrDefault("aiInterpretationPlain", ""));
                result.put("alertTriggered", vars.getOrDefault("alertTriggered", false));
                result.put("messageStatus", vars.getOrDefault("messageStatus", ""));
                result.put("caseRecord", vars.getOrDefault("caseRecord", ""));
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 查询推演状态失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engineAvailable", client != null);
        out.put("processId", PROCESS_ID);
        out.put("bpmnPath", bpmnPath);
        return out;
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg == null ? "未知错误" : msg);
        return m;
    }
}
