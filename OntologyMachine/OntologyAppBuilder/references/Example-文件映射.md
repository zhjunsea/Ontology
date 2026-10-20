# 14 步产出 ↔ OntologyFrameworkExample 文件映射

> 本表是「一切参考 OntologyFrameworkExample」的**落地依据**：每一步的产出形态，都以 Example 的对应文件为模板基线。
> 路径以**工作区根**为根（开发仓库为 `OntologyMachine/`，发布环境为安装器释放出的开发工作目录；`OntologyFrameworkExample/` 位于其下、与其并列的是各新应用模块）；`Xxx` = 新应用简称，`xxx` = 小写简称。

| 步骤 | 产出物 | 参考 Example 文件（模板基线） |
|---|---|---|
| 1 | 应用顶层设计（场景与范围） | `OntologyFrameworkExample/design/Example应用-顶层设计.md`（§1 概述与定位、§2 范围、§8 关键决策） |
| 2 | 流程设计文件 | `OntologyFrameworkExample/design/Example应用-流程设计.md`；业务原文样例 `OntologyFrameworkExample/design/披萨制作和烘培的流程是.txt` |
| 3 | 本体顶层设计 | `OntologyFrameworkExample/design/Example应用-本体设计.md`（§理念、§资产组织、§命名空间/import、§顶层类框架） |
| 4 | 本体核心设计（核心类/属性/关系） | `OntologyFrameworkExample/design/Example应用-本体设计.md`（§TBox 类层级、§对象/数据属性、§约束、§SWRL） |
| 5 | BPMN（含 UI） | `OntologyFrameworkExample/ontology/bpmn/myPizzaBakingProcess.bpmn`；UI：`src/main/resources/static/index.html` + `src/main/java/.../web/ExampleController.java` |
| 6 | 单文件本体（TBox + 典型 ABox） | `OntologyFrameworkExample/ontology/pizza-all.owl`（组织形态）；单文件形态 |
| 7 | 应用架构设计 + JobWorker + 工程骨架（pom/README）+ 环境启停脚本 + IDEA 运行配置 | 架构文档：`design/Example应用-顶层设计.md`（§运行时架构、§组件与包、§解耦、§数据流）；代码：`src/main/java/.../{ExampleApplication.java, ExampleOntologyJobWorker.java, web/ExampleController.java}`；配置：`src/main/resources/application.yaml`；**工程骨架：`OntologyFrameworkExample/pom.xml`（含打包插件 `spring-boot-maven-plugin`/`surefire(useManifestOnlyJar=false)`/`maven-source-plugin`）+ `OntologyFrameworkExample/README.md`（工程说明 + 打包/发布/运行，模板 `templates/应用README模板.md`）**；环境启停脚本：`OntologyFrameworkExample/start-env.cmd`（模块根，一键 createdb→start→create-rabbitmq）；IDEA 运行配置：`<工作区根>/.idea/runConfigurations/<Xxx>Application.xml`（主类 `<Xxx>Application`、`Working directory=$MODULE_WORKING_DIR$`；**须落工作区根/IDEA 工程根、非模块内**——IDEA 只加载工程根，模块内不可见；依 SKILL 步骤规范 §7-⑤ + 模板 §3.4）；**环境启动器 jar：`EnvPrepare-<version>.jar`（置于模块根、与 `start-env.cmd` 同目录；Example 源码目录不含，取自 `build/` 或 `-project.zip`）**；**工作区聚合 pom 登记：在工作区根 `pom.xml`（`packaging=pom`）的 `<modules>` 中新增 `<module>OntologyFrameworkXxx</module>`（与 `<module>OntologyFrameworkExample</module>` 并列，使 IDEA 打开工作区即可见新模块）** |
| 8 | 测试设计文档 + 用例 | 测试文档形态：`OntologyFrameworkPizza/design/披萨应用（Pizza）测试用例设计文档.md`；用例基线：`OntologyFrameworkExample/src/test/java/.../{ExampleBpmnRealEngineTest.java, ExampleSwrlRabbitMqTest.java, AllExampleSuiteTest.java}` |
| 9 | TBox/ABox 分离（两个 owl） | `OntologyFrameworkExample/ontology/{pizza-all.owl, pizza-abox.owl, pizza-components-abox.owl, pizza-processes-abox.owl}`（分离形态） |
| 10 | TBox 拆分 | `OntologyFrameworkExample/ontology/` 多文件 TBox 组织（`pizza.owl` / `pizza-components.owl` / `pizza-components-rules.owl` / `pizza-processes.owl`）+ `catalog-v001.xml` |
| 11 | 典型 ABox 入库 + 库环境/OBDA | `OntologyFrameworkExample/ontology/database/{createdb_tables.ddl, data.sql, myPizza.obda, myPizza.properties}` |
| 12 | ABox 扩展到全集 | 以 Example 的 ABox（`pizza*-abox.owl`）+ `database/data.sql` 为形态基线，扩至全集 |
| 13 | SKOS 词汇表 | `OntologyFrameworkExample/ontology/pizza-terminology.ttl` |
| 14 | 词汇表测试 + 全流程测试 | 词汇表测试形态：`OntologyFrameworkPizza/src/test/java/.../validation/PizzaSkosTerminologyTest.java`；全流程入口：`AllExampleSuiteTest.java` |

---

## Example 关键文件清单（核对用）

工作区（聚合工程）布局（新应用与 `OntologyFrameworkExample/` 并列于工作区根）：

```
<工作区根>/                          ← IDEA 打开此目录（聚合工程根）
├── pom.xml                          ← 父聚合 pom（packaging=pom；<modules> 含 OntologyFrameworkExample 及各新应用）
├── OntologyFrameworkExample/        ← 示例模块（子模块，清单见下）
├── OntologyFrameworkXxx/            ← 新应用模块（第 7 步产出；登记进根 pom 的 <modules>）
├── .mvn/maven.config                ← 离线仓库配置（安装器写于工作区根）
└── .codeartsdoer/skills/ontology-app-builder/   ← 安装器投放的 SKILL
```

示例模块 `OntologyFrameworkExample/` 关键文件：

```
OntologyFrameworkExample/
├── design/
│   ├── Example应用-顶层设计.md
│   ├── Example应用-流程设计.md
│   ├── Example应用-本体设计.md
│   └── 披萨制作和烘培的流程是.txt
├── ontology/
│   ├── bpmn/myPizzaBakingProcess.bpmn
│   ├── catalog-v001.xml
│   ├── pizza-all.owl
│   ├── pizza.owl / pizza-components.owl / pizza-components-rules.owl / pizza-processes.owl
│   ├── pizza-abox.owl / pizza-components-abox.owl / pizza-processes-abox.owl
│   ├── pizza-terminology.ttl
│   └── database/{createdb_tables.ddl, data.sql, myPizza.obda, myPizza.properties}
├── src/main/java/com/ocean/ontologyframework/example/
│   ├── ExampleApplication.java
│   ├── ExampleOntologyJobWorker.java
│   └── web/ExampleController.java
├── src/main/resources/{application.yaml, static/index.html}
├── src/test/java/com/ocean/ontologyframework/example/
│   ├── AllExampleSuiteTest.java
│   ├── ExampleBpmnRealEngineTest.java
│   └── ExampleSwrlRabbitMqTest.java
├── start-env.cmd
├── EnvPrepare-<version>.jar    ← 新应用须含于模块根（与 start-env.cmd 同目录）；Example 源码目录不含，取自 build/ 或 -project.zip
├── README.md                   ← 工程说明 + 打包/发布/运行（第 7 步产出，照 Example 形态）
└── pom.xml                     ← 含打包插件（spring-boot-maven-plugin / surefire(useManifestOnlyJar=false) / maven-source-plugin）
```

> 该清单可直接作为 `scripts/validate_structure.py` 的期望集合来源（脚本按同构规则校验新应用）。
