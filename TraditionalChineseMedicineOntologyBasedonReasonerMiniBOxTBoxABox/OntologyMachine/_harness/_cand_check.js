const fs=require('fs');
const d=JSON.parse(fs.readFileSync('D://work//Ontology//TraditionalChineseMedicineOntologyBasedonReasonerMiniBOxTBoxABox//OntologyMachine//_harness//_cand.json','utf8'));
function parseScore(s){
  const t = String(s==null?'':s);
  const g = /gap=(\d+)/.exec(t);
  const h = /hits=(\d+)\/(\d+)/.exec(t);
  const p = /poss=(\d+)\/(\d+)/.exec(t);
  return { gap: g?Number(g[1]):null, hits: h?Number(h[1]):null, required: h?Number(h[2]):null,
           possHits: p?Number(p[1]):null, possTotal: p?Number(p[2]):null };
}
(d.names||[]).forEach((x,i)=>{
  const sc=parseScore((d.scores||[])[i]);
  const bits=[];
  if(sc.gap!=null) bits.push('主证缺口 '+sc.gap);
  if(sc.hits!=null&&sc.required!=null) bits.push('主证命中 '+sc.hits+'/'+sc.required);
  if(sc.possTotal) bits.push('或然症 '+sc.possHits+'/'+sc.possTotal);
  console.log((i+1)+'. '+x+'  ——  '+bits.join(' · '));
});
