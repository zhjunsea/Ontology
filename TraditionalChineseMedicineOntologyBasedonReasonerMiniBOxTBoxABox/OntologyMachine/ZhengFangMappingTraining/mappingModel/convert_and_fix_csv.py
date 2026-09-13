import csv
import chardet
import os

def convert_and_fix_csv(input_path, output_path):
    # 1) 检测编码
    with open(input_path, 'rb') as f:
        raw = f.read()
        enc = chardet.detect(raw)['encoding']
        print(f"{os.path.basename(input_path)} 编码: {enc}")

    # 2) 读取原始内容（用检测到的编码）
    with open(input_path, 'r', encoding=enc, errors='ignore') as f:
        content = f.read()

    # 3) 重新写入，所有字段加引号，UTF-8编码
    with open(output_path, 'w', encoding='utf-8-sig', newline='') as f:
        reader = csv.reader(content.splitlines())
        writer = csv.writer(f, quoting=csv.QUOTE_ALL)
        for row in reader:
            # 如果行长度不对（由于内含逗号），进行合并
            if len(row) != 8:
                # 列：pattern_id, pattern_name, liujing, symptom_list, pulse_list, tongue_list, jianjia_list, clause
                # 症状列可能有多个，合并前3列以后的到第3列
                if len(row) > 8:
                    merged = row[:3] + [','.join(row[3:len(row)-4])] + row[len(row)-4:]
                    if len(merged) == 8:
                        writer.writerow(merged)
                    else:
                        # 如果还是不对，跳过此行
                        continue
                else:
                    # 行数少于8，补空
                    row += [''] * (8 - len(row))
                    writer.writerow(row[:8])
            else:
                writer.writerow(row)

    print(f"已生成 {output_path}")

# 转换两个文件
convert_and_fix_csv('伤寒论病症方剂映射.csv', '伤寒论病症方剂映射_fixed.csv')
convert_and_fix_csv('金匮要略病症方剂映射.csv', '金匮要略病症方剂映射_fixed.csv')
print("转换完成！")