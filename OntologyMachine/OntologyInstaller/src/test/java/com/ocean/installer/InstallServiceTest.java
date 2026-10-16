package com.ocean.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void generateExampleRunConfiguration_writesSpringBootConfig(@TempDir Path root) throws Exception {
        Path module = root.resolve(InstallService.PROJECT_DIR);
        Files.createDirectories(module);

        InstallService.generateExampleRunConfiguration(module, s -> {
        });

        Path rc = module.resolve(".idea/runConfigurations/ExampleApplication.xml");
        assertTrue(Files.isRegularFile(rc), "运行配置应被生成");
        String xml = Files.readString(rc, StandardCharsets.UTF_8);
        assertTrue(xml.contains("type=\"SpringBootApplicationConfigurationType\""), "应为 Spring Boot 类型");
        assertTrue(xml.contains("<module name=\"OntologyFrameworkExample\" />"), "module 名应为示例模块");
        assertTrue(xml.contains("value=\"com.ocean.ontologyframework.example.ExampleApplication\""),
                "主类应指向 ExampleApplication");
        assertTrue(xml.contains("value=\"$MODULE_WORKING_DIR$\""), "Working directory 应为 $MODULE_WORKING_DIR$");
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
