package com.ocean.ontologyframework;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 经方 IPF 推理引擎 (Java 版)
 * 使用 auto_tune.py 调优后的最优参数:
 *   weight_cap = 8.0
 *   norm_alpha = 0.65
 *   boost_types: mx:1.4, sx:1.4
 *
 * 排序策略：匹配率优先（命中特征数/总特征数），匹配率相同则按置信度
 */
public class JingFangInference {

    private static final double WEIGHT_CAP = 8.0;
    private static final double NORM_ALPHA = 0.65;
    private static final Map<String, Double> BOOST_TYPES = new HashMap<>();
    static {
        BOOST_TYPES.put("mx", 1.4);
        BOOST_TYPES.put("sx", 1.4);
    }

    private Map<String, PatternInfo> patternInfoMap = new HashMap<>();
    private Map<String, String> patternNameMap = new HashMap<>();
    private Map<String, String> patternLiujingMap = new HashMap<>();

    private static class PatternInfo {
        String patternId;
        Map<String, Double> featureWeights;
        double maxPossible;
        int totalFeatures;

        PatternInfo(String id) {
            this.patternId = id;
            this.featureWeights = new HashMap<>();
            this.maxPossible = 0.0;
            this.totalFeatures = 0;
        }

        void addFeature(String feature, double weight) {
            double effective = getEffectiveWeight(feature, weight);
            if (effective > 0.001) {
                featureWeights.put(feature, effective);
                maxPossible += effective;
            }
            totalFeatures++;
        }

        private double getEffectiveWeight(String feature, double rawWeight) {
            double w = rawWeight;
            for (Map.Entry<String, Double> entry : BOOST_TYPES.entrySet()) {
                if (feature.startsWith(entry.getKey() + ":")) {
                    w *= entry.getValue();
                    break;
                }
            }
            return Math.min(w, WEIGHT_CAP);
        }
    }

    public static class Result {
        public String patternId;
        public String patternName;
        public String liujing;
        public double confidence;
        public double adjustedConfidence;
        public double totalScore;
        public double matchRate;
        public int matchedFeatures;
        public int totalFeatures;

        public Result(String id, String name, String liujing, double conf, double score,
                      double rate, int matched, int total) {
            this.patternId = id;
            this.patternName = name;
            this.liujing = liujing;
            this.confidence = conf;
            this.totalScore = score;
            this.matchRate = rate;
            this.matchedFeatures = matched;
            this.totalFeatures = total;
            this.adjustedConfidence = conf;
        }

        @Override
        public String toString() {
            return String.format("%s (%s) 得分:%.4f 匹配:%d/%d 匹配率:%.1f%%",
                    patternName, patternId, adjustedConfidence, matchedFeatures, totalFeatures, matchRate*100);
        }
    }

    public void loadWeightMatrixFromResource() throws IOException {
        InputStream is = getClass().getResourceAsStream("/ipf_pattern_weight_matrix.csv");
        if (is == null) {
            throw new IOException("文件未找到: /ipf_pattern_weight_matrix.csv");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            parseLines(lines);
        }
    }

    private void parseLines(List<String> lines) {
        if (lines.isEmpty()) return;
        String[] header = lines.get(0).split(",");
        List<String> featureNames = new ArrayList<>();
        for (int i = 5; i < header.length; i++) {
            featureNames.add(header[i].trim());
        }

        for (int rowIdx = 1; rowIdx < lines.size(); rowIdx++) {
            String line = lines.get(rowIdx);
            String[] cells = line.split(",");
            if (cells.length < 6) continue;

            String patternId = cells[0].trim();
            String patternName = cells[1].trim();
            String liujing = cells[2].trim();
            int totalFeatures = Integer.parseInt(cells[4].trim());

            PatternInfo info = new PatternInfo(patternId);
            for (int i = 0; i < featureNames.size(); i++) {
                int colIdx = i + 5;
                if (colIdx >= cells.length) break;
                String val = cells[colIdx].trim();
                if (val.isEmpty()) continue;
                double weight = Double.parseDouble(val);
                if (Math.abs(weight) > 0.0001) {
                    info.addFeature(featureNames.get(i), weight);
                }
            }
            info.totalFeatures = totalFeatures;
            patternInfoMap.put(patternId, info);
            patternNameMap.put(patternId, patternName);
            patternLiujingMap.put(patternId, liujing);
        }
        System.out.println("加载完成: " + patternInfoMap.size() + " 个方证");
    }

    public List<Result> infer(List<String> patientFeatures, int topN) {
        Set<String> patientSet = new HashSet<>(patientFeatures);
        List<Result> rawResults = new ArrayList<>();

        for (Map.Entry<String, PatternInfo> entry : patternInfoMap.entrySet()) {
            String patternId = entry.getKey();
            PatternInfo info = entry.getValue();

            long matchedCount = info.featureWeights.keySet().stream()
                    .filter(patientSet::contains)
                    .count();
            if (matchedCount == 0) continue;

            double matchRate = (double) matchedCount / info.totalFeatures;

            double totalScore = 0.0;
            for (String feat : patientSet) {
                Double w = info.featureWeights.get(feat);
                if (w != null) totalScore += w;
            }
            if (info.maxPossible == 0) continue;

            double confidence = totalScore / Math.pow(info.maxPossible, NORM_ALPHA);

            rawResults.add(new Result(
                    patternId,
                    patternNameMap.getOrDefault(patternId, patternId),
                    patternLiujingMap.getOrDefault(patternId, ""),
                    confidence,
                    totalScore,
                    matchRate,
                    (int) matchedCount,
                    info.totalFeatures
            ));
        }

        // 不采用加分规则，完全依靠匹配率排序
        // 排序：匹配率优先，相同则按置信度
        rawResults.sort((a, b) -> {
            int cmp = Double.compare(b.matchRate, a.matchRate);
            if (cmp != 0) return cmp;
            return Double.compare(b.confidence, a.confidence);
        });

        return rawResults.stream().limit(topN).collect(Collectors.toList());
    }

    // ==================== 测试用例 ====================

    public static void main(String[] args) throws IOException {
        JingFangInference engine = new JingFangInference();

        try {
            engine.loadWeightMatrixFromResource();
        } catch (IOException e) {
            System.err.println("加载权重文件失败: " + e.getMessage());
            System.err.println("请将 ipf_pattern_weight_matrix.csv 放入 src/main/resources/ 目录");
            return;
        }

        // 定义 17 个真实医案测试用例
        List<TestCase> testCases = new ArrayList<>();

        // 1. 桂枝汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:EHan", "zz:FaRe", "zz:HanChu", "zz:EFeng",
                        "zz:TouXiangQiangTong", "zz:BiMing", "zz:GanOu", "mx:Pulse_Huan",
                        "sx:PaleRedTongue", "sx:ThinWhiteCoating"),
                "fj:GuiZhiTang", "桂枝汤证"
        ));
        // 2. 麻黄汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:EHan", "zz:FaRe", "zz:WuHan", "zz:ShenTiTengTong",
                        "zz:TouXiangQiangTong", "zz:KeNi", "mx:Pulse_Jin",
                        "sx:PaleRedTongue", "sx:ThinWhiteCoating"),
                "fj:MaHuangTang", "麻黄汤证"
        ));
        // 3. 葛根汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:EHan", "zz:WuHan", "zz:TouXiangQiangTong",
                        "zz:DaKe", "mx:Pulse_Fu_Jin"),
                "fj:GeGenTang", "葛根汤证"
        ));
        // 4. 大青龙汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:FaRe", "zz:EHan", "zz:WuHan", "zz:ShenTiTengTong",
                        "zz:FanZao", "mx:Pulse_Fu_Jin",
                        "sx:RedTongue", "sx:YellowCoating"),
                "fj:DaQingLongTang", "大青龙汤证"
        ));
        // 5. 小青龙汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:GanOu", "zz:FaRe", "zz:KeNi", "zz:XiaoBianBuLi",
                        "zz:KeNi", "jj:WaterRetention",
                        "sx:PaleRedTongue", "sx:WhiteSlipperyCoating"),
                "fj:XiaoQingLongTang", "小青龙汤证"
        ));
        // 6. 五苓散证
        testCases.add(new TestCase(
                Arrays.asList("zz:XiaoBianBuLi", "zz:KeErYuYin", "zz:FaRe",
                        "mx:Pulse_Fu", "jj:WaterRetention",
                        "sx:PaleRedTongue", "sx:WhiteGreasyCoating"),
                "fj:WuLingSan", "五苓散证"
        ));
        // 7. 白虎汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:ShenRe", "zz:DaKe", "zz:HanChu",
                        "zz:BuEhanFanEre", "mx:Pulse_Hong",
                        "sx:RedTongue", "sx:YellowDryCoating"),
                "fj:BaiHuTang", "白虎汤证"
        ));
        // 8. 大承气汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:ChaoRe", "zz:ZhanYu", "zz:FuManJuAn",
                        "zz:DaBianNan", "zz:ShouZuJiRanHanChu",
                        "mx:Pulse_Chi", "jj:FoodStagnation",
                        "sx:RedTongue", "sx:YellowDryCoating", "sx:PrickledTongue"),
                "fj:DaChengQiTang", "大承气汤证"
        ));
        // 9. 茵陈蒿汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:HuangDan", "zz:XiaoBianBuLi", "zz:FuMan",
                        "jj:Re",
                        "sx:RedTongue", "sx:YellowGreasyCoating"),
                "fj:YinChenHaoTang", "茵陈蒿汤证"
        ));
        // 10. 小柴胡汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:WangLaiHanRe", "zz:XiongXieKuMan",
                        "zz:MomoBuYuYinShi", "zz:XinFanXiOu",
                        "zz:KouKu", "zz:YanGan", "zz:MuXuan",
                        "mx:Pulse_Xian",
                        "sx:RedTongue", "sx:YellowCoating"),
                "fj:XiaoChaiHuTang", "小柴胡汤证"
        ));
        // 11. 大柴胡汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:GanOu", "zz:XinXiaPi", "zz:FuManJuAn",
                        "zz:ChaoRe", "jj:QiStagnation",
                        "sx:RedTongue", "sx:YellowDryCoating"),
                "fj:DaChaiHuTang", "大柴胡汤证"
        ));
        // 12. 理中汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:FuManErTu", "zz:ShiBuXia", "zz:ZiLi",
                        "zz:ShiFuZiTong", "zz:KeErBuYuYin",
                        "mx:Pulse_Chen_Chi", "sx:SheShangTaiHua",
                        "jj:Han",
                        "sx:PaleWhiteTongue", "sx:WhiteSlipperyCoating"),
                "fj:LiZhongTang", "理中汤证"
        ));
        // 13. 小建中汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:FuTong", "zz:XuLaoLiJi", "zz:XinJi",
                        "zz:YiJing", "zz:ShenTiTengTong",
                        "zz:ShouZuFanRe", "zz:YanGan",
                        "mx:Pulse_Xu_Chi",
                        "sx:PaleRedTongue", "sx:ThinWhiteCoating"),
                "fj:XiaoJianZhongTang", "小建中汤证"
        ));
        // 14. 四逆汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:SiZhiJueNi", "zz:XiaLiQingGu", "zz:DanYuMei",
                        "mx:Pulse_Wei", "mx:Pulse_Chen",
                        "sx:PaleWhiteTongue", "sx:WhiteSlipperyCoating"),
                "fj:SiNiTang", "四逆汤证"
        ));
        // 15. 黄连阿胶汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:XinFan", "zz:BuDeWo",
                        "mx:Pulse_Xi_Shu", "sx:SheJiang",
                        "sx:RedTongue", "sx:ThinDryCoating"),
                "fj:HuangLianEJiaoTang", "黄连阿胶汤证"
        ));
        // 16. 真武汤证
        testCases.add(new TestCase(
                Arrays.asList("zz:FuTong", "zz:XiaoBianBuLi",
                        "zz:SiZhiChenZhong", "zz:ZiXiaLi",
                        "sx:PaleWhiteTongue", "sx:WhiteSlipperyCoating"),
                "fj:ZhenWuTang", "真武汤证"
        ));
        // 17. 乌梅丸证
        testCases.add(new TestCase(
                Arrays.asList("zz:XiaoKe", "zz:QiShangZhuangXin",
                        "zz:XinZhongAoNong", "zz:JiErBuYuShi",
                        "zz:ShiZeTuHui", "mx:Pulse_Xian",
                        "sx:RedTongue", "sx:YellowWhiteCoating"),
                "fj:WuMeiWan", "乌梅丸证"
        ));

        System.out.println("=".repeat(70));
        System.out.println("经方推理引擎测试（真实医案）");
        System.out.println("参数: weight_cap=" + WEIGHT_CAP + ", norm_alpha=" + NORM_ALPHA + ", boost=1.4");
        System.out.println("排序: 匹配率优先");
        System.out.println("=".repeat(70));

        int passed = 0;
        for (int i = 0; i < testCases.size(); i++) {
            TestCase tc = testCases.get(i);
            List<Result> results = engine.infer(tc.symptoms, 5);

            boolean found = results.stream().anyMatch(r -> r.patternId.equals(tc.expectedId));
            boolean correctRank = found && results.get(0).patternId.equals(tc.expectedId);

            System.out.printf("\n测试用例 %d: %s (期望: %s)\n", i+1, tc.name, tc.expectedId);
            if (results.isEmpty()) {
                System.out.println("  无结果");
            } else {
                for (int j = 0; j < results.size(); j++) {
                    Result r = results.get(j);
                    String mark = r.patternId.equals(tc.expectedId) ? "✓" : " ";
                    System.out.printf("  %d. %s %s\n", j+1, mark, r);
                }
            }

            if (correctRank) {
                System.out.println("  ✅ 期望方证排名第一");
                passed++;
            } else if (found) {
                System.out.println("  ⚠️ 期望方证进入前5但未排第一");
            } else {
                System.out.println("  ❌ 期望方证未进入前5");
            }
        }

        System.out.println("\n" + "=".repeat(70));
        System.out.printf("通过率: %d / %d (%.1f%%)\n", passed, testCases.size(), passed*100.0/testCases.size());
        System.out.println("=".repeat(70));
    }

    private static class TestCase {
        List<String> symptoms;
        String expectedId;
        String name;
        TestCase(List<String> sym, String id, String name) {
            this.symptoms = sym;
            this.expectedId = id;
            this.name = name;
        }
    }
}