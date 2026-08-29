import { useEffect, useState } from 'react';
import {
  partnerService, type PartnerTrainingDto, type CreateTrainingRequestDto,
} from '../../api/partnerService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { FileUploadDropzone } from '../../components/common/FileUploadDropzone';
import { Loader2, Plus, Pencil, Trash2, GraduationCap, X, Image as ImageIcon, Video, AlertCircle } from 'lucide-react';

const EMPTY_FORM: CreateTrainingRequestDto = {
  title: '', medicalSpecialty: '', description: '', durationDays: 10, isLongStay: false, price: 0,
};

export function PartnerTrainingsPage() {
  const [trainings, setTrainings] = useState<PartnerTrainingDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editing, setEditing] = useState<PartnerTrainingDto | null>(null);
  const [form, setForm] = useState<CreateTrainingRequestDto>(EMPTY_FORM);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploadingImage, setIsUploadingImage] = useState(false);
  const [isUploadingVideo, setIsUploadingVideo] = useState(false);

  const fetchTrainings = async () => {
    setIsLoading(true);
    try {
      const data = await partnerService.getMyTrainingsDetailed();
      setTrainings(data);
    } catch {
      setToast({ message: 'Impossible de charger vos formations.', type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => { fetchTrainings(); }, []);

  const openCreateModal = () => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setIsModalOpen(true);
  };

  const openEditModal = (t: PartnerTrainingDto) => {
    setEditing(t);
    setForm({
      title: t.title, medicalSpecialty: t.medicalSpecialty, description: t.description,
      durationDays: t.durationDays, isLongStay: t.isLongStay, price: t.price,
    });
    setIsModalOpen(true);
  };

  const refreshEditing = (updated: PartnerTrainingDto) => {
    setEditing(updated);
    setTrainings(prev => prev.map(t => t.id === updated.id ? updated : t));
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    try {
      if (editing) {
        const updated = await partnerService.updateTraining(editing.id, form);
        setTrainings(prev => prev.map(t => t.id === editing.id ? updated : t));
        setToast({ message: 'Formation mise à jour — en attente de nouvelle validation par l\'administration.', type: 'success' });
        setIsModalOpen(false);
      } else {
        const created = await partnerService.createTraining(form);
        setTrainings(prev => [created, ...prev]);
        setToast({ message: 'Formation créée — en attente de validation par l\'administration.', type: 'success' });
        // On garde la modale ouverte, en mode édition, pour permettre d'ajouter tout de suite image/vidéo.
        setEditing(created);
      }
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || "Erreur lors de l'enregistrement.", type: 'error' });
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (t: PartnerTrainingDto) => {
    if (!window.confirm(`Supprimer la formation "${t.title}" ? Cette action est irréversible.`)) return;
    try {
      await partnerService.deleteTraining(t.id);
      setTrainings(prev => prev.filter(x => x.id !== t.id));
      setToast({ message: 'Formation supprimée.', type: 'success' });
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Suppression impossible.', type: 'error' });
    }
  };

  const handleImageUpload = async (file: File) => {
    if (!editing) return;
    setIsUploadingImage(true);
    try {
      const updated = await partnerService.uploadTrainingImage(editing.id, file);
      refreshEditing(updated);
      setToast({ message: 'Image ajoutée.', type: 'success' });
    } catch {
      setToast({ message: "Erreur lors de l'upload de l'image.", type: 'error' });
    } finally {
      setIsUploadingImage(false);
    }
  };

  const handleVideoUpload = async (file: File) => {
    if (!editing) return;
    setIsUploadingVideo(true);
    try {
      const updated = await partnerService.uploadTrainingVideo(editing.id, file);
      refreshEditing(updated);
      setToast({ message: 'Vidéo ajoutée.', type: 'success' });
    } catch {
      setToast({ message: "Erreur lors de l'upload de la vidéo.", type: 'error' });
    } finally {
      setIsUploadingVideo(false);
    }
  };

  return (
    <div className="p-8 max-w-6xl">
      <PageHeader
        title="Mes Formations"
        subtitle="Créez vos formations — chaque nouvelle formation ou modification est soumise à la validation de l'administration avant publication."
        actions={
          <button
            onClick={openCreateModal}
            className="inline-flex items-center px-4 py-2.5 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors text-sm"
          >
            <Plus className="w-4 h-4 mr-2" /> Nouvelle formation
          </button>
        }
      />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {isLoading ? (
          <div className="p-12 flex justify-center"><Loader2 className="w-8 h-8 text-brand-green animate-spin" /></div>
        ) : trainings.length === 0 ? (
          <EmptyState
            icon={GraduationCap}
            title="Aucune formation créée pour le moment."
            description="Créez votre première formation pour pouvoir ensuite ouvrir des sessions."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Formation</th>
                  <th className="px-6 py-4">Spécialité</th>
                  <th className="px-6 py-4">Durée</th>
                  <th className="px-6 py-4">Prix</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {trainings.map((t) => (
                  <tr key={t.id} className="hover:bg-slate-50/50 transition-colors">
                    <td className="px-6 py-4">
                      <div className="font-semibold text-brand-dark">{t.title}</div>
                      {t.approvalStatus === 'REJECTED' && t.rejectionReason && (
                        <div className="flex items-start gap-1 text-xs text-rose-600 mt-1 max-w-xs">
                          <AlertCircle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
                          <span>{t.rejectionReason}</span>
                        </div>
                      )}
                    </td>
                    <td className="px-6 py-4 text-slate-600">{t.medicalSpecialty}</td>
                    <td className="px-6 py-4 text-slate-600">{t.durationDays} j.{t.isLongStay ? ' (long séjour)' : ''}</td>
                    <td className="px-6 py-4 font-semibold text-slate-900">{t.price.toFixed(2)} €</td>
                    <td className="px-6 py-4"><StatusBadge status={t.approvalStatus} /></td>
                    <td className="px-6 py-4 text-right space-x-2">
                      <button
                        onClick={() => openEditModal(t)}
                        className="inline-flex items-center justify-center p-2 bg-slate-100 text-slate-600 rounded-lg hover:bg-slate-200 transition"
                        title="Modifier"
                      >
                        <Pencil className="w-4 h-4" />
                      </button>
                      <button
                        onClick={() => handleDelete(t)}
                        className="inline-flex items-center justify-center p-2 bg-rose-100 text-rose-700 rounded-lg hover:bg-rose-200 transition"
                        title="Supprimer"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {isModalOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-sm">
          <div className="bg-white rounded-2xl w-full max-w-lg overflow-hidden shadow-xl border border-slate-200 max-h-[90vh] overflow-y-auto">
            <div className="flex justify-between items-center p-6 border-b border-slate-100 bg-slate-50 sticky top-0">
              <h2 className="text-xl font-bold text-brand-dark">{editing ? 'Modifier la formation' : 'Nouvelle formation'}</h2>
              <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-brand-dark">
                <X className="w-6 h-6" />
              </button>
            </div>
            <form onSubmit={handleSubmit} className="p-6 space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Titre de la formation</label>
                <input type="text" required value={form.title} onChange={e => setForm({ ...form, title: e.target.value })}
                  className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Spécialité médicale</label>
                  <input type="text" required value={form.medicalSpecialty} onChange={e => setForm({ ...form, medicalSpecialty: e.target.value })}
                    className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
                </div>
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Prix (€)</label>
                  <input type="number" step="0.01" min="0" required value={form.price}
                    onChange={e => setForm({ ...form, price: parseFloat(e.target.value) || 0 })}
                    className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
                </div>
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Description</label>
                <textarea rows={3} required value={form.description} onChange={e => setForm({ ...form, description: e.target.value })}
                  className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
              </div>
              <div className="grid grid-cols-2 gap-4 items-end">
                <div>
                  <label className="block text-sm font-medium text-slate-700 mb-1">Durée (jours)</label>
                  <input type="number" min="1" required value={form.durationDays}
                    onChange={e => setForm({ ...form, durationDays: parseInt(e.target.value) || 0 })}
                    className="w-full rounded-md border-slate-300 shadow-sm p-2 border" />
                </div>
                <div className="flex items-center gap-2 pb-2">
                  <input type="checkbox" id="isLongStay" checked={form.isLongStay}
                    onChange={e => setForm({ ...form, isLongStay: e.target.checked })}
                    className="rounded border-slate-300 text-brand-green focus:ring-brand-green" />
                  <label htmlFor="isLongStay" className="text-sm text-slate-700">Séjour longue durée</label>
                </div>
              </div>

              {editing ? (
                <div className="pt-4 border-t border-slate-100 space-y-4">
                  <div>
                    <label className="flex items-center gap-2 text-sm font-bold text-slate-700 mb-2">
                      <ImageIcon className="w-4 h-4 text-brand-green" /> Image d'illustration
                    </label>
                    {editing.imageUrl && (
                      <img src={editing.imageUrl} alt="" className="w-full h-32 object-cover rounded-lg mb-2 border border-slate-200" />
                    )}
                    <FileUploadDropzone
                      label="Glissez-déposez une image"
                      acceptedTypes={['image/jpeg', 'image/png', 'image/webp']}
                      maxSizeMb={5}
                      isLoading={isUploadingImage}
                      onFileSelect={handleImageUpload}
                    />
                  </div>
                  <div>
                    <label className="flex items-center gap-2 text-sm font-bold text-slate-700 mb-2">
                      <Video className="w-4 h-4 text-brand-green" /> Courte vidéo d'illustration
                    </label>
                    {editing.videoUrl && (
                      <video src={editing.videoUrl} controls className="w-full h-32 rounded-lg mb-2 border border-slate-200 bg-black" />
                    )}
                    <FileUploadDropzone
                      label="Glissez-déposez une vidéo"
                      acceptedTypes={['video/mp4', 'video/webm', 'video/quicktime']}
                      maxSizeMb={25}
                      isLoading={isUploadingVideo}
                      onFileSelect={handleVideoUpload}
                    />
                  </div>
                </div>
              ) : (
                <p className="text-xs text-slate-400 pt-2 border-t border-slate-100">
                  L'ajout d'une image et d'une vidéo sera possible juste après la création.
                </p>
              )}

              <div className="pt-4 border-t border-slate-100 flex justify-end gap-3">
                <button type="button" onClick={() => setIsModalOpen(false)} className="px-4 py-2 text-sm font-medium text-slate-600 bg-white border border-slate-300 rounded-lg hover:bg-slate-50">
                  Fermer
                </button>
                <button type="submit" disabled={isSaving} className="flex items-center px-4 py-2 text-sm font-bold text-white bg-brand-green rounded-lg hover:bg-[#0f3c35] disabled:opacity-70">
                  {isSaving && <Loader2 className="w-4 h-4 mr-2 animate-spin" />}
                  {editing ? 'Enregistrer les modifications' : 'Créer la formation'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
