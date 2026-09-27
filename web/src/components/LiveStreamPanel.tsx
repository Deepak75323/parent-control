import {useEffect, useState} from 'react';
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
import {startLivePipCue} from '../livestream/pipAudible';
import {
  assertParentWebAuthn,
  describeWebAuthnError,
  hasParentWebAuthnCredentials,
  isWebAuthnAvailable,
} from '../lib/webAuthnParentGate';

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
  const [biometricAlreadyPassed, setBiometricAlreadyPassed] = useState(false);
  const [hasFingerprint, setHasFingerprint] = useState(false);
  const [gateBusy, setGateBusy] = useState(false);
  const [gateError, setGateError] = useState<string | null>(null);

  // Prefetch so Start can call WebAuthn in the same tap (browser user-gesture).
  useEffect(() => {
    if (!userId || !isWebAuthnAvailable()) {
      setHasFingerprint(false);
      return;
    }
    let cancelled = false;
    void hasParentWebAuthnCredentials(userId).then(has => {
      if (!cancelled) {
        setHasFingerprint(has);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [userId]);

  const streamOnOtherDevice = sessionId && activeDeviceId && activeDeviceId !== deviceId;
  const streamOnThisDevice = sessionId && activeDeviceId === deviceId;

  async function requestStart() {
    if (streamType !== 'CAMERA') {
      startLivePipCue();
      void start(deviceId, deviceName);
      return;
    }
    setGateError(null);
    if (hasFingerprint) {
      setGateBusy(true);
      try {
        // Same click as Start — avoids a second "Use fingerprint" tap.
        const ok = await assertParentWebAuthn(userId);
        if (!ok) {
          setBiometricAlreadyPassed(false);
          setCameraGateOpen(true);
          setGateError('Fingerprint not verified. Retry in the dialog.');
          return;
        }
        setBiometricAlreadyPassed(true);
        setCameraGateOpen(true);
      } catch (err) {
        setBiometricAlreadyPassed(false);
        setCameraGateOpen(true);
        setGateError(describeWebAuthnError(err));
      } finally {
        setGateBusy(false);
      }
      return;
    }
    setBiometricAlreadyPassed(false);
    setCameraGateOpen(true);
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
      {gateError && !cameraGateOpen ? <div className="error">{gateError}</div> : null}

      {streamOnOtherDevice ? (
        <p className="muted">
          Stop the other stream first, or{' '}
          <Link to={`/devices/${activeDeviceId}`}>open that device</Link>.
        </p>
      ) : (
        <>
          <StreamTypePicker disabled={busy || gateBusy || Boolean(sessionId)} />

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
              Tap Start — fingerprint runs immediately, then enter your decrypt code.
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
                disabled={busy || gateBusy}
                onClick={() => void requestStart()}>
                {gateBusy ? 'Fingerprint…' : busy ? 'Starting…' : 'Start'}
              </button>
            ) : streamOnThisDevice ? null : (
              <button
                type="button"
                className="btn primary"
                disabled={busy || gateBusy}
                onClick={() => void requestStart()}>
                {gateBusy ? 'Fingerprint…' : busy ? 'Starting…' : 'Switch stream here'}
              </button>
            )}
          </div>

          {sessionId && streamOnThisDevice ? (
            <p className="muted tiny">
              The small floating video stays on this site. Picture in picture opens the Meet window for this camera. Switching to another tab does the same. Chrome may show the microphone dot while the live view is on. Nothing from that dot is played, recorded, or sent.
            </p>
          ) : null}
        </>
      )}

      <CameraUnlockDialog
        open={cameraGateOpen}
        userId={userId}
        biometricAlreadyPassed={biometricAlreadyPassed}
        onCancel={() => {
          setCameraGateOpen(false);
          setBiometricAlreadyPassed(false);
        }}
        onUnlocked={() => {
          setCameraGateOpen(false);
          setBiometricAlreadyPassed(false);
          setGateError(null);
          void start(deviceId, deviceName);
        }}
      />
    </div>
  );
}
