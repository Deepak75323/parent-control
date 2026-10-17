import {Avatar} from '../Avatar';
import {useChatCallSessionStore} from '../../realtime/chatCallSessionStore';

type Props = {
  displayName: string;
  seed: string;
  onOpenSettings?: () => void;
};

/** Discord-style self panel: mute / deafen / leave while in a voice call. */
export function ChatVoiceSelfBar({displayName, seed, onOpenSettings}: Props) {
  const joined = useChatCallSessionStore(s => s.joined);
  const channelName = useChatCallSessionStore(s => s.channelName);
  const roomName = useChatCallSessionStore(s => s.roomName);
  const micOn = useChatCallSessionStore(s => s.micOn);
  const deafened = useChatCallSessionStore(s => s.deafened);
  const camOn = useChatCallSessionStore(s => s.camOn);
  const toggleMic = useChatCallSessionStore(s => s.toggleMic);
  const toggleDeafen = useChatCallSessionStore(s => s.toggleDeafen);
  const toggleCam = useChatCallSessionStore(s => s.toggleCam);
  const requestLeave = useChatCallSessionStore(s => s.requestLeave);
  const setMinimized = useChatCallSessionStore(s => s.setMinimized);

  const voiceLabel = channelName || roomName || 'Voice';
  const status = !joined
    ? 'Online'
    : deafened
      ? 'Deafened'
      : !micOn
        ? 'Muted'
        : 'Connected';

  return (
    <div className="chat-voice-self" aria-label="Voice controls">
      <button
        type="button"
        className="chat-voice-self-main"
        onClick={() => {
          if (joined) {
            setMinimized(false);
          }
        }}
        title={joined ? 'Return to call' : undefined}
        disabled={!joined}>
        <Avatar name={displayName} seed={seed} size="sm" />
        <span className="chat-voice-self-meta">
          <span className="chat-voice-self-name">{displayName}</span>
          <span className={`chat-voice-self-status${joined ? ' chat-voice-self-status--live' : ''}`}>
            {joined ? `${status} · ${voiceLabel}` : status}
          </span>
        </span>
      </button>
      <div className="chat-voice-self-actions" role="toolbar" aria-label="Self voice controls">
        <button
          type="button"
          className={`chat-voice-self-btn${!micOn || deafened ? ' chat-voice-self-btn--off' : ''}`}
          disabled={!joined}
          aria-label={micOn && !deafened ? 'Mute' : 'Unmute'}
          title={micOn && !deafened ? 'Mute' : 'Unmute'}
          onClick={toggleMic}>
          {micOn && !deafened ? <IconMic /> : <IconMicOff />}
        </button>
        <button
          type="button"
          className={`chat-voice-self-btn${deafened ? ' chat-voice-self-btn--off' : ''}`}
          disabled={!joined}
          aria-label={deafened ? 'Undeafen' : 'Deafen'}
          title={deafened ? 'Undeafen' : 'Deafen'}
          onClick={toggleDeafen}>
          {deafened ? <IconDeafenOff /> : <IconDeafen />}
        </button>
        <button
          type="button"
          className={`chat-voice-self-btn${!camOn ? ' chat-voice-self-btn--off' : ''}`}
          disabled={!joined}
          aria-label={camOn ? 'Turn camera off' : 'Turn camera on'}
          title={camOn ? 'Camera off' : 'Camera on'}
          onClick={toggleCam}>
          {camOn ? <IconCam /> : <IconCamOff />}
        </button>
        {joined ? (
          <button
            type="button"
            className="chat-voice-self-btn chat-voice-self-btn--danger"
            aria-label="Disconnect"
            title="Disconnect"
            onClick={requestLeave}>
            <IconDisconnect />
          </button>
        ) : onOpenSettings ? (
          <button
            type="button"
            className="chat-voice-self-btn"
            aria-label="Chat settings"
            title="Settings"
            onClick={onOpenSettings}>
            <IconSettings />
          </button>
        ) : null}
      </div>
    </div>
  );
}

function IconMic() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
      <path
        fill="currentColor"
        d="M12 14a3 3 0 0 0 3-3V5a3 3 0 0 0-6 0v6a3 3 0 0 0 3 3Zm5-3a5 5 0 0 1-10 0H5a7 7 0 0 0 6 6.92V21h2v-3.08A7 7 0 0 0 19 11h-2Z"
      />
    </svg>
  );
}

function IconMicOff() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
      <path
        fill="currentColor"
        d="M19 11h-1.7c0 .58-.14 1.13-.37 1.62l1.27 1.27A6.94 6.94 0 0 0 19 11ZM12 3a3 3 0 0 0-3 3v.18l5.72 5.72A3 3 0 0 0 15 11V6a3 3 0 0 0-3-3Zm4.91 12.91-1.41-1.41-.04.04A4.98 4.98 0 0 1 12 14a5 5 0 0 1-5-5H5a6.97 6.97 0 0 0 5.92 6.91L10 18.83V21h4v-2.17l.09-.09 3.82 3.82 1.41-1.41L4.27 3.46 2.86 4.87l16.05 16.04Z"
      />
    </svg>
  );
}

function IconDeafen() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
      <path
        fill="currentColor"
        d="M12 3a9 9 0 0 0-9 9v7a2 2 0 0 0 2 2h3v-8H5v-1a7 7 0 0 1 14 0v1h-3v8h3a2 2 0 0 0 2-2v-7a9 9 0 0 0-9-9Z"
      />
    </svg>
  );
}

function IconDeafenOff() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
      <path
        fill="currentColor"
        d="M2.1 3.51 3.51 2.1l18.38 18.39-1.41 1.41-3.1-3.1H17v-1.8l-2-2V19h2a2 2 0 0 0 2-2v-7c0-1.4-.35-2.72-.97-3.89L12.7 9.44A5 5 0 0 1 17 12v1h-2v-1c0-.34-.04-.67-.11-.99L2.1 3.51ZM7 13v8H5a2 2 0 0 1-2-2v-7a8.96 8.96 0 0 1 5.56-8.3l1.52 1.52A7 7 0 0 0 5 12v1h2Z"
      />
    </svg>
  );
}

function IconCam() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
      <path
        fill="currentColor"
        d="M17 10.5V7a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3.5l4 4v-11l-4 4Z"
      />
    </svg>
  );
}

function IconCamOff() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
      <path
        fill="currentColor"
        d="M3.27 2 2 3.27 4.73 6H4a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h11c.35 0 .68-.1.97-.26L19.73 22 21 20.73 3.27 2ZM17 10.5l4-4v11l-2.39-2.39.01-.11V10.5H17ZM15 8.82 7.18 16H4V8h9.18L15 8.82Z"
      />
    </svg>
  );
}

function IconDisconnect() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
      <path
        fill="currentColor"
        d="M6.62 10.79a15.05 15.05 0 0 0 6.59 6.59l2.2-2.2a1 1 0 0 1 1.01-.24 11.36 11.36 0 0 0 3.58.57 1 1 0 0 1 1 1V20a1 1 0 0 1-1 1A17 17 0 0 1 3 4a1 1 0 0 1 1-1h3.5a1 1 0 0 1 1 1 11.36 11.36 0 0 0 .57 3.58 1 1 0 0 1-.25 1.01l-2.2 2.2Z"
        transform="rotate(135 12 12)"
      />
    </svg>
  );
}

function IconSettings() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden>
      <path
        fill="currentColor"
        d="M19.14 12.94c.04-.31.06-.63.06-.94s-.02-.63-.06-.94l2.03-1.58a.5.5 0 0 0 .12-.64l-1.92-3.32a.5.5 0 0 0-.6-.22l-2.39.96a7.07 7.07 0 0 0-1.63-.94l-.36-2.54A.5.5 0 0 0 13.9 2h-3.8a.5.5 0 0 0-.49.42l-.36 2.54c-.59.24-1.13.55-1.63.94l-2.39-.96a.5.5 0 0 0-.6.22L2.71 8.48a.5.5 0 0 0 .12.64l2.03 1.58c-.04.31-.06.63-.06.94s.02.63.06.94L2.83 14.5a.5.5 0 0 0-.12.64l1.92 3.32c.14.24.43.34.68.22l2.39-.96c.5.39 1.04.71 1.63.94l.36 2.54c.05.24.25.42.49.42h3.8c.24 0 .44-.18.49-.42l.36-2.54c.59-.24 1.13-.55 1.63-.94l2.39.96c.25.12.54.02.68-.22l1.92-3.32a.5.5 0 0 0-.12-.64l-2.03-1.58ZM12 15.5A3.5 3.5 0 1 1 12 8a3.5 3.5 0 0 1 0 7.5Z"
      />
    </svg>
  );
}
