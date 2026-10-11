package com.optimisante.backend.domain.exercerenfrance.web;

import com.optimisante.backend.domain.exercerenfrance.dto.ExercerEnFranceDtos.*;
import com.optimisante.backend.domain.exercerenfrance.service.ServiceDemandesAccompagnement;
import com.optimisante.backend.domain.exercerenfrance.service.ServicePublicationEvc;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Les deux seules routes ouvertes de « Exercer en France ».
 *
 * <p><b>Aucune n'accepte de tenant.</b> La lecture sert le contenu éditorial, qui est global ;
 * le dépôt est rattaché par le serveur. Ni en-tête ni champ du corps n'entrent dans ce choix :
 * laisser l'appelant le désigner reviendrait à le laisser écrire chez qui il veut.</p>
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class ExercerEnFrancePublicResource {

    private final ServicePublicationEvc publication;
    private final ServiceDemandesAccompagnement demandes;

    /**
     * La page publiée, en un seul appel.
     *
     * <p>Les statuts de période sont recalculés à chaque lecture : c'est le seul champ que
     * l'instantané ne peut pas figer sans se périmer.</p>
     */
    @GetMapping("/exercer-en-france")
    public ResponseEntity<PagePublique> page() {
        return ResponseEntity.ok(publication.publieeAvecStatutsFrais());
    }

    /**
     * Dépôt d'une demande d'accompagnement.
     *
     * <p><b>Un dépôt écarté répond comme un dépôt accepté.</b> Dire « champ piège rempli » ou
     * « trop rapide » enseignerait à l'auteur comment passer la prochaine fois. Seul le
     * plafond quotidien s'annonce, parce qu'il concerne un praticien réel qui s'inquiète de
     * ne pas avoir de réponse.</p>
     */
    @PostMapping("/demandes-accompagnement")
    public ResponseEntity<DemandeConfirmation> deposer(@Valid @RequestBody DemandeRequest dto) {
        try {
            var demande = demandes.deposer(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(new DemandeConfirmation(
                    demande.getId(),
                    "Votre demande est enregistrée. Un conseiller vous répond sous 48 heures ouvrées."));
        } catch (ServiceDemandesAccompagnement.DepotRefuse e) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new DemandeConfirmation(null, e.getMessage()));
        }
    }
}
