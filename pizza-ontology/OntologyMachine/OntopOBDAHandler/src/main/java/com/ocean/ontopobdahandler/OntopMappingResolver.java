package com.ocean.ontopobdahandler;

import it.unibz.inf.ontop.injection.OntopMappingSQLAllConfiguration;
import it.unibz.inf.ontop.iq.IQ;
import it.unibz.inf.ontop.iq.IQTree;
import it.unibz.inf.ontop.iq.node.ConstructionNode;
import it.unibz.inf.ontop.iq.node.ExtensionalDataNode;
import it.unibz.inf.ontop.iq.node.QueryNode;
import it.unibz.inf.ontop.model.atom.RDFAtomPredicate;
import it.unibz.inf.ontop.model.term.*;
import it.unibz.inf.ontop.spec.OBDASpecification;
import it.unibz.inf.ontop.spec.mapping.Mapping;
import it.unibz.inf.ontop.substitution.Substitution;
import org.apache.commons.rdf.api.IRI;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Ontop OBDA 映射静态解析器。
 * 无需数据库连接即可从 .obda 文件中提取：
 * 1. 属性 IRI → 物理表列的结构化映射
 * 2. 跨表 JOIN 键（基于 Subject Template Variable）
 */
public class OntopMappingResolver {

    // ==================== 公共数据结构 ====================

    /** 属性到物理列的结构化映射 */
    public record ColumnMapping(String tableName, String columnName) {}

    /** JOIN 键元数据：记录某个 Subject Variable 在所有映射中绑定的 (table, column) 对 */
    public record JoinKeyInfo(
            String subjectVarName,
            Set<String> tableColumns  // 格式: "table.column"，跨越多张表
    ) {}

    // ==================== 核心公共 API ====================

    /**
     * 解析属性 IRI 到精确的 (表名, 列名) 结构化映射。
     * 一个属性通常只对应一个物理列；若 IQ 树中存在多列，取首个有效列。
     */
    public static Map<String, ColumnMapping> resolvePropertyToColumnMappings(
            String obdaFilePath, Properties props) throws Exception {

        Mapping mapping = loadMapping(obdaFilePath, props);
        Map<String, ColumnMapping> result = new LinkedHashMap<>();

        for (RDFAtomPredicate rdfAtomPredicate : mapping.getRDFAtomPredicates()) {
            for (IRI propertyIRI : mapping.getRDFProperties(rdfAtomPredicate)) {
                Optional<IQ> iqOpt = mapping.getRDFPropertyDefinition(rdfAtomPredicate, propertyIRI);
                if (iqOpt.isEmpty()) continue;

                Set<String> columns = extractColumnsForProperty(iqOpt.get(), propertyIRI);
                if (!columns.isEmpty()) {
                    String tableCol = columns.iterator().next();
                    String[] parts = tableCol.split("\\.", 2);
                    if (parts.length == 2) {
                        result.put(propertyIRI.getIRIString(),
                                new ColumnMapping(parts[0], parts[1]));
                    }
                }
            }
        }
        return result;
    }

    /**
     * 解析所有跨表 JOIN 键。
     * 从每个属性的 IQ 中提取 Subject Variable 及其绑定的 EDN 列，
     * 按 Subject Variable 聚合后，仅保留关联 ≥2 张表的条目。
     */
    public static List<JoinKeyInfo> resolveJoinKeys(
            String obdaFilePath, Properties props) throws Exception {

        Mapping mapping = loadMapping(obdaFilePath, props);
        Map<String, Set<String>> subjectVarToTableColumns = new LinkedHashMap<>();

        for (RDFAtomPredicate rdfAtomPredicate : mapping.getRDFAtomPredicates()) {
            for (IRI propertyIRI : mapping.getRDFProperties(rdfAtomPredicate)) {
                Optional<IQ> iqOpt = mapping.getRDFPropertyDefinition(rdfAtomPredicate, propertyIRI);
                if (iqOpt.isEmpty()) continue;

                extractSubjectBindings(iqOpt.get(), subjectVarToTableColumns);
            }
        }

        return subjectVarToTableColumns.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .map(e -> new JoinKeyInfo(e.getKey(), Collections.unmodifiableSet(e.getValue())))
                .toList();
    }

    // ==================== 共享基础设施 ====================

    private static Mapping loadMapping(String obdaFilePath, Properties props) throws Exception {
        OntopMappingSQLAllConfiguration configuration = OntopMappingSQLAllConfiguration.defaultBuilder()
                .nativeOntopMappingFile(new File(obdaFilePath))
                .properties(props)
                .build();
        OBDASpecification spec = configuration.loadSpecification();
        return spec.getSaturatedMapping();
    }

    /**
     * 递归穿透 ImmutableFunctionalTerm 嵌套结构，提取最底层 Variable。
     * 例如: RDF(VARCHARToTEXT(name1m2), xsd:string) → name1m2
     */
    private static Variable extractUnderlyingVariable(ImmutableTerm term) {
        if (term instanceof Variable v) return v;
        if (term instanceof ImmutableFunctionalTerm funcTerm) {
            for (ImmutableTerm arg : funcTerm.getTerms()) {
                Variable result = extractUnderlyingVariable(arg);
                if (result != null) return result;
            }
        }
        return null;
    }

    /**
     * 在 IQ 子树中查找包含指定变量的 EDN，收集该变量对应的物理列（格式: table.column）。
     * 被属性列提取和 JOIN 键提取共同复用。
     */
    private static void collectEdnColumnsForVariable(IQTree tree, Variable variable, Set<String> accumulator) {
        if (tree instanceof ExtensionalDataNode edn) {
            edn.getArgumentMap().forEach((position, term) -> {
                if (term.equals(variable)) {
                    var relation = edn.getRelationDefinition();
                    String tableName = relation.getAtomPredicate().getName();
                    var attr = relation.getAttribute(position + 1);
                    accumulator.add(tableName + "." + attr.getID().getName());
                }
            });
        } else {
            tree.getChildren().forEach(child ->
                    collectEdnColumnsForVariable(child, variable, accumulator));
        }
    }

    // ==================== Object 列提取（属性映射） ====================

    private static Set<String> extractColumnsForProperty(IQ iq, IRI targetPropertyIRI) {
        Set<String> result = new LinkedHashSet<>();
        collectObjectColumnsFromTree(iq.getTree(), targetPropertyIRI, result);
        return result;
    }

    private static void collectObjectColumnsFromTree(IQTree tree, IRI targetPropertyIRI, Set<String> accumulator) {
        QueryNode node = tree.getRootNode();

        if (node instanceof ConstructionNode cn) {
            Substitution<? extends ImmutableTerm> substitution = cn.getSubstitution();
            Variable objectVar = findObjectVariable(substitution, targetPropertyIRI);

            if (objectVar != null) {
                ImmutableTerm objectTerm = substitution.get(objectVar);
                Variable underlyingVar = extractUnderlyingVariable(objectTerm);
                if (underlyingVar != null) {
                    for (IQTree child : tree.getChildren()) {
                        collectEdnColumnsForVariable(child, underlyingVar, accumulator);
                    }
                }
            }
        }

        // 无论当前节点是否命中，都继续递归（处理 UNION 多分支）
        for (IQTree child : tree.getChildren()) {
            collectObjectColumnsFromTree(child, targetPropertyIRI, accumulator);
        }
    }

    /**
     * 兼容两种 IQ 树格式提取 Object 变量：
     * 1. triple(sm, pm, om) 打包形式
     * 2. sm/pm/om 独立绑定的解构形式
     */
    private static Variable findObjectVariable(
            Substitution<? extends ImmutableTerm> substitution, IRI targetPropertyIRI) {

        String targetIriStr = targetPropertyIRI.getIRIString();

        // 策略1: triple() 打包形式
        Optional<Variable> fromTriple = substitution.stream()
                .map(Map.Entry::getValue)
                .filter(t -> t instanceof ImmutableFunctionalTerm ft
                        && "triple".equals(ft.getFunctionSymbol().getName())
                        && ft.getArity() == 3)
                .map(t -> (ImmutableFunctionalTerm) t)
                .filter(ft -> ft.getTerms().get(1) instanceof RDFConstant c
                        && c.getValue().equals(targetIriStr))
                .map(ft -> ft.getTerms().get(2))
                .filter(Variable.class::isInstance)
                .map(Variable.class::cast)
                .findFirst();

        if (fromTriple.isPresent()) return fromTriple.get();

        // 策略2: 解构形式（S/P/O 独立绑定）
        boolean hasTargetPredicate = substitution.rangeAnyMatch(t ->
                t instanceof RDFConstant c && c.getValue().equals(targetIriStr));
        if (!hasTargetPredicate) return null;

        Variable predicateVar = substitution.stream()
                .filter(e -> e.getValue() instanceof RDFConstant c
                        && c.getValue().equals(targetIriStr))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        List<Variable> rdfVars = substitution.stream()
                .filter(e -> !e.getKey().equals(predicateVar))
                .filter(e -> e.getValue() instanceof ImmutableFunctionalTerm ft
                        && "RDF".equals(ft.getFunctionSymbol().getName()))
                .map(Map.Entry::getKey)
                .toList();

        return rdfVars.stream()
                .filter(v -> v.getName().startsWith("om"))
                .findFirst()
                .orElse(rdfVars.size() == 1 ? rdfVars.get(0) : null);
    }

    // ==================== Subject 列提取（JOIN 键） ====================

    private static void extractSubjectBindings(IQ iq, Map<String, Set<String>> accumulator) {
        collectSubjectBindingsFromTree(iq.getTree(), accumulator);
    }

    private static void collectSubjectBindingsFromTree(IQTree tree, Map<String, Set<String>> accumulator) {
        QueryNode node = tree.getRootNode();

        if (node instanceof ConstructionNode cn) {
            Substitution<? extends ImmutableTerm> substitution = cn.getSubstitution();
            Variable subjectVar = findSubjectVariable(substitution);

            if (subjectVar != null) {
                ImmutableTerm subjectTerm = substitution.get(subjectVar);
                Variable underlyingVar = extractUnderlyingVariable(subjectTerm);

                if (underlyingVar != null) {
                    Set<String> columns = new LinkedHashSet<>();
                    for (IQTree child : tree.getChildren()) {
                        collectEdnColumnsForVariable(child, underlyingVar, columns);
                    }
                    if (!columns.isEmpty()) {
                        accumulator.computeIfAbsent(underlyingVar.getName(), k -> new LinkedHashSet<>())
                                .addAll(columns);
                    }
                }
            }
        }

        for (IQTree child : tree.getChildren()) {
            collectSubjectBindingsFromTree(child, accumulator);
        }
    }

    /**
     * 从 ConstructionNode Substitution 中提取 Subject Variable。
     * 兼容 triple() 打包形式与 "sm" 前缀解构形式。
     */
    private static Variable findSubjectVariable(Substitution<? extends ImmutableTerm> substitution) {
        // 策略1: triple() 打包形式 → 第一个参数是 Subject
        Optional<Variable> fromTriple = substitution.stream()
                .map(Map.Entry::getValue)
                .filter(t -> t instanceof ImmutableFunctionalTerm ft
                        && "triple".equals(ft.getFunctionSymbol().getName())
                        && ft.getArity() == 3)
                .map(t -> ((ImmutableFunctionalTerm) t).getTerms().get(0))
                .filter(Variable.class::isInstance)
                .map(Variable.class::cast)
                .findFirst();

        if (fromTriple.isPresent()) return fromTriple.get();

        // 策略2: 解构形式 → 按 "sm" 命名约定匹配
        return substitution.stream()
                .filter(e -> e.getValue() instanceof ImmutableFunctionalTerm ft
                        && "RDF".equals(ft.getFunctionSymbol().getName()))
                .map(Map.Entry::getKey)
                .filter(v -> v.getName().startsWith("sm"))
                .findFirst()
                .orElse(null);
    }
}