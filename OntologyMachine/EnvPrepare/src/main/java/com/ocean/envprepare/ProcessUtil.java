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

    public static void killTree(long pid) {
        if (isWindows()) {
            run(List.of("taskkill", "/PID", String.valueOf(pid), "/T", "/F"), null, null, 30);
            return;
        }
        ProcessHandle.of(pid).ifPresent(ProcessUtil::killTreeJava);
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
}
