/** Learning feed for Parent ↔ Parent notebook chat (UI-only): exam practice + news. */

import {learningExamBank} from './learningExamBank';

export type LearningKind = 'question' | 'news';

export type LearningCategory =
  | 'Current Affairs'
  | 'CSAT'
  | 'BPSC'
  | 'Gov Exam English'
  | 'Gov Exam Maths'
  | 'JEE Maths'
  | 'JEE Physics'
  | 'AI News'
  | 'Maths News'
  | 'Technology News'
  /** Legacy categories (older cache / mapped feeds). */
  | 'Maths'
  | 'Reasoning'
  | 'Quantitative Aptitude'
  | 'General Knowledge'
  | 'Science'
  | 'Geography'
  | 'History'
  | 'Basic Economics'
  | 'Computer Science';

export type LearningQuestion = {
  id: string;
  kind?: LearningKind;
  category: LearningCategory;
  /** Short heading, e.g. "🧠 CSAT (UPSC):" or "🤖 AI News:" */
  label: string;
  question: string;
  /** Present for practice questions; omitted/empty for news briefs. */
  answer?: string;
  source?: string;
};

/** Default pool = curated exam practice (always available offline). */
export const learningQuestions: LearningQuestion[] = learningExamBank;

function hashSeed(seed: string): number {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i += 1) {
    h ^= seed.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

let activeLearningPool: LearningQuestion[] = learningQuestions;

export function getActiveLearningPool(): LearningQuestion[] {
  return activeLearningPool.length > 0 ? activeLearningPool : learningQuestions;
}

export function setActiveLearningPool(questions: LearningQuestion[]): void {
  activeLearningPool = questions.length > 0 ? questions : learningQuestions;
}

export function resetActiveLearningPoolForTests(): void {
  activeLearningPool = learningQuestions;
}

export function isNewsItem(item: LearningQuestion): boolean {
  return item.kind === 'news' || !item.answer;
}

export type PickLearningOptions = {
  /** Prefer CSAT / exam practice / current affairs for the footer card. */
  preferExamPractice?: boolean;
  /** @deprecated use preferExamPractice */
  preferReasoning?: boolean;
};

const EXAM_FOCUS: LearningCategory[] = [
  'CSAT',
  'BPSC',
  'Current Affairs',
  'Gov Exam English',
  'Gov Exam Maths',
  'JEE Maths',
  'JEE Physics',
  'Reasoning',
];

/** Stable pick from the active pool; optionally skip a just-used id. */
export function pickLearningQuestion(
  seed: string,
  avoidId?: string | null,
  options?: PickLearningOptions,
): LearningQuestion {
  const full = getActiveLearningPool();
  let pool = full;
  if (options?.preferExamPractice || options?.preferReasoning) {
    const focused = full.filter(q => EXAM_FOCUS.includes(q.category) && !isNewsItem(q));
    if (focused.length > 0) {
      pool = focused;
    }
  }
  if (pool.length === 0) {
    throw new Error('learningQuestions pool is empty');
  }
  let idx = hashSeed(seed) % pool.length;
  if (avoidId && pool[idx]!.id === avoidId && pool.length > 1) {
    idx = (idx + 1) % pool.length;
  }
  return pool[idx]!;
}

export function learningChallengeSeed(roomId: string, latestMessageId?: string | null): string {
  return `challenge:${roomId}:${latestMessageId ?? 'empty'}`;
}

export function labelForCategory(category: LearningCategory, kind: LearningKind = 'question'): string {
  if (kind === 'news') {
    switch (category) {
      case 'AI News':
        return '🤖 AI News:';
      case 'Maths News':
        return '📐 Maths News:';
      case 'Technology News':
        return '💻 Tech News:';
      case 'Current Affairs':
        return '🗞 Current Affairs:';
      default:
        return '📰 News:';
    }
  }
  switch (category) {
    case 'CSAT':
      return '🧠 CSAT (UPSC):';
    case 'BPSC':
      return '🏛 BPSC:';
    case 'Gov Exam English':
      return '📘 English (Gov exam):';
    case 'Gov Exam Maths':
      return '🧮 Maths (Gov exam):';
    case 'JEE Maths':
      return '📐 JEE Advanced Maths:';
    case 'JEE Physics':
      return '⚛️ JEE Advanced Physics:';
    case 'Current Affairs':
      return '🗞 Current Affairs:';
    default:
      return '🧠 Practice:';
  }
}
