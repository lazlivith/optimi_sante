import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { adminHomeFor } from '../../lib/adminUniverses';

/**
 * Accueil de /admin : renvoie chacun vers le tableau de bord de son métier.
 *
 * Une page d'accueil unique n'aurait plus de sens depuis la scission — elle mélangerait
 * des indicateurs que la moitié des administrateurs n'a pas le droit de consulter.
 */
export function AdminHomeRedirect() {
  const { user } = useAuth();
  return <Navigate to={adminHomeFor(user?.role)} replace />;
}
