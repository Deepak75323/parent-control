type Props = {
  count: number;
  onClick?: () => void;
};

export function UnreadSeparator({count, onClick}: Props) {
  if (count <= 0) {
    return null;
  }
  const label = count === 1 ? '1 unread question' : `${count} unread questions`;
  return (
    <div className="chat-unread-separator-wrap">
      <span className="chat-unread-separator-line" />
      <button
        type="button"
        className="chat-unread-separator"
        aria-label={label}
        title={`${label} — Escape or Mark as read to catch up`}
        onClick={onClick}>
        {label}
      </button>
      <span className="chat-unread-separator-line" />
    </div>
  );
}
