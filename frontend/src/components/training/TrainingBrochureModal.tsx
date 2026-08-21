import React, { useState } from 'react';
import { FileUploadDropzone } from '../common/FileUploadDropzone';
import { trainingService } from '../../api/trainingService';
import { CheckCircle2, AlertCircle } from 'lucide-react';

interface TrainingBrochureModalProps {
  trainingId: string;
  trainingTitle: string;
  isOpen: boolean;
  onClose: () => void;
  onSuccess: (message: string) => void;
}

export const TrainingBrochureModal: React.FC<TrainingBrochureModalProps> = ({
  trainingId,
  trainingTitle,
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [file, setFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleUpload = async () => {
    if (!file) return;

    setIsUploading(true);
    setErrorMessage(null);
    setSuccessMessage(null);

    try {
      const result = await trainingService.uploadBrochure(trainingId, file, (percent) => {
        setProgress(percent);
      });
      setSuccessMessage("Brochure mise à jour avec succès !");
      setTimeout(() => {
        onSuccess(result.message);
        onClose();
      }, 1500);
    } catch (err: any) {
      setErrorMessage(err.response?.data?.message || "Échec de l'envoi du document sur Cloudinary. Réessayez.");
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
      <div className="bg-white rounded-2xl max-w-md w-full p-6 space-y-6 shadow-xl">
        <div>
          <h3 className="text-lg font-bold text-slate-900">Mettre à jour la brochure</h3>
          <p className="text-sm text-slate-500">{trainingTitle}</p>
        </div>

        {successMessage ? (
          <div className="p-8 text-center space-y-4">
            <CheckCircle2 className="w-16 h-16 text-emerald-500 mx-auto" />
            <p className="text-emerald-700 font-medium">{successMessage}</p>
          </div>
        ) : (
          <>
            <FileUploadDropzone
              onFileSelect={(selectedFile) => setFile(selectedFile)}
              isLoading={isUploading}
              uploadProgress={progress}
            />

            {errorMessage && (
              <div className="p-3 bg-rose-50 border border-rose-200 rounded-lg text-rose-700 text-xs flex items-center space-x-2">
                <AlertCircle className="w-4 h-4 shrink-0" />
                <span>{errorMessage}</span>
              </div>
            )}

            <div className="flex justify-end space-x-3 pt-2">
              <button
                type="button"
                onClick={onClose}
                disabled={isUploading}
                className="px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100 rounded-lg transition"
              >
                Annuler
              </button>
              <button
                type="button"
                onClick={handleUpload}
                disabled={!file || isUploading}
                className="px-4 py-2 text-sm font-medium text-white bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 rounded-lg transition flex items-center space-x-2"
              >
                {isUploading ? 'Transfert en cours...' : 'Publier la brochure'}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
};
