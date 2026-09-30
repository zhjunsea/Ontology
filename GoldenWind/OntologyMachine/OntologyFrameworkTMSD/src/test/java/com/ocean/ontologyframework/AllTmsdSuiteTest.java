package com.ocean.ontologyframework;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * TMSD（塔架中段设计）测试总入口套件 —— 一次串跑本模块全部 6 个 TMSD 测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllTmsdSuiteTest'，即可一次性跑完下列 6 个类，
 * 并在测试树里逐类、逐方法看到通过 / 失败 / 跳过：
 * <pre>
 *   TmsdOntologyLoadTest       【离线】本体加载与约束校验（OWL API + Openllet，不依赖 Camunda/Ontop/MySQL）
 *   TMSDForwardRulesTest       【离线】正向设计规则单测（附件排布 / 梯档踏棍 / 防雷等）
 *   TMSDDesignPipelineTest     【离线】完全正向设计流水线求解与本体约束校验
 *   TmsdDebugTest              【离线】诊断打印（各段几何 / 焊缝 / 附件排布搜索结果）
 *   TMSDProcessTest            【内存引擎】zeebe-process-test 端到端（测试 JVM 内内存引擎，无需外部环境）
 *   TMSDDesignProcessTest      【真实引擎】连真实 Zeebe(26500) + 外部 TMSDApplication(9081) 的端到端链路
 * </pre>
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -pl OntologyFramework -am test -Dtest=AllTmsdSuiteTest
 * </pre>
 *
 * <p><b>为什么用 {@code @SelectPackages} 而非 {@code @SelectClasses}</b>：TMSD 的测试类
 * 分布于根包 {@code com.ocean.ontologyframework}（{@code TMSDDesignProcessTest}）与子包
 * {@code com.ocean.ontologyframework.tmsd}（其余 5 个），且均为<b>包级可见</b>（非 public），
 * 无法跨包用 {@code @SelectClasses} 直接引用；{@code @SelectPackages} 递归选择该包及其全部子包，
 * 可一次性覆盖两个包（无 @Test 的辅助类会被自动跳过）。
 *
 * <p><b>注意</b>：本类已在本模块 pom 的 surefire {@code <excludes>} 中排除，
 * 目的是避免 {@code mvn test} 时「套件跑一遍 + 6 个类各自再跑一遍」导致重复执行；
 * {@code mvn test} 仍会照常跑这 6 个类。IDEA 中手动 Run 本类不受影响，
 * {@code mvn test -Dtest=AllTmsdSuiteTest} 也可显式指定运行（{@code -Dtest} 会覆盖 excludes）。
 *
 * <p><b>注意</b>：{@code TMSDDesignProcessTest} 上的 {@code StopOnTimeoutExtension}
 * 使用 <b>静态</b> 中止标志，套件在同一 JVM 内顺序执行 —— 因此该真实引擎用例一旦超时，
 * 其后用例会被跳过。它属真实引擎用例，需先满足其前置条件（真实 Zeebe 健康 + 外部运行
 * {@code TMSDApplication} 消费作业），否则会因等待流程结果而超时。
 */
@Suite
@SuiteDisplayName("TMSD 测试套件（离线规则/本体/流水线 + 内存引擎 + 真实引擎）")
@SelectPackages("com.ocean.ontologyframework")
public class AllTmsdSuiteTest {
}
