package com.ocean.utilities;

import java.net.InetSocketAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 集成测试前置探测工具。
 *
 * <p>根工程一键 {@code install} 会执行各模块的集成测试；当本机未启动 Camunda / Ontop /
 * MySQL / RabbitMQ 等外部服务时，这些测试会长时间超时并以「连接被拒」的堆栈刷屏。
 * 通过 {@link #assumeReachable} 在测试前置阶段探测端口可达性：服务在则照常执行，
 * 不在则以 JUnit {@link org.junit.jupiter.api.Assumptions} 中止（自动跳过），
 * 既不中断构建也不产生噪声。
 *
 * <p>与 {@link StopOnTimeoutExtension} 同属「位于 main 源码、供各模块测试复用的测试基础设施」。
 */
public final class ServiceProbe {

    /** 本机回环地址（各集成测试默认连接 localhost）。 */
    public static final String LOCALHOST = "127.0.0.1";

    private static final int DEFAULT_TIMEOUT_MILLIS = 800;

    private ServiceProbe() {
    }

    /** 端口是否可达（默认 800ms 超时）。 */
    public static boolean isReachable(String host, int port) {
        return isReachable(host, port, DEFAULT_TIMEOUT_MILLIS);
    }

    /** 端口是否可达。 */
    public static boolean isReachable(String host, int port, int timeoutMillis) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 单个服务可达才继续，否则跳过当前测试（或其所属测试类）。 */
    public static void assumeReachable(String serviceLabel, String host, int port) {
        assumeTrue(isReachable(host, port),
                () -> "外部服务不可达，跳过集成测试：" + serviceLabel + " (" + host + ":" + port + ")");
    }

    /** 同一主机上一组端口全部可达才继续，否则跳过当前测试（或其所属测试类）。 */
    public static void assumeAllReachable(String serviceLabel, String host, int... ports) {
        for (int port : ports) {
            assumeReachable(serviceLabel + " @" + port, host, port);
        }
    }
}
