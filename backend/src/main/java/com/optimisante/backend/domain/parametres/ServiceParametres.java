package com.optimisante.backend.domain.parametres;

import com.optimisante.backend.config.tenant.TenantContext;
import com.optimisante.backend.domain.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Les réglages que l'administration tient elle-même.
 *
 * <p><b>Pourquoi ils ont quitté les variables d'environnement.</b> {@code TVA_ACTIVE} y était :
 * le lever imposait d'ouvrir le tableau de bord de l'hébergeur, de modifier un réglage et
 * d'attendre un redémarrage. Pour un interrupteur que le gestionnaire du catalogue doit
 * pouvoir actionner le jour où son comptable répond, c'est à la fois trop lourd et au mauvais
 * endroit : la personne qui décide n'est pas celle qui a les accès.</p>
 *
 * <p><b>Pas de cache.</b> Un réglage se lit à chaque émission de document, soit quelques
 * centaines de fois par jour au plus — une requête sur clé primaire. Un cache y gagnerait une
 * microseconde et y perdrait la seule propriété qui compte : qu'un interrupteur levé à
 * l'écran prenne effet immédiatement, et non au prochain vidage.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceParametres {

    /** La ventilation de la TVA s'imprime-t-elle sur les documents ? */
    public static final String TVA_ACTIVE = "tva.active";

    private final ParametrePlateformeRepository repository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public boolean estActif(String cle, boolean parDefaut) {
        return repository.findByTenantIdAndCle(tenantRequis(), cle)
                .map(p -> Boolean.parseBoolean(p.getValeur()))
                .orElse(parDefaut);
    }

    /**
     * Change un réglage, et en laisse la trace.
     *
     * <p>La trace part au journal d'audit, qui capture déjà l'auteur, son rôle et l'horodatage.
     * Tenir un second historique propre aux réglages reviendrait à maintenir deux vérités sur
     * le même fait, et la seconde finirait par diverger.</p>
     */
    @Transactional
    public boolean definir(String cle, boolean valeur) {
        UUID tenantId = tenantRequis();
        ParametrePlateforme parametre = repository.findByTenantIdAndCle(tenantId, cle)
                .orElseGet(() -> new ParametrePlateforme(
                        tenantId, cle, String.valueOf(valeur), OffsetDateTime.now()));

        String avant = parametre.getValeur();
        parametre.setValeur(String.valueOf(valeur));
        parametre.setModifieLe(OffsetDateTime.now());
        repository.save(parametre);

        auditService.record("PARAMETRE_MODIFIE", "PARAMETRE", cle,
                "Réglage « " + cle + " » : " + avant + " → " + valeur, null);
        log.info("Réglage {} : {} → {}", cle, avant, valeur);
        return valeur;
    }

    private UUID tenantRequis() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }
}
