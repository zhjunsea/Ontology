package org.junzhang.ontologymachine;

import org.semanticweb.owlapi.model.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class HermiTReasonerApp implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(HermiTReasonerApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String mainPath = null;
        String mappingDirs = null;
        boolean debug = false;

        for (int i = 0; i < args.length; i++) {
            if ("--debug".equals(args[i])) {
                debug = true;
            } else if (mainPath == null) {
                mainPath = args[i];
            } else if (mappingDirs == null) {
                mappingDirs = args[i];
            }
        }

        if (mainPath == null) {
            System.out.println("用法: java -jar app.jar [--debug] <主本体文件路径> [映射目录1,映射目录2...]");
            return;
        }

        try (ReasonerService service = new ReasonerService(mainPath, mappingDirs)) {
            OWLOntology ontology = service.getReasoner().getRootOntology();

            IRI neapolitanIRI = IRI.create("http://example.org/pizza/NeapolitanPizza");
            IRI hasProcessStepIRI = IRI.create("http://example.org/pizza/hasProcessStep");
            if (debug) {
                System.out.println("【调试】本体内所有个体 IRI：");
                ontology.individualsInSignature().forEach(ind ->
                        System.out.println("    -> " + ind.getIRI().getIRIString()));
            }

            // 收集工序个体
            Set<OWLNamedIndividual> processSteps = new HashSet<>();
            for (OWLSubClassOfAxiom axiom : ontology.axioms(AxiomType.SUBCLASS_OF).collect(Collectors.toSet())) {
                OWLClassExpression subClass = axiom.getSubClass();
                if (subClass.isOWLClass() && subClass.asOWLClass().getIRI().equals(neapolitanIRI)) {
                    OWLClassExpression superClass = axiom.getSuperClass();
                    if (superClass instanceof OWLObjectHasValue) {
                        OWLObjectHasValue hasValue = (OWLObjectHasValue) superClass;
                        if (hasValue.getProperty().getNamedProperty().getIRI().equals(hasProcessStepIRI)) {
                            OWLIndividual filler = hasValue.getFiller();
                            if (filler.isNamed()) {
                                processSteps.add(filler.asOWLNamedIndividual());
                            }
                        }
                    }
                }
            }

            // 调试输出：使用 resolveFullIRI 确保完整IRI
            if (debug) {
                System.out.println("找到 " + processSteps.size() + " 个工序个体：");
                processSteps.forEach(ind -> {
                    IRI fullIRI = service.resolveFullIRI(ontology, ind.getIRI());
                    String label = service.getLabel(ontology, fullIRI, "zh");
                    System.out.println("  - " + fullIRI.getIRIString() + " (标签: " + label + ")");
                });
            }

            // 查找目标工序
            OWLNamedIndividual targetStep = null;
            for (OWLNamedIndividual ind : processSteps) {
                IRI fullIRI = service.resolveFullIRI(ontology, ind.getIRI());
                if ("面团原料准备与揉制".equals(service.getLabel(ontology, fullIRI, "zh"))) {
                    targetStep = ind;
                    break;
                }
            }

            if (targetStep == null) {
                System.out.println("未在 NeapolitanPizza 中找到“面团原料准备与揉制”工序。");
                return;
            }

            String stepIRI = service.resolveFullIRI(ontology, targetStep.getIRI()).getIRIString();
            System.out.println("找到工序: " + stepIRI);

            Map<String, Set<String>> allProps = service.getAllPropertiesOfIndividual(stepIRI);
            System.out.println("\n该工序的所有属性:");
            allProps.forEach((propIRI, values) -> System.out.println("  " + propIRI + " : " + values));

            String durationIRI = "http://example.org/pizza/process/duration";
            if (allProps.containsKey(durationIRI)) {
                System.out.println("\n操作时长: " + allProps.get(durationIRI));
            } else {
                System.out.println("\n未找到 duration 属性值。");
            }

        } catch (Exception e) {
            System.err.println("执行测试时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}