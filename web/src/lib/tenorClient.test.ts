import {describe, expect, it, vi} from 'vitest';
import {
  downloadGifAsFile,
  mapTenorResults,
  searchTenorGifs,
  tenorApiKeyFromEnv,
} from './tenorClient';

describe('tenorClient', () => {
  it('reads API key from env', () => {
    expect(tenorApiKeyFromEnv({VITE_TENOR_API_KEY: ' abc '})).toBe('abc');
    expect(tenorApiKeyFromEnv({VITE_TENOR_API_KEY: ''})).toBeNull();
  });

  it('maps Tenor media formats to gif results', () => {
    const results = mapTenorResults({
      results: [
        {
          id: '1',
          title: 'Cat',
          media_formats: {
            gif: {url: 'https://example.com/a.gif', dims: [200, 100]},
            tinygif: {url: 'https://example.com/a-tiny.gif', dims: [100, 50]},
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

  it('searches Tenor and downloads a gif file', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          results: [
            {
              id: '99',
              title: 'Wave',
              media_formats: {
                gif: {url: 'https://example.com/wave.gif', dims: [120, 120]},
              },
            },
          ],
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        blob: async () => new Blob([new Uint8Array([71, 73, 70])], {type: 'image/gif'}),
      });

    const gifs = await searchTenorGifs('wave', 'test-key', fetchImpl as unknown as typeof fetch);
    expect(gifs).toHaveLength(1);
    expect(String(fetchImpl.mock.calls[0][0])).toContain('tenor.googleapis.com/v2/search');
    expect(String(fetchImpl.mock.calls[0][0])).toContain('contentfilter=high');

    const file = await downloadGifAsFile(gifs[0], fetchImpl as unknown as typeof fetch);
    expect(file.type).toBe('image/gif');
    expect(file.name).toContain('99');
  });
});
