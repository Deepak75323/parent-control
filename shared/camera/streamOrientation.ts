/**
 * Remote camera orientation contract (child → viewers).
 *
 * The child publishes a portrait-locked WebRTC CVO while the phone is upright.
 * CVO changes only when the display rotates to landscape (horizontal tilt).
 * Table props, pick-up motion, and accelerometer jitter do not rotate the stream.
 *
 * Manual "Rotate view" on parent/web is viewer-only and additive.
 */
export type CameraStreamFacing = 'user' | 'environment';

/** Clockwise degrees stamped on each WebRTC frame by the child capturer. */
export type StreamFrameRotation = 0 | 90 | 180 | 270;
