package com.ocean.openlletresolver;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * 通用 OWL 公理构建器
 * 完全基于三元组 (Subject, Predicate, Object) 驱动，不绑定任何具体业务实体
 */
public class GenericAxiomBuilder {

    private final OWLDataFactory df;
    private final String namespace;

    public GenericAxiomBuilder(String namespace) {
        this.df = OWLManager.getOWLDataFactory();
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
            OWLNamedIndividual ind = df.getOWLNamedIndividual(base.resolve(t.subject()));

            if ("rdf:type".equals(t.predicate()) || "a".equals(t.predicate())) {
                // ⭐ 类型断言
                axioms.add(df.getOWLClassAssertionAxiom(
                        df.getOWLClass(base.resolve(t.object())), ind));
            } else if (t.isObjectProperty()) {
                // ⭐ 对象属性断言
                OWLNamedIndividual objInd = df.getOWLNamedIndividual(base.resolve(t.object()));
                axioms.add(df.getOWLObjectPropertyAssertionAxiom(
                        df.getOWLObjectProperty(base.resolve(t.predicate())), ind, objInd));
            } else {
                // ⭐ 数据属性断言（自动推断字面量类型）
                OWLLiteral literal = inferLiteral(t.object());
                axioms.add(df.getOWLDataPropertyAssertionAxiom(
                        df.getOWLDataProperty(base.resolve(t.predicate())), ind, literal));
            }
        }
        return axioms;
    }

    /**
     * 智能字面量推断：优先尝试数值/布尔，失败则回退为字符串
     */
    private OWLLiteral inferLiteral(String value) {
        try { return df.getOWLLiteral(Double.parseDouble(value)); } catch (NumberFormatException ignored) {}
        try { return df.getOWLLiteral(Integer.parseInt(value)); } catch (NumberFormatException ignored) {}
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return df.getOWLLiteral(Boolean.parseBoolean(value));
        }
        return df.getOWLLiteral(value);
    }
}