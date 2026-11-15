package com.optimisante.backend.domain.partnership.controller;

import com.optimisante.backend.domain.partnership.dto.PartnershipRequestResponseDto;
import com.optimisante.backend.domain.partnership.service.PartnershipService;
import lombok.RequiredArgsConstructor;
import com.optimisante.backend.domain.partnership.excel.ConventionExcelWriter;
import com.optimisante.backend.domain.partnership.excel.DossierExcelReader;
import com.optimisante.backend.domain.partnership.excel.DossierExcelWriter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.optimisante.backend.common.storage.ControleFichier;
import com.optimisante.backend.infrastructure.debit.GardeDepotPartenariat;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/partnership")
@RequiredArgsConstructor
public class PartnershipResource {

    private final PartnershipService partnershipService;
    private final DossierExcelWriter dossierExcelWriter;
    private final ConventionExcelWriter conventionExcelWriter;
    private final DossierExcelReader dossierExcelReader;
    private final GardeDepotPartenariat gardeDepotPartenariat;

    /**
     * Ce que le formulaire public demande à son affichage, avant tout envoi.
     *
     * <p>Le jeton rendu date l'affichage : l'envoi devra le présenter, et il devra avoir
     * quelques secondes. Voir {@link GardeDepotPartenariat} pour ce que cela arrête et ce que
     * cela n'arrête pas.</p>
     */
    @GetMapping("/formulaire")
    public ResponseEntity<Map<String, Object>> formulaire() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("jeton", gardeDepotPartenariat.emettreJeton()));
    }

    /**
     * Modèle de convention cadre, en classeur, produit à la demande.
     *
     * <p>Servait auparavant un PDF : le centre devait l'imprimer pour le remplir à la main, et
     * les dossiers revenaient manuscrits — l'IBAN recopié à la main étant précisément le champ
     * qu'on ne veut pas deviner. Les cases se saisissent désormais au clavier, et l'impression
     * reste possible pour la signature.</p>
     *
     * <p>Régénéré à chaque appel plutôt que servi depuis un fichier figé, comme le classeur de
     * dossier : c'est la seule façon qu'il ne se décale jamais du texte que nous tenons.</p>
     */
    @GetMapping(value = "/convention-modele", produces =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> modeleConvention() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("convention-partenariat-optimi-sante.xlsx",
                                          java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(conventionExcelWriter.modeleVierge());
    }

    /**
     * Modèle Excel du dossier de partenariat, produit à la demande.
     *
     * <p>Le classeur est régénéré à chaque appel plutôt que servi depuis un fichier figé : c'est
     * la seule façon qu'il ne se décale jamais de ce que le serveur sait relire.</p>
     */
    @GetMapping(value = "/dossier-modele", produces =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> modeleDossier() {
        byte[] classeur = dossierExcelWriter.modeleVierge();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("dossier-partenariat-optimi-sante.xlsx",
                                          java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-Robots-Tag", "noindex, nofollow")
                .body(classeur);
    }

    /**
     * Relit un dossier rempli et rend le rapport de contrôle.
     *
     * <p>Ne crée rien : ce point d'entrée existe pour que l'établissement puisse corriger son
     * fichier avant de déposer sa demande, plutôt que de découvrir ses fautes après coup.</p>
     *
     * <p>Répond 200 même lorsque le dossier comporte des anomalies : ce n'est pas la requête qui
     * a échoué, c'est le contenu du fichier qui demande une correction. Un 4xx pousserait le
     * navigateur à traiter la réponse comme une erreur et à masquer le rapport.</p>
     */
    @PostMapping(value = "/dossier/verification", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> verifierDossier(
            @RequestParam("fichier") MultipartFile fichier) {
        DossierExcelReader.Lecture lecture;
        try {
            // Avant toute ouverture : ce fichier vient d'un anonyme, et le lire le décompresse.
            ControleFichier.verifierClasseurDossier(fichier);
            lecture = dossierExcelReader.lire(fichier);
        } catch (IllegalArgumentException refus) {
            // Rendu comme une anomalie et non comme un 400, pour la raison dite plus haut :
            // l'écran affiche un rapport, il masquerait une erreur.
            lecture = new DossierExcelReader.Lecture(Map.of(), List.of(), List.of(
                    new com.optimisante.backend.domain.partnership.excel.AnomalieCellule("—", 0, "Fichier",
                            nomCourt(fichier), refus.getMessage())));
        }
        return ResponseEntity.ok(Map.of(
                "conforme", lecture.conforme(),
                "identite", lecture.identite(),
                "capacites", lecture.capacites(),
                "anomalies", lecture.anomalies().stream()
                        .map(a -> Map.of(
                                "feuille", a.feuille(),
                                "ligne", a.ligne(),
                                "colonne", a.colonne(),
                                "valeur", a.valeur(),
                                "probleme", a.probleme(),
                                "resume", a.resume()))
                        .toList()));
    }

    @PostMapping(value = "/requests", consumes = "multipart/form-data")
    public ResponseEntity<PartnershipRequestResponseDto> submitRequest(
            @RequestParam String institutionName,
            @RequestParam(required = false) String finessAccreditation,
            @RequestParam String contactPersonName,
            @RequestParam String contactEmail,
            @RequestParam String contactPhone,
            @RequestParam String address,
            @RequestParam("conventionFile") MultipartFile conventionFile,
            @RequestParam(required = false) String jeton,
            @RequestParam(name = "siteInternet", required = false) String piege) {
        // Tout est vérifié AVANT le dépôt au stockage. Dans l'ordre inverse, un champ trop long
        // ferait échouer l'écriture en base après l'envoi du fichier, et laisserait au stockage
        // une convention qu'aucune ligne ne référence.
        gardeDepotPartenariat.verifierEnvoi(jeton, piege);
        borner("Le nom de l'institution", institutionName, 255);
        borner("Le numéro FINESS", finessAccreditation, 255);
        borner("Le nom du contact", contactPersonName, 255);
        borner("L'adresse e-mail", contactEmail, 255);
        borner("Le téléphone", contactPhone, 255);
        borner("L'adresse", address, 1000);
        if (!contactEmail.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("L'adresse e-mail de contact n'est pas valide.");
        }
        ControleFichier.verifierConvention(conventionFile);

        PartnershipRequestResponseDto recu = partnershipService.submitRequest(
                institutionName, finessAccreditation, contactPersonName, contactEmail, contactPhone, address, conventionFile);
        // La clé de stockage ne part pas chez un anonyme : il n'en a aucun usage, et c'est la
        // règle posée par DP-4 pour tout le reste du site.
        recu.setConventionFileKey(null);
        return ResponseEntity.ok(recu);
    }

    private static void borner(String libelle, String valeur, int maximum) {
        if (valeur != null && valeur.length() > maximum) {
            throw new IllegalArgumentException(
                    libelle + " dépasse " + maximum + " caractères.");
        }
    }

    /** Le nom du fichier sans son chemin, borné : il est renvoyé dans le rapport. */
    private static String nomCourt(MultipartFile fichier) {
        String nom = fichier == null || fichier.getOriginalFilename() == null
                ? "" : fichier.getOriginalFilename().replace('\\', '/');
        nom = nom.substring(nom.lastIndexOf('/') + 1);
        return nom.length() > 120 ? nom.substring(0, 120) : nom;
    }
}
