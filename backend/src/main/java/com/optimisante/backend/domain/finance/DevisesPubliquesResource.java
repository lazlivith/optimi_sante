package com.optimisante.backend.domain.finance;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Les devises qu'un visiteur peut choisir, et de quoi convertir les prix à l'écran.
 *
 * <p><b>Pourquoi le taux sort d'ici au lieu que le serveur renvoie des prix convertis.</b>
 * Convertir côté serveur aurait voulu dire ajouter une devise à chaque réponse du catalogue, du
 * panier, de la fiche produit — et toucher au contrat de tout ce qui fonctionne déjà. Le taux et
 * le palier ne sont pas des secrets : le navigateur applique la même règle, et le prix affiché
 * change instantanément quand le visiteur change de devise, sans rappeler le serveur.</p>
 *
 * <p><b>Ce qui fait foi reste le serveur.</b> Le montant réellement encaissé est recalculé au
 * moment de payer, à partir du prix de référence. L'affichage est une commodité ; il ne décide
 * de rien.</p>
 */
@RestController
@RequiredArgsConstructor
public class DevisesPubliquesResource {

    private final ServiceDevises serviceDevises;

    /**
     * Les devises ouvertes à la vente, avec leur règle de conversion.
     *
     * @param pays code ISO 3166-1 alpha-2, facultatif : si la plateforme sert la devise de ce
     *             pays, elle est marquée comme suggérée
     */
    @GetMapping("/api/v1/devises")
    public ResponseEntity<List<DeviseAffichageDto>> actives(
            @RequestParam(required = false) String pays) {

        String suggeree = PaysDevise.suggestion(pays)
                .map(Devise::code)
                .orElse(Devise.REFERENCE.code());

        return ResponseEntity.ok(serviceDevises.grille().stream()
                .filter(t -> Boolean.TRUE.equals(t.getActif()))
                .map(t -> new DeviseAffichageDto(
                        t.getDevise(),
                        t.getTaux(),
                        t.getPalierArrondi(),
                        Devise.de(t.getDevise()).decimales(),
                        t.getDevise().equals(suggeree)))
                .toList());
    }

    /**
     * @param taux          combien d'unités de cette devise valent un euro
     * @param palierArrondi multiple auquel le montant converti remonte
     * @param suggeree      vrai pour la devise correspondant au pays interrogé
     */
    public record DeviseAffichageDto(String code, BigDecimal taux, BigDecimal palierArrondi,
                                     int decimales, boolean suggeree) {}
}
