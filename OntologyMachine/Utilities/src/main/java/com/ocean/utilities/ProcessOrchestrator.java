package com.ocean.utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.response.ProcessDefinition;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import io.camunda.zeebe.client.ZeebeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Camunda 8 流程编排通用机制（与业务无关）。
 *
 * <p>提供启流程实例、读流程状态/变量（含 404 最终一致性降级）、状态推导、幂等部署等静态工具。
 * 业务层注入 {@link CamundaClient} 后调用本类方法，自身只保留业务变量组装与结论解读。
 */
public final class ProcessOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ProcessOrchestrator.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private ProcessOrchestrator() {
    }

    /** 流程实例的可见状态摘要（{@code state} 为 {@code null} 表示尚未导出到查询存储）。 */
    public record InstanceInfo(String state, boolean hasIncident) {
    }

    /** 启动一个流程实例，返回 {@code processInstanceKey}。 */
    public static long start(CamundaClient client, String bpmnProcessId, Map<String, Object> vars) {
        ProcessInstanceEvent ev = client.newCreateInstanceCommand()
                .bpmnProcessId(bpmnProcessId)
                .latestVersion()
                .variables(vars == null ? Map.of() : vars)
                .send()
                .join();
        return ev.getProcessInstanceKey();
    }

    /**
     * 读取流程实例状态。查询走 Camunda 8 REST 的二级存储（exporter 异步导出），
     * 实例刚创建后可能尚未同步 → 404，此时返回 {@code null}（按运行中处理），仅记 DEBUG。
     */
    public static InstanceInfo fetchInstance(CamundaClient client, long processInstanceKey) {
        try {
            ProcessInstance pi = client.newProcessInstanceGetRequest(processInstanceKey).send().join();
            if (pi == null) {
                return null;
            }
            String state = pi.getState() == null ? null : pi.getState().name();
            return new InstanceInfo(state, Boolean.TRUE.equals(pi.getHasIncident()));
        } catch (ClientHttpException e) {
            if (e.code() == 404) {
                log.debug("[流程编排] 实例尚未导出到查询存储 key={}，按运行中处理", processInstanceKey);
            } else {
                log.warn("[流程编排] 读取流程状态失败 key={}: {}", processInstanceKey, e.toString());
            }
            return null;
        } catch (Exception e) {
            log.warn("[流程编排] 读取流程状态失败 key={}: {}", processInstanceKey, e.toString());
            return null;
        }
    }

    /** 读取流程变量并解码 JSON 值为对象。 */
    public static Map<String, Object> readVariables(CamundaClient client, long processInstanceKey) {
        Map<String, Object> vars = new LinkedHashMap<>();
        try {
            List<Variable> items = client.newVariableSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey))
                    .send()
                    .join()
                    .items();
            for (Variable v : items) {
                vars.put(v.getName(), decode(v.getValue()));
            }
        } catch (Exception e) {
            log.warn("[流程编排] 读取流程变量失败 key={}: {}", processInstanceKey, e.toString());
        }
        return vars;
    }

    /** 由实例状态与 incident 标志推导统一的运行状态字符串。 */
    public static String statusOf(String state, boolean hasIncident) {
        if ("COMPLETED".equals(state)) {
            return "COMPLETED";
        }
        if ("TERMINATED".equals(state)) {
            return "TERMINATED";
        }
        if (hasIncident) {
            return "INCIDENT";
        }
        return "RUNNING";
    }

    /**
     * 幂等部署：若流程定义不存在则部署；若已存在则比对已部署 BPMN 与本地文件内容，
     * 内容一致才跳过，内容有变更则重新部署（避免改了 BPMN 但 processId 不变时引擎仍跑旧版本）。
     *
     * @return {@code true}＝已部署或内容一致已存在；{@code false}＝部署失败
     */
    public static boolean deployIfAbsent(CamundaClient client, String processId, String bpmnPath) {
        try {
            List<ProcessDefinition> existing = client.newProcessDefinitionSearchRequest()
                    .filter(f -> f.processDefinitionId(processId))
                    .send()
                    .join()
                    .items();
            if (existing != null && !existing.isEmpty()) {
                ProcessDefinition latest = latest(existing);
                if (resourceMatches(client, latest, List.of(bpmnPath))) {
                    log.info("[流程编排] 流程 {} 已存在且内容一致（version={}），跳过部署",
                            processId, latest.getVersion());
                    return true;
                }
                log.info("[流程编排] 流程 {} 已存在但内容已变更（deployed version={}），重新部署",
                        processId, latest.getVersion());
            }
            DeploymentEvent deployed = client.newDeployResourceCommand()
                    .addResourceFile(bpmnPath)
                    .send()
                    .join();
            log.info("[流程编排] 已部署 {}，processes={}", bpmnPath,
                    deployed.getProcesses() == null ? 0 : deployed.getProcesses().size());
            return true;
        } catch (Exception e) {
            log.warn("[流程编排] 部署失败 processId={} path={}: {}", processId, bpmnPath, e.toString());
            return false;
        }
    }

    /**
     * 幂等部署多文件：若流程定义不存在则部署；若已存在则比对已部署 BPMN 与本地文件内容，
     * 内容一致才跳过，内容有变更则重新部署（避免改了 BPMN 但 processId 不变时引擎仍跑旧版本）。
     *
     * @return {@code true}＝已部署或内容一致已存在；{@code false}＝部署失败
     */
    public static boolean deployIfAbsent(CamundaClient client, String processId, List<String> bpmnPaths) {
        if (bpmnPaths == null || bpmnPaths.isEmpty()) {
            log.warn("[流程编排] 部署文件列表为空 processId={}", processId);
            return false;
        }
        try {
            List<ProcessDefinition> existing = client.newProcessDefinitionSearchRequest()
                    .filter(f -> f.processDefinitionId(processId))
                    .send()
                    .join()
                    .items();
            if (existing != null && !existing.isEmpty()) {
                ProcessDefinition latest = latest(existing);
                if (resourceMatches(client, latest, bpmnPaths)) {
                    log.info("[流程编排] 流程 {} 已存在且内容一致（version={}），跳过部署",
                            processId, latest.getVersion());
                    return true;
                }
                log.info("[流程编排] 流程 {} 已存在但内容已变更（deployed version={}），重新部署",
                        processId, latest.getVersion());
            }
            DeployResourceCommandStep1.DeployResourceCommandStep2 step = client.newDeployResourceCommand()
                    .addResourceFile(bpmnPaths.get(0));
            for (int i = 1; i < bpmnPaths.size(); i++) {
                step = step.addResourceFile(bpmnPaths.get(i));
            }
            DeploymentEvent deployed = step.send().join();
            log.info("[流程编排] 已部署 {} 个文件，processes={}", bpmnPaths.size(),
                    deployed.getProcesses() == null ? 0 : deployed.getProcesses().size());
            return true;
        } catch (Exception e) {
            log.warn("[流程编排] 部署失败 processId={} files={}: {}", processId, bpmnPaths, e.toString());
            return false;
        }
    }
    /**
     * 幂等部署（{@link ZeebeClient} 重载）：若 client 实为 {@link CamundaClient}，委托至
     * {@link #deployIfAbsent(CamundaClient, String, String)} 走完整幂等检查；
     * 否则（如内存测试引擎）直接部署。
     */
    public static boolean deployIfAbsent(ZeebeClient client, String processId, String bpmnPath) {
        if (client instanceof CamundaClient camundaClient) {
            return deployIfAbsent(camundaClient, processId, bpmnPath);
        }
        try {
            io.camunda.zeebe.client.api.response.DeploymentEvent deployed = client.newDeployResourceCommand()
                    .addResourceFile(bpmnPath)
                    .send()
                    .join();
            log.info("[流程编排] 已部署 {}，processes={}", bpmnPath,
                    deployed.getProcesses() == null ? 0 : deployed.getProcesses().size());
            return true;
        } catch (Exception e) {
            log.warn("[流程编排] 部署失败 processId={} path={}: {}", processId, bpmnPath, e.toString());
            return false;
        }
    }

    /** 取版本号最大的流程定义（部署始终产生新版本，最大版本即最新）。 */
    private static ProcessDefinition latest(List<ProcessDefinition> defs) {
        ProcessDefinition best = defs.get(0);
        for (ProcessDefinition d : defs) {
            if (d.getVersion() > best.getVersion()) {
                best = d;
            }
        }
        return best;
    }

    /**
     * 比对已部署的 BPMN 资源与本地文件内容是否一致（按 {@code resourceName} 匹配本地文件）。
     * 比较前按 LF 归一化并去除首尾空白；读取失败时返回 {@code false}（宁可重新部署）。
     */
    private static boolean resourceMatches(CamundaClient client, ProcessDefinition pd, List<String> bpmnPaths) {
        try {
            String resourceName = pd.getResourceName();
            if (resourceName == null || resourceName.isBlank()) {
                return false;
            }
            for (String p : bpmnPaths) {
                Path path = Path.of(p);
                String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
                if (fileName.equals(resourceName)) {
                    String deployedXml = client.newProcessDefinitionGetXmlRequest(pd.getProcessDefinitionKey())
                            .send()
                            .join();
                    String localXml = Files.readString(path, StandardCharsets.UTF_8);
                    return normalizeXml(deployedXml).equals(normalizeXml(localXml));
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("[流程编排] 比对已部署 BPMN 失败，按需重新部署: {}", e.toString());
            return false;
        }
    }

    private static String normalizeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private static Object decode(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
