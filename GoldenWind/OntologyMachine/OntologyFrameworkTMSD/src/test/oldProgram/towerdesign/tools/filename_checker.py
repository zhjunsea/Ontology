"""
文件名格式验证工具
用于检查Markov等文件的命名是否符合规范
"""
import re
import sys


def is_valid_markov_filename(filename):
    """
    检查Markov文件名是否符合规范
    
    支持的格式：
    1. 主要格式：包含数字+k+数字，如 Markov_T_049k500.xlsx
       - 正则：\d+(k\d+)
       - 示例：049k500 -> 49.5
    
    2. 备用格式：2_和m之间的数字，如 xxx_2_15.5m_xxx.xlsx
       - 正则：(?<=2_).*?(?=m)
       - 示例：2_15.5m -> 15.5
    
    Args:
        filename: 文件名（如 Markov_T_049k500.xlsx）
    
    Returns:
        tuple: (是否有效, 提取的位置值或None, 错误信息)
    """
    # 检查是否为xlsx文件
    if not filename.endswith('.xlsx'):
        return False, None, "文件扩展名必须是.xlsx"
    
    # 尝试格式1：数字+k+数字
    try:
        match = re.search(r"\d+(k\d+)", filename)
        if match:
            loc_str = match.group().replace('k', '.')
            loc = float(loc_str)
            return True, loc, None
    except Exception as e:
        pass
    
    # 尝试格式2：2_和m之间的数字
    try:
        match = re.search(r"(?<=2_).*?(?=m)", filename)
        if match:
            loc = float(match.group())
            return True, loc, None
    except Exception as e:
        pass
    
    # 两种格式都不符合
    return False, None, "markov文件名错误，请按约定要求修改（如：Markov_T_049k500.xlsx）"


def validate_markov_files(file_list):
    """
    批量验证Markov文件名
    
    Args:
        file_list: 文件名列表
    
    Returns:
        dict: {
            'valid': [(文件名, 位置值), ...],
            'invalid': [(文件名, 错误信息), ...]
        }
    """
    result = {
        'valid': [],
        'invalid': []
    }
    
    for filename in file_list:
        is_valid, loc, error = is_valid_markov_filename(filename)
        if is_valid:
            result['valid'].append((filename, loc))
        else:
            result['invalid'].append((filename, error))
    
    return result


if __name__ == "__main__":
    # 测试用例
    test_files = [
        "Markov_T_049k500.xlsx",  # 标准格式
        "Markov_T_010k200.xlsx",  # 标准格式
        "data_2_15.5m_result.xlsx",  # 备用格式
        "invalid_file.xlsx",  # 无效格式
        "wrong_name.txt",  # 错误扩展名
    ]
    
    print("=== Markov文件名验证测试 ===\n")
    
    for filename in test_files:
        is_valid, loc, error = is_valid_markov_filename(filename)
        print(f"文件名: {filename}")
        print(f"  验证结果: {'✓ 有效' if is_valid else '✗ 无效'}")
        if is_valid:
            print(f"  提取位置: {loc}米")
        else:
            print(f"  错误信息: {error}")
        print()
    
    # 批量验证测试
    print("\n=== 批量验证测试 ===\n")
    result = validate_markov_files(test_files)
    print(f"有效文件数: {len(result['valid'])}")
    for fname, loc in result['valid']:
        print(f"  - {fname} (位置: {loc}米)")
    
    print(f"\n无效文件数: {len(result['invalid'])}")
    for fname, error in result['invalid']:
        print(f"  - {fname}: {error}")