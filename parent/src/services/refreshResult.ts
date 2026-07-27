import type {AxiosError} from 'axios';

export type RefreshResultStatus = 'ok' | 'invalid' | 'transient';

/** Classify refresh failures so we only force logout on real auth rejection. */
export function classifyRefreshFailure(error: unknown): 'invalid' | 'transient' {
  const axiosError = error as AxiosError | undefined;
  const status = axiosError?.response?.status;
  if (status === 401 || status === 403) {
    return 'invalid';
  }
  // 429 = concurrent refresh lock — retry later, do not sign out
  // no response = network / timeout
  // 5xx = server blip
  return 'transient';
}
