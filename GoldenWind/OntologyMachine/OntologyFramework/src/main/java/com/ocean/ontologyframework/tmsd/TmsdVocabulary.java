package com.ocean.ontologyframework.tmsd;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 塔架中段本体词表与<b>运行时约束入口</b>。
 *
 * <p><b>零硬编码</b>：本类只保留本体中稳定的 IRI 常量（类/对象属性/数据属性），
 * 一切「业务真值」（版本、数值约束、值集、机型映射）都不再以字面量镜像，而是
 * 在 {@link #init(Path)} 时由 {@link TmsdOntologyModel} 从
 * {@code TowerMidSection.owl} 解析得到，供引擎/校验/输出统一取值。
 *
 * <p>命名空间：{@code http://goldwind.com/ontology/tower-mid-section#}
 *
 * <p>初始化入口：运行时由 {@code TMSDOntologyJobWorker}（Spring 启动时，读配置
 * {@code ontology.main-path}）调用；测试由 {@code TmsdTestConfig} 提供同一路径。
 */
public final class TmsdVocabulary {

    private TmsdVocabulary() {
    }

    public static final String NS = "http://goldwind.com/ontology/tower-mid-section#";

    // ==================== 类（64 个，仅列本应用使用的） ====================
    public static final String C_ACCESSORY = NS + "Accessory";
    public static final String C_CABLE = NS + "Cable";
    public static final String C_CABLE_BRACKET = NS + "CableBracket";
    public static final String C_CABLE_TRAY = NS + "CableTray";
    public static final String C_COMPONENT = NS + "Component";
    public static final String C_ELEVATOR = NS + "Elevator";
    public static final String C_FLANGE = NS + "Flange";
    public static final String C_LADDER = NS + "Ladder";
    public static final String C_LADDER_GUIDED_ELEVATOR = NS + "LadderGuidedElevator";
    public static final String C_LADDER_SUPPORT = NS + "LadderSupport";
    public static final String C_LIGHT = NS + "Light";
    public static final String C_LIGHTNING_GROUNDING_STUD = NS + "LightningGroundingStud";
    public static final String C_MODEL = NS + "Model";
    public static final String C_PLATFORM = NS + "Platform";
    public static final String C_REGION = NS + "Region";
    public static final String C_ROPE_GUIDED_ELEVATOR = NS + "RopeGuidedElevator";
    public static final String C_STUD = NS + "Stud";
    public static final String C_SUPPORT = NS + "Support";
    public static final String C_TOWER = NS + "Tower";
    public static final String C_TOWER_MID_SECTION = NS + "TowerMidSection";
    public static final String C_TOWER_TUBE = NS + "TowerTube";
    public static final String C_TRAY_LIGHT = NS + "TrayLight";
    public static final String C_TUBE_SECTION = NS + "TubeSection";
    public static final String C_WELD_SEAM = NS + "WeldSeam";
    public static final String C_WELDED_LIGHT = NS + "WeldedLight";

    // ==================== 对象属性 ====================
    public static final String OP_HAS_ACCESSORY = NS + "hasAccessory";
    public static final String OP_HAS_COMPONENT = NS + "hasComponent";
    public static final String OP_HAS_ELEVATOR = NS + "hasElevator";
    public static final String OP_HAS_FLANGE = NS + "hasFlange";
    public static final String OP_HAS_LADDER = NS + "hasLadder";
    public static final String OP_HAS_LIGHT = NS + "hasLight";
    public static final String OP_HAS_MODEL = NS + "hasModel";
    public static final String OP_HAS_PLATFORM = NS + "hasPlatform";
    public static final String OP_HAS_REGION = NS + "hasRegion";
    public static final String OP_HAS_SUPPORT = NS + "hasSupport";
    public static final String OP_HAS_TOWER_MID_SECTION = NS + "hasTowerMidSection";
    public static final String OP_HAS_TOWER_TUBE = NS + "hasTowerTube";
    public static final String OP_HAS_TUBE_SECTION = NS + "hasTubeSection";
    public static final String OP_HAS_WELD_SEAM = NS + "hasWeldSeam";
    public static final String OP_FLUSH_WITH = NS + "flushWith";
    public static final String OP_MAPPED_TO_LADDER_SUPPORT = NS + "mappedToLadderSupport";
    public static final String OP_MOUNTED_ON_STUD = NS + "mountedOnStud";
    public static final String OP_SUPPORTS_LADDER = NS + "supportsLadder";
    public static final String OP_CONNECTS_TUBE_SECTION = NS + "connectsTubeSection";
    public static final String OP_PLATFORM_TO_TUBE_TOP = NS + "platformToTubeTop";
    public static final String OP_INTEGRATED_IN_TRAY = NS + "integratedInTray";
    public static final String OP_CARRIES_CABLE = NS + "carriesCable";
    public static final String OP_DOCKS_AT_PLATFORM = NS + "docksAtPlatform";
    public static final String OP_GUIDED_BY_LADDER = NS + "guidedByLadder";
    public static final String OP_INSTALLED_IN_TOWER_TUBE = NS + "installedInTowerTube";
    public static final String OP_STUD_TO_WELD_SEAM_POSITION = NS + "studToWeldSeamPosition";
    public static final String OP_HAS_POSITION_TO_WELD_SEAM = NS + "hasPositionToWeldSeam";
    public static final String OP_SUPPORTS_CABLE_TRAY = NS + "supportsCableTray";
    public static final String OP_PASSES_THROUGH_PLATFORM = NS + "passesThroughPlatform";
    public static final String OP_ADJACENT_TO = NS + "adjacentTo";

    // ==================== 数据属性 ====================
    public static final String DP_ACCESSORY_CENTER_SPACING = NS + "accessoryCenterSpacing";
    public static final String DP_ACCESSORY_CONNECTION_TYPE = NS + "accessoryConnectionType";
    public static final String DP_ACCESSORY_TO_WELD_DISTANCE = NS + "accessoryToWeldDistance";
    public static final String DP_ACCESSORY_TYPE = NS + "accessoryType";
    public static final String DP_AVERAGE_WALL_THICKNESS = NS + "averageWallThickness";
    public static final String DP_BOLT_CIRCLE_DIAMETER = NS + "boltCircleDiameter";
    public static final String DP_BOLT_COUNT = NS + "boltCount";
    public static final String DP_BOLT_GRADE = NS + "boltGrade";
    public static final String DP_BOLT_HOLE_DIAMETER = NS + "boltHoleDiameter";
    public static final String DP_BRACKET_LEFT_CHORD = NS + "bracketLeftChord";
    public static final String DP_BRACKET_LENGTH = NS + "bracketLength";
    public static final String DP_BRACKET_POSITION = NS + "bracketPosition";
    public static final String DP_BRACKET_RIGHT_CHORD = NS + "bracketRightChord";
    public static final String DP_ELEVATION_BOTTOM = NS + "elevationBottom";
    public static final String DP_ELEVATION_TOP = NS + "elevationTop";
    public static final String DP_FIRST_ACCESSORY_TO_BOTTOM = NS + "firstAccessoryToBottom";
    public static final String DP_FIRST_LIGHT_HEIGHT = NS + "firstLightHeight";
    public static final String DP_FLANGE_ELEVATION = NS + "flangeElevation";
    public static final String DP_FLANGE_FILLET_RADIUS = NS + "flangeFilletRadius";
    public static final String DP_FLANGE_INNER_DIAMETER = NS + "flangeInnerDiameter";
    public static final String DP_FLANGE_NECK_HEIGHT = NS + "flangeNeckHeight";
    public static final String DP_FLANGE_NECK_THICKNESS = NS + "flangeNeckThickness";
    public static final String DP_FLANGE_OUTER_DIAMETER = NS + "flangeOuterDiameter";
    public static final String DP_FLANGE_THICKNESS = NS + "flangeThickness";
    public static final String DP_HEIGHT = NS + "height";
    public static final String DP_LADDER_HEIGHT = NS + "ladderHeight";
    public static final String DP_LADDER_SUPPORT_LENGTH = NS + "ladderSupportLength";
    public static final String DP_LADDER_SUPPORT_POSITION = NS + "ladderSupportPosition";
    public static final String DP_LAST_BRACKET_TO_PLATFORM = NS + "lastBracketToPlatform";
    public static final String DP_LAST_BRACKET_TO_TOP_FLANGE = NS + "lastBracketToTopFlange";
    public static final String DP_LIGHT_STUD_SPACING = NS + "lightStudSpacing";
    public static final String DP_LIGHT_STUD_TO_WELD_DISTANCE = NS + "lightStudToWeldDistance";
    public static final String DP_LIGHT_TO_LIGHT_MAX_SPACING = NS + "lightToLightMaxSpacing";
    public static final String DP_LIGHT_TO_LIGHT_MIN_SPACING = NS + "lightToLightMinSpacing";
    public static final String DP_LIGHT_TYPE = NS + "lightType";
    public static final String DP_MATERIAL = NS + "material";
    public static final String DP_MODEL_NAME = NS + "modelName";
    public static final String DP_OUTER_DIAMETER_BOTTOM = NS + "outerDiameterBottom";
    public static final String DP_OUTER_DIAMETER_TOP = NS + "outerDiameterTop";
    public static final String DP_PLATFORM_INNER_DIAMETER_TOLERANCE = NS + "platformInnerDiameterTolerance";
    public static final String DP_PLATFORM_LOCATION_INNER_DIAMETER = NS + "platformLocationInnerDiameter";
    public static final String DP_PLATFORM_TO_TOP_DISTANCE = NS + "platformToTopDistance";
    public static final String DP_REGION_NAME = NS + "regionName";
    public static final String DP_SECOND_LAST_TO_PLATFORM = NS + "secondLastToPlatform";
    public static final String DP_SECTION_COUNT = NS + "sectionCount";
    public static final String DP_SUPPORT_POSITION = NS + "supportPosition";
    public static final String DP_SUPPORT_TO_WELD_DISTANCE = NS + "supportToWeldDistance";
    public static final String DP_TRAY_LENGTH = NS + "trayLength";
    public static final String DP_TUBE_SECTION_HEIGHT = NS + "tubeSectionHeight";
    public static final String DP_TUBE_SECTION_INNER_DIAMETER = NS + "tubeSectionInnerDiameter";
    public static final String DP_TUBE_SECTION_NUMBER = NS + "tubeSectionNumber";
    public static final String DP_TUBE_SECTION_WALL_THICKNESS = NS + "tubeSectionWallThickness";
    public static final String DP_WALL_THICKNESS_MAX = NS + "wallThicknessMax";
    public static final String DP_WALL_THICKNESS_MIN = NS + "wallThicknessMin";
    public static final String DP_WELD_SEAM_POSITION = NS + "weldSeamPosition";

    // ==================== 运行时解析模型（零硬编码入口） ====================

    private static volatile TmsdOntologyModel MODEL;

    /** 解析本体并缓存（幂等，可重复调用覆盖）。运行时/测试均应在使用引擎前调用。 */
    public static synchronized void init(Path owlFile) {
        MODEL = TmsdOntologyModel.parse(owlFile);
    }

    /** 是否已初始化。 */
    public static boolean isLoaded() {
        return MODEL != null;
    }

    /** 解析结果模型；未初始化时抛错（避免误用硬编码兜底）。 */
    public static TmsdOntologyModel model() {
        TmsdOntologyModel m = MODEL;
        if (m == null) {
            throw new IllegalStateException(
                    "TmsdVocabulary 尚未初始化：请先调用 TmsdVocabulary.init(<TowerMidSection.owl>)");
        }
        return m;
    }

    /** 本体版本（{@code owl:versionInfo}）。 */
    public static String ontologyVersion() {
        return model().versionInfo();
    }

    /** 17 条可数值校验的约束。 */
    public static List<TmsdOntologyModel.Constraint> numericConstraints() {
        return model().numeric();
    }

    /** 基数约束文本。 */
    public static List<String> cardinalityConstraints() {
        return model().cardinality();
    }

    /** 身份键文本。 */
    public static List<String> hasKeys() {
        return model().hasKeys();
    }

    /** 按属性 local name 取约束。 */
    public static TmsdOntologyModel.Constraint constraint(String propLocal) {
        return model().byProperty(propLocal);
    }

    /** 单值数值约束的数值（{@code hasValue} / 下界）。 */
    public static double num(String propLocal) {
        TmsdOntologyModel.Constraint c = require(propLocal);
        return c.lower();
    }

    /** 约束下界。 */
    public static double lower(String propLocal) {
        return require(propLocal).lower();
    }

    /** 约束上界。 */
    public static double upper(String propLocal) {
        return require(propLocal).upper();
    }

    /** 值集（多条 hasValue，升序），如 {@code accessorySpacingMultiple} → [5,6,7]。 */
    public static List<Double> valueSet(String propLocal) {
        return model().valueSet(propLocal);
    }

    /** 值集（降序），如 {@code accessorySpacingMultiple} → [7,6,5]。 */
    public static List<Double> valueSetDesc(String propLocal) {
        List<Double> v = new ArrayList<>(model().valueSet(propLocal));
        v.sort(java.util.Comparator.reverseOrder());
        return v;
    }

    /** 字符串 hasValue，如 {@code lightType} → "焊接灯"。 */
    public static String stringValue(String propLocal) {
        return model().stringValue(propLocal);
    }

    /** 机型 → [托架长度, 右弦长, 左弦长]；未定义抛错（不得硬编码兜底）。 */
    public static double[] modelParams(String model) {
        double[] p = model().modelParams(model);
        if (p == null) {
            throw new IllegalArgumentException("本体未定义机型：" + model + "（请在 TowerMidSection.owl 补 :Model 个体）");
        }
        return p;
    }

    private static TmsdOntologyModel.Constraint require(String propLocal) {
        TmsdOntologyModel.Constraint c = model().byProperty(propLocal);
        if (c == null) {
            throw new IllegalStateException("本体未定义约束：" + propLocal);
        }
        return c;
    }

    /** 取 local name。 */
    public static String local(String iri) {
        int i = iri.lastIndexOf('#');
        return i < 0 ? iri : iri.substring(i + 1);
    }

    /** 拼个体 IRI。 */
    public static String ind(String localName) {
        return NS + localName;
    }
}
