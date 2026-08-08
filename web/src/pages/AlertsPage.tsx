import {useCallback, useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {alertsApi} from '../api/services';
import type {AlertItem} from '../types/api';
import {useRealtimeStore} from '../realtime/realtimeStore';

export function AlertsPage() {
  const [alerts, setAlerts] = useState<AlertItem[]>([]);
  const [error, setError] = useState<string | null>(null);
  const liveAlerts = useRealtimeStore(s => s.liveAlerts);

  const load = useCallback(async () => {
    setError(null);
    try {
      const {data} = await alertsApi.list(0, 40);
      setAlerts(data.data?.content ?? []);
    } catch {
      setError('Failed to load alerts');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const feed = [
    ...liveAlerts,
    ...alerts.filter(a => !liveAlerts.some(l => l.id === a.id)),
  ].slice(0, 60);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Alerts</h1>
          <p className="muted">Policy violations, geofence events, and device alerts.</p>
        </div>
        <button type="button" className="btn ghost" onClick={() => void load()}>
          Refresh
        </button>
      </div>
      {error ? <div className="error">{error}</div> : null}
      <div className="card">
        {feed.length === 0 ? (
          <p className="muted">No alerts yet.</p>
        ) : (
          <ul className="list">
            {feed.map(a => (
              <li key={a.id}>
                <div className="row-between">
                  <strong>{a.title}</strong>
                  <span className={`sev ${a.severity.toLowerCase()}`}>{a.severity}</span>
                </div>
                {a.message ? <div className="muted">{a.message}</div> : null}
                <div className="tiny">
                  <Link to={`/devices/${a.deviceId}`}>Device</Link>
                  {' · '}
                  {a.alertType}
                  {' · '}
                  {new Date(a.createdAt).toLocaleString()}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
