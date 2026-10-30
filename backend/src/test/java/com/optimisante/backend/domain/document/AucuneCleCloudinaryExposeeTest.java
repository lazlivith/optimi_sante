package com.optimisante.backend.domain.document;

import com.optimisante.backend.domain.orders.dto.OrderResponseDto;
import com.optimisante.backend.domain.training.dto.EnrollmentDetailDto;
import com.optimisante.backend.domain.training.dto.EnrollmentResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que les réponses au navigateur ne doivent plus contenir : aucune clé Cloudinary, aucune
 * URL de pièce (DP-4).
 *
 * <p><b>Pourquoi un test sur la forme des DTO.</b> La fuite ne venait pas d'un calcul mais de
 * champs : {@code documentS3Key}, {@code conventionS3Key}, {@code diplomaUrl},
 * {@code passportUrl}… livraient le {@code public_id} d'objets alors publics, contournables.
 * Un champ retiré ne doit pas revenir par inadvertance lors d'une évolution : c'est le
 * <b>type</b> qu'on verrouille, comme pour {@code AucuneFuiteDeBrochureTest}.</p>
 *
 * <p>Les pièces se consultent par le coffre-fort et les documents émis par
 * {@code GET /api/v1/documents/{type}/{id}/download}, tous deux sous contrôle de rôle, qui
 * rendent une URL à jeton de notre backend — jamais une adresse Cloudinary.</p>
 */
@DisplayName("Aucune clé Cloudinary n'est exposée au navigateur (DP-4)")
class AucuneCleCloudinaryExposeeTest {

    /** Fragments de nom qui trahissent une clé de stockage ou une URL de fichier. */
    private static final List<String> FRAGMENTS_INTERDITS = List.of(
            "s3key", "s3_key", "publicid", "public_id", "cloudinary",
            "diplomaurl", "passporturl", "medicalboardregistrationurl",
            "storagekey", "filekey");

    private static Stream<String> nomsDeChamps(Class<?> type) {
        if (type.isRecord()) {
            return Stream.of(type.getRecordComponents()).map(rc -> rc.getName());
        }
        return Stream.of(type.getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(f2 -> f2.getName());
    }

    private void aucunChampInterdit(Class<?> type) {
        List<String> fautifs = nomsDeChamps(type)
                .filter(nom -> {
                    String n = nom.toLowerCase(Locale.ROOT);
                    return FRAGMENTS_INTERDITS.stream().anyMatch(n::contains);
                })
                .toList();
        assertThat(fautifs)
                .as("%s est renvoyé au navigateur : aucun champ ne doit porter une clé Cloudinary "
                        + "ni une URL de pièce", type.getSimpleName())
                .isEmpty();
    }

    @Test
    @DisplayName("OrderResponseDto ne porte plus documentS3Key")
    void orderResponseDto() {
        aucunChampInterdit(OrderResponseDto.class);
        // Et porte bien le booléen de présence qui le remplace.
        assertThat(nomsDeChamps(OrderResponseDto.class)).contains("hasDocument");
    }

    @Test
    @DisplayName("EnrollmentResponseDto ne porte plus les URL des pièces du candidat")
    void enrollmentResponseDto() {
        aucunChampInterdit(EnrollmentResponseDto.class);
    }

    @Test
    @DisplayName("EnrollmentDetailDto ne porte ni clé ni URL de pièce, mais les présences")
    void enrollmentDetailDto() {
        aucunChampInterdit(EnrollmentDetailDto.class);
        assertThat(nomsDeChamps(EnrollmentDetailDto.class))
                .contains("hasConvention", "hasAttestation");
    }
}
