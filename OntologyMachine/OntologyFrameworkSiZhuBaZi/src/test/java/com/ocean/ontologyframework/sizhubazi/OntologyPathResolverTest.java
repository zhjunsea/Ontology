package com.ocean.ontologyframework.sizhubazi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OntologyPathResolver 全分支离线测试：空/绝对命中/绝对不命中/相对命中/相对不命中/解析异常降级。
 */
@DisplayName("路径解析·OntologyPathResolver.resolve 全分支（离线）")
class OntologyPathResolverTest {

    @Test
    @DisplayName("null / blank → 原样返回")
    void nullBlank() {
        assertThat(OntologyPathResolver.resolve(null)).isNull();
        assertThat(OntologyPathResolver.resolve("   ")).isEqualTo("   ");
        assertThat(OntologyPathResolver.resolve("")).isEqualTo("");
    }

    @Test
    @DisplayName("已存在绝对文件 → 返回规范化绝对路径")
    void existingAbsolute() throws Exception {
        Path tmp = Files.createTempFile("ontopath", ".owl");
        try {
            String r = OntologyPathResolver.resolve(tmp.toString());
            assertThat(Path.of(r)).exists();
            assertThat(Path.of(r).isAbsolute()).isTrue();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("不存在的绝对路径 → 原样返回（保留既有报错信息）")
    void missingAbsolute() {
        String abs = Path.of("").toAbsolutePath().resolve("___no_such_dir___/x.owl").toString();
        assertThat(OntologyPathResolver.resolve(abs)).isEqualTo(abs);
    }

    @Test
    @DisplayName("存在的相对路径 → 解析为绝对路径")
    void existingRelative() throws Exception {
        Path tmp = Path.of("").toAbsolutePath().resolve("_relpath_probe_" + System.nanoTime() + ".owl");
        Files.writeString(tmp, "x");
        Path rel = Path.of("").toAbsolutePath().relativize(tmp.toAbsolutePath());
        try {
            String r = OntologyPathResolver.resolve(rel.toString());
            assertThat(Path.of(r)).exists();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @DisplayName("不存在的相对路径 → 向上查找失败后原样返回")
    void missingRelative() {
        String rel = "___no_such___/y.owl";
        assertThat(OntologyPathResolver.resolve(rel)).isEqualTo(rel);
    }

    @Test
    @DisplayName("非法路径字符 → 解析异常降级为原样返回")
    void invalidPath() {
        String bad = "bad\u0000name.owl";
        assertThat(OntologyPathResolver.resolve(bad)).isEqualTo(bad);
    }
}
