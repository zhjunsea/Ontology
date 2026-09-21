import com.ocean.openlletresolver.OntologyService;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 验证假设：分类耗时的非确定性来自「合并本体时公理插入顺序」。
 * 做法：把 TBox 公理按 toString() 稳定排序后重新装入一个全新本体，再分类。
 * 若多次运行耗时一致 → 顺序是根因；若仍大幅波动 → 另有来源。
 *
 * 用法: java PrecomputeProbe4 <tcm-all.owl 路径> [sort|nosort]
 */
public class PrecomputeProbe4 {

    public static void main(String[] args) throws Exception {
        String path = args[0];
        boolean sort = args.length < 2 || "sort".equalsIgnoreCase(args[1]);

        long t0 = System.currentTimeMillis();
        OntologyService os = new OntologyService(path);
        OWLOntology src = os.gettBoxOntology();
        long t1 = System.currentTimeMillis();

        List<OWLAxiom> axioms = new ArrayList<>(src.getAxioms());
        int before = axioms.size();
        if (sort) {
            axioms.sort(Comparator.comparing(OWLAxiom::toString));
        }

        OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
        OWLOntology tbox = mgr.createOntology(IRI.create("urn:probe4:deterministic"));
        mgr.addAxioms(tbox, new java.util.LinkedHashSet<>(axioms));
        long t2 = System.currentTimeMillis();

        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(tbox);
        r.flush();
        long t3 = System.currentTimeMillis();

        r.precomputeInferences(
                InferenceType.CLASS_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.DISJOINT_CLASSES);
        long t4 = System.currentTimeMillis();

        System.out.println("[RESULT] mode=" + (sort ? "sorted" : "raw")
                + " load=" + (t1 - t0) + "ms rebuild=" + (t2 - t1)
                + "ms flush=" + (t3 - t2) + "ms precompute=" + (t4 - t3)
                + "ms total=" + (t4 - t0) + "ms axioms=" + before + "->" + tbox.getAxiomCount());
        System.out.flush();
    }
}
