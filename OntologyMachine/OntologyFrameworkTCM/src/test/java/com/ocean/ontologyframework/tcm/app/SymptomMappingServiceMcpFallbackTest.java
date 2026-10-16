package com.ocean.ontologyframework.tcm.app;

import com.ocean.utilities.LlmClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 症状映射 L4（MCP 大模型兜底）测试。
 *
 * <p>全部离线运行：L2 用返回 null 的 {@link LlmClient} 桩使其失败，L4 用
 * {@link SymptomMatchMcpClient#setOverride} 注入桩函数，不发起任何真实 MCP/HTTP 调用。
 */
class SymptomMappingServiceMcpFallbackTest {

    /** 与 {@code SymptomMappingServiceTest} 一致：SKOS 未收录的表述，保证 L1 无候选。 */
    private static final String UNMATCHABLE = "头发一夜变绿";

    private static SymptomCatalog catalog;

    @BeforeAll
    static void setUp() {
        catalog = new SymptomCatalog(SymptomCatalogTest.readAboxDir());
    }

    /** L2 全部失败（返回 null）→ 待处理项全部落到 L4。 */
    private static SymptomMappingService l2Fails() {
        LlmClient llm = new LlmClient((sys, user) -> null);
        SymptomMappingService s = new SymptomMappingService(catalog, llm);
        s.configure(3, 0.85, 0.50, 3, 60);
        return s;
    }

    private static SymptomMatchMcpClient mcpStub(BiFunction<String, String, String> stub) {
        SymptomMatchMcpClient c = new SymptomMatchMcpClient();
        c.setEnabled(false);
        c.setOverride(stub);
        return c;
    }

    private static String frag(String label) {
        return catalog.byLabel(label).orElseThrow().getFragment();
    }

    private static List<String> fragmentsOf(SymptomMappingService.MappingResult r) {
        return r.detail.stream().map(d -> String.valueOf(d.get("fragment"))).toList();
    }

    @Test
    @DisplayName("L4 兜底：L2 未解析的表述经 MCP 命中，且一律标为待确认")
    void l4ResolvesViaMcpAndNeedsConfirmation() {
        String target = frag("胸胁苦满");
        var svc = l2Fails();
        svc.setMcpFallback(mcpStub((sys, user) -> {
            assertThat(sys).contains("只能从候选清单中选择");
            assertThat(user).contains(UNMATCHABLE);
            return "{\"matches\":[{\"text\":\"" + UNMATCHABLE + "\",\"fragments\":[\"" + target
                    + "\"],\"confidence\":0.99}],\"unmatched\":[]}";
        }));

        var r = svc.map(UNMATCHABLE, null, null, null, 0);
        assertThat(fragmentsOf(r)).contains(target);
        assertThat(r.unmatched).isEmpty();
        assertThat(r.needsConfirmation).isTrue();
        var d = r.detail.stream().filter(x -> target.equals(x.get("fragment"))).findFirst().orElseThrow();
        assertThat(d.get("source")).isEqualTo("MCP_LLM");
    }

    @Test
    @DisplayName("L4 防幻觉：候选清单外片段被丢弃 → 未匹配")
    void l4RejectsHallucination() {
        var svc = l2Fails();
        svc.setMcpFallback(mcpStub((sys, user) ->
                "{\"matches\":[{\"text\":\"" + UNMATCHABLE + "\",\"fragments\":[\"NotExist_instance\"],"
                        + "\"confidence\":0.99}],\"unmatched\":[]}"));
        var r = svc.map(UNMATCHABLE, null, null, null, 0);
        assertThat(fragmentsOf(r)).doesNotContain("NotExist_instance");
        assertThat(r.unmatched).contains(UNMATCHABLE);
    }

    @Test
    @DisplayName("L4 降级：MCP 返回 null（不可用/超时）→ 未匹配，不抛异常")
    void l4DegradesOnNull() {
        var svc = l2Fails();
        svc.setMcpFallback(mcpStub((sys, user) -> null));
        var r = svc.map(UNMATCHABLE, null, null, null, 0);
        assertThat(r.unmatched).contains(UNMATCHABLE);
        assertThat(r.needsConfirmation).isTrue();
    }

    @Test
    @DisplayName("L4 降级：MCP 返回非法 JSON → 未匹配")
    void l4DegradesOnBadJson() {
        var svc = l2Fails();
        svc.setMcpFallback(mcpStub((sys, user) -> "这不是 JSON"));
        var r = svc.map(UNMATCHABLE, null, null, null, 0);
        assertThat(r.unmatched).contains(UNMATCHABLE);
    }

    @Test
    @DisplayName("L4 默认关闭：未注入 MCP 通道 → 保持既有行为（未匹配）")
    void l4SkippedWhenNotInjected() {
        var svc = l2Fails();
        var r = svc.map(UNMATCHABLE, null, null, null, 0);
        assertThat(r.unmatched).contains(UNMATCHABLE);
    }

    @Test
    @DisplayName("L4 仅在 L2 未解析时触发：L2 命中则不调用 MCP")
    void l4NotInvokedWhenL2Resolves() {
        String target = frag("胸胁苦满");
        LlmClient llm = new LlmClient((sys, user) ->
                "{\"matches\":[{\"text\":\"" + UNMATCHABLE + "\",\"fragments\":[\"" + target
                        + "\"],\"confidence\":0.99}],\"unmatched\":[]}");
        SymptomMappingService svc = new SymptomMappingService(catalog, llm);
        svc.configure(3, 0.85, 0.50, 3, 60);

        final boolean[] mcpCalled = {false};
        svc.setMcpFallback(mcpStub((sys, user) -> {
            mcpCalled[0] = true;
            return null;
        }));

        var r = svc.map(UNMATCHABLE, null, null, null, 0);
        assertThat(fragmentsOf(r)).contains(target);
        assertThat(mcpCalled[0]).isFalse();
    }
}
