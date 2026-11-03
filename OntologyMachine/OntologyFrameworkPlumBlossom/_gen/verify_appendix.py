#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第八器：附录 逐篇核校核验器（照录须依所对之篇 + 三向）。

附录四篇（底本《梅花易数》附录）：
  一、邵康节易数一撮金：起卦法 ＋「八卦诀」(64 卦名，八卦宫序) ＋ 爻诀 (64卦×3爻)
  二、渔樵对问 (98 则)
  三、宋史·邵雍传 (10 段；篇题题署「元脱脱等撰」)
口径：**逐篇**以「底本规范化串（仅 CJK）」对「交付物重建串」作**精确相等**比较，
      非连文滑窗（避免 DB 分列/卦序不同所致伪影）。
输出：每篇 PASS/FAIL ＋ 差异首处；末尾 RESULT。
"""
import io, re, glob

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
def cjk(x): return "".join(CJK.findall(x))

def strip_comments(src):
    out=[]; i=0; n=len(src)
    while i<n:
        ch=src[i]
        if ch=='/' and i+1<n and src[i+1]=='/':
            j=src.find('\n',i); i=n if j<0 else j; continue
        if ch=='/' and i+1<n and src[i+1]=='*':
            j=src.find('*/',i); i=n if j<0 else j+2; continue
        if ch=='"' or ch=="'":
            q=ch; out.append(ch); i+=1
            while i<n:
                c=src[i]
                if c=='\\': out.append(src[i:i+2]); i+=2; continue
                out.append(c); i+=1
                if c==q: break
            continue
        out.append(ch); i+=1
    return "".join(out)

def java_literals(src):
    src = strip_comments(src)
    src = re.sub(r'"\s*\+\s*"', '', src)   # 教训㉑：并相邻字面量
    out=[]; i=0; n=len(src)
    while i<n:
        if src[i]=='"':
            j=i+1; buf=""
            while j<n:
                c=src[j]
                if c=='\\': buf+=src[j+1:j+2]; j+=2; continue
                if c=='"': break
                buf+=c; j+=1
            out.append(buf); i=j+1; continue
        i+=1
    return out

def parse_rows(sql, table=None):
    for m in re.finditer(r"INSERT INTO\s+`?(\w+)`?\s*\(([^)]*)\)\s*VALUES\s*(.*?);\s*\n", sql, re.S):
        t=m.group(1)
        if table and t!=table: continue
        cols=[c.strip().strip('`') for c in m.group(2).split(",")]
        body=m.group(3); rows=[]; fields=[]; buf=""; i=0; n=len(body); ins=False
        while i<n:
            ch=body[i]
            if ins:
                if ch=="'":
                    if i+1<n and body[i+1]=="'": buf+="'"; i+=2; continue
                    ins=False
                else: buf+=ch
            elif ch=="'": ins=True
            elif ch=="(": fields=[]; buf=""
            elif ch==")": fields.append(buf.strip()); rows.append(list(fields)); fields=[]
            elif ch==",": fields.append(buf.strip()); buf=""
            else: buf+=ch
            i+=1
        for r in rows:
            if len(r)==len(cols): yield cols, r
def col(cols,r,name): return r[cols.index(name)]

# ---------- 语料 / 底本 ----------
java_pieces=set()
for f in glob.glob("src/main/**/*.java", recursive=True):
    for x in java_literals(io.open(f, encoding='utf-8', errors='ignore').read()):
        if cjk(x): java_pieces.add(cjk(x))
sql=io.open("ontology/database/data.sql", encoding="utf-8").read()
NC=cjk(io.open('design/_epub_dump.txt', encoding='utf-8').read())

# 八卦宫序（独立参考＝底本之序）
palace={"乾":["乾为天","天泽履","天火同人","天雷无妄","天风姤","天水讼","天山遁","天地否"],
"兑":["兑为泽","泽天夬","泽火革","泽雷随","泽风大过","泽水困","泽山咸","泽地萃"],
"离":["离为火","火天大有","火泽睽","火雷噬嗑","火风鼎","火水未济","火山旅","火地晋"],
"震":["震为雷","雷天大壮","雷泽归妹","雷火丰","雷风恒","雷水解","雷山小过","雷地豫"],
"巽":["巽为风","风天小畜","风泽中孚","风火家人","风雷益","风水涣","风山渐","风地观"],
"坎":["坎为水","水天需","水泽节","水火既济","水雷屯","水风井","水山蹇","水地比"],
"艮":["艮为山","山天大畜","山泽损","山火贲","山雷颐","山风蛊","山水蒙","山地剥"],
"坤":["坤为地","地天泰","地泽临","地火明夷","地雷复","地风升","地水师","地山谦"]}
names=[]
for k in ["乾","兑","离","震","巽","坎","艮","坤"]: names+=palace[k]

# 附录坐标（CJK 层）
hs = NC.find("八卦诀", 55000)
hx = NC.find("九二二爻上上", hs)                 # 爻诀首：乾为天 九二
yq = NC.find("渔樵对问", hx)
sy = NC.find("元脱脱等撰", yq)

fails=[]
def check(name, ep, db, note=""):
    ok = (ep == db)
    print(f"[{' OK ' if ok else 'FAIL'}] {name}  len={len(ep)}  {note}")
    if not ok:
        for k in range(min(len(ep),len(db))):
            if ep[k]!=db[k]:
                print(f"       first diff @{k}\n        底本: {ep[max(0,k-18):k+18]}\n        重建: {db[max(0,k-18):k+18]}")
                break
        print(f"       len diff = {len(ep)-len(db)}")
        fails.append(name)

# 1) 起卦法
qifa_ep = NC[NC.find("邵康节易数一撮金",50000)+len("邵康节易数一撮金"):hs]
qifa_db = next((p for p in java_pieces if p==qifa_ep), None)
check("附录·一撮金·起卦法", qifa_ep, qifa_db if qifa_db is not None else "", "= YICUOJIN_ALLOC" if qifa_db else "(Java 未命中)")

# 2) 八卦诀 64 卦名
bgj_ep = NC[hs+len("八卦诀"):hx-len("乾为天")]        # 爻诀前的 64 名（不含爻诀之首「乾为天」）
check("附录·八卦诀·64卦名", bgj_ep, "".join(names), "八卦宫序")
bgj_db = [cjk(col(c,r,'text_body')) for c,r in parse_rows(sql,'appendix_text') if col(c,r,'work_name')=='八卦诀']
db_bgj_join = "".join(bgj_db).replace("、","")
print(f"[INFO] 八卦诀 DB 行数 = {len(bgj_db)}；DB 八行（去顿号）== 64名: {db_bgj_join==''.join(names)}")

# 3) 一撮金 爻诀（逐卦）
yj=[(col(c,r,'hexagram_name'),int(col(c,r,'line_position')),col(c,r,'yao_label'),col(c,r,'grade'),col(c,r,'dictum_text')) for c,r in parse_rows(sql,'yicuo_jin_yao')]
from collections import defaultdict
byh=defaultdict(list)
for h_,lp,yl,gr,dt in yj: byh[h_].append((lp,yl,gr,dt))
for h_ in byh: byh[h_].sort()
seg=NC[hx-len("乾为天"):yq]
bad=0; pos=0
for idx,h_ in enumerate(names):
    blk_db=cjk("".join(yl+gr+dt for lp,yl,gr,dt in byh[h_]))
    start=seg.find(h_,pos)
    nxt=seg.find(names[idx+1],start+len(h_)) if idx+1<len(names) else len(seg)
    blk_ep=seg[start: nxt if nxt>=0 else len(seg)]
    if blk_ep != h_+blk_db: bad+=1
    pos=start+len(h_)
print(f"[{' OK ' if bad==0 else 'FAIL'}] 附录·一撮金·爻诀 64卦×3爻  异卦数={bad}")
if bad: fails.append("爻诀")

# 4) 渔樵对问
yq_rows=sorted([(int(col(c,r,'section_index')),col(c,r,'text_body')) for c,r in parse_rows(sql,'appendix_text') if col(c,r,'work_name')=='渔樵对问'])
ep_yq=NC[yq+len("渔樵对问"):NC.find("宋史邵雍传",yq)]
db_yq=cjk("".join(t for _,t in yq_rows))
check("附录·渔樵对问", ep_yq, db_yq, f"{len(yq_rows)} 则")

# 5) 宋史·邵雍传
sy_rows=sorted([(int(col(c,r,'section_index')),col(c,r,'text_body')) for c,r in parse_rows(sql,'appendix_text') if col(c,r,'work_name')=='宋史·邵雍传'])
ep_sy=NC[sy+len("元脱脱等撰"):]
db_sy=cjk("".join(t for _,t in sy_rows))
check("附录·宋史·邵雍传", ep_sy, db_sy, f"{len(sy_rows)} 段")

# 6) 题署
ttl=io.open("ontology/plumblossom-terminology.ttl",encoding="utf-8").read()
ok6 = "元脱脱等撰" in cjk(ttl)
print(f"[{' OK ' if ok6 else 'FAIL'}] 附录·宋史·邵雍传·题署「元脱脱等撰」  载于 SKOS")
if not ok6: fails.append("题署")

print("RESULT: " + ("ALL PASS（附录四篇照录无遗）" if not fails else f"FAIL -> {fails}"))
