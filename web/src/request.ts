/** Deadline mencakup fetch DAN pembacaan body. Cancel milik satu attempt. */
export async function request<T>(
  url: string,
  init: RequestInit,
  read: (response: Response) => Promise<T>,
  signal?: AbortSignal,
  timeoutMs = 15_000,
): Promise<T> {
  const controller = new AbortController();
  let rejectAbort: (reason: unknown) => void = () => {};
  const cancelled = new Promise<never>((_, reject) => { rejectAbort = reject; });
  const abort = () => {
    const reason = signal?.reason ?? new DOMException('Permintaan dibatalkan.', 'AbortError');
    controller.abort(reason);
    rejectAbort(reason);
  };
  const timer = setTimeout(() => {
    const reason = new DOMException('Server tidak menjawab dalam batas waktu.', 'TimeoutError');
    controller.abort(reason);
    rejectAbort(reason);
  }, timeoutMs);
  signal?.addEventListener('abort', abort, {once: true});
  if (signal?.aborted) abort();
  try {
    const operation = async () => {
      if (controller.signal.aborted) throw controller.signal.reason;
      const response = await fetch(url, {...init, signal: controller.signal});
      return read(response);
    };
    return await Promise.race([operation(), cancelled]);
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', abort);
  }
}
