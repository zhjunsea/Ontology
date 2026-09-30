$src = @"
using System;
using System.Runtime.InteropServices;
public static class HC {
    [DllImport("ntdll.dll")]
    public static extern int NtQuerySystemInformation(int cls, IntPtr buf, int len, out int ret);
}
"@
Add-Type -TypeDefinition $src -Language CSharp
$len = 16777216
$buf = [System.Runtime.InteropServices.Marshal]::AllocHGlobal($len)
$ret = 0
$st = [HC]::NtQuerySystemInformation(64, $buf, $len, [ref]$ret)
if ($st -ne 0) { Write-Output ("STATUS=" + $st) } else {
    $count = [System.Runtime.InteropServices.Marshal]::ReadInt64($buf, 0)
    Write-Output ("HANDLE_COUNT=" + $count)
}
[System.Runtime.InteropServices.Marshal]::FreeHGlobal($buf)
Write-Output "COUNT_DONE"
