$ErrorActionPreference = 'Stop'
$root = 'D:\work\Ontology\GoldenWind\OntologyMachine\OntologyFrameworkTCM\src'
$enc = New-Object System.Text.UTF8Encoding($false)
$changed = @()
Get-ChildItem -Path $root -Recurse -Filter *.java | ForEach-Object {
    $f = $_.FullName
    $c = [System.IO.File]::ReadAllText($f)
    $o = $c
    $c = $c -replace 'package com\.ocean\.ontologyframework;', 'package com.ocean.ontologyframework.tcm;'
    $c = $c -replace 'com\.ocean\.ontologyframework\.(?!tcm\.)', 'com.ocean.ontologyframework.tcm.'
    if ($c -ne $o) {
        [System.IO.File]::WriteAllText($f, $c, $enc)
        $changed += $f
    }
}
$changed | ForEach-Object { Write-Output $_ }
Write-Output ("changed count = " + $changed.Count)
