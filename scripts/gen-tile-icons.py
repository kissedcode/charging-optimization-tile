from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
import pathlib, subprocess

FONT = '/usr/share/fonts/truetype/roboto/unhinted/RobotoTTF/Roboto-Black.ttf'
font = TTFont(FONT); gs = font.getGlyphSet(); cmap = font.getBestCmap(); upm = font['head'].unitsPerEm
cap = font['OS/2'].sCapHeight

def text_path(txt, cap_h, cx, cy, hscale=1.0, tracking=0):
    """Return SVG path for txt, cap height cap_h, centered at (cx, cy) (y down)."""
    s = cap_h / cap
    names = [cmap[ord(c)] for c in txt]
    widths = [gs[n].width * s * hscale for n in names]
    total = sum(widths) + tracking * (len(txt) - 1)
    x = cx - total / 2
    parts = []
    for n, w in zip(names, widths):
        pen = SVGPathPen(gs)
        # flip y, scale, translate: baseline at cy + cap_h/2
        tp = TransformPen(pen, (s * hscale, 0, 0, -s, x, cy + cap_h / 2))
        gs[n].draw(tp)
        parts.append(pen.getCommands())
        x += w + tracking
    return ' '.join(parts)

# Horizontal battery geometry (24x24 viewport)
X0, X1, Y0, Y1, SW = 0.75, 21.25, 5.0, 19.0, 1.6
IX0, IX1, IY0, IY1 = X0 + SW, X1 - SW, Y0 + SW, Y1 - SW
R = 2.0
def rrect(x0, y0, x1, y1, r):
    return (f"M{x0+r},{y0}H{x1-r}A{r},{r} 0 0 1 {x1},{y0+r}V{y1-r}A{r},{r} 0 0 1 {x1-r},{y1}"
            f"H{x0+r}A{r},{r} 0 0 1 {x0},{y1-r}V{y0+r}A{r},{r} 0 0 1 {x0+r},{y0}Z")
def rect(x0, y0, x1, y1):
    return f"M{x0},{y0}H{x1}V{y1}H{x0}Z"
shell = rrect(X0, Y0, X1, Y1, R) + rrect(IX0, IY0, IX1, IY1, R - SW/2)   # evenOdd ring
nub = rrect(X1 - 0.3, 9.0, 23.25, 15.0, 1.0)
cx, cy = (IX0 + IX1) / 2, (IY0 + IY1) / 2

def fmt(p):  # round numbers
    import re
    return re.sub(r'-?\d+\.\d+', lambda m: ('%.2f' % float(m.group())).rstrip('0').rstrip('.'), p)

icons = {}
# OFF: empty battery with bolt
bolt = f"M{cx+1.4},{IY0+0.9}L{cx-3.1},{cy+0.9}H{cx-0.2}L{cx-1.2},{IY1-0.7}L{cx+3.2},{cy-0.9}H{cx+0.3}Z"
icons['ic_off'] = [(shell + nub, 'evenOdd'), (bolt, 'nonZero')]
# LIMIT 80%: fill 80% of interior, cut "80%" out of the fill
fill_x1 = IX0 + 0.8 * (IX1 - IX0)
fill = rect(IX0 + 0.5, IY0 + 0.5, fill_x1, IY1 - 0.5)
txt = text_path('80%', 5.6, (IX0 + 0.5 + fill_x1) / 2, cy, hscale=0.82, tracking=-0.15)
icons['ic_limit'] = [(shell + nub, 'evenOdd'), (fill + txt, 'evenOdd')]
# ADAPTIVE: full fill, cut "A"
fillA = rect(IX0 + 0.5, IY0 + 0.5, IX1 - 0.5, IY1 - 0.5)
txtA = text_path('A', 7.2, cx, cy)
icons['ic_adaptive'] = [(shell + nub, 'evenOdd'), (fillA + txtA, 'evenOdd')]
# UNKNOWN: empty battery with "?"
icons['ic_unknown'] = [(shell + nub, 'evenOdd'), (text_path('?', 7.2, cx, cy), 'nonZero')]

out = pathlib.Path('/home/user/workspace/charging-optimization-tile/app/src/main/res/drawable')
for name, paths in icons.items():
    xml = ['<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">']
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="192" height="192"><rect width="24" height="24" fill="#6b5a2e"/>']
    for d, ft in paths:
        d = fmt(d)
        xml.append(f'    <path android:fillColor="#FFFFFFFF" android:fillType="{ft}" android:pathData="{d}"/>')
        svg.append(f'<path fill="#fff" fill-rule="{ "evenodd" if ft=="evenOdd" else "nonzero"}" d="{d}"/>')
    xml.append('</vector>'); svg.append('</svg>')
    (out / f'{name}.xml').write_text('\n'.join(xml) + '\n')
    pathlib.Path(f'/tmp/icons/{name}.svg').write_text(''.join(svg))
    print(name, 'ok', sum(len(d) for d,_ in paths), 'chars')
