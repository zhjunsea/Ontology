import os, re, glob

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

defined = {}
labels = {}
for f in files:
    txt = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, root)
    for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)"[^>]*>\s*<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', txt):
        labels[m.group(1)] = m.group(2)
    for m in re.finditer(r'rdf:(?:about|ID)="#?([^"]+)"', txt):
        defined.setdefault(m.group(1), rel)

names = """Xulaoxufanbudemian Xulaoyaotong Furenhuaishen Renshenxiaobiannan Furenlouxia
Renshenyangtai Furenruzhongxu Fanluannouni Fuzhangman Anzhibutong Aqibuchu
Xiongbibudewo Xiongbixinzhongpi Xinzhongfan Fuzhongxueqicitong Chuanxi Xiongbeitong
Butong Toufeng Jingshuibibuli Zangjianpibuzhi Xiaxue Xianbianhoubianxue Xianxuehoubian
Hanchu Xiali Buke Jingduanweijisanyue LouxiaBuzhi Taidongzaiqishang Sasaehan
Qizetouxuan Jingyiyuezaijian Furennianwushisuo Bingxialishushiribuzhi Shouzhangfanre
Yichangfu Zuoutu Xuanji Xinxiayouzhiyin Weishumai Chensemai Shenjiacuo Maishu
Oujiabenke Anzhibutong Jiaoqichongxin Renbijiaozhi Weixianmai Jinchuang Jinyinchung
Huichong Xintongfazuoyoushi Xinzhongpiqi Dafeng Churetanxian Rukuangzhuang
Shouzujuji Baijietengtong Darupan Bianruxuanpan
Xufan Budemian Yaotong Renshen Louxia Outu Xiongbi Budewo Xinzhongpi Xinfan
Futong Citong Chuan Toutong Efeng Bianxue Jingshuibuli Fuman Yiqi Fanluan Ouni
Mianseweihuang Weiruomai Xiongzhongfan Rukuang
Xiongzhongkui Yanzhongruyouzhiluan Xiongzhongkuikui Furenyanzhongruyouzhilian
Chanhoufutong Xiaobiannan""".split()

seen = set()
for n in names:
    if n in seen: continue
    seen.add(n)
    if n in defined:
        print(f"{n:28s} EXISTS  [{defined[n]}] {labels.get(n,'')}")
    else:
        print(f"{n:28s} MISSING")
