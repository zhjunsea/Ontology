package com.ocean.ontologyframework;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * 任意测试超时即中止整个测试套件的 JUnit 5 扩展。
 *
 * 原理：
 *  - 捕获测试抛出的异常；若异常链中包含 Timeout 类型（含 JUnit 5 的 TimeoutException），
 *    设置全局 STOP_REQUESTED = true。
 *  - 在 beforeEach 阶段检查该标志；若已置位，用 Assumptions.assumeFalse 跳过后续所有测试。
 *
 * 使用方式：
 *   在测试类上加 @ExtendWith(StopOnTimeoutExtension.class)
 *   并在测试类或方法上加 @Timeout(value = N, unit = TimeUnit.SECONDS)
 */
public class StopOnTimeoutExtension
        implements TestExecutionExceptionHandler, BeforeEachCallback {

    static final AtomicBoolean STOP_REQUESTED = new AtomicBoolean(false);

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable throwable)
            throws Throwable {
        if (isTimeout(throwable)) {
            STOP_REQUESTED.set(true);
            System.err.println("⚠️ [超时中止] 测试 \"" + context.getDisplayName()
                    + "\" 超时，后续所有测试将被跳过。");
        }
        throw throwable;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        assumeFalse(STOP_REQUESTED.get(),
                "前一个测试超时，测试套件已中止，跳过此测试：" + context.getDisplayName());
    }

    /**
     * 检查异常链中是否含 Timeout 类型。
     * 兼容 java.util.concurrent.TimeoutException、JUnit 5 的超时异常以及各种包装。
     */
    private boolean isTimeout(Throwable t) {
        while (t != null) {
            String name = t.getClass().getName();
            if (name.contains("Timeout")) return true;
            t = t.getCause();
        }
        return false;
    }
}