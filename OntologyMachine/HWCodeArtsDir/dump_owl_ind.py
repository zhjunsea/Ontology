import re

path = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkPlumBlossom\ontology\plumblossom-all.owl"
s = open(path, encoding="utf-8").read()


def block(about):
    m = re.search(r'<owl:NamedIndividual rdf:about="%s">(.*?)</owl:NamedIndividual>' % re.escape(about), s, re.S)
    return m.group(1) if m else None


def show(about):
    b = block(about)
    print("==== %s ====" % about)
    if b is None:
        print("  <not found>")
        return
    b = re.sub(r"\s+", " ", b).strip()
    print("  ", b)


for x in ["core:QianTrigram", "core:KanTrigram", "core:KunTrigram",
          "core:QianHexagram", "core:KunHexagram",
          "rel:Sheng_HuoTu", "rel:Vitality_Wang", "rel:Nature_Qian",
          "sym:Symbol_Qian", "sym:Dim_TianShi",
          "div:Precept_WuShi", "div:Precept_ChongGua",
          "jud:DaJi", "jud:DaXiong", "jud:Grade_ShangShang",
          "jud:Cat_TianShi", "yijing:Seq_01", "yijing:Dictum_Qian"]:
    show(x)
