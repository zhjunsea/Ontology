package com.ocean.ontologyframework.pizza;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PizzaApplication.class)
@ActiveProfiles("PizzaBPMNTest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 300, unit = TimeUnit.SECONDS)
@DisplayName("披萨 BPMN · 真实引擎端到端（连 localhost:26500，生产 PizzaOntologyJobWorker 消费）")
class PizzaBpmnRealEngineTest {

    private static final Logger log = LoggerFactory.getLogger(PizzaBpmnRealEngineTest.class);

    private static final String BAKING_PROCESS_ID = "PizzaMakingStandardProcess";
    private static final String DESIGN_PROCESS_ID = "PizzaOntologyDesignProcess";
    private static final String PIZZA_TYPE_ITALIAN_STYLE =
            "http://example.org/pizza/core/ItalianStyleWhiteSeafoodPizza";
    private static final String PIZZA_TYPE_MARGHERITA =
            "http://example.org/pizza/core/MargheritaPizza";
    private static final String PIZZA_TYPE_VEGETARIAN =
            "http://example.org/pizza/core/VegetarianPizza";
    private static final Duration WAIT = Duration.ofMinutes(2);

    // 设计流程 userTask 变量：按 pizzaType 给出符合本体约束的组件实例，避免写入 ABox 冲突数据
    // - ItalianStyleWhiteSeafoodPizza（WhitePizza）：白酱（非番茄）；无 topping（hasTopping 无值域）
    // - MargheritaPizza（Neapolitan，hasTopping hasValue Basil）：番茄酱 + 罗勒
    // - VegetarianPizza（hasTopping allValuesFrom VegetableTopping）：蔬菜 topping；无 sauce（无 sauce 约束）
    private static final Map<String, Map<String, Object>> TASK_VARS_ITALIAN_STYLE = Map.of(
            "UT_Crust", Map.<String, Object>of("selectedCrust", "NeapolitanCrustInstance"),
            "UT_Cheese", Map.<String, Object>of("selectedCheese", "LowMoistureMozzarellaInstance"),
            "UT_Sauce", Map.<String, Object>of("selectedSauce", "WhiteSauceInstance"));
    private static final Map<String, Map<String, Object>> TASK_VARS_MARGHERITA = Map.of(
            "UT_Crust", Map.<String, Object>of("selectedCrust", "NeapolitanCrustInstance"),
            "UT_Cheese", Map.<String, Object>of("selectedCheese", "BuffaloMozzarellaInstance"),
            "UT_Sauce", Map.<String, Object>of("selectedSauce", "NeapolitanTomatoSauceInstance"),
            "UT_Topping", Map.<String, Object>of("selectedToppings", List.of("BasilInstance")));
    private static final Map<String, Map<String, Object>> TASK_VARS_VEGETARIAN = Map.of(
            "UT_Crust", Map.<String, Object>of("selectedCrust", "NeapolitanCrustInstance"),
            "UT_Cheese", Map.<String, Object>of("selectedCheese", "LowMoistureMozzarellaInstance"),
            "UT_Topping", Map.<String, Object>of("selectedToppings", List.of("MushroomInstance")));

    @Value("${ontology.bpmn-path}")
    private String bakingBpmn;

    @Value("${ontology.design-bpmn-path}")
    private String designBpmn;

    @Autowired
    private CamundaClient client;

    @BeforeEach
    void deploy() {
        for (String path : List.of(bakingBpmn, designBpmn)) {
            assertThat(Files.isRegularFile(Path.of(path)))
                    .as("BPMN 文件应存在: %s", path).isTrue();
        }

        List<String> resources = new ArrayList<>(List.of(bakingBpmn, designBpmn));
        Path formDir = Path.of(designBpmn).getParent();
        try (Stream<Path> forms = Files.list(formDir)) {
            forms.filter(p -> p.getFileName().toString().endsWith(".form"))
                    .map(Path::toString)
                    .sorted()
                    .forEach(resources::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        DeployResourceCommandStep2 step = client.newDeployResourceCommand()
                .addResourceFile(resources.get(0));
        for (int i = 1; i < resources.size(); i++) {
            step = step.addResourceFile(resources.get(i));
        }
        DeploymentEvent dep = step.send().join();
        assertThat(dep.getProcesses()).as("应成功部署流程").isNotEmpty();
        log.info("✅ 已部署 BPMN 与表单资源 | {}", resources);
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

    @Test
    @Order(4)
    @DisplayName("设计流程·ItalianStyleWhiteSeafoodPizza：自动完成 userTask，真实引擎端到端完成")
    void designProcessCompletesItalianStyle() {
        runDesign(PIZZA_TYPE_ITALIAN_STYLE, TASK_VARS_ITALIAN_STYLE);
    }

    @Test
    @Order(5)
    @DisplayName("设计流程·MargheritaPizza：自动完成 userTask，真实引擎端到端完成")
    void designProcessCompletesMargherita() {
        runDesign(PIZZA_TYPE_MARGHERITA, TASK_VARS_MARGHERITA);
    }

    @Test
    @Order(6)
    @DisplayName("设计流程·VegetarianPizza：自动完成 userTask，真实引擎端到端完成")
    void designProcessCompletesVegetarian() {
        runDesign(PIZZA_TYPE_VEGETARIAN, TASK_VARS_VEGETARIAN);
    }

    private void runBaking(String pizzaType) {
        ProcessInstanceEvent instance = client.newCreateInstanceCommand()
                .bpmnProcessId(BAKING_PROCESS_ID)
                .latestVersion()
                .variables(Map.of("pizzaType", pizzaType))
                .send().join();

        awaitCompleted(instance.getProcessInstanceKey());
        log.info("✅ 烘焙流程真实引擎完成 | pizzaType={} | key={}", pizzaType, instance.getProcessInstanceKey());
    }

    private void runDesign(String pizzaType, Map<String, Map<String, Object>> taskVars) {
        ProcessInstanceEvent instance = client.newCreateInstanceCommand()
                .bpmnProcessId(DESIGN_PROCESS_ID)
                .latestVersion()
                .variables(Map.of("pizzaType", pizzaType))
                .send().join();

        long key = instance.getProcessInstanceKey();

        Set<String> seenIncidents = new HashSet<>();
        Set<Long> completedTaskKeys = new HashSet<>();
        Set<String> completedElements = new HashSet<>();
        long deadline = System.nanoTime() + WAIT.toNanos();
        int round = 0;
        while (System.nanoTime() < deadline) {
            round++;
            List<UserTask> tasks = searchUserTasks(key);
            if (round == 1 || !tasks.isEmpty() || round % 20 == 0) {
                log.info("轮询#{} | pizzaType={} | userTask数={} | 明细={}",
                        round, pizzaType, tasks.size(),
                        tasks.stream().map(t -> t.getElementId() + ":" + t.getState()).collect(Collectors.toList()));
            }
            for (UserTask task : tasks) {
                if (task.getState() == UserTaskState.CREATED && completedTaskKeys.add(task.getUserTaskKey())) {
                    Map<String, Object> vars = taskVars.getOrDefault(task.getElementId(), Map.of());
                    try {
                        client.newCompleteUserTaskCommand(task.getUserTaskKey())
                                .variables(vars).send().join();
                        completedElements.add(task.getElementId());
                        log.info("✅ 完成 userTask | pizzaType={} | elementId={} | key={}",
                                pizzaType, task.getElementId(), task.getUserTaskKey());
                    } catch (Exception e) {
                        log.debug("userTask 完成跳过（索引延迟/已完成）| elementId={} | key={} | {}",
                                task.getElementId(), task.getUserTaskKey(), e.getMessage());
                    }
                }
            }
            for (Incident inc : searchIncidents(key)) {
                String sig = inc.getElementId() + ":" + inc.getErrorType() + ":" + inc.getErrorMessage();
                if (seenIncidents.add(sig)) {
                    log.error("❌ 发现 incident | key={} | {}", key, sig);
                }
            }
            if (isCompleted(key)) {
                log.info("✅ 设计流程真实引擎完成 | pizzaType={} | key={} | 已自动完成 userTask={}",
                        pizzaType, key, completedElements);
                return;
            }
            sleep();
        }
        throw new AssertionError("设计流程未在 " + WAIT + " 内完成 | pizzaType=" + pizzaType + " | key=" + key);
    }

    private List<UserTask> searchUserTasks(long key) {
        try {
            return client.newUserTaskSearchRequest()
                    .filter(f -> f.processInstanceKey(key))
                    .send().join().items();
        } catch (Exception e) {
            log.warn("⚠️ userTask 搜索异常 | key={} | {}", key, e.toString());
            return List.of();
        }
    }

    private List<Incident> searchIncidents(long key) {
        try {
            return client.newIncidentSearchRequest()
                    .filter(f -> f.processInstanceKey(key))
                    .send().join().items();
        } catch (Exception e) {
            log.warn("⚠️ incident 搜索异常 | key={} | {}", key, e.toString());
            return List.of();
        }
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
        try {
            ProcessInstance pi = client.newProcessInstanceGetRequest(key).send().join();
            return pi.getState() == ProcessInstanceState.COMPLETED;
        } catch (Exception e) {
            log.debug("实例 {} 状态暂不可查询（可能索引延迟）: {}", key, e.getMessage());
            return false;
        }
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
        Path props = Path.of("ontology/database/myPizza.properties");
        if (!Files.isRegularFile(props)) {
            log.warn("⚠️ 未找到 DB 配置，跳过清理: {}", props);
            return;
        }
        Properties p = new Properties();
        try (var in = Files.newInputStream(props)) {
            p.load(in);
        } catch (IOException e) {
            log.warn("⚠️ 读取 DB 配置失败，跳过清理: {}", e.toString());
            return;
        }
        String url = p.getProperty("jdbc.url");
        String user = p.getProperty("jdbc.user");
        String password = p.getProperty("jdbc.password");
        try (Connection c = DriverManager.getConnection(url, user, password);
             Statement st = c.createStatement()) {
            int rows = st.executeUpdate("DELETE FROM myPizza WHERE name LIKE 'MyPizza%'");
            log.info("🧹 已清理设计流程写入的 MyPizza 实例 | rows={}", rows);
        } catch (Exception e) {
            log.warn("⚠️ 清理 MyPizza 实例失败: {}", e.toString());
        }
    }
}
