# -*- coding: utf-8 -*-
"""生成塔架中段定制化设计 BPMN 流程工作台 UI（index.html）。
将 .bpmn 内容内联嵌入，保证 file:// 直接打开也能渲染。
"""
import io
import os

BASE = os.path.dirname(os.path.abspath(__file__))
BPMN_PATH = os.path.join(BASE, "塔架中段定制化设计流程.bpmn")
OUT_PATH = os.path.join(BASE, "index.html")

with io.open(BPMN_PATH, "r", encoding="utf-8") as f:
    bpmn_xml = f.read()

HTML = r'''<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>塔架中段定制化设计 · BPMN 流程工作台</title>
<link rel="stylesheet" href="vendor/diagram-js.css">
<link rel="stylesheet" href="vendor/bpmn-js.css">
<link rel="stylesheet" href="vendor/bpmn-font.css">
<style>
  :root{
    --bg:#eef2f7; --panel:#ffffff; --panel-2:#f8fafc;
    --line:#dfe6ee; --line-2:#eaeff5;
    --tx:#1b2430; --tx-2:#5a6675; --tx-3:#8b97a6;
    --blue:#2563eb; --blue-bg:#eff5ff;
    --amber:#d97706; --amber-bg:#fff8ec;
    --green:#059669; --green-bg:#ecfdf5;
    --violet:#4f46e5; --violet-bg:#eef2ff;
    --red:#dc2626;
    --radius:10px;
    --shadow:0 1px 2px rgba(16,24,40,.05), 0 4px 14px rgba(16,24,40,.06);
  }
  *{box-sizing:border-box;}
  html,body{margin:0;padding:0;height:100%;}
  body{
    font-family:"Microsoft YaHei","PingFang SC","Hiragino Sans GB","Segoe UI",system-ui,sans-serif;
    background:var(--bg); color:var(--tx); font-size:13px; line-height:1.6;
    display:flex; flex-direction:column; height:100vh; overflow:hidden;
  }

  /* ---------- header ---------- */
  header{
    flex:0 0 auto; display:flex; align-items:center; gap:14px;
    padding:12px 20px; background:var(--panel); border-bottom:1px solid var(--line);
    box-shadow:0 1px 3px rgba(16,24,40,.04); z-index:5;
  }
  header .logo{
    width:34px;height:34px;border-radius:9px;flex:0 0 auto;
    background:linear-gradient(135deg,#2563eb,#4f46e5);
    display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700;font-size:15px;
  }
  header h1{margin:0;font-size:15.5px;font-weight:700;letter-spacing:.2px;}
  header .sub{font-size:11.5px;color:var(--tx-3);margin-top:1px;}
  header .spacer{flex:1;}
  header .tag{
    font-size:11.5px;padding:3px 10px;border-radius:20px;
    background:var(--blue-bg);color:var(--blue);border:1px solid #d6e4ff;font-weight:600;
  }
  header .tag.gray{background:var(--panel-2);color:var(--tx-2);border-color:var(--line);}

  /* ---------- layout ---------- */
  main{flex:1 1 auto;display:flex;min-height:0;gap:12px;padding:12px;}
  .canvas-wrap{
    flex:1 1 auto;min-width:0;display:flex;flex-direction:column;
    background:var(--panel);border:1px solid var(--line);border-radius:var(--radius);
    box-shadow:var(--shadow);overflow:hidden;
  }
  .canvas-bar{
    flex:0 0 auto;display:flex;align-items:center;gap:8px;
    padding:8px 12px;border-bottom:1px solid var(--line-2);background:var(--panel-2);
  }
  .canvas-bar .title{font-weight:700;font-size:12.5px;}
  .canvas-bar .spacer{flex:1;}
  .legend{display:flex;gap:12px;align-items:center;font-size:11.5px;color:var(--tx-2);}
  .legend i{display:inline-block;width:10px;height:10px;border-radius:3px;margin-right:4px;vertical-align:-1px;}
  #canvas{flex:1 1 auto;min-height:0;position:relative;}
  #canvas .djs-container{background:
     radial-gradient(circle at 1px 1px, #dfe6ee 1px, transparent 0) 0 0/18px 18px, #fbfcfe;}

  /* ---------- panel ---------- */
  aside{
    flex:0 0 400px;width:400px;display:flex;flex-direction:column;gap:12px;
    overflow-y:auto;overflow-x:hidden;padding-right:2px;
  }
  .card{
    background:var(--panel);border:1px solid var(--line);border-radius:var(--radius);
    box-shadow:var(--shadow);overflow:hidden;flex:0 0 auto;
  }
  .card > h2{
    margin:0;padding:10px 14px;font-size:12.5px;font-weight:700;
    border-bottom:1px solid var(--line-2);background:var(--panel-2);
    display:flex;align-items:center;gap:7px;
  }
  .card > h2 .n{
    width:18px;height:18px;border-radius:5px;background:var(--blue);color:#fff;
    font-size:11px;display:flex;align-items:center;justify-content:center;font-weight:700;
  }
  .card .body{padding:12px 14px;}

  /* inputs */
  .grid2{display:grid;grid-template-columns:1fr 1fr;gap:9px 10px;}
  .field{display:flex;flex-direction:column;gap:4px;}
  .field label{font-size:11.5px;color:var(--tx-2);font-weight:600;}
  .field input,.field select{
    width:100%;padding:6px 8px;border:1px solid var(--line);border-radius:7px;
    font-size:12.5px;color:var(--tx);background:#fff;outline:none;font-family:inherit;
  }
  .field input:focus,.field select:focus{border-color:var(--blue);box-shadow:0 0 0 3px rgba(37,99,235,.10);}
  .field .hint{font-size:10.5px;color:var(--tx-3);}

  /* hit level radio */
  .hitlist{display:flex;flex-direction:column;gap:6px;}
  .hitopt{
    display:flex;align-items:center;gap:9px;padding:8px 10px;border:1px solid var(--line);
    border-radius:8px;cursor:pointer;background:#fff;transition:.15s;
  }
  .hitopt:hover{border-color:#c7d5e8;background:var(--panel-2);}
  .hitopt input{accent-color:var(--blue);width:14px;height:14px;flex:0 0 auto;}
  .hitopt .lbl{font-weight:600;font-size:12.5px;}
  .hitopt .chain{font-size:11px;color:var(--tx-3);margin-left:auto;text-align:right;}
  .hitopt.on{border-color:var(--blue);background:var(--blue-bg);box-shadow:0 0 0 2px rgba(37,99,235,.08);}

  /* chips */
  .chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:2px;}
  .chip{
    font-size:11.5px;padding:3px 9px;border-radius:20px;border:1px solid var(--line);
    background:var(--panel-2);color:var(--tx-3);font-weight:600;
  }
  .chip.hit{background:var(--amber-bg);border-color:#f5d9a8;color:var(--amber);}
  .chip.reuse{background:var(--green-bg);border-color:#b8e6d2;color:var(--green);}
  .chip.miss{background:#fdf2f2;border-color:#f6cfcf;color:var(--red);}

  /* result */
  .result-head{
    display:flex;align-items:center;gap:10px;padding:11px 12px;border-radius:9px;
    background:linear-gradient(135deg,#eff5ff,#f5f3ff);border:1px solid #dbe6fb;
  }
  .result-head .big{font-size:15px;font-weight:800;color:var(--blue);}
  .result-head .small{font-size:11.5px;color:var(--tx-2);}
  .flowline{display:flex;flex-wrap:wrap;align-items:center;gap:6px;margin-top:10px;}
  .step-pill{
    font-size:11.5px;font-weight:700;padding:4px 10px;border-radius:7px;
    background:var(--green-bg);border:1px solid #b8e6d2;color:var(--green);white-space:nowrap;
  }
  .step-pill.none{background:var(--panel-2);border-color:var(--line);color:var(--tx-3);}
  .arrow{color:var(--tx-3);font-size:12px;}
  .kv{display:flex;gap:8px;font-size:11.5px;color:var(--tx-2);margin-top:9px;}
  .kv b{color:var(--tx);}

  /* step detail */
  .stepcard{border:1px solid var(--line);border-radius:9px;overflow:hidden;margin-bottom:9px;}
  .stepcard:last-child{margin-bottom:0;}
  .stepcard .hd{
    display:flex;align-items:center;gap:8px;padding:8px 11px;background:var(--panel-2);
    border-bottom:1px solid var(--line-2);
  }
  .stepcard .hd .no{
    font-size:11px;font-weight:800;color:#fff;background:var(--green);
    padding:2px 7px;border-radius:5px;
  }
  .stepcard .hd .nm{font-weight:700;font-size:12.5px;}
  .stepcard .hd .tag{margin-left:auto;font-size:10.5px;color:var(--tx-3);}
  .stepcard ul{margin:0;padding:9px 11px 9px 26px;}
  .stepcard li{font-size:11.8px;color:var(--tx-2);margin:3px 0;}
  .stepcard li b{color:var(--tx);font-weight:700;}
  .stepcard .code{
    font-size:11px;color:#475569;background:#f6f8fb;border-top:1px dashed var(--line);
    padding:7px 11px;font-family:Consolas,"Courier New",monospace;word-break:break-all;
  }
  .stepcard .code b{color:var(--blue);}

  /* doc viewer */
  #docbox{
    font-size:11.8px;color:var(--tx-2);white-space:pre-wrap;line-height:1.7;
    max-height:230px;overflow:auto;
  }
  #docbox .dt{font-weight:700;color:var(--tx);font-size:12.5px;display:block;margin-bottom:5px;}
  #docbox .empty{color:var(--tx-3);font-style:italic;}

  /* footer table */
  footer{
    flex:0 0 auto;background:var(--panel);border-top:1px solid var(--line);
    padding:0;max-height:0;overflow:hidden;transition:max-height .25s ease;
  }
  footer.open{max-height:280px;overflow:auto;}
  .footbar{
    display:flex;align-items:center;gap:10px;padding:7px 20px;font-size:12px;
    color:var(--tx-2);cursor:pointer;user-select:none;
  }
  .footbar:hover{background:var(--panel-2);}
  .footbar .caret{transition:.2s;}
  footer.open .footbar .caret{transform:rotate(90deg);}
  table.cons{width:100%;border-collapse:collapse;font-size:11.8px;}
  table.cons th,table.cons td{border:1px solid var(--line-2);padding:6px 10px;text-align:left;vertical-align:top;}
  table.cons th{background:var(--panel-2);font-weight:700;color:var(--tx);white-space:nowrap;}
  table.cons td b{color:var(--blue);}
  table.cons td code{font-family:Consolas,monospace;font-size:11px;color:#475569;}

  /* bpmn highlight */
  .djs-element.wb-hit .djs-visual > :nth-child(1){stroke:#d97706 !important;stroke-width:2.6px !important;fill:#fff8ec !important;}
  .djs-element.wb-task .djs-visual > :nth-child(1){stroke:#059669 !important;stroke-width:2.6px !important;fill:#ecfdf5 !important;}
  .djs-element.wb-end .djs-visual > :nth-child(1){stroke:#4f46e5 !important;stroke-width:3px !important;fill:#eef2ff !important;}
  .djs-element.wb-dim{opacity:.30;}
  .djs-element.wb-hit .djs-label,.djs-element.wb-task .djs-label,.djs-element.wb-end .djs-label{font-weight:700;}

  ::-webkit-scrollbar{width:9px;height:9px;}
  ::-webkit-scrollbar-thumb{background:#cbd5e1;border-radius:6px;border:2px solid transparent;background-clip:content-box;}
  ::-webkit-scrollbar-thumb:hover{background:#94a3b8;background-clip:content-box;}
  ::-webkit-scrollbar-track{background:transparent;}
</style>
</head>
<body>

<header>
  <div class="logo">塔</div>
  <div>
    <h1>塔架中段定制化设计 · BPMN 流程工作台</h1>
    <div class="sub">依据《塔架中段设计流程》9 步流程与老塔架设计程序 towerdesign 的硬编码逻辑构建</div>
  </div>
  <div class="spacer"></div>
  <span class="tag">BPMN 2.0</span>
  <span class="tag gray">从后往前匹配</span>
</header>

<main>
  <section class="canvas-wrap">
    <div class="canvas-bar">
      <span class="title">流程图</span>
      <span class="legend">
        <span><i style="background:#d97706"></i>判定网关</span>
        <span><i style="background:#059669"></i>需执行环节</span>
        <span><i style="background:#4f46e5"></i>结束</span>
      </span>
      <span class="spacer"></span>
      <button class="btn" id="btn-fit">适应窗口</button>
      <button class="btn" id="btn-reset">清除高亮</button>
      <button class="btn" id="btn-dl">导出 BPMN</button>
    </div>
    <div id="canvas"></div>
  </section>

  <aside>
    <div class="card">
      <h2><span class="n">1</span>设计输入</h2>
      <div class="body">
        <div class="grid2">
          <div class="field"><label>塔筒段数</label><input id="in-sec" value="6"></div>
          <div class="field"><label>机型</label>
            <select id="in-model">
              <option>GWH182-7.5</option><option>GWH191-6.25</option>
              <option>GWH204-6.25</option><option>GWH221-8.0</option>
            </select>
          </div>
          <div class="field"><label>升降机配置</label>
            <select id="in-lift"><option>钢绳导向</option><option>爬梯导向</option></select>
          </div>
          <div class="field"><label>区域</label>
            <select id="in-region"><option>中国</option><option>欧洲</option><option>北美</option><option>亚太</option></select>
          </div>
          <div class="field"><label>中段筒高 (mm)</label><input id="in-h" value="24000"></div>
          <div class="field"><label>平台到筒顶 (mm)</label><input id="in-plat" value="1250"></div>
        </div>
        <div class="field" style="margin-top:9px;">
          <label>附件连接方式</label>
          <select id="in-conn"><option>焊接式</option><option>粘贴式</option></select>
          <div class="hint">指爬梯支撑与电缆托架如何安装在塔筒上，两种方式选用不同附件</div>
        </div>
      </div>
    </div>

    <div class="card">
      <h2><span class="n">2</span>命中层级判定</h2>
      <div class="body">
        <div class="hitlist" id="hitlist"></div>
        <div class="chips" id="chips" style="margin-top:10px;"></div>
      </div>
    </div>

    <div class="card">
      <h2><span class="n">3</span>匹配结果</h2>
      <div class="body">
        <div class="result-head">
          <div class="big" id="res-level">—</div>
          <div class="small" id="res-desc"></div>
        </div>
        <div class="flowline" id="res-flow"></div>
        <div class="kv" id="res-kv"></div>
      </div>
    </div>

    <div class="card">
      <h2><span class="n">4</span>环节详情</h2>
      <div class="body" id="step-detail"></div>
    </div>

    <div class="card">
      <h2><span class="n">5</span>节点说明</h2>
      <div class="body"><div id="docbox"><span class="empty">点击左侧流程图中的任意节点，查看其设计说明与对应老程序实现。</span></div></div>
    </div>
  </aside>
</main>

<footer id="footer">
  <div class="footbar" id="footbar"><span class="caret">▶</span> 设计约束速查表（步骤 4 ~ 步骤 9）</div>
  <div style="padding:0 20px 14px;">
    <table class="cons">
      <tr><th>环节</th><th>关键约束</th><th>老程序实现</th></tr>
      <tr><td><b>步骤4 平台设计</b></td><td>平台到筒顶距离 <b>1250mm</b></td><td><code>H_platform = stan_layGeo(2, n)</code>；<code>platSugget()</code> 内径 ±6mm 匹配</td></tr>
      <tr><td><b>步骤5 筒节设计</b></td>
        <td>附件（爬梯支撑+电缆托架，平齐）与环焊缝上下距离 <b>&gt;100mm</b>；相邻附件中心间距 <b>1680~1960mm</b>（6×280~7×280）；第一个附件到底部 <b>980mm</b>；倒数第二个附件到平台 <b>1400~1960mm</b>；最后电缆托架距平台 <b>200mm</b>；灯安装高度上方空间 <b>2.6~3m</b>；灯上螺柱间距 <b>5~10m</b>、螺柱间距 <b>500mm</b>；灯螺柱与焊缝上下 <b>&gt;100mm</b></td>
        <td><code>ls_heights()</code> 增量 <code>[1960,1680,1400]</code>，平台下 840mm 停止，焊缝避让 200mm；<code>lasupport2w()</code> ≥125mm；<code>lampLay()</code> 灯避让焊缝</td></tr>
      <tr><td><b>步骤6 高度设计</b></td><td>更换与筒高一致的梯子（爬梯）与线槽</td><td><code>L_LADDER = L_C = 段长</code>；<code>ladderSuggest()/cableSuggest()</code> 按母线长选型</td></tr>
      <tr><td><b>步骤7 直径设计</b></td><td>电缆托架中心距同侧爬梯支撑的弦长满足映射关系</td><td><code>L1_CABLE_I / L2_CABLE_I</code>：V12/V15=800/800，V17=650/650，V19=650/650</td></tr>
      <tr><td><b>步骤8 附件类型替换</b></td><td>按附件连接方式（粘贴式 / 焊接式）替换适配的附件类型</td><td><code>select_accessory()</code> 汇总段数/机型/平台内径/门框/升降机形式选内附件</td></tr>
      <tr><td><b>步骤9 升降机设计</b></td><td>钢绳导向：扶持位置 =（中段筒高 − 1250mm）÷ 2；扶持上下沿与焊缝 <b>&gt;100mm</b>；水平位置按公式计算。爬梯导向：后续补充</td><td><code>support()</code>：<code>H_SUPPORT = SEC_H_TOTAL/2 − H_PLATFORM</code>，焊缝冲突避让 200mm</td></tr>
    </table>
  </div>
</footer>

<script type="text/plain" id="bpmn-xml">__BPMN_XML__</script>

<script src="vendor/bpmn-navigated-viewer.production.min.js"></script>
<script>
(function(){
  "use strict";

  var GATEWAYS = ["Gateway_Inner","Gateway_Section","Gateway_Height","Gateway_Diameter","Gateway_Conn","Gateway_Model"];
  var LEVEL_NAMES = ["平台所在处内径","筒节","高度","直径","附件连接方式","机型及升降机类型"];

  var CASES = [
    { key:"inner",    label:"内径命中",     idx:0, tasks:[], end:"End_Reuse",
      desc:"平台所在处内径命中 → 该级及之后全部复用，直接复用原设计图，无需修改。" },
    { key:"section",  label:"筒节命中",     idx:1, tasks:["Task_Step4"], end:"End_Design",
      desc:"内径未命中、筒节命中 → 仅需完成平台设计（步骤4）。" },
    { key:"height",   label:"高度命中",     idx:2, tasks:["Task_Step5","Task_Step4"], end:"End_Design",
      desc:"内径、筒节未命中，高度命中 → 筒节设计（步骤5）→ 平台设计（步骤4）。" },
    { key:"diameter", label:"直径命中",     idx:3, tasks:["Task_Step6","Task_Step5","Task_Step4"], end:"End_Design",
      desc:"高度未命中、直径命中 → 高度设计（步骤6）→ 筒节设计（步骤5）→ 平台设计（步骤4）。" },
    { key:"conn",     label:"连接方式命中", idx:4, tasks:["Task_Step7","Task_Step6","Task_Step5","Task_Step4"], end:"End_Design",
      desc:"直径未命中、附件连接方式命中 → 直径设计（步骤7）→ 高度设计（步骤6）→ 筒节设计（步骤5）→ 平台设计（步骤4）。" },
    { key:"model",    label:"机型命中",     idx:5, tasks:["Task_Step8","Task_Step7","Task_Step6","Task_Step5","Task_Step4"], end:"End_Design",
      desc:"附件连接方式未命中、机型命中 → 附件类型替换（步骤8）→ 直径设计（步骤7）→ 高度设计（步骤6）→ 筒节设计（步骤5）→ 平台设计（步骤4）。" },
    { key:"none",     label:"全未命中",     idx:6, tasks:["Task_Step9","Task_Step8","Task_Step7","Task_Step6","Task_Step5","Task_Step4"], end:"End_Design",
      desc:"机型及升降机类型亦未命中 → 升降机设计（步骤9）→ 附件类型替换（步骤8）→ 直径设计（步骤7）→ 高度设计（步骤6）→ 筒节设计（步骤5）→ 平台设计（步骤4）。" }
  ];

  var STEP_INFO = {
    Task_Step9:{ no:"步骤9", name:"升降机设计", tag:"机型未命中时执行", cons:[
      "钢绳导向：扶持位置 =（<b>中段筒高 − 1250mm</b>）÷ 2",
      "扶持上下沿与焊缝距离 <b>&gt; 100mm</b>",
      "水平位置按公式计算",
      "爬梯导向：后续补充流程"
    ], code:"support(n, SEC_H_TOTAL, H_PLATFORM) → H_SUPPORT = SEC_H_TOTAL/2 − H_PLATFORM；与焊缝冲突时避让 200mm" },
    Task_Step8:{ no:"步骤8", name:"附件类型替换", tag:"连接方式未命中时执行", cons:[
      "按附件连接方式替换适配的附件类型",
      "<b>粘贴式</b> 与 <b>焊接式</b> 选用不同附件",
      "连接方式指爬梯支撑与电缆托架如何安装在塔筒上"
    ], code:"selectacc.select_accessory() → 汇总段数 / 机型 / 平台内径 / 门框形式 / 升降机形式，用于选取内附件与附件总成" },
    Task_Step7:{ no:"步骤7", name:"直径设计", tag:"直径未命中时执行", cons:[
      "电缆托架中心距同侧爬梯支撑的弦长满足映射关系",
      "弦长按机型取值：V12/V15 = 800/800，V17 = 650/650，V19 = 650/650"
    ], code:"midskelW → L_CABLE / L1_CABLE_I / L2_CABLE_I（windturbine_models 按机型取值）" },
    Task_Step6:{ no:"步骤6", name:"高度设计", tag:"高度未命中时执行", cons:[
      "更换与筒高一致的<b>梯子（爬梯）</b>",
      "更换与筒高一致的<b>线槽</b>"
    ], code:"midskelW → L_LADDER = L_C = 段长；ladderLay() 计算母线长与下端漏出；ladderSuggest()/cableSuggest() 按母线长选型" },
    Task_Step5:{ no:"步骤5", name:"筒节设计", tag:"筒节未命中时执行", cons:[
      "5.1 附件（爬梯支撑 + 电缆托架，两者平齐）与环焊缝上下距离 <b>&gt; 100mm</b>",
      "5.2 相邻附件中心间距 <b>≤ 280×7 = 1960mm</b> 且 <b>≥ 280×6 = 1680mm</b>",
      "5.3 第一个附件到底部 <b>980mm</b>；倒数第二个附件到平台 <b>5×280~7×280mm</b>；最后一个电缆托架（无爬梯支撑）距平台 <b>200mm</b>",
      "5.4 第一个灯安装高度上方空间 <b>2.6~3m</b>；灯型由机型 + 区域确定",
      "5.5 每个灯上螺柱间距最大 <b>10m</b>、最小 <b>5m</b>；螺柱间距 <b>500mm</b>",
      "5.6 焊接在筒壁上的灯每个螺柱与焊缝上下距离 <b>&gt; 100mm</b>（线槽灯不考虑）"
    ], code:"ls_heights() 增量 [1960, 1680, 1400]，平台下 840mm 停止，焊缝避让 200mm；lasupport2w() 支撑距焊缝 ≥125mm；lampLay() 灯避让焊缝" },
    Task_Step4:{ no:"步骤4", name:"平台设计", tag:"所有路径的终点", cons:[
      "平台到筒顶距离 <b>1250mm</b>"
    ], code:"midskelW → H_platform = stan_layGeo.cell_value(2, n)；platSugget() 以平台所在处内径 ±6mm 匹配平台物料号" }
  };

  /* ---------- build hit list ---------- */
  var hitlist = document.getElementById("hitlist");
  CASES.forEach(function(c, i){
    var lab = document.createElement("label");
    lab.className = "hitopt" + (i === 0 ? " on" : "");
    lab.dataset.idx = i;
    var chain = c.idx === 0 ? "直接复用" : (c.tasks.length ? c.tasks.length + " 个环节" : "无");
    lab.innerHTML = '<input type="radio" name="hit" value="' + i + '"' + (i === 0 ? " checked" : "") + '>' +
      '<span class="lbl">' + c.label + '</span><span class="chain">需执行 ' + chain + '</span>';
    hitlist.appendChild(lab);
  });

  /* ---------- viewer ---------- */
  var viewer = new BpmnJS({ container: "#canvas" });
  var canvas = null;
  var ALL_IDS = GATEWAYS.concat(["Task_Step1","Task_Step4","Task_Step5","Task_Step6","Task_Step7","Task_Step8","Task_Step9","End_Reuse","End_Design"]);

  function clearMarkers(){
    if(!canvas) return;
    ALL_IDS.forEach(function(id){
      ["wb-hit","wb-task","wb-end","wb-dim"].forEach(function(cls){ canvas.removeMarker(id, cls); });
    });
  }

  function applyCase(i){
    var c = CASES[i];
    clearMarkers();

    var traversed = c.idx >= 6 ? GATEWAYS.slice(0) : GATEWAYS.slice(0, c.idx + 1);
    traversed.forEach(function(id){ canvas.addMarker(id, "wb-hit"); });
    c.tasks.forEach(function(id){ canvas.addMarker(id, "wb-task"); });
    canvas.addMarker(c.end, "wb-end");

    // dim the untouched design tasks
    ["Task_Step4","Task_Step5","Task_Step6","Task_Step7","Task_Step8","Task_Step9"].forEach(function(id){
      if(c.tasks.indexOf(id) === -1) canvas.addMarker(id, "wb-dim");
    });

    // chips
    var chips = document.getElementById("chips");
    chips.innerHTML = "";
    LEVEL_NAMES.forEach(function(nm, k){
      var st = k < c.idx ? "miss" : (k === c.idx && c.idx < 6 ? "hit" : (c.idx >= 6 ? "miss" : "reuse"));
      var txt = k < c.idx ? "未命中" : (k === c.idx && c.idx < 6 ? "命中" : (c.idx >= 6 ? "未命中" : "复用"));
      var d = document.createElement("span");
      d.className = "chip " + st;
      d.textContent = nm + " · " + txt;
      chips.appendChild(d);
    });

    // result
    document.getElementById("res-level").textContent = c.label;
    document.getElementById("res-desc").textContent = c.desc;

    var flow = document.getElementById("res-flow");
    flow.innerHTML = "";
    if(c.tasks.length === 0){
      flow.innerHTML = '<span class="step-pill none">无需修改 · 直接复用原设计图</span>';
    } else {
      c.tasks.forEach(function(id, k){
        if(k > 0){ var a = document.createElement("span"); a.className = "arrow"; a.textContent = "→"; flow.appendChild(a); }
        var s = document.createElement("span");
        s.className = "step-pill";
        s.textContent = STEP_INFO[id].no + " " + STEP_INFO[id].name;
        flow.appendChild(s);
      });
    }

    var reuse = c.idx >= 6 ? "无（全部需重新设计）" : LEVEL_NAMES.slice(c.idx).join("、");
    document.getElementById("res-kv").innerHTML =
      '<div style="flex:1"><b>复用环节：</b>' + reuse + '</div>' +
      '<div><b>需执行：</b>' + (c.tasks.length || 0) + ' 个</div>';

    // step details
    var det = document.getElementById("step-detail");
    det.innerHTML = "";
    if(c.tasks.length === 0){
      det.innerHTML = '<div style="font-size:12px;color:var(--tx-3);padding:6px 2px;">命中内径层级，无需执行任何设计环节，直接复用原设计图。</div>';
    } else {
      c.tasks.forEach(function(id){
        var s = STEP_INFO[id];
        var d = document.createElement("div");
        d.className = "stepcard";
        d.innerHTML = '<div class="hd"><span class="no">' + s.no + '</span><span class="nm">' + s.name +
          '</span><span class="tag">' + s.tag + '</span></div>' +
          '<ul>' + s.cons.map(function(x){ return "<li>" + x + "</li>"; }).join("") + '</ul>' +
          '<div class="code"><b>老程序：</b>' + s.code + '</div>';
        det.appendChild(d);
      });
    }
  }

  /* ---------- events ---------- */
  hitlist.addEventListener("change", function(e){
    if(e.target.name !== "hit") return;
    var i = parseInt(e.target.value, 10);
    Array.prototype.forEach.call(hitlist.children, function(el){
      el.classList.toggle("on", parseInt(el.dataset.idx, 10) === i);
    });
    applyCase(i);
  });

  document.getElementById("btn-fit").addEventListener("click", function(){
    if(canvas) canvas.zoom("fit-viewport", "auto");
  });
  document.getElementById("btn-reset").addEventListener("click", clearMarkers);
  document.getElementById("btn-dl").addEventListener("click", function(){
    var xml = document.getElementById("bpmn-xml").textContent;
    var blob = new Blob([xml], {type:"application/xml"});
    var a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = "塔架中段定制化设计流程.bpmn";
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    setTimeout(function(){ URL.revokeObjectURL(a.href); }, 2000);
  });

  document.getElementById("footbar").addEventListener("click", function(){
    document.getElementById("footer").classList.toggle("open");
  });

  /* ---------- import ---------- */
  var xml = document.getElementById("bpmn-xml").textContent;
  viewer.importXML(xml).then(function(){
    canvas = viewer.get("canvas");
    canvas.zoom("fit-viewport", "auto");

    viewer.get("eventBus").on("element.click", function(e){
      var el = e.element;
      if(!el || !el.businessObject) return;
      var bo = el.businessObject;
      var box = document.getElementById("docbox");
      var docs = bo.documentation;
      var txt = (docs && docs.length) ? docs.map(function(d){ return d.text; }).join("\n\n") : "";
      var head = (bo.name || bo.id);
      if(txt){
        box.innerHTML = '<span class="dt">' + head + '</span>' + txt.replace(/</g,"&lt;").replace(/>/g,"&gt;");
      } else {
        box.innerHTML = '<span class="dt">' + head + '</span><span class="empty">该节点暂无补充说明。</span>';
      }
    });
  }).catch(function(err){
    document.getElementById("canvas").innerHTML =
      '<div style="padding:24px;color:#dc2626;font-size:13px;">BPMN 渲染失败：' + err.message + '</div>';
  });

  applyCase(0);
})();
</script>
</body>
</html>
'''

html = HTML.replace("__BPMN_XML__", bpmn_xml)
with io.open(OUT_PATH, "w", encoding="utf-8") as f:
    f.write(html)

print("written:", OUT_PATH, len(html), "chars")
