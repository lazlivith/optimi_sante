import React, { useState } from 'react';
import { ShieldCheck, CheckCircle2, Clock, Download, PenTool, ExternalLink, FileCheck2 } from 'lucide-react';

interface SignatureStatus {
  party: 'Médecin / Candidat' | 'Établissement / Financeur' | 'Organisme de Formation';
  isSigned: boolean;
  signatureDate?: string;
}

interface ConventionVaultViewProps {
  conventionId: string;
  pdfUrl: string; // Secure Cloudinary URL
  sha256Hash: string;
  signatures: SignatureStatus[];
  onSign: (partyIndex: number) => void;
  onVerifyIntegrity: () => void;
}

export const ConventionVaultView: React.FC<ConventionVaultViewProps> = ({
  conventionId,
  pdfUrl,
  sha256Hash,
  signatures,
  onSign,
  onVerifyIntegrity,
}) => {
  const [copied, setCopied] = useState(false);

  const handleCopyHash = () => {
    navigator.clipboard.writeText(sha256Hash);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="flex flex-col lg:flex-row gap-6 p-6 max-w-7xl mx-auto min-h-[calc(100vh-4rem)]">
      {/* Panneau Gauche : Statuts & Actions */}
      <div className="w-full lg:w-1/3 flex flex-col gap-6">
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200 p-6">
          <div className="flex items-center gap-3 mb-6 pb-4 border-b border-slate-100">
            <ShieldCheck className="text-blue-600 w-8 h-8" />
            <div>
              <h2 className="text-xl font-semibold text-slate-800">Coffre-Fort Numérique</h2>
              <p className="text-sm text-slate-500">Convention #{conventionId.substring(0, 8).toUpperCase()}</p>
            </div>
          </div>

          <div className="space-y-4 mb-8">
            <h3 className="text-sm font-medium text-slate-700 uppercase tracking-wider">État des signatures</h3>
            {signatures.map((sig, index) => (
              <div key={index} className="flex items-start justify-between p-3 rounded-lg bg-slate-50 border border-slate-100">
                <div>
                  <p className="font-medium text-slate-800 text-sm">{sig.party}</p>
                  <p className="text-xs text-slate-500 mt-0.5">
                    {sig.isSigned ? `Signé le ${sig.signatureDate}` : 'En attente de signature'}
                  </p>
                </div>
                {sig.isSigned ? (
                  <CheckCircle2 className="w-5 h-5 text-emerald-500" />
                ) : (
                  <Clock className="w-5 h-5 text-amber-500" />
                )}
              </div>
            ))}
          </div>

          <div className="bg-slate-900 rounded-xl p-4 mb-8 text-white">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-medium text-slate-400 uppercase tracking-wider">Empreinte SHA-256</span>
              <FileCheck2 className="w-4 h-4 text-emerald-400" />
            </div>
            <div className="font-mono text-xs break-all text-slate-300 bg-slate-800 p-2 rounded-lg border border-slate-700">
              {sha256Hash}
            </div>
            <button 
              onClick={handleCopyHash}
              className="mt-3 text-xs font-medium text-blue-400 hover:text-blue-300 transition-colors w-full text-left"
            >
              {copied ? 'Copié !' : 'Copier l\'empreinte'}
            </button>
          </div>

          <div className="space-y-3">
            <button 
              onClick={() => onSign(0)} // Simulation pour le signataire actuel
              className="w-full flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-700 text-white font-medium py-2.5 px-4 rounded-lg transition-colors"
            >
              <PenTool className="w-4 h-4" />
              Déclencher ma signature
            </button>
            <a 
              href={pdfUrl}
              target="_blank"
              rel="noreferrer"
              className="w-full flex items-center justify-center gap-2 bg-white border border-slate-300 hover:bg-slate-50 text-slate-700 font-medium py-2.5 px-4 rounded-lg transition-colors"
            >
              <Download className="w-4 h-4" />
              Télécharger la convention
            </a>
            <button 
              onClick={onVerifyIntegrity}
              className="w-full flex items-center justify-center gap-2 bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium py-2.5 px-4 rounded-lg transition-colors"
            >
              <ShieldCheck className="w-4 h-4" />
              Vérifier l'intégrité
            </button>
          </div>
        </div>
      </div>

      {/* Panneau Droit : Prévisualisation PDF */}
      <div className="w-full lg:w-2/3 flex flex-col bg-slate-100 rounded-2xl overflow-hidden shadow-sm border border-slate-200">
        <div className="bg-white border-b border-slate-200 p-4 flex justify-between items-center">
          <h3 className="font-medium text-slate-800">Prévisualisation du document</h3>
          <a href={pdfUrl} target="_blank" rel="noreferrer" className="text-blue-600 hover:text-blue-700 flex items-center gap-1 text-sm font-medium">
            Ouvrir dans un nouvel onglet <ExternalLink className="w-4 h-4" />
          </a>
        </div>
        <div className="flex-1 min-h-[600px] w-full relative">
          {pdfUrl ? (
            <iframe 
              src={`${pdfUrl}#toolbar=0`} 
              className="absolute inset-0 w-full h-full border-0"
              title="Prévisualisation Convention"
            />
          ) : (
            <div className="flex h-full items-center justify-center text-slate-500">
              Aucun document disponible
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
