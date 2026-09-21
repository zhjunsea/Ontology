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

/** 快速定位 TaiyinbingDefinitionTest 推理全空的根因（不依赖 JUnit）。 */
public class TaiyinProbe {

    static final String NS = "http://www.tcm-classics.org/jingfang#";

    public static void main(String[] args) throws Exception {
        String dir = "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";
        Path mainPath = Paths.get(dir, "tcm-all.owl");
        Path aboxPath = Paths.get(dir, "tcm-all-abox.owl");

        OWLOntologyManager loadMgr = OWLManager.createOWLOntologyManager();
        loadMgr.getIRIMappers().add(new AutoIRIMapper(new File(dir), true));
        loadMgr.loadOntologyFromOntologyDocument(mainPath.toFile());
        System.out.println("[1] 加载 tcm-all.owl 后本体数 = " + loadMgr.getOntologies().size());

        Set<OWLAxiom> tboxAxioms = new LinkedHashSet<>();
        for (OWLOntology o : loadMgr.getOntologies()) tboxAxioms.addAll(o.getAxioms());
        System.out.println("[2] TBox 闭包公理数 = " + tboxAxioms.size());

        loadMgr.loadOntologyFromOntologyDocument(aboxPath.toFile());
        System.out.println("[3] 加载 tcm-all-abox.owl 后本体数 = " + loadMgr.getOntologies().size());
        Set<OWLAxiom> lookupAxioms = new LinkedHashSet<>();
        for (OWLOntology o : loadMgr.getOntologies()) lookupAxioms.addAll(o.getAxioms());
        System.out.println("[4] lookup 公理数 = " + lookupAxioms.size());

        OWLOntology lookup = loadMgr.createOntology(lookupAxioms);
        OWLOntology tbox = loadMgr.createOntology(tboxAxioms);
        OWLDataFactory df = loadMgr.getOWLDataFactory();

        OWLClass taiyin = df.getOWLClass(IRI.create(NS + "Taiyinbing"));
        OWLClass fuman = df.getOWLClass(IRI.create(NS + "Fuman"));
        System.out.println("[5] tbox 含 Taiyinbing 类? " + tbox.containsClassInSignature(taiyin.getIRI()));
        System.out.println("[5] tbox 含 Fuman 类? " + tbox.containsClassInSignature(fuman.getIRI()));
        System.out.println("[5] tbox 中 Taiyinbing 等价类 = " + tbox.equivalentClassesAxioms(taiyin).count());
        System.out.println("[5] tbox 中 Li/Yin 子类公理数 = "
                + tbox.subClassAxiomsForSuperClass(df.getOWLClass(IRI.create(NS + "Li"))).count());

        // 患者 fumanRuoMai：腹满 + 弱脉
        OWLNamedIndividual p = df.getOWLNamedIndividual(IRI.create(NS + "ProbePatient"));
        Set<OWLAxiom> acc = new LinkedHashSet<>();
        acc.add(df.getOWLClassAssertionAxiom(df.getOWLClass(IRI.create(NS + "Huanzhe")), p));
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(lookup, df, acc,
                df.getOWLObjectProperty(IRI.create(NS + "you_zhengzhuang")), p,
                List.of(NS + "Fuman_instance"), NS);
        OntologyModuleUtils.addObjectAssertionsAndCopyTypes(lookup, df, acc,
                df.getOWLObjectProperty(IRI.create(NS + "you_maixiang")), p,
                List.of(NS + "Ruomai_instance"), NS);
        System.out.println("[6] 患者公理数 = " + acc.size());
        for (OWLAxiom a : acc) System.out.println("      " + a);

        OWLOntologyManager mgr = OWLManager.createOWLOntologyManager();
        OWLOntology ont = mgr.createOntology(IRI.create("urn:probe"));
        mgr.addAxioms(ont, tbox.axioms());
        mgr.addAxioms(ont, acc);
        System.out.println("[7] 推理本体公理数 = " + ont.getAxiomCount());
        System.out.println("[7] 推理本体含 Taiyinbing 等价类 = " + ont.equivalentClassesAxioms(taiyin).count());

        System.out.println("[8] 开始推理（可能数分钟）...");
        long t0 = System.nanoTime();
        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(ont);
        r.precomputeInferences(InferenceType.CLASS_ASSERTIONS);
        System.out.println("[8] 推理完成，用时 " + (System.nanoTime() - t0) / 1_000_000_000 + "s");

        Set<String> types = r.getTypes(p, false).getFlattened().stream()
                .map(c -> c.getIRI().getFragment()).collect(Collectors.toCollection(TreeSet::new));
        System.out.println("[9] 患者类型 = " + types);
        r.dispose();
        Runtime.getRuntime().halt(0);
    }
}
