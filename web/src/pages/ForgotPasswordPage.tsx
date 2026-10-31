import {useState} from 'react';
import type {FormEvent} from 'react';
import {Link} from 'react-router-dom';
import {authApi} from '../api/services';

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError(null);
    setMessage(null);
    setLoading(true);
    try {
      const {data} = await authApi.forgotPassword(email.trim());
      setMessage(
        data.message ??
          'If an account exists for that email, a reset link has been sent.',
      );
    } catch (err: unknown) {
      const msg =
        (err as {response?: {data?: {message?: string}}})?.response?.data?.message ??
        'Could not request a reset link. Try again later.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-card" onSubmit={e => void onSubmit(e)}>
        <div className="auth-brand">
          <h1>Forgot password</h1>
          <p className="muted">We’ll email a reset link if that account exists.</p>
        </div>
        {error ? <div className="error">{error}</div> : null}
        {message ? <p className="muted">{message}</p> : null}
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
        <button className="btn primary btn-block" type="submit" disabled={loading}>
          {loading ? 'Sending…' : 'Send reset link'}
        </button>
        <p className="muted tiny" style={{marginTop: 12, textAlign: 'center'}}>
          <Link to="/login">Back to sign in</Link>
        </p>
      </form>
    </div>
  );
}
