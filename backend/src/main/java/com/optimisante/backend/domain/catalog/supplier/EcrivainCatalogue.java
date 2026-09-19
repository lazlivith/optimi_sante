package com.optimisante.backend.domain.catalog.supplier;

import com.optimisante.backend.domain.catalog.entity.Category;
import com.optimisante.backend.domain.catalog.entity.Product;
import com.optimisante.backend.domain.catalog.repository.CategoryRepository;
import com.optimisante.backend.domain.catalog.repository.ProductRepository;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/**
 * Décide du sort de chaque ligne d'un catalogue fournisseur, puis l'écrit.
 *
 * <p>Séparé de {@link CatalogImportService} pour deux raisons : les écritures se font par lots,
 * chacun dans sa propre transaction — un lot qui échoue n'annule pas les 1 900 lignes déjà
 * écrites — et les téléchargements d'images, longs et incertains, se font <b>entre</b> les
 * transactions, jamais pendant.</p>
 *
 * <p><b>La règle qui protège le catalogue existant.</b> Un import ne touche qu'un produit déjà
 * rattaché au fournisseur qui dépose le fichier. Un SKU appartenant à un autre fournisseur, ou
 * aux 1 518 références historiques sans fournisseur, est écarté et signalé — jamais écrasé.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EcrivainCatalogue {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TenantRepository tenantRepository;
    private final SupplierRepository supplierRepository;

    public enum Action {
        CREER, METTRE_A_JOUR, IGNORER
    }

    /**
     * @param produitId    produit existant, nul pour une création
     * @param aDejaImage   vrai si le produit porte déjà un visuel : on ne le retélécharge pas
     * @param motif        pourquoi la ligne est écartée ({@link Action#IGNORER} uniquement)
     */
    public record Decision(FichierCatalogue.Ligne ligne, Action action, UUID produitId,
                           boolean aDejaImage, String motif, MargeCatalogue.Calcul prix, UUID categorieId) {
    }

    public record Resultat(int crees, int misAJour, int ignores, int images, List<String> motifs) {
    }

    /** Ce qu'il faut pour décider : marges des catégories et commission du fournisseur. */
    private record Bareme(Map<String, Category> categoriesParNom, BigDecimal margeFournisseur) {
    }

    /**
     * Ce que l'import ferait de ces lignes, sans rien écrire. Sert à l'analyse comme au traitement :
     * une seule règle, donc l'annonce et l'exécution ne peuvent pas diverger.
     */
    @Transactional(readOnly = true)
    public List<Decision> decider(UUID tenantId, UUID supplierId, List<FichierCatalogue.Ligne> lignes) {
        Bareme bareme = bareme(tenantId, supplierId);
        List<String> skus = lignes.stream()
                .filter(l -> l.erreur() == null)
                .map(l -> l.sku().toLowerCase(Locale.ROOT))
                .distinct().toList();
        Map<String, Map<String, Object>> existants = new HashMap<>();
        // Par paquets : une clause IN de 2 000 valeurs est refusée par certains pilotes.
        for (int debut = 0; debut < skus.size(); debut += 500) {
            productRepository.trouverParSku(tenantId, skus.subList(debut, Math.min(debut + 500, skus.size())))
                    .forEach(ligne -> existants.put(String.valueOf(ligne.get("sku")), ligne));
        }

        Set<String> vusDansLeFichier = new HashSet<>();
        List<Decision> decisions = new ArrayList<>();
        for (FichierCatalogue.Ligne ligne : lignes) {
            if (ligne.erreur() != null) {
                continue;
            }
            String cle = ligne.sku().toLowerCase(Locale.ROOT);
            if (!vusDansLeFichier.add(cle)) {
                decisions.add(ecartee(ligne, "Référence en double dans le fichier : seule la première ligne est retenue."));
                continue;
            }
            Map<String, Object> existant = existants.get(cle);
            if (existant == null) {
                decisions.add(retenue(ligne, Action.CREER, null, false, bareme));
                continue;
            }
            if (existant.get("deleted_at") != null) {
                decisions.add(ecartee(ligne, "Référence appartenant à un produit supprimé."));
                continue;
            }
            Object proprietaire = existant.get("supplier_id");
            if (proprietaire == null) {
                decisions.add(ecartee(ligne, "Référence déjà utilisée par un produit du catalogue, sans fournisseur : non modifiée."));
                continue;
            }
            if (!supplierId.equals(UUID.fromString(String.valueOf(proprietaire)))) {
                decisions.add(ecartee(ligne, "Référence appartenant à un autre fournisseur."));
                continue;
            }
            if (Boolean.FALSE.equals(existant.get("is_active"))) {
                decisions.add(ecartee(ligne, "Produit désactivé : réactivez-le avant de le mettre à jour."));
                continue;
            }
            Object image = existant.get("image_url");
            decisions.add(retenue(ligne, Action.METTRE_A_JOUR, UUID.fromString(String.valueOf(existant.get("id"))),
                    image != null && !String.valueOf(image).isBlank(), bareme));
        }
        return decisions;
    }

    /** Ligne écartée : aucun prix n'a de sens à calculer. */
    private static Decision ecartee(FichierCatalogue.Ligne ligne, String motif) {
        return new Decision(ligne, Action.IGNORER, null, false, motif, null, null);
    }

    /**
     * Ligne retenue : le prix de vente est arrêté ICI, à la décision, et non à l'écriture. C'est ce
     * qui permet à l'analyse d'annoncer exactement le prix qui sera porté au catalogue.
     */
    private static Decision retenue(FichierCatalogue.Ligne ligne, Action action, UUID produitId,
                                    boolean aDejaImage, Bareme bareme) {
        Category categorie = ligne.categorie() == null ? null
                : bareme.categoriesParNom().get(ligne.categorie().trim().toLowerCase(Locale.ROOT));
        MargeCatalogue.Calcul prix = MargeCatalogue.calculer(ligne.prix(), ligne.prixAchat(),
                categorie == null ? null : categorie.getMarginRate(), bareme.margeFournisseur());
        return new Decision(ligne, action, produitId, aDejaImage, null, prix,
                categorie == null ? null : categorie.getId());
    }

    private Bareme bareme(UUID tenantId, UUID supplierId) {
        Map<String, Category> parNom = new HashMap<>();
        categoryRepository.findByTenantId(tenantId)
                .forEach(c -> parNom.putIfAbsent(c.getName().trim().toLowerCase(Locale.ROOT), c));
        BigDecimal commission = supplierRepository.findById(supplierId)
                .map(Supplier::getCommissionRate).orElse(BigDecimal.ZERO);
        return new Bareme(parNom, commission);
    }

    /**
     * Écrit un lot. Transaction propre au lot : ce qui est écrit l'est définitivement, même si un
     * lot suivant échoue.
     *
     * @param visuels clé de stockage du visuel principal, par SKU ; vide si aucune image n'a pu
     *                être récupérée pour la ligne
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Resultat ecrire(UUID tenantId, UUID supplierId, List<Decision> lot, Map<String, String> visuels) {
        var tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant introuvable."));
        // Référence paresseuse : le fournisseur n'a pas besoin d'être chargé pour être rattaché.
        Supplier fournisseur = supplierRepository.getReferenceById(supplierId);

        int crees = 0;
        int misAJour = 0;
        int ignores = 0;
        int images = 0;
        List<String> motifs = new ArrayList<>();

        for (Decision decision : lot) {
            FichierCatalogue.Ligne ligne = decision.ligne();
            if (decision.action() == Action.IGNORER) {
                ignores++;
                motifs.add("Ligne " + ligne.numero() + " (" + ligne.sku() + ") : " + decision.motif());
                continue;
            }
            Category categorie = categorie(tenantId, ligne.categorie(), ligne, motifs);
            String visuel = visuels.get(ligne.sku());
            MargeCatalogue.Calcul prix = decision.prix();
            if (prix.origine() == MargeCatalogue.Origine.AUCUNE && ligne.prixAchat() != null) {
                motifs.add("Ligne " + ligne.numero() + " (" + ligne.sku() + ") : aucune marge définie "
                        + "(ni sur la catégorie, ni sur le fournisseur), prix d'achat porté tel quel.");
            }

            if (decision.action() == Action.CREER) {
                Product produit = Product.builder()
                        .tenant(tenant)
                        .sku(ligne.sku())
                        .name(ligne.nom())
                        .slug(slugUnique(tenantId, ligne.nom()))
                        .description(ligne.description())
                        .basePrice(prix.prixVente())
                        .purchasePrice(ligne.prixAchat())
                        .stockQuantity(ligne.stock() != null ? ligne.stock() : 0)
                        .category(categorie)
                        .imageUrl(visuel)
                        .supplier(fournisseur)
                        .build();
                productRepository.save(produit);
                crees++;
            } else {
                Product produit = productRepository.findById(decision.produitId()).orElse(null);
                if (produit == null) {
                    ignores++;
                    motifs.add("Ligne " + ligne.numero() + " (" + ligne.sku() + ") : produit introuvable au moment de l'écriture.");
                    continue;
                }
                // Seules les colonnes présentes dans le fichier écrasent la fiche : un fichier sans
                // colonne description ne doit pas vider les descriptions saisies à la main.
                produit.setName(ligne.nom());
                produit.setBasePrice(prix.prixVente());
                if (ligne.prixAchat() != null) {
                    produit.setPurchasePrice(ligne.prixAchat());
                }
                if (ligne.description() != null) {
                    produit.setDescription(ligne.description());
                }
                if (ligne.stock() != null) {
                    produit.setStockQuantity(ligne.stock());
                }
                if (categorie != null) {
                    produit.setCategory(categorie);
                }
                if (visuel != null) {
                    produit.setImageUrl(visuel);
                }
                productRepository.save(produit);
                misAJour++;
            }
            if (visuel != null) {
                images++;
            }
        }
        return new Resultat(crees, misAJour, ignores, images, motifs);
    }

    /** Catégorie nommée dans le fichier. Inconnue : le produit passe sans, et la ligne le signale. */
    private Category categorie(UUID tenantId, String nom, FichierCatalogue.Ligne ligne, List<String> motifs) {
        if (nom == null || nom.isBlank()) {
            return null;
        }
        return categoryRepository.findFirstByTenantIdAndNameIgnoreCase(tenantId, nom.trim())
                .orElseGet(() -> {
                    motifs.add("Ligne " + ligne.numero() + " (" + ligne.sku() + ") : catégorie « " + nom
                            + " » inconnue, produit rangé sans catégorie.");
                    return null;
                });
    }

    /** Même règle que la création manuelle d'un produit : un slug lisible, unique par tenant. */
    private String slugUnique(UUID tenantId, String nom) {
        String base = Normalizer.normalize(nom, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (base.isBlank()) {
            base = "produit";
        }
        if (productRepository.findByTenantIdAndSlug(tenantId, base).isEmpty()) {
            return base;
        }
        // Un suffixe tiré au sort plutôt qu'une boucle « -2, -3, -4… » : sur deux mille produits aux
        // noms proches, la boucle ferait des milliers de requêtes pour trouver un numéro libre.
        return base + "-" + UUID.randomUUID().toString().substring(0, 6);
    }
}
