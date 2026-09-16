import re

base = 'OntologyFramework/src/test/java/com/ocean/ontologyframework/tcm/'
files = ['DuliFangzhengTest.java', 'HebingFangzhengTest.java', 'ZabingFangzhengTest.java',
         'ShaoyinTaiyinFangzhengTest.java', 'ShaoyangYangmingFangzhengTest.java',
         'TaiyangFangzhengTest.java']
for f in files:
    s = open(base + f, encoding='utf-8').read()
    orders = re.findall(r'@Order\((\d+)\)', s)
    dup = [o for o in set(orders) if orders.count(o) > 1]
    print(f, 'brace_diff=', s.count('{') - s.count('}'),
          'tests=', s.count('@Test'), 'orders=', len(orders), 'dup=', dup)
