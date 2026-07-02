package org.junzhang.ontologymachine;

import org.apache.jena.ontapi.OntModelFactory;
import org.apache.jena.ontapi.model.OntModel;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.OWL;
import org.semanticweb.HermiT.ReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.formats.PrefixDocumentFormat;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.semanticweb.owlapi.util.SimpleIRIMapper;
import org.semanticweb.owlapi.vocab.PrefixOWLOntologyFormat;

import java.io.*;
import java.net.URI;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReasonerService implements AutoCloseable {

    private final OWLOntology ontology;
    private final OWLReasoner reasoner;
    private static boolean debug = false;

    public ReasonerService(String mainOntologyPath, String mappingDirs) throws Exception {
        /*
        File mainFile = new File(mainOntologyPath);

        // 确定扫描目录
        String[] dirs;
        if (mappingDirs != null && !mappingDirs.trim().isEmpty()) {
            dirs = mappingDirs.split(",");
        } else {
            dirs = new String[]{mainFile.getParent()};
        }

        // 获取全局文档管理器并添加 file:/// 映射
        org.apache.jena.ontology.OntDocumentManager docMgr =
                org.apache.jena.ontology.OntDocumentManager.getInstance();
        for (String dirPath : dirs) {
            File dir = new File(dirPath.trim());
            if (dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) ->
                        name.endsWith(".owl") || name.endsWith(".ttl") || name.endsWith(".rdf") ||
                                name.endsWith(".n3") || name.endsWith(".nt"));
                if (files != null) {
                    for (File f : files) {
                        String fileURI = "file:///" + f.getAbsolutePath().replace('\\', '/');
                        docMgr.addAltEntry(fileURI, f.getAbsolutePath());
                    }
                }
            }
        }*/

        // 创建 Jena 模型（自动使用上面的全局文档管理器）
        //OntModel jenaModel = OntModelFactory.createModel();
        OntModel jenaModel = loadOntology(mainOntologyPath);
        /*
        try (FileInputStream in = new FileInputStream(mainFile)) {
            // Jena 会递归加载所有 owl:imports，并根据 docMgr 找到本地文件
            jenaModel.read(in, mainFile.toURI().toString(), "RDF/XML");
        }
        */
        // 将合并后的完整本体序列化为 RDF/XML
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        jenaModel.write(out, "RDF/XML");
        byte[] mergedBytes = out.toByteArray();

        // OWL API 加载合并后的本体（无外部导入）
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        this.ontology = manager.loadOntologyFromOntologyDocument(
                new java.io.ByteArrayInputStream(mergedBytes));
        System.out.println("本体加载完成: " + ontology.getOntologyID());

        // 创建 HermiT 推理机
        OWLReasonerFactory factory = new ReasonerFactory();
        this.reasoner = factory.createReasoner(ontology);
        this.reasoner.precomputeInferences(
                InferenceType.CLASS_HIERARCHY,
                InferenceType.CLASS_ASSERTIONS,
                InferenceType.OBJECT_PROPERTY_HIERARCHY,
                InferenceType.DATA_PROPERTY_HIERARCHY,
                InferenceType.OBJECT_PROPERTY_ASSERTIONS,
                InferenceType.DATA_PROPERTY_ASSERTIONS);
    }
    private OntModel loadOntology(String mainFile) throws IOException {
        OntModel model = OntModelFactory.createModel();
        loadRecursive(model, mainFile, new HashSet<>());
        return model;
    }

    private void loadRecursive(Model model, String filePath, Set<String> loaded) throws IOException {
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

    // ================= 推理 API =================
    public boolean isConsistent() { return reasoner.isConsistent(); }

    public Set<OWLClass> getTypes(String individualIRI) {
        OWLNamedIndividual ind = getIndividual(individualIRI);
        return reasoner.getTypes(ind, true).entities()
                .filter(cls -> !cls.isOWLThing())
                .collect(Collectors.toSet());
    }

     /**
     * 将可能被缩写的前缀 IRI 转换为完整 IRI。
     */
     public IRI resolveFullIRI(OWLOntology ontology, IRI iri) {
        String iriString = iri.getIRIString();

        // 如果不是标准协议开头且包含冒号，可能是前缀缩写
        if (!iriString.startsWith("http://") && !iriString.startsWith("https://") &&
                !iriString.startsWith("file:/") && !iriString.startsWith("urn:") &&
                iriString.contains(":")) {

            OWLOntologyManager manager = ontology.getOWLOntologyManager();
            OWLDocumentFormat format = manager.getOntologyFormat(ontology);  // 返回 OWLDocumentFormat

            if (format instanceof PrefixOWLOntologyFormat) {
                PrefixOWLOntologyFormat prefixFormat = (PrefixOWLOntologyFormat) format;
                String prefixName = iriString.substring(0, iriString.indexOf(":") + 1);
                String localName = iriString.substring(iriString.indexOf(":") + 1);
                String namespace = prefixFormat.getPrefix(prefixName);
                if (namespace != null) {
                    return IRI.create(namespace + localName);
                }
            }

            // 兜底硬编码映射
            if (iriString.startsWith("proc:")) {
                return IRI.create("http://example.org/pizza/process/" + iriString.substring(5));
            }
            if (iriString.startsWith("pizza:")) {
                return IRI.create("http://example.org/pizza/" + iriString.substring(6));
            }
        }
        return iri;
    }

    /**
     * 使用 OWL API 的 EntitySearcher 获取指定 IRI 的标签。
     */
    public String getLabel(OWLOntology ontology, IRI iri, String lang) {
        OWLDataFactory df = ontology.getOWLOntologyManager().getOWLDataFactory();
        OWLAnnotationProperty rdfsLabel = df.getRDFSLabel();
        List<OWLLiteral> labels = new ArrayList<>();
        // 1. 优先尝试通过实体签名获取注解（兼容性更好，能覆盖导入闭包）
        ontology.getEntitiesInSignature(iri).forEach(entity -> {
            try (Stream<OWLAnnotationAssertionAxiom> stream = EntitySearcher.getAnnotationAssertionAxioms(entity, ontology)) {
                stream.filter(ax -> ax.getProperty().equals(rdfsLabel))
                        .map(OWLAnnotationAssertionAxiom::getValue)
                        .filter(val -> val instanceof OWLLiteral)
                        .map(val -> (OWLLiteral) val)
                        .forEach(labels::add);
            }
        });
        // 2. 兜底：直接通过 IRI 获取注解断言
        if (labels.isEmpty()) {
            ontology.annotationAssertionAxioms(iri)
                    .filter(ax -> ax.getProperty().equals(rdfsLabel))
                    .map(OWLAnnotationAssertionAxiom::getValue)
                    .filter(val -> val instanceof OWLLiteral)
                    .map(val -> (OWLLiteral) val)
                    .forEach(labels::add);
        }
        // 调试排查：如果此时 labels 仍为空，说明本体中根本没有匹配上该 IRI
        if (labels.isEmpty()) {
            System.out.println("【调试】未找到 IRI: " + iri + " 的标签。");
            System.out.println("【调试】本体签名中是否包含此实体? " + ontology.containsEntityInSignature(iri));
            // 如果上一行打印 false，说明传入的 IRI 和本体实际 IRI 不一致

            return null;
        }
        // 3. 优先匹配指定语言的 label
        if (lang != null && !lang.trim().isEmpty()) {
            Optional<String> langLabel = labels.stream()
                    .filter(lit -> lit.hasLang(lang))
                    .map(OWLLiteral::getLiteral)
                    .findFirst();
            if (langLabel.isPresent()) {
                return langLabel.get();
            }
        }
        // 4. 如果未找到指定语言，则匹配无语言标签的 label
        Optional<String> noLangLabel = labels.stream()
                .filter(lit -> !lit.hasLang())
                .map(OWLLiteral::getLiteral)
                .findFirst();
        if (noLangLabel.isPresent()) {
            return noLangLabel.get();
        }
        // 5. 最终兜底：返回该 IRI 的任意一个 label（忽略语言）
        return labels.stream()
                .map(OWLLiteral::getLiteral)
                .findFirst()
                .orElse(null);
    }

    public Set<OWLNamedIndividual> getInstances(String classIRI) {
        OWLClass cls = getClass(classIRI);
        return reasoner.getInstances(cls, true).entities().collect(Collectors.toSet());
    }

    public void printAllInferredTypes() {
        System.out.println("===== 推理后的类型断言 =====");
        ontology.individualsInSignature().forEach(ind -> {
            System.out.println("个体: " + ind.getIRI().getIRIString());
            reasoner.getTypes(ind, true).entities()
                    .filter(cls -> !cls.isOWLThing())
                    .forEach(cls -> System.out.println("  └─ 类型: " + cls.getIRI().getIRIString()));
        });
    }

    public OWLReasoner getReasoner() { return reasoner; }

    // ================= 属性查询 API =================
    public Set<OWLProperty> getSpecifiedPropertiesOfClass(String classIRI, Set<String> propertyIRIs) {
        OWLClass cls = getClass(classIRI);
        Set<OWLProperty> result = new HashSet<>();
        for (String iri : propertyIRIs) {
            ontology.objectPropertiesInSignature()
                    .filter(p -> p.getIRI().getIRIString().equals(iri))
                    .findFirst()
                    .ifPresent(result::add);

            ontology.dataPropertiesInSignature()
                    .filter(p -> p.getIRI().getIRIString().equals(iri))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }

    public Set<OWLProperty> getAllPropertiesOfClass(String classIRI) {
        OWLClass cls = getClass(classIRI);
        Set<OWLProperty> result = new HashSet<>();
        Set<OWLClass> relevantClasses = reasoner.getSuperClasses(cls, false).entities().collect(Collectors.toSet());
        relevantClasses.add(cls);

        ontology.objectPropertiesInSignature().forEach(prop -> {
            reasoner.getObjectPropertyDomains(prop, true).entities().forEach(domain -> {
                if (relevantClasses.contains(domain)) {
                    result.add(prop);
                }
            });
        });

        ontology.dataPropertiesInSignature().forEach(prop -> {
            reasoner.getDataPropertyDomains(prop, true).entities().forEach(domain -> {
                if (relevantClasses.contains(domain)) {
                    result.add(prop);
                }
            });
        });

        return result;
    }

    public Map<String, Set<String>> getSpecifiedPropertiesOfIndividual(String individualIRI, Set<String> propertyIRIs) {
        OWLNamedIndividual ind = getIndividual(individualIRI);
        Map<String, Set<String>> result = new HashMap<>();

        for (String iri : propertyIRIs) {
            Optional<OWLDataProperty> dataProp = ontology.dataPropertiesInSignature()
                    .filter(p -> p.getIRI().getIRIString().equals(iri))
                    .findFirst();
            if (dataProp.isPresent()) {
                Set<OWLLiteral> values = reasoner.getDataPropertyValues(ind, dataProp.get());
                if (!values.isEmpty()) {
                    result.put(iri, values.stream().map(OWLLiteral::getLiteral).collect(Collectors.toSet()));
                }
                continue;
            }

            Optional<OWLObjectProperty> objProp = ontology.objectPropertiesInSignature()
                    .filter(p -> p.getIRI().getIRIString().equals(iri))
                    .findFirst();
            if (objProp.isPresent()) {
                Set<OWLNamedIndividual> values = reasoner.getObjectPropertyValues(ind, objProp.get()).entities().collect(Collectors.toSet());
                if (!values.isEmpty()) {
                    result.put(iri, values.stream().map(i -> i.getIRI().getIRIString()).collect(Collectors.toSet()));
                }
            }
        }
        return result;
    }

    public Map<String, Set<String>> getAllPropertiesOfIndividual(String individualIRI) {
        OWLNamedIndividual ind = getIndividual(individualIRI);
        Map<String, Set<String>> result = new HashMap<>();

        ontology.dataPropertiesInSignature().forEach(prop -> {
            Set<OWLLiteral> values = reasoner.getDataPropertyValues(ind, prop);
            if (!values.isEmpty()) {
                Set<String> valueStrings = values.stream().map(OWLLiteral::getLiteral).collect(Collectors.toSet());
                result.put(prop.getIRI().getIRIString(), valueStrings);
            }
        });

        ontology.objectPropertiesInSignature().forEach(prop -> {
            Set<OWLNamedIndividual> values = reasoner.getObjectPropertyValues(ind, prop).entities().collect(Collectors.toSet());
            if (!values.isEmpty()) {
                Set<String> valueStrings = values.stream().map(i -> i.getIRI().getIRIString()).collect(Collectors.toSet());
                result.put(prop.getIRI().getIRIString(), valueStrings);
            }
        });

        return result;
    }

    private OWLNamedIndividual getIndividual(String individualIRI) {
        return ontology.individualsInSignature()
                .filter(i -> i.getIRI().getIRIString().equals(individualIRI))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("个体未找到: " + individualIRI));
    }

    private OWLClass getClass(String classIRI) {
        return ontology.classesInSignature()
                .filter(c -> c.getIRI().getIRIString().equals(classIRI))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("类未找到: " + classIRI));
    }

    @Override
    public void close() { reasoner.dispose(); }
}