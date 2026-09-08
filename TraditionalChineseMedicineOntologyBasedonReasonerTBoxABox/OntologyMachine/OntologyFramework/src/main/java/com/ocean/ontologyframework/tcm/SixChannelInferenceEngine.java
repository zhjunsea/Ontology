package com.ocean.ontologyframework.tcm;

import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.QueryService;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 胡希恕八纲-六经一体辨证引擎
 *
 * 核心原则：
 * 1. "六经来自八纲"——六经 = 病位(表/里/半表半里) × 病性(阳/阴)
 * 2. "但见一证便是，不必悉具"——提纲证匹配为存在量词
 * 3. 三病位独立评估，两个以上并存即为合病
 * 4. 少阴特异性规则前置："脉微细，但欲寐"直判
 * 5. 无权重、无评分、无阈值
 */
public class SixChannelInferenceEngine {

    private static final Logger log = LoggerFactory.getLogger(SixChannelInferenceEngine.class);

    // ==================== 命名空间 ====================
    private static final String TCM_NS = "http://www.tcm-classics.org/tcm#";
    private static final String BZ_NS  = "http://www.tcm-classics.org/bingzheng#";
    private static final String ZZ_NS  = "http://www.tcm-classics.org/zhengzhuangtizheng#";
    private static final String LJ_NS  = "http://www.tcm-classics.org/liujing#";
    private static final String BG_NS  = "http://www.tcm-classics.org/bagang#";
    private static final String MX_NS  = "http://www.tcm-classics.org/maixiang#";

    // 病位 IRI（本体中类或个体？在bagang.owl中病位是类，但代码用个体？实际上要匹配病位类，但症状的indicates指向类，所以使用类IRI即可）
    public static final String BW_BIAO         = BG_NS + "Biao";
    public static final String BW_LI           = BG_NS + "Li";
    public static final String BW_BANBIAOBANLI = BG_NS + "BanBiaoBanLi";

    // 病性（阴阳总纲）IRI（bagang中的类）
    public static final String BX_YANG = BG_NS + "Yang";
    public static final String BX_YIN  = BG_NS + "Yin";

    // 六经实例IRI（liujing.owl中的个体）
    public static final String LJ_TAIYANG    = LJ_NS + "Taiyang_Instance";
    public static final String LJ_SHAOYIN    = LJ_NS + "Shaoyin_Instance";
    public static final String LJ_YANGMING   = LJ_NS + "Yangming_Instance";
    public static final String LJ_TAIYIN     = LJ_NS + "Taiyin_Instance";
    public static final String LJ_SHAOYANG   = LJ_NS + "Shaoyang_Instance";
    public static final String LJ_JUEYIN     = LJ_NS + "Jueyin_Instance";

    // ==================== 六经中文标签 ====================
    private static final Map<String, String> CHANNEL_LABELS = new LinkedHashMap<>();
    static {
        CHANNEL_LABELS.put(LJ_TAIYANG,  "太阳病");
        CHANNEL_LABELS.put(LJ_SHAOYIN,  "少阴病");
        CHANNEL_LABELS.put(LJ_YANGMING, "阳明病");
        CHANNEL_LABELS.put(LJ_TAIYIN,   "太阴病");
        CHANNEL_LABELS.put(LJ_SHAOYANG, "少阳病");
        CHANNEL_LABELS.put(LJ_JUEYIN,   "厥阴病");
    }

    // ==================== 病位中文标签 ====================
    private static final Map<String, String> LOCATION_LABELS = new LinkedHashMap<>();
    static {
        LOCATION_LABELS.put(BW_BIAO, "表");
        LOCATION_LABELS.put(BW_LI, "里");
        LOCATION_LABELS.put(BW_BANBIAOBANLI, "半表半里");
    }

    // ==================== 病位→六经映射 ====================
    // key = 病位IRI + "|" + 病性IRI → 六经IRI
    private static final Map<String, String> COMBINE_MAP = new LinkedHashMap<>();
    static {
        COMBINE_MAP.put(BW_BIAO + "|" + BX_YANG,         LJ_TAIYANG);
        COMBINE_MAP.put(BW_BIAO + "|" + BX_YIN,          LJ_SHAOYIN);
        COMBINE_MAP.put(BW_LI + "|" + BX_YANG,           LJ_YANGMING);
        COMBINE_MAP.put(BW_LI + "|" + BX_YIN,            LJ_TAIYIN);
        COMBINE_MAP.put(BW_BANBIAOBANLI + "|" + BX_YANG, LJ_SHAOYANG);
        COMBINE_MAP.put(BW_BANBIAOBANLI + "|" + BX_YIN,  LJ_JUEYIN);
    }

    // ==================== 字段 ====================
    private final BackendService backendService;
    private final OWLDataFactory df;
    private final OWLReasoner reasoner;
    private final QueryService queryService;

    /** 病位→指示症状集（反向索引：病位IRI → Set<症状IRI>） */
    private Map<String, Set<String>> locationIndex = new HashMap<>();

    /** 病性→指示症状集（反向索引：病性IRI → Set<症状IRI>） */
    private Map<String, Set<String>> natureIndex = new HashMap<>();

    // ==================== 构造 ====================
    public SixChannelInferenceEngine(BackendService backendService) {
        this.backendService = backendService;
        this.df = backendService.getOntologyService().getDataFactory();
        this.reasoner = backendService.getReasonerService().getReasoner();
        this.queryService = new QueryService(backendService);
    }

    // ==================== 索引预计算 ====================

    /**
     * 启动时调用：通过推理器展开病位/病性的指示症状索引。
     * 遍历本体中所有声明了 tcm:indicates_bingwei 和 tcm:indicates_bingxing 的症状个体，
     * 构建反向索引。
     */
    public void buildIndexes() {
        log.info("🔧 开始构建六经辨证索引...");

        // 使用 tcm:symptom_indicates_bagang 代替不存在的属性
        OWLObjectProperty indicatesBagang = df.getOWLObjectProperty(
                IRI.create(TCM_NS + "symptom_indicates_bagang"));

        OWLClass symptomClass = df.getOWLClass(IRI.create(ZZ_NS + "Symptom"));
        Set<OWLNamedIndividual> allSymptoms = reasoner.getInstances(symptomClass, true)
                .entities().collect(Collectors.toSet());

        int locationCount = 0;
        // 不再构建 natureIndex，因为 determineNature 使用动态查询

        for (OWLNamedIndividual symptom : allSymptoms) {
            String symptomIri = symptom.getIRI().toString();

            // 获取 symptom_indicates_bagang 指向的所有八纲要素
            NodeSet<OWLNamedIndividual> elements = reasoner.getObjectPropertyValues(
                    symptom, indicatesBagang);
            for (OWLNamedIndividual elem : elements.entities().collect(Collectors.toList())) {
                String elemIri = elem.getIRI().toString();
                // 只记录病位要素（Biao, Li, BanBiaoBanLi）
                if (elemIri.equals(BW_BIAO) || elemIri.equals(BW_LI)
                        || elemIri.equals(BW_BANBIAOBANLI)) {
                    locationIndex.computeIfAbsent(elemIri, k -> new HashSet<>())
                            .add(symptomIri);
                    locationCount++;
                }
                // 病性要素（Yang, Yin 等）不在此处构建，因 determineNature 用动态查询
            }
        }

        log.info("✅ 六经辨证索引构建完成 | 病位指示关系={}", locationCount);
    }

    // ==================== 主入口 ====================

    /**
     * 八纲-六经一体辨证主方法
     *
     * @param symptomIris 患者症状 IRI 列表（来自第一步症状映射）
     * @param pulseIris   患者脉象 IRI 列表
     * @param tongueIris  患者舌象 IRI 列表
     * @return InferenceResult 完整判定结果
     */
    public InferenceResult infer(List<String> symptomIris,
                                 List<String> pulseIris,
                                 List<String> tongueIris) {
        if (symptomIris == null || symptomIris.isEmpty()) {
            return InferenceResult.indeterminate("症状列表为空，无法辨证");
        }

        // 防御性处理：脉象和舌象可能为 null
        List<String> pulses = pulseIris != null ? pulseIris : Collections.emptyList();
        List<String> tongues = tongueIris != null ? tongueIris : Collections.emptyList();

        Set<String> S = new LinkedHashSet<>(symptomIris);
        log.info("🔍 开始八纲-六经辨证 | 症状数={} | 脉象数={} | 舌象数={}",
                S.size(), pulses.size(), tongues.size());

        // ===== 第0步：少阴特异性短路检查 =====
        String shaoYinReason = checkShaoYinSpecificRule(S);
        if (shaoYinReason != null) {
            log.info("⚡ 少阴特异性规则命中：{}", shaoYinReason);
            return InferenceResult.shaoYinDirect(shaoYinReason);
        }

        // ===== 第1步：三病位独立平行扫描 =====
        List<LocationHit> hits = determineLocations(S);

        // ===== 第1.5步（新增）：脉象佐证病位 =====
        if (!pulses.isEmpty()) {
            log.info("🩺 脉象佐证 | 患者脉象={}", pulses);
            // 如果提纲命中了多个病位，脉象可作为消歧依据
            // 例如：脉浮 → 支持太阳；脉沉 → 支持少阴
            if (hits.size() > 1) {
                List<LocationHit> pulseFiltered = disambiguateByPulse(hits, pulses);
                if (!pulseFiltered.isEmpty()) {
                    hits = pulseFiltered;
                    log.info("🩺 脉象消歧后病位数={} | 剩余={}", hits.size(),
                            hits.stream().map(h -> h.locationIri)
                                    .collect(Collectors.toList()));
                }
            }
        }

        // ===== 第1.6步（新增）：舌象佐证病位 =====
        if (!tongues.isEmpty()) {
            log.info("👅 舌象佐证 | 患者舌象={}", tongues);
            if (hits.size() > 1) {
                List<LocationHit> tongueFiltered = disambiguateByTongue(hits, tongues);
                if (!tongueFiltered.isEmpty()) {
                    hits = tongueFiltered;
                    log.info("👅 舌象消歧后病位数={} | 剩余={}", hits.size(),
                            hits.stream().map(h -> h.locationIri)
                                    .collect(Collectors.toList()));
                }
            }
        }

        // ===== 第2步：检查命中结果 =====
        String confidence = "high";
        if (hits.isEmpty()) {
            // 退化路径：指征推断
            hits = inferByIndicators(S);
            confidence = "low";
            if (hits.isEmpty()) {
                log.warn("⚠️ 三提纲均未命中且指征推断无结果，无法辨证");
                return InferenceResult.indeterminate(
                        "三病位提纲均未命中，且症状无 tcm:indicates_bingwei 指示，"
                                + "建议补充问诊");
            }
            log.info("⚠️ 提纲未命中，退化为指征推断（置信度=low）");
        }

        // ===== 第3步：病性判定（症状 + 脉象协同） =====
        NatureResult natureResult = determineNature(S);
        String nature = natureResult.natureLabel;
        String natureIri = natureResult.natureIri;
        boolean natureFromPulse = false; // 标记病性是否由脉象补充

        // 脉象佐证/补充病性
        if (!pulses.isEmpty()) {
            String pulseNatureHint = inferNatureFromPulse(pulses);
            if (pulseNatureHint != null) {
                if ("未定".equals(nature)) {
                    // ✅ 症状病性未定 → 以脉象判定为主
                    log.info("🩺 症状病性未定，采用脉象判定病性={}", pulseNatureHint);
                    nature = pulseNatureHint;
                    natureIri = resolveNatureIri(pulseNatureHint);
                    natureFromPulse = true;
                } else if (!isNatureCompatible(pulseNatureHint, nature)) {
                    // ⚠️ 症状已定但与脉象语义冲突 → 以症状为主，脉象作参考
                    log.warn("⚠️ 脉象提示病性={} 与症状判定病性={} 语义冲突，"
                            + "以症状为主，脉象作参考", pulseNatureHint, nature);
                }
                // 若语义兼容（如"阳"与"热"），无需额外处理
            }
        }

        // ===== 第4步：病位 × 病性 → 六经组合 =====
        List<String> channels = new ArrayList<>();
        List<String> channelLabels = new ArrayList<>();
        for (LocationHit hit : hits) {
            String channelIri = combine(hit.locationIri, natureIri);
            if (channelIri != null) {
                channels.add(channelIri);
                channelLabels.add(CHANNEL_LABELS.getOrDefault(channelIri, channelIri));
            }
        }

        if (channels.isEmpty()) {
            return InferenceResult.indeterminate("病性未定，无法组合六经");
        }

        // ===== 第5步：构建结果 =====
        InferenceResult result = new InferenceResult();
        result.primaryChannel = channels.get(0);
        result.primaryChannelLabel = channelLabels.get(0);
        result.location = LOCATION_LABELS.getOrDefault(
                hits.get(0).locationIri, hits.get(0).locationIri);
        result.locationIri = hits.get(0).locationIri;
        result.nature = nature;
        result.natureIri = natureIri;
        result.baGangLabel = result.location + nature + "证";
        result.isHeBing = channels.size() > 1;
        result.heBingChannels = channels;
        result.heBingChannelLabels = channelLabels;
        result.confidence = confidence;
        result.isShaoYinDirect = false;
        result.indeterminate = false;

        // 依据链
        result.rationale.add("【病位判定】"
                + (confidence.equals("high") ? "提纲证直接命中"
                : "指征推断（退化）"));
        for (LocationHit hit : hits) {
            result.rationale.add("  病位="
                    + LOCATION_LABELS.getOrDefault(
                    hit.locationIri, hit.locationIri)
                    + " | 命中症状=" + hit.matchedSymptoms);
        }

        // 【病性判定】依据链（根据来源输出不同说明）
        if (natureFromPulse) {
            result.rationale.add("【病性判定】" + nature
                    + " | 来源=脉象推断（症状未定，以脉定性）"
                    + " | 脉象=" + String.join(", ", pulses));
        } else {
            result.rationale.add("【病性判定】" + nature
                    + " | 来源=症状判定"
                    + " | 依据=" + natureResult.matchedSymptoms);
            // 脉象不一致时追加参考记录
            String pulseNatureHint = inferNatureFromPulse(pulses);
            if (pulseNatureHint != null
                    && !isNatureCompatible(pulseNatureHint, nature)) {
                result.rationale.add("  ⚠️ 脉象提示" + pulseNatureHint
                        + "，与症状判定不一致，仅作参考");
            }
        }

        // 脉象佐证记录
        if (!pulses.isEmpty()) {
            result.rationale.add("【脉象佐证】" + String.join(", ", pulses));
        }
        // 舌象佐证记录
        if (!tongues.isEmpty()) {
            result.rationale.add("【舌象佐证】" + String.join(", ", tongues));
        }

        result.rationale.add("【六经组合】" + String.join(" + ", channelLabels)
                + (result.isHeBing ? " → 合病" : " → 单经"));

        log.info("✅ 辨证完成 | 六经={} | 合病={} | 置信度={}",
                result.primaryChannelLabel, result.isHeBing, confidence);

        return result;
    }

    // ==================== 辅助方法 ====================

    /**
     * 将中文病性标签解析为八纲IRI
     *
     * @param natureLabel 病性中文标签（热/寒/实/虚/阳/阴）
     * @return 对应的八纲IRI，未知标签返回 null
     */
    private String resolveNatureIri(String natureLabel) {
        switch (natureLabel) {
            case "热":  return BG_NS + "Re";
            case "寒":  return BG_NS + "Han";
            case "实":  return BG_NS + "Shi";
            case "虚":  return BG_NS + "Xu";
            case "阳":  return BG_NS + "Yang";
            case "阴":  return BG_NS + "Yin";
            default:    return null;
        }
    }

    /**
     * 判断两个病性标签是否语义兼容
     * 阳组：阳、热、实（偏亢进方向）
     * 阴组：阴、寒、虚（偏衰退方向）
     * 同组内不视为冲突，只有跨组对立（如寒vs热、虚vs实）才触发警告
     *
     * @param a 病性标签A
     * @param b 病性标签B
     * @return true 如果语义兼容（不冲突），false 如果语义冲突
     */
    private boolean isNatureCompatible(String a, String b) {
        if (a.equals(b)) return true;
        Set<String> yangGroup = new HashSet<>(
                Arrays.asList("阳", "热", "实"));
        Set<String> yinGroup = new HashSet<>(
                Arrays.asList("阴", "寒", "虚"));
        boolean aInYang = yangGroup.contains(a);
        boolean aInYin = yinGroup.contains(a);
        boolean bInYang = yangGroup.contains(b);
        boolean bInYin = yinGroup.contains(b);
        return (aInYang && bInYang) || (aInYin && bInYin);
    }

    // ==================== 脉象消歧 ====================

    /**
     * 脉象消歧：当多个病位命中时，用脉象缩小范围
     * 例如：脉浮 → 偏表（太阳）；脉沉 → 偏里（少阴/太阴）
     */
    private List<LocationHit> disambiguateByPulse(
            List<LocationHit> hits, List<String> pulses) {
        // 检查是否包含"浮脉"相关IRI
        boolean hasFloating = pulses.stream().anyMatch(p ->
                p.contains("Pulse_Fu") || p.contains("floating"));
        // 检查是否包含"沉脉"相关IRI
        boolean hasSinking = pulses.stream().anyMatch(p ->
                p.contains("Pulse_Chen") || p.contains("sinking"));

        if (hasFloating && !hasSinking) {
            // 脉浮 → 保留表证病位（太阳、少阳）
            return hits.stream()
                    .filter(h -> h.locationIri.contains("Biao")
                            || h.locationIri.contains("Taiyang")
                            || h.locationIri.contains("Shaoyang")
                            || h.locationIri.contains("Yangming"))
                    .collect(Collectors.toList());
        }
        if (hasSinking && !hasFloating) {
            // 脉沉 → 保留里证病位（太阴、少阴、厥阴）
            return hits.stream()
                    .filter(h -> h.locationIri.contains("Li")
                            || h.locationIri.contains("Taiyin")
                            || h.locationIri.contains("Shaoyin")
                            || h.locationIri.contains("Jueyin"))
                    .collect(Collectors.toList());
        }
        // 无法消歧，返回原列表
        return hits;
    }

    // ==================== 舌象消歧 ====================

    /**
     * 舌象消歧：当多个病位命中时，用舌象缩小范围
     * 例如：舌红苔黄 → 偏热（阳明/少阳）；舌淡苔白 → 偏寒（太阴/少阴）
     */
    private List<LocationHit> disambiguateByTongue(
            List<LocationHit> hits, List<String> tongues) {
        boolean hasHeat = tongues.stream().anyMatch(t ->
                t.contains("HongShe") || t.contains("HuangTai")
                        || t.contains("Red") || t.contains("Yellow"));
        boolean hasCold = tongues.stream().anyMatch(t ->
                t.contains("DanShe") || t.contains("BaiTai")
                        || t.contains("Pale") || t.contains("White"));

        if (hasHeat && !hasCold) {
            return hits.stream()
                    .filter(h -> h.locationIri.contains("Yangming")
                            || h.locationIri.contains("Shaoyang")
                            || h.locationIri.contains("Re"))
                    .collect(Collectors.toList());
        }
        if (hasCold && !hasHeat) {
            return hits.stream()
                    .filter(h -> h.locationIri.contains("Taiyin")
                            || h.locationIri.contains("Shaoyin")
                            || h.locationIri.contains("Han"))
                    .collect(Collectors.toList());
        }
        return hits;
    }

    // ==================== 从脉象推断病性 ====================

    /**
     * 从脉象推断病性倾向（动态查询本体）
     * 通过 tcm:symptom_indicates_bagang 属性读取脉象的八纲归属
     *
     * @param pulseIris 脉象 IRI 列表
     * @return 病性中文标签（热/寒/实/虚），无法判断返回 null
     */
    private String inferNatureFromPulse(List<String> pulseIris) {
        if (pulseIris == null || pulseIris.isEmpty()) {
            return null;
        }

        String indicatesBagangPropIri = TCM_NS + "symptom_indicates_bagang";

        // 收集所有脉象指向的八纲元素
        Set<String> bagangValues = new LinkedHashSet<>();
        for (String pulseIri : pulseIris) {
            List<String> values = queryService.queryPropertyValueInOntology(
                    pulseIri, indicatesBagangPropIri);
            bagangValues.addAll(values);
        }

        if (bagangValues.isEmpty()) {
            log.warn("脉象未找到八纲映射: {}", pulseIris);
            return null;
        }

        // 根据八纲元素判断病性
        // bg:Re → 热；bg:Han → 寒；bg:Shi → 实；bg:Xu → 虚
        String reIri = BG_NS + "Re";
        String hanIri = BG_NS + "Han";
        String shiIri = BG_NS + "Shi";
        String xuIri = BG_NS + "Xu";

        boolean hasRe = bagangValues.contains(reIri);
        boolean hasHan = bagangValues.contains(hanIri);
        boolean hasShi = bagangValues.contains(shiIri);
        boolean hasXu = bagangValues.contains(xuIri);

        log.debug("脉象八纲映射结果: {} -> {}", pulseIris, bagangValues);

        // 优先级：热 > 寒 > 实 > 虚（与中医辨证习惯一致）
        if (hasRe) return "热";
        if (hasHan) return "寒";
        if (hasShi) return "实";
        if (hasXu) return "虚";

        return null;
    }

    // ==================== 第0步：少阴特异性短路 ====================

    /**
     * 少阴特异性规则检查
     *
     * 胡希恕原话："脉微细，但欲寐，此为少阴确证，无论兼有何证，皆从少阴论治。"
     *
     * 条件：S 中同时包含 (脉微 ∨ 脉细) 且包含 但欲寐
     * 安全意义：防止少阴恶寒被误判为太阳而误用汗法→亡阳
     *
     * @param S 患者症状IRI集合
     * @return 命中时返回依据文本；未命中返回 null
     */
    private String checkShaoYinSpecificRule(Set<String> S) {
        // 脉微、脉细（来自 maixiang.owl）
        String maiWei = MX_NS + "Pulse_Wei";
        String maiXi = MX_NS + "Pulse_Xi";
        // 但欲寐（来自 zhengzhuangtizheng.owl）
        String danYuMei = ZZ_NS + "DanYuMei";

        boolean hasPulseMicro = S.contains(maiWei) || S.contains(maiXi);
        boolean hasDrowsiness = S.contains(danYuMei);

        if (hasPulseMicro && hasDrowsiness) {
            List<String> matched = new ArrayList<>();
            if (S.contains(maiWei)) matched.add("脉微");
            if (S.contains(maiXi)) matched.add("脉细");
            matched.add("但欲寐");
            return "少阴提纲直判：" + String.join("+", matched)
                    + " → 少阴病（表阴证）。依据：胡希恕"
                    + "'脉微细但欲寐，无论兼有何证，皆从少阴论治'";
        }
        return null;
    }

    // ==================== 第1步：三病位独立平行扫描 ====================

    /**
     * 病位判定：通过本体 tcm:symptom_indicates_bagang 属性动态查询
     * 三提纲独立评估，存在量词（∃），但见一证便是。
     * 三者完全独立，可同时成立（→合病基础）。
     *
     * @param S 患者症状IRI集合
     * @return 所有成立的病位命中列表（可能0~3个）
     */
    private List<LocationHit> determineLocations(Set<String> S) {
        if (S == null || S.isEmpty()) {
            return Collections.emptyList();
        }

        String indicatesBagangPropIri = TCM_NS + "symptom_indicates_bagang";

        // 三个病位的八纲IRI
        String biaoIri = BG_NS + "Biao";
        String liIri = BG_NS + "Li";
        String banBiaoBanLiIri = BG_NS + "BanBiaoBanLi";

        // 按病位分组收集命中的症状IRI
        Set<String> biaoMatched = new LinkedHashSet<>();
        Set<String> liMatched = new LinkedHashSet<>();
        Set<String> banBiaoBanLiMatched = new LinkedHashSet<>();

        for (String symptomIri : S) {
            List<String> bagangValues = queryService.queryPropertyValueInOntology(
                    symptomIri, indicatesBagangPropIri);

            for (String valIri : bagangValues) {
                if (valIri.equals(biaoIri)) {
                    biaoMatched.add(symptomIri);
                } else if (valIri.equals(liIri)) {
                    liMatched.add(symptomIri);
                } else if (valIri.equals(banBiaoBanLiIri)) {
                    banBiaoBanLiMatched.add(symptomIri);
                }
            }
        }

        List<LocationHit> hits = new ArrayList<>();
        if (!biaoMatched.isEmpty()) {
            hits.add(new LocationHit(BW_BIAO, biaoMatched));
        }
        if (!liMatched.isEmpty()) {
            hits.add(new LocationHit(BW_LI, liMatched));
        }
        if (!banBiaoBanLiMatched.isEmpty()) {
            hits.add(new LocationHit(BW_BANBIAOBANLI, banBiaoBanLiMatched));
        }

        log.debug("病位判定结果: 表={} 里={} 半表半里={}",
                biaoMatched.size(), liMatched.size(),
                banBiaoBanLiMatched.size());

        return hits;
    }

    /**
     * 病位命中记录
     */
    private static class LocationHit {
        final String locationIri;
        final Set<String> matchedSymptoms;

        LocationHit(String locationIri, Set<String> matchedSymptoms) {
            this.locationIri = locationIri;
            this.matchedSymptoms = matchedSymptoms;
        }
    }

    // ==================== 第3步：病性判定（阳证优先） ====================

    /**
     * 病性判定：阳证指征优先。
     *
     * 胡希恕原则：阳证显性易辨，阴证需排除阳证后方可确认。
     * 即：先查阳证指征，命中→阳；未命中再查阴证指征，命中→阴；均未命中→未定。
     *
     * @param S 患者症状集合
     * @return NatureResult 包含病性、病性IRI、命中症状
     */
    private NatureResult determineNature(Set<String> S) {
        Set<String> yangMatched = new LinkedHashSet<>();
        Set<String> yinMatched = new LinkedHashSet<>();

        // 八纲元素IRI
        String yangIri = BG_NS + "Yang";
        String yinIri = BG_NS + "Yin";
        // 属性IRI
        String indicatesBagangPropIri = TCM_NS + "symptom_indicates_bagang";

        for (String symptomIri : S) {
            // 复用 queryService 的方法查询该症状的所有八纲指向值
            List<String> bagangValues = queryService.queryPropertyValueInOntology(
                    symptomIri, indicatesBagangPropIri);

            for (String valIri : bagangValues) {
                if (valIri.equals(yangIri)) {
                    yangMatched.add(symptomIri);
                } else if (valIri.equals(yinIri)) {
                    yinMatched.add(symptomIri);
                }
            }
        }

        if (!yangMatched.isEmpty()) {
            return new NatureResult("阳", BX_YANG, yangMatched);
        }
        if (!yinMatched.isEmpty()) {
            return new NatureResult("阴", BX_YIN, yinMatched);
        }

        return new NatureResult("未定", null, Collections.emptySet());
    }

    /**
     * 病性判定结果
     */
    private static class NatureResult {
        final String natureLabel;
        final String natureIri;
        final Set<String> matchedSymptoms;

        NatureResult(String natureLabel, String natureIri,
                     Set<String> matchedSymptoms) {
            this.natureLabel = natureLabel;
            this.natureIri = natureIri;
            this.matchedSymptoms = matchedSymptoms;
        }
    }

    // ==================== 第4步：病位 × 病性 → 六经 ====================

    /**
     * 组合病位与病性，得到六经 IRI。
     *
     * @param locationIri 病位 IRI（BW_BIAO / BW_LI / BW_BANBIAOBANLI）
     * @param natureIri   病性 IRI（BX_YANG / BX_YIN）
     * @return 六经 IRI，若无法组合返回 null
     */
    private String combine(String locationIri, String natureIri) {
        if (locationIri == null || natureIri == null) {
            return null;
        }
        return COMBINE_MAP.get(locationIri + "|" + natureIri);
    }

    // ==================== 退化路径：指征推断 ====================

    /**
     * 当三提纲均未命中时，退化为指征推断。
     * 遍历患者症状，查询每个症状的 tcm:indicates_bingwei 属性，
     * 统计各病位得票数，取票数最多者作为候选病位。
     *
     * @param S 患者症状集合
     * @return 候选病位命中列表（可能为空）
     */
    private List<LocationHit> inferByIndicators(Set<String> S) {
        Map<String, Set<String>> voteMap = new LinkedHashMap<>();
        voteMap.put(BW_BIAO, new LinkedHashSet<>());
        voteMap.put(BW_LI, new LinkedHashSet<>());
        voteMap.put(BW_BANBIAOBANLI, new LinkedHashSet<>());

        OWLObjectProperty indicatesBagang = df.getOWLObjectProperty(
                IRI.create(TCM_NS + "symptom_indicates_bagang"));

        for (String symptomIri : S) {
            OWLNamedIndividual symptomInd = df.getOWLNamedIndividual(
                    IRI.create(symptomIri));
            NodeSet<OWLNamedIndividual> elements = reasoner
                    .getObjectPropertyValues(symptomInd, indicatesBagang);
            for (OWLNamedIndividual elem : elements.entities()
                    .collect(Collectors.toList())) {
                String locIri = elem.getIRI().toString();
                if (voteMap.containsKey(locIri)) {
                    voteMap.get(locIri).add(symptomIri);
                }
            }
        }

        List<LocationHit> hits = new ArrayList<>();
        int maxVotes = 0;
        for (Map.Entry<String, Set<String>> entry : voteMap.entrySet()) {
            if (entry.getValue().size() > maxVotes) {
                maxVotes = entry.getValue().size();
            }
        }
        if (maxVotes == 0) return hits;

        for (Map.Entry<String, Set<String>> entry : voteMap.entrySet()) {
            if (entry.getValue().size() == maxVotes) {
                hits.add(new LocationHit(entry.getKey(), entry.getValue()));
            }
        }
        return hits;
    }
}