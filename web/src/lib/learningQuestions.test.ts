import {describe, expect, it} from 'vitest';
import {learningExamBank} from './learningExamBank';
import {
  learningQuestions,
  pickLearningQuestion,
  setActiveLearningPool,
  type LearningQuestion,
} from './learningQuestions';

describe('learningQuestions', () => {
  it('defaults to curated exam practice categories', () => {
    const categories = new Set(learningQuestions.map(q => q.category));
    expect(categories.has('CSAT')).toBe(true);
    expect(categories.has('BPSC')).toBe(true);
    expect(categories.has('Gov Exam English')).toBe(true);
    expect(categories.has('Gov Exam Maths')).toBe(true);
    expect(categories.has('JEE Maths')).toBe(true);
    expect(categories.has('JEE Physics')).toBe(true);
    expect(categories.has('Current Affairs')).toBe(true);
    expect(learningExamBank.length).toBeGreaterThan(20);
  });

  it('picks the same question for the same seed', () => {
    const a = pickLearningQuestion('msg-123');
    const b = pickLearningQuestion('msg-123');
    expect(a.id).toBe(b.id);
  });

  it('prefers exam practice for the footer challenge', () => {
    const pool: LearningQuestion[] = [
      {
        id: 'news-1',
        kind: 'news',
        category: 'AI News',
        label: '🤖 AI News:',
        question: 'Some AI headline',
      },
      {
        id: 'csat-1',
        kind: 'question',
        category: 'CSAT',
        label: '🧠 CSAT (UPSC):',
        question: 'CSAT practice?',
        answer: 'Yes',
      },
    ];
    setActiveLearningPool(pool);
    const picked = pickLearningQuestion('footer', null, {preferExamPractice: true});
    expect(picked.category).toBe('CSAT');
  });
});
