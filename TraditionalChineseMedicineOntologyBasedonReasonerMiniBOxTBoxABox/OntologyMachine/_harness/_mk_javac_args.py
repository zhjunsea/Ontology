# -*- coding: utf-8 -*-
"""生成 javac @argfile，绕开 Windows 命令行长度限制。"""
import io, os, sys

MOD = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/OntologyMachine/OntologyFramework"
cp = io.open(MOD + "/target/test-classpath.txt", encoding="utf-8").read().strip().replace("\r", "").replace("\n", "")

MAIN = [
    "src/main/java/com/ocean/ontologyframework/tcm/app/SymptomCatalog.java",
    "src/main/java/com/ocean/ontologyframework/tcm/app/SymptomMappingService.java",
]
TEST = [
    "src/test/java/com/ocean/ontologyframework/tcm/app/SymptomCatalogTest.java",
    "src/test/java/com/ocean/ontologyframework/tcm/app/SkosAliasTest.java",
]

def write(path, outdir, sources):
    lines = ["-encoding", "UTF-8", "-nowarn", "-cp", '"' + cp.replace("\\", "\\\\") + '"', "-d", outdir]
    lines += sources
    io.open(path, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
    print("WROTE", path, len(sources), "sources")

write(MOD + "/target/_compile_main.args", "target/classes", MAIN)
write(MOD + "/target/_compile_test.args", "target/test-classes", TEST)
