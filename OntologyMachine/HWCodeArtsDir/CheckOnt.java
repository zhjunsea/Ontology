import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.SimpleIRIMapper;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class CheckOnt {
    static void addCatalog(OWLOntologyManager m, String base) {
        String[][] map = {
                {"http://example.org/plumblossom/all", "plumblossom-all.owl"},
                {"http://example.org/plumblossom/tbox", "plumblossom-tbox.owl"},
                {"http://example.org/plumblossom/abox", "plumblossom-abox.owl"},
                {"http://example.org/plumblossom/core", "plumblossom-core.owl"},
                {"http://example.org/plumblossom/divination", "plumblossom-divination.owl"},
                {"http://example.org/plumblossom/yijing", "plumblossom-yijing.owl"},
                {"http://example.org/plumblossom/relations", "plumblossom-relations.owl"},
                {"http://example.org/plumblossom/symbols", "plumblossom-symbols.owl"},
                {"http://example.org/plumblossom/omens", "plumblossom-omens.owl"},
                {"http://example.org/plumblossom/judgement", "plumblossom-judgement.owl"},
                {"http://example.org/plumblossom/calendars", "plumblossom-calendars.owl"},
                {"http://example.org/plumblossom/rules", "plumblossom-rules.owl"},
                {"http://example.org/plumblossom/term", "plumblossom-terminology.ttl"},
        };
        for (String[] e : map) {
            m.getIRIMappers().add(new SimpleIRIMapper(IRI.create(e[0]), IRI.create(new File(base, e[1]))));
        }
    }

    public static void main(String[] args) throws Exception {
        String base = args.length > 0 ? args[0]
                : "D:/work/Ontology/OntologyMachine/OntologyFrameworkPlumBlossom/ontology";
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        addCatalog(m, base);
        String file = args.length > 1 ? args[1] : "plumblossom-all.owl";
        OWLOntology o = m.loadOntologyFromOntologyDocument(new File(base, file));
        System.out.println("### loaded file = " + file);
        Set<OWLOntology> all = new LinkedHashSet<>();
        all.add(o);
        o.importsClosure().forEach(all::add);
        OWLDataFactory df = m.getOWLDataFactory();
        String[] iris = (args.length > 2 ? args[2] : "http://example.org/plumblossom/calendars/stemName").split(",");
        for (String s : iris) {
            IRI iri = IRI.create(s.trim());
            OWLDataProperty dp = df.getOWLDataProperty(iri);
            OWLObjectProperty op = df.getOWLObjectProperty(iri);
            System.out.println("---- " + s.trim() + " ----");
            for (OWLOntology x : all) {
                for (OWLAxiom a : x.axioms().collect(Collectors.toList())) {
                    if (a.toString().contains(iri.getShortForm())) {
                        System.out.println("  [" + a.getAxiomType() + "] " + a);
                    }
                }
            }
        }
        System.out.println("== global both (data AND object) properties ==");
        Set<IRI> dps = new HashSet<>();
        Set<IRI> ops = new HashSet<>();
        for (OWLOntology x : all) {
            x.dataPropertiesInSignature().forEach(p -> dps.add(p.getIRI()));
            x.objectPropertiesInSignature().forEach(p -> ops.add(p.getIRI()));
        }
        Set<IRI> both = new HashSet<>(dps);
        both.retainAll(ops);
        System.out.println("  dataProps=" + dps.size() + " objectProps=" + ops.size() + " both=" + both.size());
        for (IRI b : both) System.out.println("    " + b);
    }
}
