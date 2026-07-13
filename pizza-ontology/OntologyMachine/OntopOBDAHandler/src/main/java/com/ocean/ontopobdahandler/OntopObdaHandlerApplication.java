package com.ocean.ontopobdahandler;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;

/**
 * 纯 SPARQL 客户端
 * 通过 RDFConnectionRemote 访问已独立部署的 Ontop Endpoint
 *
 * 架构: [本程序] --HTTP/SPARQL--> [Ontop Endpoint] --SQL--> [MySQL]
 */
public class OntopObdaHandlerApplication {

    // Ontop Endpoint 的标准 SPARQL 协议地址
    private static final String ONTOP_SPARQL_ENDPOINT = "http://localhost:8080/sparql";

    public static void main(String[] args) {
        System.out.println("=== VKG SPARQL Client (Jena 6.x) ===\n");

        // ✅ 创建远程连接 (自动处理 HTTP 请求、结果集解析)
        try (RDFConnection conn = RDFConnectionRemote.create()
                .destination(ONTOP_SPARQL_ENDPOINT)
                .build()) {

            // 1. 基础查询: 直接查 MySQL 中的数据 (通过 mapping 映射)
            queryEmployees(conn);

            // 2. 推理查询: 利用 Openllet 预处理 + Ontop QL 推理
            //    例如: 查询 ex:Person, Ontop 会自动展开为 Employee|Manager 等子类
            queryWithInference(conn);

            // 3. 聚合查询: 下推到 MySQL 执行 GROUP BY
            queryAggregation(conn);

        } catch (Exception e) {
            System.err.println("❌ 无法连接到 Ontop Endpoint: " + e.getMessage());
            System.err.println("请确认服务已启动: " + ONTOP_SPARQL_ENDPOINT);
            e.printStackTrace();
        }

        System.out.println("\n=== 查询完成 ===");
    }

    /**
     * 基础查询
     */
    private static void queryEmployees(RDFConnection conn) {
        System.out.println("--- [1] 基础员工查询 ---");
        String sparql = """
                PREFIX : <http://example.org/pizza/>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                SELECT ?property ?value
                WHERE {
                  :NeapolitanCrust ?property ?value .
                }
                ORDER BY ?property
                """;

        conn.querySelect(sparql, (QuerySolution qs) -> {
            // 获取属性名（去掉命名空间前缀，只保留本地名）
            String property = qs.getResource("property").getLocalName();

            // ?value 可能是 Literal 也可能是 Resource，必须分别处理
            String value;
            if (qs.get("value").isLiteral()) {
                value = qs.getLiteral("value").getString();
            } else {
                // 如果是资源，取其本地名；若需完整URI可改为 .getURI()
                value = qs.getResource("value").getLocalName();
            }

            System.out.printf("   %-25s | %s%n", property, value);
        });
        System.out.println();
    }

    /**
     * 带推理的查询 (核心优势)
     * Ontop 会将 TBox 中的子类关系编译进 SQL WHERE 条件
     * 示例：查询所有 PizzaComponent 及其子类(如 Crust, Topping 等)的实例
     */
    private static void queryWithInference(RDFConnection conn) {
        System.out.println("--- [2] 推理查询 (PizzaComponent 及其所有子类) ---");
        String sparql = """
                PREFIX : <http://example.org/pizza/>
                PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                SELECT ?individual ?type WHERE {
                    ?individual a :PizzaComponent .
                    ?individual rdf:type ?type .
                }
                LIMIT 20
                """;

        conn.querySelect(sparql, (QuerySolution qs) -> {
            // ⚠️ 注意：?type 可能是匿名节点或外部词汇，需防御性取值
            String typeStr = qs.get("type").isResource()
                    ? qs.getResource("type").getLocalName()
                    : qs.get("type").toString();

            System.out.printf("   %-25s → %s%n",
                    qs.getResource("individual").getLocalName(),
                    typeStr);
        });
        System.out.println();
    }

    /**
     * 聚合查询 (验证 SQL 下推能力)
     * 示例：按供应商统计组件数量及平均价格，验证 COUNT/AVG/GROUP BY 下推
     */
    private static void queryAggregation(RDFConnection conn) {
        System.out.println("--- [3] 供应商组件统计 (SQL GROUP BY 下推) ---");
        String sparql = """
                PREFIX : <http://example.org/pizza/>
                SELECT ?supplier (COUNT(?item) AS ?count) (AVG(?price) AS ?avgPrice) WHERE {
                    ?item a :PizzaComponent ;
                          :supplier ?supplier ;
                          :price ?price .
                }
                GROUP BY ?supplier
                ORDER BY DESC(?count)
                """;

        conn.querySelect(sparql, (QuerySolution qs) -> {
            System.out.printf("   %-20s : %d 种组件, 均价 %.2f 元%n",
                    qs.getLiteral("supplier").getString(),
                    qs.getLiteral("count").getInt(),
                    qs.getLiteral("avgPrice").getDouble());
        });
        System.out.println();
    }
}