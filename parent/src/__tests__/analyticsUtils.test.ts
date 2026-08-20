import {
  AnalyticsConstants,
  clientAnalyticsUtils,
  EventType,
  flushClientAnalytics,
  initClientAnalytics,
  trackScreenAnalytics,
} from '../services/analyticsUtils';
import {api} from '../services/api';
import {useAuthStore} from '../store/authStore';

jest.mock('../services/api', () => ({
  api: {post: jest.fn().mockResolvedValue({data: {success: true}})},
}));

jest.mock('../store/authStore', () => ({
  useAuthStore: {getState: jest.fn(() => ({accessToken: 'test-token'}))},
}));

describe('clientAnalyticsUtils', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    process.env.ENABLE_ANALYTICS = 'true';
    initClientAnalytics();
  });

  it('queues impression events without throwing', () => {
    expect(() =>
      clientAnalyticsUtils(EventType.impression, {
        section: AnalyticsConstants.eventSection.NAVIGATION,
        subSection: 'DeviceList',
        eventLabel: AnalyticsConstants.eventLabel.SCREEN_VIEW,
        screen: 'DeviceList',
      }),
    ).not.toThrow();
  });

  it('flushes batch to parent analytics API when authenticated', async () => {
    trackScreenAnalytics('Login');
    await flushClientAnalytics();
    expect(api.post).toHaveBeenCalledWith(
      '/api/v1/parent/analytics/batch',
      expect.objectContaining({
        events: expect.arrayContaining([
          expect.objectContaining({
            eventType: 'impression',
            section: 'navigation',
            subSection: 'Login',
          }),
        ]),
      }),
    );
  });

  it('skips flush when not authenticated', async () => {
    (useAuthStore.getState as jest.Mock).mockReturnValueOnce({accessToken: null});
    trackScreenAnalytics('Login');
    await flushClientAnalytics();
    expect(api.post).not.toHaveBeenCalled();
  });
});
