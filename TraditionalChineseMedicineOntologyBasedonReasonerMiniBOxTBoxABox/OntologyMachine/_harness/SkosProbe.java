import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.SkosSynonymReader;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.List;
import java.util.Map;

/**
 * SKOS 同义词识别探针。
 *
 * <p>验证目标：SKOS 词表经 owl:imports 挂进 tcm-all.owl 后，
 * Openllet 推理机能否「自动识别」其中的 skos:Concept，
 * 并据此构建「任意中文表述 → 规范症状名」的同义词词典。
 *
 * <p>关键链路（SkosSynonymReader.buildSynonymDictionary）：
 * <pre>
 *   getReasoner().getInstances(skos:Concept, true)   ← 依赖推理机发现概念
 *     → 逐概念读 prefLabel / altLabel / hiddenLabel / note
 *     → dict[表面形式小写] = prefLabel
 * </pre>
 */
public class SkosProbe {

    private static final String NS = "http://www.tcm-classics.org/jingfang#";
    private static final String SKOS_NS = "http://www.tcm-classics.org/skos/zhengzhuang#";

    public static void main(String[] args) throws Exception {
        String tbox = args.length > 0 ? args[0]
                : "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/tcm-all.owl";

        System.out.println("========== 1. 加载 TBox + Openllet 推理 ==========");
        long t0 = System.currentTimeMillis();
        BackendService backend = BackendService.getInstance(tbox);
        long t1 = System.currentTimeMillis();
        System.out.printf("[计时] BackendService 初始化（含推理）: %.1f s%n", (t1 - t0) / 1000.0);

        OWLOntology ont = SkosSynonymReader.getTBox();
        System.out.println("[TBox] 公理总数        = " + ont.getAxiomCount());
        System.out.println("[TBox] 个体签名数      = " + ont.getIndividualsInSignature().size());
        System.out.println("[TBox] 已加载本体数    = " + ont.getOWLOntologyManager().getOntologies().size());
        System.out.println("[TBox] importsClosure  = " + ont.importsClosure().count());

        System.out.println();
        System.out.println("========== 2. 推理机是否「看见」skos:Concept ==========");
        OWLReasoner reasoner = backend.getReasonerService().getReasoner();
        System.out.println("[Reasoner] 实现类        = " + reasoner.getClass().getSimpleName());
        System.out.println("[Reasoner] 是否一致      = " + reasoner.isConsistent());

        var conceptCls = ont.getOWLOntologyManager().getOWLDataFactory()
                .getOWLClass(IRI.create("http://www.w3.org/2004/02/skos/core#Concept"));
        long t2 = System.currentTimeMillis();
        var instances = reasoner.getInstances(conceptCls, true).entities().toList();
        long t3 = System.currentTimeMillis();
        System.out.println("[Reasoner] getInstances(skos:Concept) = " + instances.size()
                + " 个（耗时 " + (t3 - t2) + " ms）");
        System.out.println("[Reasoner] 本体签名中 skos:Concept 类型断言数 = "
                + ont.axioms(org.semanticweb.owlapi.model.AxiomType.CLASS_ASSERTION)
                        .filter(ax -> ax.getClassExpression().isOWLClass()
                                && ax.getClassExpression().asOWLClass().getIRI().toString()
                                        .equals("http://www.w3.org/2004/02/skos/core#Concept"))
                        .count());

        System.out.println();
        System.out.println("========== 3. 构建同义词词典 ==========");
        long t4 = System.currentTimeMillis();
        Map<String, String> dict = SkosSynonymReader.buildSynonymDictionary();
        long t5 = System.currentTimeMillis();
        System.out.printf("[计时] buildSynonymDictionary: %.1f s%n", (t5 - t4) / 1000.0);
        System.out.println("[词典] 条目数 = " + dict.size());

        System.out.println();
        System.out.println("========== 4. 口语 / 同义词命中验证 ==========");
        String[] probes = {
                "拉肚子", "腹泻", "肚子胀", "腹胀", "手脚冰凉", "手脚冰冷",
                "两边肋骨下面胀痛", "肋骨下胀痛", "忽冷忽热", "一阵冷一阵热",
                "大便秘结", "小便黄赤", "完谷不化", "怕冷", "睡不着", "失眠",
                "反酸", "烧心", "打嗝", "没胃口", "食欲不振", "尿频",
                "浑身疼", "抽筋", "说胡话", "拉不消化的东西", "想拉又拉不出",
                "下午定时发热", "气从小腹往上冲心", "老想捶胸口", "起鸡皮疙瘩",
        };
        int hit = 0;
        for (String p : probes) {
            String v = dict.get(p.toLowerCase());
            if (v != null) hit++;
            System.out.printf("  %-16s -> %s%n", p, v == null ? "（未命中）" : v);
        }
        System.out.printf("[命中] %d / %d%n", hit, probes.length);

        System.out.println();
        System.out.println("========== 5. exactMatch 反查（个体 → 同义词）==========");
        for (String frag : new String[]{"Ehan_instance", "Xiali_instance", "Shouzujueni_instance", "Fuman_instance"}) {
            String uri = NS + frag;
            List<String> syn = SkosSynonymReader.getSynonymsByOwlIndividual(uri);
            System.out.printf("  %-22s -> %d 个: %s%n", frag, syn.size(),
                    syn.size() > 8 ? syn.subList(0, 8) + "..." : syn);
        }

        System.out.println();
        System.out.println("========== 6. prefLabel 反查概念 IRI ==========");
        for (String lab : new String[]{"恶寒", "下利", "手足厥逆", "腹满", "不存在的症状"}) {
            System.out.printf("  %-12s -> %s%n", lab, SkosSynonymReader.findConceptIRIByPrefLabel(lab));
        }

        System.out.println();
        System.out.printf("[总计时] 端到端 %.1f s%n", (System.currentTimeMillis() - t0) / 1000.0);
        System.exit(0);
    }
}
