import { useCallback, useEffect, useState } from 'react';
import { CheckCircle2, FileText, Loader2, PackageCheck, Trash2, Upload, XCircle } from 'lucide-react';
import {
  CATEGORIES_ADMIN, CATEGORIES_PARTENAIRE, officialDocumentService,
  type OfficialDocument, type OfficialDocumentCategory,
} from '../../api/officialDocumentService';
import { vaultService } from '../../api/vaultService';
import { FileUploadDropzone } from '../common/FileUploadDropzone';
import { Toast, type ToastType } from '../common/Toast';
import { DocumentButton } from '../documents/DocumentButton';

const STATUT: Record<OfficialDocument['status'], { libelle: string; classe: string }> = {
  PENDING_REVIEW: { libelle: 'À vérifier', classe: 'bg-amber-100 text-amber-800' },
  PUBLISHED: { libelle: 'Publié au coffre-fort', classe: 'bg-success/10 text-success' },
  REJECTED: { libelle: 'Refusé', classe: 'bg-rose-100 text-rose-800' },
};

interface OfficialDocumentsPanelProps {
  enrollmentId: string;
  mode: 'admin' | 'partenaire';
  /** Le CHU ne dépose qu'une fois la candidature acceptée ; la liste reste lisible avant. */
  depotOuvert?: boolean;
}

/**
 * Programme et convention de l'établissement, kit de départ : dépôt, vérification, retrait.
 *
 * <p>Un seul composant pour deux espaces, parce que c'est un seul circuit : le CHU dépose, Optimi
 * Santé vérifie avant que le médecin ne voie quoi que ce soit. Le mode ne fait qu'ouvrir ou
 * fermer des gestes ; les droits sont vérifiés par le serveur.</p>
 */
export function OfficialDocumentsPanel({ enrollmentId, mode, depotOuvert = true }: OfficialDocumentsPanelProps) {
  const admin = mode === 'admin';
  const categories = admin ? CATEGORIES_ADMIN : CATEGORIES_PARTENAIRE;

  const [documents, setDocuments] = useState<OfficialDocument[]>([]);
  const [chargement, setChargement] = useState(true);
  const [categorie, setCategorie] = useState<OfficialDocumentCategory>(categories[0].value);
  const [titre, setTitre] = useState('');
  const [envoi, setEnvoi] = useState(false);
  // Remonte la zone de dépôt après un envoi réussi : sans cela, elle garde le fichier affiché
  // et laisse croire qu'il reste à envoyer.
  const [zoneDepot, setZoneDepot] = useState(0);
  const [actionEnCours, setActionEnCours] = useState<string | null>(null);
  const [refusPour, setRefusPour] = useState<string | null>(null);
  const [motif, setMotif] = useState('');
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const charger = useCallback(async () => {
    try {
      setDocuments(admin
        ? await officialDocumentService.listForAdmin(enrollmentId)
        : await officialDocumentService.listForPartner(enrollmentId));
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Impossible de charger les documents.', type: 'error' });
    } finally {
      setChargement(false);
    }
  }, [admin, enrollmentId]);

  useEffect(() => { charger(); }, [charger]);

  const deposer = async (fichier: File) => {
    setEnvoi(true);
    try {
      if (admin) await officialDocumentService.uploadByAdmin(enrollmentId, fichier, categorie, titre);
      else await officialDocumentService.uploadByPartner(enrollmentId, fichier, categorie, titre);
      setTitre('');
      setZoneDepot((n) => n + 1);
      setToast({
        message: admin
          ? 'Document publié au coffre-fort du médecin.'
          : 'Document envoyé à Optimi Santé pour vérification.',
        type: 'success',
      });
      await charger();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || "Le document n'a pas pu être déposé.", type: 'error' });
    } finally {
      setEnvoi(false);
    }
  };

  const verifier = async (document: OfficialDocument, accepter: boolean) => {
    if (!accepter && !motif.trim()) {
      setToast({ message: "Indiquez le motif du refus : l'établissement doit savoir quoi corriger.", type: 'error' });
      return;
    }
    setActionEnCours(document.id);
    try {
      await officialDocumentService.review(document.id, accepter, accepter ? undefined : motif.trim());
      setToast({
        message: accepter ? 'Document publié au coffre-fort du médecin.' : "Refus transmis à l'établissement.",
        type: 'success',
      });
      setRefusPour(null);
      setMotif('');
      await charger();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Action impossible.', type: 'error' });
    } finally {
      setActionEnCours(null);
    }
  };

  const retirer = async (document: OfficialDocument) => {
    if (!window.confirm(`Retirer « ${document.title} » ?`)) return;
    setActionEnCours(document.id);
    try {
      if (admin) await officialDocumentService.deleteByAdmin(document.id);
      else await officialDocumentService.deleteByPartner(document.id);
      await charger();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Retrait impossible.', type: 'error' });
    } finally {
      setActionEnCours(null);
    }
  };

  const aVerifier = documents.filter((d) => d.status === 'PENDING_REVIEW').length;

  return (
    <section className="bg-white rounded-3xl shadow-sm border border-slate-200 overflow-hidden" aria-labelledby={`officiels-${enrollmentId}`}>
      <div className="p-6 border-b border-slate-100 bg-slate-50 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <PackageCheck className="w-5 h-5 text-brand" aria-hidden="true" />
          <div>
            <h2 id={`officiels-${enrollmentId}`} className="text-xl font-bold text-brand-dark">
              {admin ? 'Documents officiels & kit de départ' : 'Documents de formation'}
            </h2>
            <p className="text-xs text-slate-500 mt-0.5">
              {admin
                ? "Programme et convention du CHU à vérifier ; kit de départ débloqué au médecin après le solde."
                : 'Programme officiel et convention : vérifiés par Optimi Santé avant d’arriver au coffre-fort du médecin.'}
            </p>
          </div>
        </div>
        {admin && aVerifier > 0 && (
          <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-800">
            {aVerifier} à vérifier
          </span>
        )}
      </div>

      {depotOuvert && (
        <div className="p-6 border-b border-slate-100 space-y-3">
          <div className="flex items-center gap-2 text-sm font-bold text-slate-700">
            <Upload className="w-4 h-4 text-slate-400" aria-hidden="true" /> Déposer un document
          </div>
          <div className="grid gap-3 sm:grid-cols-2">
            <div>
              <label htmlFor={`categorie-${enrollmentId}`} className="block text-xs font-medium text-slate-600 mb-1">Nature</label>
              <select
                id={`categorie-${enrollmentId}`}
                value={categorie}
                onChange={(e) => setCategorie(e.target.value as OfficialDocumentCategory)}
                className="w-full rounded-lg border-slate-300 border p-2 text-sm bg-white focus:ring-brand focus:border-brand"
              >
                {categories.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
              </select>
            </div>
            <div>
              <label htmlFor={`titre-${enrollmentId}`} className="block text-xs font-medium text-slate-600 mb-1">
                Titre <span className="text-slate-400">(facultatif)</span>
              </label>
              <input
                id={`titre-${enrollmentId}`}
                value={titre}
                onChange={(e) => setTitre(e.target.value)}
                maxLength={160}
                placeholder="Ex. Programme Échographie clinique 2026"
                className="w-full rounded-lg border-slate-300 border p-2 text-sm focus:ring-brand focus:border-brand"
              />
            </div>
          </div>
          <FileUploadDropzone
            key={zoneDepot}
            label="Glissez-déposez le document (PDF, JPG ou PNG)"
            acceptedTypes={['application/pdf', 'image/jpeg', 'image/png']}
            maxSizeMb={10}
            isLoading={envoi}
            onFileSelect={deposer}
          />
        </div>
      )}

      {chargement ? (
        <div className="p-10 flex justify-center"><Loader2 className="w-6 h-6 animate-spin text-brand" aria-label="Chargement" /></div>
      ) : documents.length === 0 ? (
        <p className="p-6 text-sm text-slate-500">
          {admin ? "Aucun document officiel sur ce dossier." : "Aucun document déposé pour ce dossier."}
        </p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {documents.map((d) => (
            <li key={d.id} className="px-6 py-4 space-y-3">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex gap-3 min-w-0">
                  <FileText className="w-5 h-5 text-slate-400 shrink-0 mt-0.5" aria-hidden="true" />
                  <div className="min-w-0">
                    <p className="font-semibold text-brand-dark">{d.title}</p>
                    <p className="text-xs text-slate-500">
                      {d.categoryLabel !== d.title && `${d.categoryLabel} · `}{d.issuer === 'PARTNER' ? "Déposé par l'établissement" : 'Déposé par Optimi Santé'}
                      {' · '}{new Date(d.uploadedAt).toLocaleDateString('fr-FR')}
                    </p>
                    {d.status === 'REJECTED' && d.rejectionReason && (
                      <p className="text-xs text-rose-700 mt-1">Motif : {d.rejectionReason}</p>
                    )}
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${STATUT[d.status].classe}`}>
                    {STATUT[d.status].libelle}
                  </span>
                  <DocumentButton
                    libelle="Ouvrir"
                    obtenirLien={() => vaultService.getPresignedUrl('OFFICIAL_DOCUMENT', d.id)}
                  />
                  {(admin || (d.issuer === 'PARTNER' && d.status !== 'PUBLISHED')) && (
                    <button
                      type="button" onClick={() => retirer(d)} disabled={actionEnCours === d.id}
                      className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 hover:text-rose-600 disabled:opacity-50"
                    >
                      <Trash2 className="w-3.5 h-3.5" aria-hidden="true" /> Retirer
                    </button>
                  )}
                </div>
              </div>

              {admin && d.status === 'PENDING_REVIEW' && (
                refusPour === d.id ? (
                  <div className="pl-8 space-y-2">
                    <label htmlFor={`motif-${d.id}`} className="block text-xs font-medium text-slate-600">
                      Motif du refus, transmis à l'établissement
                    </label>
                    <textarea
                      id={`motif-${d.id}`} value={motif} onChange={(e) => setMotif(e.target.value)} rows={2}
                      className="w-full rounded-lg border-slate-300 border p-2 text-sm focus:ring-brand focus:border-brand"
                    />
                    <div className="flex gap-2">
                      <button
                        type="button" onClick={() => verifier(d, false)} disabled={actionEnCours === d.id}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600 text-white text-xs font-semibold hover:bg-rose-700 disabled:opacity-60"
                      >
                        {actionEnCours === d.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <XCircle className="w-3.5 h-3.5" />}
                        Confirmer le refus
                      </button>
                      <button
                        type="button" onClick={() => { setRefusPour(null); setMotif(''); }}
                        className="px-3 py-1.5 rounded-lg border border-slate-300 text-xs font-medium text-slate-700 hover:bg-slate-50"
                      >
                        Annuler
                      </button>
                    </div>
                  </div>
                ) : (
                  <div className="pl-8 flex flex-wrap gap-2">
                    <button
                      type="button" onClick={() => verifier(d, true)} disabled={actionEnCours === d.id}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-brand text-white text-xs font-semibold hover:bg-brand-fonce disabled:opacity-60"
                    >
                      {actionEnCours === d.id ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <CheckCircle2 className="w-3.5 h-3.5" />}
                      Valider et publier au coffre-fort
                    </button>
                    <button
                      type="button" onClick={() => { setRefusPour(d.id); setMotif(''); }}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-rose-200 text-rose-700 text-xs font-semibold hover:bg-rose-50"
                    >
                      <XCircle className="w-3.5 h-3.5" /> Refuser
                    </button>
                  </div>
                )
              )}
            </li>
          ))}
        </ul>
      )}

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </section>
  );
}
