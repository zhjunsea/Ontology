import pandas as pd
import numpy as np
from sklearn.model_selection import LeaveOneOut, cross_val_score
from sklearn.preprocessing import MultiLabelBinarizer, LabelEncoder
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score
from itertools import combinations
import warnings
warnings.filterwarnings('ignore')

# ================================================================
# 1. 加载与合并数据集
# ================================================================
def load_and_merge_data(shanghan_path, jingui_path):
    df1 = pd.read_csv(shanghan_path, encoding='utf-8-sig')
    df2 = pd.read_csv(jingui_path, encoding='utf-8-sig')
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
    """
    选择出现频率最高的 top_k 个特征，生成两两交互项。
    高频特征更有可能存在临床协同效应，同时避免维度爆炸。
    """
    # 计算每个特征的出现频率
    freq = np.array(X.sum(axis=0)).flatten()
    top_indices = np.argsort(freq)[-top_k:].tolist()
    top_feats = [feature_names[i] for i in top_indices]
    
    interaction_list = []
    for (i, j) in combinations(range(len(top_feats)), 2):
        # 生成新特征：两个特征同时出现则为1，否则为0
        new_feat = X[:, top_indices[i]] * X[:, top_indices[j]]
        interaction_list.append(new_feat)
    
    if interaction_list:
        X_inter = np.column_stack([X] + interaction_list)
        # 新特征命名
        inter_names = [f"{top_feats[i]}__AND__{top_feats[j]}" 
                       for (i, j) in combinations(range(len(top_feats)), 2)]
        new_feature_names = list(feature_names) + inter_names
    else:
        X_inter = X
        new_feature_names = feature_names
    
    return X_inter, new_feature_names

# ================================================================
# 3. 主训练流程（含非线性交互）
# ================================================================
def main():
    print("=" * 70)
    print("经方精确权重训练 (L1逻辑回归 + 二阶交互特征)")
    print("解决“非线性协同效应”问题，同时保留可解释权重")
    print("=" * 70)
    
    # ---- 3.1 加载数据 ----
    df = load_and_merge_data('伤寒论病症方剂映射.csv', '金匮要略病症方剂映射.csv')
    print(f"有效方证记录数: {len(df)}")
    
    # ---- 3.2 基础向量化 ----
    mlb = MultiLabelBinarizer()
    X_base = mlb.fit_transform(df['feature_list'])
    feature_names_base = mlb.classes_
    
    le = LabelEncoder()
    y = le.fit_transform(df['pattern_id'])
    target_names = le.classes_
    
    # ---- 3.3 生成交互特征（捕捉非线性组合） ----
    print("\n生成二阶交互特征 (高频特征组合)...")
    X, feature_names = create_interaction_features(X_base, feature_names_base, top_k=30)
    print(f"原始特征: {len(feature_names_base)}, 交互后总特征: {len(feature_names)}")
    
    # ---- 3.4 训练 L1 逻辑回归（包含交互项） ----
    print("\n训练 L1 逻辑回归 (含非线性交互权重)...")
    model = LogisticRegression(
        multi_class='ovr',
        solver='saga',
        penalty='l1',
        C=0.5,          # 适当调高一点，让模型有机会保留有意义的交互项
        max_iter=3000,
        random_state=42,
        class_weight='balanced'
    )
    model.fit(X, y)
    
    # ---- 3.5 留一交叉验证 ----
    print("\n留一交叉验证 (LOOCV) 评估...")
    loo = LeaveOneOut()
    y_pred_loo = []
    for train_idx, test_idx in loo.split(X):
        X_train, X_test = X[train_idx], X[test_idx]
        y_train, y_test = y[train_idx], y[test_idx]
        temp_model = LogisticRegression(multi_class='ovr', solver='saga', penalty='l1', 
                                        C=0.5, max_iter=3000, random_state=42)
        temp_model.fit(X_train, y_train)
        y_pred_loo.append(temp_model.predict(X_test)[0])
    
    loo_acc = accuracy_score(y, y_pred_loo)
    loo_f1 = f1_score(y, y_pred_loo, average='macro')
    print(f"LOOCV 准确率 (Top-1): {loo_acc:.4f}")
    print(f"LOOCV F1-score (macro): {loo_f1:.4f}")
    
    # ---- 3.6 提取权重（核心：主特征权重 + 交互项权重） ----
    coefficients = model.coef_
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
    
    # ---- 3.7 展示非线性交互权重的效果 ----
    print("\n===== 非线性交互特征权重示例 (大青龙汤) =====")
    daqing = weight_df[weight_df['pattern_id'] == 'fj:DaQingLongTang']
    # 筛选出交互特征（包含 '__AND__'）
    inter_weights = daqing[daqing['feature'].str.contains('__AND__')].head(5)
    if not inter_weights.empty:
        for _, row in inter_weights.iterrows():
            print(f"  组合特征: {row['feature']} -> 权重: {row['weight']:.3f}")
    else:
        print("  未发现显著的交互特征（L1正则化已将其压缩为0，说明线性组合已足够）。")

if __name__ == "__main__":
    main()