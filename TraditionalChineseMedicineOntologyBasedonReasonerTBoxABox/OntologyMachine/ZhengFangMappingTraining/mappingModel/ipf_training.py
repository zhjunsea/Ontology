import pandas as pd
import numpy as np
import csv
import os
import chardet
import math
from collections import defaultdict
import warnings
warnings.filterwarnings('ignore')

# ================================================================
# 0. 智能读取CSV
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

# ================================================================
# 1. 加载与合并数据集
# ================================================================
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

# ================================================================
# 2. IPF 权重训练
# ================================================================
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

# ================================================================
# 3. 生成方证-特征权重矩阵
# ================================================================
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

# ================================================================
# 4. 导出权重文件
# ================================================================
def export_weights(weights, feature_count, total_patterns, pattern_matrix, output_dir='.'):
    weight_df = pd.DataFrame([
        {'feature': feat, 'weight': round(w, 4), 'occurrence': feature_count.get(feat, 0)}
        for feat, w in weights.items()
    ])
    weight_df = weight_df.sort_values('weight', ascending=False)
    weight_df.to_csv(f'{output_dir}/ipf_feature_weights.csv', index=False, encoding='utf-8-sig')
    print(f"特征权重已保存: ipf_feature_weights.csv (共 {len(weight_df)} 条)")

    all_features = list(weights.keys())
    pattern_rows = []
    for item in pattern_matrix:
        row = {
            'pattern_id': item['pattern_id'],
            'pattern_name': item['pattern_name'],
            'liujing': item['liujing'],
            'clause': item['clause'],
            'total_features': item['total_features']
        }
        feat_weight_dict = {fw['feature']: fw['weight'] for fw in item['feature_weights']}
        for feat in all_features:
            row[feat] = feat_weight_dict.get(feat, 0.0)
        pattern_rows.append(row)

    wide_df = pd.DataFrame(pattern_rows)
    wide_df.to_csv(f'{output_dir}/ipf_pattern_weight_matrix.csv', index=False, encoding='utf-8-sig')
    print(f"方证-特征权重矩阵已保存: ipf_pattern_weight_matrix.csv ({len(wide_df)} 行 × {len(wide_df.columns)} 列)")

    summary = []
    for item in pattern_matrix:
        top10 = item['feature_weights'][:10]
        summary.append({
            'pattern_id': item['pattern_id'],
            'pattern_name': item['pattern_name'],
            'liujing': item['liujing'],
            'clause': item['clause'],
            'total_features': item['total_features'],
            'top_features': '; '.join([f"{fw['feature']}({fw['weight']})" for fw in top10])
        })
    summary_df = pd.DataFrame(summary)
    summary_df.to_csv(f'{output_dir}/ipf_pattern_top10_summary.csv', index=False, encoding='utf-8-sig')
    print(f"方证 Top-10 摘要已保存: ipf_pattern_top10_summary.csv")

# ================================================================
# 5. 优化推理引擎（使用 auto_tune.py 调优后的最优参数）
# ================================================================
class OptimizedIPFInference:
    """
    使用 auto_tune.py 调优后的最优参数：
    - weight_cap: 8.0
    - norm_alpha: 0.65
    - min_match_rate: 0.10
    - boost_types: {'mx': 1.4, 'sx': 1.4}
    """
    def __init__(self, pattern_matrix, weights):
        # 硬编码调优后的最优参数
        self.pattern_matrix = pattern_matrix
        self.weights = weights
        self.weight_cap = 8.0
        self.norm_alpha = 0.65
        self.min_match_rate = 0.10
        self.boost_types = {'mx': 1.4, 'sx': 1.4}

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

            # 长度自适应归一化 - 不截断，保留实际值
            confidence = total_score / (max_possible ** self.norm_alpha)

            results.append({
                'pattern_id': pattern_id,
                'pattern_name': pattern_name,
                'liujing': item['liujing'],
                'confidence': round(confidence, 4),
                'total_score': round(total_score, 4),
                'match_rate': round(match_rate, 3),
                'matched_features': matched_count,
                'total_features': total_features
            })

        # 排序：先按confidence降序，若相等则按total_score降序
        results = sorted(results, key=lambda x: (x['confidence'], x['total_score']), reverse=True)
        return results[:top_n]

# ================================================================
# 6. 原始推理引擎（用于对比）
# ================================================================
class LegacyIPFInference:
    def __init__(self, pattern_matrix, weights):
        self.pattern_matrix = pattern_matrix
        self.weights = weights

    def infer(self, patient_features, top_n=5):
        patient_set = set(patient_features)
        results = []
        for item in self.pattern_matrix:
            pattern_id = item['pattern_id']
            pattern_name = item['pattern_name']
            total_score = 0.0
            for fw in item['feature_weights']:
                if fw['feature'] in patient_set:
                    total_score += fw['weight']
            max_possible = sum([fw['weight'] for fw in item['feature_weights']])
            confidence = total_score / max_possible if max_possible > 0 else 0
            if confidence > 0:
                results.append({
                    'pattern_id': pattern_id,
                    'pattern_name': pattern_name,
                    'liujing': item['liujing'],
                    'confidence': round(confidence, 4),
                    'matched_features': len([fw for fw in item['feature_weights'] if fw['feature'] in patient_set])
                })
        results = sorted(results, key=lambda x: x['confidence'], reverse=True)
        return results[:top_n]

# ================================================================
# 7. 主程序
# ================================================================
def main():
    print("=" * 70)
    print("经方 IPF 权重训练 + 优化推理引擎")
    print("使用 auto_tune.py 调优后的最优参数")
    print("参数: weight_cap=8.0, norm_alpha=0.65, min_match_rate=0.10, boost=1.4")
    print("=" * 70)

    # ---- 7.1 加载数据 ----
    print("\n[1] 加载数据...")
    df = load_and_merge_data('伤寒论病症方剂映射.csv', '金匮要略病症方剂映射.csv')
    print(f"有效方证记录数: {len(df)}")
    total_features = len(set([f for sublist in df['feature_list'] for f in sublist]))
    print(f"总特征数（症状+脉+舌+兼夹）: {total_features}")

    # ---- 7.2 训练 IPF 权重 ----
    print("\n[2] 训练 IPF 权重...")
    weights, feature_count, total_patterns = train_ipf_weights(df)
    print(f"总特征数（去重后）: {len(weights)}")
    print(f"总方证数: {total_patterns}")

    # ---- 7.3 构建方证-特征权重矩阵 ----
    print("\n[3] 构建方证-特征权重矩阵...")
    pattern_matrix = build_pattern_weight_matrix(df, weights)

    # ---- 7.4 导出权重文件 ----
    print("\n[4] 导出权重文件...")
    export_weights(weights, feature_count, total_patterns, pattern_matrix)

    # ---- 7.5 显示高权重特征 ----
    print("\n===== Top 10 高权重特征（最具鉴别力）=====")
    sorted_weights = sorted(weights.items(), key=lambda x: x[1], reverse=True)
    for i, (feat, w) in enumerate(sorted_weights[:10]):
        cnt = feature_count.get(feat, 0)
        print(f"{i+1}. {feat} : {w:.4f} (出现于 {cnt}/{total_patterns} 个方证)")

    # ---- 7.6 初始化优化推理引擎 ----
    print("\n[5] 初始化优化推理引擎...")
    optimized_engine = OptimizedIPFInference(pattern_matrix, weights)

    # ---- 7.7 测试推理（太阳中风） ----
    print("\n===== 测试案例1：太阳中风 =====")
    patient1 = ['zz:EHan', 'zz:FaRe', 'zz:HanChu', 'zz:EFeng',
                'zz:TouXiangQiangTong', 'mx:Pulse_Huan']
    print("患者症状: 恶寒、发热、汗出、恶风、头项强痛、脉浮缓")

    result_opt = optimized_engine.infer(patient1, top_n=10)
    print("优化引擎推荐:")
    for r in result_opt:
        print(f"  {r['pattern_name']} ({r['pattern_id']}) 得分:{r['confidence']:.4f} 匹配:{r['matched_features']}/{r['total_features']} 匹配率:{r['match_rate']:.1%}")

    # ---- 7.8 对比原始引擎 ----
    print("\n[对比] 原始引擎（未优化）推荐:")
    legacy_engine = LegacyIPFInference(pattern_matrix, weights)
    result_legacy = legacy_engine.infer(patient1, top_n=5)
    for r in result_legacy:
        print(f"  {r['pattern_name']} ({r['pattern_id']}) 置信度:{r['confidence']:.2%} 匹配特征:{r['matched_features']}个")

    # ---- 7.9 测试案例2：阳明腑实 ----
    print("\n===== 测试案例2：阳明腑实 =====")
    patient2 = ['zz:ChaoRe', 'zz:ZhanYu', 'zz:FuManJuAn', 'zz:DaBianNan',
                'zz:ShouZuJiRanHanChu', 'jj:FoodStagnation']
    print("患者症状: 潮热、谵语、腹满拒按、不大便、手足濈然汗出、食积")
    result_opt2 = optimized_engine.infer(patient2, top_n=10)
    print("优化引擎推荐:")
    for r in result_opt2:
        print(f"  {r['pattern_name']} ({r['pattern_id']}) 得分:{r['confidence']:.4f} 匹配:{r['matched_features']}/{r['total_features']} 匹配率:{r['match_rate']:.1%}")

if __name__ == "__main__":
    main()