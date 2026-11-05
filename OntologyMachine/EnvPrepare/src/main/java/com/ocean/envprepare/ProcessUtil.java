package com.ocean.envprepare;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 进程与环境工具：命令执行、进程快照、进程树终止、管理员检测、端口占用检测。
 */
public final class ProcessUtil {

    public record WinProcess(long pid, String name, String commandLine) {
    }

    public record ExecResult(int code, String stdout, String stderr) {
    }

    private ProcessUtil() {
    }

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static String baseName(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        String s = command.replace('\\', '/');
        int idx = s.lastIndexOf('/');
        return (idx >= 0 ? s.substring(idx + 1) : s).toLowerCase(Locale.ROOT);
    }

    public static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static ExecResult run(List<String> cmd, Path cwd, Map<String, String> extraEnv, long timeoutSeconds) {
        ProcessBuilder pb = new ProcessBuilder(wrapForWindows(cmd));
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        if (extraEnv != null) {
            pb.environment().putAll(extraEnv);
        }
        try {
            Process p = pb.start();
            try {
                p.getOutputStream().close();
            } catch (IOException ignored) {
            }
            byte[] out = p.getInputStream().readAllBytes();
            byte[] err = p.getErrorStream().readAllBytes();
            boolean done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                return new ExecResult(-1, new String(out, StandardCharsets.UTF_8),
                        "命令超时 (> " + timeoutSeconds + "s)");
            }
            return new ExecResult(p.exitValue(),
                    new String(out, StandardCharsets.UTF_8).strip(),
                    new String(err, StandardCharsets.UTF_8).strip());
        } catch (IOException e) {
            return new ExecResult(-1, "", e.getMessage() == null ? e.toString() : e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecResult(-1, "", "命令被中断");
        }
    }

    /**
     * Windows 上以「路径含空格的 .bat/.cmd」启动时，cmd 的引号剥离规则会破坏首参路径
     * （命令行引号多于两个 → 去掉首尾引号 → 路径被空格截断，报「'X:\dir' 不是内部或外部命令」）。
     * 统一改写为 {@code cmd.exe /s /c "<逐段加引号>"}：{@code /s} 使引号剥离行为确定，
     * 内层对各含空格/引号片段逐个加引号保护。非 Windows 或首参非 .bat/.cmd 时原样返回。
     */
    public static List<String> wrapForWindows(List<String> cmd) {
        if (!isWindows() || cmd.isEmpty()) {
            return new ArrayList<>(cmd);
        }
        String exe = cmd.get(0).toLowerCase(Locale.ROOT);
        if (!(exe.endsWith(".bat") || exe.endsWith(".cmd"))) {
            return new ArrayList<>(cmd);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cmd.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            String s = cmd.get(i);
            boolean needQuote = s.contains(" ") || s.contains("\t") || s.contains("\"");
            sb.append(needQuote ? "\"" + s.replace("\"", "\"\"") + "\"" : s);
        }
        return List.of("cmd.exe", "/s", "/c", "\"" + sb + "\"");
    }

    public static List<WinProcess> snapshot() {
        if (isWindows()) {
            List<WinProcess> ps = powershellSnapshot();
            if (!ps.isEmpty()) {
                return ps;
            }
        }
        return javaSnapshot();
    }

    private static List<WinProcess> powershellSnapshot() {
        String script = "Get-CimInstance Win32_Process | ForEach-Object { "
                + "[string]$_.ProcessId + '|' + [string]$_.Name + '|' + [string]$_.CommandLine }";
        ExecResult r = run(List.of("powershell", "-NoProfile", "-Command", script), null, null, 60);
        List<WinProcess> list = new ArrayList<>();
        for (String line : r.stdout().split("\\R")) {
            String s = line.strip();
            if (s.isEmpty()) {
                continue;
            }
            int p1 = s.indexOf('|');
            int p2 = p1 < 0 ? -1 : s.indexOf('|', p1 + 1);
            if (p1 < 0 || p2 < 0) {
                continue;
            }
            try {
                long pid = Long.parseLong(s.substring(0, p1).strip());
                String name = s.substring(p1 + 1, p2).strip();
                String cl = s.substring(p2 + 1).strip();
                list.add(new WinProcess(pid, name, cl));
            } catch (NumberFormatException ignored) {
            }
        }
        return list;
    }

    private static List<WinProcess> javaSnapshot() {
        List<WinProcess> list = new ArrayList<>();
        for (ProcessHandle ph : ProcessHandle.allProcesses().collect(Collectors.toList())) {
            String name = baseName(ph.info().command().orElse(""));
            String cl = ph.info().commandLine().orElse("");
            list.add(new WinProcess(ph.pid(), name, cl));
        }
        return list;
    }

    /**
     * 终止进程树。返回是否「已确认终止」（进程不再存活）。
     * <p>Windows 上 taskkill 对以系统服务/更高权限（如 Session 0）运行的进程会返回「拒绝访问」而失败，
     * 此时不能静默当作成功——必须依据进程是否真正消失来判定，交由调用方决定后续处置。
     */
    public static boolean killTree(long pid) {
        if (pid <= 0) {
            return false;
        }
        if (isWindows()) {
            ExecResult r = run(List.of("taskkill", "/PID", String.valueOf(pid), "/T", "/F"), null, null, 30);
            boolean gone = !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
            return r.code() == 0 || gone;
        }
        ProcessHandle.of(pid).ifPresent(ProcessUtil::killTreeJava);
        return !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    public static void killTreeJava(ProcessHandle ph) {
        if (ph == null || !ph.isAlive()) {
            return;
        }
        List<ProcessHandle> children = ph.descendants().collect(Collectors.toList());
        for (ProcessHandle c : children) {
            try {
                c.destroy();
            } catch (Exception ignored) {
            }
        }
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && children.stream().anyMatch(ProcessHandle::isAlive)) {
            sleep(100);
        }
        for (ProcessHandle c : children) {
            if (c.isAlive()) {
                try {
                    c.destroyForcibly();
                } catch (Exception ignored) {
                }
            }
        }
        try {
            ph.destroy();
        } catch (Exception ignored) {
        }
        long d2 = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < d2 && ph.isAlive()) {
            sleep(100);
        }
        if (ph.isAlive()) {
            try {
                ph.destroyForcibly();
            } catch (Exception ignored) {
            }
        }
    }

    public static boolean isAdmin() {
        if (!isWindows()) {
            return true;
        }
        String script = "([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]"
                + "::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)";
        ExecResult r = run(List.of("powershell", "-NoProfile", "-Command", script), null, null, 20);
        return r.stdout().strip().equalsIgnoreCase("True");
    }

    public static boolean portFree(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", port), 500);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    /** 查询监听指定端口的进程 PID（仅 Windows，基于 netstat -ano 的 LISTENING 行）；未找到返回 -1。 */
    public static long pidOnPort(int port) {
        if (!isWindows()) {
            return -1;
        }
        ExecResult r = run(List.of("netstat", "-ano"), null, null, 20);
        String suffix = ":" + port;
        for (String line : r.stdout().split("\\R")) {
            String s = line.strip();
            if (s.isEmpty() || !s.toUpperCase(Locale.ROOT).contains("LISTENING")) {
                continue;
            }
            String[] t = s.split("\\s+");
            if (t.length < 4) {
                continue;
            }
            String local = t[1];
            if (local.endsWith(suffix)) {
                try {
                    return Long.parseLong(t[t.length - 1]);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    /**
     * 判断 {@code pid} 是否为 {@code ancestorPid} 自身或其（递归）后代进程。
     * <p>用于 mysqld 等会在启动时「重新执行自身」的程序：{@code ProcessBuilder.start()} 得到的是外层
     * 启动器 PID，而真正监听端口的往往是它的子进程，二者 PID 不同。因此就绪/归属判定必须按进程树
     * 关系（或命令行特征）判断，而不能用 PID 相等。
     */
    public static boolean isSelfOrDescendant(long ancestorPid, long pid) {
        if (ancestorPid <= 0 || pid <= 0) {
            return false;
        }
        if (ancestorPid == pid) {
            return true;
        }
        return ProcessHandle.of(ancestorPid)
                .map(ph -> ph.descendants().anyMatch(d -> d.pid() == pid))
                .orElse(false);
    }

    /**
     * 判断指定 PID 的进程可执行名是否包含给定关键字（如 "mysqld" / "epmd"）。
     * 优先用 {@link ProcessHandle#info()#command()} 取可执行路径（不依赖命令行快照，端口反查归属更可靠），
     * 取不到时回退进程快照按 PID 匹配。用于「按端口找占用者 → 校验是否本环境进程 → 精确终止」。
     */
    public static boolean processNameContains(long pid, String keyword) {
        if (pid <= 0 || keyword == null || keyword.isBlank()) {
            return false;
        }
        String kw = keyword.toLowerCase(Locale.ROOT);
        String cmd = ProcessHandle.of(pid).flatMap(ph -> ph.info().command()).orElse("");
        if (!cmd.isEmpty()) {
            return baseName(cmd).contains(kw);
        }
        for (WinProcess wp : snapshot()) {
            if (wp.pid() == pid) {
                String name = wp.name();
                return name != null && name.toLowerCase(Locale.ROOT).contains(kw);
            }
        }
        return false;
    }
}
