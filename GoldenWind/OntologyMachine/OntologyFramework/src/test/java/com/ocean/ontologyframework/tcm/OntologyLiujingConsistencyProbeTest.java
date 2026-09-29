package com.ocean.ontologyframework.tcm;

import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OntologyLiujingConsistencyProbeTest {

    static final String DIR = "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";
    static final String NS = JingfangTestSupport.NS;
    static final Set<String> LJ = Set.of("Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing");

    @Test
    void probe() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        m.getIRIMappers().add(new AutoIRIMapper(new File(DIR), true));
        try (Stream<Path> s = Files.walk(Paths.get(DIR))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".owl")).collect(Collectors.toList())) {
                try { m.loadOntologyFromOntologyDocument(p.toFile()); } catch (Exception ignored) { }
            }
        }
        OWLDataFactory df = m.getOWLDataFactory();
        OWLClass fz = df.getOWLClass(IRI.create(NS + "Fangzheng"));

        Set<OWLClass> fzClasses = new TreeSet<>(Comparator.comparing(c -> c.getIRI().getFragment()));
        for (OWLOntology o : m.getOntologies()) {
            o.axioms(AxiomType.SUBCLASS_OF).forEach(ax -> {
                if (ax.getSuperClass().isOWLClass() && ax.getSuperClass().asOWLClass().equals(fz)
                        && ax.getSubClass().isOWLClass()) {
                    fzClasses.add(ax.getSubClass().asOWLClass());
                }
            });
        }

        int mismatch = 0;
        for (OWLClass c : fzClasses) {
            String name = c.getIRI().getFragment();
            Set<String> belongs = new TreeSet<>();
            Set<String> eqLj = new TreeSet<>();
            for (OWLOntology o : m.getOntologies()) {
                for (OWLAnnotationAssertionAxiom ax : o.annotationAssertionAxioms(c.getIRI()).collect(Collectors.toList())) {
                    if ("belongsToLiujing".equals(ax.getProperty().getIRI().getFragment())
                            && ax.getValue() instanceof IRI iri) {
                        String f = iri.getFragment();
                        if (LJ.contains(f)) belongs.add(f);
                    }
                }
                for (OWLEquivalentClassesAxiom ax : o.equivalentClassesAxioms(c).collect(Collectors.toList())) {
                    for (OWLClassExpression ce : ax.getClassExpressions()) {
                        if (!ce.isOWLClass()) collectLj(ce, eqLj);
                    }
                }
            }
            if (!belongs.isEmpty() && !eqLj.isEmpty() && !belongs.equals(eqLj)) {
                mismatch++;
                System.out.println("[LJ-MISMATCH] " + name + " belongs=" + belongs + " eq=" + eqLj);
            }
        }
        System.out.println("[LJ-PROBE] 方证=" + fzClasses.size() + " 不一致=" + mismatch);
    }

    static void collectLj(OWLClassExpression e, Set<String> out) {
        if (e.isOWLClass()) {
            String f = e.asOWLClass().getIRI().getFragment();
            if (LJ.contains(f)) out.add(f);
            return;
        }
        e.nestedClassExpressions().forEach(n -> {
            if (n.isOWLClass()) {
                String f = n.asOWLClass().getIRI().getFragment();
                if (LJ.contains(f)) out.add(f);
            }
        });
    }
}
