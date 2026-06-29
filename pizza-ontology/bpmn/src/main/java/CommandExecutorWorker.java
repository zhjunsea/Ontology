import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CommandExecutorWorker {

    private static final Logger LOG = LoggerFactory.getLogger(CommandExecutorWorker.class);

    @JobWorker(type = "command-executor")
    public void handle(final ActivatedJob job) {
        // 读取任务头配置
        Map<String, String> headers = job.getCustomHeaders();
        String commandTemplate = headers.get("command");
        if (commandTemplate == null || commandTemplate.isEmpty()) {
            fail(job, "缺少 'command' 任务头");
            return;
        }
        String resultVariable = headers.getOrDefault("resultVariable", "commandResult");
        int timeout = Integer.parseInt(headers.getOrDefault("timeout", "30"));

        // 获取流程变量并替换占位符 ${varName}
        Map<String, Object> variables = job.getVariablesAsMap();
        String command = replaceVariables(commandTemplate, variables);
        LOG.info("执行命令: {}", command);

        try {
            // 构建进程（自动适配 Windows/Linux）
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb.command("cmd.exe", "/c", command);
            } else {
                pb.command("sh", "-c", command);
            }
            Process process = pb.start();

            // 读取标准输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder outputBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (outputBuilder.length() > 0) outputBuilder.append("\n");
                outputBuilder.append(line);
            }
            // 读取错误输出
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errBuilder = new StringBuilder();
            while ((line = errReader.readLine()) != null) {
                errBuilder.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                fail(job, "命令失败 (退出码: " + exitCode + "): " + errBuilder.toString());
                return;
            }

            // 将结果写入流程变量
            String result = outputBuilder.toString().trim();
            Map<String, Object> resultVars = new HashMap<>();
            resultVars.put(resultVariable, result);
            job.getClient().newCompleteCommand(job.getKey())
                .variables(resultVars)
                .send()
                .join();

        } catch (Exception e) {
            LOG.error("命令执行异常", e);
            fail(job, "命令执行异常: " + e.getMessage());
        }
    }

    private String replaceVariables(String template, Map<String, Object> variables) {
        Pattern pattern = Pattern.compile("\\$\\{(\\w+)\\}");
        Matcher matcher = pattern.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            String replacement = (value != null) ? value.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private void fail(ActivatedJob job, String message) {
        LOG.error(message);
        job.getClient().newFailCommand(job.getKey())
            .retries(0)
            .errorMessage(message)
            .send()
            .join();
    }
}