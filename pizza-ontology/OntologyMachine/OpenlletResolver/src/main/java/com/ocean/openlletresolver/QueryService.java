package com.ocean.openlletresolver;

import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;
import org.apache.jena.rdf.model.*;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static com.ocean.ontopobdahandler.OBDAHandler.escapeSparqlUri;
import static com.ocean.ontopobdahandler.OBDAHandler.queryConstruct;

/**
 * 通用本体实例查询服务。
 * 提供基于 TBox+ABox 合并推理的实例检索、类型推断及数据属性提取能力，
 * 不绑定任何特定业务领域。
 */
public class QueryService {

    private final BackendService backendService;
    private static final Logger log = LoggerFactory.getLogger(QueryService.class);

    public QueryService(BackendService backendService) {
        this.backendService = backendService;
    }

    /**
     * 通用实例查询结果记录。
     */
    public record IndividualRecord(
            String individualIri,
            String localName,
            List<String> inferredTypes,
            Map<String, String> dataProperties
    ) {}

    /**
     * 查询参数配置（Builder 模式）。
     * ✅ 已改造：支持无参 builder() 供反射映射使用，同时保留原带参构造向后兼容。
     */
    public static class QueryConfig {
        private final String rootClassIri;
        private final List<String> dataPropertyIris;
        private final int maxResults;
        private final boolean includeDirectType;

        private QueryConfig(Builder builder) {
            this.rootClassIri = builder.rootClassIri;
            this.dataPropertyIris = Collections.unmodifiableList(builder.dataPropertyIris);
            this.maxResults = builder.maxResults;
            this.includeDirectType = builder.includeDirectType;
        }

        public String getRootClassIri()           { return rootClassIri; }
        public List<String> getDataPropertyIris() { return dataPropertyIris; }
        public int getMaxResults()                { return maxResults; }
        public boolean isIncludeDirectType()      { return includeDirectType; }

        // ✅ 新增：无参 builder()，供反射映射使用
        public static Builder builder() {
            return new Builder();
        }

        // 保留原有带参 builder()，兼容已有调用方
        public static Builder builder(String rootClassIri) {
            return new Builder(rootClassIri);
        }

        public static class Builder {
            // ✅ 去掉 final，允许反射后续赋值
            private String rootClassIri;
            private List<String> dataPropertyIris = List.of();
            private int maxResults = -1;
            private boolean includeDirectType = false;

            // ✅ 新增：无参私有构造
            private Builder() {}

            // 保留原有带参构造
            private Builder(String rootClassIri) {
                this.rootClassIri = Objects.requireNonNull(rootClassIri);
            }

            // ✅ 新增：rootClassIri setter，反射可优先走方法注入
            public Builder rootClassIri(String rootClassIri) {
                this.rootClassIri = rootClassIri;
                return this;
            }

            public Builder dataProperties(List<String> iris) {
                this.dataPropertyIris = iris != null ? new ArrayList<>(iris) : List.of();
                return this;
            }

            public Builder dataProperties(String... iris) {
                this.dataPropertyIris = Arrays.asList(iris);
                return this;
            }

            public Builder maxResults(int max) {
                this.maxResults = max;
                return this;
            }

            public Builder includeDirectType(boolean include) {
                this.includeDirectType = include;
                return this;
            }

            public QueryConfig build() {
                // ✅ 校验延迟到 build 阶段，兼容空 Builder + 反射注入模式
                Objects.requireNonNull(rootClassIri,
                        "rootClassIri is required. Ensure BPMN passes a variable named 'rootClassIri'.");
                return new QueryConfig(this);
            }
        }
    }

    /**
     * 核心查询方法：获取指定类的所有实例（含推理）、推断类型及数据属性值。
     */
    public List<IndividualRecord> queryInstances(
            OWLOntology tbox, OWLOntology abox, QueryConfig config)
            throws OWLOntologyCreationException {

        Objects.requireNonNull(config, "QueryConfig must not be null");
        log.info("[OntologyQuery] rootClass={} | properties={} | max={}",
                config.getRootClassIri(), config.getDataPropertyIris(), config.getMaxResults());

        OWLOntology merged = backendService.getOntologyService().mergeInMemory(tbox, abox);
        OWLDataFactory df = merged.getOWLOntologyManager().getOWLDataFactory();

        OWLClass rootClass = df.getOWLClass(IRI.create(config.getRootClassIri()));
        List<OWLDataProperty> dataProps = config.getDataPropertyIris().stream()
                .map(iri -> df.getOWLDataProperty(IRI.create(iri)))
                .toList();

        OpenlletReasoner reasoner = OpenlletReasonerFactory.getInstance().createReasoner(merged);
        try {
            Set<OWLNamedIndividual> individuals = backendService.filterRealIndividuals(
                    reasoner.getInstances(rootClass, false).getFlattened(), merged);

            List<IndividualRecord> results = new ArrayList<>();
            int count = 0;

            for (OWLNamedIndividual ind : individuals) {
                if (config.getMaxResults() > 0 && count >= config.getMaxResults()) break;

                String iri = ind.getIRI().toString();
                String localName = ind.getIRI().getFragment();

                List<String> types = reasoner.getTypes(ind, false).getFlattened().stream()
                        .filter(c -> config.isIncludeDirectType() || !c.equals(rootClass))
                        .map(c -> c.getIRI().getFragment())
                        .sorted()
                        .toList();

                Map<String, String> propValues = new LinkedHashMap<>();
                for (OWLDataProperty prop : dataProps) {
                    String value = reasoner.getDataPropertyValues(ind, prop).stream()
                            .map(OWLLiteral::getLiteral)
                            .findFirst()
                            .orElse(null);
                    propValues.put(prop.getIRI().getFragment(), value);
                }

                results.add(new IndividualRecord(iri, localName, types, propValues));
                count++;
            }

            log.info("[OntologyQuery] ✅ 完成: {} 个实例 (上限 {})", results.size(), config.getMaxResults());
            return Collections.unmodifiableList(results);

        } finally {
            reasoner.dispose();
        }
    }

    /**
     * 通过 Ontop Endpoint 查询指定个体的属性值，统一以字符串列表返回（支持多值）。
     * <ul>
     *   <li>DataProperty → 返回所有字面量的词法值</li>
     *   <li>ObjectProperty → 返回所有关联个体的完整 IRI 字符串</li>
     *   <li>属性无值或未声明 → 返回空列表</li>
     * </ul>
     * 逻辑与 {@link #queryPropertyValueInOntology} 完全对齐，仅数据源为数据库。
     *
     * @param ns            命名空间前缀（用于 SPARQL PREFIX）
     * @param individualIri 个体的完整 IRI
     * @param propertyIri   属性的完整 IRI
     * @return 属性值的字符串列表，不会返回 null
     */
    public List<String> queryPropertyValueInDB(String ns, String individualIri, String propertyIri) {
        Objects.requireNonNull(individualIri, "individualIri must not be null");
        Objects.requireNonNull(propertyIri, "propertyIri must not be null");

        String safeNs = escapeSparqlUri(ns);
        String safeIndividual = escapeSparqlUri(individualIri);
        String safeProperty = escapeSparqlUri(propertyIri);

        // ✅ 使用 SELECT 替代 CONSTRUCT，直接获取多值结果集
        // ✅ 不限制类型（a :PizzaComponent），保持与本体查询一致的通用性
        String sparql = """
            PREFIX : <%s>
            SELECT ?value
            WHERE {
                <%s> <%s> ?value .
            }
            """.formatted(safeNs, safeIndividual, safeProperty);

        List<String> results = new ArrayList<>();
        Model resultModel = queryConstruct(sparql);

        try {
            Resource individualRes = resultModel.getResource(individualIri);
            Property prop = resultModel.getProperty(propertyIri);

            StmtIterator it = resultModel.listStatements(individualRes, prop, (RDFNode) null);
            while (it.hasNext()) {
                RDFNode node = it.next().getObject();
                String value = node.isLiteral()
                        ? node.asLiteral().getLexicalForm()
                        : node.toString();
                results.add(value);
            }
        } finally {
            resultModel.close();
        }

        log.debug("[PropertyQuery-DB] {} on {} → {} values: {}",
                propertyIri, individualIri, results.size(), results);
        return results;
    }

    /**
     * 查询指定个体的属性值，统一以字符串列表返回（支持多值）。
     * <ul>
     *   <li>DataProperty → 返回所有推断字面量的词法值</li>
     *   <li>ObjectProperty → 返回所有推断个体的完整 IRI 字符串</li>
     *   <li>属性无值或未声明 → 返回空列表</li>
     * </ul>
     *
     * @param individualIri 个体的完整 IRI
     * @param propertyIri   属性的完整 IRI
     * @return 属性值的字符串列表，不会返回 null
     */
    public List<String> queryPropertyValueInOntology(String individualIri, String propertyIri){
        Objects.requireNonNull(individualIri, "individualIri must not be null");
        Objects.requireNonNull(propertyIri, "propertyIri must not be null");

        // ✅ 复用类成员 reasoner，无需重新创建/预计算/dispose
        OWLReasoner reasoner = backendService.getReasonerService().getReasoner();
        if (reasoner == null || !reasoner.isConsistent()) {
            throw new IllegalStateException(
                    "ReasonerService 的推理器未初始化或本体不一致，无法执行属性查询");
        }

        OWLNamedIndividual individual = backendService.getIndividual(individualIri);
        IRI propIRI = IRI.create(propertyIri);
        List<String> results = new ArrayList<>();

        // ========== 1. 优先尝试 DataProperty ==========
        OWLDataFactory dataFactory = backendService.getOntologyService().getDataFactory();
        OWLDataProperty dataProp = dataFactory.getOWLDataProperty(propIRI);
        Set<OWLLiteral> dataValues = reasoner.getDataPropertyValues(individual, dataProp);
        for (OWLLiteral literal : dataValues) {
            results.add(literal.getLiteral());
        }

        // ========== 2. 若数据属性无值，回退尝试 ObjectProperty ==========
        if (results.isEmpty()) {
            OWLObjectProperty objProp = dataFactory.getOWLObjectProperty(propIRI);
            NodeSet<OWLNamedIndividual> objValues =
                    reasoner.getObjectPropertyValues(individual, objProp);
            objValues.entities()
                    .map(ind -> ind.getIRI().toString())
                    .forEach(results::add);
        }

        log.debug("[PropertyQuery] {} on {} → {} values: {}",
                propIRI.getShortForm(), individualIri, results.size(), results);
        return results;
    }

    /**
     * 沿类层级向上查找对象属性的最佳匹配值域（Range）
     * 从 sourceClassIri 开始，若该类未定义 objectPropertyIri 的 Range，
     * 则逐层向父类查找，直到找到或到达 owl:Thing。
     *
     * @param sourceClassIri    起始类的完整 IRI
     * @param objectPropertyIri 要查找的对象属性 IRI
     * @return 匹配到的属性值域 IRI 集合；若整条继承链均未定义则返回空集
     */
    public Set<String> getBestMatchedType(String sourceClassIri, String objectPropertyIri) {
        try {
            OWLDataFactory df = backendService.getOntologyService().getDataFactory();
            OWLObjectProperty property = df.getOWLObjectProperty(IRI.create(objectPropertyIri));
            OWLClass currentClass = df.getOWLClass(IRI.create(sourceClassIri));

            // 使用 BFS 逐层向上查找，保证找到的是"最近"定义了该属性 Range 的祖先类
            Queue<OWLClass> queue = new LinkedList<>();
            Set<OWLClass> visited = new HashSet<>();
            queue.add(currentClass);
            visited.add(currentClass);

            while (!queue.isEmpty()) {
                OWLClass cls = queue.poll();

                // 检查当前类是否直接定义了该属性
                Set<String> ranges = backendService.getPropertyFillerFromClass(cls, objectPropertyIri);
                if (!ranges.isEmpty()) {
                    // ranges 已经是 Set<String>，无需再次 map 转换，直接使用即可
                    log.info("✅ 在类 {} 上找到属性 {} 的值域: {}",
                            cls.getIRI().getShortForm(),
                            property.getIRI().getShortForm(),
                            ranges);
                    return ranges;
                }

                // 当前类未定义，将其直接父类加入队列继续查找
                Set<OWLClass> superClasses = backendService.getSuperClasses(cls).stream()
                        .filter(c -> !c.isOWLThing())
                        .collect(Collectors.toSet());

                for (OWLClass superClass : superClasses) {
                    if (!visited.contains(superClass)) {
                        visited.add(superClass);
                        queue.add(superClass);
                    }
                }
            }

            log.warn("⚠️ 类 {} 及其所有祖先类均未定义属性 {} 的值域",
                    sourceClassIri, objectPropertyIri);
            return Set.of();

        } catch (Exception e) {
            log.error("沿类层级查找属性值域失败: {}", e.getMessage(), e);
            return Set.of();
        }
    }
    /**
     * 便捷重载：仅查实例及推断类型，无数据属性。
     */
    public List<IndividualRecord> queryInstances(
            OWLOntology tbox, OWLOntology abox, String rootClassIri, int maxResults)
            throws OWLOntologyCreationException {
        return queryInstances(tbox, abox,
                QueryConfig.builder(rootClassIri).maxResults(maxResults).build());
    }
}