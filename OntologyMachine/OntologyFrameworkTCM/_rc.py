import os,io
csv=r"D:\work\Ontology\OntologyMachine\OntologyFrameworkTCM\target\site\jacoco\jacoco.csv"
for l in io.open(csv,encoding="utf-8",errors="replace").read().splitlines():
    if l.startswith("GROUP"): continue
    p=l.split(",")
    if len(p)>=12 and any(k in p[2] for k in ["SixChannelInferenceEngine","JingFangInference","TCMOntologyJobWorker","TCMDiagnosisService","TCMDiagnosisController","PulseParser","DefinitionGapUtils","InferenceResult","SymptomMappingService","SymptomCatalog"]):
        br=int(p[6])+int(p[7]); ln=int(p[8])+int(p[9]); ins=int(p[3])+int(p[4])
        print(f"{p[2]:46s} BR {int(p[7]):>4}/{br:<4} LINE {int(p[9]):>4}/{ln:<4} INSTR {int(p[4]):>5}/{ins:<5}")
