import {describe, expect, it} from 'vitest';
import {groupMessagesByDate} from './chatMessageGrouping';

describe('groupMessagesByDate', () => {
  it('groups consecutive messages on the same day', () => {
    const groups = groupMessagesByDate([
      {id: '1', createdAt: '2026-09-02T09:00:00.000Z'},
      {id: '2', createdAt: '2026-09-02T10:00:00.000Z'},
      {id: '3', createdAt: '2026-09-03T10:00:00.000Z'},
    ]);
    expect(groups).toHaveLength(2);
    expect(groups[0]!.messages).toHaveLength(2);
    expect(groups[1]!.messages).toHaveLength(1);
  });
});
