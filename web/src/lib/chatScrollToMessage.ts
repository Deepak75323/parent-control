export const CHAT_MESSAGE_HIGHLIGHT_MS = 2200;

export function chatMessageElementId(messageId: string): string {
  return `chat-message-${messageId}`;
}

export function computeCenteredScrollTop(
  containerScrollTop: number,
  containerClientHeight: number,
  containerTop: number,
  elementTop: number,
  elementHeight: number,
): number {
  const offset =
    elementTop - containerTop - containerClientHeight / 2 + elementHeight / 2;
  return Math.max(0, containerScrollTop + offset);
}

export function scrollChatMessageIntoView(
  container: HTMLElement | null,
  messageId: string,
  behavior: ScrollBehavior = 'smooth',
): boolean {
  const element = document.getElementById(chatMessageElementId(messageId));
  if (!element) {
    return false;
  }

  if (container) {
    const containerRect = container.getBoundingClientRect();
    const elementRect = element.getBoundingClientRect();
    const targetTop = computeCenteredScrollTop(
      container.scrollTop,
      container.clientHeight,
      containerRect.top,
      elementRect.top,
      elementRect.height,
    );
    container.scrollTo({top: targetTop, behavior});
  } else {
    element.scrollIntoView({behavior, block: 'center', inline: 'nearest'});
  }

  return true;
}

export function flashChatMessageHighlight(
  messageId: string,
  durationMs = CHAT_MESSAGE_HIGHLIGHT_MS,
): () => void {
  const element = document.getElementById(chatMessageElementId(messageId));
  if (!element) {
    return () => undefined;
  }

  element.classList.remove('chat-message-row--highlight');
  void element.offsetWidth;
  element.classList.add('chat-message-row--highlight');

  const timeoutId = window.setTimeout(() => {
    element.classList.remove('chat-message-row--highlight');
  }, durationMs);

  return () => {
    window.clearTimeout(timeoutId);
    element.classList.remove('chat-message-row--highlight');
  };
}
