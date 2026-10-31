# -*- coding: utf-8 -*-
"""第 11 步（闸门⑩）：将「查表型历法个体」自 ziweidoushu-abox.owl 迁出（DB 为唯一真源）。
迁移集（闸门⑩裁决 2026-10-31·陈希夷）：
  core: Stem_* / Branch_* / Ju_* / Stage_* / NaYin_* / 六十甲子代表(JiaZi,JiaXu,BingYin,GengWu,RenShen,WuChen)
  calendar: LeapMonth_ZhengYue / TrueSolar_Demo01
保留：demo 命例链、神煞名录(Deity_*)、所忌(Taboo_*)、静态术语、告警标志等。
"""
import re, sys, io

PATH = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkZiWeiDouShu\ontology\ziweidoushu-abox.owl"
AB = "http://example.org/ziweidoushu/"

TARGETS = set()
for n in ["Stem_Jia","Stem_Yi","Stem_Bing","Stem_Ding","Stem_Wu","Stem_Ji","Stem_Geng","Stem_Xin","Stem_Ren","Stem_Gui",
          "Branch_Zi","Branch_Chou","Branch_Yin","Branch_Mao","Branch_Chen","Branch_Si","Branch_Wu","Branch_Wei",
          "Branch_Shen","Branch_You","Branch_Xu","Branch_Hai",
          "Ju_Shui2","Ju_Mu3","Ju_Jin4","Ju_Tu5","Ju_Huo6",
          "Stage_ChangSheng","Stage_MuYu","Stage_GuanDai","Stage_LinGuan","Stage_DiWang","Stage_Shuai",
          "Stage_Bing","Stage_Si","Stage_Mu","Stage_Jue","Stage_Tai","Stage_Yang",
          "NaYin_HaiZhongJin","NaYin_LuZhongHuo","NaYin_DaLinMu","NaYin_LuPangTu","NaYin_JianFengJin","NaYin_ShanTouHuo",
          "JiaZi","JiaXu","BingYin","GengWu","RenShen","WuChen"]:
    TARGETS.add(AB + "core#" + n)
for n in ["LeapMonth_ZhengYue","TrueSolar_Demo01"]:
    TARGETS.add(AB + "calendar#" + n)

src = io.open(PATH, encoding="utf-8").read()

# 匹配 <rdf:Description rdf:about="IRI"> ... </rdf:Description>
pat = re.compile(r'[ \t]*<rdf:Description rdf:about="([^"]+)">.*?</rdf:Description>\n?', re.DOTALL)

removed = []
def repl(m):
    iri = m.group(1)
    if iri in TARGETS:
        removed.append(iri)
        return ""
    return m.group(0)

out = pat.sub(repl, src)

# 折叠连续空行
out = re.sub(r'\n{3,}', '\n\n', out)

io.open(PATH, "w", encoding="utf-8").write(out)
missing = TARGETS - set(removed)
print("removed=%d / target=%d" % (len(removed), len(TARGETS)))
if missing:
    print("MISSING (未找到):", sorted(x.replace(AB,'') for x in missing))
    sys.exit(1)
print("OK")
