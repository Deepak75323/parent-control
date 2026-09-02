import {describe, expect, it} from 'vitest';
import {
  buildMentionCandidates,
  filterMentionCandidates,
  findActiveMentionQuery,
  insertMention,
  mentionTokenForMember,
  splitMessageText,
} from './chatMentions';

describe('chatMentions', () => {
  it('uses nickname as mention token', () => {
    expect(
      mentionTokenForMember({userId: '1', label: '@deepak', nickname: 'Golden Aurora'}),
    ).toBe('@GoldenAurora');
  });

  it('finds mention query at cursor', () => {
    expect(findActiveMentionQuery('hello @dee world', 10)).toEqual({
      query: 'dee',
      start: 6,
      end: 10,
    });
  });

  it('inserts mention token with trailing space', () => {
    expect(insertMention('hi @de', 3, 6, '@deepak')).toEqual({
      nextText: 'hi @deepak ',
      cursor: 11,
    });
  });

  it('highlights known mention tokens in message text', () => {
    const members = [{userId: '1', label: '@deepak', nickname: 'Deepak Parent'}];
    const tokens = buildMentionCandidates(members).map(item => item.token);
    const parts = splitMessageText('hey @DeepakParent check this', tokens);
    expect(parts).toEqual([
      {type: 'text', value: 'hey '},
      {type: 'mention', value: '@DeepakParent', known: true},
      {type: 'text', value: ' check this'},
    ]);
  });

  it('filters candidates by handle or display name', () => {
    const candidates = buildMentionCandidates([
      {userId: '1', label: '@deepak', nickname: 'Golden Aurora'},
      {userId: '2', label: '@sam', nickname: 'Silver Willow'},
    ]);
    expect(filterMentionCandidates(candidates, 'gold').map(item => item.userId)).toEqual(['1']);
  });
});
