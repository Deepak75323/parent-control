let chatOnly = false;
let preferredRoomId: string | null = null;
const roomListeners = new Set<() => void>();

export function isChatOnlyPipRequested(): boolean {
  return chatOnly;
}

export function setChatOnlyPip(value: boolean) {
  chatOnly = value;
}

export function chatPipRoomId(): string | null {
  return preferredRoomId;
}

export function subscribeChatPipRoom(listener: () => void): () => void {
  roomListeners.add(listener);
  return () => roomListeners.delete(listener);
}

export function rememberChatRoom(roomId: string | null) {
  preferredRoomId = roomId;
  roomListeners.forEach(listener => listener());
}
