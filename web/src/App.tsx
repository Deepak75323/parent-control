import {useEffect} from 'react';
import {BrowserRouter, Navigate, Route, Routes} from 'react-router-dom';
import {AppShell} from './components/AppShell';
import {ProtectedRoute} from './components/ProtectedRoute';
import {useAuthStore} from './auth/authStore';
import {connectWebSocket, disconnectWebSocket} from './realtime/websocket';
import {LoginPage} from './pages/LoginPage';
import {DevicesPage} from './pages/DevicesPage';
import {DeviceDetailPage} from './pages/DeviceDetailPage';
import {AlertsPage} from './pages/AlertsPage';
import {PoliciesPage} from './pages/PoliciesPage';
import {SettingsPage} from './pages/SettingsPage';
import {AuditPage} from './pages/AuditPage';
import {BillingPage} from './pages/BillingPage';
import {GeofencesPage} from './pages/GeofencesPage';
import {ChatPage} from './pages/ChatPage';
import './styles/app.css';

function Bootstrap() {
  const hydrate = useAuthStore(s => s.hydrate);
  const accessToken = useAuthStore(s => s.accessToken);
  const hydrated = useAuthStore(s => s.hydrated);

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    if (!hydrated) {
      return;
    }
    if (accessToken) {
      connectWebSocket();
    } else {
      disconnectWebSocket();
    }
  }, [hydrated, accessToken]);

  useEffect(() => {
    if (!hydrated || !accessToken) {
      return;
    }
    const retry = () => connectWebSocket();
    const onVisible = () => {
      if (document.visibilityState === 'visible') {
        retry();
      }
    };
    window.addEventListener('focus', retry);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      window.removeEventListener('focus', retry);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [hydrated, accessToken]);

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route path="/devices" element={<DevicesPage />} />
          <Route path="/devices/:deviceId" element={<DeviceDetailPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/policies" element={<PoliciesPage />} />
          <Route path="/geofences" element={<GeofencesPage />} />
          <Route path="/billing" element={<BillingPage />} />
          <Route path="/audit" element={<AuditPage />} />
          <Route path="/chat" element={<ChatPage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/devices" replace />} />
    </Routes>
  );
}

export default function App() {
  const basename = import.meta.env.BASE_URL.replace(/\/$/, '') || undefined;
  return (
    <BrowserRouter basename={basename}>
      <Bootstrap />
    </BrowserRouter>
  );
}
