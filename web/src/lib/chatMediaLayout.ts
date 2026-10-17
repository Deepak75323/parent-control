import type {CSSProperties} from 'react';

export const CHAT_MEDIA_MAX_WIDTH = 420;
export const CHAT_MEDIA_MAX_HEIGHT = 320;

export type MediaDimensions = {
  width: number;
  height: number;
};

/** Fit media inside chat bounds while preserving aspect ratio. */
export function fitChatMediaDimensions(
  naturalWidth: number,
  naturalHeight: number,
  maxWidth = CHAT_MEDIA_MAX_WIDTH,
  maxHeight = CHAT_MEDIA_MAX_HEIGHT,
): MediaDimensions {
  if (!Number.isFinite(naturalWidth) || !Number.isFinite(naturalHeight)) {
    return {width: maxWidth, height: maxHeight};
  }
  if (naturalWidth <= 0 || naturalHeight <= 0) {
    return {width: maxWidth, height: maxHeight};
  }

  const scale = Math.min(1, maxWidth / naturalWidth, maxHeight / naturalHeight);
  return {
    width: Math.max(1, Math.round(naturalWidth * scale)),
    height: Math.max(1, Math.round(naturalHeight * scale)),
  };
}

export function chatMediaLayoutStyle(
  naturalWidth: number,
  naturalHeight: number,
  maxWidth = CHAT_MEDIA_MAX_WIDTH,
  maxHeight = CHAT_MEDIA_MAX_HEIGHT,
): CSSProperties {
  const {width, height} = fitChatMediaDimensions(
    naturalWidth,
    naturalHeight,
    maxWidth,
    maxHeight,
  );
  return {
    width: `${width}px`,
    height: `${height}px`,
    maxWidth: '100%',
    objectFit: 'contain',
  };
}
