package com.ocean.ontologyframework.tmsd;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 「下一阶段 3D 模型参数表」输出器（完全正向）。
 *
 * <p>每个塔段输出 <b>3 份 Creo 关系式 txt</b>（GBK），字段与注释逐行对齐老工具 towerdesign：
 * <ol>
 *   <li>筒体信息关系式 —— {@code drawing/infoW.py#towerInfoW}（L76–197）；</li>
 *   <li>附件信息关系式 —— {@code drawing/infoW.py#midSkelW}（L417–484）；</li>
 *   <li>法兰信息关系式 —— {@code drawing/infoW.py#flWrite}（L375–387）。</li>
 * </ol>
 * 另输出 {@code 方案对比报告.md}（UTF-8）：推荐方案（驱动 txt）+ 备选方案的差异与优劣。
 *
 * <p>注意：完全正向流程在若干处按本体/设计流程口径修正了老应用（如 B_B_A/B_T_A 由规则计算、
 * H{i}_L/H{i}_CABLE 为增量语义、附件边缘避焊缝），差异在各行注释中标注。
 */
public final class TmsdOutputWriter {

    /** 老工具使用的输出编码。 */
    public static final Charset CREO_CHARSET = Charset.forName("GBK");

    private TmsdOutputWriter() {
    }

    /**
     * 写出一个用例的全部设计结果：对<b>推荐方案</b>逐段写 3 份 txt，另写方案对比报告。
     *
     * @param result    求解结果
     * @param outputDir 输出目录
     * @return 写出的文件列表（txt 在前，报告在后）
     */
    public static List<Path> write(TmsdDesignPipeline.CaseResult result, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        List<Path> written = new ArrayList<>();
        String caseTag = safe(result.caseName());

        TmsdDesignPipeline.VariantResult rec = result.recommended();
        if (rec != null) {
            for (Map.Entry<Integer, TowerDesignEngine.SectionDesign> e : rec.sections().entrySet()) {
                int no = e.getKey();
                TowerDesignEngine.SectionDesign sd = e.getValue();
                written.add(writeFile(outputDir,
                        String.format("%s-第%d段-筒体信息关系式.txt", caseTag, no),
                        renderTowerInfo(result.request(), sd)));
                written.add(writeFile(outputDir,
                        String.format("%s-第%d段-附件信息关系式.txt", caseTag, no),
                        renderAttachment(result.request(), sd)));
                written.add(writeFile(outputDir,
                        String.format("%s-第%d段-连接法兰关系式.txt", caseTag, no),
                        renderFlange(result.request(), sd)));
            }
        }

        Path report = outputDir.resolve(caseTag + "-方案对比报告.md");
        Files.writeString(report, renderReport(result), Charset.forName("UTF-8"));
        written.add(report);
        return written;
    }

    private static Path writeFile(Path dir, String name, String content) throws IOException {
        Path p = dir.resolve(name);
        Files.writeString(p, content, CREO_CHARSET);
        return p;
    }

    // ============================================================
    // 1) 筒体信息关系式（复刻 towerInfoW）
    // ============================================================

    /** 渲染一段的筒体信息关系式（格式对齐 {@code infoW.towerInfoW}）。 */
    public static String renderTowerInfo(TowerDesignRequest req, TowerDesignEngine.SectionDesign sd) {
        StringBuilder b = new StringBuilder();
        int n = sd.sectionNo() - 1;
        TowerGeometry.SectionCourses sc = req.geometry().sectionCourses(n);
        List<Double> t = sc.wallThicknesses();
        List<Double> h = sc.heights();
        List<Double> db = sc.outerDiametersBottom();
        List<Double> dt = sc.outerDiametersTop();

        b.append("/*=====================| 塔架中段筒体信息 |=====================*/\n");
        b.append("/* 用例：").append(req.caseName()).append("  第").append(sd.sectionNo()).append("段\n");
        b.append("   本体：TowerMidSection.owl ").append(TmsdVocabulary.ontologyVersion())
                .append("（约束已逐条校验通过）\n*/\n");
        b.append("DELTA=0.02/*缝隙高度\n");
        b.append("SEC_H_TOTAL=").append(num(sd.sectionTotalHeight())).append("/*筒段总高\n");
        b.append("DA_TOP=").append(num(sd.upperFlangeOuterDiameter())).append("/*上法兰外径\n");
        b.append("TFL_TOP=").append(num(sd.upperFlangeThickness())).append("/*上法兰厚\n");
        b.append("TFL_BOTTOM=").append(num(sd.lowerFlangeThickness())).append("/*下法兰厚\n");
        b.append("H_BOTTOM=").append(num(sd.lowerFlangeNeckThickness())).append("/*下法兰脖子高度\n");
        b.append("H_TOP=").append(num(sd.upperFlangeNeckThickness())).append("/*上法兰脖子高度\n\n");

        b.append("/*********主体参数************\n");
        for (int k = 1; k <= 20; k++) {
            b.append("cy").append(k).append("_t=").append(k <= t.size() ? num(t.get(k - 1)) : "0")
                    .append("/*筒节").append(k).append("壁厚\n");
        }
        for (int k = 1; k <= 20; k++) {
            b.append("cy").append(k).append("_h=").append(k <= h.size() ? num(h.get(k - 1)) : "1")
                    .append("/*筒节").append(k).append("节高\n");
        }
        for (int k = 1; k <= 20; k++) {
            b.append("cy").append(k).append("_D_bottom=").append(k <= db.size() ? num(db.get(k - 1)) : "4300")
                    .append("/*筒节").append(k).append("下端直径\n");
        }
        double lastTop = dt.isEmpty() ? 4300 : dt.get(dt.size() - 1);
        for (int k = 1; k <= 20; k++) {
            b.append("cy").append(k).append("_D_top=").append(k <= dt.size() ? num(dt.get(k - 1)) : num(lastTop))
                    .append("/*筒节").append(k).append("上端直径\n");
        }

        // 中段：爬梯支撑 / 电缆线夹存在性（H1..H20）
        Map<String, Object> p = sd.parameters();
        for (int i = 1; i <= 20; i++) {
            b.append("H").append(i).append("_L_Exist=").append(p.get("H" + i + "_L_Exist"))
                    .append("/*爬梯支撑存在\n");
        }
        for (int i = 1; i <= 20; i++) {
            b.append("H").append(i).append("_CABLE_Exist=").append(p.get("H" + i + "_CABLE_Exist"))
                    .append("/*电缆线夹存在\n");
        }
        b.append("\n/*筒体驱动尺寸（由上方值派生）*/\n");
        b.append("drive_size=").append(num(sd.sectionTotalHeight())).append("/*驱动尺寸\n");
        return b.toString();
    }

    // ============================================================
    // 2) 附件信息关系式（复刻 midSkelW）
    // ============================================================

    /** 渲染一段的附件信息关系式（格式对齐 {@code infoW.midSkelW}）。 */
    public static String renderAttachment(TowerDesignRequest req, TowerDesignEngine.SectionDesign sd) {
        StringBuilder b = new StringBuilder();
        int n = sd.sectionNo() - 1;
        Map<String, Object> p = sd.parameters();
        List<Double> inc = sd.accessoryIncrements();
        List<Double> cable = sd.cableClampIncrements();

        b.append("/*=====================| 塔架中段附件信息 |=====================*/\n");
        b.append("/* 用例：").append(req.caseName()).append("  第").append(sd.sectionNo()).append("段\n");
        b.append("   本体：TowerMidSection.owl ").append(TmsdVocabulary.ontologyVersion())
                .append("（约束已逐条校验通过）\n*/\n");
        b.append("/*---------------------| 筒段 |------------------------------*/\n");
        b.append("SEC_H_total=").append(num(p.get("SEC_H_total"))).append("/*筒段总高\n\n");

        b.append("/*---------------------| 上法兰 |------------------------------*/\n");
        b.append("DA_TOP=").append(num(p.get("DA_TOP"))).append("/*上法兰外径\n");
        b.append("DI_TOP=").append(num(p.get("DI_TOP"))).append("/*上法兰内径\n");
        b.append("TFL_TOP=").append(num(p.get("TFL_TOP"))).append("/*上法兰厚\n");
        b.append("S_TOP=").append(num(p.get("S_TOP"))).append("/*上法兰颈厚\n\n");

        b.append("/*---------------------| 下法兰 |------------------------------*/\n");
        b.append("DA_BOTTOM=").append(num(p.get("DA_BOTTOM"))).append("/*下法兰外径\n");
        b.append("DI_BOTTOM=").append(num(p.get("DI_BOTTOM"))).append("/*下法兰内径\n");
        b.append("TFL_BOTTOM=").append(num(p.get("TFL_BOTTOM"))).append("/*下法兰厚\n");
        b.append("S_BOTTOM=").append(num(p.get("S_BOTTOM"))).append("/*下法兰颈厚\n\n");

        b.append("/*---------------------| 平台 |------------------------------*/\n");
        b.append("H_platform=").append(num(p.get("H_platform"))).append("/*平台距离顶法兰距离\n\n");

        b.append("/*---------------------| 爬梯 |------------------------------*/\n");
        b.append("$H_LADDER_TOP= 0 /*爬梯位置\n");
        b.append("L_LADDER=").append(num(p.get("L_LADDER"))).append("/*爬梯长度\n");
        b.append("L_LADDER_I =").append(num(p.get("L_LADDER_I"))).append("/*爬梯支撑长度\n");
        b.append("W_LADDER_I =").append(num(p.get("W_LADDER_I"))).append("/*爬梯支撑宽度\n");
        b.append("Alpha = atan((DA_BOTTOM - DA_TOP) / 2 / SEC_H_TOTAL)\n");
        b.append("H_L_LADDER = L_LADDER * cos(Alpha)\n");
        b.append("b = H_LIGHT2FL + 300 /*B截面高度\n");
        b.append("Ladder_up_circle = DA_TOP - 2 * S_TOP\n");
        b.append("Ladder_bottom_circle = DA_BOTTOM - 2 * S_BOTTOM\n\n");

        b.append("/*---------------------| 电缆线槽 |------------------------------*/\n");
        b.append("L_C=").append(num(p.get("L_C"))).append("/*电缆线槽长度\n\n");

        b.append("/*---------------------| 扶持 |------------------------------*/\n");
        b.append("H_SUPPORT=").append(num(p.get("H_SUPPORT"))).append("/*扶持高度（直读布局表 (12,n)）\n\n");

        b.append("/*---------------------| 中间段爬梯支撑 |------------------------------*/\n");
        for (int i = 0; i < inc.size(); i++) {
            b.append("H").append(i + 1).append("_L= ").append(num(inc.get(i)))
                    .append(" /*第").append(i + 1).append("组爬梯支撑安装高度(距离上一组安装高度)\n");
        }
        for (int i = 1; i <= 20; i++) {
            b.append("H").append(i).append("_L_Exist = ").append(p.get("H" + i + "_L_Exist"))
                    .append(" /*存在\n");
        }
        b.append("accessoryCenterSpacing=").append(num(sd.accessorySpacing()))
                .append("/*相邻附件中心间距（本体 1400~1960）\n");
        b.append("firstAccessoryToBottom=").append(num(sd.firstAccessoryToBottom()))
                .append("/*第一个附件到底部（本体 hasValue 980）\n");
        b.append("secondLastToPlatform=").append(num(sd.secondLastToPlatform()))
                .append("/*倒数第二个配件到平台（本体 840~1960，末组落平台带下沿档位）\n");
        b.append("accessoryToWeldDistance=").append(num(sd.minAccessoryToWeldDistance()))
                .append("/*附件上/下边缘与环焊缝最小距离（本体 > 100）\n\n");

        b.append("/*---------------------| 电缆线夹 |------------------------------*/\n");
        for (int i = 0; i < cable.size(); i++) {
            b.append("H").append(i + 1).append("_CABLE= ").append(num(cable.get(i)))
                    .append(" /*第").append(i + 1).append("组电缆线夹安装高度(距离上一组安装高度)\n");
        }
        for (int i = 1; i <= 20; i++) {
            b.append("H").append(i).append("_CABLE_Exist = ").append(p.get("H" + i + "_CABLE_Exist"))
                    .append(" /*存在\n");
        }
        b.append("Cable_top_h = ").append(num(TmsdVocabulary.num("lastBracketToTopFlange")))
                .append("/*最后一组电缆夹板相对于顶法兰上端面（本体 hasValue 1000）\n");
        b.append("lastBracketToPlatform=").append(num(sd.lastBracketToPlatform()))
                .append("/*最后一个电缆托架到平台（平台上方，本体 hasValue 200）\n");
        b.append("L_CABLE=").append(num(p.get("L_CABLE"))).append("/*电缆托架长度\n");
        b.append("L1_CABLE_I=").append(num(p.get("L1_CABLE_I"))).append("/*右侧电缆托架安装弦长\n");
        b.append("L2_CABLE_I=").append(num(p.get("L2_CABLE_I"))).append("/*左侧电缆托架安装弦长\n");
        b.append("di_cable_top=").append(num(p.get("di_cable_top")))
                .append("/*平台上方电缆夹板位置处塔筒内径\n");
        b.append("di_cable_down=").append(num(p.get("di_cable_down")))
                .append("/*下方第一个电缆夹板位置处塔筒内径\n\n");

        b.append("/*---------------------| 照明灯 |------------------------------*/\n");
        List<Double> lights = sd.lightHeights();
        double topLightAbs = lights.isEmpty() ? sd.sectionTotalHeight() : lights.get(lights.size() - 1);
        double hLight2Fl = lights.isEmpty() ? TmsdVocabulary.lower("firstLightHeight") : lights.get(0);
        double hLight2Platform = round1(sd.sectionTotalHeight() - topLightAbs);
        b.append("H_LIGHT2FL=").append(num(hLight2Fl)).append("/*下灯位置\n");
        b.append("LIGHT_BOTTOM_circle=").append(num(req.geometry().innerDiameterAt(n, hLight2Fl)))
                .append("/*下灯位置处塔筒内径\n");
        b.append("H_LIGHT2PLATFORM=").append(num(hLight2Platform)).append("/*上灯位置（距平台）\n");
        b.append("LIGHT_TOP_circle=")
                .append(num(req.geometry().innerDiameterAt(n, sd.sectionTotalHeight() - hLight2Platform)))
                .append("/*上灯位置处塔筒内径\n");
        b.append("LIGHT_TYPE=").append(sd.lightType()).append("/*灯类型\n");
        b.append("firstLightHeight=").append(num(sd.firstLightHeight()))
                .append("/*第一个灯安装高度（本体 2600~3000）\n");
        if (lights.size() >= 2) {
            b.append("lightToLightMinSpacing=").append(num(TmsdDesignPipeline.minSpacing(lights)))
                    .append("/*灯与灯最小间距（本体 >= 5000）\n");
            b.append("lightToLightMaxSpacing=").append(num(TmsdDesignPipeline.maxSpacing(lights)))
                    .append("/*灯与灯最大间距（本体 <= 10000）\n");
        }
        if (TmsdVocabulary.C_WELDED_LIGHT.equals(TowerDesignEngine.lightClassIri(sd.lightType()))) {
            b.append("lightStudSpacing=").append(num(sd.lightStudSpacing()))
                    .append("/*灯螺柱间距（本体 hasValue 500）\n");
            b.append("lightStudToWeldDistance=").append(num(sd.minLightStudToWeldDistance()))
                    .append("/*灯螺柱与环焊缝最小距离（本体 > 100）\n");
        }
        b.append('\n');

        b.append("/*---------------------| 爬梯安全锚点 |------------------------------*/\n");
        b.append("H_AP=").append(num(req.layoutReference().middleSection(sd.sectionNo()) != null
                ? req.layoutReference().middleSection(sd.sectionNo()).safetyAnchorHeight() : 0))
                .append("/*爬梯安全锚点安装高度（布局表 (9,n)）\n\n");

        b.append("/*---------------------| B和C 二维视图所需信息 |------------------------------*/\n");
        b.append("DA_B=").append(num(req.geometry().innerDiameterAt(n, sd.sectionTotalHeight() - 825)))
                .append("/*B_B视图截面所在外径（命名沿用老应用）\n");
        double hSupport = sd.supportHeight();
        b.append("DA_C=").append(num(req.geometry().innerDiameterAt(n, hSupport + 400)))
                .append("/*C_C视图截面所在外径（命名沿用老应用）\n");

        b.append("/*---------------------| 防雷螺柱定位 |------------------------------*/\n");
        b.append("B_B_A=").append(num((Double) p.get("B_B_A")))
                .append("/*下端防雷螺柱安装角度（距爬梯中心线顺时针，其余 120° 均布）\n");
        b.append("B_T_A=").append(num((Double) p.get("B_T_A")))
                .append("/*上端防雷螺柱安装角度（距爬梯中心线顺时针，其余 120° 均布）\n");
        b.append("LIGHTNING_STUD_FLANGE_DISTANCE=").append(num((Double) p.get("LIGHTNING_STUD_FLANGE_DISTANCE")))
                .append("/*防雷螺柱距法兰面距离（本体 hasValue 50）\n\n");

        b.append("/*---------------------| 梯子与固定尾段 |------------------------------*/\n");
        b.append("RUNG_SPACING=").append(num(sd.rungSpacing())).append("/*梯档间距（本体 hasValue 280）\n");
        b.append("FIRST_RUNG_TO_BOTTOM=").append(num(sd.firstRungToBottom()))
                .append("/*第一个踏棍到下法兰距离（本体 hasValue 140）\n");
        b.append("Bush_a = DA_TOP - 2 * S_TOP\n");
        b.append("DI_BOTTOM_BUSH = DI_BOTTOM  /*下法兰内径\n");
        b.append("DI_TOP_BUSH = DI_TOP  /*上法兰内径\n");
        b.append("bush_bottom_h = TFL_BOTTOM\n");
        b.append("bush_top_h = TFL_TOP\n");
        b.append("$H1_S = 0\n");
        b.append("cable_up_circle = DA_TOP - 2 * S_TOP\n");
        b.append("$rest1 = 0\n\n");

        b.append("/*---------------------| 附件连接方式 |------------------------------*/\n");
        b.append("ACCESSORY_CONNECTION_TYPE=").append(sd.connectionType()).append("/*附件连接方式\n");
        b.append("ACCESSORY_TYPE=").append(sd.accessoryType()).append("/*配件类型\n");
        return b.toString();
    }

    // ============================================================
    // 3) 法兰信息关系式（复刻 flWrite）
    // ============================================================

    /** 渲染一段的法兰信息关系式（格式对齐 {@code infoW.flWrite}；本段输出上法兰）。 */
    public static String renderFlange(TowerDesignRequest req, TowerDesignEngine.SectionDesign sd) {
        StringBuilder b = new StringBuilder();
        int n = sd.sectionNo() - 1;
        TowerGeometry.Flange upper = req.geometry().flanges().get(n + 1);

        b.append("/*=====================| 连接法兰信息 |=====================*/\n");
        b.append("/* 用例：").append(req.caseName()).append("  第").append(sd.sectionNo()).append("段上法兰\n");
        b.append("   本体：TowerMidSection.owl ").append(TmsdVocabulary.ontologyVersion()).append("*/\n");
        b.append("/*****连接法兰").append(sd.sectionNo()).append("参数****\n");
        b.append("DA=").append(num(upper.outerDiameter())).append("/*法兰外径\n");
        b.append("DI=").append(num(upper.innerDiameter())).append("/*法兰内径\n");
        b.append("DM=").append(num(upper.boltCircleDiameter())).append("/*螺栓分度圆直径\n");
        b.append("TFL=").append(num(upper.thickness())).append("/*法兰厚度\n");
        b.append("S=").append(num(upper.neckThickness())).append("/*法兰颈厚\n");
        b.append("H_TOTAL=").append(num(upper.neckHeight())).append("/*法兰高\n");
        b.append("DHOLE=").append(num(upper.boltHoleDiameter())).append("/*螺栓孔直径\n");
        b.append("N=").append(upper.boltCount()).append("/*螺栓数\n");
        return b.toString();
    }

    // ============================================================
    // 方案对比报告
    // ============================================================

    /** 渲染方案对比报告（推荐 + 备选差异 + 被否决）。 */
    public static String renderReport(TmsdDesignPipeline.CaseResult result) {
        StringBuilder b = new StringBuilder();
        b.append("# 塔架中段设计——方案对比报告\n\n");
        b.append("- **用例**：").append(result.caseName()).append('\n');
        b.append("- **设计输入**：").append(result.request()).append('\n');
        b.append("- **本体**：TowerMidSection.owl ").append(TmsdVocabulary.ontologyVersion())
                .append("（16 条可数值校验 Restriction 逐条判定）\n");
        b.append("- **范式**：完全正向（不依赖历史设计匹配；S0→S8 全部必做）\n\n");

        TmsdDesignPipeline.VariantResult rec = result.recommended();
        b.append("## 一、推荐方案\n\n");
        if (rec == null) {
            b.append("> 无方案满足全部本体约束。\n\n");
        } else {
            b.append("**").append(rec.variantName()).append("** —— ").append(rec.note()).append("\n\n");
            b.append("推荐依据：满足全部本体约束的前提下附件数量最少（单件受力最大、材料最省）；")
                    .append("并列时取间距最大者。\n\n");
        }

        b.append("## 二、满足本体约束的设计方案（").append(result.satisfied().size()).append(" 个）\n\n");
        if (result.satisfied().isEmpty()) {
            b.append("> 无方案满足全部本体约束。\n\n");
        } else {
            b.append("| 方案 | 附件模式 | 中段附件总数 | 各段间距 | 各段扶持高度 | 各段灯数 |\n");
            b.append("|---|---|---|---|---|---|\n");
            for (TmsdDesignPipeline.VariantResult v : result.satisfied()) {
                b.append("| ").append(v.variantName())
                        .append(" | ").append(v.modeLabel())
                        .append(" | ").append(totalAccessories(v))
                        .append(" | ").append(perSection(v, sd -> num(sd.accessorySpacing())))
                        .append(" | ").append(perSection(v, sd -> num(sd.supportHeight())))
                        .append(" | ").append(perSection(v, sd -> String.valueOf(sd.lightHeights().size())))
                        .append(" |\n");
            }
            b.append('\n');

            b.append("### 各方案差异与优劣\n\n");
            for (TmsdDesignPipeline.VariantResult v : result.satisfied()) {
                b.append("#### ").append(v.variantName()).append("\n\n");
                b.append("- 附件模式：").append(v.modeLabel()).append('\n');
                b.append("- 中段附件总数：").append(totalAccessories(v)).append('\n');
                b.append("- 各段附件数：").append(perSection(v, sd -> String.valueOf(sd.accessoryCount())))
                        .append('\n');
                b.append("- 各段附件间距：").append(perSection(v, sd -> num(sd.accessorySpacing()))).append(" mm\n");
                b.append("- 各段扶持高度：").append(perSection(v, sd -> num(sd.supportHeight()))).append(" mm\n");
                b.append("- 优劣：").append(prosCons(v)).append("\n\n");
            }
        }

        b.append("## 三、被本体约束否决的设计方案（").append(result.rejected().size()).append(" 个）\n\n");
        if (result.rejected().isEmpty()) {
            b.append("> 无。\n\n");
        } else {
            for (TmsdDesignPipeline.VariantResult v : result.rejected()) {
                b.append("### ").append(v.variantName()).append(" —— 否决\n\n");
                for (String f : v.failures()) {
                    b.append("- ").append(f).append('\n');
                }
                b.append('\n');
            }
        }
        return b.toString();
    }

    private static String prosCons(TmsdDesignPipeline.VariantResult v) {
        return switch (v.variant().accessoryMode()) {
            case PREFER_MAX -> "附件间距贪心取大（每步优先 1960mm，避焊缝才降 1680/1400）："
                    + "附件数量最少、安装工作量最小、材料最省；"
                    + "单件承受的爬梯/电缆载荷最大，对单件强度与焊缝承载要求最高。";
        };
    }

    private static int totalAccessories(TmsdDesignPipeline.VariantResult v) {
        int n = 0;
        for (TowerDesignEngine.SectionDesign sd : v.sections().values()) {
            n += sd.accessoryCount();
        }
        return n;
    }

    private static String perSection(TmsdDesignPipeline.VariantResult v,
                                     java.util.function.Function<TowerDesignEngine.SectionDesign, String> f) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, TowerDesignEngine.SectionDesign> e : v.sections().entrySet()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append("第").append(e.getKey()).append("段=").append(f.apply(e.getValue()));
        }
        return sb.toString();
    }

    private static String num(Object v) {
        if (v == null) {
            return "0";
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.rint(d)) {
                return String.valueOf((long) d);
            }
            return trim(d);
        }
        return v.toString();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String trim(double d) {
        String s = String.format("%.4f", d);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String safe(String s) {
        return s == null ? "case" : s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
