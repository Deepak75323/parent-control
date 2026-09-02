import {resolveChatDisplayNickname} from './chatDisplayNickname';

export type ChatMentionMember = {
  userId: string;
  label: string;
  nickname?: string;
};

export type MentionCandidate = {
  userId: string;
  token: string;
  displayName: string;
  subtitle: string;
};

export function mentionTokenForMember(member: ChatMentionMember): string {
  const label = member.label.trim();
  if (label.startsWith('@')) {
    return label;
  }
  const displayName = resolveChatDisplayNickname(member.nickname, member.label);
  const compact = displayName.replace(/[^\w]/g, '');
  return compact ? `@${compact}` : '@parent';
}

export function buildMentionCandidates(members: ChatMentionMember[]): MentionCandidate[] {
  return members.map(member => {
    const token = mentionTokenForMember(member);
    const displayName = resolveChatDisplayNickname(member.nickname, member.label);
    const subtitle = member.label.startsWith('@') ? displayName : member.label;
    return {
      userId: member.userId,
      token,
      displayName,
      subtitle,
    };
  });
}

export function findActiveMentionQuery(
  text: string,
  cursorIndex: number,
): {query: string; start: number; end: number} | null {
  const before = text.slice(0, cursorIndex);
  const match = /(?:^|\s)@([^\s@]*)$/.exec(before);
  if (!match) {
    return null;
  }
  const query = match[1] ?? '';
  const start = before.length - query.length - 1;
  return {query, start, end: cursorIndex};
}

export function filterMentionCandidates(
  candidates: MentionCandidate[],
  query: string,
): MentionCandidate[] {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return candidates;
  }
  return candidates.filter(candidate => {
    const token = candidate.token.slice(1).toLowerCase();
    const name = candidate.displayName.toLowerCase();
    return token.includes(normalized) || name.includes(normalized);
  });
}

export function insertMention(
  text: string,
  start: number,
  end: number,
  token: string,
): {nextText: string; cursor: number} {
  const prefix = text.slice(0, start);
  const suffix = text.slice(end);
  const nextText = `${prefix}${token} ${suffix}`;
  const cursor = prefix.length + token.length + 1;
  return {nextText, cursor};
}

export function collectMentionTokens(members: ChatMentionMember[]): string[] {
  return buildMentionCandidates(members).map(candidate => candidate.token);
}

export type MessageTextPart =
  | {type: 'text'; value: string}
  | {type: 'mention'; value: string; known: boolean};

export function splitMessageText(text: string, knownTokens: string[]): MessageTextPart[] {
  if (!text) {
    return [];
  }
  const tokenSet = new Set(knownTokens.map(token => token.toLowerCase()));
  const pattern = /(@[A-Za-z0-9_]+)/g;
  const parts: MessageTextPart[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > lastIndex) {
      parts.push({type: 'text', value: text.slice(lastIndex, match.index)});
    }
    const value = match[1] ?? '';
    parts.push({
      type: 'mention',
      value,
      known: tokenSet.has(value.toLowerCase()),
    });
    lastIndex = match.index + value.length;
  }

  if (lastIndex < text.length) {
    parts.push({type: 'text', value: text.slice(lastIndex)});
  }

  return parts.length > 0 ? parts : [{type: 'text', value: text}];
}
