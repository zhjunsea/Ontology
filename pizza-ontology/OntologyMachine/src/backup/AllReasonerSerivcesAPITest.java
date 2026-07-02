package org.junzhang.ontologymachine;

import org.semanticweb.owlapi.model.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.*;
import java.util.stream.Collectors;

public class AllReasonerSerivcesAPITest implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(HermiTReasonerApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 解析参数：主本体路径、映射目录、--debug 标志
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
            batchAPITest(service); // 可按需启用
        }
    }

    private static void batchAPITest(ReasonerService service) {
        try {
            // 1. 测试一致性检查
            if (!service.isConsistent()) {
                System.out.println("✗ 本体不一致");
                return;
            }
            System.out.println("✓ 本体一致（OWL 2 DL）");
            // 2. 测试打印所有推导类型
            System.out.println("\n--- 测试 printAllInferredTypes ---");
            service.printAllInferredTypes();
            // 动态获取本体中的任意一个类和个体用于后续测试
            Optional<OWLClass> optClass = service.getReasoner().getRootOntology().classesInSignature().findFirst();
            Optional<OWLNamedIndividual> optInd = service.getReasoner().getRootOntology().individualsInSignature().findFirst();
            // 3. 测试类的相关接口
            if (optClass.isPresent()) {
                OWLClass cls = optClass.get();
                String classIRI = cls.getIRI().getIRIString();
                System.out.println("\n--- 测试类的相关接口，使用类: " + classIRI + " ---");
                // 3.1 测试 getInstances
                System.out.println("\n[测试 getInstances] 类的实例:");
                Set<OWLNamedIndividual> instances = service.getInstances(classIRI);
                if (instances.isEmpty()) {
                    System.out.println("  (无实例)");
                } else {
                    instances.forEach(ind -> System.out.println("  - " + ind.getIRI().getIRIString()));
                }
                // 3.2 测试 getAllPropertiesOfClass
                System.out.println("\n[测试 getAllPropertiesOfClass] 类的所有属性:");
                Set<OWLProperty> allProps = service.getAllPropertiesOfClass(classIRI);
                if (allProps.isEmpty()) {
                    System.out.println("  (无属性)");
                } else {
                    allProps.forEach(p -> System.out.println("  - " + p.getIRI().getIRIString()));
                }
                // 3.3 测试 getSpecifiedPropertiesOfClass
                if (!allProps.isEmpty()) {
                    String propIRI = allProps.iterator().next().getIRI().getIRIString();
                    System.out.println("\n[测试 getSpecifiedPropertiesOfClass] 指定属性组 (包含: " + propIRI + "):");
                    Set<OWLProperty> specifiedProps = service.getSpecifiedPropertiesOfClass(classIRI, Collections.singleton(propIRI));
                    specifiedProps.forEach(p -> System.out.println("  - " + p.getIRI().getIRIString()));
                } else {
                    System.out.println("\n[测试 getSpecifiedPropertiesOfClass] 跳过，因为该类无属性。");
                }
            } else {
                System.out.println("\n本体中未找到类，跳过类相关测试。");
            }
            // 4. 测试个体的相关接口
            if (optInd.isPresent()) {
                OWLNamedIndividual ind = optInd.get();
                String indIRI = ind.getIRI().getIRIString();
                System.out.println("\n--- 测试个体的相关接口，使用个体: " + indIRI + " ---");
                // 4.1 测试 getTypes
                System.out.println("\n[测试 getTypes] 个体的类型:");
                service.getTypes(indIRI).forEach(c -> System.out.println("  - " + c.getIRI().getIRIString()));
                // 4.2 测试 getAllPropertiesOfIndividual
                System.out.println("\n[测试 getAllPropertiesOfIndividual] 个体的所有属性及值:");
                Map<String, Set<String>> allIndProps = service.getAllPropertiesOfIndividual(indIRI);
                if (allIndProps.isEmpty()) {
                    System.out.println("  (无属性)");
                } else {
                    allIndProps.forEach((pIRI, values) -> {
                        System.out.println("  属性: " + pIRI);
                        values.forEach(v -> System.out.println("    └─ 值: " + v));
                    });
                }
                // 4.3 测试 getSpecifiedPropertiesOfIndividual
                if (!allIndProps.isEmpty()) {
                    String propIRI = allIndProps.keySet().iterator().next();
                    System.out.println("\n[测试 getSpecifiedPropertiesOfIndividual] 指定属性组 (包含: " + propIRI + "):");
                    Map<String, Set<String>> specifiedIndProps = service.getSpecifiedPropertiesOfIndividual(indIRI, Collections.singleton(propIRI));
                    specifiedIndProps.forEach((pIRI, values) -> {
                        System.out.println("  属性: " + pIRI);
                        values.forEach(v -> System.out.println("    └─ 值: " + v));
                    });
                } else {
                    System.out.println("\n[测试 getSpecifiedPropertiesOfIndividual] 跳过，因为该个体无属性。");
                }
            } else {
                System.out.println("\n本体中未找到个体，跳过个体相关测试。");
            }
        } catch (Exception e) {
            System.err.println("执行测试时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}