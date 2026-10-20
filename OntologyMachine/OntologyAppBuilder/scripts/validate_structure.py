#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ontology-app-builder 结构校验器
===============================

对一个「新建的本体应用模块」做**工件齐备性**与**契约一致性**机检，作为 14 步流程
各阶段的自动化验收辅助（不替代人工闸门）。

校验项：
  1. 工件齐备性  —— 设计文档 / ontology / src / 测试 / 构建脚本 是否按 Example 同构齐备；
     IDEA 运行配置须落在「工作区根（--workdir）/IDEA 工程根」的 .idea/runConfigurations/（非模块内）。
  2. BPMN ⇄ JobWorker 契约 —— 每个 serviceTask 的 zeebe:taskDefinition type 是否都有
     @JobWorker(type) 承接（★铁律 4），反之是否存在未被流程引用的 worker。
  3. 闸门留痕 —— 人工闸门共 11 个（①~⑪）。design/ 下 5 份设计文档的既有闸门
     （流程① / 本体顶层② / 本体核心③ / 应用架构④ / 测试⑤）须留有闸门标记；
     新增闸门 ⑦（第 6 步·典型 ABox，留痕于本体核心设计）/ ⑧⑨（第 9 步前·是否分离、
     第 10 步·是否拆分，留痕于本体顶层设计）/ ⑩⑪（第 11 步前·是否用库、第 12 步前·如何
     导入完整数据，留痕于应用架构设计）须留有对应 `闸门⑦`~`闸门⑪` 标记。
     SKOS 闸门⑥的载体是 ontology/<xxx>-terminology.ttl，不在 design/ 下
     （其留痕由词汇表测试覆盖）。
  4. 铁律落点抽检 —— catalog-v001.xml 存在；SKOS 术语文件存在；若分离 ABox，主本体不得
     活动导入 ABox（★铁律 55/56）。

设计约束：
  * 仅用 Python 标准库；不依赖第三方包。
  * **不硬编码任何绝对路径**；一切路径由命令行参数或相对推导得到。
  * 非工程产物（报告）建议写入工作目录下 HWCodeArtsDir/（即 <工作目录>/HWCodeArtsDir/，★铁律 16）。

用法：
  python validate_structure.py --app-dir <路径> --short <Xxx> [--workdir <路径>]
                               [--report <文件路径>] [--strict]

示例：
  python validate_structure.py --app-dir ../../OntologyFrameworkPizza --short Pizza
"""

import argparse
import io
import json
import os
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

try:  # 保证 Windows 控制台 UTF-8 输出
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding="utf-8", errors="replace")
except Exception:
    pass


# ----------------------------- 结果模型 -----------------------------

class Finding:
    def __init__(self, level, check, detail):
        self.level = level      # ERROR / WARN / INFO
        self.check = check
        self.detail = detail

    def __str__(self):
        return "[{:<5}] ({}) {}".format(self.level, self.check, self.detail)


class Report:
    def __init__(self):
        self.findings = []

    def add(self, level, check, detail):
        self.findings.append(Finding(level, check, detail))

    def error(self, check, detail):
        self.add("ERROR", check, detail)

    def warn(self, check, detail):
        self.add("WARN", check, detail)

    def info(self, check, detail):
        self.add("INFO", check, detail)

    @property
    def errors(self):
        return [f for f in self.findings if f.level == "ERROR"]

    @property
    def warns(self):
        return [f for f in self.findings if f.level == "WARN"]

    def to_dict(self, app_dir, short):
        return {
            "tool": "ontology-app-builder/validate_structure.py",
            "generated_at": datetime.now().isoformat(timespec="seconds"),
            "app_dir": str(app_dir),
            "short": short,
            "summary": {
                "error": len(self.errors),
                "warn": len(self.warns),
                "info": len([f for f in self.findings if f.level == "INFO"]),
            },
            "findings": [
                {"level": f.level, "check": f.check, "detail": f.detail}
                for f in self.findings
            ],
        }


# ----------------------------- 工具函数 -----------------------------

def glob_first(base: Path, pattern: str):
    """返回 base 下匹配 pattern 的第一个文件；无则 None。"""
    for p in sorted(base.glob(pattern)):
        if p.is_file():
            return p
    return None


def read_text(path: Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except Exception:
        return ""


def localname(tag: str) -> str:
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


# ----------------------------- 校验 1：工件齐备性 -----------------------------

def check_completeness(app_dir: Path, short: str, rep: Report, workdir=None):
    lower = short.lower()

    # (描述, 相对路径 glob, 是否必需)
    expected = [
        ("构建文件 pom.xml", "pom.xml", True),
        ("环境脚本 start-env.cmd", "start-env.cmd", True),
        ("环境启动器 EnvPrepare-*.jar", "EnvPrepare-*.jar", True),

        ("设计·顶层设计（第 1 步）", "design/{}应用-顶层设计.md".format(short), True),
        ("设计·流程设计（第 2 步·闸门①）", "design/{}应用-流程设计.md".format(short), True),
        ("设计·本体顶层设计（第 3 步·闸门②；⑧⑨留痕）", "design/{}应用-本体顶层设计.md".format(short), True),
        ("设计·本体核心设计（第 4 步·闸门③；⑦留痕）", "design/{}应用-本体核心设计.md".format(short), True),
        ("设计·应用架构设计（第 7 步·闸门④；⑩⑪留痕）", "design/{}应用-应用架构设计.md".format(short), True),
        ("设计·测试设计文档（第 8 步·闸门⑤）", "design/{}应用-测试设计文档.md".format(short), True),

        ("本体·总入口", "ontology/{}-all.owl".format(lower), True),
        ("本体·IRI 目录 catalog-v001.xml", "ontology/catalog-v001.xml", True),
        ("本体·SKOS 术语表", "ontology/{}-terminology.ttl".format(lower), True),
        ("本体·BPMN 流程文件", "ontology/bpmn/*.bpmn", True),

        ("配置 application.yaml", "src/main/resources/application.yaml", True),
        ("前端 static/index.html", "src/main/resources/static/index.html", False),

        ("主代码·Application", "src/main/java/**/{}Application.java".format(short), True),
        ("主代码·JobWorker", "src/main/java/**/{}OntologyJobWorker.java".format(short), True),
        ("主代码·Controller", "src/main/java/**/web/{}Controller.java".format(short), False),

        ("测试·BPMN 端到端", "src/test/java/**/{}Bpmn*Test.java".format(short), True),
        ("测试·聚合套件入口", "src/test/java/**/All{}SuiteTest.java".format(short), True),
    ]

    for desc, pattern, required in expected:
        hit = glob_first(app_dir, pattern)
        if hit is None:
            if required:
                rep.error("completeness", "缺失：{}（{}）".format(desc, pattern))
            else:
                rep.warn("completeness", "缺失（可选）：{}（{}）".format(desc, pattern))
        else:
            rep.info("completeness", "存在：{} → {}".format(desc, hit.relative_to(app_dir)))

    # 目录型期望产物：glob_first 只认文件，故以 is_dir 单独判定
    dir_expected = [
        ("本体·库环境目录 database/", "ontology/database", True),
    ]
    for desc, rel, required in dir_expected:
        p = app_dir / rel
        if p.is_dir():
            rep.info("completeness", "存在：{} → {}".format(desc, p.relative_to(app_dir)))
        elif required:
            rep.error("completeness", "缺失：{}（{}）".format(desc, rel))
        else:
            rep.warn("completeness", "缺失（可选）：{}（{}）".format(desc, rel))

    # IDEA 运行配置：须落在「工作区根（IDEA 工程根）」的 .idea/runConfigurations/，
    # 而非模块目录内——IDEA 只加载工程根；聚合工程下子模块目录的 .idea 不被加载（不可见）。
    if workdir:
        rc = Path(workdir) / ".idea" / "runConfigurations" / "{}Application.xml".format(short)
        if rc.is_file():
            rep.info("completeness", "存在：IDEA 运行配置（工作区根） → {}".format(rc))
        else:
            rep.error("completeness",
                      "缺失：IDEA 运行配置（须置于工作区根 {}/.idea/runConfigurations/{}Application.xml）"
                      .format(workdir, short))
    else:
        rep.warn("completeness", "未提供 --workdir，跳过 IDEA 运行配置（工作区根 .idea/runConfigurations/）检查")
    mod_rc = app_dir / ".idea" / "runConfigurations" / "{}Application.xml".format(short)
    if mod_rc.is_file():
        rep.warn("completeness",
                 "运行配置误置于模块内（聚合工程下 IDEA 不可见，应移至工作区根/工程根）：{}"
                 .format(mod_rc.relative_to(app_dir)))


# ----------------------------- 校验 2：BPMN ⇄ JobWorker 契约 -----------------------------

def collect_bpmn_jobtypes(app_dir: Path, rep: Report):
    """从所有 BPMN 收集 zeebe:taskDefinition type 与 serviceTask id。"""
    types = {}   # jobType -> [节点id...]
    bpmn_files = list(app_dir.glob("ontology/bpmn/*.bpmn"))
    if not bpmn_files:
        rep.error("contract", "未找到任何 .bpmn（ontology/bpmn/*.bpmn）")
        return types

    for bpmn in bpmn_files:
        try:
            tree = ET.parse(str(bpmn))
        except Exception as e:
            rep.error("contract", "BPMN 解析失败：{}（{}）".format(bpmn.name, e))
            continue
        root = tree.getroot()

        # 遍历 serviceTask，取其 extensionElements/taskDefinition 的 type
        for task in root.iter():
            if localname(task.tag) != "serviceTask":
                continue
            node_id = task.get("id", "?")
            for child in task.iter():
                if localname(child.tag) == "taskDefinition":
                    jt = child.get("type")
                    if jt:
                        types.setdefault(jt, []).append(node_id)

    return types


def collect_worker_jobtypes(app_dir: Path, rep: Report):
    """从 src/main/java 收集 @JobWorker(type="...")。"""
    types = {}   # jobType -> [类名...]
    java_root = app_dir / "src" / "main" / "java"
    if not java_root.exists():
        rep.error("contract", "未找到主代码目录 src/main/java")
        return types

    pat = re.compile(r'@JobWorker\s*\(([^)]*?)\)', re.S)
    type_pat = re.compile(r'type\s*=\s*"([^"]+)"')
    for jf in java_root.rglob("*.java"):
        text = read_text(jf)
        if "@JobWorker" not in text:
            continue
        for args in pat.findall(text):
            m = type_pat.search(args)
            if m:
                types.setdefault(m.group(1), []).append(jf.stem)
    return types


def check_contract(app_dir: Path, rep: Report):
    bpmn_types = collect_bpmn_jobtypes(app_dir, rep)
    worker_types = collect_worker_jobtypes(app_dir, rep)

    if not bpmn_types and not worker_types:
        return

    total_tasks = sum(len(v) for v in bpmn_types.values())
    rep.info("contract", "BPMN serviceTask 作业类型 {} 个（节点实例 {} 个）；@JobWorker {} 个"
             .format(len(bpmn_types), total_tasks, len(worker_types)))

    # BPMN 有、Worker 无 → ERROR（节点无承接）
    for jt in sorted(bpmn_types):
        if jt not in worker_types:
            rep.error("contract",
                      "BPMN 作业类型 `{}`（节点 {}）无对应 @JobWorker".format(
                          jt, ",".join(sorted(set(bpmn_types[jt])))))
        else:
            rep.info("contract", "绑定 OK：`{}` → @JobWorker({})".format(
                jt, ",".join(sorted(set(worker_types[jt])))))

    # Worker 有、BPMN 无 → WARN（可能存在未被流程引用的 worker）
    for jt in sorted(worker_types):
        if jt not in bpmn_types:
            rep.warn("contract",
                     "@JobWorker(`{}`，类 {}) 未被任何 BPMN 节点引用".format(
                         jt, ",".join(sorted(set(worker_types[jt])))))


# ----------------------------- 校验 3：闸门留痕 -----------------------------

def check_gates(app_dir: Path, short: str, rep: Report):
    # 既有闸门①~⑤：仅要求文档中留有「闸门」留痕节。
    legacy = [
        ("闸门①（第 2 步·流程设计）", "design/{}应用-流程设计.md".format(short)),
        ("闸门②（第 3 步·本体顶层设计）", "design/{}应用-本体顶层设计.md".format(short)),
        ("闸门③（第 4 步·本体核心设计）", "design/{}应用-本体核心设计.md".format(short)),
        ("闸门④（第 7 步·应用架构设计）", "design/{}应用-应用架构设计.md".format(short)),
        ("闸门⑤（第 8 步·测试设计文档）", "design/{}应用-测试设计文档.md".format(short)),
    ]
    for name, rel in legacy:
        p = app_dir / rel
        if not p.exists():
            continue  # 缺失已由齐备性检查报告
        text = read_text(p)
        if "闸门" not in text:
            rep.warn("gates", "{} 未在 {} 中找到闸门留痕节".format(name, rel))
        else:
            rep.info("gates", "{} 留痕节存在：{}".format(name, rel))

    # 新增闸门⑦~⑪：各自留痕于对应设计文档，要求出现具体「闸门X」标记。
    extended = [
        ("闸门⑦（第 6 步·典型 ABox 确认）", "闸门⑦", "design/{}应用-本体核心设计.md".format(short)),
        ("闸门⑧（第 9 步前·是否 TBox/ABox 分离）", "闸门⑧", "design/{}应用-本体顶层设计.md".format(short)),
        ("闸门⑨（第 10 步·是否 TBox 拆分）", "闸门⑨", "design/{}应用-本体顶层设计.md".format(short)),
        ("闸门⑩（第 11 步前·是否使用数据库）", "闸门⑩", "design/{}应用-应用架构设计.md".format(short)),
        ("闸门⑪（第 12 步前·如何导入/生成完整数据）", "闸门⑪", "design/{}应用-应用架构设计.md".format(short)),
    ]
    for name, marker, rel in extended:
        p = app_dir / rel
        if not p.exists():
            continue  # 缺失已由齐备性检查报告
        text = read_text(p)
        if marker in text:
            rep.info("gates", "{} 留痕标记存在：{}".format(name, rel))
        else:
            rep.warn("gates", "{} 未在 {} 中找到留痕标记 `{}`".format(name, rel, marker))


# ----------------------------- 校验 4：铁律落点抽检 -----------------------------

def check_rules(app_dir: Path, short: str, rep: Report):
    lower = short.lower()

    # ★55/56：若存在 ABox 文件，主本体不得「活动」导入 ABox
    abox_files = list((app_dir / "ontology").glob("*-abox.owl")) \
        if (app_dir / "ontology").exists() else []
    if abox_files:
        rep.info("rules", "检测到 ABox 文件 {} 个（TBox/ABox 分离）".format(len(abox_files)))
        main_owl = app_dir / "ontology" / "{}-all.owl".format(lower)
        if main_owl.exists():
            text = read_text(main_owl)
            active_hits = []
            for line in text.splitlines():
                stripped = line.strip()
                if not stripped or stripped.startswith("#"):
                    continue
                if re.search(r'owl:imports', stripped) and re.search(r'-abox\.owl', stripped):
                    active_hits.append(stripped)
                if re.search(r'<owl:imports', line) and re.search(r'-abox\.owl', line):
                    active_hits.append(line.strip())
            if active_hits:
                rep.error("rules", "主本体 {} 仍在「活动」导入 ABox（★55 要求注释掉）：{}"
                          .format(main_owl.name, active_hits[0]))
            else:
                rep.info("rules", "主本体未活动导入 ABox（符合 ★55）")
        else:
            rep.warn("rules", "存在 ABox 文件但未找到主本体 {}-all.owl".format(lower))

    # ★12：约束应以 owl:Restriction 表达（抽检是否存在 Restriction）
    tbox = app_dir / "ontology" / "{}-all.owl".format(lower)
    if tbox.exists() and "Restriction" not in read_text(tbox):
        # 子模块可能承载约束，做一次目录级抽检
        all_owl_text = "".join(read_text(p) for p in (app_dir / "ontology").glob("*.owl"))
        if "Restriction" not in all_owl_text:
            rep.warn("rules", "ontology/*.owl 未发现 owl:Restriction（★12：约束不得只写在注释里）")
        else:
            rep.info("rules", "ontology/ 中发现 owl:Restriction（★12 OK）")

    # catalog-v001.xml 可解析性
    cat = app_dir / "ontology" / "catalog-v001.xml"
    if cat.exists():
        try:
            ET.parse(str(cat))
            rep.info("rules", "catalog-v001.xml 可解析")
        except Exception as e:
            rep.error("rules", "catalog-v001.xml 解析失败：{}".format(e))


# ----------------------------- 主流程 -----------------------------

def main():
    ap = argparse.ArgumentParser(description="ontology-app-builder 结构校验器")
    ap.add_argument("--app-dir", required=True, help="新建本体应用模块目录")
    ap.add_argument("--short", required=True, help="应用简称（如 Pizza / Xxx）")
    ap.add_argument("--workdir", default=os.environ.get("HWCODEARTS_DIR"),
                    help="开发工作目录＝工作区根/IDEA 工程根（默认取环境变量 HWCODEARTS_DIR；HWCodeArtsDir 位于其下）；亦用于定位 .idea/runConfigurations/")
    ap.add_argument("--report", default=None,
                    help="报告输出文件路径（建议置于工作目录下 HWCodeArtsDir/ 内）")
    ap.add_argument("--strict", action="store_true",
                    help="严格模式：WARN 也视为失败退出码")
    args = ap.parse_args()

    app_dir = Path(args.app_dir).resolve()
    if not app_dir.exists():
        print("应用目录不存在：{}".format(app_dir), file=sys.stderr)
        return 2

    rep = Report()
    check_completeness(app_dir, args.short, rep, args.workdir)
    check_contract(app_dir, rep)
    check_gates(app_dir, args.short, rep)
    check_rules(app_dir, args.short, rep)

    # 输出
    print("=" * 68)
    print("ontology-app-builder 结构校验：{}（简称 {}）".format(app_dir, args.short))
    print("=" * 68)
    for f in rep.findings:
        print(f)
    print("-" * 68)
    print("汇总：ERROR={} WARN={} INFO={}".format(
        len(rep.errors), len(rep.warns),
        len([f for f in rep.findings if f.level == "INFO"])))

    # 报告落盘（若指定）
    if args.report:
        out = Path(args.report)
        out.parent.mkdir(parents=True, exist_ok=True)
        if out.suffix.lower() == ".json":
            out.write_text(json.dumps(rep.to_dict(app_dir, args.short),
                                      ensure_ascii=False, indent=2), encoding="utf-8")
        else:
            lines = ["# 结构校验报告", "",
                     "- 应用目录：`{}`".format(app_dir),
                     "- 应用简称：{}".format(args.short),
                     "- 生成时间：{}".format(datetime.now().isoformat(timespec="seconds")),
                     "- 汇总：ERROR={} WARN={}".format(len(rep.errors), len(rep.warns)), ""]
            lines += ["{}".format(f) for f in rep.findings]
            out.write_text("\n".join(lines), encoding="utf-8")
        print("报告已写入：{}".format(out))

    # 退出码
    failed = bool(rep.errors) or (args.strict and bool(rep.warns))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
