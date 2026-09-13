import axios from 'axios';

export const axiosClient = axios.create({
  baseURL: '/api/v1',
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
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
