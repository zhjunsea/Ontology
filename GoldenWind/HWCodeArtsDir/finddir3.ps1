param([int[]]$Pids, [string]$Filter = 'OntologyFramework')
$src = @"
using System;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Collections.Generic;

public static class DF3 {
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

    public static List<string> Run(int[] pids, string filter, out string diag) {
        List<string> res = new List<string>();
        IntPtr dirH = CreateFileW("D:\\", 0x80000000, 0x7, IntPtr.Zero, 3, 0x02000000, IntPtr.Zero);
        diag = "dirH=" + dirH.ToInt64() + ";valid=" + (dirH != (IntPtr)(-1));
        int len = 16777216;
        IntPtr buf = Marshal.AllocHGlobal(len);
        int ret;
        int status = NtQuerySystemInformation(64, buf, len, out ret);
        if (status != 0) { Marshal.FreeHGlobal(buf); res.Add("query failed: " + status); return res; }
        long count = Marshal.ReadInt64(buf, 0);
        IntPtr entry = new IntPtr(buf.ToInt64() + 16);
        int entrySize = 40;
        long selfPid = GetCurrentProcessId();
        long selfHandle = dirH.ToInt64();
        int dirIndex = -1;
        for (long i = 0; i < count; i++) {
            IntPtr e = new IntPtr(entry.ToInt64() + i * entrySize);
            if (Marshal.ReadInt64(e, 8) == selfPid && Marshal.ReadInt64(e, 16) == selfHandle) {
                dirIndex = Marshal.ReadInt16(e, 30);
                break;
            }
        }
        diag += ";dirIndex=" + dirIndex;
        if (dirIndex < 0) { CloseHandle(dirH); Marshal.FreeHGlobal(buf); return res; }
        // verify: query our own dir handle name
        IntPtr ob0 = Marshal.AllocHGlobal(4096); int rr;
        if (NtQueryObject(dirH, 1, ob0, 4096, out rr) == 0) diag += ";selfName=" + ReadUnicodeString(ob0);
        Marshal.FreeHGlobal(ob0);
        if (diag.IndexOf("selfName=\\\\") < 0 && diag.IndexOf("selfName=D:") < 0) { CloseHandle(dirH); Marshal.FreeHGlobal(buf); diag += ";VERIFY_FAILED"; return res; }

        IntPtr self = GetCurrentProcess();
        HashSet<int> want = new HashSet<int>();
        foreach (int p in pids) want.Add(p);
        Dictionary<int,IntPtr> ph = new Dictionary<int,IntPtr>();
        long processed = 0;
        for (long i = 0; i < count; i++) {
            IntPtr e = new IntPtr(entry.ToInt64() + i * entrySize);
            if ((int)Marshal.ReadInt16(e, 30) != dirIndex) continue;
            processed++;
            if (processed > 20000) break;
            int pid = (int)Marshal.ReadInt64(e, 8);
            if (want.Count > 0 && !want.Contains(pid)) continue;
            long handleVal = Marshal.ReadInt64(e, 16);
            IntPtr hp;
            if (ph.ContainsKey(pid)) hp = ph[pid];
            else { hp = OpenProcess(0x40, false, pid); ph[pid] = hp; }
            if (hp == IntPtr.Zero) continue;
            IntPtr dup;
            if (DuplicateHandle(hp, new IntPtr(handleVal), self, out dup, 0, false, 2)) {
                string result = null;
                ManualResetEventSlim done = new ManualResetEventSlim(false);
                IntPtr captured = dup;
                Thread t = new Thread(delegate() {
                    try { IntPtr ob = Marshal.AllocHGlobal(4096); int r2; if (NtQueryObject(captured, 1, ob, 4096, out r2) == 0) result = ReadUnicodeString(ob); Marshal.FreeHGlobal(ob); }
                    catch {} finally { done.Set(); }
                });
                t.IsBackground = true; t.Start();
                if (done.Wait(50) && result != null && result.IndexOf(filter, StringComparison.OrdinalIgnoreCase) >= 0)
                    res.Add(pid + "\t" + result);
                CloseHandle(dup);
            }
        }
        diag += ";dirHandlesProcessed=" + processed;
        foreach (var kv in ph) CloseHandle(kv.Value);
        CloseHandle(dirH);
        Marshal.FreeHGlobal(buf);
        return res;
    }
}
"@
Add-Type -TypeDefinition $src -Language CSharp
$diag = ""
$found = [DF3]::Run($Pids, $Filter, [ref]$diag)
Write-Output ("DIAG: " + $diag)
$found | ForEach-Object { $_ }
Write-Output "DIR3_DONE"
