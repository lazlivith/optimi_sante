-- ═════════════════════════════════════════════════════════════════════════════════════════
-- « Exercer en France » : réglages éditoriaux datés, et seuil de rappel
-- ═════════════════════════════════════════════════════════════════════════════════════════


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 1. Les réglages de la page
--
-- Pourquoi une table et non une ligne de texte dans une section : la date de dernière
-- actualisation du calendrier des commissions était destinée à vivre dans le texte, sous la
-- forme « Dernière actualisation connue : 20/07/2026 ». Une date écrite dans une phrase ne
-- se compare à rien : elle vieillit sans que personne ne s'en aperçoive, et c'est justement
-- ce qu'un rappel doit détecter. Rangée ici, elle se compare à aujourd'hui.
--
-- Pas de tenant_id : comme les sections, les périodes et les liens, ces réglages décrivent
-- une procédure administrative française, la même pour qui la lit.
--
-- Pas dans parametres_plateforme : cette table-là porte des réglages d'EXPLOITATION,
-- rattachés à un tenant. Mêler les deux obligerait soit à inventer un tenant pour un contenu
-- global, soit à relâcher la contrainte qui garantit le rattachement des autres réglages.
-- ─────────────────────────────────────────────────────────────────────────────────────────

CREATE TABLE evc_reglages (
    cle        varchar(60) PRIMARY KEY,
    valeur     text,
    modifie_le timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE evc_reglages IS
    'Réglages éditoriaux de la page « Exercer en France », globaux comme son contenu. '
    'Les valeurs de type date sont au format ISO (AAAA-MM-JJ) pour être comparables.';

-- Dernière publication CONSTATÉE du calendrier des commissions.
--
-- Relevée le 11/10/2026 sur la page « Commission d'autorisation d'exercice » du CNG : son
-- lien « Consultez le calendrier prévisionnel » (/documents/calendrier-previsionnel-cae)
-- redirige vers calendrier_cae_03.09.2026.pdf, déposé dans le dossier 2026-09.
--
-- La page elle-même ne porte aucune date — elle dit seulement « mis à jour périodiquement » —
-- et le PDF n'en contient pas davantage : son titre s'arrête à « 2nd SEMESTRE 2026 ». La date
-- du nom de fichier est donc la seule information disponible, et c'est un CONSTAT de notre
-- part, non une publication du CNG. C'est pourquoi la page l'annonce comme telle.
INSERT INTO evc_reglages (cle, valeur) VALUES
    ('calendrier_actualise_le', '2026-09-03')
ON CONFLICT (cle) DO NOTHING;


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 2. Le seuil du rappel « prestation sans date de fin »
--
-- Paramétrable, comme les deux durées de conservation : c'est un réglage d'exploitation, et
-- le bon délai se découvrira à l'usage. Douze mois pour commencer.
-- ─────────────────────────────────────────────────────────────────────────────────────────

INSERT INTO parametres_plateforme (tenant_id, cle, valeur)
SELECT t.id, 'rgpd.accompagnement.mois_alerte_prestation_sans_fin', '12'
FROM tenants t
ON CONFLICT (tenant_id, cle) DO NOTHING;


-- ─────────────────────────────────────────────────────────────────────────────────────────
-- 3. Reprise des demandes déjà réglées
--
-- Le régime de conservation long se reconnaissait au statut « payé ». C'était faux dès qu'une
-- prestation s'achevait : la demande passait à « en cours » puis « terminé », quittait donc
-- ce statut, et retombait dans le régime des prospects — anonymisée vingt-quatre mois après
-- la dernière activité au lieu de cinq ans après la fin de prestation. Une pièce
-- contractuelle aurait disparu bien avant la fin de la prescription commerciale.
--
-- Le marqueur devient la DATE DE FIN DE PRESTATION, qui ne s'efface pas quand le statut
-- avance. Les demandes déjà passées par « payé » sans date reçoivent ici celle de leur
-- dernière activité : faute de mieux, elle est la plus proche de la vérité, et elle place la
-- demande dans le bon régime plutôt que de l'exposer à un effacement prématuré.
-- ─────────────────────────────────────────────────────────────────────────────────────────

UPDATE accompaniment_requests
SET prestation_fin_le = derniere_activite_le
WHERE prestation_fin_le IS NULL
  AND statut IN ('en_cours', 'termine');
