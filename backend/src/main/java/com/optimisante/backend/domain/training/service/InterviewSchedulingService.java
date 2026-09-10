package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.common.email.EmailService;
import com.optimisante.backend.domain.document.service.PdfGeneratorService;
import com.optimisante.backend.domain.identity.repository.DoctorProfileRepository;
import com.optimisante.backend.domain.training.dto.InterviewDtos.*;
import com.optimisante.backend.domain.training.entity.*;
import com.optimisante.backend.domain.training.repository.EnrollmentDocumentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import com.optimisante.backend.domain.training.repository.InterviewScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Planification de l'entretien de selection en visioconference, a trois mains.
 *
 * <p><b>Le CHU propose, OptimiSante transmet, le medecin choisit.</b> Le detour par
 * l'administration n'est pas une lourdeur : c'est la regle deja inscrite dans
 * {@link EnrollmentTransitions} — le partenaire ne s'adresse jamais directement au medecin.
 * OptimiSante reste l'intermediaire du parcours et garde trace de ce qui a ete promis au
 * candidat.</p>
 *
 * <p><b>Ce service ne touche jamais au statut du dossier.</b> Meme choix qu'en V37 pour les
 * demandes de pieces : l'entretien accompagne la decision du partenaire, il ne la remplace pas.
 * Un dossier peut etre accepte sans entretien, et un entretien confirme ne fait pas avancer la
 * candidature — c'est le CHU qui decide, pas le calendrier.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewSchedulingService {

    /** Fuseau de reference pour les dates affichees. Les creneaux sont stockes en UTC. */
    private static final ZoneId FUSEAU = ZoneId.of("Europe/Paris");

    private static final DateTimeFormatter FORMAT_LONG =
            DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH'h'mm", Locale.FRENCH);

    private final InterviewScheduleRepository scheduleRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentDocumentRepository documentRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final EmailService emailService;

    // ---------------------------------------------------------------------- Partenaire ----

    /**
     * Le CHU depose ses creneaux. Le medecin ne les voit pas encore : ils attendent d'etre
     * transmis par l'administration.
     */
    @Transactional
    public ScheduleView propose(UUID enrollmentId, UUID partnerUserId, ProposeRequest dto) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        requirePartnerOwns(enrollment, partnerUserId);

        // L'index unique partiel de la V41 fait autorite — lui seul tient si deux requetes
        // arrivent en meme temps. Ce controle n'existe que pour repondre par un message
        // comprehensible plutot que par une violation de contrainte.
        scheduleRepository.findByEnrollmentIdAndStatusNot(enrollmentId, InterviewScheduleStatus.CANCELLED)
                .ifPresent(existant -> {
                    throw new IllegalStateException(
                            "Un entretien est déjà en cours sur ce dossier (" + existant.getStatus()
                            + "). Annulez-le avant d'en proposer un autre.");
                });

        // L'entretien se tient toujours en ligne : sans lien, le rendez-vous est injoignable.
        String lien = trimOrNull(dto.getMeetingLink());
        if (lien == null) {
            throw new IllegalArgumentException(
                    "Indiquez le lien de la réunion en ligne (Teams, Meet, Zoom…).");
        }

        // Un seul créneau n'est pas un choix : c'est une convocation déguisée, sans que le
        // médecin puisse dire qu'il n'est pas disponible.
        List<SlotRequest> demandes = dto.getSlots() == null ? List.of() : dto.getSlots();
        if (demandes.size() < 2) {
            throw new IllegalArgumentException(
                    "Proposez au moins deux créneaux : un seul ne laisse aucun choix au médecin.");
        }

        InterviewSchedule entretien = InterviewSchedule.builder()
                .enrollment(enrollment)
                .status(InterviewScheduleStatus.PROPOSED)
                .meetingLink(lien)
                .partnerNote(trimOrNull(dto.getPartnerNote()))
                .proposedBy(partnerUserId)
                .build();

        OffsetDateTime maintenant = OffsetDateTime.now();
        Set<OffsetDateTime> debuts = new HashSet<>();
        for (SlotRequest s : demandes) {
            if (!s.getEndsAt().isAfter(s.getStartsAt())) {
                throw new IllegalArgumentException("Un créneau ne peut pas se terminer avant de commencer.");
            }
            // Proposer une date passée est une erreur de saisie, jamais une intention : le
            // médecin verrait un rendez-vous qu'il lui est impossible d'honorer.
            if (s.getStartsAt().isBefore(maintenant)) {
                throw new IllegalArgumentException(
                        "Le créneau du " + formatter(s.getStartsAt()) + " est déjà passé.");
            }
            if (!debuts.add(s.getStartsAt())) {
                throw new IllegalArgumentException(
                        "Le créneau du " + formatter(s.getStartsAt()) + " est proposé deux fois.");
            }
            entretien.getSlots().add(InterviewSlot.builder()
                    .schedule(entretien)
                    .startsAt(s.getStartsAt())
                    .endsAt(s.getEndsAt())
                    .build());
        }

        log.info("Entretien proposé sur le dossier {} par le partenaire {} ({} créneaux)",
                enrollmentId, partnerUserId, demandes.size());
        return toView(scheduleRepository.save(entretien));
    }

    // ------------------------------------------------------------------ Administration ----

    /**
     * OptimiSante transmet les creneaux au medecin.
     *
     * <p>C'est ce geste qui rend l'entretien visible cote medecin, et lui seul. Sans lui, les
     * creneaux restent une proposition interne entre le CHU et l'agence.</p>
     */
    @Transactional
    public ScheduleView transmit(UUID scheduleId, UUID adminId, TransmitRequest dto) {
        InterviewSchedule entretien = requireSchedule(scheduleId);

        if (entretien.getStatus() != InterviewScheduleStatus.PROPOSED) {
            throw new IllegalStateException(
                    "Seul un entretien à l'état PROPOSED peut être transmis. État actuel : "
                    + entretien.getStatus() + ".");
        }

        // Les créneaux dépassés entre la proposition et la transmission ne sont pas retirés :
        // les supprimer effacerait la trace de ce que le CHU avait offert. Ils sont refusés
        // à la transmission, pour que l'administration relance le CHU plutôt que d'envoyer au
        // médecin une liste dont une partie est morte.
        OffsetDateTime maintenant = OffsetDateTime.now();
        boolean tousPasses = entretien.getSlots().stream()
                .allMatch(s -> s.getStartsAt().isBefore(maintenant));
        if (tousPasses) {
            throw new IllegalStateException(
                    "Tous les créneaux proposés sont dépassés. Demandez au partenaire d'en proposer de nouveaux.");
        }

        entretien.setStatus(InterviewScheduleStatus.TRANSMITTED);
        entretien.setTransmittedBy(adminId);
        entretien.setTransmittedAt(OffsetDateTime.now());
        entretien.setAdminNote(dto == null ? null : trimOrNull(dto.getAdminNote()));

        InterviewSchedule sauve = scheduleRepository.save(entretien);

        Enrollment dossier = sauve.getEnrollment();
        long ouverts = sauve.getSlots().stream()
                .filter(s -> !s.getStartsAt().isBefore(maintenant)).count();
        emailService.sendInterviewSlotsEmail(
                dossier.getDoctor().getEmail(),
                nomMedecin(dossier),
                dossier.getSession().getTraining().getTitle(),
                dossier.getSession().getTraining().getPartnerProfile().getInstitutionName(),
                (int) ouverts,
                dossier.getDoctor().getId());

        log.info("Entretien {} transmis au médecin par l'admin {}", scheduleId, adminId);
        return toView(sauve);
    }

    /** Abandon de la planification. Un nouvel entretien redevient alors possible sur le dossier. */
    @Transactional
    public ScheduleView cancel(UUID scheduleId, UUID actorId, CancelRequest dto) {
        InterviewSchedule entretien = requireSchedule(scheduleId);

        if (entretien.getStatus() == InterviewScheduleStatus.CANCELLED) {
            throw new IllegalStateException("Cet entretien est déjà annulé.");
        }
        String motif = trimOrNull(dto == null ? null : dto.getReason());
        if (motif == null) {
            throw new IllegalArgumentException("Un motif est obligatoire pour annuler un entretien.");
        }

        // Le créneau retenu et sa date de confirmation sont conservés. La branche CANCELLED
        // de la contrainte de cohérence (V41) ne les interdit pas, et les effacer ferait
        // perdre l'essentiel : qu'un rendez-vous avait bien été pris, et lequel. Un entretien
        // annulé après confirmation et un entretien annulé avant se liraient sinon pareil.
        entretien.setStatus(InterviewScheduleStatus.CANCELLED);
        entretien.setCancelledReason(motif);

        // La convocation quitte les documents du médecin. La laisser lui montrerait, dans son
        // dossier, un rendez-vous qui n'aura pas lieu — et rien à l'écran ne distinguerait
        // cette convocation d'une valable. La trace de ce qui s'est passé ne disparaît pas
        // pour autant : la ligne d'entretien conserve la confirmation, l'annulation et son
        // motif, ainsi que le créneau qui avait été retenu.
        EnrollmentDocument convocation = entretien.getConvocationDocument();
        if (convocation != null) {
            entretien.setConvocationDocument(null);
            scheduleRepository.saveAndFlush(entretien);
            documentRepository.delete(convocation);
            log.info("Convocation {} retirée du dossier : entretien {} annulé",
                    convocation.getId(), scheduleId);
        }

        log.info("Entretien {} annulé par {} : {}", scheduleId, actorId, motif);
        return toView(scheduleRepository.save(entretien));
    }

    // ------------------------------------------------------------------------- Medecin ----

    /**
     * Le medecin retient un creneau : le rendez-vous est pris.
     *
     * <p>La convocation est generee et deposee au coffre du dossier dans la foulee, et les deux
     * parties sont prevenues. La generation du PDF ne doit pas pouvoir faire echouer la
     * confirmation : un rendez-vous pris reste pris meme si le document n'a pas pu etre
     * produit — il sera regenere, alors qu'un choix perdu obligerait a tout recommencer.</p>
     */
    @Transactional
    public ScheduleView confirm(UUID scheduleId, UUID doctorId, ConfirmRequest dto) {
        InterviewSchedule entretien = requireSchedule(scheduleId);
        Enrollment dossier = entretien.getEnrollment();

        if (!dossier.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }
        if (entretien.getStatus() != InterviewScheduleStatus.TRANSMITTED) {
            throw new IllegalStateException(entretien.getStatus() == InterviewScheduleStatus.CONFIRMED
                    ? "Un créneau a déjà été retenu pour cet entretien."
                    : "Cet entretien n'est pas ouvert au choix (" + entretien.getStatus() + ").");
        }

        InterviewSlot choisi = entretien.getSlots().stream()
                .filter(s -> s.getId().equals(dto.getSlotId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Ce créneau ne fait pas partie de ceux qui vous sont proposés."));

        if (choisi.getStartsAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalStateException(
                    "Ce créneau est dépassé. Choisissez-en un autre, ou signalez-le à Optimi Santé.");
        }

        entretien.setStatus(InterviewScheduleStatus.CONFIRMED);
        entretien.setConfirmedSlot(choisi);
        entretien.setConfirmedAt(OffsetDateTime.now());
        InterviewSchedule sauve = scheduleRepository.save(entretien);

        deposerConvocation(sauve, choisi);
        notifierConfirmation(sauve, choisi);

        log.info("Créneau {} retenu par le médecin {} sur l'entretien {}",
                choisi.getId(), doctorId, scheduleId);
        return toView(sauve);
    }

    // ------------------------------------------------------------------------ Lectures ----

    @Transactional(readOnly = true)
    public List<ScheduleView> historyForAdmin(UUID enrollmentId) {
        requireEnrollment(enrollmentId);
        return scheduleRepository.findByEnrollmentIdOrderByProposedAtDesc(enrollmentId)
                .stream().map(this::toView).toList();
    }

    /**
     * File de travail de l'administration : les entretiens qui attendent d'etre transmis.
     *
     * <p>Seul point du service qui liste des dossiers <i>differents</i>, donc le seul expose au
     * N+1 : les autres lectures portent sur un dossier unique. Les associations arrivent par
     * jointure, et les noms des medecins en un appel groupe — {@code findByUserId} appele en
     * boucle produisait une requete par ligne.</p>
     */
    @Transactional(readOnly = true)
    public List<ScheduleView> awaitingTransmission() {
        List<InterviewSchedule> entretiens =
                scheduleRepository.findAwaiting(InterviewScheduleStatus.PROPOSED);

        Set<UUID> medecins = entretiens.stream()
                .map(e -> e.getEnrollment().getDoctor().getId())
                .collect(java.util.stream.Collectors.toSet());
        Map<UUID, String> noms = medecins.isEmpty() ? Map.of()
                : doctorProfileRepository.findByUserIdIn(medecins).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                p -> p.getUser().getId(),
                                p -> p.getFirstName() + " " + p.getLastName()));

        return entretiens.stream().map(e -> toView(e, noms)).toList();
    }

    @Transactional(readOnly = true)
    public List<ScheduleView> historyForPartner(UUID enrollmentId, UUID partnerUserId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        requirePartnerOwns(enrollment, partnerUserId);
        return scheduleRepository.findByEnrollmentIdOrderByProposedAtDesc(enrollmentId)
                .stream().map(this::toView).toList();
    }

    /**
     * Ce que le medecin voit de son entretien.
     *
     * <p>Un entretien encore {@code PROPOSED} lui est cache : tant qu'OptimiSante ne l'a pas
     * transmis, il n'existe pas pour lui. Le lui montrer reviendrait a laisser le CHU le
     * convoquer directement, ce que tout le circuit cherche a eviter.</p>
     */
    @Transactional(readOnly = true)
    public Optional<ScheduleView> currentForDoctor(UUID enrollmentId, UUID doctorId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        if (!enrollment.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }
        return scheduleRepository.findByEnrollmentIdAndStatusNot(
                        enrollmentId, InterviewScheduleStatus.CANCELLED)
                .filter(e -> e.getStatus() != InterviewScheduleStatus.PROPOSED)
                .map(this::toView);
    }

    // -------------------------------------------------------------------------- Interne ----

    /**
     * Genere la convocation et la depose au coffre du dossier.
     *
     * <p>Les erreurs sont avalees volontairement : le rendez-vous est deja pris et enregistre.
     * Laisser une panne de generation annuler la transaction ferait perdre le choix du medecin
     * pour un document qui, lui, peut etre reproduit.</p>
     */
    private void deposerConvocation(InterviewSchedule entretien, InterviewSlot creneau) {
        try {
            Enrollment dossier = entretien.getEnrollment();
            Training formation = dossier.getSession().getTraining();

            Map<String, Object> donnees = new HashMap<>();
            donnees.put("doctorName", nomMedecin(dossier));
            donnees.put("doctorEmail", dossier.getDoctor().getEmail());
            donnees.put("trainingTitle", formation.getTitle());
            donnees.put("institutionName", formation.getPartnerProfile().getInstitutionName());
            donnees.put("interviewDate", formatter(creneau.getStartsAt()));
            donnees.put("interviewEnd", heure(creneau.getEndsAt()));
            donnees.put("meetingLink", entretien.getMeetingLink());
            donnees.put("partnerNote", entretien.getPartnerNote());
            donnees.put("adminNote", entretien.getAdminNote());
            donnees.put("reference", "ENT-" + entretien.getId().toString().substring(0, 8).toUpperCase());
            donnees.put("issuedAt", formatter(OffsetDateTime.now()));

            String publicId = pdfGeneratorService.generateAndUploadPdf(
                    "convocation-entretien", donnees, "docs/enrollments",
                    "convocation-" + entretien.getId());

            EnrollmentDocument piece = documentRepository.save(EnrollmentDocument.builder()
                    .enrollment(dossier)
                    .documentType(DocumentType.INTERVIEW_CONVOCATION)
                    .cloudinaryPublicId(publicId)
                    // Emise par la plateforme, pas deposee par le candidat : rien a verifier.
                    .isVerified(true)
                    .build());

            entretien.setConvocationDocument(piece);
            scheduleRepository.save(entretien);
        } catch (Exception e) {
            log.error("Convocation non générée pour l'entretien {} : {}",
                    entretien.getId(), e.getMessage());
        }
    }

    /** Previent le medecin et l'etablissement. Echec non bloquant, comme tous les envois. */
    private void notifierConfirmation(InterviewSchedule entretien, InterviewSlot creneau) {
        Enrollment dossier = entretien.getEnrollment();
        Training formation = dossier.getSession().getTraining();
        String medecin = nomMedecin(dossier);
        String creneauLisible = formatter(creneau.getStartsAt());

        emailService.sendInterviewConfirmedEmail(
                dossier.getDoctor().getEmail(), medecin, medecin, formation.getTitle(),
                creneauLisible, entretien.getMeetingLink(), dossier.getDoctor().getId());

        // L'etablissement est prevenu du meme rendez-vous : c'est lui qui recevra le candidat.
        String emailPartenaire = formation.getPartnerProfile().getContactEmail();
        if (emailPartenaire != null && !emailPartenaire.isBlank()) {
            emailService.sendInterviewConfirmedEmail(
                    emailPartenaire, formation.getPartnerProfile().getInstitutionName(), medecin,
                    formation.getTitle(), creneauLisible, entretien.getMeetingLink(), null);
        }
    }

    private ScheduleView toView(InterviewSchedule e) {
        // Sans annuaire : le nom est cherche ligne a ligne, ce qui convient aux lectures
        // portant sur un seul dossier.
        return toView(e, Map.of());
    }

    private ScheduleView toView(InterviewSchedule e, Map<UUID, String> nomsDejaCharges) {
        Enrollment dossier = e.getEnrollment();
        Training formation = dossier.getSession().getTraining();
        UUID retenu = e.getConfirmedSlot() == null ? null : e.getConfirmedSlot().getId();

        return ScheduleView.builder()
                .id(e.getId())
                .enrollmentId(dossier.getId())
                .status(e.getStatus())
                .meetingLink(e.getMeetingLink())
                .partnerNote(e.getPartnerNote())
                .adminNote(e.getAdminNote())
                .proposedAt(e.getProposedAt())
                .transmittedAt(e.getTransmittedAt())
                .confirmedAt(e.getConfirmedAt())
                .cancelledReason(e.getCancelledReason())
                .slots(e.getSlots().stream()
                        .sorted(Comparator.comparing(InterviewSlot::getStartsAt))
                        .map(s -> SlotView.builder()
                                .id(s.getId())
                                .startsAt(s.getStartsAt())
                                .endsAt(s.getEndsAt())
                                .confirmed(s.getId().equals(retenu))
                                .build())
                        .toList())
                .doctorName(nomDepuis(nomsDejaCharges, dossier))
                .doctorEmail(dossier.getDoctor().getEmail())
                .trainingTitle(formation.getTitle())
                .partnerInstitutionName(formation.getPartnerProfile().getInstitutionName())
                .convocationDocumentId(e.getConvocationDocument() == null
                        ? null : e.getConvocationDocument().getId())
                .build();
    }

    /**
     * Nom du medecin, pris dans l'annuaire deja charge quand il s'y trouve.
     *
     * <p>Ecrit en deux temps, et non avec {@code getOrDefault} : celui-ci <b>evalue son
     * argument par defaut dans tous les cas</b>. La requete par ligne partait donc malgre le
     * chargement groupe, qui n'ajoutait qu'une requete de plus — mesure a 8 acces a
     * {@code doctor_profiles} pour 7 entretiens. Un chargement groupe qui ne remplace rien est
     * pire que pas de chargement groupe du tout.</p>
     */
    private String nomDepuis(Map<UUID, String> annuaire, Enrollment dossier) {
        String connu = annuaire.get(dossier.getDoctor().getId());
        return connu != null ? connu : nomMedecin(dossier);
    }

    private String nomMedecin(Enrollment dossier) {
        return doctorProfileRepository.findByUserId(dossier.getDoctor().getId())
                .map(p -> p.getFirstName() + " " + p.getLastName())
                .orElse(dossier.getDoctor().getEmail());
    }

    private String formatter(OffsetDateTime moment) {
        return moment.atZoneSameInstant(FUSEAU).format(FORMAT_LONG);
    }

    private String heure(OffsetDateTime moment) {
        return moment.atZoneSameInstant(FUSEAU)
                .format(DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH));
    }

    private Enrollment requireEnrollment(UUID id) {
        return enrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable : " + id));
    }

    private InterviewSchedule requireSchedule(UUID id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entretien introuvable : " + id));
    }

    /** Meme regle de propriete que {@code EnrollmentService.partnerDecision}. */
    private void requirePartnerOwns(Enrollment enrollment, UUID partnerUserId) {
        if (!enrollment.getSession().getTraining().getPartnerProfile().getUser().getId()
                .equals(partnerUserId)) {
            throw new AccessDeniedException("Ce dossier ne relève pas de votre établissement.");
        }
    }

    private String trimOrNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
