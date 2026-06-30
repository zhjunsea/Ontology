import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandExecutorWorker implements JobHandler {

    private static final String JOB_TYPE = "command-execute-task";

    @Override
    public void handle(JobClient client, ActivatedJob job) {
        System.out.println("🔵 [START] handle() called for job key: " + job.getKey());
        System.out.println("   Element ID: " + job.getElementId());
        System.out.println("   Process instance key: " + job.getProcessInstanceKey());

        try {
            // 1. 读取所有变量
            Map<String, Object> variables = job.getVariablesAsMap();
            System.out.println("📦 Variables received: " + variables);

            String systemCommand = (String) variables.get("systemCommand");
            String classPath = (String) variables.get("classPath");
            String excuteCommand = (String) variables.get("excuteCommand");
            String topOntology = (String) variables.get("topOntology");
            String processIRI = (String) variables.get("processIRI");
            String targetEntity = (String) variables.get("targetEntity");
            String targetProperty = (String) variables.get("targetProperty");

            System.out.println("   systemCommand: " + systemCommand);
            System.out.println("   classPath: " + classPath);
            System.out.println("   excuteCommand: " + excuteCommand);
            System.out.println("   topOntology: " + topOntology);
            System.out.println("   processIRI: " + processIRI);
            System.out.println("   targetEntity: " + targetEntity);
            System.out.println("   targetProperty: " + targetProperty);

            // totalDuration
            BigDecimal totalDuration = BigDecimal.ZERO;
            Object durationObj = variables.get("totalDuration");
            if (durationObj != null) {
                try {
                    totalDuration = new BigDecimal(durationObj.toString());
                } catch (NumberFormatException e) {
                    System.out.println("⚠️ Warning: totalDuration invalid, using 0. Value: " + durationObj);
                }
            }
            System.out.println("   totalDuration (initial): " + totalDuration);

            // 校验必需变量
            if (systemCommand == null || systemCommand.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required variable 'systemCommand'");
            }
            if (excuteCommand == null || excuteCommand.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required variable 'excuteCommand'");
            }
            if (topOntology == null || topOntology.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required variable 'topOntology'");
            }
            if (processIRI == null || processIRI.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required variable 'processIRI'");
            }
            if (targetEntity == null || targetEntity.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required variable 'targetEntity'");
            }
            if (targetProperty == null || targetProperty.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required variable 'targetProperty'");
            }

            String currentTaskId = job.getElementId();
            System.out.println("   Current task ID: " + currentTaskId);

            // 拼接 targetPropertyPath
            String targetPropertyPath = processIRI + "," + currentTaskId + "," + targetProperty;
            System.out.println("   targetPropertyPath: " + targetPropertyPath);

            // 构建参数列表（用于 main 方法）
            List<String> mainArgs = new ArrayList<>();
            mainArgs.add("-o");
            mainArgs.add(topOntology);
            mainArgs.add("-s");
            mainArgs.add(targetEntity);
            mainArgs.add("-p");
            mainArgs.add(targetPropertyPath);

            String output;
            // 判断是否直接调用 Java 类
            if ("java".equalsIgnoreCase(systemCommand)) {
                // 直接调用 excuteCommand 类的 main 方法
                System.out.println("🔧 Directly invoking Java class: " + excuteCommand);
                output = runJavaClass(excuteCommand, mainArgs);
            } else {
                // 其他命令使用 ProcessBuilder
                // 构建完整命令（systemCommand + 参数）
                List<String> cmd = new ArrayList<>();
                cmd.add(systemCommand);
                // 如果传入了 classPath，也作为参数（但这里通常不会）
                // 但为了兼容，我们仍保留，但实际不用于 java
                if (classPath != null && !classPath.isEmpty()) {
                    cmd.add("-cp");
                    cmd.add(classPath);
                }
                cmd.add(excuteCommand);
                cmd.addAll(mainArgs);
                System.out.println("🚀 Full command: " + String.join(" ", cmd));
                output = runSystemCommand(cmd);
            }

            System.out.println("📝 Output length: " + output.length());

            // 解析命令输出为数值，累加到 totalDuration
            BigDecimal commandResult;
            try {
                commandResult = new BigDecimal(output.trim());
            } catch (NumberFormatException e) {
                throw new RuntimeException("Command output is not a valid number: " + output, e);
            }
            BigDecimal newTotalDuration = totalDuration.add(commandResult);
            System.out.println("➕ Added command result: " + commandResult + ", new totalDuration: " + newTotalDuration);

            // 保存输出到临时文件
            Path tempFile = Files.createTempFile("cmd-output-", ".txt");
            Files.writeString(tempFile, output, StandardCharsets.UTF_8);
            String filePath = tempFile.toString();
            System.out.println("📄 Output saved to: " + filePath);

            String summary = output.length() > 200 ? output.substring(0, 200) + "..." : output;

            // 完成作业
            System.out.println("⏳ Completing job...");
            client.newCompleteCommand(job.getKey())
                    .variables(Map.of(
                            "outputFilePath", filePath,
                            "outputSummary", summary,
                            "outputSize", output.length(),
                            "totalDuration", newTotalDuration.toString()
                    ))
                    .send()
                    .join();
            System.out.println("✅ Job completed successfully.");

        } catch (Exception e) {
            System.err.println("❌ ERROR in handle(): " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Error processing job: " + e.getMessage(), e);
        }
        System.out.println("🔵 [END] handle() finished for job key: " + job.getKey());
    }

    /**
     * 调用 Java 类的 main 方法（捕获 System.out 输出）
     */
    private String runJavaClass(String className, List<String> args) throws Exception {
        Class<?> clazz = Class.forName(className);
        Method mainMethod = clazz.getMethod("main", String[].class);
        // 重定向 System.out
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(baos, true, StandardCharsets.UTF_8.name()));
        try {
            mainMethod.invoke(null, (Object) args.toArray(new String[0]));
        } finally {
            System.setOut(originalOut);
        }
        return baos.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * 执行系统命令（非 java）
     */
    private String runSystemCommand(List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        long startTime = System.currentTimeMillis();
        Process process = pb.start();

        Charset outputCharset = Charset.forName("GBK");
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), outputCharset))) {
            output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("⏱️ Command execution took " + duration + " ms");
            if (exitCode != 0) {
                throw new RuntimeException("Command exited with code " + exitCode + ", output: " + output);
            }
        }
        return output;
    }

    public static void main(String[] args) {
        System.out.println("🟢 Starting CommandExecutorWorker...");
        String gatewayAddress = System.getenv().getOrDefault("ZEEBE_GATEWAY_ADDRESS", "localhost:26500");
        System.out.println("   Gateway address: " + gatewayAddress);
        System.out.println("Classpath: " + System.getProperty("java.class.path"));

        try (ZeebeClient client = ZeebeClient.newClientBuilder()
                .gatewayAddress(gatewayAddress)
                .usePlaintext()
                .build()) {

            System.out.println("   Client connected.");

            try (JobWorker worker = client.newWorker()
                    .jobType(JOB_TYPE)
                    .handler(new CommandExecutorWorker())
                    .timeout(Duration.ofMinutes(10))
                    .open()) {

                System.out.println("✅ Worker started, listening for jobs on type '" + JOB_TYPE + "'");
                System.out.println("   (Press Ctrl+C to stop)");
                Thread.sleep(Long.MAX_VALUE);
            }
        } catch (Exception e) {
            System.err.println("❌ Fatal error in main(): " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("🟢 Worker stopped.");
    }
}