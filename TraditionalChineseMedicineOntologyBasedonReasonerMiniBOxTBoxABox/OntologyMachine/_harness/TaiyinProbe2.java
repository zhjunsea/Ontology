import com.ocean.openlletresolver.OntologyModuleUtils;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 复刻新版 TaiyinbingDefinitionTest 的逻辑（含 assertFindingTypes），快速验证场景。
 * 用法：TaiyinProbe2 [precompute|lazy] [case1 case2 ...]
 */
public class TaiyinProbe2 {

    static final String NS = "http://www.tcm-classics.org/jingfang#";
    static final String SUF = "_instance";
    static final Set<String> SIX = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing");

    static OWLOntology lookup;
    static OWLDataFactory df;
    static final Map<String, String> PATIENTS = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "lazy";
        Set<String> want = new HashSet<>(Arrays.asList(args).subList(Math.min(1, args.length), args.length));

        String dir = "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";
        Path mainPath = Paths.get(dir, "tcm-all.owl");
        Path aboxPath = Paths.get(dir, "tcm-all-abox.owl");

        OWLOntologyManager loadMgr = OWLManager.createOWLOntologyManager();
        loadMgr.getIRIMappers().add(new AutoIRIMapper(new File(dir), true));
        loadMgr.loadOntologyFromOntologyDocument(mainPath.toFile());
        Set<OWLAxiom> tboxAxioms = new LinkedHashSet<>();
        for (OWLOntology o : loadMgr.getOntologies()) tboxAxioms.addAll(o.getAxioms());
        System.out.println("[1] TBox 闭包公理数 = " + tboxAxioms.size());

        loadMgr.loadOntologyFromOntologyDocument(aboxPath.toFile());
        Set<OWLAxiom> lookupAxioms = new LinkedHashSet<>();
        for (OWLOntology o : loadMgr.getOntologies()) lookupAxioms.addAll(o.getAxioms());
        lookup = loadMgr.createOntology(lookupAxioms);
        OWLOntology tbox = loadMgr.createOntology(tboxAxioms);
        df = loadMgr.getOWLDataFactory();

        Set<OWLAxiom> abox = new HashSet<>();
        maybe(want, "fumanYellow", () -> addPatient(abox, "fumanYellow", List.of("Fuman"), List.of(), List.of("YellowCoating"), List.of()));
        maybe(want, "daChengQiTang", () -> addPatient(abox, "daChengQiTang",
                List.of("Danrebuhan", "Kouke", "Chaore", "Zaoshi", "Zhanyu", "Fuman", "Futong", "Juan"),
                List.of("Chenshimai"), List.of("YellowCoating", "DryCoating", "TongueWithThorns"), List.of()));
        maybe(want, "fumanRuoMai", () -> addPatient(abox, "fumanRuoMai", List.of("Fuman"), List.of("Ruomai"), List.of(), List.of()));
        maybe(want, "fumanWhite", () -> addPatient(abox, "fumanWhite", List.of("Fuman"), List.of("Ruomai"), List.of("WhiteCoating"), List.of()));
        maybe(want, "fumanPaleWhite", () -> addPatient(abox, "fumanPaleWhite", List.of("Fuman"), List.of("Chenchimai"), List.of("PaleWhiteTongue"), List.of()));

        OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
        OWLOntology ont = mgr.createOntology(IRI.create("urn:probe2"));
        mgr.addAxioms(ont, tbox.axioms());
        mgr.addAxioms(ont, abox);
        System.out.println("[2] 推理本体公理数 = " + ont.getAxiomCount() + "，患者数 = " + PATIENTS.size() + "，模式 = " + mode);

        System.out.println("[3] 开始推理...");
        long t0 = System.nanoTime();
        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(ont);
        if ("precompute".equals(mode)) {
            r.precomputeInferences(InferenceType.CLASS_ASSERTIONS);
        }
        System.out.println("[3] 推理准备完成，用时 " + (System.nanoTime() - t0) / 1_000_000_000 + "s");

        for (Map.Entry<String, String> e : PATIENTS.entrySet()) {
            long t1 = System.nanoTime();
            OWLNamedIndividual p = df.getOWLNamedIndividual(IRI.create(e.getValue()));
            Set<String> all = r.getTypes(p, false).getFlattened().stream()
                    .map(c -> c.getIRI().getFragment()).collect(Collectors.toCollection(TreeSet::new));
            Set<String> six = all.stream().filter(SIX::contains).collect(Collectors.toCollection(TreeSet::new));
            System.out.println("[4] " + e.getKey() + " 六经 = " + six
                    + "  (耗时 " + (System.nanoTime() - t1) / 1_000_000_000 + "s)");
            System.out.println("        全部类型 = " + all);
        }
        r.dispose();
        Runtime.getRuntime().halt(0);
    }

    static void maybe(Set<String> want, String name, Runnable r) {
        if (want.isEmpty() || want.contains(name)) r.run();
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
