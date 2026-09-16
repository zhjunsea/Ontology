# -*- coding: utf-8 -*-
import re

ZZ = '../ontology/tcm-zhengzhuang.owl'
zz = open(ZZ, encoding='utf-8').read()
defined = set(re.findall(r'<owl:Class rdf:about="#([^"]+)"', zz))
print('症状类总数:', len(defined))

# 测试用例用到的症状（失败案例）
test_syms = {
 'case1 柴胡桂枝汤证': ['Fare','Ehan','Wanglaihanre','Xiongxiekuman','Kouku','Gujietengfan','Xinxiazhijie','Weiou','Fumai','Xianmai'],
 'case2 柴胡去半夏加栝楼汤证': ['Kouke','Wanglaihanre','Runvezhuang'],
 'case3 四逆散证': ['Shouzuleng','Wanglaihanre','Xiongxiekuman','Kouku','Futong','Xieli','Xianmai'],
 'case4 大承气汤证': ['Danrebuhan','Kouke','Chaore','Dabianying','Zhanwang','Chenshimai','Fuman','Futong','Juan'],
 'case5 大黄蛰虫丸证': ['Wulaoxuji','Fuman','Bunengyinshi','Jifujiacuo','Liangmuanhei'],
 'case6 大黄甘遂汤证': ['Furenshaofumanrudunzhuang','Xiaobiannan','Buke'],
 'case7 下瘀血汤证': ['Futong','Shaofujijie','Citong'],
 'case8 枳实芍药散证': ['Chanhoufutong','Fanman','Budewo','Fuman','Xiali','Buke'],
 'case9 泻心汤证': ['Tuxue','Nvxue','XinqiBuzu','Hongmai'],
}
for name, syms in test_syms.items():
    missing = [s for s in syms if s not in defined]
    print(f'\n{name}: 缺失={missing}')

# 方证定义中引用的症状
fz_defs = {
 'Chaihuguizhitangzheng(hebing)': '../ontology/fangzheng/hebing.owl',
 'Sinisanzheng(duli)': '../ontology/fangzheng/duli.owl',
 'Xiexintangzheng(zabing)': '../ontology/fangzheng/zabing.owl',
 'Dachengqitangzheng(syy)': '../ontology/fangzheng/shaoyang_yangming.owl',
 'Dahuangzhechongwanzheng(zabing)': '../ontology/fangzheng/zabing.owl',
 'Xiayuxuetangzheng(zabing)': '../ontology/fangzheng/zabing.owl',
 'Zhishishaoyaosanzheng(zabing)': '../ontology/fangzheng/zabing.owl',
 'Dahuanggansuitangzheng(duli)': '../ontology/fangzheng/duli.owl',
}
print('\n\n===== 方证定义引用症状检查 =====')
for fz, path in fz_defs.items():
    text = open(path, encoding='utf-8').read()
    m = re.search(r'<owl:Class rdf:about="#' + fz.split('(')[0] + r'">(.*?)\n    </owl:Class>', text, re.S)
    if not m:
        print(f'{fz}: 未找到定义')
        continue
    body = m.group(1)
    syms = re.findall(r'#you_zhengzhuang"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"', body)
    maix = re.findall(r'#you_maixiang"/>\s*<owl:someValuesFrom rdf:resource="#([^"]+)"', body)
    miss = [s for s in syms + maix if s not in defined]
    print(f'{fz}: 症状={syms} 脉={maix} 缺失={miss}')
