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
        Set<OWLClass> visited = new HashSet<>();
        visited.add(cls);   // 避免自引用死循环
        collectFromClass(tbox, cls, propIris, result, visited);
        return result;
    }

    private static void collectFromClass(OWLOntology tbox, OWLClass cls,
                                         Set<IRI> propIris, Set<String> acc,
                                         Set<OWLClass> visited) {
        for (OWLEquivalentClassesAxiom ax :
                tbox.equivalentClassesAxioms(cls).collect(Collectors.toList())) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.isOWLClass() && e.asOWLClass().equals(cls)) continue;
                collectRestrictions(tbox, e, propIris, acc, visited);
            }
        }
        for (OWLSubClassOfAxiom ax :
                tbox.subClassAxiomsForSubClass(cls).collect(Collectors.toList())) {
            collectRestrictions(tbox, ax.getSuperClass(), propIris, acc, visited);
        }
    }

    public static void collectRestrictions(OWLOntology tbox,
                                           OWLClassExpression expr,
                                           Set<IRI> propIris,
                                           Set<String> acc,
                                           Set<OWLClass> visited) {
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
                collectRestrictions(tbox, op, propIris, acc, visited);
            }
        } else if (expr instanceof OWLObjectUnionOf union) {
            for (OWLClassExpression op : union.getOperands()) {
                collectRestrictions(tbox, op, propIris, acc, visited);
            }
        } else if (expr.isOWLClass()) {
            // 【新增】命名类引用：递归展开
            OWLClass c = expr.asOWLClass();
            // 排除不需要展开的系统类
            if (c.isOWLThing() || c.isOWLNothing()) return;
            if (!visited.add(c)) return;   // 已访问过，避免环
            collectFromClass(tbox, c, propIris, acc, visited);
        }
    }

    // ============================================================
    // 1b. 结构化「主证缺口」gap（判定用途，区分 AND/OR 语义）
    // ============================================================

    /**
     * 方证定义（{@code equivalentClass}）的「主证缺口」：尚未满足的原子要求数。
     *
     * <pre>
     *   gap(叶子 症状 s)  = 0 若 s ∈ satisfiedFrags，否则 1
     *   gap(叶子 类 C)    = 0 若 C ∈ satisfiedFrags，否则展开其等价类定义；无定义则 1
     *   gap(AND(φ1..φn))  = Σ gap(φi)      // 合取项须全部满足
     *   gap(OR(φ1..φn))   = min_i gap(φi)  // 析取项任一满足即可
     * </pre>
     *
     * <p>语义：「还差几个症状才能命中该方证」。纯整数计数、零自由参数。
     * {@code gap = 0} ⟺ 定义被完全满足（realize 命中）。
     *
     * <p><b>与 {@link #collectRestrictionFillers} 的分工</b>：
     * 后者是「索引」用途，须收集 OR 分支的<b>全部</b>填充符（命中任一即登记）；
     * 本方法是「判定」用途，须区分 AND/OR 语义（union 计 1，intersection 计子项数）。
     * 二者不可混用——把 union 平铺当 AND 正是此前 {@code required} 失真的根因。
     *
     * @param satisfiedFrags 患者已满足的类 fragment 集合（症状/脉象/舌象/腹证 + 推理所得八纲六经）
     * @param propIris       参与计数的属性 IRI（you_zhengzhuang / you_maixiang / you_shexiang / you_fuzheng）
     */
    public static int definitionGap(OWLOntology tbox, OWLClass cls,
                                    Set<String> satisfiedFrags, Set<IRI> propIris) {
        Set<OWLClass> path = new HashSet<>();
        path.add(cls);
        int gap = 0;
        for (OWLEquivalentClassesAxiom ax :
                tbox.equivalentClassesAxioms(cls).collect(Collectors.toList())) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.isOWLClass() && e.asOWLClass().equals(cls)) continue;
                gap += gapOf(tbox, e, satisfiedFrags, propIris, path);
            }
        }
        return gap;
    }

    private static int gapOf(OWLOntology tbox, OWLClassExpression expr,
                             Set<String> satisfiedFrags, Set<IRI> propIris,
                             Set<OWLClass> path) {
        if (expr instanceof OWLObjectIntersectionOf inter) {
            int sum = 0;
            for (OWLClassExpression op : inter.getOperands()) {
                sum += gapOf(tbox, op, satisfiedFrags, propIris, path);
            }
            return sum;
        }
        if (expr instanceof OWLObjectUnionOf union) {
            int min = Integer.MAX_VALUE;
            for (OWLClassExpression op : union.getOperands()) {
                min = Math.min(min, gapOf(tbox, op, satisfiedFrags, propIris, path));
            }
            return min == Integer.MAX_VALUE ? 0 : min;
        }
        if (expr instanceof OWLObjectSomeValuesFrom svf) {
            IRI propIri = svf.getProperty().getNamedProperty().getIRI();
            if (!propIris.contains(propIri)) return 0;   // 非相关属性，不计入缺口
            OWLClassExpression filler = svf.getFiller();
            if (filler.isOWLClass()) {
                return satisfiedFrags.contains(filler.asOWLClass().getIRI().getFragment()) ? 0 : 1;
            }
            return gapOf(tbox, filler, satisfiedFrags, propIris, path);
        }
        if (expr.isOWLClass()) {
            OWLClass c = expr.asOWLClass();
            if (c.isOWLThing() || c.isOWLNothing()) return 0;
            if (satisfiedFrags.contains(c.getIRI().getFragment())) return 0;
            if (path.contains(c)) return 0;             // 环保护
            Set<OWLClass> next = new HashSet<>(path);
            next.add(c);
            boolean hasEq = false;
            int sub = 0;
            for (OWLEquivalentClassesAxiom ax :
                    tbox.equivalentClassesAxioms(c).collect(Collectors.toList())) {
                for (OWLClassExpression e : ax.getClassExpressions()) {
                    if (e.isOWLClass() && e.asOWLClass().equals(c)) continue;
                    hasEq = true;
                    sub += gapOf(tbox, e, satisfiedFrags, propIris, next);
                }
            }
            return hasEq ? sub : 1;                     // 无定义 → 原子要求（如六经类）
        }
        return 0;
    }

    /**
     * 方证/判据定义中「尚缺的叶子类」——用于生成追问清单。
     *
     * <p>与 {@link #definitionGap} 同构：AND 取全部子项缺口叶子，OR 取<b>最小缺口分支</b>
     * （而非平铺全部叶子，否则会问一堆「或」里只需其一的症状）。
     * 返回顺序为定义中的出现顺序，已去重。
     */
    public static List<OWLClass> definitionGapLeaves(OWLOntology tbox, OWLClass cls,
                                                     Set<String> satisfiedFrags, Set<IRI> propIris) {
        Set<OWLClass> path = new HashSet<>();
        path.add(cls);
        LinkedHashSet<OWLClass> acc = new LinkedHashSet<>();
        for (OWLEquivalentClassesAxiom ax :
                tbox.equivalentClassesAxioms(cls).collect(Collectors.toList())) {
            for (OWLClassExpression e : ax.getClassExpressions()) {
                if (e.isOWLClass() && e.asOWLClass().equals(cls)) continue;
                gapLeavesOf(tbox, e, satisfiedFrags, propIris, path, acc);
            }
        }
        return new ArrayList<>(acc);
    }

    private static void gapLeavesOf(OWLOntology tbox, OWLClassExpression expr,
                                    Set<String> satisfiedFrags, Set<IRI> propIris,
                                    Set<OWLClass> path, Set<OWLClass> acc) {
        if (expr instanceof OWLObjectIntersectionOf inter) {
            for (OWLClassExpression op : inter.getOperands()) {
                gapLeavesOf(tbox, op, satisfiedFrags, propIris, path, acc);
            }
            return;
        }
        if (expr instanceof OWLObjectUnionOf union) {
            // 取「最小缺口分支」——任一满足即可，故只需补最省力的那一支
            OWLClassExpression best = null;
            int bestGap = Integer.MAX_VALUE;
            for (OWLClassExpression op : union.getOperands()) {
                int g = gapOf(tbox, op, satisfiedFrags, propIris, path);
                if (g < bestGap) { bestGap = g; best = op; }
            }
            if (best != null) gapLeavesOf(tbox, best, satisfiedFrags, propIris, path, acc);
            return;
        }
        if (expr instanceof OWLObjectSomeValuesFrom svf) {
            IRI propIri = svf.getProperty().getNamedProperty().getIRI();
            if (!propIris.contains(propIri)) return;
            OWLClassExpression filler = svf.getFiller();
            if (filler.isOWLClass()) {
                OWLClass c = filler.asOWLClass();
                if (!satisfiedFrags.contains(c.getIRI().getFragment())) acc.add(c);
            } else {
                gapLeavesOf(tbox, filler, satisfiedFrags, propIris, path, acc);
            }
            return;
        }
        if (expr.isOWLClass()) {
            OWLClass c = expr.asOWLClass();
            if (c.isOWLThing() || c.isOWLNothing()) return;
            if (satisfiedFrags.contains(c.getIRI().getFragment())) return;
            if (path.contains(c)) return;
            Set<OWLClass> next = new HashSet<>(path);
            next.add(c);
            boolean hasEq = false;
            for (OWLEquivalentClassesAxiom ax :
                    tbox.equivalentClassesAxioms(c).collect(Collectors.toList())) {
                for (OWLClassExpression e : ax.getClassExpressions()) {
                    if (e.isOWLClass() && e.asOWLClass().equals(c)) continue;
                    hasEq = true;
                    gapLeavesOf(tbox, e, satisfiedFrags, propIris, next, acc);
                }
            }
            if (!hasEq) acc.add(c);   // 无定义 → 原子要求（如六经类）
        }
    }

    /**
     * 收集某类自身声明的「或然症」填充类（注解属性 {@code possibleSymptom}）。
     *
     * <p>或然症是脉象/舌象/腹证进入方证匹配的<b>唯一通道</b>（方证定义层 {@code equivalentClass}
     * 只用 {@code you_zhengzhuang}）。此方法只读类自身的注解，不沿父类继承。
     */
    public static Set<OWLClass> collectPossibleSymptoms(OWLOntology tbox, OWLClass cls, IRI propIri) {
        Set<OWLClass> result = new LinkedHashSet<>();
        OWLDataFactory df = tbox.getOWLOntologyManager().getOWLDataFactory();
        for (OWLAnnotationAssertionAxiom ax :
                tbox.annotationAssertionAxioms(cls.getIRI()).collect(Collectors.toList())) {
            if (!ax.getProperty().getIRI().equals(propIri)) continue;
            OWLAnnotationValue v = ax.getValue();
            if (v instanceof IRI iri) result.add(df.getOWLClass(iri));
        }
        return result;
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