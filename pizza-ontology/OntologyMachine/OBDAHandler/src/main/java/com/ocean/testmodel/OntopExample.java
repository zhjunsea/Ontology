package com.ocean.testmodel;

import it.unibz.inf.ontop.injection.OntopMappingSQLConfiguration;
import it.unibz.inf.ontop.injection.OntopSystemSQLConfiguration;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import it.unibz.inf.ontop.rdf4j.repository.impl.OntopVirtualRepository;
import it.unibz.inf.ontop.injection.OntopSystemConfiguration;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import java.util.Properties;

public class OntopExample {
    public static void main(String[] args) {

        // 1. 使用 Properties 配置所有参数（Ontop 5.x 标准做法）
        Properties properties = new Properties();

        // 本体和映射文件路径（请确保这两个文件在你的项目根目录或提供绝对路径）
        properties.setProperty("ontop.ontologyFile", "pizza-tbox.owl");
        properties.setProperty("ontop.nativeOntopMappingFile", "mapping.obda");

        // 数据库连接信息（这里以 H2 内存数据库为例）
        properties.setProperty("jdbc.url", "jdbc:h2:mem:pizza;DB_CLOSE_DELAY=-1");
        properties.setProperty("jdbc.driver", "org.h2.Driver");
        properties.setProperty("jdbc.user", "sa");
        properties.setProperty("jdbc.password", "");

        OntopVirtualRepository repository = null;

        // 2. 构建配置并强制转换为系统配置
        /*OntopSystemConfiguration config = (OntopSystemConfiguration)
        OntopMappingSQLConfiguration.defaultBuilder()
                .properties(properties)
                .build();*/
        OntopSystemConfiguration config = OntopSystemSQLConfiguration.defaultBuilder()
                .properties(properties)
                .build();

        // 3. 创建并初始化虚拟仓库
        repository = new OntopVirtualRepository(config);
        repository.init();

        // 4. 获取连接
        try (RepositoryConnection conn = repository.getConnection()){

            // 5. 执行 SPARQL 查询
            String sparql = "PREFIX comp: <http://example.org/pizza/components/classes/>\n" +
                    "PREFIX ind: <http://example.org/pizza/individuals/>\n" +
                    "SELECT ?supplier WHERE {\n" +
                    "  ind:neapolitanCrustInstance comp:supplier ?supplier .\n" +
                    "}";
            TupleQuery query = conn.prepareTupleQuery(sparql);

            try(TupleQueryResult result = query.evaluate()) {

                // 6. 遍历结果，获取每一行的 BindingSet
                while (result.hasNext()) {
                    BindingSet bs = result.next();
                    System.out.println("供应商: " + bs.getValue("supplier").stringValue());
                }
            }
        } finally {
            if (repository != null) {
                try {
                    repository.shutDown(); // 注意：RDF4J 4.x 中仓库的关闭方法通常是 shutDown()
                } catch (Throwable t) {  // ⚠️ 核心修改：将 Exception 改为 Throwable
                    t.printStackTrace();
                }
            }
        }
    }
}