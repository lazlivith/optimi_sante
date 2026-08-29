import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { axiosClient } from '../../api/axiosClient';
import { adminService } from '../../api/adminService';
import { vaultService, type DocumentItemDto } from '../../api/vaultService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { StatusBadge, getStatusLabel } from '../../components/common/StatusBadge';
import { Stepper, ENROLLMENT_STEPS } from '../../components/common/Stepper';
import { EmptyState } from '../../components/common/EmptyState';
import { FileUploadDropzone } from '../../components/common/FileUploadDropzone';
import { ArrowLeft, Loader2, CheckCircle, FileText, Download, Lock, FileSignature, Stamp, Upload } from 'lucide-react';

const STEPS = ENROLLMENT_STEPS.map(s => s.id);

const OPTIONAL_DOCUMENT_TYPES = [
  { value: 'CONSULAR_LETTER', label: "Lettre d'Accompagnement Consulaire" },
  { value: 'ACCOMMODATION_PROOF', label: "Attestation d'Hébergement" },
];

export function AdminEnrollmentDetailPage() {
  const { id } = useParams<{ id: string }>();
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

  const handleDownloadDocument = async (documentId: string, documentType: string) => {
    try {
      const url = await vaultService.getPresignedUrl(documentType, documentId);
      window.open(url, '_blank');
    } catch (err) {
      setToast({ message: 'Erreur lors de la récupération du lien sécurisé.', type: 'error' });
    }
  };

  if (isLoading || !enrollment) {
    return <div className="min-h-screen flex items-center justify-center"><Loader2 className="w-8 h-8 animate-spin text-brand-green" /></div>;
  }

  const currentIndex = STEPS.findIndex(s => s === enrollment.status);
  const nextStep = currentIndex >= 0 && currentIndex < STEPS.length - 1 ? STEPS[currentIndex + 1] : null;

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

              <div className="flex gap-4 border-t border-slate-100 pt-6">
                {nextStep && (
                  <button
                    onClick={() => handleUpdateStatus(nextStep)}
                    disabled={isProcessing}
                    className="flex items-center px-6 py-3 bg-blue-600 text-white font-bold rounded-xl hover:bg-blue-700 transition-colors disabled:opacity-50"
                  >
                    {isProcessing ? <Loader2 className="w-5 h-5 mr-2 animate-spin" /> : <CheckCircle className="w-5 h-5 mr-2" />}
                    Passer à « {getStatusLabel(nextStep)} »
                  </button>
                )}
                
                {enrollment.status === 'APPROVED_ADMINISTRATIVE' && (
                  <>
                    <button
                      onClick={handleGenerateConvention}
                      disabled={isProcessing}
                      className="flex items-center px-6 py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors disabled:opacity-50"
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
                    <button onClick={() => handleDownloadDocument(enrollment.id, 'CONVENTION')} className="inline-flex items-center text-xs font-semibold text-emerald-700 hover:underline">
                      <Download className="w-3.5 h-3.5 mr-1.5" /> Voir la convention
                    </button>
                  )}
                  {enrollment.attestationS3Key && (
                    <button onClick={() => handleDownloadDocument(enrollment.id, 'ATTESTATION')} className="inline-flex items-center text-xs font-semibold text-indigo-700 hover:underline">
                      <Download className="w-3.5 h-3.5 mr-1.5" /> Voir l'attestation
                    </button>
                  )}
                </div>
              )}
            </div>

            {/* Vault Viewer */}
            <div className="bg-white rounded-3xl shadow-sm border border-slate-200 overflow-hidden">
              <div className="p-6 border-b border-slate-100 bg-slate-50 flex items-center gap-3">
                <Lock className="w-5 h-5 text-brand-green" />
                <h2 className="text-xl font-bold text-brand-dark">Coffre-fort Documentaire</h2>
              </div>

              <div className="p-6 border-b border-slate-100 space-y-3">
                <div className="flex items-center gap-2 text-sm font-bold text-slate-700">
                  <Upload className="w-4 h-4 text-slate-400" /> Ajouter une pièce optionnelle
                </div>
                <select
                  value={optionalDocType}
                  onChange={e => setOptionalDocType(e.target.value)}
                  className="w-full sm:w-72 rounded-lg border-slate-300 border p-2 text-sm bg-white focus:ring-brand-green focus:border-brand-green"
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
                            {doc.title || doc.type}
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
                          <button
                            onClick={() => handleDownloadDocument(doc.id, doc.type)}
                            className="inline-flex items-center justify-center p-2 text-blue-600 hover:bg-blue-50 rounded-lg transition-colors"
                            title="Télécharger / Visualiser sécurisé"
                          >
                            <Download className="w-5 h-5" />
                          </button>
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
