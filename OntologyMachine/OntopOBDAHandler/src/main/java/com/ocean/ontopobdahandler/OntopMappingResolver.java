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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OntopMappingResolver {

    // ==================== 公共数据结构 ====================

    public record ColumnMapping(String tableName, String columnName) {}
    private static final Logger log = LoggerFactory.getLogger(OntopMappingResolver.class);

    public record JoinKeyInfo(
            String subjectVarName,
            Set<String> tableColumns
    ) {}

    // ==================== 核心公共 API ====================

    public static Map<String, ColumnMapping> resolvePropertyToColumnMappings(
            String obdaFilePath, Properties props) throws Exception {

        Mapping mapping = loadMapping(obdaFilePath, props);
        Map<String, ColumnMapping> result = new LinkedHashMap<>();

        for (RDFAtomPredicate rdfAtomPredicate : mapping.getRDFAtomPredicates()) {
            log.debug("📦 RDFAtomPredicate: " + rdfAtomPredicate);

            // 打印该 predicate 下所有 class IRI
            for (IRI classIRI : mapping.getRDFClasses(rdfAtomPredicate)) {
                log.debug("   🏷️  Class: " + classIRI.getIRIString());
            }

            // 遍历每个 property
            for (IRI propertyIRI : mapping.getRDFProperties(rdfAtomPredicate)) {
                log.debug("\n🔎 Property: " + propertyIRI.getIRIString());

                Optional<IQ> iqOpt = mapping.getRDFPropertyDefinition(rdfAtomPredicate, propertyIRI);
                if (iqOpt.isEmpty()) {
                    log.debug("   ⚠️  No IQ definition found, skipping.");
                    continue;
                }

                IQ iq = iqOpt.get();
                log.debug("   🌳 IQ Tree:");
                printIQTree(iq.getTree(), "      ");

                Set<String> columns = extractColumnsForProperty(iq, propertyIRI);
                if (!columns.isEmpty()) {
                    String tableCol = columns.iterator().next();
                    String[] parts = tableCol.split("\\.", 2);
                    if (parts.length == 2) {
                        result.put(propertyIRI.getIRIString(),
                                new ColumnMapping(parts[0], parts[1]));
                        System.out.println("   ✅ Resolved → " + parts[0] + "." + parts[1]);
                    }
                } else {
                    System.out.println("   ❌ No column resolved for this property.");
                }
            }
        }
        return result;
    }

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
                .map(e -> new JoinKeyInfo(
                        e.getKey().replace("__implicit_join__", ""),  // 清理前缀
                        Collections.unmodifiableSet(e.getValue())))
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

    // ==================== 🌳 IQ Tree 调试打印 ====================

    private static void printIQTree(IQTree tree, String indent) {
        QueryNode node = tree.getRootNode();
        String nodeType = node.getClass().getSimpleName();

        if (node instanceof ConstructionNode cn) {
            log.debug(indent + "├─ ConstructionNode");
            Substitution<? extends ImmutableTerm> sub = cn.getSubstitution();
            for (var entry : sub.stream().toList()) {
                log.debug(indent + "│   " + entry.getKey() + " ← " + entry.getValue());
            }
        } else if (node instanceof ExtensionalDataNode edn) {
            String tableName = edn.getRelationDefinition().getAtomPredicate().getName();
            boolean isSub = isSqlSubquery(tableName);
            String displayTable = isSub ? "(SQL subquery)" : tableName;
            log.debug(indent + "├─ EDN [" + displayTable + "]");

            var argMap = edn.getArgumentMap();
            for (var entry : argMap.entrySet()) {
                log.debug(indent + "│   col[" + entry.getKey() + "] → " + entry.getValue());
            }

            // 如果是子查询，额外打印解析结果
            if (isSub) {
                SqlParseResult parsed = parseSqlSubquery(tableName);
                log.debug(indent + "│   📋 SQL Parse: aliases=" + parsed.aliasToColumnRef());
                log.debug(indent + "│   📋 SQL Parse: tables=" + parsed.tableAliasToPhysical());
            }
        } else {
            log.debug(indent + "├─ " + nodeType + " [" + node + "]");
        }

        for (IQTree child : tree.getChildren()) {
            printIQTree(child, indent + "│   ");
        }
    }

    // ==================== SQL 子查询解析工具 ====================

    private static boolean isSqlSubquery(String tableName) {
        if (tableName == null) return false;
        String trimmed = tableName.trim();
        if (trimmed.startsWith("`") && trimmed.endsWith("`")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        String upper = trimmed.toUpperCase();
        return upper.startsWith("SELECT") || upper.startsWith("(SELECT");
    }

    private record SqlParseResult(
            Map<String, String> aliasToColumnRef,
            Map<String, String> tableAliasToPhysical
    ) {
        String resolveToPhysicalColumn(String columnAlias) {
            String colRef = aliasToColumnRef.get(columnAlias);
            if (colRef == null) return null;

            String[] parts = colRef.split("\\.", 2);
            if (parts.length != 2) return null;

            String tableAlias = parts[0];
            String columnName = parts[1];

            String physicalTable = tableAliasToPhysical.get(tableAlias);
            if (physicalTable == null) return null;

            return physicalTable + "." + columnName;
        }
    }

    private static SqlParseResult parseSqlSubquery(String sql) {
        String cleaned = sql.trim();
        while (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }

        Map<String, String> aliasToColumnRef = new LinkedHashMap<>();
        Map<String, String> tableAliasToPhysical = new LinkedHashMap<>();

        Pattern fromPattern = Pattern.compile(
                "(?:FROM|JOIN)\\s+([\\w.\"]+)\\s+(?:AS\\s+)?(\\w+)",
                Pattern.CASE_INSENSITIVE);
        Matcher fromMatcher = fromPattern.matcher(cleaned);
        while (fromMatcher.find()) {
            String physicalTable = fromMatcher.group(1).replace("\"", "");
            String alias = fromMatcher.group(2);
            if (!isSqlKeyword(alias)) {
                tableAliasToPhysical.put(alias, physicalTable);
            }
        }

        int fromIdx = findMainFromIndex(cleaned);
        String upperSql = cleaned.toUpperCase();
        int selectIdx = upperSql.indexOf("SELECT");

        if (selectIdx < 0 || fromIdx < 0 || fromIdx <= selectIdx + 6) {
            return new SqlParseResult(aliasToColumnRef, tableAliasToPhysical);
        }

        String selectList = cleaned.substring(selectIdx + 6, fromIdx).trim();
        List<String> selectItems = splitTopLevelCommas(selectList);

        for (String item : selectItems) {
            String trimmedItem = item.trim();
            if (trimmedItem.isEmpty()) continue;

            String alias = null;
            String expr = trimmedItem;

            Pattern asPattern = Pattern.compile("^(.+)\\s+AS\\s+(\\w+)\\s*$", Pattern.CASE_INSENSITIVE);
            Matcher asMatcher = asPattern.matcher(trimmedItem);
            if (asMatcher.matches()) {
                expr = asMatcher.group(1).trim();
                alias = asMatcher.group(2).trim();
            } else if (trimmedItem.matches("\\w+\\.\\w+")) {
                String[] dotParts = trimmedItem.split("\\.", 2);
                alias = dotParts[1];
                expr = trimmedItem;
            } else {
                continue;
            }

            String columnRef = extractFirstColumnRef(expr);
            if (columnRef != null && alias != null) {
                aliasToColumnRef.put(alias, columnRef);
            }
        }

        return new SqlParseResult(aliasToColumnRef, tableAliasToPhysical);
    }

    private static int findMainFromIndex(String sql) {
        String upper = sql.toUpperCase();
        int depth = 0;
        for (int i = 0; i < sql.length() - 4; i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (depth == 0 && upper.substring(i).startsWith("FROM")
                    && (i == 0 || !Character.isLetterOrDigit(sql.charAt(i - 1)))
                    && (i + 4 >= sql.length() || !Character.isLetterOrDigit(sql.charAt(i + 4)))) {
                return i;
            }
        }
        return -1;
    }

    private static List<String> splitTopLevelCommas(String str) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                result.add(str.substring(start, i));
                start = i + 1;
            }
        }
        result.add(str.substring(start));
        return result;
    }

    private static String extractFirstColumnRef(String expr) {
        Pattern colPattern = Pattern.compile("(\\w+)\\.(\\w+)");
        Matcher matcher = colPattern.matcher(expr);
        if (matcher.find()) {
            String prefix = matcher.group(1);
            if (!isSqlKeyword(prefix) && !prefix.equals("xsd") && !prefix.equals("rdf")) {
                return matcher.group(0);
            }
        }
        return null;
    }

    private static boolean isSqlKeyword(String word) {
        Set<String> keywords = Set.of(
                "ON", "WHERE", "AND", "OR", "INNER", "LEFT", "RIGHT", "OUTER",
                "CROSS", "FULL", "NATURAL", "JOIN", "SELECT", "FROM", "AS",
                "GROUP", "ORDER", "BY", "HAVING", "LIMIT", "OFFSET", "UNION",
                "ALL", "DISTINCT", "NOT", "NULL", "IS", "IN", "EXISTS",
                "BETWEEN", "LIKE", "CASE", "WHEN", "THEN", "ELSE", "END",
                "COALESCE", "CAST", "CONVERT", "IF", "INTO", "VALUES", "SET"
        );
        return keywords.contains(word.toUpperCase());
    }

    // ==================== ⭐ 核心列收集（变量穿透 + SQL 解析） ====================

    private static void collectColumnsWithChaining(IQTree tree, Variable targetVar, Set<String> accumulator) {
        QueryNode node = tree.getRootNode();
        String nodeType = node.getClass().getSimpleName();

        // ---- 情况1: ConstructionNode — 变量穿透 ----
        if (node instanceof ConstructionNode cn) {
            Substitution<? extends ImmutableTerm> sub = cn.getSubstitution();
            ImmutableTerm mappedTerm = sub.get(targetVar);

            log.debug("      🔍 [CN] looking for targetVar=" + targetVar.getName());
            if (mappedTerm != null) {
                log.debug("         substitution hit: " + targetVar + " ← " + mappedTerm);
            } else {
                log.debug("         substitution miss, pass-through");
            }

            if (mappedTerm != null) {
                Variable deeperVar = extractUnderlyingVariable(mappedTerm);
                if (deeperVar != null && !deeperVar.equals(targetVar)) {
                    log.debug("         ↪ chaining to deeperVar=" + deeperVar.getName());
                    for (IQTree child : tree.getChildren()) {
                        collectColumnsWithChaining(child, deeperVar, accumulator);
                    }
                    return;
                }
            }

            for (IQTree child : tree.getChildren()) {
                collectColumnsWithChaining(child, targetVar, accumulator);
            }
            return;
        }

        // ---- 情况2: ExtensionalDataNode — 匹配列 ----
        if (node instanceof ExtensionalDataNode edn) {
            var relDef = edn.getRelationDefinition();
            var argMap = edn.getArgumentMap();
            String rawTableName = relDef.getAtomPredicate().getName();

            boolean isSubquery = isSqlSubquery(rawTableName);
            SqlParseResult sqlParseResult = isSubquery ? parseSqlSubquery(rawTableName) : null;

            log.debug("      🔍 [EDN] table=" + (isSubquery ? "(subquery)" : rawTableName)
                    + ", looking for targetVar=" + targetVar.getName());

            for (var entry : argMap.entrySet()) {
                int pos = entry.getKey();
                ImmutableTerm term = entry.getValue();

                if (term instanceof Variable v && targetVar.getName().equals(v.getName())) {
                    try {
                        var attr = relDef.getAttribute(pos + 1);
                        String columnName = attr.getID().getName();

                        if (isSubquery && sqlParseResult != null) {
                            String physicalColumn = sqlParseResult.resolveToPhysicalColumn(columnName);
                            if (physicalColumn != null) {
                                log.debug("         ✅ MATCH! col[" + pos + "]=" + columnName
                                        + " → resolved: " + physicalColumn);
                                accumulator.add(physicalColumn);
                            } else {
                                log.debug("         ⚠️  MATCH but unresolved: col[" + pos + "]="
                                        + columnName + ", aliases=" + sqlParseResult.aliasToColumnRef());
                                accumulator.add("[unresolved-subquery]." + columnName);
                            }
                        } else {
                            String cleanTableName = rawTableName;
                            if (cleanTableName.startsWith("`") && cleanTableName.endsWith("`")) {
                                cleanTableName = cleanTableName.substring(1, cleanTableName.length() - 1);
                            }
                            log.debug("         ✅ MATCH! col[" + pos + "] → "
                                    + cleanTableName + "." + columnName);
                            accumulator.add(cleanTableName + "." + columnName);
                        }
                    } catch (Exception e) {
                        log.debug("         ❌ Error resolving col[" + pos + "]: " + e.getMessage());
                    }
                }
            }
            return;
        }

        // ---- 情况3: 其他中间节点 ----
        log.debug("      🔍 [" + nodeType + "] pass-through targetVar=" + targetVar.getName());
        for (IQTree child : tree.getChildren()) {
            collectColumnsWithChaining(child, targetVar, accumulator);
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

                log.debug("   🎯 Found objectVar=" + objectVar.getName()
                        + ", term=" + objectTerm
                        + ", underlyingVar=" + (underlyingVar != null ? underlyingVar.getName() : "null"));

                if (underlyingVar != null) {
                    for (IQTree child : tree.getChildren()) {
                        collectColumnsWithChaining(child, underlyingVar, accumulator);
                    }
                    return;
                }
            }
        }

        for (IQTree child : tree.getChildren()) {
            collectObjectColumnsFromTree(child, targetPropertyIRI, accumulator);
        }
    }

    private static Variable findObjectVariable(
            Substitution<? extends ImmutableTerm> substitution, IRI targetPropertyIRI) {

        String targetIriStr = targetPropertyIRI.getIRIString();

        Optional<Variable> fromTriple = substitution.stream()
                .map(Map.Entry::getValue)
                .filter(t -> t instanceof ImmutableFunctionalTerm ft
                        && "triple".equals(ft.getFunctionSymbol().getName())
                        && ft.getArity() == 3)
                .map(t -> (ImmutableFunctionalTerm) t)
                .filter(ft -> matchesPropertyIRI(ft.getTerms().get(1), targetIriStr))
                .map(ft -> ft.getTerms().get(2))
                .filter(Variable.class::isInstance)
                .map(Variable.class::cast)
                .findFirst();

        if (fromTriple.isPresent()) return fromTriple.get();

        Variable predicateVar = substitution.stream()
                .filter(e -> matchesPropertyIRI(e.getValue(), targetIriStr))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (predicateVar == null) return null;

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

    private static boolean matchesPropertyIRI(ImmutableTerm term, String targetIriStr) {
        if (term instanceof RDFConstant c) {
            return c.getValue().equals(targetIriStr);
        }
        if (term instanceof IRIConstant c) {
            return c.getIRI().getIRIString().equals(targetIriStr);
        }
        if (term instanceof ImmutableFunctionalTerm ft) {
            for (ImmutableTerm arg : ft.getTerms()) {
                if (arg instanceof Constant c && c.getValue().equals(targetIriStr)) return true;
                if (arg instanceof RDFConstant rc && rc.getValue().equals(targetIriStr)) return true;
                if (arg instanceof IRIConstant ic && ic.getIRI().getIRIString().equals(targetIriStr)) return true;
            }
            return term.toString().contains(targetIriStr);
        }
        return term.toString().contains(targetIriStr);
    }

    // ==================== Subject 列提取（JOIN 键）====================

    private static void extractSubjectBindings(IQ iq, Map<String, Set<String>> acc) {
        collectSubjectBindingsFromTree(iq.getTree(), acc);
    }

    /**
     * ⭐ 修改点：将原来的 collectAllNonSubjectColumns 替换为只收集 object 变量对应的列。
     * 只有 object 变量（om*）才代表跨表对象引用，纯数据属性列不应作为 JOIN 键。
     */
    private static void collectSubjectBindingsFromTree(IQTree tree, Map<String, Set<String>> acc) {
        QueryNode node = tree.getRootNode();
        if (node instanceof ConstructionNode cn) {
            Variable subjVar = findSubjectVariable(cn.getSubstitution());
            if (subjVar != null) {
                Variable underlying = extractUnderlyingVariable(cn.getSubstitution().get(subjVar));
                if (underlying != null) {
                    // 1. 收集 subject 变量对应的所有底层列
                    Set<String> cols = new LinkedHashSet<>();
                    for (IQTree child : tree.getChildren()) {
                        collectColumnsWithChaining(child, underlying, cols);
                    }
                    if (!cols.isEmpty()) {
                        acc.computeIfAbsent(underlying.getName(), k -> new LinkedHashSet<>()).addAll(cols);
                    }

                    // 2. ⭐ 修复：只收集 object 变量（om*）对应的列作为隐式 JOIN 键
                    //    并过滤掉与 subject 同表的列，防止下游 JOIN 键分发时属性值污染同表标识列
                    Variable objVar = findObjectVariableInSubstitution(cn.getSubstitution());
                    if (objVar != null) {
                        Variable objUnderlying = extractUnderlyingVariable(cn.getSubstitution().get(objVar));
                        if (objUnderlying != null && !objUnderlying.equals(underlying)) {
                            Set<String> objectCols = new LinkedHashSet<>();
                            for (IQTree child : tree.getChildren()) {
                                collectColumnsWithChaining(child, objUnderlying, objectCols);
                            }
                            if (!objectCols.isEmpty()) {
                                // 关键过滤：仅保留与 subject 不在同一张表的列
                                Set<String> crossTableCols = objectCols.stream()
                                        .filter(col -> {
                                            String objTable = col.split("\\.", 2)[0];
                                            return cols.stream().noneMatch(sc -> sc.startsWith(objTable + "."));
                                        })
                                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

                                if (!crossTableCols.isEmpty()) {
                                    String joinKey = "__implicit_join__" + underlying.getName();
                                    acc.computeIfAbsent(joinKey, k -> new LinkedHashSet<>()).addAll(crossTableCols);
                                    // 同时把 subject 列也加入，确保成对出现供下游匹配
                                    acc.get(joinKey).addAll(cols);
                                }
                            }
                        }
                    }

                    return;
                }
            }
        }
        for (IQTree child : tree.getChildren()) {
            collectSubjectBindingsFromTree(child, acc);
        }
    }

    /**
     * ⭐ 新增：从 substitution 中找到 object 变量（om* 开头的 RDF 变量）。
     * 不依赖具体 property IRI，因为此时只关心"是否存在跨表对象引用"。
     */
    private static Variable findObjectVariableInSubstitution(Substitution<? extends ImmutableTerm> sub) {
        return sub.stream()
                .filter(e -> e.getValue() instanceof ImmutableFunctionalTerm ft
                        && "RDF".equals(ft.getFunctionSymbol().getName()))
                .map(Map.Entry::getKey)
                .filter(v -> v.getName().startsWith("om"))
                .findFirst()
                .orElse(null);
    }

    private static Variable findSubjectVariable(Substitution<? extends ImmutableTerm> substitution) {
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

        return substitution.stream()
                .filter(e -> e.getValue() instanceof ImmutableFunctionalTerm ft
                        && "RDF".equals(ft.getFunctionSymbol().getName()))
                .map(Map.Entry::getKey)
                .filter(v -> v.getName().startsWith("sm"))
                .findFirst()
                .orElse(null);
    }
}