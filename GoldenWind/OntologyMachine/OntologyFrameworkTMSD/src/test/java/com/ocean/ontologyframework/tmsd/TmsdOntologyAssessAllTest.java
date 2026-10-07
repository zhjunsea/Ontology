package com.ocean.ontologyframework.tmsd;

import com.ocean.openlletresolver.BackendService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 在线逐项声明式判定（{@link TmsdOntologyService#assessAll}）集成测试。
 *
 * <p>验证生产路径：把一段设计的各项实测值物化进本体为校验个体，由 Openllet 分类回读
 * 「合规类 / :约束违规 / :无附件段」，并按段逐项产出判定；与离线 Java 兜底
 * （{@link TmsdDesignPipeline#javaVerdicts}）逐项一致。
 */
class TmsdOntologyAssessAllTest {

    private static TmsdOntologyService svc;
    private static TowerDesignRequest req;

    @BeforeAll
    static void setUp() throws Exception {
        TmsdTestConfig.initTmsdVocabulary();
        TowerGeometry geo = TowerExcelReader.readGeometry(
                Paths.get(TmsdTestConfig.tmsd("historical-geo-path")));
        LayoutSpec lay = TowerExcelReader.readLayout(
                Paths.get(TmsdTestConfig.tmsd("historical-layout-path")));
        req = new TowerDesignRequest("在线判定用例-V12", geo, "V12",
                ElevatorType.ROPE_GUIDED, "中国", AccessoryConnectionType.WELDED, lay);
        BackendService backend = BackendService.getInstance(TmsdTestConfig.ontology("main-path"));
        svc = new TmsdOntologyService(backend);
    }

    @Test
    @DisplayName("assessAll：在线本体回读的各项判定与 Java 兜底逐项一致")
    void assessAllMatchesJavaFallback() {
        TowerDesignEngine.DesignVariant v = TowerDesignEngine.variants().get(0);
        for (int s : req.middleSectionNumbers()) {
            TowerDesignEngine.SectionDesign sd = TowerDesignEngine.designSection(
                    req.geometry(), s, req, req.layoutReference(), v);
            TmsdDesignPipeline.ConstraintVerdicts online = svc.assessAll("T_ACC_" + s, req, sd);
            TmsdDesignPipeline.ConstraintVerdicts offline = TmsdDesignPipeline.javaVerdicts(req, sd);

            assertThat(online).as("在线回读不应为 null（本体服务可用）").isNotNull();
            assertThat(online.noAccessory()).as("第%d段无附件段状态", s).isEqualTo(offline.noAccessory());
            assertThat(online.pass().keySet()).as("第%d段在线判定应覆盖离线各项", s)
                    .containsAll(offline.pass().keySet());
            for (Map.Entry<String, Boolean> e : offline.pass().entrySet()) {
                assertThat(online.get(e.getKey()))
                        .as("第%d段 %s：在线(本体回读)=%s 应等于离线(Java)=%s",
                                s, e.getKey(), online.get(e.getKey()), e.getValue())
                        .isEqualTo(e.getValue());
            }
        }
    }
}
