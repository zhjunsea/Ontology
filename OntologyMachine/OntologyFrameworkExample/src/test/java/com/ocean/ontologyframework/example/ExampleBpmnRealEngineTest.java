package com.ocean.ontologyframework.example;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.utilities.ProcessOrchestrator;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = ExampleApplication.class)
@ActiveProfiles("ExampleBPMN")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 300, unit = TimeUnit.SECONDS)
@DisplayName("示例模块 BPMN · 真实引擎端到端（连 localhost:26500，生产 ExampleOntologyJobWorker 消费）")
class ExampleBpmnRealEngineTest {

    private static final Logger log = LoggerFactory.getLogger(ExampleBpmnRealEngineTest.class);

    private static final String BAKING_PROCESS_ID = "PizzaMakingStandardProcess";
    private static final String PIZZA_TYPE_ITALIAN_STYLE =
            "http://example.org/pizza/core/ItalianStyleWhiteSeafoodPizza";
    private static final String PIZZA_TYPE_MARGHERITA =
            "http://example.org/pizza/core/MargheritaPizza";
    private static final String PIZZA_TYPE_VEGETARIAN =
            "http://example.org/pizza/core/VegetarianPizza";
    private static final Duration WAIT = Duration.ofMinutes(2);

    @Value("${ontology.bpmn-path}")
    private String bakingBpmn;

    @Autowired
    private CamundaClient client;

    @BeforeEach
    void deploy() {
        assertThat(Files.isRegularFile(Path.of(bakingBpmn)))
                .as("BPMN 文件应存在: %s", bakingBpmn).isTrue();

        boolean deployed = ProcessOrchestrator.deployIfAbsent(client, BAKING_PROCESS_ID, List.of(bakingBpmn));
        assertThat(deployed).as("应成功部署流程").isTrue();
        log.info("✅ 已部署 BPMN 资源 | {}", bakingBpmn);
    }

    @Test
    @Order(1)
    @DisplayName("烘焙流程·ItalianStyleWhiteSeafoodPizza：真实引擎全流程完成，生产 worker 消费全部 serviceTask")
    void bakingProcessCompletesItalianStyle() {
        runBaking(PIZZA_TYPE_ITALIAN_STYLE);
    }

    @Test
    @Order(2)
    @DisplayName("烘焙流程·MargheritaPizza：真实引擎全流程完成，生产 worker 消费全部 serviceTask")
    void bakingProcessCompletesMargherita() {
        runBaking(PIZZA_TYPE_MARGHERITA);
    }

    @Test
    @Order(3)
    @DisplayName("烘焙流程·VegetarianPizza：真实引擎全流程完成，生产 worker 消费全部 serviceTask")
    void bakingProcessCompletesVegetarian() {
        runBaking(PIZZA_TYPE_VEGETARIAN);
    }

    private void runBaking(String pizzaType) {
        long instanceKey = ProcessOrchestrator.start(client, BAKING_PROCESS_ID, Map.of("pizzaType", pizzaType));

        awaitCompleted(instanceKey);
        log.info("✅ 烘焙流程真实引擎完成 | pizzaType={} | key={}", pizzaType, instanceKey);
    }

    private void awaitCompleted(long key) {
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (isCompleted(key)) {
                return;
            }
            sleep();
        }
        throw new AssertionError("流程未在 " + WAIT + " 内完成 | key=" + key);
    }

    private boolean isCompleted(long key) {
        ProcessOrchestrator.InstanceInfo info = ProcessOrchestrator.fetchInstance(client, key);
        return info != null && "COMPLETED".equals(ProcessOrchestrator.statusOf(info.state(), info.hasIncident()));
    }

    private static void sleep() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @AfterAll
    static void cleanupWrittenPizzaInstances() {
        try {
            OBDAHandler.getInstance().executeInTransaction(conn -> {
                try (Statement st = conn.createStatement()) {
                    int rows = st.executeUpdate("DELETE FROM myPizza WHERE name LIKE 'MyPizza%'");
                    log.info("🧹 已清理流程写入的 MyPizza 实例 | rows={}", rows);
                } catch (java.sql.SQLException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            log.warn("⚠️ 清理 MyPizza 实例失败: {}", e.toString());
        }
    }
}
