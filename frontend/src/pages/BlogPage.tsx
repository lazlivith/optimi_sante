import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ArrowRight, CalendarDays, MapPin, Newspaper } from 'lucide-react';
import { blogService, type BlogPost } from '../api/blogService';
import { usePageMeta } from '../hooks/usePageMeta';
import { formaterPeriode, estPasse } from '../lib/dates';

/**
 * Le blog : actualites d'Optimi Sante et agenda des evenements.
 *
 * Deux sections plutot qu'une liste unique. Un congres a venir se lit pour s'y inscrire,
 * un article pour s'informer : les melanger obligerait le visiteur a trier lui-meme des
 * dates passees et futures pour retrouver ce qui le concerne encore.
 */
export function BlogPage() {
  usePageMeta(
    'Actualités & événements',
    "Congrès, salons et actualités d'Optimi Santé : l'agenda des rendez-vous du secteur médical et les nouvelles de la plateforme.");

  const { data: publications = [], isLoading } = useQuery({
    queryKey: ['blog'],
    queryFn: () => blogService.publications(),
  });

  const aVenir = publications.filter(p => p.isEvent && !estPasse(p.eventEndsOn ?? p.eventStartsOn));
  const reste = publications.filter(p => !aVenir.includes(p));

  return (
    <div className="bg-brand-cream min-h-screen">
      <header className="bg-brand-dark text-white">
        <div className="container mx-auto px-6 py-14 max-w-5xl">
          <p className="text-xs font-bold uppercase tracking-wider text-brand-light/70 mb-3">
            Le blog
          </p>
          <h1 className="text-3xl md:text-4xl font-bold mb-3">Actualités & événements</h1>
          <p className="text-slate-300 max-w-2xl">
            Les congrès et salons où nous retrouver, et les nouvelles de la plateforme.
          </p>
        </div>
      </header>

      <div className="container mx-auto px-6 py-12 max-w-5xl">
        {isLoading && <p className="text-slate-500">Chargement des publications…</p>}

        {!isLoading && publications.length === 0 && (
          <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center">
            <Newspaper className="w-8 h-8 text-slate-300 mx-auto mb-3" />
            <p className="font-semibold text-brand-dark mb-1">Aucune publication pour le moment</p>
            <p className="text-sm text-slate-500">
              Les prochains événements et actualités seront annoncés ici.
            </p>
          </div>
        )}

        {aVenir.length > 0 && (
          <section className="mb-12">
            <h2 className="text-lg font-bold text-brand-dark mb-4">À venir</h2>
            <div className="grid md:grid-cols-2 gap-6">
              {aVenir.map(publication => (
                <CarteEvenement key={publication.id} publication={publication} />
              ))}
            </div>
          </section>
        )}

        {reste.length > 0 && (
          <section>
            <h2 className="text-lg font-bold text-brand-dark mb-4">
              {aVenir.length > 0 ? 'Toutes les publications' : 'Publications'}
            </h2>
            <div className="grid md:grid-cols-2 gap-6">
              {reste.map(publication => (
                <CarteArticle key={publication.id} publication={publication} />
              ))}
            </div>
          </section>
        )}
      </div>
    </div>
  );
}

/** La carte d'un evenement a venir : ce que le visiteur doit savoir avant de cliquer. */
function CarteEvenement({ publication }: { publication: BlogPost }) {
  return (
    <article className="bg-gradient-to-br from-brand to-brand-dark rounded-2xl p-8 text-white flex flex-col">
      <p className="text-brand-light/80 text-xs font-bold uppercase mb-2">À venir</p>
      <h3 className="text-2xl font-bold mb-2">{publication.title}</h3>

      <p className="text-sm text-brand-light mb-4 flex flex-wrap items-center gap-x-2 gap-y-1">
        {publication.eventLocation && (
          <span className="inline-flex items-center gap-1.5">
            <MapPin className="w-4 h-4 shrink-0" />
            {publication.eventLocation}
          </span>
        )}
        {publication.eventLocation && <span aria-hidden="true">·</span>}
        <span className="inline-flex items-center gap-1.5">
          <CalendarDays className="w-4 h-4 shrink-0" />
          {formaterPeriode(publication.eventStartsOn, publication.eventEndsOn)}
        </span>
      </p>

      {publication.excerpt && (
        <p className="text-sm text-brand-light/90 mb-5 line-clamp-3">{publication.excerpt}</p>
      )}

      <Link
        to={`/blog/${publication.slug}`}
        className="mt-auto self-start inline-flex items-center gap-2 bg-white text-brand font-bold px-4 py-2 rounded-full text-sm hover:bg-brand-light transition-colors"
      >
        En savoir plus <ArrowRight className="w-4 h-4" />
      </Link>
    </article>
  );
}

/** Un article, ou un evenement deja passe : meme presentation, sobre. */
function CarteArticle({ publication }: { publication: BlogPost }) {
  return (
    <article className="bg-white rounded-2xl border border-slate-200 overflow-hidden flex flex-col">
      {publication.coverImageUrl && (
        <img
          src={publication.coverImageUrl}
          alt=""
          className="w-full h-44 object-cover"
          loading="lazy"
        />
      )}
      <div className="p-6 flex flex-col flex-1">
        {publication.isEvent && (
          <p className="text-xs font-semibold text-slate-500 mb-2 inline-flex items-center gap-1.5">
            <CalendarDays className="w-3.5 h-3.5 shrink-0" />
            {formaterPeriode(publication.eventStartsOn, publication.eventEndsOn)}
            {publication.eventLocation && ` · ${publication.eventLocation}`}
          </p>
        )}
        <h3 className="text-lg font-bold text-brand-dark mb-2">{publication.title}</h3>
        {publication.excerpt && (
          <p className="text-sm text-slate-600 mb-4 line-clamp-3">{publication.excerpt}</p>
        )}
        <Link
          to={`/blog/${publication.slug}`}
          className="mt-auto self-start inline-flex items-center gap-2 text-brand font-semibold text-sm hover:text-brand-fonce transition-colors"
        >
          Lire la suite <ArrowRight className="w-4 h-4" />
        </Link>
      </div>
    </article>
  );
}
