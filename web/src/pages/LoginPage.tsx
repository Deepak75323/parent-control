import {useState} from 'react';
import type {FormEvent} from 'react';
import {Navigate, useNavigate} from 'react-router-dom';
import {authApi} from '../api/services';
import {useAuthStore} from '../auth/authStore';
import {connectWebSocket} from '../realtime/websocket';

export function LoginPage() {
  const accessToken = useAuthStore(s => s.accessToken);
  const setTokens = useAuthStore(s => s.setTokens);
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  if (accessToken) {
    return <Navigate to="/devices" replace />;
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const {data} = await authApi.login(email.trim(), password);
      if (!data.success || !data.data?.accessToken || !data.data.refreshToken) {
        setError(data.message ?? 'Login failed');
        return;
      }
      setTokens(
        data.data.accessToken,
        data.data.refreshToken,
        data.data.profile?.email ?? email.trim(),
        data.data.profile ?? null,
      );
      connectWebSocket();
      navigate('/devices', {replace: true});
    } catch (err: unknown) {
      const msg =
        (err as {response?: {data?: {message?: string}}})?.response?.data?.message ??
        'Could not sign in. Check email, password, and that the API is reachable.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={e => void onSubmit(e)}>
        <div className="auth-brand">
          <img src="/app-logo.png" alt="" className="auth-logo" width={64} height={64} />
          <h1>Parent Control</h1>
          <p className="muted">Sign in to your parent account</p>
        </div>
        {error ? <div className="error">{error}</div> : null}
        <label>
          Email
          <input
            type="email"
            autoComplete="username"
            placeholder="you@example.com"
            value={email}
            onChange={e => {
              setEmail(e.target.value);
              setError(null);
            }}
            required
          />
        </label>
        <label>
          Password
          <input
            type="password"
            autoComplete="current-password"
            placeholder="Your password"
            value={password}
            onChange={e => {
              setPassword(e.target.value);
              setError(null);
            }}
            required
          />
        </label>
        <button className="btn primary btn-block" type="submit" disabled={loading}>
          {loading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
    </div>
  );
}
