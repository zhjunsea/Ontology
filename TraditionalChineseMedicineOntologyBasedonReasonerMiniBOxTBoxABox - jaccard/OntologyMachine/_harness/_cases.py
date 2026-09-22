# -*- coding: utf-8 -*-
"""批量打印失败用例的日志打分块（精确症状集合匹配）。"""
import io, os, re

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOG = os.path.join(BASE, 'app_run_v4.log')
INST_RE = re.compile(r'症状=\[([^\]]*)\]\s*脉象=\[([^\]]*)\]')

CASES = [
    ('t_helilesan', {'Xialiqi'}, {'Chenmai'}),
    ('t_baihujiaguizhitang', {'Danrebuhan', 'Gujietengfan', 'Dare', 'Dake', 'Dahan'}, {'Pingmai'}),
    ('t_xiaochengqitang', {'Fuman', 'Chaore', 'Zhanyu'}, {'Huamai'}),
    ('t_dahuangfuzitang', {'Xiexiapiantong', 'Fare', 'Fuman', 'Xiali', 'Buke'}, {'Jinxianmai'}),
    ('t_zhishizhizichitang', {'Dabingchaihou', 'Laofu'}, {'Fumai'}),
    ('t_tongmaisinitang', {'Xialiqinggu', 'Shouzujueni', 'Mianchi', 'Buehan'}, {'Weiximai'}),
    ('t_kujiutang', {'Yanzhongshangshengchuang', 'Budeyu'}, {'Fumai'}),
    ('t_guizhirenshentang', {'Xinxiapiying', 'Xialibuzhi', 'Fare', 'Ehan'}, {'Fumai', 'Xumai'}),
    ('t_caihu_guizhi_ganjiang_taiyin',
     {'Wanglaihanre', 'Xiongxiekuman', 'Xiaobianbuli', 'Kouke', 'Buou', 'Dantouhanchu',
      'Xinfan', 'Fuman', 'Buke', 'Kouku'}, {'Xianmai', 'Ruomai'}),
]


def clean(s):
    s = re.sub(r'^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+ ', '', s)
    s = re.sub(r'\[[^\]]*thread[^\]]*\]\s*', '', s)
    s = s.replace('INFO  c.o.o.TCMOntologyJobWorker - ', '')
    s = s.replace('WARN  c.o.o.TCMOntologyJobWorker - ', 'WARN ')
    return s.rstrip()


def main():
    lines = io.open(LOG, 'r', encoding='utf-8', errors='replace').readlines()
    idx = []
    for i, l in enumerate(lines):
        m = INST_RE.search(l)
        if not m:
            continue
        syms = set(x.strip().replace('_instance', '') for x in m.group(1).split(',') if x.strip())
        puls = set(x.strip().replace('_instance', '') for x in m.group(2).split(',') if x.strip())
        idx.append((i, syms, puls))
    for name, ws, wp in CASES:
        print('#' * 100)
        print('### %s  want sym=%s pul=%s' % (name, sorted(ws), sorted(wp)))
        found = 0
        for (i, syms, puls) in idx:
            if ws == syms and wp == puls:
                found += 1
                if found > 1:
                    break
                print('-- line %d' % i)
                for k in range(i, min(len(lines), i + 60)):
                    s = clean(lines[k])
                    if any(t in s for t in ('症状=', 'realize', '#1', '#2', '#3', '#4', '#5', '#6',
                                            '#7', '#8', '#9', '#10', 'Top', '方证完成', '六经来源',
                                            '六经完成', '八纲来源', '八纲完成', '池', '筛选', '兜底',
                                            '物化', '合成复合')):
                        print('   ', s[:250])
        if found == 0:
            print('   (未找到精确匹配)')


if __name__ == '__main__':
    main()
