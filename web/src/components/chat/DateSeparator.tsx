type Props = {
  label: string;
};

export function DateSeparator({label}: Props) {
  if (!label) {
    return null;
  }
  return (
    <div className="chat-date-separator" role="separator" aria-label={label}>
      <span>{label}</span>
    </div>
  );
}
