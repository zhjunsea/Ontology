import org.apache.jena.ontology.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.util.FileManager;

public class JenaSWRLValidator {
    private static final String SWRL_NS = "http://www.w3.org/2003/11/swrl#";
    private static final String SWRL_IMP = SWRL_NS + "Imp";
    private static final String SWRL_BODY = SWRL_NS + "body";
    private static final String SWRL_HEAD = SWRL_NS + "head";

    public static void main(String[] args) {
        String mainOwlPath = "D:/work/Ontology/pizza-ontology/pizza-components.owl";

        try {
            OntModelSpec spec = new OntModelSpec(OntModelSpec.OWL_DL_MEM);
            OntDocumentManager ontDocMgr = spec.getDocumentManager();
            ontDocMgr.setProcessImports(true);
            FileManager fileMgr = ontDocMgr.getFileManager();
            fileMgr.addLocatorFile("D:/work/Ontology/pizza-ontology/");

            OntModel ontModel = ModelFactory.createOntologyModel(spec, null);
            ontModel.read(mainOwlPath);

            System.out.println("===== 模型加载完成，总语句数：" + ontModel.size() + " =====");

            // 1. 通过 swrl:body 属性查找所有规则
            Property bodyProp = ontModel.getProperty(SWRL_BODY);
            ResIterator rulesViaBody = ontModel.listSubjectsWithProperty(bodyProp);
            int ruleCount = 0;
            while (rulesViaBody.hasNext()) {
                ruleCount++;
                Resource rule = rulesViaBody.nextResource();
                printRuleDetails(ontModel, rule, ruleCount);
            }

            // 2. 如果 swrl:body 找不到，尝试 swrl:head
            if (ruleCount == 0) {
                Property headProp = ontModel.getProperty(SWRL_HEAD);
                ResIterator rulesViaHead = ontModel.listSubjectsWithProperty(headProp);
                while (rulesViaHead.hasNext()) {
                    ruleCount++;
                    Resource rule = rulesViaHead.nextResource();
                    printRuleDetails(ontModel, rule, ruleCount);
                }
            }

            // 3. 如果仍然没找到，遍历所有语句，找出 swrl 相关的内容
            if (ruleCount == 0) {
                System.out.println("通过 body/head 未找到规则，正在扫描所有包含 'swrl' 的语句...");
                StmtIterator iter = ontModel.listStatements();
                while (iter.hasNext()) {
                    Statement s = iter.next();
                    if (s.getPredicate().getNameSpace() != null && s.getPredicate().getNameSpace().contains("swrl")) {
                        System.out.println(s);
                    }
                }
            } else {
                System.out.println("✅ 共发现 " + ruleCount + " 条 SWRL 规则");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void printRuleDetails(Model model, Resource rule, int index) {
        System.out.println("\n===== 规则 " + index + " =====");
        System.out.println("规则 IRI: " + rule.getURI());

        // 输出 rdf:type
        if (rule.hasProperty(RDF.type)) {
            Resource type = rule.getProperty(RDF.type).getResource();
            System.out.println("  类型: " + type);
        }

        // 输出 body
        Property bodyProp = model.getProperty(SWRL_BODY);
        if (rule.hasProperty(bodyProp)) {
            System.out.println("  body 节点: " + rule.getProperty(bodyProp).getResource());
            // 进一步解析 body 内部的原子（通常 body 是一个列表）
            printListDetails(model, rule.getProperty(bodyProp).getResource(), "    ");
        }

        // 输出 head
        Property headProp = model.getProperty(SWRL_HEAD);
        if (rule.hasProperty(headProp)) {
            System.out.println("  head 节点: " + rule.getProperty(headProp).getResource());
            printListDetails(model, rule.getProperty(headProp).getResource(), "    ");
        }
    }

    private static void printListDetails(Model model, Resource listNode, String indent) {
        if (listNode == null) return;
        Property first = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#first");
        Property rest  = model.getProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#rest");
        Resource nil   = model.getResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#nil");

        while (listNode != null && !listNode.equals(nil)) {
            if (listNode.hasProperty(first)) {
                Resource item = listNode.getProperty(first).getResource();
                System.out.println(indent + "原子: " + item);
                // 打印该原子的所有属性
                StmtIterator props = item.listProperties();
                while (props.hasNext()) {
                    Statement s = props.next();
                    System.out.println(indent + "  " + s.getPredicate().getLocalName() + " -> " + s.getObject());
                }
            }
            if (listNode.hasProperty(rest)) {
                listNode = listNode.getProperty(rest).getResource();
            } else {
                break;
            }
        }
    }
}