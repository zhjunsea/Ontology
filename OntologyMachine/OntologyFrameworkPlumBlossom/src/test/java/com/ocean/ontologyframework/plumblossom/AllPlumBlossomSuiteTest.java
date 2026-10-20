package com.ocean.ontologyframework.plumblossom;

import com.ocean.ontologyframework.plumblossom.validation.PlumBlossomOntologyComplianceTest;
import com.ocean.ontologyframework.plumblossom.validation.PlumBlossomSkosTerminologyTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * PlumBlossom 测试套件 —— 一个总入口，串跑本包下全部测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllPlumBlossomSuiteTest'，即可一次性跑完下列类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
 *   PlumBlossomOntologyComplianceTest  【本体合规】本体解析/一致性/域知识完整性（离线，不依赖外部服务）
 *   PlumBlossomSkosTerminologyTest     【SKOS术语】术语表结构 + 交叉校验（离线；依赖第 13 步词汇表）
 *   PlumBlossomSwrlRabbitMqTest        【SWRL+MQ】告警消息外发集成测试（需 RabbitMQ）
 *   PlumBlossomBpmnInMemoryTest        【内存流程】占断流程内存引擎端到端（需 RabbitMQ）
 *   PlumBlossomBpmnRealEngineTest      【真实引擎】占断流程 Camunda 真实引擎端到端（需 Camunda+RabbitMQ）
 * </pre>
 *
 * <p>纯离线用例排在前，环境未就绪时先拿到本体合规结果，不被后续集成用例连坐跳过。
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkPlumBlossom -am test -Dtest=AllPlumBlossomSuiteTest
 * </pre>
 */
@Suite
@SuiteDisplayName("PlumBlossom 测试套件（本体合规 + SKOS术语 + SWRL/MQ + 内存流程 + 真实引擎）")
@SelectClasses({
        PlumBlossomOntologyComplianceTest.class,
        PlumBlossomSkosTerminologyTest.class,
        PlumBlossomSwrlRabbitMqTest.class,
        PlumBlossomBpmnInMemoryTest.class,
        PlumBlossomBpmnRealEngineTest.class
})
public class AllPlumBlossomSuiteTest {
}
