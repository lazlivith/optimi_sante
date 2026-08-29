#!/usr/bin/env python3
"""
import_woocommerce_csv.py
─────────────────────────────────────────────────────────────────────────────
Migre les 1 518 produits WooCommerce vers PostgreSQL (Docker) + Cloudinary.

Pour chaque ligne du CSV :
  1. Extrait SKU, nom, prix régulier, URL image d'origine, catégorie
  2. Upload l'image sur Cloudinary (folder: optimisante/products/)
     - Si l'image WP est accessible  → upload direct
     - Si l'image est morte (404)    → upload d'un visuel Unsplash thématique
  3. Met à jour base_price et image_url dans PostgreSQL par batch

Usage :
  python workers/import_woocommerce_csv.py

Logs : workers/import_log_YYYYMMDD_HHMMSS.csv
"""

import csv
import os
import re
import sys
import time
import logging
import datetime
import unicodedata
from pathlib import Path

import requests
import cloudinary
import cloudinary.uploader
import psycopg2
from tqdm import tqdm
from dotenv import load_dotenv

# Charge workers/.env (jamais commité — voir workers/.env.example pour la marche a suivre)
load_dotenv(Path(__file__).parent / ".env")

# ─────────────────────────────────────────────────────────────────
# CONFIGURATION
# ─────────────────────────────────────────────────────────────────
CSV_PATH = Path(r"D:\OptimiSanté_Projet\optimi-sante\wc-product-export-23-8-2026-1787451241798.csv")

# PostgreSQL (Docker : port 5051 exposé sur localhost) — valeurs réelles dans workers/.env
DB_CONFIG = {
    "host":            os.getenv("DB_HOST", "localhost"),
    "port":            int(os.getenv("DB_PORT", "5051")),
    "dbname":          os.getenv("DB_NAME", "optimisante_db"),
    "user":            os.getenv("DB_USER", "postgres"),
    "password":        os.getenv("DB_PASSWORD", ""),
    "connect_timeout": 10,
}

# Cloudinary — valeurs réelles dans workers/.env
CLOUDINARY_CONFIG = {
    "cloud_name": os.getenv("CLOUDINARY_CLOUD_NAME", ""),
    "api_key":    os.getenv("CLOUDINARY_API_KEY", ""),
    "api_secret": os.getenv("CLOUDINARY_API_SECRET", ""),
}

# Batch size pour les UPDATE PostgreSQL
BATCH_SIZE = 50

# Timeout HTTP pour les images WooCommerce (secondes)
IMAGE_TIMEOUT = 8

# ─────────────────────────────────────────────────────────────────
# FALLBACKS UNSPLASH par catégorie (images libres de droits)
# ─────────────────────────────────────────────────────────────────
CATEGORY_FALLBACKS = {
    "diagnostic":          "https://images.unsplash.com/photo-1584820927498-cfe5211fd8bf?w=600",
    "tensiometre":         "https://images.unsplash.com/photo-1576091160550-2173dba999ef?w=600",
    "oxymetr":             "https://images.unsplash.com/photo-1576091160550-2173dba999ef?w=600",
    "stethoscop":          "https://images.unsplash.com/photo-1584820927498-cfe5211fd8bf?w=600",
    "autoclave":           "https://images.unsplash.com/photo-1581093458791-9d42cc4f33b2?w=600",
    "sterilisateur":       "https://images.unsplash.com/photo-1581093458791-9d42cc4f33b2?w=600",
    "centrifug":           "https://images.unsplash.com/photo-1579154204601-01588f351e67?w=600",
    "echograph":           "https://images.unsplash.com/photo-1559757175-5700dde675bc?w=600",
    "ecg":                 "https://images.unsplash.com/photo-1628348070889-cb656235b4eb?w=600",
    "cardiolog":           "https://images.unsplash.com/photo-1628348070889-cb656235b4eb?w=600",
    "defibrillateur":      "https://images.unsplash.com/photo-1559757148-5c350d0d3c56?w=600",
    "urgence":             "https://images.unsplash.com/photo-1559757148-5c350d0d3c56?w=600",
    "fauteuil":            "https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?w=600",
    "lit":                 "https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?w=600",
    "divan":               "https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?w=600",
    "table":               "https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?w=600",
    "microscop":           "https://images.unsplash.com/photo-1576671081837-49000212a370?w=600",
    "laboratoire":         "https://images.unsplash.com/photo-1579154204601-01588f351e67?w=600",
    "chirurgi":            "https://images.unsplash.com/photo-1551601651-2a8555f1a136?w=600",
    "pince":               "https://images.unsplash.com/photo-1551601651-2a8555f1a136?w=600",
    "ciseaux":             "https://images.unsplash.com/photo-1551601651-2a8555f1a136?w=600",
    "blouse":              "https://images.unsplash.com/photo-1582719471384-894fbb16e074?w=600",
    "masque":              "https://images.unsplash.com/photo-1586897338786-2c15498a46c8?w=600",
    "gant":                "https://images.unsplash.com/photo-1586897338786-2c15498a46c8?w=600",
    "froid":               "https://images.unsplash.com/photo-1585771724684-38269d6639fd?w=600",
    "refrigerateur":       "https://images.unsplash.com/photo-1585771724684-38269d6639fd?w=600",
    "kinesitherapie":      "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=600",
    "podologie":           "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=600",
    "optique":             "https://images.unsplash.com/photo-1516981879613-9f5da904015f?w=600",
    "otoscop":             "https://images.unsplash.com/photo-1584820927498-cfe5211fd8bf?w=600",
    "default":             "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=600",
}

# ─────────────────────────────────────────────────────────────────
# LOGGING
# ─────────────────────────────────────────────────────────────────
LOG_DIR = Path("D:/OptimiSanté_Projet/optimi-sante/workers")
LOG_DIR.mkdir(parents=True, exist_ok=True)
ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
LOG_FILE = LOG_DIR / f"import_log_{ts}.csv"

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    handlers=[logging.StreamHandler(sys.stdout)],
)
# Force UTF-8 on Windows consoles
if sys.stdout.encoding and sys.stdout.encoding.lower() != 'utf-8':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
log = logging.getLogger(__name__)


# ─────────────────────────────────────────────────────────────────
# HELPERS
# ─────────────────────────────────────────────────────────────────
def normalize(text: str) -> str:
    """Lowercase + strip accents for fuzzy matching."""
    nfkd = unicodedata.normalize("NFKD", text)
    return "".join(c for c in nfkd if not unicodedata.combining(c)).lower()


def parse_price(raw: str) -> float | None:
    """Converts French decimal '148,43' or '1 200,00' to float 148.43."""
    cleaned = raw.strip().replace("\xa0", "").replace(" ", "").replace(",", ".")
    try:
        v = float(cleaned)
        return v if v > 0 else None
    except ValueError:
        return None


def get_fallback_url(name: str, category: str) -> str:
    """Returns the best matching Unsplash fallback image URL."""
    text = normalize(name + " " + category)
    for keyword, url in CATEGORY_FALLBACKS.items():
        if keyword in text:
            return url
    return CATEGORY_FALLBACKS["default"]


def image_is_accessible(url: str) -> bool:
    """HEAD request to check if image URL is alive."""
    if not url or not url.startswith("http"):
        return False
    try:
        r = requests.head(url, timeout=IMAGE_TIMEOUT, allow_redirects=True)
        return r.status_code == 200
    except Exception:
        return False


def upload_to_cloudinary(image_url: str, public_id_hint: str) -> str | None:
    """Uploads image_url to Cloudinary. Returns secure_url or None."""
    try:
        result = cloudinary.uploader.upload(
            image_url,
            folder="optimisante/products",
            public_id=public_id_hint,
            overwrite=True,
            resource_type="image",
            transformation=[{"width": 800, "height": 800, "crop": "limit", "quality": "auto"}],
        )
        return result.get("secure_url")
    except Exception as e:
        log.warning(f"  Cloudinary upload failed for '{public_id_hint}': {e}")
        return None


# ─────────────────────────────────────────────────────────────────
# MAIN
# ─────────────────────────────────────────────────────────────────
def main():
    # Configure Cloudinary
    cloudinary.config(**CLOUDINARY_CONFIG)
    log.info("✅ Cloudinary configured.")

    # Connect to PostgreSQL
    conn = psycopg2.connect(**DB_CONFIG)
    conn.autocommit = False
    cur = conn.cursor()
    log.info("✅ Connected to PostgreSQL (optimisante_db).")

    # Fetch existing SKUs from DB for matching
    cur.execute("SELECT sku FROM products WHERE deleted_at IS NULL;")
    db_skus = {row[0].strip() for row in cur.fetchall()}
    log.info(f"📦 {len(db_skus)} products found in DB.")

    # Parse CSV
    rows_to_process = []
    with open(CSV_PATH, encoding="utf-8", errors="replace") as f:
        reader = csv.DictReader(f)
        keys = reader.fieldnames or []

        # Map column names (handle encoding artifacts)
        col_sku = next((k for k in keys if "ugs" in k.lower()), None)
        col_name = next((k for k in keys if normalize(k) == "nom"), None)
        col_price = next((k for k in keys if "tarif" in k.lower() and "gul" in k.lower()), None)
        col_img = next((k for k in keys if normalize(k) == "images"), None)
        col_cat = next((k for k in keys if "cat" in k.lower() and "gor" in k.lower()), None)

        log.info(f"📊 CSV columns → SKU:{col_sku} | Name:{col_name} | Price:{col_price} | Img:{col_img} | Cat:{col_cat}")

        for row in reader:
            sku = row.get(col_sku, "").strip() if col_sku else ""
            name = row.get(col_name, "").strip() if col_name else ""
            price_raw = row.get(col_price, "").strip() if col_price else ""
            img_url = row.get(col_img, "").strip() if col_img else ""
            cat = row.get(col_cat, "").strip() if col_cat else ""

            if not sku or not name:
                continue

            price = parse_price(price_raw)
            rows_to_process.append({
                "sku": sku,
                "name": name,
                "price": price,
                "img_url": img_url,
                "cat": cat,
            })

    log.info(f"📄 {len(rows_to_process)} rows parsed from CSV.")

    # Stats
    matched = 0
    not_found = 0
    img_success = 0
    img_fallback = 0
    img_fail = 0

    # Log file
    log_rows = []
    batch_updates = []

    for item in tqdm(rows_to_process, desc="Processing products", unit="prod"):
        sku = item["sku"]

        if sku not in db_skus:
            not_found += 1
            log_rows.append({"sku": sku, "name": item["name"], "status": "NOT_IN_DB", "cloudinary_url": "", "price": item["price"]})
            continue

        matched += 1
        price = item["price"]
        src_img = item["img_url"]
        name = item["name"]
        cat = item["cat"]

        # ── Image Upload Logic ──
        cloudinary_url = None
        safe_sku = re.sub(r"[^a-zA-Z0-9_-]", "_", sku)[:80]

        if src_img and image_is_accessible(src_img):
            cloudinary_url = upload_to_cloudinary(src_img, f"prod_{safe_sku}")
            if cloudinary_url:
                img_success += 1
            else:
                img_fail += 1
        else:
            # Image inaccessible → fallback Unsplash
            fallback_url = get_fallback_url(name, cat)
            cloudinary_url = upload_to_cloudinary(fallback_url, f"prod_fallback_{safe_sku}")
            if cloudinary_url:
                img_fallback += 1
            else:
                img_fail += 1
                cloudinary_url = fallback_url  # Use Unsplash URL directly as last resort

        batch_updates.append({
            "sku": sku,
            "price": price,
            "image_url": cloudinary_url,
        })
        log_rows.append({"sku": sku, "name": name, "status": "OK", "cloudinary_url": cloudinary_url or "", "price": price})

        # ── Flush batch to PostgreSQL ──
        if len(batch_updates) >= BATCH_SIZE:
            _flush_batch(cur, conn, batch_updates)
            batch_updates.clear()

    # Flush remaining
    if batch_updates:
        _flush_batch(cur, conn, batch_updates)

    cur.close()
    conn.close()

    # ── Write log CSV ──
    with open(LOG_FILE, "w", newline="", encoding="utf-8") as lf:
        writer = csv.DictWriter(lf, fieldnames=["sku", "name", "status", "price", "cloudinary_url"])
        writer.writeheader()
        writer.writerows(log_rows)

    log.info("\n" + "─" * 60)
    log.info(f"✅ IMPORT TERMINÉ")
    log.info(f"  • Produits CSV parsés    : {len(rows_to_process)}")
    log.info(f"  • Matchés en DB         : {matched}")
    log.info(f"  • Non trouvés en DB     : {not_found}")
    log.info(f"  • Images WP uploadées   : {img_success}")
    log.info(f"  • Images fallback       : {img_fallback}")
    log.info(f"  • Erreurs image         : {img_fail}")
    log.info(f"  • Log sauvegardé        : {LOG_FILE}")


def _flush_batch(cur, conn, batch: list):
    """Executes batch UPDATE on PostgreSQL."""
    for item in batch:
        sets = []
        params = []
        if item["price"] is not None:
            sets.append("base_price = %s")
            params.append(item["price"])
        if item["image_url"]:
            sets.append("image_url = %s")
            params.append(item["image_url"])
        if not sets:
            continue
        params.append(item["sku"])
        sql = f"UPDATE products SET {', '.join(sets)} WHERE sku = %s AND deleted_at IS NULL;"
        try:
            cur.execute(sql, params)
        except Exception as e:
            log.warning(f"  DB update failed for SKU {item['sku']}: {e}")
    try:
        conn.commit()
        log.debug(f"  Batch of {len(batch)} committed.")
    except Exception as e:
        conn.rollback()
        log.error(f"  Batch commit failed: {e}")


if __name__ == "__main__":
    main()
