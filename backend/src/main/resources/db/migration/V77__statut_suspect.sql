-- ═════════════════════════════════════════════════════════════════════════════════════════
-- Le statut « suspect » : ce qu'un filtre anti-robot écarte, sans le jeter
--
-- CE QUE FAISAIT LE CODE AVANT. Un dépôt piégé était REFUSÉ : rien n'était enregistré, et la
-- réponse HTTP valait 202 là qu'un dépôt accepté rendait 201. Deux défauts dans le même geste.
--
-- Le premier est un aveu. Le commentaire du code promettait qu'« un dépôt écarté répond comme
-- un dépôt accepté », parce que dire « champ piège rempli » enseigne à l'auteur comment passer
-- la fois suivante. Mais un robot lit le code HTTP avant de lire la phrase : l'écart 201/202
-- lui apprenait précisément ce qu'on voulait lui cacher.
--
-- Le second est une perte. Le filtre de délai minimum se déclenche sur un formulaire envoyé
-- trop vite — ce qu'un praticien pressé, ou un gestionnaire de mots de passe, fait très bien.
-- Celui-là voyait « Votre demande n'a pas pu être enregistrée », et personne, de notre côté,
-- n'en savait rien. Un client perdu sans trace.
--
-- CE QUE PERMET CE STATUT. La demande est conservée, visible et filtrable en administration,
-- et n'envoie AUCUN e-mail — ni accusé au déposant, dont l'adresse peut être inventée et
-- appartenir à un tiers, ni alerte à l'équipe, qu'il serait absurde de réveiller pour un
-- robot. Quelqu'un relit, et tranche.
-- ═════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE accompaniment_requests
    DROP CONSTRAINT IF EXISTS accompaniment_requests_statut_check;

ALTER TABLE accompaniment_requests
    ADD CONSTRAINT accompaniment_requests_statut_check
    CHECK (statut IN ('nouveau', 'qualifie', 'devis_envoye', 'paye', 'en_cours', 'termine',
                      'perdu', 'suspect'));

COMMENT ON COLUMN accompaniment_requests.statut IS
    'Avancement commercial de la demande. « suspect » désigne un dépôt écarté par un filtre '
    'anti-robot : conservé et visible, mais sans aucun e-mail envoyé.';
