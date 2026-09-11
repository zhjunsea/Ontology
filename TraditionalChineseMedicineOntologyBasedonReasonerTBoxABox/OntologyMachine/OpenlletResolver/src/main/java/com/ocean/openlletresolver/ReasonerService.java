package com.ocean.openlletresolver;

import com.clarkparsia.owlapi.explanation.BlackBoxExplanation;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owl.explanation.api.Explanation;
import org.semanticweb.owl.explanation.api.ExplanationGenerator;
import org.semanticweb.owl.explanation.api.ExplanationGeneratorFactory;
import org.semanticweb.owl.explanation.impl.blackbox.checker.InconsistentOntologyExplanationGeneratorFactory;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.vocab.OWLFacet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.semanticweb.owlapi.apibinding.OWLManager;

import org.semanticweb.owlapi.model.AddImport;
import org.semanticweb.owlapi.model.OWLOntologyChange;

public class ReasonerService {

    private static final Logger log = LoggerFactory.getLogger(ReasonerService.class);

    private final OWLDataFactory dataFactory;
    private final OWLReasonerFactory factory;
    private OWLReasoner reasoner;

    private static final int POOL_SIZE = 0;
    private final BlockingQueue<PooledReasonerContext> reasonerPool =
            new ArrayBlockingQueue<>(Math.max(POOL_SIZE, 1));

    public ReasonerService(OntologyService ontologySrv) {
        this.factory = new OpenlletReasonerFactory();
        this.dataFactory = ontologySrv.getDataFactory();

        long t0 = System.currentTimeMillis();
        this.reasoner = factory.createReasoner(ontologySrv.gettBoxOntology());
        this.reasoner.flush();
        log.info("[ReasonerService] 推理器创建 + flush 完成，耗时 {} ms",
                System.currentTimeMillis() - t0);

        long t1 = System.currentTimeMillis();
        this.reasoner.precomputeInferences(
                InferenceType.CLASS_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.DISJOINT_CLASSES
        );
        /*this.reasoner.precomputeInferences(
                InferenceType.CLASS_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_ASSERTIONS,
                InferenceType.DIFFERENT_INDIVIDUALS,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.SAME_INDIVIDUAL,
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.DISJOINT_CLASSES
                );*/
        log.info("[ReasonerService] TBox 层 precompute 完成，耗时 {} ms",
                System.currentTimeMillis() - t1);

        if (!reasoner.isConsistent()) {
            ExplainInconsistencyWithOWLExplanation(ontologySrv);
        }

        initReasonerPool(ontologySrv);

        log.info("[ReasonerService] 初始化完成，总耗时 {} ms",
                System.currentTimeMillis() - t0);
    }

    private void initReasonerPool(OntologyService ontologySrv) {
        if (POOL_SIZE <= 0) {
            log.info("[ReasonerService] 池化已禁用（POOL_SIZE={}），跳过", POOL_SIZE);
            return;
        }
        OWLOntology tbox = ontologySrv.gettBoxOntology();
        IRI tboxIRI = tbox.getOntologyID().getOntologyIRI()
                .orElseThrow(() -> new IllegalStateException("TBox 缺少 IRI"));
        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                PooledReasonerContext ctx = createPooledContext(ontologySrv, tbox, tboxIRI, i);
                reasonerPool.offer(ctx);
            } catch (Exception e) {
                log.error("创建 Reasoner 池实例 #{} 失败", i, e);
            }
        }
    }

    private PooledReasonerContext createPooledContext(OntologyService ontologySrv,
                                                      OWLOntology tbox, IRI tboxIRI,
                                                      int index) throws OWLOntologyCreationException {
        OWLOntologyManager mgr = ontologySrv.getManager();
        IRI tempIRI = IRI.create("urn:pooled:reasoner:" + index);
        OWLOntology tempOnt = mgr.createOntology(tempIRI);
        OWLImportsDeclaration importDecl = dataFactory.getOWLImportsDeclaration(tboxIRI);
        mgr.applyChange(new AddImport(tempOnt, importDecl));
        OWLReasoner r = factory.createReasoner(tempOnt);
        return new PooledReasonerContext(mgr, tempOnt, r);
    }

    public PooledReasonerContext borrowContext(long timeoutMs) throws InterruptedException {
        PooledReasonerContext ctx = reasonerPool.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (ctx == null) {
            throw new IllegalStateException("Reasoner 池耗尽，等待超时 " + timeoutMs + "ms");
        }
        return ctx;
    }

    public void returnContext(PooledReasonerContext ctx) {
        if (ctx == null) return;
        try {
            if (!ctx.sessionAxioms.isEmpty()) {
                ctx.manager.removeAxioms(ctx.ontology, ctx.sessionAxioms);
                ctx.sessionAxioms.clear();
                ctx.reasoner.flush();
            }
        } catch (Exception e) {
            log.warn("归还 Context 时清理失败", e);
        }
        reasonerPool.offer(ctx);
    }

    public static class PooledReasonerContext implements AutoCloseable {
        public final OWLOntologyManager manager;
        public final OWLOntology ontology;
        public final OWLReasoner reasoner;
        private final Set<OWLAxiom> sessionAxioms = new HashSet<>();

        PooledReasonerContext(OWLOntologyManager m, OWLOntology o, OWLReasoner r) {
            this.manager = m;
            this.ontology = o;
            this.reasoner = r;
        }

        public void addAxioms(Set<OWLAxiom> axioms) {
            manager.addAxioms(ontology, axioms);
            sessionAxioms.addAll(axioms);
        }

        public void flush() { reasoner.flush(); }

        public OWLDataFactory getDataFactory() { return manager.getOWLDataFactory(); }

        @Override
        public void close() { sessionAxioms.clear(); }
    }

    public OWLReasonerFactory getFactory() { return factory; }
    public OWLReasoner getReasoner() { return reasoner; }
    public void setReasoner(OWLReasoner reasoner) { this.reasoner = reasoner; }

    public void ExplainInconsistencyWithOWLExplanation(OntologyService ontologySrv) {
        log.error("本体不一致！开始生成冲突解释...");
        try {
            Supplier<OWLOntologyManager> managerSupplier = ontologySrv::getManager;
            ExplanationGeneratorFactory<OWLAxiom> genFac =
                    new InconsistentOntologyExplanationGeneratorFactory(
                            factory, dataFactory, managerSupplier, 5000);
            ExplanationGenerator<OWLAxiom> gen =
                    genFac.createExplanationGenerator(ontologySrv.gettBoxOntology());

            OWLAxiom inconsistencyAxiom = dataFactory.getOWLSubClassOfAxiom(
                    dataFactory.getOWLThing(), dataFactory.getOWLNothing());

            Set<Explanation<OWLAxiom>> explanations = gen.getExplanations(inconsistencyAxiom, 5);
            int idx = 0;
            for (Explanation<OWLAxiom> exp : explanations) {
                log.error("--- 冲突解释 #{} ---", ++idx);
                exp.getAxioms().forEach(a -> log.error("  → {}", a));
            }
            if (explanations.isEmpty()) {
                log.error("未生成任何解释，请检查本体或增加解释数量限制。");
            }
        } catch (Exception e) {
            log.error("生成解释时发生异常：{}", e.getMessage(), e);
        }
        throw new InconsistentOntologyException("本体不一致，已输出冲突解释（见上方），推理终止。");
    }

    public void ExplainInconsistencyWithBlackBoxExplanation(OWLOntology ontology) {
        log.error("\n🔍 [诊断] 正在分析不一致原因...");
        List<String> datatypeViolations;
        try {
            datatypeViolations = findSyntaxLevelViolations(ontology);
        } catch (Exception e) {
            log.error("❌ findSyntaxLevelViolations 执行失败!", e);
            throw e;
        }

        if (!datatypeViolations.isEmpty()) {
            datatypeViolations.forEach(v -> log.error("   → {}", v));
            return;
        }

        BlackBoxExplanation explainer = null;
        try {
            explainer = new BlackBoxExplanation(ontology, factory, reasoner);
            Set<OWLAxiom> inconsistentAxioms = explainer.getExplanation(dataFactory.getOWLThing());
            if (inconsistentAxioms == null || inconsistentAxioms.isEmpty()) {
                log.error("⚠️ 推理器报告不一致，但 BlackBox 未提取到具体公理");
            } else {
                inconsistentAxioms.forEach(a -> log.error("   → {}", a));
            }
        } catch (Exception e) {
            log.error("⚠️ 解释器执行异常: {}", e.getMessage(), e);
        } finally {
            if (explainer != null) {
                try { explainer.dispose(); } catch (Exception ignored) {}
            }
        }
    }

    private List<String> findSyntaxLevelViolations(OWLOntology ontology) {
        List<String> violations = new ArrayList<>();
        Map<IRI, List<DatatypeConstraint>> propertyConstraints = extractDatatypeConstraints(ontology);

        for (OWLAxiom axiom : ontology.getAxioms()) {
            if (axiom instanceof OWLDataPropertyAssertionAxiom dpaa) {
                OWLDataPropertyExpression propExpr = dpaa.getProperty();
                if (propExpr instanceof OWLDataProperty prop) {
                    List<DatatypeConstraint> constraints = propertyConstraints.get(prop.getIRI());
                    if (constraints != null) {
                        Set<String> declaredTypes = ontology.getClassAssertionAxioms(dpaa.getSubject())
                                .stream()
                                .map(OWLClassAssertionAxiom::getClassExpression)
                                .filter(OWLClassExpression::isOWLClass)
                                .map(ce -> ce.asOWLClass().getIRI().getShortForm())
                                .collect(Collectors.toSet());
                        boolean noDeclaredType = declaredTypes.isEmpty();
                        OWLLiteral lit = dpaa.getObject();
                        for (DatatypeConstraint c : constraints) {
                            if (!noDeclaredType && !declaredTypes.contains(c.sourceClassName)) continue;
                            String err = c.validate(lit, dpaa.getSubject(), prop.getIRI());
                            if (err != null) violations.add(err);
                        }
                    }
                }
            }

            if (axiom instanceof OWLDataPropertyAssertionAxiom dpaa) {
                OWLDataPropertyExpression propExpr = dpaa.getProperty();
                if (propExpr instanceof OWLDataProperty prop) {
                    boolean isFunctional = ontology.getFunctionalDataPropertyAxioms(prop).stream()
                            .anyMatch(a -> a.getProperty().equals(prop));
                    if (isFunctional) {
                        long count = ontology.getDataPropertyAssertionAxioms(dpaa.getSubject()).stream()
                                .filter(a -> a.getProperty().equals(prop))
                                .count();
                        if (count > 1) {
                            violations.add(String.format("[Functional] %s 是函数型属性，但个体 %s 有 %d 个值",
                                    prop.getIRI().getShortForm(), dpaa.getSubject().toStringID(), count));
                        }
                    }
                }
            }

            if (axiom instanceof OWLClassAssertionAxiom caa && caa.getIndividual().isNamed()) {
                OWLNamedIndividual ind = caa.getIndividual().asOWLNamedIndividual();
                Set<OWLClass> assertedClasses = ontology.getClassAssertionAxioms(ind).stream()
                        .map(OWLClassAssertionAxiom::getClassExpression)
                        .filter(OWLClassExpression::isOWLClass)
                        .map(OWLClassExpression::asOWLClass)
                        .collect(Collectors.toSet());

                for (OWLDisjointClassesAxiom dca : ontology.getAxioms(AxiomType.DISJOINT_CLASSES)) {
                    Set<OWLClass> disjointSet = dca.classExpressions()
                            .filter(OWLClassExpression::isOWLClass)
                            .map(OWLClassExpression::asOWLClass)
                            .collect(Collectors.toSet());
                    Set<OWLClass> intersection = new HashSet<>(assertedClasses);
                    intersection.retainAll(disjointSet);
                    if (intersection.size() >= 2) {
                        violations.add(String.format("[Disjoint] 个体 %s 同时属于互斥类: %s",
                                ind.getIRI().getShortForm(), intersection));
                    }
                }
            }
        }
        return violations;
    }

    private Map<IRI, List<DatatypeConstraint>> extractDatatypeConstraints(OWLOntology ontology) {
        Map<IRI, List<DatatypeConstraint>> constraintMap = new HashMap<>();
        for (OWLSubClassOfAxiom subAx : ontology.getAxioms(AxiomType.SUBCLASS_OF)) {
            OWLClassExpression superExpr = subAx.getSuperClass();
            OWLDataProperty dataProp = null;
            OWLDataRange dataRange = null;

            if (superExpr instanceof OWLDataAllValuesFrom dataAllValues) {
                dataProp = dataAllValues.getProperty().asOWLDataProperty();
                dataRange = dataAllValues.getFiller();
            } else if (superExpr instanceof OWLObjectAllValuesFrom objAllValues) {
                if (objAllValues.getProperty() instanceof OWLDataProperty dp
                        && objAllValues.getFiller() instanceof OWLDataRange dr) {
                    dataProp = dp;
                    dataRange = dr;
                }
            }

            if (dataProp == null || dataRange == null) continue;

            OWLDatatypeRestriction restriction = resolveDatatypeRestriction(dataRange, ontology);
            if (restriction == null) continue;

            String sourceClass = subAx.getSubClass().isOWLClass()
                    ? subAx.getSubClass().asOWLClass().getIRI().getShortForm()
                    : "Anonymous";

            DatatypeConstraint constraint = DatatypeConstraint.from(restriction, sourceClass);
            if (constraint != null) {
                constraintMap.computeIfAbsent(dataProp.getIRI(), k -> new ArrayList<>()).add(constraint);
            }
        }
        return constraintMap;
    }

    private OWLDatatypeRestriction resolveDatatypeRestriction(OWLDataRange filler, OWLOntology ontology) {
        if (filler instanceof OWLDatatypeRestriction restriction) return restriction;
        if (filler instanceof OWLDatatype dt) {
            for (OWLDatatypeDefinitionAxiom defAx : ontology.getDatatypeDefinitions(dt)) {
                if (defAx.getDataRange() instanceof OWLDatatypeRestriction r) return r;
            }
        }
        return null;
    }

    private static double safeParseDouble(OWLLiteral literal) {
        try {
            return literal.parseDouble();
        } catch (NumberFormatException e) {
            return Double.parseDouble(literal.getLiteral());
        }
    }

    private static class DatatypeConstraint {
        final double min, max;
        final boolean hasMin, hasMax;
        final String sourceClassName;

        private DatatypeConstraint(double min, boolean hasMin, double max, boolean hasMax, String s) {
            this.min = min; this.hasMin = hasMin;
            this.max = max; this.hasMax = hasMax;
            this.sourceClassName = s;
        }

        static DatatypeConstraint from(OWLDatatypeRestriction restriction, String sourceClass) {
            Double min = null, max = null;
            for (OWLFacetRestriction facet : restriction.getFacetRestrictions()) {
                if (facet.getFacet() == OWLFacet.MIN_INCLUSIVE) min = safeParseDouble(facet.getFacetValue());
                else if (facet.getFacet() == OWLFacet.MAX_INCLUSIVE) max = safeParseDouble(facet.getFacetValue());
            }
            if (min == null && max == null) return null;
            return new DatatypeConstraint(
                    min != null ? min : Double.NEGATIVE_INFINITY, min != null,
                    max != null ? max : Double.POSITIVE_INFINITY, max != null, sourceClass);
        }

        String validate(OWLLiteral literal, OWLIndividual individual, IRI propIRI) {
            try {
                double value = safeParseDouble(literal);
                if (hasMin && value < min) {
                    return String.format("[DatatypeRestriction] 个体 %s 属性 <%s> 值 %.2f < 最小值 %.2f",
                            individual.toStringID(), propIRI.getShortForm(), value, min);
                }
                if (hasMax && value > max) {
                    return String.format("[DatatypeRestriction] 个体 %s 属性 <%s> 值 %.2f > 最大值 %.2f",
                            individual.toStringID(), propIRI.getShortForm(), value, max);
                }
            } catch (NumberFormatException ignored) {}
            return null;
        }
    }

    public <T> T withReasoner(OWLOntology ontology, Function<OWLReasoner, T> queryFn)
            throws OWLOntologyCreationException {
        OWLReasoner localReasoner = OpenlletReasonerFactory.getInstance().createReasoner(ontology);
        try {
            localReasoner.flush();
            localReasoner.precomputeInferences(
                    InferenceType.CLASS_HIERARCHY,
                    InferenceType.OBJECT_PROPERTY_HIERARCHY,
                    InferenceType.DATA_PROPERTY_HIERARCHY,
                    InferenceType.DISJOINT_CLASSES);
            if (!localReasoner.isConsistent()) {
                ExplainInconsistencyWithBlackBoxExplanation(ontology);
                throw new IllegalStateException("一致性检查失败！");
            }
            return queryFn.apply(localReasoner);
        } finally {
            localReasoner.dispose();
        }
    }

    public Set<String> getInferredTypes(OWLReasoner r, OWLDataFactory df, String classIRI) {
        OWLClass targetClass = df.getOWLClass(IRI.create(classIRI));
        return r.getInstances(targetClass, false).entities()
                .flatMap(ind -> r.getTypes(ind, false).entities()
                        .filter(cls -> !cls.equals(targetClass) && !cls.isOWLThing())
                        .map(cls -> ind.getIRI().getShortForm() + " ⇒ " + cls.getIRI().getShortForm()))
                .collect(Collectors.toSet());
    }

    public Set<String> getInferredPropertyValues(OWLReasoner r, OWLDataFactory df,
                                                 String individualIRI, String propertyIRI) {
        OWLNamedIndividual individual = df.getOWLNamedIndividual(IRI.create(individualIRI));
        OWLObjectProperty property = df.getOWLObjectProperty(IRI.create(propertyIRI));
        return r.getObjectPropertyValues(individual, property).entities()
                .map(i -> i.getIRI().getShortForm())
                .collect(Collectors.toSet());
    }

    public Set<OWLClass> getSuperClassesIncludingSelf(String classIRI) {
        if (classIRI == null || classIRI.isBlank()) return Collections.emptySet();
        try {
            if (reasoner == null) return Collections.emptySet();
            OWLClass targetClass = dataFactory.getOWLClass(IRI.create(classIRI));
            Set<OWLClass> result = reasoner.getSuperClasses(targetClass, false)
                    .entities().collect(Collectors.toCollection(LinkedHashSet::new));
            result.add(targetClass);
            return Collections.unmodifiableSet(result);
        } catch (Exception e) {
            log.error("getSuperClassesIncludingSelf 异常 | classIRI={}", classIRI);
            return Collections.emptySet();
        }
    }
}