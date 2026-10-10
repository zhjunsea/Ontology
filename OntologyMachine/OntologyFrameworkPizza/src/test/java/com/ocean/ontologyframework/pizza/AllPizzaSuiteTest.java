package com.ocean.ontologyframework.pizza;

import com.ocean.ontologyframework.pizza.validation.PizzaSkosTerminologyTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Pizza 测试套件 —— 一个总入口，串跑本包下全部 4 个测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllPizzaSuiteTest'，即可一次性跑完下列 4 个类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
 *   ConsistencyTest                 【一致性】本体 TBox 一致性检查（离线，不依赖外部服务）
 *   PizzaSkosTerminologyTest        【SKOS术语】Pizza SKOS 术语体系验证（离线）
 *   PizzaBakingProcessInMemoryTest  【内存流程】Pizza 烘焙流程内存引擎测试（不依赖 Camunda 网关）
 *   PizzaBpmnRealEngineTest         【真实引擎】Pizza 烘焙流程 Camunda 真实引擎集成测试
 * </pre>
 *
 * <p><b>为什么 {@code ConsistencyTest} 和 {@code PizzaSkosTerminologyTest} 排在前面</b>：
 * 它们是纯离线的本体验证测试，不需要 Zeebe 网关与外部服务。放在最前，可以在环境未就绪时
 * 也先拿到本体一致性和术语体系的结果，不会被后面的集成测试连坐跳过。
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkPizza -am test -Dtest=AllPizzaSuiteTest
 * </pre>
 *
 * <p><b>前置条件</b>：{@code PizzaBpmnRealEngineTest} 需先启动 Camunda/Zeebe 网关（26500）
 * 与本体推理 Worker（HTTP 9080），并确保 Ontop（8080）已用 Pizza 配置启动；
 * {@code ConsistencyTest} 和 {@code PizzaSkosTerminologyTest} 无此前置条件。
 */
@Suite
@SuiteDisplayName("Pizza 测试套件（一致性 + SKOS术语 + 内存流程 + 真实引擎）")
@SelectClasses({
        ConsistencyTest.class,
        PizzaSkosTerminologyTest.class,
        PizzaBakingProcessInMemoryTest.class,
        PizzaBpmnRealEngineTest.class
})
public class AllPizzaSuiteTest {
}
