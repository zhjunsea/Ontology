package com.ocean.ontologyframework.industryinsight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行业洞察「联网检索取证」通道单元测试（离线，不发起任何真实 MCP/HTTP 调用）。
 *
 * <p>经 {@link IndustryInsightWebSearch#setOverride} 注入桩函数，验证：默认关闭不检索、
 * 结果解析、证据块渲染（[S#] 全局连续编号）、非法/空返回降级为 {@code null}。
 */
@DisplayName("行业洞察 · 联网检索取证通道（离线）")
class IndustryInsightWebSearchTest {

    @Test
    @DisplayName("默认关闭：isEnabled=false，search 返回 null，不发起外部调用")
    void disabledByDefault() {
        IndustryInsightWebSearch ws = new IndustryInsightWebSearch();
        assertThat(ws.isEnabled()).isFalse();
        assertThat(ws.search("objective", List.of("q"), null)).isNull();
    }

    @Test
    @DisplayName("注入桩：解析 results 并渲染 [S#] 证据块（编号自 baseIndex 连续）")
    void parsesAndRendersEvidence() {
        IndustryInsightWebSearch ws = new IndustryInsightWebSearch();
        ws.setOverride((objective, queries) -> "{\"results\":["
                + "{\"url\":\"https://a.example/x\",\"title\":\"标题A\",\"excerpts\":[\"摘录1\"]},"
                + "{\"url\":\"https://b.example/y\",\"title\":\"标题B\",\"excerpts\":[\"摘录2\",\"摘录2b\"]}]}");

        IndustryInsightWebSearch.Result result = ws.search("obj", List.of("q"), "s1");

        assertThat(result).isNotNull();
        assertThat(result.sources()).hasSize(2);
        String block = ws.renderEvidence(result, 3);
        assertThat(block)
                .contains("[S4]").contains("[S5]")
                .contains("标题A").contains("https://a.example/x").contains("摘录1")
                .contains("标题B").contains("https://b.example/y").contains("摘录2b");
    }

    @Test
    @DisplayName("非法 / 空返回：search 返回 null（降级，不抛异常）")
    void invalidOrBlankReturnsNull() {
        IndustryInsightWebSearch ws = new IndustryInsightWebSearch();
        ws.setOverride((o, q) -> "这不是 JSON");
        assertThat(ws.search("o", List.of("q"), null)).isNull();

        ws.setOverride((o, q) -> "");
        assertThat(ws.search("o", List.of("q"), null)).isNull();

        ws.setOverride((o, q) -> null);
        assertThat(ws.search("o", List.of("q"), null)).isNull();
    }

    @Test
    @DisplayName("results 为空数组：返回 null（无来源不注入）")
    void emptyResultsReturnsNull() {
        IndustryInsightWebSearch ws = new IndustryInsightWebSearch();
        ws.setOverride((o, q) -> "{\"results\":[]}");
        assertThat(ws.search("o", List.of("q"), null)).isNull();
    }

    @Test
    @DisplayName("未注入桩且未开启：renderEvidence(null) 返回空串")
    void renderNullSafe() {
        IndustryInsightWebSearch ws = new IndustryInsightWebSearch();
        assertThat(ws.renderEvidence(null, 0)).isEmpty();
    }
}
