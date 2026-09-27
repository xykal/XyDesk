/** Video dan audio remote diputar terpisah; audio yang tidak mengalir tidak
 * ikut menjadi track pada elemen video. Tidak menghentikan track milik peer. */
export function videoOnlyStream(stream: MediaStream): MediaStream {
  return new MediaStream(stream.getVideoTracks());
}

export async function playRemoteVideo(video: HTMLVideoElement, stream: MediaStream): Promise<void> {
  video.muted = true;
  video.playsInline = true;
  if (video.srcObject !== stream) video.srcObject = stream;
  await video.play();
}

/** Own one element/stream attachment. A null srcObject is the normal FIRST
 * mount, not evidence of a stale callback. Cleanup never stops peer tracks. */
export function startRemoteVideoPlayback(
  video: HTMLVideoElement,
  stream: MediaStream,
  isCurrent: () => boolean,
  onBlocked: (blocked: boolean) => void = () => {},
): () => void {
  let cancelled = false;
  let retries = 0;
  let pending = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const current = () => !cancelled && isCurrent();
  const play = () => {
    if (!current() || pending) return;
    if (timer !== undefined) clearTimeout(timer);
    pending = true;
    void playRemoteVideo(video, stream).then(() => {
      if (current()) onBlocked(false);
    }).catch(() => {
      if (!current()) return;
      onBlocked(true);
      if (retries++ < 12) timer = setTimeout(play, 500);
    }).finally(() => { pending = false; });
  };
  video.addEventListener('canplay', play);
  play();
  return () => {
    cancelled = true;
    if (timer !== undefined) clearTimeout(timer);
    video.removeEventListener('canplay', play);
    if (video.srcObject === stream) {
      video.pause();
      video.srcObject = null;
    }
  };
}
