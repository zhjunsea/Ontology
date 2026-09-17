import os, re, glob, difflib

root = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\ontology"
files = glob.glob(os.path.join(root, "**", "*.owl"), recursive=True)

instances = {}
classes = {}
labels = {}
for f in files:
    txt = open(f, encoding="utf-8").read()
    rel = os.path.relpath(f, root)
    for m in re.finditer(r'<owl:Class rdf:about="#([^"]+)"[^>]*>\s*<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', txt):
        labels[m.group(1)] = m.group(2)
    for m in re.finditer(r'rdf:about="#([^"]+)"', txt):
        n = m.group(1)
        if n.endswith("_instance"):
            instances.setdefault(n[:-9], set()).add(rel)
        else:
            classes.setdefault(n, set()).add(rel)

allnames = sorted(set(instances) | set(classes))

missing = """Chensemai Shenjiacuo Maishu Zuoutu Gejianyoushui Xuanji
Furenyanzhongruyouzhilian Xiongzhongkuikui Chuanxi Xiongbeitong Xiongbibudewo
Xiongbixinzhongpi Darupan Bianruxuanpan Weishumai Xinzhongfan Furenhuaishen
Yichangfu Renshenxiaobiannan Aqibuchu Xianbianhoubianxue Xianxuehoubian
Furennianwushisuo Bingxialishushiribuzhi Shaofumantong Jingshuibibuli Zangjianpibuzhi
Fuzhongxueqicitong Furenlouxia Renshenyangtai Furenruzhongxu Fanluannouni Butong
Jinchuang Jinyinchung Renbijiaozhi Maishangxiuxing Huichong Xintongfazuoyoushi
Xinzhongpiqi Dafeng Churetanxian Rukuangzhuang Toufeng LouxiaBuzhi Fuzhangman
Oujiabenke Fanbuke Xinxiayouzhiyin Xinxiapi Gejianyoushui Xuanji
Furenyanzhongruyouzhilian Xiongzhongkuikui
Xinxiajian Darupan Bianruxuanpan
Kouku Xiaobianchi Xinzhongfan Xiaobianbuli
Fare Renshen Yichangfu
Xulaoxufanbudemian Xulaoyaotong Shaofujuji
Xinji Furenzangzao Xibeishangyuku Xiangrushenlingsuozuo Shuqianshen
Furennianwushisuo Bingxialishushiribuzhi Mujifare Shaofuliji Fuman Shouzhangfanre Chunkouganzao
Daixia Jingshuibuli Shaofumantong Jingyiyuezaijian
Jingshuibibuli Zangjianpibuzhi Xiabaiwu
Fuzhongxueqicitong
Furenlouxia Banchanhouxiaxuebuduan Renshenxiaxue
Renshenyangtai
Furenruzhongxu Fanluannouni
Toutong Sizhikufanre Butong
Jinchuang
Jinyinchung
Zhuanjin Renbijiaozhi Maishangxiuxing
Huichong Tuxian Xintongfazuoyoushi
Xiongbi Xinzhongpiqi Xiongman Xiexianiqiangxin
Dafeng Sizhifanzhong Xinzhongehanbuzu
Churetanxian
Rukuangzhuang Wangxing Duyubuxiu Wuhanre
Toufeng
Jiaoqichongxin
Shouzujuji Baijietengtong
Furensuyouzhengbing Jingduanweijisanyue LouxiaBuzhi Taidongzaiqishang
Fuzhangman Anzhibutong
Xinxiapiying Aqibuchu
Jiaoluanji
Ehan Hanchu
Tuxuebuzhi
Xiaxue Xianbianhoubianxue Mianseweihuang
Xiaxue Xianxuehoubian
Wulaoxuji Fuman Bunengyinshi Jifujiacuo Liangmuanhei
Futong Shaofujijie Citong
Chanhoufutong Fanman Budewo Fuman Xiali Buke
Tuxue Nvxue Xinqibuzu
Changyong Shenjiacuo Fupiji Anzhiruruzhongzhuang Wujiju Shenwure Maishu
Renshenyoushuiqi Shenzhong Xiaobianbuli Sasaehan Qizetouxuan
Kesou Chuan Budewo
Xinxiajidong""".split()

seen = set()
for name in missing:
    if name in seen:
        continue
    seen.add(name)
    if name in instances or name in classes:
        continue
    close = difflib.get_close_matches(name, allnames, n=5, cutoff=0.6)
    print(f"### {name}  (inst={name in instances}, cls={name in classes})")
    for c in close:
        kind = "inst" if c in instances else "cls"
        print(f"    {c:40s} [{kind}] {labels.get(c,'')}")
    print()
