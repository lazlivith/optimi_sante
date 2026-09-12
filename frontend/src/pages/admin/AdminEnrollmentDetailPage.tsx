import { useState, useEffect } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { axiosClient } from '../../api/axiosClient';
import { adminService } from '../../api/adminService';
import { vaultService, getDocumentLabel, type DocumentItemDto } from '../../api/vaultService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { StatusBadge, getStatusLabel } from '../../components/common/StatusBadge';
import { Stepper, ENROLLMENT_STEPS } from '../../components/common/Stepper';
import { EmptyState } from '../../components/common/EmptyState';
import { FileUploadDropzone } from '../../components/common/FileUploadDropzone';
import { AdminDocumentRequestsPanel } from '../../components/enrollment/AdminDocumentRequestsPanel';
import { AdminInterviewPanel } from '../../components/enrollment/AdminInterviewPanel';
import { ArrowLeft, Loader2, CheckCircle, FileText, Lock, FileSignature, Stamp, Upload, Send, AlertTriangle, Trash2 } from 'lucide-react';
import { DocumentButton } from '../../components/documents/DocumentButton';

const STEPS = ENROLLMENT_STEPS.map(s => s.id);

const OPTIONAL_DOCUMENT_TYPES = [
  { value: 'CONSULAR_LETTER', label: "Lettre d'Accompagnement Consulaire" },
  { value: 'ACCOMMODATION_PROOF', label: "Attestation d'Hébergement" },
];

export function AdminEnrollmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { user, isAuthenticated } = useAuth();
  
  const [enrollment, setEnrollment] = useState<any>(null);
  const [documents, setDocuments] = useState<DocumentItemDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isProcessing, setIsProcessing] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);
  const [optionalDocType, setOptionalDocType] = useState(OPTIONAL_DOCUMENT_TYPES[0].value);
  const [isUploadingOptionalDoc, setIsUploadingOptionalDoc] = useState(false);

  useEffect(() => {
    if (isAuthenticated && (user?.role === 'ADMIN' || user?.role === 'SUPER_ADMIN')) {
      fetchEnrollmentData();
    }
  }, [id, isAuthenticated, user]);

  const fetchEnrollmentData = async () => {
    setIsLoading(true);
    try {
      const { data: enrollmentData } = await axiosClient.get(`/admin/enrollments/${id}`);
      setEnrollment(enrollmentData);

      const { data: docsData } = await axiosClient.get(`/admin/enrollments/${id}/documents`);
      setDocuments(docsData);
    } catch (err) {
      console.error('Erreur lors du chargement du dossier', err);
      setToast({ message: "Impossible de charger ce dossier.", type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleUpdateStatus = async (newStatus: string) => {
    if (!id) return;
    setIsProcessing(true);
    try {
      await adminService.updateEnrollmentStatus(id, newStatus);
      setToast({ message: 'Statut mis à jour avec succès.', type: 'success' });
      fetchEnrollmentData();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la mise à jour.', type: 'error' });
    } finally {
      setIsProcessing(false);
    }
  };

  /** Pré-qualification validée : le dossier part au CHU, qui statuera sur l'admission. */
  const handleSubmitToPartner = async () => {
    if (!id) return;
    setIsProcessing(true);
    try {
      await adminService.submitEnrollmentToPartner(id);
      setToast({ message: "Dossier transmis à l'établissement. Le médecin en est informé.", type: 'success' });
      fetchEnrollmentData();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la transmission.', type: 'error' });
    } finally {
      setIsProcessing(false);
    }
  };

  /**
   * Renvoi au médecin pour correction. Le motif est saisi ici puis affiché tel quel dans
   * son espace et dans l'e-mail qu'il reçoit : sans lui, il saurait que son dossier est
   * bloqué sans savoir quelle pièce reprendre.
   */
  const handleRequestAction = async () => {
    if (!id) return;
    const note = window.prompt(
      'Quelle pièce doit être corrigée ?\n\n'
      + 'Ce texte est envoyé au médecin par e-mail et affiché dans son espace.\n'
      + "Exemple : « Le diplôme est illisible, merci de redéposer un scan couleur. »",
    );
    if (note === null) return;
    if (!note.trim()) {
      setToast({ message: 'Un motif est obligatoire pour demander une correction.', type: 'error' });
      return;
    }
    setIsProcessing(true);
    try {
      await adminService.requestEnrollmentAction(id, note.trim());
      setToast({ message: 'Demande envoyée au médecin.', type: 'success' });
      fetchEnrollmentData();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || "Erreur lors de l'envoi.", type: 'error' });
    } finally {
      setIsProcessing(false);
    }
  };

  /** Retrait definitif. Le serveur refuse au-dela de la revue OptimiSante, quoi qu'affiche l'UI. */
  const handleDelete = async () => {
    if (!id) return;
    if (!window.confirm(
      "Supprimer définitivement ce dossier ?\n\n"
      + "Les pièces déposées seront effacées et la place rendue à la session. "
      + "Cette action est irréversible.",
    )) return;
    setIsProcessing(true);
    try {
      await adminService.deleteEnrollment(id);
      navigate('/admin/enrollments');
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Suppression impossible.', type: 'error' });
      setIsProcessing(false);
    }
  };

  const handleGenerateConvention = async () => {
    if (!id) return;
    setIsProcessing(true);
    try {
      await adminService.generateConvention(id);
      setToast({ message: 'Convention générée avec succès.', type: 'success' });
      fetchEnrollmentData();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la génération.', type: 'error' });
    } finally {
      setIsProcessing(false);
    }
  };

  const handleGenerateAttestation = async () => {
    if (!id) return;
    setIsProcessing(true);
    try {
      await adminService.generateAttestation(id);
      setToast({ message: "Attestation d'accueil générée avec succès.", type: 'success' });
      fetchEnrollmentData();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de la génération.', type: 'error' });
    } finally {
      setIsProcessing(false);
    }
  };

  const handleUploadOptionalDocument = async (file: File) => {
    if (!id) return;
    setIsUploadingOptionalDoc(true);
    try {
      await adminService.uploadEnrollmentDocument(id, file, optionalDocType);
      setToast({ message: 'Document ajouté au dossier.', type: 'success' });
      fetchEnrollmentData();
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || "Erreur lors de l'envoi.", type: 'error' });
    } finally {
      setIsUploadingOptionalDoc(false);
    }
  };

  if (isLoading || !enrollment) {
    return <div className="min-h-screen flex items-center justify-center"><Loader2 className="w-8 h-8 animate-spin text-brand" /></div>;
  }

  const currentIndex = STEPS.findIndex(s => s === enrollment.status);
  const nextStep = currentIndex >= 0 && currentIndex < STEPS.length - 1 ? STEPS[currentIndex + 1] : null;
  // Étape d'instruction OptimiSanté : c'est la seule où l'admin arbitre entre transmettre
  // au CHU et renvoyer le dossier au médecin. Ailleurs, l'avancement reste linéaire.
  const isUnderReview = enrollment.status === 'UNDER_OPTIMI_REVIEW';
  // Meme regle que le serveur : passe la transmission au CHU, le dossier engage un tiers
  // et ne peut plus disparaitre — seule l'annulation motivee reste ouverte.
  const isDeletable = ['UNDER_OPTIMI_REVIEW', 'ACTION_REQUIRED'].includes(enrollment.status);

  return (
    <div className="min-h-screen bg-slate-50 py-10">
      <div className="container mx-auto px-6 max-w-6xl">
        <Link to="/admin/enrollments" className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-8 transition-colors">
          <ArrowLeft className="w-4 h-4 mr-2" /> Retour à la liste
        </Link>

        <div className="grid lg:grid-cols-3 gap-8">
          {/* Pilotage Column */}
          <div className="lg:col-span-2 space-y-8">
            <div className="bg-white rounded-3xl shadow-sm border border-slate-200 p-8">
              <div className="flex justify-between items-start mb-2">
                <div>
                  <h1 className="text-2xl font-bold text-brand-dark mb-1">Dossier Mobilité</h1>
                  <p className="text-slate-500 font-medium">{enrollment.doctorName}</p>
                </div>
                <span className="bg-slate-100 text-slate-700 font-mono text-xs px-3 py-1.5 rounded border border-slate-200">
                  ID: {enrollment.id.substring(0, 8)}
                </span>
              </div>

              <div className="mb-4">
                <StatusBadge status={enrollment.status} />
              </div>

              {!['REJECTED', 'CANCELLED'].includes(enrollment.status) && (
                <Stepper steps={ENROLLMENT_STEPS} currentStepId={enrollment.status} size="full" />
              )}

              {enrollment.actionRequiredNote && (
                <div className="mb-6 rounded-2xl border border-amber-200 bg-amber-50 p-4">
                  <p className="text-xs font-bold uppercase tracking-wide text-amber-700 mb-1">
                    Correction demandée
                  </p>
                  <p className="text-sm text-amber-900 whitespace-pre-line">{enrollment.actionRequiredNote}</p>
                </div>
              )}

              <div className="flex flex-wrap gap-4 border-t border-slate-100 pt-6">
                {isUnderReview && (
                  <>
                    <button
                      onClick={handleSubmitToPartner}
                      disabled={isProcessing}
                      className="flex items-center px-6 py-3 bg-brand text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors disabled:opacity-50"
                    >
                      {isProcessing ? <Loader2 className="w-5 h-5 mr-2 animate-spin" /> : <Send className="w-5 h-5 mr-2" />}
                      Valider et transmettre au CHU
                    </button>
                    <button
                      onClick={handleRequestAction}
                      disabled={isProcessing}
                      className="flex items-center px-6 py-3 bg-amber-500 text-white font-bold rounded-xl hover:bg-amber-600 transition-colors disabled:opacity-50"
                    >
                      <AlertTriangle className="w-5 h-5 mr-2" />
                      Demander une correction
                    </button>
                  </>
                )}

                {isDeletable && (
                  <button
                    onClick={handleDelete}
                    disabled={isProcessing}
                    className="flex items-center px-6 py-3 bg-white text-rose-600 border border-rose-200 font-bold rounded-xl hover:bg-rose-50 transition-colors disabled:opacity-50"
                  >
                    <Trash2 className="w-5 h-5 mr-2" />
                    Supprimer le dossier
                  </button>
                )}

                {nextStep && !isUnderReview && (
                  <button
                    onClick={() => handleUpdateStatus(nextStep)}
                    disabled={isProcessing}
                    className="flex items-center px-6 py-3 bg-blue-600 text-white font-bold rounded-xl hover:bg-blue-700 transition-colors disabled:opacity-50"
                  >
                    {isProcessing ? <Loader2 className="w-5 h-5 mr-2 animate-spin" /> : <CheckCircle className="w-5 h-5 mr-2" />}
                    Passer à « {getStatusLabel(nextStep)} »
                  </button>
                )}
                
                {enrollment.status === 'CONFIRMED' && (
                  <>
                    <button
                      onClick={handleGenerateConvention}
                      disabled={isProcessing}
                      className="flex items-center px-6 py-3 bg-brand text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors disabled:opacity-50"
                    >
                      {isProcessing ? <Loader2 className="w-5 h-5 mr-2 animate-spin" /> : <FileSignature className="w-5 h-5 mr-2" />}
                      Générer Convention Tripartite
                    </button>
                    <button
                      onClick={handleGenerateAttestation}
                      disabled={isProcessing}
                      className="flex items-center px-6 py-3 bg-indigo-600 text-white font-bold rounded-xl hover:bg-indigo-700 transition-colors disabled:opacity-50"
                    >
                      {isProcessing ? <Loader2 className="w-5 h-5 mr-2 animate-spin" /> : <Stamp className="w-5 h-5 mr-2" />}
                      Générer Attestation d'Accueil
                    </button>
                  </>
                )}
              </div>

              {(enrollment.conventionS3Key || enrollment.attestationS3Key) && (
                <div className="flex gap-4 pt-4">
                  {enrollment.conventionS3Key && (
                    <DocumentButton
                      libelle="Voir la convention"
                      obtenirLien={() => vaultService.getPresignedUrl('CONVENTION', enrollment.id)}
                    />
                  )}
                  {enrollment.attestationS3Key && (
                    <DocumentButton
                      libelle="Voir l'attestation"
                      obtenirLien={() => vaultService.getPresignedUrl('ATTESTATION', enrollment.id)}
                    />
                  )}
                </div>
              )}
            </div>

            {/* Entretien de selection : c'est ici que la transmission au medecin se decide,
                geste sans lequel les creneaux du CHU restent invisibles au candidat. */}
            <AdminInterviewPanel enrollmentId={enrollment.id} />

            {/* Pieces reclamees au candidat. Place AVANT le coffre-fort : ce qui manque se
                traite avant ce qui est deja la, et c'est de ce panneau que part l'action. */}
            <AdminDocumentRequestsPanel enrollmentId={enrollment.id} />

            {/* Vault Viewer */}
            <div className="bg-white rounded-3xl shadow-sm border border-slate-200 overflow-hidden">
              <div className="p-6 border-b border-slate-100 bg-slate-50 flex items-center gap-3">
                <Lock className="w-5 h-5 text-brand" />
                <h2 className="text-xl font-bold text-brand-dark">Coffre-fort Documentaire</h2>
              </div>

              <div className="p-6 border-b border-slate-100 space-y-3">
                <div className="flex items-center gap-2 text-sm font-bold text-slate-700">
                  <Upload className="w-4 h-4 text-slate-400" /> Ajouter une pièce optionnelle
                </div>
                <select
                  value={optionalDocType}
                  onChange={e => setOptionalDocType(e.target.value)}
                  className="w-full sm:w-72 rounded-lg border-slate-300 border p-2 text-sm bg-white focus:ring-brand focus:border-brand"
                >
                  {OPTIONAL_DOCUMENT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
                </select>
                <FileUploadDropzone
                  label="Glissez-déposez le document"
                  acceptedTypes={['application/pdf', 'image/jpeg', 'image/png']}
                  maxSizeMb={10}
                  isLoading={isUploadingOptionalDoc}
                  onFileSelect={handleUploadOptionalDocument}
                />
              </div>

              {documents.length === 0 ? (
                <EmptyState icon={FileText} title="Aucun document dans le coffre-fort." />
              ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm text-left">
                  <thead className="text-xs text-slate-500 uppercase bg-white border-b border-slate-100">
                    <tr>
                      <th className="px-6 py-4 font-semibold">Document</th>
                      <th className="px-6 py-4 font-semibold">Date</th>
                      <th className="px-6 py-4 font-semibold">Intégrité (SHA-256)</th>
                      <th className="px-6 py-4 font-semibold text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {documents.map((doc) => (
                      <tr key={doc.id} className="border-b border-slate-50 hover:bg-slate-50/50">
                        <td className="px-6 py-4">
                          <div className="flex items-center font-semibold text-brand-dark">
                            <FileText className="w-4 h-4 mr-2 text-slate-400" />
                            {getDocumentLabel(doc.type || doc.title, doc.typeLabel)}
                          </div>
                        </td>
                        <td className="px-6 py-4 text-slate-500">
                          {new Date(doc.date).toLocaleDateString()}
                        </td>
                        <td className="px-6 py-4">
                          {doc.sha256Checksum ? (
                            <code className="font-mono text-xs bg-slate-100 p-1 rounded text-slate-600 truncate max-w-[180px] inline-block" title={doc.sha256Checksum}>
                              {doc.sha256Checksum}
                            </code>
                          ) : (
                            <span className="text-slate-400 italic text-xs">Non calculé</span>
                          )}
                        </td>
                        <td className="px-6 py-4 text-right">
                          <DocumentButton
                            libelle="Ouvrir"
                            obtenirLien={() => vaultService.getPresignedUrl(doc.type, doc.id)}
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              )}
            </div>
          </div>

          {/* Summary Sidebar */}
          <div className="lg:col-span-1">
             <div className="bg-slate-800 text-white rounded-3xl p-8 sticky top-28 shadow-lg">
                <h3 className="text-lg font-bold mb-6 flex items-center">
                  <FileText className="w-5 h-5 mr-3 text-slate-400" /> Informations
                </h3>
                <div className="space-y-4 text-sm">
                  <div>
                    <p className="text-slate-400 mb-1">Formation cible</p>
                    <p className="font-semibold">{enrollment.trainingTitle}</p>
                  </div>
                  <div>
                    <p className="text-slate-400 mb-1">Date de création</p>
                    <p className="font-semibold">{new Date(enrollment.submittedAt).toLocaleDateString()}</p>
                  </div>
                </div>
             </div>
          </div>
        </div>
      </div>
      
      {toast && (
        <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  );
}
