# -*- coding: utf-8 -*-
import io, sys

pairs = []
raw = """Beiweiehan|Beiehan
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
for line in raw.strip().split('\n'):
    o, n = line.split('|')
    pairs.append((o, n))

abox = '../ontology/tcm-zhengzhuang-abox.owl'
s = io.open(abox, encoding='utf-8').read()
cnt = 0
for o, n in pairs:
    old = '#%s_instance' % o
    new = '#%s_instance' % n
    c = s.count(old)
    if c != 1:
        print('WARN: %s 出现 %d 次（预期1）' % (old, c))
    s = s.replace(old, new)
    cnt += c
io.open(abox, 'w', encoding='utf-8').write(s)
print('abox 替换实例ID总数:', cnt)

# 测试文件同步：仅 2 个旧 ID 被引用
tests = [
    '../OntologyMachine/OntologyFramework/src/test/java/com/ocean/ontologyframework/JingfangDiagnosisProcessTest.java',
    '../OntologyMachine/OntologyFramework/src/test/java/com/ocean/ontologyframework/tcm/JianjiaFangzhengTest.java',
    '../OntologyMachine/OntologyFramework/src/test/java/com/ocean/ontologyframework/tcm/ShaoyangYangmingFangzhengTest.java',
]
for tp in tests:
    t = io.open(tp, encoding='utf-8').read()
    before = t
    t = t.replace('Xinxianzhimantong_instance', 'Xinxiaanzhimantong_instance')
    t = t.replace('Yanzhongruyouzhilian_instance', 'Yanzhongruyouzhiluan_instance')
    if t != before:
        io.open(tp, 'w', encoding='utf-8').write(t)
        print('已更新测试:', tp)
print('DONE')
