# 应用发布工程（OntologyAppRelease）

> 独立脚本工程：把一个本体应用打包成「客户可直接安装运行」的 ZIP 运行包，并生成安装文档。
> **不参与 Maven reactor 构建**（无 `pom.xml`，独立于 `OntologyMachine` 聚合工程）。

## 1. 工程定位

| 项 | 说明 |
|---|---|
| 输入 | 一个**应用模块名**（如 `OntologyFrameworkLatticeTower`） |
| 处理 | 定位该模块的 fat jar 与 `ontology/` 资源；**按需裁剪**依赖软件；组装运行布局与安装器 |
| 输出 | ① **运行包** `<模块名>-<版本>-runtime.zip` + `安装文档.md`；② **开发工程** `<模块名>-<版本>-project.zip` |
| 客户侧 | 运行包：解压 → `install.cmd` → `start-env.cmd` → `start-app.cmd`；开发工程：解压 → `deploy-dev.cmd <开发环境目录>` → IDEA 打开 |
| 客户形态 | 运行包**仅运行**（无需 IDEA/Maven/联网/预装 Java）；开发工程**可开发**（工作区聚合工程，等同 OntologyMachine 形态） |

## 2. 目录

```
OntologyAppRelease/
├── release.py                 # 打包工具（Python 3）
├── templates/
│   ├── install.cmd            # 客户安装入口（GBK，打包期填充应用名）
│   ├── install.ps1            # 安装引擎（Windows 内置 PowerShell 5.1）
│   ├── start-env.cmd.tmpl     # 环境服务启动脚本模板
│   ├── start-app.cmd.tmpl     # 应用 Worker 启动脚本模板
│   ├── 安装文档.md.tmpl        # 安装文档模板
│   ├── workspace-pom.xml.tmpl # 开发工程：工作区聚合 pom 模板（@{MODULE}/@{VERSION}）
│   ├── deploy-dev.cmd         # 开发工程：开发环境部署入口（GBK，打包期填充应用名）
│   ├── deploy-dev.ps1         # 开发工程：开发环境部署引擎（Windows 内置 PowerShell 5.1）
│   └── 开发环境部署说明.md.tmpl # 开发工程：部署说明模板
└── output/                    # 打包产物（zip；由工具生成）
```

> `application.yaml.tmpl` **不在此目录**：它由 `release.py` 从被打包应用的
> `application.yaml` 实时派生（保留原注释与配置，仅把各软件 home 值 token 化）。

## 3. 使用

```bash
# 打包（默认从仓库 softwares/ 取软件、产物落 output/）
python release.py --app OntologyFrameworkLatticeTower

# 随包携带 Python 客户端（自动定位 <repo>/OntologyFrameworkPythonClient）+ Python 运行时
python release.py --app OntologyFrameworkLatticeTower --with-python-client

# 随附任意载荷目录（可重复）；卸载时释放到 <安装根>\<name>
python release.py --app Xxx --extra python-client=D:\path\to\client --extra docs=D:\path\to\docs

# 只组装、不压缩（快速核对裁剪与产物结构）
python release.py --app OntologyFrameworkLatticeTower --dry-run

# 指定仓库根 / 软件目录 / 输出目录
python release.py --app OntologyFrameworkExample --out D:\tmp\rel

# 发布「开发工程」zip（工作区聚合工程：根 pom + 应用模块源码，可用 IDEA 打开/部署到开发环境）
python release.py --app OntologyFrameworkLatticeTower --project           # 运行包 + 开发工程
python release.py --app OntologyFrameworkLatticeTower --project-only      # 仅开发工程（无需构建 fat jar）
python release.py --app OntologyFrameworkTMSD --project-only --project-exclude tmsd-output
```

前置条件：待打包模块已 `mvn package`（`target/<模块>-<版本>.jar` 存在）；基础库
（`EnvPrepare` / `OntopOBDAHandler` / `OpenlletResolver` / `Utilities`）已构建或已在 `build/`。

## 4. 依赖软件「按需裁剪」规则

`release.py` 解析被打包应用的 `application.yaml`（忽略注释）后判定：

| 软件 | 随包条件 |
|---|---|
| JDK | **恒随包**（运行必备） |
| Camunda 8 | 声明 `camunda:` 节点，或 `env-prepare.camunda-home` 非空，或 `ontology.bpmn-path` 存在 |
| MySQL | 声明 `env-prepare.mysql` 节点（整块注释即视为不需要） |
| Ontop | `env-prepare.ontop-home` 非空 **且** `ontology.obda-path` 存在 |
| RabbitMQ | 声明 `env-prepare.rabbitmq` 节点 |
| Erlang/OTP | 随 RabbitMQ 一并携带（或 `env-prepare.erlang-home` 非空） |
| Python | **随包 `python-client` 时携带**（`--with-python-client` 或 `--extra python-client=...`）；kind=exe，安装时静默安装到 `runtime\python` |

裁剪结果体现在：`softwares/` 只含所需压缩包、`manifest.json.softwares`、安装文档软件清单。

## 5. 产物结构（客户解压后）

```
<模块名>-<版本>-runtime.zip
├── install.cmd                双击安装
├── manifest.json              打包清单（软件列表 + home 探测规则 + payloadDirs + pythonClient）
├── 安装文档.md                 面向客户的安装与使用说明
├── softwares/                 按需裁剪的软件压缩包（zip）+ 安装器（exe，如 Python）
├── payload/
│   ├── app/                   应用 fat jar + ontology/
│   ├── lib/                   基础依赖库（含 EnvPrepare）
│   ├── python-client/         （可选）随附的 Python 客户端脚本
│   └── templates/             application.yaml.tmpl / start-*.cmd.tmpl
└── tools/install.ps1          安装引擎
```

安装后（`install.cmd` 执行结果）：

```
<安装目录>/
├── app/           应用 jar + application.yaml（含注入的绝对路径）+ ontology/
├── lib/           基础依赖库
├── runtime/       解压/安装后的各软件 home（含 python）
├── python-client/ （可选）Python 客户端脚本（随包时）
├── start-env.cmd  启动环境服务（GBK）
├── start-app.cmd  启动应用 Worker（GBK）
├── start-client.cmd（可选）运行 Python 客户端（GBK，随包 python-client 时生成）
└── .installed.json 安装记录
```

## 5.1 随包 Python 客户端（可选）

用 `--with-python-client`（或 `--extra python-client=<path>`）可将 `OntologyFrameworkPythonClient` 随包携带：

- 载荷放 `payload/python-client/`（自动排除 `.venv` / `out` / `__pycache__` / `*.iml`），安装后释放到 `<安装根>\python-client\`；
- 自动**追加 Python 运行时**为依赖软件（`kind=exe`，安装时静默安装到 `runtime\python`；`manifest.softwares` 与安装文档软件清单同步体现）；
- 自动按应用模块名推导入口脚本（`OntologyFrameworkXxx` → `verify_xxx.py`，否则 `xxx_client.py`），写入 `manifest.pythonClient`；
- 安装后生成 **`start-client.cmd`**（GBK+CRLF）：切到 `python-client\`，用随包 Python 运行入口脚本，可透传应用地址参数（如 `start-client.cmd http://localhost:9086`）。

## 5.2 开发工程（`--project` / `--project-only`）

除运行包外，`release.py` 还可发布应用的**开发工程** —— 一个**工作区聚合工程** zip，
形态对齐发布包（dist）内的 `OntologyFrameworkExample-<版本>-project.zip`，可直接部署到开发环境。

### 产物结构（解压后）

```
<模块名>-<版本>-project.zip   → 解压即得一个「工作区根」
├── pom.xml                   # 工作区聚合 pom（packaging=pom，<modules> 含本模块）
├── <模块名>/                  # 应用模块完整源码
│   ├── pom.xml  src/  ontology/  design/  README.md  .gitignore
│   ├── start-env.cmd         # 模块自带环境启动脚本（建库灌数、拉起环境服务）
│   └── EnvPrepare-<版本>.jar  # 打包期注入（供 start-env.cmd）
├── deploy-dev.cmd            # 开发环境部署入口（GBK+CRLF）
├── tools/deploy-dev.ps1      # 部署引擎（UTF-8+BOM；部署时释放随包 JDK 到 <开发环境>/jdk）
├── softwares/jdk-*.zip       # 随包 JDK（与运行包同一份；保证 JDK 27，避免版本不一致）
└── 开发环境部署说明.md         # 两种部署情形的说明
```

> **JDK 一致性**：开发工程随包携带与运行包相同的 JDK 27；`deploy-dev.cmd` 部署时释放到
> `<开发环境>\jdk`，模块 `start-env.cmd` 优先使用它（`ONTOLOGY_JAVA_HOME` → `<模块>/jdk`
> → `<开发环境>/jdk` → 仓库 `jdk-27` → `JAVA_HOME` → PATH 的 `java`），避免落到系统低版本 JDK。

### 部署方法（`deploy-dev.cmd <目标开发环境目录>`）

「**开发环境**」= 一个工作区聚合工程（根 `pom.xml` 通过 `<modules>` 并列多个应用模块），
与当前的 **OntologyMachine** 完全一致——多应用模块共存于同一工作区。脚本按目标目录状态自动判定：

| 情形 | 行为 |
|---|---|
| 目标目录**不存在** | **新建**开发环境：把本工程释放为该目录的工作区根（根 pom + 模块） |
| 目标目录**已是开发环境**（根含聚合 pom） | **并入**：拷入本模块并登记到根 pom 的 `<modules>`，与已有模块（如 `OntologyFrameworkExample`）**共用同一开发环境** |
| 目标目录存在但不是开发环境 | 报错并提示（避免误写） |

- 幂等：重复部署会覆盖更新模块文件、并避免在 `<modules>` 中重复登记；
- 部署后用 IDEA 打开**工作区根目录** → Reload Maven → 运行模块 `start-env.cmd` 与主类即可开发调试；
- 编码硬约束：`deploy-dev.cmd` 为 **GBK+CRLF**，`deploy-dev.ps1` 为 **UTF-8+BOM**，`开发环境部署说明.md` 为 UTF-8。

## 6. 关键设计约定

- **绝对路径在安装时注入**：安装目录由客户自选，故 `install.ps1` 解压后按「特征文件」探测各软件 home，
 把绝对路径写入生成的 `application.yaml` 与 `start-*.cmd`；**绝不写死构建机路径**。重复安装可迁移目录。
- **编码硬约束**：`install.cmd` / `start-env.cmd` / `start-app.cmd` 一律 **GBK(ANSI) + CRLF**（否则 cmd 中文乱码）；
  `application.yaml` / `安装文档.md` / `manifest.json` 为 UTF-8（无 BOM）。
- **本体从文件系统加载**：`ontology/` 必须随包（不打进 jar），与 `application.yaml` 中的相对路径
  （以 `app/` 为模块根）一致。
- **环境服务按需启停**：沿用 `EnvPrepare` 语义；未声明的服务自动 `[SKIP]`。
- **便携式 MySQL**：随包为便携 MySQL，安装时 `mysqld --initialize-insecure` 初始化 `data`，运行时不注册 Windows 服务。
- **开发工程 = 工作区聚合工程**：与运行包并列产出；解压即得工作区根（等同 OntologyMachine 形态），
  `deploy-dev.cmd` 支持「新建开发环境」与「并入已有开发环境（与 Example 共用）」两种情形。

## 7. 与既有工程的关系

- 与 `OntologyInstaller`（**开发环境**安装包：IDEA + 离线 Maven 仓库 + 全量软件，约 4.5 GB）**不同**：
  本工程产出的是**运行环境**运行包（仅运行，按需裁剪）。
- **开发工程**与 `OntologyFrameworkPlatform` 生成的 `OntologyFrameworkExample-…-project.zip` **同形**
  （工作区聚合 pom + 模块源码）；区别是后者固定为 Example、由 Maven antrun 产出，本工程可对**任意应用模块**产出，
  并额外附带 `deploy-dev.cmd` 部署器（新建 / 并入开发环境）。
- 复用 `EnvPrepare` 的环境启停能力与 `application.yaml` 的 `env-prepare` 配置语义（含 token 约定）。
