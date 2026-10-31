import json, os

d = json.load(open('last-status.json', 'r', encoding='utf-8'))
key = d['instanceKey']
v = d['designResultSummary']
print('INSTANCE_KEY', key)
print('V_LEN', len(v))

# 1) is the value itself valid JSON?
try:
    json.loads(v)
    print('V_IS_VALID_JSON', True)
except Exception as e:
    print('V_IS_VALID_JSON', False, '| err:', str(e)[:80])

# 2) does re-encoding the md file reproduce the raw value prefix?
mdpath = os.path.join('..', 'OntologyFrameworkLatticeTower', 'logs', 'latticetower',
                      'latticetower-result-%d.md' % key)
print('MD_EXISTS', os.path.exists(mdpath), mdpath)
if os.path.exists(mdpath):
    md = open(mdpath, 'r', encoding='utf-8').read()
    print('MD_LEN', len(md))
    enc = json.dumps(md, ensure_ascii=True)   # JSON-encoded markdown
    print('JSON_ENC_MD_LEN', len(enc))
    enc_u = json.dumps(md, ensure_ascii=False)
    print('JSON_ENC_MD_LEN_NONASCII', len(enc_u))
    print('ENC_PREFIX_MATCH_ASCII', enc[:60] == v[:60])
    print('ENC_PREFIX_MATCH_NONASCII', enc_u[:60] == v[:60])
    print('V_IS_PREFIX_OF_ENC_ASCII', enc.startswith(v))
    print('V_IS_PREFIX_OF_ENC_NONASCII', enc_u.startswith(v))
    print('ENC_ASCII_HEAD', repr(enc[:80]))
    print('V_HEAD', repr(v[:80]))
    # what does enc look like at position len(v)?
    print('ENC_AT_LENV', repr(enc[len(v)-5:len(v)+20]))
