import {lockedChatBodyDisplay} from '../lib/chatDecryptSession';
import {isEncryptedChatBody} from '../lib/chatMessageCrypto';
import {encodeChatMessagePlaintext} from '../lib/chatMessageObfuscation';

export function isChildVideoSession(sessionId: string | null, streamType: string): boolean {
  return Boolean(sessionId) && streamType !== 'AUDIO';
}

/** App path from a nav link, with the portal base stripped. */
export function pathnameFromAppHref(href: string): string | null {
  const trimmed = href.trim();
  if (!trimmed || trimmed.startsWith('#') || /^(mailto|javascript):/i.test(trimmed)) {
    return null;
  }
  let pathname: string;
  try {
    pathname = new URL(trimmed, 'https://portal.local').pathname;
  } catch {
    return null;
  }
  if (pathname === '/portal') {
    return '/';
  }
  if (pathname.startsWith('/portal/')) {
    pathname = pathname.slice('/portal'.length);
  }
  if (pathname.length > 1 && pathname.endsWith('/')) {
    pathname = pathname.slice(0, -1);
  }
  return pathname;
}

export function hrefOpensParentChat(href: string): boolean {
  const path = pathnameFromAppHref(href);
  return path === '/chat' || (path?.startsWith('/chat/') ?? false);
}

/** A nav link that leaves the live device page, so desktop PiP may open from that click. */
export function hrefLeavesLiveDevice(href: string, deviceId: string | null): boolean {
  const path = pathnameFromAppHref(href);
  if (!path || path === '/') {
    return false;
  }
  if (deviceId && (path === `/devices/${deviceId}` || path.startsWith(`/devices/${deviceId}/`))) {
    return false;
  }
  return path.startsWith('/');
}

export const MEET_PIP_OPEN_SIZE = {width: 380, height: 640};
export const MEET_PIP_MINIMIZED_SIZE = {width: 320, height: 220};

export function meetPipWindowSize(minimized: boolean): {width: number; height: number} {
  return minimized ? MEET_PIP_MINIMIZED_SIZE : MEET_PIP_OPEN_SIZE;
}

/** A server notebook, not a friend direct message. */
export function isServerChatRoom(room: {directMessage?: boolean; memberCount?: number}): boolean {
  if (typeof room.directMessage === 'boolean') {
    return !room.directMessage;
  }
  return room.memberCount !== 2;
}

export function pipChatChoices<T extends {directMessage?: boolean; memberCount?: number}>(
  rooms: T[],
): {servers: T[]; directs: T[]} {
  const servers: T[] = [];
  const directs: T[] = [];
  for (const room of rooms) {
    if (isServerChatRoom(room)) {
      servers.push(room);
    } else {
      directs.push(room);
    }
  }
  return {servers, directs};
}

export function pickLatestChatRoom<T extends {updatedAt: string}>(rooms: T[]): T | null {
  if (rooms.length === 0) {
    return null;
  }
  return [...rooms].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0];
}

export function mergeChatMessages<T extends {id: string; createdAt: string}>(
  existing: T[],
  incoming: T[],
): T[] {
  const byId = new Map(existing.map(message => [message.id, message]));
  for (const message of incoming) {
    const previous = byId.get(message.id);
    byId.set(message.id, previous ? {...previous, ...message} : message);
  }
  return [...byId.values()].sort((a, b) => a.createdAt.localeCompare(b.createdAt));
}

/**
 * The floating window can report pagehide when the main browser window is covered.
 * Tear it down only after that floating window itself has actually closed.
 */
export function shouldReleaseMeetPip(pipClosed: boolean): boolean {
  return pipClosed;
}

/** Padded notes are one token per word, each at least 7 characters. */
export function isPaddedChatBody(body: string): boolean {
  const words = body.trim().split(/\s+/).filter(Boolean);
  return words.length > 0 && words.every(word => word.length >= 7);
}

/** Locked view: keep padded or AES soup, and scramble a plain sentence for display only. */
export function pipLockedText(body: string, seed: string): string {
  if (!body) {
    return '';
  }
  if (isEncryptedChatBody(body) || isPaddedChatBody(body)) {
    return lockedChatBodyDisplay(body);
  }
  return encodeChatMessagePlaintext(body, {seed});
}
