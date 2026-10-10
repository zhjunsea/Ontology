package com.ocean.installer;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Installer 测试套件 —— 一个总入口，串跑本模块全部测试类。
 *
 * <p>在 IDEA 中直接右键本类 → Run 'AllInstallerSuiteTest'，即可一次跑完下列测试类：
 * <pre>
 *   InstallServiceTest   安装器核心逻辑（便携模式空密码对齐等）
 * </pre>
 *
 * <p>命令行等价写法：
 * <pre>
 *   mvn -f OntologyMachine/pom.xml -pl OntologyInstaller test -Dtest=AllInstallerSuiteTest
 * </pre>
 */
@Suite
@SuiteDisplayName("Installer 测试套件（安装器核心逻辑）")
@SelectClasses({
        InstallServiceTest.class
})
public class AllInstallerSuiteTest {
}
