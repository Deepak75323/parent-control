/** UPI-style analytics dimensions shared by parent and child apps. */
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
    DEVICE: 'device',
    STREAM: 'stream',
    SUPPORT: 'support',
    ACCOUNT: 'account',
    PERMISSIONS: 'permissions',
    ONBOARDING: 'onboarding',
    FCM: 'fcm',
    SYSTEM: 'system',
  },
  subSection: {
    SCREEN: 'screen',
    BUTTON: 'button',
    FORM: 'form',
    LIST: 'list',
    DIALOG: 'dialog',
    WAKE: 'wake',
    HEARTBEAT: 'heartbeat',
  },
  eventLabel: {
    SCREEN_VIEW: 'screen_view',
    BACK_NAV: 'back_nav',
    TAP: 'tap',
    SUBMIT: 'submit',
    SUCCESS: 'success',
    FAILURE: 'failure',
    INGRESS: 'ingress',
    EXCEPTION: 'exception',
  },
} as const;

export const COLLECTOR_VERSION = '1.0.0';
