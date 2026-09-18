package com.optimisante.backend.common.storage;

/**
 * Arborescence de stockage d'Optimi Santé : la seule liste des dossiers où la plateforme écrit.
 *
 * <p><b>Pourquoi une énumération.</b> Les chemins étaient écrits en toutes lettres à quatorze
 * endroits du code, et avaient divergé : les devis partaient dans {@code docs/devis} ou
 * {@code docs/quotes} selon l'écran, et {@code docs/enrollments} mêlait les pièces du médecin,
 * les convocations et les attestations de souscription. Un chemin inconnu ne compile plus.</p>
 *
 * <p><b>Chemins relatifs.</b> Le stockage les place sous une racine et un environnement
 * ({@code optimisante/dev/…}, {@code optimisante/prod/…}) : voir
 * {@code cloudinary.ArborescenceCloudinary}. Ajouter un dossier ici suffit ; aucun appelant ne
 * compose de chemin lui-même.</p>
 *
 * <pre>
 * optimisante/&lt;environnement&gt;/
 *   catalogue/           produits · galerie · videos
 *   formations/          images · videos · brochures
 *   dossiers-candidats/  pieces · officiels · convocations · souscriptions
 *   documents-emis/      conventions · attestations · recus · devis · releves-reversement
 *   partenariats/        demandes · modeles
 * </pre>
 */
public enum DossierStockage {

    // ── Catalogue de la boutique ───────────────────────────────────────────────────────
    CATALOGUE_PRODUITS("catalogue/produits", TypeRessource.IMAGE),
    CATALOGUE_GALERIE("catalogue/galerie", TypeRessource.IMAGE),
    CATALOGUE_VIDEOS("catalogue/videos", TypeRessource.VIDEO),
    /** Fichiers catalogue déposés par les fournisseurs, conservés pour rejouer un import (V58). */
    CATALOGUE_IMPORTS("catalogue/imports", TypeRessource.DOCUMENT),

    // ── Formations publiées par les CHU ────────────────────────────────────────────────
    FORMATIONS_IMAGES("formations/images", TypeRessource.IMAGE),
    FORMATIONS_VIDEOS("formations/videos", TypeRessource.VIDEO),
    FORMATIONS_BROCHURES("formations/brochures", TypeRessource.DOCUMENT),

    // ── Dossiers des candidats ─────────────────────────────────────────────────────────
    /** Pièces déposées par le médecin ou pour lui : diplôme, passeport, visa… */
    DOSSIERS_PIECES("dossiers-candidats/pieces", TypeRessource.DOCUMENT),
    /** Programme et convention du CHU, kit de départ (V57). */
    DOSSIERS_OFFICIELS("dossiers-candidats/officiels", TypeRessource.DOCUMENT),
    DOSSIERS_CONVOCATIONS("dossiers-candidats/convocations", TypeRessource.DOCUMENT),
    DOSSIERS_SOUSCRIPTIONS("dossiers-candidats/souscriptions", TypeRessource.DOCUMENT),

    // ── Documents émis par la plateforme ───────────────────────────────────────────────
    DOCUMENTS_CONVENTIONS("documents-emis/conventions", TypeRessource.DOCUMENT),
    DOCUMENTS_ATTESTATIONS("documents-emis/attestations", TypeRessource.DOCUMENT),
    DOCUMENTS_RECUS("documents-emis/recus", TypeRessource.DOCUMENT),
    DOCUMENTS_DEVIS("documents-emis/devis", TypeRessource.DOCUMENT),
    DOCUMENTS_RELEVES_REVERSEMENT("documents-emis/releves-reversement", TypeRessource.DOCUMENT),

    // ── Partenariats ───────────────────────────────────────────────────────────────────
    PARTENARIATS_DEMANDES("partenariats/demandes", TypeRessource.DOCUMENT),
    PARTENARIATS_MODELES("partenariats/modeles", TypeRessource.DOCUMENT);

    /** Nature du fichier, qui décide de la façon dont le stockage le conserve et le sert. */
    public enum TypeRessource {
        /** PDF et images justificatives : servis par lien signé, jamais publiquement. */
        DOCUMENT,
        /** Visuel affiché tel quel dans une page. */
        IMAGE,
        VIDEO
    }

    private final String chemin;
    private final TypeRessource type;

    DossierStockage(String chemin, TypeRessource type) {
        this.chemin = chemin;
        this.type = type;
    }

    /** Chemin relatif, sans racine ni environnement. */
    public String chemin() {
        return chemin;
    }

    public TypeRessource type() {
        return type;
    }
}
