package com.ocean.ontologyframework.industryinsight;

import com.ocean.ontologyframework.industryinsight.validation.IndustryInsightOntologyComplianceTest;
import com.ocean.ontologyframework.industryinsight.validation.IndustryInsightSkosTerminologyTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * IndustryInsight（行业洞察·五看三定）测试套件 —— 一个总入口，串跑本包下全部测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllIndustryInsightSuiteTest'，即可一次性跑完下列类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
 *   IndustryInsightOntologyComplianceTest  【本体合规】本体解析/一致性/域知识完整性（离线，不依赖外部服务）
 *   IndustryInsightSkosTerminologyTest     【SKOS术语】术语表结构 + 交叉校验（离线；依赖第 13 步词汇表）
 *   IndustryInsightWebSearchTest           【联网检索】检索通道解析/渲染/降级（离线）
 *   IndustryInsightKeyInsightMessageTest   【消息外发+高分支】G2「高」分支路由 + RabbitMQ 投递（需 RabbitMQ）
 *   IndustryInsightBpmnInMemoryTest        【内存流程】五看三定流程内存引擎端到端（离线）
 *   IndustryInsightBpmnRealEngineTest      【真实引擎】五看三定流程 Camunda 真实引擎端到端（需 Camunda+MQ）
 * </pre>
 *
 * <p>纯离线用例排在前，环境未就绪时先拿到本体合规结果，不被后续集成用例连坐跳过。
 *
 * <p>说明：OBDA/推理集成用例（{@code IndustryInsightObdaInferenceTest}）依赖第 11 步（ABox 入库）
 * 确定的库表结构与 OBDA 映射，届时据实际 DDL 细化后登记到本套件（★8/10，不预先臆造字段）。
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyFrameworkIndustryInsight -o test -Dtest=AllIndustryInsightSuiteTest
 * </pre>
 */
@Suite
@SuiteDisplayName("IndustryInsight 测试套件（本体合规 + SKOS术语 + 消息外发/高分支 + 内存流程 + 真实引擎）")
@SelectClasses({
        IndustryInsightOntologyComplianceTest.class,
        IndustryInsightSkosTerminologyTest.class,
        IndustryInsightWebSearchTest.class,
        IndustryInsightKeyInsightMessageTest.class,
        IndustryInsightBpmnInMemoryTest.class,
        IndustryInsightBpmnRealEngineTest.class
})
public class AllIndustryInsightSuiteTest {
}
