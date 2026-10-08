package com.ocean.envprepare;

import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;

/**
 * MySQL Windows 服务生命周期管理：查询服务状态、按需启动（必要时 UAC 提权并等待就绪）、停止服务。
 * 由 start 命令（启动环境前确保 MySQL 就绪、退出时停止）与 createdb 命令（建库建表前确保 MySQL 就绪）共用。
 * 是否纳入管理由应用 application.yaml 是否声明 env-prepare.mysql 节点决定。
 */
public final class MysqlService {

    /** 启动结果：已在运行（跳过）/ 本次启动成功 / 启动失败。 */
    public enum StartOutcome {
        ALREADY_RUNNING, STARTED, FAILED
    }

    private final EnvConfig cfg;

    public MysqlService(EnvConfig cfg) {
        this.cfg = cfg;
    }

    /** 查询 Windows 服务状态（RUNNING/STOPPED/...）；未找到服务返回 null。 */
    public String status() {
        ProcessUtil.ExecResult r = ProcessUtil.run(
                List.of("sc", "query", cfg.mysqlServiceName()), null, null, 15);
        for (String line : r.stdout().split("\\R")) {
            String s = line.strip();
            String upper = s.toUpperCase(Locale.ROOT);
            if (upper.startsWith("STATE")) {
                int colon = s.indexOf(':');
                if (colon >= 0) {
                    String[] tokens = s.substring(colon + 1).strip().split("\\s+");
                    if (tokens.length >= 2) {
                        return tokens[tokens.length - 1].toUpperCase(Locale.ROOT);
                    }
                    if (tokens.length == 1 && !tokens[0].isEmpty()) {
                        return tokens[0].toUpperCase(Locale.ROOT);
                    }
                }
            }
        }
        String upperAll = r.stdout().toUpperCase(Locale.ROOT);
        for (String kw : new String[]{"RUNNING", "START_PENDING", "CONTINUE_PENDING",
                "STOP_PENDING", "PAUSE_PENDING", "PAUSED", "STOPPED"}) {
            if (upperAll.contains(kw)) {
                return kw;
            }
        }
        return null;
    }

    public boolean isRunning() {
        return "RUNNING".equals(status());
    }

    /** 确保 MySQL 服务已启动：已在运行则跳过；否则按需提权启动并等待就绪。 */
    public StartOutcome ensureStarted(PrintWriter log) {
        String service = cfg.mysqlServiceName();
        System.out.println("[CHECK] MySQL: 检查 Windows 服务 '" + service + "' 状态 ...");

        String status = status();
        if (status == null) {
            System.out.println("       [FAIL] 未找到 Windows 服务 '" + service + "'，请确认服务已注册");
            System.out.println("          提示: sc query type= service state= all | findstr /i mysql");
            return StartOutcome.FAILED;
        }
        if ("RUNNING".equals(status)) {
            System.out.println("       [INFO] " + service + " 已在运行中，跳过启动");
            return StartOutcome.ALREADY_RUNNING;
        }

        if ("START_PENDING".equals(status) || "CONTINUE_PENDING".equals(status)) {
            System.out.println("       [INFO] " + service + " 正在启动中 (state=" + status + ")，等待就绪 ...");
        } else {
            System.out.println("       [INFO] " + service + " 当前状态: " + status + "，正在启动 ...");
            boolean started;
            if (ProcessUtil.isAdmin()) {
                started = netExec("start", service, log);
            } else {
                System.out.println("       [INFO] 需要管理员权限，正在请求提权 ...");
                started = elevatedNetExec("start", service, log);
            }
            if (!started) {
                System.out.println("       [FAIL] net start 未能启动服务（权限被拒 / 凭据无效 / 服务被禁用）");
                System.out.println("          建议: 以管理员身份手动执行 net start " + service + " 查看详细错误");
                return StartOutcome.FAILED;
            }
        }

        int maxWait = 30;
        for (int i = 0; i < maxWait; i++) {
            ProcessUtil.sleep(1000);
            String current = status();
            if ("RUNNING".equals(current)) {
                System.out.println("       [OK] " + service + " 已成功启动 (耗时 " + (i + 1) + "s)");
                return StartOutcome.STARTED;
            }
            if (current == null) {
                System.out.println("       [FAIL] 等待过程中服务消失");
                return StartOutcome.FAILED;
            }
        }
        System.out.println("       [FAIL] " + service + " 启动超时 (" + maxWait + "s)，最终状态: " + status());
        System.out.println("          建议: 在管理员 CMD 中手动执行 net start " + service + " 查看详细错误");
        return StartOutcome.FAILED;
    }

    /** 停止 MySQL 服务（已在运行时），按需提权并等待完全停止。 */
    public boolean stop(PrintWriter log) {
        String service = cfg.mysqlServiceName();
        System.out.println("[STOP] MySQL: 检查 Windows 服务 '" + service + "' 状态 ...");

        String status = status();
        if (status == null) {
            System.out.println("       [FAIL] 未找到 Windows 服务 '" + service + "'，跳过停止");
            return false;
        }
        if ("STOPPED".equals(status)) {
            System.out.println("       [INFO] " + service + " 已处于停止状态，跳过");
            return true;
        }

        boolean stopped;
        if (ProcessUtil.isAdmin()) {
            stopped = netExec("stop", service, log);
        } else {
            System.out.println("       [INFO] 需要管理员权限，正在请求提权 ...");
            stopped = elevatedNetExec("stop", service, log);
        }
        if (!stopped) {
            System.out.println("       [FAIL] net stop 未能停止服务");
            System.out.println("          建议: 以管理员身份手动执行 net stop " + service + " 查看详细错误");
            return false;
        }

        int maxWait = 30;
        for (int i = 0; i < maxWait; i++) {
            ProcessUtil.sleep(1000);
            String current = status();
            if ("STOPPED".equals(current)) {
                System.out.println("       [OK] " + service + " 已成功停止 (耗时 " + (i + 1) + "s)");
                return true;
            }
            if (current == null) {
                System.out.println("       [FAIL] 等待过程中服务消失");
                return false;
            }
        }
        System.out.println("       [WARN] " + service + " 停止超时 (" + maxWait + "s)，最终状态: " + status());
        return false;
    }

    private boolean netExec(String action, String service, PrintWriter log) {
        ProcessUtil.ExecResult r = ProcessUtil.run(List.of("net", action, service), null, null, 60);
        if (log != null) {
            log.println("net " + action + " " + service + " -> rc=" + r.code());
            if (!r.stdout().isEmpty()) {
                log.println(r.stdout());
            }
            if (!r.stderr().isEmpty()) {
                log.println(r.stderr());
            }
            log.flush();
        }
        if (r.code() != 0) {
            System.out.println("       [FAIL] net " + action + " 返回码: " + r.code());
            if (!r.stdout().isEmpty()) {
                System.out.println("          stdout: " + r.stdout());
            }
            if (!r.stderr().isEmpty()) {
                System.out.println("          stderr: " + r.stderr());
            }
        }
        return r.code() == 0;
    }

    private boolean elevatedNetExec(String action, String service, PrintWriter log) {
        String script = "Start-Process -FilePath 'net.exe' -ArgumentList '" + action + "','" + service
                + "' -Verb RunAs -Wait";
        ProcessUtil.ExecResult r = ProcessUtil.run(
                List.of("powershell", "-NoProfile", "-Command", script), null, null, 180);
        if (log != null) {
            log.println("elevated net " + action + " " + service + " -> rc=" + r.code());
            log.flush();
        }
        String target = "stop".equals(action) ? "STOPPED" : "RUNNING";
        for (int i = 0; i < 30; i++) {
            ProcessUtil.sleep(1000);
            if (target.equals(status())) {
                return true;
            }
        }
        return false;
    }
}
