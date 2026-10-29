package com.optimisante.backend.domain.training.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que les réponses publiques ne doivent plus contenir.
 *
 * <p><b>Pourquoi un test sur la forme des dto, et non sur un appel.</b> La fuite ne venait pas
 * d'une erreur de calcul : elle venait de deux champs. {@code brochureDownloadUrl} sur la
 * réponse au formulaire public, et {@code brochureUrl} sur la liste des formations servie en
 * {@code permitAll} — tous deux pointant la brochure <b>d'origine du partenaire</b>, avec les
 * coordonnées du CHU. Un test qui monterait le serveur passerait aussi, et ne dirait rien le
 * jour où quelqu'un remet le champ : c'est le <b>type</b> qu'il faut garder propre.</p>
 */
@DisplayName("Les réponses publiques ne portent aucun lien vers la brochure d'origine")
class AucuneFuiteDeBrochureTest {

    /** Ce qui, dans un nom de champ, trahit un chemin vers un fichier. */
    private static final List<String> MOTS_INTERDITS =
            List.of("brochure", "url", "lien", "jeton", "token", "publicid", "s3key", "cle");

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    @DisplayName("la réponse au formulaire public ne porte ni lien, ni jeton, ni clé")
    void laReponseAuFormulaireNePorteAucunLien() throws Exception {
        for (Field champ : LeadCaptureResponseDto.class.getDeclaredFields()) {
            if (champ.isSynthetic()) {
                continue;
            }
            String nom = champ.getName().toLowerCase(Locale.ROOT);
            assertThat(MOTS_INTERDITS.stream().noneMatch(nom::contains))
                    .as("le champ « %s » de LeadCaptureResponseDto désigne un fichier : "
                            + "cette réponse part à un visiteur non authentifié", champ.getName())
                    .isTrue();
        }

        // Et le corps réellement sérialisé, puisque c'est lui qui sort sur le réseau.
        String corps = JSON.writeValueAsString(
                LeadCaptureResponseDto.builder().demandeEnregistree(true).build());
        assertThat(corps).doesNotContain("http").doesNotContain("/api/");
    }

    @Test
    @DisplayName("la liste publique des formations ne porte aucun champ de brochure")
    void laListePubliqueNePorteAucuneBrochure() {
        List<String> fautifs = Arrays.stream(TrainingSummaryDto.class.getDeclaredFields())
                .filter(champ -> !champ.isSynthetic())
                .map(champ -> champ.getName())
                .filter(nom -> nom.toLowerCase(Locale.ROOT).contains("brochure"))
                .toList();

        assertThat(fautifs)
                .as("GET /api/v1/trainings est en permitAll : un champ de brochure y serait "
                        + "public, sans compte ni formulaire")
                .isEmpty();
    }

    @Test
    @DisplayName("le dto public reste utilisable : son contenu utile n'a pas bougé")
    void leContenuUtileResteLa() {
        // Sans cette vérification, retirer un champ de trop passerait pour une réussite.
        TrainingSummaryDto resume = TrainingSummaryDto.builder()
                .id(UUID.randomUUID())
                .title("Échographie d'urgence")
                .medicalSpecialty("Radiologie")
                .durationDays(20)
                .location("Valbrune")
                .price(new BigDecimal("1200.00"))
                .centerName("CHU de Valbrune")
                .build();

        assertThat(resume.getTitle()).isEqualTo("Échographie d'urgence");
        assertThat(resume.getCenterName()).isEqualTo("CHU de Valbrune");
        assertThat(resume.getLocation()).isEqualTo("Valbrune");
    }
}
