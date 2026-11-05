package com.ocean.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 安装器核心逻辑单测：重点覆盖「便携模式空密码对齐」。
 */
class InstallServiceTest {

    @Test
    void alignJdbcPassword_clearsHardcodedPasswordKeepingOtherLines(@TempDir Path root) throws Exception {
        Path dbDir = root.resolve("ontology/database");
        Files.createDirectories(dbDir);
        Path props = dbDir.resolve("myPizza.properties");
        String body = "jdbc.url=jdbc:mysql://localhost:3306/mypizzadb\r\n"
                + "jdbc.user=root\r\n"
                + "jdbc.password=CHANGE_ME\r\n"
                + "port=8080\r\n";
        Files.writeString(props, body, StandardCharsets.UTF_8);

        InstallService.alignJdbcPassword(root, s -> {
        });

        String out = Files.readString(props, StandardCharsets.UTF_8);
        assertTrue(out.contains("jdbc.password=\r\n"), "jdbc.password 应被清空且保留 CRLF");
        assertFalse(out.contains("CHANGE_ME"), "原密码不应残留");
        assertTrue(out.contains("jdbc.user=root"), "其它行应保持不变");
        assertTrue(out.contains("port=8080"), "其它行应保持不变");
    }

    @Test
    void alignJdbcPassword_leavesFilesWithoutPasswordUntouched(@TempDir Path root) throws Exception {
        Path props = root.resolve("a.properties");
        String body = "foo=bar\r\n";
        Files.writeString(props, body, StandardCharsets.UTF_8);

        InstallService.alignJdbcPassword(root, s -> {
        });

        assertEquals(body, Files.readString(props, StandardCharsets.UTF_8));
    }

    @Test
    void projectDirConstantIsDefined() {
        assertEquals("OntologyFrameworkExample", InstallService.PROJECT_DIR);
    }

    @Test
    void installAppBuilderSkill_copiesSkillIntoProjectCodeartsDir(@TempDir Path root) throws Exception {
        Path resHome = root.resolve("dist");
        Path skillSrc = resHome.resolve("skills/ontology-app-builder");
        Files.createDirectories(skillSrc.resolve("references"));
        Files.writeString(skillSrc.resolve("SKILL.md"), "---\nname: ontology-app-builder\n---\n");
        Files.writeString(skillSrc.resolve("references/步骤规范.md"), "步骤规范");
        Path projectRoot = root.resolve("work/OntologyFrameworkExample");
        Files.createDirectories(projectRoot);

        InstallService.installAppBuilderSkill(resHome, projectRoot, s -> {
        });

        Path deployDir = projectRoot.resolve(InstallService.SKILL_DEPLOY_DIR);
        assertTrue(Files.isRegularFile(deployDir.resolve("SKILL.md")), "SKILL.md 应被投放");
        assertTrue(Files.isRegularFile(deployDir.resolve("references/步骤规范.md")), "子目录文件应被投放");
    }

    @Test
    void installAppBuilderSkill_skipsWhenSourceMissing(@TempDir Path root) throws Exception {
        Path resHome = root.resolve("dist");
        Files.createDirectories(resHome);
        Path projectRoot = root.resolve("work");
        Files.createDirectories(projectRoot);

        InstallService.installAppBuilderSkill(resHome, projectRoot, s -> {
        });

        assertFalse(Files.exists(projectRoot.resolve(InstallService.SKILL_DEPLOY_DIR)), "来源缺失时不应创建投放目录");
    }

    @Test
    void alignPomJavaVersion_rewritesToJdkMajorVersion(@TempDir Path root) throws Exception {
        Path workspace = root.resolve("work");
        Path module = workspace.resolve("OntologyFrameworkExample");
        Files.createDirectories(module);
        Files.writeString(workspace.resolve("pom.xml"),
                "<project>\n  <modules><module>OntologyFrameworkExample</module></modules>\n</project>\n");
        Path modulePom = module.resolve("pom.xml");
        Files.writeString(modulePom,
                "<project>\n  <properties>\n    <java.version>26</java.version>\n  </properties>\n</project>\n");

        Path jdkHome = root.resolve("jdk-27");
        Files.createDirectories(jdkHome);
        Files.writeString(jdkHome.resolve("release"),
                "IMPLEMENTOR=\"Oracle Corporation\"\nJAVA_VERSION=\"27.0.1\"\n");

        InstallService.alignPomJavaVersion(workspace, jdkHome, s -> {
        });

        String out = Files.readString(modulePom);
        assertTrue(out.contains("<java.version>27</java.version>"), "子模块 pom 的 java.version 应对齐为 JDK 主版本 27");
        assertFalse(out.contains(">26<"), "旧版本号应被替换");
    }

    @Test
    void alignPomJavaVersion_keepsWhenJdkReleaseMissing(@TempDir Path root) throws Exception {
        Path workspace = root.resolve("work");
        Files.createDirectories(workspace);
        Path pom = workspace.resolve("pom.xml");
        String body = "<project>\n  <properties>\n    <java.version>26</java.version>\n  </properties>\n</project>\n";
        Files.writeString(pom, body);

        Path jdkHome = root.resolve("jdk-norel");
        Files.createDirectories(jdkHome);

        InstallService.alignPomJavaVersion(workspace, jdkHome, s -> {
        });

        assertEquals(body, Files.readString(pom), "无法确定 JDK 版本时应保持原样");
    }

    @Test
    void generateExampleRunConfiguration_writesToWorkspaceRoot(@TempDir Path root) throws Exception {
        Path workspace = root;
        Path module = workspace.resolve(InstallService.PROJECT_DIR);
        Files.createDirectories(module);

        InstallService.generateExampleRunConfiguration(workspace, s -> {
        });

        Path rc = workspace.resolve(".idea/runConfigurations/ExampleApplication.xml");
        assertTrue(Files.isRegularFile(rc), "运行配置应生成在工程根 .idea 下");
        assertFalse(Files.exists(module.resolve(".idea/runConfigurations/ExampleApplication.xml")),
                "不应再写入示例模块自身（IDEA 无法从聚合工程识别模块内的 .idea）");
        String xml = Files.readString(rc, StandardCharsets.UTF_8);
        assertTrue(xml.contains("type=\"SpringBootApplicationConfigurationType\""), "应为 Spring Boot 类型");
        assertTrue(xml.contains("<module name=\"OntologyFrameworkExample\" />"), "module 名应为示例模块");
        assertTrue(xml.contains("<option name=\"SHORTEN_COMMAND_LINE\" value=\"ARGS_FILE\" />"),
                "须含 SHORTEN_COMMAND_LINE=ARGS_FILE（依赖繁多时避免命令行过长致 Java 进程无法启动）");
        assertTrue(xml.contains("value=\"com.ocean.ontologyframework.example.ExampleApplication\""),
                "主类应指向 ExampleApplication");
        assertTrue(xml.contains("value=\"$MODULE_WORKING_DIR$\""), "Working directory 应为 $MODULE_WORKING_DIR$");

        Path suiteRc = workspace.resolve(".idea/runConfigurations/AllExampleSuiteTest.xml");
        assertTrue(Files.isRegularFile(suiteRc), "应生成测试套件运行配置 AllExampleSuiteTest.xml");
        assertFalse(Files.exists(module.resolve(".idea/runConfigurations/AllExampleSuiteTest.xml")),
                "测试套件运行配置也应写在工程根而非示例模块自身");
        String suiteXml = Files.readString(suiteRc, StandardCharsets.UTF_8);
        assertTrue(suiteXml.contains("type=\"JUnit\""), "应为 JUnit 类型");
        assertTrue(suiteXml.contains("<module name=\"OntologyFrameworkExample\" />"), "module 名应为示例模块");
        assertTrue(suiteXml.contains("<shortenClasspath name=\"ARGS_FILE\" />"),
                "JUnit 套件配置须含 <shortenClasspath name=\"ARGS_FILE\" />（JUnit 类型的正确字段名，IDEA 才显示 Shorten command line）");
        assertTrue(suiteXml.contains("value=\"com.ocean.ontologyframework.example.AllExampleSuiteTest\""),
                "测试类应指向 AllExampleSuiteTest");
        assertTrue(suiteXml.contains("<option name=\"TEST_OBJECT\" value=\"class\" />"), "应按 class 运行");
        assertTrue(suiteXml.contains("value=\"$MODULE_WORKING_DIR$\""), "Working directory 应为 $MODULE_WORKING_DIR$");
    }

    @Test
    void configureMavenLocalRepo_rewritesDefaultLocalRepository(@TempDir Path root) throws Exception {
        Path mavenHome = root.resolve("apache-maven-3.9.16");
        Path conf = mavenHome.resolve("conf");
        Files.createDirectories(conf);
        Path settings = conf.resolve("settings.xml");
        Files.writeString(settings,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.2.0\"\n"
                        + "          xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n"
                        + "          xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd\">\n"
                        + "  <!-- localRepository\n"
                        + "   | Default: ${user.home}/.m2/repository\n"
                        + "  <localRepository>/path/to/local/repo</localRepository>\n"
                        + "  -->\n"
                        + "</settings>\n", StandardCharsets.UTF_8);
        Path repoDir = root.resolve("install/maven-repo");
        Files.createDirectories(repoDir);

        Map<String, Path> homes = new LinkedHashMap<>();
        homes.put("apache-maven-3.9.16-bin.zip", mavenHome);

        InstallService.configureMavenLocalRepo(homes, repoDir, s -> {
        });

        String repo = repoDir.toAbsolutePath().normalize().toString().replace('\\', '/');
        String token = "<localRepository>" + repo + "</localRepository>";
        String out = Files.readString(settings, StandardCharsets.UTF_8);
        assertTrue(out.contains(token), "应写入指向安装后离线仓库的 localRepository");
        assertTrue(out.contains("<offline>true</offline>"), "应强制离线（offline=true），杜绝从 repo.maven.apache.org 抓取");
        assertEquals(2, out.split(Pattern.quote("</localRepository>"), -1).length - 1,
                "注释块内的缺省示例应保留（不计入生效项）");

        // 幂等：重复执行不应产生第二条生效的 localRepository / offline
        InstallService.configureMavenLocalRepo(homes, repoDir, s -> {
        });
        String out2 = Files.readString(settings, StandardCharsets.UTF_8);
        assertEquals(1, out2.split(Pattern.quote(token), -1).length - 1, "重复执行应只有一条生效的 localRepository");
        assertEquals(1, out2.split(Pattern.quote("<offline>true</offline>"), -1).length - 1, "重复执行应只有一条 offline");
    }

    @Test
    void writeMavenConfig_setsOfflineAndLocalRepo(@TempDir Path root) throws Exception {
        Path workspace = root.resolve("work");
        Path repoDir = root.resolve("install/maven-repo");
        Files.createDirectories(repoDir);

        InstallService.writeMavenConfig(workspace, repoDir, s -> {
        });

        Path cfg = workspace.resolve(".mvn/maven.config");
        assertTrue(Files.isRegularFile(cfg), "应生成 .mvn/maven.config");
        String out = Files.readString(cfg, StandardCharsets.UTF_8).replace("\r\n", "\n");
        String repo = repoDir.toAbsolutePath().normalize().toString().replace('\\', '/');
        assertTrue(out.contains("-o\n") || out.startsWith("-o\n"), "首行应为离线开关 -o（绝不访问远程仓库）");
        assertTrue(out.contains("-Dmaven.repo.local=" + repo), "应把本地仓库指向随包离线仓库");
        assertFalse(out.contains("repo.maven.apache.org"), "配置中不应出现任何远程仓库地址");

        // 幂等：重复执行内容一致
        InstallService.writeMavenConfig(workspace, repoDir, s -> {
        });
        assertEquals(out, Files.readString(cfg, StandardCharsets.UTF_8).replace("\r\n", "\n"),
                "重复执行应幂等");
    }

    @Test
    void configureIdeaMavenWorkspaceSettings_overridesIdeaMavenToOfflineRepo(@TempDir Path root) throws Exception {
        Path installDir = root.resolve("install");
        Path repoDir = installDir.resolve("maven-repo");
        Files.createDirectories(repoDir);
        Path workspaceRoot = root.resolve("workspace");
        Files.createDirectories(workspaceRoot.resolve(".idea"));
        // 预置一个含其它 component 的 workspace.xml，验证插入时不破坏既有内容。
        Path ws = workspaceRoot.resolve(".idea/workspace.xml");
        Files.writeString(ws,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\">\n"
                        + "  <component name=\"ProjectId\" id=\"keepme\" />\n</project>\n",
                StandardCharsets.UTF_8);

        Path mavenHome = installDir.resolve("apache-maven-3.9.16");

        InstallService.configureIdeaMavenWorkspaceSettings(workspaceRoot, installDir, repoDir, mavenHome, s -> {
        });

        String repo = repoDir.toAbsolutePath().normalize().toString().replace('\\', '/');
        Path settings = installDir.resolve("maven-settings.xml");
        String settingsSlash = settings.toAbsolutePath().normalize().toString().replace('\\', '/');
        assertTrue(Files.isRegularFile(settings), "应在安装目录生成 maven-settings.xml");
        String set = Files.readString(settings, StandardCharsets.UTF_8);
        assertTrue(set.contains("<localRepository>" + repo + "</localRepository>"), "settings 应指向离线仓库");
        assertTrue(set.contains("<offline>true</offline>"), "settings 应开启离线");

        String w = Files.readString(ws, StandardCharsets.UTF_8);
        assertTrue(w.contains("<component name=\"MavenImportPreferences\">"), "应写入 MavenImportPreferences");
        assertTrue(w.contains("<option name=\"userSettingsFile\" value=\"" + settingsSlash + "\" />"),
                "应 override 用户设置文件（IDEA 真实 tag: userSettingsFile）");
        assertTrue(w.contains("<option name=\"localRepository\" value=\"" + repo + "\" />"),
                "应 override 本地仓库（IDEA 真实 tag: localRepository）");
        assertTrue(w.contains("<option name=\"useMavenConfig\" value=\"true\" />"), "应启用 .mvn/maven.config");
        assertTrue(w.contains("<option name=\"workOffline\" value=\"true\" />"), "应强制离线");
        String mavenHomeNative = mavenHome.toAbsolutePath().normalize().toString();
        assertTrue(w.contains("<option name=\"customMavenHome\" value=\"" + mavenHomeNative + "\" />"),
                "应把 Maven 主路径固定为随包 apache-maven（customMavenHome）");
        assertTrue(w.contains("<option name=\"mavenHomeTypeForPersistence\" value=\"CUSTOM\" />"),
                "Maven 主路径类型应为 CUSTOM（避免 Use Maven wrapper 联网下载）");
        assertTrue(w.contains("name=\"ProjectId\" id=\"keepme\""), "原有 component 不应被破坏");

        // 幂等：重复执行不产生重复的 component / localRepository。
        InstallService.configureIdeaMavenWorkspaceSettings(workspaceRoot, installDir, repoDir, mavenHome, s -> {
        });
        String w2 = Files.readString(ws, StandardCharsets.UTF_8);
        assertEquals(1, w2.split(Pattern.quote("<component name=\"MavenImportPreferences\">"), -1).length - 1,
                "重复执行应只有一条 MavenImportPreferences");
        String set2 = Files.readString(settings, StandardCharsets.UTF_8);
        assertEquals(1, set2.split(Pattern.quote("<localRepository>" + repo + "</localRepository>"), -1).length - 1,
                "重复执行应只有一条生效 localRepository");
    }

    @Test
    void parseMajorVersion_extractsMajorOrMinusOne() {
        assertEquals(10, InstallService.parseMajorVersion("10.0"));
        assertEquals(6, InstallService.parseMajorVersion("6.3"));
        assertEquals(-1, InstallService.parseMajorVersion(null));
        assertEquals(-1, InstallService.parseMajorVersion("abc"));
    }

    @Test
    void checkWindowsVersion_acceptsWin10AndWin11() throws Exception {
        InstallService.checkWindowsVersion("Windows 10", "10.0", -1, s -> {
        });
        InstallService.checkWindowsVersion("Windows 11", "10.0", 11, s -> {
        });
    }

    @Test
    void checkWindowsVersion_rejectsNonWindowsOrBelowWin10() {
        assertThrows(IOException.class,
                () -> InstallService.checkWindowsVersion("Windows 7", "6.1", -1, s -> {
                }), "Win7 应被拒绝");
        assertThrows(IOException.class,
                () -> InstallService.checkWindowsVersion("Windows 8.1", "6.3", -1, s -> {
                }), "Win8.1 应被拒绝");
        assertThrows(IOException.class,
                () -> InstallService.checkWindowsVersion("Linux", "5.15", -1, s -> {
                }), "非 Windows 应被拒绝");
    }

    @Test
    void checkWindowsVersion_registryOverridesStaleOsVersion() throws Exception {
        // os.version 误报 6.2，但注册表主版本为 10，应以注册表为准放行
        InstallService.checkWindowsVersion("Windows 10", "6.2", 10, s -> {
        });
    }

    @Test
    void enablePortableIdeaConfig_setsConfigAndSystemPaths(@TempDir Path root) throws Exception {
        Path ideaHome = root.resolve("idea");
        Path bin = ideaHome.resolve("bin");
        Files.createDirectories(bin);
        Path props = bin.resolve("idea.properties");
        Files.writeString(props,
                "# comment\n# idea.config.path=${user.home}/.IntelliJIdea/config\nidea.max.intellisense.filesize=2500\n");

        InstallService.enablePortableIdeaConfig(ideaHome, s -> {
        });

        String out = Files.readString(props, StandardCharsets.UTF_8);
        assertTrue(out.contains("idea.config.path=${idea.home.path}/config"), "应把 config.path 指向 IDE_HOME");
        assertTrue(out.contains("idea.system.path=${idea.home.path}/system"), "应把 system.path 指向 IDE_HOME");
        assertFalse(out.contains("${user.home}/.IntelliJIdea/config"), "旧注释项应被移除");
        assertTrue(out.contains("idea.max.intellisense.filesize=2500"), "其它行应保留");
    }
}
