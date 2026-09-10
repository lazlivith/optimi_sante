-- V44 : une souscription peut nommer son encaissement avant d'etre soldee
--
-- CE QUI CLOCHAIT
-- La V43 exigeait qu'une souscription encore `SELECTED` n'ait AUCUN paiement rattache :
--
--     (status = 'SELECTED' AND paid_at IS NULL AND payment_id IS NULL)
--
-- Cette clause contredisait le parcours de paiement. A l'ouverture du reglement, les services
-- retenus sont rattaches a la ligne d'encaissement **avant** que Stripe ne confirme quoi que ce
-- soit : c'est ce rattachement qui fige le panier. Sans lui, un medecin qui souscrirait un
-- service pendant qu'il paie verrait ce service solde par un encaissement qui ne le couvrait
-- pas — le montant preleve et les prestations rendues divergeraient.
--
-- Le parcours etait donc juste et la contrainte trop stricte : elle interdisait l'etat
-- intermediaire « retenu, paiement ouvert, pas encore confirme », qui dure le temps du
-- formulaire de paiement.
--
-- CE QUE LA CONTRAINTE DOIT DIRE
-- L'invariant qui compte est l'inverse : **une souscription payee nomme toujours l'encaissement
-- qui l'a couverte et la date du reglement.** Sans cela, un controle comptable saurait qu'une
-- prestation a ete reglee sans pouvoir dire par quel paiement. Rattacher une ligne encore en
-- attente ne fait perdre aucune information — cela en ajoute une.

ALTER TABLE enrollment_service_options DROP CONSTRAINT IF EXISTS eso_coherent;

ALTER TABLE enrollment_service_options
    ADD CONSTRAINT eso_coherent CHECK (
        -- Retenu : le paiement peut deja etre ouvert, mais rien n'est encaisse.
        (status = 'SELECTED'  AND paid_at IS NULL)
        -- Paye : l'encaissement et sa date sont nommes, sans exception.
     OR (status = 'PAID'      AND paid_at IS NOT NULL AND payment_id IS NOT NULL)
     OR (status = 'CANCELLED')
    );

COMMENT ON COLUMN enrollment_service_options.payment_id IS
    'Encaissement couvrant ce service. Rattache des l''ouverture du reglement — c''est ce qui '
    'fige le panier — et non a la confirmation du paiement.';
