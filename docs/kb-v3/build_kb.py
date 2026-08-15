# Assembles the VastuFirst Vastu Knowledge Base for expert review.
#
# Reads : body.html  — the hand-written rule sections, carrying the whole stylesheet
#         copy.json  — every user-visible string swept from the app source
# Writes: kb-v3.html — the document that gets published and printed to PDF
#
# The copy appendix is deliberately PLAIN: the words, the situation that produces them, and an
# empty column for the reviewer. Our own internal misgivings about a line are not printed — a
# reviewer should read the copy as a customer meets it, not through our commentary.
import json, io, html, os

D = os.path.dirname(os.path.abspath(__file__))
copy = json.load(io.open(os.path.join(D, 'copy.json'), encoding='utf-8'))
body = io.open(os.path.join(D, 'body.html'), encoding='utf-8').read()

def e(s):
    return html.escape(s or '')

NONE = ('none', 'none.', '')

parts = []
total = 0
for i, s in enumerate(copy):
    ss = s['strings']
    total += len(ss)
    trs = []
    for t in ss:
        # A line we already have a question about is shaded, so the reviewer's eye goes there
        # first — but the question itself stays ours.
        flagged = (t.get('concern') or '').strip().lower() not in NONE
        cond = e(t.get('condition') or '')
        trs.append(
            '<tr class="%s"><td class="txt">%s</td><td class="role">%s%s</td><td class="rv"></td></tr>'
            % ('flag' if flagged else '',
               e(t['text']),
               e(t.get('role') or ''),
               ('<br><span class="cond">%s</span>' % cond) if cond else ''))
    parts.append(
        '<section class="sc"><h3>%d. %s</h3><p class="when"><b>When you see it:</b> %s</p>'
        '<div class="scroll"><table class="ci"><thead><tr><th>The words on screen</th>'
        '<th>Kind</th><th>Your comment</th></tr></thead><tbody>%s</tbody></table></div></section>'
        % (i + 1, e(s['screen']), e(s.get('whenSeen') or ''), ''.join(trs)))

out = (body
       .replace('<!--REWRITES_PLACEHOLDER-->', '')
       .replace('<!--INVENTORY-->', ''.join(parts)))

io.open(os.path.join(D, 'kb-v3.html'), 'w', encoding='utf-8').write(out)
print('screens', len(copy), 'strings', total, 'bytes', len(out))
