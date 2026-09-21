import com.ocean.openlletresolver.OntologyService;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * 逐类 satisfiability 计时探针（详细版）—— 每个类检查前先打印类名并 flush，
 * 因此若进程卡死，日志最后一行即为「正在检查的类」。
 *
 * 用法: java SatProbe2 <tcm-all.owl 路径> [慢阈值ms，默认 200]
 */
public class SatProbe2 {

    public static void main(String[] args) throws Exception {
        String path = args[0];
        long threshold = args.length > 1 ? Long.parseLong(args[1]) : 200L;

        OntologyService os = new OntologyService(path);
        OWLOntology ont = os.gettBoxOntology();
        System.out.println("[load] axioms=" + ont.getAxiomCount()
                + ", individuals=" + ont.individualsInSignature().count()
                + ", classes=" + ont.classesInSignature().count());
        System.out.flush();

        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(ont);
        r.flush();

        java.util.List<OWLClass> classes = new java.util.ArrayList<>(ont.classesInSignature().toList());
        classes.sort(java.util.Comparator.comparing(c -> c.getIRI().getShortForm()));

        long total = 0;
        int i = 0;
        for (OWLClass c : classes) {
            i++;
            System.out.println("[check] " + i + "/" + classes.size() + " " + c.getIRI().getShortForm());
            System.out.flush();
            long t = System.currentTimeMillis();
            boolean sat;
            try {
                sat = r.isSatisfiable(c);
            } catch (Throwable e) {
                System.out.println("[sat] " + i + " " + c.getIRI().getShortForm()
                        + " THROW " + e.getClass().getSimpleName());
                System.out.flush();
                continue;
            }
            long d = System.currentTimeMillis() - t;
            total += d;
            if (d >= threshold) {
                System.out.println("[SLOW] " + i + "/" + classes.size() + " "
                        + c.getIRI().getShortForm() + " " + d + " ms " + (sat ? "sat" : "UNSAT"));
                System.out.flush();
            }
        }
        System.out.println("[summary] classes=" + classes.size()
                + ", total isSatisfiable=" + total + " ms");
        System.out.flush();

        r.dispose();
    }
}
