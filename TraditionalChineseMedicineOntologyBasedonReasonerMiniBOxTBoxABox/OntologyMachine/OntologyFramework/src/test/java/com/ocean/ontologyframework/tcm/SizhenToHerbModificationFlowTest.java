package com.ocean.ontologyframework.tcm;

import io.camunda.zeebe.client.api.response.ProcessInstanceResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.MissingImportHandlingStrategy;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDisjointClassesAxiom;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyLoaderConfiguration;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.SWRLAtom;
import org.semanticweb.owlapi.model.SWRLClassAtom;
import org.semanticweb.owlapi.model.SWRLDataPropertyAtom;
import org.semanticweb.owlapi.model.SWRLRule;
import org.semanticweb.owlapi.model.SWRLLiteralArgument;
import org.semanticweb.owlapi.util.AutoIRIMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「四诊 → 八纲 → 六经 → 方证 → 加减药」真实 BPMN 端到端全量测试。
 *
 * <p><b>纯 Java，无 Python</b>：期望值由本测试用 OWL API 直接解析
 * {@code ontology/fangzheng/rules.owl} 的 259 条 SWRL 规则自动生成——
 * 对「实际喂给流程的四诊输入（含六经锚点）」求值，得到应加/应去/剂量集合，
 * 再与真实流程（Zeebe + 常驻 Worker + Openllet）的输出逐字段比对。
 *
 * <p><b>链路</b>：四诊输入 → {@code JingfangTestSupport.buildAnchoredVars} 注入六经锚点
 * → 真实 BPMN 流程 → Openllet 自动推出方证 → 主方确定后 Openllet 自动激发加减药 SWRL
 * 规则（生产 {@code querySwrlDerived} → {@code deriveFormulaFromSwrl}）→ 断言输出与本体一致。
 *
 * <p><b>用例生成</b>：base = 方证 {@code equivalentClass} 的 DNF 最小充分分支；
 * 每个唯一触发集单独成一例（用户裁定 3：每例只含最小触发集，避免命中兄弟方证）。
 */
public class SizhenToHerbModificationFlowTest extends AbstractJingfangDiagnosisTest {

    static final String ONTOLOGY_DIR =
            "D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology";

    static final String NS = JingfangTestSupport.NS;
    static final String INSTANCE_SUFFIX = "_instance";

    static final String HAS_SYMPTOM = "you_zhengzhuang";
    static final String HAS_PULSE = "you_maixiang";

    /** 八纲「寒证」类无实例，医理上由「畏寒」(WeiHan ⊑ Han) 承载。 */
    static final Map<String, String> TRIGGER_ALIAS = Map.of("Han", "WeiHan");

    // ---------- 本体索引 ----------
    static OWLOntologyManager manager;
    static OWLDataFactory df;
    static Set<String> fangzhengClasses = new HashSet<>();
    static final Map<String, Set<String>> parents = new HashMap<>();
    static final Map<String, Set<String>> huchi = new HashMap<>();
    static final Map<String, String> instChannel = new HashMap<>();

    // ---------- 规则 ----------
    record Action(String prop, String value) { }

    record RuleDef(String fz, Set<String> triggers, List<Action> actions) { }

    static final List<RuleDef> rules = new ArrayList<>();
    static final Map<String, List<RuleDef>> rulesByFz = new LinkedHashMap<>();

    // ---------- 方证定义 ----------
    record Branch(Set<String> syms, Set<String> pulses) { }

    static final Map<String, List<Branch>> fzDnf = new LinkedHashMap<>();
    static final Map<String, List<String>> fzLj = new LinkedHashMap<>();

    // ---------- 用例 ----------
    record Case(String fz, String lj, List<String> syms, List<String> pulses, List<String> tongues) {
        @Override
        public String toString() {
            return fz + " [" + String.join("+", syms) + "|" + String.join("+", pulses) + "]";
        }
    }

    static final List<Case> cases = new ArrayList<>();

    @BeforeAll
    static void parseOntology() throws Exception {
        manager = OWLManager.createOWLOntologyManager();
        manager.setOntologyLoaderConfiguration(new OWLOntologyLoaderConfiguration()
                .setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT));
        manager.getIRIMappers().add(new AutoIRIMapper(new File(ONTOLOGY_DIR), true));

        for (Path p : owlFiles()) {
            try {
                manager.loadOntologyFromOntologyDocument(p.toFile());
            } catch (Exception e) {
                System.out.println("[SizhenFlow] 跳过 " + p.getFileName() + " : " + e.getMessage());
            }
        }
        df = manager.getOWLDataFactory();

        indexClasses();
        indexInstances();
        parseRules();
        parseFangzhengDefs();
        generateCases();

        System.out.println("[SizhenFlow] 本体=" + manager.getOntologies().size()
                + " 方证类=" + fangzhengClasses.size()
                + " SWRL规则=" + rules.size()
                + " 覆盖方证=" + rulesByFz.size()
                + " 用例=" + cases.size());
    }

    static List<Path> owlFiles() throws IOException {
        try (Stream<Path> s = Files.walk(Paths.get(ONTOLOGY_DIR))) {
            return s.filter(p -> p.toString().endsWith(".owl")).collect(Collectors.toList());
        }
    }

    // ============================================================
    // 索引构建
    // ============================================================

    static void indexClasses() {
        OWLClass fz = df.getOWLClass(IRI.create(NS + "Fangzheng"));
        // 两阶段：先收集全部 SUBCLASS_OF 建 parents，再处理 DISJOINT_CLASSES。
        // 否则 disjoint 处理时 descendants() 依赖的 parents 可能尚未完整（manager.getOntologies()
        // 顺序不定），导致「子类互斥」漏建（如 Ou ⊥ Buou 未传导到 Xiou ⊑ Ou）。
        List<OWLDisjointClassesAxiom> disjoints = new ArrayList<>();
        for (OWLOntology o : manager.getOntologies()) {
            o.axioms(AxiomType.SUBCLASS_OF).forEach(ax -> {
                if (!ax.getSuperClass().isOWLClass() || !ax.getSubClass().isOWLClass()) return;
                String sup = frag(ax.getSuperClass().asOWLClass());
                String sub = frag(ax.getSubClass().asOWLClass());
                parents.computeIfAbsent(sub, k -> new HashSet<>()).add(sup);
                if (ax.getSuperClass().asOWLClass().equals(fz)) fangzhengClasses.add(sub);
            });
            o.axioms(AxiomType.DISJOINT_CLASSES).forEach(disjoints::add);
        }
        for (OWLDisjointClassesAxiom ax : disjoints) {
            List<OWLClass> cs = ax.classesInSignature().collect(Collectors.toList());
            for (int i = 0; i < cs.size(); i++) {
                for (int j = i + 1; j < cs.size(); j++) {
                    addDisjoint(frag(cs.get(i)), frag(cs.get(j)));
                }
            }
        }
    }

    static void addDisjoint(String a, String b) {
        for (String x : descendants(a)) {
            Set<String> s = huchi.computeIfAbsent(x, k -> new HashSet<>());
            s.addAll(descendants(b));
            s.remove(x);
        }
        for (String y : descendants(b)) {
            Set<String> s = huchi.computeIfAbsent(y, k -> new HashSet<>());
            s.addAll(descendants(a));
            s.remove(y);
        }
    }

    static Set<String> descendants(String c) {
        Set<String> out = new LinkedHashSet<>();
        java.util.Deque<String> st = new java.util.ArrayDeque<>();
        st.push(c);
        while (!st.isEmpty()) {
            String x = st.pop();
            if (!out.add(x)) continue;
            for (Map.Entry<String, Set<String>> e : parents.entrySet()) {
                if (e.getValue().contains(x)) st.push(e.getKey());
            }
        }
        return out;
    }

    static Set<String> ancestors(String c) {
        Set<String> out = new LinkedHashSet<>();
        java.util.Deque<String> st = new java.util.ArrayDeque<>();
        st.push(c);
        while (!st.isEmpty()) {
            String x = st.pop();
            if (!out.add(x)) continue;
            Set<String> ps = parents.get(x);
            if (ps != null) ps.forEach(st::push);
        }
        return out;
    }

    static void indexInstances() {
        Map<String, String> files = Map.of(
                "tcm-zhengzhuang-abox.owl", "symptom",
                "tcm-maixiang-abox.owl", "pulse",
                "tcm-shexiang-abox.owl", "tongue",
                "tcm-fuzheng-abox.owl", "fuzheng");
        java.util.regex.Pattern p =
                java.util.regex.Pattern.compile("rdf:about=\"#([A-Za-z0-9_]+)_instance\"");
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path f = Paths.get(ONTOLOGY_DIR, e.getKey());
            if (!Files.isRegularFile(f)) continue;
            try {
                String txt = Files.readString(f);
                java.util.regex.Matcher m = p.matcher(txt);
                while (m.find()) instChannel.putIfAbsent(m.group(1), e.getValue());
            } catch (IOException ignored) {
                // 实例通道仅用于分流，缺失时按症状处理
            }
        }
    }

    // ============================================================
    // SWRL 规则解析
    // ============================================================

    static void parseRules() {
        for (OWLOntology o : manager.getOntologies()) {
            for (SWRLRule r : o.axioms(AxiomType.SWRL_RULE).collect(Collectors.toList())) {
                String fz = null;
                Set<String> triggers = new LinkedHashSet<>();
                List<Action> actions = new ArrayList<>();

                for (SWRLAtom a : r.getBody()) {
                    if (a instanceof SWRLClassAtom ca && ca.getPredicate().isOWLClass()) {
                        String cls = frag(ca.getPredicate().asOWLClass());
                        if (fangzhengClasses.contains(cls)) fz = cls;
                        else if (!"Huanzhe".equals(cls) && !"Thing".equals(cls)) triggers.add(cls);
                    }
                }
                for (SWRLAtom a : r.getHead()) {
                    if (a instanceof SWRLDataPropertyAtom dp) {
                        String prop = frag(dp.getPredicate().asOWLDataProperty());
                        if (dp.getSecondArgument() instanceof SWRLLiteralArgument lit) {
                            actions.add(new Action(prop, lit.getLiteral().getLiteral()));
                        }
                    }
                }
                if (fz != null) {
                    RuleDef def = new RuleDef(fz, triggers, actions);
                    rules.add(def);
                    rulesByFz.computeIfAbsent(fz, k -> new ArrayList<>()).add(def);
                }
            }
        }
    }

    // ============================================================
    // 方证定义（equivalentClass → DNF）
    // ============================================================

    static void parseFangzhengDefs() {
        for (OWLOntology o : manager.getOntologies()) {
            for (OWLEquivalentClassesAxiom ax :
                    o.axioms(AxiomType.EQUIVALENT_CLASSES).collect(Collectors.toList())) {
                OWLClass fzCls = null;
                OWLClassExpression def = null;
                for (OWLClassExpression ce : ax.getClassExpressions()) {
                    if (ce.isOWLClass() && fangzhengClasses.contains(frag(ce.asOWLClass()))) {
                        fzCls = ce.asOWLClass();
                    } else {
                        def = ce;
                    }
                }
                if (fzCls == null || def == null) continue;
                fzDnf.put(frag(fzCls), toDnf(def));
                fzLj.putIfAbsent(frag(fzCls), readLiujing(o, fzCls));
            }
        }
    }

    static List<String> readLiujing(OWLOntology o, OWLClass cls) {
        List<String> out = new ArrayList<>();
        for (var ax : o.annotationAssertionAxioms(cls.getIRI()).collect(Collectors.toList())) {
            if (!"belongsToLiujing".equals(ax.getProperty().getIRI().getFragment())) continue;
            if (ax.getValue() instanceof IRI iri) {
                String f = frag(iri);
                if (!out.contains(f)) out.add(f);
            }
        }
        return out;
    }

    static List<Branch> toDnf(OWLClassExpression e) {
        if (e instanceof OWLObjectIntersectionOf inter) {
            List<Branch> res = List.of(new Branch(Set.of(), Set.of()));
            for (OWLClassExpression op : inter.getOperands()) {
                List<Branch> part = toDnf(op);
                List<Branch> merged = new ArrayList<>();
                for (Branch a : res) {
                    for (Branch b : part) {
                        Set<String> s = new LinkedHashSet<>(a.syms());
                        s.addAll(b.syms());
                        Set<String> p = new LinkedHashSet<>(a.pulses());
                        p.addAll(b.pulses());
                        merged.add(new Branch(s, p));
                    }
                }
                res = merged;
            }
            return res;
        }
        if (e instanceof OWLObjectUnionOf uni) {
            List<Branch> res = new ArrayList<>();
            for (OWLClassExpression op : uni.getOperands()) res.addAll(toDnf(op));
            return res;
        }
        if (e instanceof OWLObjectSomeValuesFrom svf) {
            String prop = frag(svf.getProperty().getNamedProperty());
            OWLClassExpression filler = svf.getFiller();
            if (filler.isOWLClass()) {
                String cls = frag(filler.asOWLClass());
                if (HAS_SYMPTOM.equals(prop)) return List.of(new Branch(Set.of(cls), Set.of()));
                if (HAS_PULSE.equals(prop)) return List.of(new Branch(Set.of(), Set.of(cls)));
            }
            return List.of(new Branch(Set.of(), Set.of()));
        }
        return List.of(new Branch(Set.of(), Set.of()));
    }

    // ============================================================
    // 用例生成
    // ============================================================

    static void generateCases() {
        for (String fz : rulesByFz.keySet()) {
            List<Branch> dnf = fzDnf.getOrDefault(fz, List.of());
            // 用户裁定 A：base 取「DNF 完整定义」——覆盖目标方证全部分支的症状/脉象并集，
            // 以唯一确定目标方证、消除兄弟方证干扰（不再取最小充分分支）。
            Set<String> baseSyms = new LinkedHashSet<>();
            Set<String> basePulses = new LinkedHashSet<>();
            for (Branch b : dnf) {
                baseSyms.addAll(b.syms());
                basePulses.addAll(b.pulses());
            }
            Branch base = new Branch(baseSyms, basePulses);
            // 方证可归属多经（如柴胡加龙骨牡蛎汤证属少阳+阳明），锚点取全部归属经。
            List<String> ljList = fzLj.getOrDefault(fz, List.of());
            String lj = ljList.isEmpty() ? "" : String.join(";", ljList);

            List<Set<String>> uniqueTrigs = new ArrayList<>();
            uniqueTrigs.add(Set.of());
            for (RuleDef r : rulesByFz.get(fz)) {
                if (!uniqueTrigs.contains(r.triggers())) uniqueTrigs.add(r.triggers());
            }

            Set<String> seen = new HashSet<>();
            for (Set<String> trig : uniqueTrigs) {
                Set<String> syms = new LinkedHashSet<>(base.syms());
                Set<String> pulses = new LinkedHashSet<>(base.pulses());
                for (String t : trig) {
                    String rt = TRIGGER_ALIAS.getOrDefault(t, t);
                    if ("pulse".equals(instChannel.get(rt))) pulses.add(rt);
                    else syms.add(rt);
                }
                if (!seen.add(syms + "|" + pulses)) continue;
                // 医理互斥过滤：union 型方证（如小柴胡「但见一证便是」）base 取 DNF 并集时，
                // 叠加某条规则的 trigger 可能与 base 中症状互斥（如 Xiou 喜呕 ⊥ Buou 不呕），
                // 患者在本体下 inconsistent，方证必推不出。此类矛盾组合不生成用例（铁律 63/64）。
                if (hasConflict(syms, pulses)) continue;
                cases.add(new Case(fz, lj, new ArrayList<>(syms), new ArrayList<>(pulses), List.of()));
            }
        }
    }

    /** 用例内症状/脉象是否存在互斥（本体 disjointWith 闭包），存在则该组合在本体下不一致。 */
    static boolean hasConflict(Set<String> syms, Set<String> pulses) {
        List<String> all = new ArrayList<>(syms);
        all.addAll(pulses);
        for (int i = 0; i < all.size(); i++) {
            Set<String> excl = huchi.get(all.get(i));
            if (excl == null) continue;
            for (int j = i + 1; j < all.size(); j++) {
                if (excl.contains(all.get(j))) return true;
            }
        }
        return false;
    }

    // ============================================================
    // 期望值（用实际输入对 rules.owl 规则求值）
    // ============================================================

    record Expected(Set<String> added, Set<String> removed, Set<String> rawAdd, Set<String> rawRemove) { }

    static Expected expected(String mainFz, Set<String> inputFrags, Set<String> baseHerbs) {
        Set<String> add = new LinkedHashSet<>();
        Set<String> rem = new LinkedHashSet<>();
        // 用户裁定：主方确定后只激发主方证的加减药规则（兄弟方证规则不得污染）。
        // 与 worker 的 withFangzhengReasonerForSwrl（只注入主方规则）口径一致。
        List<RuleDef> rs = rulesByFz.getOrDefault(mainFz, List.of());
        for (RuleDef r : rs) {
            if (!satisfied(r.triggers(), inputFrags)) continue;
            for (Action a : r.actions()) {
                switch (a.prop()) {
                    case "shouldAddHerbName" -> add.add(a.value());
                    case "shouldRemoveHerbName" -> rem.add(a.value());
                    default -> { }
                }
            }
        }
        Set<String> expAdded = new LinkedHashSet<>(add);
        expAdded.removeAll(baseHerbs);
        Set<String> expRemoved = new LinkedHashSet<>(rem);
        expRemoved.retainAll(baseHerbs);
        return new Expected(expAdded, expRemoved, add, rem);
    }

    /** 触发集是否被输入满足（含子类蕴含 f ⊑ t），且无互斥冲突。 */
    static boolean satisfied(Set<String> triggers, Set<String> inputFrags) {
        for (String t : triggers) {
            boolean ok = false;
            for (String f : inputFrags) {
                if (ancestors(f).contains(t)) { ok = true; break; }
            }
            if (!ok) return false;
        }
        return true;
    }

    // ============================================================
    // 端到端执行
    // ============================================================

    static Stream<Case> cases() {
        return cases.stream();
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("cases")
    @DisplayName("四诊→八纲→六经→方证→加减药：真实 BPMN 端到端")
    void endToEnd(Case c) {
        List<String> symIris = c.syms().stream().map(s -> NS + s + INSTANCE_SUFFIX).toList();
        List<String> pulseIris = c.pulses().stream().map(s -> NS + s + INSTANCE_SUFFIX).toList();
        // 锚点口径与既有 assertFangzheng 一致：篇章归属(belongsToLiujing) ∪ 诊断六经(equivalentClass)。
        // 引擎两道关卡口径不同（铁律 22）：候选池按篇章归属过滤、realize 按诊断六经判定，
        // 二者不同者（如白通汤证出少阴篇、诊断六经为太阴）须同时呈现两经方能既进池又被命中。
        String anchorLj = JingfangTestSupport.anchorLiujingFor(c.fz(), c.lj());
        Map<String, Object> vars = JingfangTestSupport.buildAnchoredVars(
                anchorLj, symIris, pulseIris, List.of(), List.of());

        Set<String> inputFrags = new LinkedHashSet<>();
        for (String key : List.of("symptomIris", "pulseIris", "tongueIris", "fuzhengIris")) {
            for (String iri : asList(vars.get(key))) inputFrags.add(fragmentOf(iri));
        }

        ProcessInstanceResult result = JingfangTestSupport.startProcessAndGetResult(vars);
        Map<String, Object> out = result.getVariablesAsMap();

        Set<String> baseHerbs = frags(out.get("baseHerbs"));
        Expected exp = expected(c.fz(), inputFrags, baseHerbs);

        Set<String> actAdded = frags(out.get("addedHerb"));
        Set<String> actRemoved = frags(out.get("removedHerb"));

        assertThat(out.get("fangzheng"))
                .as("[%s] 方证应被 Openllet 自动推出", c)
                .isEqualTo(c.fz());

        if (Boolean.TRUE.equals(out.get("yaozhengApplied"))) {
            System.out.println("[SizhenFlow] SKIP(药证兜底，非 rules.owl 范围) " + c
                    + " 加=" + actAdded + " 去=" + actRemoved);
            return;
        }

        assertThat(actRemoved)
                .as("[%s] 去药应与 rules.owl 一致（母方=%s）", c, baseHerbs)
                .containsExactlyInAnyOrderElementsOf(exp.removed());
        assertThat(actAdded)
                .as("[%s] 加药应与 rules.owl 一致（母方=%s）", c, baseHerbs)
                .containsExactlyInAnyOrderElementsOf(exp.added());

        System.out.println("[SizhenFlow] OK " + c + " 加=" + actAdded + " 去=" + actRemoved);
    }

    // ============================================================
    // 工具
    // ============================================================

    static String frag(OWLClass c) {
        return c.getIRI().getFragment();
    }

    static String frag(OWLObjectProperty p) {
        return p.getIRI().getFragment();
    }

    static String frag(OWLDataProperty p) {
        return p.getIRI().getFragment();
    }

    static String frag(IRI iri) {
        return iri.getFragment();
    }

    static String fragmentOf(String iri) {
        if (iri == null) return "";
        String s = iri.trim();
        int hash = s.lastIndexOf('#');
        String f = hash >= 0 ? s.substring(hash + 1) : s;
        return f.endsWith(INSTANCE_SUFFIX) ? f.substring(0, f.length() - INSTANCE_SUFFIX.length()) : f;
    }

    @SuppressWarnings("unchecked")
    static List<String> asList(Object v) {
        if (v instanceof List<?> l) return l.stream().map(String::valueOf).collect(Collectors.toList());
        return List.of();
    }

    static Set<String> frags(Object v) {
        Set<String> out = new LinkedHashSet<>();
        for (String iri : asList(v)) out.add(fragmentOf(iri));
        return out;
    }
}
