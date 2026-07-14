package com.ocean.openlletresolver;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.OWLOntologyMerger;

import java.io.File;

//本函数合并TBox和ABox数据

public class OwlOntologyBuilder {

    public static OWLOntology createMergedOntology(File tboxFile, File aboxFile)
            throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();

        OWLOntology tboxOntology = manager.loadOntologyFromOntologyDocument(tboxFile);
        OWLOntology aboxOntology = manager.loadOntologyFromOntologyDocument(aboxFile);

        OWLOntologyMerger merger = new OWLOntologyMerger(manager);
        IRI mergedOntologyIRI = IRI.create("http://example.org/merged-ontology");

        // ⭐ 修正：Manager 在前，IRI 在后
        return merger.createMergedOntology(manager, mergedOntologyIRI);
    }

    public static OWLOntology mergeInMemory(OWLOntology tbox, OWLOntology abox)
            throws OWLOntologyCreationException {
        OWLOntologyManager manager = tbox.getOWLOntologyManager();
        IRI mergedIRI = IRI.create("http://example.org/merged-ontology");

        OWLOntology merged = manager.createOntology(mergedIRI);
        manager.addAxioms(merged, tbox.axioms());
        manager.addAxioms(merged, abox.axioms());

        return merged;
    }
}