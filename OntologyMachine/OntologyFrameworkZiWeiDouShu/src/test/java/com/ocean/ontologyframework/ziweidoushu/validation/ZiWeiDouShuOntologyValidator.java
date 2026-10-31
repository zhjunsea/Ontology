package com.ocean.ontologyframework.ziweidoushu.validation;

import com.ocean.openlletresolver.BackendService;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 命例本体合规校验器（参照 {@code Pizza.PizzaOntologyValidator} 形态，**不依赖 OBDA 映射缓存**）。
 *
 * <p>逐条命例做两类校验：
 * <ol>
 *   <li><b>约束字段校验</b>：对照《本体核心设计》§4 {@code owl:Restriction} 清单，检查
 *       {@code hasPalace=12}、{@code hasLifePalace/hasBodyPalace/hasBureau cardinality 1} 等；</li>
 *   <li><b>一致性校验</b>：据命例构造临时公理，经 {@link BackendService#safeVerifyAndDBExecution}
 *       注入推理并检查一致性（注入后自动回滚，不污染基线）。</li>
 * </ol>
 */
public class ZiWeiDouShuOntologyValidator implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ZiWeiDouShuOntologyValidator.class);

    private static final String CORE = "http://example.org/ziweidoushu/core#";
    private static final String CHART = "http://example.org/ziweidoushu/chart#";
    private static final String ABOX = "http://example.org/ziweidoushu/test-abox#";

    private final BackendService backendService;
    private final OWLOntology ontology;
    private final OWLDataFactory df;
    private final OWLReasoner reasoner;

    public ZiWeiDouShuOntologyValidator(BackendService backendService) {
        this.backendService = backendService;
        this.ontology = backendService.getTBoxOntology();
        this.df = backendService.getDataFactory();
        this.reasoner = backendService.getReasonerService().getReasoner();
    }

    public ValidationResult validate(Map<String, Object> instance) {
        List<String> violations = new ArrayList<>();

        String name = getStr(instance, "name");
        String type = getStr(instance, "type");
        String lifePalace = getStr(instance, "life_palace");
        String bodyPalace = getStr(instance, "body_palace");
        String bureau = getStr(instance, "bureau");
        int palaceCount = getInt(instance, "palace_count", -1);

        if (name.isBlank()) {
            violations.add("name 不能为空");
        }
        if (type.isBlank()) {
            violations.add("type 不能为空");
        }
        if (!"NatalChart".equals(type)) {
            violations.add("type 应为 NatalChart，实际=" + type);
        }
        if (palaceCount != 12) {
            violations.add("chart:hasPalace 应为 12（卷二·安十二宮例），实际=" + palaceCount);
        }
        if (lifePalace.isBlank()) {
            violations.add("chart:hasLifePalace 必填（owl:cardinality 1；卷二·安身命例）");
        }
        if (bodyPalace.isBlank()) {
            violations.add("chart:hasBodyPalace 必填（owl:cardinality 1；卷二·安身命例）");
        }
        if (bureau.isBlank()) {
            violations.add("chart:hasBureau 必填（owl:cardinality 1；卷二·起五行寅例）");
        }

        if (!violations.isEmpty()) {
            return ValidationResult.invalid(violations, instance);
        }

        Set<OWLAxiom> tempAxioms = buildChartAxioms(name, palaceCount);
        try {
            backendService.safeVerifyAndDBExecution(tempAxioms, (java.util.function.Consumer<java.sql.Connection>) null);
        } catch (Exception e) {
            violations.add("本体一致性校验失败: " + e.getMessage());
        } finally {
            try {
                OWLOntologyManager manager = ontology.getOWLOntologyManager();
                manager.removeAxioms(ontology, tempAxioms);
                reasoner.flush();
            } catch (Exception e) {
                log.error("🧹 临时公理回滚失败，验证器状态可能已污染", e);
            }
        }

        return violations.isEmpty()
                ? ValidationResult.valid(instance)
                : ValidationResult.invalid(violations, instance);
    }

    private Set<OWLAxiom> buildChartAxioms(String name, int palaceCount) {
        Set<OWLAxiom> axioms = new HashSet<>();

        OWLClass natalChart = df.getOWLClass(IRI.create(CHART + "NatalChart"));
        OWLClass palaceCls = df.getOWLClass(IRI.create(CHART + "Palace"));
        OWLClass lifeCls = df.getOWLClass(IRI.create(CHART + "LifePalace"));
        OWLClass bodyCls = df.getOWLClass(IRI.create(CHART + "BodyPalace"));
        OWLClass bureauCls = df.getOWLClass(IRI.create(CORE + "FivePhaseBureau"));

        OWLObjectProperty hasPalace = df.getOWLObjectProperty(IRI.create(CHART + "hasPalace"));
        OWLObjectProperty hasLifePalace = df.getOWLObjectProperty(IRI.create(CHART + "hasLifePalace"));
        OWLObjectProperty hasBodyPalace = df.getOWLObjectProperty(IRI.create(CHART + "hasBodyPalace"));
        OWLObjectProperty hasBureau = df.getOWLObjectProperty(IRI.create(CHART + "hasBureau"));

        OWLNamedIndividual chart = df.getOWLNamedIndividual(IRI.create(ABOX + name));
        axioms.add(df.getOWLClassAssertionAxiom(natalChart, chart));

        for (int i = 1; i <= palaceCount; i++) {
            OWLNamedIndividual p = df.getOWLNamedIndividual(IRI.create(ABOX + name + "_Palace_" + i));
            axioms.add(df.getOWLClassAssertionAxiom(palaceCls, p));
            axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasPalace, chart, p));
        }

        OWLNamedIndividual life = df.getOWLNamedIndividual(IRI.create(ABOX + name + "_Life"));
        axioms.add(df.getOWLClassAssertionAxiom(lifeCls, life));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasLifePalace, chart, life));

        OWLNamedIndividual body = df.getOWLNamedIndividual(IRI.create(ABOX + name + "_Body"));
        axioms.add(df.getOWLClassAssertionAxiom(bodyCls, body));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasBodyPalace, chart, body));

        OWLNamedIndividual bureauInd = df.getOWLNamedIndividual(IRI.create(ABOX + name + "_Bureau"));
        axioms.add(df.getOWLClassAssertionAxiom(bureauCls, bureauInd));
        axioms.add(df.getOWLObjectPropertyAssertionAxiom(hasBureau, chart, bureauInd));

        return axioms;
    }

    private static String getStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? "" : val.toString().trim();
    }

    private static int getInt(Map<String, Object> map, String key, int fallback) {
        Object val = map.get(key);
        if (val == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void close() {
        log.debug("🧹 ZiWeiDouShuOntologyValidator 关闭（共享 BackendService，不释放其推理机）");
    }
}
