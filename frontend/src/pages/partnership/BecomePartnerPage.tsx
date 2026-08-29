import React, { useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, UploadCloud, CheckCircle2, Building2, ShieldCheck } from 'lucide-react';
import { partnershipService } from '../../api/partnershipService';
import { Toast, type ToastType } from '../../components/common/Toast';

const CONDITIONS = [
  "Structure de santé agréée (numéro FINESS requis), capable d'accueillir des stagiaires cliniques encadrés.",
  "Signature d'une convention tripartite (Optimi Santé · Centre · Stagiaire) pour chaque session.",
  "Engagement de traçabilité et de conformité RGPD/HDS sur les dossiers médicaux transmis.",
  "Validation du dossier par l'équipe Optimi Santé avant provisionnement de l'Espace Centre.",
];

const STEPS = [
  { label: 'Téléchargement de la convention', tone: 'active' as const },
  { label: 'Dépôt du fichier rempli', tone: 'active' as const },
  { label: "Analyse par l'Admin Optimi Santé", tone: 'pending' as const },
  { label: "Accès à l'Espace Centre provisionné", tone: 'success' as const },
];

const STEP_BADGE_STYLES: Record<(typeof STEPS)[number]['tone'], string> = {
  active: 'bg-amber-100 text-amber-700 border border-amber-200',
  pending: 'bg-slate-100 text-slate-500 border border-slate-200',
  success: 'bg-emerald-50 text-brand-green border border-emerald-200',
};

export function BecomePartnerPage() {
  const [isDownloading, setIsDownloading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const formRef = useRef<HTMLDivElement>(null);

  const [form, setForm] = useState({
    institutionName: '', finessAccreditation: '', contactPersonName: '',
    contactEmail: '', contactPhone: '', address: ''
  });

  const handleDownloadTemplate = async () => {
    setIsDownloading(true);
    try {
      const url = await partnershipService.getConventionTemplateUrl();
      window.open(url, '_blank');
    } catch (err) {
      setToast({ message: "Impossible de générer le modèle pour le moment.", type: 'error' });
    } finally {
      setIsDownloading(false);
    }
  };

  const scrollToForm = () => {
    formRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) {
      setToast({ message: 'Merci de joindre la convention signée.', type: 'error' });
      return;
    }
    setIsSubmitting(true);
    try {
      await partnershipService.submitRequest({ ...form, conventionFile: file });
      setSuccess(true);
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || "Erreur lors de l'envoi de votre demande.", type: 'error' });
    } finally {
      setIsSubmitting(false);
    }
  };

  if (success) {
    return (
      <div className="max-w-2xl mx-auto py-20 px-6 text-center">
        <div className="bg-white p-10 rounded-3xl shadow-sm border border-slate-200">
          <CheckCircle2 className="w-16 h-16 text-brand-green mx-auto mb-4" />
          <h1 className="text-3xl font-bold text-brand-dark mb-4">Dossier déposé !</h1>
          <p className="text-slate-600 mb-8">
            Votre dossier de partenariat a bien été transmis à notre équipe. Après analyse, vous recevrez un email
            avec vos identifiants de connexion à l'Espace Centre si votre candidature est retenue.
          </p>
          <Link to="/" className="px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors">
            Retour à l'accueil
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-brand-cream/30 py-16">
      <div className="container mx-auto px-6 max-w-5xl">
        {/* En-tête */}
        <div className="mb-10">
          <p className="text-[11px] font-bold tracking-[0.2em] text-brand-green uppercase mb-3">
            Cliniques, Hôpitaux, CHU
          </p>
          <h1 className="text-3xl md:text-4xl font-bold text-brand-dark mb-3 tracking-tight">
            Devenir centre partenaire
          </h1>
          <p className="text-slate-500 max-w-2xl leading-relaxed">
            Ouvrez une session de formation clinique et accédez à l'Espace Centre après validation de votre convention.
          </p>
        </div>

        {/* Section : conditions + parcours */}
        <div className="grid md:grid-cols-3 gap-6 mb-12">
          <div className="md:col-span-2 bg-white rounded-2xl border border-slate-200 shadow-sm p-8">
            <h2 className="font-bold text-brand-dark mb-6">Conditions &amp; politique de partenariat</h2>
            <ul className="space-y-4 mb-8">
              {CONDITIONS.map((text, i) => (
                <li key={i} className="flex items-start gap-3">
                  <ShieldCheck className="w-5 h-5 text-brand-green shrink-0 mt-0.5" />
                  <span className="text-sm text-slate-600 leading-relaxed">{text}</span>
                </li>
              ))}
            </ul>

            <div className="flex flex-col sm:flex-row gap-3">
              <button
                onClick={handleDownloadTemplate}
                disabled={isDownloading}
                className="inline-flex items-center justify-center px-5 py-3 border border-brand-green text-brand-green font-bold rounded-xl hover:bg-brand-light transition-colors disabled:opacity-50"
              >
                <Download className="w-4 h-4 mr-2" /> {isDownloading ? 'Génération...' : 'Télécharger la convention type (.pdf)'}
              </button>
              <button
                onClick={scrollToForm}
                className="inline-flex items-center justify-center px-5 py-3 bg-brand-dark text-white font-bold rounded-xl hover:bg-slate-800 transition-colors"
              >
                <UploadCloud className="w-4 h-4 mr-2" /> Remplir &amp; déposer mon dossier
              </button>
            </div>
          </div>

          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-8">
            <h2 className="font-bold text-brand-dark mb-6">Parcours du dossier</h2>
            <ol className="space-y-4">
              {STEPS.map((step, i) => (
                <li key={i} className="flex items-center gap-3">
                  <span className={`w-7 h-7 shrink-0 rounded-lg flex items-center justify-center text-xs font-bold ${STEP_BADGE_STYLES[step.tone]}`}>
                    {i + 1}
                  </span>
                  <span className="text-sm text-slate-600">{step.label}</span>
                </li>
              ))}
            </ol>
          </div>
        </div>

        {/* Section : dépôt du dossier */}
        <div ref={formRef} className="mb-10 scroll-mt-24">
          <div className="mb-6 flex items-center gap-3">
            <div className="w-10 h-10 bg-brand-light text-brand-green rounded-xl flex items-center justify-center">
              <Building2 className="w-5 h-5" />
            </div>
            <h2 className="text-xl font-bold text-brand-dark">Déposer votre dossier de candidature</h2>
          </div>

          <form onSubmit={handleSubmit} className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 space-y-5">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-700 mb-1">Nom de l'institution</label>
                <input type="text" required value={form.institutionName} onChange={e => setForm({ ...form, institutionName: e.target.value })} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">N° d'accréditation FINESS</label>
                <input type="text" value={form.finessAccreditation} onChange={e => setForm({ ...form, finessAccreditation: e.target.value })} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Nom du contact référent</label>
                <input type="text" required value={form.contactPersonName} onChange={e => setForm({ ...form, contactPersonName: e.target.value })} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" />
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Email de contact</label>
                <input type="email" required value={form.contactEmail} onChange={e => setForm({ ...form, contactEmail: e.target.value })} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" />
                <p className="text-xs text-slate-400 mt-1">Vos identifiants de connexion seront envoyés à cette adresse en cas de validation.</p>
              </div>
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Téléphone</label>
                <input type="text" required value={form.contactPhone} onChange={e => setForm({ ...form, contactPhone: e.target.value })} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" />
              </div>
              <div className="md:col-span-2">
                <label className="block text-sm font-medium text-slate-700 mb-1">Adresse complète</label>
                <input type="text" required value={form.address} onChange={e => setForm({ ...form, address: e.target.value })} className="w-full rounded-md border-slate-300 shadow-sm p-2.5 border" />
              </div>
            </div>

            <div>
              <label className="block text-sm font-medium text-slate-700 mb-2">Convention de partenariat signée</label>
              <div className={`border-2 border-dashed rounded-2xl p-6 text-center transition-colors ${file ? 'border-brand-green bg-emerald-50/30' : 'border-slate-300 hover:border-brand-green bg-slate-50'}`}>
                <input
                  type="file"
                  id="convention-file"
                  accept=".pdf,.doc,.docx"
                  onChange={e => setFile(e.target.files?.[0] || null)}
                  className="hidden"
                />
                <label htmlFor="convention-file" className="cursor-pointer flex flex-col items-center">
                  <UploadCloud className={`w-10 h-10 mb-3 ${file ? 'text-brand-green' : 'text-slate-400'}`} />
                  <span className="font-semibold text-brand-dark">
                    {file ? file.name : 'Cliquez ou glissez-déposez le fichier'}
                  </span>
                  <span className="text-sm text-slate-500 mt-1">PDF, DOC, DOCX</span>
                </label>
              </div>
            </div>

            <button type="submit" disabled={isSubmitting} className="w-full flex justify-center py-3 px-4 rounded-xl shadow-sm text-sm font-bold text-white bg-brand-green hover:bg-[#0f3c35] disabled:opacity-70 transition-colors">
              {isSubmitting ? 'Envoi en cours...' : 'Envoyer ma demande de partenariat'}
            </button>
          </form>
        </div>
      </div>

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}
