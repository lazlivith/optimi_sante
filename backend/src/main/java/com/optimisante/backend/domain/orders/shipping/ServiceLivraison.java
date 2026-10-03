package com.optimisante.backend.domain.orders.shipping;

import com.optimisante.backend.config.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les frais de port d'une commande, d'après son pays de destination.
 *
 * <p><b>Une destination non desservie est refusée, pas facturée au hasard.</b> Le service
 * renvoie une erreur explicite plutôt qu'un forfait emprunté à une zone voisine : encaisser
 * un transport qu'on ne sait pas assurer est pire que de dire qu'on ne le sait pas.</p>
 *
 * <p><b>Les droits et taxes à l'arrivée ne sont pas inclus.</b> Les expéditions hors de France
 * sont livrées DAP : le destinataire acquitte les droits de douane et la TVA locale auprès des
 * autorités de son pays. Cela doit figurer sur le document, sans quoi le client découvre une
 * facture douanière qu'il n'attendait pas.</p>
 */
@Service
@RequiredArgsConstructor
public class ServiceLivraison {

    private final ShippingRateRepository shippingRateRepository;

    /** Ce que la livraison coûte, et sous quelle zone elle a été calculée. */
    public record FraisLivraison(ZoneLivraison zone, BigDecimal montant, boolean offerte) {}

    @Transactional(readOnly = true)
    public List<ShippingRate> grille() {
        return shippingRateRepository.findByTenantIdOrderByZoneAsc(tenantRequis());
    }

    /**
     * Les frais pour ce pays, à ce montant de commande.
     *
     * @param codePays       code ISO 3166-1 alpha-2 de la destination
     * @param montantArticles total des articles, qui décide d'une éventuelle gratuité
     * @throws IllegalArgumentException si la destination n'est pas desservie, ou si sa zone
     *                                  n'a pas de tarif actif — dans les deux cas le message
     *                                  s'adresse au client et lui indique quoi faire
     */
    @Transactional(readOnly = true)
    public FraisLivraison calculer(String codePays, BigDecimal montantArticles) {
        ZoneLivraison zone = ZoneLivraison.pour(codePays).orElseThrow(() ->
                new IllegalArgumentException(
                        "Nous ne livrons pas encore cette destination. Écrivez-nous pour "
                                + "obtenir un devis de transport : nous étudions chaque demande."));

        ShippingRate tarif = shippingRateRepository
                .findByTenantIdAndZone(tenantRequis(), zone)
                .filter(ShippingRate::getIsActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "La livraison vers « " + zone.libelle() + " » n'est pas disponible "
                                + "actuellement. Écrivez-nous pour un devis de transport."));

        BigDecimal base = montantArticles == null ? BigDecimal.ZERO : montantArticles;
        boolean offerte = tarif.getFreeFrom() != null && base.compareTo(tarif.getFreeFrom()) >= 0;
        BigDecimal montant = offerte ? BigDecimal.ZERO : tarif.getAmount();

        return new FraisLivraison(zone, montant.setScale(2, java.math.RoundingMode.HALF_UP), offerte);
    }

    /** Les frais quand une destination est fournie ; rien du tout sinon. */
    @Transactional(readOnly = true)
    public Optional<FraisLivraison> calculerSiRenseigne(String codePays, BigDecimal montantArticles) {
        if (codePays == null || codePays.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(calculer(codePays, montantArticles));
    }

    @Transactional
    public ShippingRate enregistrer(ZoneLivraison zone, BigDecimal montant, BigDecimal offerteDes,
                                    boolean active) {
        if (montant == null || montant.signum() < 0) {
            throw new IllegalArgumentException("Le tarif de livraison ne peut pas être négatif.");
        }
        if (offerteDes != null && offerteDes.signum() < 0) {
            throw new IllegalArgumentException("Le seuil de gratuité ne peut pas être négatif.");
        }
        ShippingRate tarif = shippingRateRepository.findByTenantIdAndZone(tenantRequis(), zone)
                .orElseThrow(() -> new IllegalArgumentException("Zone inconnue : " + zone));
        tarif.setAmount(montant);
        tarif.setFreeFrom(offerteDes);
        tarif.setIsActive(active);
        return shippingRateRepository.save(tarif);
    }

    private UUID tenantRequis() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant context is required");
        }
        return tenantId;
    }
}
