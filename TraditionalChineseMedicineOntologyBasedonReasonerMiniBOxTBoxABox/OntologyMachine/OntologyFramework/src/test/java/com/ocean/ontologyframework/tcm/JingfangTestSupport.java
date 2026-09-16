package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 经方辨证测试框架 —— 与具体用例完全解耦。
 */
public final class JingfangTestSupport {

    public static final String NS = "http://www.tcm-classics.org/jingfang#";
    public static final String PROCESS_ID = "Process_Jingfang_Diagnosis";

    public static final Set<String> SIX_CHANNELS = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing"
    );

    public static final Map<String, List<String>> LJ_ANCHOR_SYMPTOMS = Map.of(
            "Taiyangbing",  List.of("Ehan"),
            "Yangmingbing", List.of("Kouke"),
            "Shaoyangbing", List.of("Wanglaihanre", "Xiongxiekuman"),
            "Taiyinbing",   List.of("Fuman"),
            "Shaoyinbing",  List.of("Danyumei"),
            "Jueyinbing",   List.of("Xiaoke", "Shouzuleng")
    );

    public static final Map<String, List<String>> LJ_ANCHOR_PULSES = Map.of(
            "Taiyangbing",  List.of("Fumai"),
            "Yangmingbing", List.of("Hongmai"),
            "Shaoyangbing", List.of("Xianmai"),
            "Taiyinbing",   List.of("Ruomai"),
            "Shaoyinbing",  List.of("Chenweimai"),
            "Jueyinbing",   List.of("Weiximai")
    );

    private static ZeebeClient client;
    private static volatile boolean initialized = false;

    private JingfangTestSupport() {}

    public static synchronized void ensureInitialized() {
        if (initialized) return;

        Yaml yaml = new Yaml();
        Map<String, Object> config;
        try (InputStream is = JingfangTestSupport.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml must exist on classpath").isNotNull();
            config = yaml.load(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load application.yml", e);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> ontology = (Map<String, Object>) config.get("ontology");
        String bpmnPath = (String) ontology.get("bpmn-path");
        assertThat(bpmnPath).as("ontology.bpmn-path must be configured").isNotBlank();

        @SuppressWarnings("unchecked")
        Map<String, Object> camunda = (Map<String, Object>) config.get("camunda");
        @SuppressWarnings("unchecked")
        Map<String, Object> clientCfg = (Map<String, Object>) camunda.get("client");
        String grpcAddress = (String) clientCfg.get("grpc-address");
        assertThat(grpcAddress).as("camunda.client.grpc-address must be configured").isNotBlank();

        boolean useTls = grpcAddress.startsWith("https://");
        String hostPort = grpcAddress.replaceFirst("^https?://", "");

        client = useTls
                ? ZeebeClient.newClientBuilder()
                .gatewayAddress(hostPort)
                .defaultRequestTimeout(Duration.ofSeconds(60)).build()
                : ZeebeClient.newClientBuilder()
                .gatewayAddress(hostPort)
                .usePlaintext()
                .defaultRequestTimeout(Duration.ofSeconds(60)).build();

        System.out.println("📂 Deploying BPMN from: " + bpmnPath);
        DeploymentEvent deployment = client.newDeployResourceCommand()
                .addResourceFile(bpmnPath).send().join();
        assertThat(deployment.getProcesses()).hasSize(1);
        System.out.println("✅ 流程已部署，key=" + deployment.getKey()
                + ", version=" + deployment.getProcesses().get(0).getVersion());

        initialized = true;
    }

    public static ProcessInstanceResult startProcessAndGetResult(Map<String, Object> variables) {
        ensureInitialized();
        return client.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(variables)
                .withResult()
                .requestTimeout(Duration.ofSeconds(120))
                .send()
                .join();
    }

    @SuppressWarnings("unchecked")
    public static void printResult(String caseName, ProcessInstanceResult result) {
        Map<String, Object> vars = result.getVariablesAsMap();
        Map<String, Object> bagang = (Map<String, Object>) vars.get("bagangResult");

        System.out.println("\n===== " + caseName + " =====");
        if (bagang != null) {
            Map<String, Object> cn = new LinkedHashMap<>();
            cn.put("表里", bagang.get("表里"));
            cn.put("寒热", bagang.get("寒热"));
            cn.put("虚实", bagang.get("虚实"));
            cn.put("阴阳", bagang.get("阴阳"));
            cn.put("bagangTypes", bagang.get("bagangTypesCn") != null
                    ? bagang.get("bagangTypesCn") : bagang.get("bagangTypes"));
            System.out.println("八纲：" + cn);
        } else {
            System.out.println("八纲：null");
        }

        System.out.println("六经：" + getChineseOrOriginal(vars, "sixChannel", "sixChannelCn"));
        System.out.println("方证：" + getChineseOrOriginal(vars, "fangzheng", "fangzhengCn"));
        System.out.println("推荐方剂：" + getChineseOrOriginal(vars, "finalFormula", "finalFormulaCn"));
        System.out.println("药物组成：" + getChineseListOrOriginal(vars, "herbs", "herbsCn"));
        System.out.println("六经列表：" + getChineseListOrOriginal(vars, "liujingTypes", "liujingTypesCn"));
        System.out.println("合病标记：" + vars.get("combinedDiseaseMark"));

        if (vars.get("candidateFangzhengs") != null) {
            System.out.println("候选方证："
                    + getChineseListOrOriginal(vars, "candidateFangzhengs", "candidateFangzhengsCn"));
            System.out.println("候选方证打分：" + vars.get("candidateScores"));
        }
        if (vars.get("jianJiaZhengs") != null) {
            System.out.println("兼夹证："
                    + getChineseListOrOriginal(vars, "jianJiaZhengs", "jianJiaZhengsCn"));
        }
        if (vars.get("addedHerb") != null) {
            System.out.println("加味药物："
                    + getChineseListOrOriginal(vars, "addedHerb", "addedHerbCn"));
        }
        if (vars.get("removedHerb") != null) {
            List<String> r = getChineseListOrOriginal(vars, "removedHerb", "removedHerbCn");
            if (!r.isEmpty()) System.out.println("减味药物：" + r);
        }
        if (vars.get("warnings") != null) {
            System.out.println("配伍禁忌警告：" + vars.get("warnings"));
        }
    }

    private static String getChineseOrOriginal(Map<String, Object> vars,
                                               String origKey, String cnKey) {
        Object cn = vars.get(cnKey);
        if (cn instanceof String s && !s.isEmpty()) return s;
        Object orig = vars.get(origKey);
        return orig != null ? orig.toString() : null;
    }

    private static List<String> getChineseListOrOriginal(Map<String, Object> vars,
                                                         String origKey, String cnKey) {
        Object cn = vars.get(cnKey);
        if (cn instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        Object orig = vars.get(origKey);
        if (orig instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    public static void assertBasicResult(ProcessInstanceResult result,
                                         String expectedSixChannel,
                                         String expectedFangzheng,
                                         String expectedFormula) {
        Map<String, Object> vars = result.getVariablesAsMap();

        if (expectedSixChannel != null) {
            List<String> lj = (List<String>) vars.get("liujingTypes");
            if (lj != null && lj.size() > 1) {
                assertThat((String) vars.get("sixChannel"))
                        .isEqualTo(vars.get("combinedDiseaseMark"));
                assertThat(lj).contains(expectedSixChannel);
            } else {
                assertThat(vars.get("sixChannel")).isEqualTo(expectedSixChannel);
            }
        }

        assertThat(vars.get("fangzheng")).isEqualTo(expectedFangzheng);
        assertThat(vars.get("finalFormula")).isEqualTo(expectedFormula);
    }

    @SuppressWarnings("unchecked")
    public static void assertBagang(ProcessInstanceResult result,
                                    List<String> expectedBiaoli,
                                    List<String> expectedXushi,
                                    List<String> expectedYinyang) {
        Map<String, Object> bagang = (Map<String, Object>)
                result.getVariablesAsMap().get("bagangResult");
        assertThat(bagang).isNotNull();

        if (expectedBiaoli != null) {
            assertThat((List<String>) bagang.get("表里"))
                    .containsExactlyInAnyOrderElementsOf(expectedBiaoli);
        }
        if (expectedXushi != null) {
            assertThat((List<String>) bagang.get("虚实"))
                    .containsExactlyInAnyOrderElementsOf(expectedXushi);
        }
        if (expectedYinyang != null) {
            assertThat((List<String>) bagang.get("阴阳"))
                    .containsExactlyInAnyOrderElementsOf(expectedYinyang);
        }
    }

    public static boolean isLiujing(String lj) {
        if (lj == null) return false;
        if (SIX_CHANNELS.contains(lj)) return true;
        return lj.endsWith("hebing") || lj.endsWith("Hebing");
    }

    public static List<String> parseIris(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(";"))
                .filter(x -> !x.isBlank())
                .map(x -> NS + x + "_instance")
                .collect(Collectors.toList());
    }

    public static void assertFangzheng(String name, String lj, String fz, String formula,
                                       String syms, String pulses) {
        assertFangzheng(name, lj, fz, formula, syms, pulses, "", "");
    }

    public static void assertFangzheng(String name, String lj, String fz, String formula,
                                       String syms, String pulses,
                                       String tongues, String fuzhengs) {
        List<String> symList = new ArrayList<>(parseIris(syms));
        List<String> pulseList = new ArrayList<>(parseIris(pulses));

        if (isLiujing(lj)) {
            for (var e : LJ_ANCHOR_SYMPTOMS.entrySet()) {
                if (lj.contains(e.getKey())) {
                    for (String s : e.getValue()) {
                        String iri = NS + s + "_instance";
                        if (!symList.contains(iri)) symList.add(iri);
                    }
                }
            }
            for (var e : LJ_ANCHOR_PULSES.entrySet()) {
                if (lj.contains(e.getKey())) {
                    for (String p : e.getValue()) {
                        String iri = NS + p + "_instance";
                        if (!pulseList.contains(iri)) pulseList.add(iri);
                    }
                }
            }
        }

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("symptomIris", symList);
        vars.put("pulseIris", pulseList);
        vars.put("tongueIris", parseIris(tongues));
        vars.put("fuzhengIris", parseIris(fuzhengs));

        ProcessInstanceResult result = startProcessAndGetResult(vars);
        printResult(name, result);

        String expectedSix = isLiujing(lj) ? lj : null;
        assertBasicResult(result, expectedSix, fz, NS + formula);
    }
}