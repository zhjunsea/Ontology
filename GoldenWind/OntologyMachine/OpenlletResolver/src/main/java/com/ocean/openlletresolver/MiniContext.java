package com.ocean.openlletresolver;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 迷你本体推理上下文：封装临时 OWLOntologyManager / OWLOntology / OWLReasoner，
 * 并提供按个体 IRI 缓存类型推断结果的接口。
 *
 * 与任何业务无关，可被任何基于 OWL + Openllet 的项目复用。
 */
public class MiniContext {

    private final OWLOntologyManager manager;
    private final OWLOntology ontology;
    private final OWLDataFactory df;
    private final OWLReasoner reasoner;

    /** individualIri -> 推断出的类型集合缓存 */
    private final Map<String, Set<OWLClass>> typeCache = new ConcurrentHashMap<>();

    public MiniContext(OWLOntologyManager manager,
                       OWLOntology ontology,
                       OWLDataFactory df,
                       OWLReasoner reasoner) {
        this.manager = manager;
        this.ontology = ontology;
        this.df = df;
        this.reasoner = reasoner;
    }

    public Set<OWLClass> getTypes(String individualIri) {
        return typeCache.computeIfAbsent(individualIri, iri -> {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(iri));
            return reasoner.getTypes(ind, false).getFlattened();
        });
    }

    public boolean isConsistent() {
        return reasoner.isConsistent();
    }

    public OWLOntology getOntology() { return ontology; }
    public OWLOntologyManager getManager() { return manager; }
    public OWLDataFactory getDataFactory() { return df; }
    public OWLReasoner getReasoner() { return reasoner; }

    public void dispose() {
        try { reasoner.dispose(); } catch (Exception ignored) {}
        try { manager.removeOntology(ontology); } catch (Exception ignored) {}
    }
}