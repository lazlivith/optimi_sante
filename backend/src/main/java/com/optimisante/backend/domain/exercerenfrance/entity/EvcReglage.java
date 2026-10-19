package com.optimisante.backend.domain.exercerenfrance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Un réglage éditorial de la page, désigné par une clé.
 *
 * <p><b>Pourquoi la date du calendrier des commissions vit ici et non dans un texte.</b>
 * Elle devait s'écrire « Dernière actualisation connue : 20/07/2026 », au fil d'une phrase.
 * Une date dans une phrase ne se compare à rien : elle vieillit sans que personne ne s'en
 * aperçoive, et la page finit par annoncer en 2027 une actualisation de juillet 2026. Rangée
 * dans une colonne, elle se compare à aujourd'hui — c'est ce qui permet au tableau de bord
 * de la signaler.</p>
 */
@Entity
@Table(name = "evc_reglages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvcReglage {

    /** Date de la dernière publication connue du calendrier des commissions, par le CNG. */
    public static final String CALENDRIER_ACTUALISE_LE = "calendrier_actualise_le";

    /**
     * Date du premier passage réussi du contenu initial.
     *
     * <p>Tant qu'elle est absente, le seed pose ce qui manque ; une fois posée, il ne fait
     * plus rien. C'est ce qui rend une suppression DÉFINITIVE : sans ce marqueur, un élément
     * retiré par l'administration revenait au déploiement suivant, et la seule façon de s'en
     * débarrasser aurait été de le supprimer après chaque mise en ligne.</p>
     */
    public static final String SEED_APPLIQUE_LE = "seed_applique_le";

    @Id
    @Column(length = 60)
    private String cle;

    @Column(columnDefinition = "TEXT")
    private String valeur;

    @Column(name = "modifie_le", nullable = false)
    @Builder.Default
    private OffsetDateTime modifieLe = OffsetDateTime.now();

    /**
     * La valeur lue comme une date.
     *
     * @return la date, ou {@code null} si la valeur est absente ou illisible — un réglage
     *         fautif ne doit pas empêcher la page de s'afficher
     */
    public LocalDate enDate() {
        try {
            return valeur == null || valeur.isBlank() ? null : LocalDate.parse(valeur.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
