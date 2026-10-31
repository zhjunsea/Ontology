# -*- coding: utf-8 -*-
"""第 12 步（闸门⑪）：ABox 格局（pattern）扩至《紫微斗數全書》全集。
来源：卷一·定富局(6)/定贵局(27)/定贫贱局(8)/定杂局(8)。
仅新增 StarPattern 个体，不改 TBox、不改 DDL。"""
import io, os, shutil, xml.dom.minidom as md

ABOX = os.path.join(os.path.dirname(__file__), "..", "OntologyFrameworkZiWeiDouShu", "ontology", "ziweidoushu-abox.owl")
BAK = os.path.join(os.path.dirname(__file__), "_ziweidoushu-abox.owl.bak12")
NS = "http://example.org/ziweidoushu/pattern#"

NEW = [
    # 富局（+3）
    ("Fu_CaiLuJiaMa", "财禄夹马格（富局）"),
    ("Fu_YinYinGongShen", "荫印拱身格（富局）"),
    ("Fu_RiYueZhaoBi", "日月照璧格（富局）"),
    # 贵局（+16）
    ("Gui_RiYueJiaMing", "日月夹命格（贵局）"),
    ("Gui_RiChuFuSang", "日出扶桑格（贵局）"),
    ("Gui_YueLuoHaiGong", "月落亥宫格（月朗天门·贵局）"),
    ("Gui_YueShengCangHai", "月生沧海格（贵局）"),
    ("Gui_FuBiGongZhu", "辅弼拱主格（贵局）"),
    ("Gui_JunChenQingHui", "君臣庆会格（贵局）"),
    ("Gui_CaiYinJiaLu", "财印夹禄格（贵局）"),
    ("Gui_LuMaPeiYin", "禄马佩印格（贵局）"),
    ("Gui_ZuoGuiXiangGui", "坐贵向贵格（贵局）"),
    ("Gui_MaTouDaiJian", "马头带剑格（贵局）"),
    ("Gui_XingQiuJiaYin", "刑囚夹印格（贵局）"),
    ("Gui_TanHuoXiangFeng", "贪火相逢格（贵局）"),
    ("Gui_WuQuShouYuan", "武曲守垣格（贵局）"),
    ("Gui_QuanLuShengFeng", "权禄生逢格（贵局）"),
    ("Gui_YangRenRuMiao", "羊刃入庙格（贵局）"),
    ("Gui_JinYuFuJia", "金舆扶驾格（贵局）"),
    # 贫贱局（+5）
    ("Pin_CaiYuQiuChou", "财与囚仇格（贫贱局）"),
    ("Pin_YiShengGuPin", "一生孤贫格（贫贱局）"),
    ("Pin_JunZiZaiYe", "君子在野格（贫贱局）"),
    ("Pin_LiangChongHuaGai", "两重华盖格（贫贱局）"),
    ("Pin_LuFengLiangSha", "禄逢两杀格（贫贱局）"),
    # 杂局（+5）
    ("Za_JinShangTianHua", "锦上添花格（杂局）"),
    ("Za_LuShuaiMaKun", "禄衰马困格（杂局）"),
    ("Za_BuShuWuYi", "步数无依格（杂局）"),
    ("Za_ShuiShangJiaXing", "水上驾星格（杂局）"),
    ("Za_JiXiongXiangBan", "吉凶相伴格（杂局）"),
]

shutil.copyfile(ABOX, BAK)
s = io.open(ABOX, encoding="utf-8").read()

# 幂等：先剔除已存在的同名个体块
import re
for local, _ in NEW:
    s = re.sub(r'\s*<rdf:Description rdf:about="%s%s">.*?</rdf:Description>' % (re.escape(NS), re.escape(local)), "", s, flags=re.S)

lines = []
for local, name in NEW:
    lines.append('    <rdf:Description rdf:about="%s%s"><rdf:type rdf:resource="%sStarPattern"/><pattern:patternName>%s</pattern:patternName></rdf:Description>' % (NS, local, NS, name))
block = "\n".join(lines) + "\n"

# 锚定到格名块之后（Za_YiJinHuanXiang 行尾）
anchor = 'rdf:about="http://example.org/ziweidoushu/pattern#Za_YiJinHuanXiang"'
idx = s.index(anchor)
end = s.index("</rdf:Description>", idx) + len("</rdf:Description>")
s = s[:end] + "\n" + block.rstrip("\n") + s[end:]

io.open(ABOX, "w", encoding="utf-8", newline="\n").write(s)

md.parseString(s.encode("utf-8"))
print("ok; added", len(NEW), "pattern individuals; well-formed XML")
