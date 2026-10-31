package com.ocean.ontologyframework.ziweidoushu;

import com.ocean.ontologyframework.ziweidoushu.validation.ZiWeiDouShuSkosTerminologyTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * ZiWeiDouShu 测试套件 —— 一个总入口，串跑本包下第 8 步固化的 4 个测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllZiWeiDouShuSuiteTest'，即可一次性跑完下列 4 个类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
 *   ZiWeiDouShuOntologyTests         【推理+合规】TBox 子类检索、R1/R8/R8b/R21 推导、owl:Restriction 合规校验
 *   ZiWeiDouShuBpmnInMemoryTest      【内存流程】BPMN 编排内存引擎端到端（不依赖 Camunda 网关）
 *   ZiWeiDouShuBpmnRealEngineTest    【真实引擎】BPMN 真实引擎端到端（连 localhost:26500，生产 Worker 消费）
 *   ZiWeiDouShuSwrlRabbitMqTest      【SWRL+消息】R21 命中 → SwrlRuleTriggerListener → RabbitMQ 告警投递
 *   ZiWeiDouShuSkosTerminologyTest   【SKOS 术语】词汇表结构 + 与 OWL 交叉校验（第 13 步产出术语表后启用）
 * </pre>
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkZiWeiDouShu -am test -Dtest=AllZiWeiDouShuSuiteTest
 * </pre>
 *
 * <p><b>前置条件</b>：推理/合规类与内存流程类需 Ontop(8080) + MySQL(3306)；真实引擎类需 Camunda/Zeebe
 * 网关(26500) 与运行中的本体推理应用(9089)；SWRL 消息类需 RabbitMQ(5672)。外部服务不可达时，
 * 各测试类经 {@code ServiceProbe} 自动跳过（不中断构建）。
 */
@Suite
@SuiteDisplayName("ZiWeiDouShu 测试套件（推理合规 + 内存流程 + 真实引擎 + SWRL/RabbitMQ）")
@SelectClasses({
        ZiWeiDouShuOntologyTests.class,
        ZiWeiDouShuBpmnInMemoryTest.class,
        ZiWeiDouShuBpmnRealEngineTest.class,
        ZiWeiDouShuSwrlRabbitMqTest.class,
        ZiWeiDouShuSkosTerminologyTest.class
})
public class AllZiWeiDouShuSuiteTest {
}
