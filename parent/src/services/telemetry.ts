/**
 * Parent-app telemetry: Sentry crash reporting + lightweight analytics breadcrumbs.
 * No-ops when SENTRY_DSN is unset or ENABLE_ANALYTICS=false.
 */
import type {ComponentType} from 'react';
import {getCorrelationId} from '../utils/correlation';

export type TelemetryProps = Record<string, string | number | boolean | null | undefined>;

type SentryModule = typeof import('@sentry/react-native');

let initialized = false;
let sentry: SentryModule | null = null;

function analyticsAllowed(): boolean {
  return process.env.ENABLE_ANALYTICS !== 'false';
}

function dsnConfigured(): boolean {
  return !!process.env.SENTRY_DSN?.trim();
}

export function isTelemetryActive(): boolean {
  return analyticsAllowed() && dsnConfigured() && sentry != null;
}

function sanitizeProps(props?: TelemetryProps): Record<string, string | number | boolean> {
  if (!props) return {};
  const out: Record<string, string | number | boolean> = {};
  for (const [key, value] of Object.entries(props)) {
    if (value == null) continue;
    const lower = key.toLowerCase();
    if (
      lower.includes('password') ||
      lower.includes('token') ||
      lower.includes('secret') ||
      lower.includes('authorization')
    ) {
      out[key] = '[redacted]';
      continue;
    }
    out[key] = value;
  }
  return out;
}

function hashId(value: string): string {
  let h = 0;
  for (let i = 0; i < value.length; i++) {
    h = (Math.imul(31, h) + value.charCodeAt(i)) | 0;
  }
  return `u_${(h >>> 0).toString(16)}`;
}

/** Scrub obvious secrets from Sentry event payloads. */
export function scrubSentryEvent<T extends {request?: {headers?: Record<string, unknown>}}>(
  event: T,
): T {
  const headers = event.request?.headers;
  if (headers) {
    for (const key of Object.keys(headers)) {
      if (/authorization|cookie|token|password/i.test(key)) {
        headers[key] = '[redacted]';
      }
    }
  }
  return event;
}

export function initTelemetry(): void {
  if (initialized) return;
  initialized = true;

  if (!analyticsAllowed() || !dsnConfigured()) {
    if (__DEV__) {
      console.info('[telemetry] disabled (no SENTRY_DSN or ENABLE_ANALYTICS=false)');
    }
    return;
  }

  try {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    sentry = require('@sentry/react-native') as SentryModule;
    sentry.init({
      dsn: process.env.SENTRY_DSN!.trim(),
      environment: process.env.SENTRY_ENVIRONMENT?.trim() || (__DEV__ ? 'development' : 'production'),
      sendDefaultPii: false,
      tracesSampleRate: __DEV__ ? 0 : 0.15,
      beforeSend(event) {
        return scrubSentryEvent(event);
      },
    });
    sentry.setTag('correlation_id', getCorrelationId());
    sentry.setTag('app', 'parent');
  } catch (err) {
    sentry = null;
    if (__DEV__) {
      console.warn('[telemetry] Sentry init failed', err);
    }
  }
}

export function identifyUser(email: string | null | undefined): void {
  if (!sentry) return;
  if (!email) {
    sentry.setUser(null);
    return;
  }
  sentry.setUser({id: hashId(email.toLowerCase().trim())});
}

export function trackScreen(screenName: string): void {
  if (!analyticsAllowed()) return;
  const props = {screen: screenName, correlation_id: getCorrelationId()};
  if (__DEV__) {
    console.info('[telemetry] screen', screenName);
  }
  sentry?.addBreadcrumb({
    category: 'navigation',
    type: 'navigation',
    message: screenName,
    data: props,
    level: 'info',
  });
  void import('./analyticsUtils').then(({trackScreenAnalytics}) => {
    trackScreenAnalytics(screenName);
  }).catch(() => undefined);
}

export function track(event: string, props?: TelemetryProps): void {
  if (!analyticsAllowed()) return;
  const data = {...sanitizeProps(props), correlation_id: getCorrelationId()};
  if (__DEV__) {
    console.info('[telemetry] event', event, data);
  }
  sentry?.addBreadcrumb({
    category: 'analytics',
    message: event,
    data,
    level: 'info',
  });
  void import('./analyticsUtils').then(({trackTelemetryEventAsAnalytics}) => {
    trackTelemetryEventAsAnalytics(event, props);
  }).catch(() => undefined);
}

export function captureException(error: unknown, context?: TelemetryProps): void {
  if (!analyticsAllowed()) return;
  if (__DEV__) {
    console.warn('[telemetry] exception', error, context);
  }
  void import('./analyticsUtils').then(({trackErrorAnalytics, AnalyticsConstants, EventType}) => {
    const message = error instanceof Error ? error.message : String(error);
    trackErrorAnalytics(AnalyticsConstants.eventSection.SYSTEM, AnalyticsConstants.eventLabel.EXCEPTION, {
      message,
      ...sanitizeProps(context),
    });
  }).catch(() => undefined);
  if (!sentry) return;
  if (context) {
    sentry.withScope(scope => {
      scope.setExtras(sanitizeProps(context));
      scope.setTag('correlation_id', getCorrelationId());
      sentry!.captureException(error);
    });
    return;
  }
  sentry.captureException(error);
}

export function wrapRootComponent<P extends object>(
  Component: ComponentType<P>,
): ComponentType<P> {
  initTelemetry();
  if (sentry?.wrap) {
    return sentry.wrap(Component) as ComponentType<P>;
  }
  return Component;
}
