#处理txt的应力曲线为excel

import pandas as pd

#读取txt文件
with open(r'D:\202410\12.底法兰计算\10459699--106m_华润项目\V12_10459699_huarun_baseflange_fatigue_R25_1.4_tf150_60mm_24.11.08.11.05.42.681/stress_weld.txt', 'r') as f:
    data = f.readlines()
    print(data)

# 将数据转换为二维列表
data_list = [line.strip().split() for line in data]

# 创建DataFrame
df = pd.DataFrame(data_list, columns=[f'Column_{i+1}' for i in range(len(data_list[0]))])

# 写入Excel文件
df.to_excel(r'D:\202410\12.底法兰计算\10459699--106m_华润项目\V12_10459699_huarun_baseflange_fatigue_R25_1.4_tf150_60mm_24.11.08.11.05.42.681\output.xlsx', index=False)

print("数据已成功写入Excel文件：output.xlsx")