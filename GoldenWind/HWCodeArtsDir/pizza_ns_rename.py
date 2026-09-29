# -*- coding: utf-8 -*-
"""One-off migration: rename pizza namespace tokens + rewrite owl:imports.

Decisions (confirmed with user 2026-09-29):
  - namespace: keep trailing delimiter; collapse sub-token
      classes/ -> core/ ; individuals/ -> core-abox/
      components/classes/ -> components/ ; components/individuals/ -> components-abox/
      components/rules -> components-rules ; processes/classes/ -> processes/
      processes/individuals/ -> processes-abox/ ; all ; term/ (unchanged)
  - owl:imports: drop path, use ontology IRI (resolved via catalog/AutoIRIMapper)
"""
import os
import sys

ROOT = r"D:\work\Ontology\GoldenWind\OntologyMachine"

NS_REPL = [
    ("http://example.org/pizza/components/classes", "http://example.org/pizza/components"),
    ("http://example.org/pizza/components/individuals", "http://example.org/pizza/components-abox"),
    ("http://example.org/pizza/components/rules", "http://example.org/pizza/components-rules"),
    ("http://example.org/pizza/processes/classes", "http://example.org/pizza/processes"),
    ("http://example.org/pizza/processes/individuals", "http://example.org/pizza/processes-abox"),
    ("http://example.org/pizza/classes", "http://example.org/pizza/core"),
    ("http://example.org/pizza/individuals", "http://example.org/pizza/core-abox"),
]

IMPORT_REPL = [
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-classes-withoutInds.owl", "http://example.org/pizza/core"),
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-classes.owl", "http://example.org/pizza/core"),
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-components-classes-withoutRules.owl", "http://example.org/pizza/components"),
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-components-classes.owl", "http://example.org/pizza/components"),
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-components-individuals.owl", "http://example.org/pizza/components-abox"),
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-components-rules.ttl", "http://example.org/pizza/components-rules"),
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-individuals.owl", "http://example.org/pizza/core-abox"),
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-processes-classes.owl", "http://example.org/pizza/processes"),
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-processes-individuals.owl", "http://example.org/pizza/processes-abox"),
    ("file:///D:/work/Ontology/pizza-ontology/ontology/pizza-terminology.ttl", "http://example.org/pizza/term"),
]

FILES = [
    r"OntologyFrameworkExample/ontology/pizza-all-classes.owl",
    r"OntologyFrameworkExample/ontology/pizza-all.owl",
    r"OntologyFrameworkExample/ontology/pizza-classes-withoutInds.owl",
    r"OntologyFrameworkExample/ontology/pizza-components-classes.owl",
    r"OntologyFrameworkExample/ontology/pizza-components-individuals.owl",
    r"OntologyFrameworkExample/ontology/pizza-components-rules.owl",
    r"OntologyFrameworkExample/ontology/pizza-individuals.owl",
    r"OntologyFrameworkExample/ontology/pizza-processes-classes.owl",
    r"OntologyFrameworkExample/ontology/pizza-processes-individuals.owl",
    r"OntologyFrameworkExample/ontology/pizza-terminology.ttl",
    r"OntologyFrameworkExample/ontology/database/myPizza.obda",
    r"OntologyFrameworkExample/ontology/bpmn/测试输入数据.txt",
    r"OntologyFrameworkExample/src/main/java/com/ocean/ontologyframework/example/PizzaOntologyJobWorker.java",
    r"OntologyFrameworkExample/src/test/java/com/ocean/ontologyframework/example/ConsistencyTest.java",
    r"OntologyFrameworkExample/src/test/java/com/ocean/ontologyframework/example/OntologyFrameworkPizzaTests.java",
    r"OntologyFrameworkExample/src/test/java/com/ocean/ontologyframework/example/pizza/PizzaOntologyValidator.java",
    r"OntopOBDAHandler/src/main/java/com/ocean/ontopobdahandler/VkgController.java",
    r"OntopOBDAHandler/src/test/java/com/ocean/ontopobdahandler/OntopObdaHandlerApplication.java",
    r"OntopOBDAHandler/src/test/java/com/ocean/ontopobdahandler/OntopObdaHandlerApplicationTests.java",
    r"OpenlletResolver/src/main/java/com/ocean/openlletresolver/DeleteService.java",
    r"OpenlletResolver/src/main/java/com/ocean/openlletresolver/OntologyService.java",
    r"OpenlletResolver/src/test/java/com/ocean/openlletresolver/GenerateSWRLPizzaRulesFile.java",
    r"OpenlletResolver/src/test/java/com/ocean/openlletresolver/OpenlletResolverTests.java",
]

def main():
    total = 0
    for rel in FILES:
        path = os.path.join(ROOT, rel.replace("/", os.sep))
        if not os.path.exists(path):
            print("MISSING:", rel)
            continue
        with open(path, "rb") as f:
            raw = f.read()
        text = raw.decode("utf-8")
        before = text
        cnt = 0
        for old, new in NS_REPL:
            n = text.count(old)
            if n:
                text = text.replace(old, new)
                cnt += n
        for old, new in IMPORT_REPL:
            n = text.count(old)
            if n:
                text = text.replace(old, new)
                cnt += n
        if text != before:
            with open(path, "wb") as f:
                f.write(text.encode("utf-8"))
            print("OK   %-3d %s" % (cnt, rel))
            total += cnt
        else:
            print("SAME      %s" % rel)
    print("TOTAL replacements:", total)

if __name__ == "__main__":
    main()
