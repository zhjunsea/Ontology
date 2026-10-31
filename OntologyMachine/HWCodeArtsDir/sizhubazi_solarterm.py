# -*- coding: utf-8 -*-
"""
二十四节气「精确交节时刻」计算器（供 gen_sizhubazi_full_data.py 生成 solar_term_time 表）。

算法（自产，无外部天文库依赖）：
  1) 太阳视黄经：Meeus《Astronomical Algorithms》(2nd ed.) 第 25 章「太阳位置·低精度」
     几何平黄经 L0 + 中心差 C（含 3 项周期项）→ 真黄经；再加章动/光行差修正
     （-0.00569° - 0.00478°·sinΩ）→ 视黄经。精度约 0.01°（≈ ±15 分钟）。
  2) 求交节时刻：视黄经为一年约 360° 单调增，对目标黄经做牛顿迭代（dλ/dt ≈ 0.9856°/日）。
  3) 时标：计算用 TT；转 UT 用 ΔT（Espenak & Meeus 多项式）；输出北京时间（UTC+8）。

覆盖：1950–2050（与 SolarTermInstant 年度实例一致）。
出处：Meeus, Astronomical Algorithms, 2nd ed., ch. 22/25；ΔT 多项式据 Espenak & Meeus (NASA)。
注意：本算法为「太阳黄经法」自算，非照抄历书；交节「日」与公开历法一致，「时刻」误差约 ±15 分钟。
      ★禁编造：算法与出处如实标注，不声称秒级精度。
"""
import math
from datetime import datetime, timedelta

YEAR_MIN = 1950
YEAR_MAX = 2050

# S_TERM 同序（0=小寒 … 23=冬至）；idx → 节气名
TERM_NAMES = ['小寒', '大寒', '立春', '雨水', '惊蛰', '春分', '清明', '谷雨',
              '立夏', '小满', '芒种', '夏至', '小暑', '大暑', '立秋', '处暑',
              '白露', '秋分', '寒露', '霜降', '立冬', '小雪', '大雪', '冬至']
# 节气名 → 本地名（与 solar_term 表 Term_* 一致）
TERM_PY = {'小寒': 'XiaoHan', '大寒': 'DaHan', '立春': 'LiChun', '雨水': 'YuShui',
           '惊蛰': 'JingZhe', '春分': 'ChunFen', '清明': 'QingMing', '谷雨': 'GuYu',
           '立夏': 'LiXia', '小满': 'XiaoMan', '芒种': 'MangZhong', '夏至': 'XiaZhi',
           '小暑': 'XiaoShu', '大暑': 'DaShu', '立秋': 'LiQiu', '处暑': 'ChuShu',
           '白露': 'BaiLu', '秋分': 'QiuFen', '寒露': 'HanLu', '霜降': 'ShuangJiang',
           '立冬': 'LiDong', '小雪': 'XiaoXue', '大雪': 'DaXue', '冬至': 'DongZhi'}

# S_TERM 近似偏移（毫秒，与 Java S_TERM 完全一致；仅用作牛顿迭代初值）
S_TERM_MS = [
    0, 21208, 42467, 63836, 85337, 107014, 128867, 150921, 173149, 195551, 218072, 240693,
    263343, 285989, 308563, 331033, 353350, 375494, 397447, 419210, 440795, 462224, 483532, 504758]


def _jd_from_ymd(y, m, d, hours=0.0):
    """公历（格里高利）→ 儒略日（含小时小数）。"""
    if m <= 2:
        y -= 1
        m += 12
    a = y // 100
    b = 2 - a + a // 4
    return int(365.25 * (y + 4716)) + int(30.6001 * (m + 1)) + d + b - 1524.5 + hours / 24.0


def _jd_to_datetime(jd):
    """儒略日 → UTC datetime（分钟精度）。"""
    jd += 0.5
    z = int(jd)
    f = jd - z
    if z < 2299161:
        a = z
    else:
        alpha = int((z - 1867216.25) / 36524.25)
        a = z + 1 + alpha - alpha // 4
    b = a + 1524
    c = int((b - 122.1) / 365.25)
    dd = int(365.25 * c)
    e = int((b - dd) / 30.6001)
    day = b - dd - int(30.6001 * e)
    month = e - 1 if e < 14 else e - 13
    year = c - 4716 if month > 2 else c - 4715
    frac = f * 24.0
    hour = int(frac)
    minute = int(round((frac - hour) * 60.0))
    if minute == 60:
        minute = 0
        hour += 1
    base = datetime(year, month, day) + timedelta(hours=hour, minutes=minute)
    return base


def _delta_t_seconds(year):
    """ΔT = TT - UT（秒）。Espenak & Meeus 分段多项式（NASA）。"""
    if 1941 <= year < 1961:
        t = year - 1950.0
        return 29.07 + 0.407 * t - t * t / 233.0 + t ** 3 / 2547.0
    if 1961 <= year < 1986:
        t = year - 1975.0
        return 45.45 + 1.067 * t - t * t / 260.0 - t ** 3 / 718.0
    if 1986 <= year < 2005:
        t = year - 2000.0
        return (63.86 + 0.3345 * t - 0.060374 * t * t + 0.0017275 * t ** 3
                + 0.000651814 * t ** 4 + 0.00002373599 * t ** 5)
    if 2005 <= year < 2050:
        t = year - 2000.0
        return 62.92 + 0.32217 * t + 0.005589 * t * t
    if 2050 <= year <= 2150:
        return -20 + 32 * ((year - 1820) / 100.0) ** 2 - 0.5628 * (2150 - year)
    t = year - 1950.0
    return 29.07 + 0.407 * t - t * t / 233.0 + t ** 3 / 2547.0


def sun_apparent_longitude(jd_tt):
    """太阳视黄经（度，[0,360)）。Meeus ch.25 低精度。"""
    t = (jd_tt - 2451545.0) / 36525.0
    l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
    m = 357.52911 + 35999.05029 * t - 0.0001537 * t * t
    mr = math.radians(m % 360.0)
    c = ((1.914602 - 0.004817 * t - 0.000014 * t * t) * math.sin(mr)
         + (0.019993 - 0.000101 * t) * math.sin(2 * mr)
         + 0.000289 * math.sin(3 * mr))
    true_long = l0 + c
    omega = 125.04 - 1934.136 * t
    lam = true_long - 0.00569 - 0.00478 * math.sin(math.radians(omega))
    return lam % 360.0


def _seed_jd_tt(year, idx):
    """以 S_TERM 近似（与 Java 同式）作初值，转 TT。"""
    ms = 31556925974.0 * (year - 1900) + S_TERM_MS[idx] * 60000.0
    jd_ut = _jd_from_ymd(1900, 1, 6, 2.0 + 5.0 / 60.0) + ms / 86400000.0
    return jd_ut + _delta_t_seconds(year) / 86400.0


def solar_term_datetime(year, idx):
    """返回第 idx 个节气（0=小寒）的精确交节时刻（北京时间 datetime）。"""
    target = (285.0 + 15.0 * idx) % 360.0
    jd = _seed_jd_tt(year, idx)
    for _ in range(12):
        lam = sun_apparent_longitude(jd)
        diff = ((lam - target + 180.0) % 360.0) - 180.0  # 归一到 [-180,180)
        jd -= diff / 0.9856473
        if abs(diff) < 1e-6:
            break
    jd_ut = jd - _delta_t_seconds(year) / 86400.0
    return _jd_to_datetime(jd_ut + 8.0 / 24.0)  # 北京时间


def year_rows(year):
    """返回该年 24 行：(idx, term_name, term_py, 'YYYY-MM-DD HH:MM:SS')。"""
    rows = []
    for idx in range(24):
        dt = solar_term_datetime(year, idx)
        rows.append((idx, TERM_NAMES[idx], TERM_PY[TERM_NAMES[idx]],
                     dt.strftime('%Y-%m-%d %H:%M:%S')))
    return rows


def all_rows():
    """生成 1950–2050 全部行：list of (year, idx, term_name, term_py, datetime_str)。"""
    out = []
    for y in range(YEAR_MIN, YEAR_MAX + 1):
        for (idx, nm, py, dts) in year_rows(y):
            out.append((y, idx, nm, py, dts))
    return out


if __name__ == '__main__':
    # 自检：内部一致性 + 与 S_TERM 近似对比
    print('年份  序号 节气 精确交节时刻        相邻间隔(日)')
    dt_prev = None
    for idx in range(24):
        dt = solar_term_datetime(2024, idx)
        gap = '' if dt_prev is None else '%.2f' % ((dt - dt_prev).total_seconds() / 86400.0)
        print('2024  %2d   %s   %s  %s' % (idx, TERM_NAMES[idx], dt, gap))
        dt_prev = dt
    print('---- 与 Java 近似算法(S_TERM) 按「日」对比（应相差 <1 天）----')
    base = datetime(1900, 1, 6, 2, 5, 0)
    maxdiff = 0
    for y in range(1950, 2051):
        for idx in range(24):
            ms = 31556925974 * (y - 1900) + S_TERM_MS[idx] * 60000
            approx = base + timedelta(seconds=ms // 1000)
            exact = solar_term_datetime(y, idx)
            maxdiff = max(maxdiff, abs((exact - approx).total_seconds()) / 86400.0)
    print('最大「日」偏差 = %.3f 天' % maxdiff)
