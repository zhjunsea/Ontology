import xml.etree.ElementTree as ET
import sys

NS = {'bpmn': 'http://www.omg.org/spec/BPMN/20100524/MODEL'}
path = sys.argv[1]
tree = ET.parse(path)
root = tree.getroot()
proc = root.find('bpmn:process', NS)
assert proc is not None, 'no process'

nodes = {}
for tag in ['startEvent', 'endEvent', 'serviceTask', 'task', 'exclusiveGateway', 'parallelGateway']:
    for e in proc.findall('bpmn:' + tag, NS):
        nodes[e.get('id')] = tag

flows = proc.findall('bpmn:sequenceFlow', NS)
print('nodes:', len(nodes), 'flows:', len(flows))

problems = []
# flow endpoint refs
for f in flows:
    s, t = f.get('sourceRef'), f.get('targetRef')
    if s not in nodes:
        problems.append(f'flow {f.get("id")}: source {s} missing')
    if t not in nodes:
        problems.append(f'flow {f.get("id")}: target {t} missing')

# incoming/outgoing declared <-> flows
decl_in = {n: [] for n in nodes}
decl_out = {n: [] for n in nodes}
for tag in ['startEvent', 'endEvent', 'serviceTask', 'task', 'exclusiveGateway', 'parallelGateway']:
    for e in proc.findall('bpmn:' + tag, NS):
        nid = e.get('id')
        for i in e.findall('bpmn:incoming', NS):
            decl_in[nid].append(i.text)
        for o in e.findall('bpmn:outgoing', NS):
            decl_out[nid].append(o.text)

real_in = {n: [] for n in nodes}
real_out = {n: [] for n in nodes}
for f in flows:
    real_out[f.get('sourceRef')].append(f.get('id'))
    real_in[f.get('targetRef')].append(f.get('id'))

for n in nodes:
    if sorted(decl_in[n]) != sorted(real_in[n]):
        problems.append(f'{n} incoming mismatch declared={decl_in[n]} real={real_in[n]}')
    if sorted(decl_out[n]) != sorted(real_out[n]):
        problems.append(f'{n} outgoing mismatch declared={decl_out[n]} real={real_out[n]}')

# start: no incoming; end: no outgoing
for tag in ['startEvent']:
    for e in proc.findall('bpmn:' + tag, NS):
        if real_in[e.get('id')]:
            problems.append(f'start {e.get("id")} has incoming')
for tag in ['endEvent']:
    for e in proc.findall('bpmn:' + tag, NS):
        if real_out[e.get('id')]:
            problems.append(f'end {e.get("id")} has outgoing')

if problems:
    print('PROBLEMS:')
    for p in problems:
        print('  -', p)
    sys.exit(1)
print('OK: XML well-formed and references consistent')
