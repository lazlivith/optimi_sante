import { Link, Navigate, useParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight } from 'lucide-react';
import { usePageMeta } from '../../hooks/usePageMeta';
import { SERVICES, serviceParSlug } from './contenu';

/**
 * Un service, et lui seul.
 *
 * <p>Les huit prestations tenaient dans une page unique parcourue par un sommaire d'ancres :
 * choisir « Devis professionnel » dans le menu déroulait toute l'offre et déposait le visiteur
 * au milieu d'un texte qui parlait d'autre chose avant et après. Chaque service a désormais son
 * adresse et n'affiche que ce qui le concerne.</p>
 *
 * <p>Les autres services restent proposés en bas de page : on arrive souvent ici par le menu,
 * sans avoir vu l'ensemble de l'offre, et une page qui ne mène nulle part est une impasse.</p>
 */
export function ServiceDetailPage() {
  const { slug } = useParams();
  const service = serviceParSlug(slug);

  usePageMeta(service ? service.titre : 'Nos services', service?.meta);

  // Une adresse inventée renvoie vers l'ensemble des services plutôt que vers une page
  // d'erreur : le visiteur cherchait nos prestations, il les trouve.
  if (!service) return <Navigate to="/services" replace />;

  const autres = SERVICES.filter((s) => s.slug !== service.slug);

  return (
    <div className="bg-slate-50">
      <section className="bg-brand-dark text-white">
        <div className="container mx-auto px-4 md:px-8 py-10 md:py-14 max-w-4xl">
          <Link
            to="/services"
            className="inline-flex items-center gap-2 text-sm text-slate-300 hover:text-white transition-colors mb-6"
          >
            <ArrowLeft className="w-4 h-4" aria-hidden="true" /> Tous nos services
          </Link>

          <p className="text-[11px] font-bold uppercase tracking-wider text-brand mb-3">
            {service.famille}
          </p>
          <h1 className="flex items-start gap-4 text-2xl md:text-4xl font-bold leading-tight">
            <span className="mt-0.5 flex h-11 w-11 shrink-0 items-center justify-center rounded-2xl bg-brand text-white md:h-14 md:w-14">
              <service.icon className="h-5 w-5 md:h-7 md:w-7" aria-hidden="true" />
            </span>
            {service.titre}
          </h1>
          <p className="mt-4 text-slate-300 text-base md:text-lg leading-relaxed max-w-2xl">
            {service.resume}
          </p>
        </div>
      </section>

      <div className="container mx-auto px-4 md:px-8 py-10 md:py-12 max-w-4xl">
        <service.Contenu />
      </div>

      <div className="border-t border-slate-200 bg-white">
        <div className="container mx-auto px-4 md:px-8 py-10 max-w-4xl">
          <h2 className="text-lg font-bold text-brand-dark mb-5">Nos autres services</h2>
          <ul className="grid gap-3 sm:grid-cols-2">
            {autres.map((autre) => (
              <li key={autre.slug}>
                <Link
                  to={`/services/${autre.slug}`}
                  className="group flex items-start gap-3 rounded-2xl border border-slate-200 p-4 transition-colors hover:border-brand hover:bg-brand-cream"
                >
                  <span className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-brand-light text-brand">
                    <autre.icon className="h-4 w-4" aria-hidden="true" />
                  </span>
                  <span className="min-w-0">
                    <span className="block font-semibold text-brand-dark">{autre.titre}</span>
                    <span className="block text-sm text-slate-500">{autre.resume}</span>
                  </span>
                  <ArrowRight
                    aria-hidden="true"
                    className="ml-auto mt-1 h-4 w-4 shrink-0 text-slate-300 transition-colors group-hover:text-brand"
                  />
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
