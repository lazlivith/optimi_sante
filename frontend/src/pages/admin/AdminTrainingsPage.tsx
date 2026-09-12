import { useEffect, useState } from 'react';
import { Loader2, GraduationCap, Check, X, Image as ImageIcon, Video, Wallet, Package } from 'lucide-react';
import { adminTrainingService, type AdminTrainingDto } from '../../api/adminTrainingService';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { Toast, type ToastType } from '../../components/common/Toast';
import { AdminServiceCatalogPanel } from '../../components/enrollment/AdminServiceCatalogPanel';

export function AdminTrainingsPage() {
  const [trainings, setTrainings] = useState<AdminTrainingDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [processingId, setProcessingId] = useState<string | null>(null);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  // Formation en cours de revue. La validation passe par cet écran plutôt que par un clic
  // direct : c'est le moment où l'administration examine la proposition du partenaire, et donc
  // le moment où elle décide des frais de dossier. Valider sans rien demander laisserait
  // publier une formation dont personne n'a arbitré le tarif.
  // Formation dont on tient le catalogue de services. Ouvert depuis la liste : c'est en
  // regardant une formation qu'on decide de ce qu'on propose autour d'elle.
  const [catalogueDe, setCatalogueDe] = useState<AdminTrainingDto | null>(null);
  const [enRevue, setEnRevue] = useState<AdminTrainingDto | null>(null);
  const [fraisSaisis, setFraisSaisis] = useState('');

  const fetchTrainings = async () => {
    setIsLoading(true);
    try {
      const data = await adminTrainingService.listTrainings();
      setTrainings(data);
    } catch {
      setToast({ message: 'Impossible de charger les formations.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchTrainings(); }, []);

  const ouvrirRevue = (t: AdminTrainingDto) => {
    setEnRevue(t);
    setFraisSaisis(t.applicationFee != null ? String(t.applicationFee) : '');
  };

  const handleApprove = async (t: AdminTrainingDto, frais: number | null) => {
    setProcessingId(t.id);
    try {
      const updated = await adminTrainingService.approve(t.id, frais);
      setTrainings(prev => prev.map(x => x.id === t.id ? updated : x));
      setToast({ message: `"${t.title}" validée et publiée sur le catalogue.`, type: 'success' });
      setEnRevue(null);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la validation.', type: 'error' });
    } finally {
      setProcessingId(null);
    }
  };

  /**
   * Fixe les frais de dossier de la formation.
   *
   * Un champ vide retire le tarif propre et fait revenir la formation à la valeur globale —
   * distinct de 0, qui rendrait la candidature gratuite. L'invite le dit explicitement, sans
   * quoi l'utilisateur ne peut pas deviner la différence.
   */
  const handleSetFee = async (t: AdminTrainingDto) => {
    const saisi = window.prompt(
      `Frais de dossier pour « ${t.title} », en euros.\n\n`
      + "Laissez vide pour appliquer le tarif par défaut de la plateforme.\n"
      + "Saisissez 0 pour rendre la candidature gratuite.",
      t.applicationFee != null ? String(t.applicationFee) : '');
    if (saisi === null) return;

    const valeur = saisi.trim() === '' ? null : Number(saisi.replace(',', '.'));
    if (valeur !== null && (Number.isNaN(valeur) || valeur < 0)) {
      setToast({ message: 'Montant invalide.', type: 'error' });
      return;
    }

    setProcessingId(t.id);
    try {
      const updated = await adminTrainingService.setApplicationFee(t.id, valeur);
      setTrainings(prev => prev.map(x => (x.id === t.id ? updated : x)));
      setToast({
        message: valeur === null
          ? "Tarif propre retiré : la valeur par défaut s'applique."
          : 'Frais de dossier mis à jour.',
        type: 'success',
      });
    } catch (err: any) {
      setToast({ message: err?.response?.data?.message ?? 'La mise à jour a échoué.', type: 'error' });
    } finally {
      setProcessingId(null);
    }
  };

  const handleReject = async (t: AdminTrainingDto) => {
    const reason = window.prompt(`Motif du rejet de "${t.title}" (visible par le partenaire) :`);
    if (!reason || !reason.trim()) return;
    setProcessingId(t.id);
    try {
      const updated = await adminTrainingService.reject(t.id, reason.trim());
      setTrainings(prev => prev.map(x => x.id === t.id ? updated : x));
      setToast({ message: 'Formation rejetée.', type: 'success' });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors du rejet.', type: 'error' });
    } finally {
      setProcessingId(null);
    }
  };

  const pendingCount = trainings.filter(t => t.approvalStatus === 'PENDING_REVIEW').length;

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Formations des partenaires"
        subtitle={`Validez les formations créées par les CHU avant leur publication sur le catalogue public.${pendingCount > 0 ? ` ${pendingCount} en attente.` : ''}`}
      />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center"><Loader2 className="w-8 h-8 text-brand animate-spin" /></div>
        ) : trainings.length === 0 ? (
          <EmptyState icon={GraduationCap} title="Aucune formation soumise pour le moment." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Formation</th>
                  <th className="px-6 py-4">Partenaire</th>
                  <th className="px-6 py-4">Prix</th>
                  <th className="px-6 py-4">Médias</th>
                  <th className="px-6 py-4">Frais dossier</th>
                  <th className="px-6 py-4">Services</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {trainings.map((t) => (
                  <tr key={t.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="font-semibold text-slate-900">{t.title}</div>
                      <div className="text-xs text-slate-500">{t.medicalSpecialty} · {t.durationDays} j.</div>
                    </td>
                    <td className="px-6 py-4">
                      <div className="text-slate-700">{t.partnerInstitutionName}</div>
                      <div className="text-xs text-slate-400">{t.partnerContactEmail}</div>
                    </td>
                    <td className="px-6 py-4 font-semibold text-slate-900">{t.price.toFixed(2)} €</td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-2 text-slate-400">
                        <ImageIcon className={`w-4 h-4 ${t.imageUrl ? 'text-emerald-600' : ''}`} />
                        <Video className={`w-4 h-4 ${t.videoUrl ? 'text-emerald-600' : ''}`} />
                      </div>
                    </td>
                    {/* Les frais de dossier sont une recette OptimiSanté : ils se règlent ici,
                        et jamais depuis le formulaire du partenaire, qui n'a pas à fixer la
                        rémunération d'un travail qu'il n'effectue pas. */}
                    <td className="px-6 py-4">
                      <button
                        type="button"
                        onClick={() => handleSetFee(t)}
                        disabled={processingId === t.id}
                        className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand-dark hover:text-brand transition-colors disabled:opacity-50"
                        title="Modifier les frais de dossier"
                      >
                        <Wallet className="w-3.5 h-3.5 text-slate-400" />
                        {t.applicationFee != null
                          ? `${t.applicationFee.toFixed(0)} €`
                          : <span className="text-slate-400 font-normal">par défaut</span>}
                      </button>
                    </td>
                    {/* Assurance, hebergement, transport : recette 100 % OptimiSante, donc
                        tarifee ici et jamais depuis l'espace du partenaire. */}
                    <td className="px-6 py-4">
                      <button
                        type="button"
                        onClick={() => setCatalogueDe(t)}
                        className="inline-flex items-center gap-1.5 text-sm font-semibold text-slate-600 hover:text-brand transition-colors"
                      >
                        <Package className="w-3.5 h-3.5 text-slate-400" />
                        Catalogue
                      </button>
                    </td>
                    <td className="px-6 py-4"><StatusBadge status={t.approvalStatus} /></td>
                    <td className="px-6 py-4 text-right">
                      {t.approvalStatus === 'PENDING_REVIEW' && (
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => ouvrirRevue(t)}
                            disabled={processingId === t.id}
                            className="inline-flex items-center justify-center p-2 bg-emerald-100 text-emerald-700 rounded-lg hover:bg-emerald-200 transition disabled:opacity-50"
                            title="Examiner, fixer les frais et publier"
                          >
                            {processingId === t.id ? <Loader2 className="w-4 h-4 animate-spin" /> : <Check className="w-4 h-4" />}
                          </button>
                          <button
                            onClick={() => handleReject(t)}
                            disabled={processingId === t.id}
                            className="inline-flex items-center justify-center p-2 bg-rose-100 text-rose-700 rounded-lg hover:bg-rose-200 transition disabled:opacity-50"
                            title="Rejeter"
                          >
                            <X className="w-4 h-4" />
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Fenêtre de revue : le partenaire propose, l'administration examine et fixe les frais
          avant publication. Les deux gestes sont réunis parce qu'ils forment une seule
          décision — publier une formation, c'est en arrêter le tarif de dossier. */}
      {enRevue && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-2xl w-full max-w-lg shadow-xl border border-slate-200 overflow-hidden">
            <div className="px-6 py-5 border-b border-slate-100 bg-slate-50">
              <h2 className="text-lg font-bold text-brand-dark">Valider et publier</h2>
              <p className="text-sm text-slate-500 mt-0.5">
                Vérifiez la proposition du partenaire, puis fixez les frais de dossier.
              </p>
            </div>

            <div className="px-6 py-5 space-y-4">
              <div className="rounded-xl border border-slate-200 p-4">
                <p className="font-semibold text-brand-dark">{enRevue.title}</p>
                <p className="text-sm text-slate-500 mt-0.5">
                  {enRevue.medicalSpecialty} · {enRevue.durationDays} jours ·{' '}
                  {enRevue.partnerInstitutionName}
                </p>
                <p className="text-sm text-slate-600 mt-2">
                  Prix de la formation, fixé par le partenaire :{' '}
                  <strong className="text-brand-dark">{enRevue.price.toFixed(2)} €</strong>
                </p>
              </div>

              <div>
                <label htmlFor="frais-dossier" className="block text-sm font-semibold text-slate-700 mb-1">
                  Frais de dossier — recette Optimi Santé
                </label>
                <div className="flex items-center gap-2">
                  <input
                    id="frais-dossier" type="number" min="0" step="0.01"
                    value={fraisSaisis}
                    onChange={(e) => setFraisSaisis(e.target.value)}
                    placeholder="Tarif par défaut de la plateforme"
                    className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm focus:border-brand focus:ring-1 focus:ring-brand outline-none"
                  />
                  <span className="text-slate-500 font-semibold">€</span>
                </div>
                {/* Un champ vide et un zéro ne veulent pas dire la même chose : sans cette
                    phrase, personne ne peut deviner la différence. */}
                <p className="text-xs text-slate-500 mt-2">
                  Laissez vide pour appliquer le tarif par défaut de la plateforme.
                  Saisissez <strong>0</strong> pour rendre la candidature gratuite.
                </p>
              </div>
            </div>

            <div className="px-6 py-4 border-t border-slate-100 bg-slate-50 flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setEnRevue(null)}
                className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100 transition-colors"
              >
                Annuler
              </button>
              <button
                type="button"
                disabled={processingId === enRevue.id}
                onClick={() => {
                  const brut = fraisSaisis.trim();
                  const valeur = brut === '' ? null : Number(brut.replace(',', '.'));
                  if (valeur !== null && (Number.isNaN(valeur) || valeur < 0)) {
                    setToast({ message: 'Montant invalide.', type: 'error' });
                    return;
                  }
                  handleApprove(enRevue, valeur);
                }}
                className="inline-flex items-center gap-2 px-5 py-2 rounded-xl bg-brand text-white text-sm font-bold hover:bg-[#0f3c35] disabled:opacity-50 transition-colors"
              >
                {processingId === enRevue.id
                  ? <Loader2 className="w-4 h-4 animate-spin" />
                  : <Check className="w-4 h-4" />}
                Publier la formation
              </button>
            </div>
          </div>
        </div>
      )}

      {catalogueDe && (
        <AdminServiceCatalogPanel
          trainingId={catalogueDe.id}
          trainingTitle={catalogueDe.title}
          onClose={() => setCatalogueDe(null)}
        />
      )}

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
