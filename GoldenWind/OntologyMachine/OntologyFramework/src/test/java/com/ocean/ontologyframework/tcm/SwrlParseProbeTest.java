package com.ocean.ontologyframework.tcm;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SwrlParseProbeTest {

    static final String DIR =
            "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";

    @Test
    void parse() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        m.setOntologyLoaderConfiguration(new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT));
        m.getIRIMappers().add(new AutoIRIMapper(new File(DIR), true));

        List<Path> files;
        try (Stream<Path> s1 = Files.list(Paths.get(DIR));
             Stream<Path> s2 = Files.list(Paths.get(DIR, "fangzheng"))) {
            files = Stream.concat(s1, s2)
                    .filter(p -> p.toString().endsWith(".owl"))
                    .collect(Collectors.toList());
        }
        for (Path p : files) {
            try {
                m.loadOntologyFromOntologyDocument(p.toFile());
            } catch (Exception e) {
                System.out.println("[probe] skip " + p.getFileName() + " : " + e.getMessage());
            }
        }
        System.out.println("[probe] loaded ontologies=" + m.getOntologies().size());

        Set<SWRLRule> rules = m.ontologies()
                .flatMap(o -> o.axioms(AxiomType.SWRL_RULE))
                .collect(Collectors.toSet());
        System.out.println("[probe] SWRL rules=" + rules.size());

        OWLDataFactory df = m.getOWLDataFactory();
        OWLClass fz = df.getOWLClass(IRI.create("http://www.tcm-classics.org/jingfang#Fangzheng"));
        int cnt = 0;
        for (OWLOntology o : m.getOntologies()) {
            for (var ax : o.getAxioms(AxiomType.SUBCLASS_OF)) {
                if (ax.getSuperClass().isOWLClass() && ax.getSuperClass().asOWLClass().equals(fz)) cnt++;
            }
        }
        System.out.println("[probe] direct Fangzheng subclasses=" + cnt);

        int i = 0;
        for (SWRLRule r : rules) {
            if (i++ >= 3) break;
            System.out.println("[probe] --- rule " + i + " ---");
            for (SWRLAtom a : r.getBody()) System.out.println("[probe]   B: " + a);
            for (SWRLAtom a : r.getHead()) System.out.println("[probe]   H: " + a);
        }
    }
}
