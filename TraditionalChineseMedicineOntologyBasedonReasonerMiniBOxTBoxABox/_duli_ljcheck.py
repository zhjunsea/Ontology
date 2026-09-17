# -*- coding: utf-8 -*-
import re, io, os
BASE = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox"
FZ = os.path.join(BASE, 'ontology', 'fangzheng')
TEST = os.path.join(BASE, 'OntologyMachine', 'OntologyFramework', 'src', 'test', 'java', 'com', 'ocean', 'ontologyframework', 'tcm', 'DuliFangzhengTest.java')
out = io.StringIO()

files = {}
for f in os.listdir(FZ):
    if f.endswith('.owl'):
        files[f] = open(os.path.join(FZ, f), encoding='utf-8').read()

def lj_of(cls):
    for f, txt in files.items():
        m = re.search(r'<owl:Class rdf:about="#' + re.escape(cls) + r'">', txt)
        if m:
            end = txt.find('</owl:Class>', m.start())
            return re.findall(r'<belongsToLiujing rdf:resource="#([^"]+)"/>', txt[m.start():end])
    return None

# LIUJING_OF_MISC / HEBING from JingfangTestSupport
MISC = {"Shibing":"Taiyangbing","Xiongbibing":"Taiyinbing","Feizhangbing":"Taiyangbing","Shuiqibing":"Taiyangbing",
"Bentunbing":"Taiyangbing","Xuebibing":"Taiyangbing","Jingbing":"Taiyangbing","Jingjibing":"Taiyinbing",
"Taiyangzhongye":"Taiyangbing","Xulaobing":"Shaoyinbing","Nuebing":"Shaoyangbing","Tanyinbing":"Taiyinbing",
"Shuixiebing":"Taiyinbing","Outuoyuexialibing":"Taiyinbing","Furenzabing":"Taiyinbing","Furenchanhoubing":"Taiyinbing",
"Chanhoubing":"Taiyinbing","Renshengbing":"Taiyinbing","Jinchuangbing":"Taiyinbing","Zhuanjinbing":"Taiyinbing",
"Feiweibing":"Taiyinbing","Ganzhuobing":"Taiyinbing","Feiweifeiyongkesoushangqi":"Taiyinbing","Feiyongbing":"Taiyinbing",
"Kesoushangqibing":"Taiyinbing","Hanshanbing":"Shaoyinbing","Huangdanbing":"Yangmingbing","Changyongbing":"Yangmingbing",
"Tunvxiaxuebing":"Yangmingbing","Xiaxuebing":"Taiyinbing","Yinyangdu":"Jueyinbing","Huhuobing":"Jueyinbing",
"Zhongfengbing":"Jueyinbing","Yinhushanbing":"Jueyinbing","Huichongbing":"Jueyinbing",
"Chuangyongchangyongjinyinbing":"Jueyinbing","Baihebing":"Shaoyangbing","Lijiebing":"Jueyinbing",
"Fumanbing":"Yangmingbing","Chahoulaofubing":"Yangmingbing"}
HEBING = {"Taiyangyangminghebing":["Taiyangbing","Yangmingbing"],"Taiyangshaoyanghebing":["Taiyangbing","Shaoyangbing"],
"Yangmingshaoyanghebing":["Yangmingbing","Shaoyangbing"],"TaiyinYangmingHebing":["Taiyinbing","Yangmingbing"],
"ShaoyangTaiyinHebing":["Shaoyangbing","Taiyinbing"],"TaiyangYangmingHebing":["Taiyangbing","Yangmingbing"],
"TaiyangTaiyinHebing":["Taiyangbing","Taiyinbing"],"Sanyanghebing":["Taiyangbing","Yangmingbing","Shaoyangbing"]}
SIX = ["Taiyangbing","Yangmingbing","Shaoyangbing","Taiyinbing","Shaoyinbing","Jueyinbing"]

def resolve(lj):
    if lj in SIX: return [lj]
    if lj in HEBING: return HEBING[lj]
    if lj in MISC: return [MISC[lj]]
    return None

txt = open(TEST, encoding='utf-8').read()
pat = re.compile(r'void\s+(\w+)\(\)\s*\{\s*assertFangzheng\(\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)"', re.S)
out.write(f"{'用例':<46}{'lj':<22}{'解析六经':<26}{'方证':<46}{'方证归属':<24}{'一致?'}\n")
for m in pat.finditer(txt):
    method, name, lj, fz, formula = m.groups()
    r = resolve(lj)
    fl = lj_of(fz)
    ok = 'OK' if (r and fl and set(r) & set(fl)) else '**不一致**'
    out.write(f"{method:<46}{lj:<22}{str(r):<26}{fz:<46}{str(fl):<24}{ok}\n")

open(os.path.join(BASE,'ontology','_duli_ljcheck.txt'),'w',encoding='utf-8').write(out.getvalue())
print("done")
