# -*- coding: utf-8 -*-
import sys
sys.path.insert(0, '.')
from _fangji81_data import FANGJI81
total = sum(len(f[2]) for f in FANGJI81)
print('81方药物关系总数(理论新增):', total)
print('数据库 fangji_yaowu 总数: 1228')
print('原关系数:', 1228 - total)
