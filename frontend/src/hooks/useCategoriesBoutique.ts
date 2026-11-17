import { useQuery } from '@tanstack/react-query';
import { catalogService, type Category } from '../api/catalogService';

/**
 * Les catégories telles que la boutique les propose dans un choix — une seule fois.
 *
 * <p><b>Pourquoi un hook et pas une requête par écran.</b> Cette liste n'est pas le contenu
 * brut de la route : trois décisions y sont prises, et chacune a une raison. Deux libellés
 * identiques désignent le même rayon, et on garde celui qui a des produits — sinon le choix
 * mène vers le jumeau vide. Un rayon sans produit est une impasse : le proposer n'allonge
 * qu'une liste de plus de deux cents entrées. Et l'ordre est alphabétique, parce qu'une liste
 * aussi longue ne se parcourt pas autrement.</p>
 *
 * <p>Ces trois décisions vivaient dans <code>CatalogPage</code>. La barre de recherche de
 * l'en-tête en a besoin des mêmes : les réécrire lui aurait donné une <b>seconde</b> liste de
 * catégories, libre de diverger de la première. C'est exactement ce qui est arrivé à la barre
 * de navigation, écrite deux fois, et aux visuels factices, connus de deux côtés sur trois.
 * Une seule clé de cache, donc une seule requête, et la même liste partout.</p>
 */
export function useCategoriesBoutique() {
  return useQuery({
    queryKey: ['categories'],
    queryFn: async (): Promise<Category[]> => {
      const cats = await catalogService.getCategories();
      if (!cats) return [];

      const uniqueMap = new Map<string, Category>();
      cats.forEach(cat => {
        const decodedName = cat.name.replace(/&amp;/g, '&');
        const dejaVu = uniqueMap.get(decodedName);
        if (!dejaVu || (dejaVu.productCount ?? 0) < (cat.productCount ?? 0)) {
          uniqueMap.set(decodedName, { ...cat, name: decodedName });
        }
      });

      return Array.from(uniqueMap.values())
        .filter(cat => (cat.productCount ?? 0) > 0)
        .sort((a, b) => a.name.localeCompare(b.name));
    },
  });
}
