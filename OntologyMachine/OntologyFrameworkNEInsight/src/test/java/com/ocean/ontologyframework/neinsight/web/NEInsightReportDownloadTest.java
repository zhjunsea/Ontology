package com.ocean.ontologyframework.neinsight.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 洞察报告下载端点（{@code GET /api/neinsight/insight/{key}/report}）离线验证。
 *
 * <p>不依赖真实引擎：{@link NEInsightController} 的 {@code client} 为 null 时，读取流程变量失败会
 * 自动回退「按命名规则定位 {@code <neinsight.report.dir>/neinsight-<key>-洞察报告.md}」，从而可在
 * 临时目录中构造报告文件直接驱动下载逻辑；同时覆盖「路径归属校验 + 404 兜底」。
 */
@DisplayName("NEInsight 下载端点 · 洞察报告（离线）")
public class NEInsightReportDownloadTest {

    @TempDir
    Path tmp;

    private NEInsightController newController() {
        NEInsightController controller = new NEInsightController();
        ReflectionTestUtils.setField(controller, "reportDir", tmp.toString());
        return controller;
    }

    @Test
    @DisplayName("D-1 文件存在：按命名规则定位并作为附件返回全文（text/markdown + attachment 文件名）")
    void downloadsReportByNamingFallback() throws Exception {
        long key = 456L;
        String content = "# 洞察报告\n\n> 报告下载端点测试内容\n";
        Files.write(tmp.resolve("neinsight-" + key + "-洞察报告.md"), content.getBytes(StandardCharsets.UTF_8));

        ResponseEntity<?> resp = newController().downloadInsightReport(key);

        assertThat(resp.getStatusCode()).as("应返回 200").isEqualTo(HttpStatus.OK);
        assertThat(String.valueOf(resp.getHeaders().getFirst("Content-Disposition")))
                .as("应作为附件下载并给出文件名")
                .contains("attachment")
                .contains("neinsight-" + key);
        assertThat(String.valueOf(resp.getHeaders().getContentType()))
                .as("内容类型应为 Markdown 文本")
                .contains("text/markdown");
        assertThat(new String((byte[]) resp.getBody(), StandardCharsets.UTF_8))
                .as("响应体应为报告文件全文")
                .isEqualTo(content);
    }

    @Test
    @DisplayName("D-2 文件缺失：返回 404 且携带友好错误信息")
    void returns404WhenReportMissing() {
        ResponseEntity<?> resp = newController().downloadInsightReport(99999L);

        assertThat(resp.getStatusCode()).as("应返回 404").isEqualTo(HttpStatus.NOT_FOUND);
        Object body = resp.getBody();
        assertThat(body).as("应返回结构化错误体").isInstanceOf(Map.class);
        assertThat(String.valueOf(((Map<?, ?>) body).get("error"))).contains("未找到");
    }
}
