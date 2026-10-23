import {describe, expect, it, vi} from 'vitest';
import {
  downloadGifAsFile,
  giphyApiKeyFromEnv,
  mapGiphyResults,
  searchGiphyGifs,
} from './giphyClient';

describe('giphyClient', () => {
  it('reads API key from env', () => {
    expect(giphyApiKeyFromEnv({VITE_GIPHY_API_KEY: ' abc '})).toBe('abc');
    expect(giphyApiKeyFromEnv({VITE_GIPHY_API_KEY: ''})).toBeNull();
  });

  it('maps Giphy images to gif results', () => {
    const results = mapGiphyResults({
      data: [
        {
          id: '1',
          title: 'Cat',
          images: {
            original: {url: 'https://example.com/a.gif', width: '200', height: '100'},
            fixed_width_small: {
              url: 'https://example.com/a-tiny.gif',
              width: '100',
              height: '50',
            },
          },
        },
        {id: '2', title: 'Broken'},
      ],
    });
    expect(results).toEqual([
      {
        id: '1',
        title: 'Cat',
        previewUrl: 'https://example.com/a-tiny.gif',
        gifUrl: 'https://example.com/a.gif',
        width: 200,
        height: 100,
      },
    ]);
  });

  it('searches Giphy and downloads a gif file', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          data: [
            {
              id: '99',
              title: 'Wave',
              images: {
                original: {url: 'https://example.com/wave.gif', width: '120', height: '120'},
              },
            },
          ],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        blob: async () => new Blob([new Uint8Array([71, 73, 70])], {type: 'image/gif'}),
      });

    const gifs = await searchGiphyGifs('wave', 'test-key', fetchImpl as unknown as typeof fetch);
    expect(gifs).toHaveLength(1);
    expect(String(fetchImpl.mock.calls[0][0])).toContain('api.giphy.com/v1/gifs/search');
    expect(String(fetchImpl.mock.calls[0][0])).toContain('rating=g');

    const file = await downloadGifAsFile(gifs[0], fetchImpl as unknown as typeof fetch);
    expect(file.type).toBe('image/gif');
    expect(file.name).toContain('99');
  });
});
