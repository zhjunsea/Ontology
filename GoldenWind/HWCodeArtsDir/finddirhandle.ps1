$src = @"
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Collections.Generic;

public static class DirHandleFinder {
    [DllImport("ntdll.dll")]
    static extern int NtQuerySystemInformation(int cls, IntPtr buf, int len, out int ret);
    [DllImport("ntdll.dll")]
    static extern int NtQueryObject(IntPtr h, int cls, IntPtr buf, int len, out int ret);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern IntPtr OpenProcess(int access, bool inherit, int pid);
    [DllImport("kernel32.dll", SetLastError=true)]
    static extern bool DuplicateHandle(IntPtr sp, IntPtr sh, IntPtr dp, out IntPtr dh, int access, bool inherit, int options);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    static extern IntPtr CreateFileW(string name, uint access, uint share, IntPtr sec, uint disp, uint flags, IntPtr tmpl);
    [DllImport("kernel32.dll")]
    static extern IntPtr GetCurrentProcess();
    [DllImport("kernel32.dll")]
    static extern uint GetCurrentProcessId();
    [DllImport("kernel32.dll")]
    static extern bool CloseHandle(IntPtr h);

    static string ReadUnicodeString(IntPtr ob) {
        short sl = Marshal.ReadInt16(ob, 0);
        if (sl <= 0) return "";
        long sptr = Marshal.ReadInt64(ob, 8);
        return Marshal.PtrToStringUni(new IntPtr(sptr), sl / 2);
    }

    public static List<string> Find(string filter) {
        List<string> res = new List<string>();
        IntPtr dirH = CreateFileW("D:\\", 0x80000000, 0x7, IntPtr.Zero, 3, 0x02000000, IntPtr.Zero);
        long selfPid = GetCurrentProcessId();
        long selfHandle = dirH.ToInt64();
        int len = 1 << 25;
        IntPtr buf = Marshal.AllocHGlobal(len);
        int ret;
        int status = NtQuerySystemInformation(64, buf, len, out ret);
        if (status != 0) { Marshal.FreeHGlobal(buf); res.Add("query failed: " + status); return res; }
        long count = Marshal.ReadInt64(buf, 0);
        IntPtr entry = new IntPtr(buf.ToInt64() + 16);
        int entrySize = 40;
        // determine Directory ObjectTypeIndex by locating our own dir handle
        int dirIndex = -1;
        for (long i = 0; i < count; i++) {
            IntPtr e = new IntPtr(entry.ToInt64() + i * entrySize);
            if ((long)Marshal.ReadInt64(e, 8) == selfPid && Marshal.ReadInt64(e, 16) == selfHandle) {
                dirIndex = Marshal.ReadInt16(e, 30);
                break;
            }
        }
        res.Add("DIR_TYPE_INDEX=" + dirIndex);
        if (dirIndex < 0) { CloseHandle(dirH); Marshal.FreeHGlobal(buf); return res; }
        IntPtr self = GetCurrentProcess();
        Dictionary<int,IntPtr> ph = new Dictionary<int,IntPtr>();
        for (long i = 0; i < count; i++) {
            IntPtr e = new IntPtr(entry.ToInt64() + i * entrySize);
            int pid = (int)Marshal.ReadInt64(e, 8);
            if ((int)Marshal.ReadInt16(e, 30) != dirIndex) continue;
            long handleVal = Marshal.ReadInt64(e, 16);
            IntPtr hp;
            if (ph.ContainsKey(pid)) hp = ph[pid];
            else { hp = OpenProcess(0x40, false, pid); ph[pid] = hp; }
            if (hp == IntPtr.Zero) continue;
            IntPtr dup;
            if (DuplicateHandle(hp, new IntPtr(handleVal), self, out dup, 0, false, 2)) {
                IntPtr ob = Marshal.AllocHGlobal(4096);
                int r2;
                if (NtQueryObject(dup, 1, ob, 4096, out r2) == 0) {
                    string s = ReadUnicodeString(ob);
                    if (s != null && s.IndexOf(filter, StringComparison.OrdinalIgnoreCase) >= 0)
                        res.Add(pid + "\t" + s);
                }
                Marshal.FreeHGlobal(ob);
                CloseHandle(dup);
            }
        }
        foreach (var kv in ph) CloseHandle(kv.Value);
        CloseHandle(dirH);
        Marshal.FreeHGlobal(buf);
        return res;
    }
}
"@
Add-Type -TypeDefinition $src -Language CSharp
$found = [DirHandleFinder]::Find("OntologyFramework")
$found | ForEach-Object { $_ }
Write-Output "DIRHANDLE_SCAN_DONE"
