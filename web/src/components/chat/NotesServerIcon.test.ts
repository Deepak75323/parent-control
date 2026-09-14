import {describe, expect, it} from 'vitest';
import {isNotesServerName} from './NotesServerIcon';

describe('isNotesServerName', () => {
  it('matches Notes case-insensitively', () => {
    expect(isNotesServerName('Notes')).toBe(true);
    expect(isNotesServerName('notes')).toBe(true);
    expect(isNotesServerName(' NOTES ')).toBe(true);
  });

  it('does not match other server names', () => {
    expect(isNotesServerName('Family')).toBe(false);
    expect(isNotesServerName('Notebook')).toBe(false);
    expect(isNotesServerName('')).toBe(false);
    expect(isNotesServerName(null)).toBe(false);
  });
});
