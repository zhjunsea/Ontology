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
param(
    [Parameter(Mandatory = $false)][string]$Target,
    # 可选：显式指定目标 IDEA 的 Maven 本地仓库；缺省时自动探测（workspace.xml / ~\.m2\settings.xml / ~\.m2\repository）
    [Parameter(Mandatory = $false)][string]$Repo,
    # 可选：显式指定安装信息文档（installer 生成的 install-info.json）；缺省时自动在目标目录及其父级查找
    [Parameter(Mandatory = $false)][string]$InstallInfo
)

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
    # 必须用「无 BOM 的 UTF-8」写回：Windows PowerShell 5.1 的 Set-Content -Encoding UTF8 会写入
    # UTF-8 BOM，而带 BOM 的 pom.xml 会让 Maven/IDEA 解析失败（报 "only whitespace content allowed
    # before start tag"），进而使工程不被识别为 Maven/Java 工程。故改用 .NET 的无 BOM 编码写回。
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($p, $t, $utf8NoBom)
    Ok "已在目标根 pom 的 <modules> 中登记：$mod"
}

# --- 把本工程携带的 IDEA 运行配置释放到目标工作区根 .idea\runConfigurations\（幂等） --------
#     开发工程 zip 的工作区根 .idea\runConfigurations\ 内含本应用的两份运行配置：
#       · <App>Application.xml   —— 应用主类（Spring Boot，承载 JobWorker）
#       · All<Xxx>SuiteTest.xml  —— 总 JUnit 测试入口
#     无论「新建」还是「并入」开发环境，都要把它们放进目标工作区根，IDEA 打开后才有一键运行项
#     （IDEA 只识别工程根下的 .idea；模块内的 .idea 对聚合工程无效）。
function Install-RunConfigurations([string]$srcRoot, [string]$dstRoot) {
    $srcRc = Join-Path $srcRoot ".idea\runConfigurations"
    if (-not (Test-Path -LiteralPath $srcRc)) {
        Info "未发现可释放的 IDEA 运行配置（$srcRc），跳过"
        return
    }
    $dstRc = Join-Path $dstRoot ".idea\runConfigurations"
    New-Item -ItemType Directory -Path $dstRc -Force | Out-Null
    $configs = Get-ChildItem -LiteralPath $srcRc -Filter "*.xml" -File
    if (-not $configs) {
        Info "运行配置目录为空，跳过"
        return
    }
    foreach ($c in $configs) {
        Copy-Item -LiteralPath $c.FullName -Destination $dstRc -Force
    }
    Ok "已生成 IDEA 运行配置（$(($configs | ForEach-Object { $_.Name }) -join ', ')）-> $dstRc"
}

# --- 补齐目标工作区根 .idea 的工程配置骨架（misc.xml / modules.xml / encodings.xml）-------------
#     使 IDEA 打开工作区根时直接识别为 Maven/Java 工程并自动导入（无需手工 Add as Maven Project）。
#     关键：misc.xml 的 MavenProjectsManager.originalFiles 指向根 pom.xml。仅补目标缺失的文件，
#     绝不覆盖已有文件（并入已有开发环境时保留其原有 IDEA 工程配置）。
function Save-IdeaXml([System.Xml.XmlDocument]$doc, [string]$path) {
    # 无 BOM 的 UTF-8 写回（带 BOM 的 .idea xml 虽不致命，但保持干净）
    $settings = New-Object System.Xml.XmlWriterSettings
    $settings.Encoding = New-Object System.Text.UTF8Encoding($false)
    $settings.Indent = $true
    $writer = [System.Xml.XmlWriter]::Create($path, $settings)
    try { $doc.Save($writer) } finally { $writer.Close() }
}

function Merge-IdeaProjectFiles([string]$srcFile, [string]$dstFile) {
    # 把源 .idea 文件里「目标缺失的 component / file 条目」合并进目标文件；
    # 目标已有内容一律保留、绝不覆盖、绝不删除。返回新增项列表。
    $dstDoc = New-Object System.Xml.XmlDocument
    $dstDoc.Load($dstFile)
    $srcDoc = New-Object System.Xml.XmlDocument
    $srcDoc.Load($srcFile)
    $added = @()
    $root = $dstDoc.DocumentElement
    foreach ($sc in $srcDoc.SelectNodes("/project/component")) {
        $name = $sc.GetAttribute("name")
        $dc = $dstDoc.SelectSingleNode("/project/component[@name='$name']")
        if ($null -eq $dc) {
            [void]$root.AppendChild($dstDoc.ImportNode($sc, $true))
            $added += $name
            continue
        }
        if ($name -eq "MavenProjectsManager") {
            $dstList = $dc.SelectSingleNode("option[@name='originalFiles']/list")
            if ($null -eq $dstList) {
                $srcOpt = $sc.SelectSingleNode("option[@name='originalFiles']")
                if ($null -ne $srcOpt) { [void]$dc.AppendChild($dstDoc.ImportNode($srcOpt, $true)); $added += "MavenProjectsManager.originalFiles" }
            }
            else {
                # 确保「根 pom」已登记到 originalFiles（IDEA 依据它加载 Maven 模型）
                $hasRoot = $false
                foreach ($o in $dstList.SelectNodes("option")) {
                    $rel = ($o.GetAttribute("value") -replace '^\$PROJECT_DIR\$', '' -replace '^[\\/]+', '' -replace '[\\/]+$', '') -replace '\\', '/'
                    if ($rel -ieq "pom.xml") { $hasRoot = $true; break }
                }
                if (-not $hasRoot) {
                    foreach ($o in $sc.SelectNodes("option[@name='originalFiles']/list/option")) {
                        [void]$dstList.AppendChild($dstDoc.ImportNode($o, $true)); $added += ("originalFiles:" + $o.GetAttribute("value"))
                    }
                }
            }
            # 关键修复：清除目标里对本应用 pom 的忽略项。
            #   IDEA 会把「新接入但未纳入 Maven 模型」的 pom 写进 ignoredFiles；被忽略的 Maven
            #   项目不会被导入，表现为「工程不被识别为 Maven/Java 工程」。并入已有工程时必须清掉。
            $ignOpt = $dc.SelectSingleNode("option[@name='ignoredFiles']")
            if ($null -eq $ignOpt) { $ignOpt = $dc.SelectSingleNode("option[@name='ignoredFilesList']") }
            if ($null -ne $ignOpt) {
                foreach ($o in @($ignOpt.SelectNodes(".//option"))) {
                    $v = $o.GetAttribute("value")
                    $rel = ($v -replace '^\$PROJECT_DIR\$', '' -replace '^[\\/]+', '' -replace '[\\/]+$', '') -replace '\\', '/'
                    if (($rel -ieq "pom.xml") -or ($rel -ieq "$module/pom.xml")) {
                        [void]$o.ParentNode.RemoveChild($o)
                        $added += ("unignore:" + $v)
                    }
                }
            }
        }
        elseif ($name -eq "Encoding") {
            if (($dc.GetAttribute("defaultCharsetForPropertiesFiles") -eq "") -and ($sc.GetAttribute("defaultCharsetForPropertiesFiles") -ne "")) {
                $dc.SetAttribute("defaultCharsetForPropertiesFiles", $sc.GetAttribute("defaultCharsetForPropertiesFiles"))
            }
            $existingUrls = @{}
            foreach ($df in $dc.SelectNodes("file")) { $existingUrls[$df.GetAttribute("url")] = $true }
            foreach ($sf in $sc.SelectNodes("file")) {
                $u = $sf.GetAttribute("url")
                if (-not $existingUrls.ContainsKey($u)) {
                    [void]$dc.AppendChild($dstDoc.ImportNode($sf, $true))
                    $added += ("encoding:" + $u)
                }
            }
        }
    }
    if ($added.Count -gt 0) { Save-IdeaXml $dstDoc $dstFile }
    return $added
}

function Ensure-IdeaProjectFiles([string]$srcRoot, [string]$dstRoot) {
    # 将本应用随包的 .idea 工程配置「并入」目标工作区根 .idea：
    #   · 文件不存在        -> 直接拷贝（缺才拷贝）；
    #   · 文件已存在        -> 文件内合并，仅补目标缺失的 component/file 条目，保留原有内容；
    #   · 绝不覆盖整个 .idea 目录、绝不删除目标已有配置（并入已有开发环境时安全）。
    $srcIdea = Join-Path $srcRoot ".idea"
    if (-not (Test-Path -LiteralPath $srcIdea)) {
        Warn "未发现 IDEA 工程配置源目录（$srcIdea），跳过"
        return
    }
    $dstIdea = Join-Path $dstRoot ".idea"
    New-Item -ItemType Directory -Path $dstIdea -Force | Out-Null
    $allAdded = @()
    foreach ($f in @("misc.xml", "modules.xml", "encodings.xml")) {
        $s = Join-Path $srcIdea $f
        if (-not (Test-Path -LiteralPath $s)) { continue }
        $d = Join-Path $dstIdea $f
        if (-not (Test-Path -LiteralPath $d)) {
            Copy-Item -LiteralPath $s -Destination $d -Force
            $allAdded += ("$f(新增)")
        }
        elseif (($f -eq "misc.xml") -or ($f -eq "encodings.xml")) {
            $r = Merge-IdeaProjectFiles $s $d
            if ($r.Count -gt 0) { $allAdded += ("$f(合并: " + ($r -join ', ') + ")") }
        }
        else {
            # modules.xml：Maven 模块本就不写入 modules 清单，目标已存在则保留，不覆盖
        }
    }
    if ($allAdded.Count -gt 0) {
        Ok ("已并入 IDEA 工程配置（保留原 .idea，仅补缺失项）：" + ($allAdded -join '; '))
    }
    else {
        Info "目标 .idea 已含所需工程配置，无需补充（保留原有内容）"
    }
}

# --- 探测「该开发环境 IDEA 实际使用的 Maven 本地仓库」 ------------------------------------
#     优先级：① 目标工作区 .idea\workspace.xml 的 MavenImportPreferences.localRepository（绝对路径、非宏）；
#             ② 用户级 ~\.m2\settings.xml 的 <localRepository>；
#             ③ 兜底 IDEA 默认 ~\.m2\repository（日志会提示，可用 -Repo 显式覆盖）。
function Resolve-IdeaMavenRepo([string]$targetDir, [string]$override) {
    if (-not [string]::IsNullOrWhiteSpace($override)) {
        return [System.IO.Path]::GetFullPath($override)
    }
    $ws = Join-Path $targetDir ".idea\workspace.xml"
    if (Test-Path -LiteralPath $ws) {
        $x = Get-Content -LiteralPath $ws -Raw
        $m = [regex]::Match($x, '<option\s+name="localRepository"\s+value="([^"]+)"')
        if ($m.Success) {
            $v = $m.Groups[1].Value.Trim()
            if ($v -and ($v -notmatch '\$[A-Za-z_]') -and [System.IO.Path]::IsPathRooted($v)) { return $v }
        }
    }
    $set = Join-Path $env:USERPROFILE ".m2\settings.xml"
    if (Test-Path -LiteralPath $set) {
        $x = Get-Content -LiteralPath $set -Raw
        $m = [regex]::Match($x, '<localRepository>\s*([^<]+?)\s*</localRepository>')
        if ($m.Success) {
            $v = $m.Groups[1].Value.Trim()
            if ($v) { return [System.IO.Path]::GetFullPath($v) }
        }
    }
    return (Join-Path $env:USERPROFILE ".m2\repository")
}

# --- 把随包 Maven 依赖仓库合并进「该开发环境 IDEA 实际使用的仓库」（而非盲目写缺省 ~\.m2）-----
function Install-MavenRepo([string]$srcRoot, [string]$targetDir, [string]$repoOverride) {
    $srcRepo = Join-Path $srcRoot "maven-repo"
    if (-not (Test-Path -LiteralPath $srcRepo)) {
        Info "本包未携带 Maven 依赖仓库（$srcRepo），跳过"
        return
    }
    $dstRepo = Resolve-IdeaMavenRepo $targetDir $repoOverride
    Info "该开发环境 IDEA 使用的 Maven 仓库：$dstRepo"
    New-Item -ItemType Directory -Path $dstRepo -Force | Out-Null
    # robocopy 合并：/E 含子目录，/XO 不覆盖较新的目标文件，/NFL /NDL /NJH /NJS 精简输出
    $rcArgs = @("`"$srcRepo`"", "`"$dstRepo`"", "/E", "/XO", "/NFL", "/NDL", "/NJH", "/NJS", "/R:1", "/W:1")
    $rc = (Start-Process -FilePath "robocopy" -ArgumentList $rcArgs -Wait -PassThru -NoNewWindow).ExitCode
    # robocopy：0-7 为成功/有复制，>=8 为失败
    if ($rc -ge 8) {
        Warn "合并 Maven 依赖仓库失败（robocopy 返回 $rc）；可稍后手动把 $srcRepo 拷入 $dstRepo"
    }
    else {
        Ok "已把随包 Maven 依赖合并进该 IDEA 仓库：$dstRepo"
    }
}

# --- 探测目标机 / 目标开发环境实际安装位置，写回应用 application.yaml 的 *-home ----------------
#     打包阶段已把 application.yaml 的 *-home 清空（不外泄打包机路径）。部署时按以下顺序探测：
#       ① 安装信息文档 install-info.json（安装器在安装目录/工作目录各放一份，见 Find-InstallInfo；
#          仅当该路径在本机真实存在时采用——异机失效则自动回落）
#       ② 目标开发环境根下的 softwares\（若目标是 dist 解出的工作区且自带软件目录）
#       ③ 环境变量（JAVA_HOME / CAMUNDA_HOME / ONTOP_HOME / RABBITMQ_HOME / ERLANG_HOME）
#       ④ 打包机默认值（tools/home-defaults.json，仅当该路径在本机真实存在时采用——同机部署场景）
#     仅写回探测到的项；打包机有配置（说明应用真实需要）但目标机探测不到的，给出明确提示。
$homeDefaults = @{}
$homeDefaultsFile = Join-Path $here "home-defaults.json"
if (Test-Path -LiteralPath $homeDefaultsFile) {
    try {
        (Get-Content -LiteralPath $homeDefaultsFile -Raw | ConvertFrom-Json).PSObject.Properties |
            ForEach-Object { $homeDefaults[$_.Name] = [string]$_.Value }
    }
    catch { Warn "解析 home-defaults.json 失败：$($_.Exception.Message)" }
}

# --- 读取「安装信息」文档（installer 生成的 install-info.json）-----------------------------------
#     优先：-InstallInfo 参数 > 环境变量 ONTOLOGY_INSTALL_INFO > 目标目录根 > 目标目录各级父目录（最多 4 级）。
#     取其中 envPrepareHomes（java-home / ontop-home / camunda-home / rabbitmq-home / erlang-home）
#     作为回填 application.yaml 的【首选】来源（仅当该路径在本机真实存在时采用，异机失效则自动回落）。
$installHomes = @{}
function Find-InstallInfo([string]$target, [string]$explicit) {
    if ($explicit -and (Test-Path -LiteralPath $explicit)) { return (Resolve-Path -LiteralPath $explicit).Path }
    foreach ($scope in @('Process', 'User', 'Machine')) {
        $ev = [Environment]::GetEnvironmentVariable('ONTOLOGY_INSTALL_INFO', $scope)
        if ($ev -and (Test-Path -LiteralPath $ev)) { return (Resolve-Path -LiteralPath $ev).Path }
    }
    $cur = $target
    for ($i = 0; $i -lt 4 -and $cur; $i++) {
        $p = Join-Path $cur "install-info.json"
        if (Test-Path -LiteralPath $p) { return (Resolve-Path -LiteralPath $p).Path }
        $cur = Split-Path -Parent $cur
    }
    return $null
}
$installInfoPath = Find-InstallInfo $Target $InstallInfo
if ($installInfoPath) {
    try {
        $j = Get-Content -LiteralPath $installInfoPath -Raw | ConvertFrom-Json
        if ($j.envPrepareHomes) {
            $j.envPrepareHomes.PSObject.Properties | ForEach-Object { $installHomes[$_.Name] = [string]$_.Value }
        }
        Info "已读取安装信息文档：$installInfoPath"
    }
    catch { Warn "解析安装信息文档失败：$($_.Exception.Message)" }
}
else {
    Info "未找到安装信息文档（install-info.json），回落到 softwares\ / 环境变量 / home-defaults 探测。"
}
function Find-HomeInSoftwares([string]$targetDir, [string[]]$patterns) {
    $sw = Join-Path $targetDir "softwares"
    if (-not (Test-Path -LiteralPath $sw)) { return $null }
    foreach ($pat in $patterns) {
        $hit = Get-ChildItem -LiteralPath $sw -Directory -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -like $pat } | Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

function Resolve-Home([string]$targetDir, [string[]]$patterns, [string]$envName, [string]$key) {
    # ① 安装信息文档（installer 的 install-info.json）：最精准，仅当该路径在本机真实存在时采用
    if ($key -and $installHomes.ContainsKey($key)) {
        $h0 = $installHomes[$key]
        if ($h0 -and (Test-Path -LiteralPath $h0)) { return $h0 }
    }
    # ② 目标开发环境根下的 softwares\
    $h = Find-HomeInSoftwares -targetDir $targetDir -patterns $patterns
    if ($h) { return $h }
    # ③ 环境变量
    if ($envName) {
        foreach ($scope in @('Process', 'User', 'Machine')) {
            $v = [Environment]::GetEnvironmentVariable($envName, $scope)
            if ($v -and (Test-Path -LiteralPath $v)) { return $v }
        }
    }
    if ($key -and $homeDefaults.ContainsKey($key)) {
        $d = $homeDefaults[$key]
        if ($d -and (Test-Path -LiteralPath $d)) { return $d }
    }
    return $null
}

function Set-YamlHome([string]$yamlPath, [string]$key, [string]$val) {
    if ([string]::IsNullOrWhiteSpace($val)) { return $false }
    $t = Get-Content -LiteralPath $yamlPath -Raw
    $pattern = '(?m)^([ \t]*)' + [regex]::Escape($key) + ':[ \t].*$'
    if (-not [regex]::IsMatch($t, $pattern)) { return $false }
    $repl = '$1' + $key + ": '" + $val + "'"
    $t = [regex]::Replace($t, $pattern, $repl, 1)
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($yamlPath, $t, $utf8NoBom)
    return $true
}

function Update-AppYamlHomes([string]$moduleDir, [string]$targetDir) {
    $yaml = Join-Path $moduleDir "src\main\resources\application.yaml"
    if (-not (Test-Path -LiteralPath $yaml)) { $yaml = Join-Path $moduleDir "application.yaml" }
    if (-not (Test-Path -LiteralPath $yaml)) {
        Warn "未找到 application.yaml（$moduleDir），跳过环境路径写回"
        return
    }
    $map = [ordered]@{
        'java-home'     = Resolve-Home -targetDir $targetDir -patterns @('jdk*') -envName 'JAVA_HOME' -key 'java-home'
        'camunda-home'  = Resolve-Home -targetDir $targetDir -patterns @('c8run*', 'camunda*') -envName 'CAMUNDA_HOME' -key 'camunda-home'
        'ontop-home'    = Resolve-Home -targetDir $targetDir -patterns @('ontop-cli*', 'ontop*') -envName 'ONTOP_HOME' -key 'ontop-home'
        'rabbitmq-home' = Resolve-Home -targetDir $targetDir -patterns @('rabbitmq_server*', 'rabbitmq*') -envName 'RABBITMQ_HOME' -key 'rabbitmq-home'
        'erlang-home'   = Resolve-Home -targetDir $targetDir -patterns @('otp_win64*', 'erlang*', 'erl*') -envName 'ERLANG_HOME' -key 'erlang-home'
    }
    $written = @()
    foreach ($k in $map.Keys) {
        if ($map[$k] -and (Set-YamlHome -yamlPath $yaml -key $k -val $map[$k])) {
            $written += "$k=$($map[$k])"
        }
    }
    if ($written.Count -gt 0) {
        Ok ("已按目标环境探测并写回 application.yaml：" + ($written -join '; '))
    }
    $neededMissing = @($homeDefaults.Keys | Where-Object { $map.Keys -contains $_ -and -not $map[$_] })
    if ($neededMissing.Count -gt 0) {
        Warn ("以下 *-home 目标机上未探测到安装位置（该应用打包时有配置，说明确实需要），application.yaml 中保持为空：" + ($neededMissing -join ', '))
        Warn "请设置对应环境变量（JAVA_HOME / CAMUNDA_HOME / ONTOP_HOME / RABBITMQ_HOME / ERLANG_HOME），"
        Warn "或把对应软件放入目标目录的 softwares\ 后重新执行部署；否则 EnvPrepare 启动时会因该 home 为空而报『缺少配置』。"
    }
    if ($written.Count -eq 0 -and $neededMissing.Count -eq 0) {
        Info "未探测到需要写回的软件安装位置（application.yaml 的 *-home 保持为空，交 EnvPrepare 自动探测/环境变量解析）"
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
    Info "用 IDEA 打开该开发环境根目录并 Reload Maven 工程即可看到新模块。"
}
else {
    # ================= 目标存在但不是开发环境 =================
    Warn "目标目录已存在，但不是开发环境（未找到聚合 pom.xml 与 <modules>）：$Target"
    Warn "请指定一个开发环境目录，或指定一个尚不存在的目录以新建开发环境。"
    throw "目标目录不可用作开发环境：$Target"
}

# --- 无论新建还是并入，都把本应用的 IDEA 运行配置放到目标工作区根（新建分支其实已随整体复制带上，
#     此处再显式释放一次，保证并入已有开发环境时也有；幂等覆盖）。 ------------------------------
Install-RunConfigurations $root $Target

# --- 补齐目标工作区根 .idea 的工程配置骨架（misc.xml/modules.xml/encodings.xml），
#     使 IDEA 打开即识别为 Maven/Java 工程并自动导入（幂等，仅补缺失文件） ----------
Ensure-IdeaProjectFiles $root $Target

# --- 把随包 Maven 依赖仓库合并进该开发环境 IDEA 实际使用的仓库（幂等） ----------------------
Install-MavenRepo $root $Target $Repo

# --- 把 application.yaml 的 *-home 按目标机/目标环境实际安装位置写回（打包期已清空，避免打包机路径外泄） ---
Update-AppYamlHomes (Join-Path $Target $module) $Target

Write-Host ""
Write-Host "============================================================"
Write-Host "  [完成] 开发环境部署结束"
Write-Host "  后续：用 IDEA 打开工作区根目录即可（工程已带 .idea 配置，会自动识别为 Maven 工程）："
Write-Host "        · IDEA 未打开该工程 -> 直接打开工作区根，自动按 Maven 工程导入，无需手工 Add as Maven Project。"
Write-Host "        · IDEA 已开着旧工程 -> File > Reload All from Disk，再点右侧 Maven 面板的"
Write-Host "          『Reload All Maven Projects』（或关闭工程后重新打开）；否则看不到刚并入的新模块。"
Write-Host "        然后运行模块的 start-env.cmd（建库灌数、拉起环境服务）与主类即可开发调试。"
Write-Host "============================================================"
