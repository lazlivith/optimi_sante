import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ArrowLeft, CalendarDays, MapPin } from 'lucide-react';
import { blogService } from '../api/blogService';
import { usePageMeta } from '../hooks/usePageMeta';
import { formaterPeriode } from '../lib/dates';

/**
 * Une publication du blog, lue en entier.
 *
 * Le contenu est du texte saisi par la redaction : il est rendu tel quel, en preservant les
 * retours a la ligne. Surtout pas via `dangerouslySetInnerHTML` — ce serait ouvrir une
 * injection de balises sur une page publique pour le seul confort de la mise en forme.
 */
export function BlogPostPage() {
  const { slug = '' } = useParams();

  const { data: publication, isLoading, isError } = useQuery({
    queryKey: ['blog', slug],
    queryFn: () => blogService.parSlug(slug),
    enabled: slug.length > 0,
    retry: false,
  });

  usePageMeta(
    publication?.title ?? 'Actualités',
    publication?.excerpt ?? "Actualités et événements d'Optimi Santé.");

  return (
    <div className="bg-brand-cream min-h-screen">
      <div className="container mx-auto px-6 py-10 max-w-3xl">
        <Link
          to="/blog"
          className="inline-flex items-center gap-2 text-sm text-slate-500 hover:text-brand transition-colors mb-6"
        >
          <ArrowLeft className="w-4 h-4" /> Toutes les publications
        </Link>

        {isLoading && <p className="text-slate-500">Chargement…</p>}

        {isError && (
          <div className="bg-white rounded-2xl border border-slate-200 p-10 text-center">
            <p className="font-semibold text-brand-dark mb-1">Publication introuvable</p>
            <p className="text-sm text-slate-500">
              Elle a peut-être été retirée du site depuis que ce lien a été partagé.
            </p>
          </div>
        )}

        {publication && (
          <article className="bg-white rounded-2xl border border-slate-200 overflow-hidden">
            {publication.coverImageUrl && (
              <img src={publication.coverImageUrl} alt="" className="w-full h-64 object-cover" />
            )}
            <div className="p-8">
              <h1 className="text-3xl font-bold text-brand-dark mb-3">{publication.title}</h1>

              {publication.isEvent && (
                <p className="text-sm text-brand font-semibold mb-5 flex flex-wrap items-center gap-x-2 gap-y-1">
                  <span className="inline-flex items-center gap-1.5">
                    <CalendarDays className="w-4 h-4 shrink-0" />
                    {formaterPeriode(publication.eventStartsOn, publication.eventEndsOn)}
                  </span>
                  {publication.eventLocation && (
                    <>
                      <span aria-hidden="true">·</span>
                      <span className="inline-flex items-center gap-1.5">
                        <MapPin className="w-4 h-4 shrink-0" />
                        {publication.eventLocation}
                      </span>
                    </>
                  )}
                </p>
              )}

              {publication.excerpt && (
                <p className="text-lg text-slate-600 mb-6">{publication.excerpt}</p>
              )}

              <div className="text-slate-700 leading-relaxed whitespace-pre-line">
                {publication.content}
              </div>
            </div>
          </article>
        )}
      </div>
    </div>
  );
}
