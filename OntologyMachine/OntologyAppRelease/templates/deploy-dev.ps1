<#
  开发环境部署器（由 OntologyAppRelease 打包生成，随「开发工程」zip 携带）
  ---------------------------------------------------------------------------
  把一个本体应用的「开发工程」部署到一个开发环境（工作区聚合工程）：

    · 目标目录不存在                 -> 新建开发环境：以本工程为该目录的工作区根。
    · 目标目录已是开发环境(聚合 pom)  -> 合并：拷入本应用模块并登记到根 pom 的 <modules>，
                                       与已有模块（如 Example）共用同一开发环境
                                       （就像现在的 OntologyMachine 一样，多模块并列）。

  由同目录上层的 deploy-dev.cmd 调用；也可直接：
      powershell -NoProfile -ExecutionPolicy Bypass -File tools\deploy-dev.ps1 -Target <目录>
#>
param([Parameter(Mandatory = $false)][string]$Target)

$ErrorActionPreference = "Stop"
# 关闭 Expand-Archive 的进度条输出（否则控制台会被进度条刷屏，且影响日志可读性）
$ProgressPreference = "SilentlyContinue"

# --- 定位：$here = ...\tools，$root = 工程根（含 pom.xml） ----------------------
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $here
$module = "@{MODULE}"

function Info([string]$m) { Write-Host "  $m" }
function Ok([string]$m)   { Write-Host "  [OK] $m" -ForegroundColor Green }
function Warn([string]$m) { Write-Host "  [警告] $m" -ForegroundColor Yellow }

$wsPom = Join-Path $root "pom.xml"
if (-not (Test-Path -LiteralPath $wsPom)) {
    throw "未找到工作区根 pom.xml：$wsPom（请解压完整的开发工程 zip 后再运行）"
}
$moduleDir = Join-Path $root $module
if (-not (Test-Path -LiteralPath $moduleDir)) {
    throw "未找到应用模块目录：$moduleDir"
}

if ([string]::IsNullOrWhiteSpace($Target)) {
    $Target = Join-Path (Get-Location).Path "OntologyFrameworkWorkspace"
}
$Target = [System.IO.Path]::GetFullPath($Target)

Write-Host "============================================================"
Write-Host "  本体应用开发环境 - 部署器"
Write-Host "  应用模块 : $module"
Write-Host "  工程根   : $root"
Write-Host "  目标目录 : $Target"
Write-Host "============================================================"
Write-Host ""

# --- 判定：目录是否为「开发环境」（根 pom 为聚合 pom 且含 <modules>） ----------
function Test-Workspace([string]$dir) {
    $p = Join-Path $dir "pom.xml"
    if (-not (Test-Path -LiteralPath $p)) { return $false }
    $t = Get-Content -LiteralPath $p -Raw
    return ($t -match "<packaging>\s*pom\s*</packaging>") -or ($t -match "<modules>")
}

# --- 在目标工作区根 pom 中登记本模块（幂等） ---------------------------------
function Register-Module([string]$dir, [string]$mod) {
    $p = Join-Path $dir "pom.xml"
    $t = Get-Content -LiteralPath $p -Raw
    if ($t -match "<module>\s*$([regex]::Escape($mod))\s*</module>") {
        Info "目标 <modules> 中已登记模块：$mod（跳过）"
        return
    }
    if ($t -match "(?m)^([ \t]*)<modules>") {
        # 在 <modules> 后插入，缩进对齐：<modules> 的缩进 + 4 空格。
        $t = $t -replace "(?m)^([ \t]*)<modules>", "`$1<modules>`r`n`$1    <module>$mod</module>"
    }
    else {
        $t = $t.Replace("</project>", "    <modules>`r`n        <module>$mod</module>`r`n    </modules>`r`n</project>")
    }
    Set-Content -LiteralPath $p -Value $t -Encoding UTF8
    Ok "已在目标根 pom 的 <modules> 中登记：$mod"
}

#
# --- 释放随包 JDK 到 <目标>/jdk（与运行包同一份 JDK 27；已存在则跳过） --------
#     目的：模块 start-env.cmd 会优先使用 %~dp0..\jdk，从而与运行包/安装器一致地用 JDK 27，
#           不落到系统 PATH 上可能存在的低版本 JDK（避免 UnsupportedClassVersionError）。
function Ensure-Jdk([string]$target, [string]$projRoot) {
    $dest = Join-Path $target "jdk"
    if (Test-Path -LiteralPath (Join-Path $dest "bin\java.exe")) {
        Ok "已存在随包 JDK：$dest"
        return
    }
    $cands = @()
    $cands += (Get-ChildItem -Path (Join-Path $projRoot "softwares") -Filter "jdk-*.zip" -File -ErrorAction SilentlyContinue)
    $cands += (Get-ChildItem -Path (Join-Path $target "softwares") -Filter "jdk-*.zip" -File -ErrorAction SilentlyContinue)
    $jdkZip = $cands | Select-Object -First 1
    if (-not $jdkZip) {
        Warn "未找到随包 JDK 压缩包（softwares\jdk-*.zip），跳过释放；可稍后用 JAVA_HOME 指定 JDK 27。"
        return
    }
    $tSoft = Join-Path $target "softwares"
    if (-not (Test-Path -LiteralPath $tSoft)) { New-Item -ItemType Directory -Path $tSoft -Force | Out-Null }
    $tZip = Join-Path $tSoft $jdkZip.Name
    if (-not (Test-Path -LiteralPath $tZip)) { Copy-Item -LiteralPath $jdkZip.FullName -Destination $tZip -Force }

    $tmp = Join-Path $target "_jdk_extract_tmp"
    if (Test-Path -LiteralPath $tmp) {
        try { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction Stop } catch { }
    }
    New-Item -ItemType Directory -Path $tmp -Force | Out-Null
    Info "释放随包 JDK：$($jdkZip.Name)（解压中，请稍候）..."
    # 优先用 .NET ZipFile 解压（无进度条刷屏、更快）；失败再回退 Expand-Archive。
    $extracted = $false
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop
        [System.IO.Compression.ZipFile]::ExtractToDirectory($tZip, $tmp)
        $extracted = $true
    } catch {
        $extracted = $false
    }
    if (-not $extracted) {
        Expand-Archive -LiteralPath $tZip -DestinationPath $tmp -Force
    }

    # JDK 压缩包可能多一层包装目录：递归定位真实 home（含 bin\java.exe 的目录的父级）
    $hit = Get-ChildItem -LiteralPath $tmp -Recurse -Filter "java.exe" -File -ErrorAction SilentlyContinue |
        Where-Object { $_.DirectoryName -match "\\bin$" } | Select-Object -First 1
    if (-not $hit) { Warn "JDK 压缩包内未找到 bin\java.exe：$($jdkZip.Name)"; return }
    $realHome = Split-Path -Parent $hit.DirectoryName

    if (Test-Path -LiteralPath $dest) {
        # 罕见：dest 存在但无 bin\java.exe —— 先改名挪开（不删除），再落位
        $bak = "$dest.bak"
        if (Test-Path -LiteralPath $bak) { try { Remove-Item -LiteralPath $bak -Recurse -Force -ErrorAction Stop } catch { } }
        try { Move-Item -LiteralPath $dest -Destination $bak -Force } catch { }
    }
    Move-Item -LiteralPath $realHome -Destination $dest
    if (Test-Path -LiteralPath $tmp) {
        try { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction Stop } catch { }
    }
    if (Test-Path -LiteralPath (Join-Path $dest "bin\java.exe")) {
        Ok "已释放随包 JDK：$dest"
    } else {
        Warn "随包 JDK 释放结果异常，未找到 $dest\bin\java.exe"
    }
}

$targetExists = Test-Path -LiteralPath $Target

if (-not $targetExists) {
    # ================= 新建开发环境 =================
    Info "目标目录不存在 -> 新建开发环境"
    New-Item -ItemType Directory -Path $Target -Force | Out-Null
    # 释放本工程内容（根 pom + 模块 + 工具/说明）到目标目录，使其成为工作区根。
    Copy-Item -Path (Join-Path $root '*') -Destination $Target -Recurse -Force
    Ok "已新建开发环境：$Target"
    Ensure-Jdk $Target $root
    Info "工作区根 pom：$Target\pom.xml（<modules> 含 $module）"
    Info "用 IDEA 打开该目录即可（Reload Maven 工程）。"
}
elseif (Test-Workspace $Target) {
    # ================= 并入已有开发环境（与 Example 共用） =================
    Info "目标目录已是开发环境 -> 并入本应用模块（与已有模块共用）"
    $dstModule = Join-Path $Target $module
    if (Test-Path -LiteralPath $dstModule) {
        # 已存在：就地覆盖更新（不删除整目录，避免破坏性操作；同名文件覆盖，其余保留）。
        Info "目标已存在模块目录，覆盖更新：$module"
        New-Item -ItemType Directory -Path $dstModule -Force | Out-Null
        Copy-Item -Path (Join-Path $moduleDir '*') -Destination $dstModule -Recurse -Force
    }
    else {
        Copy-Item -LiteralPath $moduleDir -Destination $dstModule -Recurse -Force
    }
    Ok "已拷入模块：$dstModule"
    Register-Module $Target $module
    Ensure-Jdk $Target $root
    Info "用 IDEA 打开该开发环境根目录并 Reload Maven 工程即可看到新模块。"
}
else {
    # ================= 目标存在但不是开发环境 =================
    Warn "目标目录已存在，但不是开发环境（未找到聚合 pom.xml 与 <modules>）：$Target"
    Warn "请指定一个开发环境目录，或指定一个尚不存在的目录以新建开发环境。"
    throw "目标目录不可用作开发环境：$Target"
}

Write-Host ""
Write-Host "============================================================"
Write-Host "  [完成] 开发环境部署结束"
Write-Host "  后续：用 IDEA 打开工作区根目录 -> Reload Maven 工程 -> 运行模块的"
Write-Host "        start-env.cmd（建库灌数、拉起环境服务）与主类即可开发调试。"
Write-Host "============================================================"
