package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.document.service.DocumentLinkService;
import com.optimisante.backend.domain.training.dto.AdminTrainingResponseDto;
import com.optimisante.backend.domain.training.entity.Training;
import com.optimisante.backend.domain.training.entity.TrainingApprovalStatus;
import com.optimisante.backend.domain.training.repository.TrainingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTrainingService {

    private final TrainingRepository trainingRepository;
    private final StorageService storageService;
    private final DocumentLinkService documentLinkService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public List<AdminTrainingResponseDto> listTrainings(TrainingApprovalStatus statusFilter) {
        List<Training> trainings = statusFilter != null
                ? trainingRepository.findByApprovalStatus(statusFilter)
                : trainingRepository.findAll();

        return trainings.stream()
                .sorted(Comparator.comparing(Training::getCreatedAt).reversed())
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Valide et publie la formation, en fixant au passage ses frais de dossier.
     *
     * <p>La revue est le moment ou l'administration examine ce que le partenaire a propose ;
     * c'est donc la, et pas dans un ecran separe, qu'elle decide de la remuneration
     * d'OptimiSante. Sans cela, une formation pouvait etre publiee sans que personne ait
     * consciemment arbitre ses frais — elle heritait du tarif global par simple omission.</p>
     *
     * @param feeProvided distingue « l'appelant n'a pas parle des frais » (on ne touche a
     *                    rien) de « l'appelant demande le tarif global » ({@code fee} nul).
     *                    Sans cette distinction, valider une formation effacerait un tarif
     *                    deja saisi.
     */
    @Transactional
    public AdminTrainingResponseDto approve(UUID trainingId, boolean feeProvided, java.math.BigDecimal fee) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("Formation introuvable : " + trainingId));
        if (feeProvided) {
            training.setApplicationFee(fee);
        }
        training.setApprovalStatus(TrainingApprovalStatus.APPROVED);
        training.setIsPublished(true);
        training.setRejectionReason(null);
        Training saved = trainingRepository.save(training);

        // Sans cette notification, un CHU ne savait pas si son offre etait publiee : il lui
        // fallait revenir la consulter au hasard dans son espace.
        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.TrainingApprovalDecided(
                saved.getId(), saved.getPartnerProfile().getUser().getId(), saved.getTitle(),
                true, null));

        return toResponseDto(saved);
    }

    @Transactional
    public AdminTrainingResponseDto reject(UUID trainingId, String reason) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found"));
        training.setApprovalStatus(TrainingApprovalStatus.REJECTED);
        training.setIsPublished(false);
        training.setRejectionReason(reason);
        Training saved = trainingRepository.save(training);

        eventPublisher.publishEvent(new com.optimisante.backend.domain.notification.event.NotificationEvents.TrainingApprovalDecided(
                saved.getId(), saved.getPartnerProfile().getUser().getId(), saved.getTitle(),
                false, reason));

        return toResponseDto(saved);
    }

    /**
     * Fixe ou retire les frais de dossier propres a la formation.
     *
     * <p>{@code null} n'est pas une erreur : il retire le tarif propre et fait revenir la
     * formation a la valeur globale. Le distinguer de zero est essentiel — zero voudrait dire
     * « candidature gratuite ».</p>
     *
     * <p>N'altere ni le statut de validation ni la publication : le contenu pedagogique n'a
     * pas change, seule la remuneration d'OptimiSante.</p>
     */
    @Transactional
    public AdminTrainingResponseDto setApplicationFee(UUID trainingId, java.math.BigDecimal fee) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("Formation introuvable : " + trainingId));
        training.setApplicationFee(fee);
        return toResponseDto(trainingRepository.save(training));
    }

    private AdminTrainingResponseDto toResponseDto(Training training) {
        return AdminTrainingResponseDto.builder()
                .id(training.getId())
                .title(training.getTitle())
                .medicalSpecialty(training.getMedicalSpecialty())
                .description(training.getDescription())
                .durationDays(training.getDurationDays())
                .isLongStay(training.getIsLongStay())
                .price(training.getPrice())
                .applicationFee(training.getApplicationFee())
                .isPublished(training.getIsPublished())
                .approvalStatus(training.getApprovalStatus().name())
                .rejectionReason(training.getRejectionReason())
                .brochureUrl(safeSignedUrl(training.getBrochureS3Key()))
                .imageUrl(safeMediaUrl(training.getImageS3Key(), "image"))
                .videoUrl(safeMediaUrl(training.getVideoS3Key(), "video"))
                .partnerInstitutionName(training.getPartnerProfile().getInstitutionName())
                .partnerContactEmail(training.getPartnerProfile().getContactEmail())
                .createdAt(training.getCreatedAt())
                .build();
    }

    private String safeSignedUrl(String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        try {
            return documentLinkService.lienDeTelechargement(publicId);
        } catch (Exception e) {
            log.error("Failed to generate signed URL for {}: {}", publicId, e.getMessage());
            return null;
        }
    }

    /**
     * Retire définitivement une formation, ses sessions et ses options.
     *
     * <p><b>Ce que la suppression refuse de faire.</b> Une session à laquelle un médecin s'est
     * inscrit porte un dossier, parfois un paiement et une convention ; une session sur laquelle
     * une candidature a été déposée porte une décision d'établissement. Les effacer au passage
     * ferait disparaître des faits comptables et médicaux au motif qu'on voulait ranger un
     * catalogue. La suppression est donc refusée dans ces deux cas, en nommant ce qui bloque.</p>
     *
     * <p><b>Ce qu'elle délie plutôt que d'effacer.</b> Un produit rattaché à la formation et un
     * prospect capté sur sa page existent indépendamment d'elle : ils perdent le lien, pas leur
     * existence.</p>
     */
    @Transactional
    public void supprimer(UUID trainingId) {
        Training formation = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("Formation introuvable."));

        long dossiers = trainingRepository.compterDossiers(trainingId);
        long candidatures = trainingRepository.compterCandidatures(trainingId);
        if (dossiers > 0 || candidatures > 0) {
            throw new IllegalStateException(
                    "Suppression impossible : cette formation porte " + dossiers + " dossier(s) "
                            + "et " + candidatures + " candidature(s). Dépubliez-la plutôt, pour "
                            + "la retirer du catalogue sans effacer ces enregistrements.");
        }

        trainingRepository.delierProduits(trainingId);
        trainingRepository.delierProspects(trainingId);
        trainingRepository.supprimerOptions(trainingId);
        long sessions = trainingRepository.supprimerSessions(trainingId);
        trainingRepository.delete(formation);

        log.info("Formation « {} » supprimée avec {} session(s)", formation.getTitle(), sessions);
    }

    private String safeMediaUrl(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) return null;
        try {
            return storageService.generateMediaUrl(publicId, resourceType);
        } catch (Exception e) {
            log.error("Failed to generate media URL for {}: {}", publicId, e.getMessage());
            return null;
        }
    }
}
