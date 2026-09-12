import { useState } from 'react';
import { Play, Video as VideoIcon } from 'lucide-react';

export type VideoProvider = 'CLOUDINARY' | 'YOUTUBE' | 'VIMEO' | 'LOOM';

/**
 * Adresse d'intégration d'une vidéo hébergée ailleurs.
 *
 * <p>Les liens que l'on copie depuis un navigateur ne sont pas des adresses d'intégration :
 * `youtube.com/watch?v=…` affiche la page YouTube entière dans le cadre, avec ses suggestions
 * et son en-tête. Chaque hébergeur a sa forme `embed`, reconstruite ici à partir de
 * l'identifiant.</p>
 *
 * <p>Renvoie `null` quand l'adresse ne correspond à aucune forme connue : un cadre vide vaut
 * mieux qu'une page tierce affichée en pleine fiche produit.</p>
 */
export function adresseIntegration(url: string, provider: VideoProvider): string | null {
  try {
    const u = new URL(url);

    if (provider === 'YOUTUBE') {
      // Trois formes circulent : /watch?v=, youtu.be/, et /embed/ déjà prête.
      const id = u.searchParams.get('v')
        ?? (u.hostname.includes('youtu.be') ? u.pathname.slice(1) : null)
        ?? (u.pathname.startsWith('/embed/') ? u.pathname.slice(7) : null);
      return id ? `https://www.youtube-nocookie.com/embed/${id}` : null;
    }

    if (provider === 'VIMEO') {
      const id = u.pathname.split('/').filter(Boolean).pop();
      return id && /^\d+$/.test(id) ? `https://player.vimeo.com/video/${id}` : null;
    }

    if (provider === 'LOOM') {
      // loom.com/share/<id> devient loom.com/embed/<id>.
      const id = u.pathname.split('/').filter(Boolean).pop();
      return id ? `https://www.loom.com/embed/${id}` : null;
    }

    return null;
  } catch {
    return null;
  }
}

/**
 * Lecteur de la vidéo de démonstration d'un produit.
 *
 * <p>Cloudinary héberge nos propres fichiers : une balise `<video>` native suffit, elle est
 * plus légère et ne dépose aucun traceur. Les trois autres hébergeurs imposent leur cadre.</p>
 *
 * <p><b>Aucune lecture automatique.</b> Une vidéo qui démarre seule sur une fiche produit
 * surprend, consomme des données mobiles et gêne les personnes sensibles au mouvement. Le
 * visiteur appuie sur lecture — et pour les hébergeurs tiers, le cadre n'est même pas chargé
 * avant ce geste : rien n'est demandé à YouTube tant que personne ne regarde.</p>
 */
export function ProductVideo({
  videoUrl, videoProvider, productName,
}: {
  videoUrl: string;
  videoProvider: VideoProvider;
  productName: string;
}) {
  const [lecteurCharge, setLecteurCharge] = useState(false);

  if (videoProvider === 'CLOUDINARY') {
    return (
      <video
        controls
        preload="metadata"
        playsInline
        className="h-full w-full rounded-2xl bg-black object-contain"
        aria-label={`Vidéo de démonstration — ${productName}`}
      >
        <source src={videoUrl} />
        Votre navigateur ne sait pas lire cette vidéo.{' '}
        <a href={videoUrl}>La télécharger</a>.
      </video>
    );
  }

  const integration = adresseIntegration(videoUrl, videoProvider);

  if (!integration) {
    // Adresse non reconnue : plutôt qu'un cadre vide, on propose le lien d'origine.
    return (
      <a
        href={videoUrl}
        target="_blank"
        rel="noopener noreferrer"
        className="flex h-full w-full flex-col items-center justify-center gap-2 rounded-2xl border border-slate-200 bg-slate-50 text-sm font-semibold text-brand-green hover:bg-slate-100"
      >
        <VideoIcon className="h-7 w-7" />
        Voir la vidéo de démonstration
      </a>
    );
  }

  if (!lecteurCharge) {
    return (
      <button
        type="button"
        onClick={() => setLecteurCharge(true)}
        className="group relative flex h-full w-full items-center justify-center rounded-2xl bg-brand-dark focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-green focus-visible:ring-offset-2"
        aria-label={`Lire la vidéo de démonstration de ${productName}`}
      >
        <span className="flex h-16 w-16 items-center justify-center rounded-full bg-white/95 shadow-lg transition-transform group-hover:scale-105">
          <Play className="ml-1 h-7 w-7 fill-brand-dark text-brand-dark" />
        </span>
        <span className="absolute bottom-4 text-xs font-medium text-white/70">
          Vidéo de démonstration
        </span>
      </button>
    );
  }

  return (
    <iframe
      src={`${integration}?autoplay=1`}
      title={`Vidéo de démonstration — ${productName}`}
      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
      allowFullScreen
      className="h-full w-full rounded-2xl border-0 bg-black"
    />
  );
}

/**
 * Vidéo jouée sur une carte produit des sections promotionnelles.
 *
 * <p>Elle ne remplace la vignette qu'au survol, en boucle et sans son. Trois garde-fous :</p>
 *
 * <ul>
 *   <li><b>Cloudinary seulement</b> — une `<iframe>` tierce ne se contrôle pas au survol, et
 *       chargerait un lecteur complet pour chaque carte de la page.</li>
 *   <li><b>Chargée à la demande</b> — `preload="none"` tant que personne ne survole : une page
 *       d'accueil ne doit pas télécharger dix vidéos que nul ne regardera.</li>
 * </ul>
 */
export function PromoVideoOverlay({
  videoUrl, videoProvider, actif,
}: {
  videoUrl: string;
  videoProvider: VideoProvider;
  /** Vrai quand la lecture est demandée : survol, ou appui sur Play. C'est la carte qui en
   *  décide, parce qu'elle seule sait si l'animation automatique est autorisée. */
  actif: boolean;
}) {
  if (videoProvider !== 'CLOUDINARY') {
    return null;
  }

  return (
    <video
      src={videoUrl}
      muted
      loop
      playsInline
      preload="none"
      aria-hidden="true"
      // La vidéo se superpose à la vignette et n'apparaît qu'au survol : la vignette reste
      // l'état de repos, celui que voit un visiteur qui ne fait rien.
      className={`pointer-events-none absolute inset-0 h-full w-full object-contain transition-opacity duration-300 ${
        actif ? 'opacity-100' : 'opacity-0'
      }`}
      ref={(el) => {
        if (!el) return;
        if (actif) {
          // La promesse est ignorée volontairement : un navigateur peut refuser la lecture
          // (onglet en arrière-plan, économiseur de données), et ce refus n'est pas une erreur.
          void el.play().catch(() => undefined);
        } else {
          el.pause();
          el.currentTime = 0;
        }
      }}
    />
  );
}
