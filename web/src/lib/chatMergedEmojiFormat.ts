/** Normalize line endings and split flattened “I love you N%” blobs into one row each. */
export function normalizeMergedEmojiArt(text: string): string {
  const normalized = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n').replace(/\n+$/, '');
  const loveYouLower = /\s+(?=I\s+love\s+you\s+\d+%)/iu;
  const loveYouRow =
    /\s+(?=I\s+(?:Love|love|LOVE)\s+(?:You|you|YOU)(?:\s+💕?\d+%|\s+\d+%))/u;

  const expanded = normalized.split('\n').flatMap(line => {
    const pattern = loveYouLower.test(line) ? loveYouLower : loveYouRow.test(line) ? loveYouRow : null;
    if (!pattern) {
      return [line];
    }
    const parts = line
      .trim()
      .split(pattern)
      .map(part => part.trimEnd());
    if (parts.length <= 1) {
      return [line];
    }
    return parts;
  });

  return expanded.join('\n');
}

export function countMergedEmojiLines(text: string): number {
  if (!text.trim()) {
    return 0;
  }
  return normalizeMergedEmojiArt(text).split('\n').length;
}
