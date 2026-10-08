# -*- coding: utf-8 -*-
"""Moissonne les fiches du fournisseur : reference LCM + images, depuis le sitemap.

Le sitemap porte deja les URL d'images ; la fiche n'est ouverte que pour y lire la
REFERENCE, seul point de jonction avec la tarification (le code du slug est un
identifiant interne du site, etranger a nos SKU).
"""
import json, re, sys, time, threading, queue
import urllib.request, gzip

BASE = "https://www.lacentralemedicale.fr"
UA = "Mozilla/5.0 (compatible; OptimiSanteCatalogue/1.0; revendeur)"
REF = re.compile(r'class="product-ref">\s*Ref\.\s*:\s*([0-9A-Za-z._/-]+)\s*<', re.S)
LD = re.compile(r'application/ld\+json[^>]*>(.*?)</script>', re.S)

def lire(url, essais=3):
    for n in range(essais):
        try:
            rq = urllib.request.Request(url, headers={"User-Agent": UA,
                "Accept-Encoding": "gzip", "Accept-Language": "fr-FR"})
            with urllib.request.urlopen(rq, timeout=30) as r:
                b = r.read()
                if r.headers.get("Content-Encoding") == "gzip":
                    b = gzip.decompress(b)
                return b.decode("utf-8", "replace")
        except Exception:
            if n == essais - 1:
                return None
            time.sleep(2 * (n + 1))

def images_ld(html):
    """Les images en pleine resolution, telles que la fiche les declare."""
    for m in LD.findall(html):
        try:
            j = json.loads(m)
        except Exception:
            continue
        for o in (j if isinstance(j, list) else [j]):
            if isinstance(o, dict) and o.get("@type") == "Product":
                im = o.get("image") or []
                return im if isinstance(im, list) else [im]
    return []

def main(fichier_sitemap, sortie, debut=0, fin=None, fils=3, pause=0.35):
    x = open(fichier_sitemap, encoding="utf-8").read()
    blocs = re.findall(r"<url>(.*?)</url>", x, re.S)
    taches = []
    for b in blocs:
        loc = re.search(r"<loc>(.*?)</loc>", b).group(1).replace("http://", "https://")
        sm = [u.replace("http://", "https://")
              for u in re.findall(r"<image:loc>(.*?)</image:loc>", b)]
        taches.append((loc, sm))
    taches = taches[debut:fin]
    print("fiches a lire : %d" % len(taches), flush=True)

    q = queue.Queue()
    for t in taches:
        q.put(t)
    verrou = threading.Lock()
    resultats, compteur = [], [0]

    def ouvrier():
        while True:
            try:
                loc, sm = q.get_nowait()
            except queue.Empty:
                return
            time.sleep(pause)
            html = lire(loc)
            ligne = {"url": loc, "images_sitemap": sm}
            if html:
                m = REF.search(html)
                ligne["ref"] = m.group(1).strip() if m else None
                n = re.search(r'id="sylius-product-name"[^>]*>(.*?)</h1>', html, re.S)
                ligne["nom"] = re.sub(r"\s+", " ", n.group(1)).strip() if n else None
                ligne["images"] = images_ld(html) or sm
            else:
                ligne["erreur"] = "injoignable"
            with verrou:
                resultats.append(ligne)
                compteur[0] += 1
                if compteur[0] % 50 == 0:
                    print("  %d/%d" % (compteur[0], len(taches)), flush=True)
            q.task_done()

    fils_l = [threading.Thread(target=ouvrier, daemon=True) for _ in range(fils)]
    [f.start() for f in fils_l]
    [f.join() for f in fils_l]

    with open(sortie, "w", encoding="utf-8") as f:
        for r in resultats:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    avec = sum(1 for r in resultats if r.get("ref"))
    print("ecrit %d lignes, %d avec reference" % (len(resultats), avec))

if __name__ == "__main__":
    a = sys.argv[1:]
    main(a[0], a[1], int(a[2]) if len(a) > 2 else 0,
         int(a[3]) if len(a) > 3 else None,
         int(a[4]) if len(a) > 4 else 3)
