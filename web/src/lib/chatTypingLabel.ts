import type {ChatTypingPeer} from '../realtime/realtimeStore';
import {resolveChatDisplayNickname} from './chatDisplayNickname';

export function formatChatTypingLine(peers: ChatTypingPeer[]): string {
  const names = peers
    .map(peer => resolveChatDisplayNickname(peer.nickname, peer.label).trim())
    .filter(Boolean);
  if (names.length === 0) {
    return '';
  }
  if (names.length === 1) {
    return `${names[0]} is typing…`;
  }
  if (names.length === 2) {
    return `${names[0]} and ${names[1]} are typing…`;
  }
  return 'Several people are typing…';
}
