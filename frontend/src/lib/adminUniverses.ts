import {
  LayoutDashboard, TrendingUp, ShoppingBag, Package, Tag, FileText,
  GraduationCap, BookOpen, Building2, Mail, Banknote, Users,
  BarChart3, FileSpreadsheet, ScrollText, ShieldCheck, BellRing, Bot,
  type LucideIcon,
} from 'lucide-react';

/**
 * Répartition des deux univers d'administration.
 *
 * Source unique : la sidebar, les gardes de route et la redirection post-connexion lisent
 * tous cette définition. Décrire la répartition à trois endroits distincts garantirait
 * qu'ils divergent — un menu montrerait une entrée qu'une garde refuse, ou l'inverse.
 *
 * ⚠️ Ce fichier ne fait qu'aligner l'interface sur les droits ; il ne les crée pas. La
 * séparation réelle est appliquée par le serveur (annotations @EcommerceAdmin /
 * @MobilityAdmin, migration V30). Masquer une entrée de menu ne protège rien.
 */

export type AdminRole = 'SUPER_ADMIN' | 'ADMIN_ECOMMERCE' | 'ADMIN_MOBILITE' | 'ADMIN';

export interface AdminNavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  end?: boolean;
}

export interface AdminUniverse {
  id: 'ecommerce' | 'mobilite' | 'gouvernance' | 'supervision';
  label: string;
  home: string;
  items: AdminNavItem[];
}

export const ECOMMERCE_UNIVERSE: AdminUniverse = {
  id: 'ecommerce',
  label: 'Négoce',
  home: '/admin/ventes',
  items: [
    { to: '/admin/ventes', label: 'Tableau de bord', icon: LayoutDashboard, end: true },
    // Renommé : « Finance » et « Reversements » cohabitaient sans qu'on puisse les
    // distinguer. Ici il s'agit du chiffre d'affaires de la boutique.
    { to: '/admin/finance', label: 'Chiffre d\'affaires', icon: TrendingUp },
    { to: '/admin/orders', label: 'Commandes', icon: ShoppingBag },
    { to: '/admin/catalog', label: 'Catalogue', icon: Package },
    { to: '/admin/promo-codes', label: 'Codes promo', icon: Tag },
    { to: '/admin/quotes', label: 'Devis B2B', icon: FileText },
  ],
};

export const MOBILITE_UNIVERSE: AdminUniverse = {
  id: 'mobilite',
  label: 'Mobilité',
  home: '/admin/mobilite',
  items: [
    { to: '/admin/mobilite', label: 'Tableau de bord', icon: LayoutDashboard, end: true },
    { to: '/admin/enrollments', label: 'Dossiers CHU', icon: GraduationCap },
    { to: '/admin/trainings', label: 'Formations', icon: BookOpen },
    { to: '/admin/partnership-requests', label: 'Partenariats', icon: Building2 },
    // Renommé pour lever l'ambiguïté avec le chiffre d'affaires du négoce.
    { to: '/admin/payouts', label: 'Reversements CHU', icon: Banknote },
  ],
};

/**
 * Transverse aux deux métiers : le journal des envois relève de la supervision de
 * l'infrastructure, pas d'un métier. Un administrateur du négoce doit pouvoir vérifier qu'un
 * email est bien parti sans dépendre de son homologue mobilité. Univers distinct plutôt que
 * doublon dans les deux : le super admin le verrait alors deux fois dans sa sidebar.
 */
export const SUPERVISION_UNIVERSE: AdminUniverse = {
  id: 'supervision',
  label: 'Supervision',
  home: '/admin/emails',
  items: [
    { to: '/admin/emails', label: 'Emails', icon: Mail },
    // Meme logique que le journal des envois : les alertes operationnelles portent sur
    // l'etat de la plateforme (rapports en echec, commandes impayees, dossiers dormants),
    // pas sur un metier. L'assistant IA est un outil, disponible aux deux univers.
    { to: '/admin/alerts', label: 'Alertes', icon: BellRing },
    { to: '/admin/ai', label: 'Intelligence artificielle', icon: Bot },
  ],
};

export const GOUVERNANCE_UNIVERSE: AdminUniverse = {
  id: 'gouvernance',
  label: 'Gouvernance',
  home: '/admin/users',
  items: [
    { to: '/admin/users', label: 'Comptes', icon: Users },
    // Analytics et rapports croisent le negoce ET la mobilite : les rattacher a l'un des
    // deux metiers donnerait a son administrateur une vue sur les chiffres de l'autre.
    // Ils restent donc ici, avec le journal d'audit et le RGPD, reserves aux roles
    // qui ont deja la vue d'ensemble.
    { to: '/admin/analytics', label: 'Analytics', icon: BarChart3 },
    { to: '/admin/reports', label: 'Rapports', icon: FileSpreadsheet },
    { to: '/admin/audit', label: "Journal d'audit", icon: ScrollText },
    { to: '/admin/governance', label: 'Gouvernance / RGPD', icon: ShieldCheck },
  ],
};

/**
 * Univers accessibles à un rôle, dans l'ordre d'affichage.
 *
 * `ADMIN` est le rôle hérité d'avant la scission : il voit encore tout, le temps que les
 * comptes concernés soient réaffectés (V30).
 */
export function universesFor(role: string | undefined): AdminUniverse[] {
  switch (role) {
    case 'ADMIN_ECOMMERCE':
      return [ECOMMERCE_UNIVERSE, SUPERVISION_UNIVERSE];
    case 'ADMIN_MOBILITE':
      return [MOBILITE_UNIVERSE, SUPERVISION_UNIVERSE];
    case 'SUPER_ADMIN':
    case 'ADMIN':
      return [ECOMMERCE_UNIVERSE, MOBILITE_UNIVERSE, GOUVERNANCE_UNIVERSE, SUPERVISION_UNIVERSE];
    default:
      return [];
  }
}

/** Page d'accueil d'un administrateur après connexion : le tableau de bord de son métier. */
export function adminHomeFor(role: string | undefined): string {
  const [first] = universesFor(role);
  return first ? first.home : '/';
}

/**
 * Rôles autorisés par univers, consommés par les gardes de route de `App.tsx`.
 *
 * Ils vivent ici, à côté de la répartition qu'ils protègent, pour que déplacer une entrée
 * d'un univers à l'autre se voie dans le même fichier que la garde correspondante. Le
 * miroir côté serveur est `@EcommerceAdmin` / `@MobilityAdmin` / `@PlatformAdmin`.
 */
export const ECOM_ROLES: AdminRole[] = ['SUPER_ADMIN', 'ADMIN', 'ADMIN_ECOMMERCE'];
export const MOB_ROLES: AdminRole[] = ['SUPER_ADMIN', 'ADMIN', 'ADMIN_MOBILITE'];
export const GOV_ROLES: AdminRole[] = ['SUPER_ADMIN', 'ADMIN'];
/** Accès au layout d'administration : nécessaire, jamais suffisant. */
export const ALL_ADMIN_ROLES: AdminRole[] = [
  'SUPER_ADMIN', 'ADMIN', 'ADMIN_ECOMMERCE', 'ADMIN_MOBILITE',
];
