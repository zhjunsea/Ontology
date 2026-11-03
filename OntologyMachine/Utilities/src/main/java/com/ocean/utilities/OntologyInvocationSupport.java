package com.ocean.utilities;

import io.camunda.client.CamundaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与业务无关的「本体流程调用」门面：把「提交输入 → 启动流程 → 等待终态 → 回收结果」
 * 沉淀为一处，使本体应用能被外部程序以「一次调用拿到结果」的同步语义集成。
 *
 * <p>典型用途：Python 程序把输入交给本体应用（经 {@link OntologyInvocationController} 的
 * REST 端点或本类直接调用），流程跑完后取回结构化结果，Python 继续执行后续逻辑。
 *
 * <p><b>设计约束</b>：本类不感知任何业务语义——流程 ID、输入变量、输出变量全部由调用方
 * 透传；底层复用 {@link ProcessOrchestrator} 的既有 API，不重复实现流程编排（工程铁律 21）。
 * 因而与具体领域应用解耦，任何本体应用接入即可用。
 */
public final class OntologyInvocationSupport {

    private static final Logger log = LoggerFactory.getLogger(OntologyInvocationSupport.class);

    /** 流程正常完成。 */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** 流程因 incident（作业失败等）中断。 */
    public static final String STATUS_INCIDENT = "INCIDENT";
    /** 流程被终止。 */
    public static final String STATUS_TERMINATED = "TERMINATED";
    /** 流程仍在运行（查询快照时可能出现）。 */
    public static final String STATUS_RUNNING = "RUNNING";
    /** 同步等待超时（流程仍在运行，已返回当时的输出快照）。 */
    public static final String STATUS_TIMEOUT = "TIMEOUT";
    /** 调用侧错误（未就绪 / 参数非法 / 启动失败）。 */
    public static final String STATUS_ERROR = "ERROR";

    /** 同步等待默认超时：5 分钟。 */
    public static final long DEFAULT_TIMEOUT_MILLIS = 300_000L;
    /** 轮询默认间隔：500 毫秒。 */
    public static final long DEFAULT_POLL_INTERVAL_MILLIS = 500L;

    private OntologyInvocationSupport() {
    }

    /**
     * 同步调用：{@code [可选]幂等部署 → 启动流程 → 阻塞等待终态 → 回收结果}。
     *
     * @param client  Camunda 客户端（由宿主应用注入；为 {@code null} 时返回 {@code ERROR} 结果，不抛异常）
     * @param request 调用请求（{@code processId} 必填）
     * @return 结构化结果；内部异常一律转为 {@code ERROR} 结果，便于外部程序稳定拿到响应
     */
    public static InvokeResult invoke(CamundaClient client, InvokeRequest request) {
        long startedAt = System.currentTimeMillis();
        String processId = request == null ? null : request.processId();

        if (client == null) {
            return new InvokeResult(false, STATUS_ERROR, processId, null, Map.of(),
                    elapsed(startedAt), "CamundaClient 未就绪：请确认已启用 camunda 客户端配置");
        }
        if (isBlank(processId)) {
            return new InvokeResult(false, STATUS_ERROR, processId, null, Map.of(),
                    elapsed(startedAt), "processId 不能为空");
        }

        deployIfNeeded(client, request);

        long processInstanceKey;
        try {
            processInstanceKey = ProcessOrchestrator.start(client, processId, request.inputs());
        } catch (Exception e) {
            log.warn("[本体调用] 启动流程失败 processId={}: {}", processId, e.toString());
            return new InvokeResult(false, STATUS_ERROR, processId, null, Map.of(),
                    elapsed(startedAt), "启动流程失败：" + e.getMessage());
        }
        log.info("[本体调用] 已启动 processId={} instanceKey={}", processId, processInstanceKey);
        return await(client, request, processInstanceKey, startedAt);
    }

    /**
     * 异步调用：{@code [可选]幂等部署 → 启动流程}，立即返回 {@code processInstanceKey}，不等待。
     * 调用方后续可用 {@link #query(CamundaClient, long, List)} 轮询结果。
     *
     * @throws IllegalArgumentException {@code client}/{@code processId} 为空
     * @throws RuntimeException         启动失败（含引擎不可用）
     */
    public static long startAsync(CamundaClient client, InvokeRequest request) {
        if (client == null) {
            throw new IllegalArgumentException("CamundaClient 未就绪");
        }
        if (request == null || isBlank(request.processId())) {
            throw new IllegalArgumentException("processId 不能为空");
        }
        deployIfNeeded(client, request);
        return ProcessOrchestrator.start(client, request.processId(), request.inputs());
    }

    /**
     * 等待指定实例到达终态并回收结果（供同步调用与外部轮询复用）。
     *
     * @param startedAt 计时起点（毫秒时间戳），用于统一计算超时与耗时
     */
    public static InvokeResult await(CamundaClient client, InvokeRequest request,
                                     long processInstanceKey, long startedAt) {
        long timeout = resolveTimeout(request);
        long interval = resolveInterval(request);
        long deadline = startedAt + timeout;

        while (true) {
            String status = currentStatus(client, processInstanceKey);
            if (!STATUS_RUNNING.equals(status)) {
                return buildResult(client, request, processInstanceKey, status, startedAt, null);
            }
            if (System.currentTimeMillis() >= deadline) {
                return buildResult(client, request, processInstanceKey, STATUS_TIMEOUT, startedAt,
                        "等待超时（" + timeout + " ms），流程仍在运行，返回当前输出快照");
            }
            sleep(interval);
        }
    }

    /**
     * 查询快照（不等待）：返回实例当前状态与已产生的输出变量。
     *
     * @param resultKeys 输出变量白名单；为空则回传全部
     */
    public static InvokeResult query(CamundaClient client, long processInstanceKey, List<String> resultKeys) {
        long startedAt = System.currentTimeMillis();
        if (client == null) {
            return new InvokeResult(false, STATUS_ERROR, null, processInstanceKey, Map.of(),
                    0L, "CamundaClient 未就绪");
        }
        String status = currentStatus(client, processInstanceKey);
        Map<String, Object> vars = ProcessOrchestrator.readVariables(client, processInstanceKey);
        return new InvokeResult(
                STATUS_COMPLETED.equals(status),
                status, null, processInstanceKey, filter(vars, resultKeys), 0L,
                "查询快照：" + status);
    }

    // ------------------------------------------------------------------ 内部实现

    private static void deployIfNeeded(CamundaClient client, InvokeRequest request) {
        if (request.bpmnPaths() != null && !request.bpmnPaths().isEmpty()) {
            ProcessOrchestrator.deployIfAbsent(client, request.processId(), request.bpmnPaths());
        }
    }

    private static String currentStatus(CamundaClient client, long processInstanceKey) {
        ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, processInstanceKey);
        return info == null ? STATUS_RUNNING
                : ProcessOrchestrator.statusOf(info.state(), info.hasIncident());
    }

    private static InvokeResult buildResult(CamundaClient client, InvokeRequest request,
                                            long processInstanceKey, String status,
                                            long startedAt, String message) {
        Map<String, Object> vars = ProcessOrchestrator.readVariables(client, processInstanceKey);
        Map<String, Object> outputs = filter(vars, request == null ? null : request.resultKeys());
        boolean success = STATUS_COMPLETED.equals(status);
        String msg = message != null ? message
                : (success ? "流程已完成" : "流程结束，状态=" + status);
        return new InvokeResult(success, status,
                request == null ? null : request.processId(),
                processInstanceKey, outputs, elapsed(startedAt), msg);
    }

    /** 按白名单过滤输出变量；白名单为空时原样返回。 */
    private static Map<String, Object> filter(Map<String, Object> vars, List<String> resultKeys) {
        if (vars == null || vars.isEmpty()) {
            return Map.of();
        }
        if (resultKeys == null || resultKeys.isEmpty()) {
            return vars;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : resultKeys) {
            if (vars.containsKey(key)) {
                out.put(key, vars.get(key));
            }
        }
        return out;
    }

    private static long resolveTimeout(InvokeRequest request) {
        Long t = request == null ? null : request.timeoutMillis();
        return t == null ? DEFAULT_TIMEOUT_MILLIS : Math.max(0L, t);
    }

    private static long resolveInterval(InvokeRequest request) {
        Long i = request == null ? null : request.pollIntervalMillis();
        return i == null ? DEFAULT_POLL_INTERVAL_MILLIS : Math.max(50L, i);
    }

    private static long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待流程结束时被中断", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
