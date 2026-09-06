package com.ocean.ontologyframework;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JingfangDiagnosisProcessTest {

    private static ZeebeClient zeebeClient;
    private static String bpmnPath;
    private static final String PROCESS_ID = "Process_Jingfang_Diagnosis";
    private static final String NS = "http://www.tcm-classics.org/jingfang#";

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void setUp() {
        Yaml yaml = new Yaml();
        try (InputStream is = JingfangDiagnosisProcessTest.class
                .getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml must exist on classpath").isNotNull();
            Map<String, Object> config = yaml.load(is);

            Map<String, Object> ontology = (Map<String, Object>) config.get("ontology");
            bpmnPath = (String) ontology.get("bpmn-path");
            assertThat(bpmnPath).as("ontology.bpmn-path must be configured").isNotBlank();

            Map<String, Object> camunda = (Map<String, Object>) config.get("camunda");
            Map<String, Object> client = (Map<String, Object>) camunda.get("client");
            String grpcAddress = (String) client.get("grpc-address");
            assertThat(grpcAddress).as("camunda.client.grpc-address must be configured").isNotBlank();

            boolean useTls = grpcAddress.startsWith("https://");
            String hostPort = grpcAddress.replaceFirst("^https?://", "");

            if (useTls) {
                zeebeClient = ZeebeClient.newClientBuilder()
                        .gatewayAddress(hostPort)
                        .defaultRequestTimeout(Duration.ofSeconds(60))
                        .build();
            } else {
                zeebeClient = ZeebeClient.newClientBuilder()
                        .gatewayAddress(hostPort)
                        .usePlaintext()
                        .defaultRequestTimeout(Duration.ofSeconds(60))
                        .build();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load application.yml", e);
        }

        System.out.println("📂 Deploying BPMN from: " + bpmnPath);
        DeploymentEvent deployment = zeebeClient.newDeployResourceCommand()
                .addResourceFile(bpmnPath)
                .send()
                .join();
        assertThat(deployment.getProcesses()).hasSize(1);
        System.out.println("✅ 流程已部署，key=" + deployment.getKey()
                + ", version=" + deployment.getProcesses().get(0).getVersion());
    }

    // ==================== 测试用例 ====================

    @Test
    void shouldDiagnoseGuizhiTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",
                        NS + "Efeng_instance",
                        NS + "Hanchu_instance"
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",
                        NS + "Huanmai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("桂枝汤证", result);
        assertBasicResult(result, "Taiyangbing", "GuizhiTangZheng", NS + "GuizhiTang");
        assertBagang(result, List.of("表证"), List.of("虚证"), List.of("阳证"));
    }

    @Test
    void shouldDiagnoseMahuangTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",
                        NS + "Ehan_instance",
                        NS + "Wuhan_instance"
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",
                        NS + "Jinmai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("麻黄汤证", result);
        assertBasicResult(result, "Taiyangbing", "MahuangTangZheng", NS + "MahuangTang");
        assertBagang(result, List.of("表证"), List.of("实证"), List.of("阳证"));
    }

    @Test
    void shouldDiagnoseBaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "DanreBuhan_instance",
                        NS + "Kouke_instance",
                        NS + "DaRe_instance",
                        NS + "DaKe_instance",
                        NS + "DaHan_instance"
                ),
                "pulseIris", List.of(NS + "Hongdamai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("白虎汤证", result);
        assertBasicResult(result, "Yangmingbing", "BaihuTangZheng", NS + "BaihuTang");
        assertBagang(result, List.of("里证"), null, List.of("阳证"));
    }

    @Test
    void shouldDiagnoseDaChengqiTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "DanreBuhan_instance",
                        NS + "Kouke_instance",
                        NS + "Chaore_instance",
                        NS + "Bianmi_instance",
                        NS + "Zhanwang_instance"
                ),
                "pulseIris", List.of(NS + "Chenshimai_instance"),
                "tongueIris", List.of(NS + "HuangzaoQiciTai_instance"),
                "fuzhengIris", List.of(NS + "FumanYingtong_instance")
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大承气汤证", result);
        assertBasicResult(result, "Yangmingbing", "DaChengqiTangZheng", NS + "DaChengqiTang");
        assertBagang(result, List.of("里证"), List.of("实证"), List.of("阳证"));
    }

    @Test
    void shouldDiagnoseXiaoChaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "WanglaiHanre_instance",
                        NS + "XiongxieKuman_instance",
                        NS + "HeiheiBuyuYinshi_instance",
                        NS + "XinfanXiou_instance",
                        NS + "Kouku_instance"
                ),
                "pulseIris", List.of(NS + "Xianmai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证", result);
        assertBasicResult(result, "Shaoyangbing", "XiaoChaihuTangZheng", NS + "XiaoChaihuTang");
        assertBagang(result, List.of("半表半里"), null, List.of("阳证"));
    }

    @Test
    void shouldDiagnoseDaChaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "WanglaiHanre_instance",
                        NS + "XiongxieKuman_instance",
                        NS + "XinxiaJi_instance",
                        NS + "OuBuzhi_instance",
                        NS + "YuyuWeifan_instance",
                        NS + "Bianmi_instance",
                        NS + "Kouku_instance"
                ),
                "pulseIris", List.of(NS + "Xianmai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of(NS + "XinxiaAnzhiMantong_instance")
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大柴胡汤证", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("sixChannel")).isEqualTo("Shaoyangbing");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Shaoyangbing", "Yangmingbing");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("少阳阳明合病");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);

        assertThat(vars.get("fangzheng")).isEqualTo("DaChaihuTangZheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "DaChaihuTang");

        assertBagang(result, List.of("里证", "半表半里"), List.of("实证"), List.of("阳证"));
    }

    @Test
    void shouldDiagnoseLizhongTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fuman_instance",
                        NS + "Outu_instance",
                        NS + "ShiBuXia_instance",
                        NS + "Xiali_instance",
                        NS + "ShiFuZiTong_instance",
                        NS + "BuKe_instance"
                ),
                "pulseIris", List.of(NS + "Chenruomai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("理中汤证", result);
        assertBasicResult(result, "Taiyinbing", "LizhongTangZheng", NS + "LizhongTang");
        assertBagang(result, List.of("里证"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    void shouldDiagnoseSiniTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fuman_instance",
                        NS + "Outu_instance",
                        NS + "ShiBuXia_instance",
                        NS + "Xiali_instance",
                        NS + "XialiQinggu_instance",
                        NS + "ShouzuJueleng_instance",
                        NS + "DanYuMei_instance"
                ),
                "pulseIris", List.of(NS + "Chenweimai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("四逆汤证", result);
        assertBasicResult(result, "Taiyinbing", "SiniTangZheng", NS + "SiniTang");
        assertBagang(result, List.of("里证"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    void shouldDiagnoseMahuangFuziXixinTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",
                        NS + "Ehan_instance",
                        NS + "Wuhan_instance",
                        NS + "DanYuMei_instance"
                ),
                "pulseIris", List.of(
                        NS + "Chenmai_instance",
                        NS + "Weiximai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("麻黄附子细辛汤证", result);
        assertBasicResult(result, "Shaoyinbing", "MahuangFuziXixinTangZheng", NS + "MahuangFuziXixinTang");
        assertBagang(result, List.of("表证"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    void shouldDiagnoseZhenwuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Ehan_instance",
                        NS + "DanYuMei_instance",
                        NS + "XinxiaJi2_instance",
                        NS + "Touxuan_instance",
                        NS + "ShenShunDong_instance",
                        NS + "Futong_instance",
                        NS + "XiaobianBuli_instance",
                        NS + "SizhiChenzhongTengtong_instance",
                        NS + "Xiali_instance",
                        NS + "ShouzuJueleng_instance"
                ),
                "pulseIris", List.of(NS + "Weiximai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("真武汤证", result);
        assertBasicResult(result, "Shaoyinbing", "ZhenwuTangZheng", NS + "ZhenwuTang");
        assertBagang(result, List.of("表证", "里证"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    void shouldDiagnoseWumeiWanPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Xiaoke_instance",
                        NS + "QiShangZhuangXin_instance",
                        NS + "XinzhongTengre_instance",
                        NS + "JiErBuyuShi_instance",
                        NS + "ShiZeTuHui_instance",
                        NS + "ShouzuJueleng_instance",
                        NS + "Kouku_instance"
                ),
                "pulseIris", List.of(NS + "Weiximai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("乌梅丸证", result);
        assertBasicResult(result, "Jueyinbing", "WumeiWanZheng", NS + "WumeiWan");
        assertBagang(result, List.of("半表半里"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    void shouldDiagnoseChaihuGuizhiGanjiangTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "WanglaiHanre_instance",
                        NS + "XiongxieManWeijie_instance",
                        NS + "XiaobianBuli_instance",
                        NS + "KeErBuOu_instance",
                        NS + "DanTouHanchu_instance",
                        NS + "Xinfan_instance",
                        NS + "ShouzuJueleng_instance"
                ),
                "pulseIris", List.of(NS + "Weiximai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("柴胡桂枝干姜汤证", result);
        assertBasicResult(result, "Jueyinbing", "ChaihuGuizhiGanjiangTangZheng", NS + "ChaihuGuizhiGanjiangTang");
        assertBagang(result, List.of("半表半里"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    void shouldDetectTaiyangShaoyangHebing() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",
                        NS + "Ehan_instance",
                        NS + "WanglaiHanre_instance",
                        NS + "XiongxieKuman_instance",
                        NS + "Kouku_instance"
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",
                        NS + "Xianmai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("太阳少阳合病", result);  // 增加输出，保持一致

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太阳少阳合病");
        assertThat(vars.get("sixChannel")).isEqualTo("Shaoyangbing");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyangbing");
    }

    @Test
    void shouldDiagnoseSanyangHebingChaihuBaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",
                        NS + "Ehan_instance",
                        NS + "Wuhan_instance",
                        NS + "Kouke_instance",
                        NS + "DaHan_instance",
                        NS + "DanreBuhan_instance",
                        NS + "WanglaiHanre_instance",
                        NS + "XiongxieKuman_instance",
                        NS + "Kouku_instance"
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",
                        NS + "Hongdamai_instance",
                        NS + "Xianmai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("柴胡白虎汤证（三阳合病）", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("三阳合病");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Yangmingbing", "Shaoyangbing");
        assertThat(vars.get("fangzheng")).isEqualTo("ChaihuBaihuTangZheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "ChaihuBaihuTang");

        // 复合证候，八纲只检查阴阳包含阳证
        assertBagang(result, null, null, List.of("阳证"));
    }

    @Test
    void shouldDetectTaiShaoLiangGan() {
        // 太少两感：太阳表证 + 少阴里虚寒，匹配麻黄附子细辛汤证
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",
                        NS + "Ehan_instance",
                        NS + "Wuhan_instance",
                        NS + "DanYuMei_instance"
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",
                        NS + "Weiximai_instance",
                        NS + "Chenmai_instance"    // 沉脉，关键匹配
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("太少两感（麻黄附子细辛汤证）", result);  // 完整输出

        Map<String, Object> vars = result.getVariablesAsMap();

        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太少两感");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyinbing");

        assertThat(vars.get("fangzheng")).isEqualTo("MahuangFuziXixinTangZheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "MahuangFuziXixinTang");
    }

    // ==================== 辅助方法 ====================

    private ProcessInstanceResult startProcessAndGetResult(Map<String, Object> variables) {
        return zeebeClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(variables)
                .withResult()
                .requestTimeout(Duration.ofSeconds(120))
                .send()
                .join();
    }

    private void printResult(String caseName, ProcessInstanceResult result) {
        Map<String, Object> vars = result.getVariablesAsMap();
        Map<String, Object> bagang = (Map<String, Object>) vars.get("bagangResult");
        System.out.println("\n===== " + caseName + " =====");
        System.out.println("八纲：" + bagang);
        System.out.println("六经：" + vars.get("sixChannel"));
        System.out.println("方证：" + vars.get("fangzheng"));
        System.out.println("推荐方剂：" + vars.get("finalFormula"));
        System.out.println("药物组成：" + vars.get("herbs"));
        System.out.println("六经列表：" + vars.get("liujingTypes"));
        System.out.println("合病标记：" + vars.get("combinedDiseaseMark"));
    }

    private void assertBasicResult(ProcessInstanceResult result,
                                   String expectedSixChannel,
                                   String expectedFangzheng,
                                   String expectedFormula) {
        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("sixChannel")).isEqualTo(expectedSixChannel);
        assertThat(vars.get("fangzheng")).isEqualTo(expectedFangzheng);
        assertThat(vars.get("finalFormula")).isEqualTo(expectedFormula);
    }

    /**
     * 断言八纲结果（精确匹配，顺序无关）
     */
    private void assertBagang(ProcessInstanceResult result,
                              List<String> expectedBiaoli,
                              List<String> expectedXushi,
                              List<String> expectedYinyang) {
        Map<String, Object> vars = result.getVariablesAsMap();
        Map<String, Object> bagang = (Map<String, Object>) vars.get("bagangResult");
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
}