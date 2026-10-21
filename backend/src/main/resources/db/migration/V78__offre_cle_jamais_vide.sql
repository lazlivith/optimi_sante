-- ═════════════════════════════════════════════════════════════════════════════════════════
-- Une clé d'offre est renseignée, ou elle est NULLE. Jamais vide.
--
-- CE QUI S'EST PASSÉ. Des demandes sont arrivées avec « offre_cle = '' ». Le code qui compose
-- l'accusé de réception écartait le cas par « getOffreCle() == null » — une chaîne vide n'est
-- pas nulle, le garde n'a pas joué, et des praticiens ont reçu un message disant :
--
--     « Nous avons bien reçu votre demande concernant . »
--
-- Deux valeurs pour une seule idée — « pas d'offre » — et c'est la seconde, celle à laquelle
-- personne ne pensait, qui est passée dans un courriel client.
--
-- CE QUE FAIT CETTE MIGRATION. Elle ramène les lignes existantes à NULL, puis elle INTERDIT
-- la chaîne vide. La contrainte est le vrai correctif : le code applicatif normalise déjà,
-- mais un code se contourne par une route oubliée, un import, une correction à la main. Une
-- contrainte, non. Le jour où une chaîne vide repart, l'écriture échouera bruyamment au lieu
-- de ressortir, silencieusement, dans la phrase d'un courriel.
--
-- Le NULL reste autorisé : une demande générale — « Prendre rendez-vous », « Être
-- accompagné » — n'a légitimement pas d'offre.
-- ═════════════════════════════════════════════════════════════════════════════════════════

UPDATE accompaniment_requests
   SET offre_cle = NULL
 WHERE offre_cle IS NOT NULL AND btrim(offre_cle) = '';

ALTER TABLE accompaniment_requests
    DROP CONSTRAINT IF EXISTS accompaniment_requests_offre_cle_non_vide;

ALTER TABLE accompaniment_requests
    ADD CONSTRAINT accompaniment_requests_offre_cle_non_vide
    CHECK (offre_cle IS NULL OR btrim(offre_cle) <> '');

COMMENT ON COLUMN accompaniment_requests.offre_cle IS
    'Clé de l''offre choisie, ou NULL pour une demande générale. Jamais une chaîne vide : '
    'la contrainte l''interdit, parce que le cas « vide » a déjà échappé à un garde écrit '
    'pour le cas « nul ».';
