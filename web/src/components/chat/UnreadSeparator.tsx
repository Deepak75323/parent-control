type Props = {
  count: number;
  onClick?: () => void;
};

export function UnreadSeparator({count, onClick}: Props) {
  if (count <= 0) {
    return null;
  }
  const label = count === 1 ? '1 unread message' : `${count} unread messages`;
  return (
    <div className="chat-unread-separator-wrap">
      <button
        type="button"
        className="chat-unread-separator"
        aria-label={label}
        onClick={onClick}>
        {label}
      </button>
    </div>
  );
}
