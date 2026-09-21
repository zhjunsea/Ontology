# -*- coding: utf-8 -*-
"""
index.html 结果区改版：
  1) 「候选方证」不再可点击填入输入框 —— 改为纯列表，显示「方证名 · 主证缺口 · 主证命中 · 或然症」
  2) 候选方证提前并显眼展示
  3) 有直接命中时：直接命中方证（显眼）→ 候选方证 → 概览及以下
  4) 无直接命中时：概览 → 候选方证 → 补充症状（路A）→ 或然症候选（路B）→ 其余
CRLF 感知；块级锚点替换，带唯一性校验。
"""
import io
import os
import shutil
import sys

SRC = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\src\main\resources\static\index.html"
DST = r"D:\work\Ontology\TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox\OntologyMachine\OntologyFramework\target\classes\static\index.html"
BAK = SRC + ".bak-before-candui-20260921"

CRLF = "\r\n"


def crlf(text):
    """把 LF 文本转成 CRLF 文本（幂等）。"""
    return text.replace("\r\n", "\n").replace("\n", CRLF)


def must_replace(doc, old, new, label):
    n = doc.count(old)
    if n != 1:
        print("[FAIL] %s: 锚点出现 %d 次（应为 1）" % (label, n))
        sys.exit(1)
    print("[OK] %s: 锚点唯一，替换 %d 字节 → %d 字节" % (label, len(old.encode("utf-8")), len(new.encode("utf-8"))))
    return doc.replace(old, new)


def main():
    with io.open(SRC, "r", encoding="utf-8", newline="") as f:
        doc = f.read()

    if "candbox" in doc:
        print("[SKIP] 已打过补丁（检测到 candbox）")
        return

    # ---------- 1) CSS ----------
    css_old = crlf("  .cand button{padding:4px 10px;font-size:12.5px;border-radius:999px}\n")
    css_new = crlf(
        "  .cand button{padding:4px 10px;font-size:12.5px;border-radius:999px}\n"
        "  /* 直接命中方证 / 候选方证：显眼展示块 */\n"
        "  .hitbox{background:var(--accent-soft);border:1px solid #ddd4ff;border-radius:10px;\n"
        "        padding:14px 16px;margin-top:14px}\n"
        "  .hitbox-k{font-size:12px;color:var(--accent);font-weight:600;letter-spacing:.5px}\n"
        "  .hitbox-v{font-size:22px;font-weight:700;color:var(--accent);margin-top:4px}\n"
        "  .hitbox-m{font-size:12.5px;color:var(--ink-2);margin-top:6px}\n"
        "  .candbox{background:#fafbfc;border:1px solid var(--line);border-left:3px solid var(--brand);\n"
        "        border-radius:0 10px 10px 0;padding:12px 16px;margin-top:14px}\n"
        "  .candbox-k{font-size:13px;font-weight:600;color:var(--ink)}\n"
        "  .candbox-m{font-size:12px;color:var(--ink-2);margin-top:2px}\n"
        "  .candlist{margin:8px 0 0;padding-left:22px}\n"
        "  .candlist li{margin:4px 0;font-size:14px}\n"
        "  .candname{font-weight:600}\n"
        "  .candmeta{font-size:12px;color:var(--ink-2);margin-left:8px}\n"
    )
    doc = must_replace(doc, css_old, css_new, "CSS 新增展示块样式")

    # ---------- 2) 辅助函数 ----------
    helper_old = crlf("// ---------------- 结果 ----------------\n")
    helper_new = crlf(
        "// ---------------- 直接命中 / 候选方证 展示块 ----------------\n"
        "/**\n"
        " * 解析后端 candidateScores 中的打分串。\n"
        " * 形如 \"Guizhijiashaoyaotangzheng(hits=2/4, gap=1, poss=0/0, prio=3)\"。\n"
        " */\n"
        "function parseScore(s){\n"
        "  const t = String(s==null?'':s);\n"
        "  const g = /gap=(\\d+)/.exec(t);\n"
        "  const h = /hits=(\\d+)\\/(\\d+)/.exec(t);\n"
        "  const p = /poss=(\\d+)\\/(\\d+)/.exec(t);\n"
        "  return {\n"
        "    gap:       g ? Number(g[1]) : null,\n"
        "    hits:      h ? Number(h[1]) : null,\n"
        "    required:  h ? Number(h[2]) : null,\n"
        "    possHits:  p ? Number(p[1]) : null,\n"
        "    possTotal: p ? Number(p[2]) : null\n"
        "  };\n"
        "}\n"
        "\n"
        "/** 直接命中方证：显眼卡片（accent 高亮）。 */\n"
        "function hitFangzhengBlock(d){\n"
        "  const w = document.createElement('div');\n"
        "  w.className = 'hitbox';\n"
        "  const meta = [];\n"
        "  if(d.finalFormulaCn) meta.push('推荐方剂：' + d.finalFormulaCn);\n"
        "  if(d.baseFormulaCn)  meta.push('母方：' + d.baseFormulaCn);\n"
        "  w.innerHTML = '<div class=\"hitbox-k\">✓ 直接命中方证</div>'\n"
        "              + '<div class=\"hitbox-v\">' + esc(d.fangzhengCn || '未定') + '</div>'\n"
        "              + (meta.length ? '<div class=\"hitbox-m\">' + meta.map(esc).join(' · ') + '</div>' : '');\n"
        "  return w;\n"
        "}\n"
        "\n"
        "/**\n"
        " * 候选方证：显眼卡片，纯列表展示（不可点击填入输入框）。\n"
        " * 每项显示「方证名 · 主证缺口 N · 主证命中 H/R · 或然症 P/T」。\n"
        " */\n"
        "function candidateBlock(d){\n"
        "  const cands = d.candidateFangzhengsCn || d.fangzhengCandidatesCn || d.fangzhengCandidates || [];\n"
        "  if(!cands.length) return null;\n"
        "  const scores = d.candidateScores || [];\n"
        "  const w = document.createElement('div');\n"
        "  w.className = 'candbox';\n"
        "  let h = '<div class=\"candbox-k\">候选方证</div>'\n"
        "        + '<div class=\"candbox-m\">按主证缺口升序排列，仅供参考，非确定结论。</div>'\n"
        "        + '<ol class=\"candlist\">';\n"
        "  cands.forEach((x,i)=>{\n"
        "    const sc = parseScore(scores[i]);\n"
        "    const bits = [];\n"
        "    if(sc.gap != null) bits.push('主证缺口 ' + sc.gap);\n"
        "    if(sc.hits != null && sc.required != null) bits.push('主证命中 ' + sc.hits + '/' + sc.required);\n"
        "    if(sc.possTotal) bits.push('或然症 ' + sc.possHits + '/' + sc.possTotal);\n"
        "    h += '<li><span class=\"candname\">' + esc(x) + '</span>'\n"
        "       + (bits.length ? '<span class=\"candmeta\">' + esc(bits.join(' · ')) + '</span>' : '')\n"
        "       + '</li>';\n"
        "  });\n"
        "  h += '</ol>';\n"
        "  w.innerHTML = h;\n"
        "  return w;\n"
        "}\n"
        "\n"
        "// ---------------- 结果 ----------------\n"
    )
    doc = must_replace(doc, helper_old, helper_new, "新增 parseScore / hitFangzhengBlock / candidateBlock")

    # ---------- 3) renderResult 重排 ----------
    body_old = crlf(
        "  // 概览\n"
        "  const grid = document.createElement('div'); grid.className='grid';\n"
        "  grid.appendChild(kv('六经', d.sixChannelCn || '未定'));\n"
        "  grid.appendChild(kv('方证', d.fangzhengCn || '未定'));\n"
        "  grid.appendChild(kv('推荐方剂', d.finalFormulaCn || '未定', outcome==='success'));\n"
        "  grid.appendChild(kv('母方', d.baseFormulaCn || '—'));\n"
        "  box.appendChild(grid);\n"
        "\n"
        "  // 双路径（无确定结论时）\n"
        "  if(outcome === 'noresult') renderPaths(box, d);\n"
        "\n"
        "  // 候选方证（仅有候选 / 或后端暴露了候选列表时）\n"
        "  // 后端实际字段为 candidateFangzhengsCn（中文名）/ fangzhengCandidates（IRI 片段），\n"
        "  // 此处按优先级取用，避免回退到英文片段。\n"
        "  const cands = d.candidateFangzhengsCn || d.fangzhengCandidatesCn || d.fangzhengCandidates || [];\n"
        "  if(cands.length){\n"
        "    const c = document.createElement('div');\n"
        "    c.innerHTML = '<div class=\"muted\" style=\"margin-top:16px\">候选方证（点击即填入输入框）</div>';\n"
        "    const cd = document.createElement('div'); cd.className='cand';\n"
        "    const scores = d.candidateScores || [];\n"
        "    cands.forEach((x,i)=>{\n"
        "      const s = document.createElement('span');\n"
        "      s.className = 'chip plain clickable';\n"
        "      s.setAttribute('data-sym', x);          // 点击后追加到「输入症状」框\n"
        "      s.title = '点击填入输入框';\n"
        "      const sc = scores[i];\n"
        "      s.textContent = x + (sc!=null && !isNaN(Number(sc)) ? ' (' + Math.round(Number(sc)*100) + '%)' : '');\n"
        "      cd.appendChild(s);\n"
        "    });\n"
        "    c.appendChild(cd); box.appendChild(c);\n"
        "  }\n"
    )
    body_new = crlf(
        "  // ---- 概览（六经 / 方证 / 推荐方剂 / 母方）----\n"
        "  const overview = () => {\n"
        "    const grid = document.createElement('div'); grid.className='grid';\n"
        "    grid.appendChild(kv('六经', d.sixChannelCn || '未定'));\n"
        "    grid.appendChild(kv('方证', d.fangzhengCn || '未定'));\n"
        "    grid.appendChild(kv('推荐方剂', d.finalFormulaCn || '未定', outcome==='success'));\n"
        "    grid.appendChild(kv('母方', d.baseFormulaCn || '—'));\n"
        "    return grid;\n"
        "  };\n"
        "\n"
        "  // ---- 候选方证（显眼列表，不可点击）----\n"
        "  const candBlock = candidateBlock(d);\n"
        "\n"
        "  if(outcome === 'success'){\n"
        "    // 有直接命中：直接命中方证（显眼）→ 候选方证（显眼）→ 概览及以下\n"
        "    box.appendChild(hitFangzhengBlock(d));\n"
        "    if(candBlock) box.appendChild(candBlock);\n"
        "    box.appendChild(overview());\n"
        "  }else{\n"
        "    // 无直接命中：概览 → 候选方证 → 补充症状（路A）→ 或然症候选（路B）→ 其余\n"
        "    box.appendChild(overview());\n"
        "    if(candBlock) box.appendChild(candBlock);\n"
        "    if(outcome === 'noresult') renderPaths(box, d);\n"
        "  }\n"
    )
    doc = must_replace(doc, body_old, body_new, "renderResult 结果区重排")

    # ---------- 备份 + 落盘 ----------
    if not os.path.exists(BAK):
        shutil.copy2(SRC, BAK)
        print("[OK] 备份 -> %s" % BAK)

    with io.open(SRC, "w", encoding="utf-8", newline="") as f:
        f.write(doc)
    print("[OK] 写入 %s" % SRC)

    if os.path.exists(os.path.dirname(DST)):
        shutil.copy2(SRC, DST)
        print("[OK] 同步 -> %s" % DST)
    else:
        print("[WARN] target/classes/static 不存在，跳过同步")

    # ---------- 校验 ----------
    with io.open(SRC, "r", encoding="utf-8", newline="") as f:
        chk = f.read()
    print("--- 校验 ---")
    print("candbox 出现次数:", chk.count("candbox"))
    print("hitbox 出现次数:", chk.count("hitbox"))
    print("candidateBlock 调用:", chk.count("candidateBlock(d)"))
    print("残留『点击即填入输入框』:", chk.count("点击即填入输入框"))
    print("残留 data-sym 绑定(候选方证块):", chk.count("s.setAttribute('data-sym', x)"))
    print("CRLF:", chk.count("\r\n"), " 裸LF:", chk.count("\n") - chk.count("\r\n"))


if __name__ == "__main__":
    main()
