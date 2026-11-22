import os,glob,re
BASE=r"D:\work\Ontology\OntologyMachine\OntologyFrameworkTCM\src\main\java"
names=["SixChannelInferenceEngine","JingFangInference","PulseParser","TCMDiagnosisService","DefinitionGapUtils","InferenceResult"]
files=[p for p in glob.glob(BASE+"/**/*.java",recursive=True)]
for nm in names:
    print("=== usages of",nm,"===")
    for p in files:
        for i,l in enumerate(open(p,encoding="utf-8",errors="replace").read().splitlines(),1):
            if nm in l and ("new "+nm in l or nm+" " in l or nm+"(" in l or nm+"." in l or " "+nm+";" in l or "<"+nm in l):
                print(f"  {os.path.basename(p)}:{i}: {l.strip()[:120]}")
