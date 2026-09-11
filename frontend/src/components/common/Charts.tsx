import type { ReactNode } from 'react';
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';

/**
 * Briques de visualisation du Dashboard Governance.
 *
 * Palette catégorielle = Okabe–Ito, référence reconnue pour la lisibilité en vision des
 * couleurs déficiente (deutéranopie / protanopie / tritanopie). Assignée dans un ordre fixe,
 * jamais cyclée : au-delà de 7 séries, tout est replié sur "Autre" en gris. Une seule échelle
 * par graphique, grille discrète, info-bulle systématique. L'appli n'a pas de thème sombre —
 * les couleurs sont donc définies une seule fois, pour fond clair.
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

const tooltipStyle = {
  borderRadius: 12,
  border: '1px solid #e2e8f0',
  fontSize: 12,
  boxShadow: '0 8px 24px rgba(15,23,42,0.08)',
};

interface TrendPoint {
  day: string;
  revenue: number;
  count: number;
}

/** Série temporelle à échelle unique (aire). `mode` choisit la mesure affichée. */
export function TrendArea({ data, mode }: { data: TrendPoint[]; mode: 'revenue' | 'count' }) {
  const isRevenue = mode === 'revenue';
  return (
    <ResponsiveContainer width="100%" height={260}>
      <AreaChart data={data} margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
        <defs>
          <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={ACCENT} stopOpacity={0.25} />
            <stop offset="100%" stopColor={ACCENT} stopOpacity={0.02} />
          </linearGradient>
        </defs>
        <CartesianGrid stroke={GRID} vertical={false} />
        <XAxis
          dataKey="day"
          tick={{ fontSize: 11, fill: MUTED }}
          tickFormatter={(d: string) => d?.slice(5)}
          minTickGap={24}
          axisLine={{ stroke: GRID }}
          tickLine={false}
        />
        <YAxis
          tick={{ fontSize: 11, fill: MUTED }}
          tickFormatter={(v: number) => (isRevenue ? euro(v) : int(v))}
          width={isRevenue ? 64 : 40}
          axisLine={false}
          tickLine={false}
        />
        <Tooltip
          contentStyle={tooltipStyle}
          formatter={(v: number) => [isRevenue ? euro(v) : int(v), isRevenue ? 'Revenu' : 'Volume']}
          labelFormatter={(d: string) => `Jour ${d}`}
        />
        <Area
          type="monotone"
          dataKey={isRevenue ? 'revenue' : 'count'}
          stroke={ACCENT}
          strokeWidth={2}
          fill="url(#trendFill)"
        />
      </AreaChart>
    </ResponsiveContainer>
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
  const height = Math.max(200, data.length * 38 + 24);
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data} layout="vertical" margin={{ top: 0, right: 48, bottom: 0, left: 8 }}>
        <CartesianGrid stroke={GRID} horizontal={false} />
        <XAxis type="number" tick={{ fontSize: 11, fill: MUTED }} tickFormatter={fmt} axisLine={false} tickLine={false} />
        <YAxis
          type="category"
          dataKey="label"
          tick={{ fontSize: 11, fill: INK }}
          width={150}
          axisLine={false}
          tickLine={false}
        />
        <Tooltip contentStyle={tooltipStyle} formatter={(v: number) => [fmt(v), unit === 'euro' ? 'Revenu' : 'Volume']} cursor={{ fill: '#f1f5f9' }} />
        <Bar dataKey="value" radius={[0, 4, 4, 0]} label={{ position: 'right', fontSize: 11, fill: INK, formatter: fmt }}>
          {data.map((_, i) => (
            <Cell key={i} fill={colorByCategory ? colorFor(i) : ACCENT} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
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
