package com.optimisante.backend.domain.training.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.optimisante.backend.domain.training.entity.EnrollmentStatus.*;
import static com.optimisante.backend.domain.training.entity.RegistrationType.INTERNATIONAL_VISA;
import static com.optimisante.backend.domain.training.entity.RegistrationType.LOCAL_FRANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Automate de candidature, selon le parcours")
class EnrollmentTransitionsParcoursTest {

    @Nested
    @DisplayName("Le parcours international ne change pas")
    class International {

        @Test
        @DisplayName("les quatre étapes de mobilité restent celles d'avant")
        void mobiliteInchangee() {
            assertThat(EnrollmentTransitions.allowedFrom(CONVENTION_ISSUED, INTERNATIONAL_VISA))
                    .containsExactlyInAnyOrder(VISA_SUBMITTED, CANCELLED);
            assertThat(EnrollmentTransitions.allowedFrom(VISA_SUBMITTED, INTERNATIONAL_VISA))
                    .containsExactlyInAnyOrder(VISA_GRANTED, CANCELLED);
            assertThat(EnrollmentTransitions.allowedFrom(VISA_GRANTED, INTERNATIONAL_VISA))
                    .containsExactlyInAnyOrder(READY_TO_START, CANCELLED);
        }

        @Test
        @DisplayName("sauter les étapes de visa reste interdit")
        void pasDeRaccourci() {
            assertThatThrownBy(() -> EnrollmentTransitions.assertAllowed(
                    CONVENTION_ISSUED, READY_TO_START, INTERNATIONAL_VISA))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Transition interdite");
        }

        @Test
        @DisplayName("un parcours absent vaut international, par continuité")
        void parcoursAbsent() {
            assertThat(EnrollmentTransitions.allowedFrom(CONVENTION_ISSUED, null))
                    .isEqualTo(EnrollmentTransitions.allowedFrom(CONVENTION_ISSUED));
            assertThat(EnrollmentTransitions.isAllowed(CONVENTION_ISSUED, READY_TO_START, null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Le parcours France saute les deux étapes de visa")
    class France {

        @Test
        @DisplayName("la convention mène directement à la convocation")
        void conventionVersConvocation() {
            assertThatCode(() -> EnrollmentTransitions.assertAllowed(
                    CONVENTION_ISSUED, READY_TO_START, LOCAL_FRANCE))
                    .doesNotThrowAnyException();
            assertThat(EnrollmentTransitions.allowedFrom(CONVENTION_ISSUED, LOCAL_FRANCE))
                    .containsExactlyInAnyOrder(READY_TO_START, CANCELLED);
        }

        @Test
        @DisplayName("les statuts de visa lui sont fermés")
        void pasDeVisa() {
            assertThatThrownBy(() -> EnrollmentTransitions.assertAllowed(
                    CONVENTION_ISSUED, VISA_SUBMITTED, LOCAL_FRANCE))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(EnrollmentTransitions.allowedFrom(VISA_SUBMITTED, LOCAL_FRANCE)).isEmpty();
            assertThat(EnrollmentTransitions.allowedFrom(VISA_GRANTED, LOCAL_FRANCE)).isEmpty();
        }

        @Test
        @DisplayName("l'annulation reste ouverte jusqu'à la convocation")
        void annulationPossible() {
            assertThat(EnrollmentTransitions.isAllowed(CONVENTION_ISSUED, CANCELLED, LOCAL_FRANCE)).isTrue();
            assertThat(EnrollmentTransitions.isAllowed(CONFIRMED, CANCELLED, LOCAL_FRANCE)).isTrue();
        }

        @Test
        @DisplayName("tout le cycle commercial est identique à l'international")
        void cycleCommercialCommun() {
            for (EnrollmentStatus depart : new EnrollmentStatus[]{
                    UNDER_OPTIMI_REVIEW, ACTION_REQUIRED, SUBMITTED_TO_PARTNER,
                    ACCEPTED_BY_PARTNER, PENDING_TUITION_FEE, CONFIRMED}) {
                assertThat(EnrollmentTransitions.allowedFrom(depart, LOCAL_FRANCE))
                        .as("depuis %s", depart)
                        .isEqualTo(EnrollmentTransitions.allowedFrom(depart, INTERNATIONAL_VISA));
            }
        }

        @Test
        @DisplayName("les états terminaux le restent")
        void etatsTerminaux() {
            assertThat(EnrollmentTransitions.allowedFrom(REJECTED, LOCAL_FRANCE)).isEmpty();
            assertThat(EnrollmentTransitions.allowedFrom(CANCELLED, LOCAL_FRANCE)).isEmpty();
            assertThat(EnrollmentTransitions.allowedFrom(READY_TO_START, LOCAL_FRANCE)).isEmpty();
        }
    }

    @Test
    @DisplayName("passer au même statut est refusé, quel que soit le parcours")
    void memeStatut() {
        assertThatThrownBy(() -> EnrollmentTransitions.assertAllowed(CONFIRMED, CONFIRMED, LOCAL_FRANCE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà au statut");
    }
}
