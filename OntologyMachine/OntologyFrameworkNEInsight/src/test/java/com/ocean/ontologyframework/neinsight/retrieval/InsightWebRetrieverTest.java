package com.ocean.ontologyframework.neinsight.retrieval;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * InsightWebRetriever · 检索结果解析与降级（离线，不发起真实联网）。
 */
@DisplayName("InsightWebRetriever · 检索结果解析与降级（离线）")
public class InsightWebRetrieverTest {

    /** 博查（bocha）响应结构：webPages.value[]，字段 name/url/summary|snippet。 */
    private static final String BOCHA_BODY = """
            {"webPages":{"value":[
              {"name":"标题一","url":"https://a.example/1","summary":"第一段  摘要\\n内容","snippet":"片段"},
              {"name":"标题二","url":"https://b.example/2","snippet":"第二段"},
              {"name":"","url":"https://c.example/3"}
            ]}}""";

    /** Tavily 响应结构：results[]，字段 title/url/content。 */
    private static final String TAVILY_BODY = """
            {"results":[
              {"title":"T1","url":"https://t.example/1","content":"内容一  压缩\\n文本"},
              {"title":"","url":"https://t.example/2","content":""}
            ]}""";

    @Test
    @DisplayName("renderResults(bocha)：解析 name/url/summary(|snippet) 为 Markdown 列表，压缩空白")
    void rendersBochaResults() {
        String md = InsightWebRetriever.renderResults(BOCHA_BODY, "bocha", 4, 600);
        assertThat(md)
                .contains("- 标题一 —— https://a.example/1：第一段 摘要 内容")
                .contains("- 标题二 —— https://b.example/2：第二段")
                .contains("- https://c.example/3 —— https://c.example/3");
        assertThat(md.lines().count()).isEqualTo(3);
    }

    @Test
    @DisplayName("renderResults(tavily)：解析 title/url/content 为 Markdown 列表")
    void rendersTavilyResults() {
        String md = InsightWebRetriever.renderResults(TAVILY_BODY, "tavily", 4, 600);
        assertThat(md)
                .contains("- T1 —— https://t.example/1：内容一 压缩 文本")
                .contains("- https://t.example/2 —— https://t.example/2");
        assertThat(md.lines().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("renderResults：条数上限与摘要截断生效")
    void limitsAndTruncates() {
        String md = InsightWebRetriever.renderResults(BOCHA_BODY, "bocha", 1, 3);
        assertThat(md.lines().count()).isEqualTo(1);
        assertThat(md).contains("第一段…");
    }

    @Test
    @DisplayName("renderResults：空/非 JSON/结构不匹配输入返回空串（不抛异常）")
    void toleratesBadInput() {
        assertThat(InsightWebRetriever.renderResults(null, "bocha", 4, 600)).isEmpty();
        assertThat(InsightWebRetriever.renderResults("   ", "bocha", 4, 600)).isEmpty();
        assertThat(InsightWebRetriever.renderResults("not-json", "bocha", 4, 600)).isEmpty();
        assertThat(InsightWebRetriever.renderResults("{\"foo\":1}", "bocha", 4, 600)).isEmpty();
        assertThat(InsightWebRetriever.renderResults(TAVILY_BODY, "bocha", 4, 600)).isEmpty();
        assertThat(InsightWebRetriever.renderResults(BOCHA_BODY, "tavily", 4, 600)).isEmpty();
    }

    @Test
    @DisplayName("默认启用；enabled=false 时 retrieve 直接返回空，不发起联网")
    void disabledReturnsEmpty() {
        InsightWebRetriever r = new InsightWebRetriever();
        assertThat(r.isEnabled()).isTrue();
        ReflectionTestUtils.setField(r, "enabled", false);
        assertThat(r.isEnabled()).isFalse();
        Map<String, String> out = r.retrieve("s", List.of(
                new InsightWebRetriever.NodeQuery("scan-industry-trend", "o", List.of("q"))));
        assertThat(out).isEmpty();
    }

    @Test
    @DisplayName("未配置 api-key 时 retrieve 返回空（降级，不发起联网）")
    void unconfiguredKeySkips() {
        InsightWebRetriever r = new InsightWebRetriever();
        assertThat(r.isConfigured()).isFalse();
        Map<String, String> out = r.retrieve("s", List.of(
                new InsightWebRetriever.NodeQuery("scan-industry-trend", "o", List.of("q"))));
        assertThat(out).isEmpty();
    }
}
