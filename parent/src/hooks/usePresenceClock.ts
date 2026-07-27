import {useEffect, useState} from 'react';

/**
 * Re-render on an interval so presence UI re-evaluates stale lastSeen without
 * waiting for pull-to-refresh or a websocket event.
 */
export function usePresenceClock(intervalMs: number = 15_000): number {
  const [nowMs, setNowMs] = useState(() => Date.now());

  useEffect(() => {
    const id = setInterval(() => setNowMs(Date.now()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);

  return nowMs;
}
