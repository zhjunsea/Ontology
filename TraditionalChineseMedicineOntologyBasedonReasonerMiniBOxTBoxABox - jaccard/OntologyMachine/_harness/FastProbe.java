import com.ocean.openlletresolver.OntologyModuleUtils;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 快速患者探针：只加载四诊模块（core/zhengzhuang/maixiang/shexiang + abox），
 * 秒级得到 八纲 / 判据命中 / 六经。
 *
 * 用法: java FastProbe <ontologyDir> <cases.txt>
 */
public class FastProbe {

    static final String BASE = "http://www.tcm-classics.org/jingfang/";
    static final String NS = "http://www.tcm-classics.org/jingfang#";
    static final String SUF = "_instance";

    static final Set<String> BAGANG = Set.of(
            "Biao", "Li", "Banbiaobanli", "Han", "Re", "Xu", "Shi", "Yin", "Yang");
    static final Set<String> LIUJING = Set.of(
            "Taiyangbing", "Yangmingbing", "Shaoyangbing",
            "Taiyinbing", "Shaoyinbing", "Jueyinbing");

    public static void main(String[] args) throws Exception {
        String ontDir = args[0];
        String casesFile = args[1];

        long t0 = System.currentTimeMillis();
        OWLOntologyManager m = OWLManager.createOWLOntologyManager();
        OWLDataFactory df = m.getOWLDataFactory();
        m.getIRIMappers().add(new AutoIRIMapper(new File(ontDir), true));

        String[] mods = {"core", "zhengzhuang", "maixiang", "shexiang",
                "shexiang-abox", "zhengzhuang-abox", "maixiang-abox"};
        OWLOntology probe = m.createOntology(IRI.create(NS + "_fastprobe"));
        for (String mod : mods) {
            OWLOntology mo = m.loadOntology(IRI.create(BASE + mod));
            m.addAxioms(probe, mo.axioms());
        }
        System.out.println("[probe] 公理=" + probe.getAxiomCount()
                + " subClassOf=" + probe.axioms(AxiomType.SUBCLASS_OF).count()
                + " equivalentClass=" + probe.axioms(AxiomType.EQUIVALENT_CLASSES).count()
                + " classAssertion=" + probe.axioms(AxiomType.CLASS_ASSERTION).count());
        OWLClass fareCls = df.getOWLClass(IRI.create(NS + "Fare"));
        OWLClass c6Cls = df.getOWLClass(IRI.create(NS + "Panju_C6"));
        long fareSubManual = probe.axioms(AxiomType.SUBCLASS_OF)
                .filter(a -> a.getSubClass().equals(fareCls)).count();
        long c6EqManual = probe.axioms(AxiomType.EQUIVALENT_CLASSES)
                .filter(a -> a.classExpressions().anyMatch(e -> e.equals(c6Cls))).count();
        System.out.println("[probe] Fare 的 subClassOf(索引)=" + probe.subClassAxiomsForSubClass(fareCls).count()
                + " (手工)=" + fareSubManual
                + " ; Panju_C6 的 equivalentClass(索引)=" + probe.equivalentClassesAxioms(c6Cls).count()
                + " (手工)=" + c6EqManual);
        System.out.println("[load] " + (System.currentTimeMillis() - t0) + " ms, 自身公理=" + probe.getAxiomCount()
                + ", 闭包公理=" + probe.axioms().count()
                + ", 类=" + probe.classesInSignature().count()
                + ", 个体=" + probe.individualsInSignature().count());
        for (String mod : mods) {
            OWLOntology mo = m.getOntology(IRI.create(BASE + mod));
            System.out.println("   loaded " + mod + " -> " + (mo != null)
                    + (mo != null ? (" 公理=" + mo.getAxiomCount() + " 类=" + mo.classesInSignature().count()) : ""));
        }

        // 复合脉映射（供合成复合脉）
        OWLOntology core = m.getOntology(IRI.create(BASE + "core"));
        OWLOntology mx = m.getOntology(IRI.create(BASE + "maixiang"));
        Set<OWLClass> pulseSubs = mx.classesInSignature().collect(Collectors.toSet());
        Map<OWLClass, Set<OWLClass>> pmap = OntologyModuleUtils.buildIntersectionCompositeMap(
                core, IRI.create(NS + "Maixiang"), pulseSubs);
        System.out.println("[composite] 脉=" + pmap.size());

        // 判据类
        List<OWLClass> panju = core.classesInSignature()
                .filter(c -> c.getIRI().getFragment().startsWith("Panju_"))
                .sorted(Comparator.comparing(c -> c.getIRI().getFragment()))
                .collect(Collectors.toList());

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

            OWLNamedIndividual patient = df.getOWLNamedIndividual(IRI.create("urn:fp:" + (idx++)));
            Set<OWLAxiom> ax = new LinkedHashSet<>();
            ax.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + "Huanzhe")), patient));
            addFindings(probe, df, ax, patient, df.getOWLObjectProperty(IRI.create(NS + "you_zhengzhuang")), syms);
            addFindings(probe, df, ax, patient, df.getOWLObjectProperty(IRI.create(NS + "you_maixiang")), pulses);
            addFindings(probe, df, ax, patient, df.getOWLObjectProperty(IRI.create(NS + "you_shexiang")), tongues);
            addFindings(probe, df, ax, patient, df.getOWLObjectProperty(IRI.create(NS + "you_fuzheng")), fuzhengs);
            OntologyModuleUtils.addSynthesizedComposites(df, ax, patient, toIris(pulses),
                    df.getOWLObjectProperty(IRI.create(NS + "you_maixiang")), pmap, NS, SUF, null);

            names.add(name);
            patients.add(patient);
            all.addAll(ax);
        }

        long t1 = System.currentTimeMillis();
        m.addAxioms(probe, new HashSet<>(all));
        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(probe);
        r.precomputeInferences(InferenceType.CLASS_ASSERTIONS);
        System.out.println("[reason] " + names.size() + " 患者, " + (System.currentTimeMillis() - t1) + " ms"
                + ", 一致=" + r.isConsistent());
        if (!names.isEmpty()) {
            Set<String> all0 = new TreeSet<>();
            for (OWLClass c : r.getTypes(patients.get(0), false).getFlattened()) {
                all0.add(c.getIRI().getFragment());
            }
            System.out.println("[debug] 患者0 全部类型(" + all0.size() + ")=" + all0);
            System.out.println("[debug] 患者0 直接类型=" + r.getTypes(patients.get(0), true).getFlattened());
        }

        for (int i = 0; i < names.size(); i++) {
            Set<String> bg = new TreeSet<>();
            Set<String> lj = new TreeSet<>();
            Set<String> pj = new TreeSet<>();
            for (OWLClass c : r.getTypes(patients.get(i), false).getFlattened()) {
                String f = c.getIRI().getFragment();
                if (BAGANG.contains(f)) bg.add(f);
                if (LIUJING.contains(f)) lj.add(f);
                if (f.startsWith("Panju_")) pj.add(f);
            }
            System.out.println("[" + names.get(i) + "] 八纲=" + bg + " 六经=" + lj + " 判据=" + pj);
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

    static void addFindings(OWLOntology ont, OWLDataFactory df, Set<OWLAxiom> ax,
                            OWLNamedIndividual patient, OWLObjectProperty prop, List<String> frags) {
        for (String f : frags) {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(IRI.create(NS + f + SUF));
            ax.add(df.getOWLObjectPropertyAssertionAxiom(prop, patient, ind));
            int before = ax.size();
            ont.axioms(AxiomType.CLASS_ASSERTION)
                    .filter(ca -> ca.getIndividual().equals(ind) && ca.getClassExpression().isOWLClass())
                    .forEach(ca -> ax.add(df.getOWLClassAssertionAxiom(
                            ca.getClassExpression().asOWLClass(), ind)));
            if (ax.size() == before) {
                System.out.println("[warn] " + f + " 个体无类断言(手工扫描)");
            }
        }
    }
}
