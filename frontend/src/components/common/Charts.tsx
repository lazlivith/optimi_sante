import type { ReactNode } from 'react';

/**
 * Briques de visualisation du Dashboard Governance.
 *
 * Palette catégorielle = Okabe–Ito, référence reconnue pour la lisibilité en vision des
 * couleurs déficiente (deutéranopie / protanopie / tritanopie). Assignée dans un ordre fixe,
 * jamais cyclée : au-delà de 7 séries, tout est replié sur "Autre" en gris. Une seule échelle
 * par graphique, grille discrète, info-bulle systématique. L'appli n'a pas de thème sombre —
 * les couleurs sont donc définies une seule fois, pour fond clair.
 *
 * <p><b>Dessiné à la main, sans bibliothèque.</b> Recharts pesait 350 Ko de JavaScript servis
 * à <i>tous</i> les visiteurs du site — le paquet est unique — pour deux graphiques que seule
 * l'administration ouvre. Trois formes suffisaient ici : une aire, des barres horizontales et
 * un entonnoir, que la grille et le SVG natif produisent sans un octet de dépendance. Le
 * contrat des composants est inchangé : l'écran qui les appelle n'a pas bougé.</p>
 *
 * <p>Les info-bulles sont des éléments <code>&lt;title&gt;</code> : c'est le navigateur qui les
 * affiche, et elles restent accessibles au lecteur d'écran. Aucun état, aucun écouteur.</p>
 */
export const CATEGORICAL = [
  '#0072B2', // bleu
  '#D55E00', // vermillon
  '#009E73', // vert (proche de la couleur de marque)
  '#CC79A7', // mauve
  '#E69F00', // ambre
  '#56B4E9', // bleu ciel
  '#666666', // gris (7e série + repli "Autre")
] as const;

export const ACCENT = '#0F766E'; // teal-700 : hue unique pour les graphes à série unique
const INK = '#334155'; // slate-700
const MUTED = '#94a3b8'; // slate-400
const GRID = '#e2e8f0'; // slate-200

export function colorFor(index: number): string {
  return index < CATEGORICAL.length ? CATEGORICAL[index] : CATEGORICAL[CATEGORICAL.length - 1];
}

const euro = (v: number) => `${Number(v).toLocaleString('fr-FR', { maximumFractionDigits: 0 })} €`;
const int = (v: number) => Number(v).toLocaleString('fr-FR');

/**
 * Un plafond d'échelle lisible : 1, 2, 2,5 ou 5 fois une puissance de dix.
 *
 * Sans lui, une série qui culmine à 3 847 € graduerait l'axe en 1 282 € et 2 564 €, des
 * repères que personne ne lit. Avec, elle gradue en 1 000 € et 2 000 €.
 */
function plafond(max: number): number {
  if (!(max > 0)) return 1;
  const puissance = Math.pow(10, Math.floor(Math.log10(max)));
  const reste = max / puissance;
  const arrondi = reste <= 1 ? 1 : reste <= 2 ? 2 : reste <= 2.5 ? 2.5 : reste <= 5 ? 5 : 10;
  return arrondi * puissance;
}

interface CardProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
}

export function ChartCard({ title, subtitle, actions, children }: CardProps) {
  return (
    <div className="bg-white rounded-2xl border border-slate-200 p-5 shadow-sm">
      <div className="flex items-start justify-between gap-3 mb-4">
        <div>
          <h3 className="text-sm font-bold text-slate-800">{title}</h3>
          {subtitle && <p className="text-xs text-slate-500 mt-0.5">{subtitle}</p>}
        </div>
        {actions}
      </div>
      {children}
    </div>
  );
}

/** Le cadre vide, quand la période interrogée ne contient rien. */
function SansDonnees({ hauteur = 260 }: { hauteur?: number }) {
  return (
    <div className="flex items-center justify-center rounded-xl bg-slate-50 text-xs text-slate-400"
         style={{ height: hauteur }}>
      Aucune donnée sur la période.
    </div>
  );
}

interface TrendPoint {
  day: string;
  revenue: number;
  count: number;
}

/** Série temporelle à échelle unique (aire). `mode` choisit la mesure affichée. */
export function TrendArea({ data, mode }: { data: TrendPoint[]; mode: 'revenue' | 'count' }) {
  const isRevenue = mode === 'revenue';
  const fmt = isRevenue ? euro : int;
  const mesure = isRevenue ? 'Revenu' : 'Volume';

  if (!data.length) return <SansDonnees />;

  // Le dessin vit dans son viewBox et c'est la carte qui l'etire : le texte grandit avec lui,
  // ce qui evite d'avoir a mesurer la largeur du conteneur a l'execution.
  const L = 720, H = 260;
  const gaucheAxe = isRevenue ? 68 : 46;
  const bas = H - 26, haut = 10, droite = L - 10;
  const largeurTrace = droite - gaucheAxe;
  const hauteurTrace = bas - haut;

  const valeurs = data.map(p => (isRevenue ? p.revenue : p.count));
  const maxEchelle = plafond(Math.max(...valeurs));
  const x = (i: number) => (data.length === 1
    ? gaucheAxe + largeurTrace / 2
    : gaucheAxe + (i * largeurTrace) / (data.length - 1));
  const y = (v: number) => bas - (Math.max(0, v) / maxEchelle) * hauteurTrace;

  const sommets = valeurs.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  const aire = `M ${x(0).toFixed(1)},${bas} L ${sommets.replace(/ /g, ' L ')} L ${x(data.length - 1).toFixed(1)},${bas} Z`;

  const graduations = [0, 0.25, 0.5, 0.75, 1].map(part => ({
    valeur: maxEchelle * part, y: bas - part * hauteurTrace,
  }));

  // Au-dela de six dates, les etiquettes se chevauchent : on n'en garde qu'une sur n.
  const pas = Math.max(1, Math.ceil(data.length / 6));

  return (
    <svg viewBox={`0 0 ${L} ${H}`} className="w-full" style={{ height: 'auto' }}
         role="img"
         aria-label={`Évolution du ${mesure.toLowerCase()} sur ${data.length} jours, `
           + `maximum ${fmt(Math.max(...valeurs))}`}>
      <defs>
        <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={ACCENT} stopOpacity={0.25} />
          <stop offset="100%" stopColor={ACCENT} stopOpacity={0.02} />
        </linearGradient>
      </defs>

      {graduations.map(g => (
        <g key={g.y}>
          <line x1={gaucheAxe} y1={g.y} x2={droite} y2={g.y} stroke={GRID} strokeWidth={1} />
          <text x={gaucheAxe - 8} y={g.y + 4} textAnchor="end" fontSize={11} fill={MUTED}>
            {fmt(g.valeur)}
          </text>
        </g>
      ))}

      <path d={aire} fill="url(#trendFill)" />
      <polyline points={sommets} fill="none" stroke={ACCENT} strokeWidth={2}
                strokeLinejoin="round" strokeLinecap="round" />

      {/* Une serie d'un ou deux jours ne dessine quasiment pas de ligne : le point, lui,
          reste visible. Au-dela, les marqueurs encombreraient la courbe. */}
      {data.length <= 2 && valeurs.map((v, i) => (
        <circle key={`pt-${i}`} cx={x(i)} cy={y(v)} r={3.5} fill={ACCENT} />
      ))}

      {data.map((p, i) => (
        // Une zone de survol par point, large d'un pas : c'est le navigateur qui affiche
        // l'info-bulle, et le lecteur d'ecran la lit.
        <rect key={p.day} x={x(i) - largeurTrace / (2 * Math.max(1, data.length - 1))}
              y={haut} width={largeurTrace / Math.max(1, data.length - 1)} height={hauteurTrace}
              fill="transparent">
          <title>{`Jour ${p.day} — ${mesure} ${fmt(valeurs[i])}`}</title>
        </rect>
      ))}

      {data.map((p, i) => (i % pas === 0 ? (
        <text key={`e-${p.day}`} x={x(i)} y={H - 8} textAnchor="middle" fontSize={11} fill={MUTED}>
          {p.day?.slice(5)}
        </text>
      ) : null))}
    </svg>
  );
}

interface CatDatum {
  label: string;
  value: number;
  secondary?: number;
}

/**
 * Barres horizontales pour une mesure catégorielle unique. Une seule série => pas de légende
 * (le titre de la carte nomme la mesure), couleur d'accent unique, valeurs en label direct.
 */
export function CategoryBars({
  data,
  unit = 'number',
  colorByCategory = false,
}: {
  data: CatDatum[];
  unit?: 'number' | 'euro';
  colorByCategory?: boolean;
}) {
  const fmt = unit === 'euro' ? euro : int;
  const mesure = unit === 'euro' ? 'Revenu' : 'Volume';
  if (!data.length) return <SansDonnees hauteur={200} />;

  // L'echelle part du plus grand de la serie, pas d'un plafond arrondi : sur des barres, c'est
  // la comparaison entre elles qui porte l'information, pas la lecture d'un axe.
  const max = Math.max(1, ...data.map(d => d.value));

  return (
    <div className="space-y-2">
      {data.map((d, i) => (
        <div key={d.label} className="flex items-center gap-3" title={`${d.label} — ${mesure} ${fmt(d.value)}`}>
          <div className="w-32 shrink-0 truncate text-right text-xs sm:w-40" style={{ color: INK }}>
            {d.label}
          </div>
          <div className="h-8 flex-1 overflow-hidden rounded-lg bg-slate-100">
            <div className="h-full rounded-lg transition-all"
                 style={{
                   width: `${Math.max(2, (d.value / max) * 100)}%`,
                   backgroundColor: colorByCategory ? colorFor(i) : ACCENT,
                 }} />
          </div>
          <div className="w-20 shrink-0 text-xs font-semibold tabular-nums" style={{ color: INK }}>
            {fmt(d.value)}
          </div>
        </div>
      ))}
    </div>
  );
}

interface FunnelDatum {
  label: string;
  count: number;
  shareOfTop: number;
}

/** Entonnoir = barres horizontales décroissantes, largeur ∝ volume, part du sommet en label. */
export function FunnelBars({ steps }: { steps: FunnelDatum[] }) {
  const max = Math.max(1, ...steps.map((s) => s.count));
  return (
    <div className="space-y-2">
      {steps.map((s, i) => (
        <div key={s.label} className="flex items-center gap-3">
          <div className="w-44 shrink-0 text-xs text-slate-600 text-right">{s.label}</div>
          <div className="flex-1 h-8 bg-slate-100 rounded-lg overflow-hidden">
            <div
              className="h-full rounded-lg flex items-center px-2 text-[11px] font-semibold text-white transition-all"
              style={{ width: `${Math.max(4, (s.count / max) * 100)}%`, backgroundColor: colorFor(i) }}
            >
              {int(s.count)}
            </div>
          </div>
          <div className="w-14 shrink-0 text-xs text-slate-400 tabular-nums">{s.shareOfTop}%</div>
        </div>
      ))}
    </div>
  );
}
