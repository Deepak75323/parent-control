import {useCallback, useEffect, useMemo, useState} from 'react';
import type {FormEvent} from 'react';
import {Link, useSearchParams} from 'react-router-dom';
import {deviceApi, monitoringApi, policiesApi} from '../api/services';
import type {DeviceSummary, GeofenceZone} from '../types/api';

export function GeofencesPage() {
  const [params, setParams] = useSearchParams();
  const [devices, setDevices] = useState<DeviceSummary[]>([]);
  const [zones, setZones] = useState<GeofenceZone[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [name, setName] = useState('');
  const [lat, setLat] = useState('');
  const [lng, setLng] = useState('');
  const [radius, setRadius] = useState('200');

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

  const loadZones = useCallback(async (id: string) => {
    if (!id) {
      setZones([]);
      return;
    }
    setError(null);
    try {
      const {data} = await monitoringApi.geofences(id);
      setZones(data.data ?? []);
    } catch {
      setError('Failed to load geofences');
    }
  }, []);

  useEffect(() => {
    void loadDevices();
  }, [loadDevices]);

  useEffect(() => {
    if (deviceId) {
      void loadZones(deviceId);
    }
  }, [deviceId, loadZones]);

  useEffect(() => {
    if (!params.get('deviceId') && devices[0]?.id) {
      setParams({deviceId: devices[0].id}, {replace: true});
    }
  }, [devices, params, setParams]);

  useEffect(() => {
    if (selected?.lastLatitude != null && selected.lastLongitude != null && !lat && !lng) {
      setLat(String(selected.lastLatitude));
      setLng(String(selected.lastLongitude));
    }
  }, [selected, lat, lng]);

  const onCreate = async (e: FormEvent) => {
    e.preventDefault();
    const latitude = Number(lat);
    const longitude = Number(lng);
    const radiusM = Number(radius);
    if (!deviceId || !name.trim() || Number.isNaN(latitude) || Number.isNaN(longitude)) {
      setError('Name, latitude, and longitude are required');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await policiesApi.createGeofence({
        name: `Geofence ${name.trim()}`,
        deviceIds: [deviceId],
        geofenceName: name.trim(),
        latitude,
        longitude,
        radiusM: Number.isNaN(radiusM) ? 200 : radiusM,
      });
      setName('');
      await loadZones(deviceId);
    } catch {
      setError('Could not create geofence');
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async (policyId: string) => {
    if (!window.confirm('Delete this geofence?')) {
      return;
    }
    setBusy(true);
    try {
      await policiesApi.delete(policyId);
      await loadZones(deviceId);
    } catch {
      setError('Could not delete geofence');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h1>Geofences</h1>
          <p className="muted">Safe zones for a child device. Edit = delete + recreate.</p>
        </div>
      </div>
      {error ? <div className="error">{error}</div> : null}

      <div className="card">
        <label>
          Device
          <select
            value={deviceId}
            onChange={e => setParams({deviceId: e.target.value})}>
            {devices.map(d => (
              <option key={d.id} value={d.id}>
                {d.deviceName}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="split">
        <section className="card">
          <h2>Zones</h2>
          {zones.length === 0 ? (
            <p className="muted">No geofences for this device.</p>
          ) : (
            <ul className="list">
              {zones.map(z => (
                <li key={z.ruleId || z.policyId}>
                  <div className="row-between">
                    <strong>{z.name}</strong>
                    <button
                      type="button"
                      className="btn ghost"
                      disabled={busy}
                      onClick={() => void onDelete(z.policyId)}>
                      Delete
                    </button>
                  </div>
                  <div className="muted">
                    {z.radiusM}m · enter {z.alertOnEnter ? 'on' : 'off'} · exit{' '}
                    {z.alertOnExit ? 'on' : 'off'}
                  </div>
                  <a
                    href={`https://www.openstreetmap.org/?mlat=${z.latitude}&mlon=${z.longitude}#map=15/${z.latitude}/${z.longitude}`}
                    target="_blank"
                    rel="noreferrer"
                    className="tiny">
                    {z.latitude.toFixed(5)}, {z.longitude.toFixed(5)}
                  </a>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="card">
          <h2>Add zone</h2>
          <form className="form-row" onSubmit={e => void onCreate(e)}>
            <label>
              Name
              <input value={name} onChange={e => setName(e.target.value)} required />
            </label>
            <label>
              Latitude
              <input value={lat} onChange={e => setLat(e.target.value)} required />
            </label>
            <label>
              Longitude
              <input value={lng} onChange={e => setLng(e.target.value)} required />
            </label>
            <label>
              Radius (m)
              <input value={radius} onChange={e => setRadius(e.target.value)} />
            </label>
            <button type="submit" className="btn primary" disabled={busy || !deviceId}>
              Create
            </button>
          </form>
          {selected ? (
            <p className="muted tiny">
              Prefills from last known location when available.{' '}
              <Link to={`/devices/${selected.id}`}>Open device</Link>
            </p>
          ) : null}
        </section>
      </div>
    </div>
  );
}
