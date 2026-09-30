package com.ocean.ontologyframework.tmsd;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;

/**
 * 临时诊断：打印各段的几何、焊缝与附件排布（正向 280 倍数 + 边缘避焊缝）搜索结果。
 */
class TmsdDebugTest {

    private static TowerDesignRequest req;

    @BeforeAll
    static void load() throws Exception {
        TmsdTestConfig.initTmsdVocabulary();
        TowerGeometry geo = TowerExcelReader.readGeometry(
                Paths.get(TmsdTestConfig.tmsd("historical-geo-path")));
        LayoutSpec lay = TowerExcelReader.readLayout(
                Paths.get(TmsdTestConfig.tmsd("historical-layout-path")));
        req = new TowerDesignRequest("诊断", geo, "V12", ElevatorType.ROPE_GUIDED, "中国",
                AccessoryConnectionType.WELDED, lay);
    }

    @Test
    void dump() {
        for (int s : req.middleSectionNumbers()) {
            TowerGeometry.SectionCourses sc = req.geometry().sectionCourses(s - 1);
            double secH = sc.totalHeight();
            double hPlat = req.platformDistance(s);
            double platH = secH - hPlat;
            List<Integer> welds = req.geometry().weldPositions(s - 1);
            LayoutSpec.MiddleSection lay = req.layoutReference().middleSection(s);
            double wRung = lay != null ? lay.rungWidth() : 85;
            TowerDesignEngine.AccessoryLayout acc =
                    TowerDesignEngine.accessoryLayout(platH, welds, wRung / 2.0);
            System.out.printf("  第%d段 secH=%.1f platH=%.1f courses=%d welds=%s%n",
                    s, secH, platH, sc.heights().size(), welds);
            System.out.printf("     →贪心取大 n=%d spacing=%.1f lastToPlat=%.1f minWeld=%.1f heights=%s%n",
                    acc.heights().size(), acc.spacing(), acc.lastToPlatform(), acc.minWeldDistance(), acc.heights());
        }
    }
}
