package com.optimisante.backend.domain.parametres;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un reglage d'exploitation, modifiable depuis l'administration sans redeploiement.
 *
 * <p>La frontiere avec les variables d'environnement est nette : l'environnement porte ce qui
 * releve du DEPLOIEMENT — secrets, adresses, cles — que personne ne change en exploitation ;
 * la base porte ce qui releve de l'EXPLOITATION, qu'on change sans redeployer et dont on veut
 * la trace.</p>
 */
@Entity
@Table(name = "parametres_plateforme")
@IdClass(ParametrePlateforme.Cle.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ParametrePlateforme {

    @Id
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Id
    @Column(nullable = false, length = 80)
    private String cle;

    @Column(nullable = false, columnDefinition = "text")
    private String valeur;

    @Column(name = "modifie_le", nullable = false)
    private OffsetDateTime modifieLe = OffsetDateTime.now();

    /** Cle composite : un reglage appartient a un tenant. */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Cle implements Serializable {
        private UUID tenantId;
        private String cle;

        @Override
        public boolean equals(Object autre) {
            if (this == autre) return true;
            if (!(autre instanceof Cle c)) return false;
            return java.util.Objects.equals(tenantId, c.tenantId)
                    && java.util.Objects.equals(cle, c.cle);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(tenantId, cle);
        }
    }
}
