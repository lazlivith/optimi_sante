import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  AlertTriangle, ArrowDownRight, ArrowUpRight, ClipboardCheck, History,
  PackageSearch, RefreshCw, Boxes,
} from 'lucide-react';
import {
  adminStockService,
  type EtatStock, type FiltreStock, type LigneMouvement, type LigneStock, type MotifMouvement,
} from '../../api/adminStockService';
import { PageHeader } from '../../components/common/PageHeader';
import { EmptyState } from '../../components/common/EmptyState';
import { useToast } from '../../context/ToastContext';

/**
 * Rafraîchissement automatique, en millisecondes.
 *
 * <p>Il n'y a pas de canal temps réel sur la plateforme, et en ouvrir un pour cet écran
 * coûterait plus qu'il ne rapporte. Une vente met donc au plus dix secondes à se voir ici,
 * et les gestes faits depuis l'écran se répercutent immédiatement : la mutation invalide le
 * cache sans attendre le prochain cycle.</p>
 */
const RAFRAICHISSEMENT_MS = 10_000;

const ETAT_STYLE: Record<EtatStock, { libelle: string; classe: string }> = {
  RUPTURE: { libelle: 'Rupture', classe: 'bg-danger/10 text-danger' },
  CRITIQUE: { libelle: 'Critique', classe: 'bg-warning/10 text-warning' },
  NON_INVENTORIE: { libelle: 'Jamais inventorié', classe: 'bg-slate-100 text-slate-600' },
  OK: { libelle: 'Correct', classe: 'bg-success/10 text-success' },
};

const FILTRES: { valeur: FiltreStock; libelle: string }[] = [
  { valeur: '', libelle: 'Tout le catalogue' },
  { valeur: 'rupture', libelle: 'En rupture' },
  { valeur: 'critique', libelle: 'Sous le seuil' },
  { valeur: 'jamais_inventorie', libelle: 'Jamais inventoriés' },
];

const MOTIF_LIBELLE: Record<MotifMouvement, string> = {
  VENTE: 'Vente',
  REAPPROVISIONNEMENT: 'Réapprovisionnement',
  INVENTAIRE: 'Inventaire',
  CORRECTION: 'Correction',
  ANNULATION: 'Annulation',
};

const dateCourte = (iso: string | null): string =>
  iso ? new Date(iso).toLocaleDateString('fr-FR', { dateStyle: 'short' }) : '—';

const dateHeure = (iso: string): string =>
  new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' });

export function AdminStockPage() {
  const [recherche, setRecherche] = useState('');
  const [filtre, setFiltre] = useState<FiltreStock>('');
  const [ouvert, setOuvert] = useState<LigneStock | null>(null);
  const { notify } = useToast();
  const queryClient = useQueryClient();

  const resume = useQuery({
    queryKey: ['stock', 'resume'],
    queryFn: adminStockService.resume,
    refetchInterval: RAFRAICHISSEMENT_MS,
  });

  const lignes = useQuery({
    queryKey: ['stock', 'lignes', recherche, filtre],
    queryFn: () => adminStockService.lignes(recherche, filtre),
    refetchInterval: RAFRAICHISSEMENT_MS,
  });

  const journal = useQuery({
    queryKey: ['stock', 'journal'],
    queryFn: () => adminStockService.journal(15),
    refetchInterval: RAFRAICHISSEMENT_MS,
  });

  // Tout ce qui touche au stock est invalidé d'un bloc : une seule saisie change la ligne,
  // le résumé et le journal, et les laisser diverger donnerait un écran qui se contredit.
  const rafraichirTout = () => queryClient.invalidateQueries({ queryKey: ['stock'] });

  return (
    <div>
      <PageHeader
        title="Stock"
        subtitle="Quantités, seuils critiques et journal des mouvements, synchronisés avec les ventes."
        actions={
          <button
            onClick={() => rafraichirTout()}
            className="flex items-center gap-2 px-3 py-2 text-sm rounded-lg border border-slate-200 text-slate-600 hover:border-brand hover:text-brand-dark transition-colors"
          >
            <RefreshCw className={`w-4 h-4 ${lignes.isFetching ? 'animate-spin' : ''}`} />
            Actualiser
          </button>
        }
      />

      <Resume donnees={resume.data} />

      <div className="flex flex-wrap items-center gap-2 mb-4">
        <div className="relative flex-1 min-w-[16rem]">
          <PackageSearch className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            value={recherche}
            onChange={(e) => setRecherche(e.target.value)}
            placeholder="Rechercher un produit…"
            className="w-full pl-9 pr-3 py-2 text-sm rounded-lg border border-slate-200 focus:outline-none focus:ring-2 focus:ring-brand/30"
          />
        </div>
        {FILTRES.map((f) => (
          <button
            key={f.valeur || 'tout'}
            onClick={() => setFiltre(f.valeur)}
            className={`px-3 py-2 text-sm rounded-lg border transition-colors ${
              filtre === f.valeur
                ? 'border-brand bg-brand/5 text-brand-dark font-medium'
                : 'border-slate-200 text-slate-600 hover:border-brand'
            }`}
          >
            {f.libelle}
          </button>
        ))}
      </div>

      {lignes.data && lignes.data.length === 0 ? (
        <EmptyState
          icon={Boxes}
          title="Aucun produit ne correspond"
          description="Changez de filtre ou élargissez la recherche."
        />
      ) : (
        <Tableau lignes={lignes.data ?? []} onOuvrir={setOuvert} />
      )}

      <Journal mouvements={journal.data ?? []} />

      {ouvert && (
        <PanneauProduit
          ligne={ouvert}
          onFermer={() => setOuvert(null)}
          onFait={(message) => {
            notify(message, 'success');
            rafraichirTout();
          }}
          onErreur={(message) => notify(message, 'error')}
        />
      )}
    </div>
  );
}

// --------------------------------------------------------------------------- résumé

function Resume({ donnees }: { donnees?: { produitsSuivis: number; enRupture: number; sousSeuil: number; jamaisInventories: number; unitesEnStock: number; unitesReservees: number } }) {
  const cartes = [
    { libelle: 'Produits actifs', valeur: donnees?.produitsSuivis, accent: 'text-slate-800' },
    { libelle: 'En rupture', valeur: donnees?.enRupture, accent: 'text-danger' },
    { libelle: 'Sous le seuil', valeur: donnees?.sousSeuil, accent: 'text-warning' },
    { libelle: 'Jamais inventoriés', valeur: donnees?.jamaisInventories, accent: 'text-slate-500' },
    { libelle: 'Unités en stock', valeur: donnees?.unitesEnStock, accent: 'text-slate-800' },
    { libelle: 'Unités réservées', valeur: donnees?.unitesReservees, accent: 'text-brand-dark' },
  ];

  return (
    <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-3 mb-6">
      {cartes.map((c) => (
        <div key={c.libelle} className="bg-white rounded-xl border border-slate-200 px-4 py-3">
          <p className="text-xs text-slate-500">{c.libelle}</p>
          <p className={`text-2xl font-semibold mt-1 ${c.accent}`}>
            {c.valeur === undefined ? '—' : c.valeur.toLocaleString('fr-FR')}
          </p>
        </div>
      ))}
    </div>
  );
}

// --------------------------------------------------------------------------- tableau

function Tableau({ lignes, onOuvrir }: { lignes: LigneStock[]; onOuvrir: (l: LigneStock) => void }) {
  return (
    <div className="bg-white rounded-xl border border-slate-200 overflow-hidden mb-8">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-slate-500 text-xs uppercase">
            <tr>
              <th className="text-left font-medium px-4 py-3">Produit</th>
              <th className="text-right font-medium px-4 py-3">Stock</th>
              <th className="text-right font-medium px-4 py-3">Réservé</th>
              <th className="text-right font-medium px-4 py-3">Disponible</th>
              <th className="text-right font-medium px-4 py-3">Seuil</th>
              <th className="text-left font-medium px-4 py-3">État</th>
              <th className="text-left font-medium px-4 py-3">Inventorié</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-100">
            {lignes.map((l) => (
              <tr key={l.productId} className="hover:bg-slate-50/60">
                <td className="px-4 py-3">
                  <p className="font-medium text-slate-800">{l.nom}</p>
                  {l.categorie && <p className="text-xs text-slate-500">{l.categorie}</p>}
                </td>
                <td className="px-4 py-3 text-right tabular-nums">{l.stock}</td>
                <td className="px-4 py-3 text-right tabular-nums text-slate-500">
                  {l.reserve || '—'}
                </td>
                <td className="px-4 py-3 text-right tabular-nums font-medium">{l.disponible}</td>
                <td className="px-4 py-3 text-right tabular-nums text-slate-500">{l.seuil}</td>
                <td className="px-4 py-3">
                  <span className={`text-xs px-2 py-1 rounded-full ${ETAT_STYLE[l.etat].classe}`}>
                    {ETAT_STYLE[l.etat].libelle}
                  </span>
                </td>
                <td className="px-4 py-3 text-slate-500">{dateCourte(l.inventorieLe)}</td>
                <td className="px-4 py-3 text-right">
                  <button
                    onClick={() => onOuvrir(l)}
                    className="text-brand hover:text-brand-dark text-xs font-medium underline underline-offset-2"
                  >
                    Ajuster
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// --------------------------------------------------------------------------- journal

function Journal({ mouvements }: { mouvements: LigneMouvement[] }) {
  if (mouvements.length === 0) {
    return null;
  }
  return (
    <section className="bg-white rounded-xl border border-slate-200 p-4">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-700 mb-3">
        <History className="w-4 h-4" />
        Derniers mouvements
      </h2>
      <ul className="divide-y divide-slate-100">
        {mouvements.map((m) => (
          <li key={m.id} className="py-2 flex items-center gap-3 text-sm">
            <Delta valeur={m.delta} />
            <div className="min-w-0 flex-1">
              <p className="text-slate-800 truncate">{m.produit ?? 'Produit supprimé'}</p>
              <p className="text-xs text-slate-500">
                {MOTIF_LIBELLE[m.motif]}
                {m.auteur && ` · ${m.auteur}`}
                {m.commentaire && ` · ${m.commentaire}`}
              </p>
            </div>
            <div className="text-right shrink-0">
              <p className="tabular-nums text-slate-600">reste {m.stockApres}</p>
              <p className="text-xs text-slate-400">{dateHeure(m.date)}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

function Delta({ valeur }: { valeur: number }) {
  if (valeur === 0) {
    return <ClipboardCheck className="w-4 h-4 text-slate-400 shrink-0" />;
  }
  const entrant = valeur > 0;
  const Icone = entrant ? ArrowUpRight : ArrowDownRight;
  return (
    <span
      className={`flex items-center gap-1 tabular-nums text-sm font-medium shrink-0 ${
        entrant ? 'text-success' : 'text-danger'
      }`}
    >
      <Icone className="w-4 h-4" />
      {entrant ? `+${valeur}` : valeur}
    </span>
  );
}

// --------------------------------------------------------------------------- panneau

function PanneauProduit({
  ligne, onFermer, onFait, onErreur,
}: {
  ligne: LigneStock;
  onFermer: () => void;
  onFait: (message: string) => void;
  onErreur: (message: string) => void;
}) {
  const [motif, setMotif] = useState<Exclude<MotifMouvement, 'VENTE'>>('INVENTAIRE');
  const [valeur, setValeur] = useState('');
  const [commentaire, setCommentaire] = useState('');
  const [seuil, setSeuil] = useState(String(ligne.seuil));

  const historique = useQuery({
    queryKey: ['stock', 'historique', ligne.productId],
    queryFn: () => adminStockService.historique(ligne.productId, 20),
  });

  const estInventaire = motif === 'INVENTAIRE';

  // Ce que vaudra le stock une fois la saisie appliquée : un inventaire POSE la valeur,
  // les autres motifs l'ajoutent. L'afficher évite la faute de signe, qui est l'erreur la
  // plus courante — et la plus difficile à repérer après coup.
  const apres = useMemo(() => {
    const n = Number(valeur);
    if (valeur.trim() === '' || Number.isNaN(n)) return null;
    return estInventaire ? Math.max(0, n) : Math.max(0, ligne.stock + n);
  }, [valeur, estInventaire, ligne.stock]);

  const messageErreur = (e: unknown): string =>
    (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ?? "L'ajustement n'a pas pu être enregistré.";

  const ajustement = useMutation({
    mutationFn: () => {
      const n = Number(valeur);
      return adminStockService.ajuster(ligne.productId, {
        motif,
        ...(estInventaire ? { quantiteConstatee: n } : { delta: n }),
        commentaire: commentaire.trim() || undefined,
      });
    },
    onSuccess: (r) => {
      onFait(`« ${ligne.nom} » : stock à ${r.stock}.`);
      onFermer();
    },
    onError: (e) => onErreur(messageErreur(e)),
  });

  const modifSeuil = useMutation({
    mutationFn: () => adminStockService.seuil(ligne.productId, Number(seuil)),
    onSuccess: () => onFait(`Seuil critique de « ${ligne.nom} » réglé sur ${seuil}.`),
    onError: (e) => onErreur(messageErreur(e)),
  });

  const saisieValide = valeur.trim() !== '' && !Number.isNaN(Number(valeur))
    && (estInventaire ? Number(valeur) >= 0 : Number(valeur) !== 0);

  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-slate-900/40 p-4">
      <div className="bg-white rounded-2xl w-full max-w-lg max-h-[90vh] overflow-y-auto p-5 shadow-xl">
        <div className="flex items-start justify-between gap-3 mb-4">
          <div className="min-w-0">
            <h2 className="font-semibold text-slate-800">{ligne.nom}</h2>
            <p className="text-sm text-slate-500">
              Stock {ligne.stock} · réservé {ligne.reserve} · disponible {ligne.disponible}
            </p>
          </div>
          <button onClick={onFermer} className="text-slate-400 hover:text-slate-600 text-sm">
            Fermer
          </button>
        </div>

        {ligne.inventorieLe === null && (
          <p className="flex gap-2 text-xs bg-warning/10 border border-warning/30 text-warning rounded-lg px-3 py-2 mb-4">
            <AlertTriangle className="w-4 h-4 shrink-0" />
            <span>
              Ce produit n'a jamais été compté : sa quantité vient de l'import, pas d'un
              constat. Un inventaire la rendra fiable.
            </span>
          </p>
        )}

        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            {(['INVENTAIRE', 'REAPPROVISIONNEMENT', 'CORRECTION', 'ANNULATION'] as const).map((m) => (
              <button
                key={m}
                onClick={() => { setMotif(m); setValeur(''); }}
                className={`px-3 py-1.5 text-xs rounded-lg border transition-colors ${
                  motif === m
                    ? 'border-brand bg-brand/5 text-brand-dark font-medium'
                    : 'border-slate-200 text-slate-600 hover:border-brand'
                }`}
              >
                {MOTIF_LIBELLE[m]}
              </button>
            ))}
          </div>

          <label className="block">
            <span className="text-xs text-slate-500">
              {estInventaire ? 'Quantité comptée' : 'Variation (ex. 40 ou -3)'}
            </span>
            <input
              value={valeur}
              onChange={(e) => setValeur(e.target.value)}
              inputMode="numeric"
              placeholder={estInventaire ? String(ligne.stock) : '+10'}
              className="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-brand/30"
            />
          </label>

          {apres !== null && (
            <p className="text-sm text-slate-600">
              Nouveau stock : <span className="font-semibold tabular-nums">{apres}</span>
              {apres <= ligne.seuil && (
                <span className="text-warning"> — sous le seuil de {ligne.seuil}</span>
              )}
            </p>
          )}

          <label className="block">
            <span className="text-xs text-slate-500">Commentaire (facultatif)</span>
            <input
              value={commentaire}
              onChange={(e) => setCommentaire(e.target.value)}
              maxLength={500}
              placeholder="Réception fournisseur, casse, écart constaté…"
              className="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-brand/30"
            />
          </label>

          <button
            onClick={() => ajustement.mutate()}
            disabled={!saisieValide || ajustement.isPending}
            className="w-full py-2.5 rounded-lg bg-brand text-white text-sm font-medium hover:bg-brand-fonce disabled:opacity-40 transition-colors"
          >
            {ajustement.isPending ? 'Enregistrement…' : 'Enregistrer le mouvement'}
          </button>
        </div>

        <div className="mt-5 pt-4 border-t border-slate-100">
          <label className="flex items-end gap-2">
            <span className="flex-1">
              <span className="text-xs text-slate-500">Seuil critique</span>
              <input
                value={seuil}
                onChange={(e) => setSeuil(e.target.value)}
                inputMode="numeric"
                className="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-brand/30"
              />
            </span>
            <button
              onClick={() => modifSeuil.mutate()}
              disabled={seuil.trim() === '' || Number.isNaN(Number(seuil)) || Number(seuil) < 0}
              className="px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-600 hover:border-brand hover:text-brand-dark disabled:opacity-40 transition-colors"
            >
              Régler
            </button>
          </label>
          <p className="text-xs text-slate-400 mt-1">
            Zéro : aucun avertissement de creux. Une rupture reste signalée.
          </p>
        </div>

        <div className="mt-5 pt-4 border-t border-slate-100">
          <h3 className="text-xs font-semibold text-slate-600 uppercase mb-2">Historique</h3>
          {(historique.data ?? []).length === 0 ? (
            <p className="text-sm text-slate-400">Aucun mouvement enregistré pour ce produit.</p>
          ) : (
            <ul className="divide-y divide-slate-100">
              {(historique.data ?? []).map((m) => (
                <li key={m.id} className="py-2 flex items-center gap-3 text-sm">
                  <Delta valeur={m.delta} />
                  <span className="flex-1 min-w-0 text-slate-600 truncate">
                    {MOTIF_LIBELLE[m.motif]}
                    {m.commentaire && ` · ${m.commentaire}`}
                  </span>
                  <span className="text-xs text-slate-400 shrink-0">{dateHeure(m.date)}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  );
}
