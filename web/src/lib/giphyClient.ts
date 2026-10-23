export type GifResult = {
  id: string;
  title: string;
  previewUrl: string;
  gifUrl: string;
  width: number;
  height: number;
};

type GiphyImage = {
  url?: string;
  width?: string;
  height?: string;
};

type GiphyResponse = {
  data?: Array<{
    id?: string;
    title?: string;
    images?: {
      original?: GiphyImage;
      downsized?: GiphyImage;
      fixed_width?: GiphyImage;
      fixed_width_small?: GiphyImage;
      preview_gif?: GiphyImage;
    };
  }>;
};

const GIPHY_BASE = 'https://api.giphy.com/v1/gifs';

export function giphyApiKeyFromEnv(
  env: Record<string, string | undefined> = import.meta.env as Record<string, string | undefined>,
): string | null {
  const key = env.VITE_GIPHY_API_KEY?.trim();
  return key ? key : null;
}

export function mapGiphyResults(payload: GiphyResponse): GifResult[] {
  const mapped: GifResult[] = [];
  for (const item of payload.data ?? []) {
    const images = item.images ?? {};
    const full = images.original ?? images.downsized ?? images.fixed_width;
    const preview =
      images.fixed_width_small ?? images.preview_gif ?? images.fixed_width ?? images.original;
    const gifUrl = full?.url?.trim();
    const previewUrl = preview?.url?.trim() ?? gifUrl;
    if (!item.id || !gifUrl || !previewUrl) {
      continue;
    }
    mapped.push({
      id: item.id,
      title: item.title?.trim() || 'GIF',
      previewUrl,
      gifUrl,
      width: Number(full?.width ?? 0) || 0,
      height: Number(full?.height ?? 0) || 0,
    });
  }
  return mapped;
}

async function giphyGet(
  path: string,
  apiKey: string,
  params: Record<string, string>,
  fetchImpl: typeof fetch = fetch,
): Promise<GiphyResponse> {
  const url = new URL(`${GIPHY_BASE}${path}`);
  url.searchParams.set('api_key', apiKey);
  url.searchParams.set('limit', '24');
  url.searchParams.set('rating', 'g');
  url.searchParams.set('bundle', 'messaging_non_clips');
  for (const [key, value] of Object.entries(params)) {
    url.searchParams.set(key, value);
  }
  const response = await fetchImpl(url.toString());
  if (!response.ok) {
    throw new Error(`Giphy request failed (${response.status})`);
  }
  return (await response.json()) as GiphyResponse;
}

export async function searchGiphyGifs(
  query: string,
  apiKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<GifResult[]> {
  const q = query.trim();
  if (!q) {
    return fetchTrendingGiphyGifs(apiKey, fetchImpl);
  }
  const payload = await giphyGet('/search', apiKey, {q}, fetchImpl);
  return mapGiphyResults(payload);
}

export async function fetchTrendingGiphyGifs(
  apiKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<GifResult[]> {
  const payload = await giphyGet('/trending', apiKey, {}, fetchImpl);
  return mapGiphyResults(payload);
}

export async function downloadGifAsFile(
  gif: GifResult,
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
