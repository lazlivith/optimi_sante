-- ═════════════════════════════════════════════════════════════════════════════════════════
-- Ce que l'accusé de réception promet, et à qui le praticien peut répondre
--
-- Deux valeurs écrites dans le texte du message, et qui n'y avaient pas leur place.
--
-- LE DÉLAI. « Sous 48 heures ouvrées » est un engagement commercial, pas une constante
-- technique. Il se révisera quand l'équipe grossira, ou pendant les congés — et le changer ne
-- doit pas demander un déploiement à quelqu'un qui n'a pas les accès.
--
-- L'ADRESSE DE RÉPONSE. Un message envoyé depuis « no-reply » dit au praticien qu'on ne
-- veut pas de sa réponse. Or celui qui vient de confier son parcours professionnel RÉPOND :
-- il ajoute une pièce, corrige une date, pose une question. Sans adresse surveillée, cette
-- réponse part dans le vide, et il conclut qu'on l'ignore.
-- ═════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO parametres_plateforme (tenant_id, cle, valeur)
SELECT t.id, p.cle, p.valeur
FROM tenants t
CROSS JOIN (VALUES
    ('accompagnement.delai_reponse',   'sous 48 h ouvrées'),
    -- Surveillée, et non « no-reply » : ce que le praticien écrit en réponse doit arriver
    -- quelque part. À confirmer avec l'adresse réellement relevée.
    ('accompagnement.adresse_reponse', 'contact@optimisante.com')
) AS p(cle, valeur)
ON CONFLICT (tenant_id, cle) DO NOTHING;

COMMENT ON TABLE parametres_plateforme IS
    'Réglages d''exploitation, modifiables depuis l''administration sans redéploiement. '
    'Qui les a changés et quand est tracé par le journal d''audit, pas ici.';
