from steeltowerdesign.Door.Door_DNV_buckling import dnv_door_buckling
import contextlib
import io

if __name__ == '__main__':
    tower_excel = r'D:\towerdesignrelated\flasktest\1TowerGeoInput.xlsx'
    load_excel = r'D:\towerdesignrelated\flasktest\3ultimate_loads_tower_sf_section.xlsx'
    shell_material = "Q355"
    gamma_M_buckle = 1.2
    QualityClass = 'B'
    r_Rpl = 2.77
    r_Rcr = 8.81
    is_buckling_2017 = True

    # 创建一个字符串缓冲区来捕获标准输出
    f = io.StringIO()
    with contextlib.redirect_stdout(f):
        dnv_door_buckling(tower_excel, load_excel, shell_material, gamma_M_buckle, QualityClass, r_Rpl, r_Rcr, is_buckling_2017)

    # 获取捕获的输出内容
    output = f.getvalue()
    print(output)
    # # 将捕获的内容写入文件
    # with open('buckling_results.txt', 'w') as file:
    #     file.write(output)
