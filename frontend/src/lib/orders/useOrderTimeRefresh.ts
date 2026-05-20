/**
 * Força re-render a cada N segundos para recalcular tempo decorrido
 * sem precisar refetchar dados do backend.
 *
 * Default: 30s — suficiente para granularidade de "minutos" sem ser caro.
 */
import { useState, useEffect } from 'react';

export function useOrderTimeRefresh(intervalMs: number = 30_000): Date {
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), intervalMs);
    return () => clearInterval(id);
  }, [intervalMs]);

  return now;
}
