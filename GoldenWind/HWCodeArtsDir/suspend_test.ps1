$src = @"
using System;
using System.Runtime.InteropServices;
public static class Sus {
    [DllImport("ntdll.dll")] public static extern int NtSuspendProcess(IntPtr h);
    [DllImport("ntdll.dll")] public static extern int NtResumeProcess(IntPtr h);
    [DllImport("kernel32.dll", SetLastError=true)] public static extern IntPtr OpenProcess(int access, bool inherit, int pid);
    [DllImport("kernel32.dll")] public static extern bool CloseHandle(IntPtr h);
}
"@
Add-Type -TypeDefinition $src -Language CSharp

$targets = (Get-Process wps,wpp,et,winword -ErrorAction SilentlyContinue).Id
Write-Output ("TARGETS=" + ($targets -join ','))

$handles = @()
foreach ($procId in $targets) {
    $h = [Sus]::OpenProcess(0x0800, $false, $procId)   # PROCESS_SUSPEND_RESUME
    if ($h -ne [IntPtr]::Zero) {
        [void][Sus]::NtSuspendProcess($h)
        $handles += [pscustomobject]@{Pid=$procId; Handle=$h}
        Write-Output ("SUSPENDED: " + $procId)
    }
}
Start-Sleep -Milliseconds 500

try {
    Rename-Item 'D:\work\Ontology\GoldenWind\OntologyMachine\OntologyFramework' 'OntologyFrameworkTMSD' -ErrorAction Stop
    Write-Output "RENAME_OK_WITH_SUSPEND"
    Rename-Item 'D:\work\Ontology\GoldenWind\OntologyMachine\OntologyFrameworkTMSD' 'OntologyFramework' -ErrorAction Stop
    Write-Output "REVERTED"
} catch {
    Write-Output ("RENAME_FAIL: " + $_.Exception.Message)
}

foreach ($item in $handles) {
    [void][Sus]::NtResumeProcess($item.Handle)
    [void][Sus]::CloseHandle($item.Handle)
    Write-Output ("RESUMED: " + $item.Pid)
}
Write-Output "SUSPEND_TEST_DONE"
