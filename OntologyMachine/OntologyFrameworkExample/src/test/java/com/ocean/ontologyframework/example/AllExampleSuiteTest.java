package com.ocean.ontologyframework.example;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Example 测试套件 —— 一个总入口，串跑本包下全部 2 个测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllExampleSuiteTest'，即可一次性跑完下列 2 个类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
 *   ExampleBpmnRealEngineTest       【真实引擎】Example 烘焙流程 Camunda 真实引擎集成测试（3种披萨）
 *   ExampleSwrlRabbitMqTest         【SWRL+RabbitMQ】SWRL 规则触发与 RabbitMQ 消息外发测试
 * </pre>
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkExample -am test -Dtest=AllExampleSuiteTest
 * </pre>
 *
 * <p><b>前置条件</b>：需先启动 Camunda/Zeebe 网关（26500）、本体推理 Worker（HTTP 9083）、
 * Ontop（8080，用 Example 配置启动）以及 RabbitMQ；
 * {@code ExampleSwrlRabbitMqTest} 还需 RabbitMQ 服务就绪。
 */
@Suite
@SuiteDisplayName("Example 测试套件（真实引擎 + SWRL/RabbitMQ）")
@SelectClasses({
        ExampleBpmnRealEngineTest.class,
        ExampleSwrlRabbitMqTest.class
})
public class AllExampleSuiteTest {
}
