package com.ocean.ontologyframework.pizza;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.process.test.api.ZeebeTestEngine;
import io.camunda.zeebe.process.test.assertions.BpmnAssert;
import io.camunda.zeebe.process.test.engine.EngineFactory;
import io.camunda.zeebe.process.test.filters.RecordStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PizzaApplication.class)
@ActiveProfiles("PizzaBPMNTest")
@DisplayName("通用披萨标准制作工艺流程 · 内存引擎端到端（生产 PizzaOntologyJobWorker 驱动）")
class PizzaBakingProcessInMemoryTest {

    private static final Logger log = LoggerFactory.getLogger(PizzaBakingProcessInMemoryTest.class);

    private static final String PROCESS_ID = "PizzaMakingStandardProcess";
    private static final String PIZZA_TYPE_ITALIAN_STYLE =
            "http://example.org/pizza/core/ItalianStyleWhiteSeafoodPizza";
    private static final String PIZZA_TYPE_MARGHERITA =
            "http://example.org/pizza/core/MargheritaPizza";
    private static final String PIZZA_TYPE_VEGETARIAN =
            "http://example.org/pizza/core/VegetarianPizza";
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    private static final String[] MAIN_TASKS = {
            "Task_DoughMix", "Task_DoughFerment", "Task_CrustForm",
            "Task_GetSauce", "Task_GetCheese", "Task_GetTopping",
            "Task_GetTopCheese", "Task_Bake", "Task_Decorate", "Task_Finish"
    };

    private static ZeebeTestEngine engine;
    private static ZeebeClient testClient;

    @Value("${ontology.bpmn-path}")
    private String bpmnPath;

    @DynamicPropertySource
    static void startInMemoryEngine(DynamicPropertyRegistry registry) {
        engine = EngineFactory.create();
        engine.start();
        testClient = engine.createClient();

        BpmnAssert.initRecordStream(RecordStream.of(engine.getRecordStreamSource()));

        String address = normalize(engine.getGatewayAddress());
        log.info("内存引擎已启动 | rawGatewayAddress={} | normalized={}", engine.getGatewayAddress(), address);
        registry.add("camunda.client.grpc-address", () -> address);
        registry.add("camunda.client.rest-address", () -> address);
        registry.add("camunda.client.prefer-rest-over-grpc", () -> "false");
    }

    @AfterAll
    static void stopEngine() {
        if (testClient != null) {
            try {
                testClient.close();
            } catch (Exception ignored) {
            }
        }
        if (engine != null) {
            try {
                engine.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    @DisplayName("ItalianStyleWhiteSeafoodPizza：全流程无 incident 完成，主干工序全部通过")
    void italianStyleWhiteSeafoodPizzaFlowCompletes() {
        runBakingFlow(PIZZA_TYPE_ITALIAN_STYLE);
    }

    @Test
    @DisplayName("MargheritaPizza：全流程无 incident 完成，主干工序全部通过")
    void margheritaPizzaFlowCompletes() {
        runBakingFlow(PIZZA_TYPE_MARGHERITA);
    }

    @Test
    @DisplayName("VegetarianPizza：全流程无 incident 完成，主干工序全部通过")
    void vegetarianPizzaFlowCompletes() {
        runBakingFlow(PIZZA_TYPE_VEGETARIAN);
    }

    private void runBakingFlow(String pizzaType) {
        assertThat(Files.isRegularFile(Path.of(bpmnPath)))
                .as("BPMN 文件应存在: %s", bpmnPath)
                .isTrue();

        DeploymentEvent deployment = testClient.newDeployResourceCommand()
                .addResourceFile(Path.of(bpmnPath).toString())
                .send()
                .join();
        assertThat(deployment.getProcesses()).as("应成功部署 1 个流程").hasSize(1);
        assertThat(deployment.getProcesses().get(0).getBpmnProcessId()).isEqualTo(PROCESS_ID);

        ProcessInstanceEvent instance = testClient.newCreateInstanceCommand()
                .bpmnProcessId(PROCESS_ID)
                .latestVersion()
                .variables(Map.of("pizzaType", pizzaType))
                .send()
                .join();

        awaitCompletion(instance);

        BpmnAssert.assertThat(instance)
                .isCompleted()
                .hasNoIncidents()
                .hasPassedElement("Start_01")
                .hasPassedElement("End_01")
                .hasPassedElementsInOrder(MAIN_TASKS);
    }

    private static void awaitCompletion(ProcessInstanceEvent instance) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        AssertionError last = null;
        while (System.nanoTime() < deadline) {
            try {
                BpmnAssert.assertThat(instance).isCompleted();
                return;
            } catch (AssertionError e) {
                last = e;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw last != null ? last : new AssertionError("流程未在 " + TIMEOUT + " 内完成");
    }

    private static String normalize(String gatewayAddress) {
        if (gatewayAddress == null || gatewayAddress.isBlank()) {
            throw new IllegalStateException("内存引擎未返回有效 gateway 地址");
        }
        return gatewayAddress.startsWith("http") ? gatewayAddress : "http://" + gatewayAddress;
    }
}
