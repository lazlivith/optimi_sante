package com.optimisante.backend.domain.training.service;

import com.optimisante.backend.common.storage.StorageService;
import com.optimisante.backend.domain.training.dto.DocumentRequestDtos.CreateRequest;
import com.optimisante.backend.domain.training.dto.DocumentRequestDtos.DossierSummary;
import com.optimisante.backend.domain.training.dto.DocumentRequestDtos.RequestView;
import com.optimisante.backend.domain.training.dto.DocumentRequestDtos.ReviewRequest;
import com.optimisante.backend.domain.training.entity.*;
import com.optimisante.backend.domain.training.repository.EnrollmentDocumentRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentDocumentRequestRepository;
import com.optimisante.backend.domain.training.repository.EnrollmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Espace de constitution du dossier de pieces d'un candidat.
 *
 * <p>Trois acteurs, trois gestes : l'administration <b>reclame</b> une piece, le medecin la
 * <b>depose</b>, l'administration <b>verifie</b>. Chaque piece est suivie separement, de sorte
 * qu'a tout moment on sache ce qui manque encore pour la demande de visa.</p>
 *
 * <p><b>Ce service ne touche jamais au statut du dossier.</b> C'est sa difference essentielle
 * avec {@code EnrollmentService.requestAction}, qui bascule le dossier en
 * {@code ACTION_REQUIRED} et bloque la procedure. Les pieces du dossier de visa se rassemblent
 * <i>pendant</i> la procedure — apres l'acceptation du CHU, apres la convention — moments ou
 * renvoyer le dossier en revue serait absurde. Les deux mecanismes coexistent : l'un bloque la
 * revue initiale, l'autre accompagne le parcours.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentDocumentRequestService {

    private static final EnumSet<DocumentRequestStatus> OUVERTES =
            EnumSet.of(DocumentRequestStatus.PENDING, DocumentRequestStatus.SUBMITTED);

    private final EnrollmentDocumentRequestRepository requestRepository;
    private final EnrollmentDocumentRepository documentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final StorageService storageService;

    // ------------------------------------------------------------------ Administration ----

    /** L'administration reclame une piece au candidat. */
    @Transactional
    public RequestView requestDocument(UUID enrollmentId, UUID adminId, CreateRequest dto) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        String label = dto.getLabel().trim();

        // L'index unique partiel de la V37 fait autorite — lui seul tient si deux requetes
        // arrivent en meme temps. Ce controle n'existe que pour repondre par un message
        // comprehensible plutot que par une violation de contrainte.
        if (requestRepository.existsByEnrollmentIdAndLabelIgnoreCaseAndStatusIn(
                enrollmentId, label, OUVERTES)) {
            throw new IllegalStateException(
                    "« " + label + " » est déjà réclamé sur ce dossier et n'a pas encore été réglé.");
        }

        EnrollmentDocumentRequest demande = EnrollmentDocumentRequest.builder()
                .enrollment(enrollment)
                .documentType(dto.getDocumentType())
                .label(label)
                .instructions(trimOrNull(dto.getInstructions()))
                .dueDate(dto.getDueDate())
                .status(DocumentRequestStatus.PENDING)
                .requestedBy(adminId)
                .build();

        log.info("Pièce « {} » réclamée sur le dossier {} par l'admin {}", label, enrollmentId, adminId);
        return toView(requestRepository.save(demande));
    }

    /** L'administration retire une demande : la piece n'est plus attendue. */
    @Transactional
    public RequestView cancelRequest(UUID requestId, UUID adminId) {
        EnrollmentDocumentRequest demande = requireRequest(requestId);
        if (demande.getStatus() == DocumentRequestStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "Cette pièce a déjà été acceptée : elle fait partie du dossier et ne peut plus être retirée.");
        }
        demande.setStatus(DocumentRequestStatus.CANCELLED);
        demande.setReviewedBy(adminId);
        demande.setReviewedAt(OffsetDateTime.now());
        log.info("Demande {} annulée par l'admin {}", requestId, adminId);
        return toView(requestRepository.save(demande));
    }

    /** L'administration accepte ou refuse la piece deposee. */
    @Transactional
    public RequestView reviewRequest(UUID requestId, UUID adminId, ReviewRequest dto) {
        EnrollmentDocumentRequest demande = requireRequest(requestId);

        if (demande.getStatus() != DocumentRequestStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Seule une pièce déposée peut être vérifiée. État actuel : " + demande.getStatus() + ".");
        }

        demande.setReviewedBy(adminId);
        demande.setReviewedAt(OffsetDateTime.now());

        if (Boolean.TRUE.equals(dto.getAccepted())) {
            demande.setStatus(DocumentRequestStatus.ACCEPTED);
            demande.setRejectionReason(null);
            // `is_verified` porte la meme information au niveau du fichier : c'est ce drapeau
            // que lisent les ecrans qui listent les pieces sans passer par les demandes.
            EnrollmentDocument piece = demande.getDocument();
            if (piece != null) {
                piece.setIsVerified(true);
                documentRepository.save(piece);
            }
        } else {
            // Un refus sans motif laisse le medecin devant un blocage qu'on ne lui explique
            // pas : il redeposera la meme piece, et le dossier tournera en rond.
            String motif = trimOrNull(dto.getRejectionReason());
            if (motif == null) {
                throw new IllegalArgumentException("Un motif est obligatoire pour refuser une pièce.");
            }
            demande.setStatus(DocumentRequestStatus.REJECTED);
            demande.setRejectionReason(motif);
        }

        log.info("Demande {} {} par l'admin {}", requestId, demande.getStatus(), adminId);
        return toView(requestRepository.save(demande));
    }

    // ------------------------------------------------------------------------- Medecin ----

    /**
     * Le medecin depose la piece reclamee.
     *
     * <p>Le televersement passe par cette methode plutot que par l'endpoint generique de
     * documents : c'est ici que la piece est <i>rattachee a la demande</i>. Deposee ailleurs,
     * elle arriverait bien dans le dossier mais la demande resterait ouverte, et l'un comme
     * l'autre continueraient de croire qu'il manque quelque chose.</p>
     */
    @Transactional
    public RequestView fulfilRequest(UUID requestId, UUID doctorId, MultipartFile file) {
        EnrollmentDocumentRequest demande = requireRequest(requestId);

        if (!demande.getEnrollment().getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }
        if (demande.getStatus() == DocumentRequestStatus.CANCELLED
                || demande.getStatus() == DocumentRequestStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "Cette pièce n'est plus attendue (" + demande.getStatus() + ").");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier reçu.");
        }

        String publicId = storageService.uploadFile(file, "docs/enrollments");
        EnrollmentDocument piece = documentRepository.save(EnrollmentDocument.builder()
                .enrollment(demande.getEnrollment())
                .documentType(demande.getDocumentType())
                .cloudinaryPublicId(publicId)
                .isVerified(false)
                .build());

        demande.setDocument(piece);
        demande.setSubmittedAt(OffsetDateTime.now());
        demande.setStatus(DocumentRequestStatus.SUBMITTED);
        // Le motif du refus precedent est efface : le conserver afficherait un reproche
        // portant sur une piece qui n'est plus celle qu'on examine.
        demande.setRejectionReason(null);
        demande.setReviewedAt(null);
        demande.setReviewedBy(null);

        log.info("Pièce déposée sur la demande {} par le médecin {}", requestId, doctorId);
        return toView(requestRepository.save(demande));
    }

    // ------------------------------------------------------------------------ Lectures ----

    @Transactional(readOnly = true)
    public DossierSummary getDossierForAdmin(UUID enrollmentId) {
        requireEnrollment(enrollmentId);
        return summarize(requestRepository.findByEnrollmentId(enrollmentId));
    }

    @Transactional(readOnly = true)
    public DossierSummary getDossierForDoctor(UUID enrollmentId, UUID doctorId) {
        Enrollment enrollment = requireEnrollment(enrollmentId);
        if (!enrollment.getDoctor().getId().equals(doctorId)) {
            throw new AccessDeniedException("Ce dossier ne vous appartient pas.");
        }
        // Les demandes annulees ne sont pas montrees au medecin : elles ne lui demandent rien
        // et ajouteraient du bruit a une liste qui doit se lire comme une liste de courses.
        List<EnrollmentDocumentRequest> visibles = requestRepository.findByEnrollmentId(enrollmentId)
                .stream()
                .filter(r -> r.getStatus() != DocumentRequestStatus.CANCELLED)
                .toList();
        return summarize(visibles);
    }

    // -------------------------------------------------------------------------- Interne ----

    private DossierSummary summarize(List<EnrollmentDocumentRequest> demandes) {
        // Les demandes annulees ne comptent dans aucun total : elles ne sont plus attendues,
        // et les inclure ferait baisser un taux de completude sans raison.
        List<EnrollmentDocumentRequest> actives = demandes.stream()
                .filter(r -> r.getStatus() != DocumentRequestStatus.CANCELLED)
                .toList();

        int accepted = (int) actives.stream()
                .filter(r -> r.getStatus() == DocumentRequestStatus.ACCEPTED).count();
        int awaitingDoctor = (int) actives.stream()
                .filter(r -> r.getStatus() == DocumentRequestStatus.PENDING
                          || r.getStatus() == DocumentRequestStatus.REJECTED).count();
        int awaitingReview = (int) actives.stream()
                .filter(r -> r.getStatus() == DocumentRequestStatus.SUBMITTED).count();

        return DossierSummary.builder()
                .total(actives.size())
                .accepted(accepted)
                .awaitingDoctor(awaitingDoctor)
                .awaitingReview(awaitingReview)
                // Un dossier sans aucune demande n'est pas « complet » : il n'a pas commencé.
                .complete(!actives.isEmpty() && accepted == actives.size())
                .requests(demandes.stream().map(this::toView).toList())
                .build();
    }

    private RequestView toView(EnrollmentDocumentRequest r) {
        return RequestView.builder()
                .id(r.getId())
                .documentType(r.getDocumentType().name())
                .label(r.getLabel())
                .instructions(r.getInstructions())
                .status(r.getStatus().name())
                .dueDate(r.getDueDate())
                .requestedAt(r.getRequestedAt())
                .submittedAt(r.getSubmittedAt())
                .reviewedAt(r.getReviewedAt())
                .rejectionReason(r.getRejectionReason())
                .documentId(r.getDocument() != null ? r.getDocument().getId() : null)
                .open(r.getStatus().estOuverte())
                .build();
    }

    private Enrollment requireEnrollment(UUID id) {
        return enrollmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dossier introuvable : " + id));
    }

    private EnrollmentDocumentRequest requireRequest(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Demande introuvable : " + id));
    }

    private static String trimOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
