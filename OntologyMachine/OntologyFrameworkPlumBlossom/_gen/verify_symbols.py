#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第九器：八卦万物类占（卷之一）↔ 交付物 ABox 取象（Symbol_<卦>_<维度>_<序>）结构化比对。
判据：底本「八卦万物类占」逐卦逐维之文（、-分项）与 ABox 逐 (卦,维度) 重建串「精确相等」。"""
import io, re
from collections import defaultdict

GUA = {'乾':'Qian','兑':'Dui','离':'Li','震':'Zhen','巽':'Xun','坎':'Kan','艮':'Gen','坤':'Kun'}
DIM = {'天时':'TianShi','地理':'DiLi','人物':'RenWu','人事':'RenShi','身体':'ShenTi','时序':'ShiXu',
       '动物':'DongWu','静物':'JingWu','屋舍':'WuShe','家宅':'JiaZhai','婚姻':'HunYin','饮食':'YinShi',
       '生产':'ShengChan','求名':'QiuMing','谋望':'MouWang','交易':'JiaoYi','求利':'QiuLi','出行':'ChuXing',
       '谒见':'YeJian','疾病':'JiBing','官讼':'GuanSong','坟墓':'FenMu','方道':'FangDao','五色':'WuSe',
       '姓字':'XingZi','数目':'ShuMu','五味':'WuWei'}
CJK=re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))

# ---- ABox ----
s=io.open('ontology/plumblossom-abox.owl',encoding='utf-8').read()
ab=defaultdict(lambda: defaultdict(list))
for m in re.finditer(r'Symbol_([A-Za-z]+)_([A-Za-z]+)_(\d+)">(.*?)</owl:NamedIndividual>', s, re.S):
    gua,dim,idx,body=m.group(1),m.group(2),int(m.group(3)),m.group(4)
    lab=re.search(r'<rdfs:label xml:lang="zh">([^<]*)</rdfs:label>', body)
    if lab: ab[gua][dim].append((idx, cjk(lab.group(1))))
AB={g:{d:cjk("".join(v for _,v in sorted(lst))) for d,lst in dms.items()} for g,dms in ab.items()}

# ---- 底本 ----
raw=io.open('design/_epub_dump.txt',encoding='utf-8').read().replace('\r','')
i=raw.find('八卦万物类占'); j=raw.find('右万物之象', i)
blk=raw[i:j]
# 去页标与空白，但需保留 ［］、：，、 以解析
blk=re.sub(r'\[\d+\]','',blk)
# 逐卦切分
order=['乾','兑','离','震','巽','坎','艮','坤']
segs={}
pos=[]
for g in order:
    m=re.search(g+r'\s*卦', blk)
    pos.append((m.start(), g))
pos.sort()
for k,(p,g) in enumerate(pos):
    e = pos[k+1][0] if k+1<len(pos) else len(blk)
    segs[g]=blk[p:e]
ok=0; bad=[]
for g in order:
    body=re.sub(r'［([^］]+)］', lambda mm: mm.group(0) if (mm.group(1) in DIM or mm.group(1)=='竭见') else mm.group(1), segs[g])
    for cat,eng in DIM.items():
        pat=r'［'+cat+r'］：([^［]*)'
        if cat=='谒见': pat=r'［[谒竭]见］：([^［]*)'
        m=re.search(pat, body)
        val=cjk(m.group(1)) if m else ""
        got=AB.get(GUA[g],{}).get(eng,"")
        if val==got: ok+=1
        else: bad.append((g,cat,val,got))
print(f"[INFO] 逐卦逐维比对：相符 {ok}/216；不符 {len(bad)}")
for g,cat,a,b in bad:
    print(f"  !! {g}·{cat}\n     底本: {a}\n     ABox: {b}")
print("RESULT:", "ALL PASS（八卦万物类占 216 维全同）" if not bad else "HAS DIFF")
