# -*- coding: utf-8 -*-
import re, io, os, sys

bad = """Beiweiehan|Beiehan
Ruzhuang|Runvezhuang
Duohanshui|Duohanchu
Mianchibanzhang|Mianchibanbanrujinwen
Biniu|Binv
Muzhongliaoliao|Muzhongbulele
Mianseqinghei|Mianselihei
Mushundong|Murundong
Mukeshanweiweiyong|Mukeshangweiyong
Koushebuzhen|Kousheburen
Yanzhongruyouzhilian|Yanzhongruyouzhiluan
Koutuoxian|Koutuxian
Yuyannanchuchu|Yuyannanchu
Kesuo|Ketuo
Kesangerqi|Keershangqi
Xinzhongzhi|Xiongzhongzhi
Xiongzhongjiatuo|Xiongzhongjiacuo
Xinzhongdahantong|Xinxiongdahantong
Xiexiaoniqiangxin|Xiexianiqiangxin
Fuzhongleng|Fuzhonghan
Fuzhongjitong|Fuzhongjiaotong
Leiming|Fuzhongleiming
Shaofuji|Shaofujuji
Shaofujili|Shaofuliji
Xinxianzhimantong|Xinxiaanzhimantong
AnzhijitongRulin|Anzhijitongrulin
Shaofuzhengjia|Shaofuzhengkuai
Raogitong|Raoqitong
Fuzhongjijitong|Fuzhongjitong
Pangguangji|Bangguangji
Xialirishu|Xialirishushixing
Dabianzhananzayi|Dabianzhananzhayi
Zili'erke|Zilierke
E|Hui
Gantaishichou|Ganyishichou
Shouzuraorao|Shouzuzaorao
Shouzhufanre|Shouzufanre
Jintiroushun|Jintirourun
Danbibuxui|Danbibusui
Shenrushichongxingpizhong|Shenruchongxingpizhong
Yaoyixiashuiqi|Yaoyixiayoushuiqi
Tibueran|Tierbuan
Rujiangguizhuang|Rujianguizhuang
Zhenzhenshenshunju|Zhenzhenshenrunju
Mianmuzhachizhaheizhaibai|Mianmuzhachizhaheizhabai
Wozegjing|Wozejing
Cukoujin|Zukoujin
Wobuzhuoxi|Wobuzhexi
Niaoshitoutong|Nishitoutong
Sezhenghuangrubai|Sezhenghuangrubaizhi
Shensheruxunhuang|Shenseruxunhuang
Nuxue|Nvxue
RenshenXiaxue|Renshenxiaxue
RenshenOutuBuzhi|Renshenoutubuzhi
RenshenYoushuiqi|Renshenyoushuiqi
Furensuyouzhengjia|Furensuyouzhengbing
ChanhouFutong|Chanhoufutong
ChanhouXiali|Chanhouxiali"""

pairs = []
for line in bad.strip().split('\n'):
    o, n = line.split('|')
    pairs.append((o, n))

roots = ['../ontology', '../OntologyMachine/OntologyFramework/src', '../ontology/database']
# gather files
files = []
for r in roots:
    for dp, dn, fn in os.walk(r):
        if '__pycache__' in dp or '_backup' in dp:
            continue
        for f in fn:
            if f.endswith(('.owl', '.java', '.sql', '.obda', '.properties', '.txt', '.csv')):
                files.append(os.path.join(dp, f))

print('扫描文件数:', len(files))
for o, n in pairs:
    hits = []
    pat = o + '_instance'
    for fp in files:
        try:
            s = io.open(fp, encoding='utf-8', errors='ignore').read()
        except Exception:
            continue
        c = s.count(pat)
        if c:
            hits.append('%s x%d' % (fp, c))
    if hits:
        print('OLD=%s -> NEW=%s : %s' % (o, n, '; '.join(hits)))
