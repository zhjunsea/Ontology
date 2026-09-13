package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.ObdaQueryUtils;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 与业务无关的 OWL 本体操作工具集：
 *  - 从 someValuesFrom 限制中收集 filler fragment
 *  - 扫描"复合类 = 原子类交集"的结构
 *  - 查找与某类相关（注解/父类/等价类命中）的目标类
 *  - 类闭包收集
 *  - TBox 模块精确抽取
 *  - 从个体 IRI 收集类断言
 *  - 向 ABox 加对象断言并复制类型
 *  - 依据复合类映射合成新个体
 *  - 从类型集合按元类过滤 fragment
 */
public final class OntologyModuleUtils {

    private OntologyModuleUtils() {}

    // ============================================================
    // 1. 从 someValuesFrom 限制收集 filler
    // ============================================================

    /**
     * 收集某类等价类/子类公理中，通过指定属性 someValuesFrom 出现的 filler fragment。
     */
    public static Set<String> collectRestrictionFillers(
            OWLOntology tbox, OWLClass cls, Set<IRI> propIris) {
        Set<String> result = new HashSet<>();
        for (OWLEquivalentClassesAxiom ax :
                tbox.equivalentClassesAxioms(cls).collect(Collectors.toList())) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.isOWLClass() && e.asOWLClass().equals(cls)) continue;
                collectRestrictions(e, propIris, result);
            }
        }
        for (OWLSubClassOfAxiom ax :
                tbox.subClassAxiomsForSubClass(cls).collect(Collectors.toList())) {
            collectRestrictions(ax.getSuperClass(), propIris, result);
        }
        return result;
    }

    public static void collectRestrictions(OWLClassExpression expr,
                                           Set<IRI> propIris,
                                           Set<String> acc) {
        if (expr instanceof OWLObjectSomeValuesFrom svf) {
            IRI propIri = svf.getProperty().getNamedProperty().getIRI();
            if (propIris.contains(propIri)) {
                OWLClassExpression filler = svf.getFiller();
                if (filler.isOWLClass()) {
                    acc.add(filler.asOWLClass().getIRI().getFragment());
                }
            }
        } else if (expr instanceof OWLObjectIntersectionOf inter) {
            for (OWLClassExpression op : inter.getOperands()) {
                collectRestrictions(op, propIris, acc);
            }
        } else if (expr instanceof OWLObjectUnionOf union) {
            for (OWLClassExpression op : union.getOperands()) {
                collectRestrictions(op, propIris, acc);
            }
        }
    }

    // ============================================================
    // 2. 复合类 → 原子类集合
    // ============================================================

    /**
     * 扫描某顶层类下所有子类，若其等价类定义是若干"原子类"的交集，
     * 返回 Map<复合类, 原子类集合>。
     *
     * @param tbox        TBox 本体
     * @param topClassIri 顶层类 IRI
     * @param allSubs     topClassIri 下的全部命名子类（由调用方通过 BackendService 获取）
     */
    public static Map<OWLClass, Set<OWLClass>> buildIntersectionCompositeMap(
            OWLOntology tbox, IRI topClassIri, Set<OWLClass> allSubs) {
        Map<OWLClass, Set<OWLClass>> result = new HashMap<>();
        OWLClass top = tbox.getOWLOntologyManager().getOWLDataFactory()
                .getOWLClass(topClassIri);

        for (OWLClass cls : allSubs) {
            for (OWLEquivalentClassesAxiom ax :
                    tbox.equivalentClassesAxioms(cls).collect(Collectors.toList())) {
                for (OWLClassExpression e : ax.getClassExpressions()) {
                    if (e.isOWLClass() && e.asOWLClass().equals(cls)) continue;
                    Set<OWLClass> comps = extractAtomicComponents(e, allSubs, top, cls);
                    if (comps != null) result.put(cls, comps);
                }
            }
        }
        return result;
    }

    private static Set<OWLClass> extractAtomicComponents(
            OWLClassExpression e, Set<OWLClass> allSubs, OWLClass top, OWLClass self) {
        if (!(e instanceof OWLObjectIntersectionOf inter)) return null;
        Set<OWLClass> comps = new HashSet<>();
        for (OWLClassExpression op : inter.getOperands()) {
            if (!op.isOWLClass()) return null;
            OWLClass oc = op.asOWLClass();
            if (oc.equals(self) || oc.equals(top)) return null;
            if (!allSubs.contains(oc)) return null;
            comps.add(oc);
        }
        return comps.isEmpty() ? null : comps;
    }

    // ============================================================
    // 3. 查找与某类相关的目标类
    // ============================================================

    /**
     * 找出与给定类相关（注解值 / 父类 / 等价类命中）且属于 targetUniverse 的类。
     * 适用于"从方证读八纲"、"从方证读六经"这类模式。
     */
    public static Set<OWLClass> findRelatedClasses(
            OWLOntology tbox, OWLClass cls, Set<OWLClass> targetUniverse) {
        Set<OWLClass> result = new HashSet<>();
        if (targetUniverse == null || targetUniverse.isEmpty()) return result;

        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();

        // 注解
        for (OWLAnnotationAssertionAxiom ax :
                tbox.annotationAssertionAxioms(cls.getIRI()).collect(Collectors.toList())) {
            if (!(ax.getValue() instanceof IRI valueIri)) continue;
            OWLClass valueCls = df.getOWLClass(valueIri);
            if (targetUniverse.contains(valueCls)) result.add(valueCls);
        }
        // 父类
        for (OWLSubClassOfAxiom ax :
                tbox.subClassAxiomsForSubClass(cls).collect(Collectors.toList())) {
            OWLClassExpression sc = ax.getSuperClass();
            if (sc.isOWLClass() && targetUniverse.contains(sc.asOWLClass())) {
                result.add(sc.asOWLClass());
            }
        }
        // 等价类里的命名类
        for (OWLEquivalentClassesAxiom ax :
                tbox.equivalentClassesAxioms(cls).collect(Collectors.toList())) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.isOWLClass() && !e.asOWLClass().equals(cls)
                        && targetUniverse.contains(e.asOWLClass())) {
                    result.add(e.asOWLClass());
                }
            }
        }
        return result;
    }

    // ============================================================
    // 4. 类闭包
    // ============================================================

    /**
     * 沿 equivalentClasses / subClassOf 递归收集类的闭包（不含 OWLThing / OWLNothing）。
     */
    public static Set<OWLClass> collectClassClosure(OWLOntology tbox,
                                                    Set<OWLClass> initial) {
        Set<OWLClass> closure = new HashSet<>(initial);
        Deque<OWLClass> queue = new ArrayDeque<>(initial);

        while (!queue.isEmpty()) {
            OWLClass c = queue.poll();

            for (OWLEquivalentClassesAxiom ax :
                    tbox.equivalentClassesAxioms(c).collect(Collectors.toList())) {
                for (OWLClassExpression e : ax.getClassExpressions()) {
                    if (e.isOWLClass() && e.asOWLClass().equals(c)) continue;
                    for (OWLClass ref : e.classesInSignature().collect(Collectors.toList())) {
                        if (!ref.isOWLThing() && !ref.isOWLNothing() && closure.add(ref)) {
                            queue.add(ref);
                        }
                    }
                }
            }

            for (OWLSubClassOfAxiom ax :
                    tbox.subClassAxiomsForSubClass(c).collect(Collectors.toList())) {
                for (OWLClass ref :
                        ax.getSuperClass().classesInSignature().collect(Collectors.toList())) {
                    if (!ref.isOWLThing() && !ref.isOWLNothing() && closure.add(ref)) {
                        queue.add(ref);
                    }
                }
            }
        }
        return closure;
    }

    // ============================================================
    // 5. 从个体 IRI 收集类型
    // ============================================================

    /**
     * 给定一组个体 IRI，从 TBox 中读取它们的类断言，返回其类型集合。
     * 调用方通常将此结果并入闭包 seed。
     */
    public static Set<OWLClass> collectIndividualTypes(OWLOntology tbox,
                                                       Collection<String> individualIris,
                                                       String baseNs) {
        Set<OWLClass> result = new HashSet<>();
        if (individualIris == null || individualIris.isEmpty()) return result;

        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();
        for (String iri : individualIris) {
            OWLNamedIndividual ind = df.getOWLNamedIndividual(
                    IRI.create(ObdaQueryUtils.toFullIri(iri, baseNs)));
            for (OWLClassAssertionAxiom ax :
                    tbox.classAssertionAxioms(ind).collect(Collectors.toList())) {
                OWLClassExpression ce = ax.getClassExpression();
                if (ce.isOWLClass()) result.add(ce.asOWLClass());
            }
        }
        return result;
    }

    // ============================================================
    // 6. TBox 模块精确抽取
    // ============================================================

    /**
     * 从 TBox 中抽取与 keepClasses 相关的公理，过滤掉 ABox 与个体相关公理。
     */
    public static Set<OWLAxiom> extractTBoxModule(OWLOntology tbox,
                                                  Set<OWLClass> keepClasses) {
        return tbox.axioms()
                .filter(ax -> !(ax instanceof OWLClassAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLObjectPropertyAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLDataPropertyAssertionAxiom))
                .filter(ax -> !(ax instanceof OWLSameIndividualAxiom))
                .filter(ax -> !(ax instanceof OWLDifferentIndividualsAxiom))
                .filter(ax -> isRelevantAxiom(ax, keepClasses))
                .collect(Collectors.toSet());
    }

    private static boolean isRelevantAxiom(OWLAxiom ax, Set<OWLClass> keep) {
        if (ax instanceof OWLSubClassOfAxiom sub) {
            return sub.getSubClass().isOWLClass()
                    && keep.contains(sub.getSubClass().asOWLClass());
        }
        if (ax instanceof OWLEquivalentClassesAxiom eq) {
            return eq.getClassExpressions().stream()
                    .anyMatch(e -> e.isOWLClass() && keep.contains(e.asOWLClass()));
        }
        if (ax instanceof OWLDeclarationAxiom decl) {
            if (!decl.getEntity().isOWLClass()) return false;
            return keep.contains(decl.getEntity().asOWLClass());
        }
        if (ax instanceof OWLObjectPropertyDomainAxiom
                || ax instanceof OWLObjectPropertyRangeAxiom
                || ax instanceof OWLDataPropertyDomainAxiom
                || ax instanceof OWLDataPropertyRangeAxiom
                || ax instanceof OWLFunctionalObjectPropertyAxiom
                || ax instanceof OWLInverseFunctionalObjectPropertyAxiom
                || ax instanceof OWLSymmetricObjectPropertyAxiom
                || ax instanceof OWLTransitiveObjectPropertyAxiom
                || ax instanceof OWLReflexiveObjectPropertyAxiom
                || ax instanceof OWLIrreflexiveObjectPropertyAxiom
                || ax instanceof OWLAsymmetricObjectPropertyAxiom
                || ax instanceof OWLFunctionalDataPropertyAxiom
                || ax instanceof OWLInverseObjectPropertiesAxiom
                || ax instanceof OWLSubObjectPropertyOfAxiom
                || ax instanceof OWLSubDataPropertyOfAxiom) {
            return true;
        }
        if (ax instanceof OWLDisjointClassesAxiom) return true;
        return false;
    }

    // ============================================================
    // 7. 向 ABox 加对象断言并复制类型
    // ============================================================

    /**
     * 给 subj 加 prop→obj 对象断言，并把 obj 在 TBox 中的类断言复制进 acc。
     */
    public static void addObjectAssertionsAndCopyTypes(
            OWLOntology tbox, OWLDataFactory df, Set<OWLAxiom> acc,
            OWLObjectProperty prop, OWLNamedIndividual subj,
            List<String> objects, String baseNs) {
        if (objects == null || objects.isEmpty()) return;
        for (String iri : objects) {
            OWLNamedIndividual obj = df.getOWLNamedIndividual(
                    IRI.create(ObdaQueryUtils.toFullIri(iri, baseNs)));
            acc.add(df.getOWLObjectPropertyAssertionAxiom(prop, subj, obj));
            tbox.classAssertionAxioms(obj).forEach(ca -> {
                OWLClassExpression ce = ca.getClassExpression();
                if (ce.isOWLClass()) {
                    acc.add(df.getOWLClassAssertionAxiom(ce.asOWLClass(), obj));
                }
            });
        }
    }

    // ============================================================
    // 8. 合成复合个体
    // ============================================================

    /**
     * 若患者已提供某复合类的全部原子组成，则合成一个匿名个体，断言其属于复合类与各原子类，
     * 并把患者指向该合成个体。
     *
     * @param compositeMap 复合类 → 原子类集合；若为 null 或空则跳过
     */
    public static void addSynthesizedComposites(
            OWLDataFactory df, Set<OWLAxiom> acc,
            OWLNamedIndividual patient, List<String> providedIris,
            OWLObjectProperty prop,
            Map<OWLClass, Set<OWLClass>> compositeMap,
            String baseNs,
            String instanceSuffix,                 // 【新增】
            Logger log) {
        if (providedIris == null || providedIris.isEmpty()) return;
        if (compositeMap == null || compositeMap.isEmpty()) return;

        Set<String> patientFrags = providedIris.stream()
                .map(i -> ObdaQueryUtils.frag(i, baseNs, instanceSuffix))  // 【修复】
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> alreadyComposite = new HashSet<>();
        for (String p : providedIris) {
            String f = ObdaQueryUtils.frag(p, baseNs, instanceSuffix);     // 【修复】
            if (f == null) continue;
            OWLClass c = df.getOWLClass(IRI.create(baseNs + f));
            if (compositeMap.containsKey(c)) alreadyComposite.add(f);
        }

        for (Map.Entry<OWLClass, Set<OWLClass>> entry : compositeMap.entrySet()) {
            OWLClass composite = entry.getKey();
            Set<OWLClass> components = entry.getValue();
            String compFrag = composite.getIRI().getFragment();

            if (alreadyComposite.contains(compFrag)) continue;

            boolean allPresent = true;
            for (OWLClass comp : components) {
                if (!patientFrags.contains(comp.getIRI().getFragment())) {
                    allPresent = false;
                    break;
                }
            }
            if (!allPresent) continue;

            OWLNamedIndividual synth = df.getOWLNamedIndividual(
                    IRI.create("urn:synth:" + compFrag + ":"
                            + patient.getIRI().getFragment()));
            acc.add(df.getOWLClassAssertionAxiom(composite, synth));
            for (OWLClass comp : components) {
                acc.add(df.getOWLClassAssertionAxiom(comp, synth));
            }
            acc.add(df.getOWLObjectPropertyAssertionAxiom(prop, patient, synth));

            if (log != null) {
                log.info("[合成复合] {} 具备 {} 的成分 {}，合成",
                        patient.getIRI().getFragment(), compFrag,
                        components.stream().map(c -> c.getIRI().getFragment())
                                .sorted().collect(Collectors.toList()));
            }
        }
    }

    // ============================================================
    // 9. 按元类过滤 fragment
    // ============================================================

    /**
     * 从类型集合中过滤出属于某元类集合的类，返回其 fragment（排序后）。
     */
    public static List<String> extractFragmentsByMetaClass(
            Set<OWLClass> allTypes, Set<OWLClass> metaClassSet) {
        if (allTypes == null || metaClassSet == null) return Collections.emptyList();
        return allTypes.stream()
                .filter(metaClassSet::contains)
                .map(c -> c.getIRI().getFragment())
                .sorted()
                .collect(Collectors.toList());
    }
}