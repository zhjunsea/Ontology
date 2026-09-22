import com.ocean.openlletresolver.OntologyService;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.util.ArrayList;
import java.util.List;

/**
 * 「分类为什么慢」根因消融探针。
 *
 * 用法: java AblationProbe <tcm-all.owl 路径> <mode>
 *   mode 可取（可组合，用 _ 连接）：
 *     full                 不改动，基线
 *     noabox               移除全部 ABox 断言公理（ClassAssertion / 属性断言 / same / different）
 *     nohasvalue           移除所有含 owl:hasValue 的类公理（= 方证的 ∃you_chufang.{方剂} 名义量）
 *     norange              移除对象属性的 rdfs:range / rdfs:domain 公理（全局 ∀ 约束）
 *     noeq                 移除所有 owl:equivalentClass 定义公理
 *     nosubrest            移除所有「超类为匿名 Restriction」的 rdfs:subClassOf 公理
 *
 * 目的：定位 CLASS_HIERARCHY 分类 200~345s 的真正来源，而不是把开销挪到别处。
 */
public class AblationProbe {

    public static void main(String[] args) throws Exception {
        String path = args[0];
        String mode = args.length > 1 ? args[1] : "full";

        long t0 = System.currentTimeMillis();
        OntologyService os = new OntologyService(path);
        OWLOntology ont = os.gettBoxOntology();
        System.out.println("[load] " + (System.currentTimeMillis() - t0) + " ms"
                + ", 公理=" + ont.getAxiomCount()
                + ", 个体=" + ont.individualsInSignature().count()
                + ", 类=" + ont.classesInSignature().count());
        System.out.flush();

        OWLOntologyManager mgr = ont.getOWLOntologyManager();
        List<OWLOntologyChange> ch = new ArrayList<>();

        if (mode.contains("noabox")) {
            for (OWLAxiom ax : ont.getAxioms()) {
                if (ax instanceof OWLClassAssertionAxiom
                        || ax instanceof OWLObjectPropertyAssertionAxiom
                        || ax instanceof OWLDataPropertyAssertionAxiom
                        || ax instanceof OWLNegativeObjectPropertyAssertionAxiom
                        || ax instanceof OWLNegativeDataPropertyAssertionAxiom
                        || ax instanceof OWLSameIndividualAxiom
                        || ax instanceof OWLDifferentIndividualsAxiom) {
                    ch.add(new RemoveAxiom(ont, ax));
                }
            }
        }

        if (mode.contains("nohasvalue")) {
            for (OWLAxiom ax : ont.getAxioms()) {
                if (ax instanceof OWLEquivalentClassesAxiom eq && containsHasValue(eq.getClassExpressions())) {
                    ch.add(new RemoveAxiom(ont, ax));
                } else if (ax instanceof OWLSubClassOfAxiom sc
                        && (hasHasValue(sc.getSubClass()) || hasHasValue(sc.getSuperClass()))) {
                    ch.add(new RemoveAxiom(ont, ax));
                }
            }
        }

        if (mode.contains("norange")) {
            for (OWLAxiom ax : ont.getAxioms()) {
                if (ax instanceof OWLObjectPropertyRangeAxiom
                        || ax instanceof OWLObjectPropertyDomainAxiom
                        || ax instanceof OWLDataPropertyRangeAxiom
                        || ax instanceof OWLDataPropertyDomainAxiom) {
                    ch.add(new RemoveAxiom(ont, ax));
                }
            }
        }

        if (mode.contains("noeq")) {
            for (OWLAxiom ax : ont.getAxioms()) {
                if (ax instanceof OWLEquivalentClassesAxiom) {
                    ch.add(new RemoveAxiom(ont, ax));
                }
            }
        }

        if (mode.contains("nosubrest")) {
            for (OWLAxiom ax : ont.getAxioms()) {
                if (ax instanceof OWLSubClassOfAxiom sc
                        && !sc.getSuperClass().isNamed()) {
                    ch.add(new RemoveAxiom(ont, ax));
                }
            }
        }

        // noind = 移除「提及任何命名个体」的全部公理（声明 + 断言）。
        // 这精确模拟「把 ABox 模块从 tcm-all.owl 的 import 中摘掉」后 gettBoxOntology() 的样子：
        // TBox 保留（含 hasValue 名义量），但个体不再声明。
        if (mode.contains("noind")) {
            for (OWLAxiom ax : ont.getAxioms()) {
                boolean touchesInd = ax.signature().anyMatch(e -> e.isOWLNamedIndividual())
                        || ax instanceof OWLClassAssertionAxiom
                        || ax instanceof OWLObjectPropertyAssertionAxiom
                        || ax instanceof OWLDataPropertyAssertionAxiom
                        || ax instanceof OWLSameIndividualAxiom
                        || ax instanceof OWLDifferentIndividualsAxiom;
                if (touchesInd) {
                    ch.add(new RemoveAxiom(ont, ax));
                }
            }
        }

        if (!ch.isEmpty()) {
            mgr.applyChanges(ch);
            System.out.println("[ablate] mode=" + mode + " 移除公理 " + ch.size()
                    + " 条 → 剩余 " + ont.getAxiomCount()
                    + " 条, 个体=" + ont.individualsInSignature().count()
                    + ", 类=" + ont.classesInSignature().count());
            System.out.flush();
        }

        OWLReasoner r = new OpenlletReasonerFactory().createReasoner(ont);
        r.flush();
        long t1 = System.currentTimeMillis();
        r.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        long d = System.currentTimeMillis() - t1;
        System.out.println("[CLASS_HIERARCHY] mode=" + mode + " = " + d + " ms");
        System.out.flush();

        try {
            System.out.println("[unsat] " + r.getUnsatisfiableClasses().getEntitiesMinusBottom().size()
                    + " 个不可满足类");
            System.out.flush();
        } catch (Throwable e) {
            System.out.println("[unsat] 查询失败 " + e);
        }

        r.dispose();
        System.out.println("[done] mode=" + mode + " total = " + (System.currentTimeMillis() - t0) + " ms");
        System.out.flush();
    }

    private static boolean containsHasValue(java.util.Set<OWLClassExpression> exprs) {
        for (OWLClassExpression e : exprs) {
            if (hasHasValue(e)) return true;
        }
        return false;
    }

    private static boolean hasHasValue(OWLClassExpression e) {
        if (e instanceof OWLObjectHasValue || e instanceof OWLDataHasValue) return true;
        for (OWLClassExpression n : e.getNestedClassExpressions()) {
            if (n instanceof OWLObjectHasValue || n instanceof OWLDataHasValue) return true;
        }
        return false;
    }
}
