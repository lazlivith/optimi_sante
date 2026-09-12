package com.optimisante.backend.domain.partnership.controller;

import com.optimisante.backend.domain.partnership.dto.PartnershipRequestResponseDto;
import com.optimisante.backend.domain.partnership.service.PartnershipService;
import lombok.RequiredArgsConstructor;
import com.optimisante.backend.domain.partnership.excel.DossierExcelReader;
import com.optimisante.backend.domain.partnership.excel.DossierExcelWriter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/partnership")
@RequiredArgsConstructor
public class PartnershipResource {

    private final PartnershipService partnershipService;
    private final DossierExcelWriter dossierExcelWriter;
    private final DossierExcelReader dossierExcelReader;

    @GetMapping("/convention-template")
    public ResponseEntity<Map<String, String>> getConventionTemplate() {
        return ResponseEntity.ok(Map.of("downloadUrl", partnershipService.getConventionTemplateUrl()));
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
        var lecture = dossierExcelReader.lire(fichier);
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
            @RequestParam("conventionFile") MultipartFile conventionFile) {
        return ResponseEntity.ok(partnershipService.submitRequest(
                institutionName, finessAccreditation, contactPersonName, contactEmail, contactPhone, address, conventionFile));
    }
}
