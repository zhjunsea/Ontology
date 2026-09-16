package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("【兼夹】瘀血/痰饮/气郁等兼夹证检测")
class JianjiaFangzhengTest extends AbstractJingfangDiagnosisTest {

    @Test @Order(80) @DisplayName("小柴胡汤证夹瘀血检测")
    @SuppressWarnings("unchecked")
    void shouldDetectYuXueJianJiaZhengWithXiaoChaihuTang() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("symptomIris", List.of(
                NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance",
                NS + "Kouku_instance", NS + "Citong_instance", NS + "Xiongman_instance"));
        variables.put("pulseIris", List.of(NS + "Xianmai_instance", NS + "Semai_instance"));
        variables.put("tongueIris", List.of(NS + "BlueTongue_instance"));
        variables.put("fuzhengIris", List.of());

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证夹瘀血", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Xiaochaihutangzheng");
        assertThat((List<String>) vars.get("jianJiaZhengs")).containsExactly("Yuxuezheng");
        assertThat((List<String>) vars.get("addedHerb"))
                .contains(NS + "Danshen", NS + "Taoren");
    }

    @Test @Order(81) @DisplayName("小柴胡汤证夹痰饮检测")
    @SuppressWarnings("unchecked")
    void shouldDetectTanYinJianJiaZhengWithXiaoChaihuTang() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("symptomIris", List.of(
                NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance",
                NS + "Kouku_instance", NS + "Touxuan_instance", NS + "Xinji_instance"));
        variables.put("pulseIris", List.of(
                NS + "Xianmai_instance", NS + "Chenxianmai_instance"));
        variables.put("tongueIris", List.of(
                NS + "SlipperyCoating_instance", NS + "GreasyCoating_instance"));
        variables.put("fuzhengIris", List.of());

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证夹痰饮", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Xiaochaihutangzheng");
        assertThat((List<String>) vars.get("jianJiaZhengs")).containsExactly("Tanyinzheng");
        assertThat((List<String>) vars.get("addedHerb"))
                .contains(NS + "Banxia", NS + "Fuling");
    }

    @Test @Order(82) @DisplayName("大柴胡汤证夹痰饮检测")
    @SuppressWarnings("unchecked")
    void shouldDetectTanYinJianJiaZhengWithDaChaihuTang() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("symptomIris", List.of(
                NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance",
                NS + "Xinxiaji_instance", NS + "Outubuzhi_instance",
                NS + "Yuyuweifan_instance", NS + "Dabianying_instance",
                NS + "Touxuan_instance", NS + "Xinji_instance"));
        variables.put("pulseIris", List.of(
                NS + "Xianmai_instance", NS + "Chenxianmai_instance"));
        variables.put("tongueIris", List.of(
                NS + "SlipperyCoating_instance", NS + "GreasyCoating_instance"));
        variables.put("fuzhengIris", List.of(NS + "Xinxiaanzhimantong_instance"));

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("大柴胡汤证夹痰饮", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Dachaihutangzheng");
        assertThat((List<String>) vars.get("jianJiaZhengs")).containsExactly("Tanyinzheng");
        assertThat((List<String>) vars.get("addedHerb"))
                .contains(NS + "Banxia", NS + "Fuling");
    }

    @Test @Order(83) @DisplayName("小柴胡汤证夹气郁检测")
    @SuppressWarnings("unchecked")
    void shouldDetectQiYuJianJiaZhengWithXiaoChaihuTang() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("symptomIris", List.of(
                NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance",
                NS + "Kouku_instance", NS + "Momo_instance", NS + "Buyushi_instance",
                NS + "Xinfan_instance", NS + "Xiou_instance",
                NS + "Shantaixi_instance", NS + "Yanzhongruyouzhiluan_instance"));
        variables.put("pulseIris", List.of(NS + "Xianmai_instance"));
        variables.put("tongueIris", List.of());
        variables.put("fuzhengIris", List.of());

        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("小柴胡汤证夹气郁", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("fangzheng")).isEqualTo("Xiaochaihutangzheng");
        assertThat((List<String>) vars.get("jianJiaZhengs")).containsExactly("Qiyuzheng");
        assertThat((List<String>) vars.get("addedHerb"))
                .contains(NS + "Xiangfu", NS + "Yujin");
    }
}