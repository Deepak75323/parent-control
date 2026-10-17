export function insertTextAtSelection(
  text: string,
  insertion: string,
  selectionStart: number,
  selectionEnd: number,
): {nextText: string; cursor: number} {
  const start = Math.max(0, Math.min(selectionStart, text.length));
  const end = Math.max(start, Math.min(selectionEnd, text.length));
  const nextText = `${text.slice(0, start)}${insertion}${text.slice(end)}`;
  const cursor = start + insertion.length;
  return {nextText, cursor};
}
