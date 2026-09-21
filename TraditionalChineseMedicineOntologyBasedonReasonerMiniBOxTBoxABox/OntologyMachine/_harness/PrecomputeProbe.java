import com.ocean.openlletresolver.OntologyService;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * 逐个 InferenceType 计时，用于判定 ReasonerService 启动 precompute 的耗时构成。
 * 用法: java PrecomputeProbe <tcm-all.owl 路径>
 */
public class PrecomputeProbe {

    private static final String BASE = "http://www.tcm-classics.org/jingfang#";

    public static void main(String[] args) throws Exception {
        String path = args[0];
        long t0 = System.currentTimeMillis();

        OntologyService os = new OntologyService(path);
        OWLOntology tbox = os.gettBoxOntology();
        System.out.println("[load] OntologyService = " + (System.currentTimeMillis() - t0)
                + " ms, 公理数=" + tbox.getAxiomCount()
                + ", 个体数=" + tbox.individualsInSignature().count());
        System.out.flush();

        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(tbox);
        long t1 = System.currentTimeMillis();
        r.flush();
        System.out.println("[flush] = " + (System.currentTimeMillis() - t1) + " ms");
        System.out.flush();

        InferenceType[] types = {
                InferenceType.CLASS_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_ASSERTIONS,
                InferenceType.DIFFERENT_INDIVIDUALS,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.SAME_INDIVIDUAL,
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.DISJOINT_CLASSES
        };

        long sum = 0;
        for (InferenceType t : types) {
            long s = System.currentTimeMillis();
            String status;
            try {
                r.precomputeInferences(t);
                status = "OK";
            } catch (Throwable e) {
                status = "FAILED(" + e.getClass().getSimpleName() + ")";
            }
            long d = System.currentTimeMillis() - s;
            sum += d;
            System.out.println("[precompute] " + t + " = " + d + " ms  " + status);
            System.out.flush();
        }
        System.out.println("[precompute] 合计 = " + sum + " ms");
        System.out.flush();

        OWLDataFactory df = os.getDataFactory();

        long s2 = System.currentTimeMillis();
        boolean cons = r.isConsistent();
        System.out.println("[isConsistent] = " + cons + " , " + (System.currentTimeMillis() - s2) + " ms");

        OWLClass fangzheng = df.getOWLClass(IRI.create(BASE + "Fangzheng"));
        long s3 = System.currentTimeMillis();
        int subs = r.getSubClasses(fangzheng, false).getFlattened().size();
        System.out.println("[TBox getSubClasses(Fangzheng)] = " + subs + " , " + (System.currentTimeMillis() - s3) + " ms");

        OWLClass wenjing = df.getOWLClass(IRI.create(BASE + "Wenjingtangzheng"));
        long s4 = System.currentTimeMillis();
        int sups = r.getSuperClasses(wenjing, false).getFlattened().size();
        System.out.println("[TBox getSuperClasses(Wenjingtangzheng)] = " + sups + " , " + (System.currentTimeMillis() - s4) + " ms");

        OWLNamedIndividual ind = tbox.individualsInSignature().findFirst().orElse(null);
        if (ind != null) {
            long s5 = System.currentTimeMillis();
            int n = r.getTypes(ind, false).getFlattened().size();
            System.out.println("[ABox getTypes(" + ind.getIRI().getShortForm() + ")] = " + n + " , " + (System.currentTimeMillis() - s5) + " ms");
        }

        OWLClass zhengzhuang = df.getOWLClass(IRI.create(BASE + "Zhengzhuang"));
        long s6 = System.currentTimeMillis();
        int inst = r.getInstances(zhengzhuang, false).getFlattened().size();
        System.out.println("[ABox getInstances(Zhengzhuang)] = " + inst + " , " + (System.currentTimeMillis() - s6) + " ms");

        OWLClass skos = df.getOWLClass(IRI.create("http://www.w3.org/2004/02/skos/core#Concept"));
        long s7 = System.currentTimeMillis();
        int skosN = r.getInstances(skos, true).getFlattened().size();
        System.out.println("[ABox getInstances(skos:Concept)] = " + skosN + " , " + (System.currentTimeMillis() - s7) + " ms");

        r.dispose();
        System.out.println("[done] total = " + (System.currentTimeMillis() - t0) + " ms");
        System.out.flush();
    }
}
