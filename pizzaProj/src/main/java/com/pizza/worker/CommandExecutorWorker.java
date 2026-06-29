package com.pizza.worker;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CommandExecutorWorker {
    private static final Logger LOG = LoggerFactory.getLogger(CommandExecutorWorker.class);
    private static final Pattern VAR_PLACEHOLDER = Pattern.compile("\\$\\{(\\w+)\\}");
    private static final String DEFAULT_RESULT_VAR = "commandResult";
    // 命令执行超时10秒，防止worker线程卡死
    private static final long CMD_TIMEOUT_SECONDS = 10;

    private final ZeebeClient zeebeClient;

    // 构造器打印日志，启动时可快速判断类是否被Spring扫描实例化
    public CommandExecutorWorker(ZeebeClient zeebeClient) {
        this.zeebeClient = zeebeClient;
        LOG.info("===== CommandExecutorWorker 实例已创建，等待订阅 command-executor 任务 =====");
    }

    @JobWorker(type = "command-executor")
    public void handle(final ActivatedJob job) {
        long jobKey = job.getKey();
        Map<String, String> headers = job.getCustomHeaders();
        Map<String, Object> variables = job.getVariablesAsMap();

        LOG.info("收到任务 key={}, headers={}, variables={}", jobKey, headers, variables);

        String commandTemplate = headers.get("command");
        if (commandTemplate == null || commandTemplate.isBlank()) {
            failJob(job, "任务自定义header缺少 command 模板", 0);
            return;
        }
        String resultVariable = headers.getOrDefault("resultVariable", DEFAULT_RESULT_VAR);
        String realCommand = replaceVariables(commandTemplate, variables);
        LOG.info("任务{} 解析后执行命令：{}", jobKey, realCommand);

        Process process = null;
        BufferedReader stdInReader = null;
        BufferedReader stdErrReader = null;
        try {
            ProcessBuilder pb = new ProcessBuilder();
            // 区分操作系统
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                pb.command("cmd.exe", "/c", realCommand);
            } else {
                pb.command("sh", "-c", realCommand);
            }
            process = pb.start();

            // 指定UTF-8编码，解决中文乱码
            stdInReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            stdErrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));

            StringBuilder outputSb = new StringBuilder();
            String line;
            while ((line = stdInReader.readLine()) != null) {
                if (!outputSb.isEmpty()) outputSb.append(System.lineSeparator());
                outputSb.append(line);
            }

            StringBuilder errorSb = new StringBuilder();
            while ((line = stdErrReader.readLine()) != null) {
                errorSb.append(line).append(System.lineSeparator());
            }

            // 超时等待进程结束
            boolean finished = process.waitFor(CMD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroy();
                failJob(job, "命令执行超时" + CMD_TIMEOUT_SECONDS + "秒，强制终止进程", 0);
                return;
            }

            int exitCode = process.exitValue();
            String errorLog = errorSb.toString().trim();
            if (exitCode != 0) {
                String errMsg = String.format("命令执行失败，退出码：%d，错误信息：%s", exitCode, errorLog);
                LOG.error("任务{} {}", jobKey, errMsg);
                failJob(job, errMsg, 0);
                return;
            }

            // 正常完成，返回输出结果
            String cmdOutput = outputSb.toString().trim();
            LOG.info("任务{} 执行成功，输出：{}", jobKey, cmdOutput);
            Map<String, Object> resultVars = new HashMap<>();
            resultVars.put(resultVariable, cmdOutput);

            zeebeClient.newCompleteCommand(jobKey)
                    .variables(resultVars)
                    .send()
                    .join();

        } catch (Exception e) {
            LOG.error("任务{} 命令执行发生异常", jobKey, e);
            failJob(job, "执行异常：" + e.getMessage(), 0);
        } finally {
            // 统一关闭流、销毁进程，释放资源
            closeReader(stdInReader);
            closeReader(stdErrReader);
            if (process != null) {
                process.destroy();
            }
        }
    }

    /** 替换 ${var} 占位符 */
    private String replaceVariables(String template, Map<String, Object> variables) {
        Matcher matcher = VAR_PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = variables.get(varName);
            String replaceStr = value != null ? value.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replaceStr));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 任务失败封装 */
    private void failJob(ActivatedJob job, String errorMsg, int retries) {
        LOG.error("任务{} 标记失败，msg={}", job.getKey(), errorMsg);
        zeebeClient.newFailCommand(job.getKey())
                .retries(retries)
                .errorMessage(errorMsg)
                .send()
                .join();
    }

    /** 关闭流工具方法 */
    private void closeReader(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Exception e) {
                LOG.warn("关闭输入流异常", e);
            }
        }
    }
}