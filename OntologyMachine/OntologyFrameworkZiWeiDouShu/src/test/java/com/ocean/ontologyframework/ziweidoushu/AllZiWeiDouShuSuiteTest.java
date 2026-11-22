package com.ocean.ontologyframework.ziweidoushu;

import com.ocean.ontologyframework.ziweidoushu.validation.ZiWeiDouShuSkosTerminologyTest;
import com.ocean.ontologyframework.ziweidoushu.web.ZiWeiDouShuControllerTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * ZiWeiDouShu 测试套件 —— 一个总入口，串跑本包下的全部测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllZiWeiDouShuSuiteTest'，即可一次性跑完下列 9 个类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
 *   ZiWeiDouShuStarTableTest         【经典自检·离线】内置安星/庙陷/落闲/四化/神煞等表与卷二/卷三原文逐格比对（无需环境）
 *   ZiWeiDouShuOntologyTests         【推理+合规】TBox 子类检索、R1/R8/R8b/R21 推导、owl:Restriction 合规校验
 *   ZiWeiDouShuBpmnInMemoryTest      【内存流程】BPMN 编排内存引擎端到端（不依赖 Camunda 网关）
 *   ZiWeiDouShuBpmnRealEngineTest    【真实引擎】BPMN 真实引擎端到端（连 localhost:26500，生产 Worker 消费）
 *   ZiWeiDouShuSwrlRabbitMqTest      【SWRL+消息】R21 命中 → SwrlRuleTriggerListener → RabbitMQ 告警投递
 *   ZiWeiDouShuSkosTerminologyTest   【SKOS 术语】词汇表结构 + 与 OWL 交叉校验（第 13 步产出术语表后启用）
 * ZiWeiDouShuControllerTest        【接口层】REST Controller 全接口与校验/异常分支（standalone MockMvc，离线可跑）
 *   ZiWeiDouShuJobWorkerLogicTest    【纯逻辑】JobWorker 纯函数边界/守护分支（离线可跑）
 *   ZiWeiDouShuPatternMatrixTest     【全量矩阵】51 例数据驱动端到端（真实引擎；含 11 修复格局 + 5 维度专项）
 * </pre>
 * <p><b>离线可跑子集</b>：`ZiWeiDouShuStarTableTest` + `ZiWeiDouShuSkosTerminologyTest` 无需任何外部环境，
 * 可在环境未起时独立回归（第 8 步「经典自检」的主要落点）。
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
@SuiteDisplayName("ZiWeiDouShu 测试套件（经典自检 + 推理合规 + 内存流程 + 真实引擎 + SWRL/RabbitMQ + SKOS）")
@SelectClasses({
        ZiWeiDouShuStarTableTest.class,
        ZiWeiDouShuOntologyTests.class,
        ZiWeiDouShuJobWorkerLogicTest.class,
        ZiWeiDouShuDataClassCoverageTest.class,
        ZiWeiDouShuJudgeTargetedTest.class,
        ZiWeiDouShuHelperExhaustiveTest.class,
        ZiWeiDouShuFuzzCoverageTest.class,
        ZiWeiDouShuBranchGapBatchTest.class,
        ZiWeiDouShuJinYuFuJiaTest.class,
        ZiWeiDouShuPalaceZhiPoCorrectionTest.class,
        ZiWeiDouShuJiaBothSidesTest.class,
        ZiWeiDouShuTongYuanPhraseTest.class,
        ZiWeiDouShuPatternTargetedTest.class,
        LunarCalendarConverterTest.class,
        ZiWeiDouShuHandlerGuardTest.class,
        ZiWeiDouShuObdaFaultInjectionTest.class,
        ZiWeiDouShuInterpretLlmCoverageTest.class,
        ZiWeiDouShuBpmnInMemoryTest.class,
        ZiWeiDouShuInMemoryDataMatrixTest.class,
        ZiWeiDouShuBpmnRealEngineTest.class,
        ZiWeiDouShuRealEngineDataValueTest.class,
        ZiWeiDouShuSwrlRabbitMqTest.class,
        ZiWeiDouShuSkosTerminologyTest.class,
        ZiWeiDouShuControllerTest.class,
        ZiWeiDouShuPatternMatrixTest.class,
        ZiWeiDouShuDataBranchMatrixTest.class,
        ZiWeiDouShuChartGridTest.class,
        ZiWeiDouShuBClassResidualCoverageTest.class,
        ZiWeiDouShuBClassResidualCoverageTest2.class,
        ZiWeiDouShuBClassResidualCoverageTest3.class,
        ZiWeiDouShuBClassResidualCoverageTest4.class,
        ZiWeiDouShuBClassResidualCoverageTest5.class,
        ZiWeiDouShuBClassResidualCoverageTest6.class,
        ZiWeiDouShuBClassResidualCoverageTest7.class,
        ZiWeiDouShuBClassResidualCoverageTest8.class,
        ZiWeiDouShuBClassResidualCoverageTest9.class,
        ZiWeiDouShuBClassResidualCoverageTest10.class,
        ZiWeiDouShuBClassResidualCoverageTest11.class,
        ZiWeiDouShuBClassResidualCoverageTest12.class,
        ZiWeiDouShuBClassResidualCoverageTest13.class,
        ZiWeiDouShuBClassResidualCoverageTest14.class
})
public class AllZiWeiDouShuSuiteTest {
}
