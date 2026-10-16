# OntologyFrameworkPlatform 发行包

本发行包由 `OntologyFrameworkPlatform` 模块构建产出，用于**部署本体应用开发环境**并
**开发与运行**类似 `OntologyFrameworkTCM` 的新本体应用。

## 内容

构建后 `OntologyMachine/build/` 顶层（开发分发物）：

- `OntopOBDAHandler-*.jar`、`OpenlletResolver-*.jar`、`Utilities-*.jar` — 基础库
- `EnvPrepare-*.jar` — 可执行环境启动工具（支持 **service** 与 **portable** 两种 MySQL 模式）
- `OntologyFrameworkExample-*-project.zip` — **工作区聚合工程包**（根为父聚合 `pom.xml` + `OntologyFrameworkExample/` 子模块：含源码/本体/设计 + 模块根 `README.md` 工程说明 + EnvPrepare 可执行 jar 与一键环境脚本 `start-env.cmd`；解压后在 IDEA 打开**工作区根**即可见示例，双击脚本一键备好环境）
- `OntologyFrameworkExample-*-sources.jar` — 示例工程源码（仅看代码参考）
- `lib/` — 第三方依赖集合
- `DEPENDENCIES.txt` / `DEPENDENCY-TREE.txt` — 依赖清单
- `installer/` — 客户一键安装器目录（**构建中间产物**，见下）
- `OntologyInstaller-0.0.1-SNAPSHOT-dist.zip` — **发布态压缩包**（对外交付物，见下）
- `新应用开发与部署指南.md` — **请从此文档开始**

## 发布态：单一压缩包

对外交付只需 **`OntologyInstaller-0.0.1-SNAPSHOT-dist.zip`**（由 `OntologyInstaller` 模块
构建时自动产出，即把整个 `installer/` 目录打成的一个 zip）。解压后得到：

```
installer/
├── start-installer.cmd                       # 双击启动（自带 JDK，无需预装 Java）
├── OntologyInstaller.jar                     # Swing 安装器（可执行 fat jar）
├── softwares/                                # 全部待装软件（.zip）
├── lib/                                      # 四个基础库 jar（新应用开发/运行的基础依赖）
├── OntologyFrameworkExample-*-project.zip    # 工作区聚合工程包（父聚合 pom + OntologyFrameworkExample 子模块）
├── maven-repo.zip                            # 离线 Maven 仓库快照
├── skills/ontology-app-builder/              # 本体应用构建 SKILL（安装时投放到工作区根 .codeartsdoer/skills/）
├── README.md                                 # 本文件
└── 新应用开发与部署指南.md
```

> `skills/ontology-app-builder/` 是「本体应用构建」技能（14 步流程 + 11 个人工闸门 + 铁律）。安装器会把它
> **自动投放**到释放出的**工作区根** `.codeartsdoer/skills/ontology-app-builder/`，客户用随包 IDEA 打开工作区后，
> **码道（CodeArts）插件**即识别该技能，按 14 步协助客户一步步创建新的本体应用（用法与推荐 prompt 见指南 §3.0）。
>
> 新应用建在工作区根下、与 `OntologyFrameworkExample` **并列**，并由技能第 7 步登记进工作区根 `pom.xml` 的
> `<modules>`——故在 IDEA 中打开工作区即可**同时看到示例与新应用**（重载 Maven 工程后生效）。
>
> **临时产物目录**：所有新建工程的码道临时文件、脚本、日志统一写入**工作目录下的 `HWCodeArtsDir/`**
> （即 `<工作目录>/HWCodeArtsDir/`，与各工程模块**并列**、**不在模块内**，★铁律 16）。
>
> 参照示例构建的**新应用**同样会产出**同款 `README.md`**（工程说明 + 打包/发布/运行）与**同样的 `pom.xml` 打包配置**，
> 因此具备与示例一致的**构建、打包与分发**能力（详见指南 §3.5）。

## 快速开始

- **客户一键安装（推荐给最终用户）**：把 `OntologyInstaller-0.0.1-SNAPSHOT-dist.zip` 拷给
  客户并解压，**双击 `start-installer.cmd`**（脚本自带 JDK，无需预装 Java）。

  在 Swing 界面选择「软件安装目录」与「开发工作目录」后点击开始，安装器将自动完成：
  拷贝并解压全部软件到安装目录 → 初始化便携式 MySQL（`mysqld --initialize-insecure`）→
  把**工作区聚合工程**释放到工作目录（根为父聚合 `pom.xml`，示例 `OntologyFrameworkExample` 为其子模块），
  并生成示例模块的 `application.yaml` / `start-env.cmd`（指向安装目录下各软件 home）。
  同时把随包的**离线 Maven 仓库**释放到 `<安装目录>/maven-repo`，并在工作区根写 `.mvn/maven.config`
  默认使用它，使目标机**无网络**时也能用 Maven 构建并运行测试。
  完成后用随包 IDEA 打开**工作目录**（聚合工程根），进入示例模块双击 `start-env.cmd` 即可一键拉起环境。

- **开发场景一键环境**：解压 `OntologyFrameworkExample-0.0.1-SNAPSHOT-project.zip`（得到工作区根 + 示例模块），进入示例模块 `OntologyFrameworkExample/`，双击 `start-env.cmd`，即自动完成：建库+灌数（createdb）→ 启动环境服务（start，新窗口常驻）→ 创建 RabbitMQ 资源（create-rabbitmq）。
- **用码道（CodeArts）+「本体应用构建」SKILL 创建新应用（推荐）**：用随包 IDEA 打开释放出的**工作区根**（示例工程的上一级目录），码道插件即启用；
  安装器已把技能投放到工作区根 `.codeartsdoer/skills/ontology-app-builder/`，码道按 **14 步流程 + 11 个人工闸门**
  协助你一步步构建新的本体应用（新应用与示例**并列**、登记进工作区根 `pom.xml` 后在工程视图中可见；用法与推荐 prompt 见《新应用开发与部署指南.md》§3.0）。
- **手工启动**：

  ```bash
  java -jar EnvPrepare-0.0.1-SNAPSHOT.jar start -c <新应用>/src/main/resources/application.yaml
  ```

详见《新应用开发与部署指南.md》。
