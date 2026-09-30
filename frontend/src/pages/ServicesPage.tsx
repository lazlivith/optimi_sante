import { Link } from 'react-router-dom';
import { LEGAL } from '../config/legal';
import { ArrowRight } from 'lucide-react';
import { usePageMeta } from '../hooks/usePageMeta';
import { SERVICES } from './services/contenu';

/**
 * L'ensemble des services, d'un coup d'œil.
 *
 * <p>Cette page portait auparavant les huit prestations bout à bout, parcourues par un
 * sommaire d'ancres. Elle en présente désormais la carte : chaque vignette mène à la page du
 * service, qui n'affiche que ce qui le concerne.</p>
 *
 * <p>Le regroupement par métier n'est pas décoratif : un médecin en mobilité et un acheteur
 * d'équipement ne cherchent pas la même chose, et le titre de colonne leur dit immédiatement
 * laquelle des deux moitiés les concerne.</p>
 */
export function ServicesPage() {
  usePageMeta('Nos services & accompagnement',
    "Accompagnement visa, assurance rapatriement, hébergement et accueil : comment Optimi Santé "
    + "accompagne les médecins vers les CHU français, et à quelles conditions.");

  const familles = ['Mobilité médicale', 'Négoce & partenaires'] as const;

  return (
    <div className="bg-slate-50">
      <section className="bg-brand-dark text-white">
        <div className="container mx-auto px-4 md:px-8 py-14 max-w-4xl">
          <p className="text-[11px] font-bold uppercase tracking-wider text-brand mb-3">
            Nos services &amp; accompagnement
          </p>
          <h1 className="text-3xl md:text-5xl font-bold leading-tight mb-4">
            Vous vous formez.<br />Nous nous occupons du reste.
          </h1>
          <p className="text-slate-300 text-lg leading-relaxed max-w-2xl">
            Un stage clinique en France, ce n'est pas seulement un programme médical : c'est un
            dossier consulaire, une assurance, un logement et une arrivée à organiser. Voici ce
            qu'Optimi Santé prend en charge, et ce que cela coûte.
          </p>
          <div className="flex flex-wrap gap-3 mt-7">
            <Link
              to="/formations"
              className="inline-flex items-center gap-2 px-6 py-3 rounded-xl bg-brand text-white font-bold hover:bg-brand-fonce transition-colors"
            >
              Voir les formations <ArrowRight className="w-4 h-4" />
            </Link>
            <a
              href={LEGAL.whatsapp}
              target="_blank" rel="noopener noreferrer"
              className="inline-flex items-center gap-2 px-5 py-3 rounded-xl bg-white/10 hover:bg-white/20 text-white font-semibold transition-colors"
            >
              Parler à un conseiller
            </a>
          </div>
        </div>
      </section>

      <div className="container mx-auto px-4 md:px-8 py-12 max-w-5xl space-y-12">
        {familles.map((famille) => (
          <section key={famille}>
            <h2 className="text-xs font-bold uppercase tracking-wider text-slate-400 mb-4">
              {famille}
            </h2>
            <ul className="grid gap-4 md:grid-cols-2">
              {SERVICES.filter((s) => s.famille === famille).map((service) => (
                <li key={service.slug}>
                  <Link
                    to={`/services/${service.slug}`}
                    className="group flex h-full items-start gap-4 rounded-2xl border border-slate-200 bg-white p-5 transition-colors hover:border-brand hover:bg-brand-cream"
                  >
                    <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-brand text-white">
                      <service.icon className="h-5 w-5" aria-hidden="true" />
                    </span>
                    <span className="min-w-0">
                      <span className="block font-bold text-brand-dark">{service.titre}</span>
                      <span className="mt-1 block text-sm leading-relaxed text-slate-600">
                        {service.resume}
                      </span>
                      <span className="mt-3 inline-flex items-center gap-1.5 text-sm font-semibold text-brand">
                        En savoir plus
                        <ArrowRight
                          aria-hidden="true"
                          className="h-4 w-4 transition-transform group-hover:translate-x-0.5"
                        />
                      </span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </div>
    </div>
  );
}
