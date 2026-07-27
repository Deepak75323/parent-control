/**
 * Decide whether an existing STOMP client can be reused.
 *
 * Important: `client.active` is true as soon as activate() runs — even while the
 * CONNECT handshake is stuck. Reuse only when STOMP is fully connected.
 */
export function shouldReuseStompClient(
  client: {active: boolean; connected: boolean} | null | undefined,
  connectToken: string | null,
  accessToken: string,
): boolean {
  return !!client?.connected && connectToken === accessToken;
}
