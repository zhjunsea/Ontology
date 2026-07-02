import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.ReasonerRegistry;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class GenericOntologyQuery {

    private static boolean debug = false;
    private static Mode queryMode = Mode.DEEP;
    private static boolean propertySpecified = false;

    enum Mode { DIRECT, DEEP, ALL }

    public static void main(String[] args) throws Exception {
        String ontologyFile = null;
        String subjectIRI = null;
        List<String> propertyPath = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o": case "--ontology": ontologyFile = args[++i]; break;
                case "-s": case "--subject":   subjectIRI = args[++i]; break;
                case "-p": case "--property": {
                    propertySpecified = true;
                    String raw = args[++i].replaceAll("^\"|\"$", "");
                    String[] parts = raw.split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (trimmed.isEmpty()) continue;
                        if (trimmed.contains(" ")) {
                            throw new IllegalArgumentException("属性 IRI 不能包含空格: " + trimmed);
                        }
                        propertyPath.add(trimmed);
                    }
                    break;
                }
                case "-m": case "--mode": {
                    String modeStr = args[++i].toLowerCase();
                    switch (modeStr) {
                        case "direct": queryMode = Mode.DIRECT; break;
                        case "deep":   queryMode = Mode.DEEP;   break;
                        case "all":    queryMode = Mode.ALL;    break;
                        default:
                            System.err.println("未知模式: " + modeStr + "，使用默认 deep");
                            queryMode = Mode.DEEP;
                    }
                    break;
                }
                case "--debug": debug = true; break;
                default:
                    System.err.println("未知参数: " + args[i]);
                    printUsage();
                    return;
            }
        }

        if (ontologyFile == null || subjectIRI == null) {
            System.err.println("必须提供 -o (本体) 和 -s (主体IRI)");
            printUsage();
            return;
        }

        boolean isChain = propertyPath.size() >= 2;
        if (!isChain && queryMode != Mode.ALL && !propertySpecified) {
            System.err.println("direct / deep 模式下必须提供 -p 指定要查询的属性");
            printUsage();
            return;
        }

        Model model = loadOntology(ontologyFile);
        if (debug) System.out.println("已加载本体，三元组数量: " + model.size());

        boolean isClass = isClass(model, subjectIRI);
        if (debug) System.out.println("主体类型: " + (isClass ? "类" : "个体"));

        String sparql;
        if (isChain) {
            sparql = buildChainQuery(subjectIRI, propertyPath);
        } else {
            sparql = buildQuery(subjectIRI, propertyPath, isClass);
        }

        if (debug) {
            System.out.println("\n--- SPARQL ---");
            System.out.println(sparql);
            System.out.println("--------------");
        }

        List<Map<String, String>> rawRows = executeSparqlQuery(model, sparql);
        if (debug) System.out.println("原始查询返回 " + rawRows.size() + " 行");

        // 三段式无结果时自动回退，兼容 QName 主语
        if (isChain && propertyPath.size() == 3 && rawRows.isEmpty()) {
            String objProp = propertyPath.get(0);
            String individual = propertyPath.get(1);
            String dataProp = propertyPath.get(2);
            String localName = individual.substring(individual.lastIndexOf('/') + 1);
            if (debug) {
                System.out.println("链式查询无结果，回退为模糊匹配个体属性（完整 IRI 或局部名均可）");
            }
            sparql = String.format("""
                SELECT ?value WHERE {
                  ?task <%s> ?value .
                  FILTER ( ?task = <%s> || STRENDS(STR(?task), "%s") )
                }
                """, dataProp, individual, localName);
            if (debug) System.out.println("回退 SPARQL: " + sparql);
            rawRows = executeSparqlQuery(model, sparql);
        }

        // 后处理：deep 模式单属性类的精选
        if (!isChain && queryMode == Mode.DEEP && isClass && propertySpecified && propertyPath.size() == 1) {
            List<String> values = new ArrayList<>();
            for (Map<String, String> row : rawRows) {
                values.add(row.get("value"));
            }
            List<String> refined = filterMostSpecific(model, values);
            rawRows.clear();
            for (String v : refined) {
                Map<String, String> m = new HashMap<>();
                m.put("value", v);
                rawRows.add(m);
            }
            if (debug) System.out.println("精选后剩余 " + refined.size() + " 个值");
        }

        // 诊断（如果最终结果仍为空）
        if (rawRows.isEmpty() && debug) {
            System.out.println("\n诊断：最终无结果");
        }

        // 输出
        if (!isChain && queryMode == Mode.ALL) {
            for (Map<String, String> row : rawRows) {
                System.out.println(row.get("property") + " -> " + row.get("value"));
            }
        } else {
            for (Map<String, String> row : rawRows) {
                System.out.println(row.get("value"));
            }
        }
    }

    private static Model loadOntology(String mainFile) throws IOException {
        Model model = ModelFactory.createDefaultModel();
        loadRecursive(model, mainFile, new HashSet<>());
        return model;
    }

    private static void loadRecursive(Model model, String filePath, Set<String> loaded) throws IOException {
        String absolutePath = Paths.get(filePath).toRealPath().toString();
        if (loaded.contains(absolutePath)) return;
        loaded.add(absolutePath);
        if (debug) System.out.println("加载: " + absolutePath);
        Model temp = RDFDataMgr.loadModel(absolutePath);
        model.add(temp);
        StmtIterator iter = temp.listStatements(null, OWL.imports, (RDFNode) null);
        while (iter.hasNext()) {
            Statement st = iter.next();
            String uri = st.getResource().getURI();
            if (uri != null && uri.startsWith("file:///")) {
                String path = Paths.get(URI.create(uri)).toString();
                File f = new File(path);
                if (!f.isAbsolute()) {
                    path = new File(new File(absolutePath).getParent(), path).getAbsolutePath();
                }
                if (new File(path).exists()) {
                    loadRecursive(model, path, loaded);
                } else if (debug) {
                    System.out.println("警告: 导入文件不存在 - " + path);
                }
            }
        }
    }

    private static boolean isClass(Model model, String iri) {
        Resource res = model.getResource(iri);
        return model.contains(res, RDF.type, OWL.Class) ||
                model.contains(res, RDF.type, RDFS.Class);
    }

    private static String buildQuery(String subject, List<String> propPath, boolean isClass) {
        if (queryMode == Mode.DIRECT) {
            return buildDirect(subject, propPath.get(0));
        } else if (queryMode == Mode.DEEP) {
            if (isClass) {
                return buildClassDeep(subject, propPath.get(0));
            } else {
                return buildDirect(subject, propPath.get(0));
            }
        } else {
            if (isClass) {
                return buildClassAll(subject, propPath.isEmpty() ? null : propPath.get(0));
            } else {
                return buildInstanceAll(subject, propPath.isEmpty() ? null : propPath.get(0));
            }
        }
    }

    private static String buildChainQuery(String subject, List<String> chain) {
        String objProp = chain.get(0);
        String dataProp;
        String targetIndividual = null;

        if (chain.size() == 2) {
            dataProp = chain.get(1);
        } else if (chain.size() == 3) {
            targetIndividual = chain.get(1);
            dataProp = chain.get(2);
        } else {
            throw new IllegalArgumentException("链式穿透需要 2 或 3 个属性 IRI");
        }

        if (targetIndividual != null) {
            return String.format("""
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?value WHERE {
                  <%s> rdfs:subClassOf* ?restriction .
                  ?restriction owl:onProperty <%s> ;
                               owl:hasValue ?task .
                  VALUES ?task { <%s> }
                  ?task <%s> ?value .
                }
                """, subject, objProp, targetIndividual, dataProp);
        } else {
            return String.format("""
                PREFIX owl: <http://www.w3.org/2002/07/owl#>
                PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
                SELECT ?value WHERE {
                  <%s> rdfs:subClassOf* ?restriction .
                  ?restriction owl:onProperty <%s> ;
                               owl:hasValue ?task .
                  ?task <%s> ?value .
                }
                """, subject, objProp, dataProp);
        }
    }

    private static String buildDirect(String subject, String property) {
        return String.format("""
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?value WHERE {
              <%s> <%s> ?value .
            }
            """, subject, property);
    }

    private static String buildClassDeep(String subject, String property) {
        return String.format("""
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?value WHERE {
              <%s> rdfs:subClassOf* ?restriction .
              ?restriction owl:onProperty <%s> ;
                           ?valueType ?value .
              VALUES ?valueType { owl:someValuesFrom owl:allValuesFrom owl:hasValue }
            }
            """, subject, property);
    }

    private static String buildClassAll(String subject, String propertyFilter) {
        String filter = propertyFilter != null ?
                String.format("FILTER(?property = <%s>)", propertyFilter) : "";
        return String.format("""
            PREFIX owl: <http://www.w3.org/2002/07/owl#>
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?property ?value WHERE {
              { <%s> ?property ?value . }
              UNION
              { <%s> rdfs:subClassOf* ?restriction .
                ?restriction owl:onProperty ?property ;
                             ?valueType ?value .
                VALUES ?valueType { owl:someValuesFrom owl:allValuesFrom owl:hasValue }
              }
              %s
            }
            """, subject, subject, filter);
    }

    private static String buildInstanceAll(String subject, String propertyFilter) {
        String filter = propertyFilter != null ?
                String.format("FILTER(?property = <%s>)", propertyFilter) : "";
        return String.format("""
            SELECT ?property ?value WHERE {
              <%s> ?property ?value .
              %s
            }
            """, subject, filter);
    }

    private static List<Map<String, String>> executeSparqlQuery(Model model, String sparql) {
        List<Map<String, String>> rows = new ArrayList<>();
        Query query = QueryFactory.create(sparql);
        try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
            ResultSet rs = qe.execSelect();
            List<String> vars = rs.getResultVars();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                Map<String, String> row = new LinkedHashMap<>();
                for (String var : vars) {
                    RDFNode node = sol.get(var);
                    if (node == null) continue;
                    String val = node.isResource() ? node.asResource().getURI() :
                            node.isLiteral() ? node.asLiteral().getLexicalForm() : "";
                    row.put(var, val);
                }
                if (!row.isEmpty()) rows.add(row);
            }
        }
        return rows;
    }

    private static List<String> filterMostSpecific(Model model, List<String> candidates) {
        if (candidates.size() <= 1) return candidates;
        Reasoner reasoner = ReasonerRegistry.getRDFSReasoner();
        InfModel inf = ModelFactory.createInfModel(reasoner, model);
        Set<String> supers = new HashSet<>();
        for (String a : candidates) {
            Resource ra = inf.getResource(a);
            for (String b : candidates) {
                if (a.equals(b)) continue;
                Resource rb = inf.getResource(b);
                if (inf.contains(rb, RDFS.subClassOf, ra)) {
                    supers.add(a);
                    break;
                }
            }
        }
        List<String> kept = new ArrayList<>();
        for (String c : candidates) {
            if (!supers.contains(c)) kept.add(c);
        }
        return kept.isEmpty() ? candidates : kept;
    }

    private static void printUsage() {
        System.out.println("""
            用法: java GenericOntologyQuery -o <本体文件> -s <主体IRI> [-p <属性IRI>] [-m <模式>] [--debug]

            模式 (-m):
              direct  - 类的直接单属性查询
              deep    - 穿透查询 (类属性自动精选最具体结果)和个体的单属性查询
              all     - 查询所有属性

            链式穿透 (自动激活，忽略 -m):
              两段: -p <对象属性IRI>,<数据属性IRI>
              三段: -p <对象属性IRI>,<中间个体IRI>,<数据属性IRI>

            示例:
              # 查询类的单个属性： java ... -s http://example.org/pizza/NeapolitanPizza -p http://example.org/pizza/hasSauce
              # 查询类的所有属性： java ... -s http://example.org/pizza/NeapolitanPizza -m all
              # 直接查询个体的属性：java ... -s http://example.org/pizza/neapolitanCrustInstance -p http://example.org/pizza/crustThicknessMm -m deep
              # 查询个体的所有属性：java ... -s http://example.org/pizza/neapolitanCrustInstance -m all
              # 链式查询某个属性单个属性的精确值：java ... -s http://example.org/pizza/NeapolitanPizza -p http://example.org/pizza/hasProcessStep,http://example.org/pizza/process/Task_DoughMix,http://example.org/pizza/process/duration
              # 链式查询包含该属性的所有属性的值：java ... -s http://example.org/pizza/NeapolitanPizza -p http://example.org/pizza/hasProcessStep,http://example.org/pizza/process/duration
            """);
    }
}