package com.ocean.ontologyframework;

import com.ocean.ontopobdahandler.GenericDbWriter;
import com.ocean.ontopobdahandler.OBDAHandler;
import com.ocean.ontopobdahandler.ObdaMappingParser;
import com.ocean.openlletresolver.GenericAxiomBuilder;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.springframework.util.IdGenerator;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ocean.ontologyframework.KnowledgeService.safeInsertAndVerify;

public class PizzaComponentInserter {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/mypizzadb?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC";
    private static final String DB_USER = "root";       // ← 根据实际修改
    private static final String DB_PASS = "zj780704";   // ← 根据实际修改
    private static final String NS = "http://example.org/pizza/components/classes/";
    private static final GenericDbWriter DB_WRITER = new GenericDbWriter(DB_URL, DB_USER, DB_PASS);
    private static final String BASE_NS = "http://example.org/pizza/components/classes/";
    private static final String OBDA_PATH       = "D:/work/Ontology/pizza-ontology/ontology/database/myPizza.obda";


    static void insertPizzaComponent() throws Exception {
        String NS = "http://example.org/pizza/components/classes/";
        // ⭐ 初始化通用公理构建器
        GenericAxiomBuilder AXIOM_BUILDER = new GenericAxiomBuilder(NS);

        // ⭐ 1. 以三元组形式声明本体数据（完全通用，无字段绑定）

        /*
        //不满足数据库type限制的要求
        String newName = "spicy_chicken_new";
        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "SpicyChicken", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );*/

        /*
        //不满足数据库name为唯一性要求
        String newName = "NeapolitanCrustInstance";
        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        ); */

        //添加成功
        String newName = "NeapolitanCrustInstanceTest";
        List<GenericAxiomBuilder.Triple> triples = List.of(
                new GenericAxiomBuilder.Triple(newName, "rdf:type", "NeapolitanCrust", false),
                new GenericAxiomBuilder.Triple(newName, "supplier", "SupplierX", true),
                new GenericAxiomBuilder.Triple(newName, "price", "12.99", true)
        );
        Set<OWLAxiom> tempAxioms = AXIOM_BUILDER.buildAxioms(triples);
        ObdaMappingParser.load(OBDA_PATH);

        // ⭐ 2. 数据库写入同样由三元组派生（保持语义层与数据层对齐）
        KnowledgeService.DbWriteAction dbAction = () -> {
            Map<String, Object> rowData = new LinkedHashMap<>();
            rowData.put("name", newName);

            for (GenericAxiomBuilder.Triple t : triples) {
                // ✅ resolve() 现在只接受谓词参数，找不到时内部直接抛出带诊断信息的异常
                ObdaMappingParser.ColumnMapping mapping = ObdaMappingParser.resolve(t.predicate());

                // ⚠️ object 值的处理从 Parser 中解耦，在此处根据业务需求转换
                Object columnValue = convertObjectValue(t.object(), mapping);

                rowData.put(mapping.getColumnName(), columnValue);
            }

            DB_WRITER.insert("pizza_components", "id", rowData);
        };

        String verifySparql = """
                PREFIX : <%s>
                CONSTRUCT { ?s ?p ?o }
                WHERE { ?s a :PizzaComponent ; ?p ?o } LIMIT 5000
                """.formatted(NS);

        try {
            safeInsertAndVerify(tempAxioms, "http://example.org/pizza/components/classes/PizzaComponent",verifySparql, dbAction);
        } catch (Exception e) {
            System.err.println("❌ 安全写入流程失败: " + e.getMessage());
            e.printStackTrace();
        }
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
