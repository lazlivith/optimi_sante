import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Search } from 'lucide-react';
import { useCategoriesBoutique } from '../../hooks/useCategoriesBoutique';

/**
 * La barre de recherche de l'en-tête : catégorie, terme, bouton.
 *
 * <p><b>Un seul formulaire pour les deux largeurs.</b> Le sélecteur de catégorie disparaît
 * sous 1 024 px (<code>hidden lg:flex</code>) et le bouton perd son libellé au profit de la
 * seule loupe. Écrire deux formulaires aurait donné deux comportements de soumission à
 * maintenir, et c'est la soumission qui porte toute la logique.</p>
 *
 * <p><b>L'état part dans l'adresse, pas dans un état partagé.</b> Une recherche se partage,
 * se met en favori et survit au rechargement. La barre ne retient donc rien : elle écrit
 * <code>?search=</code> et <code>?category=</code>, et la page de résultats les lit.</p>
 *
 * <p><b>Pourquoi <code>category</code> et non <code>categoryId</code>.</b> C'est le nom que
 * <code>CatalogPage</code> lit déjà depuis l'origine, et que les vignettes de l'accueil
 * écrivent. Le renommer aurait cassé des liens existants pour aligner un mot.</p>
 */
export function BarreRecherche() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const { data: categories } = useCategoriesBoutique();

  const [terme, setTerme] = useState('');
  const [categorie, setCategorie] = useState('');

  /**
   * Sur une page de rayon, le sélecteur s'ouvre sur le rayon en cours.
   *
   * <p>Le slug est lu dans le chemin et non par <code>useParams</code> : l'en-tête est rendu
   * au-dessus des routes, où <code>useParams</code> ne rend rien. Un rayon absent de la liste
   * — il n'a aucun produit — laisse « Toutes catégories », ce qui est la vérité.</p>
   */
  useEffect(() => {
    const trouve = pathname.match(/^\/category\/([^/]+)/);
    if (!trouve || !categories) {
      return;
    }
    const slug = decodeURIComponent(trouve[1]);
    const rayon = categories.find(c => c.slug === slug);
    setCategorie(rayon ? rayon.id : '');
  }, [pathname, categories]);

  const soumettre = (e: React.FormEvent) => {
    e.preventDefault();
    const q = terme.trim();
    // Un formulaire entièrement vide ne mène nulle part : l'envoyer afficherait le catalogue
    // entier en donnant à croire qu'une recherche a eu lieu.
    if (!q && !categorie) {
      return;
    }
    const params = new URLSearchParams();
    if (q) params.set('search', q);
    if (categorie) params.set('category', categorie);
    navigate(`/catalog?${params.toString()}`);
  };

  return (
    <form
      role="search"
      onSubmit={soumettre}
      className="order-last flex h-12 w-full min-w-0 items-stretch overflow-hidden rounded-xl
                 border-[1.5px] border-brand-dark bg-white
                 lg:order-none lg:h-[52px] lg:w-auto lg:flex-1 lg:max-w-2xl"
    >
      {/* Le sélecteur ne paraît qu'à partir de 1 024 px : la maquette du téléphone ne le
          porte pas, et il mangerait la moitié d'un écran de 375 px. */}
      <label className="hidden shrink-0 basis-[200px] lg:flex bg-brand-cream border-r border-slate-200">
        <span className="sr-only">Catégorie</span>
        <select
          value={categorie}
          onChange={(e) => setCategorie(e.target.value)}
          className="w-full cursor-pointer border-0 bg-transparent px-3.5 text-[15px]
                     text-brand-dark focus:outline-none focus-visible:ring-2
                     focus-visible:ring-brand/40 focus-visible:ring-inset"
        >
          <option value="">Toutes catégories</option>
          {categories?.map((cat) => (
            <option key={cat.id} value={cat.id}>{cat.name}</option>
          ))}
        </select>
      </label>

      <label className="flex min-w-0 flex-1">
        {/* « Rechercher un produit » et non « Rechercher » : le bouton porte déjà ce
            second nom, et deux contrôles au même nom dans un même formulaire laissent un
            lecteur d'écran annoncer deux fois la même chose sans dire laquelle écrit et
            laquelle envoie. Le libellé d'un champ dit ce qu'on y met. */}
        <span className="sr-only">Rechercher un produit</span>
        {/* 16 px sur téléphone, et pas moins : en dessous, iPhone agrandit toute la page a
            la mise au point du champ, et le visiteur se retrouve dans un site zoomé. */}
        <input
          type="search"
          value={terme}
          onChange={(e) => setTerme(e.target.value)}
          placeholder="Rechercher un produit, une référence…"
          className="min-w-0 flex-1 border-0 px-3.5 text-base text-brand-dark
                     placeholder:text-slate-500 focus:outline-none focus-visible:ring-2
                     focus-visible:ring-brand/40 focus-visible:ring-inset lg:px-4 lg:text-[15px]"
        />
      </label>

      {/* Sur téléphone le bouton est carré de 48 px : la cible dépasse les 44 px exigés, sans
          libellé qui prendrait la place du champ. */}
      <button
        type="submit"
        aria-label="Rechercher"
        className="flex shrink-0 basis-12 items-center justify-center gap-2 border-0
                   bg-brand-dark px-0 font-bold text-white
                   hover:bg-brand-fonce focus-visible:outline-none focus-visible:ring-2
                   focus-visible:ring-brand/60 focus-visible:ring-inset
                   lg:basis-auto lg:px-[22px]"
      >
        <Search className="h-[18px] w-[18px] shrink-0" aria-hidden="true" strokeWidth={2.2} />
        <span className="hidden lg:inline">Rechercher</span>
      </button>
    </form>
  );
}
