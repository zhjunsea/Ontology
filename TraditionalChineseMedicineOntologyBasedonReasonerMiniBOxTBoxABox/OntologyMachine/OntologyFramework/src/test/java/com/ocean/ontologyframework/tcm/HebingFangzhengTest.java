package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("【合病】太阳少阳合病/太少两感/主证不全兜底")
class HebingFangzhengTest extends AbstractJingfangDiagnosisTest {

    @Test @Order(901) @DisplayName("太阳少阳合病检测")
    @SuppressWarnings("unchecked")
    void shouldDetectTaiyangShaoyangHebing() {
        Map<String, Object> variables = Map.of(
                "symptomIris", List.of(
                        NS + "Fare_instance", NS + "Ehan_instance",
                        NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance",
                        NS + "Kouku_instance"),
                "pulseIris", List.of(NS + "Fumai_instance", NS + "Xianmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult result = startProcessAndGetResult(variables);
        printResult("太阳少阳合病", result);

        Map<String, Object> vars = result.getVariablesAsMap();
        assertThat(vars.get("isCombinedChannel")).isEqualTo(true);
        assertThat((List<String>) vars.get("liujingTypes"))
                .containsExactlyInAnyOrder("Taiyangbing", "Shaoyangbing");
        assertThat(vars.get("sixChannel")).isEqualTo("太阳少阳合病");
        assertThat(vars.get("combinedDiseaseMark")).isEqualTo("太阳少阳合病");
    }

    // ============ 从太阳方证迁移过来的合方 (904~906) ============

    @Test @Order(904) @DisplayName("桂枝二麻黄一汤证（合方）")
    void t_guizhiermahuangyitang_hebing() { assertFangzheng("桂枝二麻黄一汤证", "Taiyangbing",
            "Guizhiermahuangyitangzheng", "Guizhiermahuangyitang",
            "Fareehan;Runvezhuang;Yirizaifa", "Fumai"); }

    @Test @Order(905) @DisplayName("桂枝麻黄各半汤证（合方）")
    void t_guizhimahuanggebantang_hebing() { assertFangzheng("桂枝麻黄各半汤证", "Taiyangbing",
            "Guizhimahuanggebantangzheng", "Guizhimahuanggebantang",
            "Fareehan;Mianyourese;Shenyang", "Fumai"); }

    @Test @Order(906) @DisplayName("桂枝二越婢一汤证（合方）")
    void t_guizhieryuebiyitang_hebing() { assertFangzheng("桂枝二越婢一汤证", "Taiyangbing",
            "Guizhieryuebiyitangzheng", "Guizhieryuebiyitang",
            "Fareehan;Remianre;Kekou", "Weimai"); }

    // ============ 从少阳阳明方证迁移过来的合方 (907) ============

    @Test @Order(907) @DisplayName("厚朴七物汤证（合方）")
    void t_houpoqiwutang() { assertFangzheng("厚朴七物汤证", "TaiyangYangmingHebing",
            "Houpoqiwutangzheng", "Houpoqiwutang",
            "Fuman;Fare;Yinshirugu;Ehan;Wuhan;Dabianying;Chaore", "Fushumai"); }

    @Test @Order(75) @DisplayName("柴胡桂枝汤证")
    void t_chaihuguizhitang() { assertFangzheng("柴胡桂枝汤证", "TaiyangShaoyangHebing",
            "Chaihuguizhitangzheng", "Chaihuguizhitang",
            "Fare;Ehan;Wanglaihanre;Xiongxiekuman;Kouku;Gujietengfan;Xinxiazhijie;Weiou", "Fumai;Xianmai"); }

    @Test @Order(220) @DisplayName("乌头桂枝汤证")
    void t_wutouguizhitang() { assertFangzheng("乌头桂枝汤证", "Hanshanbing",
            "Wutouguizhitangzheng", "Wutouguizhitang",
            "Hanshanfutong;Nishen;Shouzuburen;Shentengtong", "Chenjinmai"); }
}