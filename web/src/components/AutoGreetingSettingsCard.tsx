import {type FormEvent, useCallback, useEffect, useState} from 'react';
import {autoGreetingApi, chatApi} from '../api/services';
import type {ChatRoomSummary} from '../types/api';

/**
 * Settings-gated auto chat greetings: posts "good morning baby" / "good night baby"
 * into a chosen room on a schedule (server-side).
 */
export function AutoGreetingSettingsCard() {
  const [enabled, setEnabled] = useState(false);
  const [roomId, setRoomId] = useState('');
  const [morningTime, setMorningTime] = useState('07:05');
  const [eveningStart, setEveningStart] = useState('23:55');
  const [eveningEnd, setEveningEnd] = useState('00:00');
  const [timezone, setTimezone] = useState('Asia/Kolkata');
  const [morningMessage, setMorningMessage] = useState('good morning baby');
  const [eveningMessage, setEveningMessage] = useState('good night baby');
  const [rooms, setRooms] = useState<ChatRoomSummary[]>([]);
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const [settingsRes, roomsRes] = await Promise.all([
        autoGreetingApi.get(),
        chatApi.listRooms(),
      ]);
      const s = settingsRes.data.data;
      if (s) {
        setEnabled(Boolean(s.enabled));
        setRoomId(s.roomId ?? '');
        setMorningTime(s.morningTime || '07:05');
        setEveningStart(s.eveningStart || '23:55');
        setEveningEnd(s.eveningEnd || '00:00');
        setTimezone(s.timezone || 'Asia/Kolkata');
        setMorningMessage(s.morningMessage || 'good morning baby');
        setEveningMessage(s.eveningMessage || 'good night baby');
      }
      setRooms(roomsRes.data.data ?? []);
    } catch {
      setMsg('Could not load auto greeting settings');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onSave = async (e: FormEvent) => {
    e.preventDefault();
    if (enabled && !roomId) {
      setMsg('Select a chat room before enabling auto greetings');
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      const {data} = await autoGreetingApi.update({
        enabled,
        roomId: roomId || null,
        morningTime,
        eveningStart,
        eveningEnd,
        timezone,
      });
      const s = data.data;
      if (s) {
        setEnabled(Boolean(s.enabled));
        setRoomId(s.roomId ?? '');
        setMorningTime(s.morningTime || '07:05');
        setEveningStart(s.eveningStart || '23:55');
        setEveningEnd(s.eveningEnd || '00:00');
        setTimezone(s.timezone || 'Asia/Kolkata');
      }
      setMsg(enabled ? 'Auto greetings enabled' : 'Auto greetings saved (off)');
    } catch {
      setMsg('Could not save auto greeting settings');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card">
      <h2>Auto chat greetings</h2>
      <p className="muted">
        When on, the server posts <strong>{morningMessage}</strong> after the morning time and{' '}
        <strong>{eveningMessage}</strong> in the evening window into the selected Notes room
        (timezone: {timezone}).
      </p>
      <form className="form-row" onSubmit={e => void onSave(e)}>
        <label>
          Chat room
          <select value={roomId} onChange={e => setRoomId(e.target.value)}>
            <option value="">Select a room…</option>
            {rooms.map(room => (
              <option key={room.id} value={room.id}>
                {room.name}
                {room.directMessage ? ' (DM)' : ''}
              </option>
            ))}
          </select>
        </label>
        <label>
          Morning time (HH:mm)
          <input
            value={morningTime}
            onChange={e => setMorningTime(e.target.value)}
            placeholder="07:05"
            maxLength={5}
          />
        </label>
        <label>
          Evening start (HH:mm)
          <input
            value={eveningStart}
            onChange={e => setEveningStart(e.target.value)}
            placeholder="23:55"
            maxLength={5}
          />
        </label>
        <label>
          Evening end (HH:mm)
          <input
            value={eveningEnd}
            onChange={e => setEveningEnd(e.target.value)}
            placeholder="00:00"
            maxLength={5}
          />
        </label>
        <label>
          Timezone
          <input
            value={timezone}
            onChange={e => setTimezone(e.target.value)}
            placeholder="Asia/Kolkata"
            maxLength={64}
          />
        </label>
        <div className="btn-row">
          <button
            type="button"
            className={`btn ${enabled ? 'primary' : 'ghost'}`}
            disabled={busy}
            onClick={() => setEnabled(v => !v)}>
            {enabled ? 'Auto greetings: On' : 'Auto greetings: Off'}
          </button>
          <button type="submit" className="btn primary" disabled={busy}>
            Save
          </button>
        </div>
        {msg ? <p className="muted">{msg}</p> : null}
      </form>
    </div>
  );
}
