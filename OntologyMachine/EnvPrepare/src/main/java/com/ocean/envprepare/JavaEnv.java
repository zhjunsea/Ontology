package com.ocean.envprepare;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 应用级 Java 运行环境（<b>不</b>写入系统级 / 用户级环境变量）。
 *
 * <p>本项目的 Java 一律使用「随包携带的 JDK」。为让本应用及其派生的所有子进程都能稳定地
 * 找到该 JDK，本类只做两件事：
 * <ol>
 *   <li>{@link #probe(String)}：探测随包 java-home 是否可用，读取其 {@code release} 声明的
 *       {@code JAVA_VERSION}，并实际执行 {@code java -version}，回报「真正会被使用」的版本
 *       —— 从根本上避免「压缩包目录名叫 jdk-27、里面其实是 26」这类误判。</li>
 *   <li>{@link #patch(Map, String)}：只向「本应用派生进程」的环境注入 {@code JAVA_HOME}
 *       （{@code JAVACMD} 仅在 java.exe 路径不含空格时注入，见方法内说明），
 *       并把 {@code <java-home>\bin} 前置到 {@code PATH}。</li>
 * </ol>
 *
 * <p>需要「跨进程」落地的部分交给随包脚本承担：每个启动脚本（start-env.cmd / start-app.cmd /
 * start-client.cmd / install.cmd / install.ps1）都在自己的 {@code setlocal}/进程内设置
 * {@code JAVA_HOME} 与 {@code PATH}，作用域仅限「本窗口及其子进程」，<b>绝不</b>修改注册表
 * （既不改系统变量，也不改 {@code HKCU\Environment} 用户变量）。
 */
public final class JavaEnv {

    private JavaEnv() {
    }

    /**
     * 探测结果。
     *
     * @param ok             是否能解析出 java-home
     * @param javaHome       规范化后的 java-home（绝对路径）
     * @param binDir         {@code <java-home>\bin}
     * @param javaExeExists  {@code <java-home>\bin\java.exe} 是否存在
     * @param releaseVersion 该 JDK 的 {@code release} 文件声明的 JAVA_VERSION（读不到为 null）
     * @param folderName     java-home 末级目录名（如 {@code jdk-27}，仅供提示）
     * @param runtimeVersion 实际执行 {@code java -version} 得到的版本行（取不到为 null）
     * @param message        失败原因；成功为 null
     */
    public record Info(boolean ok, String javaHome, String binDir, boolean javaExeExists,
                       String releaseVersion, String folderName, String runtimeVersion, String message) {

        static Info failure(String msg) {
            return new Info(false, null, null, false, null, null, null, msg);
        }
    }

    /** 探测给定 java-home：解析路径 → 校验 java.exe → 读 release 版本 → 执行 java -version。 */
    public static Info probe(String rawJavaHome) {
        if (rawJavaHome == null || rawJavaHome.isBlank()) {
            return Info.failure("java-home 为空");
        }
        Path home;
        try {
            home = Path.of(rawJavaHome.strip()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return Info.failure("java-home 不是合法路径: " + rawJavaHome);
        }
        Path bin = home.resolve("bin");
        Path javaExe = bin.resolve("java.exe");
        boolean exe = Files.isRegularFile(javaExe);
        String release = readReleaseVersion(home.resolve("release"));
        Path leaf = home.getFileName();
        String folder = leaf == null ? null : leaf.toString();
        String runtimeVer = null;
        if (exe) {
            ProcessUtil.ExecResult r = ProcessUtil.run(List.of(javaExe.toString(), "-version"), null, null, 20);
            String out = r.stderr().isBlank() ? r.stdout() : r.stderr();
            runtimeVer = firstLine(out);
        }
        return new Info(true, home.toString(), bin.toString(), exe, release, folder, runtimeVer, null);
    }

    /** 读取 JDK {@code release} 文件里的 {@code JAVA_VERSION="x"}；无则返回 null。 */
    private static String readReleaseVersion(Path releaseFile) {
        if (!Files.isRegularFile(releaseFile)) {
            return null;
        }
        try {
            for (String line : Files.readAllLines(releaseFile, StandardCharsets.UTF_8)) {
                String s = line.strip();
                if (s.startsWith("JAVA_VERSION=")) {
                    String v = s.substring("JAVA_VERSION=".length()).strip();
                    if (v.length() >= 2 && v.startsWith("\"") && v.endsWith("\"")) {
                        v = v.substring(1, v.length() - 1);
                    }
                    return v;
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static String firstLine(String s) {
        if (s == null) {
            return null;
        }
        for (String line : s.split("\\R")) {
            String t = line.strip();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }

    /**
     * 把「随包 JDK」注入到一组子进程环境变量：{@code JAVA_HOME}、{@code JAVACMD}（java.exe 存在时）
     * 以及前置 {@code <java-home>\bin} 的 {@code PATH}。只修改传入的 map，不触碰任何全局状态。
     *
     * @return 是否成功注入（java-home 为空则返回 false）
     */
    public static boolean patch(Map<String, String> env, String rawJavaHome) {
        if (env == null || rawJavaHome == null || rawJavaHome.isBlank()) {
            return false;
        }
        Path home;
        try {
            home = Path.of(rawJavaHome.strip()).toAbsolutePath().normalize();
        } catch (RuntimeException e) {
            return false;
        }
        Path bin = home.resolve("bin");
        env.put("JAVA_HOME", home.toString());
        Path javaExe = bin.resolve("java.exe");
        if (Files.isRegularFile(javaExe)) {
            // 关键：Camunda 的 camunda.bat 以 `%JAVACMD% %JAVA_OPTS% ...`（**未加引号**）直接执行。
            // 若 JAVACMD 含空格（如 C:\Program Files\...\java.exe），cmd 会按空格截断为 `C:\Program`，
            // 报 “'C:\Program' is not recognized as an internal or external command”，
            // Camunda 子进程随即退出、端口永不监听（c8run 只轮询健康地址，表现为一直 Waiting）。
            // 故**仅当路径不含空格/制表符时**注入 JAVACMD；含空格时交由 PATH（已前置 <java-home>\bin）
            // 解析——camunda.bat 会执行 `set JAVACMD=java`，与 c8run 官方默认行为一致。
            String exe = javaExe.toString();
            if (exe.indexOf(' ') < 0 && exe.indexOf('\t') < 0) {
                env.put("JAVACMD", exe);
            }
        }
        String path = env.get("PATH");
        if (path == null) {
            path = System.getenv("PATH");
        }
        env.put("PATH", (path == null || path.isBlank()) ? bin.toString() : bin + ";" + path);
        return true;
    }

    /** 打印一次探测结论（含「目录名 vs release vs 实际 java -version」的一致性检查）。 */
    public static void report(Info info) {
        if (info == null || !info.ok()) {
            return;
        }
        if (!info.javaExeExists()) {
            System.out.println("       [WARN] 未找到 " + info.binDir() + "\\java.exe，随包 JDK 可能不完整。");
        }
        String shown = info.runtimeVersion() != null
                ? info.runtimeVersion()
                : (info.releaseVersion() != null ? "JAVA_VERSION=" + info.releaseVersion() : "(未知)");
        System.out.println("       [INFO] 随包 Java 实际版本: " + shown);
        // 目录名与真实版本不一致时显式提醒，避免「看着像 27、其实在用 26」的误判。
        String folder = info.folderName();
        String real = info.releaseVersion();
        if (folder != null && real != null) {
            String norm = real.contains(".") ? real.substring(0, real.indexOf('.')) : real;
            if (!folder.contains(norm)) {
                System.out.println("       [WARN] 目录名 \"" + folder + "\" 与 release 声明版本 \"" + real
                        + "\" 不一致：请确认随包 JDK 版本（Camunda 日志会显示实际版本 " + real + "）。");
            }
        }
    }

    /**
     * CLI：探测并打印「本应用 Java 环境」（应用级；绝不写系统/用户环境变量）。
     * 供 {@code EnvPrepare set-java-env} 使用，便于在客户机上核对「到底会用哪个 java」。
     */
    public static int runCli(EnvConfig cfg) {
        String jh = cfg.javaHome();
        if (jh == null || jh.isBlank()) {
            System.out.println("[FAIL] 未能解析到 java-home"
                    + "（application.yaml 的 env-prepare.java-home / 环境变量 JAVA_HOME / 自动探测均未取到）。");
            return 1;
        }
        System.out.println("[INFO] 应用级 JAVA_HOME: " + jh);
        Info info = probe(jh);
        if (info.message() != null) {
            System.out.println("[FAIL] 探测失败: " + info.message());
            return 1;
        }
        report(info);
        System.out.println("[OK] 本应用 Java 环境（应用级）：");
        System.out.println("       JAVA_HOME = " + info.javaHome());
        System.out.println("       PATH      = " + info.binDir() + ";%PATH%");
        System.out.println("       说明：仅在「本应用脚本所在窗口及其子进程」内生效；未写入任何系统/用户环境变量。");
        return 0;
    }
}
