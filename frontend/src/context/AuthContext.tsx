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
    const initAuth = async () => {
      const token = localStorage.getItem('token');
      if (token) {
        try {
          const profile = await authService.getProfile();
          setUser(profile);
        } catch (error) {
          console.error('Failed to load profile', error);
          localStorage.removeItem('token');
        }
      }
      setIsLoading(false);
    };

    initAuth();
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
