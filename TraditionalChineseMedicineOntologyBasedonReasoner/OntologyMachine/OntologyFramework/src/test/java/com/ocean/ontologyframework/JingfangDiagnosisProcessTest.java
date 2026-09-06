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

            // 根据协议前缀判断是否使用 TLS
            boolean useTls = grpcAddress.startsWith("https://");
            String hostPort = grpcAddress.replaceFirst("^https?://", "");

            // 直接链式构建客户端，不使用中间变量
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

    // ==================== 测试用例（保持不变） ====================

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
        assertBagang(result, "表证", "虚证", "阳证");
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
        assertBagang(result, "表证", "实证", "阳证");
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
        assertBagang(result, "里证", null, "阳证");
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
        assertBagang(result, "里证", "实证", "阳证");
    }

    @Test
    void shouldDiagnoseXiaoChaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "WanglaiHanre_instance",
                        NS + "XiongxieKuman_instance",
                        NS + "HeiheiBuyuYinshi_instance",
                        NS + "XinfanXiou_instance",
                        NS + "Kouku_instance"      // 少阳提纲证，帮助推出阳证
                ),
                "pulseIris", List.of(NS + "Xianmai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证", result);
        assertBasicResult(result, "Shaoyangbing", "XiaoChaihuTangZheng", NS + "XiaoChaihuTang");
        assertBagang(result, "半表半里", null, "阳证");
    }

    @Test
    void shouldDiagnoseDaChaihuTangPattern() {
        // 输入四诊信息：同时具备少阳病与阳明病特征
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

        // 六经输出：第一个单经病为 Shaoyangbing，列表包含两个单经病
        assertThat(vars.get("sixChannel")).isEqualTo("Shaoyangbing");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Shaoyangbing", "Yangmingbing");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("少阳阳明合病");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);

        // 方证与方剂
        assertThat(vars.get("fangzheng")).isEqualTo("DaChaihuTangZheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "DaChaihuTang");

        // 八纲断言
        assertBagang(result, "半表半里", "实证", "阳证");
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
                        NS + "BuKe_instance"          // 自利不渴，太阴寒证依据
                ),
                "pulseIris", List.of(NS + "Chenruomai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("理中汤证", result);
        assertBasicResult(result, "Taiyinbing", "LizhongTangZheng", NS + "LizhongTang");
        assertBagang(result, "里证", "虚证", "阴证");
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
        assertBagang(result, "里证", "虚证", "阴证");
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
        // 修改期望六经为 Shaoyinbing
        assertBasicResult(result, "Shaoyinbing", "MahuangFuziXixinTangZheng", NS + "MahuangFuziXixinTang");
        assertBagang(result, "表证", "虚证", "阴证");
    }

    @Test
    void shouldDiagnoseZhenwuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Ehan_instance",                     // 恶寒，满足表证
                        NS + "DanYuMei_instance",                 // 但欲寐
                        NS + "XinxiaJi2_instance",                // 心下悸
                        NS + "Touxuan_instance",                  // 头眩
                        NS + "ShenShunDong_instance",             // 身瞤动
                        NS + "Futong_instance",                   // 腹痛
                        NS + "XiaobianBuli_instance",             // 小便不利
                        NS + "SizhiChenzhongTengtong_instance",   // 四肢沉重疼痛
                        NS + "Xiali_instance",                    // 下利
                        NS + "ShouzuJueleng_instance"             // 手足厥冷，配合微细脉推出寒证
                ),
                "pulseIris", List.of(
                        NS + "Weiximai_instance"                  // 微细脉
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("真武汤证", result);
        assertBasicResult(result, "Shaoyinbing", "ZhenwuTangZheng", NS + "ZhenwuTang");
        assertBagang(result, "表证", "虚证", "阴证");
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
                "pulseIris", List.of(
                        NS + "Weiximai_instance"   // 微细脉，推出虚证和寒证
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("乌梅丸证", result);
        assertBasicResult(result, "Jueyinbing", "WumeiWanZheng", NS + "WumeiWan");
        assertBagang(result, "半表半里", "虚证", "阴证");
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
                        NS + "ShouzuJueleng_instance"          // 手足厥冷，促成寒证
                ),
                "pulseIris", List.of(
                        NS + "Weiximai_instance"               // 微细脉，促成虚证和寒证
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("柴胡桂枝干姜汤证", result);
        assertBasicResult(result, "Jueyinbing", "ChaihuGuizhiGanjiangTangZheng", NS + "ChaihuGuizhiGanjiangTang");
        assertBagang(result, "半表半里", "虚证", "阴证");
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
        Map<String, Object> vars = result.getVariablesAsMap();

        // 合病标记
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太阳少阳合病");

        // sixChannel 应为 Shaoyangbing（按字母序）
        assertThat(vars.get("sixChannel")).isEqualTo("Shaoyangbing");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyangbing");
    }

    @Test
    void shouldDiagnoseSanyangHebingChaihuBaihuTangPattern() {
        // 三阳合病典型症状：太阳表证（发热、恶寒、无汗、浮脉），阳明里热（口渴、汗出、洪大脉），少阳半表半里（往来寒热、胸胁苦满、口苦、弦脉）
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",          // 太阳发热
                        NS + "Ehan_instance",          // 太阳恶寒
                        NS + "Wuhan_instance",         // 太阳无汗（表实证）
                        NS + "Kouke_instance",         // 阳明口渴
                        NS + "DaHan_instance",         // 阳明大汗
                        NS + "DanreBuhan_instance",    // 阳明但热不寒
                        NS + "WanglaiHanre_instance",  // 少阳往来寒热
                        NS + "XiongxieKuman_instance", // 少阳胸胁苦满
                        NS + "Kouku_instance"          // 少阳口苦
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",         // 太阳浮脉
                        NS + "Hongdamai_instance",     // 阳明洪大脉
                        NS + "Xianmai_instance"        // 少阳弦脉
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
        // 八纲断言：表里=表证（因有恶寒），寒热=热证（因有口渴、大汗等），虚实=实证（因无汗、脉实等），阴阳=阳证
        assertBagang(result, "表证", "实证", "阳证");
    }

    @Test
    void shouldDetectTaiShaoLiangGan() {
        // 同时具备太阳与少阴特征：太阳表证（发热、恶寒、无汗、浮脉），少阴（但欲寐、微细脉）
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",          // 太阳发热
                        NS + "Ehan_instance",          // 恶寒（太阳表证）
                        NS + "Wuhan_instance",         // 无汗（协助寒证）
                        NS + "DanYuMei_instance"       // 但欲寐（少阴）
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",         // 太阳浮脉
                        NS + "Weiximai_instance"       // 少阴微细脉
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        Map<String, Object> vars = result.getVariablesAsMap();

        System.out.println("===== 太少两感 =====");
        System.out.println("六经列表: " + vars.get("liujingTypes"));
        System.out.println("合病标记: " + vars.get("combinedDiseaseMark"));

        // 断言
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太少两感");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyinbing");
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

    private void assertBagang(ProcessInstanceResult result,
                              String expectedBiaoli,
                              String expectedXushi,
                              String expectedYinyang) {
        Map<String, Object> vars = result.getVariablesAsMap();
        Map<String, Object> bagang = (Map<String, Object>) vars.get("bagangResult");
        assertThat(bagang).isNotNull();
        if (expectedBiaoli != null) {
            assertThat(bagang.get("表里")).isEqualTo(expectedBiaoli);
        }
        if (expectedXushi != null) {
            assertThat(bagang.get("虚实")).isEqualTo(expectedXushi);
        }
        if (expectedYinyang != null) {
            assertThat(bagang.get("阴阳")).isEqualTo(expectedYinyang);
        }
    }
}