import sys
R = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\target\fangzheng-suite-report.txt"
s = open(R, encoding="utf-8").read().splitlines()
for i, l in enumerate(s):
    if l.startswith("FAIL"):
        print(l)
        if i + 1 < len(s) and s[i + 1].strip().startswith("└"):
            print("    ", s[i + 1].strip())
