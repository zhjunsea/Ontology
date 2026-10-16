package com.ocean.ontologyframework.neinsight.web;

import com.ocean.ontologyframework.neinsight.analyze.InputAnalysis;
import com.ocean.utilities.LlmClient;
import com.ocean.utilities.ProcessOrchestrator;
import io.camunda.client.CamundaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /**
     * 输入分析调用的大模型客户端（通用能力，见 {@link com.ocean.utilities.LlmClient}）。
     * 未配置 {@code llm.*} 时为不可用，输入分析自动降级为确定性策略（仅取场景原文、其余字段留空 → 触发追问）。
     */
    @Autowired(required = false)
    private LlmClient llm;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    /**
     * 洞察 SKILL 落盘目录（相对模块工作目录；与 {@code NEInsightOntologyJobWorker} 的落盘口径一致）。
     * 下载端点据此校验/回退定位 SKILL 文件，避免信任流程变量中的绝对路径（★铁律21：不硬编码绝对路径）。
     */
    @Value("${neinsight.skill.dir:skill}")
    private String skillDir;

    /**
     * 洞察报告落盘目录（相对模块工作目录；与 {@code NEInsightOntologyJobWorker} 的落盘口径一致）。
     * 下载端点据此校验/回退定位报告文件，避免信任流程变量中的绝对路径（★铁律21：不硬编码绝对路径）。
     */
    @Value("${neinsight.report.dir:report}")
    private String reportDir;

    /**
     * 输入分析 / 澄清（前置步骤）：解析客户输入，返回「已提取信息 + 缺失必要信息 + 追问问题」。
     *
     * <p>前端据此前置引导客户多轮补充，直至 {@code ready=true}（必要信息齐全）方可启动洞察流程。
     * 请求体可含自由文本 {@code insightScenario}，以及客户已确认的结构化字段
     * （{@code scenario}/{@code identity}/{@code industry}/{@code objective} 等，非空即覆盖抽取结果）。
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeInput(@RequestBody(required = false) Map<String, Object> body) {
        try {
            InputAnalysis.Result r = InputAnalysis.analyze(llm, body == null ? Map.of() : body);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("analysis", r.analysis());
            out.put("missingFields", r.missingFields());
            out.put("questions", r.questions());
            out.put("ready", r.ready());
            out.put("source", r.source());
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.error("[API] 输入分析失败", e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    @PostMapping("/insight")
    public ResponseEntity<?> startInsight(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> in = body == null ? Map.of() : body;
        String insightScenario = firstText(in, "insightScenario");
        if (insightScenario.isBlank()) {
            return ResponseEntity.badRequest().body(error("insightScenario 不能为空"));
        }

        InputAnalysis.Result r = InputAnalysis.analyze(llm, in);
        if (!r.ready()) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("error", "必要信息不完整，请先补充后再启动洞察流程");
            out.put("missingFields", r.missingFields());
            out.put("questions", r.questions());
            out.put("analysis", r.analysis());
            log.info("[API] 拒绝启动：必要信息不完整 | missing={}", r.missingFields());
            return ResponseEntity.badRequest().body(out);
        }

        try {
            boolean deployed = ProcessOrchestrator.deployIfAbsent(client, INSIGHT_PROCESS_ID, List.of(bpmnPath));
            if (!deployed) {
                log.warn("流程部署失败或已存在 | processId={}", INSIGHT_PROCESS_ID);
            }

            Map<String, Object> analysis = r.analysis();
            Map<String, Object> vars = new LinkedHashMap<>();
            vars.put("insightScenario", insightScenario);
            for (String f : InputAnalysis.REQUIRED_FIELDS) {
                vars.put(f, analysis.get(f));
            }
            vars.put("inputAnalysisResult", analysis);

            long instanceKey = ProcessOrchestrator.start(client, INSIGHT_PROCESS_ID, vars);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("instanceKey", instanceKey);
            result.put("processId", INSIGHT_PROCESS_ID);
            result.put("insightScenario", insightScenario);
            result.put("analysis", analysis);

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
                result.put("insightReportPath", vars.getOrDefault("insightReportPath", ""));
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[API] 查询洞察流程状态失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    /**
     * 下载本次流程自动生成的洞察 SKILL 文件（{@code skill/neinsight-<instanceKey>.md}）。
     *
     * <p>定位优先级：① 流程变量 {@code insightSkillPath}（流程完成时由 summarize-insight 回写）；
     * ② 按命名规则回退 {@code <neinsight.skill.dir>/neinsight-<key>.md}。两路径均须落在配置的
     * SKILL 目录之内，防止路径穿越。文件不存在时返回 404。
     */
    @GetMapping("/insight/{key}/skill")
    public ResponseEntity<?> downloadInsightSkill(@PathVariable("key") long key) {
        try {
            String pathVar = "";
            try {
                Map<String, Object> vars = ProcessOrchestrator.readVariables(client, key);
                pathVar = String.valueOf(vars.getOrDefault("insightSkillPath", ""));
            } catch (Exception ignore) {
                log.warn("[API] 读取 SKILL 路径失败，回退按命名规则定位 | key={}", key);
            }

            Path file = resolveSkillFile(key, pathVar);
            if (file == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(error("未找到该流程实例的洞察 SKILL 文件，请确认流程已完成"));
            }

            byte[] content = Files.readAllBytes(file);
            String filename = "neinsight-" + key + ".md";
            log.info("📥 下载洞察 SKILL | key={} | path={}", key, file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                    .body(content);

        } catch (Exception e) {
            log.error("[API] 下载洞察 SKILL 失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    /** 定位 SKILL 文件：优先流程变量路径，其次命名规则回退；两者都须位于配置的 SKILL 目录之内。 */
    private Path resolveSkillFile(long key, String pathVar) {
        Path base = Path.of(skillDir).toAbsolutePath().normalize();
        if (pathVar != null && !pathVar.isBlank() && !"null".equals(pathVar)) {
            try {
                Path p = Path.of(pathVar).toAbsolutePath().normalize();
                if (p.startsWith(base) && Files.isRegularFile(p)) {
                    return p;
                }
            } catch (Exception ignore) {
                // 非法路径，回退命名规则
            }
        }
        Path fallback = base.resolve("neinsight-" + key + ".md").normalize();
        return Files.isRegularFile(fallback) ? fallback : null;
    }

    /**
     * 下载本次流程自动生成的洞察报告（{@code report/neinsight-<instanceKey>-洞察报告.md}）。
     *
     * <p>定位优先级：① 流程变量 {@code insightReportPath}（流程完成时由 generate-report 回写）；
     * ② 按命名规则回退 {@code <neinsight.report.dir>/neinsight-<key>-洞察报告.md}。两路径均须落在配置的
     * 报告目录之内，防止路径穿越。文件不存在时返回 404。
     */
    @GetMapping("/insight/{key}/report")
    public ResponseEntity<?> downloadInsightReport(@PathVariable("key") long key) {
        try {
            String pathVar = "";
            try {
                Map<String, Object> vars = ProcessOrchestrator.readVariables(client, key);
                pathVar = String.valueOf(vars.getOrDefault("insightReportPath", ""));
            } catch (Exception ignore) {
                log.warn("[API] 读取洞察报告路径失败，回退按命名规则定位 | key={}", key);
            }

            Path file = resolveReportFile(key, pathVar);
            if (file == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(error("未找到该流程实例的洞察报告文件，请确认流程已完成"));
            }

            byte[] content = Files.readAllBytes(file);
            String asciiName = "neinsight-" + key + ".md";
            String utf8Name = URLEncoder.encode("neinsight-" + key + "-洞察报告.md", StandardCharsets.UTF_8)
                    .replace("+", "%20");
            log.info("📥 下载洞察报告 | key={} | path={}", key, file);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + asciiName + "\"; filename*=UTF-8''" + utf8Name)
                    .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                    .body(content);

        } catch (Exception e) {
            log.error("[API] 下载洞察报告失败 key={}", key, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(e.getMessage()));
        }
    }

    /** 定位洞察报告文件：优先流程变量路径，其次命名规则回退；两者都须位于配置的报告目录之内。 */
    private Path resolveReportFile(long key, String pathVar) {
        Path base = Path.of(reportDir).toAbsolutePath().normalize();
        if (pathVar != null && !pathVar.isBlank() && !"null".equals(pathVar)) {
            try {
                Path p = Path.of(pathVar).toAbsolutePath().normalize();
                if (p.startsWith(base) && Files.isRegularFile(p)) {
                    return p;
                }
            } catch (Exception ignore) {
                // 非法路径，回退命名规则
            }
        }
        Path fallback = base.resolve("neinsight-" + key + "-洞察报告.md").normalize();
        return Files.isRegularFile(fallback) ? fallback : null;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("engineAvailable", client != null);
        out.put("processId", INSIGHT_PROCESS_ID);
        out.put("bpmnPath", bpmnPath);
        out.put("llmAvailable", llm != null && llm.isAvailable());
        return out;
    }

    private static String firstText(Map<String, Object> in, String key) {
        Object v = in == null ? null : in.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg == null ? "未知错误" : msg);
        return m;
    }
}
