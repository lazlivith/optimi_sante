# M0 — cadrage du chantier catalogue (aucun code)

**Appliquer D0** (`docs/maquette_catalogue_optimisante/prompt_dette_technique.md`), puis
`docs/plan_integration_catalogue.md` § 4 « M0 ». En cas de contradiction, arrête-toi et dis-le.

Maquettes : `docs/maquette_catalogue_optimisante/maquette_catalogue/` — `.dc.html` un par
écran, `canvas.json` donne l'ordre. Références visuelles et fonctionnelles, **pas du code à
copier** : on les traduit dans nos composants React existants et nos jetons de charte.

Écrans : `Main` = page Catalogue (ordinateur) ; `Famille` = page famille (Auto-surveillance) ;
`Rayon` = page rayon (Tensiomètres) ; `Produit` = fiche produit ; `MegaMenu` = menu au survol
de « Catalogue » ; `*Mobile` = versions téléphone (`CatalogueMobile`, `FamilleMobile`,
`RayonMobile`, `FiltresMobile`, `ProduitMobile`).

## ANALYSE SEULEMENT

Sortie : `docs/analyse_integration_maquette.md`. **Aucune branche de code, aucune migration,
aucune écriture en base.**

1. **Écran par écran**, les éléments, et pour chacun : ce qui existe et se réutilise, ce qui
   est à modifier, ce qui est à créer. L'existant à examiner nommément :
   `NAVIGATION_PRINCIPALE`, `MenuMobile`, `MenuServices`, `CatalogPage`, `CategoryPage` et
   `lib/filtresRayon.ts`, `ProductDetailPage`, `ProductGallery`, `ProductImage`,
   `PromoProductVisual`, la carte produit, `ShippingResource` (`/destinations`,
   `/estimation`), la mention DAP du panier.
2. **Dépendances de données.** Pour chaque élément : la donnée affichée, et son existence en
   base **aujourd'hui**. Le plan en a déjà tranché sept (arbre, marque, garantie, documents,
   caractéristiques, délai, WhatsApp) : vérifie-les plutôt que de les reprendre, et complète.
3. **Les 39 textes entre crochets.** Liste-les un par un avec la donnée qui les alimenterait
   et son existence. Un élément sans donnée est **masqué** — jamais vide, jamais inventé.
4. **Trois vérifications en lecture seule sur la production**, à rapporter avec leur sortie :
   - la requête des visuels factices ignorés par le navigateur (`plan_integration_catalogue.md`
     § 6, à reprendre telle quelle) ;
   - le dénombrement des produits par catégorie, pour dimensionner CAT : combien de catégories
     à 0, à 1–2, et la distribution ;
   - que `categoryId` est bien le seul paramètre de filtrage par catégorie sur la route
     publique, et qu'aucun autre chemin ne contourne `ProductSpecification`.
5. **Confirme ou corrige les chiffres du plan** (§ 1) : V81/V82, 1 333 Ko, 167 Ko, 247, 211
   catégories. Un chiffre périmé se corrige dans le plan, pas dans l'analyse seule.
6. **La source unique des visuels factices** (§ 6) : confirme la proposition, ou propose mieux,
   avec le coût en kilooctets rendus.

## CE QUE LE RAPPORT MONTRE

Section 7 de D0, sauf le build et les captures — il n'y a pas de code. Donc : les sorties
réelles des trois requêtes, ce qui n'a pas pu être vérifié et pourquoi, et la liste des
éléments qui seront masqués lot par lot.
