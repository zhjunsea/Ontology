import math
import numpy as np

def cal_angle(p1, p2, p3):
    v1 = np.array(p1) - np.array(p2)
    v2 = np.array(p3) - np.array(p2)
    c = np.dot(v1, v2) / (np.linalg.norm(v1) * np.linalg.norm(v2))
    return np.arccos(np.clip(c, -1.0, 1.0))

def cal_length(p1, p2):
    return float(np.linalg.norm(np.array(p2) - np.array(p1)))

def probe(dis_bott, dis_top, high_truss, H_0=744.817, deviation=500,
          beta1_deg=47.7, beta2_deg=47.7):
    pb = np.array([dis_bott * 0.5, dis_bott * 0.5, 0.0])
    pt = np.array([dis_top * 0.5, dis_top * 0.5, high_truss])
    alpha = cal_angle([dis_top * 0.5, dis_bott * 0.5, 0], pb, pt)
    gama = cal_angle([0, 0, 0], pb, pt)
    L = cal_length(pb, pt)
    beta1 = math.radians(beta1_deg)

    if math.sin(gama) == 0:
        return None
    L0 = H_0 / math.sin(gama)
    dis_bott_small = dis_bott - deviation / math.cos(math.pi / 2 - alpha) * 2
    dis_x = dis_bott_small - 2 * (L0 - deviation * math.tan(math.pi / 2 - alpha)) * math.cos(alpha)
    tot_l = L0
    solved = False
    n_temp = 0
    last_res = None
    for i in range(1, 100):
        lx = dis_x * math.sin(beta1) / math.sin(math.pi - alpha - beta1)
        dis_x -= 2 * lx * math.cos(alpha)
        tot_l += lx
        last_res = L - tot_l
        if 0 < (L - tot_l) <= 1200:
            n_temp = i + 2
            solved = True
            break
    return {
        'alpha_deg': math.degrees(alpha),
        'gama_deg': math.degrees(gama),
        'L': L, 'L0': L0,
        'dis_x_start': None,
        'n_temp': n_temp, 'solved': solved,
        'last_res': last_res, 'tot_l': tot_l,
    }

if __name__ == '__main__':
    print("=== 复刻校验：客户脚本原参数 (dis_top=9604.88, high_truss=116733) ===")
    r = probe(25000, 9604.88, 116733)
    print(r)

    print()
    print("=== 目标工况：dis_bott=25000, dis_top=9800，扫描 high_truss ===")
    print(f"{'high_truss(m)':>14} | {'alpha':>8} | {'gama':>8} | {'L':>10} | {'n':>4} | {'solved':>6} | {'last_res':>10}")
    for h in range(100000, 300001, 2000):
        r = probe(25000, 9800, h)
        if r is None:
            continue
        print(f"{h/1000.0:14.3f} | {r['alpha_deg']:8.3f} | {r['gama_deg']:8.3f} | {r['L']:10.1f} | {r['n_temp']:4d} | {str(r['solved']):>6} | {r['last_res']:10.2f}")
