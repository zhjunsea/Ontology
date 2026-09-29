package com.ocean.ontologyframework.tcm;

import org.junit.jupiter.api.Order;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * 经方方证 JUnit 测试「串跑 + 结果汇总」程序。
 *
 * <p>把下列 9 个测试类一次性串起来跑，并逐类、逐方法给出成功 / 失败 / 跳过，最后输出汇总与报告文件：
 * <pre>
 *   HerbRuleEngineTest             【规则引擎】方后注加减法派生新方（离线，无前置条件）
 *   DuliFangzhengTest              【独立】陷胸/栀子/瓜蒂/十枣/其他伤寒杂方
 *   HebingFangzhengTest            【合病】
 *   JianjiaFangzhengTest           【兼夹】瘀血/痰饮/气郁等兼夹证检测
 *   JueyinFangzhengTest            【厥阴】
 *   ShaoyangYangmingFangzhengTest  【少阳阳明】
 *   ShaoyinTaiyinFangzhengTest     【少阴太阴】
 *   TaiyangFangzhengTest           【太阳】
 *   ZabingFangzhengTest            【杂病】
 * </pre>
 *
 * <p>用法（推荐用同目录的 run_all_fangzheng_tests.ps1 一键启动）：
 * <pre>
 *   # 1) 全部 9 个类，同一个 JVM 内顺序串跑（最快，BPMN 只部署一次）
 *   java -cp &lt;test-classpath&gt; com.ocean.ontologyframework.tcm.FangzhengSuiteRunner
 *
 *   # 2) 每个测试类单独起一个 JVM（隔离；避免 StopOnTimeoutExtension 的静态 STOP_REQUESTED 跨类连坐）
 *   ... FangzhengSuiteRunner --fork
 *
 *   # 3) 只跑指定类（短名按 tcm 包解析）
 *   ... FangzhengSuiteRunner ZabingFangzhengTest TaiyangFangzhengTest
 *   ... FangzhengSuiteRunner HerbRuleEngineTest
 * </pre>
 *
 * <p>退出码：0 = 全部通过；1 = 存在失败。
 *
 * <p>注意：本类名不以 Test/Tests/TestCase 结尾，surefire 不会把它当成测试用例自动执行。
 */
public final class FangzhengSuiteRunner {

    /**
     * 默认串跑的测试类。
     *
     * <p>短名按 {@link #PKG}（{@code com.ocean.ontologyframework.tcm}）解析。
     * 规则引擎测试 {@code HerbRuleEngineTest} 与本包其余方证测试类同包，故直接用短名，
     * 并排在首位（离线、无前置条件，环境未就绪时也能先拿到结果）。
     */
    public static final List<String> DEFAULT_CLASSES = List.of(
            "HerbRuleEngineTest",
            "DuliFangzhengTest",
            "HebingFangzhengTest",
            "JianjiaFangzhengTest",
            "JueyinFangzhengTest",
            "ShaoyangYangmingFangzhengTest",
            "ShaoyinTaiyinFangzhengTest",
            "TaiyangFangzhengTest",
            "ZabingFangzhengTest"
    );

    private static final String PKG = "com.ocean.ontologyframework.tcm.";
    private static final String DEFAULT_REPORT_DIR = "target";
    private static final int NO_ORDER = Integer.MAX_VALUE;

    // ==================================================================
    //  结果模型
    // ==================================================================

    enum Status {PASS, FAIL, SKIP}

    static final class CaseResult {
        String className = "";
        String methodName = "";
        String displayName = "";
        int order = NO_ORDER;
        Status status = Status.FAIL;
        long durationMs = 0L;
        String message = "";

        String shortClass() {
            int i = className.lastIndexOf('.');
            return i < 0 ? className : className.substring(i + 1);
        }

        String orderTag() {
            return order == NO_ORDER ? "" : "@Order(" + order + ")";
        }
    }

    // ==================================================================
    //  监听器：逐方法记录结果
    // ==================================================================

    static final class Recorder implements TestExecutionListener {

        final List<CaseResult> cases = new ArrayList<>();
        private final Map<String, Long> startedAt = new LinkedHashMap<>();
        private final Map<String, CaseResult> running = new LinkedHashMap<>();
        private final boolean quiet;

        Recorder(boolean quiet) {
            this.quiet = quiet;
        }

        @Override
        public void executionStarted(TestIdentifier id) {
            if (!id.isTest()) return;
            startedAt.put(id.getUniqueId(), System.nanoTime());
            running.put(id.getUniqueId(), newCase(id));
        }

        @Override
        public void executionFinished(TestIdentifier id, TestExecutionResult result) {
            if (!id.isTest()) {
                recordContainerFailure(id, result);
                return;
            }
            CaseResult c = running.remove(id.getUniqueId());
            if (c == null) c = newCase(id);
            c.durationMs = elapsedMs(id.getUniqueId());
            switch (result.getStatus()) {
                case SUCCESSFUL -> c.status = Status.PASS;
                case ABORTED -> {
                    c.status = Status.SKIP;
                    c.message = result.getThrowable().map(FangzhengSuiteRunner::describe).orElse("已跳过");
                }
                case FAILED -> {
                    c.status = Status.FAIL;
                    c.message = result.getThrowable().map(FangzhengSuiteRunner::describe).orElse("失败（无异常信息）");
                }
            }
            cases.add(c);
            if (!quiet) printLive(c);
        }

        @Override
        public void executionSkipped(TestIdentifier id, String reason) {
            if (!id.isTest()) return;
            CaseResult c = newCase(id);
            c.status = Status.SKIP;
            c.message = reason == null ? "已跳过" : reason;
            cases.add(c);
            if (!quiet) printLive(c);
        }

        private long elapsedMs(String uid) {
            Long t0 = startedAt.remove(uid);
            return t0 == null ? 0L : (System.nanoTime() - t0) / 1_000_000L;
        }

        /**
         * 记录「容器级失败」——典型是类级 {@code @BeforeAll} 抛异常（如本体加载失败）。
         *
         * <p><b>为什么必须记</b>：容器失败时该类<b>一个用例都不会执行</b>，若在此静默跳过，
         * 汇总里只剩「无用例」，最终仍会打印「√ 全部通过」并返回退出码 0 —— 假绿。
         * 2026-09-19 修复前，{@code TaiyinbingDefinitionTest} 的
         * {@code UnloadableImportException} 正是以「无用例」形式被吞掉的。
         * 现记为一条 {@code <整个类>} 失败用例，进入汇总、报告与退出码。
         *
         * <p>只取 {@link ClassSource} 容器，避免把引擎/套件根容器的失败重复计一遍。
         */
        private void recordContainerFailure(TestIdentifier id, TestExecutionResult result) {
            if (result.getStatus() != TestExecutionResult.Status.FAILED) return;
            if (!(id.getSource().orElse(null) instanceof ClassSource cs)) return;
            CaseResult c = new CaseResult();
            c.className = cs.getClassName();
            c.methodName = "<整个类>";
            c.displayName = id.getDisplayName();
            c.status = Status.FAIL;
            c.message = result.getThrowable().map(FangzhengSuiteRunner::describe)
                    .orElse("容器级失败（无异常信息）");
            cases.add(c);
            if (!quiet) printLive(c);
        }

        private static CaseResult newCase(TestIdentifier id) {
            CaseResult c = new CaseResult();
            c.displayName = id.getDisplayName();
            id.getSource().ifPresent(src -> {
                if (src instanceof MethodSource ms) {
                    c.className = ms.getClassName();
                    c.methodName = ms.getMethodName();
                    // 用反射取 @Order 值（不同 JUnit 版本 MethodSource 的取方法 API 不一致，反射最稳）
                    try {
                        Method m = Class.forName(ms.getClassName())
                                .getDeclaredMethod(ms.getMethodName());
                        Order o = m.getAnnotation(Order.class);
                        if (o != null) c.order = o.value();
                    } catch (ReflectiveOperationException ignored) {
                        // 取不到 @Order 不影响结果判定
                    }
                }
            });
            if (c.className.isEmpty()) c.className = id.getUniqueId();
            return c;
        }

        private static void printLive(CaseResult c) {
            String tag = switch (c.status) {
                case PASS -> "[通过]";
                case FAIL -> "[失败]";
                case SKIP -> "[跳过]";
            };
            System.out.printf("%s %-34s %-30s %-12s %8.2fs%n",
                    tag, c.shortClass(), c.methodName,
                    "「" + c.displayName + "」", c.durationMs / 1000.0);
            if (c.status != Status.PASS && !c.message.isBlank()) {
                System.out.println("        └─ " + c.message);
            }
        }
    }

    // ==================================================================
    //  main
    // ==================================================================

    public static void main(String[] args) throws Exception {
        Args a = Args.parse(args);

        // 子进程模式：只跑一个类，实时打印逐条结果，并把结果写成 JSON 供父进程汇总
        if (a.childClass != null) {
            List<CaseResult> one = runInProcess(List.of(a.childClass), false);
            writeJson(a.jsonOut, one);
            System.out.flush();
            Runtime.getRuntime().halt(0);
            return;
        }

        List<String> classes = a.classes.isEmpty() ? DEFAULT_CLASSES : a.classes;

        banner(classes, a.fork);

        // 只发现、不执行：用于秒级校验编译产物 / 选择器 / @Suite 套件是否生效
        if (a.discover) {
            discoverOnly(classes);
            Runtime.getRuntime().halt(0);
            return;
        }

        precheck();

        long t0 = System.nanoTime();
        List<CaseResult> all = a.fork ? runForked(classes) : runInProcess(classes, false);
        long totalMs = (System.nanoTime() - t0) / 1_000_000L;

        printSummary(all, totalMs, classes);

        if (!a.noReport) {
            Path dir = Paths.get(a.reportDir);
            Files.createDirectories(dir);
            Path txt = dir.resolve("fangzheng-suite-report.txt");
            Path json = dir.resolve("fangzheng-suite-report.json");
            writeTextReport(txt, all, totalMs);
            writeJson(json, all);
            System.out.println(">> 文本报告：" + txt.toAbsolutePath());
            System.out.println(">> JSON 报告：" + json.toAbsolutePath());
        }

        long failed = all.stream().filter(c -> c.status == Status.FAIL).count();
        System.out.println();
        System.out.println(failed == 0
                ? "√ 全部通过。"
                : "× 存在 " + failed + " 个失败用例，详见上方失败明细。");
        System.out.flush();

        // 测试用的 Zeebe 客户端不会主动关闭，其线程可能阻止 JVM 正常退出，故显式结束。
        Runtime.getRuntime().halt(failed == 0 ? 0 : 1);
    }

    // ==================================================================
    //  执行
    // ==================================================================

    /** 只做用例发现、不执行，用于校验编译产物 / 选择器 / @Suite 套件是否生效。 */
    static void discoverOnly(List<String> classes) {
        DiscoverySelector[] selectors = classes.stream()
                .map(c -> (DiscoverySelector) selectClass(fqcn(c)))
                .toArray(DiscoverySelector[]::new);

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .build();
        Launcher launcher = LauncherFactory.create();
        TestPlan plan = launcher.discover(request);

        Map<String, Integer> perClass = new LinkedHashMap<>();
        int total = 0;
        for (TestIdentifier root : plan.getRoots()) {
            for (TestIdentifier id : plan.getDescendants(root)) {
                if (!id.isTest()) continue;
                total++;
                String cn = "<未知>";
                TestSource src = id.getSource().orElse(null);
                if (src instanceof MethodSource ms) cn = ms.getClassName();
                perClass.merge(cn, 1, Integer::sum);
            }
        }

        System.out.println("── 用例发现（不执行） ──");
        for (Map.Entry<String, Integer> e : perClass.entrySet()) {
            String shortName = e.getKey().substring(e.getKey().lastIndexOf('.') + 1);
            System.out.printf("  %-38s %4d 个用例%n", shortName, e.getValue());
        }
        System.out.println("  ─────────────────────────────────────────────");
        System.out.printf("  %-38s %4d 个用例%n", "合计", total);
        System.out.println();
    }

    /** 在同一个 JVM 内顺序执行给定测试类。 */
    static List<CaseResult> runInProcess(List<String> classes, boolean quiet) {
        DiscoverySelector[] selectors = classes.stream()
                .map(c -> (DiscoverySelector) selectClass(fqcn(c)))
                .toArray(DiscoverySelector[]::new);

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .build();

        Launcher launcher = LauncherFactory.create();
        Recorder recorder = new Recorder(quiet);
        launcher.execute(request, recorder);
        return recorder.cases;
    }

    /** 每个测试类单独起一个 JVM 执行，父进程汇总。 */
    static List<CaseResult> runForked(List<String> classes) throws Exception {
        Path tmp = Files.createTempDirectory("fangzheng-suite-");
        List<CaseResult> all = new ArrayList<>();
        String cp = System.getProperty("java.class.path");

        for (String c : classes) {
            String fqcn = fqcn(c);
            Path json = tmp.resolve(fqcn + ".json");
            System.out.println();
            System.out.println("──────── 子进程启动：" + fqcn + " ────────");

            // classpath 很长，直接拼命令行会超出 Windows 上限，故用 Java 参数文件（@argfile）
            Path argFile = tmp.resolve(fqcn + ".args");
            List<String> childArgs = List.of(
                    "-Dfile.encoding=UTF-8",
                    "-cp",
                    "\"" + cp.replace("\\", "\\\\") + "\"",
                    FangzhengSuiteRunner.class.getName(),
                    "--child",
                    "--class=" + fqcn,
                    "--json=" + json.toAbsolutePath().toString().replace('\\', '/')
            );
            Files.writeString(argFile, String.join("\n", childArgs) + "\n", StandardCharsets.UTF_8);

            Process p = new ProcessBuilder(javaBin(), "@" + argFile.toAbsolutePath())
                    .inheritIO().start();
            int code = p.waitFor();

            if (Files.exists(json)) {
                all.addAll(readJson(json));
            } else {
                CaseResult c2 = new CaseResult();
                c2.className = fqcn;
                c2.methodName = "<整个类>";
                c2.displayName = "子进程未产出结果";
                c2.status = Status.FAIL;
                c2.message = "子进程退出码=" + code + "（可能 JVM 启动失败或类不存在）";
                all.add(c2);
            }
        }
        return all;
    }

    private static String javaBin() {
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return Paths.get(System.getProperty("java.home"), "bin", exe).toString();
    }

    private static String fqcn(String name) {
        return name.contains(".") ? name : PKG + name;
    }

    // ==================================================================
    //  输出
    // ==================================================================

    private static void banner(List<String> classes, boolean fork) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║            经方方证 JUnit 测试套件 —— 串跑 + 结果汇总                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
        System.out.println("测试类（" + classes.size() + " 个）：" + classes);
        System.out.println("执行模式：" + (fork ? "每类独立 JVM（隔离）" : "单 JVM 顺序串跑"));
        System.out.println();
    }

    /** 前置检查：Zeebe 网关 / Worker 端口是否可达（不可达时给出醒目提示，但不中断）。 */
    private static void precheck() {
        String yml = readClasspathText("application.yml");
        String grpc = firstGroup(yml, "grpc-address:\\s*(\\S+)");
        String workerPort = firstGroup(yml, "port:\\s*(\\d+)");

        if (grpc != null) {
            String hostPort = grpc.replaceFirst("^https?://", "");
            int idx = hostPort.lastIndexOf(':');
            if (idx > 0) {
                check("Zeebe 网关", hostPort.substring(0, idx), Integer.parseInt(hostPort.substring(idx + 1)));
            }
        }
        if (workerPort != null) {
            check("本体推理 Worker", "localhost", Integer.parseInt(workerPort));
        }
        System.out.println();
    }

    private static void check(String label, String host, int port) {
        boolean ok;
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(host, port), 800);
            ok = true;
        } catch (Exception e) {
            ok = false;
        }
        if (ok) {
            System.out.println("√ " + label + " " + host + ":" + port + " 可达");
        } else {
            System.out.println("× " + label + " " + host + ":" + port + " 不可达 —— 测试将超时/失败，请先启动！");
        }
    }

    private static void printSummary(List<CaseResult> all, long totalMs, List<String> requested) {
        // 按类聚合，顺序与请求的测试类一致
        Map<String, List<CaseResult>> byClass = new LinkedHashMap<>();
        for (String c : requested) byClass.put(fqcn(c), new ArrayList<>());
        for (CaseResult c : all) byClass.computeIfAbsent(c.className, k -> new ArrayList<>()).add(c);

        System.out.println();
        System.out.println("════════════════════════════════ 汇总 ════════════════════════════════");
        System.out.printf("%-38s %6s %6s %6s %6s %10s%n", "测试类", "用例", "通过", "失败", "跳过", "用时");
        System.out.println("──────────────────────────────────────────────────────────────────────────");

        int tAll = 0, tPass = 0, tFail = 0, tSkip = 0;
        long tMs = 0;
        for (Map.Entry<String, List<CaseResult>> e : byClass.entrySet()) {
            List<CaseResult> list = e.getValue();
            String shortName = e.getKey().substring(e.getKey().lastIndexOf('.') + 1);
            if (list.isEmpty()) {
                System.out.printf("%-38s %6d %6d %6d %6d %10s%n", shortName, 0, 0, 0, 0, "无用例");
                continue;
            }
            int pass = (int) list.stream().filter(c -> c.status == Status.PASS).count();
            int fail = (int) list.stream().filter(c -> c.status == Status.FAIL).count();
            int skip = (int) list.stream().filter(c -> c.status == Status.SKIP).count();
            long ms = list.stream().mapToLong(c -> c.durationMs).sum();
            tAll += list.size();
            tPass += pass;
            tFail += fail;
            tSkip += skip;
            tMs += ms;
            System.out.printf("%-38s %6d %6d %6d %6d %9.1fs%n", shortName, list.size(), pass, fail, skip, ms / 1000.0);
        }
        System.out.println("──────────────────────────────────────────────────────────────────────────");
        System.out.printf("%-38s %6d %6d %6d %6d %9.1fs%n", "合计", tAll, tPass, tFail, tSkip, tMs / 1000.0);
        System.out.printf("总墙钟用时：%.1fs%n", totalMs / 1000.0);

        List<CaseResult> fails = all.stream().filter(c -> c.status == Status.FAIL).toList();
        if (!fails.isEmpty()) {
            System.out.println();
            System.out.println("════════════════════ 失败明细（" + fails.size() + "） ════════════════════");
            int i = 1;
            for (CaseResult c : fails) {
                System.out.printf("[%d] %s#%s  「%s」 %s%n", i++, c.shortClass(), c.methodName, c.displayName, c.orderTag());
                System.out.println("    " + c.message);
            }
        }

        List<CaseResult> skips = all.stream().filter(c -> c.status == Status.SKIP).toList();
        if (!skips.isEmpty()) {
            System.out.println();
            System.out.println("════════════════════ 跳过明细（" + skips.size() + "） ════════════════════");
            int i = 1;
            for (CaseResult c : skips) {
                System.out.printf("[%d] %s#%s  「%s」 %s%n", i++, c.shortClass(), c.methodName, c.displayName, c.orderTag());
                System.out.println("    " + c.message);
            }
        }
    }

    private static void writeTextReport(Path path, List<CaseResult> all, long totalMs) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("经方方证 JUnit 测试套件 —— 运行报告\n");
        sb.append("生成时间：").append(java.time.LocalDateTime.now()).append('\n');
        sb.append("用例总数：").append(all.size())
                .append("，通过 ").append(all.stream().filter(c -> c.status == Status.PASS).count())
                .append("，失败 ").append(all.stream().filter(c -> c.status == Status.FAIL).count())
                .append("，跳过 ").append(all.stream().filter(c -> c.status == Status.SKIP).count())
                .append("，总墙钟 ").append(String.format("%.1fs", totalMs / 1000.0))
                .append("\n\n");

        sb.append("──────── 全部用例（按 @Order 排序） ────────\n");
        List<CaseResult> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing((CaseResult c) -> c.className)
                .thenComparingInt(c -> c.order)
                .thenComparing(c -> c.methodName));
        for (CaseResult c : sorted) {
            sb.append(String.format("%-6s %-34s %-32s %-14s %8.2fs  %s%n",
                    c.status, c.shortClass(), c.methodName, "「" + c.displayName + "」",
                    c.durationMs / 1000.0, c.orderTag()));
            if (c.status != Status.PASS && !c.message.isBlank()) {
                sb.append("       └─ ").append(c.message).append('\n');
            }
        }
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    // ==================================================================
    //  极简 JSON（避免引入额外依赖；格式由本类自己读写）
    // ==================================================================

    private static void writeJson(Path path, List<CaseResult> all) throws Exception {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < all.size(); i++) {
            CaseResult c = all.get(i);
            sb.append("  {\"className\":\"").append(esc(c.className))
                    .append("\",\"methodName\":\"").append(esc(c.methodName))
                    .append("\",\"displayName\":\"").append(esc(c.displayName))
                    .append("\",\"order\":").append(c.order)
                    .append(",\"status\":\"").append(c.status)
                    .append("\",\"durationMs\":").append(c.durationMs)
                    .append(",\"message\":\"").append(esc(c.message)).append("\"}");
            sb.append(i < all.size() - 1 ? ",\n" : "\n");
        }
        sb.append("]\n");
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    private static final Pattern OBJ = Pattern.compile(
            "\\{\"className\":\"(.*?)\",\"methodName\":\"(.*?)\",\"displayName\":\"(.*?)\","
                    + "\"order\":(\\d+),\"status\":\"(\\w+)\",\"durationMs\":(\\d+),\"message\":\"(.*?)\"\\}",
            Pattern.DOTALL);

    private static List<CaseResult> readJson(Path path) throws Exception {
        String s = Files.readString(path, StandardCharsets.UTF_8);
        List<CaseResult> list = new ArrayList<>();
        Matcher m = OBJ.matcher(s);
        while (m.find()) {
            CaseResult c = new CaseResult();
            c.className = unesc(m.group(1));
            c.methodName = unesc(m.group(2));
            c.displayName = unesc(m.group(3));
            c.order = Integer.parseInt(m.group(4));
            c.status = Status.valueOf(m.group(5));
            c.durationMs = Long.parseLong(m.group(6));
            c.message = unesc(m.group(7));
            list.add(c);
        }
        return list;
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (ch < 0x20) b.append(String.format("\\u%04x", (int) ch));
                    else b.append(ch);
                }
            }
        }
        return b.toString();
    }

    private static String unesc(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                switch (n) {
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case '"' -> b.append('"');
                    case '\\' -> b.append('\\');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            b.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        }
                    }
                    default -> b.append(n);
                }
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }

    // ==================================================================
    //  工具
    // ==================================================================

    /** 把异常链前几层的 message 压成一行，便于在汇总里阅读。 */
    static String describe(Throwable t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 3) {
            String msg = cur.getMessage();
            if (msg != null && !msg.isBlank()) {
                if (sb.length() > 0) sb.append(" | ");
                sb.append(msg.trim().replaceAll("\\s+", " "));
            }
            cur = cur.getCause();
            depth++;
        }
        if (sb.length() == 0) sb.append(t.getClass().getName());
        String s = sb.toString();
        return s.length() > 500 ? s.substring(0, 500) + "…" : s;
    }

    private static String readClasspathText(String resource) {
        try (InputStream is = FangzhengSuiteRunner.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) return "";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String firstGroup(String text, String regex) {
        if (text == null || text.isEmpty()) return null;
        Matcher m = Pattern.compile(regex).matcher(text);
        return m.find() ? m.group(1) : null;
    }

    // ==================================================================
    //  参数
    // ==================================================================

    static final class Args {
        boolean fork = false;
        boolean noReport = false;
        boolean discover = false;
        String reportDir = DEFAULT_REPORT_DIR;
        String childClass = null;
        Path jsonOut = null;
        final List<String> classes = new ArrayList<>();

        static Args parse(String[] argv) {
            Args a = new Args();
            for (String s : argv) {
                if (s.equals("--fork") || s.equals("-f")) a.fork = true;
                else if (s.equals("--no-report")) a.noReport = true;
                else if (s.equals("--discover")) a.discover = true;
                else if (s.equals("--child")) { /* 标记位，无参数 */ }
                else if (s.startsWith("--class=")) a.childClass = s.substring("--class=".length());
                else if (s.startsWith("--json=")) a.jsonOut = Paths.get(s.substring("--json=".length()));
                else if (s.startsWith("--report-dir=")) a.reportDir = s.substring("--report-dir=".length());
                else if (s.equals("--help") || s.equals("-h")) {
                    System.out.println("""
                            用法：FangzhengSuiteRunner [选项] [测试类短名...]
                              --fork              每个测试类单独起一个 JVM（隔离，避免超时连坐）
                              --discover          只发现用例、不执行（校验编译/选择器/@Suite 是否生效）
                              --no-report         不写报告文件
                              --report-dir=<dir>  报告输出目录（默认 target）
                              --help              显示本帮助
                            不带测试类参数时，默认串跑 9 个测试类（1 个规则引擎离线测试 + 8 个方证测试）。""");
                    Runtime.getRuntime().halt(0);
                } else if (!s.startsWith("-")) a.classes.add(s);
            }
            return a;
        }
    }

    private FangzhengSuiteRunner() {
    }
}
