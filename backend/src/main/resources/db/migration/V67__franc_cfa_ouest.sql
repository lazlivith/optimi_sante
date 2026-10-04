-- Le franc CFA d'Afrique de l'Ouest (XOF), pour le Sénégal, la Côte d'Ivoire et les six autres
-- États de l'UEMOA : Bénin, Burkina Faso, Guinée-Bissau, Mali, Niger, Togo.
--
-- Même parité FIXE que son homologue d'Afrique centrale : 1 EUR = 655,957 XOF, tenue par la
-- BCEAO. Les deux francs CFA sont des monnaies distinctes — on ne paie pas à Dakar avec des
-- billets de Brazzaville — mais leur taux face à l'euro est le même, et c'est une constante,
-- pas une cotation. Aucun risque de change à couvrir.
--
-- Semée FERMÉE, comme le XAF. Ouvrir une devise fait voir ses prix aux clients ET facturer
-- dans cette monnaie : cela se décide depuis l'écran d'administration, une fois vérifié que
-- l'encaissement dans cette devise fonctionne de bout en bout.

INSERT INTO taux_change (tenant_id, devise, taux, palier_arrondi, actif)
SELECT id, 'XOF', 655.957, 1000, false FROM tenants
ON CONFLICT (tenant_id, devise) DO NOTHING;
