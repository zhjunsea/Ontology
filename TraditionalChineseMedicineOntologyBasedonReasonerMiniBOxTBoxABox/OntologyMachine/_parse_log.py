# -*- coding: utf-8 -*-
import io, re
txt = io.open('_shaoyin_run.log', encoding='gbk', errors='replace').read()
blocks = re.split(r'===== (.+?) =====', txt)
# blocks[0] preamble, then pairs (name, body)
targets = ['通脉四逆汤证','茯苓四逆汤证','甘草汤证','苓甘五味姜辛夏汤证',
           '桂枝加龙骨牡蛎汤证','黄芪桂枝五物汤证','赤石脂禹余粮汤证',
           '小建中汤证','甘草干姜茯苓白术汤证']
for i in range(1, len(blocks), 2):
    name = blocks[i].strip()
    body = blocks[i+1]
    if name in targets:
        print('#####', name, '#####')
        for line in body.splitlines():
            if any(k in line for k in ['候选方证','候选方证得分','六经','方证：','推荐方剂','合病','症状']):
                print(line)
        print()
