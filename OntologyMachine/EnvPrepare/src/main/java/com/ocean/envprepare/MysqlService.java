package com.ocean.envprepare;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private Process portableProcess;

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

    /** 确保 MySQL 已启动：按 mysql.mode 分派（portable=独立进程 / service=Windows 服务）。 */
    public StartOutcome ensureStarted(PrintWriter log) {
        if (cfg.mysqlPortable()) {
            return ensurePortableStarted(log);
        }
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

    /** 停止 MySQL：按 mysql.mode 分派（portable=终止独立进程 / service=停止 Windows 服务）。 */
    public boolean stop(PrintWriter log) {
        if (cfg.mysqlPortable()) {
            return stopPortable(log);
        }
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

    /** 便携模式：mysqld 已在监听端口则跳过；否则以独立进程启动 mysqld --console 并等待就绪。 */
    private StartOutcome ensurePortableStarted(PrintWriter log) {
        int port = parseIntPort(cfg.mysqlPort());
        String mysqld = cfg.mysqldBin();
        String home = cfg.mysqlHome();
        System.out.println("[CHECK] MySQL(便携): 检查端口 " + port + " ...");

        if (!ProcessUtil.portFree(port)) {
            System.out.println("       [INFO] 端口 " + port + " 已被占用，视为 MySQL 已在运行，跳过启动");
            return StartOutcome.ALREADY_RUNNING;
        }
        if (mysqld.isBlank() || !Files.isRegularFile(Path.of(mysqld))) {
            System.out.println("       [FAIL] 未找到 mysqld.exe: '" + mysqld
                    + "'，请在 application.yaml 的 env-prepare.mysql 配置 home 或 mysqld-bin");
            return StartOutcome.FAILED;
        }
        Path cwd = home.isBlank() ? Path.of(mysqld).getParent().getParent() : Path.of(home);
        String datadir = cfg.mysqlDatadir().isBlank() ? ".\\data" : cfg.mysqlDatadir();
        List<String> cmd = List.of(mysqld, "--console", "--basedir=.", "--datadir=" + datadir);
        System.out.println("[START] MySQL(便携): " + String.join(" ", cmd));
        System.out.println("       cwd=" + cwd);

        Path logFile = cfg.logDir().resolve("mysql-portable.log");
        try {
            Files.createDirectories(cfg.logDir());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
            portableProcess = pb.start();
            System.out.println("       [OK] mysqld 已启动 (PID=" + portableProcess.pid() + ")，日志: " + logFile);
            if (log != null) {
                log.println("mysqld portable started (PID=" + portableProcess.pid() + ") cwd=" + cwd);
                log.flush();
            }
        } catch (IOException e) {
            System.out.println("       [FAIL] 启动 mysqld 失败: " + e.getMessage());
            return StartOutcome.FAILED;
        }

        int maxWait = 60;
        for (int i = 0; i < maxWait; i++) {
            ProcessUtil.sleep(1000);
            if (!ProcessUtil.portFree(port)) {
                System.out.println("       [OK] MySQL 就绪 (端口 " + port + "，耗时 " + (i + 1) + "s)");
                return StartOutcome.STARTED;
            }
            if (portableProcess != null && !portableProcess.isAlive()) {
                System.out.println("       [FAIL] mysqld 进程已退出，请查看日志: " + logFile);
                return StartOutcome.FAILED;
            }
        }
        System.out.println("       [FAIL] MySQL 启动超时 (" + maxWait + "s)");
        return StartOutcome.FAILED;
    }

    /** 便携模式：终止 mysqld 独立进程（优先持有的进程树，其次按命令行匹配），并等待端口释放。 */
    private boolean stopPortable(PrintWriter log) {
        int port = parseIntPort(cfg.mysqlPort());
        String datadir = cfg.mysqlDatadir();
        String home = cfg.mysqlHome();
        System.out.println("[STOP] MySQL(便携): 终止 mysqld 进程 ...");
        boolean killed = false;

        if (portableProcess != null && portableProcess.isAlive()) {
            System.out.println("       终止 mysqld PID=" + portableProcess.pid() + " (持有句柄)");
            ProcessUtil.killTree(portableProcess.pid());
            killed = true;
        }
        String datadirLc = datadir == null ? "" : datadir.toLowerCase(Locale.ROOT);
        String homeLc = home == null ? "" : home.toLowerCase(Locale.ROOT);
        for (ProcessUtil.WinProcess wp : ProcessUtil.snapshot()) {
            String name = wp.name().toLowerCase(Locale.ROOT);
            String cl = wp.commandLine() == null ? "" : wp.commandLine().toLowerCase(Locale.ROOT);
            if (name.equals("mysqld.exe") || name.equals("mysqld")) {
                boolean match = (!datadirLc.isEmpty() && cl.contains(datadirLc))
                        || (!homeLc.isEmpty() && cl.contains(homeLc));
                if (match) {
                    System.out.println("       终止 mysqld PID=" + wp.pid());
                    ProcessUtil.killTree(wp.pid());
                    killed = true;
                }
            }
        }
        if (!killed) {
            System.out.println("       [INFO] 未发现运行中的便携 mysqld 进程");
        }
        for (int i = 0; i < 15; i++) {
            ProcessUtil.sleep(1000);
            if (ProcessUtil.portFree(port)) {
                System.out.println("       [OK] 端口 " + port + " 已释放");
                return true;
            }
        }
        System.out.println("       [WARN] 端口 " + port + " 仍被占用");
        return false;
    }

    private static int parseIntPort(String s) {
        try {
            return Integer.parseInt(s.strip());
        } catch (Exception e) {
            return 3306;
        }
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
