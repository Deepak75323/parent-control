/**
 * Whether a STOMP STREAMING_STATE event means this client should drop its local viewer
 * because another parent client took over (or the session was stopped).
 *
 * @param starting when true, never release — the local client is taking over and may
 *   still receive ENDED for the previous client's room.
 */
export function shouldReleaseViewerOnStreamingEnded(
  event: {status?: string; roomId?: string} | null | undefined,
  activeRoomId: string | null | undefined,
  starting = false,
): boolean {
  if (starting) {
    return false;
  }
  if (!event || event.status !== 'ENDED') {
    return false;
  }
  if (!event.roomId || !activeRoomId) {
    return false;
  }
  return event.roomId === activeRoomId;
}
