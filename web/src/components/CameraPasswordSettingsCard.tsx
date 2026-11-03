import {type FormEvent, useCallback, useEffect, useState} from 'react';
import {cameraPasswordApi} from '../api/services';
import {validateCameraPasswordForm} from '../lib/cameraPassword';

/**
 * Camera unlock password — set/changed only here (current + new + confirm).
 * Kept separate from the chat decrypt code so opening a camera never exposes it.
 */
export function CameraPasswordSettingsCard() {
  const [configured, setConfigured] = useState(false);
  const [currentPin, setCurrentPin] = useState('');
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [msg, setMsg] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const {data} = await cameraPasswordApi.status();
      setConfigured(Boolean(data.data?.pinConfigured));
    } catch {
      setMsg('Could not load camera password status');
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const onSave = async (e: FormEvent) => {
    e.preventDefault();
    const mode = configured ? 'change' : 'create';
    const validation = validateCameraPasswordForm(mode, {currentPin, pin, confirmPin});
    if (validation) {
      setMsg(validation);
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      if (mode === 'create') {
        await cameraPasswordApi.create(pin.trim(), confirmPin.trim());
        setMsg('Camera password saved');
      } else {
        await cameraPasswordApi.change(currentPin.trim(), pin.trim(), confirmPin.trim());
        setMsg('Camera password updated');
      }
      setConfigured(true);
      setCurrentPin('');
      setPin('');
      setConfirmPin('');
    } catch {
      setMsg(
        mode === 'change'
          ? 'Could not change camera password — check the current password'
          : 'Could not save camera password',
      );
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card">
      <h2>Camera password</h2>
      <p className="muted">
        {configured
          ? 'Required to open camera live view. Enter the current password to change it. Separate from the chat decrypt code.'
          : 'Create a password before opening camera live view. First time: new password and confirm. Separate from the chat decrypt code.'}
      </p>
      <form className="form-row" onSubmit={e => void onSave(e)}>
        {configured ? (
          <label>
            Current password
            <input
              type="password"
              inputMode="numeric"
              autoComplete="off"
              value={currentPin}
              onChange={e => setCurrentPin(e.target.value.replace(/\D/g, '').slice(0, 8))}
              maxLength={8}
            />
          </label>
        ) : null}
        <label>
          {configured ? 'New password (4–8 digits)' : 'Password (4–8 digits)'}
          <input
            type="password"
            inputMode="numeric"
            autoComplete="off"
            value={pin}
            onChange={e => setPin(e.target.value.replace(/\D/g, '').slice(0, 8))}
            maxLength={8}
          />
        </label>
        <label>
          Confirm password
          <input
            type="password"
            inputMode="numeric"
            autoComplete="off"
            value={confirmPin}
            onChange={e => setConfirmPin(e.target.value.replace(/\D/g, '').slice(0, 8))}
            maxLength={8}
          />
        </label>
        <div className="btn-row">
          <button type="submit" className="btn primary" disabled={busy}>
            {configured ? 'Change password' : 'Set password'}
          </button>
        </div>
        {msg ? <p className="muted">{msg}</p> : null}
      </form>
    </div>
  );
}
