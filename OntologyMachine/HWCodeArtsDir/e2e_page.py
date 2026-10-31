import io, os, re, json

SRC = r"D:\work\Ontology\OntologyMachine\OntologyFrameworkLatticeTower\src\main\resources\static\index.html"
OUT = r"D:\work\Ontology\OntologyMachine\HWCodeArtsDir\e2e-page.html"

html = io.open(SRC, encoding="utf-8").read()

# realistic designResultSummary, mirroring buildSummary()
def sol(bw, beta, panel, layer, ecc, cross, strut, diaph):
    return ("## \u5e95\u9762\u5bbd\u5ea6 %d m\uff08beta=%s\u00b0\uff09\n" % (bw, beta)
            + "- \u51e0\u4f55\u53c2\u6570\uff1adis_bott=%d mm\uff1bdis_top=9800 mm\uff1bhigh_truss=101404.625 mm\uff1bH_0=880.0 mm\uff1bdeviation=500 mm\uff1bbeta1=%s\u00b0\uff1bbeta2=%s\u00b0\n" % (bw*1000, beta, beta)
            + "- \u8282\u95f4\u6570\uff1a%d\n" % panel
            + "- \u5c42\u6570\uff1a%d\n" % layer
            + "- \u504f\u5fc3\u7ebf\u4ea4\u70b9\u9ad8\u5ea6(mm)\uff1a%s\n" % ", ".join(ecc)
            + "- \u4ea4\u53c9\u70b9\u9ad8\u5ea6(mm)\uff1a%s\n" % ", ".join(cross)
            + "- \u6a2a\u6491\u957f\u5ea6(mm)\uff1a%s\n" % ", ".join(strut)
            + "- \u6a2a\u9694\u957f\u5ea6(mm)\uff1a%s\n\n" % ", ".join(diaph))

summary = "# \u683c\u6784\u5854\u51e0\u4f55\u5e03\u7f6e\u8bbe\u8ba1\u7ed3\u679c\n\n"
summary += "- \u5854\u67b6\u9ad8\u5ea6\uff1a100000 mm\n"
summary += "- \u53ef\u884c\u89e3\u6761\u6570\uff1a2\uff08\u9010\u6761\u5217\u51fa\uff0c\u540c\u4e00\u5e95\u5bbd\u7684\u591a\u6761 beta \u89e3\u4e0d\u5408\u5e76\uff09\n\n"
summary += sol(15, "48.3", 12, 6, ["120","9000","18000"], ["4500","13500"], ["8000","8200"], ["11314","11597"])
summary += sol(16, "47.1", 13, 6, ["120","9200","18500"], ["4700","13800"], ["8400","8600"], ["11879","12162"])

payload = {
    "status": "COMPLETED",
    "instanceKey": "1001",
    "designResultSummary": summary,
    "solutionCount": "2",
    "ontologyValidationStatus": "\u5df2\u5199\u56de\u672c\u4f53",
    "resultXlsxPaths": ["logs/latticetower/x-geo-bw15.xlsx", "logs/latticetower/x-geo-bw16.xlsx"],
    "relaxedNoSolution": [{"bottomWidthM": 30}],
    "relaxedNoSolutionCount": "1",
    "relaxResultXlsxPaths": ["logs/latticetower/x-relax-bw30.xlsx"],
}

mock = """
<script>
window.__payload = %s;
window.fetch = function(url){
  var body = String(url).indexOf('/health') >= 0
      ? {engineAvailable:true, processId:'LatticeTowerDesignProcess'}
      : window.__payload;
  return Promise.resolve({ ok:true, json: function(){ return Promise.resolve(body); } });
};
</script>
""" % json.dumps(payload, ensure_ascii=False)

html = html.replace("<body>", "<body>\n" + mock, 1)
html = html.replace("</body>", "<script>pollStatus('1001');</script>\n</body>", 1)

io.open(OUT, "w", encoding="utf-8").write(html)
print("written", OUT, len(html))
