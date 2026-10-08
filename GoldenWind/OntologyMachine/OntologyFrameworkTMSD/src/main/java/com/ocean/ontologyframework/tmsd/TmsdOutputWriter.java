package com.ocean.ontologyframework.tmsd;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 「下一阶段 3D 模型参数表」输出器（完全正向）。
 *
 * <p>按老工具 towerdesign 的目录结构输出<b>两套</b>骨架关系式：
 * {@code 项目法兰尺寸_骨架关系式/}（项目法兰）与 {@code 极限法兰尺寸_骨架关系式/}（极限法兰厚 215）。
 * 每套含 {@code 第1段 … 第N-1段}、{@code 顶段}、{@code 连接法兰} 子目录，逐行对齐
 * {@code drawing/drawingmain.py#generate_creo_parameters}。
 *
 * <p><b>零硬编码</b>：本类<b>不再保存任何变量名/尾注释/关系式文本</b>。每一行的「变量名 + 注释 +
 * 空格标点」以及老工具 {@code infoformat.py} 的原样块，全部由本体
 * （{@code TowerMidSection.owl} 的 {@code :输出行模板}/{@code :输出文本块}）承载；本类只保留
 * 「语义键 → 值」的计算，渲染时调用 {@link #fill} / {@link #outBlock} 从本体取模板并注入
 * {@code {{value}}}（索引行另注入 {@code {k}}/{@code {i}}）。所有值仍是运行时按输入表/本体算出的
 * 几何/搜索/数值结果。
 *
 * <p>另输出 {@code 方案对比报告.md}（UTF-8）。
 *
 * <p><b>极限法兰模式</b>：中间法兰（首/末法兰除外）厚度取本体 {@code limitFlangeThickness}、
 * 内径取 {@code DA - S*2 - limitFlangeInnerReduction}，并按厚度增量扣减该段首/末筒节高度；
 * 底法兰与顶法兰不变（复刻 {@code Creo_para.get_tfl/get_di/get_top_delta/get_bottom_delta}）。
 *
 * <p><b>注释格式铁律</b>：注释一律「行首 斜杠星号 起始、单行、不补 星号斜杠 闭合」。
 */
public final class TmsdOutputWriter {

    /** 老工具使用的输出编码。 */
    public static final Charset CREO_CHARSET = Charset.forName("GBK");

    /** 项目法兰尺寸套目录名（老工具 {@code drawingmain.py}）。 */
    public static final String DIR_PROJECT_FLANGE = "项目法兰尺寸_骨架关系式";

    /** 极限法兰尺寸套目录名（老工具 {@code drawingmain.py}）。 */
    public static final String DIR_LIMIT_FLANGE = "极限法兰尺寸_骨架关系式";

    private TmsdOutputWriter() {
    }

    // ============================================================
    // 本体渲染入口（零硬编码：模板来自本体，值由本类计算）
    // ============================================================

    /**
     * 取本体中 {@code key} 的行模板并注入占位符。
     *
     * <p>占位符约定：{@code {{name}}} 与 {@code {name}} 均被替换为对应 {@code kv} 中 {@code name}
     * 的值（前者用于 {@code value}/{@code note} 等整段注入，后者用于 {@code k}/{@code i} 索引）。
     * 模板自带换行，渲染结果可直接追加。
     *
     * @param key 行模板个体 local name
     * @param kv  交替的「占位符名, 值」序列
     */
    private static String fill(String key, Object... kv) {
        String tpl = TmsdVocabulary.outputTemplate(key);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String name = String.valueOf(kv[i]);
            String val = kv[i + 1] == null ? "" : String.valueOf(kv[i + 1]);
            tpl = tpl.replace("{{" + name + "}}", val).replace("{" + name + "}", val);
        }
        return tpl;
    }

    /** 取本体中的整块原样文本（老工具 {@code infoformat.py} 的照抄块）。 */
    private static String outBlock(String key) {
        return TmsdVocabulary.outputBlock(key);
    }

    /** 老工具 towerInfoW 的 cy 列表只列「真实筒节」：剔除首项（下法兰所占筒节）与末项（上法兰所占筒节）。 */
    private static List<Double> realCourses(List<Double> all) {
        if (all.size() <= 2) {
            return List.of();
        }
        return all.subList(1, all.size() - 1);
    }

    /**
     * 写出一个用例的全部设计结果（项目 + 极限两套骨架关系式），另写方案对比报告。
     *
     * @param result    求解结果
     * @param outputDir 输出根目录（该用例的目录，其下建两套子目录）
     * @return 写出的文件列表
     */
    public static List<Path> write(TmsdDesignPipeline.CaseResult result, Path outputDir) throws IOException {
        String caseTag = safe(result.caseName());
        Path caseRoot = outputDir.resolve(caseTag);
        Files.createDirectories(caseRoot);
        List<Path> written = new ArrayList<>();

        TmsdDesignPipeline.VariantResult rec = result.recommended();
        if (rec != null) {
            TowerDesignRequest req = result.request();
            TowerGeometry geo = req.geometry();
            int sectionQty = geo.sectionCount();
            int vFlangeQty = geo.vFlangeQty();

            for (boolean limit : new boolean[]{false, true}) {
                Path modeRoot = caseRoot.resolve(limit ? DIR_LIMIT_FLANGE : DIR_PROJECT_FLANGE);
                Path flangeDir = modeRoot.resolve("连接法兰");
                Files.createDirectories(flangeDir);

                // 底法兰（第0段下法兰；T 型走 tflWrite，否则 flWrite）
                String bottomName = (0 < vFlangeQty) ? "底法兰关系式_分片法兰.txt" : "底法兰关系式.txt";
                String bottomBody = renderBottomFlange(req, limit);
                if (0 < vFlangeQty) {
                    bottomBody += outBlock("vflangeInfo");
                }
                written.add(writeFile(flangeDir, bottomName, bottomBody));

                for (int i = 0; i < sectionQty; i++) {
                    if (i == 0) {
                        Path d = modeRoot.resolve("第1段");
                        Files.createDirectories(d);
                        written.add(writeSectionGeometry(d, "第1段", i, vFlangeQty, req, null, limit));
                    } else if (i == sectionQty - 1) {
                        Path d = modeRoot.resolve("顶段");
                        Files.createDirectories(d);
                        String name = (i < vFlangeQty) ? "顶段筒体信息关系式_分片段.txt" : "顶段筒体信息关系式.txt";
                        written.add(writeFile(d, name, renderTowerInfo(req, i, null, limit)));
                        writeFlangeFiles(flangeDir, i, vFlangeQty, req, limit, written);
                    } else {
                        Path d = modeRoot.resolve("第" + (i + 1) + "段");
                        Files.createDirectories(d);
                        TowerDesignEngine.SectionDesign sd = rec.section(i + 1);
                        written.add(writeSectionGeometry(d, "第" + (i + 1) + "段", i, vFlangeQty, req,
                                sd == null ? null : sd.parameters(), limit));
                        written.add(writeFile(d, "第" + (i + 1) + "段附件信息关系式.txt",
                                renderAttachment(req, sd, limit)));
                        writeFlangeFiles(flangeDir, i, vFlangeQty, req, limit, written);
                    }
                }
            }
        }

        Path report = caseRoot.resolve(caseTag + "-方案对比报告.md");
        Files.writeString(report, renderReport(result), Charset.forName("UTF-8"));
        written.add(report);
        return written;
    }

    /** 写某一段的筒体信息关系式（分片段则追加 {@code vflangetowerinfo}）。 */
    private static Path writeSectionGeometry(Path dir, String tag, int i, int vFlangeQty,
                                             TowerDesignRequest req, Map<String, Object> params,
                                             boolean limit) throws IOException {
        boolean vf = i < vFlangeQty;
        String name = tag + "筒体信息关系式" + (vf ? "_分片段" : "") + ".txt";
        String body = renderTowerInfo(req, i, params, limit);
        if (vf) {
            body += renderVFlangeTowerInfo(req, i);
        }
        return writeFile(dir, name, body);
    }

    /** 写 {@code 连接法兰{i}}（i<vFlangeQty 或 i==vFlangeQty 时另出分片法兰版）。 */
    private static void writeFlangeFiles(Path flangeDir, int i, int vFlangeQty, TowerDesignRequest req,
                                         boolean limit, List<Path> out) throws IOException {
        if (i < vFlangeQty) {
            out.add(writeFile(flangeDir, "连接法兰" + i + "关系式_分片法兰.txt",
                    renderConnectionFlange(req, i, limit) + outBlock("vflangeInfo")));
        } else {
            out.add(writeFile(flangeDir, "连接法兰" + i + "关系式.txt",
                    renderConnectionFlange(req, i, limit)));
        }
        if (i == vFlangeQty) {
            out.add(writeFile(flangeDir, "连接法兰" + i + "关系式_分片法兰.txt",
                    renderConnectionFlange(req, i, limit) + outBlock("vflangeInfo")));
        }
    }

    private static Path writeFile(Path dir, String name, String content) throws IOException {
        // 写出前按注释格式铁律校验：产物 txt 中每条注释必须以 "/*" 开头（从生成端杜绝违规）
        assertCommentPolicy(name, content);
        Path p = dir.resolve(name);
        // 老工具在 Windows 文本模式下写出 CRLF，逐字节对齐
        Files.writeString(p, content.replace("\n", "\r\n"), CREO_CHARSET);
        return p;
    }

    /**
     * 注释格式铁律校验：凡注释一律「行首以 斜杠星号 起始、单行、不以 星号斜杠 闭合」。
     * 违规即抛 {@link IllegalStateException}，使生成失败而不产出错误注释。
     *
     * @param name    输出文件名（报错定位用）
     * @param content 待写出的文本
     */
    private static void assertCommentPolicy(String name, String content) {
        int no = 0;
        for (String raw : content.split("\n", -1)) {
            no++;
            String t = raw.strip();
            if (t.isEmpty()) {
                continue;
            }
            // 禁止独立成行的 "*/" 闭合，或「非 "/*" 起始却以 "*/" 结尾」的行
            if (t.startsWith("*/") || (t.endsWith("*/") && !t.startsWith("/*"))) {
                throw new IllegalStateException("输出注释格式违规（禁止以 星号斜杠 闭合）：" + name + " 第" + no + "行: " + t);
            }
            // 以 "/*" 起始者为合法注释
            if (t.startsWith("/*")) {
                continue;
            }
            // 其余行：凡含中文且不是「变量=值」者，必为注释 ⇒ 必须以 "/*" 开头
            if (containsCjk(t) && !t.contains("=")) {
                throw new IllegalStateException("输出注释格式违规（注释必须以 斜杠星号 开头）：" + name + " 第" + no + "行: " + t);
            }
        }
    }

    /** 是否含汉字（非业务判定，仅用于注释格式校验）。 */
    private static boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.UnicodeScript.of(s.charAt(i)) == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // 1) 筒体信息关系式（复刻 towerInfoW：第1段 / 中间段 / 顶段）
    // ============================================================

    /** 渲染一段的筒体信息关系式（格式对齐 {@code infoW.towerInfoW}）。 */
    public static String renderTowerInfo(TowerDesignRequest req, TowerDesignEngine.SectionDesign sd) {
        return renderTowerInfo(req, sd.sectionNo() - 1, sd.parameters(), false);
    }

    /**
     * 渲染第 {@code n} 段（0-based）的筒体信息关系式。
     *
     * @param params 中间段的 {@code H{i}_*_Exist} 参数（第1段/顶段传 {@code null}，不写存在性行）
     * @param limit  极限法兰模式
     */
    public static String renderTowerInfo(TowerDesignRequest req, int n, Map<String, Object> params, boolean limit) {
        TowerGeometry geo = req.geometry();
        int sectionQty = geo.sectionCount();
        boolean top = (n == sectionQty - 1);
        TowerGeometry.SectionCourses sc = geo.sectionCourses(n);
        List<Double> t = realCourses(sc.wallThicknesses());
        List<Double> h = realCourses(sc.heights());
        List<Double> db = realCourses(sc.outerDiametersBottom());
        List<Double> dt = realCourses(sc.outerDiametersTop());
        TowerGeometry.Flange lower = geo.flanges().get(n);
        TowerGeometry.Flange upper = geo.flanges().get(n + 1);

        StringBuilder b = new StringBuilder();
        b.append(outBlock("weightInfo"));
        b.append(fill("towerDelta", "value", "0.02"));
        b.append(fill("towerSecH", "value", py(sc.totalHeight(), 0)));
        b.append(fill("towerDaTop", "value", py(upper.outerDiameter(), 0)));
        b.append(fill("towerTflTop", "value", tflStr(geo, upper, n + 1, limit)));
        b.append(fill("towerTflBottom", "value", tflStr(geo, lower, n, limit)));
        b.append(fill("towerHBottom",
                "value", py(lower.thickness() + lower.neckHeight() - effTfl(geo, lower, n, limit), 1)));
        if (top) {
            b.append(fill("towerHTop", "value", py(topSectionNeckHeight(geo), 1)));
        } else {
            b.append(fill("towerHTop", "value",
                    py(upper.thickness() + upper.neckHeight() - effTfl(geo, upper, n + 1, limit), 0)));
        }

        if (n == 0) {
            b.append(renderDoorBlock(geo.door()));
        }

        b.append(fill("towerBodyTitle"));
        for (int k = 1; k <= 20; k++) {
            b.append(fill("towerCyT", "k", k,
                    "value", k <= t.size() ? py(t.get(k - 1), 1) : "0"));
        }

        double bottomDelta = bottomDelta(geo, n, limit);
        double topDelta = topDelta(geo, n, limit);
        int realCount = h.size();
        for (int k = 1; k <= 20; k++) {
            String v;
            if (k <= realCount) {
                double hh = h.get(k - 1);
                if (k == 1 && bottomDelta > 0) {
                    hh -= bottomDelta;
                } else if (k == realCount && topDelta > 0) {
                    hh -= topDelta;
                }
                v = py(hh, 0);
            } else {
                v = "1";
            }
            b.append(fill("towerCyH", "k", k, "value", v));
        }

        // 顶段与底段用小写 cy_d_*，中间段用大写 cy_D_*（老工具口径）
        String dmid = (n == 0 || top) ? "d" : "D";
        String bottomKey = dmid.equals("d") ? "towerCyDBottom" : "towerCyDDBottom";
        for (int k = 1; k <= 20; k++) {
            b.append(fill(bottomKey, "k", k,
                    "value", k <= db.size() ? py(db.get(k - 1), 1) : "4300"));
        }
        double lastTop = dt.isEmpty() ? 4300 : dt.get(dt.size() - 1);
        String topKey = dmid.equals("d") ? "towerCyDTop" : "towerCyDDTop";
        for (int k = 1; k <= 20; k++) {
            b.append(fill(topKey, "k", k,
                    "value", k <= dt.size() ? py(dt.get(k - 1), 1) : py(lastTop, 1)));
        }

        // 中间段才写附件/电缆线夹存在性（老工具 ls_heights 分支）
        if (params != null) {
            for (int i = 1; i <= 20; i++) {
                Object v = params.get("H" + i + "_L_Exist");
                b.append(fill("towerLExist", "i", i, "value", v, "note", existComment(v)));
            }
            b.append(fill("towerBlank"));
            for (int i = 1; i <= 20; i++) {
                Object v = params.get("H" + i + "_CABLE_Exist");
                b.append(fill("towerCableExist", "i", i, "value", v, "note", existComment(v)));
            }
        }

        b.append(outBlock("driveSize"));
        return b.toString();
    }

    /** 第1段门洞块（复刻 {@code towerInfoW} 的 {@code n==0} 分支）。 */
    private static String renderDoorBlock(TowerGeometry.Door d) {
        StringBuilder b = new StringBuilder();
        if (d == null) {
            return "";
        }
        if (d.reinforced()) {
            b.append(fill("doorRTitle"));
            b.append(fill("doorRH", "value", py(d.reinforcementHeight(), 0)));
            b.append(fill("doorRAlpha"));
            b.append(fill("doorRAlphaFrame"));
            b.append(fill("doorRH1", "value", py(d.reinforcementOpeningHeight(), 0)));
            b.append(fill("doorRH2"));
        } else {
            b.append(fill("doorNTitle"));
            b.append(fill("doorNHFrame", "value", py(d.framePosition(), 0)));
            b.append(fill("doorNAlphaFrame"));
            b.append(fill("doorNH1", "value", py(d.openingHeight(), 0)));
            b.append(fill("doorNH2", "value", py(d.straightEdgeLength(), 0)));
            b.append(fill("doorNB1", "value", py(d.openingWidth(), 0)));
        }
        return b.toString();
    }

    // ============================================================
    // 2) 附件信息关系式（复刻 midSkelW）
    // ============================================================

    /** 渲染一段的附件信息关系式（格式对齐 {@code infoW.midSkelW}）。 */
    public static String renderAttachment(TowerDesignRequest req, TowerDesignEngine.SectionDesign sd, boolean limit) {
        StringBuilder b = new StringBuilder();
        int n = sd.sectionNo() - 1;
        TowerGeometry geo = req.geometry();
        boolean upperLimit = limit && isMiddleFlange(geo, n + 1);
        boolean lowerLimit = limit && isMiddleFlange(geo, n);
        Map<String, Object> p = sd.parameters();
        List<Double> inc = sd.accessoryIncrements();
        List<Double> cable = sd.cableClampIncrements();

        b.append(fill("attHeader1"));
        b.append(fill("attHeader2", "case", req.caseName(), "section", sd.sectionNo()));
        b.append(fill("attHeader3", "version", TmsdVocabulary.ontologyVersion()));
        // 中文名称头块（老工具 infoformat.skel_name）
        b.append(outBlock("skelName"));
        b.append(fill("attSecTitle"));
        b.append(fill("attSecHTotal", "value", py(sd.sectionTotalHeight(), 0)));

        b.append(fill("attUpperTitle"));
        b.append(fill("attDaTop", "value", py(sd.upperFlangeOuterDiameter(), 1)));
        b.append(fill("attDiTop", "value", attachmentDi(sd.upperFlangeOuterDiameter(), sd.upperFlangeNeckThickness(),
                sd.upperFlangeInnerDiameter(), upperLimit)));
        b.append(fill("attTflTop", "value", attachmentTfl(sd.upperFlangeThickness(), upperLimit)));
        b.append(fill("attSTop", "value", py(sd.upperFlangeNeckThickness(), 1)));

        b.append(fill("attLowerTitle"));
        b.append(fill("attDaBottom", "value", py(sd.lowerFlangeOuterDiameter(), 1)));
        b.append(fill("attDiBottom", "value", attachmentDi(sd.lowerFlangeOuterDiameter(), sd.lowerFlangeNeckThickness(),
                sd.lowerFlangeInnerDiameter(), lowerLimit)));
        b.append(fill("attTflBottom", "value", attachmentTfl(sd.lowerFlangeThickness(), lowerLimit)));
        b.append(fill("attSBottom", "value", py(sd.lowerFlangeNeckThickness(), 1)));

        b.append(fill("attPlatformTitle"));
        b.append(fill("attHPlatform", "value", py(p.get("H_platform"), 0)));

        b.append(fill("attLadderTitle"));
        b.append(fill("attLadderTop"));
        b.append(fill("attLLadder", "value", py(sd.ladderLength(), 0)));
        b.append(fill("attLLadderI", "value", py(p.get("L_LADDER_I"), 0)));
        b.append(fill("attWLadderI", "value", py(p.get("W_LADDER_I"), 0)));
        b.append(fill("attAlpha"));
        b.append(fill("attHLL"));
        b.append(fill("attB"));
        b.append(fill("attLadderUp"));
        b.append(fill("attLadderBottom"));

        b.append(fill("attTrayTitle"));
        b.append(fill("attLC", "value", py(sd.trayLength(), 0)));

        b.append(fill("attSupportTitle"));
        b.append(fill("attHSupport", "value", py(p.get("H_SUPPORT"), 0)));

        b.append(fill("attMidSupportTitle"));
        for (int i = 0; i < inc.size(); i++) {
            b.append(fill("attHL", "i", i + 1, "value", incrementValue(inc.get(i), i)));
        }
        b.append(fill("attBlank"));

        b.append(fill("attCableTitle"));
        for (int i = 0; i < cable.size(); i++) {
            b.append(fill("attHCable", "i", i + 1, "value", incrementValue(cable.get(i), i)));
        }
        b.append(fill("attCableTopH", "value", num(TmsdVocabulary.num("lastBracketToTopFlange"))));
        b.append(fill("attLCable", "value", num(p.get("L_CABLE"))));
        b.append(fill("attL1CableI", "value", num(p.get("L1_CABLE_I"))));
        b.append(fill("attL2CableI", "value", num(p.get("L2_CABLE_I"))));
        b.append(fill("attDiCableTop", "value", py(p.get("di_cable_top"), 1)));
        b.append(fill("attDiCableDown", "value", py(p.get("di_cable_down"), 1)));

        b.append(fill("attLightTitle"));
        List<Double> lights = sd.lightHeights();
        double topLightAbs = lights.isEmpty() ? sd.sectionTotalHeight() : lights.get(lights.size() - 1);
        double hLight2Fl = lights.isEmpty() ? TmsdVocabulary.lower("firstLightHeight") : lights.get(0);
        double hLight2Platform = round1(sd.sectionTotalHeight() - topLightAbs);
        b.append(fill("attHLight2Fl", "value", py(hLight2Fl, 0)));
        b.append(fill("attLightBottomCircle", "value", py(req.geometry().innerDiameterAt(n, hLight2Fl), 1)));
        b.append(fill("attHLight2Platform", "value", py(hLight2Platform, 0)));
        b.append(fill("attLightTopCircle",
                "value", py(req.geometry().innerDiameterAt(n, sd.sectionTotalHeight() - hLight2Platform), 1)));

        b.append(fill("attAnchorTitle"));
        b.append(fill("attHAp", "value", py(req.layoutReference().middleSection(sd.sectionNo()) != null
                ? req.layoutReference().middleSection(sd.sectionNo()).safetyAnchorHeight() : 0, 0)));

        b.append(fill("attBcTitle"));
        b.append(fill("attDaB", "value", py(req.geometry().innerDiameterAt(n, sd.sectionTotalHeight() - 825), 1)));
        double hSupport = sd.supportHeight();
        b.append(fill("attDaC", "value", py(req.geometry().innerDiameterAt(n, hSupport + 400), 1)));

        b.append(fill("attLightningTitle"));
        b.append(fill("attBBA", "value", py((Double) p.get("B_B_A"), 0)));
        b.append(fill("attBTA", "value", py((Double) p.get("B_T_A"), 0)));
        // 老应用 skel_comp 原样（Bush_a=120、上下纵向定位判定、侧支撑、电缆线槽定位、休息踏板、过法兰支撑）
        b.append(outBlock("skelComp"));
        return b.toString();
    }

    // ============================================================
    // 3) 底法兰 / 连接法兰（复刻 tflWrite / flWrite）
    // ============================================================

    /** 底法兰关系式（第0法兰；T 型走 {@code tflWrite}，否则 {@code flWrite}）。 */
    public static String renderBottomFlange(TowerDesignRequest req, boolean limit) {
        TowerGeometry geo = req.geometry();
        TowerGeometry.Flange f = geo.flanges().get(0);
        StringBuilder b = new StringBuilder();
        b.append(fill("bfHeader1"));
        b.append(fill("bfHeader2", "case", req.caseName()));
        b.append(fill("bfHeader3", "version", TmsdVocabulary.ontologyVersion()));
        b.append(outBlock("weightInfo"));
        if (geo.isTTypeBottomFlange()) {
            b.append(fill("bfTTitle"));
            b.append(fill("bfTDa", "value", py(f.outerDiameter(), 1)));
            b.append(fill("bfTDi", "value", diStr(geo, f, 0, limit)));
            b.append(fill("bfTDm", "value", py(f.boltCircleDiameter(), 0)));
            b.append(fill("bfTTfl", "value", tflStr(geo, f, 0, limit)));
            b.append(fill("bfTS", "value", py(f.neckThickness(), 1)));
            b.append(fill("bfTHTotal", "value", py(f.thickness() + f.neckHeight(), 0)));
            b.append(fill("bfTDhole", "value", py(f.boltHoleDiameter(), 0)));
            b.append(fill("bfTNInner", "value", py(f.boltCount() / 2.0, 0)));
            b.append(fill("bfTDaOuter", "value", py(f.tFlangeOuterDiameter(), 1)));
            b.append(fill("bfTDmOuter", "value", py(f.tFlangeOuterBoltCircle(), 0)));
            b.append(fill("bfTDholeOuter", "value", py(f.boltHoleDiameter(), 0)));
            b.append(fill("bfTNOuter", "value", py(f.boltCount() / 2.0, 0)));
        } else {
            b.append(fill("bfOTitle"));
            b.append(fill("bfODa", "value", py(f.outerDiameter(), 1)));
            b.append(fill("bfODi", "value", diStr(geo, f, 0, limit)));
            b.append(fill("bfODm", "value", py(f.boltCircleDiameter(), 0)));
            b.append(fill("bfOTfl", "value", tflStr(geo, f, 0, limit)));
            b.append(fill("bfOS", "value", py(f.neckThickness(), 1)));
            b.append(fill("bfOHTotal", "value", py(f.thickness() + f.neckHeight(), 0)));
            b.append(fill("bfODhole", "value", py(f.boltHoleDiameter(), 0)));
            b.append(fill("bfON", "value", py(f.boltCount(), 0)));
        }
        return b.toString();
    }

    /** 连接法兰关系式（{@code 连接法兰{i}}，读取第 {@code i} 个法兰）。 */
    public static String renderConnectionFlange(TowerDesignRequest req, int i, boolean limit) {
        TowerGeometry geo = req.geometry();
        TowerGeometry.Flange f = geo.flanges().get(i);
        StringBuilder b = new StringBuilder();
        b.append(fill("cfHeader1"));
        b.append(fill("cfHeader2", "case", req.caseName(), "i", i));
        b.append(fill("cfHeader3", "version", TmsdVocabulary.ontologyVersion()));
        b.append(outBlock("weightInfo"));
        b.append(fill("cfTitle", "i", i));
        b.append(fill("cfDa", "value", py(f.outerDiameter(), 1)));
        b.append(fill("cfDi", "value", diStr(geo, f, i, limit)));
        b.append(fill("cfDm", "value", py(f.boltCircleDiameter(), 0)));
        b.append(fill("cfTfl", "value", tflStr(geo, f, i, limit)));
        b.append(fill("cfS", "value", py(f.neckThickness(), 1)));
        b.append(fill("cfHTotal", "value", py(f.thickness() + f.neckHeight(), 0)));
        b.append(fill("cfDhole", "value", py(f.boltHoleDiameter(), 0)));
        b.append(fill("cfN", "value", py(f.boltCount(), 0)));
        return b.toString();
    }

    /** 分片段中径信息（复刻 {@code infoW.vflangetowerinfo}）。 */
    public static String renderVFlangeTowerInfo(TowerDesignRequest req, int n) {
        TowerGeometry geo = req.geometry();
        TowerGeometry.Flange lower = geo.flanges().get(n + 1);
        TowerGeometry.Flange upper = geo.flanges().get(n + 2);
        StringBuilder b = new StringBuilder();
        b.append(fill("vfTitle"));
        b.append(fill("vfMidDia"));
        b.append(fill("vfDaMidTop", "value", py(lower.outerDiameter() - lower.neckThickness(), 1)));
        b.append(fill("vfDaMidBottom", "value", py(upper.outerDiameter() - upper.neckThickness(), 1)));
        b.append(outBlock("vflangeTail"));
        return b.toString();
    }

    // ============================================================
    // 极限法兰模式辅助
    // ============================================================

    /** 是否「中间法兰」（老工具 get_tfl/get_di 只替换首/末法兰之外的法兰）。 */
    private static boolean isMiddleFlange(TowerGeometry geo, int idx) {
        return idx >= 1 && idx <= geo.flangeCount() - 2;
    }

    /** 法兰有效厚度：极限模式下中间法兰取本体 {@code limitFlangeThickness}。 */
    private static double effTfl(TowerGeometry geo, TowerGeometry.Flange f, int idx, boolean limit) {
        return (limit && isMiddleFlange(geo, idx)) ? TmsdVocabulary.num("limitFlangeThickness") : f.thickness();
    }

    /** 法兰厚度文本：极限模式下中间法兰输出为整数（老工具 monkey-patch 返回 Python int）。 */
    private static String tflStr(TowerGeometry geo, TowerGeometry.Flange f, int idx, boolean limit) {
        if (limit && isMiddleFlange(geo, idx)) {
            return num(TmsdVocabulary.num("limitFlangeThickness"));
        }
        return py(f.thickness(), 0);
    }

    /** 法兰内径文本：极限模式下中间法兰 = {@code DA - S*2 - limitFlangeInnerReduction}（1 位小数）。 */
    private static String diStr(TowerGeometry geo, TowerGeometry.Flange f, int idx, boolean limit) {
        if (limit && isMiddleFlange(geo, idx)) {
            // 老工具 get_di 返回 DA-S*2-600（1 位小数），flWrite 再 round(...,0) ⇒ 最终取整
            double reduced = f.outerDiameter() - f.neckThickness() * 2 - TmsdVocabulary.num("limitFlangeInnerReduction");
            return py(reduced, 0);
        }
        return py(f.innerDiameter(), 0);
    }

    /**
     * 附件信息（{@code midSkelW}）法兰内径文本：极限模式下中间法兰 =
     * {@code DA - S*2 - limitFlangeInnerReduction}（保留 1 位小数，复刻 {@code midSkelW} 的 {@code round(...,1)}）。
     */
    private static String attachmentDi(double da, double s, double actualDi, boolean limitMiddle) {
        if (limitMiddle) {
            return py(da - s * 2 - TmsdVocabulary.num("limitFlangeInnerReduction"), 1);
        }
        return py(actualDi, 1);
    }

    /**
     * 附件信息（{@code midSkelW}）法兰厚度文本：极限模式下中间法兰取本体
     * {@code limitFlangeThickness}（整数字面量），其余保留 1 位小数前的取整（复刻 {@code round(...,0)}）。
     */
    private static String attachmentTfl(double actualTfl, boolean limitMiddle) {
        return limitMiddle ? num(TmsdVocabulary.num("limitFlangeThickness")) : py(actualTfl, 0);
    }

    /** 第 {@code n} 段顶部法兰增厚导致的末筒节扣减量（复刻 {@code get_top_delta}）。 */
    private static double topDelta(TowerGeometry geo, int n, boolean limit) {
        if (!limit) {
            return 0;
        }
        int topIdx = n + 1;
        if (topIdx == geo.flangeCount() - 1) {
            return 0;
        }
        double orig = geo.flanges().get(topIdx).thickness();
        return Math.max(round1(TmsdVocabulary.num("limitFlangeThickness") - orig), 0);
    }

    /** 第 {@code n} 段底部法兰增厚导致的首筒节扣减量（复刻 {@code get_bottom_delta}）。 */
    private static double bottomDelta(TowerGeometry geo, int n, boolean limit) {
        if (!limit) {
            return 0;
        }
        if (n == 0) {
            return 0;
        }
        double orig = geo.flanges().get(n).thickness();
        return Math.max(round1(TmsdVocabulary.num("limitFlangeThickness") - orig), 0);
    }

    /** 顶段上法兰脖子高度（老工具取 TowerGeo 倒数第二行的高度）。 */
    private static double topSectionNeckHeight(TowerGeometry geo) {
        List<TowerGeometry.Course> cs = geo.courses();
        TowerGeometry.Course c = cs.get(cs.size() - 2);
        return (c.elevationTop() - c.elevationBottom()) * 1000;
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
                .append("（").append(TmsdVocabulary.numericConstraints().size())
                .append(" 条可数值校验 Restriction 逐条判定）\n");
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

    /** {@code *_Exist} 行尾注释：{@code yes} → {@code /*存在}，其余 → {@code /*不存在}（与老应用一致）。 */
    private static String existComment(Object v) {
        return "yes".equals(v) ? " /*存在" : " /*不存在";
    }

    /**
     * 增量序列取值格式：首元素为「首组绝对高度」（老应用 {@code round(h_prev,4)}，浮点），
     * 其余元素为「距上一组的增量」（老应用 {@code round(diff)}，整数）。
     */
    private static String incrementValue(double v, int index) {
        return index == 0 ? py(v, 4) : num(v);
    }

    /** 整型（老应用 {@code str(int)} 风格：整数不带小数）。 */
    private static String num(Object v) {
        if (v == null) {
            return "0";
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            return d == Math.rint(d) ? String.valueOf((long) d) : trim(d);
        }
        return v.toString();
    }

    /**
     * 老应用浮点格式（Python {@code str(float)} 风格）：四舍五入到 {@code nd} 位；
     * 结果为整数时仍补 {@code .0}（如 {@code 4982.0}、{@code 140.0}）。
     */
    private static String py(Object value, int nd) {
        if (value == null) {
            return py(0.0, nd);
        }
        double v = value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
        BigDecimal bd = BigDecimal.valueOf(v).setScale(nd, RoundingMode.HALF_UP).stripTrailingZeros();
        String s = bd.toPlainString();
        return s.contains(".") ? s : s + ".0";
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
