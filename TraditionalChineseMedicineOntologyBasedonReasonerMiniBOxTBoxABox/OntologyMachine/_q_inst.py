import re
txt = open('_inst_dump.txt', encoding='utf-8').read()
kws = ['目', '脱', '拘急', '四逆', '厥', '烦', '手足寒', '细数', '语', '言', '饮',
       '不欲', '冒', '虚劳', '里急', '腹中', '少气', '少腹', '腰', '呕', '不能饮',
       '坐', '头重', '眩', '苦极', '重', '水', '脓', '疮', '咽', '声', '寒', '痛']
for kw in kws:
    lines = [l for l in txt.splitlines() if kw in l and '_instance' in l]
    if lines:
        print(f'=== {kw} ({len(lines)}) ===')
        for l in lines:
            print(l)
        print()
