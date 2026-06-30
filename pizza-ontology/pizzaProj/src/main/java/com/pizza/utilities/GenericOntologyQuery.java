package com.pizza.utilities;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class GenericOntologyQuery {

    private static Map<String, String> prefixMap = new LinkedHashMap<>();
    private static boolean debug = false;

    public static void main(String[] args) throws Exception {
        String ontologyFile = null;
        String subjectIRI = null;
        List<String> path = new ArrayList<>();
        String sparqlCmd = System.getProperty("os.name").toLowerCase().contains("win") ? "sparql.bat" : "sparql";
        String queryFile = "query.rq";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o": case "--ontology": ontologyFile = args[++i]; break;
                case "-s": case "--subject":   subjectIRI = args[++i]; break;
                case "-p": case "--path": {
                    String[] parts = args[++i].split(",");
                    for (String part : parts) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) path.add(trimmed);
                    }
                    break;
                }
                case "--sparql": sparqlCmd = args[++i]; break;
                case "-q": case "--query-file": queryFile = args[++i]; break;
                case "--debug": debug = true; break;
                default:
                    System.err.println("未知参数: " + args[i]);
                    printUsage();
                    return;
            }
        }

        if (ontologyFile == null || subjectIRI == null || (path.size() != 2 && path.size() != 3)) {
            System.err.println("必须提供 -o (主本体), -s (主体IRI), -p (路径)");
            printUsage();
            return;
        }

        loadPrefixesFromOntology(ontologyFile);
        if (debug) System.out.println("已加载命名空间前缀: " + prefixMap);

        String queryStr;
        if (path.size() == 2) {
            queryStr = buildQuery2(subjectIRI, path.get(0), path.get(1));
        } else {
            String objProp = path.get(0);
            String taskIRI = path.get(1);
            String dataProp = path.get(2);
            String localName = extractLocalName(taskIRI);
            queryStr = buildQuery3Local(subjectIRI, objProp, localName, dataProp);
        }

        if (debug) printQueryToConsole(queryStr);
        executeQuery(ontologyFile, queryFile, queryStr, sparqlCmd);
    }

    private static String extractLocalName(String iri) {
        int idx = iri.lastIndexOf('/');
        return (idx != -1) ? iri.substring(idx + 1) : iri;
    }

    private static void loadPrefixesFromOntology(String filePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(filePath)), "UTF-8");
        Pattern rootPattern = Pattern.compile("<rdf:RDF\\b[^>]*>", Pattern.DOTALL);
        Matcher rootMatcher = rootPattern.matcher(content);
        if (rootMatcher.find()) {
            String rootTag = rootMatcher.group();
            Pattern nsPattern = Pattern.compile("xmlns:(\\w+)=\"([^\"]+)\"");
            Matcher nsMatcher = nsPattern.matcher(rootTag);
            while (nsMatcher.find()) {
                String prefix = nsMatcher.group(1);
                String uri = nsMatcher.group(2);
                prefixMap.put(uri, prefix + ":");
            }
        }
    }

    private static String allPrefixes() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : prefixMap.entrySet()) {
            String uri = entry.getKey();
            String prefixed = entry.getValue();
            String prefixName = prefixed.substring(0, prefixed.length() - 1);
            sb.append(String.format("PREFIX %s: <%s>\n", prefixName, uri));
        }
        sb.append("PREFIX owl: <http://www.w3.org/2002/07/owl#>\n");
        sb.append("PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n");
        return sb.toString();
    }

    private static String buildQuery2(String subject, String objProp, String dataProp) {
        return allPrefixes() + String.format("""
            SELECT ?result WHERE {
              <%s> rdfs:subClassOf* ?restriction .
              ?restriction owl:onProperty <%s> ;
                           owl:hasValue ?task .
              ?task <%s> ?result .
            }
            """, subject, objProp, dataProp);
    }

    private static String buildQuery3Local(String subject, String objProp, String localName, String dataProp) {
        return allPrefixes() + String.format("""
            SELECT ?result WHERE {
              <%s> rdfs:subClassOf* ?restriction .
              ?restriction owl:onProperty <%s> ;
                           owl:hasValue ?task .
              FILTER (STRENDS(STR(?task), "%s"))
              ?task <%s> ?result .
            }
            """, subject, objProp, localName, dataProp);
    }

    private static void printQueryToConsole(String queryStr) {
        System.out.println("\n--- 生成的 query.rq 文件内容 ---");
        System.out.println(queryStr);
        System.out.println("---------------------------------");
    }

    private static void executeQuery(String ontologyFile, String queryFile, String queryStr, String sparqlCmd) throws Exception {
        Files.write(Paths.get(queryFile), queryStr.getBytes());

        List<String> command = new ArrayList<>();
        command.add(sparqlCmd);
        command.add("--data=" + ontologyFile);
        command.add("--query=" + queryFile);

        if (debug) System.out.println("执行命令: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            boolean headerPassed = false;
            boolean columnHeaderSkipped = false;   // 新增标志，用于跳过列名行
            while ((line = reader.readLine()) != null) {
                if (debug) {
                    System.out.println(line);
                } else {
                    // 跳过表头虚线
                    if (!headerPassed) {
                        if (line.contains("-----")) {
                            headerPassed = true;
                        }
                        continue;
                    }
                    // 跳过列名行（表头虚线后的第一行竖线内容）
                    if (!columnHeaderSkipped) {
                        columnHeaderSkipped = true;
                        continue;
                    }
                    // 解析数据行
                    if (line.trim().startsWith("|")) {
                        String[] parts = line.split("\\|");
                        if (parts.length >= 2) {
                            String value = parts[1].trim();
                            if (!value.isEmpty()) {
                                int caretIdx = value.indexOf("^^");
                                if (caretIdx != -1) {
                                    value = value.substring(0, caretIdx);
                                }
                                if (value.startsWith("\"") && value.endsWith("\"")) {
                                    value = value.substring(1, value.length() - 1);
                                }
                                System.out.println(value);
                            }
                        }
                    }
                }
            }
        }
        process.waitFor();
    }

    private static void printUsage() {
        System.out.println("""
            用法: java GenericOntologyQuery -o <主本体> -s <主体IRI> -p <路径> [--debug]
            路径格式（逗号分隔，每个节点均为完整 IRI）：
              两段：-p <对象属性IRI>,<数据属性IRI>
              三段：-p <对象属性IRI>,<工序个体IRI>,<数据属性IRI>
            --debug  输出详细调试信息
            """);
    }
}