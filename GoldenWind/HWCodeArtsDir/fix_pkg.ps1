$ErrorActionPreference = 'Stop'
$root = 'D:\work\Ontology\GoldenWind\OntologyMachine\OntologyFrameworkTCM\src'
$enc = New-Object System.Text.UTF8Encoding($false)
Get-ChildItem -Path $root -Recurse -Filter *.java | ForEach-Object {
    $f = $_.FullName
    $c = [System.IO.File]::ReadAllText($f)
    $o = $c
    $c = $c -replace 'com\.ocean\.ontologyframework\.tcm\.tcm', 'com.ocean.ontologyframework.tcm'
    if ($c -ne $o) { [System.IO.File]::WriteAllText($f, $c, $enc); Write-Output $f }
}
Write-Output "fix done"
