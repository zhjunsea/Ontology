import com.ocean.openlletresolver.OntologyModuleUtils;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用「精简模块」（与 SizhenToHerbModificationFlowTest 相同的加载集）快速验证
 * TaiyinbingDefinitionTest 的 5 个场景逻辑（含 assertFindingTypes）。
 * 精简模块 ⊂ 完整 TBox ⇒ 精简模块能推出的，完整 TBox 必能推出（单调性）。
 */
public class TaiyinProbe3 {

    static final String NS = "http://www.tcm-classics.org/jingfang#";
    static final String SUF = "_instance";
    static final String DIR = "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";
    static final Set<String> SIX = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing");

    static OWLOntology lookup;
    static OWLDataFactory df;
    static final Map<String, String> PATIENTS = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        OWLOntologyManager loadMgr = OWLManager.createOWLOntologyManager();
        loadMgr.getIRIMappers().add(new AutoIRIMapper(new File(DIR), true));
        for (String mod : List.of(
                "tcm-core.owl",
                "tcm-zhengzhuang.owl", "tcm-zhengzhuang-abox.owl",
                "tcm-maixiang.owl", "tcm-maixiang-abox.owl",
                "tcm-shexiang.owl", "tcm-shexiang-abox.owl")) {
            File f = new File(DIR, mod);
            if (f.isFile()) loadMgr.loadOntologyFromOntologyDocument(f);
        }
        Set<OWLAxiom> all = new LinkedHashSet<>();
        for (OWLOntology o : loadMgr.getOntologies()) all.addAll(o.getAxioms());
        lookup = loadMgr.createOntology(all);
        df = loadMgr.getOWLDataFactory();
        System.out.println("[1] 模块公理数 = " + all.size());

        Set<OWLAxiom> abox = new HashSet<>();
        addPatient(abox, "fumanYellow", List.of("Fuman"), List.of(), List.of("YellowCoating"), List.of());
        addPatient(abox, "daChengQiTang",
                List.of("Danrebuhan", "Kouke", "Chaore", "Zaoshi", "Zhanyu", "Fuman", "Futong", "Juan"),
                List.of("Chenshimai"), List.of("YellowCoating", "DryCoating", "TongueWithThorns"), List.of());
        addPatient(abox, "fumanRuoMai", List.of("Fuman"), List.of("Ruomai"), List.of(), List.of());
        addPatient(abox, "fumanWhite", List.of("Fuman"), List.of("Ruomai"), List.of("WhiteCoating"), List.of());
        addPatient(abox, "fumanPaleWhite", List.of("Fuman"), List.of("Chenchimai"), List.of("PaleWhiteTongue"), List.of());

        OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
        OWLOntology ont = mgr.createOntology(IRI.create("urn:probe3"));
        mgr.addAxioms(ont, all);
        mgr.addAxioms(ont, abox);
        System.out.println("[2] 推理本体公理数 = " + ont.getAxiomCount());

        long t0 = System.nanoTime();
        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(ont);
        System.out.println("[3] 推理准备 " + (System.nanoTime() - t0) / 1_000_000_000 + "s");

        for (Map.Entry<String, String> e : PATIENTS.entrySet()) {
            long t1 = System.nanoTime();
            OWLNamedIndividual p = df.getOWLNamedIndividual(IRI.create(e.getValue()));
            Set<String> allTypes = r.getTypes(p, false).getFlattened().stream()
                    .map(c -> c.getIRI().getFragment()).collect(Collectors.toCollection(TreeSet::new));
            Set<String> six = allTypes.stream().filter(SIX::contains).collect(Collectors.toCollection(TreeSet::new));
            System.out.println("[4] " + e.getKey() + " 六经 = " + six + "  (" + (System.nanoTime() - t1) / 1_000_000_000 + "s)");
            System.out.println("        全部 = " + allTypes);
        }
        r.dispose();
        Runtime.getRuntime().halt(0);
    }

    static void addPatient(Set<OWLAxiom> acc, String scenario,
                           List<String> syms, List<String> pulses,
                           List<String> tongues, List<String> fuzhengs) {
        String iri = NS + "VerifyPatient_" + scenario;
        PATIENTS.put(scenario, iri);
        OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create(iri));
        acc.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + "Huanzhe")), patient));
        add(acc, patient, "you_zhengzhuang", syms);
        add(acc, patient, "you_maixiang", pulses);
        add(acc, patient, "you_shexiang", tongues);
        add(acc, patient, "you_fuzheng", fuzhengs);
        assertFinding(acc, patient, syms);
        assertFinding(acc, patient, pulses);
        assertFinding(acc, patient, tongues);
        assertFinding(acc, patient, fuzhengs);
    }

    static void assertFinding(Set<OWLAxiom> acc, OWLNamedIndividual p, List<String> iris) {
        if (iris == null) return;
        for (String f : iris) {
            if (f == null || f.isBlank()) continue;
            acc.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + f)), p));
        }
    }

    static void add(Set<OWLAxiom> acc, OWLNamedIndividual p, String prop, List<String> iris) {
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(
                lookup, df, acc, df.getOWLObjectProperty(IRI.create(NS + prop)), p,
                iris.stream().map(f -> NS + f + SUF).collect(Collectors.toList()), NS);
    }
}
