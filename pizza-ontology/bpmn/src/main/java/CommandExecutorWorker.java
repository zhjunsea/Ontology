import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 通用 Job Worker，用于执行外部命令或 Java 类。
 * 输入变量：
 *   - command: 要执行的命令（如 "java"）或 Java 类名（以 .class 结尾）
 *   - arguments: 参数列表（可以是字符串数组或单个字符串，字符串按空格拆分）
 * 输出变量：
 *   - outputFilePath: 完整输出保存的临时文件路径
 *   - outputSummary: 输出摘要（前200字符）
 *   - outputSize: 输出总字符数
 */
public class CommandExecutorWorker implements JobHandler {

    @Override
    public void handle(JobClient client, ActivatedJob job) throws Exception {
        Map<String, Object> variables = job.getVariablesAsMap();
        String command = (String) variables.get("command");
        Object argsObj = variables.get("arguments");

        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required variable 'command'");
        }

        // 构建参数列表
        List<String> arguments = new ArrayList<>();
        if (argsObj instanceof List) {
            // 列表：每个元素作为独立参数
            ((List<?>) argsObj).forEach(item -> arguments.add(item.toString()));
        } else if (argsObj instanceof String) {
            // 字符串：按空格拆分成多个参数（适用于无空格路径）
            String argStr = (String) argsObj;
            if (!argStr.isEmpty()) {
                String[] parts = argStr.split("\\s+");
                for (String p : parts) {
                    if (!p.isEmpty()) {
                        arguments.add(p);
                    }
                }
            }
        } else if (argsObj != null) {
            arguments.add(argsObj.toString());
        }

        // 打印调试信息
        System.out.println("🚀 Executing command: " + command);
        System.out.println("📋 Arguments: " + arguments);
        System.out.println("📂 Current working dir: " + System.getProperty("user.dir"));

        String output;
        try {
            output = execute(command, arguments);
        } catch (Exception e) {
            // 抛出异常让流程产生 Incident，便于在 Operate 中查看
            throw new RuntimeException("Command execution failed: " + e.getMessage(), e);
        }

        // 将完整输出保存到临时文件，避免 gRPC 消息过大
        Path tempFile = Files.createTempFile("cmd-output-", ".txt");
        Files.writeString(tempFile, output, StandardCharsets.UTF_8);
        String filePath = tempFile.toString();

        // 生成摘要
        String summary = output.length() > 200 ? output.substring(0, 200) + "..." : output;

        // 返回结果变量
        client.newCompleteCommand(job.getKey())
                .variables(Map.of(
                        "outputFilePath", filePath,
                        "outputSummary", summary,
                        "outputSize", output.length()
                ))
                .send()
                .join();
    }

    /**
     * 根据命令类型执行：
     * - 如果 command 以 ".class" 结尾，尝试调用 Java 类的 main 方法
     * - 否则作为系统命令执行
     */
    private String execute(String command, List<String> arguments) throws Exception {
        if (command.endsWith(".class") || command.endsWith(".java")) {
            String className = command.replace(".class", "").replace(".java", "");
            return runJavaClass(className, arguments);
        } else {
            return runSystemCommand(command, arguments);
        }
    }

    /**
     * 执行系统命令
     */
    private String runSystemCommand(String command, List<String> arguments) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);

        // 如果是 java 命令，显式添加 -Dfile.encoding=UTF-8（避免设置 JAVA_TOOL_OPTIONS 环境变量）
        if ("java".equalsIgnoreCase(command)) {
            boolean hasEncoding = arguments.stream().anyMatch(arg -> arg.startsWith("-Dfile.encoding="));
            if (!hasEncoding) {
                cmd.add("-Dfile.encoding=UTF-8");
            }
        }
        cmd.addAll(arguments);

        System.out.println("🔧 Full command: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        // 不再设置 JAVA_TOOL_OPTIONS 环境变量

        long startTime = System.currentTimeMillis();
        Process process = pb.start();

        // 使用 GBK 读取子进程输出（Windows 默认编码，可正确显示中文）
        Charset outputCharset = Charset.forName("GBK");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), outputCharset))) {
            String output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("⏱️ Command execution took " + duration + " ms");
            if (exitCode != 0) {
                throw new RuntimeException("Command exited with code " + exitCode + ", output: " + output);
            }
            return output;
        }
    }

    /**
     * 调用 Java 类的 main 方法（用于调试或类路径内调用）
     */
    private String runJavaClass(String className, List<String> arguments) throws Exception {
        Class<?> clazz = Class.forName(className);
        Method mainMethod = clazz.getMethod("main", String[].class);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(baos, true, StandardCharsets.UTF_8.name()));
        try {
            mainMethod.invoke(null, (Object) arguments.toArray(new String[0]));
        } finally {
            System.setOut(originalOut);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    public static void main(String[] args) {
        String gatewayAddress = System.getenv().getOrDefault("ZEEBE_GATEWAY_ADDRESS", "localhost:26500");
        try (ZeebeClient client = ZeebeClient.newClientBuilder()
                .gatewayAddress(gatewayAddress)
                .usePlaintext()
                .build()) {

            try (JobWorker worker = client.newWorker()
                    .jobType("command-execute-task")
                    .handler(new CommandExecutorWorker())
                    .timeout(Duration.ofMinutes(10))  // 设置超时为10分钟
                    .open()) {
                System.out.println("✅ Worker started, listening for jobs on type 'command-execute-task'");
                Thread.sleep(Long.MAX_VALUE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}