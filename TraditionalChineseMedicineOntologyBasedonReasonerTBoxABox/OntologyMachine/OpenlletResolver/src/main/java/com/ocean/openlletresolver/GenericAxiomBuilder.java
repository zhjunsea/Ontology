package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.WriteResult;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 通用 OWL 公理构建器
 * 完全基于三元组 (Subject, Predicate, Object) 驱动，不绑定任何具体业务实体
 * ✅ 重构：复用 BackendService 的 getDataPropertyRanges() 和 DataFactory，不再自行读取 TBox
 */
public class GenericAxiomBuilder {

    private static final Logger log = LoggerFactory.getLogger(GenericAxiomBuilder.class);

    private final OWLDataFactory dataFactory;
    private final String typeNS;
    private final String indNS;
    private final BackendService backendService;

    /**
     * ✅ 推荐构造方式：传入 BackendService，复用其 Manager、DataFactory 和 TBox 查询能力
     */
    public GenericAxiomBuilder(BackendService backendService, String typeNS, String indNS) {
        this.backendService = Objects.requireNonNull(backendService, "BackendService cannot be null");
        this.dataFactory = backendService.getOntologyService().getDataFactory();
        this.typeNS = typeNS;
        this.indNS = indNS;
    }

    /**
     * ⚠️ 已废弃：无 BackendService 时无法感知 TBox Range，仅保留向后兼容
     * @deprecated 请使用 {@link #GenericAxiomBuilder(BackendService, String, String)}
     */
    /*
    @Deprecated
    public GenericAxiomBuilder(String typeNS, String indNS) {
        this.backendService = null;
        this.dataFactory = org.semanticweb.owlapi.apibinding.OWLManager.getOWLDataFactory();
        this.typeNS = typeNS;
        this.indNS = indNS;
        log.warn("⚠️ GenericAxiomBuilder 未注入 BackendService，数据属性类型将仅靠格式推断，可能与 TBox 不一致");
    }*/

    public record Triple(String subject, String predicate, String object, boolean isObjectProperty) {}

    /**
     * 将三元组列表转换为 OWL 公理集合
     * 自动识别 rdf:type、对象属性、数据属性三种模式
     */
    public Set<OWLAxiom> buildAxioms(List<Triple> triples) {
        Set<OWLAxiom> axioms = new HashSet<>();
        IRI typeBase = IRI.create(typeNS);
        IRI indBase = IRI.create(indNS);

        for (Triple t : triples) {
            OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(indBase.resolve(t.subject()));

            if ("rdf:type".equals(t.predicate())

                    || "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".equals(t.predicate())
                    || "a".equals(t.predicate())) {
                // ⭐ 类型断言
                axioms.add(dataFactory.getOWLClassAssertionAxiom(
                        dataFactory.getOWLClass(typeBase.resolve(t.object())), ind));
            } else if (t.isObjectProperty()) {
                // ⭐ 对象属性断言
                OWLNamedIndividual objInd = dataFactory.getOWLNamedIndividual(indBase.resolve(t.object()));
                axioms.add(dataFactory.getOWLObjectPropertyAssertionAxiom(
                        dataFactory.getOWLObjectProperty(typeBase.resolve(t.predicate())), ind, objInd));
            } else {
                // ⭐ 数据属性断言：✅ 复用 BackendService.getDataPropertyRanges() 确定类型
                IRI propIRI = typeBase.resolve(t.predicate());
                OWLLiteral literal = inferLiteral(t.object(), propIRI);
                axioms.add(dataFactory.getOWLDataPropertyAssertionAxiom(
                        dataFactory.getOWLDataProperty(propIRI), ind, literal));
            }
        }
        return axioms;
    }

    public Set<OWLAxiom> buildAxiomsWithIRI(List<Triple> triples) {
        Set<OWLAxiom> axioms = new HashSet<>();

        for (Triple t : triples) {
            // ✅ subject/predicate/object 均为完整 IRI，直接创建，不再 resolve
            OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(IRI.create(t.subject()));

            if ("rdf:type".equals(t.predicate())

                    || "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".equals(t.predicate())
                    || "a".equals(t.predicate())) {
                // ⭐ 类型断言
                axioms.add(dataFactory.getOWLClassAssertionAxiom(
                        dataFactory.getOWLClass(IRI.create(t.object())), ind));
            } else if (t.isObjectProperty()) {
                // ⭐ 对象属性断言
                OWLNamedIndividual objInd = dataFactory.getOWLNamedIndividual(IRI.create(t.object()));
                axioms.add(dataFactory.getOWLObjectPropertyAssertionAxiom(
                        dataFactory.getOWLObjectProperty(IRI.create(t.predicate())), ind, objInd));
            } else {
                // ⭐ 数据属性断言
                IRI propIRI = IRI.create(t.predicate());
                OWLLiteral literal = inferLiteral(t.object(), propIRI);
                axioms.add(dataFactory.getOWLDataPropertyAssertionAxiom(
                        dataFactory.getOWLDataProperty(propIRI), ind, literal));
            }
        }
        return axioms;
    }

    /**
     * ✅ 核心：根据 TBox Range 确定字面量类型
     * 优先级: TBox Range (via BackendService) > 显式类型标注 > 格式推断 > xsd:string
     */
    private OWLLiteral inferLiteral(String value, IRI propIRI) {
        if (value == null || value.isBlank()) {
            return dataFactory.getOWLLiteral("");
        }

        // 🛡️ 清洗 Ontop 双重包装: "50"^^xsd:integer → 50
        //将 <xsd:decimal> → xsd:decimal 的处理移到了解析阶段
        String cleaned = value;
        String explicitDatatypeIRI = null;
        if (cleaned.startsWith("\"") && cleaned.contains("\"^^")) {
            int endQuote = cleaned.indexOf('"', 1);
            if (endQuote > 0) {
                cleaned = cleaned.substring(1, endQuote);
                int hatPos = value.indexOf("\"^^");
                if (hatPos > 0) {
                    explicitDatatypeIRI = value.substring(hatPos + 3);
                    // ⭐ 新增：去掉可能存在的尖括号 <xsd:decimal> → xsd:decimal
                    if (explicitDatatypeIRI.startsWith("<") && explicitDatatypeIRI.endsWith(">")) {
                        explicitDatatypeIRI = explicitDatatypeIRI.substring(1, explicitDatatypeIRI.length() - 1);
                    }
                }
            }
        }

        // ⭐ 优先级1: 显式类型标注（值本身携带 ^^datatype）
        if (explicitDatatypeIRI != null) {
            try {
                // ⭐ 从 tbox 的前缀映射中展开缩写前缀
                IRI datatypeIRI;
                PrefixManager pm = backendService.getOntologyService()
                        .gettBoxOntology()
                        .getFormat()
                        .asPrefixOWLDocumentFormat();

                if (pm != null) {
                    // getIRI() 直接返回 IRI 对象，无需再 IRI.create()
                    datatypeIRI = pm.getIRI(explicitDatatypeIRI);
                } else {
                    datatypeIRI = IRI.create(explicitDatatypeIRI);
                }

                // ⭐ 兜底：tbox 中可能未显式声明 xsd 前缀（OWL内置但未必写入文档格式）
                // getIRI() 对未注册前缀会原样返回以缩写为内容的 IRI，
                // 此时 toString() 仍等于原始缩写字符串，据此判断是否需要兜底
                if (datatypeIRI.toString().equals(explicitDatatypeIRI)
                        && explicitDatatypeIRI.startsWith("xsd:")) {
                    datatypeIRI = IRI.create(
                            "http://www.w3.org/2001/XMLSchema#" + explicitDatatypeIRI.substring(4));
                }

                OWLDatatype explicitDt = dataFactory.getOWLDatatype(datatypeIRI);

                // ✅ 若值不在词法空间中，OWLAPI 会抛出 IllegalArgumentException
                return dataFactory.getOWLLiteral(cleaned, explicitDt);

            } catch (IllegalArgumentException e) {
                log.debug("ℹ️ 显式类型 {} 的词法空间不包含 \"{}\"，忽略显式标注",
                        explicitDatatypeIRI, cleaned);
            } catch (Exception ignored) {
                // 其他异常（如 IRI 无效、前缀未注册等），继续向下
            }
        }

        // ⭐ 优先级2: ✅ 复用 BackendService.getDataPropertyRanges() 查询 TBox
        if (backendService != null) {
            try {
                Set<OWLDatatype> ranges = backendService.getDataPropertyRanges(propIRI.toString());
                if (ranges != null && !ranges.isEmpty()) {
                    // ✅ 修复：遍历所有 range，用 getOWLLiteral 验证词法合法性
                    for (OWLDatatype targetDt : ranges) {
                        try {
                            OWLLiteral typedLiteral = dataFactory.getOWLLiteral(cleaned, targetDt);
                            log.debug("✅ TBox Range 匹配: {} → \"{}\"^^{}",
                                    propIRI.getShortForm(), cleaned, targetDt.getIRI().getShortForm());
                            return typedLiteral;
                        } catch (IllegalArgumentException e) {
                            // 当前 range 词法不匹配，尝试下一个
                            log.debug("ℹ️ Range {} 词法不匹配 \"{}\"，尝试下一个",
                                    targetDt.getIRI().getShortForm(), cleaned);
                        } catch (Exception e) {
                            log.debug("ℹ️ Range {} 验证异常: {}",
                                    targetDt.getIRI().getShortForm(), e.getMessage());
                        }
                    }
                    // 所有 range 都不匹配词法空间，强制使用第一个 range 构建
                    // 让后续一致性检查给出精确错误信息，而非静默降级
                    OWLDatatype fallbackDt = ranges.iterator().next();
                    log.warn("⚠️ 属性 {} 的值 \"{}\" 不在任何 TBox Range 词法空间中，强制使用 {} 构建",
                            propIRI.getShortForm(), cleaned, fallbackDt.getIRI().getShortForm());
                    return dataFactory.getOWLLiteral(cleaned, fallbackDt);
                }
            } catch (IllegalArgumentException e) {
                log.debug("ℹ️ 属性 {} 不在本体签名中，回退到格式推断", propIRI.getShortForm());
            } catch (Exception e) {
                log.warn("⚠️ 查询 TBox Range 失败 ({}): {}, 回退到格式推断",
                        propIRI.getShortForm(), e.getMessage());
            }
        }

        // ⭐ 优先级3: 无 TBox Range 时，格式推断兜底
        // ✅ 整数优先；小数统一用 xsd:decimal，避免与 TBox decimal range 冲突
        try {
            return dataFactory.getOWLLiteral(Integer.parseInt(cleaned));
        } catch (NumberFormatException ignored) {}
        try {
            new java.math.BigDecimal(cleaned);
            OWLDatatype decimalDt = dataFactory.getOWLDatatype(
                    IRI.create("http://www.w3.org/2001/XMLSchema#decimal"));
            return dataFactory.getOWLLiteral(cleaned, decimalDt);
        } catch (NumberFormatException ignored) {}
        if ("true".equalsIgnoreCase(cleaned) || "false".equalsIgnoreCase(cleaned)) {
            return dataFactory.getOWLLiteral(Boolean.parseBoolean(cleaned));
        }

        // ⭐ 优先级4: 全部失败 → xsd:string
        log.debug("ℹ️ 属性 {} 无 TBox Range 且格式推断失败，使用 xsd:string: \"{}\"",
                propIRI.getShortForm(), value);
        return dataFactory.getOWLLiteral(value);
    }

    // ==================== 写入路径 ====================

    public WriteResult safeWrite(List<org.apache.jena.graph.Triple> triples) {
        if (backendService == null) {
            throw new IllegalStateException("safeWrite 需要 BackendService，请使用带参构造函数");
        }
        Set<OWLAxiom> tempAxioms = convertToOwlAxioms(triples);

        boolean consistent = backendService.validateAxioms(tempAxioms);
        if (!consistent) {
            return WriteResult.rejected("ABox与TBox/SWRL规则存在矛盾");
        }

        backendService.getObdaHandler().persistToDatabase(triples);
        // ✅ 修复：accepted() 必须传入 String 参数
        return WriteResult.accepted("写入成功，共处理 " + triples.size() + " 条三元组");
    }

    private Set<OWLAxiom> convertToOwlAxioms(List<org.apache.jena.graph.Triple> triples) {
        return triples.stream().map(t -> {
            IRI subj = IRI.create(t.getSubject().getURI());
            IRI pred = IRI.create(t.getPredicate().getURI());

            if (t.getObject().isURI()) {
                return dataFactory.getOWLObjectPropertyAssertionAxiom(
                        dataFactory.getOWLObjectProperty(pred),
                        dataFactory.getOWLNamedIndividual(subj),
                        dataFactory.getOWLNamedIndividual(IRI.create(t.getObject().getURI()))
                );
            } else {
                // ✅ Jena 路径同样走 inferLiteral，统一类型决策逻辑
                String lexValue = t.getObject().getLiteralLexicalForm();
                OWLLiteral lit = inferLiteral(lexValue, pred);
                return dataFactory.getOWLDataPropertyAssertionAxiom(
                        dataFactory.getOWLDataProperty(pred),
                        dataFactory.getOWLNamedIndividual(subj),
                        lit
                );
            }
        }).collect(Collectors.toSet());
    }

    /**
     * 将属性 Map 直接转换为 OWL 公理集合
     * ✅ subject 与 key 优先按完整 IRI 处理，非法时安全降级为本地名拼接
     *
     * @param subject       主语（完整 IRI 或本地名）
     * @param allProperties 属性键值对，key 为完整 IRI 或本地名，value 为字符串值
     * @return OWL 公理集合
     */
    public Set<OWLAxiom> buildAxioms(String subject, Map<String, String> allProperties) {
        Set<OWLAxiom> axioms = new HashSet<>(allProperties.size());

        OWLDataFactory df = dataFactory;
        OWLOntology tbox = backendService.getOntologyService().gettBoxOntology();

        // ✅ 预计算 :type 的完整 IRI，避免循环内重复拼接
        final String typePropertyIRI = typeNS + "type";

        // ✅ 主语安全解析
        OWLNamedIndividual individual = df.getOWLNamedIndividual(resolveIRI(subject, indNS));

        for (Map.Entry<String, String> entry : allProperties.entrySet()) {
            String rawKey = entry.getKey();
            String value = entry.getValue();

            // ⭐ rdf:type / :type / 裸名 "type" 统一识别为类断言
            if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".equals(rawKey)

                    || typePropertyIRI.equals(rawKey)       // ← 新增：匹配 http://example.org/.../classes/type
                    || "type".equals(rawKey)) {
                IRI classIRI = resolveIRI(value, typeNS);
                axioms.add(df.getOWLClassAssertionAxiom(df.getOWLClass(classIRI), individual));
                continue;
            }

            // ✅ 属性键安全解析
            IRI propIRI = resolveIRI(rawKey, typeNS);

            // ⭐ TBox 签名判定属性类型
            boolean isObjectProperty;
            try {
                isObjectProperty = tbox.containsObjectPropertyInSignature(propIRI);
                if (!isObjectProperty && !tbox.containsDataPropertyInSignature(propIRI)) {
                    log.debug("ℹ️ 属性 {} 不在 TBox 签名中，默认视为数据属性", propIRI.getShortForm());
                }
            } catch (Exception e) {
                log.warn("⚠️ 查询 TBox 签名失败 ({}): {}, 默认视为数据属性",
                        propIRI.getShortForm(), e.getMessage());
                isObjectProperty = false;
            }

            if (isObjectProperty) {
                IRI objIRI = resolveIRI(value, indNS);
                axioms.add(df.getOWLObjectPropertyAssertionAxiom(
                        df.getOWLObjectProperty(propIRI), individual,
                        df.getOWLNamedIndividual(objIRI)));
            } else {
                OWLLiteral literal = inferLiteral(value, propIRI);
                axioms.add(df.getOWLDataPropertyAssertionAxiom(
                        df.getOWLDataProperty(propIRI), individual, literal));
            }
        }

        return Collections.unmodifiableSet(axioms);
    }

    /**
     * 安全 IRI 解析：合法绝对 IRI 直接创建，否则拼接默认命名空间
     * ✅ 使用 OWL API 内置解析器判断，避免手写正则误判
     */
    private static IRI resolveIRI(String value, String defaultNS) {
        if (value == null || value.isBlank()) {
            return IRI.create(defaultNS);
        }
        try {
            IRI iri = IRI.create(value);
            // OWL API 的 IRI.create() 对非绝对 URI 不会抛异常，
            // 但 getScheme() 为 null 说明它不是合法的绝对 IRI
            if (iri.getScheme() != null) {
                return iri;
            }
        } catch (IllegalArgumentException ignored) {
            // 包含非法字符等极端情况，降级到拼接
        }
        return IRI.create(defaultNS + value);
    }
}