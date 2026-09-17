import os, re, glob

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

blocks = {}
for f in files:
    txt = open(f, encoding="utf-8").read()
    for m in re.finditer(r'<owl:(?:Class|NamedIndividual) rdf:about="#([^"]+)"', txt):
        name = m.group(1)
        end = txt.find('</owl:', m.end())
        seg = txt[m.start(): m.end()+400]
        lab = re.search(r'<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', seg)
        cmt = re.search(r'<rdfs:comment xml:lang="zh">([^<]*)</rdfs:comment>', seg)
        blocks.setdefault(name, (os.path.basename(f), lab.group(1) if lab else "", cmt.group(1) if cmt else ""))

names = """Yanzhongruyouzhiluan Xiongzhongkui Xiongzhongfan Xiongzhongpiying Xinzhongpi
Xiaobiannan Jingshuibuli Louxia Budewo Yiqi Bianxue Fuman Fanluan Outu Xiongbi
Budewo Xinzhongpi Chenmai Semai Chenshimai Xianshumai WeiseMai Weimai Weiruomai
Rukuang Efeng Toutong Chuan Ketuo Fuzhonghan Yaotong Shaofujuji Xiaobianbuli
Renshen Renshenxiaxue Renshenyoushuiqi Renshenoutubuzhi Furensuyouzhengbing
Furenyinhan Furenzangzao Fanman Futong Toutong Wutoutang Tunong Muteng
Yinchuang Jingkuang Xunyimochuang Shiyihanchu Yinchetong Zhechong
Xiongzhongyoure Xinzhongji Xingzhong Xiongzhongqisai Xiongzhongzhi
Guanjietengtong Zhuzhijietengtong Zhijietong Shouzujueni Shouzuburen Shouzunileng Shouzuwen
Xialibuzhi Jingshuishilai Jingshuishiduan Fuzhongjitong Fuzhongleiming Fuzhongjiaotong
Yinzhongtong Yanzhongtong Mianfu Yitang Xiangfu Puhuisanzheng Bukequshen
Shenyang Mengjiao Shujiao Shigao Chaihu Baizhu Mahuang Dahuang
Yanhuxuanmao Banxiamahuangwan Bianxue Dahan Daohan Duanqi Danrebuhan
Xiaobianhuang Xialinongxue Xiaobianfanduo Xiaobianshu Xiaobianchi
Yutu Yuou Butu Buou Chunan Chunwei Xiongtong Xiongxiekutong Xintong Xietong
Xiongbihuanji Xingzhong Xinzhongpi Xiongzhongpiying""".split()

seen = set()
for n in names:
    if n in seen: continue
    seen.add(n)
    if n in blocks:
        f, lab, cmt = blocks[n]
        print(f"{n:28s} [{f}] {lab}  {cmt[:40]}")
    else:
        print(f"{n:28s} -- NOT DEFINED --")
