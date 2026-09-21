import com.ocean.openlletresolver.OntologyService;
import com.ocean.openlletresolver.OntologyModuleUtils;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 患者模拟探针（批量版）：一次性把所有患者加入本体，只推理一次，再逐个查询。
 *
 * 用法: java PatientProbe <tcm-all.owl> <tcm-all-abox.owl> <cases.txt>
 *   cases.txt 每行: 名称|症状1;症状2|脉象1;脉象2|舌象|腹证   （发现写 fragment）
 */
public class PatientProbe {

    static final String NS = "http://www.tcm-classics.org/jingfang#";
    static final String SUF = "_instance";

    /** 八纲（含病性层） */
    static final Set<String> BAGANG = Set.of(
            "Biao", "Li", "Banbiaobanli", "Han", "Re", "Xu", "Shi", "Yin", "Yang");
    /** 六经 */
    static final Set<String> LIUJING = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing");

    public static void main(String[] args) throws Exception {
        String tboxPath = args[0];
        String aboxPath = args[1];
        String casesFile = args[2];

        long t0 = System.currentTimeMillis();
        OntologyService os = new OntologyService(tboxPath);
        OWLOntology ont = os.gettBoxOntology();
        OWLOntologyManager mgr = ont.getOWLOntologyManager();
        OWLDataFactory df = mgr.getOWLDataFactory();
        System.out.println("[load tbox] " + (System.currentTimeMillis() - t0) + " ms, 公理=" + ont.getAxiomCount());

        OntologyService aboxSvc = new OntologyService(aboxPath);
        OWLOntology abox = aboxSvc.gettBoxOntology();
        System.out.println("[load abox] 公理=" + abox.getAxiomCount()
                + ", 个体=" + abox.individualsInSignature().count());

        OWLReasoner r0 = new OpenlletReasonerFactory().createReasoner(ont);
        r0.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        Set<OWLClass> pulseSubs = r0.getSubClasses(df.getOWLClass(IRI.create(NS + "Maixiang")), false).getFlattened();
        Set<OWLClass> symSubs = r0.getSubClasses(df.getOWLClass(IRI.create(NS + "Zhengzhuang")), false).getFlattened();
        r0.dispose();

        Map<OWLClass, Set<OWLClass>> pmap = OntologyModuleUtils.buildIntersectionCompositeMap(
                ont, IRI.create(NS + "Maixiang"), pulseSubs);
        Map<OWLClass, Set<OWLClass>> smap = OntologyModuleUtils.buildIntersectionCompositeMap(
                ont, IRI.create(NS + "Zhengzhuang"), symSubs);
        System.out.println("[composite] 脉=" + pmap.size() + ", 症状=" + smap.size());

        List<String> lines = Files.readAllLines(Paths.get(casesFile), StandardCharsets.UTF_8);
        List<String> names = new ArrayList<>();
        List<OWLNamedIndividual> patients = new ArrayList<>();
        List<OWLAxiom> all = new ArrayList<>();
        int idx = 0;
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\|", -1);
            String name = p[0];
            List<String> syms = split(p.length > 1 ? p[1] : "");
            List<String> pulses = split(p.length > 2 ? p[2] : "");
            List<String> tongues = split(p.length > 3 ? p[3] : "");
            List<String> fuzhengs = split(p.length > 4 ? p[4] : "");

            OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create("urn:probe:patient:" + (idx++)));
            Set<OWLAxiom> ax = new LinkedHashSet<>();
            ax.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + "Huanzhe")), patient));
            addFindings(abox, df, ax, patient, df.getOWLObjectProperty(IRI.create(NS + "you_zhengzhuang")), syms);
            addFindings(abox, df, ax, patient, df.getOWLObjectProperty(IRI.create(NS + "you_maixiang")), pulses);
            addFindings(abox, df, ax, patient, df.getOWLObjectProperty(IRI.create(NS + "you_shexiang")), tongues);
            addFindings(abox, df, ax, patient, df.getOWLObjectProperty(IRI.create(NS + "you_fuzheng")), fuzhengs);
            OntologyModuleUtils.addSynthesizedComposites(df, ax, patient, toIris(syms),
                    df.getOWLObjectProperty(IRI.create(NS + "you_zhengzhuang")), smap, NS, SUF, null);
            OntologyModuleUtils.addSynthesizedComposites(df, ax, patient, toIris(pulses),
                    df.getOWLObjectProperty(IRI.create(NS + "you_maixiang")), pmap, NS, SUF, null);

            names.add(name);
            patients.add(patient);
            all.addAll(ax);
        }

        long t1 = System.currentTimeMillis();
        List<OWLOntologyChange> ch = new ArrayList<>();
        for (OWLAxiom a : all) ch.add(new AddAxiom(ont, a));
        mgr.applyChanges(ch);
        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(ont);
        r.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        System.out.println("[reason] 加入 " + names.size() + " 患者, 推理 " + (System.currentTimeMillis() - t1) + " ms");

        for (int i = 0; i < names.size(); i++) {
            Set<String> bg = new TreeSet<>();
            Set<String> lj = new TreeSet<>();
            for (OWLClass c : r.getTypes(patients.get(i), false).getFlattened()) {
                String f = c.getIRI().getFragment();
                if (BAGANG.contains(f)) bg.add(f);
                if (LIUJING.contains(f)) lj.add(f);
            }
            System.out.println("[" + names.get(i) + "] 八纲=" + bg + " 六经=" + lj);
        }
        r.dispose();
        System.out.println("[done] total=" + (System.currentTimeMillis() - t0) + " ms");
    }

    static List<String> split(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split("[;,]")).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
    }

    static List<String> toIris(List<String> frags) {
        return frags.stream().map(f -> NS + f + SUF).collect(Collectors.toList());
    }

    static void addFindings(OWLOntology abox, OWLDataFactory df, Set<OWLAxiom> ax,
                            OWLNamedIndividual patient, OWLObjectProperty prop, List<String> frags) {
        for (String f : frags) {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(NS + f + SUF));
            ax.add(df.getOWLObjectPropertyAssertionAxiom(prop, patient, ind));
            abox.classAssertionAxioms(ind).forEach(ca -> {
                if (ca.getClassExpression().isOWLClass()) {
                    ax.add(df.getOWLClassAssertionAxiom(ca.getClassExpression().asOWLClass(), ind));
                }
            });
            ax.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + f)), patient));
        }
    }
}
