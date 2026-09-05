package org.junzhang.ontologymachine;

import org.semanticweb.owlapi.model.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;
import java.util.Set;

@SpringBootApplication
public class HermiTResolver implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(HermiTResolver.class, args);
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

        try (ReasonerService service = ReasonerService.getInstance(mainPath)) {
            // 查询 NeapolitanPizza 在“面团原料准备与揉制”工序的 duration
            String chainJson = "[\"http://example.org/pizza/classes/NeapolitanPizza\", " +
                    "\"http://example.org/pizza/classes/hasProcessStep\", " +
                    "\"http://example.org/pizza/classes/process/duration\"]";
            //String duration = service.queryPropertyChain(chainJson,debug);
            OWLClass neapolitan = service.getClass("http://example.org/pizza/classes/NeapolitanPizza");

            //**********************************************
            //测试打印一个类的所有的对象属性，包括其父类的属性（含逆属性）
            Set<OWLObjectPropertyExpression> props = service.getAllObjectPropertiesOfClass(neapolitan);
            props.forEach(p -> {
                String name = p.getNamedProperty().getIRI().getShortForm();
                if (p.isAnonymous()) {
                    // isAnonymous() 为 true，表示这是一个逆属性表达式
                    System.out.println("逆属性: inverseOf(" + name + ")");
                } else {
                    System.out.println("正向属性: " + name);
                }
            });

            //测试打印一个类的指定的对象属性及其domain，range
            OWLObjectPropertyExpression prop = service.getObjectPropertyOfClass(neapolitan, "http://example.org/pizza/classes/hasCrust");
            if (prop != null) {
                System.out.println("找到指定的对象属性: " + prop.getNamedProperty().getIRI().getShortForm());
                // 如果 prop 是逆属性，可额外判断
                if (prop.isAnonymous()) {
                    System.out.println("（这是一个逆属性表达式）");
                }
            } else {
                System.out.println("该类中没有此属性");
            }
            Set<OWLClassExpression> domains = service.getObjectPropertyDomain(prop);
            domains.forEach(domain -> System.out.println("Domain: " + domain));
            Set<OWLClassExpression> ranges = service.getObjectPropertyRange(prop);
            ranges.forEach(range -> System.out.println("Range: " + range));

            //打印某对象属性的类约束（包括从父类继承来的）
            Set<OWLClassExpression> limitations = service.getObjectPropertyLimitations(neapolitan, prop);
            for (OWLClassExpression expr : limitations) {
                if (expr instanceof OWLObjectSomeValuesFrom) {
                    OWLObjectSomeValuesFrom some = (OWLObjectSomeValuesFrom) expr;
                    System.out.println("someValuesFrom: " + some.getFiller());
                } else if (expr instanceof OWLObjectAllValuesFrom) {
                    OWLObjectAllValuesFrom all = (OWLObjectAllValuesFrom) expr;
                    System.out.println("allValuesFrom: " + all.getFiller());
                } else if (expr instanceof OWLObjectHasValue) {
                    OWLObjectHasValue hasVal = (OWLObjectHasValue) expr;
                    System.out.println("hasValue: " + hasVal.getFiller());
                }else{
                    System.out.println("No limitation is found!");
                }
            }

            //打印注释属性
            /*
            Map<OWLAnnotationProperty, Set<OWLLiteral>> annoProps = service.getAnnotationsOfClass(neapolitan);
            // 检查是否为空
            if (annoProps.isEmpty()) {
                System.out.println("该类没有任何注释属性。");
            } else {
                // 遍历每个注释属性
                for (Map.Entry<OWLAnnotationProperty, Set<OWLLiteral>> entry : annoProps.entrySet()) {
                    OWLAnnotationProperty property = entry.getKey();
                    service.getAnnotationValueOfClass(neapolitan,property);
                }
            }*/
            //获取类的全部注释属性
            Map<OWLAnnotationProperty, Set<OWLLiteral>> classAnno = service.getAnnotations(neapolitan);
            // 获取实例的全部注释属性
            OWLNamedIndividual pizzaInd = service.getOntology().getOWLOntologyManager().getOWLDataFactory().getOWLNamedIndividual(IRI.create("http://example.org/pizza/individuals/neapolitanPizzaInstance"));
            Map<OWLAnnotationProperty, Set<OWLLiteral>> indAnno = service.getAnnotations(pizzaInd);
            //获取类的指定注释属性
            Set<OWLLiteral> labels = service.getAnnotationValue(neapolitan, "http://www.w3.org/2000/01/rdf-schema#label");
            labels.forEach(lit -> {
                System.out.println("Label: " + lit.getLiteral() + " (语言: " + lit.getLang() + ")");
            });
            // 获取个体的 rdfs:comment
            Set<OWLLiteral> comments = service.getAnnotationValue(pizzaInd, "http://www.w3.org/2000/01/rdf-schema#comment");
            comments.forEach(lit -> {
                String value = lit.getLiteral();
                String lang = lit.getLang();
                if (lang != null && !lang.isEmpty()) {
                    System.out.println("注释值: " + value + " (语言: " + lang + ")");
                } else {
                    System.out.println("注释值: " + value);
                }
            });

            //**********************************************

            //**********************************************
            //测试打印个体的类型
            //OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/classes/neapolitanPizzaInstance");
            OWLNamedIndividual ind = service.getIndividual("http://example.org/pizza/individuals/neapolitanPizzaInstance");
            Set<OWLClass> types = service.getIndividualDirectTypes(ind);
            System.out.println("Individual Direct types: ");
            for (OWLClass cls : types) {
                System.out.println(cls.getIRI().getShortForm());
            }
            types = service.getIndividualAllTypes(ind);
            System.out.println("Individual All types: ");
            for (OWLClass cls : types) {
                System.out.println(cls.getIRI().getShortForm());
            }
            //测试打印个体的所有对象属性
            props = service.getObjectPropertiesOfIndividual(ind);
            props.forEach(p -> {
                String name = p.getNamedProperty().getIRI().getShortForm(); // 使用 p
                if (p.isAnonymous()) {                                     // 使用 p
                    System.out.println("逆属性: inverseOf(" + name + ")");
                } else {
                    System.out.println("正向属性: " + name + ". 其range是：" + service.getObjectPropertyRange(p));
                }
                Set<OWLNamedIndividual> objects = service.getObjectPropertyDirectValueOfIndividual(ind, p);
                objects.forEach(obj -> System.out.println("打印直接属性的值（宾语）是: " + obj.getIRI().getShortForm()));

                objects = service.getObjectPropertyAllValueOfIndividual(ind, p);
                objects.forEach(obj -> System.out.println("打印全部属性的值（宾语）是: " + obj.getIRI().getShortForm()));
            });

            //打印所有的数据属性
            Set<OWLDataProperty> dataProps = service.getDirectDataPropertiesOfIndividual(ind);
            dataProps.forEach(dp -> System.out.println("所有的直接数据属性是："+dp.getIRI().getShortForm()));

            dataProps = service.getAllAllowedDataPropertiesOfIndividual(ind);
            dataProps.forEach(dp -> {
                System.out.println("所有的数据属性（包括推理）是: " + dp.getIRI().getShortForm());
                //打印指定数据属性的值
                Set<OWLLiteral> literals = service.getDataPropertyValueOfIndividual(ind, dp);
                literals.forEach(lit -> {
                    System.out.println("值: " + lit.getLiteral() + " (类型: " + lit.getDatatype() + ")");
                });
            });
            //打印所有注释属性

            //IRI链式查询测试
            //chainJson = "[\"http://example.org/pizza/classes/neapolitanPizzaInstance\", \"http://example.org/pizza/classes/hasCrust\", \"http://example.org/pizza/classes/neapolitanCrustInstance\", \"http://example.org/pizza/classes/supplier\"]";
            //String result = service.queryIndividualPropertyChain(chainJson, true);
            //System.out.println("查询结果: " + result);

        } catch (Exception e) {
            System.err.println("执行错误: " + e.getMessage());
            if (debug) e.printStackTrace();
        }
    }
}