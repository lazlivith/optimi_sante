-- ═════════════════════════════════════════════════════════════════════════════════════════
-- AUDIT — adresses enregistrées qui ne sont ni http ni https
--
-- LECTURE SEULE. Cette requête ne modifie rien, et ne doit pas être transformée en UPDATE
-- sans décision : une adresse fautive peut aussi être une simple faute de frappe, et
-- l'effacer ferait disparaître l'information plutôt que le problème.
--
-- Pourquoi : ces deux colonnes sont rendues dans un « <a href> ». Une adresse
-- « javascript:… » s'y exécute au clic, dans la session de celui qui clique et depuis notre
-- domaine. Le correctif refuse désormais ces adresses à la saisie, et l'affichage masque le
-- lien quand l'adresse n'est pas une adresse web — mais il reste à savoir si la base en
-- contient déjà.
--
-- La comparaison reproduit celle du code : la chaîne est COMPACTÉE avant examen, car un
-- navigateur ignore espaces, tabulations et caractères de contrôle lorsqu'il analyse une
-- adresse — « java[retour à la ligne]script: » s'exécute.
--
-- Usage :  psql -U <utilisateur> -d <base> -f audit_adresses_javascript.sql
-- ═════════════════════════════════════════════════════════════════════════════════════════

\echo ''
\echo '── Vidéos de produit (rendues sur la fiche PUBLIQUE) ─────────────────────────────'

SELECT p.id,
       p.sku,
       left(p.name, 40)      AS produit,
       p.video_provider      AS hebergeur,
       left(p.video_url, 90) AS adresse
FROM products p
WHERE p.video_url IS NOT NULL
  AND p.video_url <> ''
  AND lower(regexp_replace(p.video_url, '[[:space:][:cntrl:]]', '', 'g'))
      !~ '^https?://'
ORDER BY p.created_at DESC;

\echo ''
\echo '── Liens de réunion des entretiens (saisis aussi par des partenaires) ────────────'

SELECT i.id,
       i.status               AS statut,
       i.proposed_at          AS propose_le,
       left(i.meeting_link, 90) AS adresse
FROM interview_schedules i
WHERE i.meeting_link IS NOT NULL
  AND i.meeting_link <> ''
  AND lower(regexp_replace(i.meeting_link, '[[:space:][:cntrl:]]', '', 'g'))
      !~ '^https?://'
ORDER BY i.proposed_at DESC;

\echo ''
\echo '── Récapitulatif ────────────────────────────────────────────────────────────────'

SELECT 'products.video_url' AS colonne,
       count(*) FILTER (WHERE video_url IS NOT NULL AND video_url <> '') AS renseignees,
       count(*) FILTER (WHERE video_url IS NOT NULL AND video_url <> ''
                          AND lower(regexp_replace(video_url, '[[:space:][:cntrl:]]', '', 'g'))
                              !~ '^https?://')                            AS suspectes
FROM products
UNION ALL
SELECT 'interview_schedules.meeting_link',
       count(*) FILTER (WHERE meeting_link IS NOT NULL AND meeting_link <> ''),
       count(*) FILTER (WHERE meeting_link IS NOT NULL AND meeting_link <> ''
                          AND lower(regexp_replace(meeting_link, '[[:space:][:cntrl:]]', '', 'g'))
                              !~ '^https?://')
FROM interview_schedules;
