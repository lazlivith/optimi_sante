import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Download, Loader2, ShieldCheck, User as UserIcon, Building2, ShoppingBag, Mail, Pencil,
} from 'lucide-react';
import { personalDataService, type PersonalData } from '../../api/personalDataService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { usePageMeta } from '../../hooks/usePageMeta';

const dateFr = (v: string | null | undefined) =>
  v ? new Date(v).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }) : '—';

/**
 * « Mes données personnelles » — droit d'accès et de portabilité (RGPD, art. 15 et 20).
 *
 * La page montre ce que la plateforme détient avant de proposer de le télécharger : un bouton
 * d'export seul obligerait à ouvrir un fichier pour savoir ce qu'il contient.
 */
export function MyPersonalDataPage() {
  usePageMeta('Mes données personnelles');
  const [data, setData] = useState<PersonalData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isExporting, setIsExporting] = useState(false);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  useEffect(() => {
    personalDataService.get()
      .then(setData)
      .catch((err) => {
        console.error('Chargement des données personnelles impossible', err);
        setToast({ message: 'Impossible de charger vos données.', type: 'error' });
      })
      .finally(() => setIsLoading(false));
  }, []);

  const exporter = async () => {
    setIsExporting(true);
    try {
      await personalDataService.downloadCsv();
      setToast({ message: 'Export téléchargé.', type: 'success' });
    } catch (err) {
      setToast({ message: "L'export n'a pas pu être généré.", type: 'error' });
    } finally {
      setIsExporting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="min-h-[60vh] flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-brand-green" />
      </div>
    );
  }

  return (
    <div className="bg-slate-50 min-h-screen py-10">
      <div className="container mx-auto px-4 max-w-4xl">
        <header className="mb-8">
          <h1 className="text-3xl font-bold text-brand-dark">Mes données personnelles</h1>
          <p className="text-slate-500 mt-2">
            Ce que la plateforme conserve à votre sujet, et comment en obtenir une copie.
          </p>
        </header>

        {/* Accès et portabilité */}
        <section className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 md:p-8 mb-6">
          <h2 className="text-xl font-bold text-brand-dark mb-2">Accès à mes données</h2>
          <p className="text-sm text-slate-600 leading-relaxed mb-5">
            Vous pouvez à tout moment récupérer une copie des données que nous détenons sur
            vous. Le fichier CSV s'ouvre dans un tableur.
          </p>
          <button
            type="button" onClick={exporter} disabled={isExporting}
            className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-brand-green text-white font-bold hover:bg-[#0f3c35] disabled:opacity-60 transition-colors"
          >
            {isExporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
            Obtenir mes données (CSV)
          </button>
        </section>

        {/* Ce que contient l'export, affiche avant de le telecharger. */}
        {data && (
          <section className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden mb-6">
            <div className="px-6 md:px-8 py-5 border-b border-slate-100">
              <h2 className="text-xl font-bold text-brand-dark">Ce que nous conservons</h2>
            </div>

            <Bloc icon={UserIcon} titre="Identité">
              <Champ label="Adresse email" valeur={data.identity.email} />
              <Champ label="Prénom" valeur={data.identity.firstName} />
              <Champ label="Nom" valeur={data.identity.lastName} />
              <Champ label="Téléphone" valeur={data.identity.phone} />
              <Champ label="Type de compte" valeur={data.identity.role} />
              <Champ label="Compte créé le" valeur={dateFr(data.identity.createdAt)} />
            </Bloc>

            {data.company && (
              <Bloc icon={Building2} titre="Entreprise">
                <Champ label="Raison sociale" valeur={data.company.companyName} />
                <Champ label="Identifiant fiscal" valeur={data.company.taxId} />
                <Champ label="Numéro de TVA" valeur={data.company.vatNumber} />
                <Champ label="Adresse de facturation" valeur={data.company.billingAddress} />
                <Champ label="Pays" valeur={data.company.country} />
              </Bloc>
            )}

            <Bloc icon={ShoppingBag} titre={`Commandes et devis (${data.orders.length})`}>
              {data.orders.length === 0 ? (
                <p className="text-sm text-slate-500">Aucune commande enregistrée.</p>
              ) : (
                <ul className="divide-y divide-slate-100 -my-2">
                  {data.orders.slice(0, 5).map((o) => (
                    <li key={o.orderNumber} className="py-2.5 flex items-center justify-between gap-4 text-sm">
                      <span className="text-slate-700 truncate">
                        {o.isQuote ? 'Devis' : 'Commande'} {o.orderNumber}
                        <span className="text-slate-400"> · {dateFr(o.createdAt)}</span>
                      </span>
                      <span className="font-semibold text-brand-dark shrink-0">
                        {o.totalAmount != null ? `${o.totalAmount.toFixed(2)} €` : '—'}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
              {data.orders.length > 5 && (
                <p className="text-xs text-slate-400 mt-3">
                  {data.orders.length - 5} de plus dans l'export ·{' '}
                  <Link to="/my-orders" className="text-brand-green font-semibold hover:underline">
                    voir l'historique complet
                  </Link>
                </p>
              )}
            </Bloc>

            <Bloc icon={Mail} titre={`Emails qui vous ont été envoyés (${data.emails.length})`} dernier>
              {data.emails.length === 0 ? (
                <p className="text-sm text-slate-500">Aucun email enregistré.</p>
              ) : (
                <>
                  <ul className="divide-y divide-slate-100 -my-2">
                    {data.emails.slice(0, 5).map((e, i) => (
                      <li key={i} className="py-2.5 flex items-center justify-between gap-4 text-sm">
                        <span className="text-slate-700 truncate">{e.subject ?? '—'}</span>
                        <span className="text-slate-400 shrink-0">{dateFr(e.sentAt)}</span>
                      </li>
                    ))}
                  </ul>
                  {/* Dit explicitement ce qui n'est PAS conserve : sans cette phrase, on peut
                      croire que le contenu des messages est stocke. */}
                  <p className="text-xs text-slate-400 mt-3">
                    Seuls l'objet, la date et le statut d'envoi sont conservés — jamais le
                    contenu des messages.
                  </p>
                </>
              )}
            </Bloc>
          </section>
        )}

        {/* Rectification et effacement */}
        <section className="bg-white rounded-2xl border border-slate-200 shadow-sm p-6 md:p-8">
          <h2 className="text-xl font-bold text-brand-dark mb-2">Rectification et effacement</h2>
          <p className="text-sm text-slate-600 leading-relaxed">
            Vous pouvez modifier vous-même la plupart de vos informations depuis la page{' '}
            <Link to="/profile" className="text-brand-green font-semibold hover:underline">
              Mon profil
            </Link>
            . Pour toute autre demande de rectification ou d'effacement, écrivez-nous : nous
            examinerons votre demande et vous répondrons dans les meilleurs délais.
          </p>
          <div className="flex flex-wrap gap-3 mt-5">
            <Link
              to="/profile"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl border border-slate-300 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition-colors"
            >
              <Pencil className="w-4 h-4" /> Modifier mon profil
            </Link>
            <a
              href="https://wa.me/33600000000"
              target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-slate-900 text-white text-sm font-semibold hover:bg-slate-800 transition-colors"
            >
              Nous contacter
            </a>
          </div>
          {/* Une commande est une piece comptable : la loi impose de la conserver meme apres
              une demande d'effacement. Le dire ici evite une promesse qu'on ne tiendrait pas. */}
          <p className="flex items-start gap-2 text-xs text-slate-500 mt-5">
            <ShieldCheck className="w-3.5 h-3.5 mt-0.5 shrink-0" />
            <span>
              Certaines données liées à vos commandes doivent être conservées pour des raisons
              comptables et légales, même après une demande d'effacement.
            </span>
          </p>
        </section>
      </div>

      {toast && <Toast type={toast.type} message={toast.message} onClose={() => setToast(null)} />}
    </div>
  );
}

function Bloc({
  icon: Icon, titre, children, dernier,
}: { icon: typeof UserIcon; titre: string; children: React.ReactNode; dernier?: boolean }) {
  return (
    <div className={`px-6 md:px-8 py-5 ${dernier ? '' : 'border-b border-slate-100'}`}>
      <h3 className="flex items-center gap-2 text-sm font-bold text-slate-700 mb-3">
        <Icon className="w-4 h-4 text-brand-green" /> {titre}
      </h3>
      {children}
    </div>
  );
}

function Champ({ label, valeur }: { label: string; valeur: string | null | undefined }) {
  return (
    <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-0.5 py-1.5 text-sm border-b border-slate-50 last:border-0">
      <span className="text-slate-500">{label}</span>
      <span className="font-medium text-brand-dark text-right break-all">{valeur || '—'}</span>
    </div>
  );
}
