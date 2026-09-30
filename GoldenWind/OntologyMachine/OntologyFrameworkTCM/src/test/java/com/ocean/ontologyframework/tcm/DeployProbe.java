package com.ocean.ontologyframework.tcm;

import java.util.List;
import java.util.Map;

/** 诊断探针：验证 ensureInitialized 与一次完整流程调用是否可用。 */
public final class DeployProbe {
    public static void main(String[] args) {
        try {
            System.out.println(">>> calling ensureInitialized()");
            JingfangTestSupport.ensureInitialized();
            System.out.println(">>> ensureInitialized OK");
        } catch (Throwable t) {
            System.out.println(">>> ensureInitialized FAILED: " + t);
            t.printStackTrace(System.out);
            return;
        }
        try {
            System.out.println(">>> starting process instance (桂枝汤证)");
            Map<String, Object> vars = Map.of(
                    "symptomIris", List.of(
                            JingfangTestSupport.NS + "Fare_instance",
                            JingfangTestSupport.NS + "Ehan_instance"),
                    "pulseIris", List.of(JingfangTestSupport.NS + "Fumai_instance"),
                    "tongueIris", List.of(),
                    "fuzhengIris", List.of());
            var r = JingfangTestSupport.startProcessAndGetResult(vars);
            System.out.println(">>> process OK: " + r.getVariablesAsMap().get("fangzheng"));
        } catch (Throwable t) {
            System.out.println(">>> process FAILED: " + t);
            t.printStackTrace(System.out);
        }
        Runtime.getRuntime().halt(0);
    }
}
