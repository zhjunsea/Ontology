# -*- coding: utf-8 -*-
import re, io, os

BASE = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
out = io.StringIO()

tbox = open(os.path.join(BASE, 'tcm-zhengzhuang.owl'), encoding='utf-8').read()
tbox_classes = set(re.findall(r'<owl:Class rdf:about="#([^"]+)">', tbox))
tbox_classes |= set(re.findall(r'<owl:Class rdf:about="#([^"]+)"/>', tbox))

def abox_inst(files):
    s = set()
    for f in files:
        p = os.path.join(BASE, f)
        if os.path.exists(p):
            s |= set(re.findall(r'<owl:NamedIndividual rdf:about="#([^"]+)_instance">', open(p, encoding='utf-8').read()))
    return s

abox = abox_inst(['tcm-zhengzhuang-abox.owl'])
maix = abox_inst(['tcm-maixiang-abox.owl'])

names = """Zhuhuang Shenjinhuang Yinshui Feitong Mianmuqing Shentongrupiang Yinzhongshichuang
Housishui Yinhushan Pianyouxiaoda Shishishangxia Gejianzhiyin Gexianzhiyin FengqiBaiji
Fengqibaiji NueMu Nuemu Nue Duohan Xialiqi ChangyuDaQiXiongShang Changyudaqixiongshang
Xieli Changjianyoushuiqi Outuerbingzaigeshang Shenghouzhe Xuruomai Shenretengzhong Chuanman
Chuanbudewo Xiongmanzhang Keyouweire Kenishangqi Koujinbunengyan Bentunqishangchongxiong
Yueni Qili Yanhoutong Shijing Yaoxilengtong Hui Yantong Duohanchu Yijing Yaozhongleng Keni
Koujin Budeyu Weire Chuan Xiongman Budewo Qishangchongxiong Xiali Xiaobianchi Kouku
Xinxiapiying Xinxiapiyingman Xinxiapijian Mianselihei ShaoyinMaiHuashu Fuzhongleiming""".split()

out.write(f"{'名称':<28}{'TBox类':<8}{'ABox症状':<10}{'ABox脉象':<10}\n")
for n in names:
    out.write(f"{n:<28}{('有' if n in tbox_classes else '无'):<8}{('有' if n in abox else '无'):<10}{('有' if n in maix else '无'):<10}\n")

# also count total
out.write(f"\nTBox 类总数: {len(tbox_classes)}\n")
open(os.path.join(BASE, '_duli_tbox.txt'), 'w', encoding='utf-8').write(out.getvalue())
print("done")
