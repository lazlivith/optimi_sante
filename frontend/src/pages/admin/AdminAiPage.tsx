import { useQuery } from '@tanstack/react-query';
import { AlertCircle, Bot, CheckCircle2, FileSearch, PenLine } from 'lucide-react';
import { aiService, type DraftTypeSpec } from '../../api/aiService';
import { PageHeader } from '../../components/common/PageHeader';
import { AiDraftButton } from '../../components/ai/AiDraftButton';
import { DocumentExtractionPanel } from '../../components/ai/DocumentExtractionPanel';

/** Contexte d'exemple pour essayer la rédaction sans dossier réel sous la main. */
const SAMPLE_CONTEXT: Record<string, unknown> = {
  medecin: { nom: 'Dr Aya Kouassi', specialite: 'Cardiologie', pays: "Côte d'Ivoire" },
  formation: { titre: 'Cardiologie interventionnelle', etablissement: 'CHU de Bordeaux' },
  session: { debut: '2026-11-02', fin: '2027-01-30' },
  decision: 'ACCEPTEE',
  piecesManquantes: [],
};

export function AdminAiPage() {
  const { data: status, isLoading } = useQuery({
    queryKey: ['ai', 'status'],
    queryFn: aiService.status,
    retry: false,
  });

  const { data: draftTypes = [] } = useQuery<DraftTypeSpec[]>({
    queryKey: ['ai', 'draft-types'],
    queryFn: aiService.draftTypes,
    enabled: !!status?.reachable,
    retry: false,
  });

  const capabilities = status?.worker?.capabilities ?? {};

  return (
    <div className="p-8 space-y-6">
      <PageHeader
        title="Intelligence artificielle"
        subtitle="Analyse automatique des documents et rédaction assistée."
      />

      {/* ------------------------------------------------------------ état */}
      <div className="bg-white rounded-xl border border-slate-200 p-5">
        <div className="flex items-start gap-3">
          <div className="w-9 h-9 rounded-lg bg-slate-100 flex items-center justify-center shrink-0">
            <Bot className="w-4 h-4 text-slate-600" />
          </div>
          <div className="min-w-0 flex-1">
            <h3 className="text-sm font-semibold text-slate-800">État du service</h3>

            {isLoading && <p className="text-xs text-slate-400 mt-1">Vérification…</p>}

            {!isLoading && !status?.reachable && (
              <p className="flex items-start gap-2 text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 mt-2">
                <AlertCircle className="w-4 h-4 shrink-0 mt-0.5" />
                <span>
                  {status?.error ?? 'Service IA injoignable.'} Démarrez le conteneur{' '}
                  <code className="bg-white px-1 rounded">ai</code> puis rechargez.
                </span>
              </p>
            )}

            {!isLoading && status?.reachable && (
              <div className="mt-2 space-y-1.5">
                {Object.entries(capabilities).map(([name, cap]) => (
                  <div key={name} className="flex items-center gap-2 text-xs">
                    {cap.configured ? (
                      <CheckCircle2 className="w-3.5 h-3.5 text-green-600 shrink-0" />
                    ) : (
                      <AlertCircle className="w-3.5 h-3.5 text-amber-500 shrink-0" />
                    )}
                    <span className="font-medium text-slate-700 capitalize w-24">{name}</span>
                    <code className="text-slate-500">{cap.model}</code>
                    {!cap.configured && (
                      <span className="text-amber-700">— clé API manquante, fonction désactivée</span>
                    )}
                  </div>
                ))}
                <p className="text-[11px] text-slate-400 pt-1">
                  Les clés se renseignent dans <code>docker/.env</code> (
                  <code>GEMINI_API_KEY</code>, <code>MISTRAL_API_KEY</code>), puis{' '}
                  <code>docker compose up -d --no-deps ai</code>.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* ------------------------------------------------- extraction documentaire */}
      <section className="space-y-2">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-800">
          <FileSearch className="w-4 h-4 text-brand" />
          Analyse d'un document
        </h2>
        <DocumentExtractionPanel onApply={() => undefined} />
      </section>

      {/* ------------------------------------------------------ rédaction assistée */}
      <section className="space-y-2">
        <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-800">
          <PenLine className="w-4 h-4 text-brand" />
          Rédaction assistée
        </h2>
        <div className="bg-white rounded-xl border border-slate-200 p-5">
          <p className="text-xs text-slate-500 mb-3">
            Chaque bouton produit un texte à partir de données réelles du dossier concerné. Ici,
            un jeu d'exemple permet d'essayer le rendu. Ces boutons s'intègrent ensuite dans les
            écrans Dossiers CHU, Demandes de partenariat et Formations.
          </p>
          {draftTypes.length === 0 && (
            <p className="text-xs text-slate-400">
              Types de brouillons indisponibles (service IA non joignable).
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            {draftTypes.map((t) => (
              <AiDraftButton key={t.key} draftType={t.key} context={SAMPLE_CONTEXT} label={t.label} />
            ))}
          </div>
        </div>
      </section>

      <p className="text-[11px] text-slate-400">
        L'IA ne modifie jamais un dossier d'elle-même : elle propose, un humain valide. Chaque
        analyse et chaque brouillon sont tracés dans le journal d'audit.
      </p>
    </div>
  );
}
