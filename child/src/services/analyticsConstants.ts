/** UPI-style analytics dimensions — child app (mirrors parent constants). */
export const EventType = {
  impression: 'impression',
  click: 'click',
  ingress: 'ingress',
  error: 'error',
  back: 'back',
} as const;

export type EventTypeName = (typeof EventType)[keyof typeof EventType];

export const AnalyticsConstants = {
  eventSection: {
    NAVIGATION: 'navigation',
    AUTH: 'auth',
    PERMISSIONS: 'permissions',
    ONBOARDING: 'onboarding',
    STREAM: 'stream',
    FCM: 'fcm',
    SYSTEM: 'system',
  },
  subSection: {
    SCREEN: 'screen',
    BUTTON: 'button',
    WAKE: 'wake',
    HEARTBEAT: 'heartbeat',
  },
  eventLabel: {
    SCREEN_VIEW: 'screen_view',
    BACK_NAV: 'back_nav',
    TAP: 'tap',
    INGRESS: 'ingress',
    EXCEPTION: 'exception',
  },
} as const;

export const COLLECTOR_VERSION = '1.0.0';
