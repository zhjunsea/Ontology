import io, json

SRC = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\src\main\resources\static\index.html"
OUT = r"D:\work\Ontology\OntologyMachine\HWCodeArtsDir\e2e-real2.html"

html = io.open(SRC, encoding="utf-8").read()
d = json.load(io.open(r"D:\work\Ontology\OntologyMachine\HWCodeArtsDir\status_new.json", encoding="utf-8"))

payload = {
    "status": d.get("status"),
    "instanceKey": str(d.get("instanceKey")),
    "designResultSummary": d.get("designResultSummary"),
    "solutionCount": d.get("solutionCount"),
    "ontologyValidationStatus": d.get("ontologyValidationStatus"),
    "resultXlsxPaths": d.get("resultXlsxPaths", []),
}

mock = """
<script>
window.__payload = %s;
window.fetch = function(url){
  return Promise.resolve({ ok:true, json: function(){ return Promise.resolve(window.__payload); } });
};
</script>
""" % json.dumps(payload, ensure_ascii=False)

html = html.replace("<body>", "<body>\n" + mock, 1)
html = html.replace("</body>", "<script>pollStatus('%s');</script>\n</body>" % payload["instanceKey"], 1)

io.open(OUT, "w", encoding="utf-8").write(html)
print("written", OUT, len(html))
print("summary_len", len(payload["designResultSummary"] or ""))
