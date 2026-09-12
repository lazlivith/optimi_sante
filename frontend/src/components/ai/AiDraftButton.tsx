import { useState } from 'react';
import { Check, Copy, Loader2, Sparkles, X } from 'lucide-react';
import { aiService } from '../../api/aiService';
import { useToast } from '../../context/ToastContext';

interface Props {
  /** Clé du type de brouillon (ENROLLMENT_DECISION, DOCUMENT_REQUEST, CONVENTION_INTRO…). */
  draftType: string;
  /** Données réelles passées au modèle — il ne doit jamais inventer un fait. */
  context: Record<string, unknown>;
  label?: string;
  /** Si fourni, un bouton « Insérer » renvoie le texte relu vers l'écran appelant. */
  onInsert?: (text: string) => void;
  className?: string;
}

/**
 * Bouton « ✨ » de rédaction assistée. Le texte produit est une **proposition** : il s'ouvre
 * dans une fenêtre où l'administrateur le relit et le modifie avant de l'utiliser.
 */
export function AiDraftButton({ draftType, context, label = 'Rédiger avec l’IA', onInsert, className }: Props) {
  const { notify } = useToast();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [text, setText] = useState('');
  const [extra, setExtra] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const generate = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await aiService.draft(draftType, context, extra || undefined);
      setText(result.text);
    } catch (e) {
      setError(
        (e as { response?: { data?: { message?: string } } }).response?.data?.message
          ?? 'La rédaction a échoué.',
      );
    } finally {
      setBusy(false);
    }
  };

  const start = () => {
    setOpen(true);
    if (!text) void generate();
  };

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      notify('Copie impossible depuis ce navigateur.', 'error');
    }
  };

  return (
    <>
      <button
        onClick={start}
        className={
          className
          ?? 'flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-600 hover:bg-slate-50'
        }
      >
        <Sparkles className="w-4 h-4 text-brand" />
        {label}
      </button>

      {open && (
        <div className="fixed inset-0 z-[70] bg-black/40 flex items-center justify-center p-4">
          <div className="bg-white rounded-xl w-full max-w-2xl max-h-[85vh] flex flex-col overflow-hidden">
            <header className="flex items-center justify-between px-5 py-3 border-b border-slate-100">
              <h3 className="text-sm font-semibold text-slate-800">Proposition de rédaction</h3>
              <button onClick={() => setOpen(false)} className="p-1.5 rounded hover:bg-slate-100">
                <X className="w-4 h-4 text-slate-500" />
              </button>
            </header>

            <div className="flex-1 overflow-y-auto p-5 space-y-3">
              <p className="text-xs text-slate-500">
                Texte généré à partir des données réelles du dossier. <b>Relisez et modifiez</b>
                {' '}avant envoi — les informations manquantes sont laissées entre crochets.
              </p>

              {error && (
                <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                  {error}
                </p>
              )}

              {busy && !text ? (
                <div className="flex items-center gap-2 text-sm text-slate-400 py-8 justify-center">
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Rédaction en cours…
                </div>
              ) : (
                <textarea
                  value={text}
                  onChange={(e) => setText(e.target.value)}
                  rows={14}
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm font-sans leading-relaxed"
                  placeholder="Le texte proposé apparaîtra ici."
                />
              )}

              <label className="block">
                <span className="text-[11px] font-medium text-slate-500">
                  Consigne supplémentaire (facultatif)
                </span>
                <input
                  value={extra}
                  onChange={(e) => setExtra(e.target.value)}
                  placeholder="ex. ton plus ferme, mentionner le délai de 15 jours…"
                  className="mt-1 w-full px-3 py-2 rounded-lg border border-slate-200 text-sm"
                />
              </label>
            </div>

            <footer className="flex items-center gap-2 px-5 py-3 border-t border-slate-100 bg-slate-50">
              <button
                onClick={generate}
                disabled={busy}
                className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 bg-white text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-40"
              >
                {busy ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
                Régénérer
              </button>

              <div className="ml-auto flex items-center gap-2">
                <button
                  onClick={copy}
                  disabled={!text}
                  className="flex items-center gap-1.5 px-3 py-2 rounded-lg border border-slate-200 bg-white text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-40"
                >
                  {copied ? <Check className="w-4 h-4 text-green-600" /> : <Copy className="w-4 h-4" />}
                  {copied ? 'Copié' : 'Copier'}
                </button>
                {onInsert && (
                  <button
                    onClick={() => {
                      onInsert(text);
                      setOpen(false);
                    }}
                    disabled={!text}
                    className="px-4 py-2 rounded-lg bg-brand text-white text-sm font-medium hover:bg-[#0f3c35] disabled:opacity-40"
                  >
                    Insérer
                  </button>
                )}
              </div>
            </footer>
          </div>
        </div>
      )}
    </>
  );
}
