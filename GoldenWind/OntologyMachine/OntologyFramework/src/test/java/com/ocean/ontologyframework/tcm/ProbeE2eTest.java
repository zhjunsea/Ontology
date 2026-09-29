package com.ocean.ontologyframework.tcm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ocean.ontologyframework.tcm.JingfangTestSupport.NS;
import static com.ocean.ontologyframework.tcm.JingfangTestSupport.startProcessAndGetResult;

@DisplayName("探针：读取 e2e_generated.json 验证真实 BPMN 端到端")
class ProbeE2eTest extends AbstractJingfangDiagnosisTest {

    private static final Path JSON = Path.of(
            "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedOnReasonerMiniBOxTBoxABox/"
                    + "HWCodeArtsDir/scripts/e2e_generated.json");
    private static final Path OUT = Path.of(
            "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedOnReasonerMiniBOxTBoxABox/"
                    + "HWCodeArtsDir/scripts/e2e_actual.json");

    @Test
    @DisplayName("跑全部用例并落盘实际输出")
    void probeAll() throws Exception {
        ObjectMapper om = new ObjectMapper();
        JsonNode root = om.readTree(JSON.toFile());
        JsonNode cases = root.get("cases");
        ArrayNode actual = om.createArrayNode();
        for (int i = 0; i < cases.size(); i++) {
            JsonNode c = cases.get(i);
            List<String> syms = iris(c.get("input").get("syms"));
            List<String> pulses = iris(c.get("input").get("pulses"));
            List<String> tongues = iris(c.get("input").get("tongues"));
            ObjectNode row = om.createObjectNode();
            row.put("idx", i);
            row.put("fangzheng", c.get("fangzheng").asText());
            row.set("expected", c.get("expected"));
            try {
                Map<String, Object> vars = JingfangTestSupport.buildAnchoredVars(
                        c.get("lj").asText(), syms, pulses, tongues, List.of());
                ProcessInstanceResult r = startProcessAndGetResult(vars);
                Map<String, Object> v = r.getVariablesAsMap();
                row.put("actualFangzheng", String.valueOf(v.get("fangzheng")));
                row.put("addedHerb", String.valueOf(v.get("addedHerb")));
                row.put("removedHerb", String.valueOf(v.get("removedHerb")));
                row.put("addedHerbCn", String.valueOf(v.get("addedHerbCn")));
                row.put("removedHerbCn", String.valueOf(v.get("removedHerbCn")));
                row.put("dosageChanges", String.valueOf(v.get("dosageChanges")));
                row.put("baseHerbs", String.valueOf(v.get("baseHerbs")));
            } catch (Exception e) {
                row.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
            actual.add(row);
            System.out.println("[probe] " + i + " " + c.get("fangzheng").asText()
                    + " -> " + row.get("actualFangzheng"));
        }
        om.writerWithDefaultPrettyPrinter().writeValue(OUT.toFile(), actual);
        System.out.println("[probe] written " + OUT);
    }

    private static List<String> iris(JsonNode arr) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : arr) out.add(NS + n.asText() + "_instance");
        return out;
    }
}
