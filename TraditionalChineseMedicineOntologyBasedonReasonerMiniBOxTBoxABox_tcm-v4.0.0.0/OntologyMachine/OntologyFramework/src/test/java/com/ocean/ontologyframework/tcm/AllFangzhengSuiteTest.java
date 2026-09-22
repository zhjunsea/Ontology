package com.ocean.ontologyframework.tcm;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * 经方方证测试套件 —— 一个总入口，串跑本包下全部 8 个方证测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllFangzhengSuiteTest'，即可一次性跑完下列 8 个类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
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
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyFramework -am test -Dtest=AllFangzhengSuiteTest
 * </pre>
 * 或使用一键脚本 {@code OntologyMachine/run_all_fangzheng_tests.ps1}（带汇总表与报告文件）。
 *
 * <p><b>注意</b>：本类已在本模块 pom 的 surefire {@code <excludes>} 中排除，
 * 目的是避免 {@code mvn test} 时「套件跑一遍 + 8 个类各自再跑一遍」导致重复执行；
 * {@code mvn test} 仍会照常跑这 8 个类。IDEA 中手动 Run 本类不受影响，
 * {@code mvn test -Dtest=AllFangzhengSuiteTest} 也可显式指定运行（{@code -Dtest} 会覆盖 excludes）。
 *
 * <p><b>与 {@code com.ocean.ontologyframework.JingfangDiagnosisProcessTest} 的关系</b>：
 * 后者是拆分前的单体版（同一批 271 个用例写在一个类里），{@code mvn test} 会把它和本包 8 个类
 * 各跑一遍（合计 542 个用例）。本类只是把本包 8 个类聚合成一个入口，不改变上述既有状况。
 *
 * <p><b>注意</b>：{@code AbstractJingfangDiagnosisTest} 上的 {@code StopOnTimeoutExtension}
 * 使用 <b>静态</b> 中止标志，且套件在同一个 JVM 内顺序执行 —— 因此
 * <b>任意一个用例超时，其后所有用例（含后续测试类）都会被跳过</b>。
 * 需要逐类看到真实结果时，请改用 {@code run_all_fangzheng_tests.ps1 -Fork}（每类独立 JVM）。
 *
 * <p><b>前置条件</b>：需先启动 Camunda/Zeebe 网关（26500）与本体推理 Worker（HTTP 9081），
 * 否则首个用例会因等待流程结果而超时。
 */
@Suite
@SuiteDisplayName("经方方证测试套件（8 类）")
@SelectClasses({
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
