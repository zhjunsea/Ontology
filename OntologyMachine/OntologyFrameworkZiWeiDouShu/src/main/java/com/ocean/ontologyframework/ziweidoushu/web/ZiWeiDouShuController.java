package com.ocean.ontologyframework.ziweidoushu.web;

import com.ocean.utilities.ProcessOrchestrator;
import com.ocean.ontologyframework.ziweidoushu.ZiWeiDouShuOntologyJobWorker;
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
@RequestMapping("/api/ziweidoushu")
public class ZiWeiDouShuController {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuController.class);

    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";

    @Autowired
    private CamundaClient client;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @PostMapping("/chart")
    public ResponseEntity<?> startChart(@RequestBody Map<String, Object> body) {
        Object birth = body == null ? null : body.get("birth");
        if (birth == null) {
            return ResponseEntity.badRequest().body(error("birth（生辰）不能为空"));
        }

        String inputMode = body.get("mode") == null ? "直接排盘" : String.valueOf(body.get("mode"));
        if (!"直接排盘".equals(inputMode) && !"验盘".equals(inputMode)) {
            return ResponseEntity.badRequest().body(error("mode 只能为「直接排盘」或「验盘」"));
        }
        if ("验盘".equals(inputMode) && body.get("subjectTraits") == null) {
            return ResponseEntity.badRequest().body(error("验盘模式须提供 subjectTraits（命主自述的相貌/性情/已知事件特征）"));
        }

        String depth = body.get("depth") == null ? "本命" : String.valueOf(body.get("depth"));
        if (!"本命".equals(depth) && !"本命+大限".equals(depth) && !"本命+大限+流年".equals(depth)) {
            return ResponseEntity.badRequest().body(error("depth 只能为「本命」「本命+大限」「本命+大限+流年」"));
        }

        try {
            boolean deployed = ProcessOrchestrator.deployIfAbsent(client, PROCESS_ID, List.of(bpmnPath));
            if (!deployed) {
                log.warn("流程部署失败或已存在 | processId={}", PROCESS_ID);
            }

            Map<String, Object> variables = new LinkedHashMap<>();
            body.forEach((k, v) -> {
                if (v != null) {
                    variables.put(k, v);
                }
            });
            variables.put("inputMode", inputMode);
            variables.put("depth", depth);

            long instanceKey = ProcessOrchestrator.start(client, PROCESS_ID, variables);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceKey", instanceKey);
            result.put("processId", PROCESS_ID);
            result.put("inputMode", inputMode);
            result.put("depth", depth);

            log.info("✅ 紫微斗数推命流程已启动 | inputMode={} | depth={} | instanceKey={}", inputMode, depth, instanceKey);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 启动紫微斗数推命流程失败", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @GetMapping("/chart/{key}")
    public ResponseEntity<?> getChartStatus(@PathVariable("key") long key) {
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
                result.put("dayNight", vars.getOrDefault("dayNight", ""));
                result.put("chartSkeleton", vars.getOrDefault("chartSkeleton", ""));
                result.put("lifeLord", vars.getOrDefault("lifeLord", ""));
                result.put("bodyLord", vars.getOrDefault("bodyLord", ""));
                result.put("mainStars", vars.getOrDefault("mainStars", ""));
                result.put("auxiliaryStars", vars.getOrDefault("auxiliaryStars", ""));
                result.put("miscStars", vars.getOrDefault("miscStars", ""));
                result.put("taiSuiBase", vars.getOrDefault("taiSuiBase", ""));
                result.put("fourTransform", vars.getOrDefault("fourTransform", ""));
                result.put("palaceStems", vars.getOrDefault("palaceStems", ""));
                result.put("twelveStages", vars.getOrDefault("twelveStages", ""));
                result.put("chartVerdict", vars.getOrDefault("chartVerdict", ""));
                result.put("verifyVerdict", vars.getOrDefault("verifyVerdict", ""));
                result.put("verifyNote", vars.getOrDefault("verifyNote", ""));
                result.put("brightnessTable", vars.getOrDefault("brightnessTable", ""));
                result.put("starRelations", vars.getOrDefault("starRelations", ""));
                result.put("palaceStrength", vars.getOrDefault("palaceStrength", ""));
                result.put("starNature", vars.getOrDefault("starNature", ""));
                result.put("starXingXing", vars.getOrDefault("starXingXing", ""));
                result.put("transformationQi", vars.getOrDefault("transformationQi", ""));
                result.put("emptyPalaceResult", vars.getOrDefault("emptyPalaceResult", ""));
                result.put("surroundPalaces", vars.getOrDefault("surroundPalaces", ""));
                result.put("clampRelations", vars.getOrDefault("clampRelations", ""));
                result.put("combinationMeaning", vars.getOrDefault("combinationMeaning", ""));
                result.put("patternResult", vars.getOrDefault("patternResult", ""));
                result.put("patternGrade", vars.getOrDefault("patternGrade", ""));
                result.put("flyingStars", vars.getOrDefault("flyingStars", ""));
                result.put("schoolNote", vars.getOrDefault("schoolNote", ""));
                result.put("natalReading", vars.getOrDefault("natalReading", ""));
                result.put("femaleReading", vars.getOrDefault("femaleReading", ""));
                result.put("childReading", vars.getOrDefault("childReading", ""));
                result.put("majorLimit", vars.getOrDefault("majorLimit", ""));
                result.put("majorLimitReading", vars.getOrDefault("majorLimitReading", ""));
                result.put("xianPinHouFu", vars.getOrDefault("xianPinHouFu", ""));
                result.put("twoLimitsTaiSui", vars.getOrDefault("twoLimitsTaiSui", ""));
                result.put("annualChart", vars.getOrDefault("annualChart", ""));
                result.put("annualReading", vars.getOrDefault("annualReading", ""));
                result.put("annualRecomputedStars", vars.getOrDefault("annualRecomputedStars", ""));
                result.put("overlayReading", vars.getOrDefault("overlayReading", ""));
                result.put("evilPatternTrigger", vars.getOrDefault("evilPatternTrigger", ""));
                result.put("limitPalaceSong", vars.getOrDefault("limitPalaceSong", ""));
                result.put("reading", vars.getOrDefault("reading", ""));
                result.put("structuredOutput", vars.getOrDefault("structuredOutput", ""));
                result.put("aiInterpretation", vars.getOrDefault("aiInterpretation", ""));
                result.put("aiInterpretationPlain", vars.getOrDefault("aiInterpretationPlain", ""));
                result.put("alertTriggered", vars.getOrDefault("alertTriggered", false));
                // D-3：暴露真实告警类别（BPMN 未映射 alertType 变量，故由 reading 第九条解析，与 sendAlert 同源）
                Object readingObj = vars.get("reading");
                result.put("alertType", ZiWeiDouShuOntologyJobWorker.extractAlertTypeFromReading(
                        readingObj == null ? "" : String.valueOf(readingObj)));
                result.put("messageStatus", vars.getOrDefault("messageStatus", ""));
                result.put("caseRecord", vars.getOrDefault("caseRecord", ""));
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 查询推命状态失败 key={}", key, e);
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
