/** Max characters users can type in the composer (plaintext). */
export const CHAT_MESSAGE_PLAINTEXT_MAX = 12000;

/** Max characters stored/sent after sealing (obfuscation adds letters per word). */
export const CHAT_MESSAGE_BODY_MAX = 32768;

/** @deprecated Use CHAT_MESSAGE_PLAINTEXT_MAX or CHAT_MESSAGE_BODY_MAX explicitly. */
export const CHAT_MESSAGE_MAX_LENGTH = CHAT_MESSAGE_PLAINTEXT_MAX;
