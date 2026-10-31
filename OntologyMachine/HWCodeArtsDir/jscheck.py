import re
base = 'D:/work/Ontology/OntologyMachine/'
src = open(base + 'OntologyFrameworkLatticeTower/src/main/resources/static/index.html', encoding='utf-8').read()
m = re.search(r'<script>(.*?)</script>', src, re.S)
js = m.group(1)
print('script block length =', len(js))
tail = """
<script>
document.body.setAttribute('data-has-rm', typeof renderMarkdown);
document.body.setAttribute('data-has-start', typeof startDesign);
document.body.setAttribute('data-has-poll', typeof pollStatus);
try {
  document.getElementById('result').innerHTML = '<article class="report">' + renderMarkdown('# 标题\\n\\n## 小节\\n- 一\\n- 二') + '</article>';
  document.body.setAttribute('data-rendered', document.querySelector('.report h1') ? 'yes' : 'no');
} catch (e) { document.body.setAttribute('data-err', String(e && e.message)); }
</script>
"""
out = '<!DOCTYPE html><html><head><meta charset="UTF-8"></head><body><div id="result"></div><script>' + js + '</script>' + tail + '</body></html>'
open(base + 'HWCodeArtsDir/js-check.html', 'w', encoding='utf-8').write(out)
print('written js-check.html')
