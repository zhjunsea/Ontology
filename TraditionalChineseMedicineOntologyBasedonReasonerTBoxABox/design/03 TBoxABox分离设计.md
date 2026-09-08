# 经方六经辨证本体 TBox/ABox 分离设计方案

> 仲景曰：“观其脉证，知犯何逆，随证治之。”  
> 胡希恕云：“辨方证是辨证的尖端，方证是六经八纲的最终落点。”  
> 本体之学，要在理法方药一气贯通。今将原本体**术语知识（TBox）**与**实例断言（ABox）**分而治之，使医理恒定，病案流转，推理不悖。

---

## 一、设计目标

1. **保持 DL 推理能力**：分离后本体不降级为 OWL Full，仍可用 HermiT、Pellet 等推理机进行分类。
2. **医理与数据解耦**：TBox 承载六经、八纲、方证、药证的**恒定规律**；ABox 承载具体方剂、药物、标准症状实例及未来患者病案。
3. **可维护性**：修改方证定义只需更新 TBox，不影响已有患者数据；新增患者只需追加 ABox，不触碰医理层。
4. **可扩展性**：合病、兼夹证、加减药物等注释信息保留，为后续 SWRL 规则或 SPARQL 查询提供支点。

---

## 二、分离原则

| 内容 | 归属 | 理由 |
|------|------|------|
| 类声明（如 `#Taiyangbing`、`#Fangzheng`） | TBox | 概念术语 |
| 对象属性/数据属性/注释属性声明 | TBox | 关系术语 |
| 类公理（subClassOf、equivalentClass、Restriction、unionOf、intersectionOf） | TBox | 概念间逻辑约束 |
| 方证类与方剂个体的 `you_chufang` hasValue 公理 | TBox | 方证定义的一部分，属于“病–方”固定对应 |
| 类注释（`possibleSymptom`、`addHerb`） | TBox | 虽不参与推理，但属于术语层知识 |
| 个体声明（`owl:NamedIndividual`） | ABox | 实例断言 |
| 个体类型断言（`rdf:type`） | ABox | 实例属于某类 |
| 个体间关系（如 `you_yaowu`） | ABox | 具体方剂与药物组成 |
| 患者实例及其症状、脉象等断言 | ABox（独立文件） | 临床数据 |
| 药物与药证关联（需修正） | ABox（改为注释属性）或 TBox 注释 | 见下节修正 |

> **关键点**：  
> TBox 中可以引用个体名（如方剂个体 `#GuizhiTang`），但不声明该个体；个体声明在 ABox 中完成。这样 TBox 仍为纯术语层，而 ABox 加载后推理机可识别全部个体。

---

## 三、TBox 模块详细设计

### 3.1 文件命名
建议使用：`jingfang-tbox.owl`（或 `.ttl` 更易读）

### 3.2 内容清单

#### （1）本体声明与命名空间
保留原 `xml:base`，标注本体版本、说明。

#### （2）属性声明
- 对象属性：
  - `you_zhengzhuang`（域：Huanzhe，值域：Zhengzhuang）
  - `you_maixiang`（域：Huanzhe，值域：Maixiang）
  - `you_shexiang`（域：Huanzhe，值域：Shexiang）
  - `you_fuzheng`（域：Huanzhe，值域：Fuzheng）
  - `you_chufang`（域：Fangzheng，值域：Fangji）
  - `you_yaowu`（域：Fangji，值域：Yaowu）
  - ~~`you_yaozheng`~~ **建议改为注释属性**（理由见下）
- 数据属性：
  - `you_jianyi`（域：Huanzhe，值域：xsd:string）
- 注释属性：
  - `possibleSymptom`（或然症）
  - `addHerb`（可加药物）
  - `hasYaozheng`（**新增**，用于药物与药证类的注释关联，替代原 `you_yaozheng`）

#### （3）类声明与层级
- 顶层：`Huanzhe`、`SizhenXinxi`、`Bagang`、`Liujingbing`、`Fangzheng`、`Fangji`、`Yaowu`、`Yaozheng`、`JianJiaZheng`
- 四诊子类：`Zhengzhuang`、`Maixiang`、`Shexiang`、`Fuzheng`、`Tizhi`
- 八纲子类：`Biao`、`Li`、`BanbiaoBanli`、`Han`、`Re`、`Xu`、`Shi`、`Yin`、`Yang`
- 六经病子类：`Taiyangbing`、`Yangmingbing`、`Shaoyangbing`、`Taiyinbing`、`Shaoyinbing`、`Jueyinbing`
- 兼夹证子类：`YuXueZheng`、`TanYinZheng`、`QiYuZheng`、`TanReShiReZheng`、`PiManShiJiZheng`
- 方证子类：`GuizhiTangZheng`、`MahuangTangZheng`、`BaihuTangZheng`、`DaChengqiTangZheng`、`XiaoChaihuTangZheng`、`DaChaihuTangZheng`、`LizhongTangZheng`、`SiniTangZheng`、`MahuangFuziXixinTangZheng`、`ZhenwuTangZheng`、`WumeiWanZheng`、`ChaihuGuizhiGanjiangTangZheng`、`ChaihuBaihuTangZheng`、`GuiZhiFuLingWanZheng`、`XiaoQingLongTangZheng`、`SiNiSanZheng`、`WenDanTangZheng`、`BanXiaXieXinTangZheng`
- 药证子类：`GuizhiZheng`（其余药证按需补充）

#### （4）类公理（核心医理定义）

所有原 `equivalentClass` 定义均保留，包括：
- 八纲等价定义（Biao、Li、Han、Re、Xu、Shi、Yin、Yang）
- 半表半里定义
- 六经病定义（含太阴寒证的 `自利+不渴` 支、阳明经证/腑证两支）
- 兼夹证定义（瘀血、痰饮、气郁、痰热/湿热、痞满/食积）
- 方证定义（决定性主症的组合，或然症仅作注释）

**注意**：  
方证类中 `you_chufang` 的 hasValue 指向方剂个体名（如 `#GuizhiTang`），此公理仍留在 TBox，因为它是方证概念的一部分。个体声明留在 ABox。

#### （5）需补全的缺失类
原文件引用了若干未声明的类，应在 TBox 中补全：
- `#XiongMan`（胸满，症状，属 Zhengzhuang）
- `#Kesou`（咳嗽，症状）
- `#TanDuo`（痰多，症状）
- `#XiaobianHuang`（小便黄，症状）
- `#TaiNi`（苔腻，舌象）
- `#FuMan`（腹满，已存在 `#Fuman` 但 `#FuMan` 可能为笔误，建议统一为 `#Fuman`）
- `#ChunWeiSheQing`、`#JifuJiacuo` 等已存在，检查拼写一致性。

#### （6）注释属性应用
- 方证类上的 `possibleSymptom` 保留原值。
- 兼夹证类上的 `addHerb` 保留原值。
- 药物个体与药证类的关联改用 `hasYaozheng` 注释属性（见修正）。

### 3.3 TBox 文件示例片段
```xml
<owl:Class rdf:about="#GuizhiTangZheng">
  <rdfs:subClassOf rdf:resource="#Fangzheng"/>
  <owl:equivalentClass>
    <owl:Class>
      <owl:intersectionOf rdf:parseType="Collection">
        <owl:Class rdf:about="#Taiyangbing"/>
        <owl:Restriction>
          <owl:onProperty rdf:resource="#you_zhengzhuang"/>
          <owl:someValuesFrom rdf:resource="#Fare"/>
        </owl:Restriction>
        <!-- ... -->
      </owl:intersectionOf>
    </owl:Class>
  </owl:equivalentClass>
  <possibleSymptom rdf:resource="#Ehan"/>
  <possibleSymptom rdf:resource="#Outu"/>
  <rdfs:subClassOf>
    <owl:Restriction>
      <owl:onProperty rdf:resource="#you_chufang"/>
      <owl:hasValue rdf:resource="#GuizhiTang"/>
    </owl:Restriction>
  </rdfs:subClassOf>
</owl:Class>
```
注意：`#GuizhiTang` 个体未在此声明。

---

## 四、ABox 模块详细设计

### 4.1 文件命名
建议：`jingfang-abox.owl`（或 `.ttl`）  
患者数据可另建：`patients-abox.owl`

### 4.2 内容清单

#### （1）导入 TBox
```xml
<owl:Ontology rdf:about="http://www.tcm-classics.org/jingfang/abox">
  <owl:imports rdf:resource="http://www.tcm-classics.org/jingfang/jingfang-tbox.owl"/>
</owl:Ontology>
```

#### （2）方剂个体
- `#GuizhiTang`、`#MahuangTang`、`#BaihuTang`、`#DaChengqiTang`、`#XiaoChaihuTang`、`#DaChaihuTang`、`#LizhongTang`、`#SiniTang`、`#MahuangFuziXixinTang`、`#ZhenwuTang`、`#WumeiWan`、`#ChaihuGuizhiGanjiangTang`、`#ChaihuBaihuTang`、`#GuiZhiFuLingWan`、`#XiaoQingLongTang`、`#SiNiSan`、`#WenDanTang`、`#BanXiaXieXinTang`
- 类型断言：`rdf:type #Fangji`
- 组成关系：`you_yaowu` 指向药物个体（原文件已有，全部迁移）

#### （3）药物个体
所有药物个体（`#Guizhi`、`#Shaoyao`……）保留，类型断言：`rdf:type #Yaowu`。  
如原文件已有 `you_yaozheng` 对象属性断言，需改为注释属性 `hasYaozheng` 指向药证类，例如：
```xml
<owl:NamedIndividual rdf:about="#Guizhi">
  <rdf:type rdf:resource="#Yaowu"/>
  <hasYaozheng rdf:resource="#GuizhiZheng"/>
</owl:NamedIndividual>
```
`hasYaozheng` 为注释属性，其值可为类，不影响 DL 推理。

#### （4）标准四诊实例
原文件中的 `#Fare_instance`、`#Fumai_instance`、`#HuangzaoQiciTai_instance`、`#XinxiaAnzhiMantong_instance` 等均为标准实例，保留在 ABox，类型断言对应类。  
这些实例可在患者断言中复用，避免为每个患者重复创建相同症状个体。

#### （5）患者实例（可后续扩展）
ABox 可包含患者实例，例如：
```xml
<owl:NamedIndividual rdf:about="#Patient001">
  <rdf:type rdf:resource="#Huanzhe"/>
  <you_zhengzhuang rdf:resource="#Fare_instance"/>
  <you_zhengzhuang rdf:resource="#Hanchu_instance"/>
  <you_zhengzhuang rdf:resource="#Efeng_instance"/>
  <you_maixiang rdf:resource="#Fumai_instance"/>
  <you_maixiang rdf:resource="#Huanmai_instance"/>
</owl:NamedIndividual>
```
此患者经推理应被归类为 `Taiyangbing`、`GuizhiTangZheng`，并可通过 `you_chufang` 推导出桂枝汤。

### 4.3 ABox 文件示例片段
```xml
<owl:NamedIndividual rdf:about="#GuizhiTang">
  <rdf:type rdf:resource="#Fangji"/>
  <you_yaowu rdf:resource="#Guizhi"/>
  <you_yaowu rdf:resource="#Shaoyao"/>
  <you_yaowu rdf:resource="#Gancao"/>
  <you_yaowu rdf:resource="#Shengjiang"/>
  <you_yaowu rdf:resource="#Dazao"/>
</owl:NamedIndividual>
```

---

## 五、需修正的建模问题

### 5.1 `you_yaozheng` 对象属性使用不当
**问题**：原文件将 `you_yaozheng` 定义为对象属性（值域为 `Yaozheng` 类），却在 `Guizhi` 个体上用它指向 `#GuizhiZheng` 类（非个体）。这会导致 OWL DL 解析冲突，推理机可能报错或降级为 OWL Full。  
**修正方案**：
- 删除或保留 `you_yaozheng` 对象属性，但不再用于药物–药证关联。
- 新增注释属性 `hasYaozheng`（或直接使用 `rdfs:seeAlso`），专门用于标注药物对应的药证类。
- 药物个体上使用 `hasYaozheng` 指向药证类，作为知识标注，不参与推理。  
- 药证类本身仍为 TBox 中的概念定义，如 `GuizhiZheng` 的等价类描述“汗出、恶风、脉浮”。

### 5.2 缺失类及命名不一致
- 补全 `#Kesou`、`#TanDuo`、`#XiaobianHuang`、`#TaiNi` 等。
- 统一 `#Fuman` 与 `#FuMan`，建议全部使用 `#Fuman`（与属性 `you_zhengzhuang` 值域范围一致）。
- 检查 `#XinxiaJi` 与 `#XinxiaJi2` 是否同一概念，建议合并。

### 5.3 兼夹证与方证的衔接
原方证如 `GuiZhiFuLingWanZheng` 的等价类包含 `YuXueZheng`，但 `YuXueZheng` 自身等价类要求“刺痛+舌紫暗+涩脉”。这逻辑合理。  
若患者同时符合六经病和兼夹证，推理机可推出相应方证。TBox 中保留这些定义即可。

---

## 六、文件结构与导入关系

```
jingfang-ontology/
├── jingfang-tbox.owl        # 术语层：类、属性、公理（不含个体声明）
├── jingfang-abox.owl        # 实例层：方剂、药物、标准症状、药证关联
├── jingfang-patients.owl    # 患者实例（可选，可合并或单独）
├── jingfang-queries.sparql  # 查询用例
└── README.md                # 说明文档
```

推荐总装载顺序：先加载 TBox，再加载 ABox（ABox 通过 `owl:imports` 自动引入 TBox）。

---

## 七、推理与查询验证

### 7.1 推理任务
- **一致性检查**：确保无类不可满足，如方证定义与六经病定义不冲突。
- **个体分类**：患者实例自动归类到 `Taiyangbing`、`GuizhiTangZheng` 等。
- **处方推导**：通过 `you_chufang` 的 hasValue 公理，从方证类推导出具体方剂个体。

### 7.2 SPARQL 查询示例
**查询患者症状及推导的方证、方剂**：
```sparql
PREFIX : <http://www.tcm-classics.org/jingfang#>
SELECT ?patient ?symptom ?fangzheng ?fangji
WHERE {
  ?patient a :Huanzhe ;
           :you_zhengzhuang ?symptom .
  ?patient a ?fangzheng .
  ?fangzheng rdfs:subClassOf :Fangzheng ;
             :you_chufang ?fangji .
}
```
**查询兼夹证可加药物**：
```sparql
PREFIX : <http://www.tcm-classics.org/jingfang#>
SELECT ?jianjia ?herb
WHERE {
  ?jianjia a :JianJiaZheng ;
           :addHerb ?herb .
}
```
（`addHerb` 为注释属性，部分三元组存储可能需启用注释查询）

---

## 八、版本管理与维护建议

1. **TBox 版本号**：医理体系升级（如增加方证、修改定义）更新 TBox 版本。
2. **ABox 版本号**：方剂、药物、患者数据更新独立管理。
3. **变更日志**：记录方证定义修改原因，如“根据《伤寒论》第 38 条增加大青龙汤证”。
4. **推理机兼容性**：确保所有构造子在 OWL 2 DL 范围内，避免 RDF-based 语义导致不可判定。

---

## 结语

如此分离，则**理法方药**各有其位：  
- **TBox** 者，犹仲景之条文，胡老之心法，万世不移；  
- **ABox** 者，犹临证之病历，患者之脉证，随人而变。  
推理机按其规矩，自能“观其脉证，知犯何逆，随证治之”。  

此设计既承《伤寒论》辨证论治之纲，又合本体工程之要，可长久为经方数字化之基石。