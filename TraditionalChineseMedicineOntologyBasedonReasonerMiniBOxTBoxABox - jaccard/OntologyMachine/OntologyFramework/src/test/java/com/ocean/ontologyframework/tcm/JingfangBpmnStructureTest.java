package com.ocean.ontologyframework.tcm;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 经方辨证流程 BPMN 结构契约测试（完全离线，不需要 Camunda / MySQL）。
 *
 * <p>守护四件事：
 * <ol>
 *   <li><b>拓扑正确</b>：「症状映射」「加减药」等关键节点确实串在主链路上，
 *       且从开始事件可达三个结束事件（诊断完成 / 给出候选方证 / 无法给出诊断）；</li>
 *   <li><b>全自动约束</b>：流程内不得出现任何人工任务（userTask）——前端只负责
 *       「输入症状」与「展示结果 + 依据」，流程必须由 JobWorker 一路跑到结束事件；</li>
 *   <li><b>引用自洽</b>：sequenceFlow 的 sourceRef/targetRef 全部可解析，
 *       节点声明的 incoming/outgoing 与实际连线双向一致；</li>
 *   <li><b>BPMN ↔ Worker 不漂移</b>：BPMN 里每个 serviceTask 的 jobType
 *       都能在 {@code TCMOntologyJobWorker} 中找到对应的 {@code @JobWorker}。</li>
 * </ol>
 *
 * <p><b>2026-09-20 契约变更</b>：流程改为「全自动版」——取消
 * {@code Gateway_NeedConfirm} + {@code Task_ConfirmSymptoms} 人工确认回环
 * （症状映射后直接进入四诊录入），并把「一致性检查不通过 / 无方证推荐」
 * 由「回到人工修改四诊」改为直接落到 {@code EndEvent_NoResult}；
 * 同时新增 {@code EndEvent_Candidates}（仅有候选）与结果形态网关
 * {@code Gateway_HasRecommendation} 的三分支。本测试据此对齐。
 */
class JingfangBpmnStructureTest {

    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String ZEEBE_NS = "http://camunda.org/schema/zeebe/1.0";
    private static final String DI_NS = "http://www.omg.org/spec/BPMN/20100524/DI";

    private static final String PROCESS_ID = "Process_Jingfang_Diagnosis";
    private static final String START = "StartEvent_1";
    private static final String END = "EndEvent_Success";
    private static final String END_CANDIDATES = "EndEvent_Candidates";
    private static final String END_NO_RESULT = "EndEvent_NoResult";

    /** 主链路关键节点 */
    private static final String T_MAPPING = "Task_SymptomMapping";
    private static final String T_INPUT = "Task_InputSizhen";
    private static final String T_CHECK = "Task_ConsistencyCheck";
    private static final String T_BAGANG = "Task_Bagang";
    private static final String T_MODIFY = "Task_HerbModification";
    private static final String T_PRESCRIPTION = "Task_Prescription";
    private static final String T_EXPLAIN = "Task_Explanation";
    private static final String GW_RESULT = "Gateway_HasRecommendation";

    /** Camunda 内置 userTask 的 jobType，不需要自定义 Worker */
    private static final String USER_TASK_JOB_TYPE = "io.camunda.zeebe:userTask";

    private static Path bpmnPath;
    private static Document doc;
    private static Element process;

    @BeforeAll
    static void load() throws Exception {
        bpmnPath = Paths.get(readConfig("bpmn-path"));
        assertThat(Files.isRegularFile(bpmnPath))
                .as("BPMN 文件必须存在: %s", bpmnPath).isTrue();

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        DocumentBuilder b = f.newDocumentBuilder();
        doc = b.parse(bpmnPath.toFile());
        doc.getDocumentElement().normalize();

        NodeList procs = doc.getElementsByTagNameNS(BPMN_NS, "process");
        assertThat(procs.getLength()).isEqualTo(1);
        process = (Element) procs.item(0);
    }

    @SuppressWarnings("unchecked")
    private static String readConfig(String key) {
        try (InputStream is = JingfangBpmnStructureTest.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml 必须在 classpath 上").isNotNull();
            Map<String, Object> cfg = new Yaml().load(is);
            Map<String, Object> ontology = (Map<String, Object>) cfg.get("ontology");
            return (String) ontology.get(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================
    // 工具
    // ============================================================

    /** 流程内所有指定 localName 的元素（限定在 process 之下，排除 DI 段） */
    private static List<Element> elements(String localName) {
        List<Element> out = new ArrayList<>();
        NodeList nl = process.getElementsByTagNameNS(BPMN_NS, localName);
        for (int i = 0; i < nl.getLength(); i++) out.add((Element) nl.item(i));
        return out;
    }

    private static Element byId(String id) {
        NodeList nl = process.getElementsByTagNameNS(BPMN_NS, "*");
        for (int i = 0; i < nl.getLength(); i++) {
            Element e = (Element) nl.item(i);
            if (id.equals(e.getAttribute("id"))) return e;
        }
        return null;
    }

    private static String localName(Element e) {
        return e.getLocalName() != null ? e.getLocalName() : e.getTagName();
    }

    /** 直接子元素文本（用于 conditionExpression / incoming / outgoing） */
    private static List<String> childTexts(Element parent, String localName) {
        List<String> out = new ArrayList<>();
        NodeList nl = parent.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            Node n = nl.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE
                    && localName.equals(n.getLocalName())) {
                out.add(n.getTextContent().trim());
            }
        }
        return out;
    }

    private static Map<String, String> sequenceFlows() {
        Map<String, String> m = new HashMap<>();
        for (Element e : elements("sequenceFlow")) {
            m.put(e.getAttribute("id"), e.getAttribute("sourceRef") + "->" + e.getAttribute("targetRef"));
        }
        return m;
    }

    /** 所有「流节点」（有 id 且属于 BPMN 流程语义的元素） */
    private static Set<String> flowNodeIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (String ln : List.of("startEvent", "endEvent", "serviceTask", "userTask",
                "exclusiveGateway", "parallelGateway", "inclusiveGateway",
                "intermediateCatchEvent", "intermediateThrowEvent", "subProcess")) {
            for (Element e : elements(ln)) {
                if (!e.getAttribute("id").isEmpty()) ids.add(e.getAttribute("id"));
            }
        }
        return ids;
    }

    private static Set<String> idsOf(String localName) {
        return elements(localName).stream()
                .map(e -> e.getAttribute("id"))
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ============================================================
    // 1. 基本结构
    // ============================================================

    @Test
    @DisplayName("流程 id 与可执行标记正确")
    void processIdAndExecutable() {
        assertThat(process.getAttribute("id")).isEqualTo(PROCESS_ID);
        assertThat(process.getAttribute("isExecutable")).isEqualTo("true");
    }

    @Test
    @DisplayName("关键节点齐备：症状映射 / 加减药 / 母方推荐 / 生成解释 / 三个结束事件")
    void newElementsPresent() {
        Element mapping = byId(T_MAPPING);
        assertThat(mapping).as("症状映射 serviceTask").isNotNull();
        assertThat(localName(mapping)).isEqualTo("serviceTask");
        assertThat(jobTypeOf(mapping)).isEqualTo("symptom-mapping");

        Element modify = byId(T_MODIFY);
        assertThat(modify).as("加减药 serviceTask").isNotNull();
        assertThat(localName(modify)).isEqualTo("serviceTask");
        assertThat(jobTypeOf(modify)).isEqualTo("herb-modification");

        Element prescription = byId(T_PRESCRIPTION);
        assertThat(prescription).as("母方推荐 serviceTask").isNotNull();
        assertThat(localName(prescription)).isEqualTo("serviceTask");

        Element explain = byId(T_EXPLAIN);
        assertThat(explain).as("生成解释 serviceTask").isNotNull();
        assertThat(localName(explain)).isEqualTo("serviceTask");

        // 三个结束事件：诊断完成 / 仅有候选 / 无法给出诊断
        assertThat(byId(END)).as("结束事件·诊断完成").isNotNull();
        assertThat(localName(byId(END))).isEqualTo("endEvent");
        assertThat(byId(END_CANDIDATES)).as("结束事件·给出候选方证").isNotNull();
        assertThat(localName(byId(END_CANDIDATES))).isEqualTo("endEvent");
        assertThat(byId(END_NO_RESULT)).as("结束事件·无法给出诊断").isNotNull();
        assertThat(localName(byId(END_NO_RESULT))).isEqualTo("endEvent");
    }

    @Test
    @DisplayName("全自动约束：流程内不得出现任何人工任务（userTask）")
    void noUserTasksFullyAutomated() {
        assertThat(elements("userTask"))
                .as("流程必须全自动：不得出现任何人工任务（userTask）")
                .isEmpty();
    }

    private static String jobTypeOf(Element task) {
        NodeList nl = task.getElementsByTagNameNS(ZEEBE_NS, "taskDefinition");
        if (nl.getLength() == 0) return null;
        return ((Element) nl.item(0)).getAttribute("type");
    }

    // ============================================================
    // 2. 引用自洽
    // ============================================================

    @Test
    @DisplayName("所有 sequenceFlow 的 sourceRef/targetRef 都能解析到真实节点")
    void sequenceFlowRefsResolve() {
        Set<String> nodes = flowNodeIds();
        assertThat(nodes).isNotEmpty();
        for (Element sf : elements("sequenceFlow")) {
            String id = sf.getAttribute("id");
            assertThat(nodes).as("flow %s sourceRef", id).contains(sf.getAttribute("sourceRef"));
            assertThat(nodes).as("flow %s targetRef", id).contains(sf.getAttribute("targetRef"));
        }
    }

    @Test
    @DisplayName("节点声明的 incoming/outgoing 与实际连线双向一致")
    void incomingOutgoingConsistent() {
        Map<String, String> flows = sequenceFlows();

        for (String nodeId : flowNodeIds()) {
            Element node = byId(nodeId);
            Set<String> declaredIn = new HashSet<>(childTexts(node, "incoming"));
            Set<String> declaredOut = new HashSet<>(childTexts(node, "outgoing"));

            Set<String> actualIn = flows.entrySet().stream()
                    .filter(e -> e.getValue().endsWith("->" + nodeId))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
            Set<String> actualOut = flows.entrySet().stream()
                    .filter(e -> e.getValue().startsWith(nodeId + "->"))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());

            assertThat(declaredIn).as("节点 %s 的 incoming", nodeId)
                    .containsExactlyInAnyOrderElementsOf(actualIn);
            assertThat(declaredOut).as("节点 %s 的 outgoing", nodeId)
                    .containsExactlyInAnyOrderElementsOf(actualOut);
        }
    }

    @Test
    @DisplayName("每个节点都有出边（结束事件除外），每个节点都有入边（开始事件除外）")
    void noDanglingNodes() {
        Map<String, String> flows = sequenceFlows();
        Set<String> startEvents = idsOf("startEvent");
        Set<String> endEvents = idsOf("endEvent");
        for (String nodeId : flowNodeIds()) {
            if (!startEvents.contains(nodeId)) {
                assertThat(flows.values().stream().anyMatch(v -> v.endsWith("->" + nodeId)))
                        .as("节点 %s 应有入边", nodeId).isTrue();
            }
            if (!endEvents.contains(nodeId)) {
                assertThat(flows.values().stream().anyMatch(v -> v.startsWith(nodeId + "->")))
                        .as("节点 %s 应有出边", nodeId).isTrue();
            }
        }
    }

    // ============================================================
    // 3. 拓扑 / 可达性
    // ============================================================

    /** 从 start 出发按 sequenceFlow 做 BFS，返回可达节点集合 */
    private static Set<String> reachableFrom(String start) {
        Map<String, List<String>> adj = new HashMap<>();
        for (String v : sequenceFlows().values()) {
            String[] p = v.split("->");
            adj.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
        }
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> q = new ArrayDeque<>();
        q.add(start);
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (!seen.add(cur)) continue;
            for (String nxt : adj.getOrDefault(cur, List.of())) q.add(nxt);
        }
        return seen;
    }

    @Test
    @DisplayName("从开始事件可达三个结束事件，且途经全部关键节点")
    void newNodesOnMainPath() {
        Set<String> reach = reachableFrom(START);
        assertThat(reach).contains(END, END_CANDIDATES, END_NO_RESULT);
        assertThat(reach).contains(T_MAPPING, T_INPUT, T_CHECK, T_BAGANG,
                T_PRESCRIPTION, T_MODIFY, T_EXPLAIN);
    }

    @Test
    @DisplayName("主链路顺序：症状映射 → 录入四诊 → … → 母方推荐 → 加减药 → 解释 → 结果网关")
    void mainPathOrder() {
        Map<String, String> flows = sequenceFlows();
        assertThat(flows.get("Flow_Start_To_Mapping")).isEqualTo(START + "->" + T_MAPPING);
        // 症状映射后直接进入四诊录入（全自动版不再有确认回环）
        assertThat(flows.get("Flow_Mapping_To_Input")).isEqualTo(T_MAPPING + "->" + T_INPUT);
        assertThat(flows.get("Flow_Input_To_Check")).isEqualTo(T_INPUT + "->" + T_CHECK);
        // 加减药插在「母方推荐」与「生成解释」之间
        assertThat(flows.get("Flow_Prescription_To_Modify")).isEqualTo(T_PRESCRIPTION + "->" + T_MODIFY);
        assertThat(flows.get("Flow_Modify_To_Explanation")).isEqualTo(T_MODIFY + "->" + T_EXPLAIN);
        // 解释之后进入结果形态网关
        assertThat(flows.get("Flow_Explanation_To_Gateway2")).isEqualTo(T_EXPLAIN + "->" + GW_RESULT);
    }

    @Test
    @DisplayName("结果形态网关三条分支互斥且覆盖 fangzhengRealized / fangzhengCandidates")
    void gatewayConditions() {
        Element gw = byId(GW_RESULT);
        assertThat(gw).as("结果形态网关").isNotNull();
        assertThat(localName(gw)).isEqualTo("exclusiveGateway");
        // 兜底默认分支：即使变量缺失也不会因「无分支命中」而卡死
        assertThat(gw.getAttribute("default")).isEqualTo("Flow_HasRecommendation_No");

        Element yes = byId("Flow_HasRecommendation_Yes");
        Element candidates = byId("Flow_HasRecommendation_Candidates");
        Element no = byId("Flow_HasRecommendation_No");
        assertThat(yes).as("完全命中分支").isNotNull();
        assertThat(candidates).as("仅有候选分支").isNotNull();
        assertThat(no).as("无候选分支").isNotNull();

        String condYes = conditionOf(yes);
        String condCandidates = conditionOf(candidates);
        String condNo = conditionOf(no);

        // 完全命中：fangzhengRealized = true
        assertThat(condYes).contains("fangzhengRealized").contains("true");
        // 仅有候选：未 realize 且候选非空
        assertThat(condCandidates).contains("fangzhengRealized").contains("false")
                .contains("fangzhengCandidates");
        // 无候选：未 realize 且候选为空
        assertThat(condNo).contains("fangzhengRealized").contains("false")
                .contains("fangzhengCandidates");

        // 互斥：Yes 判 true，另两条判 false
        assertThat(condYes).doesNotContain("false");
        assertThat(condCandidates).doesNotContain("= true");
        assertThat(condNo).doesNotContain("= true");
    }

    private static String conditionOf(Element flow) {
        NodeList nl = flow.getElementsByTagNameNS(BPMN_NS, "conditionExpression");
        assertThat(nl.getLength()).as("flow %s 必须有条件表达式", flow.getAttribute("id")).isEqualTo(1);
        return nl.item(0).getTextContent().trim();
    }

    // ============================================================
    // 4. BPMN ↔ Worker 契约
    // ============================================================

    @Test
    @DisplayName("BPMN 中每个 serviceTask 的 jobType 都有对应的 @JobWorker 实现")
    void everyJobTypeHasWorker() throws Exception {
        Path worker = Paths.get(System.getProperty("user.dir"),
                "src", "main", "java", "com", "ocean", "ontologyframework",
                "TCMOntologyJobWorker.java");
        assertThat(Files.isRegularFile(worker))
                .as("Worker 源码必须存在: %s", worker).isTrue();

        String src = Files.readString(worker, StandardCharsets.UTF_8);
        Set<String> declared = new HashSet<>();
        Matcher m = Pattern.compile("@JobWorker\\s*\\(\\s*type\\s*=\\s*\"([^\"]+)\"").matcher(src);
        while (m.find()) declared.add(m.group(1));

        Set<String> required = new LinkedHashSet<>();
        for (Element t : elements("serviceTask")) {
            String type = jobTypeOf(t);
            if (type != null && !USER_TASK_JOB_TYPE.equals(type)) required.add(type);
        }

        assertThat(required).as("BPMN 中声明的 jobType").isNotEmpty();
        for (String type : required) {
            assertThat(declared)
                    .as("BPMN 的 serviceTask jobType「%s」缺少 @JobWorker 实现", type)
                    .contains(type);
        }

        // 反向：新增的两个 jobType 必须被 BPMN 使用，避免「写了 Worker 却没接进流程」
        assertThat(required).contains("symptom-mapping", "herb-modification");
    }

    // ============================================================
    // 5. 图形信息完整（UI 可直接打开）
    // ============================================================

    @Test
    @DisplayName("每个流节点都有 BPMNShape，每条连线都有 BPMNEdge（Camunda Modeler 可直接打开）")
    void diagramComplete() {
        Set<String> shaped = new HashSet<>();
        NodeList shapes = doc.getElementsByTagNameNS(DI_NS, "BPMNShape");
        for (int i = 0; i < shapes.getLength(); i++) {
            shaped.add(((Element) shapes.item(i)).getAttribute("bpmnElement"));
        }
        Set<String> edged = new HashSet<>();
        NodeList edges = doc.getElementsByTagNameNS(DI_NS, "BPMNEdge");
        for (int i = 0; i < edges.getLength(); i++) {
            edged.add(((Element) edges.item(i)).getAttribute("bpmnElement"));
        }

        for (String nodeId : flowNodeIds()) {
            assertThat(shaped).as("节点 %s 缺少 BPMNShape", nodeId).contains(nodeId);
        }
        for (String flowId : sequenceFlows().keySet()) {
            assertThat(edged).as("连线 %s 缺少 BPMNEdge", flowId).contains(flowId);
        }
    }

    // ============================================================
    // 6. 两份 BPMN 副本同步
    // ============================================================

    @Test
    @DisplayName("MiniBOxTBoxABox 与 TraditionalChineseMedicineOntologyBasedonReasoner 两份 BPMN 保持一致")
    void bpmnCopiesInSync() throws Exception {
        // bpmnPath = <repo>/ontology/Jingfang_Diagnosis.bpmn
        Path repoOntologyDir = bpmnPath.getParent();
        Path repoRoot = repoOntologyDir.getParent();
        assertThat(repoRoot).isNotNull();

        Path other = repoRoot.getParent()
                .resolve("TraditionalChineseMedicineOntologyBasedonReasoner")
                .resolve("ontology")
                .resolve("Jingfang_Diagnosis.bpmn");

        if (!Files.isRegularFile(other)) {
            // 兄弟目录不存在时跳过（不阻塞本模块测试）
            return;
        }
        assertThat(Files.readAllBytes(bpmnPath))
                .as("两份 BPMN 副本内容必须一致: %s", other)
                .isEqualTo(Files.readAllBytes(other));
    }
}
