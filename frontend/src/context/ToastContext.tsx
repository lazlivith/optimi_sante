import React, { createContext, useCallback, useContext, useState } from 'react';
import { AlertCircle, AlertTriangle, CheckCircle2, Info, X } from 'lucide-react';

export type ToastKind = 'success' | 'error' | 'info' | 'warning';

interface ToastItem {
  id: number;
  message: string;
  kind: ToastKind;
}

interface ToastContextValue {
  /** Affiche un toast éphémère (auto-disparition ~5 s). */
  notify: (message: string, kind?: ToastKind) => void;
}

const ToastContext = createContext<ToastContextValue | undefined>(undefined);

const STYLES: Record<ToastKind, string> = {
  success: 'bg-green-50 text-green-800 border-green-200',
  error: 'bg-red-50 text-red-800 border-red-200',
  info: 'bg-blue-50 text-blue-800 border-blue-200',
  warning: 'bg-amber-50 text-amber-800 border-amber-200',
};

const ICONS: Record<ToastKind, React.ReactNode> = {
  success: <CheckCircle2 className="w-5 h-5 text-green-500 shrink-0" />,
  error: <AlertCircle className="w-5 h-5 text-red-500 shrink-0" />,
  info: <Info className="w-5 h-5 text-blue-500 shrink-0" />,
  warning: <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0" />,
};

const AUTO_DISMISS_MS = 5000;

/**
 * Fournisseur global de toasts. Remplace le state `Toast` dupliqué page par page : n'importe
 * quel composant appelle `useToast().notify(...)`. Les toasts s'empilent en bas à droite.
 */
export const ToastProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [items, setItems] = useState<ToastItem[]>([]);

  const dismiss = useCallback((id: number) => {
    setItems((current) => current.filter((t) => t.id !== id));
  }, []);

  const notify = useCallback(
    (message: string, kind: ToastKind = 'info') => {
      const id = Date.now() + Math.random();
      setItems((current) => [...current, { id, message, kind }]);
      window.setTimeout(() => dismiss(id), AUTO_DISMISS_MS);
    },
    [dismiss],
  );

  return (
    <ToastContext.Provider value={{ notify }}>
      {children}
      <div className="fixed bottom-4 right-4 z-[60] flex flex-col gap-2 items-end pointer-events-none">
        {items.map((t) => (
          <div
            key={t.id}
            className={`pointer-events-auto flex items-center gap-3 px-4 py-3 rounded-lg border shadow-lg max-w-sm ${STYLES[t.kind]}`}
          >
            {ICONS[t.kind]}
            <p className="text-sm font-medium">{t.message}</p>
            <button
              onClick={() => dismiss(t.id)}
              className="ml-auto p-1 rounded-md hover:bg-black/5 transition-colors focus:outline-none"
              aria-label="Fermer"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
};

export const useToast = (): ToastContextValue => {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast doit être utilisé à l’intérieur d’un <ToastProvider>');
  }
  return ctx;
};
