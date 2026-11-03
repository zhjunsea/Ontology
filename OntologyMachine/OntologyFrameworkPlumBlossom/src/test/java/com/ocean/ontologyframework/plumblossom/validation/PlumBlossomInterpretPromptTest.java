package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数「邵康节解读」提示词构造单测（纯反射，离线；不依赖 MySQL/Ontop/Camunda/Spring）。
 *
 * <p>核验第四十九轮：本体应用应据「所问之事 + 主体（体卦五行/方位/旺衰）＋分项断语＋破解方案」
 * 生成结构化的「占断事实包」与因事因体的建言要求，指引大模型贴身作答，而非自由发挥。
 */
@DisplayName("梅花易数解读提示词：事实包＋因事因体建言（离线）")
public class PlumBlossomInterpretPromptTest {

    private static Method m(String name, Class<?>... params) throws Exception {
        Method mm = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, params);
        mm.setAccessible(true);
        return mm;
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(Method method, Object target, Object... args) throws Exception {
        return (T) method.invoke(target, args);
    }

    private static Map<String, Object> sampleVars() {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("bodyTrigram", "乾");
        v.put("useTrigram", "离");
        v.put("bodyVitality", "死");
        v.put("originalHexagram", "火天大有");
        v.put("mutualHexagram", "泽天夬");
        v.put("changedHexagram", "乾为天");
        v.put("shengKeType", "5 用克体");
        v.put("auspiciousness", "大凶");
        v.put("categoryVerdict", "求财：用克体，财难得，且有耗散之虞。");
        v.put("imageInterpretation", "乾为金、为圆物；离为火、为文书。");
        v.put("omenVerdict", "闻哭声，主悲忧。");
        v.put("responsePeriod", "近则可于巳午火日应之。");
        v.put("breakdownPlan", "①扶体——体卦乾属金，宜近生金之土……");
        v.put("advice", "凶兆当前，宜积德改行、反求诸己。");
        return v;
    }

    @Test
    @DisplayName("占断事实包：含所问之事、主体五行方位旺衰、用卦、分项断语、破解方案、根本")
    void factSheetContainsTailoringFacts() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        String sheet = call(m("buildDivinationFactSheet", String.class, Map.class), w,
                "求财", sampleVars());
        assertThat(sheet).contains("【所问之事】求财");
        assertThat(sheet).contains("体卦 乾").contains("五行属金").contains("方位西北").contains("卦气·死");
        assertThat(sheet).contains("用卦 离").contains("五行属火");
        assertThat(sheet).contains("分项断语").contains("求财：用克体");
        assertThat(sheet).contains("【破解方案").contains("①扶体——体卦乾属金");
        assertThat(sheet).contains("修身根本·道").contains("积德改行");
    }

    @Test
    @DisplayName("解读用户提示：含事项、事实包、破解方案与因事因体建言要求")
    void interpretPromptTailors() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        String p = call(m("buildInterpretUserPrompt", String.class, Map.class, String.class), w,
                "求财", sampleVars(), "【汇总】……吉凶等级：大凶……");
        assertThat(p).contains("【占断事项】求财");
        assertThat(p).contains("【本体应用生成的占断事实包】");
        assertThat(p).contains("①扶体——体卦乾属金");
        assertThat(p).contains("【解读要求】").contains("紧扣所问之事与主体情况");
        assertThat(p).contains("将破解方案转译为对该事该主体的具体可行建议");
    }

    @Test
    @DisplayName("白话用户提示：含事实包与白话建言要求")
    void plainPromptTailors() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        String p = call(m("buildInterpretPlainUserPrompt", String.class, Map.class, String.class, String.class), w,
                "求财", sampleVars(), "【汇总】……", "【古典解读】……");
        assertThat(p).contains("【本体应用生成的占断事实包】");
        assertThat(p).contains("【白话解释要求】").contains("面向问卦者本人");
        assertThat(p).contains("①扶体——体卦乾属金");
    }

    @Test
    @DisplayName("单源复用：vars 已含 divinationFactSheet 时原样返回（前端展示≡大模型所见）")
    void factSheetReusedFromVariable() throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        Map<String, Object> v = sampleVars();
        String preset = "【所问之事】求财\n【主体情况·体卦】体卦 乾（五行属金，方位西北）；卦气·死\n";
        v.put("divinationFactSheet", preset);
        String sheet = call(m("buildDivinationFactSheet", String.class, Map.class), w, "求财", v);
        assertThat(sheet).isEqualTo(preset);
    }
}
