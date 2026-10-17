/**
 * Daily learning pool: exam practice bank + live AI/tech/maths/current-affairs news.
 * Instant hydrate from cache/defaults; background refresh. UI-only.
 */

import {learningExamBank} from './learningExamBank';
import {
  type LearningCategory,
  type LearningQuestion,
  getActiveLearningPool,
  labelForCategory,
  learningQuestions,
  setActiveLearningPool,
} from './learningQuestions';

const CACHE_KEY = 'pcp_learning_pool_v2';
const TARGET_COUNT = 400;
const DAY_MS = 24 * 60 * 60 * 1000;

type CachedPool = {
  dateKey: string;
  fetchedAt: number;
  questions: LearningQuestion[];
};

let memoryCache: CachedPool | null = null;

function todayKey(now = new Date()): string {
  return now.toISOString().slice(0, 10);
}

function hashShort(text: string): string {
  let h = 0;
  for (let i = 0; i < text.length; i += 1) {
    h = (h * 31 + text.charCodeAt(i)) >>> 0;
  }
  return h.toString(36);
}

function stripHtml(text: string): string {
  return text
    .replace(/<[^>]+>/g, ' ')
    .replace(/&quot;/g, '"')
    .replace(/&#039;/g, "'")
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function readCache(): CachedPool | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as CachedPool;
      if (parsed?.dateKey && Array.isArray(parsed.questions)) {
        memoryCache = parsed;
        return parsed;
      }
    }
  } catch {
    /* ignore */
  }
  return memoryCache;
}

function writeCache(pool: CachedPool): void {
  memoryCache = pool;
  try {
    localStorage.setItem(CACHE_KEY, JSON.stringify(pool));
  } catch {
    /* ignore */
  }
}

async function fetchJson<T>(url: string): Promise<T | null> {
  try {
    const res = await fetch(url, {method: 'GET'});
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

async function fetchText(url: string): Promise<string | null> {
  try {
    const res = await fetch(url, {method: 'GET'});
    if (!res.ok) return null;
    return await res.text();
  } catch {
    return null;
  }
}

function toNews(
  id: string,
  category: LearningCategory,
  title: string,
  summary?: string,
  source?: string,
): LearningQuestion {
  const body = summary ? stripHtml(summary).slice(0, 220) : '';
  const question = body ? `${title.trim()}\n${body}` : title.trim();
  return {
    id,
    kind: 'news',
    category,
    label: labelForCategory(category, 'news'),
    question,
    source,
  };
}

/** Free AI news API (no auth). */
async function fetchAiNews(limit = 40): Promise<LearningQuestion[]> {
  const data = await fetchJson<{
    success?: boolean;
    data?: Array<{title?: string; summary?: string; source?: string; slug?: string}>;
  }>(`https://whatstrending.ai/api/articles?limit=${limit}`);
  if (!data?.data?.length) return [];
  return data.data
    .filter(a => a.title)
    .map((a, i) =>
      toNews(
        `ai-${a.slug ?? hashShort(a.title!)}-${i}`,
        'AI News',
        a.title!,
        a.summary,
        a.source,
      ),
    );
}

/** Parse a few <item> blocks from RSS XML (title + description). */
function parseRssItems(
  xml: string,
  category: LearningCategory,
  idPrefix: string,
  max = 30,
): LearningQuestion[] {
  const items: LearningQuestion[] = [];
  const chunks = xml.split(/<item[\s>]/i).slice(1);
  for (let i = 0; i < chunks.length && items.length < max; i += 1) {
    const chunk = chunks[i]!;
    const titleMatch = chunk.match(/<title[^>]*>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/title>/i);
    const descMatch = chunk.match(
      /<description[^>]*>(?:<!\[CDATA\[)?([\s\S]*?)(?:\]\]>)?<\/description>/i,
    );
    const title = titleMatch ? stripHtml(titleMatch[1] ?? '') : '';
    if (!title || title.toLowerCase() === 'quanta magazine') continue;
    const desc = descMatch ? stripHtml(descMatch[1] ?? '') : '';
    items.push(toNews(`${idPrefix}-${hashShort(title)}-${i}`, category, title, desc));
  }
  return items;
}

async function fetchRssViaProxy(
  feedUrl: string,
  category: LearningCategory,
  idPrefix: string,
): Promise<LearningQuestion[]> {
  const proxied = `https://api.allorigins.win/raw?url=${encodeURIComponent(feedUrl)}`;
  const xml = await fetchText(proxied);
  if (!xml || !xml.includes('<item')) return [];
  return parseRssItems(xml, category, idPrefix);
}

async function fetchMathsAndTechNews(): Promise<LearningQuestion[]> {
  const [quanta, bbcTech] = await Promise.all([
    fetchRssViaProxy('https://www.quantamagazine.org/feed/', 'Maths News', 'maths-news'),
    fetchRssViaProxy(
      'https://feeds.bbci.co.uk/news/technology/rss.xml',
      'Technology News',
      'tech-news',
    ),
  ]);
  // Quanta mixes maths/physics/science — keep maths-leaning + general as Maths News / Tech.
  const maths = quanta.filter(q => {
    const t = q.question.toLowerCase();
    return (
      t.includes('math') ||
      t.includes('graph') ||
      t.includes('number') ||
      t.includes('proof') ||
      t.includes('algebra') ||
      t.includes('geometry') ||
      t.includes('quantum') ||
      t.includes('physic')
    );
  });
  const mathsNews = (maths.length > 0 ? maths : quanta.slice(0, 12)).map(q => ({
    ...q,
    category: 'Maths News' as const,
    label: labelForCategory('Maths News', 'news'),
  }));
  return [...mathsNews, ...bbcTech];
}

/** India / current affairs headlines via public RSS (best-effort). */
async function fetchCurrentAffairsNews(): Promise<LearningQuestion[]> {
  const feeds = [
    'https://feeds.bbci.co.uk/news/world/asia/india/rss.xml',
    'https://www.thehindu.com/news/national/feeder/default.rss',
  ];
  const batches = await Promise.all(
    feeds.map((url, i) => fetchRssViaProxy(url, 'Current Affairs', `ca-${i}`)),
  );
  return batches.flat();
}

/** Map quantitative aptitude API into Gov Exam Maths practice. */
async function fetchGovMathsFill(count: number): Promise<LearningQuestion[]> {
  const topics = [
    'Age',
    'SimpleInterest',
    'ProfitAndLoss',
    'SpeedTimeDistance',
    'PermutationAndCombination',
  ] as const;
  const out: LearningQuestion[] = [];
  const seen = new Set<string>();
  const batch = await Promise.all(
    topics.map(topic =>
      fetchJson<{question?: string; answer?: string}>(
        `https://aptitude-gold.vercel.app/${topic}`,
      ),
    ),
  );
  for (const item of batch) {
    if (!item?.question || !item.answer) continue;
    const key = item.question.trim().toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    out.push({
      id: `gov-math-${hashShort(item.question)}`,
      kind: 'question',
      category: 'Gov Exam Maths',
      label: labelForCategory('Gov Exam Maths'),
      question: item.question.trim(),
      answer: item.answer.trim(),
    });
    if (out.length >= count) break;
  }
  return out;
}

function dedupe(questions: LearningQuestion[]): LearningQuestion[] {
  const seen = new Set<string>();
  const out: LearningQuestion[] = [];
  for (const q of questions) {
    const key = q.question.trim().toLowerCase();
    if (!key || seen.has(key)) continue;
    seen.add(key);
    out.push(q);
  }
  return out;
}

/** Prefer exam practice, then news, then anything else. */
function preferredOrder(questions: LearningQuestion[]): LearningQuestion[] {
  const exam: LearningQuestion[] = [];
  const news: LearningQuestion[] = [];
  const rest: LearningQuestion[] = [];
  for (const q of questions) {
    if (q.kind === 'news') {
      news.push(q);
    } else if (
      q.category === 'CSAT' ||
      q.category === 'BPSC' ||
      q.category === 'Gov Exam English' ||
      q.category === 'Gov Exam Maths' ||
      q.category === 'JEE Maths' ||
      q.category === 'JEE Physics' ||
      q.category === 'Current Affairs'
    ) {
      exam.push(q);
    } else {
      rest.push(q);
    }
  }
  return [...exam, ...news, ...rest];
}

export async function fetchDailyLearningQuestions(
  targetCount: number = TARGET_COUNT,
): Promise<LearningQuestion[]> {
  const collected: LearningQuestion[] = [...learningExamBank];

  const [aiNews, mathsTech, currentAffairs, govMaths] = await Promise.all([
    fetchAiNews(40),
    fetchMathsAndTechNews(),
    fetchCurrentAffairsNews(),
    fetchGovMathsFill(20),
  ]);

  collected.push(...aiNews, ...mathsTech, ...currentAffairs, ...govMaths);

  const merged = preferredOrder(dedupe(collected));
  if (merged.length === 0) {
    return learningQuestions;
  }
  return merged.length > targetCount ? merged.slice(0, targetCount) : merged;
}

export type LearningPoolEnsureResult = {
  source: 'cache' | 'stale-cache' | 'network' | 'default';
  count: number;
  readyImmediately: boolean;
};

export function hydrateLearningPoolFromCache(
  now: Date = new Date(),
): LearningPoolEnsureResult {
  const dateKey = todayKey(now);
  const cached = readCache();
  if (cached?.questions?.length) {
    setActiveLearningPool(cached.questions);
    const fresh =
      cached.dateKey === dateKey && now.getTime() - cached.fetchedAt < DAY_MS;
    return {
      source: fresh ? 'cache' : 'stale-cache',
      count: cached.questions.length,
      readyImmediately: true,
    };
  }
  setActiveLearningPool(learningQuestions);
  return {
    source: 'default',
    count: learningQuestions.length,
    readyImmediately: true,
  };
}

export type EnsureLearningPoolOptions = {
  onPoolUpdated?: () => void;
};

export async function ensureDailyLearningPool(
  now: Date = new Date(),
  options?: EnsureLearningPoolOptions,
): Promise<LearningPoolEnsureResult> {
  const dateKey = todayKey(now);
  const immediate = hydrateLearningPoolFromCache(now);
  options?.onPoolUpdated?.();

  if (immediate.source === 'cache') {
    return immediate;
  }

  try {
    const questions = await fetchDailyLearningQuestions(TARGET_COUNT);
    const hasLiveContent =
      questions.some(q => q.kind === 'news') ||
      questions.some(q => q.id.startsWith('gov-math-') || q.id.startsWith('ai-')) ||
      questions.length > learningExamBank.length;

    if (!hasLiveContent || questions.length === 0) {
      setActiveLearningPool(learningQuestions);
      return {
        source: immediate.source === 'stale-cache' ? 'stale-cache' : 'default',
        count: getActiveLearningPool().length,
        readyImmediately: true,
      };
    }
    writeCache({dateKey, fetchedAt: now.getTime(), questions});
    setActiveLearningPool(questions);
    options?.onPoolUpdated?.();
    return {source: 'network', count: questions.length, readyImmediately: true};
  } catch {
    return {
      source: immediate.source === 'stale-cache' ? 'stale-cache' : 'default',
      count: getActiveLearningPool().length,
      readyImmediately: true,
    };
  }
}

export function clearLearningPoolCacheForTests(): void {
  memoryCache = null;
  try {
    localStorage.removeItem(CACHE_KEY);
    localStorage.removeItem('pcp_learning_pool_v1');
  } catch {
    /* ignore */
  }
}
