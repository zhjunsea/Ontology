package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.ObdaMappingParser;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.GenericAxiomBuilder;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class PizzaInsertService {

    private static final String NS = "http://example.org/pizza/components/classes/";
    private static final String OBDA_PATH = "D:/work/Ontology/pizza-ontology/ontology/database/myPizza.obda";
    private static final Logger log = LoggerFactory.getLogger(PizzaInsertService.class);

    private final BackendService backendService;
    // ✅ 不再持有 DB_WRITER，也不再需要 DataSource

    public PizzaInsertService(BackendService backendService) {
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");
    }

    public void insertPizzaComponent(String newName, List<GenericAxiomBuilder.Triple> triples) throws Exception {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("newName 不能为空");
        }
        if (triples == null || triples.isEmpty()) {
            throw new IllegalArgumentException("triples 不能为空");
        }
        Objects.requireNonNull(backendService, "backendService 不能为null");

        boolean hasType = triples.stream()
                .anyMatch(t -> "rdf:type".equals(t.predicate()) && !t.isObjectProperty());
        if (!hasType) {
            throw new IllegalArgumentException("triples 中必须包含至少一条合法的 rdf:type 声明");
        }

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(NS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(triples);
        ObdaMappingParser.load(OBDA_PATH);

        // ✅ 数据库写入动作委托给 OBDAHandler
        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            Map<String, Object> rowData = new LinkedHashMap<>();
            rowData.put("name", newName);

            for (GenericAxiomBuilder.Triple t : triples) {
                ObdaMappingParser.ColumnMapping mapping = ObdaMappingParser.resolve(t.predicate());
                Object columnValue = ObdaMappingParser.convertObjectValue(t.object(), mapping);
                rowData.put(mapping.getColumnName(), columnValue);
            }

            log.info("写入 pizza_components: name={} | 字段数={}", newName, rowData.size());
            // ✅ 直接使用 OBDAHandler 的 addComponent 方法
            List<String> columns = new ArrayList<>(rowData.keySet());
            List<Object> values = new ArrayList<>(rowData.values());
            OBDAHandler.getInstance().addComponent("pizza_components", columns, values);
        };

        String verifySparql = """
                PREFIX : <%s>
                CONSTRUCT { ?s ?p ?o }
                WHERE { ?s a :PizzaComponent ; ?p ?o } LIMIT 5000
                """.formatted(NS);

        backendService.safeInsertAndVerify(tempAxioms, NS + "PizzaComponent", verifySparql, dbAction);
    }
}