-- V53 : galerie d'images et video de demonstration sur les produits
--
-- POURQUOI
-- Le catalogue ne portait qu'une seule image par produit, importee depuis l'ancien site. Cette
-- dependance est le dernier fil qui nous relie a lui. En donnant a l'equipe les moyens de
-- televerser elle-meme plusieurs visuels et une video, on cesse d'avoir besoin de qui que ce
-- soit d'autre : les 111 produits sans visuel propre se traitent en interne.
--
-- ECART ASSUME AVEC LA SPECIFICATION
-- La specification decrivait `id BIGSERIAL` et `product_id BIGINT REFERENCES products(id)`.
-- `products.id` est un **uuid** : la cle etrangere aurait ete refusee au demarrage et Flyway
-- aurait bloque l'application entiere. Les identifiants suivent donc la convention du projet —
-- `uuid PRIMARY KEY DEFAULT gen_random_uuid()`, comme toutes les tables depuis la V26.

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS video_url          varchar(1000),
    ADD COLUMN IF NOT EXISTS video_provider     varchar(20),
    ADD COLUMN IF NOT EXISTS is_video_promoted  boolean NOT NULL DEFAULT false;

ALTER TABLE products DROP CONSTRAINT IF EXISTS products_video_provider_check;
ALTER TABLE products ADD CONSTRAINT products_video_provider_check CHECK (
    video_provider IS NULL OR video_provider IN ('CLOUDINARY', 'YOUTUBE', 'VIMEO', 'LOOM')
);

-- Une video sans hebergeur ne se lit pas : le lecteur a besoin de savoir s'il doit poser une
-- balise <video> ou une <iframe>. Et un hebergeur sans video ne designe rien.
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_video_coherent;
ALTER TABLE products ADD CONSTRAINT products_video_coherent CHECK (
    (video_url IS NULL AND video_provider IS NULL)
 OR (video_url IS NOT NULL AND length(trim(video_url)) > 0 AND video_provider IS NOT NULL)
);

-- « Mettre la video en vedette » suppose qu'il y en ait une. Sans cette regle, un produit
-- pourrait etre marque comme anime sur la page d'accueil et n'y afficher qu'un cadre vide.
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_video_promotion_check;
ALTER TABLE products ADD CONSTRAINT products_video_promotion_check CHECK (
    is_video_promoted = false OR video_url IS NOT NULL
);

-- -------------------------------------------------------------------------------------
-- Galerie de details : plusieurs visuels par produit, ordonnes.
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_gallery_images (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    -- uuid et non BIGINT : c'est le type reel de products.id.
    -- ON DELETE CASCADE : une galerie n'a aucun sens sans son produit.
    product_id  uuid NOT NULL REFERENCES products(id) ON DELETE CASCADE,

    image_url   varchar(1000) NOT NULL,

    -- Identifiant Cloudinary, conserve pour pouvoir supprimer le fichier a la source quand
    -- l'administration retire un visuel. Sans lui, retirer une image de la galerie laisserait
    -- le fichier orphelin dans l'espace de stockage, facture et invisible.
    public_id   varchar(255),

    display_order int NOT NULL DEFAULT 0,
    caption     varchar(255),
    created_at  timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pgi_image_url_check CHECK (length(trim(image_url)) > 0),
    CONSTRAINT pgi_display_order_check CHECK (display_order >= 0)
);

-- Deux fois le meme visuel dans une galerie est un double clic, jamais une intention : le
-- client verrait la meme photo deux fois dans le carrousel.
CREATE UNIQUE INDEX IF NOT EXISTS uq_gallery_product_image
    ON product_gallery_images (product_id, image_url);

-- L'ordre d'affichage fait partie de la lecture : l'index le porte pour eviter un tri en
-- memoire a chaque ouverture de fiche produit.
CREATE INDEX IF NOT EXISTS idx_gallery_product_order
    ON product_gallery_images (product_id, display_order);

COMMENT ON TABLE product_gallery_images IS
    'Visuels secondaires d''un produit, ordonnes. L''image principale reste products.image_url : '
    'elle sert de vignette au catalogue, ou une seule image doit etre choisie.';

COMMENT ON COLUMN products.is_video_promoted IS
    'Joue la video en boucle sur la carte produit des sections Promotion et Hero. Exige une '
    'video : la contrainte products_video_promotion_check l''impose.';
