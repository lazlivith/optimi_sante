import React, { useState, useRef, DragEvent, ChangeEvent } from 'react';
import { UploadCloud, FileText, AlertCircle, Loader2, X } from 'lucide-react';

interface FileUploadDropzoneProps {
  onFileSelect: (file: File) => void;
  maxSizeMb?: number;
  acceptedTypes?: string[];
  isLoading?: boolean;
  uploadProgress?: number;
}

export const FileUploadDropzone: React.FC<FileUploadDropzoneProps> = ({
  onFileSelect,
  maxSizeMb = 10,
  acceptedTypes = ['application/pdf'],
  isLoading = false,
  uploadProgress = 0,
}) => {
  const [isDragging, setIsDragging] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const validateAndSetFile = (file: File) => {
    setError(null);
    if (!acceptedTypes.includes(file.type)) {
      setError('Format non supporté. Veuillez sélectionner un fichier PDF.');
      return;
    }
    if (file.size > maxSizeMb * 1024 * 1024) {
      setError(`Le fichier est trop volumineux (maximum ${maxSizeMb} Mo).`);
      return;
    }
    setSelectedFile(file);
    onFileSelect(file);
  };

  const handleDragOver = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setIsDragging(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      validateAndSetFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileChange = (e: ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      validateAndSetFile(e.target.files[0]);
    }
  };

  const clearSelection = () => {
    setSelectedFile(null);
    setError(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  return (
    <div className="w-full space-y-3">
      <div
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        onClick={() => fileInputRef.current?.click()}
        className={`border-2 border-dashed rounded-xl p-6 text-center cursor-pointer transition-all duration-200 ${
          isDragging
            ? 'border-emerald-500 bg-emerald-50/50'
            : 'border-slate-300 hover:border-slate-400 bg-slate-50/50'
        }`}
      >
        <input
          ref={fileInputRef}
          type="file"
          accept={acceptedTypes.join(',')}
          onChange={handleFileChange}
          className="hidden"
        />

        {!selectedFile ? (
          <div className="flex flex-col items-center space-y-2">
            <div className="p-3 bg-emerald-100 text-emerald-700 rounded-full">
              <UploadCloud className="w-8 h-8" />
            </div>
            <p className="text-sm font-medium text-slate-700">
              Glissez-déposez la brochure PDF ici, ou <span className="text-emerald-600 underline">parcourez</span>
            </p>
            <p className="text-xs text-slate-500">PDF jusqu'à {maxSizeMb} Mo</p>
          </div>
        ) : (
          <div className="flex items-center justify-between p-3 bg-white border border-slate-200 rounded-lg">
            <div className="flex items-center space-x-3">
              <FileText className="w-6 h-6 text-emerald-600" />
              <div className="text-left">
                <p className="text-sm font-medium text-slate-800 truncate max-w-[200px]">{selectedFile.name}</p>
                <p className="text-xs text-slate-500">{(selectedFile.size / (1024 * 1024)).toFixed(2)} Mo</p>
              </div>
            </div>

            {isLoading ? (
              <div className="flex items-center space-x-2">
                <Loader2 className="w-5 h-5 text-emerald-600 animate-spin" />
                <span className="text-xs font-semibold text-emerald-600">{uploadProgress}%</span>
              </div>
            ) : (
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  clearSelection();
                }}
                className="p-1 text-slate-400 hover:text-slate-600 rounded-full"
              >
                <X className="w-5 h-5" />
              </button>
            )}
          </div>
        )}
      </div>

      {error && (
        <div className="flex items-center space-x-2 text-rose-600 text-xs">
          <AlertCircle className="w-4 h-4" />
          <span>{error}</span>
        </div>
      )}
    </div>
  );
};
