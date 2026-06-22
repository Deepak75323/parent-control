import {ScaledSize} from 'react-native';

export type StreamLayout = {
  isLandscape: boolean;
  isTablet: boolean;
  primaryFlex: number;
  pipWidth: number;
  pipHeight: number;
  topBarHeight: number;
  controlBarHeight: number;
};

const TABLET_MIN_WIDTH = 600;

export function getStreamLayout(window: ScaledSize): StreamLayout {
  const {width, height} = window;
  const isLandscape = width > height;
  const isTablet = Math.min(width, height) >= TABLET_MIN_WIDTH;

  const pipWidth = isTablet
    ? isLandscape
      ? 200
      : 160
    : isLandscape
      ? Math.round(width * 0.22)
      : Math.round(width * 0.32);

  const pipHeight = isTablet
    ? isLandscape
      ? 150
      : 200
    : isLandscape
      ? Math.round(pipWidth * 0.75)
      : Math.round(pipWidth * 1.33);

  return {
    isLandscape,
    isTablet,
    primaryFlex: 1,
    pipWidth: Math.max(pipWidth, 112),
    pipHeight: Math.max(pipHeight, 150),
    topBarHeight: isTablet ? 52 : 44,
    controlBarHeight: isTablet ? 64 : 56,
  };
}

export function clampPipPosition(
  x: number,
  y: number,
  pipW: number,
  pipH: number,
  areaW: number,
  areaH: number,
  margin = 8,
): {x: number; y: number} {
  return {
    x: Math.min(Math.max(margin, x), Math.max(margin, areaW - pipW - margin)),
    y: Math.min(Math.max(margin, y), Math.max(margin, areaH - pipH - margin)),
  };
}

export function defaultPipPosition(
  layout: StreamLayout,
  areaW: number,
  areaH: number,
  margin = 12,
): {x: number; y: number} {
  return clampPipPosition(
    areaW - layout.pipWidth - margin,
    margin,
    layout.pipWidth,
    layout.pipHeight,
    areaW,
    areaH,
    margin,
  );
}

export const STREAM_COLORS = {
  bg: '#000000',
  overlay: 'rgba(0,0,0,0.55)',
  overlayLight: 'rgba(15,23,42,0.72)',
  live: '#22c55e',
  pending: '#f59e0b',
  failed: '#ef4444',
  text: '#f8fafc',
  textMuted: '#94a3b8',
  accent: '#818cf8',
  danger: '#dc2626',
};
