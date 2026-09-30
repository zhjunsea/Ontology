package com.ocean.ontologyframework.tmsd;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 完全正向设计规则单元测试（不依赖 Camunda / MySQL / 本体服务）。
 *
 * <p>直接验证 {@link TowerDesignEngine} 的正向规则：附件排布（280 倍数 + 边缘避焊缝）、
 * 扶持直读、梯档 280 / 踏棍 140、防雷螺柱角度、灯型固定焊接灯、灯间距。
 */
class TMSDForwardRulesTest {

    @BeforeAll
    static void init() {
        TmsdTestConfig.initTmsdVocabulary();
    }

    @Test
    @DisplayName("附件排布：首组 980，增量为 280 倍数且 ∈[1400,1960]，末组到平台 ∈[840,1960]")
    void accessoryLayoutRules() {
        // 平台高 14000mm，焊缝落在 280 网格（此时忽略边缘偏移即有解）
        double platformHeight = 14000;
        List<Integer> welds = List.of(2800, 5600, 8400);
        TowerDesignEngine.AccessoryLayout acc =
                TowerDesignEngine.accessoryLayout(platformHeight, welds, 0);

        assertThat(acc.heights()).isNotEmpty();
        assertThat(acc.increments().get(0)).isEqualTo(980.0);
        for (int i = 1; i < acc.increments().size(); i++) {
            assertThat(acc.increments().get(i) % 280).isEqualTo(0.0);
            assertThat(acc.increments().get(i)).isBetween(1400.0, 1960.0);
        }
        assertThat(acc.lastToPlatform()).isBetween(840.0, 1960.0);
        assertThat(acc.minWeldDistance()).as("附件边缘避焊缝 > 100").isGreaterThan(100.0);
    }

    @Test
    @DisplayName("附件边缘避焊缝：边缘偏移取爬梯支撑半宽 455 时，280 网格无解")
    void edgeAvoidance() {
        double platformHeight = 14000;
        List<Integer> welds = List.of(2800, 5600, 8400);
        TowerDesignEngine.AccessoryLayout small =
                TowerDesignEngine.accessoryLayout(platformHeight, welds, 0);
        // 边缘偏移 = W_LADDER_I/2 = 455：焊缝落在 280 网格的半格上，附件恒距焊缝 140mm（<=100+455），无解
        TowerDesignEngine.AccessoryLayout large =
                TowerDesignEngine.accessoryLayout(platformHeight, welds, 455);
        assertThat(small.heights()).isNotEmpty();
        assertThat(large.heights()).isEmpty();
    }

    @Test
    @DisplayName("电缆线夹（隔开）：首组=H1_L，其后每 2 个爬梯支撑增量合并为 1 组")
    void cableClampMerge() {
        List<Double> ladder = List.of(980.0, 1960.0, 1960.0, 1960.0, 1960.0);
        List<Double> cs = TowerDesignEngine.cableClampIncrements(ladder);
        // 首组 980；其后 (1960+1960)=3920（索引1+2）、(1960+1960)=3920（索引3+4）
        assertThat(cs).containsExactly(980.0, 3920.0, 3920.0);
    }

    @Test
    @DisplayName("防雷螺柱：每法兰一组 3 个，角度取自本体 = 70°、190°、310°")
    void lightningStudAngles() {
        assertThat(TmsdVocabulary.valueSet("lightningStudInstallAngle"))
                .containsExactly(70.0, 190.0, 310.0);
    }

    @Test
    @DisplayName("灯布置：首灯 ∈[2600,3000]，相邻灯间距 ∈[5000,10000]")
    void lightRules() {
        List<Integer> welds = List.of(2800, 5600, 8400);
        List<Double> lights = TowerDesignEngine.lightHeights(30000, welds,
                TmsdVocabulary.upper("lightToLightMaxSpacing"), TmsdVocabulary.stringValue("lightType"));
        assertThat(lights).isNotEmpty();
        assertThat(lights.get(0)).isBetween(2600.0, 3000.0);
        for (int i = 1; i < lights.size(); i++) {
            double gap = lights.get(i) - lights.get(i - 1);
            assertThat(gap).isBetween(5000.0, 10000.0);
        }
    }
}
