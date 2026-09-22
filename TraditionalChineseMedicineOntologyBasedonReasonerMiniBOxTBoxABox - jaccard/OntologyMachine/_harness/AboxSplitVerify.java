import com.ocean.openlletresolver.OntologyService;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 验证 TBox / ABox 分离：
 *  1) tcm-all.owl 经 OntologyService 合并后，个体数应为 0（或仅名义量引入者），CLASS_HIERARCHY 应大幅下降；
 *  2) tcm-all-abox.owl 单独加载后，症状/脉象/舌象个体与类型断言应完好。
 */
public class AboxSplitVerify {

    public static void main(String[] args) throws Exception {
        String mainPath = args[0];
        String aboxPath = args[1];

        long t0 = System.currentTimeMillis();
        OntologyService os = new OntologyService(mainPath);
        OWLOntology tbox = os.gettBoxOntology();
        System.out.println("[TBox] load=" + (System.currentTimeMillis() - t0) + "ms"
                + " 公理=" + tbox.getAxiomCount()
                + " 个体=" + tbox.individualsInSignature().count()
                + " 类=" + tbox.classesInSignature().count());
        System.out.flush();

        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(tbox);
        r.flush();
        long t1 = System.currentTimeMillis();
        r.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        System.out.println("[CLASS_HIERARCHY] = " + (System.currentTimeMillis() - t1) + " ms");
        System.out.flush();
        try {
            System.out.println("[unsat] " + r.getUnsatisfiableClasses().getEntitiesMinusBottom().size());
        } catch (Throwable e) {
            System.out.println("[unsat] 查询失败 " + e);
        }
        r.dispose();

        File f = new File(aboxPath);
        OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
        File dir = f.getParentFile();
        if (dir != null && dir.isDirectory()) {
            AutoIRIMapper m = new AutoIRIMapper(dir, true);
            m.update();
            mgr.getIRIMappers().add(m);
        }
        mgr.loadOntologyFromOntologyDocument(f);
        // 与 TCMOntologyJobWorker 一致：合并所有已加载本体（getAxiomCount 不遍历 import）
        List<OWLOntology> loaded = new ArrayList<>(mgr.getOntologies());
        OWLOntology abox = mgr.createOntology(IRI.create("urn:tcm:abox-lookup"));
        for (OWLOntology o : loaded) {
            mgr.addAxioms(abox, o.axioms().collect(Collectors.toSet()));
        }
        System.out.println("[ABox] 公理=" + abox.getAxiomCount()
                + " 个体=" + abox.individualsInSignature().count()
                + " 类=" + abox.classesInSignature().count());
        System.out.flush();

        OWLDataFactory df = mgr.getOWLDataFactory();
        String base = "http://www.tcm-classics.org/jingfang#";
        String[] samples = {"Fare_instance", "Ehan_instance", "Fumai_instance",
                "Fuman_instance", "Kouke_instance", "Hongmai_instance"};
        for (String s : samples) {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(base + s));
            List<String> types = abox.classAssertionAxioms(ind)
                    .map(a -> a.getClassExpression().toString())
                    .collect(Collectors.toList());
            System.out.println("[ABox] " + s + " : " + types);
        }
        System.out.flush();
        System.out.println("[done]");
    }
}
