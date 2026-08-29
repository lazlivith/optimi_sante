package com.optimisante.backend.domain.training.entity;

/**
 * Statut de validation d'une formation créée par un partenaire (CHU).
 * Une formation n'est visible sur le catalogue public (voir {@code isPublished})
 * qu'une fois passée à APPROVED par l'administration.
 */
public enum TrainingApprovalStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}
