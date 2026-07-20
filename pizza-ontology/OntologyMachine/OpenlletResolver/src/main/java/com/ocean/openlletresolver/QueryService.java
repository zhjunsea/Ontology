package com.ocean.openlletresolver;

import com.ocean.openlletresolver.BackendService;
import openllet.owlapi.OpenlletReasoner;
import openllet.owlapi.OpenlletReasonerFactory;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

        public static Builder builder(String rootClassIri) {
            return new Builder(rootClassIri);
        }

        public static class Builder {
            private final String rootClassIri;
            private List<String> dataPropertyIris = List.of();
            private int maxResults = -1;
            private boolean includeDirectType = false;

            private Builder(String rootClassIri) {
                this.rootClassIri = Objects.requireNonNull(rootClassIri);
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
                return new QueryConfig(this);
            }
        }
    }

    /**
     * 核心查询方法：获取指定类的所有实例（含推理）、推断类型及数据属性值。
     * 内部创建独立 Reasoner 并在 finally 中释放，不影响全局状态。
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
     * 便捷重载：仅查实例及推断类型，无数据属性。
     */
    public List<IndividualRecord> queryInstances(
            OWLOntology tbox, OWLOntology abox, String rootClassIri, int maxResults)
            throws OWLOntologyCreationException {
        return queryInstances(tbox, abox,
                QueryConfig.builder(rootClassIri).maxResults(maxResults).build());
    }
}