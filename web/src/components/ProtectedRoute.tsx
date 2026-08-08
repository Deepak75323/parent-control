import {Navigate, Outlet} from 'react-router-dom';
import {useAuthStore} from '../auth/authStore';

export function ProtectedRoute() {
  const hydrated = useAuthStore(s => s.hydrated);
  const accessToken = useAuthStore(s => s.accessToken);

  if (!hydrated) {
    return <div className="center">Loading…</div>;
  }
  if (!accessToken) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
