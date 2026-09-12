import { useCallback, useEffect, useRef, useState } from 'react';
import { Bot, Send, Sparkles, X } from 'lucide-react';
import { streamChat, type ChatMessage } from '../../api/aiService';
import { useAuth } from '../../context/AuthContext';

const SUGGESTIONS_BY_ROLE: Record<string, string[]> = {
  MEDECIN: [
    'Où en est mon dossier de formation ?',
    'Quelles pièces dois-je encore fournir ?',
    'Quelles formations sont ouvertes actuellement ?',
  ],
  CLIENT_B2B: [
    'Quels équipements proposez-vous ?',
    'Où en sont mes devis ?',
    'Comment obtenir une remise professionnelle ?',
  ],
  CENTRE_FORMATION: [
    'Comment publier une nouvelle session ?',
    'Où voir les dossiers à examiner ?',
    'Quelles formations ai-je en ligne ?',
  ],
};

const DEFAULT_SUGGESTIONS = [
  'Quelles formations médicales proposez-vous ?',
  'Comment candidater à une formation ?',
  'Où en est ma commande ?',
];

const GREETING =
  "Bonjour, je suis l'assistant Optimi Santé. Je peux vous renseigner sur le catalogue, "
  + 'les formations, vos commandes et vos dossiers. Que puis-je faire pour vous ?';

export function ChatWidget() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [conversationId, setConversationId] = useState<string | null>(null);

  const cancelRef = useRef<(() => void) | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const suggestions = user ? SUGGESTIONS_BY_ROLE[user.role] ?? DEFAULT_SUGGESTIONS : DEFAULT_SUGGESTIONS;

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, pending]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  // Coupe le flux en cours si le composant disparaît ou si le panneau se ferme.
  useEffect(() => () => cancelRef.current?.(), []);

  const send = useCallback(
    (question: string) => {
      const trimmed = question.trim();
      if (!trimmed || pending) return;

      setError(null);
      setInput('');
      setMessages((current) => [...current, { role: 'user', content: trimmed }, { role: 'assistant', content: '' }]);
      setPending(true);

      cancelRef.current = streamChat(trimmed, conversationId, {
        onConversation: (id) => setConversationId(id),
        onDelta: (piece) =>
          setMessages((current) => {
            const next = [...current];
            const last = next[next.length - 1];
            if (last?.role === 'assistant') next[next.length - 1] = { ...last, content: last.content + piece };
            return next;
          }),
        onDone: () => setPending(false),
        onError: (message) => {
          setPending(false);
          setError(message);
          // Retire la bulle assistant restée vide.
          setMessages((current) => {
            const last = current[current.length - 1];
            return last?.role === 'assistant' && !last.content ? current.slice(0, -1) : current;
          });
        },
      });
    },
    [conversationId, pending],
  );

  const close = () => {
    cancelRef.current?.();
    setPending(false);
    setOpen(false);
  };

  return (
    <>
      {/* Bouton flottant — placé au-dessus du bouton WhatsApp de la page d'accueil. */}
      {!open && (
        <button
          onClick={() => setOpen(true)}
          className="fixed bottom-24 right-6 z-50 w-14 h-14 bg-brand rounded-full flex items-center justify-center shadow-lg hover:bg-[#0f3c35] transition-all hover:scale-110"
          aria-label="Ouvrir l'assistant Optimi Santé"
        >
          <Sparkles className="w-6 h-6 text-white" />
        </button>
      )}

      {open && (
        <div className="fixed bottom-6 right-6 z-50 w-[min(24rem,calc(100vw-3rem))] h-[min(34rem,calc(100vh-6rem))] bg-white rounded-2xl border border-slate-200 shadow-2xl flex flex-col overflow-hidden">
          <header className="flex items-center gap-2 px-4 py-3 bg-brand-dark text-white shrink-0">
            <div className="w-8 h-8 rounded-full bg-brand flex items-center justify-center">
              <Bot className="w-4 h-4" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-semibold leading-none">Assistant Optimi Santé</p>
              <p className="text-[10px] text-slate-300 leading-none mt-1">
                Réponses indicatives — aucun conseil médical
              </p>
            </div>
            <button onClick={close} className="p-1.5 rounded-lg hover:bg-white/10" aria-label="Fermer">
              <X className="w-4 h-4" />
            </button>
          </header>

          <div className="flex-1 overflow-y-auto px-4 py-3 space-y-3 bg-slate-50">
            {messages.length === 0 && (
              <>
                <Bubble role="assistant" content={GREETING} />
                <div className="space-y-1.5 pt-1">
                  {suggestions.map((s) => (
                    <button
                      key={s}
                      onClick={() => send(s)}
                      className="block w-full text-left text-xs px-3 py-2 rounded-lg bg-white border border-slate-200 text-slate-600 hover:border-brand hover:text-brand-dark transition-colors"
                    >
                      {s}
                    </button>
                  ))}
                </div>
              </>
            )}

            {messages.map((m, i) => (
              <Bubble key={i} role={m.role} content={m.content} />
            ))}

            {pending && messages[messages.length - 1]?.content === '' && (
              <div className="flex gap-1.5 px-3 py-2">
                {[0, 150, 300].map((delay) => (
                  <span
                    key={delay}
                    className="w-1.5 h-1.5 rounded-full bg-slate-400 animate-bounce"
                    style={{ animationDelay: `${delay}ms` }}
                  />
                ))}
              </div>
            )}

            {error && (
              <p className="text-xs text-red-700 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
                {error}
              </p>
            )}

            <div ref={bottomRef} />
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              send(input);
            }}
            className="flex items-center gap-2 p-3 border-t border-slate-100 bg-white shrink-0"
          >
            <input
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="Posez votre question…"
              maxLength={4000}
              className="flex-1 px-3 py-2 rounded-full bg-slate-100 border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-brand/30 focus:bg-white transition-all"
            />
            <button
              type="submit"
              disabled={pending || !input.trim()}
              className="w-9 h-9 rounded-full bg-brand text-white flex items-center justify-center disabled:opacity-40 hover:bg-[#0f3c35] transition-colors shrink-0"
              aria-label="Envoyer"
            >
              <Send className="w-4 h-4" />
            </button>
          </form>
        </div>
      )}
    </>
  );
}

function Bubble({ role, content }: ChatMessage) {
  if (!content) return null;
  const isUser = role === 'user';
  return (
    <div className={`flex ${isUser ? 'justify-end' : 'justify-start'}`}>
      <div
        className={`max-w-[85%] px-3 py-2 rounded-2xl text-sm whitespace-pre-wrap break-words ${
          isUser
            ? 'bg-brand text-white rounded-br-sm'
            : 'bg-white border border-slate-200 text-slate-700 rounded-bl-sm'
        }`}
      >
        {content}
      </div>
    </div>
  );
}
