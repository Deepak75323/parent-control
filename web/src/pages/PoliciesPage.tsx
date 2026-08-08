import {useCallback, useEffect, useMemo, useState} from 'react';
import type {FormEvent} from 'react';
import {useSearchParams} from 'react-router-dom';
import {deviceApi, policiesApi} from '../api/services';
import type {DeviceSummary, PolicyBundle} from '../types/api';

export function PoliciesPage() {
  const [params, setParams] = useSearchParams();
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [bundle, setBundle] = useState<PolicyBundle | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [packageName, setPackageName] = useState('');
  const [dailyLimit, setDailyLimit] = useState('60');

  const deviceId = params.get('deviceId') ?? devices[0]?.id ?? '';

  const selected = useMemo(
    () => devices.find(d => d.id === deviceId) ?? null,
    [devices, deviceId],
  );

  const loadDevices = useCallback(async () => {
    try {
      const {data} = await deviceApi.list();
      setDevices(data.data ?? []);
    } catch {
      setError('Failed to load devices');
    }
  }, []);

  const loadBundle = useCallback(async (id: string) => {
    if (!id) {
      setBundle(null);
      return;
    }
    setError(null);
    try {
      const {data} = await policiesApi.deviceBundle(id);
      setBundle(data.data);
    } catch {
      setError('Failed to load policies for device');
    }
  }, []);

  useEffect(() => {
    void loadDevices();
  }, [loadDevices]);

  useEffect(() => {
    if (deviceId) {
      void loadBundle(deviceId);
    }
  }, [deviceId, loadBundle]);

  useEffect(() => {
    if (!params.get('deviceId') && devices[0]?.id) {
      setParams({deviceId: devices[0].id}, {replace: true});
    }
  }, [devices, params, setParams]);

  const onAddBlock = async (e: FormEvent) => {
    e.preventDefault();
    if (!deviceId || !packageName.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await policiesApi.createAppBlock({
        name: `Block ${packageName.trim()}`,
        deviceIds: [deviceId],
        rules: [{packageName: packageName.trim(), scheduleType: 'ALWAYS'}],
      });
      setPackageName('');
      await loadBundle(deviceId);
    } catch {
      setError('Could not create app block');
    } finally {
      setBusy(false);
    }
  };

  const onAddScreenTime = async (e: FormEvent) => {
    e.preventDefault();
    if (!deviceId) {
      return;
    }
    const minutes = Number(dailyLimit);
    if (!Number.isFinite(minutes) || minutes <= 0) {
      setError('Enter a positive daily limit in minutes');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await policiesApi.createScreenTime({
        name: packageName.trim()
          ? `Limit ${packageName.trim()}`
          : `Screen time ${minutes}m`,
        deviceIds: [deviceId],
        ...(packageName.trim() ? {packageName: packageName.trim()} : {}),
        dailyLimitMinutes: minutes,
        actionOnLimit: 'BLOCK',
      });
      await loadBundle(deviceId);
    } catch {
      setError('Could not create screen-time policy');
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async (policyId: string) => {
    setBusy(true);
    setError(null);
    try {
      await policiesApi.delete(policyId);
      await loadBundle(deviceId);
    } catch {
      setError('Could not delete policy');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Policies</h1>
          <p className="muted">App blocks and screen-time limits for a child device.</p>
        </div>
      </div>

      {error ? <div className="error">{error}</div> : null}

      <div className="card">
        <label>
          Device
          <select
            value={deviceId}
            onChange={e => setParams({deviceId: e.target.value})}
            disabled={devices.length === 0}>
            {devices.length === 0 ? <option value="">No devices</option> : null}
            {devices.map(d => (
              <option key={d.id} value={d.id}>
                {d.deviceName}
              </option>
            ))}
          </select>
        </label>
        {selected ? <p className="muted tiny">Editing policies for {selected.deviceName}</p> : null}
      </div>

      <div className="split">
        <section className="card">
          <h2>App blocks</h2>
          <ul className="list">
            {(bundle?.appBlocks ?? []).map(rule => (
              <li key={`${rule.policyId}-${rule.packageName}`} className="row-between">
                <span>{rule.packageName}</span>
                <button
                  type="button"
                  className="btn ghost danger"
                  disabled={busy}
                  onClick={() => void onDelete(rule.policyId)}>
                  Remove
                </button>
              </li>
            ))}
            {(bundle?.appBlocks ?? []).length === 0 ? (
              <li className="muted">No app blocks.</li>
            ) : null}
          </ul>
        </section>

        <section className="card">
          <h2>Screen time</h2>
          <ul className="list">
            {(bundle?.screenTime ?? []).map(rule => (
              <li key={`${rule.policyId}-${rule.packageName ?? 'all'}`} className="row-between">
                <span>
                  {rule.packageName ?? 'All apps'} — {rule.dailyLimitMinutes ?? '—'} min/day
                </span>
                <button
                  type="button"
                  className="btn ghost danger"
                  disabled={busy}
                  onClick={() => void onDelete(rule.policyId)}>
                  Remove
                </button>
              </li>
            ))}
            {(bundle?.screenTime ?? []).length === 0 ? (
              <li className="muted">No screen-time rules.</li>
            ) : null}
          </ul>
        </section>
      </div>

      <div className="card">
        <h2>Add policy</h2>
        <form className="form-row" onSubmit={e => void onAddBlock(e)}>
          <label>
            Package name (e.g. com.instagram.android)
            <input
              value={packageName}
              onChange={e => setPackageName(e.target.value)}
              placeholder="com.example.app"
            />
          </label>
          <button className="btn primary" type="submit" disabled={busy || !deviceId}>
            Block app
          </button>
        </form>
        <form className="form-row" onSubmit={e => void onAddScreenTime(e)}>
          <label>
            Daily limit (minutes)
            <input
              type="number"
              min={1}
              value={dailyLimit}
              onChange={e => setDailyLimit(e.target.value)}
            />
          </label>
          <p className="muted tiny">
            Uses package above when set; otherwise applies a device-wide daily limit.
          </p>
          <button className="btn primary" type="submit" disabled={busy || !deviceId}>
            Add screen-time limit
          </button>
        </form>
      </div>
    </div>
  );
}
