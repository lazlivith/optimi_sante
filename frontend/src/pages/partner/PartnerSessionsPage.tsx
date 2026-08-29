import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  partnerService, type PartnerSessionDto, type CreateSessionRequestDto, type PartnerTrainingDto,
} from '../../api/partnerService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { ProgressBar, type ProgressTone } from '../../components/common/ProgressBar';
import { Loader2, Plus, Calendar, ArrowRight } from 'lucide-react';

function fillTone(fillRatio: number): ProgressTone {
  if (fillRatio >= 95) return 'rose';
  if (fillRatio >= 70) return 'amber';
  return 'emerald';
}

export function PartnerSessionsPage() {
  const [sessions, setSessions] = useState<PartnerSessionDto[]>([]);
  const [approvedTrainings, setApprovedTrainings] = useState<PartnerTrainingDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [trainingInput, setTrainingInput] = useState('');
  const [form, setForm] = useState<Omit<CreateSessionRequestDto, 'trainingId'>>({
    startDate: '', endDate: '', capacity: 10, location: '', price: 0
  });

  const fetchData = async () => {
    setIsLoading(true);
    try {
      const [sessionsData, trainingsData] = await Promise.all([
        partnerService.getMySessions(),
        partnerService.getMyTrainingsDetailed(),
      ]);
      setSessions(sessionsData);
      const approved = trainingsData.filter(t => t.approvalStatus === 'APPROVED');
      setApprovedTrainings(approved);
      if (approved.length > 0 && !trainingInput) {
        setTrainingInput(approved[0].title);
      }
    } catch (err) {
      console.error(err);
      setToast({ message: "Impossible de charger les sessions.", type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const handleCreateSession = async (e: React.FormEvent) => {
    e.preventDefault();
    const matched = approvedTrainings.find(t => t.title === trainingInput);
    if (!matched) {
      setToast({ message: "Aucune formation approuvée ne correspond à ce nom. Sélectionnez-en une dans la liste proposée.", type: 'error' });
      return;
    }
    setIsSubmitting(true);
    try {
      await partnerService.createSession({ ...form, trainingId: matched.id });
      setToast({ message: 'Session créée avec succès.', type: 'success' });
      setForm(f => ({ ...f, startDate: '', endDate: '', capacity: 10, location: '', price: 0 }));
      fetchData();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la création de la session.', type: 'error' });
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader title="Sessions de formation" subtitle="Gérez les sessions ouvertes pour vos formations." />

      <div className="grid lg:grid-cols-3 gap-8">
        {/* Sessions list */}
        <div className="lg:col-span-2 bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden">
          {isLoading ? (
            <div className="p-12 flex justify-center text-slate-400"><Loader2 className="w-8 h-8 animate-spin" /></div>
          ) : sessions.length === 0 ? (
            <EmptyState icon={Calendar} title="Aucune session pour le moment." />
          ) : (
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-slate-500 uppercase bg-slate-50 border-b border-slate-200">
                <tr>
                  <th className="px-6 py-4 font-semibold">Formation</th>
                  <th className="px-6 py-4 font-semibold">Dates</th>
                  <th className="px-6 py-4 font-semibold">Places</th>
                  <th className="px-6 py-4 font-semibold">Statut</th>
                </tr>
              </thead>
              <tbody>
                {sessions.map(s => {
                  const fillRatio = s.capacity > 0 ? ((s.capacity - s.availableSeats) / s.capacity) * 100 : 0;
                  return (
                    <tr key={s.id} className="border-b border-slate-100 hover:bg-slate-50/50 transition-colors">
                      <td className="px-6 py-4 font-medium text-slate-800">{s.trainingTitle}</td>
                      <td className="px-6 py-4 text-slate-500">
                        {new Date(s.startDate).toLocaleDateString('fr-FR')} → {new Date(s.endDate).toLocaleDateString('fr-FR')}
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-2.5">
                          <span className="text-slate-700 font-medium tabular-nums shrink-0">{s.availableSeats} / {s.capacity}</span>
                          <ProgressBar value={fillRatio} tone={fillTone(fillRatio)} />
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <StatusBadge status={s.status} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {/* Create session form */}
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-6">
          <div className="flex items-center gap-3 mb-6 border-b border-slate-100 pb-4">
            <div className="w-10 h-10 bg-brand-light text-brand-green rounded-xl flex items-center justify-center">
              <Plus className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-lg font-bold text-brand-dark">Nouvelle Session</h2>
              <p className="text-sm text-slate-500">Ouvrez des places pour les médecins.</p>
            </div>
          </div>

          {approvedTrainings.length === 0 && !isLoading ? (
            <div className="text-sm text-slate-500 space-y-3">
              <p>Aucune formation approuvée n'est disponible pour le moment. Une session ne peut être ouverte que pour une formation déjà validée par l'administration.</p>
              <Link to="/partner/trainings" className="inline-flex items-center gap-1 font-semibold text-brand-green hover:underline">
                Gérer mes formations <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          ) : (
            <form onSubmit={handleCreateSession} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Formation</label>
                <input
                  required
                  list="approved-trainings-options"
                  value={trainingInput}
                  onChange={e => setTrainingInput(e.target.value)}
                  placeholder="Tapez le nom d'une formation approuvée…"
                  className="w-full rounded-lg border-slate-300 border p-2.5 bg-white focus:ring-brand-green focus:border-brand-green"
                />
                <datalist id="approved-trainings-options">
                  {approvedTrainings.map(t => <option key={t.id} value={t.title} />)}
                </datalist>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Début</label>
                  <input type="date" required value={form.startDate} onChange={e => setForm({ ...form, startDate: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Fin</label>
                  <input type="date" required value={form.endDate} onChange={e => setForm({ ...form, endDate: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Capacité</label>
                  <input type="number" min="1" required value={form.capacity} onChange={e => setForm({ ...form, capacity: parseInt(e.target.value) || 0 })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Prix (EUR)</label>
                  <input type="number" min="0" required value={form.price} onChange={e => setForm({ ...form, price: parseFloat(e.target.value) || 0 })} className="w-full rounded-lg border-slate-300 border p-2.5" />
                </div>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Lieu</label>
                <input type="text" required value={form.location} onChange={e => setForm({ ...form, location: e.target.value })} className="w-full rounded-lg border-slate-300 border p-2.5" />
              </div>

              <button type="submit" disabled={isSubmitting} className="w-full flex items-center justify-center px-6 py-3 bg-brand-dark text-white font-bold rounded-xl hover:bg-slate-800 transition-colors disabled:opacity-70">
                {isSubmitting ? <Loader2 className="w-5 h-5 mr-2 animate-spin" /> : <Plus className="w-5 h-5 mr-2" />}
                Créer la session
              </button>
            </form>
          )}
        </div>
      </div>
      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
