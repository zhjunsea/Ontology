#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import io, re, json
raw = io.open('design/_epub_dump.txt', encoding='utf-8').read().replace('\r','')
def clean(s):
    s = re.sub(r'\[\d+\]', '', s); return re.sub(r'\s+', '', s)
def between(a, b):
    i = raw.find(a); j = raw.find(b, i+len(a)); assert i>=0 and j>i, (a,b)
    return clean(raw[i+len(a):j])

data = [
 ('JUAN_ER_XIN_YI_XUAN_JI',   '心易占卜玄机', between('心易占卜玄机','占卜总诀')),
 ('JUAN_ER_ZHAN_BU_ZONG_JUE', '占卜总诀',     between('占卜总诀','占卜论理诀')),
 ('JUAN_ER_ZHAN_BU_LUN_LI',   '占卜论理诀',   between('占卜论理诀','先天后天论')),
 ('JUAN_ER_XIAN_TIAN_HOU_TIAN','先天后天论',  between('先天后天论','卦断遗论')),
 ('SAN_YAO_CHAI_ZI',          '拆字之应',     between('右乃杂见观物之应','复指物名').lstrip('。')),
]
def wrap(name, heading, body, width=68):
    full = heading + body
    out = [f'    private static final String {name} =']
    segs = [full[i:i+width] for i in range(0, len(full), width)]
    for k, s in enumerate(segs):
        pre = '        ' if k==0 else '      + '
        out.append(f'{pre}"{s}"' + (';' if k==len(segs)-1 else ''))
    return "\n".join(out)

buf = []
for name, h, b in data:
    buf.append(wrap(name, h, b))
    buf.append('')
snippet = "\n".join(buf)
io.open('_gen/round43_consts.java','w',encoding='utf-8').write(snippet)
print(snippet)
print("// lens:", [(n, len(h)+len(b)) for n,h,b in data])
