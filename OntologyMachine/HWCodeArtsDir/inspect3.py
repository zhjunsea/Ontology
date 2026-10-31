import json

d = json.load(open('last-status.json', 'r', encoding='utf-8'))
vs = d.get('variables', {})
print('=== variables (name, type, len, first_char, json_valid) ===')
for k, val in vs.items():
    if isinstance(val, str):
        ok = True
        try:
            json.loads(val)
        except Exception:
            ok = False
        print('%-26s str len=%-6d first=%r json_valid=%s' % (k, len(val), val[:1], ok))
    elif isinstance(val, list):
        print('%-26s list len=%d' % (k, len(val)))
    else:
        print('%-26s %s %r' % (k, type(val).__name__, val))

print()
print('=== top-level response strings ===')
for k, val in d.items():
    if isinstance(val, str):
        print('%-26s str len=%-6d first=%r' % (k, len(val), val[:1]))
    elif isinstance(val, list):
        print('%-26s list len=%d' % (k, len(val)))
    else:
        print('%-26s %s %r' % (k, type(val).__name__, val))
