-- ═════════════════════════════════════════════════════════════════════════════════════════
-- AUDIT — images dont l'adresse ne porte aucune extension
--
-- LECTURE SEULE. Ne modifie rien.
--
-- Pourquoi : notre compte Cloudinary ne sert une adresse SANS extension que pour le WebP ;
-- pour tout le reste il repond 401, et l'image s'affiche cassee. Le correctif porte le format
-- dans l'adresse a chaque NOUVEAU televersement ; les images deposees avant gardent la leur,
-- et restent cassees. Cette requete dit combien, et lesquelles.
--
-- Une adresse Cloudinary se termine normalement par « .jpg », « .png », « .webp »...  Celles
-- qui s'arretent sur un identifiant sont celles d'avant le correctif.
--
-- ATTENTION — CETTE REQUETE SUR-SIGNALE, et il faut le savoir avant de lire ses resultats.
-- Une adresse sans extension FONCTIONNE si l'image est stockee en WebP : c'est le seul format
-- que notre compte sert ainsi. Or la base ne conserve pas le format. Les lignes ci-dessous
-- sont donc des CANDIDATES, pas des certitudes : pour trancher, il faut ouvrir l'adresse et
-- regarder la reponse — 200 pour une WebP, 401 pour le reste.
--
-- Verifie en local : sur une adresse signalee, l'image etait en WebP et s'affichait.
--
-- Usage :  psql -U <utilisateur> -d <base> -f audit_images_sans_extension.sql
-- ═════════════════════════════════════════════════════════════════════════════════════════

\echo ''
\echo '── Couvertures du blog ──────────────────────────────────────────────────────────'
SELECT id, left(title, 45) AS titre, right(cover_image_url, 50) AS fin_adresse
FROM blog_posts
WHERE cover_image_url LIKE '%cloudinary%'
  AND cover_image_url !~ '\.(jpe?g|png|webp|avif|gif)$'
ORDER BY created_at DESC;

\echo ''
\echo '── Vignettes et galerie des produits ────────────────────────────────────────────'
SELECT 'products.image_url' AS source, count(*) AS cassees
FROM products
WHERE image_url LIKE '%cloudinary%' AND image_url !~ '\.(jpe?g|png|webp|avif|gif)$'
UNION ALL
SELECT 'product_gallery_images.image_url', count(*)
FROM product_gallery_images
WHERE image_url LIKE '%cloudinary%' AND image_url !~ '\.(jpe?g|png|webp|avif|gif)$';

\echo ''
\echo '── Formations : le format n''est pas conserve, le correctif ne les couvre pas ────'
SELECT count(*) FILTER (WHERE image_s3_key IS NOT NULL AND image_s3_key <> '') AS images,
       count(*) FILTER (WHERE video_s3_key IS NOT NULL AND video_s3_key <> '') AS videos
FROM trainings;
