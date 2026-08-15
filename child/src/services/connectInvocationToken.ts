// Per-invocation token for connectChildStream.
//
// Every new connect attempt claims a fresh token. Any earlier invocation still
// sitting in its retry backoff or mid-await can compare its captured token
// against the current one and bail silently if it has been superseded.
//
// stopActiveStream / disconnectStream(true) invalidates all outstanding tokens
// so zombie retry loops from an already-stopped session cannot re-acquire the
// camera, recreate a peer connection, or POST offers for a dead session.

let currentToken = 0;

export function nextConnectToken(): number {
  currentToken += 1;
  return currentToken;
}

export function isCurrentToken(token: number): boolean {
  return token === currentToken;
}

export function invalidateAllConnectTokens(): void {
  // Bump past any outstanding token so isCurrentToken(...) returns false for
  // every previously-issued value until nextConnectToken() is called again.
  currentToken += 1;
}

export function __resetForTests(): void {
  currentToken = 0;
}
