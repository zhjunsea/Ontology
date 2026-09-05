package com.ocean.ontologyframework.tcm;

import java.util.ArrayList;
import java.util.List;

/**
 * 八纲-六经一体辨证结果
 */
public class InferenceResult {

    /** 主六经 IRI（合病时为第一个成立的病位对应经） */
    public String primaryChannel;

    /** 主六经中文标签 */
    public String primaryChannelLabel;

    /** 病位中文名（表/里/半表半里） */
    public String location;

    /** 病位 IRI */
    public String locationIri;

    /** 病性中文名（阳/阴/未定） */
    public String nature;

    /** 病性 IRI */
    public String natureIri;

    /** 八纲标签（如"表阳证""里阴证""半表半里阳证"） */
    public String baGangLabel;

    /** 是否合病 */
    public boolean isHeBing;

    /** 合病涉及的六经 IRI 列表（单经时仅含一个元素） */
    public List<String> heBingChannels = new ArrayList<>();

    /** 合病涉及的六经中文标签列表 */
    public List<String> heBingChannelLabels = new ArrayList<>();

    /** 依据链：记录每步判定的具体命中症状与推理路径 */
    public List<String> rationale = new ArrayList<>();

    /** 置信度：high=提纲证直接命中 / low=指征推断退化 */
    public String confidence;

    /** 是否少阴特异性直判 */
    public boolean isShaoYinDirect;

    /** 是否无法判定（三提纲均未命中且指征推断无结果） */
    public boolean indeterminate;

    /**
     * 工厂方法：少阴特异性直判
     */
    public static InferenceResult shaoYinDirect(String reason) {
        InferenceResult r = new InferenceResult();
        r.primaryChannel = SixChannelInferenceEngine.LJ_SHAOYIN;
        r.primaryChannelLabel = "少阴病";
        r.location = "表";
        r.locationIri = SixChannelInferenceEngine.BW_BIAO;
        r.nature = "阴";
        r.natureIri = SixChannelInferenceEngine.BX_YIN;
        r.baGangLabel = "表阴证";
        r.isHeBing = false;
        r.heBingChannels.add(SixChannelInferenceEngine.LJ_SHAOYIN);
        r.heBingChannelLabels.add("少阴病");
        r.rationale.add(reason);
        r.confidence = "high";
        r.isShaoYinDirect = true;
        r.indeterminate = false;
        return r;
    }

    /**
     * 工厂方法：无法判定
     */
    public static InferenceResult indeterminate(String reason) {
        InferenceResult r = new InferenceResult();
        r.indeterminate = true;
        r.confidence = "none";
        r.rationale.add(reason);
        return r;
    }
}