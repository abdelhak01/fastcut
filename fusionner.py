#!/usr/bin/env python3
"""
Fusionne les modules JavaScript dans index.html.

Android bloque les modules ES6 (import/export) quand la page est
chargee depuis file:///android_asset/ : la page s'affiche mais aucun
script ne demarre, d'ou un ecran noir. On produit donc une version
autonome, sans import, pour l'APK.
"""
import re
import sys

def sans_modules(chemin):
    code = open(chemin, encoding='utf-8').read()
    code = re.sub(r'^export\s+', '', code, flags=re.M)      # retire "export"
    code = re.sub(r'^import\s.*$', '', code, flags=re.M)    # retire les imports
    return code

html = open('index.html', encoding='utf-8').read()

modules = '\n'.join(sans_modules(f) for f in ['moteur.js', 'dxf.js', 'exports.js'])

# Remplacer le bloc <script type="module"> ... imports ... par le code fusionne
motif = re.compile(
    r'<script type="module">.*?from\s+[\'"]\./exports\.js[\'"];',
    re.S)
if not motif.search(html):
    print("ERREUR : bloc d'imports introuvable dans index.html", file=sys.stderr)
    sys.exit(1)

html = motif.sub('<script>\n' + modules.replace('\\', '\\\\'), html, count=1)

# Le service worker n'a pas de sens dans l'APK (tout est deja local)
html = html.replace("navigator.serviceWorker.register('sw.js')", "Promise.resolve()")

open('index-apk.html', 'w', encoding='utf-8').write(html)
print("index-apk.html genere : %d octets" % len(html))
