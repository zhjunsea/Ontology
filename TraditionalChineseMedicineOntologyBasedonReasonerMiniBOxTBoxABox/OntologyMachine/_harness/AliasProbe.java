import com.ocean.ontologyframework.tcm.app.LlmClient;
import com.ocean.ontologyframework.tcm.app.SymptomCatalog;
import com.ocean.ontologyframework.tcm.app.SymptomMappingService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 口语同义词接入探针。
 *
 * <p>用「已知失败用例」对比 SymptomCatalog 同义词来源改造前后的映射效果：
 * <ul>
 *   <li>改造前：仅硬编码 ~100 条 aliases</li>
 *   <li>改造后：SKOS 词表（tcm-zhengzhuang_skos.ttl）为主 + 硬编码兜底</li>
 * </ul>
 *
 * <p>用例取自 2026-09-21 的诊断缺口分析（六经定不出的直接触发因）。
 */
public class AliasProbe {

    public static void main(String[] args) throws Exception {
        String aboxDir = args.length > 0 ? args[0]
                : "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";

        SymptomCatalog catalog = new SymptomCatalog(aboxDir);
        LlmClient llm = new LlmClient();   // 未配置 api-key → isAvailable()=false
        SymptomMappingService svc = new SymptomMappingService(catalog, llm);

        System.out.println("========== 目录 / 同义词规模 ==========");
        System.out.println("[catalog] 个体数   = " + catalog.size());
        System.out.println("[catalog] 同义词数 = " + catalog.aliases().size());
        System.out.println("[catalog] 切词表面形式数 = " + catalog.surfaceForms().size());
        System.out.println("[llm] available = " + llm.isAvailable());

        Method l1 = SymptomMappingService.class.getDeclaredMethod("l1Candidates", String.class);
        l1.setAccessible(true);

        // 已知失败用例（2026-09-21 缺口分析）
        String[] inputs = {
                "两边肋骨下面胀痛",
                "肋骨下胀痛",
                "胁肋胀痛",
                "拉肚子",
                "肚子胀",
                "手脚冰凉",
                "睡不着",
                "反酸",
                "老想吐",
                "拉不消化的东西",
                "想拉又拉不出",
                "气从小腹往上冲心",
                "老想捶胸口",
                "起鸡皮疙瘩",
                "下午定时发热",
                "说胡话",
                "抽筋",
                "浑身疼",
                "没胃口",
                "尿频",
                // 组合用例（原诊断缺口分析里的真实输入）
                "拉肚子，肚子胀，手脚冰凉",
                "往来寒热，胸胁苦满，默默不欲饮食，心烦喜呕",
                "发热，汗出，恶风，脉缓",
                "下利脓血，里急后重，热利",
        };

        int matched = 0, total = 0;
        for (String input : inputs) {
            System.out.println();
            System.out.println("================ 输入: " + input + " ================");
            System.out.println("切分单元: " + SymptomMappingService.splitUnits(input));
            for (String unit : SymptomMappingService.splitUnits(input)) {
                @SuppressWarnings("unchecked")
                List<SymptomMappingService.Candidate> cands =
                        (List<SymptomMappingService.Candidate>) l1.invoke(svc, unit);
                System.out.printf("  单元「%s」L1 候选 %d 条:", unit, cands.size());
                for (int i = 0; i < Math.min(3, cands.size()); i++) {
                    SymptomMappingService.Candidate c = cands.get(i);
                    System.out.printf("  %s(%.2f,%s)", c.entry.getLabel(), c.score, c.source);
                }
                System.out.println();
                total++;
                if (!cands.isEmpty()) matched++;
            }

            SymptomMappingService.MappingResult r =
                    svc.map(input, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0);
            System.out.println("  → 采纳  : " + r.detail.size() + " 条");
            for (Map<String, Object> d : r.detail) {
                System.out.println("      " + d.get("text") + " → " + d.get("label")
                        + " [" + d.get("fragment") + "] conf=" + d.get("confidence")
                        + " src=" + d.get("source"));
            }
            if (!r.unmatched.isEmpty()) System.out.println("  → 未匹配: " + r.unmatched);
            if (!r.ambiguous.isEmpty()) System.out.println("  → 待确认: " + r.ambiguous.size() + " 项");
        }

        System.out.println();
        System.out.printf("[汇总] 单元命中 %d / %d%n", matched, total);
    }
}
