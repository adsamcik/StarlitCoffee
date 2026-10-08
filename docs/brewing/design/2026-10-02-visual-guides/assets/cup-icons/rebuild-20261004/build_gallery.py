"""Build the review gallery from the saved generated images and traced SVGs."""
from pathlib import Path
import html
import json

BASE = Path(__file__).resolve().parent

def svg_markup(key):
    path = BASE / 'svg' / f'{key}.svg'
    return path.read_text() if path.exists() else '<span class="pending">Awaiting trace</span>'

def main():
    catalog = json.loads((BASE / 'catalog.json').read_text(encoding='utf-8-sig'))
    cards = []
    for entry in catalog['entries']:
        key, label = entry['key'], html.escape(entry['label'])
        source = f'<img src="generated/{key}.png" alt="Generated {label}">' if (BASE / 'generated' / f'{key}.png').exists() else '<span>Generating</span>'
        vector = svg_markup(key)
        sizes = ''.join(f'<span style="--size:{size}px">{vector}<small>{size}</small></span>' for size in (24,28,34))
        cards.append(f'<article id="{key}"><h2>{label}</h2><div class="comparison"><figure>{source}<figcaption>Generated</figcaption></figure><figure class="large">{vector}<figcaption>SVG</figcaption></figure></div><div class="sizes">{sizes}</div><details><summary>Anatomy reference</summary><img class="reference" src="references/{key}.png" alt="Native {label}"></details><a href="svg/{key}.svg" download>SVG file</a></article>')
    page = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Starlit Coffee · all 28 vessel icons</title><style>
    *{box-sizing:border-box}body{font:16px system-ui,sans-serif;margin:0;background:#f7f5fc;color:#303139;padding:28px}header{max-width:1200px;margin:0 auto 24px}h1{font-size:28px;margin:0 0 10px}p{max-width:780px;line-height:1.5;margin:10px 0}a{color:#506093}button{font:inherit;background:#dee2fa;color:#303139;border:0;border-radius:24px;padding:12px 18px;cursor:pointer}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:16px;max-width:1400px;margin:auto}article{background:#fff;border-radius:24px;padding:18px;min-width:0}h2{font-size:15px;margin:0 0 10px;min-height:38px}.comparison{display:grid;grid-template-columns:1fr 1fr;gap:8px}figure{margin:0;text-align:center}figure img,figure svg{width:100%;height:96px;object-fit:contain}figcaption{font-size:11px;color:#646774;margin:6px 0}.large{color:inherit}.sizes{display:flex;align-items:end;justify-content:center;gap:28px;margin:14px 0}.sizes span{display:flex;flex-direction:column;align-items:center;gap:8px}.sizes svg{width:var(--size);height:var(--size)}small{font-size:11px;color:#646774}details{font-size:12px;margin:10px 0}.reference{display:block;width:120px;height:120px;object-fit:contain;margin:12px auto}article>a{font-size:12px}.pending{font-size:12px;display:block;min-height:96px;padding-top:38px;color:#777}footer{max-width:1200px;margin:28px auto}.dark{background:#1e202a;color:#edf0ff}.dark article{background:#30333e}.dark a{color:#c1caff}.dark small,.dark figcaption{color:#bdc0ce}
    </style><header><h1>All 28 cup &amp; vessel icons</h1><p>New generated silhouettes traced directly into smooth, tintable SVGs. Review the full catalog at 24, 28 and 34 pixels, including the glasses and servers behind the five defaults.</p><button id="theme">Toggle dark surface</button> <a href="../../../../prototype.html">Open app preview</a> · <a href="prompts.json">Generation prompts</a> · <a href="trace-report.json">Contour checks</a></header><main class="grid">'''+''.join(cards)+'''</main><footer><a href="#">Back to top</a><p>Prototype assets. The Android resources have not been replaced.</p></footer><script>document.querySelector('#theme').onclick=()=>document.body.classList.toggle('dark');</script></html>'''
    compact_css = '''.compact .grid{grid-template-columns:repeat(7,minmax(0,1fr))}.compact article{padding:16px}.compact .comparison{grid-template-columns:1fr}.compact .comparison figure:first-child,.compact figcaption,.compact details{display:none}.compact .large svg{width:76px;height:76px}.compact .sizes{gap:18px;margin:10px 0}.compact h2{font-size:13px;min-height:36px}.compact article>a{font-size:11px}.compact .pending{min-height:76px}@media(max-width:1000px){.compact .grid{grid-template-columns:repeat(4,minmax(0,1fr))}}@media(max-width:600px){.compact .grid{grid-template-columns:repeat(2,minmax(0,1fr))}}'''
    page = page.replace('</style><header>', compact_css + '</style><body class="compact"><header>')
    page = page.replace('../../../../prototype.html', '../../../prototype.html')
    page = page.replace('<button id="theme">', '<button id="compare">Compare source and SVG</button> <button id="theme">')
    page = page.replace('</script>', "document.querySelector('#compare').onclick=()=>{document.body.classList.toggle('compact');document.querySelector('#compare').textContent=document.body.classList.contains('compact')?'Compare source and SVG':'Show compact family';};</script>")
    (BASE / 'gallery.html').write_text(page, encoding='utf-8', newline='\n')

if __name__ == '__main__':
    main()
