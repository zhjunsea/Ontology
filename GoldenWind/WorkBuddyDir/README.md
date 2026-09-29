# WorkBuddyDir — WorkBuddy 生成物的隔离目录

本目录集中存放 **WorkBuddy 在 `ontology/` 下生成的临时脚本、工具链与备份文件**。

- 建立时间：2026-09-25
- 范围约定（用户确认）：
  - **`design/` 目录下的任何文件都不挪动** —— 已挪出的 3 项已全部还原回 `design/`。
  - 仅收纳来自 `ontology/` 的临时脚本与工具链、备份文件。
  - `design/bpmn/` 依用户指示整体归入 `ontology/bpmn/`（保留在 `ontology/` 下，不还原）。

---

## 目录结构

```
WorkBuddyDir/
├── README.md                    ← 本文件
├── 临时脚本与工具链/
│   ├── ontology-verify/         ← 原 ontology/.verify/（22 个文件，约 13.6 MB）
│   ├── dump_ontology.py         ← 本体盘点脚本（输出类/对象属性/数据属性/个体清单）
│   ├── ontology_inventory.txt   ← 盘点结果（v13.0 裁剪前）
│   ├── prune_v14.py             ← v13.0 → v14.0 裁剪脚本（依 BPMN 流程删无关对象）
│   ├── check_v14.py             ← v14.0 校验脚本（rdflib：悬空引用 / Restriction / 不相交类）
│   ├── patch_v15.py             ← v14.0 → v15.0 修正脚本（依专家审视 P0-1 ~ P2 逐条决策）
│   ├── patch_doc_v15.py         ← 设计文档 v14.0 → v15.0 同步脚本
│   ├── patch_v151.py            ← v15.0 → v15.1（:扶持 改挂 :构件 + 单建 :扶持到焊缝距离）
│   ├── patch_v152.py            ← v15.1 → v15.2（:平台 数据属性精简为 2 条）
│   ├── patch_doc_v1512.py       ← 设计文档 v15.0 → v15.2 同步脚本
│   ├── patch_v153.py            ← v15.2 → v15.3（灯属性修正 + 爬梯属性精简）
│   ├── patch_doc_v153.py        ← 设计文档 v15.2 → v15.3 同步脚本
│   ├── patch_v154.py            ← v15.3 → v15.4（扶持删除 :扶持水平角度）
│   ├── patch_doc_v154.py        ← 设计文档 v15.3 → v15.4 同步脚本
│   ├── patch_v155.py            ← v15.4 → v15.5（删除线夹及相关属性）
│   ├── patch_doc_v155.py        ← 设计文档 v15.4 → v15.5 同步脚本
│   ├── patch_v156.py            ← v15.5 → v15.6（改名/材料改属性/删照明系统/扁平化/若干删除）
│   ├── patch_doc_v156.py        ← 设计文档 v15.5 → v15.6 同步脚本
│   ├── patch_v157.py            ← v15.6 → v15.7（术语统一：配件→附件；本体+文档+BPMN+UI）
│   ├── patch_v158.py            ← v15.7 → v15.8（分类值类→字符串数据属性 + 删电缆托架规格类）
│   ├── patch_doc_v158.py        ← 设计文档 v15.7 → v15.8 同步 + 规范文档追加 v15.8 章节
│   └── check_v15.py             ← 本体校验脚本（含不相交组内父子同组检测 + 关键结构断言）
└── 备份文件/
    ├── TowerMidSection.owl.bak   ← 原 ontology/TowerMidSection.owl.bak（v12.0 前）
    ├── TowerMidSection.owl.bak2  ← 原 ontology/TowerMidSection.owl.bak2（v13.0 前）
    ├── TowerMidSection.owl.bak3_v13.0_删除前   ← v14.0 裁剪前
    ├── TowerMidSection.owl.bak4_v14.0_修改前   ← v15.0 修正前（= v14.0 定稿）
    ├── TowerMidSection.owl.bak5_v15.0_扶持改挂前 ← v15.1 修正前（= v15.0 定稿）
    ├── TowerMidSection.owl.bak6_v15.1_平台属性精简前 ← v15.2 修正前（= v15.1 定稿）
    ├── TowerMidSection.owl.bak7_v15.2_修改前   ← v15.3 修正前（= v15.2 定稿）
    ├── TowerMidSection.owl.bak8_v15.3_修改前   ← v15.4 修正前（= v15.3 定稿）
    ├── TowerMidSection.owl.bak9_v15.4_修改前   ← v15.5 修正前（= v15.4 定稿）
    ├── TowerMidSection.owl.bak10_v15.5_修改前  ← v15.6 修正前（= v15.5 定稿）
    ├── TowerMidSection.owl.bak11_v15.6_修改前  ← v15.7 术语统一前（= v15.6 定稿）
    ├── TowerMidSection.owl.bak12_v15.7_修改前  ← v15.8 改造前（= v15.7 定稿）
    ├── 塔架中段设计本体.md.bak_v13.0_裁剪前     ← 设计文档 v14.0 同步前
    ├── 塔架中段设计本体.md.bak_v15.2_修改前     ← 设计文档 v15.3 同步前
    ├── 塔架中段设计本体.md.bak_v15.3_修改前     ← 设计文档 v15.4 同步前
    ├── 塔架中段设计本体.md.bak_v15.4_修改前     ← 设计文档 v15.5 同步前
    ├── 塔架中段设计本体.md.bak_v15.5_修改前     ← 设计文档 v15.6 同步前
    ├── 塔架中段设计本体.md.bak_v15.6_修改前     ← 设计文档 v15.7 同步前
    ├── 塔架中段设计本体.md.bak_v15.7_修改前     ← 设计文档 v15.8 同步前
    ├── 塔架中段本体-Openllet推理适配规范.md.bak_v15.6_修改前 ← v15.7 同步前
    ├── 塔架中段本体-Openllet推理适配规范.md.bak_v15.7_修改前 ← v15.8 同步前
    ├── 塔架中段定制化设计流程.bpmn.bak_v15.6_修改前 ← v15.7 同步前
    └── _build_ui.py.bak_v15.6_修改前            ← v15.7 同步前
```

---

## 内容说明

### 1. `临时脚本与工具链/ontology-verify/`（原 `ontology/.verify/`）

本体语法校验用的临时工具链，**与本体本身无关**，可随时删除。

| 文件 | 用途 |
|---|---|
| `Verify.java` / `Verify.class` | OWL API 加载本体的校验程序 |
| `patch_v13.py` | v12.0 → v13.0 本体批量改造脚本（含 `rep()` 唯一性校验、`del_block()` 正则删块） |
| `check_v13.py` | v13.0 校验脚本（rdflib 统计三元组 / Restriction 合法性） |
| `out.txt` | 校验输出日志 |
| `owlapi.jar`、`owlapi-dist-4.5.29.jar`、`owlapi-dist-5.5.0.jar` | OWL API 各版本发行包 |
| `guava-18.0.jar`、`guava-32.1.3-jre.jar`、`caffeine-3.1.8.jar`、`failureaccess-1.0.1.jar`、`checker-qual-3.37.0.jar`、`error_prone_annotations-2.21.1.jar`、`j2objc-annotations-2.8.jar`、`jsr305-3.0.2.jar`、`javax.inject-1.jar` | 传递依赖 |
| `commons-rdf-api.jar`、`commons-io-2.15.1.jar`、`slf4j-api-1.7.36.jar`、`slf4j-simple-1.7.36.jar` | 传递依赖 |
| `hppcrt-0.7.5.jar` | **无效文件（554 字节，实为 404 页面）**。`com.carrotsearch:hppcrt` 在 Maven Central 不存在，这是 OWL API 命令行校验始终跑不通的根因 |

> **遗留问题**：OWL API 4.5.29 / 5.5.0 均依赖 `com.carrotsearch:hppcrt`，该 artifact 在 Maven Central 不存在（仅有 `hppc`）。
> 因此命令行校验未能跑通；语法合法性以 rdflib 校验为准。用户本机 Protégé 自带该依赖，可在本地验证。

### 2. `备份文件/`

本体在改造前的版本备份，保留以便回溯。

| 文件 | 对应版本 |
|---|---|
| `TowerMidSection.owl.bak` | v12.0 之前 |
| `TowerMidSection.owl.bak2` | v13.0 之前 |
| `TowerMidSection.owl.bak3_v13.0_删除前` | v14.0 裁剪前（= v13.0 定稿） |
| `TowerMidSection.owl.bak4_v14.0_修改前` | v15.0 修正前（= v14.0 定稿） |
| `塔架中段设计本体.md.bak_v13.0_裁剪前` | 设计文档 v14.0 同步前 |

---

## 各目录当前状态

### `ontology/`

```
ontology/
├── TowerMidSection.owl        本体主文件（v15.8）
├── catalog-v001.xml           本体目录文件
└── bpmn/                      BPMN 流程 + UI
    ├── 塔架中段定制化设计流程.bpmn
    ├── index.html
    ├── _build_ui.py           UI 生成脚本（随 bpmn 目录）
    └── vendor/                bpmn-js 查看器 + 样式 + 图标字体（离线可用）
```

> `_build_ui.py` 按自身所在目录定位 `.bpmn` 与输出 `index.html`，
> 因此重新生成 UI 时直接在 `ontology/bpmn/` 下执行 `python _build_ui.py` 即可。

### `design/`（已完全还原，未做任何挪动）

```
design/
├── 塔架中段设计本体.md                        本体主设计文档
├── 塔架中段设计本体.md.bak                    文档 v13.0 更新前备份
├── 塔架中段本体-Openllet推理适配规范.md
├── 塔架中段本体-专家审视建议报告.md
├── 塔架中段本体-定制化设计流程符合性核查报告.md
├── 塔架中段设计本体审核报告_v9.3.md
├── 塔架中段-老工具硬编码与本体对应分析报告.md
├── 老塔架设计工具读取逻辑与硬编码-本体对应分析报告.md
├── 塔架中段设计流程.md
├── 流程访谈.txt
├── 塔架设计提示词.docx
├── ~$架设计提示词.docx                        Office 锁文件（异常退出残留）
├── towerdesign-main/                          老塔架设计程序（用户提供）
│   ├── ~$项目布局表.xlsx                       Office 锁文件（被 WPS 占用）
│   └── ~$TowerGeoInput_10563080_...xlsx        Office 锁文件（被 WPS 占用）
└── towerdesign-main.zip
```

### `OntologyMachine/`

用户自有 Java 工程，未做任何改动。

---

## 变更记录

| 时间 | 操作 |
|---|---|
| 2026-09-25 21:33 | 建立本目录；从 `ontology/` 移入 `.verify/` 与 2 个 `.owl.bak`；从 `design/` 移入 `_build_ui.py`、`~$架设计提示词.docx`、`塔架中段设计本体.md.bak` |
| 2026-09-25 21:34 | `design/bpmn/` → `ontology/bpmn/` |
| 2026-09-25 21:59 | **`design/` 相关 3 项全部还原**：`_build_ui.py` → `ontology/bpmn/`（随 bpmn 目录）、`~$架设计提示词.docx` → `design/`、`塔架中段设计本体.md.bak` → `design/`；移除空的 `Office锁文件/` 目录 |
| 2026-09-26 | **本体 v14.0 裁剪**：依 BPMN 流程删除 `:风机`/`:风机机头`/`:偏航轴承`/`:塔架底段`/`:塔架顶段` 5 类及 9 条失效对象属性；`:有机型`/`:有区域` 改挂 `:塔架中段`。新增 `prune_v14.py` / `check_v14.py` / `dump_ontology.py` / `ontology_inventory.txt`；备份 `TowerMidSection.owl.bak3_v13.0_删除前`、`塔架中段设计本体.md.bak_v13.0_裁剪前`。同步更新 `design/塔架中段设计本体.md`（**仅改内容，未挪动文件**） |
| 2026-09-26 | **本体 v15.0 修正**（依《塔架中段本体-专家审视报告 v14.0》与业务逐条决策 P0-1 ~ P2）：`:塔架段` 改为 `:塔架` 子类；新增顶层类 `:构件`（`:螺柱`/`:线槽` 由 `:附件` 改挂）；删除 `:直径`；`:环焊缝 连接筒节` 改精确基数 2；`:电缆托架规格` 补 `hasKey`；补 3 条有依据约束；灯相关 3 属性改名；删除 8 条无依据约束公理。新增 `patch_v15.py` / `patch_doc_v15.py` / `check_v15.py`；备份 `TowerMidSection.owl.bak4_v14.0_修改前`。同步更新 `design/塔架中段设计本体.md`（**仅改内容，未挪动文件**）。校验：1083 三元组、33 类、64 对象属性、70 数据属性、19 Restriction（invalid=0）、悬空引用 0 |
| 2026-09-26 | **本体 v15.1**（业务决策：扶持归属修订）：`:扶持` 由 `:附件` 改挂 `:构件`（扶持不算附件，其属性与附件不同）；新增 `:扶持到焊缝距离`（>100）以**仅保留焊缝距离约束**，不再继承 `:附件中心间距`、不参与附件排布；不相交组相应调整（附件子类 4 类、构件子类 3 类）。新增 `patch_v151.py`；备份 `TowerMidSection.owl.bak5_v15.0_扶持改挂前`。校验：1098 三元组、71 数据属性、20 Restriction（invalid=0）、悬空引用 0 |
| 2026-09-26 | **本体 v15.2**（业务决策：平台属性精简）：`:平台` 数据属性**只保留 `:平台到筒顶距离` 与 `:平台所在处内径`**，删除 `:平台宽度`/`:平台集中荷载`/`:栏杆高度`/`:踢脚板高度`。新增 `patch_v152.py` / `patch_doc_v1512.py`；备份 `TowerMidSection.owl.bak6_v15.1_平台属性精简前`。同步更新 `design/塔架中段设计本体.md`。校验：1078 三元组、33 类、64 对象属性、67 数据属性、20 Restriction（invalid=0）、悬空引用 0 |
| 2026-09-26 | **本体 v15.3**（业务决策：灯属性修正 + 爬梯属性精简）：**(A) 灯**——`:焊接灯` 恰好有 2 个螺柱（`:安装在螺柱` `qualifiedCardinality = 2`）；`:灯上螺柱间距` 归属由 `:照明系统` 移至 `:焊接灯`（=500）；`:灯螺柱到焊缝距离` 明确为上下两个螺柱各自 > 100；删除 `:下灯位置`/`:上灯位置`。**(B) 爬梯**——只保留 `:梯子高度`，删除 `:踏级间距`/`:踏面宽度`/`:爬梯净宽`/`:爬梯与塔壁净距`。新增 `patch_v153.py` / `patch_doc_v153.py`；备份 `TowerMidSection.owl.bak7_v15.2_修改前`、`塔架中段设计本体.md.bak_v15.2_修改前`。同步更新 `design/塔架中段设计本体.md`。校验：1051 三元组、33 类、64 对象属性、61 数据属性、21 Restriction（invalid=0）、悬空引用 0 |
| 2026-09-26 | **本体 v15.4**（业务决策：扶持属性精简）：删除 `:扶持水平角度`（`supportHorizontalAngle`）；`:扶持` 现仅保留 `:扶持位置` 与 `:扶持到焊缝距离`。新增 `patch_v154.py` / `patch_doc_v154.py`；备份 `TowerMidSection.owl.bak8_v15.3_修改前`、`塔架中段设计本体.md.bak_v15.3_修改前`。同步更新 `design/塔架中段设计本体.md`。校验：1045 三元组、33 类、64 对象属性、60 数据属性、21 Restriction（invalid=0）、悬空引用 0 |
| 2026-09-26 | **本体 v15.5**（业务决策：删除线夹及相关属性）：删除类 `:电缆线夹`（CableClamp）及对象属性 `:线夹映射到爬梯支撑`/`:夹持电缆`、数据属性 `:线夹位置`/`:是否隔开电缆线夹`；不相交组「附件子类」4→3 类。新增 `patch_v155.py` / `patch_doc_v155.py`；备份 `TowerMidSection.owl.bak9_v15.4_修改前`、`塔架中段设计本体.md.bak_v15.4_修改前`。同步更新 `design/塔架中段设计本体.md`（含 SWRL 9.4 删除与 9.5/9.6 重编号）。校验：1017 三元组、32 类、62 对象属性、58 数据属性、21 Restriction（invalid=0）、悬空引用 0 |
| 2026-09-26 | **本体 v15.6**（业务决策：属性改名 + 材料改属性 + 删照明系统 + 扁平化 + 若干删除）：① `:与…平齐` 改名 `:平齐`（主语与宾语等高）；② 删除 `:材料` 类及其属性，改为每个主要结构件的 `:材质` 数据属性（并集域 8 类）；③ 删除 `:照明系统` 容器类及其组成属性（灯保留），`:有灯` 改挂 `:塔架中段`，三条照明布置属性改挂 `:塔架中段`（约束公理随之迁移）；④ 扁平化删除中间类 `:塔架段`（`:塔架中段` 直接 ⊂ `:塔架`），`:有塔架段`→`:有塔架中段`、`:法兰连接塔架段`→`:法兰连接塔架中段`、`:相邻于` 域/值域改 `:塔架中段`；⑤ 删除 `:疲劳等级`；⑥ 删除 `:螺柱安装角度`；⑦ 删除 `:配件类型名称`（`:配件类型` 身份键移除）；⑧ 删除 `:安全锚点` 类及其属性，附件子类不相交组 3→2 类；⑨ `:支撑宽度` 改名 `:宽度`（并集域扩至 `:电缆托架`）。新增 `patch_v156.py` / `patch_doc_v156.py`；备份 `TowerMidSection.owl.bak10_v15.5_修改前`、`塔架中段设计本体.md.bak_v15.5_修改前`。同步更新 `design/塔架中段设计本体.md`（56 处）。校验：958 三元组、28 具名类、57 对象属性、53 数据属性、20 Restriction（invalid=0）、21 inverseOf、6 hasKey、37 FunctionalProperty、7 AllDisjointClasses、2 个体；悬空引用 0、不相交组内无父子同组 |
| 2026-09-26 | **本体 v15.7**（业务决策：术语统一——「配件」与「附件」指同一事物，统一为「附件」）：类 `:配件类型` → `:附件类型`（AccessoryType）；对象属性 `:选用配件类型` → `:选用附件类型`（selectsAccessoryType）、`:配件类型适用于` → `:附件类型适用于`（accessoryTypeUsedByConnectionType）；本体注释与历史条目中的旧名一并改写；实例化示例个体 `:配件_焊接式`/`:配件_粘贴式` → `:附件类型_焊接式`/`:附件类型_粘贴式`。**范围（用户确认）**：`ontology/TowerMidSection.owl`、`design/塔架中段设计本体.md`、`design/塔架中段本体-Openllet推理适配规范.md`、`ontology/bpmn/` 下 `.bpmn` + `_build_ui.py` + 重新生成的 `index.html`；**未改** `design/塔架中段设计流程.md`、`design/流程访谈.txt`（流程原文，步骤 8.1 标题仍为「替换配件类型」）及历史审视/审核/核查报告。新增 `patch_v157.py`；备份 `TowerMidSection.owl.bak11_v15.6_修改前`、`塔架中段设计本体.md.bak_v15.6_修改前`、`塔架中段本体-Openllet推理适配规范.md.bak_v15.6_修改前`、`塔架中段定制化设计流程.bpmn.bak_v15.6_修改前`、`_build_ui.py.bak_v15.6_修改前`。校验：958 三元组、28 具名类、57 对象属性、53 数据属性、20 Restriction（invalid=0）、21 inverseOf、6 hasKey、37 FunctionalProperty、7 AllDisjointClasses、2 个体；悬空引用 0、不相交组内无父子同组；标识符中已无「配件/Fitting」 |
| 2026-09-26 | **本体 v15.8**（业务决策：分类值类改为物理对象的字符串数据属性 + 删除电缆托架规格类）：① `:附件连接方式` / `:附件类型` 改为 `:附件` 的数据属性——删除类 `:附件连接方式`（AccessoryConnectionType）、`:附件类型`（AccessoryType）及对象属性 `:有连接方式`/`:连接方式属于`、`:选用附件类型`/`:附件类型适用于`，新建数据属性 `:附件连接方式`/`:附件类型`（域 `:附件`，xsd:string + FunctionalProperty）；② `:灯类型` 改为 `:灯` 的数据属性——删除类 `:灯类型`（LightType）及对象属性 `:有灯类型`/`:灯类型属于`，新建 `:灯类型`（域 `:灯`）；③ 删除身份键 `:连接方式名称`/`:灯类型名称` 与 2 个分类值个体（粘贴式/焊接式）；④ 删除类 `:电缆托架规格`（CableBracketSpec）及对象属性 `:有电缆托架规格`/`:托架规格属于`，`:托架长度`/`:托架右弦长`/`:托架左弦长` 域改挂 `:电缆托架`；⑤ 分类值不相交组 6→2 类（仅剩 `:机型`/`:区域`），`hasKey` 6→3 组，`FunctionalProperty` 37→38 条。新增 `patch_v158.py` / `patch_doc_v158.py`；备份 `TowerMidSection.owl.bak12_v15.7_修改前`、`塔架中段设计本体.md.bak_v15.7_修改前`、`塔架中段本体-Openllet推理适配规范.md.bak_v15.7_修改前`。同步更新 `design/塔架中段设计本体.md`（含修复被编辑器损坏的类层次图 `:附件类型` 行、新增 3.16 章节、实例化示例改写）与 `design/塔架中段本体-Openllet推理适配规范.md`（追加「十三、v15.8 补充」章节）。**BPMN/UI 无相关引用，未改动。** 校验：881 三元组、24 具名类、49 对象属性、54 数据属性、20 Restriction（invalid=0）、17 inverseOf、3 hasKey、38 FunctionalProperty、7 AllDisjointClasses、0 个体；悬空引用 0、不相交组内无父子同组 |
