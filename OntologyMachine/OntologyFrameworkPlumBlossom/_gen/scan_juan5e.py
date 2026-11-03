#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""卷之五 逐篇比对：底本 CJK ↔ 该篇 putXz 各行 value 之 CJK 拼接。"""
import io, re
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))
JAVA = io.open("src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java",encoding='utf-8').read()
ND = io.open('design/_epub_dump.txt',encoding='utf-8').read()
Nc = cjk(ND)
# 解析 putXz(cat, key, val)
rows = re.findall(r'putXz\("([^"]*)",\s*"([^"]*)",\s*"((?:[^"\\]|\\.)*)"\)', JAVA)
from collections import OrderedDict
cats = OrderedDict()
for c,k,v in rows:
    cats.setdefault(c, []).append((k, v.replace('\\"','"')))
sections = ["五行全备","六神形式","八卦辨","神煞","七言作用歌","比例歌","西江月","易理玄微",
"断扇占","买香占","古人相字","断富贵贫贱要诀","五行四时旺相休囚表","论八卦性情","八卦取象",
"六十甲子纳音","六十四卦次第歌","系辞八卦类象歌","浑天甲子定局","后天时方","八反格","四言独步",
"五言作用歌","别理篇","六言剖断歌","格物章","物理论","五行六神辨别论","金声章"]
W=14
def covered(expected, actual):
    seg=expected; cov=bytearray(len(seg)); j=0
    while j<=len(seg)-W:
        if seg[j:j+W] in actual: cov[j:j+W]=b"\x01"*W; j+=W
        else: j+=1
    gaps=[]; i=0
    while i<len(seg):
        if cov[i]==0:
            k=i
            while k<len(seg) and cov[k]==0: k+=1
            if k-i>=12: gaps.append((i,k))
            i=k
        else: i+=1
    return gaps
# 篇边界（CJK 偏移）
marks = ["拆字杂编五行全备","六神形式","八卦辨","贵神","七言作用歌","比例歌","西江月","易理玄微",
"断扇占","买香占","古人相字","断富贵贫贱要诀","五行四时旺相休囚表","论八卦性情","八卦取象",
"六十甲子纳音","六十四卦次第歌","系辞八卦类象歌","浑天甲子定局","后天时方","八反格","四言独步",
"五言作用歌","别理篇","六言剖断歌","格物章","物理论","五行六神辨别论","金声章","附录附录邵康节"]
pos=[]
for m in marks:
    i=Nc.find(m, 45600)
    pos.append(i)
for idx in range(len(marks)-1):
    name = marks[idx]; a,b = pos[idx], pos[idx+1]
    exp = Nc[a:b]
    # 对应 putXz 类名
    cat = {"五言作用歌":"五言作用歌"}.get(name, name)
    actual = ""
    for key,name2 in [(k,c) for c,ks in [] for k in []]:
        pass
    # 收集所有类名等于 name 的（去掉首个标记字）
    keyname = name.replace("附录附录邵康节","")
    vals=[]
    for c,items in cats.items():
        if c == keyname or c == name:
            vals += [v for k,v in items]
    actual = cjk("".join(vals))
    g = covered(exp, actual)
    if g:
        print(f"### {name}: 未落 {[(s,e) for s,e in g]}")
        for s,e in g:
            print("     ", Nc[a+s:a+e])
    else:
        print(f"### {name}: 全覆盖")
