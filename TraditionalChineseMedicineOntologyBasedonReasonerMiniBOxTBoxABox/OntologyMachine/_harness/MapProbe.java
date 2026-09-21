import com.ocean.ontologyframework.tcm.app.LlmClient;
import com.ocean.ontologyframework.tcm.app.SymptomCatalog;
import com.ocean.ontologyframework.tcm.app.SymptomMappingService;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 症状映射探针：直接调用真实的 SymptomMappingService，
 * 打印每个表述单元的 L1 候选（含分数与来源）——这是 HTTP 响应里看不到的中间结果。
 */
public class MapProbe {

    public static void main(String[] args) throws Exception {
        String aboxDir = "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";
        SymptomCatalog catalog = new SymptomCatalog(aboxDir);
        LlmClient llm = new LlmClient();   // 未配置 api-key → isAvailable()=false
        SymptomMappingService svc = new SymptomMappingService(catalog, llm);

        System.out.println("[catalog] size=" + catalog.size() + " aliases=" + catalog.aliases().size());
        System.out.println("[llm] available=" + llm.isAvailable());

        // 反射拿到私有 l1Candidates
        Method l1 = SymptomMappingService.class.getDeclaredMethod("l1Candidates", String.class);
        l1.setAccessible(true);

        String[] inputs = {"下利脓血", "里急后重", "热利", "下利脓血、里急后重、热利"};
        for (String input : inputs) {
            System.out.println();
            System.out.println("================ 输入: " + input + " ================");
            System.out.println("切分单元: " + SymptomMappingService.splitUnits(input));
            for (String unit : SymptomMappingService.splitUnits(input)) {
                @SuppressWarnings("unchecked")
                List<SymptomMappingService.Candidate> cands =
                        (List<SymptomMappingService.Candidate>) l1.invoke(svc, unit);
                System.out.println("  单元「" + unit + "」L1 候选 " + cands.size() + " 条:");
                for (SymptomMappingService.Candidate c : cands) {
                    System.out.printf("      %-10s score=%.3f  src=%-14s frag=%s%n",
                            c.entry.getLabel(), c.score, c.source, c.entry.getFragment());
                }
                if (cands.isEmpty()) System.out.println("      （无候选）");
            }

            SymptomMappingService.MappingResult r =
                    svc.map(input, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0);
            System.out.println("  → summary   : " + r.summary);
            System.out.println("  → round     : " + r.round + "  needsConfirmation=" + r.needsConfirmation
                    + "  llmAvailable=" + r.llmAvailable);
            for (Map<String, Object> d : r.detail) {
                System.out.println("  → 采纳      : " + d.get("text") + " → " + d.get("label")
                        + " [" + d.get("fragment") + "] conf=" + d.get("confidence")
                        + " src=" + d.get("source"));
            }
            for (Map<String, Object> d : r.ambiguous) {
                System.out.println("  → 待确认    : " + d.get("text") + " → " + d.get("candidates"));
            }
            System.out.println("  → 未匹配    : " + r.unmatched);
            System.out.println("  → symptomIris: " + r.symptomIris);
        }

        // ---- 附：若 LLM 可用，「热利」会拿到什么候选白名单 ----
        System.out.println();
        System.out.println("================ 热利 的 LLM 候选白名单（模拟） ================");
        @SuppressWarnings("unchecked")
        List<SymptomMappingService.Candidate> hot =
                (List<SymptomMappingService.Candidate>) l1.invoke(svc, "热利");
        Class<?> pendingCls = Class.forName("com.ocean.ontologyframework.tcm.app.SymptomMappingService$Pending");
        var pCtor = pendingCls.getDeclaredConstructor(String.class, List.class);
        pCtor.setAccessible(true);
        Object pending = pCtor.newInstance("热利", hot);
        Method bw = SymptomMappingService.class.getDeclaredMethod("buildWhitelist", List.class, int.class);
        bw.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<SymptomCatalog.Entry> wl = (List<SymptomCatalog.Entry>) bw.invoke(svc, List.of(pending), 60);
        System.out.println("白名单条数 = " + wl.size());
        for (SymptomCatalog.Entry e : wl) {
            System.out.println("      " + e.getLabel() + "  [" + e.getFragment() + "]");
        }
    }
}
