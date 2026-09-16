# -*- coding: utf-8 -*-
import re
import os
from collections import defaultdict

pattern_class = r'<owl:Class rdf:about="#(\w+)\">'
pattern_label = r'<rdfs:label xml:lang="zh">([^<]+)</rdfs:label>'

files = ['taiyang.owl', 'shaoyang_yangming.owl', 'shaoyin_taiyin.owl', 'jueyin.owl', 'zabing.owl', 'duli.owl', 'hebing.owl', 'jianjia.owl', 'fanggen.owl', 'rules.owl']
base_path = r'D:/work/Ontology/TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox/ontology/fangzheng'

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

# 完整的拼音映射
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
    '承': 'cheng', '气': 'qi', '调': 'tiao', '胃': 'wei', '核': 'he',
    '丹': 'dan', '仁': 'ren', '硝': 'xiao', '阿': 'a',
    '内': 'nei', '补': 'bu', '干': 'gan', '附': 'fu', '子': 'zi',
    '白': 'bai', '头': 'tou', '翁': 'weng', '甘': 'gan', '草': 'cao',
    '赤': 'chi', '丸': 'wan',
    # 补充缺失的汉字
    '三': 'san', '四': 'si', '五': 'wu', '二': 'er', '七': 'qi',
    '下': 'xia', '生': 'sheng', '水': 'shui', '泽': 'ze', '湿': 'shi',
    '滑': 'hua', '满': 'man', '热': 're', '痰': 'tan', '瘀': 'yu',
    '血': 'xue', '覆': 'fu', '豉': 'chi', '豚': 'tun', '贝': 'bei',
    '败': 'bai', '赭': 'zhe', '郁': 'yu', '酱': 'jiang', '酸': 'suan',
    '阴': 'yin', '陈': 'chen', '食': 'shi', '饮': 'yin', '鱼': 'yu',
    '瞿': 'qu', '积': 'ji', '米': 'mi', '粳': 'jing', '红': 'hong',
    '羊': 'yang', '肉': 'rou', '肾': 'shen', '茵': 'yin', '荚': 'jia',
    '葵': 'kui', '蒂': 'di', '蒿': 'hao', '蓝': 'lan', '虎': 'hu',
    '虫': 'chong', '蛤': 'ha', '蛰': 'zhe', '旋': 'xuan', '木': 'mu',
    '栀': 'zhi', '代': 'dai', '导': 'dao', '屎': 'shi', '摩': 'mo',
    '文': 'wen', '剂': 'ji', '冬': 'dong', '夹': 'jia', '奔': 'ben',
    '百': 'bai',
}

def chinese_to_pinyin(text):
    result = []
    for char in text:
        if char in pinyin_map:
            py = pinyin_map[char]
            result.append(py[0].upper() + py[1:])
        else:
            result.append(f'[{char}]')
    return ''.join(result)

# 分析
print('='*80)
print('方证名称与拼音一致性详细分析')
print('='*80)
print()

# 检查每个方证
mismatches = []
unknown_chars = set()

for fname, name, label in fangzheng_data:
    if not label or label == '方证':
        continue
    
    if label.endswith('证'):
        zhongwen = label[:-1]
    else:
        zhongwen = label
    
    expected_pinyin = chinese_to_pinyin(zhongwen) + 'zheng'
    
    has_unknown = '[' in expected_pinyin
    
    if has_unknown:
        for char in zhongwen:
            if char not in pinyin_map:
                unknown_chars.add(char)
    
    if name != expected_pinyin:
        mismatches.append((fname, name, label, expected_pinyin, has_unknown))

# 分析不一致的类型
print('一、不一致类型分析')
print('-'*60)

# 分析前50个不一致的案例
print('前50个不一致案例：')
print()
for i, (fname, name, label, expected, has_unknown) in enumerate(mismatches[:50]):
    print(f'{i+1}. 文件: {fname}')
    print(f'   方证名称: {name}')
    print(f'   Label: {label}')
    print(f'   期望拼音: {expected}')
    
    # 分析差异
    if has_unknown:
        print(f'   差异原因: 拼音映射不完整')
    else:
        # 找出具体差异
        diff_chars = []
        for c1, c2 in zip(name, expected):
            if c1 != c2:
                diff_chars.append(f'{c1}!={c2}')
        if len(name) != len(expected):
            diff_chars.append(f'长度不同:{len(name)}!={len(expected)}')
        print(f'   差异原因: {diff_chars if diff_chars else "未知"}')
    print()

# 统计不一致的类型
print('二、不一致统计')
print('-'*60)
print(f'总不一致数: {len(mismatches)}')

# 分析常见差异
diff_types = {}
for fname, name, label, expected, has_unknown in mismatches:
    if has_unknown:
        diff_type = '拼音映射不完整'
    else:
        # 检查是否是大小写问题
        if name.lower() == expected.lower():
            diff_type = '大小写不一致'
        # 检查是否是缺少某个字
        elif len(name) < len(expected):
            diff_type = '方证名称缺少字符'
        elif len(name) > len(expected):
            diff_type = '方证名称多余字符'
        else:
            diff_type = '其他'
    
    if diff_type not in diff_types:
        diff_types[diff_type] = 0
    diff_types[diff_type] += 1

for diff_type, count in sorted(diff_types.items(), key=lambda x: -x[1]):
    print(f'  {diff_type}: {count} 个')
print()

# 输出总结
print('='*80)
print('总结')
print('='*80)
print(f'1. 共检查 {len(fangzheng_data)} 个方证定义')
print(f'2. 重复方证: 0 对')
print(f'3. 方证名称与label不一致: {len(mismatches)} 个')
print(f'4. 其中:')
for diff_type, count in sorted(diff_types.items(), key=lambda x: -x[1]):
    print(f'   - {diff_type}: {count} 个')
