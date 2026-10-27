package com.ocean.envprepare;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 端口冲突（FOREIGN：端口被非本环境进程占用）的交互处理：
 * 打印冲突信息并让用户「复用原有环境 / 杀掉原进程启动新进程 / 改用其它端口」三选一。
 * <p>非交互终端（右键双击启动器、stdin 被重定向、管道）由调用方直接失败退出，本类只负责交互询问与空闲端口建议。
 * 多个服务逐个询问时共用同一个输入缓冲，避免多次包装 {@code System.in} 丢失输入。
 */
final class ConflictResolver {

    /** 用户在冲突时的选择。 */
    enum Choice {
        /** 复用原有环境：沿用占用端口的进程，不启动本环境新进程。 */
        REUSE,
        /** 杀掉原进程并启动本环境新进程。 */
        KILL_RESTART,
        /** 改用其它端口：仅提示一个空闲端口，由用户自行修改 application.yaml 后重新启动。 */
        REASSIGN_PORT
    }

    private static BufferedReader reader;

    private ConflictResolver() {
    }

    /** 是否为可交互终端；false 时调用方应直接失败退出（安全优先）。 */
    static boolean interactive() {
        return System.console() != null;
    }

    /**
     * 三选一询问。输入无法解析（含 EOF）时默认返回 {@link Choice#REASSIGN_PORT}——
     * 该选项不改动任何进程、仅给出改端口提示，是最安全的兜底。
     */
    static Choice ask(String service, String portDesc, String holderInfo) {
        System.out.println();
        System.out.println("!".repeat(60));
        System.out.println(" [端口冲突] " + service + " 的 " + portDesc + " 已被非本环境进程占用。");
        if (holderInfo != null && !holderInfo.isBlank()) {
            System.out.println("   占用者: " + holderInfo);
        }
        System.out.println("!".repeat(60));
        System.out.println(" 请选择处理方式：");
        System.out.println("   1) 复用原有环境（沿用现有进程，不启动本环境新进程）");
        System.out.println("   2) 杀掉原进程并启动本环境新进程");
        System.out.println("   3) 改用其它端口（提示空闲端口，需自行修改 application.yaml 后重新启动）");
        System.out.print(" 请输入 1 / 2 / 3: ");
        System.out.flush();
        try {
            String line = reader().readLine();
            if (line == null) {
                return Choice.REASSIGN_PORT;
            }
            String t = line.strip();
            if ("1".equals(t)) {
                return Choice.REUSE;
            }
            if ("2".equals(t)) {
                return Choice.KILL_RESTART;
            }
            return Choice.REASSIGN_PORT;
        } catch (Exception e) {
            return Choice.REASSIGN_PORT;
        }
    }

    /** 从占用端口起向上（必要时向下）寻找一个未被占用的端口；找不到返回 -1。 */
    static int suggestFreePort(int occupied) {
        for (int p = occupied + 1; p <= occupied + 100 && p <= 65535; p++) {
            if (ProcessUtil.portFree(p)) {
                return p;
            }
        }
        for (int p = occupied - 1; p > 1024 && p >= occupied - 100; p--) {
            if (ProcessUtil.portFree(p)) {
                return p;
            }
        }
        return -1;
    }

    private static BufferedReader reader() {
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(System.in));
        }
        return reader;
    }
}
