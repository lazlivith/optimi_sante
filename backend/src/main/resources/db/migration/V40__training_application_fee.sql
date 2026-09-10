-- V40 : frais de dossier par formation
--
-- CE QUI EXISTAIT
-- Les frais de dossier etaient une valeur unique pour toute la plateforme
-- (`app.doctor-application.fee-amount`, 50 EUR). Toutes les candidatures, quelle que soit la
-- formation visee, sa duree ou sa ville, reglaient le meme montant.
--
-- CE QUE CETTE COLONNE APPORTE
-- Un tarif propre a chaque formation. Instruire une candidature pour un stage de 90 jours a
-- Bordeaux ne represente pas le meme travail qu'un stage de 10 jours : le montant peut
-- desormais suivre.
--
-- POURQUOI ELLE EST NULLABLE
-- `NULL` signifie « utiliser la valeur globale », pas « gratuit ». Sans cette nuance, il
-- faudrait renseigner chaque formation existante avant de pouvoir deployer, et toute formation
-- creee ensuite sans tarif explicite basculerait a zero — une candidature gratuite par
-- omission. Le repli est traite cote service, ou la valeur globale reste la reference.
--
-- CE QUI N'EST PAS TOUCHE
-- `doctor_applications.fee_amount` fige deja le montant paye au moment de la candidature.
-- Changer le tarif d'une formation ne modifie donc aucune candidature passee, et n'a aucun
-- effet retroactif sur ce qui a ete encaisse.

ALTER TABLE trainings
    ADD COLUMN IF NOT EXISTS application_fee NUMERIC(10, 2);

-- Des frais negatifs n'ont aucun sens ; la gratuite, elle, est un choix commercial legitime,
-- d'ou le zero autorise. La contrainte est nommee pour que sa violation soit lisible.
ALTER TABLE trainings
    DROP CONSTRAINT IF EXISTS trainings_application_fee_positive;
ALTER TABLE trainings
    ADD CONSTRAINT trainings_application_fee_positive
    CHECK (application_fee IS NULL OR application_fee >= 0);

COMMENT ON COLUMN trainings.application_fee IS
    'Frais de dossier propres a cette formation, en euros. NULL = appliquer la valeur globale '
    'app.doctor-application.fee-amount. Renseignee par l''administration de la mobilite : '
    'c''est une recette OptimiSante, jamais un tarif fixe par l''etablissement partenaire.';
