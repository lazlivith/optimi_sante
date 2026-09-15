package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.domain.training.entity.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Règle d'accès du médecin aux documents officiels : c'est elle qui décide à la fois de ce que
 * montre le coffre-fort et de ce que le téléchargement accepte. Aucune base, aucun Spring : la
 * règle ne lit que le document, l'état du dossier et ce qui reste dû.
 */
class OfficialDocumentAccessTest {

    private final OfficialDocumentService service = new OfficialDocumentService(null, null, null, null, null, null);

    private static EnrollmentOfficialDocument document(OfficialDocumentCategory categorie, OfficialDocumentStatus statut,
                                                       EnrollmentStatus dossier) {
        Enrollment inscription = new Enrollment();
        inscription.setStatus(dossier);
        return EnrollmentOfficialDocument.builder()
                .enrollment(inscription).category(categorie).status(statut).title("t").storageKey("k")
                .issuer(OfficialDocumentIssuer.PARTNER).build();
    }

    private static final java.util.function.Supplier<BigDecimal> RIEN_DU = () -> BigDecimal.ZERO;

    @Test
    void unDocumentNonPublieNestJamaisAccessible() {
        for (OfficialDocumentStatus statut : new OfficialDocumentStatus[]{
                OfficialDocumentStatus.PENDING_REVIEW, OfficialDocumentStatus.REJECTED}) {
            var acces = service.accesMedecin(
                    document(OfficialDocumentCategory.PROGRAMME, statut, EnrollmentStatus.READY_TO_START), RIEN_DU);
            assertFalse(acces.disponible(), statut.name());
        }
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class,
            names = {"UNDER_OPTIMI_REVIEW", "ACTION_REQUIRED", "SUBMITTED_TO_PARTNER", "ACCEPTED_BY_PARTNER", "PENDING_TUITION_FEE"})
    void leProgrammeResteVerrouilleAvantLAcompte(EnrollmentStatus dossier) {
        var acces = service.accesMedecin(
                document(OfficialDocumentCategory.PROGRAMME, OfficialDocumentStatus.PUBLISHED, dossier), RIEN_DU);
        assertFalse(acces.disponible());
        assertTrue(acces.motif().contains("acompte"));
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class,
            names = {"CONFIRMED", "CONVENTION_ISSUED", "VISA_SUBMITTED", "VISA_GRANTED", "READY_TO_START"})
    void leProgrammeSOuvreDesLAcompteRegle(EnrollmentStatus dossier) {
        assertTrue(service.accesMedecin(
                document(OfficialDocumentCategory.CONVENTION_CHU, OfficialDocumentStatus.PUBLISHED, dossier), RIEN_DU)
                .disponible());
    }

    @ParameterizedTest
    @EnumSource(value = EnrollmentStatus.class, names = {"REJECTED", "CANCELLED"})
    void unDossierCloFermeTout(EnrollmentStatus dossier) {
        assertFalse(service.accesMedecin(
                document(OfficialDocumentCategory.PROGRAMME, OfficialDocumentStatus.PUBLISHED, dossier), RIEN_DU)
                .disponible());
    }

    @Test
    void leKitAttendLeVisaSansMemeLireLesPaiements() {
        AtomicBoolean paiementsLus = new AtomicBoolean(false);
        var acces = service.accesMedecin(
                document(OfficialDocumentCategory.BILLET, OfficialDocumentStatus.PUBLISHED, EnrollmentStatus.CONVENTION_ISSUED),
                () -> { paiementsLus.set(true); return BigDecimal.ZERO; });
        assertFalse(acces.disponible());
        assertTrue(acces.motif().contains("visa"));
        assertFalse(paiementsLus.get(), "le reste dû ne doit être lu que si le visa est obtenu");
    }

    @Test
    void leKitAttendLeSoldeUneFoisLeVisaObtenu() {
        var acces = service.accesMedecin(
                document(OfficialDocumentCategory.HEBERGEMENT, OfficialDocumentStatus.PUBLISHED, EnrollmentStatus.VISA_GRANTED),
                () -> new BigDecimal("1280.00"));
        assertFalse(acces.disponible());
        assertTrue(acces.motif().contains("solde"));
    }

    @Test
    void leKitSeDebloqueVisaObtenuEtSoldeRegle() {
        assertTrue(service.accesMedecin(
                document(OfficialDocumentCategory.CONTACTS, OfficialDocumentStatus.PUBLISHED, EnrollmentStatus.READY_TO_START),
                RIEN_DU).disponible());
    }

    @Test
    void leChuNeDeposeQueLaPedagogie() {
        for (OfficialDocumentCategory categorie : OfficialDocumentCategory.values()) {
            assertEquals(!categorie.isKitDeDepart(), categorie.isDeposableParPartenaire(), categorie.name());
        }
    }
}
