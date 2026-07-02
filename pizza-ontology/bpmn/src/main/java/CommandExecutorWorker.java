import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
    private static final String PROCESS_STEP_NS = "http://example.org/pizza/process/";

    @Override
    public void handle(JobClient client, ActivatedJob job) {
        System.out.println("🔵 [START] handle() called for job key: " + job.getKey());
        System.out.println("   Element ID: " + job.getElementId());
        System.out.println("   Process instance key: " + job.getProcessInstanceKey());

        try {
            Map<String, Object> variables = job.getVariablesAsMap();
            System.out.println("📦 Variables received: " + variables);

            String systemCommand = (String) variables.get("systemCommand");
            String classPath = (String) variables.get("classPath");
            String excuteCommand = (String) variables.get("excuteCommand");
            String topOntology = (String) variables.get("topOntology");
            String processIRI = (String) variables.get("processIRI");
            String targetEntity = (String) variables.get("targetEntity");
            String targetProperty = (String) variables.get("targetProperty");
            String queryMode = (String) variables.get("queryMode");
            String words = (String) variables.get("words");

            if (queryMode == null || queryMode.trim().isEmpty()) {
                queryMode = "chain";
            }
            queryMode = queryMode.trim().toLowerCase();

            boolean debugMode = false;
            Object debugObj = variables.get("debugMode");
            if (debugObj != null) {
                debugMode = Boolean.parseBoolean(debugObj.toString());
            }

            System.out.println("   systemCommand: " + systemCommand);
            System.out.println("   classPath: " + classPath);
            System.out.println("   excuteCommand: " + excuteCommand);
            System.out.println("   topOntology: " + topOntology);
            System.out.println("   processIRI: " + processIRI);
            System.out.println("   targetEntity: " + targetEntity);
            System.out.println("   targetProperty: " + targetProperty);
            System.out.println("   queryMode: " + queryMode);
            System.out.println("   debugMode: " + debugMode);
            System.out.println("   words: " + words);

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

            // 基本校验
            if (systemCommand == null || systemCommand.trim().isEmpty())
                throw new IllegalArgumentException("Missing required variable 'systemCommand'");
            if (excuteCommand == null || excuteCommand.trim().isEmpty())
                throw new IllegalArgumentException("Missing required variable 'excuteCommand'");
            if (topOntology == null || topOntology.trim().isEmpty())
                throw new IllegalArgumentException("Missing required variable 'topOntology'");
            if (targetEntity == null || targetEntity.trim().isEmpty())
                throw new IllegalArgumentException("Missing required variable 'targetEntity'");

            // 如果使用 java 命令且未提供 classPath，则必须提供
            if ("java".equalsIgnoreCase(systemCommand) && (classPath == null || classPath.trim().isEmpty())) {
                throw new IllegalArgumentException("Missing required variable 'classPath' when systemCommand is 'java'");
            }

            String currentTaskId = job.getElementId();
            System.out.println("   Current Zeebe task elementId: " + currentTaskId);

            // 构建 GenericOntologyQuery 参数
            List<String> mainArgs = new ArrayList<>();
            mainArgs.add("-o");
            mainArgs.add(topOntology);
            if(!queryMode.equals("label_search")) {
                mainArgs.add("-s");
                mainArgs.add(targetEntity);
            }

            String targetPropertyPath = null;

            switch (queryMode) {
                case "chain":
                    if (processIRI == null || processIRI.trim().isEmpty())
                        throw new IllegalArgumentException("Missing required variable 'processIRI' for chain mode");
                    if (targetProperty == null || targetProperty.trim().isEmpty())
                        throw new IllegalArgumentException("Missing required variable 'targetProperty' for chain mode");
                    String stepIRI = resolveProcessStepIRI(currentTaskId, variables);
                    targetPropertyPath = processIRI + "," + stepIRI + "," + targetProperty;
                    mainArgs.add("-p");
                    mainArgs.add(targetPropertyPath);
                    break;
                case "class_direct_query":
                    if (targetProperty == null || targetProperty.trim().isEmpty())
                        throw new IllegalArgumentException("Missing required variable 'targetProperty' for " + queryMode + " mode");
                    mainArgs.add("-p");
                    mainArgs.add(targetProperty);
                    break;
                case "individual_direct_query":
                    if (targetProperty == null || targetProperty.trim().isEmpty())
                        throw new IllegalArgumentException("Missing required variable 'targetProperty' for " + queryMode + " mode");
                    mainArgs.add("-p");
                    mainArgs.add(targetProperty);
                    mainArgs.add("-m");
                    mainArgs.add("deep");
                    break;
                case "label_search":
                    if (targetProperty == null || targetProperty.trim().isEmpty())
                        throw new IllegalArgumentException("Missing required variable 'targetProperty' for " + queryMode + " mode");
                    mainArgs.add("-p");
                    mainArgs.add(targetProperty);
                    mainArgs.add("-words");
                    mainArgs.add(words);
                    break;
                case "all":
                    if (targetProperty != null && !targetProperty.trim().isEmpty()) {
                        mainArgs.add("-p");
                        mainArgs.add(targetProperty);
                    }
                    mainArgs.add("-m");
                    mainArgs.add("all");
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported queryMode: " + queryMode);
            }

            if (debugMode) {
                mainArgs.add("--debug");
            }

            System.out.println("   targetPropertyPath: " + targetPropertyPath);

            // 构建系统命令（统一使用外部进程）
            List<String> cmd = new ArrayList<>();
            cmd.add(systemCommand);                     // 例如 "java" 或 "/usr/bin/java"
            if (classPath != null && !classPath.trim().isEmpty()) {
                cmd.add("-cp");
                cmd.add(classPath);
            }
            cmd.add(excuteCommand);                     // 例如 "GenericOntologyQuery"
            cmd.addAll(mainArgs);                       // 其余参数

            System.out.println("🚀 Full command: " + String.join(" ", cmd));
            String output = runSystemCommand(cmd);

            System.out.println("📝 Output length: " + output.length());

            // 处理输出
            BigDecimal commandResult = null;
            boolean isNumeric = false;
            try {
                commandResult = new BigDecimal(output.trim());
                isNumeric = true;
            } catch (NumberFormatException e) {
                System.out.println(output);   // 非数字：直接打印原始内容
            }

            BigDecimal newTotalDuration = totalDuration;
            if (isNumeric) {
                newTotalDuration = totalDuration.add(commandResult);
                System.out.println("➕ Added command result: " + commandResult + ", new totalDuration: " + newTotalDuration);
            } else {
                System.out.println("⏭️ Non-numeric output, duration not modified. Output printed above.");
            }

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
                            "totalDuration", newTotalDuration.toString(),
                            "strResult", output
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

    private String resolveProcessStepIRI(String elementId, Map<String, Object> variables) {
        Object stepIRIObj = variables.get("processStepIRI");
        if (stepIRIObj != null) return stepIRIObj.toString();
        return PROCESS_STEP_NS + elementId;
    }

    /**
     * 执行系统命令（所有调用统一走这里）
     */
    private String runSystemCommand(List<String> cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        long startTime = System.currentTimeMillis();
        Process process = pb.start();
        Charset outputCharset = Charset.forName("GBK");   // 根据系统调整
        String output;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), outputCharset))) {
            output = reader.lines().collect(Collectors.joining("\n"));
            int exitCode = process.waitFor();
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("⏱️ Command execution took " + duration + " ms");
            System.out.println("⏱️ Command output " + output);
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