import { useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle2, Loader2, X } from 'lucide-react';
import {
  demandeAccompagnementService, type DemandeRequest,
} from '../../api/demandeAccompagnementService';
import { OFFRE_ALERTE_AEP, verifierDemande } from '../../lib/exercerEnFrance';

const ETAPES = [
  { code: 'avant_evc', libelle: 'Avant les EVC' },
  { code: 'laureat', libelle: 'Lauréat des EVC' },
  { code: 'affecte', libelle: 'Affecté dans un établissement' },
  { code: 'autre', libelle: 'Autre' },
];

const PROFESSIONS = [
  'Médecin', 'Chirurgien-dentiste', 'Sage-femme', 'Pharmacien', 'Autre',
];

interface Props {
  /** Clé de l'offre d'origine ; `alerte_aep` simplifie le formulaire. */
  offreCle: string;
  /** Intitulé lisible de l'offre, repris en tête de la fenêtre. */
  offreTitre: string;
  /** Les spécialités publiées, pour la liste déroulante. */
  specialites: string[];
  onFermer: () => void;
}

/**
 * La fenêtre « Être accompagné ».
 *
 * <p><b>Une seule fenêtre pour deux usages.</b> Demander un accompagnement et demander à
 * être prévenu de l'ouverture d'une période appellent le même point d'entrée et créent la
 * même demande : seule la clé d'offre les distingue. En faire deux composants aurait
 * dédoublé la validation, le consentement et la gestion d'erreur, pour une différence de
 * trois champs.</p>
 *
 * <p><b>L'alerte demande moins.</b> Un praticien qui veut seulement être prévenu d'une date
 * n'a aucune raison de raconter sa situation : lui réclamer son étape et un message ferait
 * abandonner ceux qui n'ont rien à dire.</p>
 */
export function ModaleDemande({ offreCle, offreTitre, specialites, onFermer }: Props) {
  const alerte = offreCle === OFFRE_ALERTE_AEP;

  const [champs, setChamps] = useState<DemandeRequest>({
    nom: '', email: '', telephoneWhatsapp: '', pays: '', profession: '',
    specialite: '', etape: 'autre', offreCle, message: '', consentementRgpd: false,
    piege: '',
  });
  const [erreurs, setErreurs] = useState<Record<string, string>>({});
  const [envoi, setEnvoi] = useState(false);
  const [confirmation, setConfirmation] = useState<string | null>(null);
  const [echec, setEchec] = useState<string | null>(null);

  // Horodatage d'ouverture : il sert au délai minimum de remplissage, côté serveur. Fourni
  // par le navigateur, donc falsifiable — il écarte les robots simples, rien de plus.
  const ouvertA = useRef(Date.now());
  const premierChamp = useRef<HTMLInputElement>(null);
  const fenetre = useRef<HTMLDivElement>(null);

  useEffect(() => {
    premierChamp.current?.focus();
    const auClavier = (e: KeyboardEvent) => { if (e.key === 'Escape') onFermer(); };
    document.addEventListener('keydown', auClavier);
    // Le défilement de la page derrière une fenêtre modale désoriente : on le fige.
    const precedent = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', auClavier);
      document.body.style.overflow = precedent;
    };
  }, [onFermer]);

  const modifier = (nom: keyof DemandeRequest, valeur: string | boolean) => {
    setChamps(c => ({ ...c, [nom]: valeur }));
    // L'erreur disparaît dès qu'on corrige : la laisser affichée pendant la saisie donne
    // l'impression que la correction n'a servi à rien.
    setErreurs(e => (e[nom] ? { ...e, [nom]: '' } : e));
  };

  const envoyer = async (e: React.FormEvent) => {
    e.preventDefault();
    const fautes = verifierDemande(champs);
    if (Object.keys(fautes).length > 0) {
      setErreurs(fautes);
      return;
    }
    setEnvoi(true);
    setEchec(null);
    try {
      const reponse = await demandeAccompagnementService.deposer({
        ...champs,
        ouvertDepuisMs: Date.now() - ouvertA.current,
      });
      setConfirmation(reponse.message);
    } catch (err: any) {
      setEchec(err.response?.data?.message
        ?? "Votre demande n'a pas pu être envoyée. Réessayez dans un instant.");
    } finally {
      setEnvoi(false);
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-end sm:items-center justify-center bg-brand-dark/60 p-0 sm:p-4"
      onMouseDown={(e) => { if (e.target === e.currentTarget) onFermer(); }}
    >
      <div
        ref={fenetre}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titre-demande"
        className="w-full sm:max-w-xl max-h-[92vh] overflow-y-auto rounded-t-2xl sm:rounded-2xl bg-white shadow-xl"
      >
        <div className="sticky top-0 flex items-start justify-between gap-4 bg-white px-5 sm:px-7 pt-6 pb-4 border-b border-slate-100">
          <div>
            <p className="text-[11px] font-bold tracking-widest text-brand-accent uppercase">
              {alerte ? "Alerte d'ouverture" : 'Être accompagné'}
            </p>
            <h2 id="titre-demande" className="mt-1 text-lg font-extrabold text-brand-dark">
              {offreTitre}
            </h2>
          </div>
          <button type="button" onClick={onFermer} aria-label="Fermer"
                  className="shrink-0 rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600">
            <X className="w-5 h-5" aria-hidden="true" />
          </button>
        </div>

        {confirmation ? (
          <div className="px-5 sm:px-7 py-10 text-center">
            <CheckCircle2 className="mx-auto w-10 h-10 text-success" aria-hidden="true" />
            <p className="mt-4 text-base font-semibold text-brand-dark">Demande enregistrée</p>
            <p className="mt-2 text-sm text-slate-600 leading-relaxed">{confirmation}</p>
            <button type="button" onClick={onFermer}
                    className="mt-6 px-6 py-2.5 rounded-full bg-brand text-white text-sm font-semibold hover:bg-brand-fonce">
              Fermer
            </button>
          </div>
        ) : (
          <form onSubmit={envoyer} noValidate className="px-5 sm:px-7 py-6 space-y-4">
            {alerte && (
              <p className="rounded-lg bg-brand-light px-4 py-3 text-sm text-slate-700">
                Nous vous préviendrons dès que le Centre National de Gestion publiera les dates
                de la prochaine période de dépôt.
              </p>
            )}

            <Champ id="nom" libelle="Nom complet" erreur={erreurs.nom} obligatoire>
              <input ref={premierChamp} id="nom" value={champs.nom} autoComplete="name"
                     onChange={(e) => modifier('nom', e.target.value)} className={CLASSE_CHAMP} />
            </Champ>

            <Champ id="email" libelle="Adresse e-mail" erreur={erreurs.email} obligatoire>
              <input id="email" type="email" value={champs.email} autoComplete="email"
                     onChange={(e) => modifier('email', e.target.value)} className={CLASSE_CHAMP} />
            </Champ>

            <div className="grid sm:grid-cols-2 gap-4">
              <Champ id="telephone" libelle="Téléphone WhatsApp"
                     aide="Avec l'indicatif, par exemple +242 06 123 45 67">
                <input id="telephone" value={champs.telephoneWhatsapp} autoComplete="tel"
                       onChange={(e) => modifier('telephoneWhatsapp', e.target.value)}
                       className={CLASSE_CHAMP} />
              </Champ>
              <Champ id="pays" libelle="Pays de résidence">
                <input id="pays" value={champs.pays} autoComplete="country-name"
                       onChange={(e) => modifier('pays', e.target.value)} className={CLASSE_CHAMP} />
              </Champ>
            </div>

            <div className="grid sm:grid-cols-2 gap-4">
              <Champ id="profession" libelle="Profession">
                <select id="profession" value={champs.profession}
                        onChange={(e) => modifier('profession', e.target.value)}
                        className={CLASSE_CHAMP}>
                  <option value="">—</option>
                  {PROFESSIONS.map(p => <option key={p} value={p}>{p}</option>)}
                </select>
              </Champ>
              <Champ id="specialite" libelle="Spécialité">
                {/* Une liste libre AVEC suggestions : les spécialités publiées couvrent la
                    commission nationale, pas les spécialités relevant des ARS. Un choix
                    fermé exclurait ces praticiens du formulaire. */}
                <input id="specialite" list="specialites-evc" value={champs.specialite}
                       onChange={(e) => modifier('specialite', e.target.value)}
                       className={CLASSE_CHAMP} />
                <datalist id="specialites-evc">
                  {specialites.map(s => <option key={s} value={s} />)}
                </datalist>
              </Champ>
            </div>

            {!alerte && (
              <>
                <Champ id="etape" libelle="Où en êtes-vous ?">
                  <select id="etape" value={champs.etape}
                          onChange={(e) => modifier('etape', e.target.value)}
                          className={CLASSE_CHAMP}>
                    {ETAPES.map(e => <option key={e.code} value={e.code}>{e.libelle}</option>)}
                  </select>
                </Champ>

                <Champ id="message" libelle="Votre message">
                  <textarea id="message" rows={4} value={champs.message}
                            onChange={(e) => modifier('message', e.target.value)}
                            className={CLASSE_CHAMP} />
                </Champ>
              </>
            )}

            {/* Champ piège : invisible pour qui lit la page, rempli par les robots qui
                remplissent tout. aria-hidden ET tabIndex -1 : un lecteur d'écran ne doit
                pas davantage l'annoncer qu'un œil ne doit le voir. */}
            <div aria-hidden="true" className="absolute left-[-9999px] w-px h-px overflow-hidden">
              <label htmlFor="site-web">Ne pas remplir</label>
              <input id="site-web" tabIndex={-1} autoComplete="off" value={champs.piege}
                     onChange={(e) => modifier('piege', e.target.value)} />
            </div>

            <label className="flex items-start gap-2.5 rounded-lg border border-slate-200 px-3 py-3 cursor-pointer">
              <input type="checkbox" checked={champs.consentementRgpd}
                     onChange={(e) => modifier('consentementRgpd', e.target.checked)}
                     className="mt-0.5 w-4 h-4 rounded border-slate-300 text-brand focus:ring-brand" />
              <span className="text-sm text-slate-700 leading-relaxed">
                J'accepte qu'Optimi Santé conserve ces informations pour traiter ma demande,
                conformément à la{' '}
                <Link to="/politique-confidentialite" target="_blank"
                      className="text-brand underline underline-offset-2">
                  politique de confidentialité
                </Link>.
                {/* role="alert" comme les autres erreurs : sans lui, celle-ci s'affichait
                    sans être annoncée, et un praticien qui n'y voit pas restait devant un
                    formulaire qui refuse de partir sans dire pourquoi. */}
                {erreurs.consentementRgpd && (
                  <span role="alert" className="mt-1 block text-xs font-semibold text-danger">
                    {erreurs.consentementRgpd}
                  </span>
                )}
              </span>
            </label>

            {echec && (
              <p role="alert" className="rounded-lg bg-danger/10 px-4 py-3 text-sm text-danger">
                {echec}
              </p>
            )}

            <div className="flex flex-col sm:flex-row gap-2 pt-1">
              <button type="submit" disabled={envoi}
                      className="inline-flex items-center justify-center gap-2 px-6 py-3 rounded-full bg-brand text-white text-sm font-semibold hover:bg-brand-fonce disabled:opacity-60">
                {envoi && <Loader2 className="w-4 h-4 animate-spin" aria-hidden="true" />}
                {alerte ? 'Me prévenir' : 'Envoyer ma demande'}
              </button>
              <button type="button" onClick={onFermer}
                      className="px-5 py-3 rounded-full border border-slate-300 text-sm font-medium text-slate-700 hover:bg-slate-50">
                Annuler
              </button>
            </div>

            <p className="text-xs text-slate-500 leading-relaxed">
              Optimi Santé n'est pas un organisme officiel et ne garantit pas l'issue des
              épreuves ni des décisions administratives.
            </p>
          </form>
        )}
      </div>
    </div>
  );
}

const CLASSE_CHAMP =
  'mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-sm focus:border-brand focus:ring-1 focus:ring-brand';

function Champ({ id, libelle, aide, erreur, obligatoire, children }: {
  id: string; libelle: string; aide?: string; erreur?: string;
  obligatoire?: boolean; children: React.ReactNode;
}) {
  return (
    <div>
      <label htmlFor={id} className="text-sm font-medium text-slate-700">
        {libelle}
        {obligatoire && <span aria-hidden="true" className="text-danger"> *</span>}
      </label>
      {children}
      {aide && !erreur && <p className="mt-1 text-xs text-slate-500">{aide}</p>}
      {erreur && (
        <p role="alert" className="mt-1 text-xs font-semibold text-danger">{erreur}</p>
      )}
    </div>
  );
}
