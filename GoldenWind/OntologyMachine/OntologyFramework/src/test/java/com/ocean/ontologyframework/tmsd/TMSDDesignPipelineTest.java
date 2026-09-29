package com.ocean.ontologyframework.tmsd;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 完全正向设计流水线求解与本体约束校验测试（不依赖 Camunda / MySQL / 本体服务）。
 *
 * <p>验证 {@link TmsdDesignPipeline} 对设计输入求解正向方案（单一方案，附件间距贪心取大），
 * 并用本体 v16.3 的 16 条可数值校验 Restriction 逐条判定；同时打印满足约束的方案与差异。
 */
class TMSDDesignPipelineTest {

    private static TowerDesignRequest base;

    @BeforeAll
    static void loadInputs() throws Exception {
        TmsdTestConfig.initTmsdVocabulary();
        TowerGeometry geo = TowerExcelReader.readGeometry(
                Paths.get(TmsdTestConfig.tmsd("historical-geo-path")));
        LayoutSpec lay = TowerExcelReader.readLayout(
                Paths.get(TmsdTestConfig.tmsd("historical-layout-path")));
        base = new TowerDesignRequest("正向用例-V12", geo, "V12",
                ElevatorType.ROPE_GUIDED, "中国", AccessoryConnectionType.WELDED, lay);
    }

    @Test
    @DisplayName("正向求解：单一方案有解且满足全部本体约束，且有推荐方案")
    void solved() {
        TmsdDesignPipeline.CaseResult r = TmsdDesignPipeline.design(base);
        assertThat(r.variants()).as("正向方案数").hasSize(1);
        assertThat(r.satisfied()).as("应有满足约束的方案").isNotEmpty();
        assertThat(r.recommended()).as("应有推荐方案").isNotNull();
    }

    @Test
    @DisplayName("附件排布规则：有解时首组 980、增量为 280 倍数且 ∈[1400,1960]、末组到平台∈[840,1960]；无解时输出为空")
    void accessoryRules() {
        TmsdDesignPipeline.CaseResult r = TmsdDesignPipeline.design(base);
        TmsdDesignPipeline.VariantResult rec = r.recommended();
        assertThat(rec).isNotNull();
        for (TowerDesignEngine.SectionDesign sd : rec.sections().values()) {
            // 与附件是否为空无关的固定规则
            assertThat(sd.rungSpacing()).isEqualTo(280.0);
            assertThat(sd.firstRungToBottom()).isEqualTo(140.0);
            assertThat(sd.lightType()).isEqualTo("焊接灯");

            List<Double> inc = sd.accessoryIncrements();
            if (sd.accessoryHeights().isEmpty()) {
                // 本样例几何下附件排布无解（n=0）：允许空输出
                assertThat(inc).isEmpty();
                assertThat(sd.accessoryCount()).isZero();
                continue;
            }
            assertThat(inc).isNotEmpty();
            assertThat(inc.get(0)).as("首组绝对高度=980").isEqualTo(980.0);
            for (int i = 1; i < inc.size(); i++) {
                assertThat(inc.get(i) % 280).as("增量须为 280 倍数").isEqualTo(0.0);
                assertThat(inc.get(i)).isBetween(1400.0, 1960.0);
            }
            double smin = TmsdDesignPipeline.minSpacing(sd.accessoryHeights());
            double smax = TmsdDesignPipeline.maxSpacing(sd.accessoryHeights());
            if (!Double.isNaN(smin)) {
                assertThat(smin).isGreaterThanOrEqualTo(1400.0 - 1e-6);
                assertThat(smax).isLessThanOrEqualTo(1960.0 + 1e-6);
            }
            assertThat(sd.secondLastToPlatform()).isBetween(840.0, 1960.0);
            assertThat(sd.minAccessoryToWeldDistance()).as("附件边缘避焊缝 > 100").isGreaterThan(100.0);
        }
    }

    @Test
    @DisplayName("机型变化（V17）：仍可正向求解并满足约束")
    void modelVariation() {
        TowerDesignRequest v17 = new TowerDesignRequest("正向用例-V17", base.geometry(), "V17",
                base.elevatorType(), "中国", AccessoryConnectionType.WELDED, base.layoutReference());
        TmsdDesignPipeline.CaseResult r = TmsdDesignPipeline.design(v17);
        assertThat(r.satisfied()).isNotEmpty();
        assertThat(r.recommended()).isNotNull();
        TowerDesignEngine.SectionDesign sd = r.recommended().sections().values().iterator().next();
        assertThat(sd.parameters().get("L_CABLE")).isEqualTo(800.0);
    }

    @Test
    @DisplayName("打印满足方案与差异（人工复核用）")
    void printAll() {
        TmsdDesignPipeline.CaseResult r = TmsdDesignPipeline.design(base);
        System.out.println("================================================================");
        System.out.println("用例：" + r.caseName());
        System.out.println("满足约束方案数：" + r.satisfied().size() + " / " + r.variants().size());
        for (TmsdDesignPipeline.VariantResult v : r.satisfied()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, TowerDesignEngine.SectionDesign> e : v.sections().entrySet()) {
                sb.append(String.format(" 第%d段[附件%d 间距%.1f 扶持%.1f 灯%d]",
                        e.getKey(), e.getValue().accessoryCount(),
                        e.getValue().accessorySpacing(), e.getValue().supportHeight(),
                        e.getValue().lightHeights().size()));
            }
            System.out.println("  ✔ " + v.variantName() + sb);
        }
        for (TmsdDesignPipeline.VariantResult v : r.rejected()) {
            System.out.println("  ✘ " + v.variantName() + " 原因：" + v.failures());
        }
    }
}
