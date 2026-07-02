import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileManager;
import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import java.io.File;
import java.util.*;

public class LabelSearch {

    public static void main(String[] args) {
        String ontologyPath = null;
        String iri = null;
        String wordsStr = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-o":
                    if (i + 1 < args.length) ontologyPath = args[++i];
                    break;
                case "-p":   // 参数由 -iri 改为 -p
                    if (i + 1 < args.length) iri = args[++i];
                    break;
                case "-words":
                    if (i + 1 < args.length) wordsStr = args[++i];
                    break;
                default:
                    System.err.println("未知选项: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        if (ontologyPath == null || iri == null || wordsStr == null) {
            System.err.println("缺少必要参数。");
            printUsage();
            System.exit(1);
        }

        List<String> targetWords = parseWordList(wordsStr);
        if (targetWords.isEmpty()) {
            System.err.println("词列表为空，请提供至少一个词。");
            System.exit(1);
        }

        try {
            // 1. 设置文件定位器，以便加载导入的本体
            File ontologyFile = new File(ontologyPath);
            String parentDir = ontologyFile.getParent();
            if (parentDir != null && !parentDir.isEmpty()) {
                FileManager.get().addLocatorFile(parentDir);
                //System.out.println("📂 添加文件定位器: " + parentDir);
            } else {
                FileManager.get().addLocatorFile(".");
            }

            // 2. 加载主本体
            OntModel model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
            model.read(ontologyPath);
            //System.out.println("✅ 本体加载成功，包含导入文件");

            // 3. 获取资源
            Resource resource = model.getResource(iri);
            if (resource == null) {
                System.out.println();
                return;
            }

            // 4. 收集要检查的实体（自身 + 所有父类/父属性）
            Set<Resource> entities = new HashSet<>();
            entities.add(resource);

            // 处理类
            if (resource.canAs(OntClass.class)) {
                OntClass ontClass = resource.as(OntClass.class);
                ExtendedIterator<? extends OntClass> superClasses = ontClass.listSuperClasses(false);
                while (superClasses.hasNext()) {
                    OntClass sup = superClasses.next();
                    if (sup != null && sup.canAs(OntClass.class)) {
                        entities.add(sup);
                    }
                }
            }

            // 处理属性
            if (resource.canAs(OntProperty.class)) {
                OntProperty prop = resource.as(OntProperty.class);
                ExtendedIterator<? extends OntProperty> superProps = prop.listSuperProperties(false);
                while (superProps.hasNext()) {
                    OntProperty sup = superProps.next();
                    if (sup != null && sup.canAs(OntProperty.class)) {
                        entities.add(sup);
                    }
                }
            }

            // 处理个体：获取类型及其父类（使用 RDF.type 避免自动转换异常）
            if (resource.canAs(Individual.class)) {
                Individual ind = resource.as(Individual.class);
                // 获取所有 rdf:type 值
                ExtendedIterator<RDFNode> typeNodes = ind.listPropertyValues(RDF.type);
                while (typeNodes.hasNext()) {
                    RDFNode node = typeNodes.next();
                    if (node.isResource()) {
                        Resource typeRes = node.asResource();
                        // 尝试转换为 OntClass
                        if (typeRes.canAs(OntClass.class)) {
                            OntClass type = typeRes.as(OntClass.class);
                            entities.add(type);
                            // 获取类型的父类
                            ExtendedIterator<? extends OntClass> superTypes = type.listSuperClasses(false);
                            while (superTypes.hasNext()) {
                                OntClass superType = superTypes.next();
                                if (superType != null && superType.canAs(OntClass.class)) {
                                    entities.add(superType);
                                }
                            }
                        }
                    }
                }
            }

            // 5. 收集所有标注
            List<String> labels = new ArrayList<>();
            for (Resource ent : entities) {
                // rdfs:label
                ExtendedIterator<RDFNode> labelIter = ent.listProperties(RDFS.label).mapWith(Statement::getObject);
                while (labelIter.hasNext()) {
                    RDFNode node = labelIter.next();
                    if (node.isLiteral()) {
                        labels.add(node.asLiteral().getString());
                    }
                }

                // skos:altLabel
                ExtendedIterator<RDFNode> altLabelIter = ent.listProperties(SKOS.altLabel).mapWith(Statement::getObject);
                while (altLabelIter.hasNext()) {
                    RDFNode node = altLabelIter.next();
                    if (node.isLiteral()) {
                        labels.add(node.asLiteral().getString());
                    }
                }

                // 本地名称
                String localName = ent.getLocalName();
                if (localName != null && !localName.isEmpty()) {
                    labels.add(localName);
                }
            }

            // 6. 匹配目标词
            String matchedWord = null;
            for (String label : labels) {
                if (label == null) continue;
                String lowerLabel = label.toLowerCase();
                for (String word : targetWords) {
                    if (lowerLabel.contains(word.toLowerCase())) {
                        matchedWord = word;
                        break;
                    }
                }
                if (matchedWord != null) break;
            }

            // 7. 输出结果
            if (matchedWord != null) {
                System.out.println(matchedWord);
            } else {
                System.out.println();
            }

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<String> parseWordList(String wordsStr) {
        String content = wordsStr.trim();
        if (content.startsWith("[") && content.endsWith("]")) {
            content = content.substring(1, content.length() - 1);
        }
        String[] parts = content.split(",");
        List<String> words = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) {
                words.add(trimmed);
            }
        }
        return words;
    }

    private static void printUsage() {
        System.err.println("用法: java LabelSearch -o <本体路径> -p <IRI> -words \"[词1, 词2]\"");
        System.err.println("示例: java LabelSearch -o myPizza.owl -p http://example.org/pizza/NeapolitanPizza -words \"[番茄, 奶酪]\"");
    }
}