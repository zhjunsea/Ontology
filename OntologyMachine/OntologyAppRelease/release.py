#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
应用发布工程（OntologyAppRelease）— 应用打包工具
================================================================

输入：一个「应用模块名」（如 OntologyFrameworkLatticeTower）。
输出：两种形态，可单独或同时产出：

  A. **运行包**（默认）：一个「客户可直接安装运行」的 ZIP 运行包 + 安装文档。
  B. **开发工程**（`--project` / `--project-only`）：一个「工作区聚合工程」ZIP，
     解压后根为聚合 pom.xml，应用模块为其子模块（含完整源码 src/ontology/design 与
     start-env.cmd、EnvPrepare 可执行 jar），可用 IDEA 直接打开并部署到开发环境；
     形态对齐发布包（dist）内的 `OntologyFrameworkExample-<版本>-project.zip`。
     随包 `deploy-dev.cmd` + `tools/deploy-dev.ps1` + `开发环境部署说明.md`：部署到开发环境时
     自动判定——目标目录不存在则**新建**开发环境；目标是已有开发环境则**并入**本模块并登记到
     根 pom 的 `<modules>`，与 Example **共用同一开发环境**（同 OntologyMachine 形态）。

运行包打包内容：
  - 应用本体 fat jar（自包含，已内置其依赖 jar）
  - 应用文件系统资源：<module>/ontology/（本体/映射/流程）
  - 基础依赖库 jar：EnvPrepare 等（环境启停用）
  - 依赖软件（**按需裁剪**）：依据应用 application.yaml 判定所需软件，
    只随包携带真正要用的软件（JDK 恒带；Camunda/MySQL/Ontop/RabbitMQ+Erlang 按声明裁剪）
  - 安装器：install.cmd（GBK）+ tools/install.ps1（Windows 内置 PowerShell）
  - 安装文档：安装文档.md

客户侧：解压 → 双击 install.cmd → start-env.cmd（环境服务）→ start-app.cmd（应用）。
安装时由 install.ps1 探测各软件实际解压位置并注入**绝对路径**，故不写死构建机路径。

用法：
  python release.py --app OntologyFrameworkLatticeTower
  python release.py --app OntologyFrameworkExample --out D:\\tmp\\release
  python release.py --app OntologyFrameworkLatticeTower --dry-run
  python release.py --app OntologyFrameworkLatticeTower --project-only          # 仅发布开发工程
  python release.py --app OntologyFrameworkLatticeTower --project              # 运行包 + 开发工程
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import re
import shutil
import sys
import zipfile
from pathlib import Path

# ---------------------------------------------------------------------------
# 常量
# ---------------------------------------------------------------------------

#: 软件目录：alias -> 元数据。markers 为「相对 home 的特征文件」，供安装器探测 home。
SOFTWARE_CATALOG = [
    dict(alias="jdk", label="JDK", pattern="jdk-*_windows-x64_bin.zip",
         token="JAVA_HOME", markers=["bin/java.exe"],
         desc="Java 运行时（运行应用与 EnvPrepare 必需，恒随包）"),
    dict(alias="camunda", label="Camunda 8 (c8run)", pattern="camunda8-run-*.zip",
         token="CAMUNDA_HOME", markers=["c8run.exe"],
         desc="BPMN 流程引擎（Zeebe gRPC + REST/Operate/Tasklist）"),
    dict(alias="mysql", label="MySQL", pattern="mysql-*-winx64.zip",
         token="MYSQL_HOME", markers=["bin/mysqld.exe"],
         desc="关系数据库（便携模式，随包独立进程，不注册 Windows 服务）"),
    dict(alias="ontop", label="Ontop", pattern="ontop-cli-*.zip",
         token="ONTOP_HOME", markers=["ontop.bat"],
         desc="OBDA 虚拟知识图谱 SPARQL 端点"),
    dict(alias="rabbitmq", label="RabbitMQ", pattern="rabbitmq-server-windows-*.zip",
         token="RABBITMQ_HOME", markers=["sbin/rabbitmq-server.bat"],
         desc="消息队列"),
    dict(alias="erlang", label="Erlang/OTP", pattern="otp_win64_*.zip",
         token="ERLANG_HOME", markers=["bin/erl.exe"],
         desc="RabbitMQ 运行依赖（随 RabbitMQ 一并携带）"),
    # kind="exe"：随包为安装器（.exe），安装时静默安装到 runtime\python（TargetDir），
    # 供随附的 Python 客户端脚本运行（仅在随包 python-client 时携带）。
    dict(alias="python", label="Python", pattern="python-*-amd64.exe",
         token="PYTHON_HOME", markers=["python.exe"],
         desc="Python 运行时（运行随附 Python 客户端脚本必需）",
         kind="exe",
         silentArgs=["/quiet", "InstallAllUsers=0", "TargetDir={dest}",
                     "PrependPath=0", "Include_launcher=0", "Include_test=0",
                     "Include_pip=0", "AssociateFiles=0", "Shortcuts=0", "Include_doc=0"]),
]

#: 随附的 Python 客户端模块（仓库内与本工程同级时，可随包携带）。
PYTHON_CLIENT_MODULE = "OntologyFrameworkPythonClient"
#: 随包时从客户端模块排除的内容（本地环境/产物/IDE 文件）。
PYTHON_CLIENT_EXCLUDES = {".venv", "out", "__pycache__", ".idea", "venv"}

#: 「开发工程」发布排除的内容：构建/运行/IDE 产物；`.git` 避免夹带嵌套仓库（个别应用模块根内嵌 .git）；
#: `HWCodeArtsDir` 为码道/CodeArts 工作目录（本地临时库 _ltlib、探针脚本、过程日志，非应用源码）。
#: 与发布包内 Example 工程 zip 的口径一致（target/logs/.idea/out/*.iml），另加 `.git` 与 HWCodeArtsDir。
#: 可用 `--project-exclude NAME`（可重复）追加应用特有的运行产物目录。
DEV_PROJECT_EXCLUDES = {"target", "logs", ".idea", "out", ".git", "HWCodeArtsDir"}

#: 应用「外部可编辑资源」目录（相对模块根）：这些是随包分发的普通数据文件，**不进 jar**，
#: 运行/安装包中放到 `<应用目录>/<目录名>/`，开发工程中即模块根的 `<目录名>/`，客户可直接修改。
#: 目前为格构塔的钢塔数据（steel-tower-nodes.csv / steel-tower-elements.csv）。
APP_DATA_DIRS = ["latticetower"]

#: 随包基础依赖库：仅 EnvPrepare（环境启停工具，自包含 Spring Boot 可执行 jar）。
#: 其余业务模块（Utilities/OpenlletResolver/OntopOBDAHandler 等）已内嵌进应用 fat jar，
#: 运行期不再需要独立 jar，故不随包（只装客户真正用到的文件）。
BASE_LIBS = ["EnvPrepare"]

#: env-prepare 一级 home 键 -> 占位符
HOME_TOKENS = {
    "java-home": "@{JAVA_HOME}",
    "ontop-home": "@{ONTOP_HOME}",
    "camunda-home": "@{CAMUNDA_HOME}",
    "rabbitmq-home": "@{RABBITMQ_HOME}",
    "erlang-home": "@{ERLANG_HOME}",
}
#: mysql 子键 -> 占位符（port 不加引号）
MYSQL_TOKENS = {
    "home": "'@{MYSQL_HOME}'",
    "bin": "'@{MYSQL_BIN}'",
    "datadir": "'@{MYSQL_DATADIR}'",
    "port": "@{MYSQL_PORT}",
}

DEFAULT_MYSQL_PORT = 3306


# ---------------------------------------------------------------------------
# 极简 YAML 解析（与 EnvConfig 一致：忽略注释/空行，按缩进建树）
# ---------------------------------------------------------------------------

def parse_yaml_min(text: str) -> dict:
    root: dict = {}
    stack = [(-1, root)]
    for raw in text.splitlines():
        line = raw.rstrip()
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("- ") or ":" not in stripped:
            continue
        indent = len(line) - len(line.lstrip(" "))
        key, _, value = stripped.partition(":")
        key = key.strip()
        value = value.strip()
        while len(stack) > 1 and indent <= stack[-1][0]:
            stack.pop()
        parent = stack[-1][1]
        if value == "":
            child: dict = {}
            parent[key] = child
            stack.append((indent, child))
        else:
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            parent[key] = value
    return root


def _node(parent: dict, key: str) -> dict:
    v = parent.get(key) if parent else None
    return v if isinstance(v, dict) else {}


def _has_text(m: dict, key: str) -> bool:
    v = m.get(key)
    return isinstance(v, str) and v.strip() != ""


# ---------------------------------------------------------------------------
# 扫描应用模块
# ---------------------------------------------------------------------------

def find_fat_jar(module_dir: Path, module: str) -> Path:
    target = module_dir / "target"
    if not target.is_dir():
        raise SystemExit(f"[错误] 未找到构建输出目录（请先构建该模块）：{target}")
    cands = []
    for p in sorted(target.glob(f"{module}-*.jar")):
        n = p.name
        if n.endswith(("-sources.jar", "-javadoc.jar")) or n.startswith("original-"):
            continue
        cands.append(p)
    if not cands:
        raise SystemExit(f"[错误] 未找到应用 fat jar：{target}\\{module}-*.jar（请先 mvn package）")
    # fat jar 体积最大
    return max(cands, key=lambda p: p.stat().st_size)


def find_app_yaml(module_dir: Path) -> Path:
    for c in (module_dir / "src/main/resources/application.yaml", module_dir / "application.yaml"):
        if c.is_file():
            return c
    raise SystemExit(f"[错误] 未找到 application.yaml：{module_dir}")


def find_ontology_dir(module_dir: Path) -> Path | None:
    d = module_dir / "ontology"
    return d if d.is_dir() else None


def find_base_lib(repo: Path, name: str, version: str) -> Path:
    mod_jar = repo / name / "target" / f"{name}-{version}.jar"
    if mod_jar.is_file():
        return mod_jar
    build_jar = repo / "build" / f"{name}-{version}.jar"
    if build_jar.is_file():
        return build_jar
    raise SystemExit(f"[错误] 未找到基础库 jar：{name}-{version}.jar（已查找 {mod_jar} 与 {build_jar}）")


def read_module_version(module_dir: Path, module: str) -> str:
    """从应用模块 pom.xml 解析版本号。

    优先取与本模块 artifactId 相邻的 <version>（模块自身版本），否则回退首个 <version>。
    这样「开发工程」发布**无需先构建 fat jar** 也能得到版本号。
    """
    pom = module_dir / "pom.xml"
    if not pom.is_file():
        raise SystemExit(f"[错误] 未找到模块 pom.xml：{pom}")
    txt = pom.read_text(encoding="utf-8")
    m = re.search(
        rf"<artifactId>\s*{re.escape(module)}\s*</artifactId>\s*<version>\s*([^<]+?)\s*</version>",
        txt,
    )
    if m:
        return m.group(1).strip()
    m = re.search(r"<version>\s*([^<]+?)\s*</version>", txt)
    if m:
        return m.group(1).strip()
    raise SystemExit(f"[错误] 无法从 {pom} 解析版本号")


def find_jdk_zip(softwares_dir: Path) -> Path | None:
    """定位随包 JDK 压缩包（与运行包同一份），供开发工程随包携带、部署时释放为 <开发环境>/jdk。"""
    for meta in SOFTWARE_CATALOG:
        if meta["alias"] == "jdk":
            matches = sorted(softwares_dir.glob(meta["pattern"]))
            return matches[0] if matches else None
    return None


def find_env_prepare_jar(repo: Path, module_dir: Path, version: str) -> Path:
    """定位 EnvPrepare 可执行 jar（开发工程随包，供模块根的 start-env.cmd 调用）。"""
    cands = [
        repo / "EnvPrepare" / "target" / f"EnvPrepare-{version}.jar",
        repo / "build" / f"EnvPrepare-{version}.jar",
    ]
    cands += sorted(module_dir.glob("EnvPrepare-*.jar"))
    cands += sorted((repo / "EnvPrepare").glob("EnvPrepare-*.jar"))
    for c in cands:
        if c.is_file():
            return c
    raise SystemExit(
        f"[错误] 未找到 EnvPrepare-{version}.jar（先构建 EnvPrepare 模块，或将其复制到模块根）"
    )


# ---------------------------------------------------------------------------
# 依赖软件裁剪（按应用 application.yaml）
# ---------------------------------------------------------------------------

def detect_scope(app: dict, need_python: bool = False) -> dict:
    """返回 alias -> bool 的软件需求表（按需裁剪的核心判定）。

    need_python：是否随包携带 Python 客户端（携带则同时携带 Python 运行时）。
    """
    env = _node(app, "env-prepare")
    ontology = _node(app, "ontology")
    mysql_node = env.get("mysql")
    rabbit_node = env.get("rabbitmq")

    need_mysql = isinstance(mysql_node, dict)
    need_rabbit = isinstance(rabbit_node, dict)
    need_camunda = ("camunda" in app) or _has_text(env, "camunda-home") or _has_text(ontology, "bpmn-path")
    need_ontop = _has_text(env, "ontop-home") and _has_text(ontology, "obda-path")
    need_erlang = need_rabbit or _has_text(env, "erlang-home")

    return {
        "jdk": True,                 # 运行必备，恒带
        "camunda": need_camunda,
        "mysql": need_mysql,
        "ontop": need_ontop,
        "rabbitmq": need_rabbit,
        "erlang": need_erlang,
        "python": need_python,       # 随包 Python 客户端时携带
    }


def resolve_softwares(scope: dict, softwares_dir: Path) -> list[dict]:
    picked = []
    for meta in SOFTWARE_CATALOG:
        if not scope.get(meta["alias"]):
            continue
        matches = sorted(softwares_dir.glob(meta["pattern"]))
        if not matches:
            raise SystemExit(f"[错误] 软件目录缺少所需软件包：{meta['pattern']}（{softwares_dir}）")
        z = matches[0]
        item = dict(meta)
        item["zip"] = z.name
        item["zipPath"] = z
        item["zipMB"] = round(z.stat().st_size / 1024 / 1024, 1)
        picked.append(item)
    return picked


# ---------------------------------------------------------------------------
# 随附载荷（Python 客户端等）
# ---------------------------------------------------------------------------

def _copy_tree_filtered(src: Path, dst: Path, excludes: set) -> None:
    """递归复制目录，跳过排除名（如 .venv / out / __pycache__）与 *.iml。"""
    dst.mkdir(parents=True, exist_ok=True)
    for item in sorted(src.iterdir()):
        if item.name in excludes or item.name.endswith(".iml"):
            continue
        if item.is_dir():
            _copy_tree_filtered(item, dst / item.name, excludes)
        else:
            shutil.copy2(item, dst / item.name)


def app_slug(module: str) -> str:
    """应用模块名 -> slug：``OntologyFrameworkLatticeTower`` -> ``latticetower``。"""
    s = module[len("OntologyFramework"):] if module.startswith("OntologyFramework") else module
    return s.lower()


def _client_keep(name: str, slug: str) -> bool:
    """Python 客户端里「本应用需要」的文件：通用零依赖客户端 + 名字含本应用 slug 的脚本。

    这样只随包本应用自己的调用/验证脚本，排除其它应用脚本（如 verify_example.py）。
    """
    n = name.lower()
    if n == "ontology_client.py":
        return True
    return n.endswith(".py") and slug in n


def _client_readme(app_name: str, slug: str, kept: list) -> str:
    """生成客户向 README（替代开发者 README，避免夹带 .venv/开发仓库路径等无关内容）。"""
    scripts = [n for n in kept if n != "ontology_client.py"]
    lines = ["- `ontology_client.py`：零依赖通用客户端（仅用 Python 标准库，可拷到任意工程）"]
    for n in scripts:
        lines.append(f"- `{n}`：本应用的调用/验证脚本")
    listing = "\n".join(lines)
    run = next((n for n in scripts if n.startswith("verify_")), scripts[0] if scripts else "")
    run_line = (f"    <Python 解释器> python-client\\{run}\n"
                if run else "    <Python 解释器> python-client\\<脚本>\n")
    return f"""# {app_name} · Python 客户端

本目录随包携带，用于以 Python 程序调用 **{app_name}**：
**Python 给出输入 → 应用执行 → 结果回传（含结果文件下载）→ Python 继续执行**。

## 文件
{listing}

## 前置
1. 已执行 `install.cmd` 完成安装；
2. 已运行 `start-env.cmd` 启动环境服务（Camunda 等），并已运行 `start-app.cmd` 启动应用 Worker。

## 运行（自动选用可用的 Python）
双击本包根目录的 **`start-client.cmd`**。或手动运行：

{run_line}
自定义应用地址：在命令末尾追加地址参数，如 `... http://localhost:<端口>`。
"""


def _copy_client_filtered(src: Path, dst: Path, slug: str, app_name: str) -> None:
    """只随包本应用需要的 Python 客户端文件，并生成客户向 README。"""
    dst.mkdir(parents=True, exist_ok=True)
    kept = []
    for item in sorted(src.iterdir()):
        if item.is_file() and _client_keep(item.name, slug):
            shutil.copy2(item, dst / item.name)
            kept.append(item.name)
    (dst / "README.md").write_text(_client_readme(app_name, slug, kept), encoding="utf-8")


def collect_extras(args, repo: Path) -> list:
    """收集随附载荷目录（name -> 源路径）。

    来源：`--extra name=path`（可重复） + `--with-python-client`（自动定位仓库同级客户端模块）。
    返回 [dict(name, src)]。
    """
    extras = {}
    for spec in (args.extra or []):
        if "=" not in spec:
            raise SystemExit(f"[错误] --extra 参数格式应为 name=path：{spec}")
        name, _, p = spec.partition("=")
        name = name.strip()
        src = Path(p).resolve()
        if not name or not src.is_dir():
            raise SystemExit(f"[错误] --extra 目录无效：{spec}")
        extras[name] = src
    if getattr(args, "with_python_client", False) and "python-client" not in extras:
        src = (repo / PYTHON_CLIENT_MODULE)
        if not src.is_dir():
            raise SystemExit(f"[错误] 未找到 Python 客户端模块：{src}")
        extras["python-client"] = src
    return [dict(name=n, src=s) for n, s in extras.items()]


# ---------------------------------------------------------------------------
# application.yaml -> 模板（home 值 token 化；缺失键自动补齐）
# ---------------------------------------------------------------------------

def _indent_of(s: str) -> int:
    return len(s) - len(s.lstrip(" "))


def _is_content(s: str) -> bool:
    t = s.strip()
    return bool(t) and not t.startswith("#")


def _block_end(lines: list[str], start: int, base_indent: int) -> int:
    """返回 [start+1, end) 内属于 base_indent 块的范围末端。"""
    for j in range(start + 1, len(lines)):
        if not _is_content(lines[j]):
            continue
        if _indent_of(lines[j]) <= base_indent:
            return j
    return len(lines)


def _set_or_insert(lines: list[str], lo: int, hi: int, key: str, value: str, child_indent: int) -> bool:
    """在 [lo,hi) 内把 `key:` 行改为 `key: value`（保留缩进）；不存在则插入在 lo 处。返回是否已存在。"""
    pat = re.compile(rf"^\s*{re.escape(key)}\s*:")
    for i in range(lo, hi):
        if not _is_content(lines[i]):
            continue
        if pat.match(lines[i]):
            lines[i] = " " * _indent_of(lines[i]) + f"{key}: {value}"
            return True
    lines.insert(lo, " " * child_indent + f"{key}: {value}")
    return False


def tokenize_app_yaml(text: str, scope: dict) -> str:
    lines = text.split("\n")
    # 定位 env-prepare 块
    ep = None
    for i, l in enumerate(lines):
        if _is_content(l) and l.strip().startswith("env-prepare:"):
            ep = i
            break
    if ep is None:
        if lines and lines[-1].strip():
            lines.append("")
        lines.append("env-prepare:")
        ep = len(lines) - 1
    ep_indent = _indent_of(lines[ep])
    ep_end = _block_end(lines, ep, ep_indent)
    ep_child = ep_indent + 2

    # 一级 home 键：token 化 / 补齐
    insert_at = ep + 1
    for key, tok in HOME_TOKENS.items():
        if _set_or_insert(lines, ep + 1, ep_end, key, f"'{tok}'", ep_child):
            ep_end = _block_end(lines, ep, ep_indent)   # 行数可能变化，重算
        else:
            ep_end = _block_end(lines, ep, ep_indent)
            insert_at += 1

    # mysql 子键：仅当声明了 mysql 且随包携带 mysql 时处理
    if scope.get("mysql"):
        my = None
        for i in range(ep + 1, ep_end):
            if _is_content(lines[i]) and lines[i].strip().startswith("mysql:"):
                my = i
                break
        if my is not None:
            my_indent = _indent_of(lines[my])
            my_end = _block_end(lines, my, my_indent)
            # 便携模式：强制 mode: portable（随包为便携 MySQL）
            _set_or_insert(lines, my + 1, my_end, "mode", "portable", my_indent + 2)
            my_end = _block_end(lines, my, my_indent)
            for key, tok in MYSQL_TOKENS.items():
                _set_or_insert(lines, my + 1, my_end, key, tok, my_indent + 2)
                my_end = _block_end(lines, my, my_indent)
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# 端口 / 文档辅助
# ---------------------------------------------------------------------------

def collect_ports(app: dict, scope: dict) -> list[str]:
    env = _node(app, "env-prepare")
    ports: list[str] = []

    def add(v, label):
        if v is None:
            return
        s = str(v)
        ports.append(f"{s}（{label}）")

    srv = _node(app, "server")
    add(srv.get("port"), "应用 Web")
    if scope.get("camunda"):
        add(env.get("camunda-port", 9080), "Camunda REST")
        add(env.get("camunda-grpc-port", 26500), "Camunda gRPC")
    if scope.get("mysql"):
        my = _node(env, "mysql")
        add(my.get("port", DEFAULT_MYSQL_PORT), "MySQL")
    if scope.get("ontop"):
        add(env.get("ontop-port", 8080), "Ontop")
    if scope.get("rabbitmq"):
        rb = _node(env, "rabbitmq")
        add(rb.get("port", 5672), "RabbitMQ AMQP")
        add(rb.get("management-port", 15672), "RabbitMQ 管理台")
    return ports


def software_table(picked: list[dict]) -> str:
    rows = ["| 软件 | 用途 | 压缩包 |", "|---|---|---|"]
    for s in picked:
        rows.append(f"| {s['label']} | {s['desc']} | `{s['zip']}` |")
    return "\n".join(rows)


def service_names(picked: list[dict]) -> str:
    names = [s["label"] for s in picked if s["alias"] != "jdk"]
    return " / ".join(names) if names else "无（仅需 JDK）"


def _python_client_section(module: str, entry) -> str:
    """生成安装文档中的「Python 客户端」章节（自包含，不含嵌套占位符）。"""
    run = entry if entry else "（选择 python-client\\ 下的脚本）"
    return f"""## 8. 运行 Python 客户端（可选）

本包随附 **Python 客户端**（`python-client\\`），用于以 Python 程序调用本应用：
**Python 给出输入 → 应用执行 → 结果回传（含结果文件下载）→ Python 继续执行**。

- 客户端**零依赖**（仅用 Python 标准库）；安装时**优先复用本机已有 Python（3.9+）**，仅当本机缺失或版本过低时才静默安装随包 Python 到 `runtime\\python`。
- 随包文件（`python-client\\`）：`ontology_client.py`（零依赖客户端）、本应用的调用/验证脚本、`README.md`。
- 使用前请先运行 `start-app.cmd` 启动应用 Worker（并确认 `start-env.cmd` 环境服务已就绪）。

双击 **`start-client.cmd`** 即可运行（脚本会自动选用可用的 Python 与 `python-client\\` 目录）。也可手动运行：

    <Python 解释器> python-client\\{run}

> 自定义应用地址：可在命令末尾追加地址参数，如
> `<Python 解释器> python-client\\{run} http://localhost:9086`
"""


# ---------------------------------------------------------------------------
# 写文件（编码敏感）
# ---------------------------------------------------------------------------

def write_text(path: Path, text: str, encoding: str = "utf-8", crlf: bool = False) -> None:
    if crlf:
        text = text.replace("\r\n", "\n").replace("\n", "\r\n")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(text.encode(encoding))


def fill_tokens(text: str, tokens: dict) -> str:
    for k, v in tokens.items():
        text = text.replace(k, v)
    leftover = re.findall(r"@\{[A-Z_]+\}", text)
    if leftover:
        raise SystemExit(f"[错误] 生成文件时仍有未替换占位符：{sorted(set(leftover))}")
    return text


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def stage_release(args) -> Path:
    script_dir = Path(__file__).resolve().parent
    repo = Path(args.repo).resolve() if args.repo else script_dir.parent
    module = args.app
    module_dir = repo / module
    if not module_dir.is_dir():
        raise SystemExit(f"[错误] 未找到应用模块目录：{module_dir}")

    softwares_dir = Path(args.softwares).resolve() if args.softwares else repo / "softwares"
    templates_dir = script_dir / "templates"
    out_dir = Path(args.out).resolve() if args.out else script_dir / "output"
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"[1/8] 扫描应用模块：{module}")
    fat_jar = find_fat_jar(module_dir, module)
    version = fat_jar.name[len(module) + 1: -len(".jar")]
    app_yaml_p = find_app_yaml(module_dir)
    ontology_dir = find_ontology_dir(module_dir)
    print(f"      fat jar : {fat_jar.name}  ({fat_jar.stat().st_size/1024/1024:.1f} MB)")
    print(f"      config  : {app_yaml_p}")
    print(f"      ontology: {ontology_dir if ontology_dir else '(无)'}")

    app = parse_yaml_min(app_yaml_p.read_text(encoding="utf-8"))

    print("[2/8] 收集随附载荷（Python 客户端等）")
    extras = collect_extras(args, repo)
    need_python = any(e["name"] == "python-client" for e in extras)
    for e in extras:
        print(f"      + {e['name']}  <-  {e['src']}")

    scope = detect_scope(app, need_python=need_python)
    picked = resolve_softwares(scope, softwares_dir)
    print("[3/8] 依赖软件裁剪（按需）：" + ", ".join(f"{s['label']}={s['zipMB']}MB" for s in picked))

    base_libs = [find_base_lib(repo, name, version) for name in BASE_LIBS]
    print(f"[4/8] 基础依赖库：{', '.join(p.name for p in base_libs)}")

    rel_name = f"{module}-{version}-runtime"
    stage_root = out_dir / rel_name
    if stage_root.exists():
        shutil.rmtree(stage_root)
    payload = stage_root / "payload"
    (payload / "app").mkdir(parents=True)
    (payload / "lib").mkdir(parents=True)
    (payload / "templates").mkdir(parents=True)
    (stage_root / "tools").mkdir(parents=True)
    (stage_root / "softwares").mkdir(parents=True)

    print(f"[5/8] 组装载荷 -> {stage_root}")
    shutil.copy2(fat_jar, payload / "app" / fat_jar.name)
    if ontology_dir:
        shutil.copytree(ontology_dir, payload / "app" / "ontology")
    # 外部可编辑资源目录（不进 jar，随包放 app/ 下，客户可直接改）
    for data_dirname in APP_DATA_DIRS:
        src_data = module_dir / data_dirname
        if src_data.is_dir():
            shutil.copytree(src_data, payload / "app" / data_dirname)
            print(f"      [外部资源] {data_dirname}/ -> payload/app/{data_dirname}/（不进 jar，可编辑）")
    for lib in base_libs:
        shutil.copy2(lib, payload / "lib" / lib.name)

    yaml_tpl = tokenize_app_yaml(app_yaml_p.read_text(encoding="utf-8"), scope)
    write_text(payload / "templates" / "application.yaml.tmpl", yaml_tpl, "utf-8")
    payload_tmpls = ["start-env.cmd.tmpl", "start-app.cmd.tmpl"]
    if need_python:
        payload_tmpls.append("start-client.cmd.tmpl")
    for t in payload_tmpls:
        shutil.copy2(templates_dir / t, payload / "templates" / t)

    # 随附载荷：放 payload\<name>，安装时释放到 <root>\<name>。
    # python-client 只随包本应用需要的文件（排除他应用脚本/开发文档），并生成客户向 README。
    slug = app_slug(module)
    for e in extras:
        if e["name"] == "python-client":
            _copy_client_filtered(e["src"], payload / e["name"], slug, module)
        else:
            _copy_tree_filtered(e["src"], payload / e["name"], PYTHON_CLIENT_EXCLUDES)
    payload_dirs = ["app", "lib"] + [e["name"] for e in extras]

    # Python 客户端入口脚本（供 start-client.cmd 使用；按应用模块名推导）
    if need_python:
        cands = [f"verify_{slug}.py", f"{slug}_client.py"]
        client_entry = next((c for c in cands if (payload / "python-client" / c).is_file()), None)
        client_info = dict(enabled=True, dir="python-client", entry=client_entry)
    else:
        client_info = dict(enabled=False, dir=None, entry=None)

    for s in picked:
        shutil.copy2(s["zipPath"], stage_root / "softwares" / s["zip"])
    # install.ps1 必须为「UTF-8 + BOM」：Windows PowerShell 5.1（install.cmd 用的 powershell.exe）
    # 对「无 BOM」文件会按系统 ANSI 代码页（中文机=GBK/936）读取，导致中文注释被误解码、报
    # “意外的标记 }” 解析错误而无法安装。此处强制补 BOM，避免模板丢失 BOM 造成回归。
    _ps1 = (templates_dir / "install.ps1").read_bytes()
    if not _ps1.startswith(b"\xef\xbb\xbf"):
        _ps1 = b"\xef\xbb\xbf" + _ps1
    (stage_root / "tools" / "install.ps1").write_bytes(_ps1)

    # manifest
    manifest = dict(
        app=module,
        version=version,
        appJar=fat_jar.name,
        generatedAt=_dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        baseLibs=[p.name for p in base_libs],
        payloadDirs=payload_dirs,
        softwares=[dict(alias=s["alias"], label=s["label"], zip=s["zip"],
                        token=s["token"], markers=s["markers"],
                        kind=s.get("kind", "zip"), silentArgs=s.get("silentArgs"))
                   for s in picked],
        pythonClient=client_info,
        mysql=(dict(included=True, port=(_node(_node(app, "env-prepare"), "mysql")).get("port", DEFAULT_MYSQL_PORT))
               if scope.get("mysql") else dict(included=False, port=DEFAULT_MYSQL_PORT)),
    )
    write_text(stage_root / "manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2), "utf-8")

    # install.cmd（GBK + CRLF，打包期填充 token）
    install_tokens = {
        "@{APP_NAME}": module,
        "@{APP_VERSION}": version,
    }
    install_cmd = fill_tokens((templates_dir / "install.cmd").read_text(encoding="utf-8"), install_tokens)
    write_text(stage_root / "install.cmd", install_cmd, "gbk", crlf=True)

    # 安装文档
    soft_mb = sum(s["zipMB"] for s in picked)
    if need_python:
        python_row = "| `python-client\\` | Python 客户端脚本（零依赖；含本应用调用/验证脚本） |"
        python_need = "| Python | 无需预装（随包携带 Python 运行时，安装脚本静默安装到 `runtime\\python`） |"
        python_section = _python_client_section(module, client_entry)
    else:
        python_row = ""
        python_need = ""
        python_section = ""
    doc_tokens = {
        "@{APP_NAME}": module,
        "@{APP_VERSION}": version,
        "@{APP_JAR}": fat_jar.name,
        "@{GENERATED_AT}": manifest["generatedAt"],
        "@{SOFTWARE_COUNT}": str(len(picked)),
        "@{SOFTWARE_TABLE}": software_table(picked),
        "@{SERVICE_LIST}": service_names(picked),
        "@{PORT_LIST}": "；".join(collect_ports(app, scope)) or "无",
        "@{DISK_SOFT}": f"{soft_mb/1024:.1f} GB",
        "@{DISK_HINT}": f"{soft_mb/1024 + 1.0:.0f} GB",
        "@{PYTHON_CLIENT_ROW}": python_row,
        "@{PYTHON_NEED_ROW}": python_need,
        "@{PYTHON_CLIENT_SECTION}": python_section,
    }
    doc = fill_tokens((templates_dir / "安装文档.md.tmpl").read_text(encoding="utf-8"), doc_tokens)
    write_text(stage_root / "安装文档.md", doc, "utf-8")

    print("[6/8] 生成 install.cmd / manifest.json / 安装文档.md" + (" / start-client.cmd" if need_python else ""))

    if args.dry_run:
        print("[dry-run] 跳过压缩。")
        return stage_root

    zip_path = out_dir / f"{rel_name}.zip"
    if zip_path.exists():
        zip_path.unlink()
    print(f"[7/8] 压缩 -> {zip_path.name}")
    _zip_dir(stage_root, zip_path)
    print(f"[8/8] 完成：{zip_path}  ({zip_path.stat().st_size/1024/1024:.1f} MB)")
    return zip_path


def stage_dev_project(args) -> Path:
    """发布应用的「开发工程」zip（形态对齐发布包内的 Example 工程包）。

    产物结构（解压后）::

        pom.xml                       # 工作区聚合 pom（<modules> 含本模块）→ IDEA 打开此根
        <模块>/
          ├── pom.xml                 # 应用模块自身 pom
          ├── src/ ontology/ design/  # 完整源码与设计资料
          ├── start-env.cmd           # 模块自带的环境启动脚本（双击即建库灌数、拉起环境服务）
          └── EnvPrepare-<版本>.jar   # 打包期注入的可执行 jar（供 start-env.cmd）
        deploy-dev.cmd                # 开发环境部署入口（自动判定新建 / 并入已有开发环境）
        tools/deploy-dev.ps1          # 部署引擎（部署时释放随包 JDK 到 <开发环境>/jdk）
        softwares/jdk-*.zip           # 随包 JDK（与运行包同一份；保证与运行包一致的 JDK 27）
        开发环境部署说明.md            # 两种部署情形的说明

    与运行包不同，本产物是**源码工程**：解压即得一个工作区聚合工程根（等同 OntologyMachine 形态）。
    运行 `deploy-dev.cmd <目标目录>` 即可部署：目标不存在 → 新建开发环境；目标是已有开发环境
    （如 dist 自带的、含 Example 的工作区）→ 并入本模块并登记到 <modules>，与 Example 共用同一开发环境。
    """
    script_dir = Path(__file__).resolve().parent
    repo = Path(args.repo).resolve() if args.repo else script_dir.parent
    module = args.app
    module_dir = repo / module
    if not module_dir.is_dir():
        raise SystemExit(f"[错误] 未找到应用模块目录：{module_dir}")

    templates_dir = script_dir / "templates"
    out_dir = Path(args.out).resolve() if args.out else script_dir / "output"
    out_dir.mkdir(parents=True, exist_ok=True)

    version = getattr(args, "app_version", None) or read_module_version(module_dir, module)
    env_jar = find_env_prepare_jar(repo, module_dir, version)
    excludes = set(DEV_PROJECT_EXCLUDES) | set(getattr(args, "project_exclude", []) or [])

    print(f"[开发工程] 模块：{module}  版本：{version}")
    print(f"[开发工程] EnvPrepare：{env_jar.name}")
    print(f"[开发工程] 排除：{', '.join(sorted(excludes))}")

    rel_name = f"{module}-{version}-project"
    stage_root = out_dir / rel_name
    if stage_root.exists():
        shutil.rmtree(stage_root)
    module_out = stage_root / module
    module_out.mkdir(parents=True, exist_ok=True)

    # 1) 工作区根聚合 pom（<modules> 含本模块）——解压后即可作为 IDEA 工程根
    ws_tpl = (templates_dir / "workspace-pom.xml.tmpl").read_text(encoding="utf-8")
    ws = fill_tokens(ws_tpl, {"@{MODULE}": module, "@{VERSION}": version})
    write_text(stage_root / "pom.xml", ws, "utf-8")

    # 2) 应用模块完整源码（排除构建/运行/IDE 产物与嵌套 .git）
    _copy_tree_filtered(module_dir, module_out, excludes)

    # 3) 注入 EnvPrepare 可执行 jar 到模块根（供 start-env.cmd）
    shutil.copy2(env_jar, module_out / env_jar.name)

    # 3.5) 随包 JDK（与运行包同一份）：部署时释放为 <开发环境>/jdk，模块 start-env.cmd 优先使用，
    #      与运行包/安装器「随包 JDK 27」保持一致，避免落到系统 PATH 的低版本 JDK。
    softwares_dir = Path(args.softwares).resolve() if getattr(args, "softwares", None) else repo / "softwares"
    jdk_zip = find_jdk_zip(softwares_dir)
    if jdk_zip:
        (stage_root / "softwares").mkdir(parents=True, exist_ok=True)
        shutil.copy2(jdk_zip, stage_root / "softwares" / jdk_zip.name)
        print(f"[开发工程] 随包 JDK：{jdk_zip.name}  ({jdk_zip.stat().st_size / 1024 / 1024:.1f} MB)")
    else:
        print(f"[开发工程][警告] 未找到 JDK 压缩包（{softwares_dir}），开发工程将不含随包 JDK；"
              f"如需随包请用 --softwares 指定软件目录。")

    # 4) 开发环境部署入口 deploy-dev.cmd（GBK + CRLF，打包期填充 token）
    dev_tokens = {"@{APP_NAME}": module, "@{APP_VERSION}": version}
    deploy_cmd = fill_tokens((templates_dir / "deploy-dev.cmd").read_text(encoding="utf-8"), dev_tokens)
    write_text(stage_root / "deploy-dev.cmd", deploy_cmd, "gbk", crlf=True)

    # 5) 部署引擎 tools/deploy-dev.ps1（UTF-8 + BOM，同 install.ps1 编码约束）
    deploy_ps1 = fill_tokens(
        (templates_dir / "deploy-dev.ps1").read_text(encoding="utf-8"), {"@{MODULE}": module}
    )
    _ps1 = deploy_ps1.encode("utf-8")
    if not _ps1.startswith(b"\xef\xbb\xbf"):
        _ps1 = b"\xef\xbb\xbf" + _ps1
    (stage_root / "tools").mkdir(parents=True, exist_ok=True)
    (stage_root / "tools" / "deploy-dev.ps1").write_bytes(_ps1)

    # 6) 开发环境部署说明.md（UTF-8）
    dev_doc = fill_tokens(
        (templates_dir / "开发环境部署说明.md.tmpl").read_text(encoding="utf-8"), dev_tokens
    )
    write_text(stage_root / "开发环境部署说明.md", dev_doc, "utf-8")

    if args.dry_run:
        print(f"[dry-run] 开发工程已组装（未压缩）：{stage_root}")
        return stage_root

    zip_path = out_dir / f"{rel_name}.zip"
    if zip_path.exists():
        zip_path.unlink()
    print(f"[开发工程] 压缩 -> {zip_path.name}")
    _zip_dir(stage_root, zip_path, strip_top=True)
    print(f"[开发工程] 完成：{zip_path}  ({zip_path.stat().st_size/1024/1024:.1f} MB)")
    print("[开发工程] 部署：解压后运行 deploy-dev.cmd <目标开发环境目录>"
          "（目标不存在→新建；目标是已有开发环境→并入并与 Example 共用）。")
    return zip_path


def _zip_dir(src: Path, zip_path: Path, strip_top: bool = False) -> None:
    """把目录压成 zip。

    strip_top=False（默认）：保留顶层目录名（解压出一层 `<rel_name>/`，适用于运行包）；
    strip_top=True：只压目录**内容**（zip 根即 `pom.xml` 等，适用于「开发工程」——
    与发布包内 Example 工程 zip 的形态一致，解压即得工作区根）。
    内容多为已压缩文件（jar/zip），用 STORED 避免重复压缩，速度优先。
    """
    base = src if strip_top else src.parent
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as zf:
        for cur, _dirs, files in os.walk(src):
            for f in files:
                full = Path(cur) / f
                rel = full.relative_to(base)
                zf.write(full, rel.as_posix())


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="本体应用打包工具（输入=应用模块名）：运行包 + 开发工程")
    ap.add_argument("--app", required=True, help="要打包的应用模块名，如 OntologyFrameworkLatticeTower")
    ap.add_argument("--repo", help="仓库根目录（默认=本工程上一级）")
    ap.add_argument("--softwares", help="软件压缩包目录（默认=<repo>/softwares）")
    ap.add_argument("--out", help="产物输出目录（默认=<本工程>/output）")
    ap.add_argument("--extra", action="append", metavar="NAME=PATH",
                    help="随附载荷目录（可重复），如 --extra python-client=D:\\...\\OntologyFrameworkPythonClient")
    ap.add_argument("--with-python-client", action="store_true",
                    help=f"随包携带 Python 客户端（自动定位 <repo>/{PYTHON_CLIENT_MODULE}）+ Python 运行时")
    ap.add_argument("--project", action="store_true",
                    help="同时发布应用「开发工程」zip（工作区聚合 pom + 完整源码，可用 IDEA 打开/部署到开发环境）")
    ap.add_argument("--project-only", action="store_true",
                    help="仅发布「开发工程」zip（不产出运行包；可无需构建 fat jar）")
    ap.add_argument("--project-exclude", action="append", metavar="NAME",
                    help="开发工程追加排除的目录名（可重复），如 --project-exclude tmsd-output")
    ap.add_argument("--app-version", help="覆盖版本号（默认从模块 pom.xml 解析）")
    ap.add_argument("--dry-run", action="store_true", help="只组装不压缩")
    args = ap.parse_args(argv)

    if args.project_only:
        result = stage_dev_project(args)
        print(f"\n产物：{result}")
        return 0

    result = stage_release(args)
    print(f"\n产物：{result}")
    if args.project:
        proj = stage_dev_project(args)
        print(f"产物：{proj}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
