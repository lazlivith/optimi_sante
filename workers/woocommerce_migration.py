import os
import logging
from woocommerce import API
import psycopg2
from psycopg2.extras import DictCursor
from dotenv import load_dotenv
from bs4 import BeautifulSoup
import re

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def load_config():
    load_dotenv()
    return {
        "db": {
            "host": os.getenv("DB_HOST", "127.0.0.1"),
            "port": os.getenv("DB_PORT", "5432"),
            "name": os.getenv("DB_NAME", "optimisante_db"),
            "user": os.getenv("DB_USER", "postgres"),
            "password": os.getenv("DB_PASSWORD", "Investx2026")
        },
        "wc": {
            "url": os.getenv("WC_URL"),
            "key": os.getenv("WC_CONSUMER_KEY"),
            "secret": os.getenv("WC_CONSUMER_SECRET")
        }
    }

def get_db_connection(config):
    return psycopg2.connect(
        host=config['db']['host'],
        port=config['db']['port'],
        dbname=config['db']['name'],
        user=config['db']['user'],
        password=config['db']['password']
    )

def setup_tenant(conn):
    """Ensure FR_MAIN tenant exists and return its UUID."""
    tenant_code = 'FR_MAIN'
    tenant_name = 'Optimi Santé France'
    tenant_domain = 'optimi-sante.fr'

    with conn.cursor() as cur:
        # Check if tenant exists
        cur.execute("SELECT id FROM tenants WHERE code = %s", (tenant_code,))
        result = cur.fetchone()

        if result:
            tenant_id = result[0]
            logger.info(f"Found existing tenant {tenant_code} with ID: {tenant_id}")
            return tenant_id

        # Insert if not exists
        logger.info(f"Tenant {tenant_code} not found. Creating fallback tenant.")
        cur.execute(
            """
            INSERT INTO tenants (code, name, domain) 
            VALUES (%s, %s, %s) 
            RETURNING id
            """,
            (tenant_code, tenant_name, tenant_domain)
        )
        tenant_id = cur.fetchone()[0]
        conn.commit()
        return tenant_id

def clean_html(raw_html):
    if not raw_html:
        return ""
    # Use BeautifulSoup to strip HTML tags cleanly
    soup = BeautifulSoup(raw_html, "html.parser")
    text = soup.get_text(separator='\n')
    # Clean up excessive newlines
    text = re.sub(r'\n\s*\n', '\n\n', text).strip()
    return text

def fetch_all_pages(wcapi, endpoint):
    """Fetch all pages from a WooCommerce REST API endpoint."""
    items = []
    page = 1
    per_page = 100

    while True:
        logger.info(f"Fetching {endpoint} - Page {page}...")
        response = wcapi.get(endpoint, params={"page": page, "per_page": per_page})
        
        if response.status_code != 200:
            logger.error(f"Failed to fetch {endpoint}: {response.status_code} - {response.text}")
            break

        data = response.json()
        if not data:
            break

        items.extend(data)
        
        # Check if there are more pages using X-WP-TotalPages header
        total_pages = int(response.headers.get('X-WP-TotalPages', 1))
        if page >= total_pages:
            break
            
        page += 1

    logger.info(f"Total items fetched from {endpoint}: {len(items)}")
    return items

def sync_categories(conn, wcapi, tenant_id):
    """Sync WooCommerce categories to PostgreSQL."""
    categories = fetch_all_pages(wcapi, "products/categories")
    
    slug_to_uuid = {}
    
    with conn.cursor() as cur:
        for cat in categories:
            name = str(cat.get('name') or '')[:150]
            slug = str(cat.get('slug') or '')[:150]
            
            cur.execute(
                """
                INSERT INTO categories (tenant_id, name, slug)
                VALUES (%s, %s, %s)
                ON CONFLICT (tenant_id, slug) 
                DO UPDATE SET name = EXCLUDED.name
                RETURNING id
                """,
                (tenant_id, name, slug)
            )
            cat_id = cur.fetchone()[0]
            slug_to_uuid[slug] = cat_id
            
        conn.commit()
    
    logger.info(f"Successfully synced {len(slug_to_uuid)} categories.")
    return slug_to_uuid

def sync_products(conn, wcapi, tenant_id, category_slug_to_uuid):
    """Sync WooCommerce products to PostgreSQL."""
    products = fetch_all_pages(wcapi, "products")
    
    synced_count = 0
    with conn.cursor() as cur:
        for prod in products:
            prod_id = prod.get('id')
            name = str(prod.get('name') or '')[:255]
            slug = str(prod.get('slug') or '')[:255]
            raw_description = prod.get('description', '')
            description = clean_html(raw_description)
            
            sku = prod.get('sku')
            if not sku:
                sku = f"OPT-WC-{prod_id}"
            sku = str(sku)[:100]
                
            price_str = prod.get('price')
            base_price = float(price_str) if price_str else 0.0
            
            stock_quantity = prod.get('stock_quantity')
            if stock_quantity is None:
                stock_quantity = 0
                
            # Get first category ID if available
            category_id = None
            prod_categories = prod.get('categories', [])
            if prod_categories:
                # Use the first category
                first_cat_slug = prod_categories[0].get('slug')
                category_id = category_slug_to_uuid.get(first_cat_slug)
            
            cur.execute(
                """
                INSERT INTO products (tenant_id, sku, name, slug, description, base_price, stock_quantity, category_id)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (sku) 
                DO UPDATE SET 
                    name = EXCLUDED.name,
                    slug = EXCLUDED.slug,
                    description = EXCLUDED.description,
                    base_price = EXCLUDED.base_price,
                    stock_quantity = EXCLUDED.stock_quantity,
                    category_id = EXCLUDED.category_id,
                    deleted_at = NULL
                """,
                (tenant_id, sku, name, slug, description, base_price, stock_quantity, category_id)
            )
            synced_count += 1
            
        conn.commit()
    
    logger.info(f"Successfully synced {synced_count} products.")

def main():
    logger.info("Starting WooCommerce to PostgreSQL migration...")
    config = load_config()
    
    if not config['wc']['url'] or not config['wc']['key']:
        logger.error("WooCommerce API credentials not found in environment variables.")
        return

    wcapi = API(
        url=config['wc']['url'],
        consumer_key=config['wc']['key'],
        consumer_secret=config['wc']['secret'],
        version="wc/v3",
        timeout=30
    )

    try:
        conn = get_db_connection(config)
        logger.info("Connected to PostgreSQL database.")
        
        tenant_id = setup_tenant(conn)
        category_map = sync_categories(conn, wcapi, tenant_id)
        sync_products(conn, wcapi, tenant_id, category_map)
        
        logger.info("Migration completed successfully!")
        
    except Exception as e:
        logger.error(f"An error occurred during migration: {e}", exc_info=True)
    finally:
        if 'conn' in locals() and conn:
            conn.close()
            logger.info("Database connection closed.")

if __name__ == "__main__":
    main()
