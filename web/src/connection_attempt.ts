/** Satu pemilik untuk request dan timer reconnect, termasuk klik manual baru. */
export class ConnectionAttempt {
  private controller?: AbortController;
  private timer?: ReturnType<typeof setTimeout>;

  begin(): AbortSignal {
    this.cancel();
    this.controller = new AbortController();
    return this.controller.signal;
  }

  isCurrent(signal: AbortSignal): boolean {
    return this.controller?.signal === signal && !signal.aborted;
  }

  schedule(signal: AbortSignal, action: () => void, delay: number) {
    if (!this.isCurrent(signal)) return;
    if (this.timer !== undefined) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.timer = undefined;
      if (this.isCurrent(signal)) action();
    }, delay);
  }

  cancel() {
    if (this.timer !== undefined) clearTimeout(this.timer);
    this.timer = undefined;
    this.controller?.abort();
    this.controller = undefined;
  }
}
