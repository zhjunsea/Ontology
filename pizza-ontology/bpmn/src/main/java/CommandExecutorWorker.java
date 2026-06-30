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

    @Override
    public void handle(JobClient client, ActivatedJob job) {
        System.out.println("🔵 [START] handle() called for job key: " + job.getKey());
        System.out.println("   Element ID: " + job.getElementId());
        System.out.println("   Process instance key: " + job.getProcessInstanceKey());

        try {
            // 1. 读取所有变量
            Map<String, Object> variables = job.getVariablesAsMap();
            System.out.println("📦 Variables received: " + variables);

            // 2. 逐一读取并打印
            String systemCommand = (String) variables.get("systemCommand");
            System.out.println("   systemCommand: " + systemCommand);
            String classPath = (String) variables.get("classPath");
            System.out.println("   classPath: " + classPath);
            String excuteCommand = (String) variables.get("excuteCommand");
            System.out.println("   excuteCommand: " + excuteCommand);
            String topOntology = (String) variables.get("topOntology");
            System.out.println("   topOntology: " + topOntology);
            String processIRI = (String) variables.get("processIRI");
            System.out.println("   processIRI: " + processIRI);
            String targetEntity = (String) variables.get("targetEntity");
            System.out.println("   targetEntity: " + targetEntity);
            String targetProperty = (String) variables.get("targetProperty");
            System.out.println("   targetProperty: " + targetProperty);

            // 3. 处理 totalDuration
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

            // 4. 校验必需变量（若缺失则抛出明确异常）
            if (systemCommand == null || systemCommand.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required variable 'systemCommand'");
            }
            if (classPath == null || classPath.trim().isEmpty()) {
                throw new IllegalArgumentException("Missing required variable 'classPath'");
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

            // 5. 获取当前任务 ID
            String currentTaskId = job.getElementId();
            System.out.println("   Current task ID: " + currentTaskId);

            // 6. 拼接 targetPropertyPath
            String targetPropertyPath = processIRI + "," + currentTaskId + "," + targetProperty;
            System.out.println("   targetPropertyPath: " + targetPropertyPath);

            // 7. 构建命令列表
            List<String> cmd = new ArrayList<>();
            cmd.add(systemCommand);
            cmd.add("-cp");
            cmd.add("\""+classPath+"\"");
            cmd.add(excuteCommand);
            cmd.add("-o");
            cmd.add(topOntology);
            cmd.add("-s");
            cmd.add(targetEntity);
            cmd.add("-p");
            cmd.add(targetPropertyPath);

            // 如果是 java 命令，添加 -Dfile.encoding=UTF-8
            if ("java".equalsIgnoreCase(systemCommand)) {
                cmd.add(1, "-Dfile.encoding=UTF-8");
            }

            System.out.println("🚀 Full command: " + String.join(" ", cmd));
            System.out.println("📂 Current working dir: " + System.getProperty("user.dir"));

            // 8. 执行命令
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            long startTime = System.currentTimeMillis();
            Process process = pb.start();

            // 9. 读取输出
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

            System.out.println("📝 Output length: " + output.length());

            // 10. 解析命令输出为数值，累加到 totalDuration
            BigDecimal commandResult;
            try {
                commandResult = new BigDecimal(output.trim());
            } catch (NumberFormatException e) {
                throw new RuntimeException("Command output is not a valid number: " + output, e);
            }
            BigDecimal newTotalDuration = totalDuration.add(commandResult);
            System.out.println("➕ Added command result: " + commandResult + ", new totalDuration: " + newTotalDuration);

            // 11. 保存输出到临时文件
            Path tempFile = Files.createTempFile("cmd-output-", ".txt");
            Files.writeString(tempFile, output, StandardCharsets.UTF_8);
            String filePath = tempFile.toString();
            System.out.println("📄 Output saved to: " + filePath);

            // 12. 生成摘要
            String summary = output.length() > 200 ? output.substring(0, 200) + "..." : output;

            // 13. 完成作业
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
            e.printStackTrace(); // 打印完整堆栈
            // 重新抛出，让 Zeebe 产生 Incident
            throw new RuntimeException("Error processing job: " + e.getMessage(), e);
        }
        System.out.println("🔵 [END] handle() finished for job key: " + job.getKey());
    }

    public static void main(String[] args) {
        System.out.println("🟢 Starting CommandExecutorWorker...");
        String gatewayAddress = System.getenv().getOrDefault("ZEEBE_GATEWAY_ADDRESS", "localhost:26500");
        System.out.println("   Gateway address: " + gatewayAddress);

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
                // 保持运行
                Thread.sleep(Long.MAX_VALUE);
            }
        } catch (Exception e) {
            System.err.println("❌ Fatal error in main(): " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("🟢 Worker stopped.");
    }
}