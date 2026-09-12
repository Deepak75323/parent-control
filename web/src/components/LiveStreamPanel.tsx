import {useState} from 'react';
import {Link} from 'react-router-dom';
import {useAuthStore} from '../auth/authStore';
import {CameraUnlockDialog} from './CameraUnlockDialog';
import {useLiveStreamStore} from '../livestream/liveStreamStore';
import {
  StreamControls,
  StreamTypePicker,
  StreamVideoView,
} from '../livestream/StreamVideoView';
import {userIdFromAccessToken} from '../lib/jwtSubject';

type Props = {
  deviceId: string;
  deviceName: string;
};

export function LiveStreamPanel({deviceId, deviceName}: Props) {
  const sessionId = useLiveStreamStore(s => s.sessionId);
  const activeDeviceId = useLiveStreamStore(s => s.deviceId);
  const status = useLiveStreamStore(s => s.status);
  const error = useLiveStreamStore(s => s.error);
  const busy = useLiveStreamStore(s => s.busy);
  const pinNeeded = useLiveStreamStore(s => s.pinNeeded);
  const pin = useLiveStreamStore(s => s.pin);
  const streamType = useLiveStreamStore(s => s.streamType);
  const start = useLiveStreamStore(s => s.start);
  const setPin = useLiveStreamStore(s => s.setPin);
  const accessToken = useAuthStore(s => s.accessToken);
  const profile = useAuthStore(s => s.profile);
  const userId = userIdFromAccessToken(accessToken) ?? profile?.userId ?? '';
  const [cameraGateOpen, setCameraGateOpen] = useState(false);

  const streamOnOtherDevice = sessionId && activeDeviceId && activeDeviceId !== deviceId;
  const streamOnThisDevice = sessionId && activeDeviceId === deviceId;

  function requestStart() {
    if (streamType === 'CAMERA') {
      setCameraGateOpen(true);
      return;
    }
    void start(deviceId, deviceName);
  }

  return (
    <div className="card live-panel">
      <div className="page-header">
        <div>
          <h2>Live view</h2>
          <p className="muted">{streamOnOtherDevice ? 'Stream active on another device' : status}</p>
        </div>
      </div>
      {error && !streamOnOtherDevice ? <div className="error">{error}</div> : null}

      {streamOnOtherDevice ? (
        <p className="muted">
          Stop the other stream first, or{' '}
          <Link to={`/devices/${activeDeviceId}`}>open that device</Link>.
        </p>
      ) : (
        <>
          <StreamTypePicker disabled={busy || Boolean(sessionId)} />

          {pinNeeded && streamType === 'SCREEN' ? (
            <label className="form-row">
              Parent PIN (silent screen)
              <input
                type="password"
                inputMode="numeric"
                value={pin}
                onChange={e => setPin(e.target.value)}
                maxLength={8}
                disabled={busy || Boolean(sessionId)}
              />
            </label>
          ) : null}

          {streamType === 'CAMERA' && !sessionId ? (
            <p className="muted tiny">
              Camera requires fingerprint first, then your chat decrypt code (both).
            </p>
          ) : null}

          {streamOnThisDevice ? (
            <>
              <StreamVideoView />
              <StreamControls inline />
            </>
          ) : null}

          <div className="btn-row wrap">
            {!sessionId ? (
              <button
                type="button"
                className="btn primary"
                disabled={busy}
                onClick={requestStart}>
                {busy ? 'Starting…' : 'Start'}
              </button>
            ) : streamOnThisDevice ? null : (
              <button
                type="button"
                className="btn primary"
                disabled={busy}
                onClick={requestStart}>
                {busy ? 'Starting…' : 'Switch stream here'}
              </button>
            )}
          </div>

          {sessionId && streamOnThisDevice ? (
            <p className="muted tiny">
              Navigate to chat or other pages — the stream stays live in a floating window.
            </p>
          ) : null}
        </>
      )}

      <CameraUnlockDialog
        open={cameraGateOpen}
        userId={userId}
        onCancel={() => setCameraGateOpen(false)}
        onUnlocked={() => {
          setCameraGateOpen(false);
          void start(deviceId, deviceName);
        }}
      />
    </div>
  );
}
