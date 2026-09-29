package com.ocean.ontologyframework.tmsd;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.ClassExpressionType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassAssertionAxiom;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataAllValuesFrom;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataHasValue;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyAssertionAxiom;
import org.semanticweb.owlapi.model.OWLDatatypeRestriction;
import org.semanticweb.owlapi.model.OWLFacetRestriction;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectCardinalityRestriction;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLSubClassOfAxiom;
import org.semanticweb.owlapi.vocab.OWLFacet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 塔架中段本体（{@code TowerMidSection.owl}）的<b>运行时解析模型</b>。
 *
 * <p>按「零硬编码」铁律：引擎所需的一切业务真值（版本、数值约束、值集、机型映射）
 * 均在本类中由 OWL API 从本体文件解析得到，不写死任何业务字面量。
 *
 * <p>解析来源（本体 TBox/ABox）：
 * <ul>
 *   <li>{@code owl:versionInfo} → {@link #versionInfo}；</li>
 *   <li>{@code Class ⊑ Restriction}：
 *     <ul>
 *       <li>{@code owl:hasValue}（单值数值）→ {@link #numeric} 中的 {@code HAS_VALUE}；</li>
 *       <li>{@code owl:allValuesFrom rdfs:Datatype[min/max(In|Ex)clusive]} → {@code RANGE}/{@code GT}；</li>
 *       <li>{@code owl:hasValue}（同一属性多条 → 值集）→ {@link #valueSets}；</li>
 *       <li>{@code owl:hasValue}（字符串）→ {@link #stringValues}；</li>
 *       <li>{@code owl:qualifiedCardinality/min/max} → {@link #cardinality}；</li>
 *     </ul>
 *   </li>
 *   <li>{@code owl:hasKey} → {@link #hasKeys}；</li>
 *   <li>机型个体（{@code :Model} 实例）的 {@code modelName} + 托架规格 → {@link #modelParams}。</li>
 * </ul>
 */
public final class TmsdOntologyModel {

    /** 一条本体数值约束（hasValue / 区间 / 严格大于）。 */
    public record Constraint(String hostClass, String property, String kind,
                             double lower, double upper, boolean exclusive, String raw) {

        /** 数值是否落在约束内。 */
        public boolean accepts(double v) {
            return switch (kind) {
                case "HAS_VALUE" -> Math.abs(v - lower) < 1e-6;
                case "RANGE", "GT" -> (Double.isNaN(lower) || (exclusive ? v > lower : v >= lower))
                        && (Double.isNaN(upper) || v <= upper + 1e-6);
                default -> true;
            };
        }

        @Override
        public String toString() {
            return hostClass + " ⊑ ∃" + property + " [" + raw + "]";
        }
    }

    private final String versionInfo;
    private final List<Constraint> numeric;
    private final List<String> cardinality;
    private final List<String> hasKeys;
    private final Map<String, List<Double>> valueSets;
    private final Map<String, String> stringValues;
    private final Map<String, double[]> modelParams;

    private TmsdOntologyModel(String versionInfo, List<Constraint> numeric, List<String> cardinality,
                              List<String> hasKeys, Map<String, List<Double>> valueSets,
                              Map<String, String> stringValues, Map<String, double[]> modelParams) {
        this.versionInfo = versionInfo;
        this.numeric = List.copyOf(numeric);
        this.cardinality = List.copyOf(cardinality);
        this.hasKeys = List.copyOf(hasKeys);
        this.valueSets = Map.copyOf(valueSets);
        this.stringValues = Map.copyOf(stringValues);
        this.modelParams = Map.copyOf(modelParams);
    }

    // ==================== 解析 ====================

    /** 解析本体文件，得到运行时模型。 */
    public static TmsdOntologyModel parse(Path owlFile) {
        try {
            OWLOntologyManager m = OWLManager.createOWLOntologyManager();
            OWLOntology ont = m.loadOntologyFromOntologyDocument(owlFile.toFile());
            OWLDataFactory df = m.getOWLDataFactory();

            String version = parseVersion(ont, df);
            List<String> hasKeys = parseHasKeys(ont);

            // 按属性分组的多条 hasValue（先收集，再判定「值集」还是「单值约束」）
            Map<String, List<Double>> hasValueNumeric = new LinkedHashMap<>();
            Map<String, String> stringValues = new LinkedHashMap<>();
            List<Constraint> numeric = new ArrayList<>();
            List<String> cardinality = new ArrayList<>();

            for (OWLSubClassOfAxiom ax : ont.getAxioms(AxiomType.SUBCLASS_OF)) {
                if (!(ax.getSubClass() instanceof OWLClass sub)) {
                    continue;
                }
                String host = local(sub.getIRI());
                OWLClassExpression sup = ax.getSuperClass();

                if (sup instanceof OWLDataHasValue dv) {
                    String prop = local(dv.getProperty().asOWLDataProperty().getIRI());
                    OWLLiteral lit = dv.getFiller();
                    if (isNumeric(lit)) {
                        hasValueNumeric.computeIfAbsent(prop, k -> new ArrayList<>()).add(toDouble(lit));
                    } else {
                        stringValues.put(prop, lit.getLiteral());
                    }
                } else if (sup instanceof OWLDataAllValuesFrom avf) {
                    Constraint c = parseRange(host, avf);
                    if (c != null) {
                        numeric.add(c);
                    }
                } else if (sup instanceof OWLObjectCardinalityRestriction card) {
                    cardinality.add(describeCardinality(host, card));
                }
            }

            // 单值数值 hasValue → 数值约束；多值 → 值集
            Map<String, List<Double>> valueSets = new LinkedHashMap<>();
            hasValueNumeric.forEach((prop, vals) -> {
                if (vals.size() > 1) {
                    List<Double> sorted = new ArrayList<>(vals);
                    sorted.sort(Comparator.naturalOrder());
                    valueSets.put(prop, List.copyOf(sorted));
                } else {
                    double v = vals.get(0);
                    numeric.add(new Constraint(hostOf(prop, ont), prop, "HAS_VALUE", v, v, false,
                            "hasValue " + trim(v)));
                }
            });

            Map<String, double[]> modelParams = parseModelParams(ont);

            return new TmsdOntologyModel(version, numeric, cardinality, hasKeys,
                    valueSets, stringValues, modelParams);
        } catch (Exception e) {
            throw new IllegalStateException("解析本体失败: " + owlFile, e);
        }
    }

    private static String parseVersion(OWLOntology ont, OWLDataFactory df) {
        for (OWLAnnotation a : ont.annotations().toList()) {
            if (a.getProperty().equals(df.getOWLVersionInfo())) {
                Optional<OWLLiteral> lit = a.getValue().asLiteral();
                if (lit.isPresent()) {
                    return lit.get().getLiteral();
                }
            }
        }
        return "unknown";
    }

    private static List<String> parseHasKeys(OWLOntology ont) {
        List<String> out = new ArrayList<>();
        ont.getAxioms(AxiomType.HAS_KEY).forEach(k -> {
            String cls = local(k.getClassExpression().asOWLClass().getIRI());
            List<String> props = new ArrayList<>();
            k.getPropertyExpressions().forEach(p -> props.add(local(p.asOWLDataProperty().getIRI())));
            out.add(cls + ": " + props);
        });
        return out;
    }

    private static Constraint parseRange(String host, OWLDataAllValuesFrom avf) {
        double lo = Double.NaN;
        double hi = Double.NaN;
        boolean exclusive = false;
        if (!(avf.getFiller() instanceof OWLDatatypeRestriction dtr)) {
            return null;
        }
        String prop = local(avf.getProperty().asOWLDataProperty().getIRI());
        for (OWLFacetRestriction fr : dtr.getFacetRestrictions()) {
            OWLFacet f = fr.getFacet();
            double val = toDouble(fr.getFacetValue());
            if (f == OWLFacet.MIN_INCLUSIVE) {
                lo = val;
            } else if (f == OWLFacet.MIN_EXCLUSIVE) {
                lo = val;
                exclusive = true;
            } else if (f == OWLFacet.MAX_INCLUSIVE) {
                hi = val;
            } else if (f == OWLFacet.MAX_EXCLUSIVE) {
                hi = val;
            }
        }
        String kind = (exclusive && Double.isNaN(hi)) ? "GT" : "RANGE";
        return new Constraint(host, prop, kind, lo, hi, exclusive, describeRange(lo, hi, exclusive));
    }

    private static String describeRange(double lo, double hi, boolean exclusive) {
        boolean hasLo = !Double.isNaN(lo);
        boolean hasHi = !Double.isNaN(hi);
        String loOp = exclusive ? "> " : ">= ";
        if (hasLo && hasHi) {
            return loOp + trim(lo) + " 且 <= " + trim(hi);
        }
        if (hasLo) {
            return loOp + trim(lo);
        }
        if (hasHi) {
            return "<= " + trim(hi);
        }
        return "任意";
    }

    private static String describeCardinality(String host, OWLObjectCardinalityRestriction card) {
        int n = card.getCardinality();
        OWLObjectPropertyExpression pe = card.getProperty();
        String prop = local(pe.asOWLObjectProperty().getIRI());
        ClassExpressionType t = card.getClassExpressionType();
        String op;
        if (t == ClassExpressionType.OBJECT_MIN_CARDINALITY) {
            op = ">=";
        } else if (t == ClassExpressionType.OBJECT_MAX_CARDINALITY) {
            op = "<=";
        } else {
            op = "=";
        }
        return host + " ⊑ " + op + n + " " + prop;
    }

    private static Map<String, double[]> parseModelParams(OWLOntology ont) {
        OWLDataFactory df = ont.getOWLOntologyManager().getOWLDataFactory();
        IRI modelIri = IRI.create(TmsdVocabulary.NS + "Model");
        Map<String, double[]> out = new LinkedHashMap<>();
        for (OWLClassAssertionAxiom ca : ont.getAxioms(AxiomType.CLASS_ASSERTION)) {
            if (!(ca.getIndividual() instanceof OWLNamedIndividual ind)) {
                continue;
            }
            if (!ca.getClassExpression().isOWLClass()
                    || !ca.getClassExpression().asOWLClass().getIRI().equals(modelIri)) {
                continue;
            }
            String name = stringAssertion(ont, ind, "modelName");
            Double len = numberAssertion(ont, ind, "cableBracketLength");
            Double right = numberAssertion(ont, ind, "cableBracketRightChord");
            Double left = numberAssertion(ont, ind, "cableBracketLeftChord");
            if (name != null && len != null && right != null && left != null) {
                out.put(name.trim().toUpperCase(), new double[]{len, right, left});
            }
        }
        return out;
    }

    private static String stringAssertion(OWLOntology ont, OWLNamedIndividual ind, String propLocal) {
        OWLDataProperty prop = ont.getOWLOntologyManager().getOWLDataFactory()
                .getOWLDataProperty(IRI.create(TmsdVocabulary.NS + propLocal));
        return ont.getDataPropertyAssertionAxioms(ind).stream()
                .filter(a -> a.getProperty().equals(prop))
                .map(OWLDataPropertyAssertionAxiom::getObject)
                .map(OWLLiteral::getLiteral)
                .findFirst().orElse(null);
    }

    private static Double numberAssertion(OWLOntology ont, OWLNamedIndividual ind, String propLocal) {
        OWLDataProperty prop = ont.getOWLOntologyManager().getOWLDataFactory()
                .getOWLDataProperty(IRI.create(TmsdVocabulary.NS + propLocal));
        return ont.getDataPropertyAssertionAxioms(ind).stream()
                .filter(a -> a.getProperty().equals(prop))
                .map(OWLDataPropertyAssertionAxiom::getObject)
                .filter(TmsdOntologyModel::isNumeric)
                .map(TmsdOntologyModel::toDouble)
                .findFirst().orElse(null);
    }

    // ==================== 访问器 ====================

    public String versionInfo() {
        return versionInfo;
    }

    public List<Constraint> numeric() {
        return numeric;
    }

    public List<String> cardinality() {
        return cardinality;
    }

    public List<String> hasKeys() {
        return hasKeys;
    }

    /** 值集（同一属性多条 hasValue，升序）。 */
    public List<Double> valueSet(String prop) {
        return valueSets.getOrDefault(prop, List.of());
    }

    /** 字符串 hasValue。 */
    public String stringValue(String prop) {
        return stringValues.get(prop);
    }

    /** 机型 → [托架长度, 右弦长, 左弦长]；未定义返回 {@code null}。 */
    public double[] modelParams(String model) {
        if (model == null) {
            return null;
        }
        return modelParams.get(model.trim().toUpperCase());
    }

    /** 按属性 local name 取「单值数值约束」。 */
    public Constraint byProperty(String prop) {
        for (Constraint c : numeric) {
            if (c.property().equals(prop)) {
                return c;
            }
        }
        return null;
    }

    // ==================== 工具 ====================

    private static boolean isNumeric(OWLLiteral lit) {
        String dt = lit.getDatatype().getIRI().toString();
        return dt.endsWith("#integer") || dt.endsWith("#int") || dt.endsWith("#decimal")
                || dt.endsWith("#double") || dt.endsWith("#float");
    }

    /** 按字面量类型取值（integer 不能走 parseDouble）。 */
    private static double toDouble(OWLLiteral lit) {
        return lit.isInteger() ? lit.parseInteger() : lit.parseDouble();
    }

    private static String hostOf(String prop, OWLOntology ont) {
        for (OWLSubClassOfAxiom ax : ont.getAxioms(AxiomType.SUBCLASS_OF)) {
            if (ax.getSubClass() instanceof OWLClass sub
                    && ax.getSuperClass() instanceof OWLDataHasValue dv
                    && local(dv.getProperty().asOWLDataProperty().getIRI()).equals(prop)) {
                return local(sub.getIRI());
            }
        }
        return "";
    }

    private static String local(IRI iri) {
        return iri.getRemainder().orElse(iri.toString());
    }

    private static String trim(double v) {
        if (v == Math.rint(v)) {
            return String.valueOf((long) v);
        }
        return String.valueOf(v);
    }
}
