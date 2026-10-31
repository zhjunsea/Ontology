package com.ocean.ontologyframework.sizhubazi;

import com.ocean.ontologyframework.sizhubazi.validation.SiZhuBaZiOntologyComplianceTest;
import com.ocean.ontologyframework.sizhubazi.validation.SiZhuBaZiSkosTerminologyTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * SiZhuBaZi 测试套件 —— 一个总入口，串跑本包下全部测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllSiZhuBaZiSuiteTest'，即可一次性跑完下列类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
 *   SiZhuBaZiOntologyComplianceTest  【本体合规】本体解析/一致性/域知识完整性（离线，不依赖外部服务）
 *   SiZhuBaZiSkosTerminologyTest     【SKOS 词汇表】结构校验 + 与 OWL 本体双向交叉校验（离线）
 *   SiZhuBaZiSwrlRabbitMqTest        【SWRL+MQ】告警消息外发集成测试（需 RabbitMQ）
 *   SiZhuBaZiBpmnInMemoryTest        【内存流程】推演流程内存引擎端到端（需 RabbitMQ）
 *   SiZhuBaZiBpmnRealEngineTest      【真实引擎】推演流程 Camunda 真实引擎端到端（需 Camunda+RabbitMQ）
 * </pre>
 *
 * <p>纯离线用例（本体合规）排在前，环境未就绪时先拿到本体合规结果，不被后续集成用例连坐跳过。
 * 词汇表（SKOS）测试于第 13/14 步产出 {@code ontology/sizhubazi-terminology.ttl} 后并入本套件。
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkSiZhuBaZi -am test -Dtest=AllSiZhuBaZiSuiteTest
 * </pre>
 */
@Suite
@SuiteDisplayName("SiZhuBaZi 测试套件（本体合规 + SWRL/MQ + 内存流程 + 真实引擎）")
@SelectClasses({
        SiZhuBaZiOntologyComplianceTest.class,
        SiZhuBaZiSkosTerminologyTest.class,
        SiZhuBaZiSwrlRabbitMqTest.class,
        SiZhuBaZiBpmnInMemoryTest.class,
        SiZhuBaZiBpmnRealEngineTest.class
})
public class AllSiZhuBaZiSuiteTest {
}
