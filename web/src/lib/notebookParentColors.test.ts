import {describe, expect, it} from 'vitest';
import {
  NOTEBOOK_PARENT_COLORS,
  assignNotebookParentColors,
  notebookParentColorForUser,
} from './notebookParentColors';

describe('notebookParentColors', () => {
  it('assigns unique colors by earliest join first', () => {
    const map = assignNotebookParentColors([
      {userId: 'b', joinedAt: '2026-09-21T10:00:00Z'},
      {userId: 'a', joinedAt: '2026-09-21T09:00:00Z'},
      {userId: 'c', joinedAt: '2026-09-21T11:00:00Z'},
    ]);
    expect(map.get('a')).toBe(NOTEBOOK_PARENT_COLORS[0]);
    expect(map.get('b')).toBe(NOTEBOOK_PARENT_COLORS[1]);
    expect(map.get('c')).toBe(NOTEBOOK_PARENT_COLORS[2]);
    expect(new Set(map.values()).size).toBe(3);
  });

  it('ties joinedAt by userId for stable order', () => {
    const map = assignNotebookParentColors([
      {userId: 'z', joinedAt: '2026-09-21T09:00:00Z'},
      {userId: 'm', joinedAt: '2026-09-21T09:00:00Z'},
    ]);
    expect(map.get('m')).toBe(NOTEBOOK_PARENT_COLORS[0]);
    expect(map.get('z')).toBe(NOTEBOOK_PARENT_COLORS[1]);
  });

  it('falls back to a deterministic palette color for unknown senders', () => {
    const color = notebookParentColorForUser('unknown-user', new Map());
    expect(NOTEBOOK_PARENT_COLORS).toContain(color);
    expect(notebookParentColorForUser('unknown-user', new Map())).toBe(color);
  });
});
