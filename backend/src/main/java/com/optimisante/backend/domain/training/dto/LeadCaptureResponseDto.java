package com.optimisante.backend.domain.training.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse à une demande de brochure, déposée par un visiteur non authentifié.
 *
 * <p><b>Ce dto ne porte plus aucun lien, et c'est tout son objet.</b> Il portait
 * {@code brochureDownloadUrl} : un lien de téléchargement de la brochure <b>d'origine du
 * partenaire</b>, valable soixante minutes, rendu à quiconque laissait une adresse sur le
 * formulaire public. Les coordonnées du CHU — adresse, téléphones, noms et courriels de
 * personnes — partaient donc avec, et un client pouvait contacter l'établissement hors de la
 * plateforme.</p>
 *
 * <p>Aucun lien, aucun jeton, aucune clé de stockage ne doit revenir ici. La brochure
 * rééditée par Optimi Santé, elle, sera servie par la fiche de la formation — pas par la
 * réponse à un formulaire.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadCaptureResponseDto {

    /**
     * Vrai dès que la demande est enregistrée.
     *
     * <p>Le champ existe pour que la réponse dise quelque chose sans rien divulguer : ce que
     * le visiteur doit savoir, c'est que sa demande est reçue.</p>
     */
    private boolean demandeEnregistree;
}
