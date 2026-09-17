# -*- coding: utf-8 -*-
import re, io, os
BASE = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
out = io.StringIO()

tbox = open(os.path.join(BASE, 'tcm-zhengzhuang.owl'), encoding='utf-8').read()
tcls = set(re.findall(r'<owl:Class rdf:about="#([^"]+)">', tbox)) | set(re.findall(r'<owl:Class rdf:about="#([^"]+)"/>', tbox))
def inst(f):
    p = os.path.join(BASE, f)
    return set(re.findall(r'<owl:NamedIndividual rdf:about="#([^"]+)_instance">', open(p, encoding='utf-8').read()))
abox = inst('tcm-zhengzhuang-abox.owl')
maix = inst('tcm-maixiang-abox.owl')

check = """Yuyin Shenhuang Mianseqing Shentong Runvezhuang
Shouzuleng Furenshaofumanrudunzhuang Xiaobiannan Buke Fare Kouke Xiaobianbuli
Fuman Kousheganzao Xietong Xinxiapi Xinxiapiying Xinxiapijian
Ou Tu Xiongman Changmingruzoushui
Jinkui Koubuyu Kousheganzao Re Duo Reqi
Qizhi Qizhiyu Xiongmen Shangqi Tanyin Yin
Xinxiayouzhiyin Duanqi Xiongzhongqisai
Fuzhongleiming Kouku Xiaobianchi""".split()

out.write(f"{'名称':<30}{'TBox类':<8}{'ABox症状':<10}{'ABox脉象':<10}\n")
for n in check:
    out.write(f"{n:<30}{('有' if n in tcls else '无'):<8}{('有' if n in abox else '无'):<10}{('有' if n in maix else '无'):<10}\n")

# labels for the key replacements
out.write("\n## 关键替换名的中文标签\n")
for n in ['Yuyin','Shenhuang','Mianseqing','Shentong','Runvezhuang','Shouzuleng','Furenshaofumanrudunzhuang','Xiaobiannan','Buke','Fuman','Kousheganzao','Xietong','Xinxiapi','Xinxiapiying','Xinxiapijian','Ou','Tu','Xiongman','Changmingruzoushui','Fuzhongleiming','Kouku','Xiaobianchi']:
    m = re.search(r'<owl:Class rdf:about="#'+re.escape(n)+r'">.*?<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', tbox, re.S)
    out.write(f"  {n}: {m.group(1) if m else '(未找到类)'}\n")

open(os.path.join(BASE,'_duli_verify2.txt'),'w',encoding='utf-8').write(out.getvalue())
print("done")
