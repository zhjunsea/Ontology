package com.ocean.ontologyframework.tmsd;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

class TmsdRenderDumpTest {

    private static TowerDesignRequest req;

    @BeforeAll
    static void load() throws Exception {
        TmsdTestConfig.initTmsdVocabulary();
        TowerGeometry geo = TowerExcelReader.readGeometry(
                Paths.get(TmsdTestConfig.tmsd("historical-geo-path")));
        LayoutSpec lay = TowerExcelReader.readLayout(
                Paths.get(TmsdTestConfig.tmsd("historical-layout-path")));
        req = new TowerDesignRequest("用例0", geo, "V12", ElevatorType.ROPE_GUIDED, "中国",
                AccessoryConnectionType.WELDED, lay);
    }

    @Test
    void dump() throws Exception {
        TmsdDesignPipeline.CaseResult r = TmsdDesignPipeline.design(req);
        Path out = Paths.get("D:/work/Ontology/GoldenWind/HWCodeArtsDir/tmsd-new2");
        for (Path p : TmsdOutputWriter.write(r, out)) {
            System.out.println("WROTE " + p);
        }
    }
}
