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

# 更完整的拼音映射
pinyin_map = {
    '桂': 'gui', '枝': 'zhi', '汤': 'tang', '证': 'zheng', '类': 'lei', '方': 'fang',
    '加': 'jia', '葛': 'ge', '根': 'gen', '厚': 'hou', '朴': 'po', '杏': 'xing',
    '子': 'zi', '附': 'fu', '去': 'qu', '芍': 'shao', '药': 'yao', '蜀': 'shu',
    '漆': 'qi', '牡': 'mu', '蛎': 'li', '龙': 'long', '骨': 'gu', '救': 'jiu',
    '逆': 'ni', '茯': 'fu', '苓': 'ling', '白': 'bai', '术': 'zhu', '甘': 'gan',
    '草': 'cao', '新': 'xin', '人': 'ren', '参': 'shen', '栝': 'gua', '楼': 'lou',
    '麻': 'ma', '黄': 'huang', '大': 'da', '青': 'qing', '小': 'xiao',
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
    '枳': 'zhi', '实': 'shi', '薤': 'xie', '胶': 'jiao', '艾': 'ai',
    '温': 'wen', '经': 'jing', '土': 'tu', '瓜': 'gua', '根': 'gen', '柏': 'bai',
    '泻': 'xie', '心': 'xin', '遂': 'sui', '物': 'wu', '升': 'sheng',
    '雄': 'xiong', '熏': 'xun', '防': 'fang', '己': 'ji', '椒': 'jiao', '苈': 'li', '葶': 'ting',
    '枣': 'zao', '肺': 'fei', '千': 'qian', '金': 'jin', '苇': 'wei', '茎': 'jing',
    '麦': 'mai', '门': 'men', '橘': 'ju', '皮': 'pi', '茹': 'ru', '紫': 'zi',
    '诃': 'he', '梨': 'li', '勒': 'le', '一': 'yi', '十': 'shi', '续': 'xu', '命': 'ming',
    '鳖': 'bie', '甲': 'jia', '煎': 'jian', '天': 'tian', '薯': 'shu', '蓣': 'yu',
    '蒲': 'pu', '灰': 'hui', '戎': 'rong', '盐': 'yan', '蜘': 'zhi', '蛛': 'zhu',
    '蛇': 'she', '床': 'chuang', '狼': 'lang', '牙': 'ya', '矾': 'fan',
    '炙': 'zhi', '王': 'wang', '不': 'bu', '留': 'liu', '行': 'xing',
    '排': 'pai', '脓': 'nong', '粉': 'fen', '蜜': 'mi', '鸡': 'ji',
    '侯': 'hou', '氏': 'shi', '黑': 'hei', '风': 'feng', '引': 'yin', '地': 'di',
    '术': 'zhu', '承': 'cheng', '气': 'qi', '调': 'tiao', '胃': 'wei', '桃': 'tao', '核': 'he',
    '丹': 'dan', '甘': 'gan', '遂': 'sui', '朴': 'po', '麻': 'ma', '仁': 'ren',
    '硝': 'xiao', '黄': 'huang', '连': 'lian', '芩': 'qin', '甘': 'gan', '草': 'cao',
    '人': 'ren', '参': 'shen', '泻': 'xie', '心': 'xin', '白': 'bai', '头': 'tou',
    '翁': 'weng', '甘': 'gan', '草': 'cao', '阿': 'a', '胶': 'jiao',
    '内': 'nei', '补': 'bu', '当': 'dang', '归': 'gui', '射': 'she', '干': 'gan',
    '麻': 'ma', '黄': 'huang', '附': 'fu', '子': 'zi', '细': 'xi', '辛': 'xin',
    '甘': 'gan', '草': 'cao', '大': 'da', '枣': 'zao',
}

def chinese_to_pinyin(text):
    """将中文转换为拼音（首字母大写，其余小写）"""
    result = []
    for char in text:
        if char in pinyin_map:
            py = pinyin_map[char]
            result.append(py[0].upper() + py[1:])
        else:
            result.append(f'[{char}]')  # 标记未知字符
    return ''.join(result)

# 分析
print('='*80)
print('方证名称与拼音一致性分析报告')
print('='*80)
print()

# 检查每个方证
mismatches = []
unknown_chars = set()

for fname, name, label in fangzheng_data:
    if not label or label == '方证':
        continue
    
    # 从label提取中文名（去掉"证"字）
    if label.endswith('证'):
        zhongwen = label[:-1]
    else:
        zhongwen = label
    
    # 期望的拼音名称
    expected_pinyin = chinese_to_pinyin(zhongwen) + 'zheng'
    
    # 检查是否有未知字符
    has_unknown = '[' in expected_pinyin
    
    if has_unknown:
        # 提取未知字符
        for char in zhongwen:
            if char not in pinyin_map:
                unknown_chars.add(char)
    
    # 比较
    if name != expected_pinyin:
        mismatches.append((fname, name, label, expected_pinyin, has_unknown))

print('一、各文件方证总数统计')
print('-'*60)
from collections import Counter
file_counts = Counter([d[0] for d in fangzheng_data])
for fname in files:
    print(f'{fname}: {file_counts[fname]} 个方证')
print(f'总计: {len(fangzheng_data)} 个方证')
print()

print('二、重复方证列表')
print('-'*60)
name_counts = Counter([d[1] for d in fangzheng_data])
duplicates = {k: v for k, v in name_counts.items() if v > 1}
if duplicates:
    for name, count in duplicates.items():
        print(f'{name}: 出现 {count} 次')
else:
    print('未发现重复的方证名称')
print()

print('三、方证名称与label拼音一致性检查')
print('-'*60)

# 分离已知和未知的mismatches
known_mismatches = [m for m in mismatches if not m[4]]
unknown_mismatches = [m for m in mismatches if m[4]]

print(f'1. 拼音映射完整的方证: {len(fangzheng_data) - len(mismatches)} 个')
print(f'2. 拼音映射不完整（有未知汉字）: {len(unknown_mismatches)} 个')
print(f'3. 已知拼音但名称不一致: {len(known_mismatches)} 个')
print()

if unknown_chars:
    print('四、拼音映射中缺失的汉字')
    print('-'*60)
    print('以下汉字在拼音映射中缺失：')
    print(', '.join(sorted(unknown_chars)))
    print()

print('五、拼写相似但不完全相同的方证名称（相似度>90%）')
print('-'*60)

# 获取所有方证名称
all_names = list(set([d[1] for d in fangzheng_data]))
similar_pairs = []

for i in range(len(all_names)):
    for j in range(i+1, len(all_names)):
        name1 = all_names[i]
        name2 = all_names[j]
        ratio = SequenceMatcher(None, name1, name2).ratio()
        if ratio >= 0.90:
            similar_pairs.append((name1, name2, ratio))

similar_pairs.sort(key=lambda x: -x[2])

print(f'发现 {len(similar_pairs)} 对方证名称高度相似（相似度>=90%）：')
for name1, name2, ratio in similar_pairs[:50]:
    print(f'  {name1}')
    print(f'  <-> {name2}')
    print(f'  相似度: {ratio:.2%}')
    print()

print('='*80)
print('总结')
print('='*80)
print(f'1. 共检查 {len(files)} 个owl文件，{len(fangzheng_data)} 个方证定义')
print(f'2. 重复方证: {len(duplicates)} 对')
print(f'3. 方证名称与label不一致: {len(known_mismatches)} 个（拼音映射完整）')
print(f'4. 拼音映射缺失的汉字: {len(unknown_chars)} 个')
print(f'5. 拼写相似的方证名称: {len(similar_pairs)} 对（相似度>=90%）')
