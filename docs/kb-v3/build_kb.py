# Assembles the VastuFirst Knowledge Base v3 review document.
# Reads: copy.json (every user-visible string, swept from source), rewrites.json (proposed copy),
#        body.html (the hand-written analysis sections).
# Writes: kb-v3.html
import json, io, html, os

D = os.path.dirname(os.path.abspath(__file__))
copy = json.load(io.open(os.path.join(D, 'copy.json'), encoding='utf-8'))
rew = json.load(io.open(os.path.join(D, 'rewrites.json'), encoding='utf-8'))
body = io.open(os.path.join(D, 'body.html'), encoding='utf-8').read()

def e(s):
    return html.escape(s or '')

NONE = ('none', 'none.', '')

# ---- Proposed copy changes table -------------------------------------------------
rows = []
for r in rew:
    rows.append(
        '<tr><td class="scr">%s</td><td class="now">%s</td><td class="why">%s</td><td class="new">%s</td></tr>'
        % (e(r['screen']), e(r['now']), e(r['why']), e(r['new'])))
rewrites_html = (
    '<table class="rw"><thead><tr><th>Screen</th><th>What it says now</th>'
    '<th>Why it is a problem</th><th>Suggested wording</th></tr></thead><tbody>'
    + ''.join(rows) + '</tbody></table>')

# ---- The full copy inventory -----------------------------------------------------
parts = []
total = 0
flagged = 0
for i, s in enumerate(copy):
    ss = s['strings']
    total += len(ss)
    n_flag = sum(1 for t in ss if (t.get('concern') or '').strip().lower() not in NONE)
    flagged += n_flag
    badge = ('<span class="badge">%d to review</span>' % n_flag) if n_flag else ''
    trs = []
    for t in ss:
        c = (t.get('concern') or '').strip()
        has = c.lower() not in NONE
        cond = e(t.get('condition') or '')
        trs.append(
            '<tr class="%s"><td class="txt">%s</td><td class="role">%s%s</td><td class="cn">%s</td><td class="rv"></td></tr>'
            % ('flag' if has else '',
               e(t['text']),
               e(t.get('role') or ''),
               ('<br><span class="cond">%s</span>' % cond) if cond else '',
               e(c) if has else ''))
    # Each table gets its OWN overflow-x container: without it a long line of copy makes the
    # whole page scroll sideways on a phone, which is exactly what this document is reviewing.
    parts.append(
        '<section class="sc"><h3>%d. %s %s</h3><p class="when"><b>When you see it:</b> %s</p>'
        '<div class="scroll"><table class="ci"><thead><tr><th>Exact words on screen</th><th>Kind</th>'
        '<th>Our note</th><th>Your comment</th></tr></thead><tbody>%s</tbody></table></div></section>'
        % (i + 1, e(s['screen']), badge, e(s.get('whenSeen') or ''), ''.join(trs)))

inventory_html = ''.join(parts)

html_out = (body
            .replace('<!--REWRITES-->', rewrites_html)
            .replace('<!--INVENTORY-->', inventory_html)
            .replace('{{SCREENS}}', str(len(copy)))
            .replace('{{STRINGS}}', str(total))
            .replace('{{FLAGGED}}', str(flagged)))

io.open(os.path.join(D, 'kb-v3.html'), 'w', encoding='utf-8').write(html_out)
print('screens', len(copy), 'strings', total, 'flagged', flagged)
print('bytes', len(html_out))
