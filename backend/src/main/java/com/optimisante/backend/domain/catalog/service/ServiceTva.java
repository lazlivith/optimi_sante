package com.optimisante.backend.domain.catalog.service;

import com.optimisante.backend.domain.catalog.entity.Category;
import com.optimisante.backend.domain.catalog.entity.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La TVA, extraite de prix annoncés toutes taxes comprises.
 *
 * <p><b>Extraite, et non ajoutée.</b> Les prix du catalogue s'entendent TTC — les conditions
 * générales l'écrivent — et le client paie ce qu'il voit. La ventilation ne change donc aucun
 * montant : elle montre ce que le prix contenait déjà. Un article à 971 € reste à 971 €, dont
 * 161,83 € de taxe. Ajouter 20 % au prix affiché aurait augmenté tout le catalogue.</p>
 *
 * <p><b>Un arrondi par ligne, pas par unité.</b> Arrondir la taxe article par article et
 * sommer fait dériver le total de quelques centimes dès qu'il y a des quantités. Le calcul
 * porte donc sur le total de la ligne — prix unitaire multiplié par la quantité — et
 * n'arrondit qu'une fois.</p>
 *
 * <p><b>Le taux se résout en cascade</b> : celui du produit, à défaut celui de sa catégorie, à
 * défaut le taux normal. Aucun taux n'est inscrit d'office sur les 1500 articles : ce serait
 * une déclaration fiscale faite par un script. Tant que la table des taux par famille n'est
 * pas saisie, tout relève du taux normal — ce qui est déjà le cas aujourd'hui dans les faits.</p>
 */
/*
 * Note d'exploitation : la ventilation ne s'IMPRIME que si `app.tva.active` vaut vrai.
 * Voir ServiceTva#publiable. Tant que la grille des taux n'est pas etablie par le comptable,
 * annoncer « dont TVA 20 % » sur un article qui releve peut-etre du taux reduit serait une
 * mention fausse sur une piece comptable — pire que pas de mention du tout.
 */
@Service
public class ServiceTva {

    /**
     * La ventilation de la taxe s'imprime-t-elle sur les documents ?
     *
     * <p><b>Fermé par défaut, et c'est le point.</b> Le calcul est juste — les prix étant
     * annoncés TTC, la taxe est extraite et le total à payer ne change pas — mais le
     * <i>taux</i> appliqué à chaque article vient d'une grille que seul le comptable peut
     * établir. Tant qu'elle n'existe pas, tout article retombe sur 20 %. Annoncer « dont
     * TVA 20 % » sur un fauteuil roulant qui relève peut-être du taux réduit de 5,5 % serait
     * une mention fausse sur une pièce comptable — plus grave que l'absence de mention.</p>
     *
     * <p>L'interrupteur s'ouvre par {@code TVA_ACTIVE=true}, une fois la grille saisie.</p>
     */
    @org.springframework.beans.factory.annotation.Value("${app.tva.active:false}")
    private boolean publiable;

    /** Vrai lorsque la grille des taux est établie et que les documents peuvent l'annoncer. */
    public boolean estPubliable() {
        return publiable;
    }


    /** Taux normal français. Sert de dernier recours quand rien n'est renseigné. */
    public static final BigDecimal TAUX_NORMAL = new BigDecimal("20.00");

    private static final BigDecimal CENT = new BigDecimal("100");

    /**
     * Le taux applicable à ce produit : le sien, celui de sa catégorie, ou le taux normal.
     *
     * <p>La catégorie n'est consultée que si le produit ne décide pas : un fauteuil roulant
     * relève de 5,5 % quand un fauteuil de pesée relève de 20 %, et ils peuvent se trouver
     * dans le même rayon. Le produit prime donc toujours sur sa famille.</p>
     */
    public BigDecimal tauxDe(Product produit) {
        if (produit == null) {
            return TAUX_NORMAL;
        }
        if (produit.getVatRate() != null) {
            return produit.getVatRate();
        }
        Category categorie = produit.getCategory();
        if (categorie != null && categorie.getVatRate() != null) {
            return categorie.getVatRate();
        }
        return TAUX_NORMAL;
    }

    /**
     * Décompose un montant TTC en base hors taxes et taxe.
     *
     * <p>Un seul arrondi, sur la base : la taxe est ensuite la différence, ce qui garantit que
     * les deux parts redonnent exactement le montant payé. Calculer les deux séparément peut
     * laisser un centime d'écart, et un centime d'écart sur une facture se voit.</p>
     */
    public Decomposition decomposer(BigDecimal montantTtc, BigDecimal taux) {
        BigDecimal ttc = montantTtc == null ? BigDecimal.ZERO : montantTtc;
        BigDecimal t = taux == null ? TAUX_NORMAL : taux;

        if (t.signum() == 0) {
            return new Decomposition(ttc.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(2), t);
        }
        BigDecimal diviseur = BigDecimal.ONE.add(t.divide(CENT, 6, RoundingMode.HALF_UP));
        BigDecimal ht = ttc.divide(diviseur, 2, RoundingMode.HALF_UP);
        return new Decomposition(ht, ttc.setScale(2, RoundingMode.HALF_UP).subtract(ht), t);
    }

    /**
     * La ventilation par taux d'un ensemble de lignes, telle qu'une facture doit la porter.
     *
     * <p>Une commande mêlant un fauteuil roulant et des gants comporte deux bases taxables.
     * Les additionner en une seule ligne de taxe masquerait l'information que la facture est
     * justement tenue de montrer.</p>
     *
     * <p>L'ordre des taux est conservé tel qu'il apparaît dans la commande : une ventilation
     * qui change d'ordre d'une facture à l'autre se relit mal.</p>
     */
    public List<Ventilation> ventiler(List<LigneTaxable> lignes) {
        Map<BigDecimal, BigDecimal> ttcParTaux = new LinkedHashMap<>();
        for (LigneTaxable ligne : lignes) {
            BigDecimal taux = ligne.taux() == null ? TAUX_NORMAL : ligne.taux().setScale(2, RoundingMode.HALF_UP);
            ttcParTaux.merge(taux, ligne.montantTtc() == null ? BigDecimal.ZERO : ligne.montantTtc(),
                    BigDecimal::add);
        }
        List<Ventilation> ventilation = new ArrayList<>();
        for (Map.Entry<BigDecimal, BigDecimal> entree : ttcParTaux.entrySet()) {
            Decomposition d = decomposer(entree.getValue(), entree.getKey());
            ventilation.add(new Ventilation(entree.getKey(), d.ht(), d.taxe(),
                    entree.getValue().setScale(2, RoundingMode.HALF_UP)));
        }
        return ventilation;
    }

    /** Un montant TTC et son taux, tels qu'une ligne de commande les porte. */
    public record LigneTaxable(BigDecimal montantTtc, BigDecimal taux) {}

    /** Les deux parts d'un montant TTC. Leur somme vaut exactement le montant d'origine. */
    public record Decomposition(BigDecimal ht, BigDecimal taxe, BigDecimal taux) {}

    /** Une base taxable d'une facture : tout ce qui relève d'un même taux. */
    public record Ventilation(BigDecimal taux, BigDecimal ht, BigDecimal taxe, BigDecimal ttc) {}
}
