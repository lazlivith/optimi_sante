-- ═════════════════════════════════════════════════════════════════════════════════════════
-- Une offre qui sert de porte d'entrée, et non de prestation à vendre
--
-- « Être alerté à l'ouverture » ouvre le même formulaire que les trois accompagnements, et
-- la demande doit donc porter une clé d'offre : c'est elle qui permettra, le jour où le CNG
-- publiera ses dates, de retrouver les praticiens à prévenir.
--
-- Mais cette offre ne se vend pas et n'a pas sa place dans « Nos accompagnements ».
--
-- Pourquoi une colonne plutôt que de la laisser inactive : « inactif » signifie « retiré de
-- la vente », et un administrateur qui tomberait dessus l'activerait de bonne foi — faisant
-- apparaître une quatrième carte « Sur devis » sans prestation derrière. La distinction est
-- de nature, pas d'état : elle mérite sa colonne.
-- ═════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE evc_offers
    ADD COLUMN IF NOT EXISTS technique BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN evc_offers.technique IS
    'Vrai pour une offre qui sert uniquement à rattacher une demande (alerte d''ouverture '
    'AEP). Jamais affichée dans « Nos accompagnements », même active.';
