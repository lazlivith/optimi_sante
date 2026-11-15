package com.optimisante.backend.domain.partnership;

import com.optimisante.backend.domain.partnership.controller.PartnershipResource;
import com.optimisante.backend.domain.partnership.dto.PartnershipRequestResponseDto;
import com.optimisante.backend.domain.partnership.excel.ConventionExcelWriter;
import com.optimisante.backend.domain.partnership.excel.DossierExcelReader;
import com.optimisante.backend.domain.partnership.excel.DossierExcelWriter;
import com.optimisante.backend.domain.partnership.service.PartnershipService;
import com.optimisante.backend.infrastructure.debit.GardeDepotPartenariat;
import com.optimisante.backend.infrastructure.debit.ReglagesDepotPartenariat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * L'ordre des contrôles sur les deux routes de dépôt anonyme.
 *
 * <p>Ce que ces tests fixent : <b>rien ne part au stockage tant qu'un contrôle peut encore
 * refuser</b>. Le service — donc l'envoi du fichier — n'est appelé qu'après le jeton, les
 * champs et la signature du fichier.</p>
 */
class DepotAnonymePartenariatTest {

    private static final byte[] PDF = "%PDF-1.7\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private static final byte[] EXECUTABLE = {'M', 'Z', (byte) 0x90, 0, 3, 0};

    private final PartnershipService service = mock(PartnershipService.class);
    private final DossierExcelReader lecteur = mock(DossierExcelReader.class);

    /** Garde désactivée : ces tests portent sur les champs et le fichier, le jeton a les siens. */
    private final PartnershipResource ressource = new PartnershipResource(
            service, mock(DossierExcelWriter.class), mock(ConventionExcelWriter.class), lecteur,
            new GardeDepotPartenariat(
                    new ReglagesDepotPartenariat(false, null, null, null, null, null, null),
                    Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC)));

    private static MockMultipartFile convention(byte[] contenu) {
        return new MockMultipartFile("conventionFile", "convention.pdf", "application/pdf", contenu);
    }

    private Object deposer(String institution, String courriel, MockMultipartFile fichier) {
        return ressource.submitRequest(institution, "330000001", "Dr Martin", courriel,
                "+33 5 56 00 00 00", "1 place Amélie Raba-Léon, 33000 Bordeaux", fichier, null, null).getBody();
    }

    @Test
    @DisplayName("un dépôt valable atteint le service, et la réponse ne porte aucune clé de stockage")
    void depotValable() {
        when(service.submitRequest(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(PartnershipRequestResponseDto.builder()
                        .institutionName("CHU de Bordeaux")
                        .conventionFileKey("optimisante/partenariats/demandes/abc.pdf")
                        .status("PENDING")
                        .build());

        PartnershipRequestResponseDto recu =
                (PartnershipRequestResponseDto) deposer("CHU de Bordeaux", "direction@chu.example", convention(PDF));

        assertThat(recu.getStatus()).isEqualTo("PENDING");
        assertThat(recu.getConventionFileKey()).isNull();
    }

    @Test
    @DisplayName("un exécutable renommé en .pdf est refusé avant tout envoi au stockage")
    void fichierFalsifie() {
        assertThatThrownBy(() -> deposer("CHU de Bordeaux", "direction@chu.example", convention(EXECUTABLE)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("un champ démesuré ou une adresse e-mail invalide sont refusés avant tout envoi au stockage")
    void champsRefuses() {
        assertThatThrownBy(() -> deposer("x".repeat(256), "direction@chu.example", convention(PDF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255 caractères");
        assertThatThrownBy(() -> deposer("CHU de Bordeaux", "pas-une-adresse", convention(PDF)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("e-mail");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("garde active : sans jeton de formulaire, le service n'est jamais appelé")
    void jetonExige() {
        PartnershipResource gardee = new PartnershipResource(
                service, mock(DossierExcelWriter.class), mock(ConventionExcelWriter.class), lecteur,
                new GardeDepotPartenariat(ReglagesDepotPartenariat.defaut(),
                        Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC)));

        assertThatThrownBy(() -> gardee.submitRequest("CHU de Bordeaux", null, "Dr Martin",
                "direction@chu.example", "+33 5 56 00 00 00", "Bordeaux", convention(PDF), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("relecture : un fichier qui n'est pas un classeur n'est jamais ouvert, et le refus arrive comme un rapport")
    @SuppressWarnings("unchecked")
    void relectureRefuseeSansOuvrirLeFichier() {
        MockMultipartFile faux = new MockMultipartFile(
                "fichier", "C:\\Users\\chu\\dossier.xlsx", null, EXECUTABLE);

        Map<String, Object> rapport = ressource.verifierDossier(faux).getBody();

        verify(lecteur, never()).lire(any());
        assertThat(rapport.get("conforme")).isEqualTo(false);
        List<Map<String, Object>> anomalies = (List<Map<String, Object>>) rapport.get("anomalies");
        assertThat(anomalies).hasSize(1);
        assertThat(anomalies.get(0).get("probleme").toString()).contains(".xlsx");
        // Le chemin du poste n'est pas renvoyé : seul le nom du fichier l'est.
        assertThat(anomalies.get(0).get("valeur")).isEqualTo("dossier.xlsx");
    }
}
