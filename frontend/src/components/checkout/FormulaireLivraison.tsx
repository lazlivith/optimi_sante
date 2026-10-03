import { useEffect, useMemo, useRef, useState } from 'react';
import { Loader2, Truck, AlertTriangle } from 'lucide-react';
import {
  shippingService, type Destination, type EstimationLivraison,
} from '../../api/adminShippingService';
import { CarteAdresse } from '../carte/CarteAdresse';

export interface AdresseLivraison {
  recipient: string;
  line1: string;
  line2: string;
  postalCode: string;
  city: string;
  /** Code ISO 3166-1 alpha-2. Vide : aucun pays choisi, donc aucun frais calculé. */
  country: string;
}

export const ADRESSE_VIDE: AdresseLivraison = {
  recipient: '', line1: '', line2: '', postalCode: '', city: '', country: '',
};

/** L'adresse est-elle assez complète pour qu'une commande puisse partir ? */
export function adresseComplete(a: AdresseLivraison): boolean {
  return !!(a.recipient.trim() && a.line1.trim() && a.postalCode.trim() && a.city.trim()
    && a.country);
}

interface Props {
  adresse: AdresseLivraison;
  onAdresseChange: (adresse: AdresseLivraison) => void;
  /** Montant des articles, remise déduite : c'est lui qui déclenche la livraison offerte. */
  montantArticles: number;
  /** Les frais calculés par le serveur, ou `null` si la destination n'est pas desservie. */
  onFraisChange: (frais: EstimationLivraison | null) => void;
}

/**
 * L'adresse de destination, et ce que la livraison coûte vers ce pays.
 *
 * <p>Le montant n'est jamais calculé ici : il vient du serveur, qui détient la grille. Une
 * destination non desservie fait répondre une erreur — alors on affiche l'invitation à
 * demander un devis de transport plutôt qu'un montant inventé, et la commande reste bloquée.</p>
 */
export function FormulaireLivraison({
  adresse, onAdresseChange, montantArticles, onFraisChange,
}: Props) {
  const [destinations, setDestinations] = useState<Destination[]>([]);
  const [calcul, setCalcul] = useState(false);
  const [refus, setRefus] = useState<string | null>(null);
  const dernierePesee = useRef(0);

  useEffect(() => {
    shippingService.destinations().then(setDestinations).catch(() => setDestinations([]));
  }, []);

  // Intl est fourni par le navigateur : les noms de pays en francais ne coutent pas un octet
  // de paquet, la ou une table de correspondance en coutait deux cents lignes a maintenir.
  const nomDePays = useMemo(() => {
    try {
      const noms = new Intl.DisplayNames(['fr'], { type: 'region' });
      return (code: string) => noms.of(code) ?? code;
    } catch {
      return (code: string) => code;
    }
  }, []);

  /** Les destinations rangées par zone, chaque zone triée par nom de pays. */
  const parZone = useMemo(() => {
    const groupes = new Map<string, { libelle: string; pays: { code: string; nom: string }[] }>();
    for (const d of destinations) {
      if (!groupes.has(d.zone)) groupes.set(d.zone, { libelle: d.zoneLibelle, pays: [] });
      groupes.get(d.zone)!.pays.push({ code: d.codePays, nom: nomDePays(d.codePays) });
    }
    for (const g of groupes.values()) g.pays.sort((a, b) => a.nom.localeCompare(b.nom, 'fr'));
    return [...groupes.values()];
  }, [destinations, nomDePays]);

  useEffect(() => {
    if (!adresse.country) { setRefus(null); onFraisChange(null); return; }

    const pesee = ++dernierePesee.current;
    setCalcul(true);
    shippingService.estimer(adresse.country, montantArticles)
      .then(estimation => {
        if (dernierePesee.current !== pesee) return;
        setRefus(null);
        onFraisChange(estimation);
      })
      .catch((err: any) => {
        if (dernierePesee.current !== pesee) return;
        setRefus(err.response?.data?.message
          ?? "Nous ne livrons pas encore vers cette destination. Écrivez-nous pour obtenir un "
             + 'devis de transport.');
        onFraisChange(null);
      })
      .finally(() => { if (dernierePesee.current === pesee) setCalcul(false); });
    // onFraisChange est volontairement hors des dependances : le parent le recree a chaque
    // rendu, et l'inclure relancerait l'estimation en boucle.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [adresse.country, montantArticles]);

  const modifier = (champ: keyof AdresseLivraison, valeur: string) =>
    onAdresseChange({ ...adresse, [champ]: valeur });

  const champ = 'w-full rounded-xl border border-slate-200 px-3 py-2.5 text-sm '
    + 'focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/20';

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="mb-6 flex items-center gap-2 text-xl font-semibold text-brand-dark">
        <Truck className="h-5 w-5 text-brand" aria-hidden="true" />
        Adresse de livraison
      </h2>

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="sm:col-span-2">
          <label htmlFor="liv-destinataire" className="mb-1 block text-xs font-medium text-slate-600">
            Destinataire
          </label>
          <input id="liv-destinataire" className={champ} autoComplete="name"
                 value={adresse.recipient}
                 onChange={e => modifier('recipient', e.target.value)} />
        </div>

        <div className="sm:col-span-2">
          <label htmlFor="liv-adresse" className="mb-1 block text-xs font-medium text-slate-600">
            Adresse
          </label>
          <input id="liv-adresse" className={champ} autoComplete="address-line1"
                 placeholder="Numéro et rue"
                 value={adresse.line1}
                 onChange={e => modifier('line1', e.target.value)} />
        </div>

        <div className="sm:col-span-2">
          <label htmlFor="liv-complement" className="mb-1 block text-xs font-medium text-slate-600">
            Complément <span className="font-normal text-slate-400">(facultatif)</span>
          </label>
          <input id="liv-complement" className={champ} autoComplete="address-line2"
                 placeholder="Bâtiment, étage, service…"
                 value={adresse.line2}
                 onChange={e => modifier('line2', e.target.value)} />
        </div>

        <div>
          <label htmlFor="liv-cp" className="mb-1 block text-xs font-medium text-slate-600">
            Code postal
          </label>
          <input id="liv-cp" className={champ} autoComplete="postal-code"
                 value={adresse.postalCode}
                 onChange={e => modifier('postalCode', e.target.value)} />
        </div>

        <div>
          <label htmlFor="liv-ville" className="mb-1 block text-xs font-medium text-slate-600">
            Ville
          </label>
          <input id="liv-ville" className={champ} autoComplete="address-level2"
                 value={adresse.city}
                 onChange={e => modifier('city', e.target.value)} />
        </div>

        <div className="sm:col-span-2">
          <label htmlFor="liv-pays" className="mb-1 block text-xs font-medium text-slate-600">
            Pays
          </label>
          <select id="liv-pays" className={champ} autoComplete="country"
                  value={adresse.country}
                  onChange={e => modifier('country', e.target.value)}>
            <option value="">Choisir le pays de livraison…</option>
            {parZone.map(groupe => (
              <optgroup key={groupe.libelle} label={groupe.libelle}>
                {groupe.pays.map(p => (
                  <option key={p.code} value={p.code}>{p.nom}</option>
                ))}
              </optgroup>
            ))}
          </select>
        </div>
      </div>

      {calcul && (
        <p className="mt-4 flex items-center gap-2 text-sm text-slate-500">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
          Calcul des frais de livraison…
        </p>
      )}

      {refus && (
        <p className="mt-4 flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          {refus}
        </p>
      )}

      {/* La carte ne recoit que le code postal, la ville et le pays : assez pour situer la
          zone de livraison, sans confier la rue et le nom du destinataire a un tiers. */}
      {adresse.country === 'FR' && adresse.postalCode.trim() && adresse.city.trim() && (
        <div className="mt-6">
          <CarteAdresse
            titre="Zone de livraison"
            adresse={`${adresse.postalCode} ${adresse.city}, France`}
            legende="Repère sur la commune de livraison. La rue n'est pas transmise à la carte."
            hauteur={220}
          />
        </div>
      )}
    </div>
  );
}
