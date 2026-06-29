import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.query.*;
import org.apache.jena.util.FileManager;
import java.nio.file.*;
import java.util.*;

public class GenericOntologySingleQuery {

    public static void main(String[] args) {
        String ontologyFile = null;
        String subjectIRI = null;
        String propertyIRI = null;

        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o": case "--ontology": ontologyFile = args[++i]; break;
                case "-s": case "--subject":   subjectIRI = args[++i]; break;
                case "-p": case "--property":  propertyIRI = args[++i]; break;
                default:
                    System.err.println("未知参数: " + args[i]);
                    printUsage();
                    return;
            }
        }

        if (ontologyFile == null || subjectIRI == null || propertyIRI == null) {
            System.err.println("必须提供 -o (主本体), -s (主体IRI), -p (属性IRI)");
            printUsage();
            return;
        }

        // 1. 加载本体，自动处理导入
        OntModel model = loadOntology(ontologyFile);
        if (model == null) {
            System.err.println("无法加载本体文件: " + ontologyFile);
            return;
        }

        // 2. 尝试直接查询
        System.out.println("=== 尝试直接属性查询 ===");
        List<String> results = directQuery(model, subjectIRI, propertyIRI);
        if (!results.isEmpty()) {
            results.forEach(val -> System.out.println("  " + val));
            return;
        }

        // 3. 若直接查询无结果，尝试 OWL 类公理穿透
        System.out.println("直接查询无结果，尝试 OWL 类公理穿透...");
        results = restrictionQuery(model, subjectIRI, propertyIRI);
        if (results.isEmpty()) {
            System.out.println("未找到任何结果。");
        } else {
            results.forEach(val -> System.out.println("  " + val));
        }
    }

    /** 加载本体，并启用导入处理 */
    private static OntModel loadOntology(String filePath) {
        try {
            Path absPath = Paths.get(filePath).toAbsolutePath().normalize();
            OntModelSpec spec = new OntModelSpec(OntModelSpec.OWL_MEM);
            OntDocumentManager docMgr = spec.getDocumentManager();
            docMgr.setProcessImports(true);               // 关键：开启递归导入
            FileManager fileMgr = docMgr.getFileManager();
            fileMgr.addLocatorFile(".");                 // 允许相对路径
            OntModel model = ModelFactory.createOntologyModel(spec, null);
            model.read(absPath.toUri().toString());
            return model;
        } catch (Exception e) {
            System.err.println("加载本体时出错: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /** 直接三元组查询：?s ?p ?o */
    private static List<String> directQuery(Model model, String subject, String property) {
        String queryStr = String.format(
            "SELECT ?value WHERE { <%s> <%s> ?value }", subject, property);
        return executeQuery(model, queryStr);
    }

    /** OWL 限制穿透查询：rdfs:subClassOf* + owl:onProperty + owl:hasValue */
    private static List<String> restrictionQuery(Model model, String subject, String property) {
        String queryStr = String.format(
            "PREFIX owl: <http://www.w3.org/2002/07/owl#> " +
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> " +
            "SELECT ?value WHERE { " +
            "  <%s> rdfs:subClassOf* ?restriction . " +
            "  ?restriction owl:onProperty <%s> ; " +
            "               owl:hasValue ?value . " +
            "}", subject, property);
        return executeQuery(model, queryStr);
    }

    /** 执行 SPARQL 查询，返回所有 ?value 的字符串形式 */
    private static List<String> executeQuery(Model model, String queryStr) {
        List<String> results = new ArrayList<>();
        try (QueryExecution qe = QueryExecutionFactory.create(QueryFactory.create(queryStr), model)) {
            ResultSet rs = qe.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                RDFNode node = sol.get("value");
                if (node != null) {
                    results.add(node.toString());
                }
            }
        } catch (Exception e) {
            System.err.println("查询执行出错: " + e.getMessage());
        }
        return results;
    }

    private static void printUsage() {
        System.out.println("""
            用法: java GenericOntologySingleQueryJena -o <主本体> -s <主体IRI> -p <属性IRI>
            程序自动先尝试直接属性查询，若无结果则穿透 OWL 类公理。
            示例：
              java GenericOntologySingleQueryJena -o myPizza.owl -s http://example.org/pizza/NeapolitanPizza -p http://example.org/pizza/hasProcessStep
            """);
    }
}