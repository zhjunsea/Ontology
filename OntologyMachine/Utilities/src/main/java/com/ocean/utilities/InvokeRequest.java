package com.ocean.utilities;

import java.util.List;
import java.util.Map;

/**
 * 通用「本体流程调用」请求（与业务无关）。
 *
 * <p>由外部程序（如 Python 客户端，经 {@link OntologyInvocationController} 的 REST 端点）
 * 提交：指定要触发的 Camunda 流程 ID 与输入变量；其余字段均为可选调优项。
 * <ul>
 *   <li>{@code processId}：Camunda BPMN 流程定义 ID（必填）。</li>
 *   <li>{@code inputs}：透传给流程实例的输入变量（即 Python 给出的输入，原样交给本体流程）。</li>
 *   <li>{@code timeoutMillis}：同步等待终态的最长时长；{@code null} 用默认值
 *       {@link OntologyInvocationSupport#DEFAULT_TIMEOUT_MILLIS}。</li>
 *   <li>{@code pollIntervalMillis}：轮询终态的间隔；{@code null} 用默认值
 *       {@link OntologyInvocationSupport#DEFAULT_POLL_INTERVAL_MILLIS}。</li>
 *   <li>{@code bpmnPaths}：可选，调用前先对这组 BPMN 文件做幂等部署（本地文件路径）。</li>
 *   <li>{@code resultKeys}：可选，输出变量白名单；为空则回传全部流程变量。</li>
 * </ul>
 *
 * <p>Jackson 可直接把外部 JSON（如 {@code {"processId":"...","inputs":{...}}}）反序列化为本记录。
 */
public record InvokeRequest(
        String processId,
        Map<String, Object> inputs,
        Long timeoutMillis,
        Long pollIntervalMillis,
        List<String> bpmnPaths,
        List<String> resultKeys
) {

    /** 便捷构造：仅指定流程 ID 与输入，其余走默认。 */
    public static InvokeRequest of(String processId, Map<String, Object> inputs) {
        return new InvokeRequest(processId, inputs, null, null, null, null);
    }
}
