import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import java.io.File;
import java.util.*;

public class Verify {
    public static void main(String[] args) throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLOntology o = m.loadOntologyFromOntologyDocument(new File(args[0]));
        System.out.println("Axioms: " + o.getAxiomCount());
        System.out.println("LogicalAxioms: " + o.getLogicalAxiomCount());
        System.out.println("Classes: " + o.getClassesInSignature().size());
        System.out.println("ObjectProps: " + o.getObjectPropertiesInSignature().size());
        System.out.println("DataProps: " + o.getDataPropertiesInSignature().size());
        System.out.println("Individuals: " + o.getIndividualsInSignature().size());

        int err = 0;
        for (OWLEntity e : o.getSignature()) {
            if (e.getIRI().toString().contains("owlapi/error")) {
                err++;
                System.out.println("  ERR ENTITY: " + e.getIRI());
            }
        }
        System.out.println("ErrorEntities: " + err);

        // 检查是否有未解析三元组残留
        int unparsed = 0;
        for (OWLAxiom ax : o.getAxioms()) {
            if (ax.toString().contains("owlapi/error")) unparsed++;
        }
        System.out.println("AxiomsWithError: " + unparsed);
        System.out.println(err == 0 && unparsed == 0 ? "RESULT: CLEAN" : "RESULT: HAS_ERRORS");
    }
}
