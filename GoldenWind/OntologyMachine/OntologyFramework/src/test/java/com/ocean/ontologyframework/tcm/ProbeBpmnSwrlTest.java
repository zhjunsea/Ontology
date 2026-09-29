package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.NS;
import static com.ocean.ontologyframework.tcm.JingfangTestSupport.startProcessAndGetResult;

@DisplayName("探针：BPMN 端到端加减药输出")
class ProbeBpmnSwrlTest extends AbstractJingfangDiagnosisTest {

    @Test
    @DisplayName("桂枝加葛根汤证")
    void probeGuizhijiagegen() {
        Map<String, Object> vars = Map.of(
                "symptomIris", List.of(
                        NS + "Xiangbeiqiangjiji_instance", NS + "Hanchu_instance", NS + "Efeng_instance"),
                "pulseIris", List.of(NS + "Fumai_instance", NS + "Huanmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult r = startProcessAndGetResult(vars);
        dump("桂枝加葛根汤证", r);
    }

    @Test
    @DisplayName("小柴胡汤证 + 咳")
    void probeXiaochaihuCough() {
        Map<String, Object> vars = Map.of(
                "symptomIris", List.of(
                        NS + "Wanglaihanre_instance", NS + "Xiongxiekuman_instance",
                        NS + "Kouku_instance", NS + "Kesou_instance"),
                "pulseIris", List.of(NS + "Xianmai_instance"),
                "tongueIris", List.of(), "fuzhengIris", List.of());
        ProcessInstanceResult r = startProcessAndGetResult(vars);
        dump("小柴胡汤证+咳", r);
    }

    @SuppressWarnings("unchecked")
    private static void dump(String name, ProcessInstanceResult r) {
        Map<String, Object> v = r.getVariablesAsMap();
        System.out.println("\n===== PROBE " + name + " =====");
        System.out.println("fangzheng      = " + v.get("fangzheng"));
        System.out.println("finalFormula   = " + v.get("finalFormula"));
        System.out.println("herbsCn        = " + v.get("herbsCn"));
        System.out.println("addedHerbCn    = " + v.get("addedHerbCn"));
        System.out.println("removedHerbCn  = " + v.get("removedHerbCn"));
        System.out.println("derived        = " + v.get("derived"));
        System.out.println("appliedRules   = " + v.get("appliedRules"));
        System.out.println("ruleSources    = " + v.get("ruleSources"));
        System.out.println("dosageChanges  = " + v.get("dosageChanges"));
        System.out.println("yaozhengApplied= " + v.get("yaozhengApplied"));
        System.out.println("liujingTypes   = " + v.get("liujingTypes"));
        System.out.println("sixChannel     = " + v.get("sixChannel"));
    }
}
