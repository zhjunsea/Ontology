import sys
import os
import time
import subprocess
import ctypes
from pathlib import Path

# ==========================================
# 1. 依赖检查
# ==========================================
try:
    import psutil
except ImportError:
    print("[ERROR] 缺少 psutil 库，请在当前虚拟环境中执行: pip install psutil")
    sys.exit(1)

# ==========================================
# 2. 读取 startup-config.properties
# ==========================================
def load_properties(filepath: str) -> dict:
    """解析 Java 风格的 .properties 配置文件（支持 # 注释）"""
    props = {}
    path = Path(filepath)
    if not path.exists():
        print(f"[ERROR] 配置文件不存在: {filepath}")
        sys.exit(1)

    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" in line:
                key, value = line.split("=", 1)
                value = value.strip()
                # ★ 自动去除值两端成对的引号（兼容误加的 " 或 '）
                if len(value) >= 2 and value[0] == value[-1] and value[0] in ('"', "'"):
                    value = value[1:-1]
                props[key.strip()] = value
    return props


def to_file_uri(path_str: str) -> str:
    """将 Windows/Unix 路径转换为 Ontop 要求的 file:/// URI 格式"""
    normalized = path_str.replace("\\", "/")
    if normalized.lower().startswith("file:"):
        return normalized
    if not normalized.startswith("/"):
        normalized = "/" + normalized
    return "file://" + normalized


SCRIPT_DIR = Path(__file__).resolve().parent
CONFIG_FILE = SCRIPT_DIR / "startup-config.properties"
CONFIG = load_properties(str(CONFIG_FILE))

REQUIRED_KEYS = [
    "JAVA_HOME",
    "ONTOP_HOME",
    "ONTOP_PROPERTIES",
    "ONTOP_MAPPING",
    "ONTOLOGY_FILE",
    "CAMUNDA_HOME",
    "RABBITMQ_HOME",
    "MYSQL_SRV_NAME",      # ★ 新增
]
missing = [k for k in REQUIRED_KEYS if k not in CONFIG]
if missing:
    print(f"[ERROR] startup-config.properties 缺少必要配置项: {', '.join(missing)}")
    sys.exit(1)

LOG_DIR = SCRIPT_DIR / "logs"

print(f"[INFO] 已加载配置: {CONFIG_FILE}")
for k, v in CONFIG.items():
    print(f"       {k} = {v}")
print(f"[INFO] 统一日志目录: {LOG_DIR}")

# ==========================================
# 3. 进程管理工具函数
# ==========================================
def kill_process_tree(proc: psutil.Process) -> None:
    """安全地终止进程及其所有子进程"""
    try:
        children = proc.children(recursive=True)
        for child in children:
            try:
                child.terminate()
            except psutil.NoSuchProcess:
                pass
        gone, alive = psutil.wait_procs(children, timeout=3)
        for p in alive:
            try:
                p.kill()
            except psutil.NoSuchProcess:
                pass
        proc.terminate()
        proc.wait(timeout=3)
    except psutil.NoSuchProcess:
        pass
    except Exception as e:
        print(f"       ⚠️ 终止进程树异常 PID={proc.pid}: {e}")


def find_processes_by_name(name: str):
    """根据进程名模糊查找"""
    targets = []
    name_lower = name.lower()
    for proc in psutil.process_iter(["pid", "name"]):
        try:
            if proc.info["name"] and name_lower in proc.info["name"].lower():
                targets.append(proc)
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            continue
    return targets


# ==========================================
# ★ MySQL Windows 服务管理（含按需 UAC 提权）
# ==========================================
_ADMIN_START_FLAG = "--_admin_start_mysql"


def _is_admin() -> bool:
    """检查当前进程是否以管理员权限运行"""
    try:
        return ctypes.windll.shell32.IsUserAnAdmin() != 0
    except Exception:
        return False


def _direct_net_start(service_name: str) -> tuple:
    """直接执行 net start（当前已是管理员时调用）"""
    try:
        r = subprocess.run(
            ["net", "start", service_name],
            capture_output=True, text=True, timeout=60,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        return r.returncode, r.stdout.strip(), r.stderr.strip()
    except subprocess.TimeoutExpired:
        return -1, "", "net start 命令超时 (>60s)"
    except Exception as e:
        return -1, "", str(e)


def _run_net_start_as_admin(service_name: str) -> tuple:
    """
    以管理员权限执行 net start <service_name>。
    通过临时文件桥接子进程输出（ShellExecuteW 无法直接捕获）。
    返回 (returncode, stdout, stderr)。
    """
    result_file = Path(os.environ.get("TEMP", ".")) / f".mysql_start_result_{os.getpid()}.tmp"
    script_path = str(Path(__file__).resolve())
    params = f'"{script_path}" {_ADMIN_START_FLAG} "{service_name}" "{result_file}"'

    ret = ctypes.windll.shell32.ShellExecuteW(
        None, "runas", sys.executable, params,
        str(Path(__file__).resolve().parent),
        0  # SW_HIDE → 不弹出控制台窗口
    )

    if ret <= 32:
        return -1, "", "UAC 提权被拒绝或失败"

    # 等待结果文件生成（最多 60s）
    for _ in range(60):
        time.sleep(1)
        if result_file.exists():
            break
    else:
        return -1, "", "管理员子进程超时，未生成结果文件"

    try:
        content = result_file.read_text(encoding="utf-8").strip()
        result_file.unlink(missing_ok=True)
        parts = content.split("\n", 2)
        rc = int(parts[0])
        stdout = parts[1] if len(parts) > 1 else ""
        stderr = parts[2] if len(parts) > 2 else ""
        return rc, stdout, stderr
    except Exception as e:
        return -1, "", f"读取结果文件失败: {e}"


def get_windows_service_status(service_name: str):
    """
    查询 Windows 服务状态。
    返回: 'RUNNING', 'STOPPED', 'START_PENDING', 'STOP_PENDING' 等，
          服务不存在时返回 None。
    """
    try:
        result = subprocess.run(
            ["sc", "query", service_name],
            capture_output=True, text=True, timeout=10,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        output = result.stdout
        for line in output.splitlines():
            stripped = line.strip()
            if stripped.upper().startswith("STATE"):
                parts = stripped.split(":")
                if len(parts) >= 2:
                    state_str = parts[1].strip()
                    tokens = state_str.split()
                    if len(tokens) >= 2:
                        return tokens[-1].upper()
                    elif len(tokens) == 1:
                        return tokens[0].upper()
        return None
    except Exception as e:
        print(f"       ⚠️ 查询服务 {service_name} 状态异常: {e}")
        return None


def start_mysql_service():
    """
    启动 MySQL Windows 服务（仅在停止状态下启动）。
    ★ 仅在实际需要 net start 且当前非管理员时才触发 UAC 提权。
    """
    service_name = CONFIG["MYSQL_SRV_NAME"]
    print(f"[START] MySQL: 检查 Windows 服务 '{service_name}' 状态 ...")

    # ---------- 1. 查询当前状态（无需管理员） ----------
    status = get_windows_service_status(service_name)

    if status is None:
        print(f"       🔴 未找到 Windows 服务 '{service_name}'，请确认服务已注册")
        print(f"          提示: sc query type= service state= all | findstr /i mysql")
        return False

    if status == "RUNNING":
        print(f"       ℹ️  {service_name} 已在运行中，跳过启动")
        return True

    # ---------- 2. 需要启动 → 按需提权 ----------
    if status in ("START_PENDING", "CONTINUE_PENDING"):
        print(f"       ℹ️  {service_name} 正在启动中 (state={status})，等待就绪 ...")
    else:
        print(f"       🔄 {service_name} 当前状态: {status}，正在启动 ...")

        if _is_admin():
            rc, stdout, stderr = _direct_net_start(service_name)
        else:
            print(f"       🔑 需要管理员权限，正在请求提权 ...")
            rc, stdout, stderr = _run_net_start_as_admin(service_name)

        if rc != 0:
            print(f"       🔴 net start 返回码: {rc}")
            if stdout:
                print(f"          stdout: {stdout}")
            if stderr:
                print(f"          stderr: {stderr}")
            error_hints = {
                5: "权限不足 → 提权可能被拒绝",
                1067: "进程意外终止 → 检查 MySQL error log 及端口占用",
                1069: "服务登录凭据无效 → services.msc 中重设 MySQL80 登录密码",
                1058: "服务被禁用 → 运行 sc config MySQL80 start= auto 后重试",
                -1: "提权失败 → 请手动以管理员身份运行本脚本",
            }
            hint = error_hints.get(rc)
            if hint:
                print(f"          💡 {hint}")
            return False

    # ---------- 3. 轮询等待 RUNNING ----------
    max_wait = 30
    for i in range(max_wait):
        time.sleep(1)
        current = get_windows_service_status(service_name)
        if current == "RUNNING":
            print(f"       ✅ {service_name} 已成功启动 (耗时 {i + 1}s)")
            return True
        if current is None:
            print(f"       🔴 等待过程中服务消失")
            return False

    # ---------- 4. 超时仍未就绪 ----------
    final_status = get_windows_service_status(service_name)
    print(f"       🔴 {service_name} 启动超时 ({max_wait}s)，最终状态: {final_status}")
    print(f"          建议: 在管理员 CMD 中手动执行 net start {service_name} 查看详细错误")
    return False


# ==========================================
# 4. 停止服务逻辑
# ==========================================
def stop_camunda():
    print("[STOP] Camunda: 执行 c8run.exe stop ...")
    cmd = [str(Path(CONFIG["CAMUNDA_HOME"]) / "c8run.exe"), "stop"]
    try:
        subprocess.run(
            cmd,
            cwd=CONFIG["CAMUNDA_HOME"],
            timeout=30,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        print("       ✅ Camunda 停止指令已发送")
    except Exception as e:
        print(f"       ⚠️ Camunda 停止异常: {e}")


def stop_ontop():
    print("[STOP] Ontop: 查找并终止进程树 ...")
    targets = find_processes_by_name("ontop")
    for proc in psutil.process_iter(["pid", "name", "cmdline"]):
        try:
            if proc.info["name"] and proc.info["name"].lower() == "java.exe":
                cmdline = " ".join(proc.info["cmdline"]).lower()
                if "ontop" in cmdline:
                    targets.append(proc)
        except (psutil.NoSuchProcess, psutil.AccessDenied, TypeError):
            continue

    seen = set()
    unique = [p for p in targets if not (p.pid in seen or seen.add(p.pid))]

    if not unique:
        print("       ℹ️ 未发现运行中的 Ontop 进程")
        return

    for p in unique:
        print(f"       正在终止 Ontop 进程树 PID={p.pid} ...")
        kill_process_tree(p)
    print("       ✅ Ontop 已完全停止")


def stop_rabbitmq():
    print("[STOP] RabbitMQ: 查找并终止 Erlang 节点 ...")
    targets = []
    for proc in psutil.process_iter(["pid", "name", "cmdline"]):
        try:
            if proc.info["name"] and proc.info["name"].lower() == "erl.exe":
                cmdline = " ".join(proc.info["cmdline"]).lower()
                if "rabbit" in cmdline or "25672" in cmdline:
                    targets.append(proc)
        except (psutil.NoSuchProcess, psutil.AccessDenied, TypeError):
            continue

    targets.extend(find_processes_by_name("rabbitmq"))
    seen = set()
    unique = [p for p in targets if not (p.pid in seen or seen.add(p.pid))]

    if not unique:
        print("       ℹ️ 未发现运行中的 RabbitMQ/Erlang 进程")
        return

    for p in unique:
        print(f"       正在终止 PID={p.pid} ({p.name()}) ...")
        kill_process_tree(p)

    time.sleep(2)
    port_freed = all(
        conn.laddr.port != 25672
        for conn in psutil.net_connections(kind="inet")
        if conn.laddr
    )
    if port_freed:
        print("       ✅ RabbitMQ 已完全停止，端口 25672 已释放")
    else:
        print("       🔴 警告: 端口 25672 仍被占用")


# ==========================================
# 5. 启动服务逻辑
# ==========================================
def start_service(name: str, cmd: list, cwd: str = None, log_file: str = None, env=None):
    print(f"[START] {name}: {' '.join(cmd)}")

    executable = cmd[0]
    if executable.lower().endswith(".bat") or executable.lower().endswith(".cmd"):
        cmd = ["cmd.exe", "/c"] + cmd

    if cwd is not None:
        cwd_path = Path(cwd)
        if not cwd_path.exists():
            raise FileNotFoundError(f"[{name}] cwd 路径不存在: {cwd}")
        if not cwd_path.is_dir():
            raise NotADirectoryError(f"[{name}] cwd 不是有效目录: {cwd}")

    kwargs = {"cwd": cwd, "creationflags": subprocess.CREATE_NO_WINDOW}
    if env is not None:
        kwargs["env"] = env

    f_handle = None
    if log_file:
        Path(log_file).parent.mkdir(parents=True, exist_ok=True)
        f_handle = open(log_file, "a", encoding="utf-8")
        kwargs["stdout"] = f_handle
        kwargs["stderr"] = subprocess.STDOUT
    else:
        kwargs["stdout"] = subprocess.PIPE
        kwargs["stderr"] = subprocess.STDOUT

    try:
        proc = subprocess.Popen(cmd, **kwargs)
        print(f"       ✅ {name} 已启动 (PID={proc.pid})")
        if log_file:
            print(f"       📄 日志输出: {log_file}")
        return proc, f_handle
    except FileNotFoundError as e:
        print(f"       🔴 {name} 启动失败: 找不到可执行文件")
        print(f"          命令: {' '.join(cmd)}")
        print(f"          CWD: {cwd}")
        raise e


# ==========================================
# 6. 主流程
# ==========================================
def main():
    print("=" * 50)
    print(" 🚀 开始初始化本体环境服务 ")
    print("=" * 50)

    # 1. 清理旧服务
    stop_camunda()
    stop_ontop()
    stop_rabbitmq()
    time.sleep(2)

    # ★ 2. 启动 MySQL（在 RabbitMQ/Ontop 之前，确保数据库就绪）
    mysql_ok = start_mysql_service()
    if not mysql_ok:
        print("\n🔴 MySQL80 未能就绪，Ontop 可能无法连接数据库。")
        print("   请手动检查 MySQL80 服务后重新运行本脚本。")
        resp = input("   是否继续启动其他服务？(y/N): ").strip().lower()
        if resp != "y":
            print("✅ 已取消启动。")
            return

    # 3. 确保统一日志目录存在
    LOG_DIR.mkdir(parents=True, exist_ok=True)

    processes = []
    file_handles = []

    # 4. 启动 RabbitMQ
    rmq_cmd = [str(Path(CONFIG["RABBITMQ_HOME"]) / "sbin" / "rabbitmq-server.bat")]
    p, f = start_service(
        "RabbitMQ",
        rmq_cmd,
        cwd=CONFIG["RABBITMQ_HOME"],
        log_file=str(LOG_DIR / "rabbitmq.log"),
    )
    processes.append(p)
    if f:
        file_handles.append(f)

    # 5. 启动 Ontop（注入 JAVA_HOME 环境变量）
    ontop_env = os.environ.copy()
    ontop_env["JAVA_HOME"] = CONFIG["JAVA_HOME"]

    ontop_properties = to_file_uri(CONFIG["ONTOP_PROPERTIES"])
    ontop_mapping = to_file_uri(CONFIG["ONTOP_MAPPING"])
    ontology_file = to_file_uri(CONFIG["ONTOLOGY_FILE"])
    ontology_catalog = CONFIG.get("ONTOLOGY_CATALOG", "")

    print(f"       📎 Properties: {ontop_properties}")
    print(f"       📎 Mapping:    {ontop_mapping}")
    print(f"       📎 Ontology:   {ontology_file}")
    print(f"       📎 Catalog:    {ontology_catalog}")

    ontop_cmd = [
        str(Path(CONFIG["ONTOP_HOME"]) / "ontop.bat"),
        "endpoint",
        "--properties", ontop_properties,
        "--xml-catalog", ontology_catalog,
        "-m", ontop_mapping,
        "-t", ontology_file,
    ]

    p, f = start_service(
        "Ontop",
        ontop_cmd,
        cwd=CONFIG["ONTOP_HOME"],
        log_file=str(LOG_DIR / "ontop.log"),
        env=ontop_env,
    )
    processes.append(p)
    if f:
        file_handles.append(f)

    # 6. 启动 Camunda
    camunda_cmd = [
        str(Path(CONFIG["CAMUNDA_HOME"]) / "c8run.exe"),
        "start",
        "--port",
        "9080",
    ]
    p, _ = start_service("Camunda", camunda_cmd, cwd=CONFIG["CAMUNDA_HOME"])
    processes.append(p)

    print("\n" + "=" * 50)
    print(" ✅ 所有服务已启动。")
    print(f" 📂 统一日志目录: {LOG_DIR}")
    print(" ⏹️  按 Ctrl+C 停止所有服务并退出。")
    print("=" * 50 + "\n")

    # 7. 保持主进程运行并监听退出信号
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n[EXIT] 收到退出信号，正在清理所有进程...")
        for p in processes:
            if p.poll() is None:
                try:
                    kill_process_tree(psutil.Process(p.pid))
                except psutil.NoSuchProcess:
                    pass
        for f in file_handles:
            f.close()
        stop_camunda()
        # ★ MySQL 是系统服务，Ctrl+C 时不自动停止
        # 如需同时停止，取消下面注释:
        # mysql_srv = CONFIG["MYSQL_SRV_NAME"]
        # subprocess.run(["net", "stop", mysql_srv],
        #                creationflags=subprocess.CREATE_NO_WINDOW, timeout=30)
        print("✅ 清理完成，已安全退出。")


# ==========================================
# ★ 入口：处理管理员子命令 OR 正常启动
# ==========================================
if __name__ == "__main__":
    # 隐藏的管理员子命令入口（由 _run_net_start_as_admin 触发）
    if len(sys.argv) >= 3 and sys.argv[1] == _ADMIN_START_FLAG:
        svc_name = sys.argv[2]
        result_path = Path(sys.argv[3]) if len(sys.argv) >= 4 else None
        rc, out, err = _direct_net_start(svc_name)
        if result_path:
            result_path.write_text(f"{rc}\n{out}\n{err}", encoding="utf-8")
        sys.exit(rc)

    # 正常入口
    main()