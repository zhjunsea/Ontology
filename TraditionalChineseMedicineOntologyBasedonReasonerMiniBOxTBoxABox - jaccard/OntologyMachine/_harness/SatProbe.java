import com.ocean.openlletresolver.OntologyService;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

/**
 * 逐类 satisfiability 计时探针 —— 找出到底是「每个类都慢」还是「少数几个类极慢」。
 *
 * 用法: java SatProbe <tcm-all.owl 路径> [慢阈值ms，默认 200]
 *
 * 输出每行: [sat] <序号>/<总数> <类名> <耗时ms> <sat|unsat>
 * 若进程卡死，日志最后一行即为「正在检查的类」。
 */
public class SatProbe {

    public static void main(String[] args) throws Exception {
        String path = args[0];
        long threshold = args.length > 1 ? Long.parseLong(args[1]) : 200L;

        OntologyService os = new OntologyService(path);
        OWLOntology ont = os.gettBoxOntology();
        System.out.println("[load] 公理=" + ont.getAxiomCount()
                + ", 个体=" + ont.individualsInSignature().count()
                + ", 类=" + ont.classesInSignature().count());
        System.out.flush();

        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(ont);
        r.flush();

        java.util.List<OWLClass> classes = new java.util.ArrayList<>(ont.classesInSignature().toList());
        classes.sort(java.util.Comparator.comparing(c -> c.getIRI().getShortForm()));

        long total = 0;
        int slow = 0;
        int i = 0;
        for (OWLClass c : classes) {
            i++;
            long t = System.currentTimeMillis();
            boolean sat;
            try {
                sat = r.isSatisfiable(c);
            } catch (Throwable e) {
                System.out.println("[sat] " + i + "/" + classes.size() + " "
                        + c.getIRI().getShortForm() + " THROW " + e.getClass().getSimpleName());
                System.out.flush();
                continue;
            }
            long d = System.currentTimeMillis() - t;
            total += d;
            if (d >= threshold) {
                slow++;
                System.out.println("[sat] " + i + "/" + classes.size() + " "
                        + c.getIRI().getShortForm() + " " + d + " ms " + (sat ? "sat" : "UNSAT"));
                System.out.flush();
            }
        }
        System.out.println("[summary] 类数=" + classes.size()
                + ", 逐类 isSatisfiable 合计=" + total + " ms"
                + ", 超过 " + threshold + " ms 的类=" + slow);
        System.out.flush();

        r.dispose();
    }
}
