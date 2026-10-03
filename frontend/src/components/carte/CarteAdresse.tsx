import { useEffect, useRef, useState } from 'react';
import { MapPin, Loader2, ExternalLink } from 'lucide-react';

interface Props {
  /** L'adresse à situer, en texte libre. Vide ou trop courte : rien n'est affiché. */
  adresse: string;
  /** Titre affiché au-dessus de la carte. */
  titre?: string;
  /** Précision sous la carte (ex. « Emplacement indicatif »). */
  legende?: string;
  /** Hauteur du cadre, en pixels. */
  hauteur?: number;
}

interface Position { lat: string; lon: string; boite: [string, string, string, string] }

/** Mémoire de la session : la même adresse n'est géocodée qu'une fois. */
const cache = new Map<string, Position | null>();

/**
 * Une carte pour situer une adresse.
 *
 * <p>Un <code>&lt;iframe&gt;</code> OpenStreetMap, et non une bibliothèque de cartographie :
 * le plafond de poids JavaScript de la charte est à 1 500 Ko et le paquet en frôle déjà la
 * limite. Un cadre embarqué n'ajoute pas une ligne de script.</p>
 *
 * <p>Le géocodage passe par Nominatim, un service tiers. C'est l'appelant qui décide de ce
 * qu'il lui confie : pour une adresse de livraison, on ne lui transmet que le code postal, la
 * ville et le pays — assez pour situer la zone, sans faire sortir la rue et le nom du
 * destinataire de la plateforme. L'adresse d'un centre de formation, elle, est publique.</p>
 */
export function CarteAdresse({ adresse, titre, legende, hauteur = 240 }: Props) {
  const [position, setPosition] = useState<Position | null>(null);
  const [etat, setEtat] = useState<'vide' | 'recherche' | 'trouve' | 'introuvable'>('vide');
  const dernier = useRef('');

  const requete = adresse.trim();

  useEffect(() => {
    if (requete.length < 6) { setEtat('vide'); setPosition(null); return; }
    if (cache.has(requete)) {
      const connu = cache.get(requete) ?? null;
      setPosition(connu);
      setEtat(connu ? 'trouve' : 'introuvable');
      return;
    }

    // L'adresse arrive frappe par frappe : sans ce delai, chaque lettre serait une requete
    // au service de geocodage, dont les conditions d'usage limitent la cadence.
    dernier.current = requete;
    setEtat('recherche');
    const abandon = new AbortController();
    const minuteur = setTimeout(async () => {
      try {
        const reponse = await fetch(
          'https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q='
          + encodeURIComponent(requete),
          { signal: abandon.signal, headers: { 'Accept-Language': 'fr' } },
        );
        const resultats = await reponse.json();
        const premier = Array.isArray(resultats) && resultats[0] ? resultats[0] : null;
        const trouve: Position | null = premier
          ? { lat: premier.lat, lon: premier.lon, boite: premier.boundingbox }
          : null;
        cache.set(requete, trouve);
        if (dernier.current !== requete) return;
        setPosition(trouve);
        setEtat(trouve ? 'trouve' : 'introuvable');
      } catch {
        // Service injoignable : la carte est un confort, pas une etape du parcours. On
        // propose le lien et on laisse le client continuer.
        if (dernier.current === requete) setEtat('introuvable');
      }
    }, 800);

    return () => { clearTimeout(minuteur); abandon.abort(); };
  }, [requete]);

  if (etat === 'vide') return null;

  const lienOsm = 'https://www.openstreetmap.org/search?query=' + encodeURIComponent(requete);

  let cadre = null;
  if (position) {
    const [sud, nord, ouest, est] = position.boite.map(Number);
    // Un point renvoie une boite minuscule : on l'elargit pour que les rues voisines
    // restent visibles, sinon la carte ne situe plus rien.
    const marge = Math.max(0.004, (nord - sud) / 2, (est - ouest) / 2);
    const bbox = [ouest - marge, sud - marge, est + marge, nord + marge].join(',');
    cadre = (
      <iframe
        title={titre ?? 'Carte de localisation'}
        loading="lazy"
        className="w-full rounded-xl border border-slate-200"
        style={{ height: hauteur }}
        src={`https://www.openstreetmap.org/export/embed.html?bbox=${bbox}&layer=mapnik`
          + `&marker=${position.lat},${position.lon}`}
      />
    );
  }

  return (
    <div>
      {titre && (
        <h3 className="mb-2 flex items-center gap-2 text-sm font-semibold text-brand-dark">
          <MapPin className="h-4 w-4 text-brand" aria-hidden="true" />
          {titre}
        </h3>
      )}

      {etat === 'recherche' && (
        <div
          className="flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-50 text-sm text-slate-500"
          style={{ height: hauteur }}
        >
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          Localisation…
        </div>
      )}

      {etat === 'introuvable' && (
        <div
          className="flex flex-col items-center justify-center gap-2 rounded-xl border border-slate-200 bg-slate-50 px-4 text-center text-sm text-slate-500"
          style={{ height: hauteur }}
        >
          <span>Cette adresse n'a pas pu être située sur la carte.</span>
          <a href={lienOsm} target="_blank" rel="noopener noreferrer"
             className="inline-flex items-center gap-1 font-semibold text-brand hover:underline">
            Ouvrir dans OpenStreetMap <ExternalLink className="h-3.5 w-3.5" aria-hidden="true" />
          </a>
        </div>
      )}

      {cadre}

      {position && (
        <p className="mt-2 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
          <span>{legende ?? 'Emplacement indicatif, fourni par OpenStreetMap.'}</span>
          <a href={lienOsm} target="_blank" rel="noopener noreferrer"
             className="inline-flex items-center gap-1 font-medium text-brand hover:underline">
            Voir en grand <ExternalLink className="h-3 w-3" aria-hidden="true" />
          </a>
        </p>
      )}
    </div>
  );
}
