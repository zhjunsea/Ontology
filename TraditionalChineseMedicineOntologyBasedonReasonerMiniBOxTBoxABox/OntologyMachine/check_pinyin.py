# -*- coding: utf-8 -*-
import re
import os
from collections import defaultdict

# 简单的汉字到拼音映射（常用字）
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
    '抵': 'di', '当': 'dang', '归': 'gui', '逆': 'ni', '通': 'tong', '脉': 'mai',
    '猪': 'zhu', '胆': 'dan', '汁': 'zhi', '干': 'gan', '姜': 'jiang', '桔': 'jie',
    '梗': 'geng', '苦': 'ku', '酒': 'jiu', '半': 'ban', '散': 'san', '及': 'ji',
    '猪': 'zhu', '肤': 'fu', '桃': 'tao', '花': 'hua', '理': 'li', '中': 'zhong',
    '赤': 'chi', '石': 'shi', '脂': 'zhi', '禹': 'yu', '余': 'yu', '粮': 'liang',
    '真': 'zhen', '武': 'wu', '附': 'fu', '子': 'zi', '苓': 'ling', '桂': 'gui',
    '五': 'wu', '味': 'wei', '辛': 'xin', '姜': 'jiang', '杏': 'xing', '仁': 'ren',
    '黄': 'huang', '芪': 'qi', '芍': 'shao', '药': 'yao', '知': 'zhi', '母': 'mu',
    '建': 'jian', '中': 'zhong', '射': 'she', 'gan': '干', '麻': 'ma', '细': 'xi',
    '辛': 'xin', '乌': 'wu', '头': 'tou', '吴': 'wu', '茱': 'zhu', '萸': 'yu',
    '白': 'bai', '头': 'tou', '翁': 'weng', '梅': 'mei', '芩': 'qin', '柴': 'chai',
    '胡': 'hu', '芒': 'mang', '硝': 'xiao', '枳': 'zhi', '实': 'shi', '薤': 'xie',
    '白': 'bai', '酒': 'jiu', '胶': 'jiao', '艾': 'ai', '温': 'wen', '经': 'jing',
    '土': 'tu', '瓜': 'gua', '根': 'gen', '柏': 'bai', '叶': 'ye', '泻': 'xie',
    '心': 'xin', '黄': 'huang', '连': 'lian', '大': 'da', '黄': 'huang', '甘': 'gan',
    '遂': 'sui', '厚': 'hou', '朴': 'po', '物': 'wu', '麻': 'ma', '仁': 'ren',
    '升': 'sheng', '麻': 'ma', '雄': 'xiong', 'huang': '黄', '熏': 'xun',
    '防': 'fang', '己': 'ji', '己': 'ji', '椒': 'jiao', '苈': 'li', '葶': 'ting',
    '枣': 'zao', '肺': 'fei', '泻': 'xie', '千': 'qian', '金': 'jin', '苇': 'wei',
    '茎': 'jing', '麦': 'mai', '门': 'men', '冬': 'dong', '橘': 'ju', '皮': 'pi',
    '茹': 'ru', '紫': 'zi', '参': 'can', '诃': 'he', '梨': 'li', '勒': 'le',
    '一': 'yi', '物': 'wu', '瓜': 'gua', '蒂': 'di', '甘': 'gan', '遂': 'sui',
    '十': 'shi', '枣': 'zao', '己': 'ji', '椒': 'jiao', '苈': 'li', '黄': 'huang',
    '丸': 'wan', '续': 'xu', '命': 'ming', '升': 'sheng', '麻': 'ma', '鳖': 'bie',
    '甲': 'jia', '煎': 'jian', '牡': 'mu', '蛎': 'li', '天': 'tian', '雄': 'xiong',
    '散': 'san', '薯': 'shu', '蓣': 'yu', '蒲': 'pu', '灰': 'hui', '滑': 'hua',
    '石': 'shi', '鱼': 'yu', '戎': 'rong', '盐': 'yan', '蜘': 'zhi', '蛛': 'zhu',
    '蛇': 'she', '床': 'chuang', '子': 'zi', '狼': 'lang', '牙': 'ya', '温': 'wen',
    '胆': 'dan', '硝': 'xiao', '矾': 'fan', '石': 'shi', '橘': 'ju', '枳': 'zhi',
    '姜': 'jiang', '文': 'wen', '蛤': 'ha', '升': 'sheng', '麻': 'ma', '雄': 'xiong',
    '黄': 'huang', '蜀': 'shu', '漆': 'qi', '苦': 'ku', '参': 'shen', '雄': 'xiong',
    '熏': 'xun', '方': 'fang', '蜀': 'shu', '漆': 'qi', '散': 'san',
    '头': 'tou', '风': 'feng', '摩': 'mo', '矾': 'fan', '石': 'shi', '枳': 'zhi',
    '术': 'zhu', '炙': 'zhi', '甘': 'gan', '草': 'cao', '麦': 'mai', '甘': 'gan',
    '大': 'da', '枣': 'zao', '王': 'wang', '不': 'bu', '留': 'liu', '行': 'xing',
    '排': 'pai', '脓': 'nong', '粉': 'fen', '蜜': 'mi', '鸡': 'ji', '子': 'zi',
    '侯': 'hou', '氏': 'shi', '黑': 'hei', '散': 'san', '风': 'feng', '引': 'yin',
    '防': 'fang', '己': 'ji', '地': 'di', '黄': 'huang', '三': 'san', '黄': 'huang',
    '术': 'zhu', '附': 'fu', '紫': 'zi', '参': 'shen', '诃': 'he', '梨': 'li',
    '勒': 'le', '一': 'yi', '物': 'wu', '瓜': 'gua', '蒂': 'di', '甘': 'gan',
    '遂': 'sui', '半': 'ban', '夏': 'xia', '十': 'shi', '枣': 'zao', '己': 'ji',
    '椒': 'jiao', '苈': 'li', '黄': 'huang', '丸': 'wan', '续': 'xu', '命': 'ming',
    '升': 'sheng', '麻': 'ma', '鳖': 'bie', '甲': 'jia', '煎': 'jian', '牡': 'mu',
    '蛎': 'li', '天': 'tian', '雄': 'xiong', '散': 'san', '薯': 'shu', '蓣': 'yu',
    '蒲': 'pu', '灰': 'hui', '滑': 'hua', '石': 'shi', '鱼': 'yu', '戎': 'rong',
    '盐': 'yan', '蜘': 'zhi', '蛛': 'zhu', '蛇': 'she', '床': 'chuang', '子': 'zi',
    '狼': 'lang', '牙': 'ya', '温': 'wen', '胆': 'dan', '硝': 'xiao', '矾': 'fan',
    '石': 'shi', '橘': 'ju', '枳': 'zhi', '姜': 'jiang', '文': 'wen', '蛤': 'ha',
    '升': 'sheng', '麻': 'ma', '雄': 'xiong', '黄': 'huang', '蜀': 'shu', '漆': 'qi',
    '苦': 'ku', '参': 'shen', '雄': 'xiong', '熏': 'xun', '方': 'fang', '蜀': 'shu',
    '漆': 'qi', '散': 'san',
}

def chinese_to_pinyin(text):
    """将中文转换为拼音（首字母大写，其余小写）"""
    result = []
    for char in text:
        if char in pinyin_map:
            py = pinyin_map[char]
            # 首字母大写
            result.append(py[0].upper() + py[1:])
        else:
            result.append(char)
    return ''.join(result)

# 读取所有方证数据
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

# 分析拼音一致性
print('五、方证名称与拼音一致性检查')
print('='*80)
print(f'{"文件名":<25} {"方证名称":<40} {"Label":<20} {"期望拼音":<40} {"是否一致"}')
print('-'*80)

mismatches = []
for fname, name, label in fangzheng_data:
    if not label:
        continue
    
    # 从label提取中文名（去掉"证"字）
    if label.endswith('证'):
        zhongwen = label[:-1]  # 去掉"证"
    else:
        zhongwen = label
    
    # 期望的拼音名称（首字母大写，其余小写）
    expected_pinyin = chinese_to_pinyin(zhongwen) + 'zheng'
    
    # 比较
    is_match = name == expected_pinyin
    
    if not is_match:
        mismatches.append((fname, name, label, expected_pinyin))
    
    status = '✓' if is_match else '✗'
    print(f'{fname:<25} {name:<40} {label:<20} {expected_pinyin:<40} {status}')

print()
print('六、方证名称与label不一致的列表')
print('='*80)
if mismatches:
    for fname, name, label, expected in mismatches:
        print(f'文件: {fname}')
        print(f'  方证名称: {name}')
        print(f'  Label: {label}')
        print(f'  期望拼音: {expected}')
        print()
else:
    print('所有方证名称与label拼音一致')
