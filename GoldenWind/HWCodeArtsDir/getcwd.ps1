$src = @"
using System;
using System.Runtime.InteropServices;
using System.Text;

public static class CwdReader {
    [DllImport("ntdll.dll")]
    static extern int NtQueryInformationProcess(IntPtr h, int cls, byte[] info, int len, out int ret);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern IntPtr OpenProcess(int access, bool inherit, int pid);
    [DllImport("kernel32.dll")]
    static extern bool ReadProcessMemory(IntPtr h, IntPtr addr, byte[] buf, int size, out IntPtr read);
    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr h);

    public static string GetCwd(int pid) {
        IntPtr h = OpenProcess(0x0410, false, pid); // QUERY_INFORMATION | VM_READ
        if (h == IntPtr.Zero) return null;
        try {
            byte[] pbi = new byte[48];
            int ret;
            if (NtQueryInformationProcess(h, 0, pbi, 48, out ret) != 0) return null;
            long peb = BitConverter.ToInt64(pbi, 8);
            if (peb == 0) return null;
            byte[] pp = new byte[8];
            IntPtr read;
            if (!ReadProcessMemory(h, new IntPtr(peb + 0x20), pp, 8, out read)) return null;
            long procParams = BitConverter.ToInt64(pp, 0);
            if (procParams == 0) return null;
            byte[] us = new byte[16];
            if (!ReadProcessMemory(h, new IntPtr(procParams + 0x38), us, 16, out read)) return null;
            ushort len = BitConverter.ToUInt16(us, 0);
            long buf = BitConverter.ToInt64(us, 8);
            if (len == 0 || buf == 0) return "";
            byte[] str = new byte[len];
            if (!ReadProcessMemory(h, new IntPtr(buf), str, len, out read)) return null;
            return Encoding.Unicode.GetString(str);
        } finally { CloseHandle(h); }
    }
}
"@
Add-Type -TypeDefinition $src -Language CSharp

Get-Process | ForEach-Object {
    $cwd = $null
    try { $cwd = [CwdReader]::GetCwd($_.Id) } catch {}
    if ($cwd -and ($cwd -like '*OntologyFramework*')) {
        "{0}`t{1}`t{2}" -f $_.Id, $_.ProcessName, $cwd
    }
}
Write-Output "SCAN_DONE"
