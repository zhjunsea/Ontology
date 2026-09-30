param([int[]]$Pids, [string]$Filter = 'OntologyFramework')
$src = @"
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Collections.Generic;

public static class HandleFinder2 {
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

    static string ReadUnicodeString(IntPtr ob) {
        short sl = Marshal.ReadInt16(ob, 0);
        if (sl <= 0) return "";
        long sptr = Marshal.ReadInt64(ob, 8);
        return Marshal.PtrToStringUni(new IntPtr(sptr), sl / 2);
    }

    public static List<string> Find(int[] pids, string filter) {
        List<string> res = new List<string>();
        int len = 1 << 24;
        IntPtr buf = Marshal.AllocHGlobal(len);
        int ret;
        int status = NtQuerySystemInformation(64, buf, len, out ret);
        if (status != 0) { Marshal.FreeHGlobal(buf); res.Add("query failed: " + status); return res; }
        long count = Marshal.ReadInt64(buf, 0);
        IntPtr entry = new IntPtr(buf.ToInt64() + 16);
        int entrySize = 40;
        IntPtr self = GetCurrentProcess();
        HashSet<int> want = new HashSet<int>();
        foreach (int p in pids) want.Add(p);
        for (long i = 0; i < count; i++) {
            IntPtr e = new IntPtr(entry.ToInt64() + i * entrySize);
            int pid = (int)Marshal.ReadInt64(e, 8);
            if (!want.Contains(pid)) continue;
            long handleVal = Marshal.ReadInt64(e, 16);
            IntPtr hp = OpenProcess(0x40, false, pid);
            if (hp == IntPtr.Zero) continue;
            IntPtr dup;
            if (DuplicateHandle(hp, new IntPtr(handleVal), self, out dup, 0, false, 2)) {
                IntPtr ob = Marshal.AllocHGlobal(4096);
                int r2;
                int st2 = NtQueryObject(dup, 2, ob, 4096, out r2); // ObjectTypeInformation
                if (st2 == 0) {
                    string type = ReadUnicodeString(ob);
                    if (type == "File") {
                        int st3 = NtQueryObject(dup, 1, ob, 4096, out r2); // ObjectNameInformation
                        if (st3 == 0) {
                            string s = ReadUnicodeString(ob);
                            if (s != null && s.IndexOf(filter, StringComparison.OrdinalIgnoreCase) >= 0)
                                res.Add(pid + "\t" + type + "\t" + s);
                        }
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
$found = [HandleFinder2]::Find($Pids, $Filter)
$found | ForEach-Object { $_ }
Write-Output "HANDLE_SCAN_DONE"
