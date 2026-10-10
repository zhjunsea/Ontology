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
                + "jdbc.password=zj780704\r\n"
                + "port=8080\r\n";
        Files.writeString(props, body, StandardCharsets.UTF_8);

        InstallService.alignJdbcPassword(root, s -> {
        });

        String out = Files.readString(props, StandardCharsets.UTF_8);
        assertTrue(out.contains("jdbc.password=\r\n"), "jdbc.password 应被清空且保留 CRLF");
        assertFalse(out.contains("zj780704"), "原密码不应残留");
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
}
