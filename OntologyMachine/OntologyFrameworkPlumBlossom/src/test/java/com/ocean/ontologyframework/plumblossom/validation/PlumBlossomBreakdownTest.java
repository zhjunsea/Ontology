package com.ocean.ontologyframework.plumblossom.validation;

import com.ocean.ontologyframework.plumblossom.PlumBlossomOntologyJobWorker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 梅花易数「破解（趋避）」规则单测（纯反射，离线；不依赖 MySQL/Ontop/Camunda/Spring）。
 *
 * <p>核验节点 14（{@code generateAdvice}）新增之破解法：①五行反查（生某 / 克某 / 通关）；
 * ②大凶·用克体三法齐备、凶·体生用两法、平·体克用略扶体、吉不破解；
 * ③四端取材（方位 / 时令 / 色 / 物）与原著引据在位。
 */
@DisplayName("梅花易数破解（趋避）：扶体/抑用/通关 + 十应四端（离线）")
public class PlumBlossomBreakdownTest {

    private static Method m(String name, Class<?>... params) throws Exception {
        Method mm = PlumBlossomOntologyJobWorker.class.getDeclaredMethod(name, params);
        mm.setAccessible(true);
        return mm;
    }

    @SuppressWarnings("unchecked")
    private static <T> T call(Method method, Object target, Object... args) throws Exception {
        return (T) method.invoke(target, args);
    }

    private static String generatorOf(String five) throws Exception {
        return call(m("generatorOf", String.class), null, five);
    }

    private static String overcomerOf(String five) throws Exception {
        return call(m("overcomerOf", String.class), null, five);
    }

    private static String mediator(String ke, String victim) throws Exception {
        return call(m("mediatorBetween", String.class, String.class), null, ke, victim);
    }

    private static String remedyLine(String five) throws Exception {
        return call(m("remedyLine", String.class), null, five);
    }

    private static String breakdown(String body, String use, String auspiciousness) throws Exception {
        PlumBlossomOntologyJobWorker w = new PlumBlossomOntologyJobWorker();
        return call(m("breakdownNote", String.class, String.class, String.class), w, body, use, auspiciousness);
    }

    @Test
    @DisplayName("扶体取材·生某五行者：金←土、水←金、木←水、火←木、土←火")
    void generatorOfFive() throws Exception {
        assertThat(generatorOf("金")).isEqualTo("土");
        assertThat(generatorOf("水")).isEqualTo("金");
        assertThat(generatorOf("木")).isEqualTo("水");
        assertThat(generatorOf("火")).isEqualTo("木");
        assertThat(generatorOf("土")).isEqualTo("火");
    }

    @Test
    @DisplayName("抑用取材·克某五行者：金←火、木←金、土←木、水←土、火←水")
    void overcomerOfFive() throws Exception {
        assertThat(overcomerOf("金")).isEqualTo("火");
        assertThat(overcomerOf("木")).isEqualTo("金");
        assertThat(overcomerOf("土")).isEqualTo("木");
        assertThat(overcomerOf("水")).isEqualTo("土");
        assertThat(overcomerOf("火")).isEqualTo("水");
    }

    @Test
    @DisplayName("通关五行：贪生忘克（火克金→土；金克木→水；木克土→火；土克水→金；水克火→木）")
    void mediatorFive() throws Exception {
        assertThat(mediator("火", "金")).isEqualTo("土");
        assertThat(mediator("金", "木")).isEqualTo("水");
        assertThat(mediator("木", "土")).isEqualTo("火");
        assertThat(mediator("土", "水")).isEqualTo("金");
        assertThat(mediator("水", "火")).isEqualTo("木");
    }

    @Test
    @DisplayName("四端取材·五行→方位/时令/色/物（十应）")
    void remedyLineFourEnds() throws Exception {
        assertThat(remedyLine("木")).contains("方位「东方」").contains("时令「春（寅卯月）」")
                .contains("色「青、碧、绿」").contains("竹木花草之属");
        assertThat(remedyLine("金")).contains("方位「西方」").contains("色「白」");
        assertThat(remedyLine("水")).contains("方位「北方」").contains("色「黑」");
    }

    @Test
    @DisplayName("大凶·用克体（乾金为体、离火为用）：三法齐备（扶体土/抑用水/通关土）＋原著引据")
    void badOvercomeThreeMethods() throws Exception {
        String s = breakdown("乾", "离", "大凶");
        assertThat(s).contains("扶体").contains("抑用").contains("通关");
        assertThat(s).contains("土").contains("水");
        assertThat(s).contains("分例指避凶趋吉之道").contains("趋避之，或可转祸为福");
        assertThat(s).contains("修德积善，君子常分");
    }

    @Test
    @DisplayName("凶·体生用泄气（乾金为体、坎水为用）：扶体＋抑用（无通关）")
    void badDrainTwoMethods() throws Exception {
        String s = breakdown("乾", "坎", "凶");
        assertThat(s).contains("泄气").contains("补其元").contains("减其耗");
        assertThat(s).doesNotContain("通关");
    }

    @Test
    @DisplayName("平·体克用（乾金为体、震木为用）：略扶体＋持正")
    void flatBodyOvercomes() throws Exception {
        String s = breakdown("乾", "震", "平");
        assertThat(s).contains("体克用虽平").contains("持正").contains("固本");
        assertThat(s).doesNotContain("通关");
    }

    @Test
    @DisplayName("吉/大吉：不设破解（守正保泰即可）")
    void goodNoBreakdown() throws Exception {
        assertThat(breakdown("乾", "坤", "吉")).isEmpty();
        assertThat(breakdown("乾", "坤", "大吉")).isEmpty();
    }
}
