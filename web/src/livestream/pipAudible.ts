/**
 * Chrome only opens picture-in-picture on its own when this tab is playing media.
 * The child camera is silent, so a very quiet local tone keeps the live view eligible
 * until the parent switches to another tab. It is not the child's microphone.
 */
let audio: HTMLAudioElement | null = null;
let context: AudioContext | null = null;
let oscillator: OscillatorNode | null = null;

export function shouldCuePictureInPicture(desktopLiveView: boolean): boolean {
  return desktopLiveView;
}

export function isLivePipCueRunning(): boolean {
  return audio != null;
}

export function startLivePipCue(): void {
  if (audio || typeof window === 'undefined') {
    return;
  }
  const AudioCtx =
    window.AudioContext ??
    (window as Window & {webkitAudioContext?: typeof AudioContext}).webkitAudioContext;
  if (!AudioCtx) {
    return;
  }
  const ctx = new AudioCtx();
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  const dest = ctx.createMediaStreamDestination();
  gain.gain.value = 0.02;
  osc.frequency.value = 140;
  osc.connect(gain);
  gain.connect(dest);
  osc.start();
  const el = document.createElement('audio');
  el.srcObject = dest.stream;
  el.loop = true;
  el.muted = false;
  el.volume = 1;
  el.setAttribute('aria-hidden', 'true');
  el.className = 'stream-browser-pip-source';
  document.body.appendChild(el);
  void ctx.resume();
  void el.play().catch(() => undefined);
  audio = el;
  context = ctx;
  oscillator = osc;
}

export function resumeLivePipCue(): void {
  if (!audio || !context) {
    startLivePipCue();
    return;
  }
  void context.resume();
  if (audio.paused) {
    void audio.play().catch(() => undefined);
  }
}

export function stopLivePipCue(): void {
  audio?.pause();
  audio?.remove();
  if (audio) {
    audio.srcObject = null;
  }
  try {
    oscillator?.stop();
  } catch {
    /* already stopped */
  }
  void context?.close();
  audio = null;
  context = null;
  oscillator = null;
}
