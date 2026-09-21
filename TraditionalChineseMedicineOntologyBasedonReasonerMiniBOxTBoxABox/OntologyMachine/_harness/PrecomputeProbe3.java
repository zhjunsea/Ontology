import com.ocean.openlletresolver.OntologyService;
import openllet.core.OpenlletOptions;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.Properties;

/**
 * 可切换 Openllet 选项的 precompute 探针，用于定位「分类耗时非确定」的开关。
 * 用法: java -Dpc3.opts="KEY=VALUE;KEY=VALUE" PrecomputeProbe3 <tcm-all.owl 路径>
 */
public class PrecomputeProbe3 {

    public static void main(String[] args) throws Exception {
        String path = args[0];

        String spec = System.getProperty("pc3.opts", "");
        Properties p = new Properties();
        if (!spec.isBlank()) {
            for (String kv : spec.split(";")) {
                if (kv.isBlank()) continue;
                int eq = kv.indexOf('=');
                if (eq <= 0) continue;
                String k = kv.substring(0, eq).trim();
                String v = kv.substring(eq + 1).trim();
                p.setProperty(k, v);
                System.out.println("[opt] " + k + " = " + v);
            }
        }
        if (!p.isEmpty()) {
            OpenlletOptions.setOptions(p);
        }
        System.out.println("[opt] effective USE_CD_CLASSIFICATION=" + OpenlletOptions.USE_CD_CLASSIFICATION
                + " USE_ADVANCED_CACHING=" + OpenlletOptions.USE_ADVANCED_CACHING
                + " USE_CACHING=" + OpenlletOptions.USE_CACHING
                + " USE_SMART_RESTORE=" + OpenlletOptions.USE_SMART_RESTORE
                + " USE_BACKJUMPING=" + OpenlletOptions.USE_BACKJUMPING
                + " ORDERED_CLASSIFICATION=" + OpenlletOptions.ORDERED_CLASSIFICATION);
        System.out.flush();

        long t0 = System.currentTimeMillis();
        OntologyService os = new OntologyService(path);
        OWLOntology tbox = os.gettBoxOntology();
        long t1 = System.currentTimeMillis();

        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(tbox);
        r.flush();
        long t2 = System.currentTimeMillis();

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
        long t3 = System.currentTimeMillis();

        System.out.println("[RESULT] load=" + (t1 - t0) + "ms flush=" + (t2 - t1)
                + "ms precompute=" + (t3 - t2) + "ms total=" + (t3 - t0)
                + "ms axioms=" + tbox.getAxiomCount());
        System.out.flush();
    }
}
