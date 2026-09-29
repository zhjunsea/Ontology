package utilities;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.Set;

public class SWRLValidator {

    public static void main(String[] args) {
        String owlPath = "D:/work/Ontology/pizza-ontology/ontology/pizza-components-classes.owl";

        try {
            OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(owlPath));

            Set<SWRLRule> rules = ontology.getAxioms(AxiomType.SWRL_RULE);
            System.out.println("✅ 共发现 " + rules.size() + " 条 SWRL 规则\n");

            int validCount = 0;
            for (SWRLRule rule : rules) {
                try {
                    validateRule(rule);
                    validCount++;
                    System.out.println("✔ 规则合法: " + rule);
                } catch (Exception e) {
                    System.err.println("✘ 规则非法: " + rule);
                    System.err.println("  原因: " + e.getMessage());
                }
                System.out.println("---");
            }

            System.out.println("\n===== 验证结果 =====");
            System.out.println("合法规则: " + validCount + "/" + rules.size());

        } catch (OWLOntologyCreationException e) {
            System.err.println("本体加载失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void validateRule(SWRLRule rule) {
        // 1. body 和 head 不能同时为空
        if (rule.getBody().isEmpty() && rule.getHead().isEmpty()) {
            throw new IllegalArgumentException("SWRL 规则的 body 和 head 不能同时为空");
        }

        // 2. 验证所有原子的类型是否属于合法的 SWRL 原子类型
        for (SWRLAtom atom : rule.getBody()) {
            validateAtom(atom, "body");
        }
        for (SWRLAtom atom : rule.getHead()) {
            validateAtom(atom, "head");
        }

        // 3. 验证变量一致性：head 中出现的变量必须在 body 中出现过
        // （这是 SWRL 规范的安全条件）
        Set<SWRLVariable> bodyVars = new java.util.HashSet<>();
        for (SWRLAtom atom : rule.getBody()) {
            collectVariables(atom, bodyVars);
        }
        Set<SWRLVariable> headVars = new java.util.HashSet<>();
        for (SWRLAtom atom : rule.getHead()) {
            collectVariables(atom, headVars);
        }
        for (SWRLVariable hv : headVars) {
            if (!bodyVars.contains(hv)) {
                throw new IllegalArgumentException(
                        "Head 中的变量 " + hv + " 未在 Body 中绑定（违反 SWRL 安全条件）"
                );
            }
        }
    }

    private static void validateAtom(SWRLAtom atom, String position) {
        // OWLAPI 已将 RDF 反序列化为强类型 AST 对象
        // 如果解析失败，loadOntology 阶段就会抛异常
        // 这里可以做额外的业务校验，例如禁止某些 built-in
        if (atom instanceof SWRLBuiltInAtom) {
            IRI builtinIRI = ((SWRLBuiltInAtom) atom).getPredicate();
            System.out.println("    [" + position + "] BuiltIn: " + builtinIRI.getFragment());
        } else if (atom instanceof SWRLClassAtom) {
            System.out.println("    [" + position + "] ClassAtom: " +
                    ((SWRLClassAtom) atom).getPredicate());
        } else if (atom instanceof SWRLObjectPropertyAtom) {
            System.out.println("    [" + position + "] ObjectPropertyAtom: " +
                    ((SWRLObjectPropertyAtom) atom).getPredicate());
        } else if (atom instanceof SWRLDataPropertyAtom) {
            System.out.println("    [" + position + "] DataPropertyAtom: " +
                    ((SWRLDataPropertyAtom) atom).getPredicate());
        }
        // 其他原子类型同理...
    }

    private static void collectVariables(SWRLAtom atom, Set<SWRLVariable> vars) {
        if (atom instanceof SWRLClassAtom) {
            checkArgument(((SWRLClassAtom) atom).getArgument(), vars);

        } else if (atom instanceof SWRLObjectPropertyAtom) {
            checkArgument(((SWRLObjectPropertyAtom) atom).getFirstArgument(), vars);
            checkArgument(((SWRLObjectPropertyAtom) atom).getSecondArgument(), vars);

        } else if (atom instanceof SWRLDataPropertyAtom) {
            checkArgument(((SWRLDataPropertyAtom) atom).getFirstArgument(), vars);
            checkArgument(((SWRLDataPropertyAtom) atom).getSecondArgument(), vars);

        } else if (atom instanceof SWRLBuiltInAtom) {
            // BuiltIn 的参数是一个 List
            for (SWRLDArgument arg : ((SWRLBuiltInAtom) atom).getArguments()) {
                if (arg instanceof SWRLVariable) {
                    vars.add((SWRLVariable) arg);
                }
            }

        } else if (atom instanceof SWRLSameIndividualAtom) {
            checkArgument(((SWRLSameIndividualAtom) atom).getFirstArgument(), vars);
            checkArgument(((SWRLSameIndividualAtom) atom).getSecondArgument(), vars);

        } else if (atom instanceof SWRLDifferentIndividualsAtom) {
            checkArgument(((SWRLDifferentIndividualsAtom) atom).getFirstArgument(), vars);
            checkArgument(((SWRLDifferentIndividualsAtom) atom).getSecondArgument(), vars);

        } else if (atom instanceof SWRLDataRangeAtom) {
            checkArgument(((SWRLDataRangeAtom) atom).getArgument(), vars);
        }
        // 其他原子类型按需补充...
    }

    private static void checkArgument(SWRLArgument arg, Set<SWRLVariable> vars) {
        if (arg instanceof SWRLVariable) {
            vars.add((SWRLVariable) arg);
        }
    }
}