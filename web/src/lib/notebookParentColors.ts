/** Pastel highlighter colors for Parent ↔ Parent notebook messages (UI-only). */

export const NOTEBOOK_PARENT_COLORS = [
  'rgba(180, 220, 255, 0.78)', // blue
  'rgba(230, 200, 255, 0.78)', // lavender
  'rgba(180, 235, 210, 0.78)', // mint
  'rgba(255, 210, 180, 0.78)', // peach
  'rgba(255, 235, 160, 0.78)', // lemon
  'rgba(255, 200, 220, 0.78)', // rose
  'rgba(190, 230, 255, 0.78)', // sky
  'rgba(210, 200, 255, 0.78)', // periwinkle
] as const;

export type NotebookMemberColorSource = {
  userId: string;
  joinedAt: string;
};

function hashUserId(userId: string): number {
  let h = 2166136261;
  for (let i = 0; i < userId.length; i += 1) {
    h ^= userId.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

/**
 * Stable per-room colors: earliest join → first palette slot.
 * Same members → same colors for every viewer.
 */
export function assignNotebookParentColors(
  members: NotebookMemberColorSource[],
): Map<string, string> {
  const sorted = [...members].sort((a, b) => {
    const byJoin = a.joinedAt.localeCompare(b.joinedAt);
    if (byJoin !== 0) {
      return byJoin;
    }
    return a.userId.localeCompare(b.userId);
  });
  const map = new Map<string, string>();
  sorted.forEach((member, index) => {
    const color = NOTEBOOK_PARENT_COLORS[index % NOTEBOOK_PARENT_COLORS.length]!;
    map.set(member.userId, color);
  });
  return map;
}

/** Resolve color for a sender; fall back to hash if not in the member map. */
export function notebookParentColorForUser(
  userId: string,
  colorsByUserId: Map<string, string>,
): string {
  const assigned = colorsByUserId.get(userId);
  if (assigned) {
    return assigned;
  }
  return NOTEBOOK_PARENT_COLORS[hashUserId(userId) % NOTEBOOK_PARENT_COLORS.length]!;
}
