import {
  getActiveRouteName,
} from '../navigation/getActiveRouteName';
import {scrubSentryEvent, trackScreen} from '../services/telemetry';

describe('getActiveRouteName', () => {
  it('returns nested active route', () => {
    const name = getActiveRouteName({
      stale: false,
      type: 'stack',
      key: 'root',
      index: 0,
      routeNames: ['Main'],
      routes: [
        {
          key: 'main',
          name: 'MainTabs',
          state: {
            stale: false,
            type: 'tab',
            key: 'tabs',
            index: 1,
            routeNames: ['device', 'me'],
            routes: [
              {key: 'd', name: 'device'},
              {key: 'm', name: 'me'},
            ],
          },
        },
      ],
    });
    expect(name).toBe('me');
  });
});

describe('scrubSentryEvent', () => {
  it('redacts sensitive request headers', () => {
    const event = scrubSentryEvent({
      request: {
        headers: {
          Authorization: 'Bearer secret',
          'Content-Type': 'application/json',
        },
      },
    });
    expect(event.request?.headers?.Authorization).toBe('[redacted]');
    expect(event.request?.headers?.['Content-Type']).toBe('application/json');
  });
});

describe('trackScreen', () => {
  it('does not throw when Sentry is disabled', () => {
    expect(() => trackScreen('Login')).not.toThrow();
  });
});
