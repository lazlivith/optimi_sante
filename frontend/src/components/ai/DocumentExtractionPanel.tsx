import { useEffect, useMemo, useRef, useState } from 'react';
import { AlertTriangle, FileSearch, Loader2, Sparkles, Upload } from 'lucide-react';
import {
  aiService,
  fieldLabel,
  type DocumentTypeSpec,
  type ExtractionResult,
} from '../../api/aiService';
import { useToast } from '../../context/ToastContext';

const ACCEPT = '.pdf,.jpg,.jpeg,.png,.webp,.heic,.tif,.tiff';
const MAX_MB = 15;

interface Props {
  /** Type présélectionné (ex. depuis un dossier CHU). Laisser vide pour laisser le choix. */
  defaultType?: string;
  /**
   * Appelé quand l'utilisateur valide les valeurs relues. Sans callback, le panneau se
   * contente d'afficher la proposition (mode démonstration / vérification).
   */
  onApply?: (fields: Record<string, string>, result: ExtractionResult) => void;
}

/**
 * Analyse un document et propose les champs extraits **pour relecture**.
 * Rien n'est enregistré tant que l'utilisateur n'a pas confirmé : l'IA pré-remplit,
 * l'humain valide.
 */
export function DocumentExtractionPanel({ defaultType, onApply }: Props) {
  const { notify } = useToast();
  const fileInput = useRef<HTMLInputElement>(null);

  const [types, setTypes] = useState<DocumentTypeSpec[]>([]);
  const [docType, setDocType] = useState(defaultType ?? 'PASSPORT');
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<ExtractionResult | null>(null);
  const [values, setValues] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    aiService
      .extractionTypes()
      .then(setTypes)
      .catch(() => setTypes([]));
  }, []);

  const lowConfidence = useMemo(
    () =>
      result
        ? Object.entries(result.fields).filter(([, f]) => f.value && f.confidence < 0.6).length
        : 0,
    [result],
  );

  const analyse = async () => {
    if (!file) return;
    if (file.size > MAX_MB * 1024 * 1024) {
      setError(`Fichier trop volumineux (maximum ${MAX_MB} Mo).`);
      return;
    }
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const extraction = await aiService.extract(file, docType);
      setResult(extraction);
      const initial: Record<string, string> = {};
      Object.entries(extraction.fields).forEach(([key, f]) => {
        initial[key] = f.value ?? '';
      });
      setValues(initial);
    } catch (e) {
      const message =
        (e as { response?: { data?: { message?: string } } }).response?.data?.message
        ?? "L'analyse a échoué.";
      setError(message);
    } finally {
      setBusy(false);
    }
  };

  const apply = async () => {
    if (!result) return;
    try {
      await aiService.markApplied(result.jobId);
    } catch {
      /* la trace est un confort : ne bloque pas la validation */
    }
    onApply?.(values, result);
    notify('Valeurs validées et reportées.', 'success');
  };

  return (
    <div className="bg-white rounded-xl border border-slate-200 p-5 space-y-4">
      <div className="flex items-start gap-3">
        <div className="w-9 h-9 rounded-lg bg-brand-green/10 flex items-center justify-center shrink-0">
          <FileSearch className="w-4 h-4 text-brand-green" />
        </div>
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-slate-800">Analyse automatique du document</h3>
          <p className="text-xs text-slate-500 mt-0.5">
            Le document est lu et les champs sont pré-remplis. <b>Relisez-les avant de valider</b> —
            rien n'est enregistré automatiquement.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <select
          value={docType}
          onChange={(e) => setDocType(e.target.value)}
          disabled={busy}
          className="px-3 py-2 rounded-lg border border-slate-200 text-sm bg-white"
        >
          {(types.length ? types : [{ key: docType, label: docType, fields: [] }]).map((t) => (
            <option key={t.key} value={t.key}>
              {t.label}
            </option>
          ))}
        </select>

        <input
          ref={fileInput}
          type="file"
          accept={ACCEPT}
          onChange={(e) => {
            setFile(e.target.files?.[0] ?? null);
            setResult(null);
            setError(null);
          }}
          className="hidden"
        />
        <button
          onClick={() => fileInput.current?.click()}
          disabled={busy}
          className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-600 hover:bg-slate-50"
        >
          <Upload className="w-4 h-4" />
          {file ? 'Changer de fichier' : 'Choisir un fichier'}
        </button>

        {file && <span className="text-xs text-slate-500 truncate max-w-[14rem]">{file.name}</span>}

        <button
          onClick={analyse}
          disabled={busy || !file}
          className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-brand-green text-white text-sm font-medium hover:bg-[#0f3c35] disabled:opacity-40 ml-auto"
        >
          {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
          {busy ? 'Analyse en cours…' : 'Analyser'}
        </button>
      </div>

      <p className="text-[11px] text-slate-400">
        Formats acceptés : PDF, JPEG, PNG, WEBP, HEIC, TIFF — {MAX_MB} Mo maximum.
      </p>

      {error && (
        <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
          {error}
        </p>
      )}

      {result && (
        <div className="space-y-3 border-t border-slate-100 pt-4">
          {!result.type_matches && (
            <p className="flex items-start gap-2 text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
              <AlertTriangle className="w-4 h-4 shrink-0 mt-0.5" />
              <span>
                Le document ne semble pas correspondre au type demandé
                {result.detected_type ? ` (détecté : ${result.detected_type})` : ''}.
              </span>
            </p>
          )}

          {result.warnings.length > 0 && (
            <ul className="text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2 space-y-0.5">
              {result.warnings.map((w, i) => (
                <li key={i}>• {w}</li>
              ))}
            </ul>
          )}

          {lowConfidence > 0 && (
            <p className="text-xs text-slate-500">
              {lowConfidence} champ(s) lus avec une confiance faible — signalés en orange, à
              vérifier en priorité.
            </p>
          )}

          <div className="grid gap-2 sm:grid-cols-2">
            {Object.entries(result.fields).map(([key, field]) => {
              const weak = !!field.value && field.confidence < 0.6;
              const empty = !field.value;
              return (
                <label key={key} className="block">
                  <span className="flex items-center justify-between text-[11px] font-medium text-slate-500 mb-1">
                    {fieldLabel(key)}
                    <span
                      className={
                        empty ? 'text-slate-300' : weak ? 'text-amber-600' : 'text-green-600'
                      }
                    >
                      {empty ? 'non lu' : `${Math.round(field.confidence * 100)} %`}
                    </span>
                  </span>
                  <input
                    value={values[key] ?? ''}
                    onChange={(e) => setValues((v) => ({ ...v, [key]: e.target.value }))}
                    placeholder="—"
                    className={`w-full px-3 py-2 rounded-lg border text-sm ${
                      weak ? 'border-amber-300 bg-amber-50/40' : 'border-slate-200'
                    }`}
                  />
                </label>
              );
            })}
          </div>

          <div className="flex items-center justify-between pt-1">
            <span className="text-[11px] text-slate-400">
              Analysé par {result.model ?? 'le service IA'}
            </span>
            {onApply && (
              <button
                onClick={apply}
                className="px-4 py-2 rounded-lg bg-brand-green text-white text-sm font-medium hover:bg-[#0f3c35]"
              >
                Valider et reporter
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
