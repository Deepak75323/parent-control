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

type GiphyImages = {
  original?: GiphyImage;
  downsized?: GiphyImage;
  downsized_medium?: GiphyImage;
  fixed_width?: GiphyImage;
  fixed_width_small?: GiphyImage;
  fixed_height?: GiphyImage;
  fixed_height_small?: GiphyImage;
  preview_gif?: GiphyImage;
  original_still?: GiphyImage;
  fixed_width_still?: GiphyImage;
  fixed_height_still?: GiphyImage;
  fixed_width_small_still?: GiphyImage;
};

type GiphyResponse = {
  data?: Array<{
    id?: string;
    title?: string;
    images?: GiphyImages;
  }>;
};

type GiphyKind = 'gifs' | 'stickers';

const GIPHY_API = 'https://api.giphy.com/v1';

function firstUrl(...images: Array<GiphyImage | undefined>): string | null {
  for (const image of images) {
    const url = image?.url?.trim();
    if (url) {
      return url;
    }
  }
  return null;
}

export function giphyApiKeyFromEnv(
  env: Record<string, string | undefined> = import.meta.env as Record<string, string | undefined>,
): string | null {
  const key = env.VITE_GIPHY_API_KEY?.trim();
  return key ? key : null;
}

export function mapGiphyResults(
  payload: GiphyResponse,
  fallbackTitle = 'GIF',
  kind: GiphyKind = 'gifs',
): GifResult[] {
  const mapped: GifResult[] = [];
  for (const item of payload.data ?? []) {
    const images = item.images ?? {};
    const gifUrl =
      kind === 'stickers'
        ? firstUrl(
            images.fixed_height,
            images.fixed_width,
            images.downsized_medium,
            images.downsized,
            images.original,
          )
        : firstUrl(images.original, images.downsized, images.fixed_width, images.fixed_height);
    // Stickers: prefer a compact still/preview so the grid doesn't stretch on tall assets.
    const previewUrl =
      kind === 'stickers'
        ? firstUrl(
            images.fixed_height_small,
            images.fixed_width_small,
            images.fixed_height_still,
            images.fixed_width_still,
            images.fixed_width_small_still,
            images.preview_gif,
            images.original_still,
            images.fixed_height,
            images.fixed_width,
          )
        : firstUrl(
            images.fixed_width_small,
            images.preview_gif,
            images.fixed_width,
            images.original,
          );
    if (!item.id || !gifUrl || !previewUrl) {
      continue;
    }
    const sizeSource =
      kind === 'stickers'
        ? images.fixed_height ?? images.fixed_width ?? images.original
        : images.original ?? images.fixed_width;
    mapped.push({
      id: item.id,
      title: item.title?.trim() || fallbackTitle,
      previewUrl,
      gifUrl,
      width: Number(sizeSource?.width ?? 0) || 0,
      height: Number(sizeSource?.height ?? 0) || 0,
    });
  }
  return mapped;
}

async function giphyGet(
  kind: GiphyKind,
  path: string,
  apiKey: string,
  params: Record<string, string>,
  fetchImpl: typeof fetch = fetch,
): Promise<GiphyResponse> {
  const url = new URL(`${GIPHY_API}/${kind}${path}`);
  url.searchParams.set('api_key', apiKey);
  url.searchParams.set('limit', '24');
  url.searchParams.set('rating', 'g');
  // Bundle changes media shapes and can break sticker previews — only use for GIFs.
  if (kind === 'gifs') {
    url.searchParams.set('bundle', 'messaging_non_clips');
  }
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
  const payload = await giphyGet('gifs', '/search', apiKey, {q}, fetchImpl);
  return mapGiphyResults(payload, 'GIF', 'gifs');
}

export async function fetchTrendingGiphyGifs(
  apiKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<GifResult[]> {
  const payload = await giphyGet('gifs', '/trending', apiKey, {}, fetchImpl);
  return mapGiphyResults(payload, 'GIF', 'gifs');
}

export async function searchGiphyStickers(
  query: string,
  apiKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<GifResult[]> {
  const q = query.trim();
  if (!q) {
    return fetchTrendingGiphyStickers(apiKey, fetchImpl);
  }
  const payload = await giphyGet('stickers', '/search', apiKey, {q}, fetchImpl);
  return mapGiphyResults(payload, 'Sticker', 'stickers');
}

export async function fetchTrendingGiphyStickers(
  apiKey: string,
  fetchImpl: typeof fetch = fetch,
): Promise<GifResult[]> {
  const payload = await giphyGet('stickers', '/trending', apiKey, {}, fetchImpl);
  return mapGiphyResults(payload, 'Sticker', 'stickers');
}

export async function downloadGifAsFile(
  gif: GifResult,
  fetchImpl: typeof fetch = fetch,
  kind: 'gif' | 'sticker' = 'gif',
): Promise<File> {
  const response = await fetchImpl(gif.gifUrl);
  if (!response.ok) {
    throw new Error(kind === 'sticker' ? 'Could not download sticker' : 'Could not download GIF');
  }
  const blob = await response.blob();
  const type = blob.type && blob.type.startsWith('image/') ? blob.type : 'image/gif';
  const ext = type.includes('webp') ? 'webp' : type.includes('png') ? 'png' : 'gif';
  const safeName = (gif.title || kind)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 40);
  return new File([blob], `${safeName || kind}-${gif.id}.${ext}`, {type});
}
