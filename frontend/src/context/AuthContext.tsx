import React, { createContext, useContext, useState, useEffect } from 'react';
import { authService, type User } from '../api/authService';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (token: string, user: User) => void;
  logout: () => void;
  updateUser: (updatedData: Partial<User>) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Double exécution de React en mode strict, ou page quittée pendant les tentatives : une
    // restauration abandonnée ne doit plus toucher à l'état.
    let abandonne = false;

    const initAuth = async () => {
      const token = localStorage.getItem('token');
      if (token) {
        // Attentes entre deux tentatives, en millisecondes : une trentaine de secondes au total.
        // C'est l'ordre de grandeur d'un redémarrage du backend, ou d'un gel de la machine
        // virtuelle de Docker quand la mémoire de l'hôte sature — mesuré à 64 et 70 s
        // d'intervalle entre deux passages du gestionnaire de connexions, au lieu de 30.
        const attentes = [1000, 2000, 4000, 8000, 15000];
        for (let tentative = 0; ; tentative++) {
          try {
            const profile = await authService.getProfile();
            if (!abandonne) setUser(profile);
            break;
          } catch (error) {
            const statut = (error as { response?: { status?: number } }).response?.status;

            // Le serveur a RÉPONDU que la session n'est plus valable : on la retire. L'intercepteur
            // d'axios renvoie déjà vers la connexion dans ce cas.
            if (statut === 401 || statut === 403) {
              localStorage.removeItem('token');
              break;
            }

            // Sinon le serveur n'a pas pu répondre — coupure, redémarrage, erreur 5xx relayée par
            // le proxy. Auparavant, le jeton était supprimé ici aussi : un backend indisponible
            // quelques secondes déconnectait l'utilisateur, qui devait se reconnecter. On
            // réessaie, et en dernier recours on GARDE le jeton : un simple rechargement de la
            // page, une fois le serveur revenu, restaure la session.
            if (abandonne || tentative >= attentes.length) {
              console.error('Profil injoignable, session conservée pour un prochain essai', error);
              break;
            }
            await new Promise((r) => setTimeout(r, attentes[tentative]));
            if (abandonne) return;
          }
        }
      }
      if (!abandonne) setIsLoading(false);
    };

    initAuth();
    return () => { abandonne = true; };
  }, []);

  const login = (token: string, newUser: User) => {
    localStorage.setItem('token', token);
    setUser(newUser);
    
    // Role-based redirection
    let redirectPath = '/';
    switch (newUser.role) {
      case 'ADMIN':
      case 'SUPER_ADMIN':
        redirectPath = '/admin';
        break;
      case 'MEDECIN':
        redirectPath = '/doctor';
        break;
      case 'CENTRE_FORMATION':
        redirectPath = '/partner';
        break;
      case 'CLIENT_B2B':
      case 'CLIENT_B2C':
        redirectPath = '/catalog';
        break;
      default:
        redirectPath = '/';
    }
    window.location.href = redirectPath;
  };

  const logout = () => {
    localStorage.removeItem('token');
    setUser(null);
    window.location.href = '/login';
  };

  const updateUser = (updatedData: Partial<User>) => {
    if (user) {
      setUser({ ...user, ...updatedData });
    }
  };

  return (
    <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
