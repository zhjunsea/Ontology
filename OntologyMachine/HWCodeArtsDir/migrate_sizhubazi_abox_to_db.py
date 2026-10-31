# -*- coding: utf-8 -*-
"""
第 11 步（闸门⑩，A 迁移口径）：
从 sizhubazi-abox.owl 删除「查表型历法」个体（天干/地支/六十甲子/纳音/藏干/节气/月建），
DB（sizhubazidb）为唯一真源，经 Ontop OBDA 虚拟访问。
保留：阴阳/五行/长生十二宫/十神名录/宫位/六亲/神煞名录/格局名录/层次/告警标志/出处/命例结构。

幂等：重复执行不改变结果（已删则跳过）。
"""
import re
import shutil
from pathlib import Path

ROOT = Path(r"D:\work\Ontology\OntologyMachine\OntologyFrameworkSiZhuBaZi")
ABOX = ROOT / "ontology" / "sizhubazi-abox.owl"
BAK = Path(r"D:\work\Ontology\OntologyMachine\HWCodeArtsDir") / "sizhubazi-abox.owl.prestep11.bak"

# 需删除的区块注释头（连同其下个体）
DROP_HEADERS = [
    "<!-- 十天干 10 -->",
    "<!-- 十二地支 12 -->",
    "<!-- 藏干（代表性） -->",
    "<!-- 纳音（代表性 6） -->",
    "<!-- 六十甲子（代表性 6） -->",
    "<!-- 节气（代表性 6） -->",
    "<!-- 月建 12 -->",
]

# 需删除的个体（按 rdf:about 前缀/精确匹配）
ABOUT_PATTERNS = [
    re.compile(r'rdf:about="core/Stem_'),
    re.compile(r'rdf:about="core/Branch_'),
    re.compile(r'rdf:about="core/NaYin_'),
    re.compile(r'rdf:about="core/ZiHidden"'),
    re.compile(r'rdf:about="core/YinHidden'),
    re.compile(r'rdf:about="calendar/Term_'),
    re.compile(r'rdf:about="calendar/Month'),
]
# 六十甲子：按类型匹配（避免误伤其他）
TYPE_SEXAGENARY = re.compile(r'rdf:type rdf:resource="core/SexagenaryCycle"')

NOTE = (
    "    <!-- 第 11 步（闸门⑩，A 迁移口径）：查表型历法个体（天干/地支/六十甲子/纳音/藏干/节气/月建）\n"
    "         已迁出至 MySQL（sizhubazidb），经 Ontop OBDA 虚拟访问，DB 为唯一真源（★铁律 13/14）；\n"
    "         本文件保留结构性/静态个体（阴阳/五行/长生十二宫/十神名录/宫位/六亲/神煞名录/格局名录/\n"
    "         层次/告警标志/出处/命例结构）。映射见 ontology/database/sizhubazi.obda。 -->\n"
)


def main():
    text = ABOX.read_text(encoding="utf-8")
    if not BAK.exists():
        shutil.copy2(ABOX, BAK)
        print(f"[bak] {BAK}")
    else:
        print(f"[bak] exists, skip: {BAK}")

    lines = text.splitlines(keepends=True)
    out = []
    dropped_individuals = 0
    dropped_headers = 0
    note_inserted = False
    for ln in lines:
        stripped = ln.strip()
        if stripped in DROP_HEADERS:
            dropped_headers += 1
            continue
        if any(p.search(ln) for p in ABOUT_PATTERNS) or TYPE_SEXAGENARY.search(ln):
            dropped_individuals += 1
            continue
        # 在 Ontology 注释块之后插入迁移说明
        if (not note_inserted) and stripped.startswith("<!-- ============================================================ -->") and dropped_individuals == 0:
            pass
        out.append(ln)

    new_text = "".join(out)

    # 在首个“典型 ABox”注释块前插入迁移说明（置于 </owl:Ontology> 之后）
    marker = "    </owl:Ontology>\n"
    if marker in new_text and "A 迁移口径" not in new_text:
        new_text = new_text.replace(marker, marker + "\n" + NOTE, 1)
        note_inserted = True

    ABOX.write_text(new_text, encoding="utf-8")
    print(f"[done] dropped_headers={dropped_headers} dropped_individuals={dropped_individuals} note_inserted={note_inserted}")


if __name__ == "__main__":
    main()
