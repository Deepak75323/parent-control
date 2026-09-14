/** Grow a textarea to fit its content up to maxHeightPx (WhatsApp-style). */
export function autosizeTextarea(
  textarea: HTMLTextAreaElement,
  maxHeightPx = 144,
): void {
  textarea.style.height = 'auto';
  const next = Math.min(textarea.scrollHeight, maxHeightPx);
  textarea.style.height = `${next}px`;
  textarea.style.overflowY = textarea.scrollHeight > maxHeightPx ? 'auto' : 'hidden';
}
