import json

with open('last-status.json', 'r', encoding='utf-8') as f:
    d = json.load(f)

v = d['designResultSummary']
nl = chr(10)
bs = chr(92)

print('TYPE', type(v).__name__)
print('LEN', len(v))
print('FIRST_CHARS_CODEPOINTS', [ord(c) for c in v[:5]])
print('LAST_CHARS_CODEPOINTS', [ord(c) for c in v[-5:]])
print('STARTS_WITH_DQUOTE', v[0] == '"')
print('ENDS_WITH_DQUOTE', v[-1] == '"')
print('FIRST2_IS_QUOTE_HASH', v[:2] == '"#')
print('CONTAINS_REAL_NEWLINE', nl in v)
print('COUNT_REAL_NEWLINE', v.count(nl))
print('CONTAINS_BACKSLASH', bs in v)
print('COUNT_BACKSLASH', v.count(bs))
print('CONTAINS_LITERAL_BS_N', (bs + 'n') in v)
print('COUNT_LITERAL_BS_N', v.count(bs + 'n'))

# Compare with the on-disk md file
import glob, os
md_files = glob.glob(os.path.join('..', 'OntologyFrameworkLatticeTower', 'logs', 'latticetower', 'latticetower-result-*.md'))
print('MD_FILES', md_files)
if md_files:
    md = open(md_files[0], 'r', encoding='utf-8').read()
    print('MD_LEN', len(md))
    print('MD_CONTAINS_REAL_NEWLINE', nl in md)
    print('MD_FIRST_CHARS_CODEPOINTS', [ord(c) for c in md[:5]])
    print('MD_STARTS_WITH_DQUOTE', md[0] == '"')
