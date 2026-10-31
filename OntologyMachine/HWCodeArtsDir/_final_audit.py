# -*- coding: utf-8 -*-
import rdflib, io, re, sys, glob
sys.stdout.reconfigure(encoding='utf-8')
T2S = {'論':'论','賦':'赋','訣':'诀','總':'总','諸':'诸','問':'问','鈴':'铃','羅':'罗','歲':'岁','宮':'宫','殺':'杀','關':'关','祿':'禄','權':'权','馬':'马','陰':'阴','陽':'阳','飛':'飞','喪':'丧','門':'门','龍':'龙','鳳':'凤','紅':'红','鸞':'鸾','傷':'伤','輔':'辅','鉞':'钺','發':'发','補':'补','談':'谈','兒':'儿','審':'审','確':'确','貧':'贫','禍':'祸','過':'过','樂':'乐','書':'书','數':'数','問':'问','臺':'台','誥':'诰','剋':'克','閑':'闲','財':'财','庫':'库','鄉':'乡','絕':'绝','體':'体','親':'亲','兩':'两','壽':'寿','蕩':'荡','殘':'残','聰':'聪','賤':'贱','盜':'盗','賊':'贼','雜':'杂','賈':'贾','藝':'艺','術':'术','業':'业','機':'机','醜':'丑','張':'张','納':'纳','準':'准','繩':'绳','彀':'彀','陽':'阳','盤':'盘','圖':'图','勝':'胜','將':'将','飛':'飞','喪':'丧','虎':'虎','吊':'吊','德':'德','解':'解','神':'神','鸞':'鸾','喜':'喜','截':'截','旬':'旬','童':'童','鎖':'锁','鐵':'铁','蛇':'蛇','強':'强','閑':'闲','製':'制','挾':'挟','變':'变','化':'化','傷':'伤','使':'使','禄':'禄','權':'权','科':'科','忌':'忌'}
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
    t=nz(t)
    t=re.sub(r'^(問|问|論|论|安|定|起)','',t)
    t=re.sub(r'(訣|诀|例|論|论|賦|赋|歌|曰|宮|宫|星|格|圖|图|表|法|關|关)$','',t)
    return t

miss=[]
total=0
for line in io.open(r'zwds-corpus\OPS\_epub_headers.txt',encoding='utf-8'):
    m=re.match(r'\s*\S+\s*\|\s*(.+?)\s*$', line.rstrip('\n'))
    if not m: continue
    t=m.group(1).strip()
    if not t or t.startswith('#####'): continue
    if t in ('紫微斗數全書卷一','紫微斗数全书卷二','紫微斗数全书卷三'): continue
    if re.fullmatch(r'[歌曰又問問歌]+', nz(t)): continue
    total+=1
    tn=nz(t)
    if tn and tn in hay: continue
    c=core(t)
    if c and c in hay: continue
    if len(c)>=2 and c[:2] in hay and len(nz(t))<=4: continue
    miss.append(t)
print('total headers:', total, ' unmatched:', len(miss))
for t in miss: print('  MISS |', t, ' => core=', core(t))
