-- V45 : les frais de formation se reglent en deux echeances
--
-- CE QUI CHANGE
-- Jusqu'ici la formation se payait en une fois, a l'acceptation par l'etablissement. Le
-- parcours reel en compte deux : un acompte a l'admission, puis le solde a la delivrance du
-- visa. Le candidat n'avance plus la totalite d'un sejour qui depend encore d'une decision
-- consulaire.
--
-- POURQUOI UNE COLONNE PLUTOT QU'UN NOUVEAU TYPE DE PAIEMENT
-- Ajouter `TUITION_DEPOSIT` et `TUITION_BALANCE` a `payment_type` aurait oblige a reecrire
-- toutes les lectures existantes — reversements partenaires, etat comptable, relevés PDF — qui
-- filtrent sur `TUITION_FEE`. Or ces deux encaissements SONT des frais de formation : ils se
-- repartissent avec le partenaire de la meme facon et alimentent le meme reversement. Ce qui
-- les distingue n'est pas leur nature mais leur rang dans un echeancier. C'est donc une
-- colonne, et les lectures existantes continuent de fonctionner sans modification.
--
-- CE QUE CETTE MIGRATION NE FAIT PAS
-- Aucun statut de dossier n'est ajoute. L'acompte se regle en `PENDING_TUITION_FEE` et fait
-- passer a `CONFIRMED`, exactement comme le paiement unique d'avant ; le solde se regle en
-- `VISA_GRANTED` et ne fait avancer aucun statut. L'automate est inchange.

ALTER TABLE enrollment_payments
    ADD COLUMN IF NOT EXISTS installment varchar(20);

-- Les paiements de formation deja encaisses l'ont ete a 100 % : ils ne sont ni un acompte ni
-- un solde, et rien ne reste du sur ces dossiers. Les marquer `FULL` dit exactement cela —
-- les reetiqueter en `DEPOSIT` reclamerait a tort un solde de 40 % a des candidats qui ont
-- deja tout paye.
UPDATE enrollment_payments
   SET installment = 'FULL'
 WHERE payment_type = 'TUITION_FEE'
   AND installment IS NULL;

ALTER TABLE enrollment_payments DROP CONSTRAINT IF EXISTS enrollment_payments_installment_check;
ALTER TABLE enrollment_payments ADD CONSTRAINT enrollment_payments_installment_check CHECK (
    -- Un encaissement de formation appartient toujours a un rang de l'echeancier : sans cela,
    -- une ligne sans rang echapperait a l'index d'unicite ci-dessous et un dossier pourrait
    -- etre debite deux fois du meme acompte.
    --
    -- Le `IS NOT NULL` explicite n'est pas une precaution decorative. Ecrit sans lui,
    -- `installment IN ('DEPOSIT', ...)` vaut NULL — et non FALSE — quand la colonne est nulle ;
    -- une contrainte qui vaut NULL est reputee SATISFAITE par PostgreSQL. La ligne sans rang
    -- serait donc passee, precisement le cas que cette contrainte existe pour interdire.
    -- Verifie : sans ce test, l'insertion d'un TUITION_FEE sans rang etait acceptee.
    (payment_type =  'TUITION_FEE' AND installment IS NOT NULL
                                   AND installment IN ('DEPOSIT', 'BALANCE', 'FULL'))
 OR (payment_type <> 'TUITION_FEE' AND installment IS NULL)
);

-- L'unicite passe du dossier au couple (dossier, echeance). L'ancien index interdisait tout
-- second encaissement de formation — c'est lui qui rendait l'echeancier impossible.
DROP INDEX IF EXISTS uq_enrollment_paid_tuition;

CREATE UNIQUE INDEX IF NOT EXISTS uq_enrollment_paid_tuition_installment
    ON enrollment_payments (enrollment_id, installment)
    WHERE payment_type = 'TUITION_FEE' AND status = 'PAID';

CREATE INDEX IF NOT EXISTS idx_enrollment_payments_installment
    ON enrollment_payments (enrollment_id, payment_type, installment);

COMMENT ON COLUMN enrollment_payments.installment IS
    'Rang dans l''echeancier des frais de formation : DEPOSIT (acompte a l''admission), '
    'BALANCE (solde a la delivrance du visa), FULL (reglement unique, anterieur a la V45). '
    'NULL pour les autres types d''encaissement.';
