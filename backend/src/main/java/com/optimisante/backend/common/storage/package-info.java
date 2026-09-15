/**
 * Stockage des fichiers de la plateforme.
 *
 * <h2>Organisation du code</h2>
 * <pre>
 * common/storage/
 *   StorageService        contrat, indépendant du fournisseur (clés de stockage, jamais d'URL en base)
 *   DossierStockage       arborescence métier : la seule liste des dossiers où l'on écrit
 *   ControleFichier       règle commune des documents déposés (PDF/JPG/PNG, 10 Mo)
 *   cloudinary/
 *     CloudinaryConfig          client Cloudinary et arborescence, construits au démarrage
 *     CloudinaryProperties      réglages app.cloudinary.* (identifiants, racine, environnement)
 *     ArborescenceCloudinary    DossierStockage → racine/environnement/chemin, vérifié au démarrage
 *     CloudinaryStorageService  implémentation (documents « raw » signés, images et vidéos publiques)
 *     ReessaiReseau             nouvelles tentatives sur les coupures réseau passagères
 * </pre>
 *
 * <h2>Arborescence dans Cloudinary</h2>
 * <pre>
 * optimisante/                 app.cloudinary.root-folder — compte partagé avec d'autres projets
 *   dev/ | prod/               app.cloudinary.environment — test et production jamais mêlés
 *     catalogue/               produits · galerie · videos
 *     formations/              images · videos · brochures
 *     dossiers-candidats/      pieces · officiels · convocations · souscriptions
 *     documents-emis/          conventions · attestations · recus · devis · releves-reversement
 *     partenariats/            demandes · modeles
 * </pre>
 *
 * <p><b>Fichiers antérieurs.</b> Les fichiers déposés avant cette organisation restent dans leurs
 * anciens dossiers ({@code docs/}, {@code catalog/}, {@code trainings/}) : leurs clés sont en base
 * et continuent de s'ouvrir. Ce sont des données de test, purgées avant la production.</p>
 *
 * <p><b>Ajouter un dossier</b> : une constante dans {@link com.optimisante.backend.common.storage.DossierStockage},
 * rien d'autre. Aucun appelant ne compose de chemin.</p>
 */
package com.optimisante.backend.common.storage;
