# ============================================================================
#  经方方证 JUnit 测试套件 —— 一键串跑脚本
#
#  把 9 个测试类一次性跑完，并输出「哪些成功 / 哪些失败」的汇总：
#      com.ocean.ontologyframework.HerbRuleEngineTest   （方后注加减规则引擎，离线）
#      DuliFangzhengTest / HebingFangzhengTest / JianjiaFangzhengTest /
#      JueyinFangzhengTest / ShaoyangYangmingFangzhengTest /
#      ShaoyinTaiyinFangzhengTest / TaiyangFangzhengTest / ZabingFangzhengTest
#
#  用法（在 PowerShell 中）：
#      .\run_all_fangzheng_tests.ps1                 # 全部 9 个类，单 JVM 串跑
#      .\run_all_fangzheng_tests.ps1 -Fork           # 每个类独立 JVM（隔离，推荐排障时用）
#      .\run_all_fangzheng_tests.ps1 -SkipBuild      # 跳过编译，直接跑（改测试代码后请勿跳过）
#      .\run_all_fangzheng_tests.ps1 -Classes ZabingFangzhengTest,TaiyangFangzhengTest
#      .\run_all_fangzheng_tests.ps1 -Classes com.ocean.ontologyframework.HerbRuleEngineTest
#
#  退出码：0 = 全部通过；1 = 存在失败。
# ============================================================================

[CmdletBinding()]
param(
    [switch]$Fork,
    [switch]$Discover,
    [switch]$SkipBuild,
    [string[]]$Classes = @()
)

$ErrorActionPreference = 'Stop'

$root   = Split-Path -Parent $MyInvocation.MyCommand.Path          # ...\OntologyMachine
$module = Join-Path $root 'OntologyFramework'
$pom    = Join-Path $module 'pom.xml'
$rootPom= Join-Path $root 'pom.xml'
$cpFile = Join-Path $module 'target\test-classpath.txt'
$runner = 'com.ocean.ontologyframework.tcm.FangzhengSuiteRunner'

if (-not (Test-Path $pom)) { throw "找不到 $pom" }

# ---- 选择 maven 命令：优先 PATH 上的 mvn，否则用项目自带 mvnw -----------------
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $mvnw = Join-Path $root 'mvnw.cmd'
    if (Test-Path $mvnw) { $mvn = $mvnw } else { throw "未找到 mvn，也未找到 $mvnw" }
}

function Step($n, $text) { Write-Host "== [$n/4] $text ==" -ForegroundColor Cyan }

# ---- 1) 编译 ----------------------------------------------------------------
# 注意：必须从聚合工程带 -am 构建。本地仓库里的 OntopOBDAHandler / OpenlletResolver
#       jar 可能是旧的（缺 com.ocean.ontopobdahandler 包），单独编译本模块会报
#       “程序包 com.ocean.ontopobdahandler 不存在”。
Step 1 '编译主代码与测试代码（聚合工程 -am）'
if ($SkipBuild) {
    Write-Host '   （-SkipBuild：已跳过）' -ForegroundColor DarkGray
} else {
    & $mvn -q -f $rootPom -pl OntologyFramework -am test-compile
    if ($LASTEXITCODE -ne 0) { throw "编译失败（mvn test-compile 退出码 $LASTEXITCODE）" }
    Write-Host '   编译完成' -ForegroundColor Green
}

# ---- 2) 生成测试类路径 ------------------------------------------------------
Step 2 '生成测试类路径'
& $mvn -q -f $pom dependency:build-classpath "-Dmdep.includeScope=test" "-Dmdep.outputFile=$cpFile"
if ($LASTEXITCODE -ne 0) { throw "生成 classpath 失败（退出码 $LASTEXITCODE）" }

$deps = (Get-Content $cpFile -Raw).Trim()
if (-not $deps) { throw "classpath 文件为空：$cpFile" }

# 兄弟模块的 target/classes 前置，确保用的是刚编译出来的类而不是 m2 里的旧 jar
$siblings = @(
    (Join-Path $root 'OntopOBDAHandler\target\classes'),
    (Join-Path $root 'OpenlletResolver\target\classes')
) | Where-Object { Test-Path $_ }

$cp = (@(
    (Join-Path $module 'target\test-classes'),
    (Join-Path $module 'target\classes')
) + $siblings + @($deps)) -join ';'

# ---- 3) 运行套件 ------------------------------------------------------------
Step 3 '运行方证测试套件'
Write-Host "   模式：$(if ($Discover) { '只发现用例（不执行）' } elseif ($Fork) { '每类独立 JVM（隔离）' } else { '单 JVM 顺序串跑' })" -ForegroundColor DarkGray

# classpath 很长，直接拼命令行会超出 Windows 命令行长上限（约 32K），
# 故写入 Java 参数文件（@argfile）由 java 自行读取。
# 注意：argfile 中双引号内的反斜杠是转义符，故路径中的 \ 需写成 \\。
$argFile = Join-Path $module 'target\fangzheng-suite.args'
$cpArg   = [string]::Concat('"', $cp.Replace('\', '\\'), '"')

$lines = New-Object 'System.Collections.Generic.List[string]'
$lines.Add('-Dfile.encoding=UTF-8')
$lines.Add('-cp')
$lines.Add($cpArg)
$lines.Add($runner)
if ($Fork) { $lines.Add('--fork') }
if ($Discover) { $lines.Add('--discover') }
foreach ($c in $Classes) { $lines.Add($c) }

[System.IO.File]::WriteAllLines($argFile, $lines.ToArray(), (New-Object System.Text.UTF8Encoding($false)))

Push-Location $module
try {
    & java "@$argFile"
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

# ---- 4) 报告 ----------------------------------------------------------------
Step 4 '报告文件'
$txt  = Join-Path $module 'target\fangzheng-suite-report.txt'
$json = Join-Path $module 'target\fangzheng-suite-report.json'
if (Test-Path $txt)  { Write-Host "   $txt"  -ForegroundColor Green }
if (Test-Path $json) { Write-Host "   $json" -ForegroundColor Green }

if ($code -eq 0) {
    Write-Host "`n[通过] 全部通过（退出码 0）" -ForegroundColor Green
} else {
    Write-Host "`n[失败] 存在失败用例（退出码 $code），详见上方失败明细与报告文件" -ForegroundColor Red
}
exit $code
