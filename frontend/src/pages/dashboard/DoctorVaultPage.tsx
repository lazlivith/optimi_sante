import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { FileText, ShieldCheck, Loader2, Calendar, ChevronRight } from 'lucide-react';
import { vaultService, getDocumentLabel } from '../../api/vaultService';
import type { DocumentItemDto } from '../../api/vaultService';
import { officialDocumentService, type VaultDossier } from '../../api/officialDocumentService';
import { HeroBanner } from '../../components/common/HeroBanner';
import { EmptyState } from '../../components/common/EmptyState';
import { DocumentButton } from '../../components/documents/DocumentButton';
import { CoffreFortDossier } from '../../components/enrollment/CoffreFortDossier';
import { ParcoursDossier } from '../../components/enrollment/ParcoursDossier';

/** Pièces commerciales : la convention et l'attestation figurent désormais dans leur dossier. */
const TYPES_COMMERCIAUX = ['INVOICE', 'QUOTE'];

export function DoctorVaultPage() {
  const [dossiers, setDossiers] = useState<VaultDossier[]>([]);
  const [factures, setFactures] = useState<DocumentItemDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [erreur, setErreur] = useState(false);

  useEffect(() => {
    // Deux sources indépendantes : l'échec de l'une n'efface pas ce que l'autre a chargé.
    Promise.allSettled([officialDocumentService.getMyVault(), vaultService.getDoctorVault()])
      .then(([parDossier, plat]) => {
        if (parDossier.status === 'fulfilled') setDossiers(parDossier.value);
        if (plat.status === 'fulfilled') setFactures(plat.value.filter((d) => TYPES_COMMERCIAUX.includes(d.type)));
        setErreur(parDossier.status === 'rejected' && plat.status === 'rejected');
      })
      .finally(() => setIsLoading(false));
  }, []);

  return (
    <div className="min-h-screen bg-slate-50 py-12 px-4">
      <div className="max-w-5xl mx-auto">
        <HeroBanner
          eyebrow="Espace Médecin"
          title="Mon Coffre-fort Numérique"
          subtitle="Vos documents officiels, dossier par dossier : ce qui est disponible, et ce qui se débloquera à chaque étape."
          icon={ShieldCheck}
        />

        {isLoading ? (
          <div className="flex justify-center items-center h-64">
            <Loader2 className="w-10 h-10 animate-spin text-brand" aria-label="Chargement" />
          </div>
        ) : erreur ? (
          <div className="bg-white rounded-2xl shadow-sm border border-slate-200">
            <EmptyState icon={FileText} title="Coffre-fort indisponible" description="Rechargez la page dans un instant." />
          </div>
        ) : dossiers.length === 0 && factures.length === 0 ? (
          <div className="bg-white rounded-2xl shadow-sm border border-slate-200">
            <EmptyState
              icon={FileText}
              title="Aucun document pour le moment"
              description="Vos conventions, programmes et documents de départ apparaîtront ici au fil de votre parcours."
            />
          </div>
        ) : (
          <div className="space-y-8">
            {dossiers.map((dossier) => (
              <section
                key={dossier.enrollmentId}
                className="bg-white rounded-2xl shadow-sm border border-slate-200 overflow-hidden"
                aria-labelledby={`coffre-${dossier.enrollmentId}`}
              >
                <div className="px-5 py-4 border-b border-slate-100 flex flex-wrap items-center justify-between gap-4">
                  <div className="min-w-0">
                    <h2 id={`coffre-${dossier.enrollmentId}`} className="text-lg font-bold text-brand-dark">
                      {dossier.trainingTitle}
                    </h2>
                    <p className="text-sm text-slate-500">
                      {dossier.institutionName}
                      {` · dossier ${dossier.enrollmentId.substring(0, 8).toUpperCase()}`}
                      {dossier.sessionStart && ` · session du ${new Date(dossier.sessionStart).toLocaleDateString('fr-FR')}`}
                    </p>
                  </div>
                  <div className="flex items-center gap-4">
                    <ParcoursDossier statut={dossier.status} variante="ligne" />
                    <Link
                      to={`/doctor/enrollments/${dossier.enrollmentId}`}
                      className="inline-flex items-center text-sm font-semibold text-brand hover:text-brand-fonce"
                    >
                      Dossier <ChevronRight className="w-4 h-4" aria-hidden="true" />
                    </Link>
                  </div>
                </div>
                <CoffreFortDossier dossier={dossier} />
              </section>
            ))}

            {factures.length > 0 && (
              <section aria-labelledby="coffre-factures">
                <h2 id="coffre-factures" className="text-lg font-bold text-brand-dark mb-4">Factures et devis</h2>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                  {factures.map((doc) => (
                    <div key={doc.id} className="bg-white rounded-2xl shadow-sm border border-slate-200 p-6 flex flex-col">
                      <span className="self-start px-2 py-1 rounded-full text-xs font-semibold bg-brand-light text-brand mb-3">
                        {getDocumentLabel(doc.type, doc.typeLabel)}
                      </span>
                      <h3 className="text-base font-bold text-slate-900 mb-2 line-clamp-2" title={doc.title}>{doc.title}</h3>
                      <div className="flex items-center text-sm text-slate-500 mb-5 mt-auto">
                        <Calendar className="w-4 h-4 mr-2" aria-hidden="true" />
                        {new Date(doc.date).toLocaleDateString('fr-FR')}
                      </div>
                      <DocumentButton
                        libelle="Télécharger"
                        variante="bouton"
                        obtenirLien={() => vaultService.getPresignedUrl(doc.type.toLowerCase(), doc.id)}
                        className="w-full justify-center rounded-xl py-2.5"
                      />
                    </div>
                  ))}
                </div>
              </section>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
