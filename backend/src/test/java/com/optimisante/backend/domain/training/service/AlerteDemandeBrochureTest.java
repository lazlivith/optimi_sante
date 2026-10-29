package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.common.email.EmailService;
import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.document.service.DocumentLinkService;
import com.optimisante.backend.domain.identity.entity.PartnerProfile;
import com.optimisante.backend.domain.identity.entity.Role;
import com.optimisante.backend.domain.identity.repository.PartnerProfileRepository;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import com.optimisante.backend.domain.identity.repository.UserRepository;
import com.optimisante.backend.domain.training.dto.LeadCaptureRequestDto;
import com.optimisante.backend.domain.training.dto.LeadCaptureResponseDto;
import com.optimisante.backend.domain.training.entity.ProspectLead;
import com.optimisante.backend.domain.training.entity.SessionStatus;
import com.optimisante.backend.domain.training.entity.Training;
import com.optimisante.backend.domain.training.repository.ProspectLeadRepository;
import com.optimisante.backend.domain.training.repository.TrainingRepository;
import com.optimisante.backend.domain.training.repository.TrainingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Ce qui part — et ne part pas — quand un visiteur demande une brochure.
 *
 * <p><b>Pourquoi cette alerte existe.</b> La demande rendait au visiteur un lien vers la
 * brochure d'origine du partenaire. Ce lien a été coupé ; sans alerte, plus rien ne partait
 * alors ni vers le prospect, ni vers nous, et la demande dormait. Le prospect, lui, ne doit
 * jamais subir notre relais de courriel.</p>
 */
@DisplayName("Alerte interne sur une demande de brochure")
class AlerteDemandeBrochureTest {

    private static final UUID FORMATION = UUID.randomUUID();

    private TrainingRepository formations;
    private ProspectLeadRepository prospects;
    private TrainingSessionRepository sessions;
    private UserRepository utilisateurs;
    private EmailService courriels;
    private TrainingService service;

    @BeforeEach
    void preparer() {
        formations = mock(TrainingRepository.class);
        prospects = mock(ProspectLeadRepository.class);
        sessions = mock(TrainingSessionRepository.class);
        utilisateurs = mock(UserRepository.class);
        courriels = mock(EmailService.class);

        service = new TrainingService(
                formations,
                mock(ApplicationEventPublisher.class),
                sessions,
                prospects,
                mock(PartnerProfileRepository.class),
                mock(TenantRepository.class),
                mock(StorageService.class),
                mock(DocumentLinkService.class),
                courriels,
                utilisateurs);

        Training formation = new Training();
        formation.setId(FORMATION);
        formation.setTitle("Échographie d'urgence");
        formation.setPartnerProfile(new PartnerProfile());
        when(formations.findById(FORMATION)).thenReturn(Optional.of(formation));

        // Aucune session ouverte : la ligne « Session » doit alors disparaître du message,
        // plutôt qu'annoncer une période que personne n'a fixée.
        when(sessions.findByTrainingIdAndStatus(FORMATION, SessionStatus.OPEN))
                .thenReturn(List.of());

        when(prospects.save(any(ProspectLead.class))).thenAnswer(appel -> {
            ProspectLead lead = appel.getArgument(0);
            lead.setId(UUID.randomUUID());
            lead.setDownloadedAt(OffsetDateTime.now());
            return lead;
        });
        when(utilisateurs.emailsActifsParRoles(List.of(Role.SUPER_ADMIN, Role.ADMIN_MOBILITE)))
                .thenReturn(List.of("mobilite@optimisante.example"));
    }

    private static LeadCaptureRequestDto demande() {
        LeadCaptureRequestDto dto = new LeadCaptureRequestDto();
        dto.setEmail("praticien@exemple.example");
        dto.setFirstName("Awa");
        dto.setLastName("Diallo");
        dto.setPhoneWhatsapp("+242 06 000 12 34");
        dto.setCountry("Congo");
        dto.setSpecialty("Radiologie");
        return dto;
    }

    @Test
    @DisplayName("prévient l'administration, avec les coordonnées et sans pièce jointe")
    void previentLAdministration() {
        when(prospects.existsByEmailIgnoreCaseAndTrainingIdAndDownloadedAtAfter(
                anyString(), any(UUID.class), any(OffsetDateTime.class))).thenReturn(false);

        LeadCaptureResponseDto reponse = service.captureLead(FORMATION, demande());

        assertThat(reponse.isDemandeEnregistree()).isTrue();
        verify(prospects).save(any(ProspectLead.class));

        ArgumentCaptor<String> destinataire = ArgumentCaptor.forClass(String.class);
        verify(courriels).sendDemandeBrochureAlerteAdmin(
                destinataire.capture(), eq("Échographie d'urgence"),
                // Pas de session ouverte : null, et non une date de remplissage.
                isNull(), eq("Awa Diallo"), eq("praticien@exemple.example"),
                eq("+242 06 000 12 34"), eq("Congo"), eq("Radiologie"), anyString());
        assertThat(destinataire.getValue()).isEqualTo("mobilite@optimisante.example");
    }

    @Test
    @DisplayName("ne renvoie pas d'alerte pour la même demande dans les 24 h, mais enregistre")
    void neRepetePasLAlerte() {
        when(prospects.existsByEmailIgnoreCaseAndTrainingIdAndDownloadedAtAfter(
                anyString(), any(UUID.class), any(OffsetDateTime.class))).thenReturn(true);

        LeadCaptureResponseDto reponse = service.captureLead(FORMATION, demande());

        // La trace compte plus que l'alerte : c'est le message qu'on ne répète pas, pas la
        // demande qu'on refuse.
        verify(prospects).save(any(ProspectLead.class));
        verifyNoInteractions(courriels);
        assertThat(reponse.isDemandeEnregistree()).isTrue();
    }

    @Test
    @DisplayName("un envoi en échec ne remonte jamais au visiteur")
    void unEchecDEnvoiNeRemontePas() {
        when(prospects.existsByEmailIgnoreCaseAndTrainingIdAndDownloadedAtAfter(
                anyString(), any(UUID.class), any(OffsetDateTime.class))).thenReturn(false);
        doThrow(new RuntimeException("relais SMTP indisponible"))
                .when(courriels).sendDemandeBrochureAlerteAdmin(
                        anyString(), anyString(), any(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyString());

        // Le prospect a laissé ses coordonnées et elles sont enregistrées : lui répondre par
        // une erreur lui ferait croire que sa demande est perdue.
        LeadCaptureResponseDto reponse = service.captureLead(FORMATION, demande());

        assertThat(reponse.isDemandeEnregistree()).isTrue();
        verify(prospects).save(any(ProspectLead.class));
    }

    @Test
    @DisplayName("aucun compte d'administration actif : la demande passe quand même")
    void aucunDestinataire() {
        when(prospects.existsByEmailIgnoreCaseAndTrainingIdAndDownloadedAtAfter(
                anyString(), any(UUID.class), any(OffsetDateTime.class))).thenReturn(false);
        when(utilisateurs.emailsActifsParRoles(any())).thenReturn(List.of());

        LeadCaptureResponseDto reponse = service.captureLead(FORMATION, demande());

        assertThat(reponse.isDemandeEnregistree()).isTrue();
        verifyNoInteractions(courriels);
    }
}
