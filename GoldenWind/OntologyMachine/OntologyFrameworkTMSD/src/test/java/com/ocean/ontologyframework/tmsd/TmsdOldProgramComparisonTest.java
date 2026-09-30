package com.ocean.ontologyframework.tmsd;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 老程序（towerdesign）产物 与 TMSD 产物 的端到端一致性比较测试。
 *
 * <p>同一份输入（{@code src/test/oldProgram/input/} 下的两个 excel）：
 * <ol>
 *   <li>用 {@code ProcessBuilder} 调用本机 python 运行老程序，生成 GBK 骨架关系式 txt；</li>
 *   <li>用真实设计引擎 {@link TmsdDesignPipeline} + {@link TmsdOutputWriter} 生成同结构 txt；</li>
 *   <li>按「值级」逐文件逐行比较（剥离 {@code /*} 行尾注释与纯注释行，忽略注释措辞差异）。</li>
 * </ol>
 *
 * <p>比较裁决规则：
 * <ul>
 *   <li>规则1：双方都有但值不一致 → 以老为准（视为失败，须修正）；</li>
 *   <li>规则2：老有、TMSD 无 → 按业务逻辑补全（视为失败）；</li>
 *   <li>规则3：老无、TMSD 有 → 交用户裁决（记录但不算失败）；</li>
 *   <li>例外：A2 电缆线夹（{@code H{i}_CABLE}/{@code *_CABLE_Exist}/T8/T9/电缆托架长度）、
 *       A3 照明灯位置、A5 爬梯支撑 {@code H{i}_L} 一律「按 TMSD」，容忍差异。</li>
 * </ul>
 */
class TmsdOldProgramComparisonTest {

    private static final Charset GBK = TmsdOutputWriter.CREO_CHARSET;

    private static final String CASE_NAME = "对比用例";

    private static Path geoPath;
    private static Path layoutPath;
    private static Path workDir;
    private static Path oldProgramDir;

    /** A2 电缆线夹相关键（含「存在」开关、T8/T9、托架长度）。 */
    private static final Pattern A2_KEY = Pattern.compile("^(H\\d+_CABLE|.*_CABLE_Exist|Cable_top_h|L_CABLE|L1_CABLE_I|L2_CABLE_I|T8|T9)$");

    /** A3 照明灯位置键。 */
    private static final Pattern A3_KEY = Pattern.compile("^(H_LIGHT2FL|H_LIGHT2PLATFORM|LIGHT_BOTTOM_circle|LIGHT_TOP_circle)$");

    /** A5 爬梯支撑安装高度键（含「存在」开关）。 */
    private static final Pattern A5_KEY = Pattern.compile("^H\\d+_L(_Exist)?$");

    @BeforeAll
    static void init() throws IOException {
        TmsdTestConfig.initTmsdVocabulary();
        Path moduleRoot = Paths.get("").toAbsolutePath();
        geoPath = moduleRoot.resolve(TmsdTestConfig.tmsd("historical-geo-path")).normalize();
        layoutPath = moduleRoot.resolve(TmsdTestConfig.tmsd("historical-layout-path")).normalize();
        oldProgramDir = geoPath.getParent().getParent();

        workDir = moduleRoot.resolve("target").resolve("tmsd-old-comparison");
        deleteRecursively(workDir);
        Files.createDirectories(workDir.resolve("input"));
    }

    @Test
    @DisplayName("老程序产物 vs TMSD 产物：逐文件值级比较（A2/A3/A5 容忍）")
    void compareWithOldProgram() throws Exception {
        // 复制输入到隔离目录，避免污染源码树
        Path geoCopy = workDir.resolve("input").resolve(geoPath.getFileName().toString());
        Path layoutCopy = workDir.resolve("input").resolve(layoutPath.getFileName().toString());
        Files.copy(geoPath, geoCopy, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(layoutPath, layoutCopy, StandardCopyOption.REPLACE_EXISTING);

        Path oldRoot = runOldProgram(geoCopy, layoutCopy);
        Path tmsdRoot = runTmsd(geoCopy, layoutCopy);

        Comparison cmp = compare(oldRoot, tmsdRoot);

        Path report = workDir.resolve("tmsd-old-comparison-report.txt");
        Files.writeString(report, cmp.render(), Charset.forName("UTF-8"));

        assertThat(cmp.rule2Missing()).as("规则2：老有、TMSD 无的内容（应补全）\n报告：%s", report).isEmpty();
        assertThat(cmp.rule1Mismatches()).as("规则1：值与老程序不一致（须以老为准修正）\n报告：%s", report).isEmpty();

        // 规则3（TMSD 多出）仅记录，不算失败
        assertThat(cmp.filesCompared()).as("应比较出至少一份 txt").isGreaterThan(0);
    }

    // ============================================================
    // 运行老程序
    // ============================================================

    private static Path runOldProgram(Path geo, Path layout) throws Exception {
        String code = "import sys,os;sys.path.insert(0,os.getcwd());"
                + "from towerdesign.drawing.drawingmain import generate_creo_parameters as g;"
                + "print('ZIP='+str(g(sys.argv[1],sys.argv[2],None)))";
        ProcessBuilder pb = new ProcessBuilder("python", "-c", code, geo.toString(), layout.toString());
        pb.directory(oldProgramDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), Charset.defaultCharset());
        int exit = p.waitFor();
        assertThat(exit).as("老程序应成功退出；输出：\n%s", out).isEqualTo(0);

        String base = geo.getFileName().toString();
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        Path oldRoot = geo.getParent().resolve(base);
        assertThat(oldRoot).as("老程序应生成输出目录 %s", oldRoot).exists();
        return oldRoot;
    }

    // ============================================================
    // 运行 TMSD
    // ============================================================

    private static Path runTmsd(Path geo, Path layout) throws IOException {
        TowerGeometry geometry = TowerExcelReader.readGeometry(geo);
        LayoutSpec lay = TowerExcelReader.readLayout(layout);
        TowerDesignRequest req = new TowerDesignRequest(CASE_NAME, geometry, "V17",
                ElevatorType.ROPE_GUIDED, "中国", AccessoryConnectionType.WELDED, lay);
        TmsdDesignPipeline.CaseResult result = TmsdDesignPipeline.design(req);
        assertThat(result.recommended()).as("应存在满足约束的推荐方案").isNotNull();
        TmsdOutputWriter.write(result, workDir);
        return workDir.resolve(CASE_NAME);
    }

    // ============================================================
    // 比较
    // ============================================================

    private record Comparison(int filesCompared, List<String> rule1, List<String> rule2,
                              List<String> rule3) {

        List<String> rule1Mismatches() {
            return rule1;
        }

        List<String> rule2Missing() {
            return rule2;
        }

        String render() {
            StringBuilder b = new StringBuilder();
            b.append("老程序 vs TMSD 比较报告\n");
            b.append("规则1（值不一致，以老为准）：").append(rule1.size()).append('\n');
            for (String s : rule1) {
                b.append("  ").append(s).append('\n');
            }
            b.append("规则2（TMSD 缺失，应补全）：").append(rule2.size()).append('\n');
            for (String s : rule2) {
                b.append("  ").append(s).append('\n');
            }
            b.append("规则3（TMSD 多出，交用户裁决）：").append(rule3.size()).append('\n');
            for (String s : rule3) {
                b.append("  ").append(s).append('\n');
            }
            return b.toString();
        }
    }

    private Comparison compare(Path oldRoot, Path tmsdRoot) throws IOException {
        Set<String> oldFiles = txtFiles(oldRoot);
        Set<String> tmsdFiles = txtFiles(tmsdRoot);
        List<String> rule1 = new ArrayList<>();
        List<String> rule2 = new ArrayList<>();
        List<String> rule3 = new ArrayList<>();

        for (String rel : oldFiles) {
            if (!tmsdFiles.contains(rel)) {
                rule2.add("文件缺失: " + rel);
            }
        }
        for (String rel : tmsdFiles) {
            if (!oldFiles.contains(rel)) {
                rule3.add("TMSD 多出文件: " + rel);
            }
        }

        List<String> both = new ArrayList<>();
        for (String rel : oldFiles) {
            if (tmsdFiles.contains(rel)) {
                both.add(rel);
            }
        }
        both.sort(Comparator.naturalOrder());

        for (String rel : both) {
            String oldText = Files.readString(oldRoot.resolve(rel), GBK);
            String tmsdText = Files.readString(tmsdRoot.resolve(rel), GBK);
            Map<String, String> oldMap = keyedLines(oldText);
            Map<String, String> tmsdMap = keyedLines(tmsdText);
            for (Map.Entry<String, String> e : oldMap.entrySet()) {
                String key = e.getKey();
                if (!tmsdMap.containsKey(key)) {
                    if (!isExcluded(key)) {
                        rule2.add(rel + " :: 缺少键 " + key + "（老值=" + e.getValue() + "）");
                    }
                } else if (!e.getValue().equals(tmsdMap.get(key)) && !isExcluded(key)) {
                    rule1.add(rel + " :: " + key + " 老[" + e.getValue() + "] vs TMSD[" + tmsdMap.get(key) + "]");
                }
            }
            for (String key : tmsdMap.keySet()) {
                if (!oldMap.containsKey(key) && !isExcluded(key)) {
                    rule3.add(rel + " :: TMSD 多出键 " + key + "（值=" + tmsdMap.get(key) + "）");
                }
            }
            List<String> oldLogic = logicLines(oldText);
            List<String> tmsdLogic = logicLines(tmsdText);
            if (!oldLogic.equals(tmsdLogic)) {
                rule1.add(rel + " :: 逻辑行不一致\n    老=" + oldLogic + "\n    TMSD=" + tmsdLogic);
            }
        }
        return new Comparison(both.size(), List.copyOf(rule1), List.copyOf(rule2), List.copyOf(rule3));
    }

    /** 值级归一：剥离 {@code /*} 行尾注释与纯注释/空行，保留「键=值」与逻辑行。 */
    private static List<String> normalizedLines(String text) {
        List<String> out = new ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw;
            int c = line.indexOf("/*");
            if (c >= 0) {
                line = line.substring(0, c);
            }
            line = line.strip();
            if (!line.isEmpty()) {
                out.add(line);
            }
        }
        return out;
    }

    private static Map<String, String> keyedLines(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : normalizedLines(text)) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                map.put(line.substring(0, eq).strip(), line);
            }
        }
        return map;
    }

    private static List<String> logicLines(String text) {
        List<String> out = new ArrayList<>();
        for (String line : normalizedLines(text)) {
            if (line.indexOf('=') < 0) {
                out.add(line);
            }
        }
        return out;
    }

    private static boolean isExcluded(String key) {
        return A2_KEY.matcher(key).matches() || A3_KEY.matcher(key).matches() || A5_KEY.matcher(key).matches();
    }

    private static Set<String> txtFiles(Path root) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        if (!Files.isDirectory(root)) {
            return out;
        }
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .forEach(p -> out.add(root.relativize(p).toString().replace('\\', '/')));
        }
        return out;
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        }
    }
}
