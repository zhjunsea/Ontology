package com.ocean.ontologyframework.tmsd;

import java.util.ArrayList;
import java.util.List;

/**
 * 塔架几何输入（对应老工具 towerdesign 读取的 {@code TowerGeoInput_*.xlsx}）。
 *
 * <p>sheet 与列的对应关系逐条抄自
 * {@code design/towerdesign-main/towerdesign/tools/tool.py} 与
 * {@code design/老塔架设计工具读取逻辑与硬编码-本体对应分析报告.md}：
 * <ul>
 *   <li>{@code Description}：r2 → 叶轮直径 / 功率 / 风区 / 顶法兰 / 项目名称 / 备注 / 主体重量 / 产品代码</li>
 *   <li>{@code TowerGeo}：r1 表头，r2 起数据；列 段数 / 标高(下) / dbottom外径 / 标高(上) / dtop外径 / 壁厚 / … / 认证参考板材等级</li>
 *   <li>{@code Flange}：r1/r2 表头，r3 起数据；列 标高 / 外径 / 内径 / 螺栓分度圆 / 法兰厚度 / 颈厚 / 颈高 / 螺栓公称直径 / 螺栓孔直径 / 螺栓数 / 圆角 / 法兰类型 / 螺栓等级 / T型法兰外径 / T型法兰外圈分度圆</li>
 *   <li>{@code Door}：r1/r2 表头，r3 起数据；列 门框位置 / 塔筒外径 / 塔筒壁厚 / 高度 / 宽度 / 直边长度 / 门框厚度 / 门框宽度 / 塔筒外漏出 / 门框弧形</li>
 * </ul>
 *
 * <p>坐标换算（老工具用 xlrd 0-based，本类用 1-based 语义）：
 * {@code openpyxl(r+1,c+1) == xlrd.cell_value(r,c)}。
 */
public final class TowerGeometry {

    /** 筒节行（{@code TowerGeo} 的一行）。 */
    public record Course(int no, double elevationBottom, double outerDiameterBottom,
                         double elevationTop, double outerDiameterTop,
                         double wallThickness, String plateGrade) {
    }

    /** 法兰行（{@code Flange} 的一行）。 */
    public record Flange(int no, double elevation, double outerDiameter, double innerDiameter,
                         double boltCircleDiameter, double thickness, double neckThickness,
                         double neckHeight, double boltHoleDiameter, int boltCount,
                         double filletRadius, String type, String boltGrade,
                         Double tFlangeOuterDiameter, Double tFlangeOuterBoltCircle) {
    }

    /** 门洞（{@code Door} 的 r3）。 */
    public record Door(double framePosition, double towerOuterDiameter, double wallThickness,
                       double openingHeight, double openingWidth, double straightEdgeLength,
                       double frameThickness, double frameWidth, double protrusion, String arcType,
                       boolean reinforced, Double reinforcementHeight, Double reinforcementOpeningHeight) {
    }

    /** 机组信息（{@code Description} 的 r2）。 */
    public record Description(double rotorDiameter, double powerMw, String windZone, String topFlange,
                              String projectName, String remark, double mainWeightTon, String productCode) {
    }

    private final Description description;
    private final List<Course> courses;
    private final List<Flange> flanges;
    private final Door door;
    private final int vFlangeQty;
    private final String sourceFile;

    public TowerGeometry(Description description, List<Course> courses, List<Flange> flanges,
                         Door door, int vFlangeQty, String sourceFile) {
        this.description = description;
        this.courses = List.copyOf(courses);
        this.flanges = List.copyOf(flanges);
        this.door = door;
        this.vFlangeQty = vFlangeQty;
        this.sourceFile = sourceFile;
    }

    public Description description() {
        return description;
    }

    public List<Course> courses() {
        return courses;
    }

    public List<Flange> flanges() {
        return flanges;
    }

    public Door door() {
        return door;
    }

    /** 分片塔段数（老工具 {@code vFlqty()}：{@code V-Flange} 工作表自第 3 行起有值的行数）。 */
    public int vFlangeQty() {
        return vFlangeQty;
    }

    /** 底法兰是否为 T 型法兰（老工具 {@code torlFlange()}：Flange r3 的 T 型法兰外径列有值）。 */
    public boolean isTTypeBottomFlange() {
        return !flanges.isEmpty() && flanges.get(0).tFlangeOuterDiameter() != null;
    }

    public String sourceFile() {
        return sourceFile;
    }

    /** 法兰数量（老工具 {@code flSecHqty()['flange_qty']}）。 */
    public int flangeCount() {
        return flanges.size();
    }

    /** 塔段数量 = 法兰数 − 1（老工具 {@code flSecHqty()['section_qty']}）。 */
    public int sectionCount() {
        return Math.max(flanges.size() - 1, 0);
    }

    /** 法兰标高序列（老工具 {@code flSecHqty()['flange_height']}）。 */
    public List<Double> flangeElevations() {
        List<Double> out = new ArrayList<>(flanges.size());
        for (Flange f : flanges) {
            out.add(f.elevation());
        }
        return out;
    }

    /**
     * 第 {@code n} 段（0-based）在 {@code TowerGeo} 中的行区间（老工具 {@code flangePos()}）。
     *
     * <p>返回 {@code [start, end]}：{@code start} 是标高(下)等于该段下法兰标高的行下标（0-based），
     * {@code end} 是下一段下法兰的行下标（或末行）。即 {@code mgeoRead(n)} 遍历 {@code [start, end)}。
     */
    public int[] sectionRowRange(int n) {
        List<Double> heights = flangeElevations();
        List<Integer> pos = new ArrayList<>();
        for (int i = 0; i < courses.size(); i++) {
            double eb = round4(courses.get(i).elevationBottom());
            for (Double h : heights) {
                if (Math.abs(eb - round4(h)) < 1e-9) {
                    pos.add(i);
                    break;
                }
            }
        }
        pos.add(courses.size() - 1);
        if (n < 0 || n + 1 >= pos.size()) {
            throw new IllegalArgumentException("段序号越界: " + n + "（共 " + sectionCount() + " 段）");
        }
        return new int[]{pos.get(n), pos.get(n + 1)};
    }

    /**
     * 第 {@code n} 段（0-based）的环焊缝位置（mm，距段底）。
     *
     * <p><b>口径（铁律）</b>：直接读取主体信息表 {@code TowerGeo} 的 B 列「标高(下)(m)」，
     * 逐筒节换算为「相对本段下法兰标高」的毫米值；<b>位于法兰标高处的行予以剔除</b>
     * （法兰处不参与「距焊缝 &gt; 100」判定）。</p>
     *
     * <p>不采用「筒节高累加」推导——口径必须直读原始数据，而非程序推导。</p>
     */
    public List<Integer> weldPositions(int n) {
        int[] r = sectionRowRange(n);
        double base = round4(courses.get(r[0]).elevationBottom());
        List<Integer> out = new ArrayList<>();
        for (int j = r[0]; j < r[1]; j++) {
            double eb = round4(courses.get(j).elevationBottom());
            if (isFlangeElevation(eb)) {
                continue;
            }
            out.add((int) Math.round((eb - base) * 1000));
        }
        return out;
    }

    /** 标高 {@code e}(m) 是否等于某一法兰标高（用于从焊缝列表中剔除法兰位置）。 */
    public boolean isFlangeElevation(double e) {
        double re = round4(e);
        for (Flange f : flanges) {
            if (Math.abs(round4(f.elevation()) - re) < 1e-9) {
                return true;
            }
        }
        return false;
    }

    /**
     * 第 {@code n} 段的筒节划分（老工具 {@code mgeoRead(n)}）。
     *
     * <p>返回该段内每一筒节（含上下法兰所占筒节）的高度（mm）、壁厚、下外径、上外径。
     * 顶段（{@code n == sectionCount-1}）额外把顶法兰那一行并入（老工具对顶段单独处理）。
     */
    public SectionCourses sectionCourses(int n) {
        int[] r = sectionRowRange(n);
        List<Double> h = new ArrayList<>();
        List<Double> t = new ArrayList<>();
        List<Double> db = new ArrayList<>();
        List<Double> dt = new ArrayList<>();
        boolean top = (n == sectionCount() - 1);
        int end = top ? r[1] - 1 : r[1];
        for (int j = r[0]; j < end; j++) {
            Course c = courses.get(j);
            t.add(c.wallThickness());
            h.add(round1((c.elevationTop() - c.elevationBottom()) * 1000));
            db.add(c.outerDiameterBottom());
            dt.add(c.outerDiameterTop());
        }
        if (top) {
            Course last = courses.get(r[1] - 1);
            Course topRow = courses.get(r[1]);
            t.add(last.wallThickness());
            h.add(round1((topRow.elevationTop() - last.elevationBottom()) * 1000));
            db.add(last.outerDiameterBottom());
            dt.add(topRow.outerDiameterTop());
        }
        return new SectionCourses(h, t, db, dt);
    }

    /** 一段的筒节划分（老工具 {@code mgeoRead(n)} 的四元组）。 */
    public record SectionCourses(List<Double> heights, List<Double> wallThicknesses,
                                 List<Double> outerDiametersBottom, List<Double> outerDiametersTop) {

        /** 段总高（mm，含上下法兰），老工具 {@code secNLength()[n]}。 */
        public double totalHeight() {
            double s = 0;
            for (double v : heights) {
                s += v;
            }
            return round1(s);
        }

    }

    /** 每段总高（mm），老工具 {@code secNLength()}。 */
    public List<Double> sectionLengths() {
        List<Double> out = new ArrayList<>(sectionCount());
        for (int i = 0; i < sectionCount(); i++) {
            out.add(sectionCourses(i).totalHeight());
        }
        return out;
    }

    /** 塔架总高（mm），老工具 {@code towerLength()}。 */
    public double towerLength() {
        double s = 0;
        for (double v : sectionLengths()) {
            s += v;
        }
        return round1(s);
    }

    /** 段内各筒节的累计高度（mm），老工具 {@code secNHeight(n)}。 */
    public List<Double> cumulativeCourseHeights(int n) {
        SectionCourses sc = sectionCourses(n);
        List<Double> out = new ArrayList<>(sc.heights().size());
        double acc = 0;
        for (double v : sc.heights()) {
            acc += v;
            out.add(round1(acc));
        }
        return out;
    }

    /**
     * 段内距段底 {@code h}（mm）处的<b>内径</b>，老工具 {@code diCal(h, n)}。
     *
     * <p>公式逐行照搬 {@code tools/tool.py#diCal}：
     * {@code Di = d + (D - d) * (L - h_m) / L}，其中 {@code h_m = h - h[0]}、
     * {@code L = 段总高 - h[0] - h[-1]}、{@code D = 下外径[1]}、{@code d = 上外径[-2]}；
     * 再按筒节归属减去 {@code 2 × 壁厚}。返回值即本体 {@code :平台所在处内径} 的取值来源。
     */
    public double innerDiameterAt(int n, double h) {
        SectionCourses sc = sectionCourses(n);
        List<Double> h1 = cumulativeCourseHeights(n);
        double h0 = sc.heights().get(0);
        double hLast = sc.heights().get(sc.heights().size() - 1);
        double hm = h - h0;
        double d = sc.outerDiametersBottom().get(1);
        double dTop = sc.outerDiametersTop().get(sc.outerDiametersTop().size() - 2);
        double l = sc.totalHeight() - h0 - hLast;
        double di = dTop + (d - dTop) * (l - hm) / l;
        for (int i = 0; i < h1.size(); i++) {
            if (h <= h1.get(i)) {
                return round1(di - 2 * sc.wallThicknesses().get(i));
            }
        }
        return round1(di - 2 * sc.wallThicknesses().get(sc.wallThicknesses().size() - 1));
    }

    /**
     * 段内距<b>段顶</b>{@code h}（mm）处的<b>中径</b>，老工具 {@code get_di(n, h)}。
     *
     * <p>公式逐行照搬 {@code tools/tool.py#get_di} + {@code calculate_diameter_at_height}：
     * {@code D_top = 上外径[-2] - 壁厚[-2]}、{@code D_bottom = 下外径[1] - 壁厚[1]}、
     * {@code H = 段总高 - h[0] - h[-1]}、{@code h' = 段总高 - h - h[0]}，
     * 结果 {@code = D_bottom + (D_top - D_bottom) * (h'/H)}。
     * 老程序 {@code selectacc.select_accessory()} 用它算 {@code di_mid}。
     */
    public double midDiameterFromSectionTop(int n, double hFromTop) {
        SectionCourses sc = sectionCourses(n);
        double h = sc.totalHeight() - hFromTop - sc.heights().get(0);
        double dTop = sc.outerDiametersTop().get(sc.outerDiametersTop().size() - 2)
                - sc.wallThicknesses().get(sc.wallThicknesses().size() - 2);
        double dBottom = sc.outerDiametersBottom().get(1) - sc.wallThicknesses().get(1);
        double hh = sc.totalHeight() - sc.heights().get(0)
                - sc.heights().get(sc.heights().size() - 1);
        return dBottom + (dTop - dBottom) * (h / hh);
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
