-- Coordonnées bancaires des établissements partenaires.
--
-- Le reversement se fait désormais dossier par dossier, et la fenêtre de confirmation affiche
-- le compte qui sera crédité : jusqu'ici, aucun IBAN n'était enregistré nulle part, et le
-- virement se préparait en recopiant à la main un RIB reçu par courriel.
--
-- Colonnes facultatives : un partenaire existant n'a pas encore fourni son RIB, et l'écran de
-- reversement le signale au lieu de bloquer tout le reste. Le format est vérifié par
-- l'application (clé de contrôle ISO 13616 pour l'IBAN) ; la base ne garde que la forme
-- normalisée, sans espaces.

ALTER TABLE partner_profiles
    ADD COLUMN iban                VARCHAR(34),
    ADD COLUMN bic                 VARCHAR(11),
    ADD COLUMN bank_account_holder VARCHAR(140);
