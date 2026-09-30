param([int[]]$Pids, [string]$Filter = 'OntologyFramework')
$src = @"
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Collections.Generic;

public static class HandleFinder4 {
    [DllImport("ntdll.dll")]
    static extern int NtQuerySystemInformation(int cls, IntPtr buf, int len, out int ret);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern IntPtr OpenProcess(int access, bool inherit, int pid);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool DuplicateHandle(IntPtr srcProc, IntPtr srcHandle, IntPtr dstProc, out IntPtr dstHandle, int access, bool inherit, int options);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    static extern uint GetFinalPathNameByHandleW(IntPtr hFile, StringBuilder buf, uint cch, uint flags);
    [DllImport("kernel32.dll")]
    static extern IntPtr GetCurrentProcess();
    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr h);

    public static List<string> Find(int[] pids, string filter) {
        List<string> res = new List<string>();
        int len = 1 << 25;
        IntPtr buf = Marshal.AllocHGlobal(len);
        int ret;
        int status = NtQuerySystemInformation(64, buf, len, out ret);
        if (status != 0) { Marshal.FreeHGlobal(buf); res.Add("query failed: " + status); return res; }
        long count = Marshal.ReadInt64(buf, 0);
        IntPtr entry = new IntPtr(buf.ToInt64() + 16);
        int entrySize = 40;
        IntPtr self = GetCurrentProcess();
        Dictionary<int,IntPtr> ph = new Dictionary<int,IntPtr>();
        HashSet<int> want = new HashSet<int>();
        foreach (int p in pids) want.Add(p);
        for (long i = 0; i < count; i++) {
            IntPtr e = new IntPtr(entry.ToInt64() + i * entrySize);
            int pid = (int)Marshal.ReadInt64(e, 8);
            if (want.Count > 0 && !want.Contains(pid)) continue;
            long handleVal = Marshal.ReadInt64(e, 16);
            IntPtr hp;
            if (ph.ContainsKey(pid)) hp = ph[pid];
            else { hp = OpenProcess(0x40, false, pid); ph[pid] = hp; }
            if (hp == IntPtr.Zero) continue;
            IntPtr dup;
            if (DuplicateHandle(hp, new IntPtr(handleVal), self, out dup, 0, false, 2)) {
                StringBuilder sb = new StringBuilder(2048);
                uint r = GetFinalPathNameByHandleW(dup, sb, (uint)sb.Capacity, 0);
                if (r > 0) {
                    string s = sb.ToString();
                    if (s.IndexOf(filter, StringComparison.OrdinalIgnoreCase) >= 0)
                        res.Add(pid + "\t" + s);
                }
                CloseHandle(dup);
            }
        }
        foreach (var kv in ph) CloseHandle(kv.Value);
        Marshal.FreeHGlobal(buf);
        return res;
    }
}
"@
Add-Type -TypeDefinition $src -Language CSharp
$found = [HandleFinder4]::Find($Pids, $Filter)
$found | ForEach-Object { $_ }
Write-Output "HANDLE_SCAN_DONE"
