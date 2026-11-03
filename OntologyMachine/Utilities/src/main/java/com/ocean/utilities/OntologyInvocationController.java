package com.ocean.utilities;

import io.camunda.client.CamundaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用「本体应用调用」REST 端点（与业务无关），供外部程序（Python 等）集成本体应用。
 *
 * <p>端点（默认基路径 {@code /api/ontology}，可由 {@code ontology.invoke.base-path} 覆盖）：
 * <ul>
 *   <li>{@code POST /invoke}        同步调用：提交输入 → 等待流程终态 → 返回 {@link InvokeResult} JSON。</li>
 *   <li>{@code POST /invoke-async}  异步调用：仅启动流程，返回 {@code processInstanceKey}。</li>
 *   <li>{@code GET  /invoke/{key}}  查询快照：不等待，返回当前状态与已产生输出。</li>
 *   <li>{@code GET  /health}        健康探测：告知调用方引擎是否就绪。</li>
 * </ul>
 *
 * <p><b>开关</b>：本控制器默认启用；各应用可在 {@code application.yaml} 设
 * {@code ontology.invoke.enabled: false} 关闭，或用 {@code ontology.invoke.base-path} 改基路径。
 * 之所以用条件装配，是因为各应用均以 {@code @ComponentScan("com.ocean")} 扫描本模块，
 * 借此保证「默认可用、按需关闭」，且不与业务自身的 Controller 路径冲突。
 *
 * <p>未装配 {@link CamundaClient} 时端点仍可访问，只是调用会返回 {@code ERROR} 结果
 * （见 {@link OntologyInvocationSupport#invoke}），不影响应用启动。
 */
@RestController
@ConditionalOnProperty(name = "ontology.invoke.enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("${ontology.invoke.base-path:/api/ontology}")
public class OntologyInvocationController {

    private static final Logger log = LoggerFactory.getLogger(OntologyInvocationController.class);

    @Autowired(required = false)
    private CamundaClient client;

    /** 本应用声明的 BPMN 路径（可选）。请求未自带 {@code bpmnPaths} 时回退用它做幂等部署。 */
    @Value("${ontology.bpmn-path:}")
    private String bpmnPath;

    /** 附加 BPMN 路径（可选，逗号分隔，如 {@code ontology.design-bpmn-path} 汇总）。 */
    @Value("${ontology.bpmn-paths:}")
    private String bpmnPaths;

    /** 同步调用：一次请求内完成「提交输入 → 等流程结束 → 取回结果」。 */
    @PostMapping("/invoke")
    public ResponseEntity<InvokeResult> invoke(@RequestBody(required = false) InvokeRequest request) {
        InvokeResult result = OntologyInvocationSupport.invoke(client, withDefaultBpmnPaths(request));
        log.info("[本体调用/API] processId={} status={} success={} elapsedMs={}",
                result.processId(), result.status(), result.success(), result.elapsedMillis());
        return ResponseEntity.status(httpStatusOf(result)).body(result);
    }

    /** 异步调用：立即返回流程实例 key，调用方自行查询。 */
    @PostMapping("/invoke-async")
    public ResponseEntity<?> invokeAsync(@RequestBody InvokeRequest request) {
        InvokeRequest req = withDefaultBpmnPaths(request);
        try {
            long key = OntologyInvocationSupport.startAsync(client, req);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("processInstanceKey", key);
            body.put("processId", req == null ? null : req.processId());
            body.put("status", "STARTED");
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.warn("[本体调用/API] 异步启动失败: {}", e.toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(e.getMessage()));
        }
    }

    /** 查询快照：不等待，返回当前状态与已产生输出。 */
    @GetMapping("/invoke/{key}")
    public ResponseEntity<InvokeResult> query(
            @PathVariable("key") long key,
            @RequestParam(name = "resultKeys", required = false) List<String> resultKeys) {
        return ResponseEntity.ok(OntologyInvocationSupport.query(client, key, resultKeys));
    }

    /** 健康探测：供外部程序在集成前探测本体应用与引擎是否就绪。 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("component", "OntologyInvocationController");
        out.put("engineAvailable", client != null);
        return out;
    }

    /**
     * 请求未显式提供 {@code bpmnPaths} 时，回退为应用在 {@code application.yaml} 声明的
     * {@code ontology.bpmn-path} / {@code ontology.bpmn-paths}。
     *
     * <p>动机：通用调用端点应「装上即用」——客户全新解压的 Camunda 里没有任何流程定义，
     * 若依赖调用方自带 BPMN 路径、或依赖业务端点先跑过一次部署，则 {@code /invoke} 首次调用
     * 必报 404（{@code process ID ... none found}）。此处回退使任何客户端都能触发幂等部署
     * （{@link OntologyInvocationSupport} 内部经 {@code ProcessOrchestrator.deployIfAbsent}，
     * 已存在且内容一致则跳过，不产生多余版本）。
     */
    private InvokeRequest withDefaultBpmnPaths(InvokeRequest req) {
        if (req == null || (req.bpmnPaths() != null && !req.bpmnPaths().isEmpty())) {
            return req;
        }
        List<String> defaults = configuredBpmnPaths();
        if (defaults.isEmpty()) {
            return req;
        }
        return new InvokeRequest(req.processId(), req.inputs(), req.timeoutMillis(),
                req.pollIntervalMillis(), defaults, req.resultKeys());
    }

    /** 汇总应用声明的 BPMN 路径（{@code ontology.bpmn-path} + 逗号分隔的 {@code ontology.bpmn-paths}）。 */
    private List<String> configuredBpmnPaths() {
        List<String> out = new ArrayList<>();
        if (bpmnPath != null && !bpmnPath.isBlank()) {
            out.add(bpmnPath.trim());
        }
        if (bpmnPaths != null && !bpmnPaths.isBlank()) {
            for (String p : bpmnPaths.split(",")) {
                if (!p.isBlank()) {
                    out.add(p.trim());
                }
            }
        }
        return out;
    }

    private static HttpStatus httpStatusOf(InvokeResult r) {
        return switch (r.status()) {
            case OntologyInvocationSupport.STATUS_COMPLETED -> HttpStatus.OK;
            case OntologyInvocationSupport.STATUS_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case OntologyInvocationSupport.STATUS_INCIDENT -> HttpStatus.CONFLICT;
            case OntologyInvocationSupport.STATUS_ERROR -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.ACCEPTED; // RUNNING / TERMINATED
        };
    }

    private static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", msg == null ? "未知错误" : msg);
        return m;
    }
}
