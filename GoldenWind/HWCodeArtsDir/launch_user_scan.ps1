$pids = (Get-Process wps,wpp,et,explorer,idea64,winword -ErrorAction SilentlyContinue).Id
Write-Output ("CANDIDATE_PIDS=" + ($pids -join ','))
& "D:\work\Ontology\GoldenWind\HWCodeArtsDir\finduser.ps1" -Pids $pids -Filter "OntologyFramework"
