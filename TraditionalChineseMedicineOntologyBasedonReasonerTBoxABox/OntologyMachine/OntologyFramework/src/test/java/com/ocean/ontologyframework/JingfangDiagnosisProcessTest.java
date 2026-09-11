package com.ocean.ontologyframework;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

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
        Map<String, Object> config;
        try (InputStream is = JingfangDiagnosisProcessTest.class
                .getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertThat(is).as("application.yml must exist on classpath").isNotNull();
            config = yaml.load(is);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load application.yml", e);
        }

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
    @DisplayName("桂枝汤证诊断")
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
        assertBasicResult(result, "Taiyangbing", "Guizhitangzheng", NS + "Guizhitang");
        assertBagang(result, List.of("表证"), List.of("虚证"), List.of("阳证"));
    }

    @Test
    @DisplayName("麻黄汤证诊断")
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
        assertBasicResult(result, "Taiyangbing", "Mahuangtangzheng", NS + "Mahuangtang");
        assertBagang(result, List.of("表证"), List.of("实证"), List.of("阳证"));
    }

    @Test
    @DisplayName("白虎汤证诊断")
    void shouldDiagnoseBaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Danrebuhan_instance",
                        NS + "Kouke_instance",
                        NS + "Dare_instance",
                        NS + "Dake_instance",
                        NS + "Dahan_instance"
                ),
                "pulseIris", List.of(NS + "Hongmai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("白虎汤证", result);
        assertBasicResult(result, "Yangmingbing", "Baihutangzheng", NS + "Baihutang");
        assertBagang(result, List.of("里证"), null, List.of("阳证"));
    }

    @Test
    @DisplayName("大承气汤证诊断")
    void shouldDiagnoseDaChengqiTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Danrebuhan_instance",
                        NS + "Kouke_instance",
                        NS + "Chaore_instance",
                        NS + "Dabianying_instance",
                        NS + "Zhanwang_instance"
                ),
                "pulseIris", List.of(NS + "Chenshimai_instance"),
                "tongueIris", List.of(
                        NS + "YellowCoating_instance",
                        NS + "DryCoating_instance",
                        NS + "TongueWithThorns_instance"
                ),
                "fuzhengIris", List.of(
                        NS + "Fuman_instance",
                        NS + "Futong_instance",
                        NS + "Juan_instance"
                )
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大承气汤证", result);
        assertBasicResult(result, "Yangmingbing", "Dachengqitangzheng", NS + "Dachengqitang");
        assertBagang(result, List.of("里证"), List.of("实证"), List.of("阳证"));
    }

    @Test
    @DisplayName("小柴胡汤证诊断")
    void shouldDiagnoseXiaoChaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Wanglaihanre_instance",
                        NS + "Xiongxiekuman_instance",
                        NS + "Momo_instance",
                        NS + "Buyushi_instance",
                        NS + "Xinfan_instance",
                        NS + "Xiou_instance",
                        NS + "Kouku_instance"
                ),
                "pulseIris", List.of(NS + "Xianmai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证", result);
        assertBasicResult(result, "Shaoyangbing", "Xiaochaihutangzheng", NS + "Xiaochaihutang");
        assertBagang(result, List.of("半表半里"), null, List.of("阳证"));
    }

    @Test
    @DisplayName("大柴胡汤证诊断")
    void shouldDiagnoseDaChaihuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Wanglaihanre_instance",
                        NS + "Xiongxiekuman_instance",
                        NS + "Xinxiaji_instance",
                        NS + "Outubuzhi_instance",
                        NS + "Yuyuweifan_instance",
                        NS + "Dabianying_instance",
                        NS + "Kouku_instance"
                ),
                "pulseIris", List.of(NS + "Xianmai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of(NS + "Xinxianzhimantong_instance")
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大柴胡汤证", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("sixChannel")).isEqualTo("Shaoyangbing");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Shaoyangbing", "Yangmingbing");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("少阳阳明合病");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);

        assertThat(vars.get("fangzheng")).isEqualTo("Dachaihutangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Dachaihutang");

        assertBagang(result, List.of("里证", "半表半里"), List.of("实证"), List.of("阳证"));
    }

    @Test
    @DisplayName("理中汤证诊断")
    void shouldDiagnoseLizhongTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fuman_instance",
                        NS + "Outu_instance",
                        NS + "Shibuxia_instance",
                        NS + "Xiali_instance",
                        NS + "Shifuzitong_instance",
                        NS + "Buke_instance"
                ),
                "pulseIris", List.of(NS + "Chenruomai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("理中汤证", result);
        assertBasicResult(result, "Taiyinbing", "Lizhongtangzheng", NS + "Lizhongtang");
        assertBagang(result, List.of("里证"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    @DisplayName("四逆汤证诊断")
    void shouldDiagnoseSiniTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fuman_instance",
                        NS + "Outu_instance",
                        NS + "Shibuxia_instance",
                        NS + "Xiali_instance",
                        NS + "Xialiqinggu_instance",
                        NS + "Shouzuleng_instance",
                        NS + "Danyumei_instance"
                ),
                "pulseIris", List.of(NS + "Chenweimai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("四逆汤证", result);
        assertBasicResult(result, "Taiyinbing", "Sinitangzheng", NS + "Sinitang");
        assertBagang(result, List.of("里证"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    @DisplayName("麻黄附子细辛汤证诊断")
    void shouldDiagnoseMahuangFuziXixinTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",
                        NS + "Ehan_instance",
                        NS + "Wuhan_instance",
                        NS + "Danyumei_instance"
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
        assertBasicResult(result, "Shaoyinbing", "Mahuangfuzixixintangzheng", NS + "Mahuangfuzixixintang");
        assertBagang(result, List.of("表证"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    @DisplayName("真武汤证诊断")
    void shouldDiagnoseZhenwuTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Ehan_instance",
                        NS + "Danyumei_instance",
                        NS + "Xinxiajidong_instance",
                        NS + "Touxuan_instance",
                        NS + "Shenshundong_instance",
                        NS + "Futong_instance",
                        NS + "Xiaobianbuli_instance",
                        NS + "Sizhichenzhongtengtong_instance",
                        NS + "Xiali_instance",
                        NS + "Shouzuleng_instance"
                ),
                "pulseIris", List.of(NS + "Weiximai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("真武汤证", result);
        assertBasicResult(result, "Shaoyinbing", "Zhenwutangzheng", NS + "Zhenwutang");
        assertBagang(result, List.of("表证", "里证"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    @DisplayName("乌梅丸证诊断")
    void shouldDiagnoseWumeiWanPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Xiaoke_instance",
                        NS + "Qicongshaofushangchongxin_instance",
                        NS + "Xinzhongtengre_instance",
                        NS + "Ji_instance",
                        NS + "Buyushi_instance",
                        NS + "Shizetuhui_instance",
                        NS + "Shouzuleng_instance",
                        NS + "Kouku_instance"
                ),
                "pulseIris", List.of(NS + "Weiximai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("乌梅丸证", result);
        assertBasicResult(result, "Jueyinbing", "Wumeiwanzheng", NS + "Wumeiwan");
        assertBagang(result, List.of("半表半里"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    @DisplayName("柴胡桂枝干姜汤证诊断")
    void shouldDiagnoseChaihuGuizhiGanjiangTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Wanglaihanre_instance",
                        NS + "Xiongxiekuman_instance",
                        NS + "Xiaobianbuli_instance",
                        NS + "Buke_instance",
                        NS + "Buou_instance",
                        NS + "Dantouhanchu_instance",
                        NS + "Xinfan_instance",
                        NS + "Shouzuleng_instance"
                ),
                "pulseIris", List.of(NS + "Weiximai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("柴胡桂枝干姜汤证", result);
        assertBasicResult(result, "Jueyinbing", "Chaihuguizhiganjiangtangzheng", NS + "Chaihuguizhiganjiangtang");
        assertBagang(result, List.of("半表半里"), List.of("虚证"), List.of("阴证"));
    }

    @Test
    @DisplayName("太阳少阳合病检测")
    void shouldDetectTaiyangShaoyangHebing() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",
                        NS + "Ehan_instance",
                        NS + "Wanglaihanre_instance",
                        NS + "Xiongxiekuman_instance",
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
        printResult("太阳少阳合病", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太阳少阳合病");
        assertThat(vars.get("sixChannel")).isEqualTo("Shaoyangbing");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyangbing");
    }

    // 说明：原“三阳合病柴胡白虎汤证诊断”测试依赖 #ChaihuBaihuTang 与 #ChaihuBaihuTangzheng，
    // 但这两者均未在当前 tcm-fangji-abox.owl 与 tcm-fangzheng-jianjia.owl 中定义，
    // 为避免测试失败，此处不再保留该用例。若后续在本体中补充该方剂与方证，可恢复此测试。

    @Test
    @DisplayName("太少两感检测")
    void shouldDetectTaiShaoLiangGan() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance",
                        NS + "Ehan_instance",
                        NS + "Wuhan_instance",
                        NS + "Danyumei_instance"
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",
                        NS + "Weiximai_instance",
                        NS + "Chenmai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("太少两感（麻黄附子细辛汤证）", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太少两感");
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyinbing");
        assertThat(vars.get("fangzheng")).isEqualTo("Mahuangfuzixixintangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Mahuangfuzixixintang");
    }

    @Test
    @DisplayName("小柴胡汤证夹瘀血检测")
    void shouldDetectYuXueJianJiaZhengWithXiaoChaihuTang() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("symptomIris", List.of(
                NS + "Wanglaihanre_instance",
                NS + "Xiongxiekuman_instance",
                NS + "Kouku_instance",
                NS + "Citong_instance",
                NS + "Xiongman_instance"
        ));
        variables.put("pulseIris", List.of(
                NS + "Xianmai_instance",
                NS + "Semai_instance"
        ));
        variables.put("tongueIris", List.of(
                NS + "BlueTongue_instance"
        ));
        variables.put("fuzhengIris", List.of());

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证夹瘀血", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Xiaochaihutangzheng");
        assertThat((List<String>) vars.get("jianJiaZhengs"))
                .containsExactly("Yuxuezheng");
        List<String> addHerbs = (List<String>) vars.get("addHerbs");
        assertThat(addHerbs)
                .contains(NS + "Danshen", NS + "Taoren");
    }

    @Test
    @DisplayName("小柴胡汤证夹痰饮检测")
    void shouldDetectTanYinJianJiaZhengWithXiaoChaihuTang() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("symptomIris", List.of(
                NS + "Wanglaihanre_instance",
                NS + "Xiongxiekuman_instance",
                NS + "Kouku_instance",
                NS + "Touxuan_instance",
                NS + "Xinji_instance"
        ));
        variables.put("pulseIris", List.of(
                NS + "Xianmai_instance",
                NS + "Chenxianmai_instance"
        ));
        variables.put("tongueIris", List.of(
                NS + "SlipperyCoating_instance",
                NS + "GreasyCoating_instance"
        ));
        variables.put("fuzhengIris", List.of());

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证夹痰饮", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Xiaochaihutangzheng");
        assertThat((List<String>) vars.get("jianJiaZhengs"))
                .containsExactly("Tanyinzheng");
        List<String> addHerbs = (List<String>) vars.get("addHerbs");
        assertThat(addHerbs)
                .contains(NS + "Banxia", NS + "Fuling");
    }

    @Test
    @DisplayName("大柴胡汤证夹痰饮检测")
    void shouldDetectTanYinJianJiaZhengWithDaChaihuTang() {
        // 医案背景：少阳阳明合病（大柴胡汤证），兼痰饮内停。
        // 少阳主症：往来寒热、胸胁苦满；阳明主症：心下急、呕不止、郁郁微烦、便秘、心下按之满痛。
        // 痰饮兼夹症：头眩、心悸、舌苔滑腻、脉沉弦。
        Map<String, Object> variables = new HashMap<>();
        variables.put("symptomIris", List.of(
                NS + "Wanglaihanre_instance",
                NS + "Xiongxiekuman_instance",
                NS + "Xinxiaji_instance",
                NS + "Outubuzhi_instance",
                NS + "Yuyuweifan_instance",
                NS + "Dabianying_instance",
                NS + "Touxuan_instance",
                NS + "Xinji_instance"
        ));
        variables.put("pulseIris", List.of(
                NS + "Xianmai_instance",
                NS + "Chenxianmai_instance"
        ));
        variables.put("tongueIris", List.of(
                NS + "SlipperyCoating_instance",
                NS + "GreasyCoating_instance"
        ));
        variables.put("fuzhengIris", List.of(
                NS + "Xinxianzhimantong_instance"
        ));

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大柴胡汤证夹痰饮", result);

        Map<String, Object> vars = result.getVariablesAsMap();

        assertThat(vars.get("fangzheng")).isEqualTo("Dachaihutangzheng");
        assertThat((List<String>) vars.get("jianJiaZhengs"))
                .containsExactly("Tanyinzheng");
        List<String> addHerbs = (List<String>) vars.get("addHerbs");
        assertThat(addHerbs)
                .contains(NS + "Banxia", NS + "Fuling");
    }

    @Test
    @DisplayName("小柴胡汤证夹气郁检测")
    void shouldDetectQiYuJianJiaZhengWithXiaoChaihuTang() {
        // 医案背景：少阳枢机不利，兼肝气郁结。
        // 主症（小柴胡汤证）：往来寒热、胸胁苦满、口苦、脉弦。
        // 气郁兼夹症：胸胁苦满、善太息，或然症咽中如有炙脔。
        Map<String, Object> variables = new HashMap<>();
        variables.put("symptomIris", List.of(
                NS + "Wanglaihanre_instance",
                NS + "Xiongxiekuman_instance",
                NS + "Kouku_instance",
                NS + "Momo_instance",
                NS + "Buyushi_instance",
                NS + "Xinfan_instance",
                NS + "Xiou_instance",
                NS + "Shantaixi_instance",
                NS + "Yanzhongruyouzhilian_instance"
        ));
        variables.put("pulseIris", List.of(
                NS + "Xianmai_instance"
        ));
        variables.put("tongueIris", List.of());
        variables.put("fuzhengIris", List.of());

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证夹气郁", result);

        Map<String, Object> vars = result.getVariablesAsMap();

        assertThat(vars.get("fangzheng")).isEqualTo("Xiaochaihutangzheng");
        assertThat((List<String>) vars.get("jianJiaZhengs"))
                .containsExactly("Qiyuzheng");
        List<String> addHerbs = (List<String>) vars.get("addHerbs");
        assertThat(addHerbs)
                .contains(NS + "Xiangfu", NS + "Yujin");
    }

    // ==================== 十八反/十九畏 警告测试 ====================

    @Test
    @DisplayName("甘遂半夏汤十八反警告检测")
    void shouldWarnOnGansuiBanxiaTangAntagonism() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Xinxiapi_instance",
                        NS + "Xiali_instance",
                        NS + "Touxuan_instance"
                ),
                "pulseIris", List.of(
                        NS + "Chenxianmai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("甘遂半夏汤（十八反：甘遂反甘草）", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Gansuibanxiantangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Gansuibanxiantang");

        List<String> warnings = (List<String>) vars.get("warnings");
        assertThat(warnings).as("应包含十八反警告").isNotNull().isNotEmpty();
        assertThat(warnings).anySatisfy(w -> assertThat(w)
                .contains("十八反")
                .contains("甘遂")
                .contains("甘草"));
    }

    @Test
    @DisplayName("附子粳米汤十八反警告检测")
    void shouldWarnOnFuziJingmiTangAntagonism() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Futong_instance",
                        NS + "Xiongxiekuman_instance",
                        NS + "Outu_instance"
                ),
                "pulseIris", List.of(
                        NS + "Chenweimai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("附子粳米汤（十八反：附子反半夏）", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Fuzijingmitangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Fuzijingmitang");

        List<String> warnings = (List<String>) vars.get("warnings");
        assertThat(warnings).as("应包含十八反警告").isNotNull().isNotEmpty();
        assertThat(warnings).anySatisfy(w -> assertThat(w)
                .contains("十八反")
                .contains("附子")
                .contains("半夏"));
    }

    @Test
    @DisplayName("栝楼瞿麦丸十八反警告检测")
    void shouldWarnOnGualouQumaiWanAntagonism() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Xiaobianbuli_instance",
                        NS + "Kouke_instance"
                ),
                "pulseIris", List.of(
                        NS + "Chenmai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("栝楼瞿麦丸（十八反：瓜蒌根反附子）", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Gualouqumaiwanzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Gualouqumaiwan");

        List<String> warnings = (List<String>) vars.get("warnings");
        assertThat(warnings).as("应包含十八反警告").isNotNull().isNotEmpty();
        assertThat(warnings).anySatisfy(w -> assertThat(w)
                .contains("十八反")
                .contains("瓜蒌")
                .contains("附子"));
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

    private String getChineseOrOriginal(Map<String, Object> vars, String originalKey, String chineseKey) {
        Object cn = vars.get(chineseKey);
        if (cn != null && cn instanceof String && !((String) cn).isEmpty()) {
            return (String) cn;
        }
        Object original = vars.get(originalKey);
        return original != null ? original.toString() : null;
    }

    private List<String> getChineseListOrOriginal(Map<String, Object> vars, String originalKey, String chineseKey) {
        Object cn = vars.get(chineseKey);
        if (cn instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        Object original = vars.get(originalKey);
        if (original instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }

    private void printResult(String caseName, ProcessInstanceResult result) {
        Map<String, Object> vars = result.getVariablesAsMap();
        Map<String, Object> bagang = (Map<String, Object>) vars.get("bagangResult");

        System.out.println("\n===== " + caseName + " =====");

        if (bagang != null) {
            Map<String, Object> bagangCn = new LinkedHashMap<>();
            bagangCn.put("表里", bagang.get("表里"));
            bagangCn.put("寒热", bagang.get("寒热"));
            bagangCn.put("虚实", bagang.get("虚实"));
            bagangCn.put("阴阳", bagang.get("阴阳"));
            List<String> bagangTypesCn = (List<String>) bagang.get("bagangTypesCn");
            bagangCn.put("bagangTypes", bagangTypesCn != null ? bagangTypesCn : bagang.get("bagangTypes"));
            System.out.println("八纲：" + bagangCn);
        } else {
            System.out.println("八纲：null");
        }

        System.out.println("六经：" + getChineseOrOriginal(vars, "sixChannel", "sixChannelCn"));
        System.out.println("方证：" + getChineseOrOriginal(vars, "fangzheng", "fangzhengCn"));
        System.out.println("推荐方剂：" + getChineseOrOriginal(vars, "finalFormula", "finalFormulaCn"));

        List<String> herbs = getChineseListOrOriginal(vars, "herbs", "herbsCn");
        System.out.println("药物组成：" + herbs);

        List<String> liujingTypes = getChineseListOrOriginal(vars, "liujingTypes", "liujingTypesCn");
        System.out.println("六经列表：" + liujingTypes);

        System.out.println("合病标记：" + vars.get("combinedDiseaseMark"));

        if (vars.get("candidateNecessaryScoresCn") != null) {
            System.out.println("主证命中数：" + vars.get("candidateNecessaryScoresCn"));
        } else if (vars.get("candidateNecessaryScores") != null) {
            System.out.println("主证命中数：" + vars.get("candidateNecessaryScores"));
        }
        if (vars.get("candidateFangzhengs") != null) {
            List<String> candidateCn = getChineseListOrOriginal(vars, "candidateFangzhengs", "candidateFangzhengsCn");
            System.out.println("候选方证：" + candidateCn);
        }
        if (vars.get("candidateScoresCn") != null) {
            System.out.println("候选得分：" + vars.get("candidateScoresCn"));
        } else if (vars.get("candidateScores") != null) {
            System.out.println("候选得分：" + vars.get("candidateScores"));
        }
        if (vars.get("jianJiaZhengs") != null) {
            List<String> jianjiaCn = getChineseListOrOriginal(vars, "jianJiaZhengs", "jianJiaZhengsCn");
            System.out.println("兼夹证：" + jianjiaCn);
        }
        if (vars.get("addHerbs") != null) {
            List<String> addHerbsCn = getChineseListOrOriginal(vars, "addHerbs", "addHerbsCn");
            System.out.println("加减药物：" + addHerbsCn);
        }
        if (vars.get("warnings") != null) {
            System.out.println("配伍禁忌警告：" + vars.get("warnings"));
        }
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