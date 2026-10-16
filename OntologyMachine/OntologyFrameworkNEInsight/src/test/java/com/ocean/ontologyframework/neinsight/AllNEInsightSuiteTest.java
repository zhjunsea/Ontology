package com.ocean.ontologyframework.neinsight;

import com.ocean.ontologyframework.neinsight.analyze.InputAnalysisTest;
import com.ocean.ontologyframework.neinsight.retrieval.InsightWebRetrieverTest;
import com.ocean.ontologyframework.neinsight.validation.NEInsightOntologyComplianceTest;
import com.ocean.ontologyframework.neinsight.validation.NEInsightSkosTerminologyTest;
import com.ocean.ontologyframework.neinsight.web.NEInsightReportDownloadTest;
import com.ocean.ontologyframework.neinsight.web.NEInsightSkillDownloadTest;
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
 *   analyze/InputAnalysisTest                     【纯函数】输入分析通用能力（必要信息判定/追问/业务无关守卫，无环境）
 *   NEInsightInputAnalysisTest                    【桩函数】analyze-input 入口节点解析/降级（无环境）
 *   NEInsightSkillGenerationTest                  【纯 Openllet】summarize-insight 洞察 SKILL 落盘校验（无环境）
 *   NEInsightReportGenerationTest                 【桩函数】generate-report 洞察报告生成/降级落盘（无环境）
 *   retrieval/InsightWebRetrieverTest             【纯函数】联网检索结果解析/裁剪/降级（无环境）
 *   web/NEInsightSkillDownloadTest                【纯函数】洞察 SKILL 下载端点定位/返回（无环境）
 *   web/NEInsightReportDownloadTest               【纯函数】洞察报告下载端点定位/返回（无环境）
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
        InputAnalysisTest.class,
        NEInsightInputAnalysisTest.class,
        NEInsightSkillGenerationTest.class,
        NEInsightReportGenerationTest.class,
        InsightWebRetrieverTest.class,
        NEInsightSkillDownloadTest.class,
        NEInsightReportDownloadTest.class,
        NEInsightSwrlRabbitMqTest.class,
        NEInsightBpmnRealEngineTest.class
})
public class AllNEInsightSuiteTest {
}
