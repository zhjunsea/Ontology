# -*- coding: utf-8 -*-
"""幂等地把附录静态块与方法写入 PlumBlossomOntologyJobWorker.java。"""
F = 'src/main/java/com/ocean/ontologyframework/plumblossom/PlumBlossomOntologyJobWorker.java'
s = open(F, encoding='utf-8').read()

if 'APPENDIX_TEXT' in s:
    print('already present')
    raise SystemExit

puts = open('_gen/out/appendix_java.txt', encoding='utf-8').read().rstrip('\n')

block = '''    // ------------------------------------------------------------------
    // 附录·渔樵对问 / 宋史·邵雍传（附录文献；第 26 轮补录）
    //   依《本体顶层设计》原著范围（附录三篇＝一撮金·渔樵对问·宋史·邵雍传）；
    //   一撮金爻诀见 YICUOJIN_YAO，此二篇为 DB appendix_text 内置同源兜底（OBDA 优先）。
    // ------------------------------------------------------------------
    /** 键＝段名，值＝[篇名, 段主题, 原文]。 */
    private static final Map<String, String[]> APPENDIX_TEXT = new LinkedHashMap<>();
    private static void putAt(String name, String work, String topic, String text) {
        APPENDIX_TEXT.put(name, new String[]{work, topic, text});
    }
    static {
''' + puts + '''
    }

    /** 附录·渔樵对问/宋史·邵雍传 义理提要（接入节点 16 汇总；OBDA 优先、内置兜底）。 */
    public static String appendixDoctrineNote() {
        int yq = 0, sy = 0;
        for (String[] v : APPENDIX_TEXT.values()) {
            if ("渔樵对问".equals(v[0])) { yq++; } else if ("宋史·邵雍传".equals(v[0])) { sy++; }
        }
        return "附录·渔樵对问（" + yq + "则：体用相息·以物观物·太极两仪四象八卦·权变性命）"
             + "；附录·宋史·邵雍传（" + sy + "段：邵雍生平学术——受学李之才河图洛书、衍先天之旨、安乐窝、内圣外王）"
             + "——依《本体顶层设计》附录三篇（一撮金已录，余二篇第 26 轮补录）。";
    }

    /** 取某附录篇提要（work 含「渔樵」或「邵雍」时命中，否则空）。 */
    public static String appendixTextNote(String work) {
        if (work == null) { return ""; }
        String w = work.contains("渔樵") ? "渔樵对问" : (work.contains("邵雍") ? "宋史·邵雍传" : null);
        if (w == null) { return ""; }
        int n = 0;
        for (String[] v : APPENDIX_TEXT.values()) {
            if (w.equals(v[0])) { n++; }
        }
        return "；附录·" + w + "（全录 " + n + " 段）";
    }

    /** 附录文献段数（自检用）。 */
    private static int appendixTextCount() { return APPENDIX_TEXT.size(); }

'''

anchor = '    /** 相字断法：类目数、条目总数（自检用）。 */'
assert anchor in s, 'anchor not found'
s = s.replace(anchor, block + anchor, 1)
open(F, 'w', encoding='utf-8').write(s)
print('inserted APPENDIX_TEXT block')
