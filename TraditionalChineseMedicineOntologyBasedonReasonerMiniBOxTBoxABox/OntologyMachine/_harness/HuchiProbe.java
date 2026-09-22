import com.ocean.openlletresolver.OntologyModuleUtils;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 互斥（owl:disjointWith）专项验证探针。
 *
 * <p>复现 TCMOntologyJobWorker.buildHuchiIndex / detectSizhenConflicts 的算法，
 * 直接在本体上验证：
 * <ol>
 *   <li>tcm-huchi.owl 的互斥公理确实进入 TBox；</li>
 *   <li>关键互斥对成立（恶寒⊥不恶寒、浮脉⊥沉脉）；</li>
 *   <li>子类传播成立（浮数脉⊥沉迟脉）；</li>
 *   <li>无假阳性（不互斥的症状不会被判为冲突）；</li>
 *   <li>加入互斥公理后本体仍一致（reasoner 不报 unsat）。</li>
 * </ol>
 *
 * 用法: java HuchiProbe &lt;ontologyDir&gt;
 */
public class HuchiProbe {

    static final String BASE = "http://www.tcm-classics.org/jingfang/";
    static final String NS = "http://www.tcm-classics.org/jingfang#";
    static int pass = 0, fail = 0;

    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("  [OK]   " + name); }
        else { fail++; System.out.println("  [FAIL] " + name); }
    }

    public static void main(String[] args) throws Exception {
        String ontDir = args[0];
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        m.getOntologyConfigurator().setMissingImportHandlingStrategy(
                org.semanticweb.owlapi.model.MissingImportHandlingStrategy.SILENT);
        AutoIRIMapper mapper = new AutoIRIMapper(new File(ontDir), true);
        mapper.update();
        m.getIRIMappers().add(mapper);
        OWLOntology ont = m.loadOntologyFromOntologyDocument(
                new File(ontDir + "/tcm-all.owl"));
        System.out.println("[probe] tcm-all 公理=" + ont.getAxiomCount()
                + " 已加载本体数=" + m.ontologies().count());

        // 汇总 imports 闭包（tcm-all 本身只有 imports，互斥公理在 tcm-huchi.owl 中）
        List<OWLOntology> closure = ont.importsClosure().collect(Collectors.toList());
        int totalAxioms = closure.stream().mapToInt(OWLOntology::getAxiomCount).sum();
        System.out.println("[probe] imports 闭包本体数=" + closure.size()
                + " 公理总数=" + totalAxioms);

        // ---- 1. 互斥公理计数 ----
        List<OWLDisjointClassesAxiom> djs = closure.stream()
                .flatMap(o -> o.axioms(AxiomType.DISJOINT_CLASSES))
                .distinct()
                .collect(Collectors.toList());
        System.out.println("[probe] disjointWith 公理数=" + djs.size());
        check("tcm-huchi.owl 互斥公理已进入 TBox (>=40)", djs.size() >= 40);

        // ---- 2. 构建 huchiIndex（与 worker 同算法） ----
        OWLReasoner r0 = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        r0.precomputeInferences(org.semanticweb.owlapi.reasoner.InferenceType.CLASS_HIERARCHY);
        Map<String, Set<String>> idx = new HashMap<>();
        for (OWLDisjointClassesAxiom ax : djs) {
            List<OWLClassExpression> es = ax.getClassExpressionsAsList();
            if (es.size() != 2 || !es.get(0).isOWLClass() || !es.get(1).isOWLClass()) continue;
            OWLClass a = es.get(0).asOWLClass(), b = es.get(1).asOWLClass();
            Set<String> sa = namedSubclassFrags(r0, a);
            Set<String> sb = namedSubclassFrags(r0, b);
            for (String x : sa) { idx.computeIfAbsent(x, k -> new HashSet<>()).addAll(sb); idx.get(x).remove(x); }
            for (String y : sb) { idx.computeIfAbsent(y, k -> new HashSet<>()).addAll(sa); idx.get(y).remove(y); }
        }
        System.out.println("[probe] 展开后 fragment 数=" + idx.size());

        // ---- 3. 关键互斥对 ----
        check("恶寒 ⊥ 不恶寒", excl(idx, "Ehan", "Buehan"));
        check("浮脉 ⊥ 沉脉", excl(idx, "Fumai", "Chenmai"));
        check("浮脉 ⊥ 伏脉", excl(idx, "Fumai", "Fumai_Yin"));
        check("数脉 ⊥ 迟脉", excl(idx, "Shumai", "Chimai"));
        check("滑脉 ⊥ 涩脉", excl(idx, "Huamai", "Semai"));
        check("虚脉 ⊥ 实脉", excl(idx, "Xumai", "Shimai"));
        check("长脉 ⊥ 短脉", excl(idx, "Changmai", "Duanmai"));
        check("大脉 ⊥ 小脉", excl(idx, "Damai", "Xiaomai"));
        check("洪脉 ⊥ 微脉", excl(idx, "Hongmai", "Weimai"));
        check("口渴 ⊥ 不渴", excl(idx, "Kouke", "Buke"));
        check("吐 ⊥ 不吐", excl(idx, "Tu", "Butu"));
        check("呕 ⊥ 不呕", excl(idx, "Ou", "Buou"));
        check("下利 ⊥ 不大便", excl(idx, "Xiali", "Budabian"));
        check("舌淡白 ⊥ 舌红", excl(idx, "PaleWhiteTongue", "RedTongue"));
        check("薄苔 ⊥ 厚苔", excl(idx, "ThinCoating", "ThickCoating"));
        check("润 ⊥ 燥", excl(idx, "MoistTongue", "DryTongue"));

        // ---- 4. 子类传播 ----
        check("浮数脉 ⊥ 沉迟脉 (子类传播)", excl(idx, "Fushumai", "Chenchimai"));
        check("浮紧脉 ⊥ 沉细脉 (子类传播)", excl(idx, "Fujinmai", "Chenximai"));
        check("浮缓脉 ⊥ 沉紧脉 (子类传播)", excl(idx, "Fuhuanmai", "Chenjinmai"));

        // ---- 5. 无假阳性 ----
        check("浮脉 不互斥 数脉", !excl(idx, "Fumai", "Shumai"));
        check("恶寒 不互斥 发热", !excl(idx, "Ehan", "Fare"));
        check("口渴 不互斥 发热", !excl(idx, "Kouke", "Fare"));
        check("沉脉 不互斥 细脉", !excl(idx, "Chenmai", "Ximai"));

        // ---- 6. detectSizhenConflicts 行为 ----
        check("冲突检测: {恶寒,不恶寒} → 1 对",
                detect(idx, Set.of("Ehan", "Buehan")).size() == 1);
        check("冲突检测: {浮脉,沉脉} → 1 对",
                detect(idx, Set.of("Fumai", "Chenmai")).size() == 1);
        check("冲突检测: {恶寒,发热,脉浮} → 0 对",
                detect(idx, Set.of("Ehan", "Fare", "Fumai")).isEmpty());
        check("冲突检测: 空集 → 0 对", detect(idx, Set.of()).isEmpty());

        // ---- 7. 本体一致性（加入互斥后不产生 unsat） ----
        OWLReasoner r = r0;
        boolean consistent = r.isConsistent();
        check("加入互斥公理后本体一致", consistent);
        Set<OWLClass> unsat = r.getUnsatisfiableClasses().getEntitiesMinusBottom();
        check("无不可满足类 (unsat=" + unsat.size() + ")", unsat.isEmpty());
        if (!unsat.isEmpty()) {
            System.out.println("    unsat: " + unsat.stream()
                    .map(c -> c.getIRI().getFragment()).limit(20).collect(Collectors.toList()));
        }
        r.dispose();

        System.out.println("\n==== 互斥验证: 通过=" + pass + " 失败=" + fail + " ====");
        System.out.println(fail == 0 ? ">>> 全部通过" : ">>> 存在失败");
        if (fail > 0) System.exit(1);
    }

    static Set<String> namedSubclassFrags(OWLReasoner r, OWLClass cls) {
        Set<String> out = new HashSet<>();
        out.add(cls.getIRI().getFragment());
        for (OWLClass c : r.getSubClasses(cls, false).getFlattened()) {
            if (!c.isOWLNothing()) out.add(c.getIRI().getFragment());
        }
        return out;
    }

    static boolean excl(Map<String, Set<String>> idx, String a, String b) {
        Set<String> e = idx.get(a);
        return e != null && e.contains(b);
    }

    static List<List<String>> detect(Map<String, Set<String>> idx, Set<String> frags) {
        List<List<String>> out = new ArrayList<>();
        List<String> s = new ArrayList<>(frags);
        Collections.sort(s);
        for (int i = 0; i < s.size(); i++) {
            Set<String> e = idx.get(s.get(i));
            if (e == null) continue;
            for (int j = i + 1; j < s.size(); j++) {
                if (e.contains(s.get(j))) out.add(List.of(s.get(i), s.get(j)));
            }
        }
        return out;
    }
}
