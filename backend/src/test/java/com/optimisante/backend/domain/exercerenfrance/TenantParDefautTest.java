package com.optimisante.backend.domain.exercerenfrance;

import com.optimisante.backend.config.tenant.TenantParDefaut;
import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Le tenant des routes publiques : il doit être désigné par la configuration, et son absence
 * doit arrêter le serveur.
 *
 * <p><b>Pourquoi refuser de démarrer.</b> Une propriété manquante ne se voit pas : le serveur
 * se lance, la page s'affiche, et c'est le premier praticien qui remplit le formulaire qui
 * déclenche l'erreur — un soir, côté visiteur, sans témoin. Un démarrage refusé est bruyant
 * et immédiat ; une demande perdue ne l'est pas.</p>
 */
class TenantParDefautTest {

    @Test
    @DisplayName("sans propriété, le démarrage est refusé et le message dit quoi faire")
    void proprieteAbsente() {
        TenantRepository depot = mock(TenantRepository.class);

        assertThatThrownBy(() -> new TenantParDefaut(depot, "   ").verifierAuDemarrage())
                .isInstanceOf(IllegalStateException.class)
                // Le message doit nommer la propriété ET donner une valeur plausible :
                // « configuration invalide » n'aide personne à 2 h du matin.
                .hasMessageContaining("app.tenant.code-par-defaut")
                .hasMessageContaining("FR_MAIN");

        // Rien n'est allé chercher en base : l'absence se tranche avant toute requête.
        verifyNoInteractions(depot);
    }

    @Test
    @DisplayName("avec un code inconnu, le démarrage est refusé et le message cite le code reçu")
    void codeInconnu() {
        TenantRepository depot = mock(TenantRepository.class);
        when(depot.findByCode(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new TenantParDefaut(depot, "ZZZ").verifierAuDemarrage())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ZZZ")
                .hasMessageContaining("aucun tenant");
    }

    @Test
    @DisplayName("avec un code connu, le démarrage passe")
    void codeConnu() {
        TenantRepository depot = mock(TenantRepository.class);
        Tenant tenant = new Tenant();
        when(depot.findByCode("FR_MAIN")).thenReturn(Optional.of(tenant));

        TenantParDefaut resolveur = new TenantParDefaut(depot, "FR_MAIN");
        assertThatNoException().isThrownBy(resolveur::verifierAuDemarrage);
        assertThat(resolveur.resoudre()).isSameAs(tenant);
    }
}
