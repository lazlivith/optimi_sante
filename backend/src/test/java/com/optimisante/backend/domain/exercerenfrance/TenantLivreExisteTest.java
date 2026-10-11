package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.ConfigurationBaseDeTest;
import com.optimisante.backend.config.tenant.TenantParDefaut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La configuration LIVREE designe un tenant que les migrations creent.
 *
 * <p><b>Distinct de {@code TenantParDefautTest}, qui eprouve la REGLE.</b> Celui-ci eprouve le
 * DEPLOIEMENT : que la valeur par defaut embarquee dans l'application corresponde bien a ce
 * que contient une base migree comme en production. Une regle juste posee sur une
 * configuration fausse ne protege de rien — et c'est exactement ce qui a failli arriver, la
 * propriete ayant d'abord ete placee dans le profil « dev », ou la production ne l'aurait
 * jamais vue.</p>
 *
 * <p>Le contexte demarre ici comme en production : si la propriete disparaissait, ou si elle
 * designait un code absent, ce test ne demarrerait pas — l'application refuse de se
 * construire, et c'est le comportement voulu.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ConfigurationBaseDeTest.class)
@EnabledIf(value = "com.optimisante.backend.Docker#disponible",
           disabledReason = "Docker est indisponible : ce test a besoin d'un PostgreSQL ephemere.")
class TenantLivreExisteTest {

    @Autowired
    private TenantParDefaut tenantParDefaut;

    @Test
    @DisplayName("la valeur livree designe un tenant que les migrations ont cree")
    void leTenantLivreExiste() {
        assertThat(tenantParDefaut.resoudre().getCode()).isEqualTo("FR_MAIN");
        assertThat(tenantParDefaut.identifiant()).isNotNull();
    }
}
