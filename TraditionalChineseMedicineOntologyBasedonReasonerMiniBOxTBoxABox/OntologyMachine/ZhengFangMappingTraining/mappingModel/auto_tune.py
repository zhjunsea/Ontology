import pandas as pd
import numpy as np
import csv
import os
import chardet
import math
import itertools
from collections import defaultdict
import warnings
warnings.filterwarnings('ignore')

# ================================================================
# 以下代码直接从 ipf_training.py 复制，确保环境一致
# ================================================================

def smart_read_csv(filepath):
    with open(filepath, 'rb') as f:
        raw = f.read(10000)
        result = chardet.detect(raw)
        encoding = result['encoding'] if result['encoding'] else 'utf-8'
        print(f"检测到文件 {os.path.basename(filepath)} 编码: {encoding}")

    try:
        df = pd.read_csv(filepath, encoding=encoding, engine='python', quotechar='"')
        return df
    except Exception as e:
        print(f"直接读取失败 ({e})，尝试修复CSV格式...")

    fixed_path = filepath.replace('.csv', '_fixed.csv')
    with open(filepath, 'r', encoding=encoding, errors='ignore') as infile, \
            open(fixed_path, 'w', encoding='utf-8-sig', newline='') as outfile:
        reader = csv.reader(infile)
        writer = csv.writer(outfile, quoting=csv.QUOTE_ALL)
        for row in reader:
            if len(row) != 8:
                if len(row) > 8:
                    merged_row = row[:3] + [','.join(row[3:len(row)-4])] + row[len(row)-4:]
                    if len(merged_row) == 8:
                        writer.writerow(merged_row)
                    else:
                        continue
                else:
                    row += [''] * (8 - len(row))
                    writer.writerow(row[:8])
            else:
                writer.writerow(row)

    print(f"修复完成，已生成 {fixed_path}")
    return pd.read_csv(fixed_path, encoding='utf-8-sig')


def load_and_merge_data(shanghan_path, jingui_path):
    df1 = smart_read_csv(shanghan_path)
    df2 = smart_read_csv(jingui_path)
    df = pd.concat([df1, df2], ignore_index=True)

    df['symptom_list'] = df['symptom_list'].fillna('')
    df['pulse_list'] = df['pulse_list'].fillna('')
    df['tongue_list'] = df['tongue_list'].fillna('')
    df['jianjia_list'] = df['jianjia_list'].fillna('')

    df['all_features'] = (df['symptom_list'] + ',' + df['pulse_list'] + ',' +
                          df['tongue_list'] + ',' + df['jianjia_list'])
    df['feature_list'] = df['all_features'].apply(
        lambda x: [f.strip() for f in x.split(',') if f.strip() != '']
    )
    df = df[df['feature_list'].apply(len) > 0].reset_index(drop=True)
    return df


def train_ipf_weights(df):
    total_patterns = len(df)
    feature_count = defaultdict(int)

    for idx, row in df.iterrows():
        unique_features = set(row['feature_list'])
        for feat in unique_features:
            feature_count[feat] += 1

    weights = {}
    for feat, count in feature_count.items():
        weights[feat] = math.log2(total_patterns / count) + 1.0

    return weights, feature_count, total_patterns


def build_pattern_weight_matrix(df, weights):
    result = []
    for idx, row in df.iterrows():
        pattern_id = row['pattern_id']
        pattern_name = row['pattern_name']
        features = row['feature_list']
        liujing = row['liujing']
        clause = row['clause']

        feature_weights = []
        for feat in features:
            w = weights.get(feat, 0.1)
            feature_weights.append({'feature': feat, 'weight': round(w, 4)})
        feature_weights = sorted(feature_weights, key=lambda x: x['weight'], reverse=True)

        result.append({
            'pattern_id': pattern_id,
            'pattern_name': pattern_name,
            'liujing': liujing,
            'clause': clause,
            'total_features': len(features),
            'feature_weights': feature_weights
        })
    return result


class OptimizedIPFInference:
    def __init__(self, pattern_matrix, weights,
                 weight_cap=5.0,
                 norm_alpha=0.85,
                 min_match_rate=0.25,
                 boost_types={'mx': 1.2, 'sx': 1.2}):
        self.pattern_matrix = pattern_matrix
        self.weights = weights
        self.weight_cap = weight_cap
        self.norm_alpha = norm_alpha
        self.min_match_rate = min_match_rate
        self.boost_types = boost_types

    def _get_effective_weight(self, feature):
        raw_w = self.weights.get(feature, 0.1)
        for prefix, factor in self.boost_types.items():
            if feature.startswith(prefix + ':'):
                raw_w *= factor
                break
        return min(raw_w, self.weight_cap)

    def infer(self, patient_features, top_n=5):
        patient_set = set(patient_features)
        results = []

        for item in self.pattern_matrix:
            pattern_id = item['pattern_id']
            pattern_name = item['pattern_name']

            total_score = 0.0
            max_possible = 0.0
            matched_count = 0

            for fw in item['feature_weights']:
                feat = fw['feature']
                w = self._get_effective_weight(feat)
                max_possible += w
                if feat in patient_set:
                    total_score += w
                    matched_count += 1

            if max_possible == 0:
                continue

            total_features = len(item['feature_weights'])
            match_rate = matched_count / total_features if total_features > 0 else 0
            if match_rate < self.min_match_rate:
                continue

            confidence = total_score / (max_possible ** self.norm_alpha)

            results.append({
                'pattern_id': pattern_id,
                'pattern_name': pattern_name,
                'confidence': confidence,
                'total_score': total_score,
                'match_rate': match_rate,
                'matched_features': matched_count,
                'total_features': total_features
            })

        results = sorted(results, key=lambda x: (x['confidence'], x['total_score']), reverse=True)
        return results[:top_n]


# ================================================================
# 修正后的完整测试用例列表
# ================================================================

test_cases = [
    # ===== 太阳病 =====
    # 1. 桂枝汤证（太阳中风）
    (
        ['zz:EHan', 'zz:FaRe', 'zz:HanChu', 'zz:EFeng',
         'zz:TouXiangQiangTong', 'zz:BiMing', 'zz:GanOu', 'mx:Pulse_Huan'],
        'fj:GuiZhiTang'
    ),
    # 2. 麻黄汤证（太阳伤寒）
    (
        ['zz:EHan', 'zz:FaRe', 'zz:WuHan', 'zz:ShenTiTengTong',
         'zz:TouXiangQiangTong', 'zz:KeNi', 'mx:Pulse_Jin'],
        'fj:MaHuangTang'
    ),
    # 3. 葛根汤证（太阳温病）
    (
        ['zz:EHan', 'zz:WuHan', 'zz:TouXiangQiangTong'],
        'fj:GeGenTang'
    ),
    # 4. 大青龙汤证（外寒内热）
    (
        ['zz:FaRe', 'zz:EHan', 'zz:WuHan', 'zz:ShenTiTengTong',
         'zz:FanZao', 'mx:Pulse_Fu_Jin'],
        'fj:DaQingLongTang'
    ),
    # 5. 小青龙汤证（外寒内饮）
    (
        ['zz:GanOu', 'zz:FaRe', 'zz:KeNi', 'zz:XiaoBianBuLi',
         'zz:KeNi', 'jj:WaterRetention'],
        'fj:XiaoQingLongTang'
    ),
    # 6. 五苓散证（蓄水证）
    (
        ['zz:XiaoBianBuLi', 'zz:KeErYuYin', 'zz:FaRe',
         'mx:Pulse_Fu', 'jj:WaterRetention'],
        'fj:WuLingSan'
    ),

    # ===== 阳明病 =====
    # 7. 白虎汤证（阳明经证）
    (
        ['zz:ShenRe', 'zz:DaKe', 'zz:HanChu', 'zz:BuEhanFanEre', 'mx:Pulse_Hong'],
        'fj:BaiHuTang'
    ),
    # 8. 大承气汤证（阳明腑实）
    (
        ['zz:ChaoRe', 'zz:ZhanYu', 'zz:FuManJuAn', 'zz:DaBianNan',
         'zz:ShouZuJiRanHanChu', 'mx:Pulse_Chi', 'jj:FoodStagnation'],
        'fj:DaChengQiTang'
    ),
    # 9. 茵陈蒿汤证（湿热黄疸）
    (
        ['zz:HuangDan', 'zz:XiaoBianBuLi', 'zz:FuMan', 'jj:Re'],
        'fj:YinChenHaoTang'
    ),

    # ===== 少阳病 =====
    # 10. 小柴胡汤证（少阳本证）
    (
        ['zz:WangLaiHanRe', 'zz:XiongXieKuMan', 'zz:MomoBuYuYinShi',
         'zz:XinFanXiOu', 'zz:KouKu', 'zz:YanGan', 'zz:MuXuan', 'mx:Pulse_Xian'],
        'fj:XiaoChaiHuTang'
    ),
    # 11. 大柴胡汤证（少阳兼阳明）
    (
        ['zz:GanOu', 'zz:XinXiaPi', 'zz:FuManJuAn', 'zz:ChaoRe', 'jj:QiStagnation'],
        'fj:DaChaiHuTang'
    ),

    # ===== 太阴病 =====
    # 12. 理中汤证（太阴虚寒）—— 全部特征！
    (
        ['zz:FuManErTu', 'zz:ShiBuXia', 'zz:ZiLi', 'zz:ShiFuZiTong',
         'zz:KeErBuYuYin', 'mx:Pulse_Chen_Chi', 'sx:SheShangTaiHua', 'jj:Han'],
        'fj:LiZhongTang'
    ),
    # 13. 小建中汤证（虚劳腹痛）
    (
        ['zz:FuTong', 'zz:XuLaoLiJi', 'zz:XinJi', 'zz:YiJing',
         'zz:ShenTiTengTong', 'zz:ShouZuFanRe', 'zz:YanGan', 'mx:Pulse_Xu_Chi'],
        'fj:XiaoJianZhongTang'
    ),

    # ===== 少阴病 =====
    # 14. 四逆汤证（少阴寒化）—— 全部特征！
    (
        ['zz:SiZhiJueNi', 'zz:XiaLiQingGu', 'zz:DanYuMei',
         'mx:Pulse_Wei', 'mx:Pulse_Chen'],
        'fj:SiNiTang'
    ),
    # 15. 黄连阿胶汤证（少阴热化）
    (
        ['zz:XinFan', 'zz:BuDeWo', 'mx:Pulse_Xi_Shu', 'sx:SheJiang'],
        'fj:HuangLianEJiaoTang'
    ),
    # 16. 真武汤证（阳虚水泛）
    (
        ['zz:FuTong', 'zz:XiaoBianBuLi', 'zz:SiZhiChenZhong', 'zz:ZiXiaLi'],
        'fj:ZhenWuTang'
    ),

    # ===== 厥阴病 =====
    # 17. 乌梅丸证（厥阴本证）
    (
        ['zz:XiaoKe', 'zz:QiShangZhuangXin', 'zz:XinZhongAoNong',
         'zz:JiErBuYuShi', 'zz:ShiZeTuHui', 'mx:Pulse_Xian'],
        'fj:WuMeiWan'
    ),
]


# ================================================================
# 自动调优核心
# ================================================================

def run_auto_tune():
    print("=" * 70)
    print("经方推理引擎自动调优程序（修正版）")
    print("测试用例数：17个，覆盖六经全部核心方证")
    print("=" * 70)

    # ---- 加载数据 ----
    print("\n[1] 加载数据...")
    df = load_and_merge_data('伤寒论病症方剂映射.csv', '金匮要略病症方剂映射.csv')
    print(f"有效方证记录数: {len(df)}")

    # ---- 训练 IPF 权重 ----
    print("\n[2] 训练 IPF 权重...")
    weights, feature_count, total_patterns = train_ipf_weights(df)
    print(f"总特征数: {len(weights)}")

    # ---- 构建方证矩阵 ----
    print("\n[3] 构建方证-特征权重矩阵...")
    pattern_matrix = build_pattern_weight_matrix(df, weights)

    # ---- 定义参数搜索空间 ----
    param_grid = {
        'weight_cap': [4.0, 5.0, 6.0, 7.0, 8.0],
        'norm_alpha': [0.65, 0.70, 0.75, 0.80, 0.85, 0.90],
        'min_match_rate': [0.10, 0.15, 0.20, 0.25, 0.30],
        'boost_factor': [1.0, 1.1, 1.2, 1.3, 1.4]
    }

    total_combinations = (len(param_grid['weight_cap']) *
                          len(param_grid['norm_alpha']) *
                          len(param_grid['min_match_rate']) *
                          len(param_grid['boost_factor']))
    print(f"\n[4] 开始搜索 ({total_combinations} 种组合)...")

    # ---- 评估函数 ----
    def evaluate_params(cap, alpha, rate, boost):
        engine = OptimizedIPFInference(
            pattern_matrix,
            weights,
            weight_cap=cap,
            norm_alpha=alpha,
            min_match_rate=rate,
            boost_types={'mx': boost, 'sx': boost}
        )

        total_fitness = 0.0
        case_results = []

        for symptoms, expected_id in test_cases:
            results = engine.infer(symptoms, top_n=10)

            expected_conf = 0.0
            expected_rank = -1
            best_other_conf = 0.0

            for idx, r in enumerate(results):
                if r['pattern_id'] == expected_id:
                    expected_conf = r['confidence']
                    expected_rank = idx + 1
                else:
                    if r['confidence'] > best_other_conf:
                        best_other_conf = r['confidence']

            if expected_rank == -1:
                fitness = -50.0
            elif expected_rank == 1:
                gap = expected_conf - best_other_conf
                fitness = 20.0 * gap
            else:
                rank_penalty = -10.0 * (expected_rank - 1)
                gap = expected_conf - best_other_conf
                fitness = rank_penalty + gap

            total_fitness += fitness
            case_results.append({
                'case_idx': len(case_results),
                'expected_id': expected_id,
                'rank': expected_rank,
                'conf': expected_conf,
                'best_other_conf': best_other_conf,
                'fitness': fitness
            })

        return total_fitness, case_results

    # ---- 执行搜索 ----
    best_score = -float('inf')
    best_params = None
    best_details = None

    count = 0
    for cap in param_grid['weight_cap']:
        for alpha in param_grid['norm_alpha']:
            for rate in param_grid['min_match_rate']:
                for boost in param_grid['boost_factor']:
                    count += 1
                    if count % 50 == 0:
                        print(f"  进度: {count}/{total_combinations}")

                    fitness, details = evaluate_params(cap, alpha, rate, boost)
                    if fitness > best_score:
                        best_score = fitness
                        best_params = (cap, alpha, rate, boost)
                        best_details = details

    print(f"\n[5] 搜索完成！最佳适应度得分: {best_score:.4f}")

    # ---- 输出最佳参数 ----
    cap, alpha, rate, boost = best_params
    print("\n" + "=" * 70)
    print("最优参数组合")
    print("=" * 70)
    print(f"  weight_cap       : {cap:.1f}")
    print(f"  norm_alpha       : {alpha:.2f}")
    print(f"  min_match_rate   : {rate:.2f}")
    print(f"  boost_factor     : {boost:.1f} (脉象、舌象统一提升)")
    print("=" * 70)

    # ---- 测试最佳参数的具体表现 ----
    print("\n[6] 最佳参数在各测试用例上的表现:")
    engine_best = OptimizedIPFInference(
        pattern_matrix,
        weights,
        weight_cap=cap,
        norm_alpha=alpha,
        min_match_rate=rate,
        boost_types={'mx': boost, 'sx': boost}
    )

    for i, (symptoms, expected_id) in enumerate(test_cases, 1):
        results = engine_best.infer(symptoms, top_n=5)
        try:
            pattern_name = df[df['pattern_id'] == expected_id]['pattern_name'].iloc[0]
        except IndexError:
            pattern_name = expected_id
        print(f"\n  测试用例 {i}: {pattern_name} ({expected_id})")
        for idx, r in enumerate(results, 1):
            mark = "✓" if r['pattern_id'] == expected_id else " "
            print(f"    {idx}. {mark} {r['pattern_name']} ({r['pattern_id']}) 得分:{r['confidence']:.4f} 匹配:{r['matched_features']}/{r['total_features']}")
        # 如果期望方证未进入前5，单独提示
        found = any(r['pattern_id'] == expected_id for r in results)
        if not found:
            print(f"    ⚠️ 期望方证 {pattern_name} 未进入前5！")

    # ---- 输出推荐参数 ----
    print("\n" + "=" * 70)
    print("推荐在 ipf_training.py 中使用的初始化参数")
    print("=" * 70)
    print(f"""
optimized_engine = OptimizedIPFInference(
    pattern_matrix, 
    weights,
    weight_cap={cap:.1f},
    norm_alpha={alpha:.2f},
    min_match_rate={rate:.2f},
    boost_types={{'mx': {boost:.1f}, 'sx': {boost:.1f}}}
)
    """)
    print("=" * 70)


if __name__ == "__main__":
    run_auto_tune()