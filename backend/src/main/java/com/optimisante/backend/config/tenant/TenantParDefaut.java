package com.optimisante.backend.config.tenant;

import com.optimisante.backend.domain.identity.entity.Tenant;
import com.optimisante.backend.domain.identity.repository.TenantRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Le tenant auquel appartient ce qui arrive par une route publique.
 *
 * <p><b>Le problème qu'il résout.</b> Une route ouverte aux visiteurs non connectés n'a pas
 * de session, donc pas de tenant venu d'un jeton. Les deux sources disponibles sont alors
 * l'en-tête {@code X-Tenant-Id} et le corps de la requête — toutes deux fournies par
 * l'appelant. S'y fier revient à laisser celui qui dépose un formulaire décider chez qui il
 * atterrit : il suffit d'un en-tête modifié pour écrire dans les données d'une autre entité.
 * Le formulaire de candidature des médecins le fait aujourd'hui ({@code dto.getTenantCode()}),
 * et c'est une dette identifiée, à corriger pour elle-même.</p>
 *
 * <p><b>Ce que fait cette classe.</b> Elle lit le code du tenant dans la CONFIGURATION du
 * déploiement, jamais dans la requête. Rien de ce qu'envoie le visiteur n'entre dans la
 * décision.</p>
 *
 * <p><b>Pourquoi pas une résolution par domaine.</b> La table {@code tenants} porte bien une
 * colonne {@code domain}, mais l'en-tête {@code Host} est lui aussi fourni par le client.
 * Avec un seul tenant, elle ajouterait une dépendance sans rien verrouiller. Le jour où un
 * second tenant arrive, c'est ici — à un seul endroit — que la règle changera.</p>
 */
@Slf4j
@Component
public class TenantParDefaut {

    private final TenantRepository tenantRepository;
    private final String code;

    public TenantParDefaut(TenantRepository tenantRepository,
                           @Value("${app.tenant.code-par-defaut:}") String code) {
        this.tenantRepository = tenantRepository;
        this.code = code == null ? "" : code.trim();
    }

    /**
     * Vérifie au démarrage que la configuration désigne un tenant qui existe.
     *
     * <p><b>Pourquoi refuser de démarrer plutôt que d'échouer à la première demande.</b> Une
     * propriété absente ou fautive ne se voit pas : l'application se lance, la page publique
     * s'affiche, et seul le premier praticien qui remplit le formulaire déclenche l'erreur —
     * côté visiteur, un soir, sans témoin. Mieux vaut un démarrage refusé, bruyant et
     * immédiat, qu'une demande perdue.</p>
     *
     * <p><b>À la construction du bean, et non sur {@code ApplicationReadyEvent}.</b> Ce
     * dernier se déclenche APRÈS l'ouverture du port : le serveur acceptait des requêtes
     * pendant la fraction de seconde séparant « démarré » de l'échec, et les journaux
     * affichaient « Started BackendApplication » juste avant l'erreur — de quoi faire croire
     * à un démarrage réussi. Ici, le contexte ne se construit pas, et rien n'écoute.</p>
     */
    @PostConstruct
    public void verifierAuDemarrage() {
        if (code.isBlank()) {
            throw new IllegalStateException(
                    "La propriété « app.tenant.code-par-defaut » n'est pas définie. Les routes "
                    + "publiques ne sauraient à quel tenant rattacher une demande. Renseignez-la "
                    + "avec le code d'un tenant existant (par exemple FR_MAIN).");
        }
        tenantRepository.findByCode(code).orElseThrow(() -> new IllegalStateException(
                "La propriété « app.tenant.code-par-defaut » vaut « " + code + " », mais aucun "
                + "tenant ne porte ce code. Vérifiez la configuration du déploiement."));
        log.info("Tenant des routes publiques : {}", code);
    }

    /** @throws IllegalStateException si la configuration ne désigne plus un tenant existant */
    public Tenant resoudre() {
        return tenantRepository.findByCode(code).orElseThrow(() -> new IllegalStateException(
                "Tenant « " + code + " » introuvable."));
    }

    public UUID identifiant() {
        return resoudre().getId();
    }
}
