package com.optimisante.backend.domain.exercerenfrance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Une version publiée de la page : un instantané figé de la réponse publique.
 *
 * <p><b>Pourquoi un document plutôt qu'un versionnement table par table.</b> Versionner
 * chaque ligne des cinq tables de contenu obligerait à estampiller tout, et ferait de chaque
 * lecture publique une reconstitution à date — lourde à écrire, lourde à lire, et fausse au
 * premier oubli. Ici, les tables de contenu SONT le brouillon ; cette table seule est le
 * publié, et la page publique ne lit qu'une ligne.</p>
 *
 * <p><b>Restaurer n'efface rien.</b> Reprendre une ancienne version crée une version NEUVE
 * qui en recopie le contenu, avec {@code restaureDe} pour le dire. L'historique s'allonge,
 * il ne se réécrit jamais — sans quoi « qui a remis en ligne quoi, et quand » deviendrait
 * indémontrable.</p>
 */
@Entity
@Table(name = "evc_publications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvcPublication {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private Integer version;

    /** La réponse publique entière, telle qu'elle sera servie, sérialisée une fois pour toutes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String instantane;

    @Column(name = "publie_le", nullable = false)
    @Builder.Default
    private OffsetDateTime publieLe = OffsetDateTime.now();

    @Column(name = "publie_par")
    private UUID publiePar;

    /** Numéro de la version reprise, quand celle-ci est une restauration. */
    @Column(name = "restaure_de")
    private Integer restaureDe;
}
