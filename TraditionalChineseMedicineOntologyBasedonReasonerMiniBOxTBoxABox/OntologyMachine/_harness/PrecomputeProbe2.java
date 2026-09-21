import com.ocean.openlletresolver.OntologyService;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * 复刻 ReasonerService 的「一次性 9 类型」precompute 调用，判定是否与逐类型调用有差异。
 * 用法: java PrecomputeProbe2 <tcm-all.owl 路径>
 */
public class PrecomputeProbe2 {

    public static void main(String[] args) throws Exception {
        String path = args[0];
        long t0 = System.currentTimeMillis();

        OntologyService os = new OntologyService(path);
        OWLOntology tbox = os.gettBoxOntology();
        System.out.println("[load] = " + (System.currentTimeMillis() - t0)
                + " ms, 公理数=" + tbox.getAxiomCount()
                + ", 个体数=" + tbox.individualsInSignature().count());
        System.out.flush();

        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(tbox);
        r.flush();
        System.out.println("[flush] done at " + (System.currentTimeMillis() - t0) + " ms");
        System.out.flush();

        long t1 = System.currentTimeMillis();
        r.precomputeInferences(
                InferenceType.CLASS_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_ASSERTIONS,
                InferenceType.DIFFERENT_INDIVIDUALS,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.SAME_INDIVIDUAL,
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.DISJOINT_CLASSES);
        System.out.println("[precompute ALL-9] = " + (System.currentTimeMillis() - t1) + " ms");
        System.out.flush();

        System.out.println("[done] total = " + (System.currentTimeMillis() - t0) + " ms");
        System.out.flush();
    }
}
