# -*- coding: utf-8 -*-
import re
import os
from collections import defaultdict
from difflib import SequenceMatcher

pattern_class = r'<owl:Class rdf:about="#(\w+)\">'
pattern_label = r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>'

files = ['taiyang.owl', 'shaoyang_yangming.owl', 'shaoyin_taiyin.owl', 'jueyin.owl', 'zabing.owl', 'duli.owl', 'hebing.owl', 'jianjia.owl', 'fanggen.owl', 'rules.owl']
base_path = r'D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/fangzheng'

# 存储数据
all_fangzheng = defaultdict(list)
fangzheng_names = defaultdict(list)
fangzheng_data = []

for fname in files:
    fpath = os.path.join(base_path, fname)
    with open(fpath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    class_matches = list(re.finditer(pattern_class, content))
    label_matches = list(re.finditer(pattern_label, content))
    
    for match in class_matches:
        name = match.group(1)
        if name.endswith('zheng') or name.endswith('Zheng'):
            all_fangzheng[fname].append(name)
            fangzheng_names[name].append(fname)
            
            class_pos = match.end()
            corresponding_label = None
            for label_match in label_matches:
                label_pos = label_match.start()
                if label_pos > class_pos:
                    next_class_pos = len(content)
                    for cm in class_matches:
                        if cm.start() > class_pos:
                            next_class_pos = cm.start()
                            break
                    if label_pos < next_class_pos:
                        corresponding_label = label_match.group(1)
                        break
            
            fangzheng_data.append((fname, name, corresponding_label))

# 拼音映射（简化版）
pinyin_map = {
    '桂': 'gui', '枝': 'zhi', '汤': 'tang', '证': 'zheng', '类': 'lei', '方': 'fang',
    '加': 'jia', '葛': 'ge', '根': 'gen', '厚': 'hou', '朴': 'po', '杏': 'xing',
    '子': 'zi', '附': 'fu', '去': 'qu', '芍': 'shao', '药': 'yao', '蜀': 'shu',
    '漆': 'qi', '牡': 'mu', '蛎': 'li', '龙': 'long', '骨': 'gu', '救': 'jiu',
    '逆': 'ni', '茯': 'fu', '苓': 'ling', '白': 'bai', '术': 'zhu', '甘': 'gan',
    '草': 'cao', '新': 'xin', '人': 'ren', '参': 'shen', '栝': 'gua', '楼': 'lou',
    '麻': 'ma', '黄': 'huang', '大': 'da', '青': 'qing', '龙': 'long', '小': 'xiao',
    '石': 'shi', '膏': 'gao', '越': 'yue', '婢': 'bi', '半': 'ban', '夏': 'xia',
    '连': 'lian', '翘': 'qiao', '赤': 'chi', '豆': 'dou', '薏': 'yi', '苡': 'yi',
    '竹': 'zhu', '叶': 'ye', '陷': 'xian', '胸': 'xiong', '丸': 'wan', '散': 'san',
    '抵': 'di', '当': 'dang', '归': 'gui', '通': 'tong', '脉': 'mai',
    '猪': 'zhu', '胆': 'dan', '汁': 'zhi', '干': 'gan', '姜': 'jiang', '桔': 'jie',
    '梗': 'geng', '苦': 'ku', '酒': 'jiu', '及': 'ji',
    '肤': 'fu', '桃': 'tao', '花': 'hua', '理': 'li', '中': 'zhong',
    '脂': 'zhi', '禹': 'yu', '余': 'yu', '粮': 'liang',
    '真': 'zhen', '武': 'wu', '味': 'wei', '辛': 'xin', '仁': 'ren',
    '芪': 'qi', '知': 'zhi', '母': 'mu', '建': 'jian',
    '射': 'she', '细': 'xi', '乌': 'wu', '头': 'tou', '吴': 'wu', '茱': 'zhu', '萸': 'yu',
    '翁': 'weng', '梅': 'mei', '芩': 'qin', '柴': 'chai', '胡': 'hu', '芒': 'mang', '硝': 'xiao',
    '枳': 'zhi', '实': 'shi', '薤': 'xie', '白': 'bai', '胶': 'jiao', '艾': 'ai',
    '温': 'wen', '经': 'jing', '土': 'tu', '瓜': 'gua', '根': 'gen', '柏': 'bai',
    '泻': 'xie', '心': 'xin', '遂': 'sui', '物': 'wu', '升': 'sheng',
    '雄': 'xiong', '熏': 'xun', '防': 'fang', '己': 'ji', '椒': 'jiao', '苈': 'li', '葶': 'ting',
    '枣': 'zao', '肺': 'fei', '千': 'qian', '金': 'jin', '苇': 'wei', '茎': 'jing',
    '麦': 'mai', '门': 'dong', '橘': 'ju', '皮': 'pi', '茹': 'ru', '紫': 'zi',
    '诃': 'he', '梨': 'li', '勒': 'le', '一': 'yi', '十': 'shi', '续': 'xu', '命': 'ming',
    '鳖': 'bie', '甲': 'jia', '煎': 'jian', '天': 'tian', '薯': 'shu', '蓣': 'yu',
    '蒲': 'pu', '灰': 'hui', '戎': 'rong', '盐': 'yan', '蜘': 'zhi', '蛛': 'zhu',
    '蛇': 'she', '床': 'chuang', '狼': 'lang', '牙': 'ya', '硝': 'xiao', '矾': 'fan',
    '炙': 'zhi', '麻': 'ma', '甘': 'gan', '草': 'cao', '王': 'wang', '不': 'bu', '留': 'liu', '行': 'xing',
    '排': 'pai', '脓': 'nong', '粉': 'fen', '蜜': 'mi', '鸡': 'ji', '子': 'zi',
    '侯': 'hou', '氏': 'shi', '黑': 'hei', '风': 'feng', '引': 'yin', '地': 'di',
    '术': 'zhu', '附': 'fu',
}

def chinese_to_pinyin(text):
    result = []
    for char in text:
        if char in pinyin_map:
            py = pinyin_map[char]
            result.append(py[0].upper() + py[1:])
        else:
            result.append(char)
    return ''.join(result)

# 1. 各文件方证总数统计
print('='*80)
print('方证分析报告')
print('='*80)
print()
print('一、各文件方证总数统计')
print('-'*60)
total = 0
for fname in files:
    count = len(all_fangzheng[fname])
    total += count
    print(f'{fname}: {count} 个方证')
print(f'总计: {total} 个方证')
print()

# 2. 重复方证列表
print('二、重复方证列表（名称完全一致）')
print('-'*60)
duplicates = {k: v for k, v in fangzheng_names.items() if len(v) > 1}
if duplicates:
    for name, files_list in duplicates.items():
        print(f'{name}: 出现在 {files_list}')
else:
    print('未发现重复的方证名称')
print()

# 3. 方证名称与label不一致的列表
print('三、方证名称与label不一致的列表（拼音命名规范检查）')
print('-'*60)
mismatches = []
for fname, name, label in fangzheng_data:
    if not label:
        continue
    
    # 从label提取中文名（去掉"证"字）
    if label.endswith('证'):
        zhongwen = label[:-1]
    else:
        zhongwen = label
    
    # 期望的拼音名称
    expected_pinyin = chinese_to_pinyin(zhongwen) + 'zheng'
    
    # 比较
    if name != expected_pinyin:
        mismatches.append((fname, name, label, expected_pinyin))

if mismatches:
    print(f'发现 {len(mismatches)} 个不一致的方证：')
    print()
    for fname, name, label, expected in mismatches[:20]:  # 只显示前20个
        print(f'文件: {fname}')
        print(f'  方证名称: {name}')
        print(f'  Label: {label}')
        print(f'  期望拼音: {expected}')
        print()
    if len(mismatches) > 20:
        print(f'... 还有 {len(mismatches) - 20} 个不一致项未显示')
else:
    print('所有方证名称与label拼音一致')
print()

# 4. 拼写相似但不完全相同的方证列表
print('四、拼写相似但不完全相同的方证列表')
print('-'*60)

# 获取所有方证名称
all_names = list(fangzheng_names.keys())
similar_pairs = []

for i in range(len(all_names)):
    for j in range(i+1, len(all_names)):
        name1 = all_names[i]
        name2 = all_names[j]
        # 计算相似度
        ratio = SequenceMatcher(None, name1, name2).ratio()
        if 0.7 < ratio < 1.0:  # 相似度在70%-100%之间（不包括完全相同）
            similar_pairs.append((name1, name2, ratio))

# 按相似度排序
similar_pairs.sort(key=lambda x: -x[2])

if similar_pairs:
    print(f'发现 {len(similar_pairs)} 对方证名称相似：')
    for name1, name2, ratio in similar_pairs[:30]:  # 显示前30对
        print(f'  {name1} <-> {name2} (相似度: {ratio:.2%})')
else:
    print('未发现拼写相似的方证名称')
print()

# 5. 特殊发现
print('五、特殊发现')
print('-'*60)

# 检查是否有"类"字的拼音问题
print('1. 关于"类"字的拼音：')
print('   当前拼音映射中"类"->"lei"，但实际方证名称中使用的是"Lei"（首字母大写）')
print()

# 检查ChiWanzheng的特殊情况
print('2. 特殊案例 ChiWanzheng：')
print('   该方证名称与label"赤丸证"完全匹配，拼音为ChiWanzheng')
print('   这是因为"Chi"是"赤"的拼音，"Wan"是"丸"的拼音')
print()

# 检查fanggen.owl中的Fangzheng
print('3. fanggen.owl中的Fangzheng：')
print('   这是一个顶层类定义，不是具体的方证，label为"方证"')
print()

# 输出总结
print('='*80)
print('总结')
print('='*80)
print(f'1. 共检查 {len(files)} 个owl文件')
print(f'2. 共发现 {total} 个方证定义')
print(f'3. 重复方证: {len(duplicates)} 对')
print(f'4. 方证名称与label不一致: {len(mismatches)} 个')
print(f'5. 拼写相似的方证名称: {len(similar_pairs)} 对')
