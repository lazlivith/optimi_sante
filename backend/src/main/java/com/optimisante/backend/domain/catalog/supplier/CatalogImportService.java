package com.optimisante.backend.domain.catalog.supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.optimisante.backend.common.storage.DossierStockage;
import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Import d'un catalogue fournisseur : analyse, confirmation, écriture en arrière-plan.
 *
 * <p><b>Deux temps.</b> Le dépôt ne fait que lire : il dit ce qui serait créé, mis à jour, écarté
 * ou refusé, et attend. Rien n'entre au catalogue tant qu'une personne n'a pas confirmé au vu de
 * ces chiffres — un fichier aux colonnes décalées se voit avant, pas après.</p>
 *
 * <p><b>Pourquoi l'arrière-plan.</b> Deux mille lignes et leurs images demandent plusieurs minutes,
 * essentiellement passées à attendre le réseau. Tenir la requête HTTP ouverte ferait expirer le
 * navigateur bien avant la fin ; l'écran suit l'avancement en interrogeant l'import.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogImportService {

    /** Le rapport reste lisible : au-delà, on dit combien de motifs n'ont pas été détaillés. */
    private static final int MOTIFS_MAX = TraitementImportCatalogue.MOTIFS_MAX;

    private final CatalogImportRepository importRepository;
    private final SupplierRepository supplierRepository;
    private final TenantRepository tenantRepository;
    private final EcrivainCatalogue ecrivain;
    private final TraitementImportCatalogue traitement;
    private final StorageService storageService;

    /**
     * Sérialiseur construit ici : ce projet n'expose pas de bean {@code ObjectMapper} (même
     * constat que {@code AiService}, où l'injection faisait échouer le démarrage).
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    // ------------------------------------------------------------------- ANALYSE ----

    /** Dépose le fichier, l'analyse, et n'écrit aucun produit. */
    @Transactional
    public CatalogImport analyser(UUID supplierId, MultipartFile fichier, UUID adminId) {
        UUID tenantId = requireTenant();
        Supplier fournisseur = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Fournisseur introuvable."));
        if (Boolean.FALSE.equals(fournisseur.getIsActive())) {
            throw new IllegalStateException("Ce fournisseur est inactif : réactivez-le avant d'importer son catalogue.");
        }
        if (importRepository.existsBySupplierIdAndStatus(supplierId, CatalogImport.Statut.IMPORT)) {
            throw new IllegalStateException("Un import est déjà en cours pour ce fournisseur : attendez sa fin.");
        }
        byte[] contenu = contenuValide(fichier);

        FichierCatalogue.Lecture lecture = FichierCatalogue.lire(contenu, fichier.getOriginalFilename());
        List<EcrivainCatalogue.Decision> decisions = ecrivain.decider(tenantId, supplierId, lecture.lignes());

        List<String> motifs = new ArrayList<>();
        lecture.lignes().stream().filter(l -> l.erreur() != null).limit(MOTIFS_MAX)
                .forEach(l -> motifs.add("Ligne " + l.numero() + (l.sku() != null ? " (" + l.sku() + ")" : "")
                        + " : " + l.erreur()));
        decisions.stream().filter(d -> d.action() == EcrivainCatalogue.Action.IGNORER)
                .limit(Math.max(0, MOTIFS_MAX - motifs.size()))
                .forEach(d -> motifs.add("Ligne " + d.ligne().numero() + " (" + d.ligne().sku() + ") : " + d.motif()));

        long erreurs = lecture.lignes().stream().filter(l -> l.erreur() != null).count();
        long creations = decisions.stream().filter(d -> d.action() == EcrivainCatalogue.Action.CREER).count();
        long majs = decisions.stream().filter(d -> d.action() == EcrivainCatalogue.Action.METTRE_A_JOUR).count();
        long ignorees = decisions.stream().filter(d -> d.action() == EcrivainCatalogue.Action.IGNORER).count();

        String cle = storageService.uploadFile(contenu, fichier.getOriginalFilename(), DossierStockage.CATALOGUE_IMPORTS);

        CatalogImport imprt = CatalogImport.builder()
                .tenant(tenantRepository.getReferenceById(tenantId))
                .supplier(fournisseur)
                .fileName(fichier.getOriginalFilename())
                .storageKey(cle)
                .status(CatalogImport.Statut.PRET)
                .totalRows(lecture.lignes().size())
                .toCreate((int) creations)
                .toUpdate((int) majs)
                .ignoredRows((int) ignorees)
                .errorRows((int) erreurs)
                .report(enJson(motifs, (int) (erreurs + ignorees), resumeDesMarges(decisions)))
                .createdBy(adminId)
                .build();
        log.info("Import analysé pour le fournisseur {} : {} création(s), {} mise(s) à jour, {} écartée(s), {} refusée(s)",
                fournisseur.getCode(), creations, majs, ignorees, erreurs);
        return importRepository.save(imprt);
    }

    // -------------------------------------------------------------- CONFIRMATION ----

    /** Confirme l'écriture. Le traitement se poursuit en arrière-plan, l'appel rend la main aussitôt. */
    @Transactional
    public CatalogImport confirmer(UUID importId) {
        CatalogImport imprt = importRepository.findByIdForUpdate(importId)
                .orElseThrow(() -> new IllegalArgumentException("Import introuvable."));
        if (imprt.getStatus() != CatalogImport.Statut.PRET) {
            throw new IllegalStateException("Cet import n'est plus en attente de confirmation (état : "
                    + imprt.getStatus() + ").");
        }
        if (imprt.getToCreate() + imprt.getToUpdate() == 0) {
            throw new IllegalStateException("Aucune ligne à écrire : corrigez le fichier et redéposez-le.");
        }
        imprt.setStatus(CatalogImport.Statut.IMPORT);
        imprt.setConfirmedAt(OffsetDateTime.now());
        CatalogImport enregistre = importRepository.save(imprt);

        // APRÈS la validation, et pas avant : le fil d'arrière-plan relit l'import en base. Lancé
        // dans la transaction, il pourrait le lire encore « en attente de confirmation », ou pas du
        // tout. Seuls des identifiants traversent — une entité détachée s'y perdrait.
        UUID importIdFinal = enregistre.getId();
        UUID tenantIdFinal = enregistre.getTenant().getId();
        UUID supplierIdFinal = enregistre.getSupplier().getId();
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        traitement.traiter(importIdFinal, tenantIdFinal, supplierIdFinal);
                    }
                });
        return enregistre;
    }

    @Transactional
    public CatalogImport annuler(UUID importId) {
        CatalogImport imprt = importRepository.findByIdForUpdate(importId)
                .orElseThrow(() -> new IllegalArgumentException("Import introuvable."));
        if (imprt.getStatus() != CatalogImport.Statut.PRET) {
            throw new IllegalStateException("Seul un import en attente de confirmation peut être abandonné.");
        }
        imprt.setStatus(CatalogImport.Statut.ANNULE);
        imprt.setFinishedAt(OffsetDateTime.now());
        return importRepository.save(imprt);
    }

    // ---------------------------------------------------------------------- OUTILS ----

    @Transactional(readOnly = true)
    public List<CatalogImport> historique(UUID supplierId) {
        return importRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId);
    }

    @Transactional(readOnly = true)
    public CatalogImport parId(UUID importId) {
        return importRepository.findById(importId)
                .orElseThrow(() -> new IllegalArgumentException("Import introuvable."));
    }

    private byte[] contenuValide(MultipartFile fichier) {
        if (fichier == null || fichier.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }
        if (fichier.getSize() > FichierCatalogue.TAILLE_MAX_OCTETS) {
            throw new IllegalArgumentException("Le fichier dépasse 15 Mo.");
        }
        String nom = fichier.getOriginalFilename() == null ? "" : fichier.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!nom.endsWith(".csv") && !nom.endsWith(".xlsx") && !nom.endsWith(".xls")) {
            throw new IllegalArgumentException("Déposez un fichier CSV ou Excel (.csv, .xlsx).");
        }
        try {
            return fichier.getBytes();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Fichier illisible.");
        }
    }

    private String enJson(List<String> motifs, int total, String resumeMarges) {
        try {
            Map<String, Object> rapport = new java.util.LinkedHashMap<>();
            rapport.put("motifs", motifs);
            rapport.put("total", total);
            rapport.put("tronque", total > motifs.size());
            rapport.put("marges", resumeMarges);
            return JSON.writeValueAsString(rapport);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * D'où viendra le prix de vente, ligne par ligne, résumé en une phrase.
     *
     * <p>Annoncé AVANT confirmation : une catégorie sans marge et un fournisseur sans commission
     * feraient revendre au prix d'achat, et cela doit se voir avant que deux mille prix ne soient
     * écrits, pas après.</p>
     */
    private static String resumeDesMarges(List<EcrivainCatalogue.Decision> decisions) {
        Map<MargeCatalogue.Origine, Long> parOrigine = decisions.stream()
                .filter(d -> d.prix() != null)
                .collect(java.util.stream.Collectors.groupingBy(d -> d.prix().origine(),
                        java.util.stream.Collectors.counting()));
        List<String> morceaux = new ArrayList<>();
        long categorie = parOrigine.getOrDefault(MargeCatalogue.Origine.CATEGORIE, 0L);
        long fournisseur = parOrigine.getOrDefault(MargeCatalogue.Origine.FOURNISSEUR, 0L);
        long aucune = parOrigine.getOrDefault(MargeCatalogue.Origine.AUCUNE, 0L);
        long fourni = parOrigine.getOrDefault(MargeCatalogue.Origine.PRIX_DE_VENTE_FOURNI, 0L);
        if (categorie > 0) morceaux.add(categorie + " au taux de la catégorie");
        if (fournisseur > 0) morceaux.add(fournisseur + " au taux du fournisseur");
        if (aucune > 0) morceaux.add(aucune + " sans marge (prix d'achat tel quel)");
        if (fourni > 0) morceaux.add(fourni + " au prix de vente du fichier");
        return morceaux.isEmpty() ? null : String.join(" · ", morceaux);
    }

    private static UUID requireTenant() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant absent du contexte.");
        }
        return tenantId;
    }
}
