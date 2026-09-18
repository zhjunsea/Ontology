package com.ocean.ontologyframework.tcm;

import com.ocean.ontologyframework.HerbRuleEngineTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 经方测试套件 —— 一个总入口，串跑「方后注加减规则引擎」+ 本包下全部 8 个方证测试类，共 9 个类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllFangzhengSuiteTest'，即可一次性跑完下列 9 个类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
 *   HerbRuleEngineTest             【规则引擎】方后注加减法派生新方（<b>离线</b>，不依赖 Zeebe/MySQL）
 *   DuliFangzhengTest              【独立】陷胸/栀子/瓜蒂/十枣/其他伤寒杂方
 *   HebingFangzhengTest            【合病】
 *   JianjiaFangzhengTest           【兼夹】瘀血/痰饮/气郁等兼夹证检测（当前为空类）
 *   JueyinFangzhengTest            【厥阴】
 *   ShaoyangYangmingFangzhengTest  【少阳阳明】
 *   ShaoyinTaiyinFangzhengTest     【少阴太阴】
 *   TaiyangFangzhengTest           【太阳】
 *   ZabingFangzhengTest            【杂病】
 * </pre>
 *
 * <p><b>为什么 {@code HerbRuleEngineTest} 排在第一位</b>：它位于
 * {@code com.ocean.ontologyframework} 包（不在本 {@code tcm} 子包内），是纯离线的规则引擎单测，
 * 不需要 Zeebe 网关与本体推理 Worker。放在最前，可以在环境未就绪时也先拿到「加减药派生」这一层的
 * 结果，不会被后面的超时用例连坐跳过。
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyFramework -am test -Dtest=AllFangzhengSuiteTest
 * </pre>
 * 或使用一键脚本 {@code OntologyMachine/run_all_fangzheng_tests.ps1}（带汇总表与报告文件）。
 *
 * <p><b>注意</b>：本类已在本模块 pom 的 surefire {@code <excludes>} 中排除，
 * 目的是避免 {@code mvn test} 时「套件跑一遍 + 9 个类各自再跑一遍」导致重复执行；
 * {@code mvn test} 仍会照常跑这 9 个类。IDEA 中手动 Run 本类不受影响，
 * {@code mvn test -Dtest=AllFangzhengSuiteTest} 也可显式指定运行（{@code -Dtest} 会覆盖 excludes）。
 *
 * <p><b>与 {@code com.ocean.ontologyframework.JingfangDiagnosisProcessTest} 的关系</b>：
 * 后者是拆分前的单体版（同一批 271 个用例写在一个类里），{@code mvn test} 会把它和本包 8 个类
 * 各跑一遍（合计 542 个用例）。本类只是把本包 8 个类聚合成一个入口，不改变上述既有状况。
 *
 * <p><b>注意</b>：{@code AbstractJingfangDiagnosisTest} 上的 {@code StopOnTimeoutExtension}
 * 使用 <b>静态</b> 中止标志，且套件在同一个 JVM 内顺序执行 —— 因此
 * <b>任意一个方证用例超时，其后所有方证用例（含后续方证测试类）都会被跳过</b>。
 * （{@code HerbRuleEngineTest} 未挂该扩展，且排在首位，不受影响。）
 * 需要逐类看到真实结果时，请改用 {@code run_all_fangzheng_tests.ps1 -Fork}（每类独立 JVM）。
 *
 * <p><b>前置条件</b>：方证类需先启动 Camunda/Zeebe 网关（26500）与本体推理 Worker（HTTP 9081），
 * 否则首个方证用例会因等待流程结果而超时；{@code HerbRuleEngineTest} 无此前置条件。
 */
@Suite
@SuiteDisplayName("经方测试套件（规则引擎 + 8 个方证类）")
@SelectClasses({
        HerbRuleEngineTest.class,
        DuliFangzhengTest.class,
        HebingFangzhengTest.class,
        JianjiaFangzhengTest.class,
        JueyinFangzhengTest.class,
        ShaoyangYangmingFangzhengTest.class,
        ShaoyinTaiyinFangzhengTest.class,
        TaiyangFangzhengTest.class,
        ZabingFangzhengTest.class
})
public class AllFangzhengSuiteTest {
}
