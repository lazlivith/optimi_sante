package com.optimisante.backend.domain.exercerenfrance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une ligne du fil d'une demande : création, changement de statut, assignation, note.
 *
 * <p><b>Pourquoi cette table et non le journal d'audit.</b> Le fil porte des données
 * personnelles — le texte d'une note parle du candidat — et doit donc s'anonymiser avec la
 * demande. Le journal d'audit, lui, est une preuve : il ne se modifie pas. Les mêler
 * obligerait soit à réécrire l'audit, soit à garder des données nominatives au-delà de leur
 * durée de conservation.</p>
 *
 * <p>La règle qui en découle : <b>le fil porte le contenu, l'audit ne porte que l'acte</b> —
 * qui, quoi, quand, sur quelle demande. Jamais de nom, d'adresse ni de note.</p>
 */
@Entity
@Table(name = "accompaniment_request_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccompanimentRequestEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "ancien_statut", length = 20)
    private String ancienStatut;

    @Column(name = "nouveau_statut", length = 20)
    private String nouveauStatut;

    /** Vidé par l'anonymisation : c'est ici que se trouve ce qui parle du candidat. */
    @Column(columnDefinition = "TEXT")
    private String contenu;

    /**
     * Nul pour l'événement de création : la demande naît d'un visiteur non connecté.
     * Conservé par l'anonymisation — l'auteur est un administrateur, pas le candidat.
     */
    @Column(name = "auteur_id")
    private UUID auteurId;

    @Column(name = "cree_le", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime creeLe = OffsetDateTime.now();
}
