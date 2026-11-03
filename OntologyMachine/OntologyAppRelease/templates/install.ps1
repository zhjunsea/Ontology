# =====================================================================
#  本体应用运行环境 - 安装引擎（install.ps1）
#  由同目录上级的 tools\install.cmd 调用；也可手工执行：
#     powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\install.ps1
#
#  职责（幂等，可重复执行）：
#    1) 解压 softwares\*.zip 到 runtime\<alias>\
#    2) 探测每个软件的 home（按特征文件，非写死目录名）
#    3) 释放 payload\app 与 payload\lib 到安装根
#    4) 用探测到的绝对路径替换模板占位符，生成：
#         app\application.yaml（UTF-8）
#         start-env.cmd / start-app.cmd（GBK/CP936 + CRLF，双击即可运行）
#    5) 如随包携带 MySQL，则初始化便携式数据目录（mysqld --initialize-insecure）
#
#  设计要点：
#   - 安装目录未知（客户自选），故所有绝对路径均在「安装时」由本脚本注入，
#     绝不写死构建机路径；重复执行可修正被移动过的安装目录。
#   - .cmd 必须为 GBK(ANSI)+CRLF，否则中文在 cmd 窗口中乱码。
# =====================================================================

param(
    # 无值守：不提问；若本机已装 Python 则一律用随包版本重装。
    # 供自动化调用；也可设环境变量 WB_INSTALL_YES=1 达到同样效果。
    [switch]$AssumeYes
)

$ErrorActionPreference = 'Stop'
$NonInteractive = $AssumeYes -or ($env:WB_INSTALL_YES -eq '1')
# 说明：不覆盖 [Console]::OutputEncoding，沿用 install.cmd 里 chcp 936 设定的控制台代码页，
# 使中文提示在 cmd 窗口正常显示（避免与安装入口脚本的代码页冲突）。

$Root        = Split-Path -Parent $PSScriptRoot          # 解压根目录（install.cmd 所在目录）
$ManifestP   = Join-Path $Root 'manifest.json'
$SoftDir     = Join-Path $Root 'softwares'
$PayloadDir  = Join-Path $Root 'payload'
$RuntimeDir  = Join-Path $Root 'runtime'

function Info($m)  { Write-Host "  $m" }
function Step($m)  { Write-Host; Write-Host "==> $m" -ForegroundColor Cyan }
function Fail($m)  { Write-Host "  [FAIL] $m" -ForegroundColor Red; exit 1 }

Step "读取清单"
if (!(Test-Path $ManifestP)) { Fail "未找到 manifest.json: $ManifestP" }
$Manifest = Get-Content -Raw -Encoding UTF8 $ManifestP | ConvertFrom-Json
$AppName    = $Manifest.app
$AppVersion = $Manifest.version
$AppJar     = $Manifest.appJar
Info "应用: $AppName $AppVersion"
Info "安装目录: $Root"

if ($Root -match '[^\x00-\x7F]') {
    # MySQL（mysqld）在 Windows 上用 ANSI API 读取自身路径，非 ASCII（中文等）字符会被截断，
    # 导致 basedir/datadir 解析失败并以 0xC0000005（访问冲突）崩溃。此处直接终止，避免"解压完才报 MySQL 初始化失败"。
    Fail "安装路径含非 ASCII 字符（中文等）：$Root`n        MySQL/Erlang 无法在含中文的路径下运行，请将本安装包解压到纯英文路径后重试（例如 D:\apps\<应用名>）。"
}

# ---- 1) 解压软件 + 探测 home ------------------------------------------------
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Expand-SoftZip($zipPath, $dest) {
    # 不做破坏性删除：目标为空则新建；已存在则就地覆盖（幂等、可恢复）
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    try {
        [System.IO.Compression.ZipFile]::ExtractToDirectory($zipPath, $dest)
    } catch {
        # 回退：Expand-Archive（-Force 覆盖既有文件）
        Expand-Archive -LiteralPath $zipPath -DestinationPath $dest -Force
    }
}

function Find-Home($base, $markerRel) {
    # markerRel 形如 'bin\java.exe'（相对 home）；home = 命中文件路径裁去 markerRel
    $rel  = $markerRel -replace '/', '\'
    $leaf = Split-Path $rel -Leaf
    $hit = Get-ChildItem -Path $base -Recurse -File -Filter $leaf -ErrorAction SilentlyContinue |
           Where-Object { $_.FullName.ToLower().EndsWith($rel.ToLower()) } |
           Select-Object -First 1
    if ($null -eq $hit) { return $null }
    $full = $hit.FullName
    return $full.Substring(0, $full.Length - $rel.Length).TrimEnd('\')
}

function Find-SystemPython {
    # 探测本机已安装的 Python（PATH / py 启动器 / 常见目录 / 注册表），返回
    #   @{ Path=<python.exe>; Home=<目录>; Version=<x.y.z> }；未找到返回 $null。
    # 每个候选都会实际执行 `-c` 校验（排除失效项与 Microsoft Store 别名桩）。
    $cands = New-Object System.Collections.Generic.List[string]
    foreach ($name in @('python.exe', 'python3.exe')) {
        $c = Get-Command $name -ErrorAction SilentlyContinue
        if ($c -and $c.Source) { $cands.Add($c.Source) }
    }
    $py = Get-Command 'py.exe' -ErrorAction SilentlyContinue
    if ($py -and $py.Source) {
        try {
            $exe = (& $py.Source -c "import sys;print(sys.executable)" 2>$null | Select-Object -First 1)
            if ($exe) { $cands.Add($exe) }
        } catch {}
    }
    foreach ($base in @((Join-Path $env:LOCALAPPDATA 'Programs\Python'),
                        (Join-Path $env:ProgramFiles 'Python'),
                        (Join-Path ${env:ProgramFiles(x86)} 'Python'))) {
        if ($base -and (Test-Path $base)) {
            Get-ChildItem -Path $base -Directory -Filter 'Python3*' -ErrorAction SilentlyContinue | ForEach-Object {
                $cands.Add((Join-Path $_.FullName 'python.exe'))
            }
        }
    }
    foreach ($hive in @('HKLM:\SOFTWARE\Python\PythonCore', 'HKCU:\SOFTWARE\Python\PythonCore')) {
        if (Test-Path $hive) {
            Get-ChildItem $hive -ErrorAction SilentlyContinue | ForEach-Object {
                $ip = Get-ItemProperty -Path (Join-Path $_.PSPath 'InstallPath') -ErrorAction SilentlyContinue
                if ($ip) {
                    $d = $ip.'(default)'
                    if (-not $d) { $d = $ip.Executable }
                    if ($d) { $cands.Add((Join-Path $d 'python.exe')) }
                }
            }
        }
    }
    $seen = @{}
    foreach ($c in $cands) {
        if (-not $c) { continue }
        if ($c -match '\\WindowsApps\\') { continue }        # 跳过 Microsoft Store 别名桩
        try { $c = [System.IO.Path]::GetFullPath($c) } catch { continue }
        if (-not (Test-Path $c)) { continue }
        $key = $c.ToLower()
        if ($seen.ContainsKey($key)) { continue }
        $seen[$key] = $true
        $ver = ''
        try { $ver = (& $c -c "import sys;print('%d.%d.%d'%sys.version_info[:3])" 2>$null | Select-Object -First 1) } catch { $ver = '' }
        if ("$ver" -match '^\d+\.\d+\.\d+$') {
            return @{ Path = $c; Home = (Split-Path $c -Parent); Version = "$ver" }
        }
    }
    return $null
}

function Test-PythonUsable($ver) {
    # 客户端零依赖（仅用标准库），Python 3.9+ 即可运行。用于判断本机现有 Python 是否可直接复用。
    if ("$ver" -notmatch '^(\d+)\.(\d+)') { return $false }
    $maj = [int]$Matches[1]; $min = [int]$Matches[2]
    return (($maj -gt 3) -or ($maj -eq 3 -and $min -ge 9))
}

$homesMap = @{}
Step "环境检查 / 安装依赖软件到 runtime\"
foreach ($sw in $Manifest.softwares) {
    $dest = Join-Path $RuntimeDir $sw.alias
    $swHome = $null
    # 幂等：已就绪（home 可探测）则跳过，保留既有数据（如便携 MySQL 的 data / 已装 Python）
    if (Test-Path $dest) {
        foreach ($mk in $sw.markers) { $swHome = Find-Home $dest $mk; if ($swHome) { break } }
        if ($swHome) { Info "[复用] $($sw.label) 已就绪：$swHome" }
    }
    if (!$swHome) {
        $kind = if ($sw.kind) { $sw.kind } else { 'zip' }
        $pkg = Join-Path $SoftDir $sw.zip
        if (!(Test-Path $pkg)) { Fail "缺少软件包: $pkg" }
        if ($kind -eq 'exe') {
            # 安装器（如 Python）：先做「环境检查」——本机已装则询问客户「是否重装」，再决定怎么装。
            $reuse = $false
            $sysPy = $null
            if ($sw.alias -eq 'python') {
                $sysPy = Find-SystemPython
                if ($sysPy) {
                    Info "环境检查：检测到本机已安装 Python —— $($sysPy.Path)（版本 $($sysPy.Version)）"
                    if ([int]($sysPy.Version.Split('.')[0]) -lt 3) {
                        Write-Host "       注意：该 Python 主版本 < 3，客户端建议 Python 3.9+。" -ForegroundColor Yellow
                    }
                    if ($NonInteractive) {
                        # 无值守：本机已有「可用 Python（>=3.9）就复用」——非破坏、免 UAC，
                        # 且客户端零依赖（仅标准库），任何 3.9+ 均可。
                        # 仅当缺失或版本过低时才静默安装随包 Python。
                        if (Test-PythonUsable $sysPy.Version) {
                            $reuse = $true
                            Info "[非交互] 检测到可用 Python（>=3.9），直接复用，不重装"
                        } else {
                            $reuse = $false
                            Info "[非交互] 现有 Python 版本过低，将安装随包 Python 到 runtime\$($sw.alias)"
                        }
                    } else {
                        $ans = Read-Host "  是否用随包 Python 重新安装到 runtime\$($sw.alias)？[y=重新安装 / 直接回车或 N=使用现有]"
                        $reuse = -not ($ans -match '^(?i)\s*(y|yes)\s*$')
                    }
                } else {
                    Info "环境检查：本机未检测到 Python；将安装随包 Python 到 runtime\$($sw.alias)"
                    $reuse = $false
                }
            }

            if ($reuse -and $sysPy) {
                $swHome = $sysPy.Home
                $homesMap['pythonSource'] = 'system'
                Info "[复用] 使用本机现有 Python：$swHome（客户端零依赖，任何 Python 3.9+ 均可）"
            } else {
                New-Item -ItemType Directory -Force -Path $dest | Out-Null
                $sargs = @()
                foreach ($a in $sw.silentArgs) { $sargs += $a.Replace('{dest}', $dest) }
                Info "静默安装 $($sw.label): $($sw.zip)  ->  $dest"
                # 启动安装器并「显式等待退出」。.exe 安装器多为 GUI 子系统：直接用 & 调用
                # 在无值守宿主下可能不等待、拿不到真实退出码。优先用 .NET Process 直接启动——
                # 它既不重建环境字典（规避 Start-Process 在「同名仅大小写不同」的环境变量上
                # 抛 "Item has already been added" 的问题），又能可靠等待。
                $argLine = (($sargs | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }) -join ' ')
                $exitCode = $null
                try {
                    $psi = New-Object System.Diagnostics.ProcessStartInfo
                    $psi.FileName = $pkg
                    $psi.UseShellExecute = $false
                    $psi.Arguments = $argLine
                    $proc = [System.Diagnostics.Process]::Start($psi)
                    $proc.WaitForExit()
                    $exitCode = $proc.ExitCode
                } catch {
                    # 回退：个别受限宿主会拦截 .NET 直接启动进程，此时退回 Start-Process。
                    $p = Start-Process -FilePath $pkg -ArgumentList $sargs -Wait -PassThru
                    $exitCode = $p.ExitCode
                }
                if ($exitCode -ne 0) { Fail "$($sw.label) 安装失败（退出码 $exitCode）" }
                foreach ($mk in $sw.markers) { $swHome = Find-Home $dest $mk; if ($swHome) { break } }
                if (!$swHome) { $swHome = $dest }   # 兜底：以目标目录为 home
                if ($sw.alias -eq 'python') { $homesMap['pythonSource'] = 'bundled' }
                Info "  -> home: $swHome"
            }
        } else {
            Info "解压 $($sw.label): $($sw.zip)"
            Expand-SoftZip $pkg $dest
            foreach ($mk in $sw.markers) { $swHome = Find-Home $dest $mk; if ($swHome) { break } }
            if (!$swHome) { $swHome = $dest }   # 兜底：以目标目录为 home
            Info "  -> home: $swHome"
        }
    }
    $homesMap[$sw.alias] = $swHome
    $homesMap[$sw.token] = $swHome
}

# ---- 1.5) 应用级 Java 环境（仅本次安装进程，不写系统/用户环境变量） ---------
if ($homesMap['jdk']) {
    $jdkHome = $homesMap['jdk']
    $env:JAVA_HOME = $jdkHome
    $env:Path = (Join-Path $jdkHome 'bin') + ';' + $env:Path
    Info "[应用级] JAVA_HOME=$jdkHome（仅本次安装进程；未写入系统/用户环境变量）"
}

# ---- 2) 释放 payload --------------------------------------------------------
Step "释放应用、依赖库与随附载荷"
$payloadDirs = if ($Manifest.payloadDirs) { $Manifest.payloadDirs } else { @('app','lib') }
foreach ($pd in $payloadDirs) {
    $src = Join-Path $PayloadDir $pd
    $dst = Join-Path $Root $pd
    if (!(Test-Path $src)) { Fail "缺少 payload\$pd" }
    # 合并式释放：不删除既有目录，逐文件覆盖（幂等、非破坏性）
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    Copy-Item -Path (Join-Path $src '*') -Destination $dst -Recurse -Force
    Info "payload\$pd -> $dst"
}
$AppDir = Join-Path $Root 'app'
$LibDir = Join-Path $Root 'lib'

$envJar = Get-ChildItem -Path $LibDir -Filter 'EnvPrepare-*.jar' -File -ErrorAction SilentlyContinue |
          Select-Object -First 1
if ($null -eq $envJar) { Fail "lib\ 下未找到 EnvPrepare-*.jar" }

# ---- 3) 组装占位符 ----------------------------------------------------------
$tokens = @{}
function SetTok($k, $v) { $script:tokens[$k] = if ($null -eq $v) { '' } else { [string]$v } }

SetTok '@{JAVA_HOME}'     ($homesMap['jdk'])
SetTok '@{CAMUNDA_HOME}'  ($homesMap['camunda'])
SetTok '@{ONTOP_HOME}'    ($homesMap['ontop'])
SetTok '@{RABBITMQ_HOME}' ($homesMap['rabbitmq'])
SetTok '@{ERLANG_HOME}'   ($homesMap['erlang'])
SetTok '@{PYTHON_HOME}'   ($homesMap['python'])
SetTok '@{MYSQL_HOME}'    ($homesMap['mysql'])
SetTok '@{MYSQL_BIN}'     $(if ($homesMap['mysql']) { Join-Path $homesMap['mysql'] 'bin\mysql.exe' } else { '' })
SetTok '@{MYSQL_DATADIR}' $(if ($homesMap['mysql']) { Join-Path $homesMap['mysql'] 'data' } else { '' })
SetTok '@{MYSQL_PORT}'    $(if ($Manifest.mysql -and $Manifest.mysql.included) { $Manifest.mysql.port } else { 3306 })
SetTok '@{APP_YAML}'      (Join-Path $AppDir 'application.yaml')
SetTok '@{APP_DIR}'       $AppDir
SetTok '@{APP_JAR}'       (Join-Path $AppDir $AppJar)
SetTok '@{APP_NAME}'      $AppName
SetTok '@{APP_VERSION}'   $AppVersion
SetTok '@{ENV_JAR}'       $envJar.FullName

function Apply-Tokens($text) {
    foreach ($k in $tokens.Keys) { $text = $text.Replace($k, $tokens[$k]) }
    return $text
}
function Assert-NoToken($path, $text) {
    if ($text -match '@\{[A-Z_]+\}') {
        $m = [regex]::Match($text, '@\{[A-Z_]+\}').Value
        Fail "生成 $path 时仍有未替换占位符 $m（可能清单与模板不匹配）"
    }
}
function Write-Utf8($path, $text) {
    $text = ($text -replace "`r`n", "`n") -replace "`n", "`r`n"
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}
function Write-Gbk($path, $text) {
    $text = ($text -replace "`r`n", "`n") -replace "`n", "`r`n"
    [System.IO.File]::WriteAllText($path, $text, [System.Text.Encoding]::GetEncoding(936))
}

$tplDir = Join-Path $PayloadDir 'templates'

# ---- 4) 生成 application.yaml ----------------------------------------------
Step "生成配置 app\application.yaml"
$yamlTpl = Get-Content -Raw -Encoding UTF8 (Join-Path $tplDir 'application.yaml.tmpl')
$yamlOut = Apply-Tokens $yamlTpl
$yamlP   = Join-Path $AppDir 'application.yaml'
Assert-NoToken $yamlP $yamlOut
Write-Utf8 $yamlP $yamlOut
Info "-> $yamlP"

# ---- 5) 生成启动脚本 --------------------------------------------------------
Step "生成启动脚本（GBK + CRLF）"
$envTpl = Get-Content -Raw -Encoding UTF8 (Join-Path $tplDir 'start-env.cmd.tmpl')
$envOut = Apply-Tokens $envTpl
$envP   = Join-Path $Root 'start-env.cmd'
Assert-NoToken $envP $envOut
Write-Gbk $envP $envOut
Info "-> $envP"

$appTpl = Get-Content -Raw -Encoding UTF8 (Join-Path $tplDir 'start-app.cmd.tmpl')
$appOut = Apply-Tokens $appTpl
$appP   = Join-Path $Root 'start-app.cmd'
Assert-NoToken $appP $appOut
Write-Gbk $appP $appOut
Info "-> $appP"

# Python 客户端启动脚本（仅在随包 python-client 时生成）
if ($Manifest.pythonClient -and $Manifest.pythonClient.enabled) {
    Step "生成 Python 客户端启动脚本（GBK + CRLF）"
    SetTok '@{CLIENT_DIR}'   $Manifest.pythonClient.dir
    SetTok '@{CLIENT_ENTRY}' $Manifest.pythonClient.entry
    $cliTpl = Get-Content -Raw -Encoding UTF8 (Join-Path $tplDir 'start-client.cmd.tmpl')
    $cliOut = Apply-Tokens $cliTpl
    $cliP   = Join-Path $Root 'start-client.cmd'
    Assert-NoToken $cliP $cliOut
    Write-Gbk $cliP $cliOut
    Info "-> $cliP"
}

# ---- 6) 便携式 MySQL 初始化 -------------------------------------------------
if ($Manifest.mysql -and $Manifest.mysql.included -and $homesMap['mysql']) {
    Step "初始化便携式 MySQL"
    $mysqlHome = $homesMap['mysql']
    $mysqld = Join-Path $mysqlHome 'bin\mysqld.exe'
    $datadir = Join-Path $mysqlHome 'data'
    if (!(Test-Path $mysqld)) { Fail "未找到 mysqld.exe: $mysqld" }
    # 幂等：已初始化的 data（存在 ibdata1）直接跳过，保留既有数据。
    if (Test-Path (Join-Path $datadir 'ibdata1')) {
        Info "data 目录已初始化（存在 ibdata1），跳过初始化（保留原有数据）"
    } else {
        New-Item -ItemType Directory -Force -Path $datadir | Out-Null
        # 二进制自检：解压不完整/磁盘不足/缺运行库时，直接给出可读原因而非后续"访问冲突"黑盒。
        & $mysqld --version 2>$null
        if ($LASTEXITCODE -ne 0) { Fail "mysqld 无法运行（--version 退出码 $LASTEXITCODE）。请检查磁盘空间、软件包解压完整性，以及是否缺少 Microsoft Visual C++ 运行库。" }
        $absBase = (Resolve-Path -LiteralPath $mysqlHome).Path
        $absData = (Resolve-Path -LiteralPath $datadir).Path
        # --no-defaults：忽略系统 my.ini/my.cnf，避免既有 MySQL 配置（basedir/datadir 等）干扰便携实例。
        # 命令/路径中若含空格，统一用双引号括起（此处显式加引号）；用调用运算符而非 Start-Process（后者同名大小写环境变量会抛异常）。
        Info "执行: `"$mysqld`" --no-defaults --initialize-insecure --basedir=`"$absBase`" --datadir=`"$absData`""
        Push-Location $mysqlHome
        try {
            & $mysqld --no-defaults --initialize-insecure "--basedir=$absBase" "--datadir=$absData"
        } finally { Pop-Location }
        if ($LASTEXITCODE -ne 0) {
            $code = [uint32]($LASTEXITCODE -band 0xFFFFFFFF)
            $errf = Get-ChildItem -Path $datadir -Filter *.err -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($errf) {
                Write-Host "  ---- mysqld 错误日志（末尾） ----" -ForegroundColor DarkYellow
                Get-Content $errf.FullName -Tail 40 | ForEach-Object { Write-Host "    | $_" }
                Write-Host "  --------------------------------" -ForegroundColor DarkYellow
            }
            $hint = ''
            if ($code -eq 0xC0000005) { $hint = '（STATUS_ACCESS_VIOLATION：常见于缺少 Microsoft Visual C++ 运行库、二进制损坏，或系统存在冲突的 MySQL 配置 my.ini）' }
            elseif ($code -eq 0xC000007B) { $hint = '（STATUS_INVALID_IMAGE_FORMAT：二进制损坏或运行库不匹配）' }
            elseif ($code -eq 0xC0000135) { $hint = '（STATUS_DLL_NOT_FOUND：缺少 Microsoft Visual C++ 运行库）' }
            Fail "MySQL 初始化失败（退出码 $LASTEXITCODE / 0x$('{0:X}' -f $code)）$hint。详见上方错误日志。"
        }
        Info "MySQL data 初始化完成（root 空密码）"
    }
}

# ---- 7) 完成标记 ------------------------------------------------------------
$stamp = @{
    app = $AppName; version = $AppVersion
    installedAt = (Get-Date).ToString('s')
    installDir = $Root
    homes = $homesMap
} | ConvertTo-Json -Depth 6
Write-Utf8 (Join-Path $Root '.installed.json') $stamp

Step "安装完成"
Info "安装根目录: $Root"
Info "环境服务:   $Root\start-env.cmd"
Info "应用 Worker: $Root\start-app.cmd"
if ($Manifest.pythonClient -and $Manifest.pythonClient.enabled) {
    Info "Python 客户端: $Root\start-client.cmd"
}
Info "配置文件:   $AppDir\application.yaml"
exit 0
