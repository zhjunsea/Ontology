# -*- coding: utf-8 -*-
import re, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

BASE = r"D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology"
files = ["tcm-zhengzhuang.owl", "tcm-maixiang.owl", "tcm-shexiang.owl", "tcm-fuzheng.owl", "tcm-core.owl"]
text = ""
for f in files:
    text += open(BASE + "/" + f, encoding='utf-8').read()

names = """Hanshijiexiong Wurezheng Wureehan Xinxiatong Anzhishiying Zhuanjin Renbijiaozhi
Hanshanfutong Shouzunileng Shouzuburen Shentong Gujietengfan Chetongbudequshen Hanchu Duanqi
Xiaobianbuli Efeng Qixiaji Yuzuobentun Shaofujijie Rukuang Xiaobianzili Shentengfan Ehan
Qicongshaofushangchongxin Huanghan Liangjingzileng Shiyihanchu Muchangdaohanchu Lishui
Shentizhong Fare Kouke Hanzhanyi Sezhenghuangrubaizhi Shentiqiangjiji Furensuyouzhengbing
Jingduanweijisanyue Louxia Taidongzaiqishang Xinxianiman Qishangchongxiong Qizetouxuan
Yantong Yanzhongtong Yanzhongshangshengchuang Budeyu Kesou Tunong Fuman Xiali
Wanglaihanre Xiongxiekuman Xinfan Buou Dantouhanchu Buke Kouku Danyumei Wuhan""".split()

for n in names:
    m = re.search(r'<owl:Class rdf:about="#%s">(.*?)</owl:Class>' % re.escape(n), text, re.DOTALL)
    if not m:
        print("%-28s <NOT FOUND>" % n); continue
    body = m.group(1)
    subs = re.findall(r'<rdfs:subClassOf rdf:resource="#([^"]+)"/>', body)
    subs = [s for s in subs if s not in ("Zhengzhuang", "Maixiang", "Shexiang", "Fuzheng")]
    label = re.search(r'<rdfs:label xml:lang="zh">(.*?)</rdfs:label>', body)
    print("%-28s 「%s」  ⊑ %s" % (n, label.group(1) if label else "", subs))
