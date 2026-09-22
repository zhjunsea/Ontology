const fs=require('fs');
const j=JSON.parse(fs.readFileSync('_harness/_diag_wendan.json','utf8'));
const d=j.diagnosis||{};
function esc(s){return String(s==null?'':s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));}
function parseScore(s){const t=String(s==null?'':s);const g=/gap=(\d+)/.exec(t);const h=/hits=(\d+)\/(\d+)/.exec(t);const p=/poss=(\d+)\/(\d+)/.exec(t);return{gap:g?Number(g[1]):null,hits:h?Number(h[1]):null,required:h?Number(h[2]):null,possHits:p?Number(p[1]):null,possTotal:p?Number(p[2]):null};}
function evLine(label,list,chipCls){if(!(list||[]).length)return '';return '<div class="evline"><span class="evk">'+esc(label)+'</span>'+(list||[]).map(s=>'<span class="chip '+(chipCls||'plain')+' clickable" data-sym="'+esc(s)+'" title="点击填入输入框">'+esc(s)+'</span>').join('')+'</div>';}
function candidateBlock(d){
  const cands=d.candidateFangzhengsCn||d.fangzhengCandidatesCn||d.fangzhengCandidates||[];
  if(!cands.length)return null;
  const scores=d.candidateScores||[];const mm=d.candidateMatchedMainCn||[];const ms=d.candidateMissingMainCn||[];const mp=d.candidateMatchedPossCn||[];
  let h='<div class="candbox-k">候选方证</div><ol class="candlist">';
  cands.forEach((x,i)=>{const sc=parseScore(scores[i]);const bits=[];
    if(sc.gap!=null)bits.push('主证缺口 '+sc.gap);
    if(sc.hits!=null&&sc.required!=null)bits.push('主证命中 '+sc.hits+'/'+sc.required);
    if(sc.possTotal)bits.push('或然症 '+sc.possHits+'/'+sc.possTotal);
    h+='<li><span class="candname">'+esc(x)+'</span>'+(bits.length?'<span class="candmeta">'+esc(bits.join(' · '))+'</span>':'')
      +evLine('命中主证',mm[i],'ok')+evLine('缺口',ms[i],'warn')+evLine('命中或然证',mp[i],'plain')+'</li>';});
  h+='</ol>';return h;
}
const html=candidateBlock(d);
// 断言：每个候选都有「命中主证」或「缺口」行
const liCount=(html.match(/<li>/g)||[]).length;
const evCount=(html.match(/class="evline"/g)||[]).length;
console.log('候选数=',d.candidateFangzhengsCn.length,'<li>=',liCount,'证据行=',evCount);
console.log('--- 渲染片段（前 3 个候选）---');
console.log(html.split('</li>').slice(0,3).join('</li>\n'));
