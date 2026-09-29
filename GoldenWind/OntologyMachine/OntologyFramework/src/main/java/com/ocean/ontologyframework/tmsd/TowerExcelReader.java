package com.ocean.ontologyframework.tmsd;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史设计 Excel 读取器（POI）。
 *
 * <p>只做「读」，不做任何业务判断；所有行列下标均来自老工具 towerdesign 的硬编码，
 * 见 {@code tools/tool.py}（{@code get_sheets} / {@code flSecHqty} / {@code mgeoRead} /
 * {@code flgeoRead}）与 {@code drawing/infoW.py}。
 *
 * <p>坐标换算：老工具用 xlrd（0-based），本类用 POI（0-based），二者一致；
 * 与 openpyxl 的 1-based 关系为 {@code openpyxl(r+1,c+1) == xlrd.cell_value(r,c)}。
 */
public final class TowerExcelReader {

    private static final Logger log = LoggerFactory.getLogger(TowerExcelReader.class);

    private TowerExcelReader() {
    }

    // ============================================================
    // 塔架几何输入
    // ============================================================

    /** 读取 {@code TowerGeoInput_*.xlsx}。 */
    public static TowerGeometry readGeometry(Path xlsx) throws IOException {
        try (InputStream in = Files.newInputStream(xlsx);
             Workbook wb = new XSSFWorkbook(in)) {
            TowerGeometry.Description desc = readDescription(wb);
            List<TowerGeometry.Course> courses = readCourses(wb);
            List<TowerGeometry.Flange> flanges = readFlanges(wb);
            TowerGeometry.Door door = readDoor(wb);
            TowerGeometry g = new TowerGeometry(desc, courses, flanges, door, xlsx.getFileName().toString());
            log.info("读取塔架几何：{} 段 / {} 法兰 / {} 筒节行（{}）",
                    g.sectionCount(), g.flangeCount(), courses.size(), xlsx.getFileName());
            return g;
        }
    }

    /** {@code Description} r2（0-based row 1）。 */
    private static TowerGeometry.Description readDescription(Workbook wb) {
        Sheet sh = wb.getSheet("Description");
        if (sh == null) {
            throw new IllegalStateException("TowerGeoInput 缺少 sheet: Description");
        }
        Row r = sh.getRow(1);
        return new TowerGeometry.Description(
                num(r, 1, 0),          // 叶轮直径(m)
                num(r, 2, 0),          // 功率(MW)
                str(r, 4),             // 风区
                str(r, 5),             // 顶法兰
                str(r, 6),             // 项目名称
                str(r, 7),             // 备注
                num(r, 8, 0),          // 主体重量预估/吨
                str(r, 9));            // 产品代码
    }

    /** {@code TowerGeo}：r1 表头，r2 起数据（0-based row 1 起）。 */
    private static List<TowerGeometry.Course> readCourses(Workbook wb) {
        Sheet sh = wb.getSheet("TowerGeo");
        if (sh == null) {
            throw new IllegalStateException("TowerGeoInput 缺少 sheet: TowerGeo");
        }
        List<TowerGeometry.Course> out = new ArrayList<>();
        for (int i = 1; i < sh.getLastRowNum() + 1; i++) {
            Row r = sh.getRow(i);
            if (r == null) {
                continue;
            }
            Double eb = numOrNull(r, 1);
            Double et = numOrNull(r, 3);
            if (eb == null || et == null) {
                continue;
            }
            int no = (int) num(r, 0, out.size() + 1);
            out.add(new TowerGeometry.Course(no, eb, num(r, 2, 0), et, num(r, 4, 0),
                    num(r, 5, 0), str(r, 12)));
        }
        return out;
    }

    /** {@code Flange}：r3 起数据（0-based row 2 起）。 */
    private static List<TowerGeometry.Flange> readFlanges(Workbook wb) {
        Sheet sh = wb.getSheet("Flange");
        if (sh == null) {
            throw new IllegalStateException("TowerGeoInput 缺少 sheet: Flange");
        }
        List<TowerGeometry.Flange> out = new ArrayList<>();
        for (int i = 2; i < sh.getLastRowNum() + 1; i++) {
            Row r = sh.getRow(i);
            if (r == null) {
                continue;
            }
            Double elev = numOrNull(r, 0);
            if (elev == null) {
                continue;
            }
            out.add(new TowerGeometry.Flange(out.size() + 1, elev,
                    num(r, 1, 0), num(r, 2, 0), num(r, 3, 0), num(r, 4, 0), num(r, 5, 0),
                    num(r, 6, 0), num(r, 8, 0), (int) num(r, 9, 0), num(r, 10, 0),
                    str(r, 11), str(r, 12), numOrNull(r, 13), numOrNull(r, 14)));
        }
        return out;
    }

    /** {@code Door} r3（0-based row 2）。 */
    private static TowerGeometry.Door readDoor(Workbook wb) {
        Sheet sh = wb.getSheet("Door");
        if (sh == null) {
            return null;
        }
        Row r = sh.getRow(2);
        if (r == null) {
            return null;
        }
        // 加强门洞（第 12 列起）有值即为加强板门框，老工具 doorRein()/doortype() 依此判断
        boolean reinforced = numOrNull(r, 12) != null;
        return new TowerGeometry.Door(num(r, 0, 0), num(r, 1, 0), num(r, 2, 0), num(r, 3, 0),
                num(r, 4, 0), num(r, 5, 0), num(r, 6, 0), num(r, 7, 0), num(r, 8, 0),
                str(r, 9), reinforced);
    }

    // ============================================================
    // 项目布局表
    // ============================================================

    /** 读取 {@code 项目布局表.xlsx}。 */
    public static LayoutSpec readLayout(Path xlsx) throws IOException {
        try (InputStream in = Files.newInputStream(xlsx);
             Workbook wb = new XSSFWorkbook(in)) {
            List<LayoutSpec.MiddleSection> mid = readMiddleSections(wb);
            Map<String, Double> bottom = readParamSheet(wb, "下段");
            Map<String, Double> top = readParamSheet(wb, "顶段");
            LayoutSpec spec = new LayoutSpec(mid, bottom, top, xlsx.getFileName().toString());
            log.info("读取项目布局：中间段 {} 列 / 下段 {} 项 / 顶段 {} 项（{}）",
                    mid.size(), bottom.size(), top.size(), xlsx.getFileName());
            return spec;
        }
    }

    /**
     * {@code 中间段} sheet：行 3..14（0-based 2..13），列 2..6（0-based 1..5）= 第二段..第六段。
     */
    private static List<LayoutSpec.MiddleSection> readMiddleSections(Workbook wb) {
        Sheet sh = wb.getSheet("中间段");
        if (sh == null) {
            throw new IllegalStateException("项目布局表缺少 sheet: 中间段");
        }
        List<LayoutSpec.MiddleSection> out = new ArrayList<>();
        for (int col = 1; col <= 5; col++) {
            Double platform = numOrNull(sh.getRow(2), col);
            if (platform == null) {
                continue;
            }
            Row modelRow = sh.getRow(5);
            String model = str(modelRow, col);
            out.add(new LayoutSpec.MiddleSection(
                    col + 1,                                   // 段号：列 1 → 第二段
                    platform,                                  // r3  平台位置
                    num(sh.getRow(3), col, 0),                 // r4  爬梯支撑长度
                    num(sh.getRow(4), col, 0),                 // r5  爬梯支撑宽度
                    num(sh.getRow(13), col, 0),                // r14 踏棍宽度 W_Rung
                    model,                                     // r6  机型
                    isTrue(str(sh.getRow(6), col)),            // r7  电缆线夹是否隔开
                    num(sh.getRow(7), col, 0),                 // r8  下灯位置
                    num(sh.getRow(8), col, 0),                 // r9  上灯位置
                    num(sh.getRow(9), col, 0),                 // r10 安全锚点高度
                    num(sh.getRow(10), col, 0),                // r11 下端防雷螺柱角度
                    num(sh.getRow(11), col, 0),                // r12 上端防雷螺柱角度
                    num(sh.getRow(12), col, 0)));              // r13 扶持高度
        }
        return out;
    }

    /** {@code 下段} / {@code 顶段}：A 列参数名，B 列设计值。 */
    private static Map<String, Double> readParamSheet(Workbook wb, String name) {
        Map<String, Double> out = new LinkedHashMap<>();
        Sheet sh = wb.getSheet(name);
        if (sh == null) {
            return out;
        }
        for (int i = 0; i < sh.getLastRowNum() + 1; i++) {
            Row r = sh.getRow(i);
            if (r == null) {
                continue;
            }
            String key = str(r, 0);
            Double val = numOrNull(r, 1);
            if (key != null && !key.isBlank() && val != null) {
                out.put(key, val);
            }
        }
        return out;
    }

    // ============================================================
    // 单元格工具
    // ============================================================

    private static Double numOrNull(Row r, int c) {
        if (r == null) {
            return null;
        }
        Cell cell = r.getCell(c);
        if (cell == null) {
            return null;
        }
        CellType t = cell.getCellType();
        if (t == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (t == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (t == CellType.FORMULA) {
            try {
                return cell.getNumericCellValue();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private static double num(Row r, int c, double dflt) {
        Double v = numOrNull(r, c);
        return v == null ? dflt : v;
    }

    private static String str(Row r, int c) {
        if (r == null) {
            return null;
        }
        Cell cell = r.getCell(c);
        if (cell == null) {
            return null;
        }
        CellType t = cell.getCellType();
        if (t == CellType.STRING) {
            String s = cell.getStringCellValue().trim();
            return s.isEmpty() ? null : s;
        }
        if (t == CellType.NUMERIC) {
            double d = cell.getNumericCellValue();
            return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
        if (t == CellType.BOOLEAN) {
            return String.valueOf(cell.getBooleanCellValue());
        }
        return null;
    }

    private static boolean isTrue(String s) {
        return s != null && (s.equalsIgnoreCase("true") || s.equals("1") || s.equalsIgnoreCase("yes")
                || s.equals("是") || s.equals("隔开"));
    }
}
