export type TenorGifResult = {
  id: string;
  title: string;
  previewUrl: string;
  gifUrl: string;
  width: number;
  height: number;
};

type TenorMedia = {
  url?: string;
  dims?: number[];
};

type TenorResponse = {
  results?: Array<{
    id?: string;
    title?: string;
    media_formats?: Record<string, TenorMedia>;
  }>;
};

const TENOR_BASE = 'https://tenor.googleapis.com/v2';

export function tenorApiKeyFromEnv(
  env: Record<string, string | undefined> = import.meta.env as Record<string, string | undefined>,
): string | null {
  const key = env.VITE_TENOR_API_KEY?.trim();
  return key ? key : null;
}

export function mapTenorResults(payload: TenorResponse): TenorGifResult[] {
  const results = payload.results ?? [];
  const mapped: TenorGifResult[] = [];
  for (const item of results) {
    const formats = item.media_formats ?? {};
    const gif = formats.gif ?? formats.mediumgif ?? formats.tinygif;
    const preview = formats.tinygif ?? formats.nanogif ?? formats.gif;
    const gifUrl = gif?.url?.trim();
    const previewUrl = preview?.url?.trim() ?? gifUrl;
    if (!item.id || !gifUrl || !previewUrl) {
      continue;
    }
    const dims = gif?.dims ?? preview?.dims ?? [0, 0];
    mapped.push({
      id: item.id,
      title: item.title?.trim() || 'GIF',
      previewUrl,
      gifUrl,
      width: dims[0] ?? 0,
      height: dims[1] ?? 0,
    });
  }
  return mapped;
}

async function tenorGet(
  path: string,
  apiKey: string,
  params: Record<string, string>,
  fetchImpl: typeof fetch = fetch,
): Promise<TenorResponse> {
  const url = new URL(`${TENOR_BASE}${path}`);
  url.searchParams.set('key', apiKey);
  url.searchParams.set('client_key', 'parental_control_notes');
  url.searchParams.set('media_filter', 'gif,tinygif,nanogif,mediumgif');
  url.searchParams.set('contentfilter', 'high');
  url.searchParams.set('limit', '24');
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value);
  }
  const response = await fetchImpl(url.toString());
  if (!response.ok) {
    throw new Error(`Tenor request failed (${response.status})`);
  }
  return (await response.json()) as TenorResponse;
}

export async function searchTenorGifs(
  query: string,
  apiKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<TenorGifResult[]> {
  const q = query.trim();
  if (!q) {
    return fetchTrendingTenorGifs(apiKey, fetchImpl);
  }
  const payload = await tenorGet('/search', apiKey, {q}, fetchImpl);
  return mapTenorResults(payload);
}

export async function fetchTrendingTenorGifs(
  apiKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<TenorGifResult[]> {
  const payload = await tenorGet('/featured', apiKey, {}, fetchImpl);
  return mapTenorResults(payload);
}

export async function downloadGifAsFile(
  gif: TenorGifResult,
  fetchImpl: typeof fetch = fetch,
): Promise<File> {
  const response = await fetchImpl(gif.gifUrl);
  if (!response.ok) {
    throw new Error('Could not download GIF');
  }
  const blob = await response.blob();
  const type = blob.type && blob.type.startsWith('image/') ? blob.type : 'image/gif';
  const safeName = (gif.title || 'gif')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 40);
  return new File([blob], `${safeName || 'sticker'}-${gif.id}.gif`, {type});
}
