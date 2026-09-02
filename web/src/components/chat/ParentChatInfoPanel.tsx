type Props = {
  open: boolean;
  width: number;
  isMobile?: boolean;
  isResizable?: boolean;
  onClose: () => void;
};

export function ParentChatInfoPanel({
  open,
  width,
  isMobile = false,
  isResizable = false,
  onClose,
}: Props) {
  if (!open) {
    return null;
  }

  return (
    <aside
      className={[
        'chat-info-panel',
        isMobile ? 'chat-info-panel--drawer' : '',
        !open ? 'chat-info-panel--collapsed' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      style={!isMobile && !isResizable ? {width} : undefined}
      aria-label="Parent chat help"
      aria-hidden={!open}>
      <div className="chat-info-panel-inner">
        <div className="chat-info-panel-head">
          <span className="chat-info-panel-title">Parent chat</span>
          <button
            type="button"
            className="chat-info-panel-close"
            aria-label="Close parent chat panel"
            onClick={onClose}>
            ×
          </button>
        </div>
        <div className="chat-side-help">
          <p>
            Create a room, then invite another parent by <strong>@handle</strong> or{' '}
            <strong>email</strong>.
          </p>
          <ol>
            <li>Create a room on the left</li>
            <li>Invite a parent</li>
            <li>They accept the invite</li>
            <li>Chat in real time</li>
          </ol>
          <p className="muted tiny">Room owners can delete a room for everyone.</p>
        </div>
      </div>
    </aside>
  );
}
