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
 * {@code drawing/drawingmain.py#generate_creo_parameters}：
 * <ul>
 *   <li>筒体信息 —— {@code infoW.towerInfoW}（第1段/中间段/顶段分支：门洞块、大小写 {@code cy_d_*}/{@code cy_D_*}）；</li>
 *   <li>附件信息 —— {@code infoW.midSkelW}（仅中间段）；</li>
 *   <li>底法兰 / 连接法兰 —— {@code infoW.tflWrite} / {@code infoW.flWrite}；</li>
 *   <li>分片法兰 —— {@code infoformat.flange_start/flange_end} 与 {@code infoW.vflangetowerinfo}。</li>
 * </ul>
 * 另输出 {@code 方案对比报告.md}（UTF-8）。
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

    /**
     * 老工具 {@code drawing/infoformat.py#weight_info} 原样文本（逐行照抄，含 Python 三引号字符串
     * 中由行尾反斜杠连接后的合并行）。
     */
    private static final String WEIGHT_INFO =
            "/**设置显示模型中文名称项**/\n"
                    + "PART_NAME=PTC_COMMON_NAME\n"
                    + "\n"
                    + "/**设置显示模型重量**/\n"
                    + "E_WGH=pro_mp_mass\n"
                    + "\n"
                    + "/*将重量参数值由实数类型转化为字符串类型\n"
                    + "if  PRO_MP_MASS<0.1\n"
                    + "重量 =\"0.1\"\n"
                    + "endif\n"
                    + "if PRO_MP_MASS>=1\n"
                    + "重量 =extract(itos(PRO_MP_MASS*100),1,string_length(itos(PRO_MP_MASS*100))-2)+\".\"+extract(itos(PRO_MP_MASS*100),string_length(itos(PRO_MP_MASS*100))-1,2)\n"
                    + "endif\n"
                    + "if PRO_MP_MASS<1 & PRO_MP_MASS>=0.1\n"
                    + "重量 =extract(itos(PRO_MP_MASS*100),1,string_length(itos(PRO_MP_MASS*100))-2)+\"0.\"+extract(itos(PRO_MP_MASS*100),string_length(itos(PRO_MP_MASS*100))-1,2)\n"
                    + "endif\n"
                    + "\n"
                    + "/*重量小于10时，3位小数\n"
                    + "If PRO_MP_MASS<10\n"
                    + "E_WGH=ceil(PRO_MP_MASS-0.0004,3)\n"
                    + "else\n"
                    + "endif\n"
                    + "\n"
                    + "/*重量大于等于10且小于100时，2位小数\n"
                    + "If PRO_MP_MASS>=10 & PRO_MP_MASS<100\n"
                    + "E_WGH=ceil(PRO_MP_MASS-0.004,2)\n"
                    + "else\n"
                    + "endif\n"
                    + "/*重量大于等于100且小于1000时，1位小数\n"
                    + "If PRO_MP_MASS>=100 & PRO_MP_MASS<1000\n"
                    + "E_WGH=ceil(PRO_MP_MASS-0.04,1)\n"
                    + "else\n"
                    + "endif\n"
                    + "/*重量大于等于1000时，整数\n"
                    + "If PRO_MP_MASS>=1000\n"
                    + "E_WGH=ceil(PRO_MP_MASS-0.4)\n"
                    + "else\n"
                    + "endif\n";

    /** 老工具 {@code drawing/infoformat.py#skel_name} 原样文本（附件信息 txt 的「中文名称」头块）。 */
    private static final String SKEL_NAME =
            "/*---------------------| 中文名称 |------------------------------*/\n"
                    + "PART_NAME=PTC_COMMON_NAME\n"
                    + "\n";

    /**
     * 老工具 {@code drawing/infoformat.py#drive_size} 原样文本（筒体信息 txt 尾部的「筒体驱动尺寸」派生块）。
     * 逐行照抄；首字符为换行（与 Python 三引号原文一致，使该块与上方内容之间留一空行）。
     */
    private static final String DRIVE_SIZE = buildDriveSize();

    /** 老工具 {@code drawing/infoformat.py#flange_start} + {@code #flange_end}（分片法兰转换块）。 */
    private static final String VFLANGE_INFO =
            "\n"
                    + "/*上法兰尺寸名称转换为参数名称\n"
                    + "DA_BOTTOM=DA_BOTTOM_PARA\n"
                    + "DI=DI_PARA\n"
                    + "DA_TOP=DA_TOP_PARA\n"
                    + "DM=DM_PARA\n"
                    + "TFL=TFL_PARA\n"
                    + "S=S_PARA\n"
                    + "H_TOTAL=H_TOTAL_PARA\n"
                    + "DHOLE=DHOLE_PARA\n"
                    + "N=N_PARA\n"
                    + "\n"
                    + "\n"
                    + "D83=TFL/2    /*连接孔定位高度\n"
                    + "D25=(360/N)/2\n"
                    + "d90=360/n*2\n"
                    + "d241=360/N\n"
                    + "d83=TFL/2\n";

    /** 老工具 {@code drawing/infoformat.py#dmwz}（分片段定位面位置）。 */
    private static final String VFLANGE_TAIL =
            "\n/*****定位面位置*****/\n"
                    + "D295=TFL_BOTTOM+H_BOTTOM\n"
                    + "D296=SEC_H_TOTAL-TFL_TOP-H_TOP\n";

    private static String buildDriveSize() {
        StringBuilder b = new StringBuilder();
        b.append("\n/***************筒体驱动尺寸***********************************************************/\n");
        b.append("SEC_H_TOTAL=SEC_H_TOTAL\n");
        b.append("D_TOP=DA_TOP\n");
        b.append("H_total_TOP=TFL_TOP+H_TOP\n");
        b.append("/***************第1节筒体***********************************************************/\n");
        b.append("H_TOTAL_BOTTOM=TFL_BOTTOM+H_BOTTOM\n");
        b.append("下法兰总高=H_TOTAL_BOTTOM\n");
        b.append("CY1_H=CY1_H-DELTA\n");
        for (int k = 2; k <= 20; k++) {
            b.append("/***************第").append(k).append("节筒体***********************************************************/\n");
            b.append("CY").append(k).append("_H=CY").append(k).append("_H-DELTA\n");
        }
        b.append("/***************每节起始位置***********************************************************/\n");
        b.append("CY2_H_STAR=CY1_H+H_TOTAL_BOTTOM+DELTA\n");
        for (int k = 3; k <= 20; k++) {
            b.append("CY").append(k).append("_H_STAR=CY").append(k - 1).append("_H_STAR+CY")
                    .append(k - 1).append("_H+DELTA\n");
        }
        return b.toString();
    }

    /**
     * 老工具 {@code drawing/infoformat.py#skel_comp} 原样文本：防雷螺柱间距角度、上/下纵向定位判定、
     * 侧支撑、电缆线槽定位、休息踏板、过法兰支撑。逐行照抄，保持与老应用一致。
     */
    private static final String SKEL_COMP =
            "Bush_a=120 /*防雷螺柱间距角度\n"
                    + "\n"
                    + "/*下端纵向定位判定\n"
                    + "DI_BOTTOM_BUSH=DI_BOTTOM\n"
                    + "DI_TOP_BUSH=DI_TOP \n"
                    + "if TFL_BOTTOM > 100\n"
                    + "bush_bottom_h = 50\n"
                    + "else\n"
                    + "bush_bottom_h = TFL_BOTTOM/2\n"
                    + "endif\n"
                    + "\n"
                    + "/*上端纵向定位判定\n"
                    + "if TFL_TOP > 100\n"
                    + "bush_top_h = 50\n"
                    + "else\n"
                    + "bush_top_h = TFL_TOP/2\n"
                    + "endif\n"
                    + "\n"
                    + "/*---------------------| 侧支撑 |------------------------------*/\n"
                    + "$H1_S=-980*cos(Alpha)/*爬梯侧支撑距上法兰上端面安装高度\n"
                    + "\n"
                    + "/*---------------------| 电缆线槽定位 |------------------------------*/\n"
                    + "cable_up_circle =DA_TOP-2*S_TOP\n"
                    + "\n"
                    + "/*---------------------| 休息踏板 |------------------------------*/\n"
                    + "if $H_LADDER_TOP < 0\n"
                    + "    $rest1=-(280*10+140)\n"
                    + " else\n"
                    + "   $rest1=-(280*11+140)\n"
                    + " endIF\n"
                    + "\n"
                    + "/*---------------------| 过法兰支撑 |------------------------------*/\n"
                    + "OFS_BOTTOM_H = 50 /*下方过法兰支撑距离底平面\n"
                    + "DI_BOTTOM_OFS=DI_BOTTOM /*下方过法兰支撑所在位置内径\n"
                    + "OFS_TOP_H = 50 /*上方过法兰支撑距离底平面\n"
                    + "DI_TOP_OFS =DI_TOP /*上方过法兰支撑所在位置内径\n"
                    + "\n";

    /** 老工具 towerInfoW 的 cy 列表只列「真实筒节」：剔除首项（下法兰所占筒节）与末项（上法兰所占筒节）。 */
    private static List<Double> realCourses(List<Double> all) {
        if (all.size() <= 2) {
            return List.of();
        }
        return all.subList(1, all.size() - 1);
    }

    private TmsdOutputWriter() {
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
                    bottomBody += VFLANGE_INFO;
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
                    renderConnectionFlange(req, i, limit) + VFLANGE_INFO));
        } else {
            out.add(writeFile(flangeDir, "连接法兰" + i + "关系式.txt",
                    renderConnectionFlange(req, i, limit)));
        }
        if (i == vFlangeQty) {
            out.add(writeFile(flangeDir, "连接法兰" + i + "关系式_分片法兰.txt",
                    renderConnectionFlange(req, i, limit) + VFLANGE_INFO));
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
        b.append(WEIGHT_INFO);
        b.append("DELTA=0.02/*缝隙高度\n");
        b.append("SEC_H_TOTAL=").append(py(sc.totalHeight(), 0)).append("/*筒段总高\n");
        b.append("DA_TOP=").append(py(upper.outerDiameter(), 0)).append("/*上法兰外径\n");
        b.append("TFL_TOP=").append(tflStr(geo, upper, n + 1, limit)).append("/* 上法兰厚\n");
        b.append("TFL_BOTTOM=").append(tflStr(geo, lower, n, limit)).append("/* 下法兰厚\n");
        b.append("H_BOTTOM=").append(py(lower.thickness() + lower.neckHeight() - effTfl(geo, lower, n, limit), 1))
                .append("/* 下法兰脖子高度\n");
        if (top) {
            b.append("H_TOP=").append(py(topSectionNeckHeight(geo), 1)).append("/* 上法兰脖子高度\n");
        } else {
            b.append("H_TOP=").append(py(upper.thickness() + upper.neckHeight() - effTfl(geo, upper, n + 1, limit), 0))
                    .append("/* 上法兰脖子高度\n");
        }

        if (n == 0) {
            b.append(renderDoorBlock(geo.door()));
        }

        b.append("/*********主体参数************\n");
        for (int k = 1; k <= 20; k++) {
            b.append("cy").append(k).append("_t=")
                    .append(k <= t.size() ? py(t.get(k - 1), 1) : "0")
                    .append("/*筒节").append(k).append("壁厚\n");
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
            b.append("cy").append(k).append("_h=").append(v).append("/*筒节").append(k).append("节高\n");
        }

        // 顶段与底段用小写 cy_d_*，中间段用大写 cy_D_*（老工具口径）
        String dmid = (n == 0 || top) ? "d" : "D";
        for (int k = 1; k <= 20; k++) {
            b.append("cy").append(k).append("_").append(dmid).append("_bottom=")
                    .append(k <= db.size() ? py(db.get(k - 1), 1) : "4300")
                    .append("/*筒节").append(k).append("下端直径\n");
        }
        double lastTop = dt.isEmpty() ? 4300 : dt.get(dt.size() - 1);
        for (int k = 1; k <= 20; k++) {
            b.append("cy").append(k).append("_").append(dmid).append("_top=")
                    .append(k <= dt.size() ? py(dt.get(k - 1), 1) : py(lastTop, 1))
                    .append("/*筒节").append(k).append("上端直径\n");
        }

        // 中间段才写附件/电缆线夹存在性（老工具 ls_heights 分支）
        if (params != null) {
            for (int i = 1; i <= 20; i++) {
                b.append("H").append(i).append("_L_Exist = ").append(params.get("H" + i + "_L_Exist"))
                        .append(existComment(params.get("H" + i + "_L_Exist"))).append("\n");
            }
            b.append("\n");
            for (int i = 1; i <= 20; i++) {
                b.append("H").append(i).append("_CABLE_Exist = ").append(params.get("H" + i + "_CABLE_Exist"))
                        .append(existComment(params.get("H" + i + "_CABLE_Exist"))).append("\n");
            }
        }

        b.append(DRIVE_SIZE);
        return b.toString();
    }

    /** 第1段门洞块（复刻 {@code towerInfoW} 的 {@code n==0} 分支）。 */
    private static String renderDoorBlock(TowerGeometry.Door d) {
        StringBuilder b = new StringBuilder();
        if (d == null) {
            return "";
        }
        if (d.reinforced()) {
            b.append("/***************加强板开洞信息*************/\n");
            b.append("H=").append(py(d.reinforcementHeight(), 0)).append("/*门框位置\n");
            b.append("$α=360-53/*门框角度，与X轴正方向，逆时针\n");
            b.append("α_frame=60/*门框加强板对应圆心角\n");
            b.append("H1_FRAME=").append(py(d.reinforcementOpeningHeight(), 0)).append("/*补强板高度\n");
            b.append("H2_FRAME=200/*补强板展开倒圆角\n");
        } else {
            b.append("/***************普通门洞信息*************/\n");
            b.append("H_FRAME=").append(py(d.framePosition(), 0)).append("/*门框位置\n");
            b.append("α_frame=360-53/*门框角度，与X轴，逆时针\n");
            b.append("H1_FRAME=").append(py(d.openingHeight(), 0)).append("/*门框开洞高度\n");
            b.append("H2_FRAME=").append(py(d.straightEdgeLength(), 0)).append("/*门洞直边长度\n");
            b.append("B1_FRAME=").append(py(d.openingWidth(), 0)).append("/*门洞宽度\n");
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

        b.append("/*=====================| 塔架中段附件信息 |=====================*/\n");
        b.append("/* 用例：").append(req.caseName()).append("  第").append(sd.sectionNo()).append("段\n");
        b.append("/*   本体：TowerMidSection.owl ").append(TmsdVocabulary.ontologyVersion())
                .append("（约束已逐条校验通过）\n");
        // 中文名称头块（老工具 infoformat.skel_name）
        b.append(SKEL_NAME);
        b.append("/*---------------------| 筒段 |------------------------------*/\n");
        b.append("SEC_H_total=").append(py(sd.sectionTotalHeight(), 0)).append("/*筒段总高\n\n");

        b.append("/*---------------------| 上法兰 |------------------------------*/\n");
        b.append("DA_TOP=").append(py(sd.upperFlangeOuterDiameter(), 1)).append("/*上法兰外径\n");
        b.append("DI_TOP=").append(attachmentDi(sd.upperFlangeOuterDiameter(), sd.upperFlangeNeckThickness(),
                sd.upperFlangeInnerDiameter(), upperLimit)).append("/*上法兰内径\n");
        b.append("TFL_TOP=").append(attachmentTfl(sd.upperFlangeThickness(), upperLimit)).append("/*上法兰厚\n");
        b.append("S_TOP=").append(py(sd.upperFlangeNeckThickness(), 1)).append("/*上法兰颈厚\n\n");

        b.append("/*---------------------| 下法兰 |------------------------------*/\n");
        b.append("DA_BOTTOM=").append(py(sd.lowerFlangeOuterDiameter(), 1)).append("/*下法兰外径\n");
        b.append("DI_BOTTOM=").append(attachmentDi(sd.lowerFlangeOuterDiameter(), sd.lowerFlangeNeckThickness(),
                sd.lowerFlangeInnerDiameter(), lowerLimit)).append("/*下法兰内径\n");
        b.append("TFL_BOTTOM=").append(attachmentTfl(sd.lowerFlangeThickness(), lowerLimit)).append("/*下法兰厚\n");
        b.append("S_BOTTOM=").append(py(sd.lowerFlangeNeckThickness(), 1)).append("/*下法兰颈厚\n\n");

        b.append("/*---------------------| 平台 |------------------------------*/\n");
        b.append("H_platform=").append(py(p.get("H_platform"), 0)).append("/*平台距离顶法兰距离\n\n");

        b.append("/*---------------------| 爬梯 |------------------------------*/\n");
        b.append("$H_LADDER_TOP= 0 /*爬梯位置\n");
        b.append("L_LADDER=").append(py(sd.ladderLength(), 0)).append("/*爬梯长度\n");
        b.append("L_LADDER_I =").append(py(p.get("L_LADDER_I"), 0)).append("/*爬梯支撑长度\n");
        b.append("W_LADDER_I =").append(py(p.get("W_LADDER_I"), 0)).append("/*爬梯支撑宽度\n");
        b.append("Alpha = atan((DA_BOTTOM - DA_TOP) / 2 / SEC_H_TOTAL)\n");
        b.append("H_L_LADDER = L_LADDER * cos(Alpha)\n");
        b.append("b = H_LIGHT2FL + 300 /*B截面高度\n");
        b.append("Ladder_up_circle = DA_TOP - 2 * S_TOP\n");
        b.append("Ladder_bottom_circle = DA_BOTTOM - 2 * S_BOTTOM\n\n");

        b.append("/*---------------------| 电缆线槽 |------------------------------*/\n");
        b.append("L_C=").append(py(sd.trayLength(), 0)).append("/*电缆线槽长度\n\n");

        b.append("/*---------------------| 扶持 |------------------------------*/\n");
        b.append("H_SUPPORT=").append(py(p.get("H_SUPPORT"), 0)).append("/*扶持高度（直读布局表 (12,n)）\n\n");

        b.append("/*---------------------| 中间段爬梯支撑 |------------------------------*/\n");
        for (int i = 0; i < inc.size(); i++) {
            b.append("H").append(i + 1).append("_L= ").append(incrementValue(inc.get(i), i))
                    .append(" /*第").append(i + 1).append("组爬梯支撑安装高度(距离上一组安装高度)\n");
        }
        b.append("\n");

        b.append("/*---------------------| 电缆线夹 |------------------------------*/\n");
        for (int i = 0; i < cable.size(); i++) {
            b.append("H").append(i + 1).append("_CABLE= ").append(incrementValue(cable.get(i), i))
                    .append(" /*第").append(i + 1).append("组电缆线夹安装高度(距离上一组安装高度)\n");
        }
        b.append("Cable_top_h = ").append(num(TmsdVocabulary.num("lastBracketToTopFlange")))
                .append("/*最后一组电缆夹板相对于顶法兰上端面\n");
        b.append("L_CABLE=").append(num(p.get("L_CABLE"))).append("/*电缆托架长度\n");
        b.append("L1_CABLE_I=").append(num(p.get("L1_CABLE_I"))).append("/*右侧电缆托架安装弦长\n");
        b.append("L2_CABLE_I=").append(num(p.get("L2_CABLE_I"))).append("/*左侧电缆托架安装弦长\n");
        b.append("di_cable_top=").append(py(p.get("di_cable_top"), 1))
                .append("/*平台上方电缆夹板位置处塔筒内径\n");
        b.append("di_cable_down=").append(py(p.get("di_cable_down"), 1))
                .append("/*下方第一个电缆夹板位置处塔筒内径\n\n");

        b.append("/*---------------------| 照明灯 |------------------------------*/\n");
        List<Double> lights = sd.lightHeights();
        double topLightAbs = lights.isEmpty() ? sd.sectionTotalHeight() : lights.get(lights.size() - 1);
        double hLight2Fl = lights.isEmpty() ? TmsdVocabulary.lower("firstLightHeight") : lights.get(0);
        double hLight2Platform = round1(sd.sectionTotalHeight() - topLightAbs);
        b.append("H_LIGHT2FL=").append(py(hLight2Fl, 0)).append("/*下灯位置\n");
        b.append("LIGHT_BOTTOM_circle=").append(py(req.geometry().innerDiameterAt(n, hLight2Fl), 1))
                .append("/*下灯位置处塔筒内径\n");
        b.append("H_LIGHT2PLATFORM=").append(py(hLight2Platform, 0)).append("/*上灯位置\n");
        b.append("LIGHT_TOP_circle=")
                .append(py(req.geometry().innerDiameterAt(n, sd.sectionTotalHeight() - hLight2Platform), 1))
                .append("/*上灯位置处塔筒内径\n\n");

        b.append("/*---------------------| 爬梯安全锚点 |------------------------------*/\n");
        b.append("H_AP=").append(py(req.layoutReference().middleSection(sd.sectionNo()) != null
                ? req.layoutReference().middleSection(sd.sectionNo()).safetyAnchorHeight() : 0, 0))
                .append("/*爬梯安全锚点安装高度（布局表 (9,n)）\n\n");

        b.append("/*---------------------| B和C 二维视图所需信息 |------------------------------*/\n");
        b.append("DA_B=").append(py(req.geometry().innerDiameterAt(n, sd.sectionTotalHeight() - 825), 1))
                .append("/*B_B视图截面所在外径（命名沿用老应用）\n");
        double hSupport = sd.supportHeight();
        b.append("DA_C=").append(py(req.geometry().innerDiameterAt(n, hSupport + 400), 1))
                .append("/*C_C视图截面所在外径（命名沿用老应用）\n");

        b.append("/*---------------------| 防雷螺柱定位 |------------------------------*/\n");
        b.append("B_B_A=").append(py((Double) p.get("B_B_A"), 0))
                .append("/*下端防雷螺柱安装角度（距爬梯中心线顺时针，其余 120° 均布）\n");
        b.append("B_T_A=").append(py((Double) p.get("B_T_A"), 0))
                .append("/*上端防雷螺柱安装角度（距爬梯中心线顺时针，其余 120° 均布）\n");
        // 老应用 skel_comp 原样（Bush_a=120、上下纵向定位判定、侧支撑、电缆线槽定位、休息踏板、过法兰支撑）
        b.append(SKEL_COMP);
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
        b.append("/*=====================| 底法兰信息 |=====================*/\n");
        b.append("/* 用例：").append(req.caseName()).append("  底法兰\n");
        b.append("/*   本体：TowerMidSection.owl ").append(TmsdVocabulary.ontologyVersion()).append("\n");
        b.append(WEIGHT_INFO);
        if (geo.isTTypeBottomFlange()) {
            b.append("/*****塔架底法兰参数****\n");
            b.append("DA=").append(py(f.outerDiameter(), 1)).append("/*T型法兰外径（筒壁外径）\n");
            b.append("DI=").append(diStr(geo, f, 0, limit)).append("/*T型法兰内径\n");
            b.append("DM=").append(py(f.boltCircleDiameter(), 0)).append("/*螺栓分度圆直径\n");
            b.append("TFL=").append(tflStr(geo, f, 0, limit)).append("/*法兰厚度\n");
            b.append("S=").append(py(f.neckThickness(), 1)).append("/*法兰颈厚\n");
            b.append("H_TOTAL=").append(py(f.thickness() + f.neckHeight(), 0)).append("/*法兰高\n");
            b.append("DHOLE=").append(py(f.boltHoleDiameter(), 0)).append("/*T型法兰内侧螺栓孔直径\n");
            b.append("N_INNER=").append(py(f.boltCount() / 2.0, 0)).append("/*T型法兰内侧螺栓数\n");
            b.append("Da_outer=").append(py(f.tFlangeOuterDiameter(), 1)).append("/*T型法兰外径\n");
            b.append("Dm_outer=").append(py(f.tFlangeOuterBoltCircle(), 0)).append("/*T型法兰外圈分度圆\n");
            b.append("dhole_outer=").append(py(f.boltHoleDiameter(), 0)).append("/*T型法兰外侧螺栓孔直径\n");
            b.append("N_OUTER=").append(py(f.boltCount() / 2.0, 0)).append("/*T型法兰外侧螺栓数\n");
        } else {
            b.append("/*****连接法兰0参数****\n");
            b.append("DA=").append(py(f.outerDiameter(), 1)).append("/*法兰外径\n");
            b.append("DI=").append(diStr(geo, f, 0, limit)).append("/*法兰内径\n");
            b.append("DM=").append(py(f.boltCircleDiameter(), 0)).append("/*螺栓分度圆直径\n");
            b.append("TFL=").append(tflStr(geo, f, 0, limit)).append("/*法兰厚度\n");
            b.append("S=").append(py(f.neckThickness(), 1)).append("/*法兰颈厚\n");
            b.append("H_TOTAL=").append(py(f.thickness() + f.neckHeight(), 0)).append("/*法兰高\n");
            b.append("DHOLE=").append(py(f.boltHoleDiameter(), 0)).append("/*螺栓孔直径\n");
            b.append("N=").append(py(f.boltCount(), 0)).append("/*螺栓数\n");
        }
        return b.toString();
    }

    /** 连接法兰关系式（{@code 连接法兰{i}}，读取第 {@code i} 个法兰）。 */
    public static String renderConnectionFlange(TowerDesignRequest req, int i, boolean limit) {
        TowerGeometry geo = req.geometry();
        TowerGeometry.Flange f = geo.flanges().get(i);
        StringBuilder b = new StringBuilder();
        b.append("/*=====================| 连接法兰信息 |=====================*/\n");
        b.append("/* 用例：").append(req.caseName()).append("  连接法兰").append(i).append("\n");
        b.append("/*   本体：TowerMidSection.owl ").append(TmsdVocabulary.ontologyVersion()).append("\n");
        b.append(WEIGHT_INFO);
        b.append("/*****连接法兰").append(i).append("参数****\n");
        b.append("DA=").append(py(f.outerDiameter(), 1)).append("/*法兰外径\n");
        b.append("DI=").append(diStr(geo, f, i, limit)).append("/*法兰内径\n");
        b.append("DM=").append(py(f.boltCircleDiameter(), 0)).append("/*螺栓分度圆直径\n");
        b.append("TFL=").append(tflStr(geo, f, i, limit)).append("/*法兰厚度\n");
        b.append("S=").append(py(f.neckThickness(), 1)).append("/*法兰颈厚\n");
        b.append("H_TOTAL=").append(py(f.thickness() + f.neckHeight(), 0)).append("/*法兰高\n");
        b.append("DHOLE=").append(py(f.boltHoleDiameter(), 0)).append("/*螺栓孔直径\n");
        b.append("N=").append(py(f.boltCount(), 0)).append("/*螺栓数\n");
        return b.toString();
    }

    /** 分片段中径信息（复刻 {@code infoW.vflangetowerinfo}）。 */
    public static String renderVFlangeTowerInfo(TowerDesignRequest req, int n) {
        TowerGeometry geo = req.geometry();
        TowerGeometry.Flange lower = geo.flanges().get(n + 1);
        TowerGeometry.Flange upper = geo.flanges().get(n + 2);
        StringBuilder b = new StringBuilder();
        b.append("/*********分片塔分缝参数************/\n");
        b.append("/**中径/\n");
        b.append("DA_MID_TOP=").append(py(lower.outerDiameter() - lower.neckThickness(), 1)).append("/*下中径\n");
        b.append("DA_MID_BOTTOM=").append(py(upper.outerDiameter() - upper.neckThickness(), 1)).append("/*上中径\n");
        b.append(VFLANGE_TAIL);
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
