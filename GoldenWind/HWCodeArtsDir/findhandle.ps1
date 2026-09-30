param([string]$Filter = 'OntologyFramework')
$src = @"
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Collections.Generic;

public static class HandleFinder {
    [DllImport("ntdll.dll")]
    static extern int NtQuerySystemInformation(int cls, IntPtr buf, int len, out int ret);
    [DllImport("ntdll.dll")]
    static extern int NtQueryObject(IntPtr h, int cls, IntPtr buf, int len, out int ret);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern IntPtr OpenProcess(int access, bool inherit, int pid);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool DuplicateHandle(IntPtr srcProc, IntPtr srcHandle, IntPtr dstProc, out IntPtr dstHandle, int access, bool inherit, int options);
    [DllImport("kernel32.dll")]
    static extern IntPtr GetCurrentProcess();
    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr h);

    public static List<string> Find(int[] pids, string filter) {
        List<string> res = new List<string>();
        int len = 1 << 24;
        IntPtr buf = Marshal.AllocHGlobal(len);
        int ret;
        int status = NtQuerySystemInformation(64, buf, len, out ret);
        if (status != 0) { Marshal.FreeHGlobal(buf); res.Add("NtQuerySystemInformation failed: " + status); return res; }
        long count = Marshal.ReadInt64(buf, 0);
        IntPtr entry = new IntPtr(buf.ToInt64() + 16);
        int entrySize = 40;
        IntPtr self = GetCurrentProcess();
        for (long i = 0; i < count; i++) {
            IntPtr e = new IntPtr(entry.ToInt64() + i * entrySize);
            long pid = Marshal.ReadInt64(e, 8);
            long handleVal = Marshal.ReadInt64(e, 16);
            bool want = false;
            if (pids == null || pids.Length == 0) want = true;
            else foreach (int p in pids) if (p == (int)pid) want = true;
            if (!want) continue;
            IntPtr hp = OpenProcess(0x40, false, (int)pid); // PROCESS_DUP_HANDLE
            if (hp == IntPtr.Zero) continue;
            IntPtr dup;
            if (DuplicateHandle(hp, new IntPtr(handleVal), self, out dup, 0, false, 2)) {
                IntPtr ob = Marshal.AllocHGlobal(4096);
                int r2;
                int st2 = NtQueryObject(dup, 1, ob, 4096, out r2);
                if (st2 == 0) {
                    short sl = Marshal.ReadInt16(ob, 0);
                    if (sl > 0) {
                        long sptr = Marshal.ReadInt64(ob, 8);
                        string s = Marshal.PtrToStringUni(new IntPtr(sptr), sl / 2);
                        if (s != null && s.IndexOf(filter, StringComparison.OrdinalIgnoreCase) >= 0)
                            res.Add(pid + "\t" + handleVal + "\t" + s);
                    }
                }
                Marshal.FreeHGlobal(ob);
                CloseHandle(dup);
            }
            CloseHandle(hp);
        }
        Marshal.FreeHGlobal(buf);
        return res;
    }
}
"@
Add-Type -TypeDefinition $src -Language CSharp
$pids = (Get-Process | ForEach-Object { $_.Id })
$found = [HandleFinder]::Find($pids, $Filter)
$found | ForEach-Object { $_ }
Write-Output "HANDLE_SCAN_DONE"
