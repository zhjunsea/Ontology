package com.ocean.utilities;

import java.util.Map;

/**
 * 通用「本体流程调用」结果（与业务无关，不可变）。
 *
 * <p>{@code status} 取值见 {@link OntologyInvocationSupport} 的 {@code STATUS_*} 常量：
 * {@code COMPLETED} / {@code INCIDENT} / {@code TERMINATED} / {@code RUNNING} / {@code TIMEOUT} / {@code ERROR}。
 * {@code success} 仅当流程正常完成（{@code COMPLETED}）时为 {@code true}。
 *
 * <p>{@code outputs} 为流程结束时回收的变量（可由请求的 {@code resultKeys} 白名单过滤），
 * 即「本体程序给出的结果」，供 Python 端继续处理。
 *
 * @param success            是否成功完成（{@code status == COMPLETED}）
 * @param status             统一状态字符串
 * @param processId          流程定义 ID
 * @param processInstanceKey Camunda 流程实例 key（启动失败时可能为 {@code null}）
 * @param outputs            回收的流程输出变量
 * @param elapsedMillis      从提交到返回的耗时（毫秒）
 * @param message            人类可读的说明（成功/失败/超时原因）
 */
public record InvokeResult(
        boolean success,
        String status,
        String processId,
        Long processInstanceKey,
        Map<String, Object> outputs,
        long elapsedMillis,
        String message
) {
}
