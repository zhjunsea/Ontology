import pandas as pd
import numpy as np
import csv
import os
import chardet
from sklearn.model_selection import LeaveOneOut
from sklearn.preprocessing import MultiLabelBinarizer, LabelEncoder
from sklearn.linear_model import LogisticRegression
from sklearn.multiclass import OneVsRestClassifier  # 新增导入
from sklearn.metrics import accuracy_score, f1_score
from itertools import combinations
import warnings
warnings.filterwarnings('ignore')

# ================================================================
# 0. 智能读取CSV（自动修复编码与分隔问题）
# ================================================================
def smart_read_csv(filepath):
    """自动检测编码，并修正CSV格式（字段加引号）"""
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
# 2. 增强特征工程：生成二阶交互特征
# ================================================================
def create_interaction_features(X, feature_names, top_k=30):
    freq = np.array(X.sum(axis=0)).flatten()
    top_indices = np.argsort(freq)[-top_k:].tolist()
    top_feats = [feature_names[i] for i in top_indices]

    interaction_list = []
    for (i, j) in combinations(range(len(top_feats)), 2):
        new_feat = X[:, top_indices[i]] * X[:, top_indices[j]]
        interaction_list.append(new_feat)

    if interaction_list:
        X_inter = np.column_stack([X] + interaction_list)
        inter_names = [f"{top_feats[i]}__AND__{top_feats[j]}"
                       for (i, j) in combinations(range(len(top_feats)), 2)]
        new_feature_names = list(feature_names) + inter_names
    else:
        X_inter = X
        new_feature_names = feature_names

    return X_inter, new_feature_names

# ================================================================
# 3. 主训练流程
# ================================================================
def main():
    print("=" * 70)
    print("经方精确权重训练 (L1逻辑回归 + 二阶交互特征)")
    print("解决“非线性协同效应”问题，同时保留可解释权重")
    print("=" * 70)

    df = load_and_merge_data('伤寒论病症方剂映射_fixed.csv', '金匮要略病症方剂映射_fixed.csv')
    print(f"有效方证记录数: {len(df)}")

    mlb = MultiLabelBinarizer()
    X_base = mlb.fit_transform(df['feature_list'])
    feature_names_base = mlb.classes_

    le = LabelEncoder()
    y = le.fit_transform(df['pattern_id'])
    target_names = le.classes_

    print("\n生成二阶交互特征 (高频特征组合)...")
    X, feature_names = create_interaction_features(X_base, feature_names_base, top_k=30)
    print(f"原始特征: {len(feature_names_base)}, 交互后总特征: {len(feature_names)}")

    print("\n训练 L1 逻辑回归 (含非线性交互权重)...")
    # 使用 OneVsRestClassifier 包装 liblinear 实现多分类
    base_lr = LogisticRegression(
        penalty='l1',
        solver='liblinear',
        C=0.5,
        max_iter=3000,
        random_state=42,
        class_weight='balanced'
    )
    model = OneVsRestClassifier(base_lr)
    model.fit(X, y)

    print("\n留一交叉验证 (LOOCV) 评估...")
    loo = LeaveOneOut()
    y_pred_loo = []
    for train_idx, test_idx in loo.split(X):
        X_train, X_test = X[train_idx], X[test_idx]
        y_train, y_test = y[train_idx], y[test_idx]
        temp_base = LogisticRegression(
            penalty='l1',
            solver='liblinear',
            C=0.5,
            max_iter=3000,
            random_state=42
        )
        temp_model = OneVsRestClassifier(temp_base)
        temp_model.fit(X_train, y_train)
        y_pred_loo.append(temp_model.predict(X_test)[0])

    loo_acc = accuracy_score(y, y_pred_loo)
    loo_f1 = f1_score(y, y_pred_loo, average='macro')
    print(f"LOOCV 准确率 (Top-1): {loo_acc:.4f}")
    print(f"LOOCV F1-score (macro): {loo_f1:.4f}")

    # 提取权重：OneVsRestClassifier 的 estimators_ 是每个二分类器
    coefficients = []
    for estimator in model.estimators_:
        coefficients.append(estimator.coef_[0])
    coefficients = np.array(coefficients)

    weight_records = []
    for i, target_id in enumerate(target_names):
        pattern_name = df[df['pattern_id'] == target_id]['pattern_name'].iloc[0]
        for j, feat in enumerate(feature_names):
            w = coefficients[i, j]
            if abs(w) > 1e-6:
                weight_records.append({
                    'pattern_id': target_id,
                    'pattern_name': pattern_name,
                    'feature': feat,
                    'weight': round(w, 4)
                })

    weight_df = pd.DataFrame(weight_records)
    weight_df['abs_weight'] = weight_df['weight'].abs()
    weight_df = weight_df.sort_values(['pattern_id', 'abs_weight'], ascending=[True, False])
    weight_df.to_csv('precise_weights_with_interactions.csv', index=False, encoding='utf-8-sig')
    print(f"\n权重已保存 (共 {len(weight_df)} 条非零权重记录)")

    print("\n===== 非线性交互特征权重示例 (大青龙汤) =====")
    daqing = weight_df[weight_df['pattern_id'] == 'fj:DaQingLongTang']
    inter_weights = daqing[daqing['feature'].str.contains('__AND__')].head(5)
    if not inter_weights.empty:
        for _, row in inter_weights.iterrows():
            print(f"  组合特征: {row['feature']} -> 权重: {row['weight']:.3f}")
    else:
        print("  未发现显著的交互特征（L1正则化已将其压缩为0，线性组合已足够）。")

    import joblib
    joblib.dump(model, "lv1_model.pkl")
    joblib.dump(mlb, "mlb.pkl")
    joblib.dump(le, "label_encoder.pkl")
    print("\n模型已保存: lv1_model.pkl, mlb.pkl, label_encoder.pkl")

if __name__ == "__main__":
    main()