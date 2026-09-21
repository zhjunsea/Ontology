import com.ocean.openlletresolver.OntologyService;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * 不做任何 precompute，测各查询的「懒计算」代价。
 * 用法: java LazyProbe <tcm-all.owl 路径>
 */
public class LazyProbe {

    private static final String BASE = "http://www.tcm-classics.org/jingfang#";

    public static void main(String[] args) throws Exception {
        String path = args[0];
        long t0 = System.currentTimeMillis();

        OntologyService os = new OntologyService(path);
        OWLOntology tbox = os.gettBoxOntology();
        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(tbox);
        r.flush();
        System.out.println("[setup] 加载+flush = " + (System.currentTimeMillis() - t0) + " ms  (未做任何 precompute)");
        System.out.flush();

        OWLDataFactory df = os.getDataFactory();

        long s = System.currentTimeMillis();
        boolean cons = r.isConsistent();
        System.out.println("[1] isConsistent() = " + cons + " , " + (System.currentTimeMillis() - s) + " ms");
        System.out.flush();

        OWLNamedIndividual ind = tbox.individualsInSignature().findFirst().orElse(null);
        if (ind != null) {
            long s1 = System.currentTimeMillis();
            int n = r.getTypes(ind, false).getFlattened().size();
            System.out.println("[2] ABox getTypes(" + ind.getIRI().getShortForm() + ") = " + n + " , " + (System.currentTimeMillis() - s1) + " ms");
            System.out.flush();
        }

        OWLClass zhengzhuang = df.getOWLClass(IRI.create(BASE + "Zhengzhuang"));
        long s2 = System.currentTimeMillis();
        int inst = r.getInstances(zhengzhuang, false).getFlattened().size();
        System.out.println("[3] ABox getInstances(Zhengzhuang) = " + inst + " , " + (System.currentTimeMillis() - s2) + " ms");
        System.out.flush();

        OWLClass fangzheng = df.getOWLClass(IRI.create(BASE + "Fangzheng"));
        long s3 = System.currentTimeMillis();
        int subs = r.getSubClasses(fangzheng, false).getFlattened().size();
        System.out.println("[4] TBox getSubClasses(Fangzheng) = " + subs + " , " + (System.currentTimeMillis() - s3) + " ms");
        System.out.flush();

        long s4 = System.currentTimeMillis();
        boolean cons2 = r.isConsistent();
        System.out.println("[5] isConsistent() 二次 = " + cons2 + " , " + (System.currentTimeMillis() - s4) + " ms");

        r.dispose();
        System.out.println("[done] total = " + (System.currentTimeMillis() - t0) + " ms");
        System.out.flush();
    }
}
