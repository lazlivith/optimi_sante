-- V39 : inscriptions a la lettre d'information
--
-- POURQUOI UNE TABLE DEDIEE PLUTOT QUE `prospect_leads`
-- `prospect_leads` sert la capture de contact sur une formation : elle exige un prenom, un
-- nom, un telephone, un pays et une formation cible. Y verser des inscriptions newsletter
-- obligerait a remplir ces colonnes avec des valeurs inventees, et melangerait deux
-- populations que le commercial doit pouvoir distinguer — un prospect formation n'est pas un
-- abonne.
--
-- CE QUI REND LE CONSENTEMENT OPPOSABLE
-- Le RGPD demande de pouvoir **prouver** le consentement, pas seulement de l'avoir recueilli.
-- Enregistrer une case cochee ne prouve rien : il faut savoir a QUOI la personne a consenti.
-- `consent_text` conserve donc le libelle exact affiche au moment de l'inscription. Si le
-- texte evolue, les consentements anterieurs restent lisibles tels qu'ils ont ete donnes.

CREATE TABLE IF NOT EXISTS newsletter_subscribers (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email            varchar(255) NOT NULL,

    consent_given_at timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    /** Libelle exact accepte : c'est lui qui rend le consentement demontrable. */
    consent_text     text         NOT NULL,

    /** D'ou vient l'inscription, pour distinguer les points de collecte. */
    source           varchar(50)  NOT NULL DEFAULT 'FOOTER',

    /** Desinscription : la ligne est conservee, sans quoi on ne pourrait pas prouver
        qu'une demande de retrait a bien ete honoree. */
    unsubscribed_at  timestamptz,

    created_at       timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Une adresse ne peut etre abonnee qu'une fois a la fois. L'index est partiel : apres une
-- desinscription, la meme adresse peut se reabonner sans que l'historique soit efface.
CREATE UNIQUE INDEX IF NOT EXISTS uq_newsletter_email_active
    ON newsletter_subscribers (lower(email))
    WHERE unsubscribed_at IS NULL;

COMMENT ON COLUMN newsletter_subscribers.consent_text IS
    'Libelle exact accepte par la personne. Conserve pour prouver a quoi elle a consenti, '
    'meme si le texte affiche evolue ensuite.';
