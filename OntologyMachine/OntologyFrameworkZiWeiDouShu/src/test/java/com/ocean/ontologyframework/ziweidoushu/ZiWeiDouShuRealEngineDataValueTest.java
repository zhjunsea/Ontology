package com.ocean.ontologyframework.ziweidoushu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ocean.utilities.ProcessOrchestrator;
import com.ocean.utilities.ServiceProbe;
import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 紫微斗数 · 真实引擎「数据值」断言：对 67 个覆盖全部星盘/命格类型 的命例，
 * 断言运行输出的核心数据（命宫、五行局）与 hunt 真值一致，且命格档次合法。
 * 补足此前仅“跑通完成”的矩阵——本类校验“判对”（数据分支的值正确性）。
 */
@SpringBootTest(classes = ZiWeiDouShuApplication.class)
@ActiveProfiles("ZiWeiDouShuBPMN")
@Timeout(value = 2400, unit = TimeUnit.SECONDS)
@DisplayName("紫微斗数·真实引擎数据值断言（命宫/五行局/命格档 判对）")
class ZiWeiDouShuRealEngineDataValueTest {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuRealEngineDataValueTest.class);
    private static final String PROCESS_ID = "ZiWeiDouShuChartProcess";
    private static final Duration WAIT = Duration.ofMinutes(2);

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @Autowired
    private CamundaClient client;

    @BeforeAll
    static void requireInfra() {
        ServiceProbe.assumeAllReachable("数据值断言依赖（Camunda/Ontop/MySQL）",
                ServiceProbe.LOCALHOST, 9080, 8080, 3306);
    }

    @BeforeEach
    void deploy() {
        assertThat(Files.isRegularFile(Path.of(bpmnPath))).as("BPMN 应存在: %s", bpmnPath).isTrue();
        assertThat(ProcessOrchestrator.deployIfAbsent(client, PROCESS_ID, List.of(bpmnPath)))
                .as("应成功部署流程").isTrue();
    }

    @Test
    @DisplayName("67 例全类型：命宫/五行局 == hunt真值，且命格档合法")
    void dataValues() throws Exception {
        List<Map<String, Object>> cases;
        try (InputStream in = new ClassPathResource("ziweidoushu/hunt_cases.json").getInputStream()) {
            cases = new ObjectMapper().readValue(in, new TypeReference<List<Map<String, Object>>>() { });
        }
        List<String> failures = new ArrayList<>();
        int ok = 0;
        for (Map<String, Object> c : cases) {
            String id = String.valueOf(c.getOrDefault("id", "?"));
            String expMing = str(c.get("expectMing"));
            String expBureau = str(c.get("expectBureau"));
            Map<String, Object> vars;
            try {
                vars = runAndAwaitCompleted(buildVars(c));
            } catch (Throwable t) {
                failures.add(id + " 未完成: " + t.getMessage());
                continue;
            }
            String skeleton = str(vars.get("chartSkeleton"));
            String grade = str(vars.get("patternGrade"));
            boolean mingOk = expMing == null || (skeleton != null && skeleton.contains("命宫=" + expMing));
            boolean bureauOk = expBureau == null || (skeleton != null && skeleton.contains("五行局=" + expBureau));
            boolean gradeOk = grade != null && (grade.contains("上格") || grade.contains("中格")
                    || grade.contains("下格") || grade.contains("常格"));
            if (!mingOk) failures.add(id + " 命宫≠" + expMing + " | skeleton=" + cut(skeleton));
            if (!bureauOk) failures.add(id + " 五行局≠" + expBureau + " | skeleton=" + cut(skeleton));
            if (!gradeOk) failures.add(id + " 命格档非法/空 | grade=" + cut(grade));
            if (mingOk && bureauOk && gradeOk) ok++;
        }
        log.info("数据值断言：{} / {} 通过", ok, cases.size());
        assertThat(failures).as("数据值断言失败：\n" + String.join("\n", failures)).isEmpty();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String cut(String s) {
        if (s == null) return "null";
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    private Map<String, Object> buildVars(Map<String, Object> c) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("birth", str(c.get("birth")));
        v.put("calendar", "公历");
        v.put("gender", str(c.get("gender")));
        v.put("birthPlace", "北京 116.4E");
        v.put("inputMode", str(c.getOrDefault("mode", "直接排盘")));
        v.put("subjectType", str(c.getOrDefault("subjectType", "成人男")));
        v.put("depth", str(c.getOrDefault("depth", "本命")));
        v.put("granularity", "年");
        return v;
    }

    private Map<String, Object> runAndAwaitCompleted(Map<String, Object> vars) {
        long key = ProcessOrchestrator.start(client, PROCESS_ID, vars);
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, key);
            String status = info == null ? "RUNNING"
                    : ProcessOrchestrator.statusOf(info.state(), info.hasIncident());
            if ("COMPLETED".equals(status)) {
                return ProcessOrchestrator.readVariables(client, key);
            }
            if ("INCIDENT".equals(status) || "TERMINATED".equals(status)) {
                throw new AssertionError("status=" + status + " key=" + key);
            }
            try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        throw new AssertionError("流程未在 " + WAIT + " 内完成 key=" + key);
    }
}
