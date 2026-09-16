# -*- coding: utf-8 -*-
import io
s = io.open('../ontology/tcm-yaowu-abox.owl', encoding='utf-8').read()
new = ['Wenge','Helile','Puhui','Luanfa','Baiyu','Rongyan','Zhizhu','Shechuangzi','Zhugao',
       'Yunmu','Yangrou','Tuguagen','Kuizi','Baiwei','Wangbuliuxing','Shuoduoixiye',
       'Sangdongnangenbaipi','Jishibai','Qianfen','Juhua','Hanshuishi','Baishizhi','Zishiying']
for iri in new:
    print(('EXIST' if ('#'+iri+'"') in s else 'NEW  '), iri)
