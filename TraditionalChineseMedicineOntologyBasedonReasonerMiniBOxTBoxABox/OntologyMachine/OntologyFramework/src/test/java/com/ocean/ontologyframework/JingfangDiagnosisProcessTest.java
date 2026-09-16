package com.ocean.ontologyframework;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(StopOnTimeoutExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 100, unit = TimeUnit.SECONDS)
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

    // ==========================================================================
    // 【家族 1】桂枝汤类  @Order(1..30)
    // ==========================================================================

    @Test @Order(1)
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
        assertBagang(result, List.of("表证"), null, List.of("阳证"));
    }

    @Test @Order(2) @DisplayName("桂枝加葛根汤证")
    void t_guizhijiagegentang() { assertFangzheng("桂枝加葛根汤证", "Taiyangbing", "Guizhijiagegentangzheng", "Guizhijiagegentang",
            "Xiangbeiqiangjiji;Hanchu;Efeng", "Fumai;Huanmai"); }

    @Test @Order(3) @DisplayName("桂枝加厚朴杏子汤证")
    void t_guizhijiahoupoxingrentang() { assertFangzheng("桂枝加厚朴杏子汤证", "Taiyangbing", "Guizhijiahoupoxingzitangzheng", "Guizhijiahoupoxingzitang",
            "Chuan;Hanchu;Efeng", "Fumai;Huanmai"); }

    @Test @Order(4) @DisplayName("桂枝加附子汤证")
    void t_guizhijiafuzitang() { assertFangzheng("桂枝加附子汤证", "Taiyangbing", "Guizhijiafuzitangzheng", "Guizhijiafuzitang",
            "Hanloubuzhi;Efeng;Xiaobiannan;Sizhiweiji", "Fumai;Xumai"); }

    @Test @Order(5) @DisplayName("桂枝去芍药汤证诊断")
    void shouldDiagnoseGuizhiQuShaoyaoTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Xiongman_instance",
                        NS + "Efeng_instance",
                        NS + "Fare_instance",
                        NS + "Hanchu_instance"
                ),
                "pulseIris", List.of(
                        NS + "Cumai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("桂枝去芍药汤证", result);
        assertBasicResult(result, "Taiyangbing",
                "Guizhiqushaoyaotangzheng",
                NS + "Guizhiqushaoyaotang");
        assertBagang(result, List.of("表证"), null, List.of("阳证"));
    }

    @Test @Order(6) @DisplayName("桂枝去芍药加附子汤证")
    void t_guizhiqushaoyaojiafuzitang() { assertFangzheng("桂枝去芍药加附子汤证", "Taiyangbing", "Guizhiqushaoyaojiafuzitangzheng", "Guizhiqushaoyaojiafuzitang",
            "Xiongman;Ehan", ""); }

    @Test @Order(7) @DisplayName("桂枝新加汤证")
    void t_guizhixinjiatang() { assertFangzheng("桂枝新加汤证", "Taiyangbing", "Guizhixinjiatangzheng", "Guizhixinjiatang",
            "Shentengtong;Ehan", "Chenchimai"); }

    @Test @Order(8) @DisplayName("桂枝加芍药汤证")
    void t_guizhijiashaoyaotang() { assertFangzheng("桂枝加芍药汤证", "Taiyinbing", "Guizhijiashaoyaotangzheng", "Guizhijiashaoyaotang",
            "Fuman;Shitong", "Fuhuamai"); }

    @Test @Order(9) @DisplayName("桂枝加大黄汤证")
    void t_guizhijiadahuangtang() { assertFangzheng("桂枝加大黄汤证", "Taiyinbing", "Guizhijiadahuangtangzheng", "Guizhijiadahuangtang",
            "Futong;Juan", "Chenshimai"); }

    @Test @Order(10) @DisplayName("桂枝加桂汤证")
    void t_guizhijiaguitang() { assertFangzheng("桂枝加桂汤证", "Bentunbing", "Guizhijiaguitangzheng", "Guizhijiaguitang",
            "Qicongshaofushangchongxin;Fazuoyusi;Fuhaizhi", "Chenchimai"); }

    @Test @Order(11) @DisplayName("桂枝加龙骨牡蛎汤证")
    void t_guizhijialonggumulitang() { assertFangzheng("桂枝加龙骨牡蛎汤证", "Xulaobing", "Guizhijialonggumulitangzheng", "Guizhijialonggumulitang",
            "Shijingjia;Shaofuxianji;Yintouhan;Muxuan;Faluo", "Jixukouchimai"); }

    @Test @Order(12) @DisplayName("桂枝加黄芪汤证")
    void t_guizhijiahuangqitang() { assertFangzheng("桂枝加黄芪汤证", "Shuiqibing", "Guizhijiahuangqitangzheng", "Guizhijiahuangqitang",
            "Huanghan;Liangjingzileng;Shiyihanchu;Muchangdaohanchu", "Chenmai"); }

    @Test @Order(13) @DisplayName("桂枝甘草汤证")
    void t_guizhigancaotang() { assertFangzheng("桂枝甘草汤证", "Taiyangbing", "Guizhigancaotangzheng", "Guizhigancaotang",
            "Xinxiajidong;Yudean", "Fumai"); }

    @Test @Order(14) @DisplayName("桂枝甘草龙骨牡蛎汤证")
    void t_guizhigancaolonggumulitang() { assertFangzheng("桂枝甘草龙骨牡蛎汤证", "Taiyangbing", "Guizhigancaolonggumulitangzheng", "Guizhigancaolonggumulitang",
            "Fanzao", "Fumai"); }

    @Test @Order(15) @DisplayName("桂枝去芍药加蜀漆牡蛎龙骨救逆汤证")
    void t_guizhiqushaoyaojiashuqimulilonggujiunitang() { assertFangzheng("桂枝去芍药加蜀漆牡蛎龙骨救逆汤证", "Taiyangbing", "Guizhiqushaoyaojiashuqimulilonggujiunitangzheng", "Guizhiqushaoyaojiashuqimulilonggujiunitang",
            "Shanghanmaifu;Yihuopojiezhi;Jingkuang;Woqibuan", "Fumai"); }

    @Test @Order(16) @DisplayName("桂枝人参汤证")
    void t_guizhirenshentang() { assertFangzheng("桂枝人参汤证", "Taiyangbing;Taiyinbing", "Guizhirenshentangzheng", "Guizhirenshentang",
            "Xinxiapiying;Xialibuzhi;Fareehan", "Fuxumai"); }

    @Test @Order(17) @DisplayName("桂枝生姜枳实汤证")
    void t_guizhishengjiangzhishitang() { assertFangzheng("桂枝生姜枳实汤证", "Xiongbibing", "Guizhishengjiangzhishitangzheng", "Guizhishengjiangzhishitang",
            "Xinzhongpi;Qini;Xinxuantong", "Chenxianmai"); }

    @Test @Order(18) @DisplayName("桂枝附子汤证")
    void t_guizhifuzitang() { assertFangzheng("桂枝附子汤证", "Shibing", "Guizhifuzitangzheng", "Guizhifuzitang",
            "Shentengfan;Nanyizhuance", "Fuxusemai"); }

    @Test @Order(19) @DisplayName("桂枝芍药知母汤证")
    void t_guizhishaoyaozhimutang() { assertFangzheng("桂枝芍药知母汤证", "Lijiebing", "Guizhishaoyaozhimutangzheng", "Guizhishaoyaozhimutang",
            "Zhuzhijietengtong;Shentiwanglei;Jiaozhongrutuo;Touxuan;Duanqi;Wenwenyutu", "Chenxianmai"); }

    @Test @Order(20) @DisplayName("桂枝茯苓丸证")
    void t_guizhifulingwan() { assertFangzheng("桂枝茯苓丸证", "Renshengbing", "Guizhifulingwanzheng", "Guizhifulingwan",
            "Furensuyouzhengbing;Jingduanweijisanyue;LouxiaBuzhi;Taidongzaiqishang", "Chenxianmai"); }

    @Test @Order(21) @DisplayName("桂枝二麻黄一汤证")
    void t_guizhiermahuangyitang() { assertFangzheng("桂枝二麻黄一汤证", "Taiyangbing", "Guizhiermahuangyitangzheng", "Guizhiermahuangyitang",
            "Fareehan;Runvezhuang;Yirizaifa", "Fumai"); }

    @Test @Order(22) @DisplayName("桂枝麻黄各半汤证")
    void t_guizhimahuanggebantang() { assertFangzheng("桂枝麻黄各半汤证", "Taiyangbing", "Guizhimahuanggebantangzheng", "Guizhimahuanggebantang",
            "Fareehan;Mianyourese;Shenyang", "Fumai"); }

    @Test @Order(23) @DisplayName("桂枝二越婢一汤证")
    void t_guizhieryuebiyitang() {
        assertFangzheng("桂枝二越婢一汤证",
                "Taiyangbing",
                "Guizhieryuebiyitangzheng",
                "Guizhieryuebiyitang",
                "Fareehan;Remianre;Kekou",   // ← 补口渴
                "Weimai");                    // 脉微
    }

    @Test @Order(24) @DisplayName("桂枝去桂加茯苓白术汤证")
    void t_guizhiquguijiafulingbaizhutang() { assertFangzheng("桂枝去桂加茯苓白术汤证", "Taiyangbing", "Guizhiquguijiafulingbaizhutangzheng", "Guizhiquguijiafulingbaizhutang",
            "Toutong;Fare;Wuhan;Xinxiaman;Xiaobianbuli", "Fumai"); }

    // ==========================================================================
    // 【家族 2】麻黄汤类  @Order(31..60)
    // ==========================================================================

    @Test @Order(31)
    @DisplayName("麻黄汤证诊断")
    void shouldDiagnoseMahuangTangPattern() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Ehan_instance",      // 恶寒
                        NS + "Fare_instance",      // 发热
                        NS + "Wuhan_instance",     // 无汗（区分竹叶汤）
                        NS + "Shentong_instance"   // 身痛（而非头痛）
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",     // 浮脉
                        NS + "Jinmai_instance"     // 紧脉（区分竹叶汤的浮弱）
                ),
                "tongueIris", List.of(
                        NS + "Baotai_instance"     // 苔薄白
                ),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("麻黄汤证", result);
        assertBasicResult(result, "Taiyangbing", "Mahuangtangzheng", NS + "Mahuangtang");
        assertBagang(result, List.of("表证"), List.of("实证"), List.of("阳证"));
    }

    @Test @Order(32)
    @DisplayName("麻黄加术汤证")
    void t_mahuangjiazhutang() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Ehan_instance",       // 恶寒
                        NS + "Fare_instance",       // 发热
                        NS + "Wuhan_instance",      // 无汗
                        NS + "Shenzhong_instance",  // 身重（湿象，关键）
                        NS + "Shentengfan_instance" // 身烦疼（湿阻经络）
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",      // 浮脉
                        NS + "Jinmai_instance"      // 紧脉
                ),
                "tongueIris", List.of(
                        NS + "Baotai_instance"      // 苔薄白（或白腻）
                ),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("麻黄加术汤证", result);
        assertBasicResult(result, "Taiyangbing", "Mahuangjiazhutangzheng", NS + "Mahuangjiazhutang");
        assertBagang(result, List.of("表证"), List.of("实证"), List.of("阳证"));
    }

    @Test @Order(33) @DisplayName("麻黄附子细辛汤证诊断")
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

    @Test @Order(34) @DisplayName("麻黄附子甘草汤证")
    void t_mahuangfuzigancaotang() { assertFangzheng("麻黄附子甘草汤证", "Shaoyinbing", "Mahuangfuzigancaotangzheng", "Mahuangfuzigancaotang",
            "Ehan;Danyumei", "Chenmai"); }

    @Test @Order(35) @DisplayName("麻黄附子汤证")
    void t_mahuangfuzitang() {
        assertFangzheng("麻黄附子汤证",
                "Shaoyinbing",                    // 六经：少阴（原文"属少阴"）
                "Mahuangfuzitangzheng",
                "Mahuangfuzitang",
                "Shuizhong;Xiaobianbuli;Wuhan",   // 症状：水肿+小便不利+无汗
                "Chenxiaomai");                   // 脉象：沉小脉
    }

    @Test @Order(36) @DisplayName("麻黄升麻汤证")
    void t_mahuangshengmatang() { assertFangzheng("麻黄升麻汤证", "Jueyinbing", "Mahuangshengmatangzheng", "Mahuangshengmatang",
            "Yanhoubuli;Tunongxue;Xielibuzhi;Shouzujueni", "Chenchimai"); }

    @Test @Order(37) @DisplayName("麻杏石甘汤证")
    void t_maxingganshitang() { assertFangzheng("麻杏石甘汤证", "Taiyangbing", "Maxingshigantangzheng", "Maxingshigantang",
            "Hanchu;Chuan;Fare;Kouke", "Fumai;Shumai"); }

    @Test @Order(38) @DisplayName("麻杏薏甘汤证")
    void t_maxingyigantang() {
        assertFangzheng("麻杏薏甘汤证",
                "Taiyangbing",                              // ← 改：太阳病（不是 Shibing）
                "Maxingyigantangzheng",
                "Maxingyigantang",
                "Yishenjinteng;Fare;Ribusuoju;Wuhan",       // ← 加：无汗（关键鉴别点）
                "Fumai");
    }

    @Test @Order(39) @DisplayName("麻黄连翘赤小豆汤证")
    void t_mahuanglianqiaochixiaodoutang() {
        assertFangzheng("麻黄连翘赤小豆汤证",
                "Taiyangbing",
                "Mahuanglianqiaochixiaodoutangzheng",
                "Mahuanglianqiaochixiaodoutang",
                "Shenhuang;Fare;Wuhan;Ehan;Xiaobianbuli",   // ← 补小便不利
                "Fumai");
    }

    @Test @Order(40) @DisplayName("大青龙汤证")
    void t_daqinglongtang() { assertFangzheng("大青龙汤证", "Taiyangbing", "Daqinglongtangzheng", "Daqinglongtang",
            "Fare;Ehan;Shentengtong;Buhanchu;Fanzao", "Fumai;Jinmai"); }

    @Test @Order(41) @DisplayName("小青龙汤证")
    void t_xiaoqinglongtang() {
        assertFangzheng("小青龙汤证",
                "Taiyangbing",
                "Xiaoqinglongtangzheng",
                "Xiaoqinglongtang",
                "Fare;Ehan;Wuhan;Kechuan;Ganou;Tanduoqingxi",   // ← 补 痰多清稀
                "Fumai;Jinmai");
    }

    @Test @Order(42) @DisplayName("小青龙加石膏汤证")
    void t_xiaoqinglongjiashigaotang() { assertFangzheng("小青龙加石膏汤证", "Feizhangbing", "Xiaoqinglongjiashigaotangzheng", "Xiaoqinglongjiashigaotang",
            "Kesou;Chuan;Fanzao;Xinxiayoushui", "Fumai"); }

    @Test @Order(43) @DisplayName("射干麻黄汤证")
    void t_sheganmahuangtang() { assertFangzheng("射干麻黄汤证", "Kesoushangqibing", "Sheganmahuangtangzheng", "Sheganmahuangtang",
            "Keershangqi;Houzhongshuijisheng", "Fumai"); }

    @Test @Order(44) @DisplayName("厚朴麻黄汤证")
    void t_houpoumahuangtang() { assertFangzheng("厚朴麻黄汤证", "Kesoushangqibing", "Houpoumahuangtangzheng", "Houpoumahuangtang",
            "Kesou;Keerbuedewo", "Fumai"); }

    @Test @Order(45) @DisplayName("越婢汤证")
    void t_yuebitang() { assertFangzheng("越婢汤证", "Shuiqibing", "Yuebitangzheng", "Yuebitang",
            "Fengshuiefeng;Yishenxizhong;Xuzihanchu;Buke", "Fumai"); }

    @Test @Order(46) @DisplayName("越婢加术汤证")
    void t_yuebijiazhutang() { assertFangzheng("越婢加术汤证", "Shuiqibing", "Yuebijiazhutangzheng", "Yuebijiazhutang",
            "Lishui;Yishenmianmuhuangzhong;Xiaobianbuli", "Chenmai"); }

    @Test @Order(47) @DisplayName("越婢加半夏汤证")
    void t_yuebijiabanxiatang() { assertFangzheng("越婢加半夏汤证", "Feizhangbing", "Yuebijiabanxiatangzheng", "Yuebijiabanxiatang",
            "Kesou;Chuan", "Fudamai", "Murutuo", ""); }

    @Test @Order(48) @DisplayName("甘草麻黄汤证")
    void t_gancaomahuangtang() { assertFangzheng("甘草麻黄汤证", "Shuiqibing", "Gancaomahuangtangzheng", "Gancaomahuangtang",
            "Lishui", "Chenmai"); }

    @Test @Order(49) @DisplayName("杏子汤证")
    void t_xingzitang() { assertFangzheng("杏子汤证", "Shuiqibing", "Xingzitangzheng", "Xingzitang",
            "Shuizhiweibing;Maifu", "Fumai"); }

    @Test @Order(50) @DisplayName("半夏麻黄丸证")
    void t_banxiamahuangwan() { assertFangzheng("半夏麻黄丸证", "Jingjibing", "Banxiamahuangwanzheng", "Banxiamahuangwan",
            "Xinxiaji", "Xianmai"); }

    // ==========================================================================
    // 【家族 3】葛根汤类  @Order(61..70)
    // ==========================================================================

    @Test @Order(61) @DisplayName("葛根汤证")
    void t_gegentang() { assertFangzheng("葛根汤证", "Taiyangbing", "Gegentangzheng", "Gegentang",
            "Xiangbeiqiangjiji;Wuhan;Efeng", "Fumai;Jinmai"); }

    @Test @Order(62) @DisplayName("葛根加半夏汤证")
    void t_gegenjiabanxiatang() { assertFangzheng("葛根加半夏汤证", "Taiyangyangminghebing", "Gegenjiabanxiatangzheng", "Gegenjiabanxiatang",
            "Xiangbeiqiangjiji;Wuhan;Outu", "Fumai;Jinmai"); }

    @Test @Order(63) @DisplayName("葛根芩连汤证")
    void t_gegenqinliantang() { assertFangzheng("葛根芩连汤证", "Taiyangyangminghebing", "Gegenqinliantangzheng", "Gegenqinliantang",
            "Xiali;Shenre;Chuan;Hanchu", "Cumai"); }

    // ==========================================================================
    // 【家族 4】柴胡汤类  @Order(71..100)
    // ==========================================================================

    @Test @Order(71)
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

    @Test @Order(72)
    @DisplayName("大柴胡汤证诊断（少阳阳明合病）")
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
                "fuzhengIris", List.of(NS + "Xinxiaanzhimantong_instance")
        );

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大柴胡汤证", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Shaoyangbing", "Yangmingbing");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("sixChannel")).isEqualTo("少阳阳明合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("少阳阳明合病");
        assertThat(vars.get("sixChannelCn")).isEqualTo("少阳阳明合病");
        assertThat(vars.get("fangzheng")).isEqualTo("Dachaihutangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Dachaihutang");
        assertBagang(result, List.of("里证", "半表半里"), List.of("实证"), List.of("阳证"));
    }

    @Test @Order(73) @DisplayName("柴胡加芒硝汤证")
    void t_chaihujiamangxiaotang() { assertFangzheng("柴胡加芒硝汤证", "Shaoyangbing", "Chaihujiamangxiaotangzheng", "Chaihujiamangxiaotang",
            "Xiongxieman;Ou;Ripuchaore;Weixiali", "Xianmai"); }

    @Test @Order(74) @DisplayName("柴胡加龙骨牡蛎汤证")
    void t_chaihujialonggumulitang() { assertFangzheng("柴胡加龙骨牡蛎汤证", "Shaoyangbing", "Chaihujialonggumulitangzheng", "Chaihujialonggumulitang",
            "Xiongman;Fanjing;Xiaobianbuli;Zhanyu;Yishenjinzhong", "Xianmai"); }

    @Test @Order(75) @DisplayName("柴胡桂枝汤证")
    void t_chaihuguizhitang() { assertFangzheng("柴胡桂枝汤证", "Taiyangshaoyanghebing", "Chaihuguizhitangzheng", "Chaihuguizhitang",
            "Fareweiehan;Zhijiefenteng;Weiou;Xinxiazhijie", "Fuxianmai"); }

    @Test @Order(76)
    @DisplayName("柴胡桂枝干姜汤证诊断（少阳太阴合病）")
    void shouldDiagnoseChaihuGuizhiGanjiangTangPattern_Taiyin() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Wanglaihanre_instance",
                        NS + "Xiongxiekuman_instance",
                        NS + "Xiaobianbuli_instance",
                        NS + "Kouke_instance",
                        NS + "Buou_instance",
                        NS + "Dantouhanchu_instance",
                        NS + "Xinfan_instance",
                        NS + "Fuman_instance"
                ),
                "pulseIris", List.of(NS + "Ruomai_instance"),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("柴胡桂枝干姜汤证（少阳太阴合病）", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Shaoyangbing", "Taiyinbing");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("sixChannel")).isEqualTo("少阳太阴合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("少阳太阴合病");
        assertThat(vars.get("fangzheng")).isEqualTo("Chaihuguizhiganjiangtangzheng");
        assertThat(vars.get("finalFormula"))
                .isEqualTo(NS + "Chaihuguizhiganjiangtang");
    }

    @Test @Order(77) @DisplayName("柴胡去半夏加栝蒌汤证")
    void t_chaihuqubanxiajiagualoutang() { assertFangzheng("柴胡去半夏加栝蒌汤证", "Nuebing", "Chaihuqubanxiajiagualoutangzheng", "Chaihuqubanxiajiagualoutang",
            "Fake;Wanglaihanre", ""); }

    @Test @Order(78) @DisplayName("柴胡白虎汤证（三阳合病）")
    void t_chaihubaihutang() { assertFangzheng("柴胡白虎汤证（三阳合病）", "Sanyanghebing", "Chaihubaihutangzheng", "Chaihubaihutang",
            "Fare;Ehan;Wuhan;Danrebuhan;Kouke;Dahan;Wanglaihanre;Xiongxiekuman;Kouku;Fuman;Shenzhong;Nanyizhuance;Kouburen;Miangou;Zhanyu;Yiniao;Danyumei;Muhezehan",
            "Fumai;Hongdamai;Xianmai"); }

    @Test @Order(79) @DisplayName("四逆散证")
    void t_sinisanzheng_test() { assertFangzheng("四逆散证", "Shaoyinbing", "Sinisanzheng", "Sinisan",
            "Sini;Futong;Xieli", "Xianmai"); }

    @Test @Order(80)
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
        List<String> addHerbs = (List<String>) vars.get("addedHerb");
        assertThat(addHerbs)
                .contains(NS + "Danshen", NS + "Taoren");
    }

    @Test @Order(81)
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
        List<String> addHerbs = (List<String>) vars.get("addedHerb");
        assertThat(addHerbs)
                .contains(NS + "Banxia", NS + "Fuling");
    }

    @Test @Order(82)
    @DisplayName("大柴胡汤证夹痰饮检测")
    void shouldDetectTanYinJianJiaZhengWithDaChaihuTang() {
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
                NS + "Xinxiaanzhimantong_instance"
        ));

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大柴胡汤证夹痰饮", result);

        Map<String, Object> vars = result.getVariablesAsMap();

        assertThat(vars.get("fangzheng")).isEqualTo("Dachaihutangzheng");
        assertThat((List<String>) vars.get("jianJiaZhengs"))
                .containsExactly("Tanyinzheng");
        List<String> addHerbs = (List<String>) vars.get("addedHerb");
        assertThat(addHerbs)
                .contains(NS + "Banxia", NS + "Fuling");
    }

    @Test @Order(83)
    @DisplayName("小柴胡汤证夹气郁检测")
    void shouldDetectQiYuJianJiaZhengWithXiaoChaihuTang() {
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
                NS + "Yanzhongruyouzhiluan_instance"
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
        List<String> addHerbs = (List<String>) vars.get("addedHerb");
        assertThat(addHerbs)
                .contains(NS + "Xiangfu", NS + "Yujin");
    }

    // ==========================================================================
    // 【家族 5】白虎汤 / 竹叶石膏汤类  @Order(101..120)
    // ==========================================================================

    @Test @Order(101)
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

    @Test @Order(102) @DisplayName("白虎加人参汤证")
    void t_baihujiarenshentang() { assertFangzheng("白虎加人参汤证", "Yangmingbing", "Baihujiarenshentangzheng", "Baihujiarenshentang",
            "Dare;Dake;Dahan", "Hongdamai", "Sheganzao", ""); }

    @Test @Order(103) @DisplayName("白虎加桂枝汤证")
    void t_baihujiaguizhitang() { assertFangzheng("白虎加桂枝汤证", "Nuebing", "Baihujiaguizhitangzheng", "Baihujiaguizhitang",
            "Danrebuhan;Gujietengfan;Ou", "Pingmai"); }

    @Test @Order(104)
    @DisplayName("白虎汤证诊断（三阳合病）")
    void shouldDiagnoseBaihuTangPattern_SanYang() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Dare_instance",
                        NS + "Dahan_instance",
                        NS + "Dake_instance",
                        NS + "Kouke_instance",
                        NS + "Efeng_instance",
                        NS + "Wanglaihanre_instance",
                        NS + "Xiongxiekuman_instance"
                ),
                "pulseIris", List.of(
                        NS + "Fumai_instance",
                        NS + "Hongmai_instance"
                ),
                "tongueIris", List.of(),
                "fuzhengIris", List.of()
        );
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("白虎汤证（三阳合病）", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Yangmingbing", "Shaoyangbing");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("sixChannel")).isEqualTo("三阳合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("三阳合病");
        assertThat(vars.get("fangzheng")).isEqualTo("Baihutangzheng");
        assertThat(vars.get("finalFormula"))
                .isEqualTo(NS + "Baihutang");
    }

    @Test @Order(105) @DisplayName("竹叶石膏汤证")
    void t_zhuyeshigaotang() { assertFangzheng("竹叶石膏汤证", "Chahoubing", "Zhuyeshigaotangzheng", "Zhuyeshigaotang",
            "Xuleishaoqi;Qiniyutu", "Xushumai", "Shehongshaotai", ""); }

    // ==========================================================================
    // 【家族 6】承气汤 / 大黄类  @Order(121..160)
    // ==========================================================================

    @Test @Order(121)
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

    @Test @Order(122) @DisplayName("小承气汤证")
    void t_xiaochengqitang() { assertFangzheng("小承气汤证", "Yangmingbing", "Xiaochengqitangzheng", "Xiaochengqitang",
            "Fuman;Chaore;Zhanyu", "Huamai"); }

    @Test @Order(123) @DisplayName("调胃承气汤证")
    void t_tiaoweichengqitang() { assertFangzheng("调胃承气汤证", "Yangmingbing", "Tiaoweichengqitangzheng", "Tiaoweichengqitang",
            "Zhengzhengfare;Fuzhangman;Xinfan", "Chenshimai"); }

    @Test @Order(124) @DisplayName("桃核承气汤证")
    void t_taohechengqitang() { assertFangzheng("桃核承气汤证", "Taiyangbing", "Taohechengqitangzheng", "Taohechengqitang",
            "Shaofujijie;Rukuang;Xiaobianzili", "Chensemai"); }

    @Test @Order(125) @DisplayName("麻子仁丸证")
    void t_mazirenwan() { assertFangzheng("麻子仁丸证", "Yangmingbing", "Mazirenwanzheng", "Mazirenwan",
            "Dabianying;Xiaobianshu", "Fusemai"); }

    @Test @Order(126) @DisplayName("大黄甘草汤证")
    void t_dahuanggangaotang() { assertFangzheng("大黄甘草汤证", "Outuoyuexialibing", "Dahuanggangaotangzheng", "Dahuanggangaotang",
            "Shiyijitu", "Xianmai"); }

    @Test @Order(127) @DisplayName("大黄牡丹汤证")
    void t_dahuangmudantang() { assertFangzheng("大黄牡丹汤证", "Changyongbing", "Dahuangmudantangzheng", "Dahuangmudantang",
            "Changyong;Shaofuzhongpi;Anzhijitongrulin;Xiaobianzidiao;Shishifare;Zihanchu;Fuehan;Maichijin", "Chijinmai"); }

    @Test @Order(128) @DisplayName("大黄硝石汤证")
    void t_dahuangxiaoshitang() { assertFangzheng("大黄硝石汤证", "Huangdanbing", "Dahuangxiaoshitangzheng", "Dahuangxiaoshitang",
            "Huangdanfuman;Xiaobianbulierchi;Zihanchu", "Chenshimai"); }

    @Test @Order(129) @DisplayName("大黄附子汤证")
    void t_dahuangfuzitang() { assertFangzheng("大黄附子汤证", "Hanshanbing", "Dahuangfuzitangzheng", "Dahuangfuzitang",
            "Xiexiapiantong;Fare", "Jinxianmai"); }

    @Test @Order(130) @DisplayName("大黄蛰虫丸证")
    void t_dahuangzhechongwan() { assertFangzheng("大黄蛰虫丸证", "Xulaobing", "Dahuangzhechongwanzheng", "Dahuangzhechongwan",
            "Wulaoxuji;Fumanbunengyinshi;Jifujiacuo;Liangmuanhei", "Chensemai"); }

    @Test @Order(131) @DisplayName("大黄甘遂汤证")
    void t_dahuangansuitang() { assertFangzheng("大黄甘遂汤证", "Furenzabing", "Dahuangansuitangzheng", "Dahuangansuitang",
            "Furenshaofumanrudunzhuang;Xiaobiannan;Buke;Shenghouzhe", "Chenxianmai"); }

    @Test @Order(132) @DisplayName("厚朴大黄汤证")
    void t_houpoudahuangtang() { assertFangzheng("厚朴大黄汤证", "Tanyinbing", "Houpoudahuangtangzheng", "Houpoudahuangtang",
            "Zhiyinxiongman", "Chenshimai"); }

    @Test @Order(133) @DisplayName("厚朴七物汤证")
    void t_houpouqiwutang() { assertFangzheng("厚朴七物汤证", "Fumanbing", "Houpoqiwutangzheng", "Houpoqiwutang",
            "Fuman;Fare;Yinshirugu", "Fushumai"); }

    @Test @Order(134) @DisplayName("厚朴三物汤证")
    void t_houpousanwutang() { assertFangzheng("厚朴三物汤证", "Fumanbing", "Houposanwutangzheng", "Houposanwutang",
            "Tongerbi", "Chenshimai"); }

    @Test @Order(135) @DisplayName("厚朴生姜半夏甘草人参汤证")
    void t_houpoushengjiangbanxiagancaorenshentang() { assertFangzheng("厚朴生姜半夏甘草人参汤证", "Taiyinbing", "Houposhengjiangbanxiagancaorenshentangzheng", "Houposhengjiangbanxiagancaorenshentang",
            "Fuzhangman;Anzhibutong", "Fumai"); }

    @Test @Order(136) @DisplayName("下瘀血汤证")
    void t_xiayuxuetang() { assertFangzheng("下瘀血汤证", "Chanhoubing", "Xiayuxuetangzheng", "Xiayuxuetang",
            "Chanfufutong;Fuzhongyouganxue", "Chenxianmai"); }

    @Test @Order(137) @DisplayName("己椒苈黄丸证")
    void t_jijiaolihuangwan() { assertFangzheng("己椒苈黄丸证", "Tanyinbing", "Jijiaolihuangwanzheng", "Jijiaolihuangwan",
            "Fuman;Kousheganzao;Changjianyoushuiqi", "Chenxianmai"); }

    @Test @Order(138) @DisplayName("枳实芍药散证")
    void t_zhishishaoyaosan() { assertFangzheng("枳实芍药散证", "Chanhoubing", "Zhishishaoyaosanzheng", "Zhishishaoyaosan",
            "Chanhoufutong;Fanmanbudewo", "Xianmai"); }

    @Test @Order(139) @DisplayName("枳实栀子豉汤证")
    void t_zhishizhizichitang() { assertFangzheng("枳实栀子豉汤证", "Chahoulaofubing", "Zhishizhizichitangzheng", "Zhishizhizichitang",
            "Dabingchaihou;Laofu", "Fumai"); }

    // ==========================================================================
    // 【家族 7】泻心汤类  @Order(161..190)
    // ==========================================================================

    @Test @Order(161) @DisplayName("半夏泻心汤证")
    void t_banxiaxiexintang() { assertFangzheng("半夏泻心汤证", "Taiyangbing", "Banxiaxiexintangzheng", "Banxiaxiexintang",
            "Xinxiapi;Ou;Changming;Xiali", "Xianmai"); }

    @Test @Order(162) @DisplayName("生姜泻心汤证")
    void t_shengjiangxiexintang() { assertFangzheng("生姜泻心汤证", "Taiyangbing", "Shengjiangxiexintangzheng", "Shengjiangxiexintang",
            "Xinxiapiying;Ganyishichou;Fuzhongleiming;Xiali", "Xianmai"); }

    @Test @Order(163) @DisplayName("甘草泻心汤证（伤寒）")
    void t_gancaoxiexintang() { assertFangzheng("甘草泻心汤证（伤寒）", "Taiyangbing", "Gancaoxiexintangzheng", "Gancaoxiexintang",
            "Xinxiapiyingerman;Xialirishushixing;Ganou;Xinfanbudean", "Xianmai"); }

    @Test @Order(164) @DisplayName("甘草泻心汤证（狐惑）")
    void t_gancaoxiexintang_huhuo() { assertFangzheng("甘草泻心汤证（狐惑）", "Huhuobing", "Gancaoxiexintangzheng", "Gancaoxiexintang",
            "Zhuangrushanghan;Momoyumian;Mubudebi;Woqibuan;Buyuyinshi;Ewenshichou;Mianmuzhachizhazaizhabai", "Xianmai"); }

    @Test @Order(165) @DisplayName("大黄黄连泻心汤证")
    void t_dahuanghuanglianxiexintang() { assertFangzheng("大黄黄连泻心汤证", "Taiyangbing", "Dahuanghuanglianxiexintangzheng", "Dahuanghuanglianxiexintang",
            "Xinxiapi;Anzhiru", "Fumai"); }

    @Test @Order(166) @DisplayName("附子泻心汤证")
    void t_fuzixiexintang() { assertFangzheng("附子泻心汤证", "Taiyangbing", "Fuzixiexintangzheng", "Fuzixiexintang",
            "Xinxiapi;Ehan;Hanchu", "Fumai"); }

    @Test @Order(167) @DisplayName("泻心汤证")
    void t_xiexintang() { assertFangzheng("泻心汤证", "Tunvxiaxuebing", "Xiexintangzheng", "Xiexintang",
            "Tuxue;Nvxue;Xinqibuzu", "Hongmai"); }

    @Test @Order(168) @DisplayName("黄连汤证")
    void t_huangliantang() { assertFangzheng("黄连汤证", "Jueyinbing", "Huangliantangzheng", "Huangliantang",
            "Xiongzhongyoure;Weizhongyouxieqi;Futong;Yuou", "Xianmai"); }

    @Test @Order(169) @DisplayName("干姜黄芩黄连人参汤证")
    void t_ganjianghuangqinhuanglianrenshentang() { assertFangzheng("干姜黄芩黄连人参汤证", "Jueyinbing", "Ganjianghuangqinhuanglianrenshentangzheng", "Ganjianghuangqinhuanglianrenshentang",
            "Shirukoujitu;Xiali", "Chenweimai"); }

    @Test @Order(170) @DisplayName("半夏泻心汤证（生姜泻心汤衍化）")
    void t_shengjiangxiexintang_alias() { assertFangzheng("生姜泻心汤证", "Taiyangbing", "Shengjiangxiexintangzheng", "Shengjiangxiexintang",
            "Xinxiapiying;Ganyishichou;Fuzhongleiming;Xiali", "Xianmai"); }

    // ==========================================================================
    // 【家族 8】理中 / 四逆 / 真武 / 附子类  @Order(191..240)
    // ==========================================================================

    @Test @Order(191)
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

    @Test @Order(192)
    @DisplayName("四逆汤证诊断（太阴少阴合病）")
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

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyinbing", "Shaoyinbing");
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat(vars.get("sixChannel")).isEqualTo("太阴少阴合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太阴少阴合病");
        assertThat(vars.get("fangzheng")).isEqualTo("Sinitangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Sinitang");
        assertBagang(result, List.of("表证", "里证"), List.of("虚证"), List.of("阴证"));
    }

    @Test @Order(193) @DisplayName("通脉四逆汤证")
    void t_tongmaisinitang() { assertFangzheng("通脉四逆汤证", "Shaoyinbing", "Tongmaisinitangzheng", "Tongmaisinitang",
            "Xialiqinggu;Shouzujueni;Mianchi;Shenbuehan", "Weiyujuemai"); }

    @Test @Order(194) @DisplayName("通脉四逆加猪胆汁汤证")
    void t_tongmaisinijiazhudanzhitang() { assertFangzheng("通脉四逆加猪胆汁汤证", "Shaoyinbing", "Tongmaisinijiazhudanzhitangzheng", "Tongmaisinijiazhudanzhitang",
            "Hanchu;Sizhijuji;Jueni", "Weimai"); }

    @Test @Order(195) @DisplayName("四逆加人参汤证")
    void t_sinijiarenshentang() { assertFangzheng("四逆加人参汤证", "Shaoyinbing", "Sinijiarenshentangzheng", "Sinijiarenshentang",
            "Ehan;Xiali;Danyumei", "Weimai"); }

    @Test @Order(196) @DisplayName("茯苓四逆汤证")
    void t_fulingsinitang() { assertFangzheng("茯苓四逆汤证", "Taiyangbing", "Fulingsinitangzheng", "Fulingsinitang",
            "Fanzao;Sini", "Weimai"); }

    @Test @Order(197) @DisplayName("白通汤证")
    void t_baitongtang() { assertFangzheng("白通汤证", "Shaoyinbing", "Baitongtangzheng", "Baitongtang",
            "Xialiqinggu;Mianchi;Ehan", "Weimai"); }

    @Test @Order(198) @DisplayName("白通加猪胆汁汤证")
    void t_baitongjiazhudanzhitang() { assertFangzheng("白通加猪胆汁汤证", "Shaoyinbing", "Baitongjiazhudanzhitangzheng", "Baitongjiazhudanzhitang",
            "Xiali;Jueni;Ganou;Fan", "Weimai"); }

    @Test @Order(199) @DisplayName("干姜附子汤证")
    void t_ganjiangfuzitang() { assertFangzheng("干姜附子汤证", "Taiyangbing", "Ganjiangfuzitangzheng", "Ganjiangfuzitang",
            "Fanzao;Budemian", "Chenweimai"); }

    @Test @Order(200)
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
        assertBagang(result, List.of("表证"), List.of("虚证"), List.of("阴证"));
    }

    @Test @Order(201) @DisplayName("附子汤证")
    void t_fuzitang() { assertFangzheng("附子汤证", "Shaoyinbing", "Fuzitangzheng", "Fuzitang",
            "Beiehan;Shentong;Shouzuhan;Gujietong", "Chenweimai"); }

    @Test @Order(202) @DisplayName("附子粳米汤十八反警告检测")
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

    @Test @Order(203) @DisplayName("白术附子汤证")
    void t_baizhufuzitang() { assertFangzheng("白术附子汤证", "Shibing", "Baizhufuzitangzheng", "Baizhufuzitang",
            "Shentitengfan;Dabianjian;Xiaobianzili", "Fuxusemai"); }

    @Test @Order(204) @DisplayName("甘草附子汤证")
    void t_gancaofuzitang() { assertFangzheng("甘草附子汤证", "Shibing", "Gancaofuzitangzheng", "Gancaofuzitang",
            "Gujietengfan;Chetongbudequshen;Hanchu;Duanqi;Xiaobianbuli;Efeng", "Fumai"); }

    @Test @Order(205) @DisplayName("薏苡附子散证")
    void t_yiyifuzisan() { assertFangzheng("薏苡附子散证", "Xiongbibing", "Yiyifuzisanzheng", "Yiyifuzisan",
            "Xiongbihuanji", "Chenchimai"); }

    @Test @Order(206) @DisplayName("薏苡附子败酱散证")
    void t_yiyifuzibaijiangsan() { assertFangzheng("薏苡附子败酱散证", "Changyongbing", "Yiyifuzibaijiangsanzheng", "Yiyifuzibaijiangsan",
            "Changyong;Shenjiacuo;Fupiji;Anzhiruruzhongzhuang;Wujiju;Shenwure;Maishu", "Shumai"); }

    @Test @Order(207) @DisplayName("吴茱萸汤证")
    void t_wuzhuyutang() { assertFangzheng("吴茱萸汤证", "Shaoyinbing", "Wuzhuyutangzheng", "Wuzhuyutang",
            "Shiguyuou;Tuli;Shouzunileng;Fanzaoyusi;Ganoutuxianmo;Toutong", "Chenxianmai"); }

    @Test @Order(208)
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

    @Test @Order(209) @DisplayName("桃花汤证")
    void t_taohuatang() { assertFangzheng("桃花汤证", "Shaoyinbing", "Taohuatangzheng", "Taohuatang",
            "Xialibiannongxue;Futong;Xiaobianbuli", "Chenweimai"); }

    @Test @Order(210) @DisplayName("黄连阿胶汤证")
    void t_huanglianejiaotang() { assertFangzheng("黄连阿胶汤证", "Shaoyinbing", "Huanglianejiaotangzheng", "Huanglianejiaotang",
            "Xinzhongfan;Budewo", "Xishumai"); }

    @Test @Order(211) @DisplayName("猪肤汤证")
    void t_zhufutang() { assertFangzheng("猪肤汤证", "Shaoyinbing", "Zhufutangzheng", "Zhufutang",
            "Xiali;Yantong;Xiongman;Xinfan", "Xishumai"); }

    @Test @Order(212) @DisplayName("甘草汤证")
    void t_gancaotang() { assertFangzheng("甘草汤证", "Shaoyinbing", "Gancaotangzheng", "Gancaotang",
            "Yantong", "Fumai"); }

    @Test @Order(213) @DisplayName("桔梗汤证")
    void t_jiegengtang() { assertFangzheng("桔梗汤证", "Shaoyinbing", "Jiegengtangzheng", "Jiegengtang",
            "Yantong;Kesou;Tunong", "Fumai"); }

    @Test @Order(214) @DisplayName("半夏散及汤证")
    void t_banxiasanjitang() { assertFangzheng("半夏散及汤证", "Shaoyinbing", "Banxiasanjitangzheng", "Banxiasanjitang",
            "Yantong", "Fumai"); }

    @Test @Order(215) @DisplayName("苦酒汤证")
    void t_kujiutang() { assertFangzheng("苦酒汤证", "Shaoyinbing", "Kujiutangzheng", "Kujiutang",
            "Yanzhongshengchuang;Bunengyanyu", "Fumai"); }

    @Test @Order(216) @DisplayName("附子汤证（少阴）")
    void t_fuzitang_shaoyin() { assertFangzheng("附子汤证", "Shaoyinbing", "Fuzitangzheng", "Fuzitang",
            "Beiehan;Shentong;Shouzuhan;Gujietong", "Chenweimai"); }

    @Test @Order(217) @DisplayName("干姜附子汤证（阳明中暍）")
    void t_ganjiangfuzitang_zhongye() { assertFangzheng("干姜附子汤证", "Taiyangbing", "Ganjiangfuzitangzheng", "Ganjiangfuzitang",
            "Fanzao;Budemian", "Chenweimai"); }

    @Test @Order(218) @DisplayName("乌头汤证")
    void t_wutoutang() { assertFangzheng("乌头汤证", "Lijiebing", "Wutoutangzheng", "Wutoutang",
            "Guanjietengtong;Bukequshen", "Chenjinmai"); }

    @Test @Order(219) @DisplayName("大乌头煎证")
    void t_dawutoujian() { assertFangzheng("大乌头煎证", "Hanshanbing", "Dawutoujianzheng", "Dawutoujian",
            "Hanshanraoqitong;Ruofazebaihanchu;Shouzujueleng", "Chenjinmai"); }

    @Test @Order(220) @DisplayName("乌头桂枝汤证")
    void t_wutouguizhitang() { assertFangzheng("乌头桂枝汤证", "Hanshanbing", "Wutouguizhitangzheng", "Wutouguizhitang",
            "Hanshanfutong;Nishen;Shouzuburen;Shentengtong", "Chenjinmai"); }

    @Test @Order(221) @DisplayName("乌头赤石脂丸证")
    void t_wutouchishizhiwan() { assertFangzheng("乌头赤石脂丸证", "Xiongbibing", "Wutouchishizhiwanzheng", "Wutouchishizhiwan",
            "Xintongchebei;Beitongchexin", "Chenjinmai"); }

    @Test @Order(222) @DisplayName("赤丸证")
    void t_chiwan() { assertFangzheng("赤丸证", "Hanshanbing", "Chiwanzheng", "Chiwan",
            "Hanqijueni", "Chenximai"); }

    @Test @Order(223) @DisplayName("当归四逆汤证")
    void t_dangguisinitang() { assertFangzheng("当归四逆汤证", "Jueyinbing", "Dangguisinitangzheng", "Dangguisinitang",
            "Shouzujuehan;Maixiyujue", "Xiruomai"); }

    @Test @Order(224) @DisplayName("当归四逆加吴茱萸生姜汤证")
    void t_dangguisnijiawuzhuyushengjiangtang() { assertFangzheng("当归四逆加吴茱萸生姜汤证", "Jueyinbing", "Dangguisnijiawuzhuyushengjiangtangzheng", "Dangguisnijiawuzhuyushengjiangtang",
            "Shouzujuehan;Neiyoujiuhan;Ou;Futong", "Xiruomai"); }

    // ==========================================================================
    // 【家族 9】苓桂 / 茯苓类  @Order(241..300)
    // ==========================================================================

    @Test @Order(241) @DisplayName("茯苓桂枝甘草大枣汤证")
    void t_fulingguizhigancaodazaotang() { assertFangzheng("茯苓桂枝甘草大枣汤证", "Taiyangbing", "Fulingguizhigancaodazaotangzheng", "Fulingguizhigancaodazaotang",
            "Qixiaji;Yuzuobentun", "Chenmai"); }

    @Test @Order(242) @DisplayName("茯苓桂枝白术甘草汤证")
    void t_fulingguizhibaizhugancaotang() { assertFangzheng("茯苓桂枝白术甘草汤证", "Taiyangbing", "Fulingguizhibaizhugancaotangzheng", "Lingguizhugantang",
            "Xinxianiman;Qishangchongxiong;Qizetouxuan", "Chenjinmai"); }

    @Test @Order(243) @DisplayName("苓桂术甘汤证")
    void t_lingguizhugantang() { assertFangzheng("苓桂术甘汤证", "Tanyinbing", "Lingguizhugantangzheng", "Lingguizhugantang",
            "Xinxianiman;Qishangchongxiong;Qizetouxuan", "Chenjinmai"); }

    @Test @Order(244) @DisplayName("五苓散证")
    void t_wulingsan() { assertFangzheng("五苓散证", "Taiyangbing", "Wulingsanzheng", "Wulingsan",
            "Fare;Kouke;Xiaobianbuli;Shuiruzeitu", "Fumai"); }

    @Test @Order(245) @DisplayName("猪苓汤证")
    void t_zhulingtang() { assertFangzheng("猪苓汤证", "Yangmingbing", "Zhulingtangzheng", "Zhulingtang",
            "Fare;Kouke;Xiaobianbuli", "Fumai"); }

    @Test @Order(246) @DisplayName("猪苓散证")
    void t_zhulingsan() { assertFangzheng("猪苓散证", "Outuoyuexialibing", "Zhulingsanzheng", "Zhulingsan",
            "Outuerbingzaigeshang;Housishui", "Fumai"); }

    @Test @Order(247) @DisplayName("泽泻汤证")
    void t_zexietang() { assertFangzheng("泽泻汤证", "Tanyinbing", "Zexietangzheng", "Zexietang",
            "Xinxiayouzhiyin;Kumaoxuan", "Chenxianmai"); }

    @Test @Order(248) @DisplayName("茯苓甘草汤证")
    void t_fulinggancaotang() { assertFangzheng("茯苓甘草汤证", "Taiyangbing", "Fulinggancaotangzheng", "Fulinggancaotang",
            "Hanchu;Buke;Xinxiaji;Xiaobianbuli", "Fumai"); }

    @Test @Order(249) @DisplayName("茯苓泽泻汤证")
    void t_fulingzexietang() { assertFangzheng("茯苓泽泻汤证", "Outuoyuexialibing", "Fulingzexietangzheng", "Fulingzexietang",
            "Outu;Kouke;Yuyinshui", "Fumai"); }

    @Test @Order(250) @DisplayName("茯苓杏仁甘草汤证")
    void t_fulingxingrengancaotang() { assertFangzheng("茯苓杏仁甘草汤证", "Xiongbibing", "Fulingxingrengancaotangzheng", "Fulingxingrengancaotang",
            "Xiongzhongqisai;Duanqi", "Chenximai"); }

    @Test @Order(251) @DisplayName("防己茯苓汤证")
    void t_fangjifulingtang() { assertFangzheng("防己茯苓汤证", "Shuiqibing", "Fangjifulingtangzheng", "Fangjifulingtang",
            "Pishui;Sizhizhong;Sizhinieniedong", "Fumai"); }

    @Test @Order(252) @DisplayName("木防己汤证")
    void t_mufangjitang() { assertFangzheng("木防己汤证", "Tanyinbing", "Mufangjitangzheng", "Mufangjitang",
            "Gejianzhiyin;Chuanman;XinxiaPijian;MianseLihei", "Chenjinmai"); }

    @Test @Order(253) @DisplayName("木防己去石膏加茯苓芒硝汤证")
    void t_Mufangjiqushigaojiafulingmangxiaotangzheng() { assertFangzheng("木防己去石膏加茯苓芒硝汤证", "Tanyinbing", "Mufangjiqushigaojiafulingmangxiaotangzheng", "Mufangjiqushigaojiafulingmangxiaotang",
            "Gejianzhiyin;XinxiaPijian", "Chenjinmai"); }

    @Test @Order(254) @DisplayName("苓甘五味姜辛汤证")
    void t_lingganwuweijiangxintang() { assertFangzheng("苓甘五味姜辛汤证", "Tanyinbing", "Lingganwuweijiangxintangzheng", "Lingganwuweijiangxintang",
            "Keman", "Chenmai"); }

    @Test @Order(255) @DisplayName("苓甘五味姜辛夏汤证")
    void t_lingganwuweijiangxinxiatang() { assertFangzheng("苓甘五味姜辛夏汤证", "Tanyinbing", "Lingganwuweijiangxinxiatangzheng", "Lingganwuweijiangxinxiatang",
            "Keman;Ou;Mao", "Chenmai"); }

    @Test @Order(256) @DisplayName("苓甘五味加姜辛半夏杏仁汤证")
    void t_lingganwuweijiajiangxinbanxiaxingrentang() { assertFangzheng("苓甘五味加姜辛半夏杏仁汤证", "Tanyinbing", "Lingganwuweijiajiangxinbanxiaxingrentangzheng", "Lingganwuweijiajiangxinbanxiaxingrentang",
            "Xingzhong", "Chenmai"); }

    @Test @Order(257) @DisplayName("苓甘五味加姜辛半杏大黄汤证")
    void t_lingganwuweijiajiangxinbanxingdahuangtang() { assertFangzheng("苓甘五味加姜辛半杏大黄汤证", "Tanyinbing", "Lingganwuweijiajiangxinbanxingdahuangtangzheng", "Lingganwuweijiajiangxinbanxingdahuangtang",
            "Mianreruzui", "Chenmai"); }

    @Test @Order(258) @DisplayName("桂苓五味甘草汤证")
    void t_guilingwuweigancaotang() { assertFangzheng("桂苓五味甘草汤证", "Tanyinbing", "Guilingwuweigancaotangzheng", "Guilingwuweigancaotang",
            "Duotuokouzao;Shouzujueni;Qicongxiaofushangchongxiongyan;Xiaobiannan;Shifumao", "Chenmai"); }

    @Test @Order(259) @DisplayName("葵子茯苓散证")
    void t_kuizifulingsan() { assertFangzheng("葵子茯苓散证", "Renshengbing", "Kuizifulingsanzheng", "Kuizifulingsan",
            "Renshenyoushuiqi;Shenzhong;Xiaobianbuli;Sasaehan;Qizetouxuan", "Fumai"); }

    // ==========================================================================
    // 【家族 10】陷胸汤类  @Order(301..320)
    // ==========================================================================

    @Test @Order(301) @DisplayName("大陷胸汤证")
    void t_daxianxiongtang() { assertFangzheng("大陷胸汤证", "Taiyangbing", "Daxianxiongtangzheng", "Daxianxiongtang",
            "Xinxiatong;Anzhishiying", "Chenjinmai"); }

    @Test @Order(302) @DisplayName("大陷胸丸证")
    void t_daxianxiongwan() { assertFangzheng("大陷胸丸证", "Taiyangbing", "Daxianxiongwanzheng", "Daxianxiongwan",
            "Jiexiong;Xiangqiang;Ruroujingzhuang", "Chenjinmai"); }

    @Test @Order(303) @DisplayName("小陷胸汤证")
    void t_xiaoxianxiongtang() { assertFangzheng("小陷胸汤证", "Taiyangbing", "Xiaoxianxiongtangzheng", "Xiaoxianxiongtang",
            "Zhengzaixinxia;Anzhizetong", "Fuhuamai"); }

    // ==========================================================================
    // 【家族 11】栀子豉汤类  @Order(321..360)
    // ==========================================================================

    @Test @Order(321) @DisplayName("栀子豉汤证")
    void t_zhizichitang() { assertFangzheng("栀子豉汤证", "Yangmingbing", "Zhizichitangzheng", "Zhizichitang",
            "Xinzhongaonao;Fanrebudemian;Shaoqi", "Fumai"); }

    @Test @Order(322) @DisplayName("栀子甘草豉汤证")
    void t_zhizigancaochitang() { assertFangzheng("栀子甘草豉汤证", "Yangmingbing", "Zhizigancaochitangzheng", "Zhizigancaochitang",
            "Xinzhongaonao;Shaoqi", "Fumai"); }

    @Test @Order(323) @DisplayName("栀子生姜豉汤证")
    void t_zhizishengjiangchitang() { assertFangzheng("栀子生姜豉汤证", "Yangmingbing", "Zhizishengjiangchitangzheng", "Zhizishengjiangchitang",
            "Xinzhongaonao;Ou", "Fumai"); }

    @Test @Order(324) @DisplayName("栀子厚朴汤证")
    void t_zhizihoupotang() { assertFangzheng("栀子厚朴汤证", "Yangmingbing", "Zhizihoupotangzheng", "Zhizihoupotang",
            "Xinfan;Fuman;Woqibuan", "Fumai"); }

    @Test @Order(325) @DisplayName("栀子干姜汤证")
    void t_zhiziganjiangtang() { assertFangzheng("栀子干姜汤证", "Taiyangbing", "Zhiziganjiangtangzheng", "Zhiziganjiangtang",
            "Shenrebuqu;Weifan", "Fushumai"); }

    @Test @Order(326) @DisplayName("栀子柏皮汤证")
    void t_zhizibaipitang() { assertFangzheng("栀子柏皮汤证", "Yangmingbing", "Zhizibaipitangzheng", "Zhizibaipitang",
            "Shenhuang;Fare", "Fumai"); }

    @Test @Order(327) @DisplayName("栀子大黄汤证")
    void t_zhizidahuangtang() { assertFangzheng("栀子大黄汤证", "Huangdanbing", "Zhizidahuangtangzheng", "Zhizidahuangtang",
            "Jiuhuangdan;Xinzhongaonao;Retong", "Xianshumai"); }

    // ==========================================================================
    // 【家族 12】半夏剂类  @Order(361..400)
    // ==========================================================================

    @Test @Order(361) @DisplayName("小半夏汤证")
    void t_xiaobanxiatang() { assertFangzheng("小半夏汤证", "Tanyinbing", "Xiaobanxiatangzheng", "Xiaobanxiatang",
            "Oujiabenke;Fanbuke;Xinxiayouzhiyin", "Xianmai"); }

    @Test @Order(362) @DisplayName("小半夏加茯苓汤证")
    void t_xiaobanxiajiafulingtang() { assertFangzheng("小半夏加茯苓汤证", "Tanyinbing", "Xiaobanxiajiafulingtangzheng", "Xiaobanxiajiafulingtang",
            "Zuoutu;Xinxiapi;Gejianyoushui;Xuanji", "Xianmai"); }

    @Test @Order(363) @DisplayName("大半夏汤证")
    void t_dabanxiatang() { assertFangzheng("大半夏汤证", "Outuoyuexialibing", "Dabanxiatangzheng", "Dabanxiatang",
            "Outu", ""); }

    @Test @Order(364) @DisplayName("半夏厚朴汤证")
    void t_banxiahoupotang() { assertFangzheng("半夏厚朴汤证", "Furenzabing", "Banxiahoupotangzheng", "Banxiahoupotang",
            "Furenyanzhongruyouzhilian", "Xianmai"); }

    @Test @Order(365) @DisplayName("半夏干姜散证")
    void t_banxiaganjiangsan() { assertFangzheng("半夏干姜散证", "Outuoyuexialibing", "Banxiaganjiangsanzheng", "Banxiaganjiangsan",
            "Ganou;Tuxianmo", "Xianmai"); }

    @Test @Order(366) @DisplayName("干姜人参半夏丸证")
    void t_ganjiangrenshenbanxiawan() { assertFangzheng("干姜人参半夏丸证", "Renshengbing", "Ganjiangrenshenbanxiawanzheng", "Ganjiangrenshenbanxiawan",
            "Renshenoutubuzhi", "Xumai"); }

    @Test @Order(367)
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
        assertThat(vars.get("fangzheng")).isEqualTo("Gansuibanxiatangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Gansuibanxiatang");

        List<String> warnings = (List<String>) vars.get("warnings");
        assertThat(warnings).as("应包含十八反警告").isNotNull().isNotEmpty();
        assertThat(warnings).anySatisfy(w -> assertThat(w)
                .contains("十八反")
                .contains("甘遂")
                .contains("甘草"));
    }

    @Test @Order(368) @DisplayName("生姜半夏汤证")
    void t_shengjiangbanxiatang() { assertFangzheng("生姜半夏汤证", "Outuoyuexialibing", "Shengjiangbanxiatangzheng", "Shengjiangbanxiatang",
            "Xiongzhongkuikui", "Xianmai"); }

    // ==========================================================================
    // 【家族 13】黄芪类  @Order(401..440)
    // ==========================================================================

    @Test @Order(401) @DisplayName("黄芪桂枝五物汤证")
    void t_huangqiguizhiwuwutang() { assertFangzheng("黄芪桂枝五物汤证", "Xuebibing", "Huangqiguizhiwuwutangzheng", "Huangqiguizhiwuwutang",
            "Shentiburen;Rufengbizhuang", "Weisemai"); }

    @Test @Order(402) @DisplayName("黄芪建中汤证")
    void t_huangqijianzhongtang() { assertFangzheng("黄芪建中汤证", "Xulaobing", "Huangqijianzhongtangzheng", "Huangqijianzhongtang",
            "Xulaoliji;Fuzhongjiaotong;Miansewuhua", ""); }

    @Test @Order(403) @DisplayName("黄芪芍药桂枝苦酒汤证")
    void t_huangqishaoyaoguizhikujiutang() { assertFangzheng("黄芪芍药桂枝苦酒汤证", "Shuiqibing", "Huangqishaoyaoguizhikujiutangzheng", "Huangqishaoyaoguizhikujiutang",
            "Huanghan;Shentizhong;Farehanchuerke;Hanzhanyi;Sezhenghuangrubaizhi", "Chenmai"); }

    @Test @Order(404) @DisplayName("防己黄芪汤证")
    void t_fangjihuangqitang() { assertFangzheng("防己黄芪汤证", "Shibing", "Fangjihuangqitangzheng", "Fangjihuangqitang",
            "Shenzhong;Hanchu;Efeng", "Fumai"); }

    // ==========================================================================
    // 【家族 14】栝楼 / 薤白类  @Order(441..500)
    // ==========================================================================

    @Test @Order(441) @DisplayName("栝蒌桂枝汤证")
    void t_gualouguizhitang() { assertFangzheng("栝蒌桂枝汤证", "Jingbing", "Gualouguizhitangzheng", "Gualouguizhitang",
            "Shentiqiangjiji;Fare;Hanchu;Efeng", "Chenchimai"); }

    @Test @Order(442) @DisplayName("栝楼瞿麦丸十八反警告检测")
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

    @Test @Order(443) @DisplayName("瓜蒌牡蛎散证")
    void t_gualoumulisan() { assertFangzheng("瓜蒌牡蛎散证", "Baihebing", "Gualoumulisanzheng", "Gualoumulisan",
            "Kouke", "Weishumai"); }

    @Test @Order(444) @DisplayName("栝蒌薤白白酒汤证")
    void t_gualouxiebaibaijiutang() { assertFangzheng("栝蒌薤白白酒汤证", "Xiongbibing", "Gualouxiebaibaijiutangzheng", "Gualouxiebaibaijiutang",
            "Xiongbi;Chuanxi;Ketuo;Xiongbeitong;Duanqi", "Chenchimai"); }

    @Test @Order(445) @DisplayName("栝蒌薤白半夏汤证")
    void t_gualouxiebaibanxiatang() { assertFangzheng("栝蒌薤白半夏汤证", "Xiongbibing", "Gualouxiebaibanxiatangzheng", "Gualouxiebaibanxiantang",
            "Xiongbibudewo;Xintongchebei", "Chenjinmai"); }

    @Test @Order(446) @DisplayName("枳实薤白桂枝汤证")
    void t_zhishixiebaiguizhitang() { assertFangzheng("枳实薤白桂枝汤证", "Xiongbibing", "Zhishixiebaiguizhitangzheng", "Zhishixiebaiguizhitang",
            "Xiongbixinzhongpi;Xiongman;Xiexianiqiangxin", "Chenjinmai"); }

    @Test @Order(447) @DisplayName("枳术汤证")
    void t_zhishutang() { assertFangzheng("枳术汤证", "Shuiqibing", "Zhishutangzheng", "Zhishutang",
            "Xinxiajian;Darupan;Bianruxuanpan", "Chenximai"); }

    // ==========================================================================
    // 【家族 15】百合类  @Order(501..540)
    // ==========================================================================

    @Test @Order(501) @DisplayName("百合地黄汤证")
    void t_baihedihuangtang() { assertFangzheng("百合地黄汤证", "Baihebing", "Baihedihuangtangzheng", "Baihedihuangtang",
            "Kouku;Xiaobianchi", "Weishumai"); }

    @Test @Order(502) @DisplayName("百合知母汤证")
    void t_baihezhimutang() { assertFangzheng("百合知母汤证", "Baihebing", "Baihezhimutangzheng", "Baihezhimutang",
            "Kouku;Xiaobianchi;Xinzhongfan", "Weishumai"); }

    @Test @Order(503) @DisplayName("滑石代赭汤证")
    void t_huashidaizhetang() { assertFangzheng("滑石代赭汤证", "Baihebing", "Huashidaizhetangzheng", "Huashidaizhetang",
            "Kouku;Xiaobianchi;Xiaobianbuli;Xinzhongfan", "Weishumai"); }

    @Test @Order(504) @DisplayName("百合鸡子汤证")
    void t_baihejizitang() { assertFangzheng("百合鸡子汤证", "Baihebing", "Baihejizitangzheng", "Baihejizitang",
            "Kouku;Xiaobianchi;Xinzhongfan", "Weishumai"); }

    @Test @Order(505) @DisplayName("百合洗方证")
    void t_baihexifang() { assertFangzheng("百合洗方证", "Baihebing", "Baihexifangzheng", "Baihexifang",
            "Kouke", "Weishumai"); }

    @Test @Order(506) @DisplayName("百合滑石散证")
    void t_baihehuashisan() { assertFangzheng("百合滑石散证", "Baihebing", "Baihehuashisanzheng", "Baihehuashisan",
            "Fare", "Weishumai"); }

    // ==========================================================================
    // 【家族 16】当归类  @Order(541..580)
    // ==========================================================================

    @Test @Order(541) @DisplayName("当归芍药散证")
    void t_dangguishaoyaosan() { assertFangzheng("当归芍药散证", "Renshengbing", "Dangguishaoyaosanzheng", "Dangguishaoyaosan",
            "Furenhuaishen;Fuzhongjiaotong", "Xianmai"); }

    @Test @Order(542) @DisplayName("当归散证")
    void t_dangguisan() { assertFangzheng("当归散证", "Renshengbing", "Dangguisanzheng", "Dangguisan",
            "Renshen;Yichangfu", "Xumai"); }

    @Test @Order(543) @DisplayName("当归贝母苦参丸证")
    void t_dangguibeimukushenwan() { assertFangzheng("当归贝母苦参丸证", "Renshengbing", "Dangguibeimukushenwanzheng", "Dangguibeimukushenwan",
            "Renshenxiaobiannan;Yinshirugu", "Xumai"); }

    @Test @Order(544) @DisplayName("当归生姜羊肉汤证")
    void t_dangguishengjiangyangroutang() { assertFangzheng("当归生姜羊肉汤证", "Hanshanbing", "Dangguishengjiangyangroutangzheng", "Dangguishengjiangyangroutang",
            "Hanshanfutong;Xietongliji", "Xianjimai"); }

    @Test @Order(545) @DisplayName("内补当归建中汤证")
    void t_neibudangguijianzhongtang() { assertFangzheng("内补当归建中汤证", "Furenchanhoubing", "Neibudangguijianzhongtangzheng", "Neibudangguijianzhongtang",
            "Fuzhongcitong;Xixishaoqi;Shaofujimotong;Yinyaobeitong;Bunengyinshi", ""); }

    // ==========================================================================
    // 【家族 17】其他伤寒方（栀子类已列，此组为杂方）  @Order(581..700)
    // ==========================================================================

    @Test @Order(581) @DisplayName("旋覆代赭汤证")
    void t_xuanfudaizhetang() { assertFangzheng("旋覆代赭汤证", "Taiyangbing", "Xuanfudaizhetangzheng", "Xuanfudaizhetang",
            "Xinxiapiying;Aqibuchu", "Xianmai"); }

    @Test @Order(582) @DisplayName("十枣汤证")
    void t_shizaotang() { assertFangzheng("十枣汤证", "Taiyangbing", "Shizaotangzheng", "Shizaotang",
            "Xinxiapiyingman;Yinxiexiatong;Ganou;Duanqi", "Chenxianmai"); }

    @Test @Order(583) @DisplayName("赤石脂禹余粮汤证")
    void t_chishizhiyuyuliangtang() { assertFangzheng("赤石脂禹余粮汤证", "Taiyangbing", "Chishizhiyuyuliangtangzheng", "Chishizhiyuyuliangtang",
            "Xinxiapiying;Xialibuzhi;Fanzhibuyu", "Chenximai"); }

    @Test @Order(584) @DisplayName("瓜蒂散证")
    void t_guadisan() { assertFangzheng("瓜蒂散证", "Taiyangbing", "Guadisanzheng", "Guadisan",
            "Xiongzhongpiying;Qishangchonghouyan;Budexi", "Weifumai"); }

    @Test @Order(585) @DisplayName("三物白散证")
    void t_sanwubaisan() { assertFangzheng("三物白散证", "Taiyangbing", "Sanwubaisanzheng", "Sanwubaisan",
            "Hanshijiexiong;Wurezheng", "Chenjinmai"); }

    @Test @Order(586) @DisplayName("芍药甘草汤证")
    void t_shaoyaogancaotang() { assertFangzheng("芍药甘草汤证", "Taiyangbing", "Shaoyaogancaotangzheng", "Shaoyaogancaotang",
            "Jiaoluanji", "Xianmai"); }

    @Test @Order(587) @DisplayName("芍药甘草附子汤证")
    void t_shaoyaogancaofuzitang() { assertFangzheng("芍药甘草附子汤证", "Taiyangbing", "Shaoyaogancaofuzitangzheng", "Shaoyaogancaofuzitang",
            "Ehan;Hanchu", "Weimai"); }

    @Test @Order(588) @DisplayName("栀子豉汤证（阳明）")
    void t_zhizichitang_yangming() { assertFangzheng("栀子豉汤证", "Yangmingbing", "Zhizichitangzheng", "Zhizichitang",
            "Xinzhongaonao;Fanrebudemian;Shaoqi", "Fumai"); }

    @Test @Order(589) @DisplayName("蜜煎导方证")
    void t_mijiandaofang() { assertFangzheng("蜜煎导方证", "Yangmingbing", "Mijiandaofangzheng", "Mijiandaofang",
            "Dabianying;Xiaobianzili", ""); }

    @Test @Order(590) @DisplayName("猪胆汁方证")
    void t_zhudanzhifang() { assertFangzheng("猪胆汁方证", "Yangmingbing", "Zhudanzhifangzheng", "Zhudanzhifang",
            "Dabianying;Xiaobianzili", ""); }

    @Test @Order(591) @DisplayName("茵陈蒿汤证")
    void t_yinchenhaotang() { assertFangzheng("茵陈蒿汤证", "Yangmingbing", "Yinchenhaotangzheng", "Yinchenhaotang",
            "Shenhuang;Xiaobianbuli;Fuman;Kouke", "Chenshimai"); }

    @Test @Order(592) @DisplayName("茵陈五苓散证")
    void t_yinchenwulingsan() { assertFangzheng("茵陈五苓散证", "Huangdanbing", "Yinchenwulingsanzheng", "Yinchenwulingsan",
            "Huangdan;Xiaobianbuli", "Fumai"); }

    @Test @Order(593) @DisplayName("一物瓜蒂汤证")
    void t_yiwuguaditang() { assertFangzheng("一物瓜蒂汤证", "Taiyangzhongye", "Yiwuguaditangzheng", "Yiwuguaditang",
            "Shenretengzhong;Ehan", "Weimai"); }

    @Test @Order(594) @DisplayName("猪膏发煎证")
    void t_zhugaofajian() { assertFangzheng("猪膏发煎证", "Huangdanbing", "Zhugaofajianzheng", "Zhugaofajian",
            "Zhuhuang", "Fumai"); }

    @Test @Order(595) @DisplayName("硝石矾石散证")
    void t_xiaoshifanshisan() { assertFangzheng("硝石矾石散证", "Huangdanbing", "Xiaoshifanshisanzheng", "Xiaoshifanshisan",
            "Nvlaodan;Bangguangji;Shaofuman;Shenjinhuang;Eshanghei;Zuxiare", "Chenximai"); }

    @Test @Order(596) @DisplayName("柏叶汤证")
    void t_baiyetang() { assertFangzheng("柏叶汤证", "Tunvxiaxuebing", "Baiyetangzheng", "Baiyetang",
            "Tuxuebuzhi", "Xumai"); }

    @Test @Order(597) @DisplayName("黄土汤证")
    void t_huangtutang() { assertFangzheng("黄土汤证", "Xiaxuebing", "Huangtutangzheng", "Huangtutang",
            "Xiaxue;Xianbianhoubianxue;Mianseweihuang", "Ximai"); }

    @Test @Order(598) @DisplayName("赤小豆当归散证")
    void t_chixiaodoudangguisan() { assertFangzheng("赤小豆当归散证", "Xiaxuebing", "Chixiaodoudangguisanzheng", "Chixiaodoudangguisan",
            "Xiaxue;Xianxuehoubian", "Ximai"); }

    @Test @Order(599) @DisplayName("橘皮竹茹汤证")
    void t_jupizhurutang() { assertFangzheng("橘皮竹茹汤证", "Outuoyuexialibing", "Jupizhurutangzheng", "Jupizhurutang",
            "Yueni", "Xumai"); }

    @Test @Order(600) @DisplayName("橘皮汤证")
    void t_jupitang() { assertFangzheng("橘皮汤证", "Outuoyuexialibing", "Jupitangzheng", "Jupitang",
            "Ganou;Yueni;Shouzujue", "Xianmai"); }

    @Test @Order(601) @DisplayName("橘枳姜汤证")
    void t_juzhijiangtang() { assertFangzheng("橘枳姜汤证", "Xiongbibing", "Juzhijiangtangzheng", "Juzhijiangtang",
            "Xiongzhongqisai;Duanqi", "Chenximai"); }

    @Test @Order(602) @DisplayName("文蛤汤证")
    void t_wengetang() { assertFangzheng("文蛤汤证", "Outuoyuexialibing", "Wengetangzheng", "Wengetang",
            "Outu;Kouke;Yinshui;Toutong", "Jinmai"); }

    @Test @Order(603) @DisplayName("紫参汤证")
    void t_zishentang() { assertFangzheng("紫参汤证", "Outuoyuexialibing", "Zishentangzheng", "Zishentang",
            "Xiali;Feitong", "Chenmai"); }

    @Test @Order(604) @DisplayName("诃梨勒散证")
    void t_helilesan() { assertFangzheng("诃梨勒散证", "Outuoyuexialibing", "Helilesanzheng", "Helilesan",
            "Qili", "Chenmai"); }

    @Test @Order(605) @DisplayName("白头翁汤证")
    void t_baitouwengtang() { assertFangzheng("白头翁汤证", "Jueyinbing", "Baitouwengtangzheng", "Baitouwengtang",
            "Relixiazhong;Xialinongxue;Kouke", "Xianshumai"); }

    @Test @Order(606) @DisplayName("白头翁加甘草阿胶汤证")
    void t_baitouwengjiagancaojiaotang() {
        assertFangzheng("白头翁加甘草阿胶汤证",
                "Jueyinbing",
                "Baitouwengjiagancaojiaotangzheng",
                "Baitouwengjiagancaojiaotang",
                "Xiali;Xufan", "Weimai");
    }

    @Test @Order(607) @DisplayName("升麻鳖甲汤证")
    void t_shengmabiejiatang() { assertFangzheng("升麻鳖甲汤证", "Yinyangdu", "Shengmabiejiatangzheng", "Shengmabiejiatang",
            "Mianchibanbanrujinwen;Yanhoutong;Tunongxue", "Fumai"); }

    @Test @Order(608) @DisplayName("升麻鳖甲去雄黄蜀椒汤证")
    void t_shengmabiejiaquxionghuangshujiaotang() { assertFangzheng("升麻鳖甲去雄黄蜀椒汤证", "Yinyangdu", "Shengmabiejiaquxionghuangshujiaotangzheng", "Shengmabiejiaquxionghuangshujiaotang",
            "Mianmuqing;Shentongrupiang", ""); }

    @Test @Order(609) @DisplayName("苦参汤证（狐惑）")
    void t_kushentang() { assertFangzheng("苦参汤证（狐惑）", "Huhuobing", "Kushentangzheng", "Kushentang",
            "Yangan;Yinzhongshichuang", ""); }

    @Test @Order(610) @DisplayName("雄黄熏方证")
    void t_xionghuangxunfang() { assertFangzheng("雄黄熏方证", "Huhuobing", "Xionghuangxunfangzheng", "Xionghuangxunfang",
            "Yinzhongshichuang", ""); }

    @Test @Order(611) @DisplayName("鳖甲煎丸证")
    void t_biejiajianwan() { assertFangzheng("鳖甲煎丸证", "Nuebing", "Biejiajianwanzheng", "Biejiajianwan",
            "Nuem;Xiexiazhengjia", "Xianmai"); }

    @Test @Order(612) @DisplayName("蜀漆散证")
    void t_shuqisan() { assertFangzheng("蜀漆散证", "Nuebing", "Shuqisanzheng", "Shuqisan",
            "Duohan;Fahan", "Xianmai"); }

    @Test @Order(613) @DisplayName("牡蛎汤证")
    void t_mulitang() { assertFangzheng("牡蛎汤证", "Nuebing", "Mulitangzheng", "Mulitang",
            "Fahan;Duohan", ""); }

    @Test @Order(614) @DisplayName("牡蛎泽泻散证")
    void t_mulizexiesan() { assertFangzheng("牡蛎泽泻散证", "Chahoubing", "Mulizexiesanzheng", "Mulizexiesan",
            "Yaoyixiayoushuiqi;Xiaobianbuli", "Chenmai"); }

    @Test @Order(615) @DisplayName("乌梅丸证（复测）")
    void t_wumeiwan_retest() { assertFangzheng("乌梅丸证", "Jueyinbing", "Wumeiwanzheng", "Wumeiwan",
            "Xiaoke;Qicongshaofushangchongxin;Xinzhongtengre;Ji;Buyushi;Shizetuhui;Shouzuleng;Kouku", "Weiximai"); }

    // ==========================================================================
    // 【家族 18】金匮杂病（中风/虚劳/妇人/疮痈/虫证）  @Order(701..900)
    // ==========================================================================

    @Test @Order(701) @DisplayName("酸枣仁汤证")
    void t_suanzaorentang() { assertFangzheng("酸枣仁汤证", "Xulaobing", "Suanzaorentangzheng", "Suanzaorentang",
            "Xulaoxufanbudemian", "Xianximai"); }

    @Test @Order(702) @DisplayName("薯蓣丸证")
    void t_shuyuwan() { assertFangzheng("薯蓣丸证", "Xulaobing", "Shuyuwanzheng", "Shuyuwan",
            "Xulao;Fengqibaiji", "Xuruomai"); }

    @Test @Order(703) @DisplayName("肾气丸证")
    void t_shenqiwan() { assertFangzheng("肾气丸证", "Xulaobing", "Shenqiwanzheng", "Shenqiwan",
            "Xulaoyaotong;Shaofujuji;Xiaobianbuli", "Chenruomai"); }

    @Test @Order(704) @DisplayName("天雄散证")
    void t_tianxiongsan() { assertFangzheng("天雄散证", "Xulaobing", "Tianxiongsanzheng", "Tianxiongsan",
            "Shijing;Yaoxilengtong", ""); }

    @Test @Order(705) @DisplayName("小建中汤证")
    void t_xiaojianzhongtang() { assertFangzheng("小建中汤证", "Taiyinbing", "Xiaojianzhongtangzheng", "Xiaojianzhongtang",
            "Fuzhongjiaotong;Xinji;Fan", "Xiansemai"); }

    @Test @Order(706) @DisplayName("大建中汤证")
    void t_dajianzhongtang() { assertFangzheng("大建中汤证", "Taiyinbing", "Dajianzhongtangzheng", "Dajianzhongtang",
            "Xinxiongdahantong;Oubunengyinshi;Fuzhonghan", "Chenxianmai"); }

    @Test @Order(707) @DisplayName("炙甘草汤证")
    void t_zhigancaotang() { assertFangzheng("炙甘草汤证", "Taiyinbing", "Zhigancaotangzheng", "Zhigancaotang",
            "Xinji", "Jiedaimai"); }

    @Test @Order(708) @DisplayName("甘草干姜汤证")
    void t_gancaoganjiangtang() { assertFangzheng("甘草干姜汤证", "Feiweibing", "Gancaoganjiangtangzheng", "Gancaoganjiangtang",
            "Feiweituxianmo;Yiniao;Xiaobianshu;Buke", "Xumai"); }

    @Test @Order(709) @DisplayName("甘草干姜茯苓白术汤证")
    void t_gancaoganjiangfulingbaizhutang() { assertFangzheng("甘草干姜茯苓白术汤证", "Shenzhuobing", "Gancaoganjiangfulingbaizhutangzheng", "Gancaoganjiangfulingbaizhutang",
            "Shenzhuo;Yaozhongleng;Ruzuoshuizhong;Fuzhongrudaiwuqianqian", "Chenmai"); }

    @Test @Order(710) @DisplayName("甘麦大枣汤证")
    void t_ganmaidazaotang() { assertFangzheng("甘麦大枣汤证", "Furenzabing", "Ganmaidazaotangzheng", "Ganmaidazaotang",
            "Furenzangzao;Xibeishangyuku;Xiangrushenlingsuozuo;Shuqianshen", "Xumai"); }

    @Test @Order(711) @DisplayName("温经汤证")
    void t_wenjingtang() { assertFangzheng("温经汤证", "Furenzabing", "Wenjingtangzheng", "Wenjingtang",
            "Furennianwushisuo;Bingxialishushiribuzhi;Mujifare;Shaofuliji;Fuman;Shouzhangfanre;Chunkouganzao", "Xumai"); }

    @Test @Order(712) @DisplayName("土瓜根散证")
    void t_tuguagensan() { assertFangzheng("土瓜根散证", "Furenzabing", "Tuguagensanzheng", "Tuguagensan",
            "Daixia;Jingshuibuli;Shaofumantong;Jingyiyuezaijian", "Chenxianmai"); }

    @Test @Order(713) @DisplayName("蛇床子散证")
    void t_shechuangzisan() { assertFangzheng("蛇床子散证", "Furenzabing", "Shechuangzisanzheng", "Shechuangzisan",
            "Furenyinhan", "Chenximai"); }

    @Test @Order(714) @DisplayName("狼牙汤证")
    void t_langyatantang() { assertFangzheng("狼牙汤证", "Furenzabing", "Langyatantangzheng", "Langyatantang",
            "Shaoyinmaihuaershu;Yinzhongjishengchuang;Yinzhongshichuanglanzhe", "Huashumai"); }

    @Test @Order(715) @DisplayName("矾石丸证")
    void t_fanshiwan() { assertFangzheng("矾石丸证", "Furenzabing", "Fanshiwanzheng", "Fanshiwan",
            "Jingshuibibuli;Zangjianpibuzhi;Xiabaiwu", ""); }

    @Test @Order(716) @DisplayName("红蓝花酒证")
    void t_honglanhuajiu() { assertFangzheng("红蓝花酒证", "Furenzabing", "Honglanhuajiuzheng", "Honglanhuajiu",
            "Fuzhongxueqicitong", ""); }

    @Test @Order(717) @DisplayName("胶艾汤证")
    void t_jiaoaitang() { assertFangzheng("胶艾汤证", "Renshengbing", "Jiaoaitangzheng", "Jiaoaitang",
            "Furenlouxia;Banchanhouxiaxuebuduan;Renshenxiaxue", "Xumai"); }

    @Test @Order(718) @DisplayName("当归芍药散证（复测）")
    void t_dangguishaoyaosan_retest() { assertFangzheng("当归芍药散证", "Renshengbing", "Dangguishaoyaosanzheng", "Dangguishaoyaosan",
            "Furenhuaishen;Fuzhongjiaotong", "Xianmai"); }

    @Test @Order(719) @DisplayName("白术散证")
    void t_baizhusan() { assertFangzheng("白术散证", "Renshengbing", "Baizhusanzheng", "Baizhusan",
            "Renshenyangtai", "Xumai"); }

    @Test @Order(720) @DisplayName("竹叶汤证")
    void t_zhuyetang() { assertFangzheng("竹叶汤证", "Chanhoubing", "Zhuyetangzheng", "Zhuyetang",
            "Chanhouzhongfeng;Fare;Mianzhengchi;Chuan;Toutong", "Fumai"); }

    @Test @Order(721) @DisplayName("竹皮大丸证")
    void t_zhupidawan() { assertFangzheng("竹皮大丸证", "Chanhoubing", "Zhupidawanzheng", "Zhupidawan",
            "Furenruzhongxu;Fanluannouni", "Xumai"); }

    @Test @Order(722) @DisplayName("三物黄芩汤证")
    void t_sanwuhuangqintang() { assertFangzheng("三物黄芩汤证", "Furenchanhoubing", "Sanwuhuangqintangzheng", "Sanwuhuangqintang",
            "Toutong;Sizhikufanre;Butong", ""); }

    @Test @Order(723) @DisplayName("王不留行散证")
    void t_wangbuliuxingsan() { assertFangzheng("王不留行散证", "Jinchuangbing", "Wangbuliuxingsanzheng", "Wangbuliuxingsan",
            "Jinchuang", "Fumai"); }

    @Test @Order(724) @DisplayName("排脓散证")
    void t_painongsan() { assertFangzheng("排脓散证", "Jinchuangbing", "Painongsanzheng", "Painongsan",
            "Jinchuang", "Fumai"); }

    @Test @Order(725) @DisplayName("排脓汤证")
    void t_painongtang() { assertFangzheng("排脓汤证", "Jinchuangbing", "Painongtangzheng", "Painongtang",
            "Jinchuang", "Fumai"); }

    @Test @Order(726) @DisplayName("黄连粉证")
    void t_huanglianfen() { assertFangzheng("黄连粉证", "Chuangyongchangyongjinyinbing", "Huanglianfenzheng", "Huanglianfen",
            "Jinyinchung", ""); }

    @Test @Order(727) @DisplayName("鸡屎白散证")
    void t_jishibaisan() { assertFangzheng("鸡屎白散证", "Zhuanjinbing", "Jishibaisanzheng", "Jishibaisan",
            "Zhuanjin;Renbijiaozhi;Maishangxiuxing", "Weixianmai"); }

    @Test @Order(728) @DisplayName("蜘蛛散证")
    void t_zhizhusan() { assertFangzheng("蜘蛛散证", "Yinhushanbing", "Zhizhusanzheng", "Zhizhusan",
            "Yinhushan;Pianyouxiaoda;Shishishangxia", "Xianmai"); }

    @Test @Order(729) @DisplayName("甘草粉蜜汤证")
    void t_gancaofenmitang() { assertFangzheng("甘草粉蜜汤证", "Huichongbing", "Gancaofenmitangzheng", "Gancaofenmitang",
            "Huichong;Tuxian;Xintongfazuoyoushi", "Xianmai"); }

    @Test @Order(730) @DisplayName("奔豚汤证")
    void t_bentuntang() { assertFangzheng("奔豚汤证", "Bentunbing", "Bentuntangzheng", "Bentuntang",
            "Bentunqishangchongxiong;Futong;Wanglaihanre", "Xianmai"); }

    @Test @Order(731) @DisplayName("旋覆花汤证")
    void t_xuanfuhuatang() { assertFangzheng("旋覆花汤证", "Ganzhuobing", "Xuanfuhuatangzheng", "Xuanfuhuatang",
            "Ganzhe;Changyudaoqixiongshang;Danyuyinre", "Xianmai"); }

    @Test @Order(732) @DisplayName("皂荚丸证")
    void t_zaojiawan() { assertFangzheng("皂荚丸证", "Feiweifeiyongkesoushangqi", "Zaojiawanzheng", "Zaojiawan",
            "Kenishangqi;Shishituzhuo", ""); }

    @Test @Order(733) @DisplayName("千金苇茎汤证")
    void t_qianjinweijingtang() { assertFangzheng("千金苇茎汤证", "Feiyongbing", "Qianjinweijingtangzheng", "Qianjinweijingtang",
            "Keyouweire;Fanman;Xiongzhongjiacuo", ""); }

    @Test @Order(734) @DisplayName("葶苈大枣泻肺汤证")
    void t_tinglidazaoxiefeitang() { assertFangzheng("葶苈大枣泻肺汤证", "Feiyongbing", "Tinglidazaoxiefeitangzheng", "Tinglidazaoxiefeitang",
            "Feiyong;Chuanbudewo;Xiongmanzhang", "Xumai"); }

    @Test @Order(735) @DisplayName("麦门冬汤证")
    void t_maimendongtang() { assertFangzheng("麦门冬汤证", "Kesoushangqibing", "Maimendongtangzheng", "Maimendongtang",
            "Huonishangqi;Yanhoubuli", "Xumai"); }

    @Test @Order(736) @DisplayName("泽漆汤证")
    void t_zeqitang() { assertFangzheng("泽漆汤证", "Kesoushangqibing", "Zeqitangzheng", "Zeqitang",
            "Kesou;Chuan", "Chenmai"); }

    @Test @Order(737) @DisplayName("人参汤证（胸痹）")
    void t_renshentang() { assertFangzheng("人参汤证（胸痹）", "Xiongbibing", "Renshentangzheng", "Lizhongtang",
            "Xiongbi;Xinzhongpiqi;Xiongman;Xiexianiqiangxin", "Chenximai"); }

    @Test @Order(738) @DisplayName("大建中汤证（复测）")
    void t_dajianzhongtang_retest() { assertFangzheng("大建中汤证", "Taiyinbing", "Dajianzhongtangzheng", "Dajianzhongtang",
            "Xinxiongdahantong;Oubunengyinshi;Fuzhonghan", "Chenxianmai"); }

    @Test @Order(739) @DisplayName("侯氏黑散证")
    void t_houshiheisan() { assertFangzheng("侯氏黑散证", "Zhongfengbing", "Houshiheisanzheng", "Houshiheisan",
            "Dafeng;Sizhifanzhong;Xinzhongehanbuzu", "Fumai"); }

    @Test @Order(740) @DisplayName("风引汤证")
    void t_fengyintang() { assertFangzheng("风引汤证", "Zhongfengbing", "Fengyintangzheng", "Fengyintang",
            "Churetanxian", "Shumai"); }

    @Test @Order(741) @DisplayName("防己地黄汤证")
    void t_fangjidihuangtang() { assertFangzheng("防己地黄汤证", "Zhongfengbing", "Fangjidihuangtangzheng", "Fangjidihuangtang",
            "Rukuangzhuang;Wangxing;Duyubuxiu;Wuhanre", "Fumai"); }

    @Test @Order(742) @DisplayName("头风摩散证")
    void t_toufengmosan() { assertFangzheng("头风摩散证", "Zhongfengbing", "Toufengmosanzheng", "Toufengmosan",
            "Toufeng", ""); }

    @Test @Order(743) @DisplayName("矾石汤证")
    void t_fanshitang() { assertFangzheng("矾石汤证", "Zhongfengbing", "Fanshitangzheng", "Fanshitang",
            "Jiaoqichongxin", ""); }

    @Test @Order(744) @DisplayName("续命汤证")
    void t_xumingtang() { assertFangzheng("续命汤证", "Zhongfengbing", "Xumingtangzheng", "Xumingtang",
            "Shentibunengzishouchi;Koujinbunengyan", ""); }

    @Test @Order(745) @DisplayName("三黄汤证")
    void t_sanhuangtang() { assertFangzheng("三黄汤证", "Zhongfengbing", "Sanhuangtangzheng", "Sanhuangtang",
            "Shouzujuji;Baijietengtong", ""); }

    @Test @Order(746) @DisplayName("术附汤证")
    void t_shufutang() { assertFangzheng("术附汤证", "Zhongfengbing", "Zhufutangzheng", "Zhufutang",
            "Touzhongxuan;Kujizhiyuandi", ""); }

    // ==========================================================================
    // 【家族 19】合病检测  @Order(901..910)
    // ==========================================================================

    @Test @Order(901)
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
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyangbing");
        assertThat(vars.get("sixChannel")).isEqualTo("太阳少阳合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太阳少阳合病");
    }

    @Test @Order(902)
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
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyinbing");
        assertThat(vars.get("sixChannel")).isEqualTo("太少两感");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太少两感");
        assertThat(vars.get("fangzheng")).isEqualTo("Mahuangfuzixixintangzheng");
        assertThat(vars.get("finalFormula")).isEqualTo(NS + "Mahuangfuzixixintang");
    }

    @Test @Order(903)
    @DisplayName("麻黄汤证诊断，主证不全返回TOP1匹配的症状")
    void shouldDiagnoseMahuangTangPatternWithouthEnoughSym() {
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

    @SuppressWarnings("unchecked")
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

        if (vars.get("candidateFangzhengs") != null) {
            List<String> candidateCn = getChineseListOrOriginal(vars, "candidateFangzhengs", "candidateFangzhengsCn");
            System.out.println("候选方证：" + candidateCn);
            System.out.println("候选方证打分：" + vars.get("candidateScores"));
        }
        if (vars.get("jianJiaZhengs") != null) {
            List<String> jianjiaCn = getChineseListOrOriginal(vars, "jianJiaZhengs", "jianJiaZhengsCn");
            System.out.println("兼夹证：" + jianjiaCn);
        }
        if (vars.get("addedHerb") != null) {
            List<String> addHerbsCn = getChineseListOrOriginal(vars, "addedHerb", "addedHerbCn");
            System.out.println("加味药物：" + addHerbsCn);
        }
        if (vars.get("removedHerb") != null) {
            List<String> removedCn = getChineseListOrOriginal(
                    vars, "removedHerb", "removedHerbCn");
            if (!removedCn.isEmpty()) {
                System.out.println("减味药物：" + removedCn);
            }
        }
        if (vars.get("warnings") != null) {
            System.out.println("配伍禁忌警告：" + vars.get("warnings"));
        }
    }

    @SuppressWarnings("unchecked")
    private void assertBasicResult(ProcessInstanceResult result,
                                   String expectedSixChannel,
                                   String expectedFangzheng,
                                   String expectedFormula) {
        Map<String, Object> vars = result.getVariablesAsMap();

        if (expectedSixChannel != null) {
            List<String> liujingTypes = (List<String>) vars.get("liujingTypes");
            if (liujingTypes != null && liujingTypes.size() > 1) {
                String sixChannel = (String) vars.get("sixChannel");
                String combinedDiseaseMark = (String) vars.get("combinedDiseaseMark");
                assertThat(sixChannel).isNotNull();
                assertThat(sixChannel).isEqualTo(combinedDiseaseMark);
                assertThat(liujingTypes).contains(expectedSixChannel);
            } else {
                assertThat(vars.get("sixChannel")).isEqualTo(expectedSixChannel);
            }
        }

        assertThat(vars.get("fangzheng")).isEqualTo(expectedFangzheng);
        assertThat(vars.get("finalFormula")).isEqualTo(expectedFormula);
    }

    @SuppressWarnings("unchecked")
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

    // ==================== 六经锚点配置 ====================

    private static final Map<String, List<String>> LJ_ANCHOR_SYMPTOMS = Map.of(
            "Taiyangbing",  List.of("Ehan"),
            "Yangmingbing", List.of("Kouke"),
            "Shaoyangbing", List.of("Wanglaihanre", "Xiongxiekuman"),
            "Taiyinbing",   List.of("Fuman"),
            "Shaoyinbing",  List.of("Danyumei"),
            "Jueyinbing",   List.of("Xiaoke", "Shouzuleng")
    );
    private static final Map<String, List<String>> LJ_ANCHOR_PULSES = Map.of(
            "Taiyangbing",  List.of("Fumai"),
            "Yangmingbing", List.of("Hongmai"),
            "Shaoyangbing", List.of("Xianmai"),
            "Taiyinbing",   List.of("Ruomai"),
            "Shaoyinbing",  List.of("Chenweimai"),
            "Jueyinbing",   List.of("Weiximai")
    );

    private static final Set<String> SIX_CHANNELS = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing"
    );

    /** 判断 lj 是否是六经（含合病）——只有六经才注入锚点并断言六经。 */
    private static boolean isLiujing(String lj) {
        if (lj == null) return false;
        if (SIX_CHANNELS.contains(lj)) return true;
        if (lj.endsWith("hebing") || lj.endsWith("Hebing")) return true;
        return false;
    }

    private void assertFangzheng(String name, String lj, String fz, String formula,
                                 String syms, String pulses) {
        assertFangzheng(name, lj, fz, formula, syms, pulses, "", "");
    }

    private void assertFangzheng(String name, String lj, String fz, String formula,
                                 String syms, String pulses, String tongues, String fuzhengs) {
        List<String> symList = new ArrayList<>(parseIris(syms));
        List<String> pulseList = new ArrayList<>(parseIris(pulses));

        if (isLiujing(lj)) {
            for (Map.Entry<String, List<String>> e : LJ_ANCHOR_SYMPTOMS.entrySet()) {
                if (lj.contains(e.getKey())) {
                    for (String s : e.getValue()) {
                        String iri = NS + s + "_instance";
                        if (!symList.contains(iri)) symList.add(iri);
                    }
                }
            }
            for (Map.Entry<String, List<String>> e : LJ_ANCHOR_PULSES.entrySet()) {
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

    private List<String> parseIris(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(";"))
                .filter(x -> !x.isBlank())
                .map(x -> NS + x + "_instance")
                .collect(Collectors.toList());
    }
}