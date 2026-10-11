package com.optimisante.backend.domain.exercerenfrance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Les cinq tables du contenu éditorial « Exercer en France ».
 *
 * <p>Réunies dans un fichier parce qu'elles n'ont de sens qu'ensemble : ce sont les morceaux
 * d'UNE page, et aucune n'est manipulée seule ailleurs dans l'application.</p>
 *
 * <p><b>Aucune ne porte de tenant.</b> Elles décrivent une procédure administrative
 * française, identique pour qui la lit. Les données d'exploitation — offres et demandes —
 * sont ailleurs, et portent un tenant.</p>
 */
public final class EvcContenu {

    private EvcContenu() {
    }

    /** Un bloc de texte de la page, désigné par une clé que le gabarit cite. */
    // Nom explicite : une entite IMBRIQUEE s'appelle sinon « EvcContenu$Section » dans les
    // requetes JPQL — illisible, et source d'une erreur qui ne se voit qu'au demarrage.
    @Entity(name = "EvcSection")
    @Table(name = "evc_sections")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Section {
        @Id @GeneratedValue @UuidGenerator
        private UUID id;

        @Column(nullable = false, unique = true, length = 60)
        private String cle;

        @Column(name = "sur_titre", length = 120)
        private String surTitre;

        @Column(nullable = false)
        private String titre;

        @Column(columnDefinition = "TEXT")
        private String contenu;

        @Column(nullable = false)
        @Builder.Default
        private Integer ordre = 0;

        @Column(nullable = false)
        @Builder.Default
        private Boolean visible = true;

        @Column(name = "modifie_le", nullable = false)
        @Builder.Default
        private OffsetDateTime modifieLe = OffsetDateTime.now();
    }

    /**
     * Une période officielle de dépôt.
     *
     * <p>Pas de champ « statut » : il se calcule à la lecture
     * ({@code StatutPeriode}). Les dates sont nullables — une période annoncée sans
     * calendrier s'affiche « À venir ».</p>
     */
    // Nom explicite : une entite IMBRIQUEE s'appelle sinon « EvcContenu$Periode » dans les
    // requetes JPQL — illisible, et source d'une erreur qui ne se voit qu'au demarrage.
    @Entity(name = "EvcPeriode")
    @Table(name = "evc_periods")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Periode {
        @Id @GeneratedValue @UuidGenerator
        private UUID id;

        @Column(nullable = false, length = 120)
        private String libelle;

        @Column(name = "date_debut")
        private LocalDate dateDebut;

        @Column(name = "date_fin")
        private LocalDate dateFin;

        @Column(name = "specialites_non_ouvertes", columnDefinition = "TEXT")
        private String specialitesNonOuvertes;

        /** Obligatoire : une date non adossée à une publication officielle n'engage rien. */
        @Column(name = "source_url", nullable = false, length = 1000)
        private String sourceUrl;

        @Column(nullable = false)
        @Builder.Default
        private Boolean visible = true;

        @Column(nullable = false)
        @Builder.Default
        private Integer ordre = 0;
    }

    /** Un lien vers une source officielle, et la date où on l'a vu fonctionner. */
    // Nom explicite : une entite IMBRIQUEE s'appelle sinon « EvcContenu$Lien » dans les
    // requetes JPQL — illisible, et source d'une erreur qui ne se voit qu'au demarrage.
    @Entity(name = "EvcLien")
    @Table(name = "evc_links")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Lien {
        /** Tant que l'adresse vaut ceci, la page ne montre pas le lien. */
        public static final String A_COMPLETER = "A_COMPLETER";

        @Id @GeneratedValue @UuidGenerator
        private UUID id;

        @Column(nullable = false, unique = true, length = 60)
        private String cle;

        @Column(nullable = false)
        private String libelle;

        @Column(nullable = false, length = 1000)
        @Builder.Default
        private String url = A_COMPLETER;

        @Column(name = "verifie_le")
        private OffsetDateTime verifieLe;

        public boolean estRenseigne() {
            return url != null && !url.isBlank() && !A_COMPLETER.equals(url);
        }
    }

    /** Une profession ou une spécialité relevant de la commission nationale. */
    // Nom explicite : une entite IMBRIQUEE s'appelle sinon « EvcContenu$Specialite » dans les
    // requetes JPQL — illisible, et source d'une erreur qui ne se voit qu'au demarrage.
    @Entity(name = "EvcSpecialite")
    @Table(name = "evc_specialties")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Specialite {
        @Id @GeneratedValue @UuidGenerator
        private UUID id;

        @Column(nullable = false, length = 20)
        private String groupe;

        @Column(nullable = false, length = 160)
        private String libelle;

        @Column(nullable = false)
        @Builder.Default
        private Integer ordre = 0;
    }

    /**
     * Une offre d'accompagnement — donnée d'exploitation, donc rattachée à un tenant.
     *
     * <p>{@code prix} nul signifie « sur devis », et non « gratuit » : c'est pourquoi la
     * colonne est nullable plutôt que par défaut à zéro.</p>
     */
    // Nom explicite : une entite IMBRIQUEE s'appelle sinon « EvcContenu$Offre » dans les
    // requetes JPQL — illisible, et source d'une erreur qui ne se voit qu'au demarrage.
    @Entity(name = "EvcOffre")
    @Table(name = "evc_offers")
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Offre {
        @Id @GeneratedValue @UuidGenerator
        private UUID id;

        @Column(name = "tenant_id", nullable = false)
        private UUID tenantId;

        @Column(nullable = false, length = 60)
        private String cle;

        @Column(name = "sur_titre", length = 120)
        private String surTitre;

        @Column(nullable = false)
        private String titre;

        /** Les puces de l'offre, une par ligne. */
        @Column(columnDefinition = "TEXT")
        private String points;

        @Column(precision = 12, scale = 2)
        private BigDecimal prix;

        @Column(nullable = false, length = 3)
        @Builder.Default
        private String devise = "EUR";

        @Column(name = "libelle_bouton", nullable = false, length = 80)
        private String libelleBouton;

        @Column(nullable = false)
        @Builder.Default
        private Boolean actif = true;

        @Column(nullable = false)
        @Builder.Default
        private Integer ordre = 0;
    }
}
