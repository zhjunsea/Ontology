from steeltowerdesign.Flange import topFlangeStress
from steeltowerdesign.Flange.topFlangeStress import topFlange_ultimate_loads2dict,get_Mxy_Mz_Fz_Fxy

#判断不同顶法兰适应的最大载荷：
def is_flange_load_within_bounds(top_flange_name, ultimate_load_excel):
    """
    Checks if the top flange is within the load bounds.
    """
    out_of_bounds_loads = []  # 存储不在范围内的载荷
    top_flange_stress_curve = get_top_flange_stress_curve(top_flange_name)

    My_min_load = top_flange_stress_curve['inner_fillet']['My'][0]
    My_max_load = top_flange_stress_curve['inner_fillet']['My'][-1]
    ultimate_load_dict = topFlange_ultimate_loads2dict(ultimate_load_excel)
    for load_name in ultimate_load_dict:
        ultimate_load_dict_i = ultimate_load_dict[load_name]
        Mxy, Mz, Fz, Fxy = get_Mxy_Mz_Fz_Fxy(ultimate_load_dict_i).values()
        is_within_bounds = My_min_load < Mxy < My_max_load
        if not is_within_bounds:  # 不在范围内
            out_of_bounds_loads.append(load_name)  # 添加到不在范围内的列表

    return out_of_bounds_loads  # 返回不在范围内的载荷列表


def get_top_flange_stress_curve(top_flange_name):
    """
    Fetches the stress curve for the specified top flange.
    """
    if top_flange_name in ["V12-6X-tfl=250-s=38", "V12-4X-tfl=250-s=38"]:
        return topFlangeStress.top_flange_stress_curve[top_flange_name]['My']
    return topFlangeStress.top_flange_stress_curve[top_flange_name]


if __name__ == '__main__':
    top_flange_name = "1MW"
    ultimate_load_excel = r'D:\towerdesignrelated\flasktest\3ultimate_loads_tower_sf_section.xlsx'
    print(is_flange_load_within_bounds(top_flange_name,ultimate_load_excel))
    out_of_bounds_loads = is_flange_load_within_bounds(top_flange_name, ultimate_load_excel)  # 接收返回的列表
    if out_of_bounds_loads:  # 如果列表非空，即有不在范围内的载荷
        print("Not suitable for the following loads:", out_of_bounds_loads)
    else:
        print("All loads are suitable")