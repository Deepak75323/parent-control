import {useCallback, useEffect, useState} from 'react';
import {Link} from 'react-router-dom';
import {auditApi} from '../api/services';
import type {AuditLogItem} from '../types/api';

function formatAuditMetadata(raw: string): string {
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const parts: string[] = [];
    if (typeof parsed.email === 'string') {
      parts.push(parsed.email);
    }
    if (typeof parsed.userAgent === 'string') {
      parts.push(parsed.userAgent);
    }
    if (parts.length > 0) {
      return parts.join(' · ');
    }
  } catch {
    // fall through
  }
  return raw;
}

export function AuditPage() {
  const [items, setItems] = useState<AuditLogItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (p: number) => {
    setError(null);
    setLoading(true);
    try {
      const {data} = await auditApi.list(p, 40);
      setItems(data.data?.content ?? []);
      setHasNext(Boolean(data.data?.hasNext));
      setPage(data.data?.page ?? p);
    } catch {
      setError('Failed to load audit log');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load(0);
  }, [load]);

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Audit log</h1>
          <p className="muted">Actions taken on this parent account.</p>
        </div>
        <button type="button" className="btn ghost" onClick={() => void load(page)}>
          Refresh
        </button>
      </div>
      {error ? <div className="error">{error}</div> : null}
      {loading ? <p>Loading…</p> : null}
      <div className="card">
        {items.length === 0 && !loading ? (
          <p className="muted">No audit entries yet.</p>
        ) : (
          <ul className="list">
            {items.map(item => (
              <li key={item.id}>
                <div className="row-between">
                  <strong>{item.action}</strong>
                  <span className="tiny">{new Date(item.createdAt).toLocaleString()}</span>
                </div>
                {item.resource ? <div className="muted">{item.resource}</div> : null}
                <div className="tiny">
                  {item.deviceId ? (
                    <>
                      <Link to={`/devices/${item.deviceId}`}>Device</Link>
                      {' · '}
                    </>
                  ) : null}
                  IP {item.ipAddress ?? '—'}
                </div>
                {item.metadataJson ? (
                  <div className="tiny muted" style={{wordBreak: 'break-word'}}>
                    {formatAuditMetadata(item.metadataJson)}
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}
        <div className="btn-row" style={{marginTop: '1rem'}}>
          <button
            type="button"
            className="btn ghost"
            disabled={page <= 0 || loading}
            onClick={() => void load(page - 1)}>
            Previous
          </button>
          <button
            type="button"
            className="btn ghost"
            disabled={!hasNext || loading}
            onClick={() => void load(page + 1)}>
            Next
          </button>
        </div>
      </div>
    </div>
  );
}
