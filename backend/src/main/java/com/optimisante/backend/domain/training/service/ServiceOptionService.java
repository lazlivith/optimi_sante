package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.domain.training.dto.ServiceOptionDtos.*;
import com.optimisante.backend.domain.training.entity.*;
import com.optimisante.backend.domain.training.repository.EnrollmentDocumentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentServiceOptionRepository;
import com.optimisante.backend.domain.training.repository.TrainingRepository;
import com.optimisante.backend.domain.training.repository.TrainingServiceOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

import static com.optimisante.backend.domain.training.entity.EnrollmentStatus.*;

/**
 * Services organises par OptimiSante autour du sejour : assurance, hebergement, transport.
 *
 * <p><b>Le catalogue est tenu par l'administration de la mobilite, jamais par le partenaire.</b>
 * Meme regle que pour les frais de dossier (V40) : le CHU ne fixe pas la remuneration d'une
 * prestation qu'il ne fournit pas. Ces services sont montes par l'agence, qui paie ses propres
 * prestataires — d'ou une recette entierement acquise a la plateforme.</p>
 *
 * <p><b>Aucun statut de dossier n'est touche.</b> Souscrire un hebergement ne fait pas avancer
 * la candidature, et une inscription reste parfaitement valable sans aucune option. Meme choix
 * qu'en V37 pour les pieces et en V41 pour les entretiens.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceOptionService {

    /**
     * Etats du dossier ou souscrire a un sens.
     *
     * <p>Avant l'acceptation par l'etablissement, reserver un logement ou une assurance serait
     * premature : le candidat paierait des prestations pour un sejour qui peut ne pas avoir
     * lieu. Apres un rejet ou une annulation, il n'y a plus de sejour du tout.</p>
     */
    private static final EnumSet<EnrollmentStatus> DOSSIERS_OUVERTS = EnumSet.of(
            ACCEPTED_BY_PARTNER, PENDING_TUITION_FEE, CONFIRMED, CONVENTION_ISSUED,
            VISA_SUBMITTED, VISA_GRANTED, READY_TO_START);

    private static final EnumSet<ServiceOptionStatus> VIVANTES =
            EnumSet.of(ServiceOptionStatus.SELECTED, ServiceOptionStatus.PAID);

    private final TrainingServiceOptionRepository catalogRepository;
    private final EnrollmentServiceOptionRepository subscriptionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentDocumentRepository documentRepository;
    private final TrainingRepository trainingRepository;

    // ------------------------------------------------------------------ Catalogue admin ----

    /**
     * Depose ou remplace l'offre d'un type sur une formation.
     *
     * <p>Deposer une nouvelle offre du meme type <b>desactive la precedente</b> plutot que de
     * la modifier : les souscriptions deja passees continuent de pointer vers l'ancienne, avec
     * le tarif auquel elles ont ete acceptees. Modifier l'offre en place reecrirait
     * l'historique de ce qui a ete propose.</p>
     */
    @Transactional
    public CatalogOptionView upsertCatalogOption(UUID trainingId, UUID adminId,
                                                 CatalogOptionRequest dto) {
        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new IllegalArgumentException("Formation introuvable : " + trainingId));

        catalogRepository.findByTrainingIdAndOptionTypeAndIsActiveTrue(trainingId, dto.getOptionType())
                .ifPresent(ancienne -> {
                    ancienne.setIsActive(false);
                    catalogRepository.save(ancienne);
                    log.info("Offre {} remplacée sur la formation {}", dto.getOptionType(), trainingId);
                });
        // L'index unique partiel ne tolere qu'une offre active a la fois : la desactivation
        // doit etre visible en base avant l'insertion de la nouvelle.
        catalogRepository.flush();

        TrainingServiceOption offre = catalogRepository.save(TrainingServiceOption.builder()
                .training(training)
                .optionType(dto.getOptionType())
                .label(dto.getLabel().trim())
                .description(trimOrNull(dto.getDescription()))
                .price(dto.getPrice())
                .isActive(true)
                .createdBy(adminId)
                .build());

        log.info("Offre {} à {} EUR déposée sur la formation {} par l'admin {}",
                offre.getOptionType(), offre.getPrice(), trainingId, adminId);
        return toCatalogView(offre);
    }

    /** Retire une offre du catalogue. Les souscriptions deja prises ne sont pas touchees. */
    @Transactional
    public CatalogOptionView deactivateCatalogOption(UUID optionId) {
        TrainingServiceOption offre = catalogRepository.findById(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Offre introuvable : " + optionId));
        offre.setIsActive(false);
        log.info("Offre {} retirée du catalogue", optionId);
        return toCatalogView(catalogRepository.save(offre));
    }

    /** Catalogue complet d'une formation, offres retirees comprises. */
    @Transactional(readOnly = true)
    public List<CatalogOptionView> getCatalogForAdmin(UUID trainingId) {
        return catalogRepository.findByTrainingIdOrderByOptionTypeAscCreatedAtDesc(trainingId)
                .stream().map(this::toCatalogView).toList();
    }

    // -------------------------------------------------------------------- Cote medecin ----

    /**
     * Etat des services d'un dossier : ce qui reste offert, ce qui est retenu, ce qui reste du.
     */
    @Transactional(readOnly = true)
    public ServiceSummary getSummaryForDoctor(UUID enrollmentId, UUID doctorId) {
        Enrollment dossier = requireEnrollment(enrollmentId);
        requireDoctorOwns(dossier, doctorId);
        return summarize(dossier);
    }

    /** Meme vue, cote administration : elle instruit le dossier et doit voir ce qui a ete pris. */
    @Transactional(readOnly = true)
    public ServiceSummary getSummaryForAdmin(UUID enrollmentId) {
        return summarize(requireEnrollment(enrollmentId));
    }

    /**
     * Le medecin retient un ou plusieurs services.
     *
     * <p>Le prix et le libelle sont <b>copies</b> depuis le catalogue : c'est le tarif de
     * l'instant qui l'engage, et une renegociation ulterieure ne le modifiera pas.</p>
     */
    @Transactional
    public ServiceSummary selectOptions(UUID enrollmentId, UUID doctorId, SelectOptionsRequest dto) {
        Enrollment dossier = requireEnrollment(enrollmentId);
        requireDoctorOwns(dossier, doctorId);
        requireDossierOuvert(dossier);

        UUID trainingId = dossier.getSession().getTraining().getId();

        // Le meme service demande deux fois dans un seul envoi n'est pas une intention : ce
        // serait un double clic, et l'index unique le refuserait avec un message illisible.
        Set<UUID> demandes = new LinkedHashSet<>(dto.getCatalogOptionIds());

        for (UUID optionId : demandes) {
            TrainingServiceOption offre = catalogRepository.findById(optionId)
                    .orElseThrow(() -> new IllegalArgumentException("Service introuvable : " + optionId));

            if (!Boolean.TRUE.equals(offre.getIsActive())) {
                throw new IllegalStateException(
                        "« " + offre.getLabel() + " » n'est plus proposé.");
            }
            // Sans ce controle, un identifiant recopie depuis une autre formation ferait
            // souscrire un logement situe dans une autre ville que le stage.
            if (!offre.getTraining().getId().equals(trainingId)) {
                throw new IllegalArgumentException(
                        "« " + offre.getLabel() + " » ne fait pas partie des services de cette formation.");
            }
            if (subscriptionRepository.existsByEnrollmentIdAndOptionTypeAndStatusIn(
                    enrollmentId, offre.getOptionType(), VIVANTES)) {
                throw new IllegalStateException(
                        "Un service de type « " + libelleType(offre.getOptionType())
                        + " » est déjà retenu sur ce dossier.");
            }

            subscriptionRepository.save(EnrollmentServiceOption.builder()
                    .enrollment(dossier)
                    .catalogOption(offre)
                    .optionType(offre.getOptionType())
                    .label(offre.getLabel())
                    .unitPrice(offre.getPrice())
                    .status(ServiceOptionStatus.SELECTED)
                    .build());

            log.info("Service {} retenu sur le dossier {} par le médecin {} ({} EUR)",
                    offre.getOptionType(), enrollmentId, doctorId, offre.getPrice());
        }

        return summarize(requireEnrollment(enrollmentId));
    }

    /**
     * Le medecin retire un service qu'il n'a pas encore regle.
     *
     * <p>Un service <b>paye</b> ne peut pas etre retire de cette facon : de l'argent a ete
     * encaisse, et une annulation appelle un remboursement — une decision qui se prend avec
     * l'administration, pas d'un clic depuis l'espace du candidat.</p>
     */
    @Transactional
    public ServiceSummary cancelSelection(UUID subscriptionId, UUID doctorId) {
        EnrollmentServiceOption souscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Souscription introuvable."));
        Enrollment dossier = souscription.getEnrollment();
        requireDoctorOwns(dossier, doctorId);

        if (souscription.getStatus() == ServiceOptionStatus.PAID) {
            throw new IllegalStateException(
                    "Ce service est déjà réglé. Contactez Optimi Santé pour toute annulation.");
        }
        if (souscription.getStatus() == ServiceOptionStatus.CANCELLED) {
            throw new IllegalStateException("Ce service a déjà été retiré.");
        }

        souscription.setStatus(ServiceOptionStatus.CANCELLED);
        subscriptionRepository.save(souscription);
        log.info("Service {} retiré du dossier {}", souscription.getOptionType(), dossier.getId());

        return summarize(dossier);
    }

    // -------------------------------------------------------------------------- Interne ----

    /** Services retenus et non encore regles : ce que le prochain paiement doit couvrir. */
    @Transactional(readOnly = true)
    public List<EnrollmentServiceOption> getPayableOptions(UUID enrollmentId) {
        return subscriptionRepository.findByEnrollmentIdAndStatus(
                enrollmentId, ServiceOptionStatus.SELECTED);
    }

    public static BigDecimal total(List<EnrollmentServiceOption> options) {
        return options.stream()
                .map(EnrollmentServiceOption::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static String libelleType(ServiceOptionType type) {
        return switch (type) {
            case INSURANCE -> "Assurance";
            case HOUSING -> "Hébergement";
            case TRANSPORT -> "Transport";
        };
    }

    private ServiceSummary summarize(Enrollment dossier) {
        UUID enrollmentId = dossier.getId();
        List<EnrollmentServiceOption> souscriptions =
                subscriptionRepository.findByEnrollmentIdOrderBySelectedAtAsc(enrollmentId);

        Set<ServiceOptionType> dejaPris = souscriptions.stream()
                .filter(s -> VIVANTES.contains(s.getStatus()))
                .map(EnrollmentServiceOption::getOptionType)
                .collect(java.util.stream.Collectors.toSet());

        // Les offres déjà retenues ne sont plus proposées : les afficher inviterait à un geste
        // que le serveur refuse.
        List<CatalogOptionView> disponibles = DOSSIERS_OUVERTS.contains(dossier.getStatus())
                ? catalogRepository
                        .findByTrainingIdAndIsActiveTrueOrderByOptionTypeAsc(
                                dossier.getSession().getTraining().getId())
                        .stream()
                        .filter(o -> !dejaPris.contains(o.getOptionType()))
                        .map(this::toCatalogView)
                        .toList()
                : List.of();

        BigDecimal du = total(souscriptions.stream()
                .filter(s -> s.getStatus() == ServiceOptionStatus.SELECTED).toList());
        BigDecimal regle = total(souscriptions.stream()
                .filter(s -> s.getStatus() == ServiceOptionStatus.PAID).toList());

        return ServiceSummary.builder()
                .available(disponibles)
                .subscriptions(souscriptions.stream().map(this::toSubscriptionView).toList())
                .amountDue(du)
                .amountPaid(regle)
                .subscriptionDocumentId(dernierEtatDocument(dossier))
                .build();
    }

    /** La derniere attestation de souscription deposee au coffre, s'il y en a une. */
    private UUID dernierEtatDocument(Enrollment dossier) {
        return documentRepository
                .findByEnrollmentIdAndDocumentTypeOrderByUploadedAtDesc(
                        dossier.getId(), DocumentType.SERVICE_SUBSCRIPTION)
                .stream()
                .findFirst()
                .map(EnrollmentDocument::getId)
                .orElse(null);
    }

    private CatalogOptionView toCatalogView(TrainingServiceOption o) {
        return CatalogOptionView.builder()
                .id(o.getId())
                .trainingId(o.getTraining().getId())
                .optionType(o.getOptionType())
                .label(o.getLabel())
                .description(o.getDescription())
                .price(o.getPrice())
                .active(Boolean.TRUE.equals(o.getIsActive()))
                .createdAt(o.getCreatedAt())
                .build();
    }

    private SubscriptionView toSubscriptionView(EnrollmentServiceOption s) {
        return SubscriptionView.builder()
                .id(s.getId())
                .optionType(s.getOptionType())
                .label(s.getLabel())
                .unitPrice(s.getUnitPrice())
                .status(s.getStatus())
                .selectedAt(s.getSelectedAt())
                .paidAt(s.getPaidAt())
                .build();
    }

    private Enrollment requireEnrollment(UUID id) {
        return enrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable : " + id));
    }

    private void requireDoctorOwns(Enrollment dossier, UUID doctorId) {
        if (!dossier.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }
    }

    private void requireDossierOuvert(Enrollment dossier) {
        if (!DOSSIERS_OUVERTS.contains(dossier.getStatus())) {
            throw new IllegalStateException(
                    "Les services ne peuvent être souscrits qu'une fois votre candidature acceptée "
                    + "par l'établissement (statut actuel : " + dossier.getStatus() + ").");
        }
    }

    private String trimOrNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
