# -*- coding: utf-8 -*-
"""脉象 / 舌象 / 腹证 的 SKOS 同义词数据（人工校订部分）。

背景：`tcm-zhengzhuang_skos.ttl` 原先只覆盖「症状」通道，脉象/舌象的口语映射
曾以 Java 硬编码补充表的形式存在于 `SymptomCatalog.buildAliases()` 中。
为落实「症状匹配一律以 SKOS 为唯一权威来源、不再硬编码」，此处把四诊其余通道
（脉象 / 舌象 / 腹证）的同义形式也纳入 SKOS 词表。

分工：
  * 可由规范名**规则化推导**的形式（脉X ↔ X脉、舌苔X ↔ X苔）由
    `_gen_zz_skos.py` 按规则生成，不在本表重复（规则化 ≠ 臆造）。
  * 本表只收录需要人工校订的**舌体**、**腹证**及少量特例。

依据：《濒湖脉学》《伤寒论》《金匮要略》舌诊/腹诊诸条。
"""

# ---------- 脉象：规则推导（脉X）之外的白话补充 ----------
# 「数脉/迟脉」的规范名无法反推出「脉搏快/脉搏慢」，故在此人工补录。
MAIXIANG = {
    "Shumai": dict(alt=[], hid=["脉搏快", "心跳快"]),
    "Chimai": dict(alt=[], hid=["脉搏慢", "心跳慢"]),
}

# ---------- 舌象：舌体（规范名以「舌」结尾）→ 口语「舌头X」 ----------
SHEXIANG_BODY = {
    "PaleRedTongue":     dict(alt=["舌头淡红"], hid=[]),
    "PaleWhiteTongue":   dict(alt=["舌头淡白"], hid=["舌头淡", "舌淡"]),
    "RedTongue":         dict(alt=["舌头红"], hid=["舌红"]),
    "CrimsonTongue":     dict(alt=["舌头绛"], hid=["舌绛", "舌头深红"]),
    "PurpleTongue":      dict(alt=["舌头紫"], hid=["舌紫"]),
    "BlueTongue":        dict(alt=["舌头青"], hid=["舌青"]),
    "SwollenTongue":     dict(alt=["舌头胖大"], hid=["舌头胖", "舌胖"]),
    "ThinTongue":        dict(alt=["舌头瘦薄"], hid=["舌头瘦", "舌瘦"]),
    "TeethMarkedTongue": dict(alt=["舌边有齿痕"], hid=["舌头有齿痕"]),
    "CrackedTongue":     dict(alt=["舌头有裂纹"], hid=["舌有裂纹"]),
    "PrickledTongue":    dict(alt=["舌头有点刺"], hid=[]),
    "TongueWithThorns":  dict(alt=["舌头生芒刺"], hid=[]),
    "EcchymosisTongue":  dict(alt=["舌头有瘀斑"], hid=[]),
    "PetechialTongue":   dict(alt=["舌头有瘀点"], hid=[]),
    "StiffTongue":       dict(alt=["舌头强硬"], hid=[]),
    "FlaccidTongue":     dict(alt=["舌头痿软"], hid=[]),
    "TremblingTongue":   dict(alt=["舌头颤动"], hid=[]),
    "DeviatedTongue":    dict(alt=["舌头歪斜"], hid=["舌头歪"]),
    "ProtrudingTongue":  dict(alt=["舌头吐弄"], hid=[]),
    "ShortenedTongue":   dict(alt=["舌头短缩"], hid=[]),
    "TongueCurling":     dict(alt=["舌头卷"], hid=[]),
    "MirrorTongue":      dict(alt=["舌光如镜"], hid=["舌面无苔"]),
    "TongueNoCoating":   dict(alt=["舌头没有苔"], hid=["舌上没有苔"]),
    "MoistTongue":       dict(alt=["舌头润"], hid=[]),
    "SlipperyTongue":    dict(alt=["舌头滑"], hid=[]),
    "DryTongue":         dict(alt=["舌头燥"], hid=["舌头干"]),
    "RoughTongue":       dict(alt=["舌头糙"], hid=[]),
}

# ---------- 舌象：苔类特例（规范名不以「苔」结尾，无法规则推导） ----------
SHEXIANG_SPECIAL = {
    "CoatingAsPowder": dict(alt=["舌苔如积粉"], hid=[]),
}

# ---------- 腹证（《伤寒论》腹诊） ----------
FUZHENG = {
    "Anzhishiying": dict(alt=["按之硬"], hid=["按下去硬", "肚子按着硬"]),
    "Antong":       dict(alt=["按之痛"], hid=["按下去疼", "一按就疼"]),
    "Anzhiru":      dict(alt=["按之软"], hid=["按下去软"]),
    "Anzhibutong":  dict(alt=["按之无痛"], hid=["按下去不疼"]),
}

# 汇总：base -> dict(alt, hid)
SYN = {}
for _d in (MAIXIANG, SHEXIANG_BODY, SHEXIANG_SPECIAL, FUZHENG):
    SYN.update(_d)
