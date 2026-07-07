package org.junzhang;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.*;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class HermiTExternalReasonerDemo implements CommandLineRunner {

    private static final String NS = "http://example.org/test#";

    public static void main(String[] args) {
        SpringApplication.run(HermiTExternalReasonerDemo.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. 使用 Jena 6.1 新本体 API 构建示例本体
        OntModel jenaModel = OntModelFactory.createModel();
        jenaModel.setNsPrefix("test", NS);
        buildExampleOntology(jenaModel);

        // 2. 将 Jena 本体序列化为 RDF/XML
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        jenaModel.write(out, "RDF/XML");
        String ontologyXml = out.toString(StandardCharsets.UTF_8);

        // 3. 通过 OWL API 加载本体
        OWLOntologyManager owlManager = OWLManager.createOWLOntologyManager();
        OWLOntology owlOntology = owlManager.loadOntologyFromOntologyDocument(
                new ByteArrayInputStream(ontologyXml.getBytes(StandardCharsets.UTF_8)));

        // 4. 外部 HermiT 推理
        OWLReasonerFactory reasonerFactory = new ReasonerFactory();
        OWLReasoner reasoner = reasonerFactory.createReasoner(owlOntology);
        reasoner.precomputeInferences(InferenceType.CLASS_ASSERTIONS);

        // 5. 输出推理结果（已消除所有过时方法）
        System.out.println("=== 推理后的类型断言 ===");
        owlOntology.individualsInSignature().forEach(ind -> {
            System.out.println("个体: " + ind.getIRI().getShortForm());
            reasoner.getTypes(ind, true).entities()
                    .filter(cls -> !cls.isTopEntity())
                    .forEach(cls -> System.out.println("  - 类型: " + cls.getIRI().getShortForm()));
        });

        reasoner.dispose();
    }

    private void buildExampleOntology(OntModel m) {
        OntClass Person = m.createOntClass(NS + "Person");
        OntClass Parent = m.createOntClass(NS + "Parent");
        OntClass Mother = m.createOntClass(NS + "Mother");
        OntObjectProperty hasChild = m.createObjectProperty(NS + "hasChild");

        Parent.addEquivalentClass(
                m.createObjectSomeValuesFrom(hasChild, Person)
        );
        Mother.addSuperClass(Parent);
        Mother.addSuperClass(Person);

        OntIndividual alice = Mother.createIndividual(NS + "Alice");
        OntIndividual bob = Person.createIndividual(NS + "Bob");

        m.add(alice, m.getProperty(NS + "hasChild"), bob);
    }
}