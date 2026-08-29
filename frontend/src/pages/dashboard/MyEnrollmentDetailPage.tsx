import React, { useState, useEffect, useRef } from 'react';
import { useParams, Link } from 'react-router-dom';
import { axiosClient } from '../../api/axiosClient';
import { enrollmentService } from '../../api/enrollmentService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { ArrowLeft, UploadCloud, FileText, Loader2, XCircle } from 'lucide-react';
import { Stepper, ENROLLMENT_STEPS } from '../../components/common/Stepper';

export function MyEnrollmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  
  const [enrollment, setEnrollment] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  // Upload state
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [documentType, setDocumentType] = useState<string>('PASSPORT');
  const [uploadProgress, setUploadProgress] = useState(0);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fetchEnrollment();
  }, [id]);

  const fetchEnrollment = async () => {
    setIsLoading(true);
    try {
      const { data } = await axiosClient.get(`/enrollments/${id}`);
      setEnrollment(data);
    } catch (err) {
      setToast({ message: "Impossible de charger ce dossier.", type: 'error' });
    } finally {
      setIsLoading(false);
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      setUploadFile(e.target.files[0]);
    }
  };

  const handleUpload = async () => {
    if (!uploadFile || !id) return;
    
    setIsUploading(true);
    setUploadProgress(0);
    try {
      await enrollmentService.uploadDocument(id, uploadFile, documentType, (percent) => {
        setUploadProgress(percent);
      });
      setToast({ message: 'Document envoyé avec succès !', type: 'success' });
      setUploadFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      fetchEnrollment(); // Refresh data
    } catch (err: any) {
      setToast({ message: err.response?.data?.message || 'Erreur lors de l\'envoi du fichier.', type: 'error' });
    } finally {
      setIsUploading(false);
    }
  };

  if (isLoading || !enrollment) {
    return <div className="min-h-screen flex items-center justify-center"><Loader2 className="w-8 h-8 animate-spin text-brand-green" /></div>;
  }

  const isFailed = enrollment.status === 'REJECTED' || enrollment.status === 'CANCELLED';

  return (
    <div className="min-h-screen bg-slate-50 py-10">
      <div className="container mx-auto px-6 max-w-5xl">
        <Link to="/doctor" className="inline-flex items-center text-sm font-medium text-slate-500 hover:text-brand-dark mb-8 transition-colors">
          <ArrowLeft className="w-4 h-4 mr-2" /> Retour à mes dossiers
        </Link>

        <div className="bg-white rounded-3xl shadow-sm border border-slate-200 p-8 mb-8">
          <div className="flex justify-between items-start mb-8">
            <div>
              <h1 className="text-2xl font-bold text-brand-dark mb-2">Suivi de dossier consulaire</h1>
              <p className="text-slate-500">Formation : <span className="font-semibold text-slate-700">{enrollment.trainingTitle}</span></p>
            </div>
            {isFailed ? (
              <span className="px-4 py-1.5 rounded-full text-sm font-bold bg-rose-50 text-rose-700 border border-rose-200 flex items-center">
                <XCircle className="w-4 h-4 mr-2" /> Dossier {enrollment.status === 'REJECTED' ? 'Rejeté' : 'Annulé'}
              </span>
            ) : (
              <span className="px-4 py-1.5 rounded-full text-sm font-bold bg-blue-50 text-blue-700 border border-blue-200 flex items-center">
                <FileText className="w-4 h-4 mr-2" /> {enrollment.id.substring(0, 8).toUpperCase()}
              </span>
            )}
          </div>

          <Stepper steps={ENROLLMENT_STEPS} currentStepId={enrollment.status} isFailed={isFailed} size="full" />
        </div>

        {/* Upload Zone */}
        <div className="bg-white rounded-3xl shadow-sm border border-slate-200 p-8">
          <h2 className="text-xl font-bold text-brand-dark mb-6">Transmettre un document justificatif</h2>
          
          <div className="max-w-2xl">
            <div className="mb-6">
              <label className="block text-sm font-medium text-slate-700 mb-2">Type de document</label>
              <select 
                value={documentType}
                onChange={(e) => setDocumentType(e.target.value)}
                className="w-full p-3 rounded-xl border border-slate-300 focus:border-brand-green focus:ring-brand-green bg-slate-50"
              >
                <option value="PASSPORT">Passeport (validité &gt; 6 mois)</option>
                <option value="DIPLOMA">Diplôme de docteur en médecine / spécialité</option>
                <option value="MEDICAL_COUNCIL_CERT">Certificat d'inscription à l'Ordre des Médecins</option>
                <option value="FINANCIAL_GUARANTEE">Garantie financière / attestation de bourse</option>
                <option value="VISA_GRANT">Attestation de visa</option>
                <option value="OTHER">Autre pièce justificative</option>
              </select>
            </div>

            <div 
              className={`border-2 border-dashed rounded-2xl p-8 text-center transition-colors ${uploadFile ? 'border-brand-green bg-emerald-50/30' : 'border-slate-300 hover:border-blue-500 bg-slate-50'}`}
            >
              <input 
                type="file" 
                ref={fileInputRef} 
                onChange={handleFileChange} 
                className="hidden" 
                id="file-upload"
              />
              <label htmlFor="file-upload" className="cursor-pointer flex flex-col items-center">
                <UploadCloud className={`w-12 h-12 mb-4 ${uploadFile ? 'text-brand-green' : 'text-slate-400'}`} />
                <span className="font-semibold text-brand-dark mb-1">
                  {uploadFile ? uploadFile.name : 'Cliquez ou glissez-déposez un fichier'}
                </span>
                <span className="text-sm text-slate-500">PDF, JPG, PNG (Max 5Mo)</span>
              </label>
            </div>

            {uploadFile && (
              <div className="mt-6">
                {isUploading ? (
                  <div className="space-y-2">
                    <div className="flex justify-between text-sm font-medium text-slate-600">
                      <span>Envoi en cours...</span>
                      <span>{uploadProgress}%</span>
                    </div>
                    <div className="w-full bg-slate-200 rounded-full h-2">
                      <div className="bg-blue-600 h-2 rounded-full transition-all duration-300" style={{ width: `${uploadProgress}%` }}></div>
                    </div>
                  </div>
                ) : (
                  <button 
                    onClick={handleUpload}
                    className="w-full flex justify-center items-center py-3 bg-brand-green text-white font-bold rounded-xl hover:bg-[#0f3c35] transition-colors"
                  >
                    Confirmer l'envoi du document
                  </button>
                )}
              </div>
            )}
          </div>
        </div>

      </div>
      {toast && (
        <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />
      )}
    </div>
  );
}
