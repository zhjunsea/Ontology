import sys

p = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\app_run_fuzheng.log"
lines = open(p, encoding='utf-8', errors='replace').read().split('\n')
blocks = []
cur = None
for i, ln in enumerate(lines):
    if '方证打分:' in ln:
        cur = {'i': i, 'score': ln.split('方证打分:')[1].strip()}
    if '诊断解释:' in ln and cur is not None:
        cur['diag'] = ln.split('诊断解释:')[1].strip()
        blocks.append(cur)
        cur = None
print("total blocks:", len(blocks))
targets = sys.argv[1:] if len(sys.argv) > 1 else ['小柴胡汤证', '甘草汤证', '理中汤证']
for b in blocks:
    d = b.get('diag', '')
    for t in targets:
        if '方证：' + t in d:
            print("=" * 80)
            print("LINE", b['i'], "|", d[:160])
            print("  SCORE:", b['score'][:700])
            break
