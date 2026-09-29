package com.ocean.ontologyframework.tcm;

import com.ocean.openlletresolver.OntologyModuleUtils;
import openllet.owlapi.OpenlletReasonerFactory;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLClassAtom;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 临时探针：复刻 worker 迷你模块，定位「Baitongtangzheng 未 realize 却激发 R84 加 Congbai」的机制。
 *
 * <p>变体对照：baseline（全 259 规则）/ 仅真武规则 / 仅白通规则 / 去白通等价定义 / 全本体。
 */
public class SwrlTypeProbeTest {

    static final String DIR =
            "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";
    static final String NS = "http://www.tcm-classics.org/jingfang#";

    static final List<String> CANDIDATES = List.of(
            "Zhenwutangzheng", "Mahuangfuzigancaotangzheng", "Baitongtangzheng",
            "Taohuatangzheng", "Jiegengtangzheng", "Zhufutangzheng",
            "Tongmaisinitangzheng", "Suanzaorentangzheng", "Dangguishengjiangyangroutangzheng");

    static final List<String> SYMS = List.of("Futong", "Sizhichenzhongtengtong", "Xiali",
            "Shenrundong", "Touxuan", "Xinxiajidong", "Kesou");
    static final List<String> PULSES = List.of("Weiximai");

    static OWLDataFactory df;
    static OWLNamedIndividual patient;

    @Test
    void probeVariants() throws Exception {
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        m.setOntologyLoaderConfiguration(new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT));
        m.getIRIMappers().add(new AutoIRIMapper(new File(DIR), true));
        try (Stream<Path> s = Files.walk(Paths.get(DIR))) {
            for (Path p : s.filter(x -> x.toString().endsWith(".owl")).collect(Collectors.toList())) {
                try {
                    m.loadOntologyFromOntologyDocument(p.toFile());
                } catch (Exception e) {
                    System.out.println("[PROBE] 跳过 " + p.getFileName() + " : " + e.getMessage());
                }
            }
        }
        df = m.getOWLDataFactory();

        OWLOntology tbox = m.createOntology(IRI.create("urn:probe:tbox"));
        Set<OWLAxiom> all = new HashSet<>();
        for (OWLOntology o : m.getOntologies()) {
            if (o.equals(tbox)) continue;
            all.addAll(o.axioms().collect(Collectors.toList()));
        }
        m.addAxioms(tbox, all);

        Set<OWLClass> initial = new HashSet<>();
        for (String f : CANDIDATES) initial.add(df.getOWLClass(IRI.create(NS + f)));
        for (String s : SYMS) initial.add(df.getOWLClass(IRI.create(NS + s)));
        for (String s : PULSES) initial.add(df.getOWLClass(IRI.create(NS + s)));
        for (String t : List.of("Huanzhe", "SizhenXinxi", "Zhengzhuang", "Maixiang",
                "Shexiang", "Fuzheng", "Tizhi", "Bagang", "Liujingbing",
                "Fangzheng", "Fangji", "Yaowu", "Yaozheng", "JianJiaZheng")) {
            initial.add(df.getOWLClass(IRI.create(NS + t)));
        }
        initial.add(df.getOWLClass(IRI.create(NS + "Shaoyinbing")));

        Set<OWLClass> keep = OntologyModuleUtils.collectClassClosure(tbox, initial);
        Set<OWLAxiom> module = OntologyModuleUtils.extractTBoxModule(tbox, keep);
        module.removeIf(ax -> ax instanceof OWLDisjointClassesAxiom);
        module.addAll(tbox.axioms(AxiomType.SWRL_RULE)
                .filter(ax -> ax instanceof SWRLRule).collect(Collectors.toSet()));

        Set<OWLAxiom> abox = buildAbox();

        run(m, "baseline(全259规则)", module, abox);
        run(m, "仅真武规则", filterRules(module, "Zhenwutangzheng"), abox);
        run(m, "仅白通规则", filterRules(module, "Baitongtangzheng"), abox);
        run(m, "去白通等价定义", removeEq(module, "Baitongtangzheng"), abox);
        Set<OWLAxiom> full = new HashSet<>();
        for (OWLAxiom a : all) if (!(a instanceof OWLDisjointClassesAxiom)) full.add(a);
        run(m, "全本体(去互斥)", full, abox);
    }

    static Set<OWLAxiom> buildAbox() {
        Set<OWLAxiom> abox = new HashSet<>();
        patient = df.getOWLNamedIndividual(IRI.create(NS + "ProbePatient"));
        OWLObjectProperty yz = df.getOWLObjectProperty(IRI.create(NS + "you_zhengzhuang"));
        OWLObjectProperty ym = df.getOWLObjectProperty(IRI.create(NS + "you_maixiang"));
        abox.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + "Huanzhe")), patient));
        abox.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + "Shaoyinbing")), patient));
        for (String s : SYMS) {
            abox.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + s)), patient));
            OWLNamedIndividual obj = df.getOWLNamedIndividual(IRI.create(NS + s + "_instance"));
            abox.add(df.getOWLObjectPropertyAssertionAxiom(yz, patient, obj));
            abox.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + s)), obj));
        }
        for (String s : PULSES) {
            abox.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + s)), patient));
            OWLNamedIndividual obj = df.getOWLNamedIndividual(IRI.create(NS + s + "_instance"));
            abox.add(df.getOWLObjectPropertyAssertionAxiom(ym, patient, obj));
            abox.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + s)), obj));
        }
        return abox;
    }

    static Set<OWLAxiom> filterRules(Set<OWLAxiom> module, String fzFrag) {
        Set<OWLAxiom> out = new HashSet<>();
        for (OWLAxiom ax : module) {
            if (!(ax instanceof SWRLRule r)) { out.add(ax); continue; }
            boolean hit = false;
            for (SWRLAtom a : r.getBody()) {
                if (a instanceof SWRLClassAtom ca && ca.getPredicate().isOWLClass()
                        && fzFrag.equals(ca.getPredicate().asOWLClass().getIRI().getFragment())) {
                    hit = true; break;
                }
            }
            if (hit) out.add(ax);
        }
        return out;
    }

    static Set<OWLAxiom> removeEq(Set<OWLAxiom> module, String fzFrag) {
        OWLClass c = df.getOWLClass(IRI.create(NS + fzFrag));
        Set<OWLAxiom> out = new HashSet<>();
        for (OWLAxiom ax : module) {
            if (ax instanceof OWLEquivalentClassesAxiom eq && eq.contains(c)) continue;
            out.add(ax);
        }
        return out;
    }

    static void run(OWLOntologyManager m, String label, Set<OWLAxiom> axioms, Set<OWLAxiom> abox) throws Exception {
        OWLOntology ont = m.createOntology(IRI.create("urn:probe:" + label));
        m.addAxioms(ont, axioms);
        m.addAxioms(ont, abox);
        OWLReasoner r = OpenlletReasonerFactory.getInstance().createReasoner(ont);
        r.precomputeInferences(InferenceType.CLASS_ASSERTIONS, InferenceType.DATA_PROPERTY_ASSERTIONS);

        List<String> fz = new ArrayList<>();
        for (var node : r.getTypes(patient, false).getNodes()) {
            for (OWLClass c : node.getEntities()) {
                String f = c.getIRI().getFragment();
                if (f.endsWith("zheng")) fz.add(f);
            }
        }
        Collections.sort(fz);
        OWLDataProperty add = df.getOWLDataProperty(IRI.create(NS + "shouldAddHerbName"));
        OWLDataProperty rem = df.getOWLDataProperty(IRI.create(NS + "shouldRemoveHerbName"));
        List<String> adds = r.getDataPropertyValues(patient, add).stream()
                .map(OWLLiteral::getLiteral).sorted().collect(Collectors.toList());
        List<String> rems = r.getDataPropertyValues(patient, rem).stream()
                .map(OWLLiteral::getLiteral).sorted().collect(Collectors.toList());
        OWLClass baitong = df.getOWLClass(IRI.create(NS + "Baitongtangzheng"));
        System.out.println("[VARIANT] " + label
                + " | axioms=" + ont.getAxiomCount()
                + " | consistent=" + r.isConsistent()
                + " | BaitongSat=" + r.isSatisfiable(baitong)
                + " | patientIsBaitong=" + r.getTypes(patient, false).containsEntity(baitong)
                + " | fz=" + fz
                + " | add=" + adds + " | rem=" + rems);
        r.dispose();
    }
}
