import {afterEach, describe, expect, it, vi} from 'vitest';
import {
  getActiveLearningPool,
  learningQuestions,
  pickLearningQuestion,
  resetActiveLearningPoolForTests,
} from './learningQuestions';
import {
  clearLearningPoolCacheForTests,
  ensureDailyLearningPool,
  hydrateLearningPoolFromCache,
} from './learningQuestionPool';

describe('learningQuestionPool', () => {
  afterEach(() => {
    resetActiveLearningPoolForTests();
    clearLearningPoolCacheForTests();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('hydrates instantly from exam-bank defaults without waiting on the network', () => {
    const result = hydrateLearningPoolFromCache(new Date('2026-09-21T05:00:00Z'));
    expect(result.readyImmediately).toBe(true);
    expect(result.source).toBe('default');
    expect(getActiveLearningPool().length).toBe(learningQuestions.length);
    expect(getActiveLearningPool().some(q => q.category === 'CSAT')).toBe(true);
  });

  it('keeps defaults when all network fetches fail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new Error('network down');
      }),
    );
    const result = await ensureDailyLearningPool(new Date('2026-09-21T05:00:00Z'));
    expect(result.source).toBe('default');
    expect(result.readyImmediately).toBe(true);
    expect(pickLearningQuestion('seed-1').question.length).toBeGreaterThan(0);
  });

  it('merges AI news into the pool when the news API responds', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: string) => {
        const u = String(url);
        if (u.includes('whatstrending.ai')) {
          return {
            ok: true,
            json: async () => ({
              success: true,
              data: [
                {
                  title: 'Open models race ahead this week',
                  summary: 'New open-weight releases reshape the leaderboard.',
                  source: 'TechCrunch',
                  slug: 'open-models-race',
                },
              ],
            }),
          };
        }
        if (u.includes('allorigins') || u.includes('aptitude-gold')) {
          return {ok: false, json: async () => ({}), text: async () => ''};
        }
        return {ok: false, json: async () => ({}), text: async () => ''};
      }),
    );

    const result = await ensureDailyLearningPool(new Date('2026-09-21T05:00:00Z'));
    expect(result.source).toBe('network');
    expect(getActiveLearningPool().some(q => q.category === 'AI News')).toBe(true);
    expect(getActiveLearningPool().some(q => q.category === 'CSAT')).toBe(true);
  });
});
