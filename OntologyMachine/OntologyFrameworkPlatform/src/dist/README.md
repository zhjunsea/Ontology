# 本体开发环境安装包

## 功能介绍

本安装包用于在目标机器上一键部署本体应用开发与运行环境。双击启动脚本后，安装器会自动完成：

1. 拷贝并解压全部配套软件（JDK、MySQL、Ontop、Camunda8、RabbitMQ、IDEA 等）到指定的软件安装目录
2. 初始化便携式 MySQL（`mysqld --initialize-insecure`，无需安装 Windows 服务）
3. 将示例工程释放到指定的开发工作目录，并自动生成配置文件指向安装目录下各软件
4. 配置 IDEA（自动安装 CodeArts Agent 插件、设置 JDK、便携化配置）
5. 释放离线 Maven 仓库，使目标机器无网络也能构建和运行工程

安装完成后，用随包 IDEA 打开工作目录即可直接开发与运行本体应用。

## 系统需求

| 项目 | 要求 |
|---|---|
| 操作系统 | Windows 10/11 64 位 |
| 磁盘空间 | ≥ 10 GB（软件安装目录约 5 GB + 离线 Maven 仓库约 500 MB + 工程与缓存） |
| 内存 | ≥ 8 GB（同时运行 IDEA + Camunda + MySQL + Ontop + RabbitMQ + 应用 Worker） |
| 路径 | 安装目录与工作目录须使用**纯英文路径**（不能含中文字符，否则 MySQL 无法启动） |
| 网络 | 无需联网（离线 Maven 仓库已随包携带） |
| Java | 无需预装（随包携带 JDK，启动脚本自动解压使用） |
| MySQL | 无需预装（便携模式，随包独立进程运行，不注册 Windows 服务） |
| 端口 | 以下端口须未被占用：3306（MySQL）、26500 与 9080（Camunda）、8080（Ontop）、5672 与 15672（RabbitMQ）、9083（应用 Worker） |

> 安装前请确认上述端口未被其他程序占用。如端口冲突，可在安装后修改 `application.yaml` 中的端口配置。

## 发布包文件说明

| 文件/目录 | 作用 |
|---|---|
| `start-installer.cmd` | 启动脚本，双击即可运行。自带 JDK，无需预装 Java，在 cmd 和 PowerShell 中均可使用，支持含空格的路径 |
| `OntologyInstaller.jar` | 安装器主程序（Swing 图形界面） |
| `softwares/` | 全部待装软件压缩包（JDK、MySQL、Ontop、Camunda8、RabbitMQ、IDEA、CodeArts 插件等） |
| `lib/` | 四个基础库 jar（OpenlletResolver、OntopOBDAHandler、Utilities、EnvPrepare），新应用开发与运行的基础依赖 |
| `OntologyFrameworkExample-*-project.zip` | 示例工程包，作为新本体应用开发的模板 |
| `maven-repo.zip` | 离线 Maven 仓库快照，释放后工程无网络也能构建和测试 |
| `skills/ontology-app-builder/` | 本体应用构建技能，安装时自动投放到工作区 `.codeartsdoer/skills/` 目录 |
| `新应用开发与部署指南.md` | 详细的使用与开发指南 |

## 如何安装

1. 将本发布包解压到任意目录（路径请使用纯英文，避免中文字符）
2. 双击 `start-installer.cmd` 启动安装器
3. 在界面中选择「软件安装目录」和「开发工作目录」
4. 点击开始，等待安装自动完成
5. 安装完成后，用随包 IDEA 打开工作目录即可开始开发

## 验证安装

安装完成后，按以下步骤验证安装是否成功：

1. 打开工作目录下的 `OntologyFrameworkExample` 模块
2. 双击 `start-env.cmd`，应依次显示 `[OK]` 建库灌数、环境服务启动、RabbitMQ 资源创建
3. 在 IDEA 中运行 `ExampleApplication`（Working directory 设为 `$MODULE_WORKING_DIR$`），控制台应显示 Worker 已启动
4. 浏览器访问 `http://localhost:9083`，页面应显示「本体应用框架示例 - 披萨烘焙流程」且引擎状态为 ✅
5. 点击任意披萨按钮，页面应显示流程启动并最终返回 COMPLETED 与披萨摘要

如以上步骤均成功，说明安装环境已就绪。详细测试方法见《新应用开发与部署指南.md》§1.5。

## 已知限制与注意事项

- **路径不能含中文字符**：MySQL（mysqld）内部使用 ANSI API 读取路径，中文字符会被截断导致启动失败。安装目录和工作目录均须使用纯英文路径。
- **端口独占**：安装包中的各软件以独立进程方式运行，不注册 Windows 服务。运行期间须独占上述端口；如需同时运行其他 MySQL / RabbitMQ 等实例，请先修改端口配置避免冲突。
- **停止环境**：`start-env.cmd` 启动的环境服务在独立窗口常驻运行，**关闭该窗口（或 Ctrl+C）即停止全部服务**。应用本体 Worker 须另行启动。
- **IDEA 便携化**：安装器已将 IDEA 配置为便携模式（配置落在 IDE_HOME 侧而非系统目录），并自动注册随包 JDK、安装 CodeArts Agent 插件、生成运行配置。打开 IDEA 即可直接运行工程，无需手工设置。
- **离线运行**：工程已配置离线 Maven 仓库（`.mvn/maven.config`），构建和测试无需联网。但首次运行环境服务（`start-env.cmd`）仍需本机已正确安装 Microsoft Visual C++ 运行时库（Windows 10/11 通常已内置）。
