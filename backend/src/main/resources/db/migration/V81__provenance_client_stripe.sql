-- D'où vient l'identifiant client Stripe que nous gardons pour un utilisateur.
--
-- Un `cus_…` n'existe que dans l'environnement Stripe qui l'a créé. Le 27 octobre, un
-- changement d'environnement de test a rendu invalides tous ceux déjà en base : la plateforme
-- les renvoyait tels quels et Stripe répondait « No such customer », donc aucun paiement
-- possible pour ces comptes. Le passage en mode réel aurait produit la même panne, sur tous
-- les utilisateurs à la fois.
--
-- Effacer les identifiants à la main réparerait ce cas-ci, puis il faudrait le refaire au
-- passage en production. Les enregistrer avec leur provenance rend la vérification définitive.
ALTER TABLE users ADD COLUMN stripe_customer_mode   varchar(10);
ALTER TABLE users ADD COLUMN stripe_customer_compte varchar(40);

-- Volontairement NULL, sans valeur par défaut : la provenance des identifiants déjà en base
-- est réellement inconnue, et c'est cela qu'il faut écrire. Le code traite « inconnue » comme
-- « ne correspond pas » — l'identifiant est donc remplacé à la première occasion, et
-- l'incertitude s'efface d'elle-même sans qu'on réécrive aucune donnée.
--
-- Rien n'est supprimé non plus : un `cus_…` conservé reste lisible pour un rapprochement
-- comptable, même lorsqu'il n'est plus utilisable pour encaisser.

COMMENT ON COLUMN users.stripe_customer_mode IS
    'Mode Stripe qui a cree stripe_customer_id : test ou live. NULL = provenance inconnue, identifiant non reutilisable.';
COMMENT ON COLUMN users.stripe_customer_compte IS
    'Compte Stripe (acct_...) qui a cree stripe_customer_id. NULL si le compte n''a pas pu etre lu a la creation.';
