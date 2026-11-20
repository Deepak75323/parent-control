import {type FormEvent, useCallback, useEffect, useState} from 'react';
import {autoGreetingApi, chatApi} from '../api/services';
import {validateAutoGreetingForm} from '../lib/autoGreetingSettings';
import type {ChatRoomSummary} from '../types/api';

/**
 * Settings-gated auto chat greetings: posts parent-chosen morning/evening text
 * into a chosen room during the configured morning and evening windows.
 */
export function AutoGreetingSettingsCard() {
  const [enabled, setEnabled] = useState(false);
  const [roomId, setRoomId] = useState('');
  const [morningStart, setMorningStart] = useState('07:05');
  const [morningEnd, setMorningEnd] = useState('12:00');
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
        setMorningStart(s.morningStart || s.morningTime || '07:05');
        setMorningEnd(s.morningEnd || '12:00');
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
    const validation = validateAutoGreetingForm({
      enabled,
      roomId,
      morningStart,
      morningEnd,
      eveningStart,
      eveningEnd,
      morningMessage,
      eveningMessage,
    });
    if (validation) {
      setMsg(validation);
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      const {data} = await autoGreetingApi.update({
        enabled,
        roomId: roomId || null,
        morningStart,
        morningEnd,
        eveningStart,
        eveningEnd,
        timezone,
        morningMessage: morningMessage.trim(),
        eveningMessage: eveningMessage.trim(),
      });
      const s = data.data;
      if (s) {
        setEnabled(Boolean(s.enabled));
        setRoomId(s.roomId ?? '');
        setMorningStart(s.morningStart || s.morningTime || '07:05');
        setMorningEnd(s.morningEnd || '12:00');
        setEveningStart(s.eveningStart || '23:55');
        setEveningEnd(s.eveningEnd || '00:00');
        setTimezone(s.timezone || 'Asia/Kolkata');
        setMorningMessage(s.morningMessage || 'good morning baby');
        setEveningMessage(s.eveningMessage || 'good night baby');
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
        When on, the server posts your morning message during the morning window and your evening
        message during the evening window into the selected Notes room (timezone: {timezone}).
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
          Morning message
          <input
            value={morningMessage}
            onChange={e => setMorningMessage(e.target.value.slice(0, 200))}
            placeholder="good morning baby"
            maxLength={200}
          />
        </label>
        <label>
          Morning start (HH:mm)
          <input
            value={morningStart}
            onChange={e => setMorningStart(e.target.value)}
            placeholder="07:05"
            maxLength={5}
          />
        </label>
        <label>
          Morning end (HH:mm)
          <input
            value={morningEnd}
            onChange={e => setMorningEnd(e.target.value)}
            placeholder="12:00"
            maxLength={5}
          />
        </label>
        <label>
          Evening message
          <input
            value={eveningMessage}
            onChange={e => setEveningMessage(e.target.value.slice(0, 200))}
            placeholder="good night baby"
            maxLength={200}
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
