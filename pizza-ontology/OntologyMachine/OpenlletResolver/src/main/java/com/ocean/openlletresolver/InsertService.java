package com.ocean.openlletresolver;

import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.ObdaMappingParser;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class InsertService {

    private static final Logger log = LoggerFactory.getLogger(InsertService.class);

    //主语及被存储到数据库的字段名对应表

    private final BackendService backendService;
    // ✅ 不再持有 DB_WRITER，也不再需要 DataSource

    public InsertService(BackendService backendService) {
        this.backendService = Objects.requireNonNull(backendService, "backendService 不能为null");

    }

    //NS是Type类的名字空间，比如NeapolitanCrust的是http://example.org/pizza/components/classes/
    //targetTopClass是为了校验用，一般设为顶级父类
    public void insertComponent(String typeNS, String indNS, BackendService.objectPair objectPair, List<GenericAxiomBuilder.Triple> triples, String tableName, String targetTopClass) throws Exception {
        if (objectPair.objectName() == null || objectPair.objectName().isBlank()) {
            throw new IllegalArgumentException("newName 不能为空");
        }
        if (triples == null || triples.isEmpty()) {
            throw new IllegalArgumentException("triples 不能为空");
        }
        Objects.requireNonNull(backendService, "backendService 不能为null");

        Optional<GenericAxiomBuilder.Triple> matchedTriple = triples.stream()
                .filter(t -> "rdf:type".equals(t.predicate()) && !t.isObjectProperty())
                .findFirst();

        if (matchedTriple.isEmpty()) {
            throw new IllegalArgumentException("triples 中必须包含至少一条合法的 rdf:type 声明");
        }

        String individualType = matchedTriple
                .map(GenericAxiomBuilder.Triple::object)
                .orElse(null);

        matchedTriple.ifPresent(t -> {
            String subject = t.subject();
            String type    = t.object();
            log.info("Subject: {}, Type: {}", subject, type);
        });

        GenericAxiomBuilder axiomBuilder = new GenericAxiomBuilder(backendService,typeNS, indNS);
        Set<OWLAxiom> tempAxioms = axiomBuilder.buildAxioms(triples);
        ObdaMappingParser.load(backendService.getObdaHandler().getObdaPath());

        // ✅ 数据库写入动作委托给 OBDAHandler
        var dbAction = (com.ocean.ontopobdahandler.GenericDbWriter.DbWriteAction) () -> {
            Map<String, Object> rowData = new LinkedHashMap<>();
            rowData.put(objectPair.columnName(), objectPair.objectName());

            for (GenericAxiomBuilder.Triple t : triples) {
                ObdaMappingParser.ColumnMapping mapping = ObdaMappingParser.resolve(t.predicate());
                Object columnValue = ObdaMappingParser.convertObjectValue(t.object(), mapping);
                rowData.put(mapping.getColumnName(), columnValue);
            }

            log.info("写入 " + tableName + ": name={} | 字段数={}", objectPair.objectName(), rowData.size());
            // ✅ 直接使用 OBDAHandler 的 addComponent 方法
            List<String> columns = new ArrayList<>(rowData.keySet());
            List<Object> values = new ArrayList<>(rowData.values());
            OBDAHandler.getInstance().addComponent(tableName, columns, values);
        };


        backendService.safeVerifyAndDBExecution(tempAxioms, targetTopClass, dbAction);
    }
}