import axios from 'axios';
import { API_BASE } from './apiBase';

export const axiosClient = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

axiosClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    const tenantId = localStorage.getItem('tenantId') || 'FR_MAIN';

    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    
    if (tenantId) {
      config.headers['X-Tenant-Id'] = tenantId;
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * Les routes de l'API ouvertes à un visiteur non connecté.
 *
 * <p>Un 401 venu de l'une d'elles ne dit JAMAIS « votre session a expiré » : il dit que la
 * requête n'est pas passée. Le 23 octobre, 148 fiches produit portaient un slug contenant un
 * « % » — reste d'un « ® » encodé à l'import. Correctement encodé dans l'adresse, ce « % »
 * devient « %25 », que le pare-feu de Spring Security refuse avant même le contrôleur : 401,
 * corps vide, sur une route pourtant publique. L'intercepteur y lisait une session expirée,
 * effaçait le jeton et renvoyait vers la connexion — y compris un super-administrateur, qui
 * perdait sa session en plus de sa fiche.</p>
 */
const ROUTES_PUBLIQUES = ['/catalog/', '/public/', '/devises', '/trainings', '/blog/'];

/**
 * Ce 401 justifie-t-il de déconnecter l'utilisateur et de le renvoyer vers la connexion ?
 *
 * <p>Deux conditions, et les deux sont nécessaires. La route doit exiger une authentification :
 * une page publique qui échoue reste une page publique, et c'est à elle d'afficher son erreur.
 * Et un jeton doit avoir été envoyé : sans jeton, « non authentifié » est la réponse attendue,
 * pas la preuve qu'une session vient de tomber.</p>
 */
export function deconnecteSur(url: string | undefined, jeton: string | null): boolean {
  if (!jeton) return false;
  const adresse = url ?? '';
  return !ROUTES_PUBLIQUES.some(prefixe => adresse.includes(prefixe));
}

// Response interceptor for automatic logout on 401/403
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    const originalRequest = error.config;
    // Don't intercept login or register routes
    if (originalRequest.url?.includes('/auth/login') || originalRequest.url?.includes('/auth/register')) {
      return Promise.reject(error);
    }
    
    // 401 seulement. Mesuré sur le serveur : jeton invalide, expiré ou absent répondent 401 ;
    // 403 signifie « connecté, mais pas autorisé à cette action ». Le traiter comme une session
    // expirée déconnectait l'utilisateur pour une simple action interdite — un client qui ouvrait
    // par erreur une page réservée aux médecins perdait sa session en plus du message d'erreur.
    if (error.response?.status === 401 && deconnecteSur(originalRequest?.url, localStorage.getItem('token'))) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
