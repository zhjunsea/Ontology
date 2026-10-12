package com.ocean.ontologyframework.neinsight;

import com.ocean.ontologyframework.neinsight.validation.NEInsightOntologyComplianceTest;
import com.ocean.ontologyframework.neinsight.validation.NEInsightSkosTerminologyTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * NEInsight 测试套件 —— 一个总入口，串跑本模块全部测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllNEInsightSuiteTest'，即可一次性跑完：
 * <pre>
 *   validation/NEInsightOntologyComplianceTest   【纯 Openllet】本体合规 + 推理（无环境）
 *   validation/NEInsightSkosTerminologyTest       【纯 Jena】SKOS 术语结构 + OWL 交叉校验（无环境）
 *   NEInsightSkillGenerationTest                  【纯 Openllet】summarize-insight 洞察 SKILL 落盘校验（无环境）
 *   NEInsightSwrlRabbitMqTest                     【SWRL+RabbitMQ】规则触发与消息外发（需 RabbitMQ）
 *   NEInsightBpmnRealEngineTest                   【真实引擎】五看三定端到端（需 Zeebe/Worker）
 * </pre>
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkNEInsight -am test -Dtest=AllNEInsightSuiteTest
 * </pre>
 *
 * <p><b>前置条件</b>：真实引擎与消息用例需先启动 Zeebe 网关（26500）、Ontop（8080）、
 * MySQL（neinsightdb）与 RabbitMQ；本体合规模块可离线执行。
 * 第 14 步并入 {@code validation/NEInsightSkosTerminologyTest}。
 */
@Suite
@SuiteDisplayName("NEInsight 测试套件（本体合规 + SWRL/RabbitMQ + 真实引擎）")
@SelectClasses({
        NEInsightOntologyComplianceTest.class,
        NEInsightSkosTerminologyTest.class,
        NEInsightSkillGenerationTest.class,
        NEInsightSwrlRabbitMqTest.class,
        NEInsightBpmnRealEngineTest.class
})
public class AllNEInsightSuiteTest {
}
