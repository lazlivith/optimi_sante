ALTER TABLE products ADD COLUMN IF NOT EXISTS image_url VARCHAR(512);

-- Set some dummy image URLs and realistic prices for existing catalog items
UPDATE products 
SET image_url = 'https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/default-medical-equipment.jpg',
    base_price = 450.00
WHERE sku = 'OPT-MC-41847';

UPDATE products 
SET image_url = 'https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/microscope.jpg',
    base_price = 1200.00
WHERE sku = 'OPT-MC-41835';

UPDATE products 
SET image_url = 'https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/stetho.jpg',
    base_price = 120.00
WHERE sku = '101350001';

-- Generic fallback for any other products that might exist
UPDATE products 
SET image_url = 'https://res.cloudinary.com/vyvufvnw/image/upload/v1724000000/catalog/medical-placeholder.jpg',
    base_price = 100.00
WHERE image_url IS NULL;
