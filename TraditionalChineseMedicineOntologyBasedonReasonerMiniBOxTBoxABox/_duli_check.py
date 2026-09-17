# -*- coding: utf-8 -*-
import re, io, difflib, os

BASE = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
out = io.StringIO()

def load_instances(path):
    """return dict name -> label"""
    txt = open(path, encoding='utf-8').read()
    d = {}
    for m in re.finditer(r'<owl:NamedIndividual rdf:about="#([^"]+)_instance">(.*?)</owl:NamedIndividual>', txt, re.S):
        name, body = m.group(1), m.group(2)
        lm = re.search(r'<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', body)
        d[name] = lm.group(1) if lm else ''
    return d

inst = {}
for f in ['tcm-zhengzhuang-abox.owl', 'tcm-maixiang-abox.owl', 'tcm-shexiang-abox.owl']:
    p = os.path.join(BASE, f)
    if os.path.exists(p):
        d = load_instances(p)
        out.write(f"### {f}: {len(d)} instances\n")
        inst.update(d)
    else:
        out.write(f"### {f}: NOT FOUND\n")

out.write(f"\nTOTAL instances: {len(inst)}\n")

missing = """Xieli Changjianyoushuiqi Outuerbingzaigeshang Housishui Gejianzhiyin Chuanman
XinxiaPijian MianseLihei Xinxiapiyingman Yinxiexiatong Qishangchonghouyan Shenretengzhong
Zhuhuang Shenjinhuang Yueni Shouzujue Yinshui Feitong Qili Yanhoutong Mianmuqing
Shentongrupiang Yinzhongshichuang Nuem Duohan Shenghouzhe Xuruomai Shijing Yaoxilengtong
Shaoyinmaihuaershu Yinhushan Pianyouxiaoda Shishishangxia Bentunqishangchongxiong
Changyudaoqixiongshang Kenishangqi Keyouweire Chuanbudewo Xiongmanzhang Koujinbunengyan""".split()

names = list(inst.keys())
out.write("\n\n## 缺失名 → 最接近的已有实例（difflib）\n")
out.write(f"{'缺失名':<26}{'存在?':<7}{'最接近':<30}{'相似度':<8}{'中文标签'}\n")
for m in missing:
    exists = m in inst
    close = difflib.get_close_matches(m, names, n=5, cutoff=0.0)
    best = close[0] if close else ''
    ratio = difflib.SequenceMatcher(None, m, best).ratio() if best else 0
    alts = ', '.join(f"{c}({inst[c]})" for c in close[:3])
    out.write(f"{m:<26}{('是' if exists else '否'):<7}{best:<30}{ratio:<8.2f}{inst.get(best,'')}\n")
    out.write(f"{'':<26}候选: {alts}\n")

open(os.path.join(BASE, '_duli_out.txt'), 'w', encoding='utf-8').write(out.getvalue())
print("done", len(inst))
