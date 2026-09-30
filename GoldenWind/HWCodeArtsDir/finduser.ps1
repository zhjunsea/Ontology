param([int[]]$Pids, [string]$Filter = 'OntologyFramework')
$src = @"
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Collections.Generic;

public static class UF {
    [DllImport("ntdll.dll")]
    static extern int NtQuerySystemInformation(int cls, IntPtr buf, int len, out int ret);
    [DllImport("ntdll.dll")]
    static extern int NtQueryObject(IntPtr h, int cls, IntPtr buf, int len, out int ret);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern IntPtr OpenProcess(int access, bool inherit, int pid);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool DuplicateHandle(IntPtr sp, IntPtr sh, IntPtr dp, out IntPtr dh, int access, bool inherit, int options);
    [DllImport("kernel32.dll")]
    static extern IntPtr GetCurrentProcess();
    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr h);

    static string ReadUnicodeString(IntPtr ob) {
        short sl = Marshal.ReadInt16(ob, 0);
        if (sl <= 0) return "";
        long sptr = Marshal.ReadInt64(ob, 8);
        return Marshal.PtrToStringUni(new IntPtr(sptr), sl / 2);
    }

    static string QueryName(IntPtr dup) {
        string result = null;
        ManualResetEventSlim done = new ManualResetEventSlim(false);
        Thread t = new Thread(delegate() {
            try {
                IntPtr ob = Marshal.AllocHGlobal(4096);
                int r2;
                if (NtQueryObject(dup, 1, ob, 4096, out r2) == 0) result = ReadUnicodeString(ob);
                Marshal.FreeHGlobal(ob);
            } catch {}
            finally { done.Set(); }
        });
        t.IsBackground = true; t.Start();
        bool ok = done.Wait(40);
        return ok ? result : null;
    }

    public static List<string> Find(int[] pids, string filter) {
        List<string> res = new List<string>();
        int len = 16777216;
        IntPtr buf = Marshal.AllocHGlobal(len);
        int ret;
        int status = NtQuerySystemInformation(64, buf, len, out ret);
        if (status != 0) { Marshal.FreeHGlobal(buf); res.Add("query failed: " + status); return res; }
        long count = Marshal.ReadInt64(buf, 0);
        res.Add("TOTAL_HANDLES=" + count);
        IntPtr entry = new IntPtr(buf.ToInt64() + 16);
        int entrySize = 40;
        IntPtr self = GetCurrentProcess();
        HashSet<int> want = new HashSet<int>();
        foreach (int p in pids) want.Add(p);
        Dictionary<int,IntPtr> ph = new Dictionary<int,IntPtr>();
        for (long i = 0; i < count; i++) {
            IntPtr e = new IntPtr(entry.ToInt64() + i * entrySize);
            int pid = (int)Marshal.ReadInt64(e, 8);
            if (!want.Contains(pid)) continue;
            long handleVal = Marshal.ReadInt64(e, 16);
            IntPtr hp;
            if (ph.ContainsKey(pid)) hp = ph[pid];
            else { hp = OpenProcess(0x40, false, pid); ph[pid] = hp; }
            if (hp == IntPtr.Zero) continue;
            IntPtr dup;
            if (DuplicateHandle(hp, new IntPtr(handleVal), self, out dup, 0, false, 2)) {
                string s = QueryName(dup);
                if (s != null && s.IndexOf(filter, StringComparison.OrdinalIgnoreCase) >= 0)
                    res.Add(pid + "\t" + s);
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
$found = [UF]::Find($Pids, $Filter)
$found | ForEach-Object { $_ }
Write-Output "USER_SCAN_DONE"
