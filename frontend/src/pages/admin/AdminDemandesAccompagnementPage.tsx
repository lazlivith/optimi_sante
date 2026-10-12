import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Loader2, Inbox, Download, BellRing, Search, X, MessageCircle, Mail,
  ChevronLeft, ChevronRight, StickyNote, UserCheck,
} from 'lucide-react';
import {
  adminDemandesAccompagnementService as service,
  type DemandeResume, type DemandeVue, type FiltresDemandes,
} from '../../api/adminDemandesAccompagnementService';
import {
  STATUTS_DEMANDE, ETAPES_DEMANDE, libelleStatut, OFFRE_ALERTE_AEP,
  type StatutDemande,
} from '../../lib/exercerEnFrance';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';

const FILTRES_VIDES: FiltresDemandes = {};

/** Le numéro tel que WhatsApp l'attend : chiffres seuls, sans « + » ni espaces. */
function lienWhatsapp(numero: string | null): string | null {
  if (!numero) return null;
  const chiffres = numero.replace(/[^0-9]/g, '');
  return chiffres.length >= 8 ? `https://wa.me/${chiffres}` : null;
}

function dateCourte(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleDateString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
  });
}

function dateHeure(iso: string | null): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

/**
 * Suivi des demandes d'accompagnement.
 *
 * <p><b>C'est l'écran qui rapporte de l'argent.</b> Chaque ligne est un praticien qui a
 * laissé ses coordonnées ; le délai entre son dépôt et le premier contact décide du reste.
 * L'écran est donc construit autour de cette question : qu'est-ce qui attend une réponse, et
 * depuis combien de temps.</p>
 *
 * <p><b>La fiche s'ouvre aussi par son adresse.</b> L'e-mail d'alerte envoie vers
 * {@code /admin/demandes-accompagnement/<id>} : ce lien doit ouvrir le bon dossier, et pas
 * une liste où il faudrait le retrouver.</p>
 */
export function AdminDemandesAccompagnementPage() {
  const { id: idUrl } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [demandes, setDemandes] = useState<DemandeResume[]>([]);
  const [page, setPage] = useState(0);
  const [pages, setPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [chargement, setChargement] = useState(true);
  const [filtres, setFiltres] = useState<FiltresDemandes>(FILTRES_VIDES);
  // La recherche est saisie ici et n'est appliquée qu'à la validation : relancer une requête
  // à chaque frappe ferait travailler le serveur pour des mots jamais terminés.
  const [saisieRecherche, setSaisieRecherche] = useState('');
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const [fiche, setFiche] = useState<DemandeVue | null>(null);
  const [ficheEnCours, setFicheEnCours] = useState(false);

  const charger = useCallback(async () => {
    setChargement(true);
    try {
      const resultat = await service.lister(filtres, page);
      setDemandes(resultat.content);
      setPages(resultat.totalPages);
      setTotal(resultat.totalElements);
    } catch {
      setToast({ message: 'Impossible de charger les demandes.', type: 'error' });
    } finally {
      setChargement(false);
    }
  }, [filtres, page]);

  useEffect(() => { charger(); }, [charger]);

  // L'identifiant vient de l'adresse : au premier affichage comme au clic sur une ligne,
  // c'est lui qui commande la fiche ouverte.
  useEffect(() => {
    if (!idUrl) {
      setFiche(null);
      return;
    }
    let abandonne = false;
    setFicheEnCours(true);
    service.detail(idUrl)
      .then(d => { if (!abandonne) setFiche(d); })
      .catch(() => {
        if (!abandonne) {
          setToast({ message: "Cette demande est introuvable.", type: 'error' });
          navigate('/admin/demandes-accompagnement', { replace: true });
        }
      })
      .finally(() => { if (!abandonne) setFicheEnCours(false); });
    return () => { abandonne = true; };
  }, [idUrl, navigate]);

  const rafraichirFiche = async () => {
    if (!idUrl) return;
    setFiche(await service.detail(idUrl));
    charger();
  };

  const appliquerFiltre = (champ: keyof FiltresDemandes, valeur: string) => {
    setPage(0);
    setFiltres(f => ({ ...f, [champ]: valeur || undefined }));
  };

  const lancerRecherche = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(0);
    setFiltres(f => ({ ...f, recherche: saisieRecherche.trim() || undefined }));
  };

  const effacerFiltres = () => {
    setSaisieRecherche('');
    setPage(0);
    setFiltres(FILTRES_VIDES);
  };

  const exporter = async (alertes: boolean) => {
    try {
      await (alertes ? service.exporterAlertes() : service.exporter(filtres));
      setToast({ message: 'Export téléchargé.', type: 'success' });
    } catch {
      setToast({ message: "L'export a échoué.", type: 'error' });
    }
  };

  const filtreActif = Object.values(filtres).some(Boolean);

  return (
    <div className="p-4 sm:p-6 lg:p-8">
      <PageHeader
        title="Demandes d'accompagnement"
        subtitle="Praticiens ayant sollicité un accompagnement ou demandé à être prévenus."
        actions={
          <>
            <button
              type="button"
              onClick={() => exporter(false)}
              className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              <Download className="w-4 h-4" /> Exporter la vue
            </button>
            <button
              type="button"
              onClick={() => exporter(true)}
              className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-brand-dark text-white text-sm font-medium hover:opacity-90"
            >
              <BellRing className="w-4 h-4" /> Contacts à prévenir
            </button>
          </>
        }
      />

      {/* ── Filtres ───────────────────────────────────────────────────────────────── */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-4 mb-4">
        <div className="flex flex-wrap items-end gap-3">
          <label className="text-xs font-semibold text-slate-500 flex flex-col gap-1">
            Statut
            <select
              value={filtres.statut ?? ''}
              onChange={e => appliquerFiltre('statut', e.target.value)}
              className="min-w-40 px-3 py-2 rounded-lg border border-slate-200 text-sm font-normal text-slate-800"
            >
              <option value="">Tous</option>
              {Object.entries(STATUTS_DEMANDE).map(([code, s]) => (
                <option key={code} value={code}>{s.libelle}</option>
              ))}
            </select>
          </label>

          <label className="text-xs font-semibold text-slate-500 flex flex-col gap-1">
            Profession
            <input
              type="text"
              value={filtres.profession ?? ''}
              onChange={e => appliquerFiltre('profession', e.target.value)}
              placeholder="Médecin, Sage-femme…"
              className="min-w-44 px-3 py-2 rounded-lg border border-slate-200 text-sm font-normal text-slate-800"
            />
          </label>

          <label className="text-xs font-semibold text-slate-500 flex flex-col gap-1">
            Depuis le
            <input
              type="date"
              value={filtres.depuis ?? ''}
              onChange={e => appliquerFiltre('depuis', e.target.value)}
              className="px-3 py-2 rounded-lg border border-slate-200 text-sm font-normal text-slate-800"
            />
          </label>

          <label className="text-xs font-semibold text-slate-500 flex flex-col gap-1">
            Jusqu'au
            <input
              type="date"
              value={filtres.jusqua ?? ''}
              onChange={e => appliquerFiltre('jusqua', e.target.value)}
              className="px-3 py-2 rounded-lg border border-slate-200 text-sm font-normal text-slate-800"
            />
          </label>

          <form onSubmit={lancerRecherche} className="flex items-end gap-2">
            <label className="text-xs font-semibold text-slate-500 flex flex-col gap-1">
              Recherche
              <div className="relative">
                <Search className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="search"
                  value={saisieRecherche}
                  onChange={e => setSaisieRecherche(e.target.value)}
                  placeholder="Nom, e-mail, spécialité…"
                  className="min-w-56 pl-9 pr-3 py-2 rounded-lg border border-slate-200 text-sm font-normal text-slate-800"
                />
              </div>
            </label>
            <button
              type="submit"
              className="px-3 py-2 rounded-lg border border-slate-200 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Chercher
            </button>
          </form>

          {/* Le raccourci qui compte : ces demandes-là n'attendent qu'une date du CNG. */}
          <button
            type="button"
            onClick={() => appliquerFiltre('offreCle',
              filtres.offreCle === OFFRE_ALERTE_AEP ? '' : OFFRE_ALERTE_AEP)}
            aria-pressed={filtres.offreCle === OFFRE_ALERTE_AEP}
            className={`inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-medium border ${
              filtres.offreCle === OFFRE_ALERTE_AEP
                ? 'bg-amber-100 text-amber-800 border-amber-200'
                : 'border-slate-200 text-slate-700 hover:bg-slate-50'
            }`}
          >
            <BellRing className="w-4 h-4" /> Alertes AEP
          </button>

          {filtreActif && (
            <button
              type="button"
              onClick={effacerFiltres}
              className="inline-flex items-center gap-1 px-2 py-2 text-sm text-slate-500 hover:text-slate-800"
            >
              <X className="w-4 h-4" /> Tout effacer
            </button>
          )}
        </div>
      </div>

      {/* ── Liste ─────────────────────────────────────────────────────────────────── */}
      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {chargement ? (
          <div className="p-12 flex justify-center">
            <Loader2 className="w-8 h-8 text-brand animate-spin" />
          </div>
        ) : demandes.length === 0 ? (
          <EmptyState
            icon={Inbox}
            title={filtreActif ? 'Aucune demande ne correspond à ces filtres.' : 'Aucune demande pour le moment.'}
            description={filtreActif ? 'Élargissez la période ou effacez les filtres.' : undefined}
          />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                  <tr>
                    <th className="px-4 py-3">Déposée le</th>
                    <th className="px-4 py-3">Praticien</th>
                    <th className="px-4 py-3">Profession</th>
                    <th className="px-4 py-3">Étape</th>
                    <th className="px-4 py-3">Demande</th>
                    <th className="px-4 py-3">Statut</th>
                    <th className="px-4 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {demandes.map(d => (
                    <tr
                      key={d.id}
                      onClick={() => navigate(`/admin/demandes-accompagnement/${d.id}`)}
                      className="hover:bg-slate-50 cursor-pointer"
                    >
                      <td className="px-4 py-3 text-slate-500 whitespace-nowrap">{dateCourte(d.creeLe)}</td>
                      <td className="px-4 py-3">
                        <div className="font-semibold text-slate-800">{d.nom}</div>
                        <div className="text-xs text-slate-500">{d.email}</div>
                      </td>
                      <td className="px-4 py-3 text-slate-600">
                        {d.profession || '—'}
                        {d.specialite && <div className="text-xs text-slate-400">{d.specialite}</div>}
                      </td>
                      <td className="px-4 py-3 text-slate-600">
                        {d.etape ? ETAPES_DEMANDE[d.etape] ?? d.etape : '—'}
                      </td>
                      <td className="px-4 py-3">
                        {d.offreCle === OFFRE_ALERTE_AEP ? (
                          <span className="inline-flex items-center gap-1.5 text-xs font-semibold text-amber-800">
                            <BellRing className="w-3.5 h-3.5" /> Alerte AEP
                          </span>
                        ) : (
                          <span className="text-slate-600">{d.offreCle || '—'}</span>
                        )}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge
                          status={d.statut}
                          label={libelleStatut(d.statut)}
                          tone={STATUTS_DEMANDE[d.statut as StatutDemande]?.ton}
                        />
                      </td>
                      <td className="px-4 py-3 text-right whitespace-nowrap">
                        <span onClick={e => e.stopPropagation()} className="inline-flex gap-1">
                          {lienWhatsapp(d.telephoneWhatsapp) && (
                            <a
                              href={lienWhatsapp(d.telephoneWhatsapp)!}
                              target="_blank"
                              rel="noopener noreferrer"
                              title={`Écrire à ${d.nom} sur WhatsApp`}
                              className="p-2 rounded-lg text-success hover:bg-success/10"
                            >
                              <MessageCircle className="w-4 h-4" />
                            </a>
                          )}
                          <a
                            href={`mailto:${d.email}`}
                            title={`Écrire à ${d.nom} par e-mail`}
                            className="p-2 rounded-lg text-slate-600 hover:bg-slate-100"
                          >
                            <Mail className="w-4 h-4" />
                          </a>
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex items-center justify-between gap-4 px-4 py-3 border-t border-slate-200 text-sm text-slate-500">
              <span>
                {total} demande{total > 1 ? 's' : ''}
                {pages > 1 && ` · page ${page + 1} sur ${pages}`}
              </span>
              {pages > 1 && (
                <div className="flex items-center gap-1">
                  <button
                    type="button"
                    onClick={() => setPage(p => Math.max(0, p - 1))}
                    disabled={page === 0}
                    className="p-2 rounded-lg border border-slate-200 disabled:opacity-40 hover:bg-slate-50"
                    aria-label="Page précédente"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setPage(p => Math.min(pages - 1, p + 1))}
                    disabled={page >= pages - 1}
                    className="p-2 rounded-lg border border-slate-200 disabled:opacity-40 hover:bg-slate-50"
                    aria-label="Page suivante"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              )}
            </div>
          </>
        )}
      </div>

      {(idUrl || ficheEnCours) && (
        <FicheDemande
          demande={fiche}
          chargement={ficheEnCours}
          onFermer={() => navigate('/admin/demandes-accompagnement')}
          onChange={rafraichirFiche}
          onErreur={message => setToast({ message, type: 'error' })}
          onSucces={message => setToast({ message, type: 'success' })}
        />
      )}

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}

interface FicheProps {
  demande: DemandeVue | null;
  chargement: boolean;
  onFermer: () => void;
  onChange: () => Promise<void> | void;
  onErreur: (message: string) => void;
  onSucces: (message: string) => void;
}

/**
 * La fiche d'une demande, en panneau latéral.
 *
 * <p><b>Un panneau, pas une page.</b> On traite ces dossiers en série : revenir à la liste
 * entre chaque, en perdant ses filtres et sa page, transforme une matinée de rappels en
 * navigation.</p>
 */
function FicheDemande({ demande, chargement, onFermer, onChange, onErreur, onSucces }: FicheProps) {
  const [note, setNote] = useState('');
  const [envoi, setEnvoi] = useState(false);

  const changerStatut = async (statut: string) => {
    if (!demande) return;
    setEnvoi(true);
    try {
      await service.changerStatut(demande.id, statut);
      await onChange();
      onSucces(`Statut passé à « ${libelleStatut(statut)} ».`);
    } catch (e: any) {
      onErreur(e.response?.data?.message || "Le changement de statut a échoué.");
    } finally {
      setEnvoi(false);
    }
  };

  const ajouterNote = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!demande || !note.trim()) return;
    setEnvoi(true);
    try {
      await service.ajouterNote(demande.id, note.trim());
      setNote('');
      await onChange();
      onSucces('Note ajoutée.');
    } catch (err: any) {
      onErreur(err.response?.data?.message || "La note n'a pas pu être enregistrée.");
    } finally {
      setEnvoi(false);
    }
  };

  const whatsapp = demande ? lienWhatsapp(demande.telephoneWhatsapp) : null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-slate-900/40" onClick={onFermer} aria-hidden="true" />
      <aside
        role="dialog"
        aria-modal="true"
        aria-label="Fiche de la demande"
        className="relative w-full max-w-xl bg-white h-full overflow-y-auto shadow-xl"
      >
        <div className="sticky top-0 bg-white border-b border-slate-200 px-5 py-4 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold text-brand-dark">{demande?.nom ?? 'Chargement…'}</h2>
            {demande && (
              <p className="text-sm text-slate-500">
                Déposée le {dateHeure(demande.creeLe)}
              </p>
            )}
          </div>
          <button
            type="button"
            onClick={onFermer}
            aria-label="Fermer la fiche"
            className="p-2 rounded-lg text-slate-500 hover:bg-slate-100"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {chargement || !demande ? (
          <div className="p-12 flex justify-center">
            <Loader2 className="w-8 h-8 text-brand animate-spin" />
          </div>
        ) : (
          <div className="p-5 space-y-6">
            {demande.anonymisee && (
              <p className="rounded-lg bg-slate-100 text-slate-600 text-sm px-3 py-2">
                Les données nominatives de cette demande ont été effacées à l'échéance de
                conservation. La ligne reste pour les statistiques.
              </p>
            )}

            {/* ── Joindre le praticien ──────────────────────────────────────────── */}
            <div className="flex flex-wrap gap-2">
              {whatsapp && (
                <a
                  href={whatsapp}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-2 px-3 py-2 rounded-lg bg-success text-white text-sm font-medium hover:opacity-90"
                >
                  <MessageCircle className="w-4 h-4" /> WhatsApp
                </a>
              )}
              <a
                href={`mailto:${demande.email}`}
                className="inline-flex items-center gap-2 px-3 py-2 rounded-lg border border-slate-200 text-sm font-medium text-slate-700 hover:bg-slate-50"
              >
                <Mail className="w-4 h-4" /> {demande.email}
              </a>
            </div>

            {/* ── Identité ──────────────────────────────────────────────────────── */}
            <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
              <Champ libelle="Téléphone / WhatsApp" valeur={demande.telephoneWhatsapp} />
              <Champ libelle="Pays" valeur={demande.pays} />
              <Champ libelle="Profession" valeur={demande.profession} />
              <Champ libelle="Spécialité" valeur={demande.specialite} />
              <Champ
                libelle="Étape du parcours"
                valeur={demande.etape ? ETAPES_DEMANDE[demande.etape] ?? demande.etape : null}
              />
              <Champ
                libelle="Demande"
                valeur={demande.offreCle === OFFRE_ALERTE_AEP ? 'Alerte AEP' : demande.offreCle}
              />
              <Champ libelle="Dernière activité" valeur={dateHeure(demande.derniereActiviteLe)} />
              <Champ libelle="Fin de prestation" valeur={dateCourte(demande.prestationFinLe)} />
            </dl>

            {demande.message && (
              <div>
                <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
                  Message du praticien
                </h3>
                <p className="text-sm text-slate-700 whitespace-pre-wrap rounded-lg bg-slate-50 p-3">
                  {demande.message}
                </p>
              </div>
            )}

            {/* ── Statut ────────────────────────────────────────────────────────── */}
            <div>
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2">
                Statut
              </h3>
              <div className="flex flex-wrap gap-2">
                {Object.entries(STATUTS_DEMANDE).map(([code, s]) => (
                  <button
                    key={code}
                    type="button"
                    disabled={envoi || code === demande.statut}
                    onClick={() => changerStatut(code)}
                    aria-pressed={code === demande.statut}
                    className={`px-3 py-1.5 rounded-full text-xs font-semibold border disabled:cursor-default ${
                      code === demande.statut
                        ? 'bg-brand-dark text-white border-transparent'
                        : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                    }`}
                  >
                    {s.libelle}
                  </button>
                ))}
              </div>
              {demande.statut === 'termine' && !demande.prestationFinLe && (
                <p className="text-xs text-amber-800 mt-2">
                  Sans date de fin, le délai de conservation de ce dossier ne commence jamais.
                </p>
              )}
            </div>

            {/* ── Notes internes ────────────────────────────────────────────────── */}
            <div>
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2 flex items-center gap-1.5">
                <StickyNote className="w-3.5 h-3.5" /> Notes internes
              </h3>
              {demande.notesInternes && (
                <p className="text-sm text-slate-700 whitespace-pre-wrap rounded-lg bg-amber-50 p-3 mb-2">
                  {demande.notesInternes}
                </p>
              )}
              <form onSubmit={ajouterNote} className="flex flex-col gap-2">
                <textarea
                  value={note}
                  onChange={e => setNote(e.target.value)}
                  rows={2}
                  placeholder="Ce que l'équipe doit savoir sur ce dossier…"
                  className="w-full px-3 py-2 rounded-lg border border-slate-200 text-sm text-slate-800"
                />
                <button
                  type="submit"
                  disabled={envoi || !note.trim()}
                  className="self-start px-3 py-2 rounded-lg bg-brand-dark text-white text-sm font-medium disabled:opacity-40"
                >
                  Ajouter la note
                </button>
              </form>
            </div>

            {/* ── Fil des événements ────────────────────────────────────────────── */}
            <div>
              <h3 className="text-xs font-bold uppercase tracking-wider text-slate-500 mb-2 flex items-center gap-1.5">
                <UserCheck className="w-3.5 h-3.5" /> Historique
              </h3>
              {demande.fil.length === 0 ? (
                <p className="text-sm text-slate-500">Rien ne s'est encore passé sur ce dossier.</p>
              ) : (
                <ol className="space-y-2">
                  {demande.fil.map((e, i) => (
                    <li key={i} className="text-sm border-l-2 border-slate-200 pl-3">
                      <span className="text-slate-500 text-xs">{dateHeure(e.creeLe)}</span>
                      <div className="text-slate-700">
                        {e.ancienStatut && e.nouveauStatut
                          ? `Statut : ${libelleStatut(e.ancienStatut)} → ${libelleStatut(e.nouveauStatut)}`
                          : e.contenu || e.type}
                      </div>
                    </li>
                  ))}
                </ol>
              )}
            </div>
          </div>
        )}
      </aside>
    </div>
  );
}

function Champ({ libelle, valeur }: { libelle: string; valeur: string | null }) {
  return (
    <div>
      <dt className="text-xs font-semibold text-slate-500">{libelle}</dt>
      <dd className="text-slate-800">{valeur || '—'}</dd>
    </div>
  );
}
