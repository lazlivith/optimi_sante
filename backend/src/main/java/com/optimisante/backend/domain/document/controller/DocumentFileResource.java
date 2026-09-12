package com.optimisante.backend.domain.document.controller;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.document.service.DocumentAccessTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Diffuse un document à partir d'un jeton signé.
 *
 * <p>Cette route est ouverte sans authentification de session, et c'est délibéré : le jeton
 * <em>est</em> l'autorisation. Il a été délivré par un service métier qui avait déjà vérifié le
 * droit d'accès, il ne désigne qu'un seul fichier, et il périme. Voir
 * {@link DocumentAccessTokenService} pour la raison — les documents s'ouvrent par
 * {@code window.open}, qui n'emporte aucun en-tête d'authentification.</p>
 *
 * <p>Le fichier transite par le serveur au lieu d'être servi par le stockage. C'est un aller-retour
 * de plus, assumé : c'est ce qui permet de ne plus jamais exposer d'adresse de stockage publique,
 * et de refuser une demande périmée.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentFileResource {

    private final DocumentAccessTokenService jetons;
    private final StorageService storageService;

    /** Extension a proposer pour chaque type servi. */
    private static final Map<MediaType, String> EXTENSIONS = Map.of(
            MediaType.APPLICATION_PDF, "pdf",
            MediaType.IMAGE_PNG, "png",
            MediaType.IMAGE_JPEG, "jpg",
            MediaType.parseMediaType("image/webp"), "webp",
            MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), "xlsx");

    /**
     * Reconnait le type d'un fichier a ses premiers octets.
     *
     * <p>Chaque format commence par une signature convenue : « %PDF » pour un PDF, deux octets
     * fixes pour un JPEG, huit pour un PNG. C'est plus sur qu'un nom de fichier, qui n'engage
     * personne — un PDF renomme en .jpg reste un PDF.</p>
     *
     * <p>Un classeur .xlsx est une archive ZIP : il partage sa signature avec elle. On ne peut
     * donc pas le distinguer ici, et il est servi comme un binaire — ce qui convient, puisque le
     * navigateur le remet alors au tableur.</p>
     */
    private static MediaType typeReel(byte[] contenu) {
        if (contenu.length >= 4) {
            if (contenu[0] == '%' && contenu[1] == 'P' && contenu[2] == 'D' && contenu[3] == 'F') {
                return MediaType.APPLICATION_PDF;
            }
            if ((contenu[0] & 0xFF) == 0xFF && (contenu[1] & 0xFF) == 0xD8) {
                return MediaType.IMAGE_JPEG;
            }
            if ((contenu[0] & 0xFF) == 0x89 && contenu[1] == 'P' && contenu[2] == 'N' && contenu[3] == 'G') {
                return MediaType.IMAGE_PNG;
            }
        }
        if (contenu.length >= 12 && contenu[8] == 'W' && contenu[9] == 'E'
                && contenu[10] == 'B' && contenu[11] == 'P') {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    @GetMapping("/file/{jeton}")
    public ResponseEntity<byte[]> telecharger(@PathVariable String jeton) {
        var demande = jetons.verifier(jeton);
        if (demande.isEmpty()) {
            // Un jeton invalide et un jeton périmé donnent la même réponse : distinguer les deux
            // renseignerait sur l'existence du document.
            return ResponseEntity.status(403).build();
        }

        String publicId = demande.get().publicId();
        byte[] contenu;
        try {
            contenu = storageService.download(publicId);
        } catch (Exception e) {
            log.error("Document introuvable au stockage pour la clé {}", publicId, e);
            return ResponseEntity.status(404).build();
        }

        // Le type vient des PREMIERS OCTETS, pas du nom. Les cles de stockage ne portent plus
        // d'extension — le compte Cloudinary refuse de livrer une ressource nommee « .pdf » —
        // et un nom de fichier ment de toute facon : il suffit de renommer un PDF en .jpg.
        MediaType type = typeReel(contenu);
        String extension = EXTENSIONS.getOrDefault(type, "pdf");

        // Un PDF s'ouvre dans l'onglet : le bouton s'appelle « Ouvrir le PDF », il serait
        // déroutant qu'il déclenche un enregistrement. Le reste est proposé au téléchargement.
        ContentDisposition disposition = (type.equals(MediaType.APPLICATION_PDF)
                ? ContentDisposition.inline()
                : ContentDisposition.attachment())
                .filename(nomFichier(demande.get().libelle(), publicId, extension),
                          StandardCharsets.UTF_8)   // filename* : les accents survivent
                .build();

        HttpHeaders entetes = new HttpHeaders();
        entetes.setContentType(type);
        entetes.setContentDisposition(disposition);
        // Un document nominatif n'a rien à faire dans un index de moteur de recherche.
        entetes.add("X-Robots-Tag", "noindex, nofollow, noarchive");
        // Ni dans le cache d'un proxy partagé : « private » le réserve au navigateur du visiteur.
        entetes.setCacheControl("private, max-age=0, no-store");

        return ResponseEntity.ok().headers(entetes).body(contenu);
    }

    /**
     * Nom proposé à l'enregistrement : le libellé métier quand il existe, sinon le dernier
     * segment de la clé de stockage. Jamais l'identifiant aléatoire seul — un dossier de
     * téléchargements rempli de {@code a3f9c1b2.pdf} est inutilisable.
     */
    private static String nomFichier(String libelle, String publicId, String extension) {
        String base = (libelle != null && !libelle.isBlank())
                ? libelle
                : publicId.substring(publicId.lastIndexOf('/') + 1);
        String propre = base.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        return propre.toLowerCase(Locale.ROOT).endsWith("." + extension)
                ? propre
                : propre + "." + (extension.isBlank() ? "pdf" : extension);
    }

}
