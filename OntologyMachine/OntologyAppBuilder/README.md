# OntologyAppBuilder（本体应用构建 · SKILL 工程）

`OntologyAppBuilder` 是本仓库内的**一等工程目录**，与 `OntologyFrameworkExample`、`OntologyFrameworkPizza` 等模块并列。它**不是**一个可运行的本体应用，而是一个**「本体应用构建」的技能工程（SKILL Project）**：

> 以**业务场景**为输入，按 **14 步流程**，把场景落地为**与 OntologyFrameworkExample 同构、可运行、可验收**的本体应用模块；全过程强制满足 `Reference/公共铁律.md`（24 条），**完全按业务、有据可依、不编造**。

---

## 目录结构

```
OntologyAppBuilder/
├── SKILL.md                        ← 技能主文档（14 步编排 + 11 个人工闸门 + 铁律强制 + 资源索引）
├── README.md                       ← 本文件（工程说明与启用方式）
├── references/                     ← 规范与依据
│   ├── 公共铁律-强制基线.md
│   ├── 步骤规范.md
│   ├── Example-文件映射.md
│   ├── API复用清单.md
│   └── 领域铁律-补充.md
├── templates/                      ← 产出工件模板（取自 Example 文档形态）
│   ├── 顶层设计模板.md             ← 第 1 步
│   ├── 流程设计模板.md             ← 第 2 步（闸门①）
│   ├── 本体顶层设计模板.md         ← 第 3 步（闸门②；另留痕闸门⑧/⑨）
│   ├── 本体核心设计模板.md         ← 第 4 步（闸门③；另留痕闸门⑦）
│   ├── 应用架构设计模板.md         ← 第 7 步（闸门④；另留痕闸门⑩/⑪）
│   ├── 应用README模板.md           ← 第 7 步（模块根 README：工程说明 + 打包/发布/运行）
│   ├── 测试设计文档模板.md         ← 第 8 步（闸门⑤）
│   └── SKOS词汇表设计模板.md       ← 第 13 步（闸门⑥）
└── scripts/
    └── validate_structure.py       ← 新应用工程工件齐备性 / 契约一致性校验
```

---

## 与 CodeArts 技能机制的关系

`SKILL.md` 采用 CodeArts 技能标准格式（YAML frontmatter：`name` + `description`）。为使其被技能加载器识别，可将其**注册/挂载**到技能目录：

- 项目级：把 `OntologyAppBuilder/` 复制或软链到 `OntologyMachine/.codeartsdoer/skills/ontology-app-builder/`；
- 或按本环境技能加载约定登记。

> 本工程默认**随仓库版本管理**（作为一等目录），不自动随 `.codeartsdoer/skills/` 加载。

---

## 使用方式（概览）

1. 让执行 Agent 加载 `SKILL.md`；
2. 用**首句提示词**启动（见 `SKILL.md`「启动提示词」一节）：**首句即声明身份**——`你是 xxx 领域的本体应用构建专家`（`xxx` 换成本应用所属领域，如「战略洞察」「中医养生」，**该身份贯穿 14 步全程**），并给出**业务场景描述**、领域黄金标准/语料位置、参考基线 `OntologyFrameworkExample`，以及「每步产出后在人工闸门停下等确认」的要求；
3. 按 14 步执行：每步产出对应工件（参见 `references/步骤规范.md`），在**闸门①~⑪**处停下等业务人员确认（⑦第 6 步典型 ABox、⑧第 9 步前是否分离、⑨第 10 步是否拆分、⑩第 11 步是否用库、⑪第 12 步如何导入完整数据）；新应用模块建在**工作区根**下、与 `OntologyFrameworkExample` 并列，并在**第 7 步**把 `<module>OntologyFrameworkXxx</module>` 登记进工作区根聚合 `pom.xml` 的 `<modules>`（否则 IDEA 打开工作区看不到新工程）；
4. 用 `scripts/validate_structure.py <新应用模块路径>` 校验结构齐备性；
5. 全部通过后运行 `All<Xxx>SuiteTest` 做全流程回归。

---

## 基线来源（有据可依）

- **文件形态基线**：`../OntologyFrameworkExample/`（design 三件套、ontology、src、tests）。
- **铁律基线**：`../Reference/公共铁律.md`（24 条）。
- **测试文档形态**：`../OntologyFrameworkPizza/design/披萨应用（Pizza）测试用例设计文档.md`。
- **构建环境链路**：`../OntologyFrameworkPlatform/`（分发/离线仓库）、`../EnvPrepare/`（环境启动）。
