export type BadgeTone = 'amber' | 'emerald' | 'blue' | 'purple' | 'rose' | 'slate';

const TONE_CLASSES: Record<BadgeTone, string> = {
  amber: 'bg-amber-100 text-amber-800',
  emerald: 'bg-emerald-100 text-emerald-800',
  blue: 'bg-blue-100 text-blue-800',
  purple: 'bg-purple-100 text-purple-800',
  rose: 'bg-rose-100 text-rose-800',
  slate: 'bg-slate-100 text-slate-700',
};

const DOT_CLASSES: Record<BadgeTone, string> = {
  amber: 'bg-amber-500',
  emerald: 'bg-emerald-500',
  blue: 'bg-blue-500',
  purple: 'bg-purple-500',
  rose: 'bg-rose-500',
  slate: 'bg-slate-400',
};

interface StatusConfig {
  label: string;
  tone: BadgeTone;
}

/**
 * Registre central des statuts métier connus dans l'application, pour éviter que
 * chaque page redéfinisse ses propres couleurs/libellés (source de divergences visuelles).
 * Un statut absent de ce registre reste affiché correctement grâce au fallback `humanize`.
 */
const STATUS_REGISTRY: Record<string, StatusConfig> = {
  // Candidatures (Enrollment)
  PENDING_REVIEW: { label: 'En attente', tone: 'amber' },
  // Devis / partenariats / formations
  PENDING: { label: 'En attente', tone: 'amber' },
  VALIDATED: { label: 'Validé', tone: 'emerald' },
  APPROVED: { label: 'Validé', tone: 'emerald' },
  APPROVED_ACADEMIC: { label: 'Validée (académique)', tone: 'blue' },
  APPROVED_ADMINISTRATIVE: { label: 'Validée (admin)', tone: 'purple' },
  CONVENTION_ISSUED: { label: 'Convention émise', tone: 'purple' },
  VISA_SUBMITTED: { label: 'Visa soumis', tone: 'blue' },
  VISA_GRANTED: { label: 'Visa obtenu', tone: 'emerald' },
  READY_TO_START: { label: 'Prête à démarrer', tone: 'emerald' },
  REJECTED: { label: 'Rejetée', tone: 'rose' },
  CANCELLED: { label: 'Annulée', tone: 'rose' },
  // Sessions de formation
  OPEN: { label: 'Ouverte', tone: 'emerald' },
  CLOSED: { label: 'Fermée', tone: 'slate' },
  COMPLETED: { label: 'Terminée', tone: 'blue' },
  // Commandes / paiements
  UNPAID: { label: 'Non payé', tone: 'amber' },
  PAID: { label: 'Payé', tone: 'emerald' },
  PENDING_APPROVAL: { label: 'En validation', tone: 'blue' },
  QUOTE_SENT: { label: 'Devis envoyé', tone: 'amber' },
  QUOTE_REJECTED: { label: 'Devis rejeté', tone: 'rose' },
  // Génériques (codes promo, comptes...)
  ACTIVE: { label: 'Actif', tone: 'emerald' },
  INACTIVE: { label: 'Inactif', tone: 'slate' },
};

function humanize(status: string): string {
  const lower = status.toLowerCase().replace(/_/g, ' ');
  return lower.charAt(0).toUpperCase() + lower.slice(1);
}

/** Libellé français seul (sans le badge), pour un bouton ou un texte libre. */
export function getStatusLabel(status: string): string {
  return STATUS_REGISTRY[status]?.label ?? humanize(status);
}

interface StatusBadgeProps {
  status: string;
  /** Surcharge ponctuelle du libellé/couleur sans toucher au registre global. */
  label?: string;
  tone?: BadgeTone;
}

export function StatusBadge({ status, label, tone }: StatusBadgeProps) {
  const known = STATUS_REGISTRY[status];
  const resolvedTone = tone ?? known?.tone ?? 'slate';
  const resolvedLabel = label ?? known?.label ?? humanize(status);

  return (
    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold ${TONE_CLASSES[resolvedTone]}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${DOT_CLASSES[resolvedTone]}`} />
      {resolvedLabel}
    </span>
  );
}
