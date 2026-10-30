package com.optimisante.backend.common.storage.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.optimisante.backend.common.storage.DossierStockage;
import com.optimisante.backend.common.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Locale;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Stockage Cloudinary.
 *
 * <p>Tout fichier est rangé sous {@code racine/environnement/} ({@link ArborescenceCloudinary}) :
 * le compte est partagé avec d'autres projets, et dev ne se mêle jamais à prod. Les documents sont
 * conservés en ressource « raw » et servis par lien signé ; images et vidéos sont publiques.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;
    private final ArborescenceCloudinary arborescence;

    /** Coupures réseau passagères : 3 tentatives, 500 ms puis 1 s d'attente (voir {@link ReessaiReseau}). */
    private final ReessaiReseau reessai = new ReessaiReseau();

    /** Partagé : un client HTTP est conçu pour être réutilisé, en créer un par appel fuit. */
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String uploadFile(byte[] bytes, String fileName, DossierStockage dossier) {
        try {
            // Identifiant unique, pour qu'un fichier n'en ecrase jamais un autre.
            //
            // L'EXTENSION EST RETIREE, et ce n'est pas cosmetique : ce compte Cloudinary a la
            // livraison des PDF desactivee, et toute ressource dont la cle finit par « .pdf »
            // est refusee avec « deny or ACL failure ». Verifie par experience — meme contenu,
            // depose deux fois, la version avec extension est la seule des deux a etre refusee.
            // Aucun document televerse par un utilisateur n'etait donc telechargeable.
            //
            // Rien n'est perdu : le type reel est deduit du contenu a la diffusion, et le nom
            // propose a l'enregistrement vient du jeton, pas de la cle de stockage.
            String publicId = UUID.randomUUID() + "_" + sansExtension(fileName);

            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", arborescence.dossier(dossier),
                    "public_id", publicId,
                    // "raw" (et non "auto") : genererUrlSignee() doit connaître le resource_type
                    // exact au moment de reconstruire l'URL de téléchargement, et "auto" n'est
                    // valide qu'à l'upload, pas pour une transformation/URL.
                    "resource_type", "raw",
                    // DP-1 : les documents non publics partent en "authenticated". Un objet
                    // authenticated ne se sert QUE par URL signée — la lecture anonyme répond
                    // 401, et connaître la clé ne suffit plus. Vérifié (analyse DOC-PRIV) : le
                    // mode authenticated par URL signée fonctionne sur l'offre gratuite ; seules
                    // les authentifications par jeton et par cookie exigent l'offre Advanced.
                    "type", typeLivraison(dossier)
            );

            Map<?, ?> uploadResult = reessai.executer("Téléversement " + publicId,
                    () -> cloudinary.uploader().upload(bytes, uploadParams));
            return uploadResult.get("public_id").toString();

        } catch (IOException e) {
            log.error("Failed to upload file to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload file to storage", e);
        } catch (InterruptedException e) {
            throw interrompu(e);
        }
    }

    /**
     * Mode de livraison Cloudinary selon la sensibilité du dossier.
     *
     * <p><b>authenticated</b> pour les documents (reçus, conventions, pièces des candidats…) :
     * l'accès passe obligatoirement par une URL signée, générée côté serveur après contrôle de
     * rôle. <b>upload</b> (public) pour les images et vidéos, qui sont servies telles quelles
     * dans les pages et doivent le rester.</p>
     *
     * <p>Pendant la transition, d'anciens documents restent en {@code upload} : {@link #download}
     * tente d'abord {@code authenticated}, puis se replie sur {@code upload}.</p>
     */
    private static String typeLivraison(DossierStockage dossier) {
        return dossier.type() == DossierStockage.TypeRessource.DOCUMENT ? "authenticated" : "upload";
    }

    /** Retire l'extension d'un nom de fichier : « passeport.pdf » devient « passeport ». */
    private static String sansExtension(String nomFichier) {
        if (nomFichier == null) return "fichier";
        int point = nomFichier.lastIndexOf('.');
        String base = point > 0 ? nomFichier.substring(0, point) : nomFichier;
        return base.isBlank() ? "fichier" : base;
    }

    @Override
    public String uploadFile(org.springframework.web.multipart.MultipartFile file, DossierStockage dossier) {
        try {
            return uploadFile(file.getBytes(), file.getOriginalFilename(), dossier);
        } catch (IOException e) {
            log.error("Failed to read MultipartFile: {}", e.getMessage(), e);
            throw new RuntimeException("Could not read file for upload", e);
        }
    }

    @Override
    public String uploadGeneratedPdf(byte[] pdfBytes, DossierStockage dossier, String fileName) {
        try {
            // Suffixe aléatoire : sans lui, la clé se reconstitue à partir du numéro de commande
            // (RECEIPT-OPT-20260829-9CEF), soit 65 536 possibilités par jour — assez peu pour
            // être énumérées. La clé reste lisible pour l'exploitation, mais plus prévisible.
            String publicId = fileName + "-" + UUID.randomUUID().toString().substring(0, 12);

            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", arborescence.dossier(dossier),
                    "public_id", publicId,
                    "resource_type", "raw", // PDFs can be uploaded as raw or image, but raw is safer for documents
                    // DP-1 : reçus, devis, conventions, attestations, relevés — tous
                    // authenticated. Ces PDF générés portent des noms et des montants.
                    "type", typeLivraison(dossier)
            );

            Map<?, ?> uploadResult = reessai.executer("Téléversement " + publicId,
                    () -> cloudinary.uploader().upload(pdfBytes, uploadParams));
            return uploadResult.get("public_id").toString();

        } catch (IOException e) {
            log.error("Failed to upload generated PDF to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload PDF to storage", e);
        } catch (InterruptedException e) {
            throw interrompu(e);
        }
    }

    @Override
    public String uploadPublicTemplate(byte[] bytes, DossierStockage dossier, String fileName) {
        try {
            // Clé volontairement STABLE : ce modèle vierge est régénéré à chaque demande d'un
            // prospect. Avec une clé aléatoire, chaque téléchargement laisserait un fichier de
            // plus dans le stockage, sans fin. Le document ne désigne personne.
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", arborescence.dossier(dossier),
                    "public_id", fileName,
                    "resource_type", "raw",
                    "type", "upload",
                    "overwrite", true,
                    "invalidate", true);

            Map<?, ?> resultat = reessai.executer("Téléversement " + fileName,
                    () -> cloudinary.uploader().upload(bytes, uploadParams));
            return resultat.get("public_id").toString();

        } catch (IOException e) {
            log.error("Failed to upload public template to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload public template", e);
        } catch (InterruptedException e) {
            throw interrompu(e);
        }
    }

    @Override
    public byte[] download(String publicId) {
        // L'URL signée sert ici de simple adresse de lecture côté serveur : elle ne sort pas
        // de la JVM, contrairement à l'usage qu'on en faisait avant, où elle partait au
        // navigateur et laissait le document lisible par quiconque la détenait.
        //
        // DP-1, repli ordonné : on tente d'abord "authenticated" (la cible), puis "upload"
        // (les documents pas encore bascules). Pendant la transition, les deux coexistent ;
        // une fois la bascule finie et verifiee, le repli "upload" sera retire (DP-5). Sans ce
        // repli, un document deja authenticated OU un document encore upload deviendrait
        // illisible selon le sens de la bascule.
        IllegalStateException dernierEchec = null;
        for (String type : new String[] {"authenticated", "upload"}) {
            try {
                return telecharger(publicId, type);
            } catch (IllegalStateException introuvable) {
                // 401/403/404 pour ce type : le document est probablement servi par l'autre.
                dernierEchec = introuvable;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Lecture du document interrompue : " + publicId, e);
            } catch (Exception e) {
                throw new RuntimeException("Document illisible au stockage : " + publicId, e);
            }
        }
        throw new RuntimeException("Document illisible au stockage : " + publicId, dernierEchec);
    }

    /** Une tentative de lecture pour un mode de livraison donné ({@code authenticated} ou {@code upload}). */
    private byte[] telecharger(String publicId, String type) throws Exception {
        String url = genererUrlSignee(publicId, type);
        HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<byte[]> reponse = reessai.executer("Lecture " + type + " " + publicId,
                () -> HTTP.send(requete, HttpResponse.BodyHandlers.ofByteArray()));
        if (reponse.statusCode() != 200) {
            throw new IllegalStateException(
                    "Le stockage a répondu " + reponse.statusCode() + " (" + type + ") pour " + publicId);
        }
        return reponse.body();
    }

    @Override
    public String generatePresignedOrSignedUrl(String publicId, int expirationMinutes) {
        // Conservée pour l'interface et d'éventuels appelants : l'ancienne lecture signée en
        // "upload". La lecture réelle passe désormais par download(), qui tente authenticated
        // puis upload.
        return genererUrlSignee(publicId, "upload");
    }

    /**
     * URL signée de lecture pour un mode de livraison donné, à usage strictement interne.
     *
     * <p><b>Pas d'expiration dans l'URL, et ce n'est pas un manque.</b> L'expiration par horodatage
     * dans l'URL relève de l'accès par jeton, réservé aux offres supérieures. Ici l'URL ne quitte
     * jamais la JVM : {@link #download} la génère, s'en sert une fois, la jette. L'expiration vue
     * du navigateur est celle de NOTRE jeton ({@code DocumentAccessTokenService}), que nous
     * maîtrisons. Pour un objet {@code authenticated}, la signature suffit à bloquer l'accès
     * anonyme — c'est tout ce qu'on lui demande.</p>
     */
    private String genererUrlSignee(String publicId, String type) {
        try {
            return cloudinary.url()
                    .resourceType("raw")
                    .type(type)
                    .signed(true)
                    .generate(publicId);
        } catch (Exception e) {
            log.error("Failed to generate signed URL ({}) for publicId {}: {}", type, publicId, e.getMessage(), e);
            throw new RuntimeException("Could not generate signed URL", e);
        }
    }

    @Override
    public String uploadMedia(byte[] bytes, String fileName, DossierStockage dossier) {
        return televerserMedia(bytes, fileName, dossier, null);
    }

    @Override
    public String uploadMedia(byte[] bytes, String fileName, DossierStockage dossier, String sousDossier) {
        return televerserMedia(bytes, fileName, dossier, sousDossier);
    }

    @Override
    public String uploadMedia(org.springframework.web.multipart.MultipartFile file, DossierStockage dossier) {
        try {
            return televerserMedia(file.getBytes(), file.getOriginalFilename(), dossier, null);
        } catch (IOException e) {
            log.error("Failed to read MultipartFile: {}", e.getMessage(), e);
            throw new RuntimeException("Could not read file for upload", e);
        }
    }

    /** Chemin commun des deux dépôts de média : le fichier téléversé et l'image déjà en mémoire. */
    private String televerserMedia(byte[] contenu, String fileName, DossierStockage dossier,
                                   String sousDossier) {
        if (dossier.type() == DossierStockage.TypeRessource.DOCUMENT) {
            // Un document déposé en média serait public : il doit passer par uploadFile.
            throw new IllegalArgumentException("Le dossier " + dossier + " n'accepte pas de média.");
        }
        String resourceType = dossier.type() == DossierStockage.TypeRessource.VIDEO ? "video" : "image";
        try {
            // Clé lisible mais unique : le nom d'origine aide à retrouver un visuel dans la console.
            String publicId = UUID.randomUUID() + (fileName == null || fileName.isBlank() ? ""
                    : "_" + sansExtension(fileName).replaceAll("[^A-Za-z0-9_-]", "-"));
            Map<String, Object> uploadParams = ObjectUtils.asMap(
                    "folder", arborescence.dossier(dossier, sousDossier),
                    "public_id", publicId,
                    "resource_type", resourceType, // "image" ou "video" : nécessaire pour un rendu
                                                    // inline (<img>/<video>) et les transformations,
                                                    // contrairement à "raw" utilisé pour les documents.
                    "type", "upload"
            );
            Map<?, ?> uploadResult = reessai.executer("Téléversement média " + publicId,
                    () -> cloudinary.uploader().upload(contenu, uploadParams));
            return uploadResult.get("public_id").toString();
        } catch (IOException e) {
            log.error("Failed to upload media to Cloudinary: {}", e.getMessage(), e);
            throw new RuntimeException("Could not upload media to storage", e);
        } catch (InterruptedException e) {
            throw interrompu(e);
        }
    }

    @Override
    public String generateMediaUrl(String publicId, String resourceType) {
        return generateMediaUrl(publicId, resourceType, null);
    }

    @Override
    public String generateMediaUrl(String publicId, String resourceType, String format) {
        var url = cloudinary.url().resourceType(resourceType).type("upload");
        if (format != null && !format.isBlank()) {
            url = url.format(format.toLowerCase(Locale.ROOT).replace(".", ""));
        }
        return url.generate(publicId);
    }

    @Override
    public void deleteFile(String publicId) {
        try {
            Map<String, Object> deleteParams = ObjectUtils.asMap("resource_type", "auto");
            reessai.executer("Suppression " + publicId, () -> cloudinary.uploader().destroy(publicId, deleteParams));
        } catch (IOException e) {
            log.error("Failed to delete file from Cloudinary (publicId: {}): {}", publicId, e.getMessage(), e);
            throw new RuntimeException("Could not delete file from storage", e);
        } catch (InterruptedException e) {
            throw interrompu(e);
        }
    }

    @Override
    public void deleteDocument(String publicId) {
        // DP-1 : un document peut être "authenticated" (déposé depuis ce lot) ou "upload"
        // (ancien, pas encore basculé). destroy() vise "upload" par défaut : sans le type exact,
        // un objet authenticated resterait indéfiniment dans le stockage. On tente authenticated
        // puis upload, et on s'arrête dès qu'un des deux a réellement supprimé quelque chose.
        try {
            for (String type : new String[] {"authenticated", "upload"}) {
                Map<?, ?> resultat = reessai.executer("Suppression (" + type + ") " + publicId,
                        () -> cloudinary.uploader().destroy(publicId,
                                ObjectUtils.asMap("resource_type", "raw", "type", type)));
                Object issue = resultat == null ? null : resultat.get("result");
                if ("ok".equals(issue)) {
                    return;
                }
            }
            // Ni l'un ni l'autre : le document n'existait plus. Rien à signaler comme erreur —
            // l'état voulu (absent) est atteint.
            log.info("Document déjà absent du stockage à la suppression : {}", publicId);
        } catch (IOException e) {
            log.error("Failed to delete document from Cloudinary (publicId: {}): {}", publicId, e.getMessage(), e);
            throw new RuntimeException("Could not delete document from storage", e);
        } catch (InterruptedException e) {
            throw interrompu(e);
        }
    }

    /** Attente interrompue (arrêt de l'application) : on rétablit le signal au lieu de l'avaler. */
    private static RuntimeException interrompu(InterruptedException e) {
        Thread.currentThread().interrupt();
        return new RuntimeException("Opération de stockage interrompue", e);
    }
}
