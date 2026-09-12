import { useState, useEffect } from 'react';
import { FileText, ShieldCheck, Loader2, Calendar } from 'lucide-react';
import { vaultService, getDocumentLabel } from '../../api/vaultService';
import type { DocumentItemDto } from '../../api/vaultService';
import { HeroBanner } from '../../components/common/HeroBanner';
import { EmptyState } from '../../components/common/EmptyState';
import { DocumentButton } from '../../components/documents/DocumentButton';

export function DoctorVaultPage() {
  const [documents, setDocuments] = useState<DocumentItemDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchVault();
  }, []);

  const fetchVault = async () => {
    try {
      setIsLoading(true);
      const data = await vaultService.getDoctorVault();
      setDocuments(data);
    } catch (error) {
      console.error("Failed to fetch vault", error);
    } finally {
      setIsLoading(false);
    }
  };

  const getDocIcon = (type: string) => {
    switch(type) {
      case 'CONVENTION': return <ShieldCheck className="w-8 h-8 text-emerald-600" />;
      case 'ATTESTATION': return <ShieldCheck className="w-8 h-8 text-indigo-600" />;
      case 'INVOICE': return <FileText className="w-8 h-8 text-blue-600" />;
      default: return <FileText className="w-8 h-8 text-slate-400" />;
    }
  };

  // Quatrième table de traduction du projet, supprimée : seule la couleur reste locale,
  // parce qu'elle relève de l'habillage. Le texte vient du serveur, via getDocumentLabel.
  const COULEUR_TYPE: Record<string, string> = {
    CONVENTION: 'bg-emerald-100 text-emerald-700',
    ATTESTATION: 'bg-indigo-100 text-indigo-700',
    INVOICE: 'bg-blue-100 text-blue-700',
    QUOTE: 'bg-blue-100 text-blue-700',
  };

  return (
    <div className="min-h-screen bg-slate-50 py-12 px-4">
      <div className="max-w-5xl mx-auto">
        <HeroBanner
          eyebrow="Espace Médecin"
          title="Mon Coffre-fort Numérique"
          subtitle="Retrouvez ici l'ensemble de vos documents administratifs officiels."
          icon={ShieldCheck}
        />

        {isLoading ? (
          <div className="flex justify-center items-center h-64">
            <Loader2 className="w-10 h-10 animate-spin text-indigo-600" />
          </div>
        ) : documents.length === 0 ? (
          <div className="bg-white rounded-2xl shadow-sm border border-slate-200">
            <EmptyState
              icon={FileText}
              title="Aucun document pour le moment"
              description="Vos factures et conventions apparaîtront ici une fois générées."
            />
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
            {documents.map((doc) => (
              <div key={doc.id} className="bg-white rounded-2xl shadow-sm border border-slate-200 p-6 flex flex-col transition hover:shadow-md">
                <div className="flex items-start justify-between mb-4">
                  <div className="p-2 bg-slate-50 rounded-lg">
                    {getDocIcon(doc.type)}
                  </div>
                  <span className={`px-2 py-1 rounded-full text-xs font-semibold ${COULEUR_TYPE[doc.type] || 'bg-slate-100 text-slate-600'}`}>
                    {getDocumentLabel(doc.type, doc.typeLabel)}
                  </span>
                </div>
                
                <h3 className="text-lg font-bold text-slate-900 mb-2 line-clamp-2" title={doc.title}>
                  {doc.title}
                </h3>
                
                <div className="flex items-center text-sm text-slate-500 mb-6 mt-auto">
                  <Calendar className="w-4 h-4 mr-2" />
                  {new Date(doc.date).toLocaleDateString('fr-FR')}
                </div>
                
                <DocumentButton
                  libelle="Télécharger"
                  variante="bouton"
                  obtenirLien={() => vaultService.getPresignedUrl(doc.type.toLowerCase(), doc.id)}
                  className="w-full justify-center !bg-slate-900 hover:!bg-slate-800 rounded-xl py-2.5"
                />
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
