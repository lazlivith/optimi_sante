import { useEffect, useState } from 'react';
import { Loader2, Newspaper, Plus, X, Pencil, Trash2, Eye, EyeOff, ExternalLink } from 'lucide-react';
import { adminBlogService, type BlogPost, type BlogPostPayload } from '../../api/blogService';
import { Toast, type ToastType } from '../../components/common/Toast';
import { PageHeader } from '../../components/common/PageHeader';
import { StatusBadge } from '../../components/common/StatusBadge';
import { EmptyState } from '../../components/common/EmptyState';
import { formaterPeriode } from '../../lib/dates';

const FORMULAIRE_VIDE: BlogPostPayload = {
  title: '', excerpt: '', content: '', coverImageUrl: '',
  eventLocation: '', eventStartsOn: '', eventEndsOn: '', published: false,
};

/**
 * La redaction du blog : actualites et annonces d'evenements.
 *
 * Une publication reste un brouillon tant qu'elle n'est pas mise en ligne explicitement.
 * Une annonce s'ecrit rarement d'un trait, et un texte a demi redige ne doit pas apparaitre
 * sur la page d'accueil entre deux enregistrements.
 */
export function AdminBlogPage() {
  const [publications, setPublications] = useState<BlogPost[]>([]);
  const [chargement, setChargement] = useState(true);
  const [enCoursEnvoi, setEnCoursEnvoi] = useState(false);
  const [modaleOuverte, setModaleOuverte] = useState(false);
  /** Publication en cours de modification, ou `null` pour une creation. */
  const [enEdition, setEnEdition] = useState<BlogPost | null>(null);
  const [form, setForm] = useState<BlogPostPayload>(FORMULAIRE_VIDE);
  const [toast, setToast] = useState<{ message: string; type: ToastType } | null>(null);

  const charger = async () => {
    setChargement(true);
    try {
      setPublications(await adminBlogService.lister());
    } catch {
      setToast({ message: 'Impossible de charger les publications.', type: 'error' });
    } finally {
      setChargement(false);
    }
  };

  useEffect(() => { charger(); }, []);

  const ouvrirCreation = () => {
    setEnEdition(null);
    setForm(FORMULAIRE_VIDE);
    setModaleOuverte(true);
  };

  const ouvrirEdition = (publication: BlogPost) => {
    setEnEdition(publication);
    setForm({
      title: publication.title,
      excerpt: publication.excerpt ?? '',
      content: publication.content,
      coverImageUrl: publication.coverImageUrl ?? '',
      eventLocation: publication.eventLocation ?? '',
      eventStartsOn: publication.eventStartsOn ?? '',
      eventEndsOn: publication.eventEndsOn ?? '',
      published: publication.isPublished,
    });
    setModaleOuverte(true);
  };

  const enregistrer = async (e: React.FormEvent) => {
    e.preventDefault();
    setEnCoursEnvoi(true);
    // Les champs laisses vides partent a `null` plutot qu'en chaine vide : le serveur
    // distingue « pas de lieu » d'un lieu dont le libelle serait vide.
    const charge: BlogPostPayload = {
      ...form,
      excerpt: form.excerpt?.trim() || null,
      coverImageUrl: form.coverImageUrl?.trim() || null,
      eventLocation: form.eventLocation?.trim() || null,
      eventStartsOn: form.eventStartsOn || null,
      eventEndsOn: form.eventEndsOn || null,
    };
    try {
      if (enEdition) {
        await adminBlogService.modifier(enEdition.id, charge);
        setToast({ message: 'Publication mise à jour.', type: 'success' });
      } else {
        await adminBlogService.creer(charge);
        setToast({ message: 'Publication créée.', type: 'success' });
      }
      setModaleOuverte(false);
      charger();
    } catch (err: any) {
      setToast({
        message: err.response?.data?.message || "Erreur lors de l'enregistrement.",
        type: 'error',
      });
    } finally {
      setEnCoursEnvoi(false);
    }
  };

  const basculerMiseEnLigne = async (publication: BlogPost) => {
    try {
      await adminBlogService.changerMiseEnLigne(publication.id, !publication.isPublished);
      setToast({
        message: publication.isPublished ? 'Publication retirée du site.' : 'Publication mise en ligne.',
        type: 'success',
      });
      charger();
    } catch {
      setToast({ message: 'Erreur lors de la mise à jour.', type: 'error' });
    }
  };

  const supprimer = async (publication: BlogPost) => {
    if (!window.confirm(`Supprimer définitivement « ${publication.title} » ?`)) return;
    try {
      await adminBlogService.supprimer(publication.id);
      setToast({ message: 'Publication supprimée.', type: 'success' });
      charger();
    } catch {
      setToast({ message: 'Erreur lors de la suppression.', type: 'error' });
    }
  };

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-6xl">
      <PageHeader
        title="Blog & événements"
        subtitle="Actualités et annonces publiées sur le site. Le prochain événement en ligne s'affiche sur la page d'accueil."
        actions={
          <button
            onClick={ouvrirCreation}
            className="inline-flex items-center gap-2 px-4 py-2.5 bg-brand text-white font-bold rounded-xl hover:bg-brand-fonce transition-colors"
          >
            <Plus className="w-4 h-4" /> Nouvelle publication
          </button>
        }
      />

      <div className="bg-white rounded-2xl border border-slate-200 shadow-sm overflow-hidden">
        {chargement ? (
          <div className="p-12 flex justify-center"><Loader2 className="w-8 h-8 text-brand animate-spin" /></div>
        ) : publications.length === 0 ? (
          <EmptyState icon={Newspaper} title="Aucune publication pour le moment." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-500 border-b border-slate-200 uppercase text-[11px] font-bold tracking-wider">
                <tr>
                  <th className="px-6 py-4">Titre</th>
                  <th className="px-6 py-4">Type</th>
                  <th className="px-6 py-4">Dates</th>
                  <th className="px-6 py-4">Statut</th>
                  <th className="px-6 py-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {publications.map((p) => (
                  <tr key={p.id} className={`hover:bg-slate-50/50 transition-colors ${!p.isPublished ? 'opacity-70' : ''}`}>
                    <td className="px-6 py-4">
                      <span className="font-semibold text-brand-dark">{p.title}</span>
                      <span className="block text-xs text-slate-400 font-mono">/blog/{p.slug}</span>
                    </td>
                    <td className="px-6 py-4 text-slate-600">{p.isEvent ? 'Événement' : 'Article'}</td>
                    <td className="px-6 py-4 text-xs text-slate-500">
                      {p.isEvent
                        ? formaterPeriode(p.eventStartsOn, p.eventEndsOn)
                        : new Date(p.createdAt).toLocaleDateString('fr-FR')}
                      {p.eventLocation && <span className="block">{p.eventLocation}</span>}
                    </td>
                    <td className="px-6 py-4">
                      <StatusBadge status={p.isPublished ? 'ACTIVE' : 'INACTIVE'} />
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center justify-end gap-2">
                        {p.isPublished && (
                          <a
                            href={`/blog/${p.slug}`} target="_blank" rel="noopener noreferrer"
                            className="inline-flex items-center justify-center p-2 border border-slate-200 rounded-lg text-slate-600 hover:bg-slate-50 transition-colors"
                            title="Voir sur le site"
                          >
                            <ExternalLink className="w-4 h-4" />
                          </a>
                        )}
                        <button
                          onClick={() => basculerMiseEnLigne(p)}
                          className="inline-flex items-center justify-center p-2 border border-slate-200 rounded-lg text-slate-600 hover:bg-slate-50 transition-colors"
                          title={p.isPublished ? 'Retirer du site' : 'Mettre en ligne'}
                        >
                          {p.isPublished ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                        </button>
                        <button
                          onClick={() => ouvrirEdition(p)}
                          className="inline-flex items-center justify-center p-2 border border-slate-200 rounded-lg text-slate-600 hover:bg-slate-50 transition-colors"
                          title="Modifier"
                        >
                          <Pencil className="w-4 h-4" />
                        </button>
                        <button
                          onClick={() => supprimer(p)}
                          className="inline-flex items-center justify-center p-2 border border-slate-200 rounded-lg text-danger hover:bg-danger/5 transition-colors"
                          title="Supprimer"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {modaleOuverte && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-2xl shadow-xl w-full max-w-2xl max-h-[90vh] overflow-y-auto p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-lg font-bold text-slate-900">
                {enEdition ? 'Modifier la publication' : 'Nouvelle publication'}
              </h2>
              <button onClick={() => setModaleOuverte(false)} className="text-slate-400 hover:text-slate-600">
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={enregistrer} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Titre</label>
                <input
                  type="text" required maxLength={200} value={form.title}
                  onChange={e => setForm({ ...form, title: e.target.value })}
                  placeholder="ex. Congrès National de Médecine"
                  className="w-full rounded-md border-slate-300 border p-2.5"
                />
                {enEdition && (
                  // Le slug est fixe a la creation : un lien deja partage ne doit pas casser
                  // parce qu'une coquille a ete corrigee dans le titre.
                  <p className="text-xs text-slate-400 mt-1">
                    Adresse publique : /blog/{enEdition.slug} — inchangée par une modification du titre.
                  </p>
                )}
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">
                  Chapô <span className="text-slate-400 font-normal">(résumé affiché dans la liste)</span>
                </label>
                <textarea
                  rows={2} maxLength={400} value={form.excerpt ?? ''}
                  onChange={e => setForm({ ...form, excerpt: e.target.value })}
                  className="w-full rounded-md border-slate-300 border p-2.5"
                />
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">Contenu</label>
                <textarea
                  rows={8} required value={form.content}
                  onChange={e => setForm({ ...form, content: e.target.value })}
                  className="w-full rounded-md border-slate-300 border p-2.5"
                />
                <p className="text-xs text-slate-400 mt-1">
                  Texte simple : les retours à la ligne sont conservés à l'affichage.
                </p>
              </div>

              <div>
                <label className="block text-sm font-medium text-slate-700 mb-1">
                  Image de couverture <span className="text-slate-400 font-normal">(adresse, facultatif)</span>
                </label>
                <input
                  type="url" value={form.coverImageUrl ?? ''}
                  onChange={e => setForm({ ...form, coverImageUrl: e.target.value })}
                  placeholder="https://…"
                  className="w-full rounded-md border-slate-300 border p-2.5"
                />
              </div>

              <fieldset className="border border-slate-200 rounded-xl p-4">
                <legend className="px-2 text-sm font-semibold text-slate-700">Événement</legend>
                <p className="text-xs text-slate-500 mb-3">
                  Renseignez une date de début pour que la publication devienne un événement :
                  elle rejoint alors l'agenda, et la plus proche s'affiche sur la page d'accueil.
                </p>
                <div className="grid sm:grid-cols-3 gap-4">
                  <div className="sm:col-span-3">
                    <label className="block text-sm font-medium text-slate-700 mb-1">Lieu</label>
                    <input
                      type="text" maxLength={160} value={form.eventLocation ?? ''}
                      onChange={e => setForm({ ...form, eventLocation: e.target.value })}
                      placeholder="ex. Bordeaux"
                      className="w-full rounded-md border-slate-300 border p-2.5"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Début</label>
                    <input
                      type="date" value={form.eventStartsOn ?? ''}
                      onChange={e => setForm({ ...form, eventStartsOn: e.target.value })}
                      className="w-full rounded-md border-slate-300 border p-2.5"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-slate-700 mb-1">Fin</label>
                    <input
                      type="date" value={form.eventEndsOn ?? ''}
                      min={form.eventStartsOn || undefined}
                      onChange={e => setForm({ ...form, eventEndsOn: e.target.value })}
                      className="w-full rounded-md border-slate-300 border p-2.5"
                    />
                  </div>
                </div>
              </fieldset>

              <label className="flex items-center gap-2.5 text-sm text-slate-700">
                <input
                  type="checkbox" checked={form.published ?? false}
                  onChange={e => setForm({ ...form, published: e.target.checked })}
                  className="w-4 h-4 rounded border-slate-300"
                />
                Mettre en ligne sur le site
              </label>

              <div className="flex justify-end gap-3 pt-2">
                <button
                  type="button" onClick={() => setModaleOuverte(false)}
                  className="px-4 py-2.5 border border-slate-200 rounded-xl text-slate-600 font-semibold hover:bg-slate-50 transition-colors"
                >
                  Annuler
                </button>
                <button
                  type="submit" disabled={enCoursEnvoi}
                  className="inline-flex items-center gap-2 px-4 py-2.5 bg-brand text-white font-bold rounded-xl hover:bg-brand-fonce transition-colors disabled:opacity-60"
                >
                  {enCoursEnvoi && <Loader2 className="w-4 h-4 animate-spin" />}
                  {enEdition ? 'Enregistrer' : 'Créer'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {toast && <Toast message={toast.message} type={toast.type} onClose={() => setToast(null)} />}
    </div>
  );
}
