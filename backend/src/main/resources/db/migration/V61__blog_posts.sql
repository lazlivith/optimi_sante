-- Blog : les publications éditoriales d'Optimi Santé, dont les événements à venir.
--
-- Pourquoi une seule table pour deux choses. Un billet d'actualité et l'annonce d'un congrès
-- se rédigent, se relisent et se publient de la même façon ; seules trois colonnes les
-- distinguent (lieu et dates). Deux tables imposeraient deux écrans d'administration, deux
-- routes publiques et deux fois la même logique de brouillon. Une publication dont
-- event_starts_on est renseigné est un événement ; sinon c'est un article.
--
-- La bannière « À venir » de la page d'accueil montrait jusqu'ici un congrès fictif écrit en
-- dur dans le code. Elle lit désormais le prochain événement publié ici.

CREATE TABLE IF NOT EXISTS blog_posts (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id),

    title           varchar(200) NOT NULL,
    -- Le slug est l'adresse publique de la publication : il est stable, et ne suit pas les
    -- retouches ultérieures du titre, sans quoi un lien déjà partagé mènerait à une 404.
    slug            varchar(220) NOT NULL,
    -- Chapô affiché dans la liste et sur la bannière d'accueil. Facultatif : une annonce
    -- d'événement tient parfois dans son titre, son lieu et ses dates.
    excerpt         varchar(400),
    content         text NOT NULL,
    cover_image_url text,

    -- Renseignées uniquement pour un événement.
    event_location  varchar(160),
    event_starts_on date,
    event_ends_on   date,

    -- Un brouillon reste invisible du public : la rédaction se fait en plusieurs fois, et
    -- une annonce à demi écrite ne doit pas apparaître sur le site entre deux enregistrements.
    is_published    boolean NOT NULL DEFAULT false,
    published_at    timestamptz,

    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_blog_posts_tenant_slug
    ON blog_posts (tenant_id, slug);

-- Les deux seules lectures publiques : la liste des publications et le prochain événement.
-- Toutes deux partent du même filtre, d'où un index unique plutôt qu'un par requête.
CREATE INDEX IF NOT EXISTS idx_blog_posts_publiees
    ON blog_posts (tenant_id, is_published, published_at DESC);

CREATE INDEX IF NOT EXISTS idx_blog_posts_evenements
    ON blog_posts (tenant_id, event_starts_on)
    WHERE event_starts_on IS NOT NULL;

-- Une date de fin sans date de début ne décrit rien, et un événement qui se termine avant
-- d'avoir commencé afficherait des dates absurdes sur la bannière d'accueil.
ALTER TABLE blog_posts DROP CONSTRAINT IF EXISTS blog_posts_dates_coherentes;
ALTER TABLE blog_posts
    ADD CONSTRAINT blog_posts_dates_coherentes
    CHECK (
        (event_ends_on IS NULL OR event_starts_on IS NOT NULL)
        AND (event_ends_on IS NULL OR event_ends_on >= event_starts_on)
    );

-- Une publication en ligne porte forcément sa date de mise en ligne : la liste publique
-- s'ordonne dessus, et une valeur nulle la ferait disparaître du classement.
ALTER TABLE blog_posts DROP CONSTRAINT IF EXISTS blog_posts_publiee_datee;
ALTER TABLE blog_posts
    ADD CONSTRAINT blog_posts_publiee_datee
    CHECK (is_published = false OR published_at IS NOT NULL);

COMMENT ON TABLE blog_posts IS
    'Publications éditoriales du site. event_starts_on renseigné : événement à venir ; sinon article.';
