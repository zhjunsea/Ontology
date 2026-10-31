# -*- coding: utf-8 -*-
import rdflib, io, re, sys, glob
sys.stdout.reconfigure(encoding='utf-8')
T2S = {'論':'论','賦':'赋','訣':'诀','總':'总','諸':'诸','問':'问','鈴':'铃','羅':'罗','歲':'岁','宮':'宫','殺':'杀','關':'关','祿':'禄','權':'权','馬':'马','陰':'阴','陽':'阳','飛':'飞','門':'门','龍':'龙','鳳':'凤','紅':'红','鸞':'鸾','傷':'伤','輔':'辅','鉞':'钺','發':'发','補':'补','談':'谈','兒':'儿','審':'审','確':'确','貧':'贫','禍':'祸','過':'过','樂':'乐','書':'书','數':'数','臺':'台','誥':'诰','剋':'克','閑':'闲','財':'财','庫':'库','鄉':'乡','絕':'绝','體':'体','親':'亲','兩':'两','壽':'寿','蕩':'荡','殘':'残','聰':'聪','賤':'贱','盜':'盗','賊':'贼','雜':'杂','賈':'贾','藝':'艺','術':'术','業':'业','機':'机','醜':'丑','張':'张','納':'纳','準':'准','繩':'绳','勝':'胜','將':'将','飛':'飞','喪':'丧','德':'德','喜':'喜','截':'截','旬':'旬','童':'童','鐵':'铁','蛇':'蛇','強':'强','製':'制','挾':'挟','變':'变'}
def s(t): return ''.join(T2S.get(c,c) for c in t)
def nz(t): return re.sub(r'[^\u4e00-\u9fff]','',s(t))
hay_parts=[]
for f in glob.glob(r'..\OntologyFrameworkZiWeiDouShu\design\*.md'):
    hay_parts.append(io.open(f,encoding='utf-8').read())
g=rdflib.Graph(); g.parse(r'..\OntologyFrameworkZiWeiDouShu\ontology\ziweidoushu-all.owl', format='xml')
for subj,p,o in g:
    hay_parts.append(str(o)); hay_parts.append(str(subj)); hay_parts.append(str(p))
hay=nz(' '.join(hay_parts))
def core(t):
    t=nz(t); t=re.sub(r'^(問|问|論|论|安|定|起)','',t)
    t=re.sub(r'(訣|诀|例|論|论|賦|赋|歌|曰|宮|宫|星|格|圖|图|表|法|關|关)$','',t)
    return t
miss=[]
for line in io.open(r'zwds-corpus\OPS\_epub_headers.txt',encoding='utf-8'):
    m=re.match(r'\s*\S+\s*\|\s*(.+?)\s*$', line.rstrip('\n'))
    if not m: continue
    t=m.group(1).strip()
    if not t or t.startswith('#####'): continue
    if t in ('紫微斗數全書卷一','紫微斗数全书卷二','紫微斗数全书卷三'): continue
    if re.fullmatch(r'[歌曰又問問歌]+', nz(t)): continue
    tn=nz(t)
    if tn and tn in hay: continue
    c=core(t)
    if c and c in hay: continue
    if len(c)>=2 and c[:2] in hay and len(nz(t))<=4: continue
    miss.append(t)

STARS='紫微天府天相天梁天同天机太阳太阴文昌文曲武曲贪狼廉贞巨门七杀破军擎羊陀罗火星铃星禄存左辅右弼天魁天钺天马'
def cls(t):
    z=nz(t)
    if '太岁并小限' in s(z): return 'B 行限歌→fortune:LimitSong'
    # 庙陷表：以星名开头且含 庙/旺/地/陷/无
    if z[:2] in STARS and re.search(r'(庙|廟|旺|陷|無|无|地|平|利)', z) and len(z)>4: return 'A 庙陷表（查表外置）'
    if re.match(r'^(二|三|四|五|六|七|八|九|十|十一|十二)(兄弟|妻妾|子女|财帛|疾厄|迁移|奴仆|官禄|田宅|福德|父母)', z): return 'C 十二宫子目→chart:Palace'
    if re.match(r'^(安|定|起|论|論)', t) or '诀' in z or '赋' in z or '曰' in z: return 'D 篇题/诀名（SourceNote 口径差）'
    return 'E 断语/星情格语→reading/pattern:StarMeaning'

out=[]
from collections import Counter
cnt=Counter()
for t in miss:
    k=cls(t); cnt[k]+=1; out.append(f'{k}\t{t}')
print(f'总标题 231，字面命中 {231-len(miss)}，未字面命中 {len(miss)}')
print('--- 未命中分类统计 ---')
for k,v in sorted(cnt.items()): print(f'  {k}: {v}')
print('--- 明细 ---')
print('\n'.join(out))
