package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.GenericDbWriter;
import com.ocean.ontopobdahandler.ObdaMappingParser;
import com.ocean.openlletresolver.BackendService;
import com.ocean.openlletresolver.GenericAxiomBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.*;

public class PizzaInsertService {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/mypizzadb?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";       // ← 根据实际修改
    private static final String DB_PASS = "zj780704";   // ← 根据实际修改
    private static final String NS = "http://example.org/pizza/components/classes/";
    private static final String BASE_NS = "http://example.org/pizza/components/classes/";
    private static final String OBDA_PATH       = "D:/work/Ontology/pizza-ontology/ontology/database/myPizza.obda";
    private static final Logger log = LoggerFactory.getLogger(PizzaInsertService.class);
    private BackendService backendService;

    private static final GenericDbWriter DB_WRITER;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(DB_URL);
        config.setUsername(DB_USER);
        config.setPassword(DB_PASS);
        // 可选：设置连接池参数
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        DataSource dataSource = new HikariDataSource(config);
        DB_WRITER = new GenericDbWriter(dataSource);
    }
    public PizzaInsertService(BackendService backendService) {
        this.backendService = backendService;
    }

    /**
     * 插入披萨组件并验证语义一致性
     * @param newName      组件唯一名称
     * @param triples      RDF三元组列表（必须包含非字面量rdf:type）
     */
    public void insertPizzaComponent(
            String newName,
            List<GenericAxiomBuilder.Triple> triples) throws Exception {

        // ⭐ 1. 参数防御（增加对新增参数的校验）
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
            throw new IllegalArgumentException("triples 中必须包含至少一条合法的 rdf:type 声明（对象不能是对象属性）");
        }

        String NS = "http://example.org/pizza/components/classes/";
        GenericAxiomBuilder AXIOM_BUILDER = new GenericAxiomBuilder(NS);

        // ⭐ 2. 构建本体公理（使用传入的obdaPath）
        Set<OWLAxiom> tempAxioms = AXIOM_BUILDER.buildAxioms(triples);
        ObdaMappingParser.load(OBDA_PATH);

        // ⭐ 3. 构建数据库写入动作（使用传入的dbWriter）
        GenericDbWriter.DbWriteAction dbAction = () -> {
            Map<String, Object> rowData = new LinkedHashMap<>();
            rowData.put("name", newName);

            for (GenericAxiomBuilder.Triple t : triples) {
                ObdaMappingParser.ColumnMapping mapping = ObdaMappingParser.resolve(t.predicate());
                Object columnValue = convertObjectValue(t.object(), mapping);
                rowData.put(mapping.getColumnName(), columnValue);
            }

            log.info("写入 pizza_components: name={} | 字段数={}", newName, rowData.size());
            DB_WRITER.insert("pizza_components", "id", rowData);
        };

        // ⭐ 4. 执行安全写入 + SPARQL 验证（使用传入的backendService）
        String verifySparql = """
            PREFIX : <%s>
            CONSTRUCT { ?s ?p ?o }
            WHERE { ?s a :PizzaComponent ; ?p ?o } LIMIT 5000
            """.formatted(NS);

        backendService.safeInsertAndVerify(
                tempAxioms,
                "http://example.org/pizza/components/classes/PizzaComponent",
                verifySparql,
                dbAction
        );
    }

    /**
     * 将 RDF Triple 的 object 值转换为数据库列所需的 Java 类型
     * 根据实际 OBDA 映射中的数据类型按需扩展
     */
    private static Object convertObjectValue(String objectValue, ObdaMappingParser.ColumnMapping mapping) {
        if (objectValue == null) return null;

        String columnName = mapping.getColumnName().toUpperCase();

        // 示例：根据列名或约定做类型转换
        // 实际项目中建议从 ColumnMapping 中读取 rr:datatype 来决定转换策略
        if (columnName.endsWith("_DATE") || columnName.contains("DATE")) {
            return java.sql.Date.valueOf(objectValue);
        } else if (columnName.endsWith("_AMOUNT") || columnName.contains("PRICE")) {
            return new java.math.BigDecimal(objectValue);
        } else if (columnName.endsWith("_FLAG") || columnName.startsWith("IS_")) {
            return Boolean.parseBoolean(objectValue);
        }

        // 默认作为字符串写入
        return objectValue;
    }
}
