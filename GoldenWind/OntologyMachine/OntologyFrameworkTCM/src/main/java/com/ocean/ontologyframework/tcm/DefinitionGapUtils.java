package com.ocean.ontologyframework.tcm;

import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationAssertionAxiom;
import org.semanticweb.owlapi.model.OWLAnnotationValue;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLEquivalentClassesAxiom;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 方证/判据定义的「结构化主证缺口」工具。
 *
 * <p>本类原为 {@code com.ocean.openlletresolver.OntologyModuleUtils} 中的方法，
 * 按工程约束（禁止修改 OntopOBDAHandler / OpenlletResolver）迁出到 OntologyFramework。
 * 逻辑与原实现逐字一致，仅改变归属包。
 *
 * <p>与 {@code OntologyModuleUtils.collectRestrictionFillers} 的分工：
 * 后者是「索引」用途，须收集 OR 分支的<b>全部</b>填充符（命中任一即登记）；
 * 本类是「判定」用途，须区分 AND/OR 语义（union 计 1，intersection 计子项数）。
 * 二者不可混用——把 union 平铺当 AND 正是此前 {@code required} 失真的根因。
 */
public final class DefinitionGapUtils {

    private DefinitionGapUtils() {
    }

    // ============================================================
    // 1. 结构化「主证缺口」gap（判定用途，区分 AND/OR 语义）
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
            // 取「最小缺口分支」——任一满足即可，故只需补最省力的那一支。
            // 并列最小者按分支签名（类 fragment 字典序）取定：OWLAPI 的 getOperands()
            // 返回无序集合，若按迭代顺序取首，同一输入在不同 JVM/哈希序下会问出不同的
            // 脉象（如 沉迟脉 / 弱脉 随机漂移），追问清单与测试均不可复现。
            OWLClassExpression best = null;
            int bestGap = Integer.MAX_VALUE;
            String bestKey = null;
            for (OWLClassExpression op : union.getOperands()) {
                int g = gapOf(tbox, op, satisfiedFrags, propIris, path);
                String key = branchKey(op);
                if (g < bestGap || (g == bestGap
                        && (bestKey == null || key.compareTo(bestKey) < 0))) {
                    bestGap = g;
                    best = op;
                    bestKey = key;
                }
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
     * 分支签名：表达式中全部具名类的 fragment 按字典序拼接，用于在「缺口并列最小」的
     * OR 分支间做确定性裁决（见 {@link #gapLeavesOf} 的 union 处理）。
     */
    private static String branchKey(OWLClassExpression e) {
        return e.classesInSignature()
                .map(c -> c.getIRI().getFragment())
                .sorted()
                .collect(Collectors.joining(","));
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
}
