package com.ocean.ontopobdahandler;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWL2Datatype;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ABoxTypeFixer {

    // 严格匹配 N-Triples 格式: <S> <P> "Literal"^^<DT> . 或 <S> <P> "Literal"@lang . 或 <S> <P> "Literal" .
    private static final Pattern NTRIPLES_PATTERN = Pattern.compile(
            "^<([^>]+)>\\s+<([^>]+)>\\s+\"(.*)\"(?:@([a-zA-Z-]+)|\\^\\^<([^>]+)>)?\\s*\\.\\s*$"
    );
    private static final Logger log = LoggerFactory.getLogger(ABoxTypeFixer.class);

    /**
     * @param tboxOntology 包含属性声明的本体（TBox）
     * @param aboxOntology 需要修复数据类型的本体（ABox）
     * @param rawNTriples  原始的 N-Triples 格式字符串
     */
    public static void fixDataPropertyTypes(
            OWLOntology tboxOntology,
            OWLOntology aboxOntology,
            String rawNTriples) {

        OWLOntologyManager manager = aboxOntology.getOWLOntologyManager();
        OWLDataFactory df = manager.getOWLDataFactory();

        // ✅ 1. 从 TBox 中提取所有已声明的 DataProperty IRI
        Set<IRI> knownDataProperties = new HashSet<>();
        for (OWLDeclarationAxiom decl : tboxOntology.getAxioms(AxiomType.DECLARATION)) {
            if (decl.getEntity().isOWLDataProperty()) {
                knownDataProperties.add(decl.getEntity().getIRI());
            }
        }

        // 防御性检查：如果 TBox 中没有找到任何 DataProperty，提前终止
        if (knownDataProperties.isEmpty()) {
            log.error("[WARN] TBox 中未找到任何 owl:DatatypeProperty 声明，跳过修复。");
            return;
        }

        Set<OWLAxiom> axiomsToAdd = new HashSet<>();
        Set<OWLAxiom> axiomsToRemove = new HashSet<>();

        // ✅ 2. 以原始 N-Triples 为唯一事实来源逐行扫描
        for (String line : rawNTriples.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher matcher = NTRIPLES_PATTERN.matcher(line);
            if (!matcher.matches()) continue;

            IRI predicateIRI = IRI.create(matcher.group(2));

            // ✅ 3. 仅当该谓词在 TBox 中被声明为 DataProperty 时才处理
            if (!knownDataProperties.contains(predicateIRI)) {
                continue;
            }

            // --- 以下重建 Literal + 清理旧公理 + 添加新公理的逻辑保持不变 ---
            String lexicalValue = matcher.group(3);
            String langTag = matcher.group(4);
            String datatypeIriStr = matcher.group(5);

            OWLLiteral correctLiteral;
            if (langTag != null) {
                correctLiteral = df.getOWLLiteral(lexicalValue, langTag);
            } else if (datatypeIriStr != null) {
                OWLDatatype datatype = df.getOWLDatatype(IRI.create(datatypeIriStr));
                correctLiteral = df.getOWLLiteral(lexicalValue, datatype);
            } else {
                correctLiteral = df.getOWLLiteral(lexicalValue, OWL2Datatype.XSD_STRING);
            }

            OWLNamedIndividual individual = df.getOWLNamedIndividual(IRI.create(matcher.group(1)));
            OWLDataProperty dataProperty = df.getOWLDataProperty(predicateIRI);
            OWLDataPropertyAssertionAxiom correctAxiom =
                    df.getOWLDataPropertyAssertionAxiom(dataProperty, individual, correctLiteral);

            // 清理同 S+P 的错误公理（AnnotationAssertion / 类型错误的 DataAssertion）
            for (OWLAxiom existingAxiom : aboxOntology.getAxioms()) {
                boolean isSameSP = false;
                OWLLiteral existingObj = null;

                if (existingAxiom instanceof OWLDataPropertyAssertionAxiom dpa) {
                    if (dpa.getSubject().equals(individual) && dpa.getProperty().equals(dataProperty)) {
                        isSameSP = true;
                        existingObj = dpa.getObject();
                    }
                } else if (existingAxiom instanceof OWLAnnotationAssertionAxiom aaa) {
                    if (aaa.getSubject().equals(individual.getIRI())
                            && aaa.getProperty().getIRI().equals(predicateIRI)) {
                        isSameSP = true;
                        if (aaa.getValue() instanceof OWLLiteral lit) {
                            existingObj = lit;
                        }
                    }
                }

                if (isSameSP && (existingObj == null || !existingObj.equals(correctLiteral))) {
                    axiomsToRemove.add(existingAxiom);
                }
            }
            axiomsToAdd.add(correctAxiom);
        }

        // ✅ 4. 批量提交变更到 ABox
        if (!axiomsToRemove.isEmpty()) {
            manager.removeAxioms(aboxOntology, axiomsToRemove);
        }
        if (!axiomsToAdd.isEmpty()) {
            manager.addAxioms(aboxOntology, axiomsToAdd);
        }
    }
}