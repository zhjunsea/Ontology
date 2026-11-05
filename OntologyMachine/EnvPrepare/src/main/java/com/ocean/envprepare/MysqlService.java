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
            int port = parseIntPort(cfg.mysqlPort());
            if (waitPortListening(port, 15)) {
                System.out.println("       [INFO] " + service + " 已在运行中，端口 " + port + " 已监听，复用（跳过启动）");
                return StartOutcome.ALREADY_RUNNING;
            }
            System.out.println("       [CONFLICT] " + service + " 服务已在运行，但配置端口 " + port
                    + " 未被监听——服务实际端口与 application.yaml 配置不一致。");
            System.out.println("          [HINT] 请择一处理：");
            System.out.println("            ①将 application.yaml 的 env-prepare.mysql.port 改为 " + service + " 实际监听端口；");
            System.out.println("            ②修改该服务的 my.ini 端口并重启服务；");
            System.out.println("            ③改用便携模式（env-prepare.mysql.mode: portable）。");
            System.out.println("          （未擅自停止该服务；如需停止请手动执行 net stop " + service + "）");
            return StartOutcome.FAILED;
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

    /**
     * 便携模式：本环境 mysqld 已在运行则跳过（复用）；端口被非本环境进程占用则冲突处理（三选一）；
     * 端口空闲则以独立进程启动 mysqld --console 并等待就绪。
     */
    private StartOutcome ensurePortableStarted(PrintWriter log) {
        int port = parseIntPort(cfg.mysqlPort());
        System.out.println("[CHECK] MySQL(便携): 检查端口 " + port + " ...");

        if (!ProcessUtil.portFree(port)) {
            if (isMineMysqldRunning()) {
                System.out.println("       [INFO] 端口 " + port + " 已被本环境 mysqld 占用，视为已在运行，跳过启动");
                return StartOutcome.ALREADY_RUNNING;
            }
            return resolvePortConflict(port, log);
        }
        return startPortableMysqld(port, log);
    }

    /** 以独立进程启动 mysqld 并等待端口就绪。 */
    private StartOutcome startPortableMysqld(int port, PrintWriter log) {
        String mysqld = cfg.mysqldBin();
        String home = cfg.mysqlHome();
        if (mysqld.isBlank() || !Files.isRegularFile(Path.of(mysqld))) {
            System.out.println("       [FAIL] 未找到 mysqld.exe: '" + mysqld
                    + "'，请在 application.yaml 的 env-prepare.mysql 配置 home 或 mysqld-bin");
            return StartOutcome.FAILED;
        }
        Path cwd = home.isBlank() ? Path.of(mysqld).getParent().getParent() : Path.of(home);
        String datadir = cfg.mysqlDatadir().isBlank() ? ".\\data" : cfg.mysqlDatadir();

        List<String> cmd = List.of(mysqld, "--console", "--basedir=.", "--datadir=" + datadir,
                "--port=" + port);
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

        long selfPid = portableProcess.pid();
        boolean win = ProcessUtil.isWindows();
        int maxWait = 60;
        for (int i = 0; i < maxWait; i++) {
            ProcessUtil.sleep(1000);
            if (portableProcess != null && !portableProcess.isAlive()) {
                // 启动器可能已完成握手并退出、由「重新执行」出的子进程继续监听端口（PID 与启动器不同）。
                long holder = win ? ProcessUtil.pidOnPort(port) : -1;
                if (win && holder > 0 && isMysqldMatching(holder)) {
                    System.out.println("       [OK] MySQL 就绪 (端口 " + port + "，PID=" + holder
                            + "，启动器 PID=" + selfPid + " 已退出，由子进程继续服务，耗时 " + (i + 1) + "s)");
                    return StartOutcome.STARTED;
                }
                System.out.println("       [FAIL] mysqld 进程已退出 (PID=" + selfPid + ")，请查看日志: " + logFile);
                return StartOutcome.FAILED;
            }
            if (win) {
                long holder = ProcessUtil.pidOnPort(port);
                if (holder > 0 && isOwnMysqld(holder, selfPid)) {
                    System.out.println("       [OK] MySQL 就绪 (端口 " + port + "，PID=" + holder
                            + "，耗时 " + (i + 1) + "s)");
                    return StartOutcome.STARTED;
                }
                if (holder > 0) {
                    System.out.println("       [FAIL] 端口 " + port + " 被其它进程 PID=" + holder
                            + " 占用，本环境 mysqld(PID=" + selfPid + ") 无法监听，启动失败");
                    System.out.println("              " + describeProcess(holder));
                    System.out.println("              请改用其它端口，或先释放该端口后重试");
                    return StartOutcome.FAILED;
                }
            } else if (!ProcessUtil.portFree(port)) {
                System.out.println("       [OK] MySQL 就绪 (端口 " + port + "，耗时 " + (i + 1) + "s)");
                return StartOutcome.STARTED;
            }
        }
        System.out.println("       [FAIL] MySQL 启动超时 (" + maxWait + "s)，端口 " + port + " 未被本环境进程监听");
        return StartOutcome.FAILED;
    }

    /** 端口被非本环境进程占用：交互三选一（复用原有环境 / 杀原进程重启 / 提示改端口）。 */
    private StartOutcome resolvePortConflict(int port, PrintWriter log) {
        long pid = ProcessUtil.pidOnPort(port);
        String holder = describeProcess(pid);
        System.out.println("       [CONFLICT] 端口 " + port + " 被非本环境进程占用（非本应用 mysqld）");
        if (!holder.isBlank()) {
            System.out.println("                 占用者: " + holder);
        }
        if (!ConflictResolver.interactive()) {
            System.out.println("       [FAIL] 非交互环境无法处理端口冲突，已中止。");
            System.out.println("          请手动释放端口，或修改 application.yaml 的 env-prepare.mysql.port 后重试");
            return StartOutcome.FAILED;
        }
        switch (ConflictResolver.ask("MySQL(便携)", "端口 " + port, holder)) {
            case REUSE:
                System.out.println("       [INFO] 复用原有环境（沿用占用端口的进程，跳过启动）");
                return StartOutcome.ALREADY_RUNNING;
            case KILL_RESTART:
                if (pid > 0) {
                    System.out.println("       终止原进程 PID=" + pid + (holder.isBlank() ? "" : " (" + holder + ")") + " ...");
                    ProcessUtil.killTree(pid);
                    if (!waitPortFree(port, 15)) {
                        System.out.println("       [FAIL] 无法释放端口 " + port + "：进程 PID=" + pid + " 仍在运行。");
                        System.out.println("              该进程可能以系统服务或更高权限身份运行，普通权限无法终止。");
                        System.out.println("              建议：以管理员身份停止对应服务（如 net stop <服务名>），"
                                + "或将 application.yaml 的 env-prepare.mysql.port 改为其它空闲端口后重试");
                        return StartOutcome.FAILED;
                    }
                    System.out.println("       [OK] 原进程已终止，端口 " + port + " 已释放，启动本环境 mysqld ...");
                } else {
                    System.out.println("       [WARN] 未能定位占用端口的进程 PID，尝试直接启动 ...");
                }
                return startPortableMysqld(port, log);
            default:
                int free = ConflictResolver.suggestFreePort(port);
                System.out.println("       [HINT] 建议将 application.yaml 的 env-prepare.mysql.port 改为 " + free
                        + "，保存后重新启动环境（本次不做任何改动，已退出）");
                return StartOutcome.FAILED;
        }
    }

    /** 该进程是否为「本环境 mysqld」：名称是 mysqld，且命令行匹配本应用的 datadir 或 home。 */
    private boolean matchesThisEnv(ProcessUtil.WinProcess wp) {
        String name = lower(wp.name());
        if (!name.equals("mysqld.exe") && !name.equals("mysqld")) {
            return false;
        }
        String datadirLc = lower(cfg.mysqlDatadir());
        String homeLc = lower(cfg.mysqlHome());
        String cl = lower(wp.commandLine());
        return (!datadirLc.isEmpty() && cl.contains(datadirLc))
                || (!homeLc.isEmpty() && cl.contains(homeLc));
    }

    /** 是否已有本环境（匹配本应用 datadir / home）的 mysqld 在运行。 */
    private boolean isMineMysqldRunning() {
        return ProcessUtil.snapshot().stream().anyMatch(this::matchesThisEnv);
    }

    /** 指定 PID 是否为命令行匹配本环境 datadir / home 的 mysqld（用于端口反查归属校验）。 */
    private boolean isMysqldMatching(long pid) {
        if (pid <= 0) {
            return false;
        }
        return ProcessUtil.snapshot().stream().anyMatch(wp -> wp.pid() == pid && matchesThisEnv(wp));
    }

    /**
     * 端口占用者是否属于本环境 mysqld：为本进程句柄（portableProcess）自身、其后代进程，
     * 或命令行匹配本环境 datadir / home 的 mysqld。
     * <p>mysqld 在 Windows 上会「重新执行自身」——{@code ProcessBuilder.start()} 得到的是启动器 PID，
     * 真正监听端口的是其子进程（PID 不同），故不能用 PID 相等判定，须按进程树归属或命令行特征判定。
     */
    private boolean isOwnMysqld(long pid, long selfPid) {
        if (pid <= 0) {
            return false;
        }
        if (selfPid > 0 && ProcessUtil.isSelfOrDescendant(selfPid, pid)) {
            return true;
        }
        return isMysqldMatching(pid);
    }

    private String describeProcess(long pid) {
        if (pid <= 0) {
            return "";
        }
        for (ProcessUtil.WinProcess wp : ProcessUtil.snapshot()) {
            if (wp.pid() == pid) {
                return "PID=" + pid + " " + wp.name() + " " + wp.commandLine();
            }
        }
        return "PID=" + pid;
    }

    private boolean waitPortFree(int port, int maxSec) {
        for (int i = 0; i < maxSec; i++) {
            if (ProcessUtil.portFree(port)) {
                return true;
            }
            ProcessUtil.sleep(1000);
        }
        return ProcessUtil.portFree(port);
    }

    /** 等待端口被监听（服务模式校验：服务状态 RUNNING 后端口可能稍后才就绪）；就绪返回 true。 */
    private boolean waitPortListening(int port, int maxSec) {
        for (int i = 0; i < maxSec; i++) {
            if (!ProcessUtil.portFree(port)) {
                return true;
            }
            ProcessUtil.sleep(1000);
        }
        return false;
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
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
            killed |= ProcessUtil.killTree(portableProcess.pid());
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
                    killed |= ProcessUtil.killTree(wp.pid());
                }
            }
        }
        if (!killed && !ProcessUtil.portFree(port)) {
            long holderPid = ProcessUtil.pidOnPort(port);
            if (holderPid > 0 && ProcessUtil.processNameContains(holderPid, "mysqld")) {
                System.out.println("       终止 mysqld PID=" + holderPid + " (端口 " + port + " 占用者)");
                killed |= ProcessUtil.killTree(holderPid);
            } else if (holderPid > 0) {
                System.out.println("       [WARN] 端口 " + port + " 被非 mysqld 进程 PID=" + holderPid
                        + " 占用，未擅自终止（请手动处理）");
            }
        }
        if (!killed) {
            System.out.println("       [INFO] 未发现运行中的便携 mysqld 进程");
        }
        for (int i = 0; i < 30; i++) {
            ProcessUtil.sleep(1000);
            if (ProcessUtil.portFree(port)) {
                System.out.println("       [OK] 端口 " + port + " 已释放");
                return true;
            }
        }
        long holder = ProcessUtil.pidOnPort(port);
        if (holder > 0) {
            boolean isMysqld = ProcessUtil.processNameContains(holder, "mysqld");
            System.out.println("       [WARN] 端口 " + port + " 仍被占用 (PID=" + holder
                    + (isMysqld ? " mysqld" : "") + ")");
            if (isMysqld) {
                System.out.println("              该 mysqld 可能以系统服务/更高权限身份运行，普通权限无法终止；"
                        + "请以管理员身份停止对应服务（如 net stop <服务名>）或改用其它端口");
            }
        } else {
            System.out.println("       [WARN] 端口 " + port + " 仍被占用");
        }
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
