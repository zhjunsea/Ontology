package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.WriteResult;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 通用 OWL 公理构建器
 * 完全基于三元组 (Subject, Predicate, Object) 驱动，不绑定任何具体业务实体
 */
public class GenericAxiomBuilder {

    private final OWLDataFactory dataFactory;
    private final String namespace;
    private BackendService backendService;

    public GenericAxiomBuilder(String namespace) {
        this.dataFactory = OWLManager.getOWLDataFactory();
        this.namespace = namespace;
    }

    public GenericAxiomBuilder(BackendService backendService, String namespace) {
        this.backendService = backendService;
        this.dataFactory = backendService.getOntologyService().getDataFactory();
        this.namespace = namespace;
    }

    /**
     * 标准化三元组记录
     * @param subject   主语（通常为个体 ID）
     * @param predicate 谓词（属性名或 rdf:type）
     * @param object    宾语（值、IRI 或类型片段）
     * @param isObjectProperty 是否为对象属性（false 则为数据属性或类型断言）
     */
    public record Triple(String subject, String predicate, String object, boolean isObjectProperty) {}

    /**
     * 将三元组列表转换为 OWL 公理集合
     * 自动识别 rdf:type、对象属性、数据属性三种模式
     */
    public Set<OWLAxiom> buildAxioms(List<Triple> triples) {
        Set<OWLAxiom> axioms = new HashSet<>();
        IRI base = IRI.create(namespace);

        for (Triple t : triples) {
            OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(base.resolve(t.subject()));

            if ("rdf:type".equals(t.predicate()) || "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".equals(t.predicate()) || "a".equals(t.predicate())) {
                // ⭐ 类型断言
                axioms.add(dataFactory.getOWLClassAssertionAxiom(
                        dataFactory.getOWLClass(base.resolve(t.object())), ind));
            } else if (t.isObjectProperty()) {
                // ⭐ 对象属性断言
                OWLNamedIndividual objInd = dataFactory.getOWLNamedIndividual(base.resolve(t.object()));
                axioms.add(dataFactory.getOWLObjectPropertyAssertionAxiom(
                        dataFactory.getOWLObjectProperty(base.resolve(t.predicate())), ind, objInd));
            } else {
                // ⭐ 数据属性断言（自动推断字面量类型）
                OWLLiteral literal = inferLiteral(t.object());
                axioms.add(dataFactory.getOWLDataPropertyAssertionAxiom(
                        dataFactory.getOWLDataProperty(base.resolve(t.predicate())), ind, literal));
            }
        }
        return axioms;
    }

    /**
     * 智能字面量推断：优先尝试数值/布尔，失败则回退为字符串
     */
    private OWLLiteral inferLiteral(String value) {
        try { return dataFactory.getOWLLiteral(Double.parseDouble(value)); } catch (NumberFormatException ignored) {}
        try { return dataFactory.getOWLLiteral(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return dataFactory.getOWLLiteral(Boolean.parseBoolean(value));
        }
        return dataFactory.getOWLLiteral(value);
    }
    // ==================== 写入路径 ====================

    /**
     * 安全写入：验证 → 写库
     */
    public WriteResult safeWrite(List<org.apache.jena.graph.Triple> triples) {
        Set<OWLAxiom> tempAxioms = convertToOwlAxioms(triples);

        boolean consistent = backendService.validateAxioms(tempAxioms);
        if (!consistent) {
            return WriteResult.rejected("ABox与TBox/SWRL规则存在矛盾");
        }

        // 验证通过，执行数据库写入（此处省略JDBC/Ontop更新逻辑）
        backendService.getObdaHandler().persistToDatabase(triples);
        return WriteResult.accepted();
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
                OWLLiteral lit = dataFactory.getOWLLiteral(
                        t.getObject().getLiteralLexicalForm(),
                        dataFactory.getOWLDatatype(IRI.create(t.getObject().getLiteralDatatypeURI()))
                );
                return dataFactory.getOWLDataPropertyAssertionAxiom(
                        dataFactory.getOWLDataProperty(pred),
                        dataFactory.getOWLNamedIndividual(subj),
                        lit
                );
            }
        }).collect(Collectors.toSet());
    }
}