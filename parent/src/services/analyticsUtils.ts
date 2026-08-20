/**
 * UPI-style client analytics: queue locally, flush batches to backend ingest API.
 *
 * Usage:
 *   clientAnalyticsUtils(EventType.impression, {
 *     section: AnalyticsConstants.eventSection.NAVIGATION,
 *     subSection: 'DeviceList',
 *     eventLabel: AnalyticsConstants.eventLabel.SCREEN_VIEW,
 *   });
 */
import {api} from './api';
import {ApiPaths} from '../constants/apiPaths';
import {getCorrelationId} from '../utils/correlation';
import {useAuthStore} from '../store/authStore';
import {
  AnalyticsConstants,
  COLLECTOR_VERSION,
  EventType,
  type EventTypeName,
} from './analyticsConstants';

export {AnalyticsConstants, EventType};

type AnalyticsAttributes = Record<string, string | number | boolean>;

export type ClientAnalyticsParams = {
  section: string;
  subSection?: string;
  eventLabel?: string;
  screen?: string;
  attributes?: AnalyticsAttributes;
};

type QueuedEvent = {
  eventType: EventTypeName;
  section: string;
  subSection?: string;
  eventLabel?: string;
  screen?: string;
  timestampMs: number;
  attributes?: AnalyticsAttributes;
};

const MAX_QUEUE = 200;
const MAX_BATCH = 25;
const FLUSH_INTERVAL_MS = 30_000;

const queue: QueuedEvent[] = [];
let flushTimer: ReturnType<typeof setInterval> | null = null;
let flushInFlight: Promise<void> | null = null;
let initialized = false;

function analyticsEnabled(): boolean {
  return process.env.ENABLE_ANALYTICS !== 'false';
}

function sanitizeAttributes(
  attrs?: AnalyticsAttributes,
): AnalyticsAttributes | undefined {
  if (!attrs) return undefined;
  const out: AnalyticsAttributes = {};
  for (const [key, value] of Object.entries(attrs)) {
    const lower = key.toLowerCase();
    if (
      lower.includes('password') ||
      lower.includes('token') ||
      lower.includes('secret') ||
      lower.includes('authorization')
    ) {
      out[key] = '[redacted]';
    } else {
      out[key] = value;
    }
  }
  return Object.keys(out).length ? out : undefined;
}

function enqueue(event: QueuedEvent): void {
  if (queue.length >= MAX_QUEUE) {
    queue.shift();
  }
  queue.push(event);
  if (queue.length >= MAX_BATCH) {
    void flushClientAnalytics();
  }
}

export function clientAnalyticsUtils(
  eventType: EventTypeName,
  params: ClientAnalyticsParams,
): void {
  if (!analyticsEnabled()) return;
  const event: QueuedEvent = {
    eventType,
    section: params.section,
    subSection: params.subSection,
    eventLabel: params.eventLabel,
    screen: params.screen,
    timestampMs: Date.now(),
    attributes: sanitizeAttributes(params.attributes),
  };
  if (__DEV__) {
    console.info('[analytics]', eventType, event);
  }
  enqueue(event);
}

export function trackScreenAnalytics(screenName: string): void {
  clientAnalyticsUtils(EventType.impression, {
    section: AnalyticsConstants.eventSection.NAVIGATION,
    subSection: screenName,
    eventLabel: AnalyticsConstants.eventLabel.SCREEN_VIEW,
    screen: screenName,
  });
}

export function trackBackAnalytics(fromScreen: string, toScreen: string): void {
  clientAnalyticsUtils(EventType.back, {
    section: AnalyticsConstants.eventSection.NAVIGATION,
    subSection: toScreen,
    eventLabel: AnalyticsConstants.eventLabel.BACK_NAV,
    screen: toScreen,
    attributes: {from_screen: fromScreen},
  });
}

export function trackClickAnalytics(
  section: string,
  subSection: string,
  eventLabel: string,
  attributes?: AnalyticsAttributes,
): void {
  clientAnalyticsUtils(EventType.click, {
    section,
    subSection,
    eventLabel,
    attributes,
  });
}

export function trackErrorAnalytics(
  section: string,
  eventLabel: string,
  attributes?: AnalyticsAttributes,
): void {
  clientAnalyticsUtils(EventType.error, {
    section,
    eventLabel,
    attributes,
  });
}

export async function flushClientAnalytics(): Promise<void> {
  if (!analyticsEnabled() || queue.length === 0) return;
  const token = useAuthStore.getState().accessToken;
  if (!token) return;

  if (flushInFlight) {
    await flushInFlight;
    return;
  }

  flushInFlight = (async () => {
    while (queue.length > 0) {
      const batch = queue.splice(0, MAX_BATCH);
      try {
        await api.post(ApiPaths.PARENT_ANALYTICS_BATCH, {
          correlationId: getCorrelationId(),
          collectorVersion: COLLECTOR_VERSION,
          events: batch,
        });
      } catch (err) {
        queue.unshift(...batch);
        if (__DEV__) {
          console.warn('[analytics] batch upload failed', err);
        }
        break;
      }
    }
  })().finally(() => {
    flushInFlight = null;
  });

  await flushInFlight;
}

export function initClientAnalytics(): void {
  if (initialized || !analyticsEnabled()) return;
  initialized = true;
  if (flushTimer) return;
  flushTimer = setInterval(() => {
    void flushClientAnalytics();
  }, FLUSH_INTERVAL_MS);
}

/** Map legacy telemetry event names to structured analytics clicks. */
export function trackTelemetryEventAsAnalytics(
  eventName: string,
  props?: Record<string, string | number | boolean | null | undefined>,
): void {
  const [section = AnalyticsConstants.eventSection.SYSTEM, ...rest] =
    eventName.split('.');
  const subSection = rest.length ? rest.join('.') : AnalyticsConstants.subSection.BUTTON;
  const attrs: AnalyticsAttributes = {};
  if (props) {
    for (const [k, v] of Object.entries(props)) {
      if (v != null) attrs[k] = v;
    }
  }
  const isFailure = /failure|error|failed/i.test(eventName);
  clientAnalyticsUtils(isFailure ? EventType.error : EventType.click, {
    section,
    subSection,
    eventLabel: eventName,
    attributes: Object.keys(attrs).length ? attrs : undefined,
  });
}
