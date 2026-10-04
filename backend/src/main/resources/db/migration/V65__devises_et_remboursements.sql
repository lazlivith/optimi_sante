-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 1. Les devises de présentation
--
-- Le prix d'un produit reste UNIQUE et libellé en euros. Vendre en franc CFA ne consiste pas
-- à saisir un second prix — ce serait deux vérités à tenir d'accord, et la seconde finirait
-- par dater — mais à convertir le prix de référence au moment de l'afficher et de l'encaisser.
--
-- Deux colonnes portent toute la règle. `taux` dit combien d'unités de la devise valent un
-- euro. `palier_arrondi` dit à quel multiple le résultat remonte : 327 978,5 XAF annoncés
-- 328 000 FCFA se lisent, 327 978,5 non. L'arrondi se fait toujours VERS LE HAUT — on
-- n'encaisse jamais moins que le prix de référence par un effet d'arrondi.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE taux_change (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid NOT NULL REFERENCES tenants(id) ON DELETE RESTRICT,
    devise         varchar(3) NOT NULL,
    -- 1 EUR = <taux> unités de cette devise. Six décimales : le franc CFA est à 655,957.
    taux           numeric(18,6) NOT NULL,
    -- Multiple auquel le montant converti remonte. 0,01 pour l'euro — donc aucun arrondi
    -- visible ; 1000 pour le franc CFA, où le centime n'existe pas et où un prix se lit.
    palier_arrondi numeric(12,2) NOT NULL DEFAULT 0.01,
    actif          boolean NOT NULL DEFAULT true,
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_taux_change_tenant_devise UNIQUE (tenant_id, devise),
    CONSTRAINT taux_change_devise_check     CHECK (devise ~ '^[A-Z]{3}$'),
    CONSTRAINT taux_change_taux_check       CHECK (taux > 0),
    CONSTRAINT taux_change_palier_check     CHECK (palier_arrondi > 0)
);

-- L'euro, devise de référence : taux de 1, et pas d'arrondi. Sa ligne existe pour que la
-- conversion n'ait pas de cas particulier à traiter, et le service refuse de la modifier.
INSERT INTO taux_change (tenant_id, devise, taux, palier_arrondi, actif)
SELECT id, 'EUR', 1, 0.01, true FROM tenants;

-- Le franc CFA d'Afrique centrale, à parité FIXE avec l'euro — 1 EUR = 655,957 XAF, garantie
-- par le Trésor français. Il n'y a donc aucun risque de change à couvrir sur cette devise :
-- le taux est une constante, pas une cotation. Inactif au départ : il s'ouvre le jour où le
-- paiement par mobile money est en service.
INSERT INTO taux_change (tenant_id, devise, taux, palier_arrondi, actif)
SELECT id, 'XAF', 655.957, 1000, false FROM tenants;

-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 2. Le montant remboursé
--
-- Un remboursement partiel n'avait nulle part où s'inscrire : la commande restait payée pour
-- la totalité, et le chiffre d'affaires annonçait un revenu dont une part avait été rendue.
-- Le montant rendu se cumule ici, et les agrégats financiers le déduisent.
--
-- Il n'y a pas de statut « partiellement remboursé » : le statut dit si la commande est
-- honorée, le montant dit ce qui a été rendu. Les mêler obligerait à choisir entre les deux.
-- ─────────────────────────────────────────────────────────────────────────────────────────

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS refunded_amount numeric(10,2) NOT NULL DEFAULT 0;

ALTER TABLE orders
    ADD CONSTRAINT orders_refunded_amount_check
        CHECK (refunded_amount >= 0 AND refunded_amount <= total_amount);

-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 3. Un paiement Stripe, une commande
--
-- L'index posé en V64 n'imposait rien : deux commandes auraient pu porter le même identifiant
-- de paiement, et la recherche du webhook de remboursement aurait alors échoué en erreur
-- serveur, que Stripe aurait rejouée sans fin. Le tunnel crée une session par commande, donc
-- la règle est déjà vraie dans les faits : il reste à la faire tenir par la base.
-- ─────────────────────────────────────────────────────────────────────────────────────────

DROP INDEX IF EXISTS idx_orders_stripe_payment_intent;

CREATE UNIQUE INDEX uq_orders_stripe_payment_intent
    ON orders (stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;

-- Meme regle pour les encaissements de formation, qui se remboursent par le meme chemin :
-- acompte, solde, options de service. La ligne est deja vraie dans les donnees (treize
-- identifiants, treize distincts) ; l'index la rend tenue.
CREATE UNIQUE INDEX uq_enrollment_payments_stripe_payment_intent
    ON enrollment_payments (stripe_payment_intent_id)
    WHERE stripe_payment_intent_id IS NOT NULL;
