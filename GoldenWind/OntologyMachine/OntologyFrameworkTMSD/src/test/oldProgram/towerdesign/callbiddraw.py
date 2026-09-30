import json
import os.path

from towerdesign.oneclickbid_main import biddraw
import sys
# ###################################################
import argparse
# ###################################################

def run(jsonfile):
    # 将原来程序封装到一个函数中，或者一个类函数中
    inputsjson = json.load(open(jsonfile, 'r', encoding='utf-8'))

    #顶法兰
    top_flange_name = inputsjson['TopFlange']['Topflange']
    if not top_flange_name:
        top_flange_name = None
    print(top_flange_name)

    #塔架主体信息表
    towerGeoExcel = inputsjson['uploadfiles']['towergeofile']
    print(towerGeoExcel)

    #图号
    drawing_number = inputsjson['Project Options']['drawing_number']
    print(drawing_number)
    #版本
    drawing_version = inputsjson['Project Options']['drawing_version']
    print(drawing_version)

    biddraw(towerGeoExcel)
# 以下部分为函数入口
if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('-work_dir', type=str, help='work_dir') #工作路径
    parser.add_argument('-inputsjson', type=str, help='inputsjson')# json文件的输入路径，默认放在work_dir目录下

    args = parser.parse_args()
    work_dir = args.work_dir
    print('work_dir:',work_dir)
    print('args:',args)
    inputsjson = args.inputsjson
    print(inputsjson)
    os.chdir(work_dir) #切换到工作目录下，计算结果和输出文件将默认存放在这个位置
    # ##################################################
    run(inputsjson)

