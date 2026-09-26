/**
 * Mise en forme des dates d'evenement du blog.
 *
 * Les dates arrivent du serveur au format `AAAA-MM-JJ`, sans heure ni fuseau : elles
 * designent un jour de calendrier, pas un instant. On les decoupe a la main plutot que de
 * les confier a `new Date('2026-10-15')`, que le navigateur interprete en UTC — un visiteur
 * situe a l'ouest de Greenwich verrait alors la veille.
 */

const MOIS = [
  'janvier', 'février', 'mars', 'avril', 'mai', 'juin',
  'juillet', 'août', 'septembre', 'octobre', 'novembre', 'décembre',
];

function decouper(iso: string): { jour: number; mois: number; annee: number } | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!m) return null;
  return { annee: Number(m[1]), mois: Number(m[2]) - 1, jour: Number(m[3]) };
}

/**
 * « 15-18 octobre 2026 », « 28 septembre - 3 octobre 2026 », ou « 15 octobre 2026 ».
 *
 * Le mois et l'annee ne sont repetes que lorsqu'ils changent : un congres de quatre jours
 * n'a pas besoin d'afficher deux fois « octobre 2026 ».
 */
export function formaterPeriode(debut: string | null, fin: string | null): string {
  const d = debut ? decouper(debut) : null;
  if (!d) return '';
  const f = fin ? decouper(fin) : null;

  if (!f || (f.jour === d.jour && f.mois === d.mois && f.annee === d.annee)) {
    return `${d.jour} ${MOIS[d.mois]} ${d.annee}`;
  }
  if (f.annee !== d.annee) {
    return `${d.jour} ${MOIS[d.mois]} ${d.annee} - ${f.jour} ${MOIS[f.mois]} ${f.annee}`;
  }
  if (f.mois !== d.mois) {
    return `${d.jour} ${MOIS[d.mois]} - ${f.jour} ${MOIS[f.mois]} ${d.annee}`;
  }
  return `${d.jour}-${f.jour} ${MOIS[d.mois]} ${d.annee}`;
}

/**
 * Le jour indique est-il derriere nous ?
 *
 * La comparaison porte sur la journee entiere : un evenement qui se tient aujourd'hui est
 * encore d'actualite jusqu'a ce soir, et ne doit pas basculer dans les archives des minuit.
 */
export function estPasse(iso: string | null): boolean {
  const d = iso ? decouper(iso) : null;
  if (!d) return false;
  const aujourdhui = new Date();
  const jourDit = new Date(d.annee, d.mois, d.jour, 23, 59, 59, 999);
  return jourDit.getTime() < aujourdhui.getTime();
}
